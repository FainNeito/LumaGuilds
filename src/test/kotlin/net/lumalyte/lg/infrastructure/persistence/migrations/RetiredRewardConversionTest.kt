package net.lumalyte.lg.infrastructure.persistence.migrations

import net.lumalyte.lg.infrastructure.persistence.guilds.RewardSqlTestFixture
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** REQ-158: owned Allied Waypoints become Grand Vault exactly once, with nothing else changed. */
class RetiredRewardConversionTest : RewardSqlTestFixture() {
    @Test
    fun `owned and goal ally homes convert to grand vault idempotently`() {
        val storage = openStorage()
        migrateProductionSchema(storage)
        val db = storage.connection
        db.executeUpdate("INSERT INTO guild_reward_accounts (guild_id, version, initial_home_capacity, prestige_count) VALUES ('g1', 3, 6, 1)")
        db.executeUpdate("INSERT INTO guild_reward_accounts (guild_id, version, initial_home_capacity, prestige_count) VALUES ('g2', 1, 6, 0)")
        db.executeUpdate("INSERT INTO guild_reward_ownership (guild_id, reward_id, permanent) VALUES ('g1', 'ally-homes', ?)", true)
        db.executeUpdate("INSERT INTO guild_reward_ownership (guild_id, reward_id, permanent) VALUES ('g1', 'bank-1', ?)", false)
        db.executeUpdate("INSERT INTO guild_reward_ownership (guild_id, reward_id, permanent) VALUES ('g2', 'ally-homes', ?)", false)
        db.executeUpdate("INSERT INTO guild_chronicle_goals (guild_id, reward_id, revision) VALUES ('g2', 'ally-homes', 4)")

        repeat(2) { storage.connection.connection.use { RetiredRewardSchema.convert(it) } }

        val owned = db.getResults("SELECT guild_id, reward_id, permanent FROM guild_reward_ownership ORDER BY guild_id, reward_id")
            .map { Triple(it.getString("guild_id"), it.getString("reward_id"), it.get<Any>("permanent").toString() in setOf("1", "true")) }
        assertEquals(
            listOf(Triple("g1", "bank-1", false), Triple("g1", "grand-vault", true), Triple("g2", "grand-vault", false)),
            owned,
        )
        assertEquals("grand-vault", db.getFirstRow("SELECT reward_id FROM guild_chronicle_goals WHERE guild_id = 'g2'")!!.getString("reward_id"))
        assertEquals(1, db.getFirstRow("SELECT prestige_count FROM guild_reward_accounts WHERE guild_id = 'g1'")!!.getInt("prestige_count"))
        // Converted accounts advance their version once so stale ownership writes fail.
        val versions = db.getResults("SELECT guild_id, version FROM guild_reward_accounts ORDER BY guild_id")
            .associate { it.getString("guild_id") to it.getLong("version") }
        assertEquals(mapOf("g1" to 4L, "g2" to 2L), versions)
    }
}
