package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection

/**
 * REQ-158: Allied Waypoints (`ally-homes`) was replaced by Grand Vault
 * (`grand-vault`). Converts owned copies and Chronicle goals in place. Safe to run
 * on every start: once no `ally-homes` rows remain it changes nothing. Runs inside
 * the caller's migration transaction.
 */
internal object RetiredRewardSchema {
    const val VERSION = 50
    const val RETIRED = "ally-homes"
    const val REPLACEMENT = "grand-vault"

    fun convert(connection: Connection) {
        if (!tableExists(connection, "guild_reward_ownership")) return
        val guilds = connection.prepareStatement(
            "SELECT DISTINCT guild_id FROM guild_reward_ownership WHERE reward_id = ?"
        ).use { statement ->
            statement.setString(1, RETIRED)
            statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }
        }
        guilds.forEach { guild ->
            // A guild cannot already own the new ID, but never create a duplicate key.
            connection.prepareStatement(
                "DELETE FROM guild_reward_ownership WHERE guild_id = ? AND reward_id = ?"
            ).use { it.setString(1, guild); it.setString(2, REPLACEMENT); it.executeUpdate() }
            connection.prepareStatement(
                "UPDATE guild_reward_ownership SET reward_id = ? WHERE guild_id = ? AND reward_id = ?"
            ).use { it.setString(1, REPLACEMENT); it.setString(2, guild); it.setString(3, RETIRED); it.executeUpdate() }
            // Advance the optimistic version so a write prepared before the upgrade fails.
            connection.prepareStatement(
                "UPDATE guild_reward_accounts SET version = version + 1 WHERE guild_id = ?"
            ).use { it.setString(1, guild); it.executeUpdate() }
        }
        if (tableExists(connection, "guild_chronicle_goals")) {
            connection.prepareStatement(
                "UPDATE guild_chronicle_goals SET reward_id = ?, revision = revision + 1 WHERE reward_id = ?"
            ).use { it.setString(1, REPLACEMENT); it.setString(2, RETIRED); it.executeUpdate() }
        }
    }

    private fun tableExists(connection: Connection, table: String): Boolean =
        connection.metaData.getTables(connection.catalog, null, table, arrayOf("TABLE")).use { it.next() }
}
