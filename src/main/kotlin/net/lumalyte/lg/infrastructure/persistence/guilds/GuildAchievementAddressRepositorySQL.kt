package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.time.Instant
import java.util.UUID

/** Stores only a keyed comparison fingerprint and observation time, never an address. */
internal class GuildAchievementAddressRepositorySQL(private val storage: Storage<Database>) {
    data class Observation(val fingerprint: String, val observedAt: Instant)

    init {
        storage.connection.executeUpdate(
            """CREATE TABLE IF NOT EXISTS guild_achievement_address_observations (
                player_id VARCHAR(36) PRIMARY KEY,
                fingerprint VARCHAR(64) NOT NULL,
                observed_at BIGINT NOT NULL
            )""".trimIndent(),
        )
    }

    fun observe(playerId: UUID, fingerprint: String, observedAt: Instant) {
        require(fingerprint.matches(Regex("[0-9a-f]{64}")))
        require(observedAt.toEpochMilli() >= 0)
        val sql = if (storage.dialect == SqlDialect.MARIADB) {
            """INSERT INTO guild_achievement_address_observations (player_id, fingerprint, observed_at)
                VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE
                fingerprint = IF(VALUES(observed_at) > observed_at, VALUES(fingerprint), fingerprint),
                observed_at = GREATEST(observed_at, VALUES(observed_at))""".trimIndent()
        } else {
            """INSERT INTO guild_achievement_address_observations (player_id, fingerprint, observed_at)
                VALUES (?, ?, ?) ON CONFLICT(player_id) DO UPDATE SET
                fingerprint = excluded.fingerprint, observed_at = excluded.observed_at
                WHERE excluded.observed_at > guild_achievement_address_observations.observed_at""".trimIndent()
        }
        storage.connection.executeUpdate(sql, playerId.toString(), fingerprint, observedAt.toEpochMilli())
    }

    fun read(playerId: UUID): Observation? {
        val row = storage.connection.getFirstRow(
            "SELECT fingerprint, observed_at FROM guild_achievement_address_observations WHERE player_id = ?",
            playerId.toString(),
        ) ?: return null
        return Observation(row.getString("fingerprint"), Instant.ofEpochMilli(row.getLong("observed_at")))
    }
}
