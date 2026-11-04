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
                key_id VARCHAR(32) NOT NULL,
                observed_at BIGINT NOT NULL,
                ambiguous INTEGER NOT NULL DEFAULT 0
            )""".trimIndent(),
        )
    }

    fun observe(playerId: UUID, fingerprint: String, keyId: String, observedAt: Instant) {
        require(fingerprint.matches(Regex("[0-9a-f]{64}")))
        require(keyId.matches(Regex("[0-9a-f]{32}")))
        require(observedAt.toEpochMilli() >= 0)
        val sql = if (storage.dialect == SqlDialect.MARIADB) {
            """INSERT INTO guild_achievement_address_observations (player_id, fingerprint, key_id, observed_at)
                VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE
                ambiguous = CASE WHEN VALUES(observed_at) > observed_at THEN 0
                    WHEN VALUES(observed_at) = observed_at AND (VALUES(fingerprint) <> fingerprint OR VALUES(key_id) <> key_id) THEN 1
                    ELSE ambiguous END,
                fingerprint = IF(VALUES(observed_at) > observed_at, VALUES(fingerprint), fingerprint),
                key_id = IF(VALUES(observed_at) > observed_at, VALUES(key_id), key_id),
                observed_at = GREATEST(observed_at, VALUES(observed_at))""".trimIndent()
        } else {
            """INSERT INTO guild_achievement_address_observations (player_id, fingerprint, key_id, observed_at)
                VALUES (?, ?, ?, ?) ON CONFLICT(player_id) DO UPDATE SET
                ambiguous = CASE WHEN excluded.observed_at > observed_at THEN 0
                    WHEN excluded.observed_at = observed_at AND (excluded.fingerprint <> fingerprint OR excluded.key_id <> key_id) THEN 1
                    ELSE ambiguous END,
                fingerprint = CASE WHEN excluded.observed_at > observed_at THEN excluded.fingerprint ELSE fingerprint END,
                key_id = CASE WHEN excluded.observed_at > observed_at THEN excluded.key_id ELSE key_id END,
                observed_at = MAX(observed_at, excluded.observed_at)""".trimIndent()
        }
        storage.connection.executeUpdate(sql, playerId.toString(), fingerprint, keyId, observedAt.toEpochMilli())
    }

    fun read(playerId: UUID, expectedKeyId: String): Observation? {
        require(expectedKeyId.matches(Regex("[0-9a-f]{32}")))
        val row = storage.connection.getFirstRow(
            "SELECT fingerprint, observed_at FROM guild_achievement_address_observations WHERE player_id = ? AND key_id = ? AND ambiguous = 0",
            playerId.toString(), expectedKeyId,
        ) ?: return null
        return Observation(row.getString("fingerprint"), Instant.ofEpochMilli(row.getLong("observed_at")))
    }
}
