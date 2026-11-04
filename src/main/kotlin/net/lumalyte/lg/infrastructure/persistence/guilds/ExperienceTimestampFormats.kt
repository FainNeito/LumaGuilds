package net.lumalyte.lg.infrastructure.persistence.guilds

import java.sql.Connection
import java.sql.Types
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Guild schemas exist with either epoch-number or MariaDB DATETIME columns. */
internal data class ExperienceTimestampFormats(
    val createdAtDateTime: Boolean, val lastUpdatedDateTime: Boolean,
    val lastLevelUpDateTime: Boolean, val transactionDateTime: Boolean,
) {
    fun value(instant: Instant, dateTime: Boolean): Any =
        if (dateTime) LocalDateTime.ofInstant(instant, ZoneOffset.UTC) else instant.toEpochMilli()

    companion object {
        fun inspect(connection: Connection, mariaDb: Boolean): ExperienceTimestampFormats {
            if (!mariaDb) return ExperienceTimestampFormats(false, false, false, false)
            fun datetime(table: String, column: String): Boolean = connection.createStatement().use { statement ->
                statement.executeQuery("SELECT $column FROM $table WHERE 1 = 0").use { rows ->
                    rows.metaData.getColumnType(1) in setOf(Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE, Types.DATE)
                }
            }
            return ExperienceTimestampFormats(datetime("guild_progression", "created_at"),
                datetime("guild_progression", "last_updated"), datetime("guild_progression", "last_level_up"),
                datetime("experience_transactions", "timestamp"))
        }
    }
}
