package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import java.time.Instant
import java.util.UUID
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage

/** Earliest proof of a guild unlock. Observations never authorize XP or personal credit. */
internal class GuildAdvancementHistorySQL(private val storage: Storage<Database>) {
    init {
        storage.connection.executeUpdate("""CREATE TABLE IF NOT EXISTS guild_advancement_history (
            guild_id VARCHAR(36)${guildIdCollation(storage)} NOT NULL, node VARCHAR(128) NOT NULL, observed_ms BIGINT NOT NULL,
            PRIMARY KEY (guild_id, node), FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
        )""".trimIndent())
    }

    fun remember(guild: UUID, nodes: Collection<String>, at: Instant) {
        if (nodes.isEmpty()) return
        val conflict = if (storage.dialect == SqlDialect.MARIADB)
            "ON DUPLICATE KEY UPDATE observed_ms = LEAST(observed_ms, VALUES(observed_ms))"
        else "ON CONFLICT(guild_id, node) DO UPDATE SET observed_ms = MIN(observed_ms, excluded.observed_ms)"
        storage.connection.connection.use { connection ->
            connection.committingTransaction {
                connection.prepareStatement("INSERT INTO guild_advancement_history (guild_id, node, observed_ms) VALUES (?, ?, ?) $conflict").use { s ->
                    nodes.forEach { node ->
                        s.setString(1, guild.toString())
                        s.setString(2, node)
                        s.setLong(3, at.toEpochMilli())
                        s.addBatch()
                    }
                    s.executeBatch()
                }
            }
        }
    }

    /** Used only from Market's post-payment callback. A quote by itself is never an unlock. */
    fun saleStartedAt(guild: UUID, sale: UUID): Instant? = storage.connection.connection.use { connection ->
        connection.selectOne("SELECT occurred_at FROM guild_shop_xp_sales WHERE guild_id = ? AND id = ?",
            guild.toString(), sale.toString()) { Instant.ofEpochMilli(it.getLong(1)) }
    }

    fun count(guild: UUID): Int = storage.connection.connection.use { connection ->
        connection.selectOne("SELECT COUNT(*) FROM guild_advancement_history WHERE guild_id = ?",
            guild.toString()) { it.getInt(1) } ?: 0
    }

    fun observed(guild: UUID, node: String): Instant? = storage.connection.connection.use { connection ->
        connection.selectOne("SELECT observed_ms FROM guild_advancement_history WHERE guild_id = ? AND node = ?",
            guild.toString(), node) { Instant.ofEpochMilli(it.getLong(1)) }
    }
}

/** MariaDB requires a string foreign key to use exactly the parent column's character set and collation. */
internal fun guildIdCollation(storage: Storage<Database>): String {
    if (storage.dialect != SqlDialect.MARIADB) return ""
    return storage.connection.connection.use { c ->
        checkNotNull(c.selectOne("""SELECT CHARACTER_SET_NAME, COLLATION_NAME FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'guilds' AND COLUMN_NAME = 'id'""".trimIndent()) { row ->
            val identifiers = listOf(row.getString(1), row.getString(2))
            require(identifiers.all { it != null && it.matches(Regex("[a-zA-Z0-9_]+")) })
            " CHARACTER SET ${identifiers[0]} COLLATE ${identifiers[1]}"
        }) { "Missing guild identifier column" }
    }
}
