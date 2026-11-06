package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import java.sql.Connection
import java.util.UUID
import net.lumalyte.lg.domain.achievements.AchievementRecord
import net.lumalyte.lg.domain.achievements.GuildAchievement
import net.lumalyte.lg.domain.achievements.GuildAchievementEngine
import net.lumalyte.lg.domain.achievements.GuildAchievementIpEligibility
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.json.JSONArray
import org.json.JSONObject

/** Isolated progress prototype. Completed nodes are not themselves authorization to pay XP. */
internal class GuildAchievementProgressRepositorySQL(
    private val storage: Storage<Database>, definitions: List<GuildAchievement>,
) {
    private val engine = GuildAchievementEngine(definitions)
    private val requirements = definitions.map { it.requirement }.toSet()
    private val maria = storage.dialect == SqlDialect.MARIADB

    data class Result(val progress: Map<String, Int>, val completed: Set<String>, val duplicate: Boolean)
    data class Completion(val achievementId: String, val eventId: UUID, val requirement: String, val target: String?, val amount: Int)
    data class CompletionCheck(val actor: UUID, val decision: GuildAchievementIpEligibility.Decision)

    data class SaleCredit(val guild: UUID, val sale: UUID, val membershipMillis: Long)

    init {
        storage.connection.executeUpdate("""CREATE TABLE IF NOT EXISTS guild_market_advancement_delivery (
            player_id VARCHAR(36) NOT NULL, guild_id VARCHAR(36)${guildIdCollation(storage)} NOT NULL, sale_id VARCHAR(36) NOT NULL,
            membership_ms BIGINT NOT NULL, PRIMARY KEY (player_id, guild_id, sale_id),
            FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
        )""".trimIndent())
        storage.connection.executeUpdate("""CREATE TABLE IF NOT EXISTS guild_achievement_progress_v1 (
            guild_id VARCHAR(36) PRIMARY KEY, progress_json TEXT NOT NULL, completed_json TEXT NOT NULL
        )""".trimIndent())
        storage.connection.executeUpdate("""CREATE TABLE IF NOT EXISTS guild_achievement_events_v1 (
            guild_id VARCHAR(36) NOT NULL, event_id VARCHAR(36) NOT NULL,
            requirement VARCHAR(128) NOT NULL, target VARCHAR(128), amount INTEGER NOT NULL,
            PRIMARY KEY (guild_id, event_id)
        )""".trimIndent())
        storage.connection.executeUpdate("""CREATE TABLE IF NOT EXISTS guild_achievement_completions_v1 (
            guild_id VARCHAR(36) NOT NULL, achievement_id VARCHAR(128) NOT NULL, event_id VARCHAR(36) NOT NULL,
            PRIMARY KEY (guild_id, achievement_id)
        )""".trimIndent())
        storage.connection.executeUpdate("""CREATE TABLE IF NOT EXISTS guild_achievement_completion_checks_v1 (
            guild_id VARCHAR(36) NOT NULL, achievement_id VARCHAR(128) NOT NULL, event_id VARCHAR(36) NOT NULL,
            actor_id VARCHAR(36) NOT NULL, decision VARCHAR(16) NOT NULL,
            PRIMARY KEY (guild_id, achievement_id, actor_id)
        )""".trimIndent())
    }

    /** Inspection only: a caller-supplied decision is not independent proof of eligibility. */
    fun completionChecks(guild: UUID, achievement: String): List<CompletionCheck> {
        require(engine.definitions.any { it.id == achievement })
        return storage.connection.connection.use { c ->
            c.prepareStatement("""SELECT x.actor_id, x.decision FROM guild_achievement_completion_checks_v1 x
                JOIN guild_achievement_completions_v1 c ON c.guild_id = x.guild_id
                AND c.achievement_id = x.achievement_id AND c.event_id = x.event_id
                WHERE x.guild_id = ? AND x.achievement_id = ? ORDER BY x.actor_id""".trimIndent()).use { s ->
                s.setString(1, guild.toString()); s.setString(2, achievement)
                s.executeQuery().use { rows -> buildList {
                    while (rows.next()) {
                        val text = rows.getString(1)
                        val id = UUID.fromString(text)
                        require(id.toString() == text)
                        add(CompletionCheck(id, GuildAchievementIpEligibility.Decision.valueOf(rows.getString(2))))
                    }
                } }
            }
        }
    }

    /** Inspect durable completion records after a restart; these records cannot authorize an award. */
    fun completions(guild: UUID): List<Completion> = storage.connection.connection.use { c ->
        c.prepareStatement("""SELECT c.achievement_id, c.event_id, e.requirement, e.target, e.amount
            FROM guild_achievement_completions_v1 c
            LEFT JOIN guild_achievement_events_v1 e ON e.guild_id = c.guild_id AND e.event_id = c.event_id
            WHERE c.guild_id = ? ORDER BY c.achievement_id""".trimIndent()).use { statement ->
            statement.setString(1, guild.toString())
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        val id = rows.getString(1)
                        val node = requireNotNull(engine.definitions.find { it.id == id })
                        val eventText = rows.getString(2)
                        val event = UUID.fromString(eventText)
                        require(event.toString() == eventText)
                        val requirement = requireNotNull(rows.getString(3)) { "Completion event missing" }
                        val target = rows.getString(4)
                        val amount = rows.getInt(5)
                        require(node.requirement == requirement && amount > 0)
                        add(Completion(id, event, requirement, target, amount))
                    }
                }
            }
        }
    }

    fun applySale(guild: UUID, sale: UUID, recipients: Map<UUID, Long>): Result {
        val requirement = "GUILD_SHOP_SALE"
        val event = net.lumalyte.lg.domain.achievements.GuildAchievementEventId.fromSource(guild, requirement, sale)
        return applyEvent(guild, event, requirement, null, 1, emptyMap(), onAccepted = { c ->
            for ((player, membershipMillis) in recipients) {
                c.updateStatement("INSERT INTO guild_market_advancement_delivery VALUES (?, ?, ?, ?)",
                    player.toString(), guild.toString(), sale.toString(), membershipMillis)
            }
        }) { _, _ -> }
    }

    fun pendingSales(player: UUID): List<SaleCredit> = storage.connection.connection.use { c ->
        c.prepareStatement("""SELECT guild_id, sale_id, membership_ms FROM guild_market_advancement_delivery
            WHERE player_id = ? ORDER BY guild_id, sale_id LIMIT 16""".trimIndent()).use { s ->
            s.setString(1, player.toString())
            s.executeQuery().use { rows -> buildList {
                while (rows.next()) add(SaleCredit(UUID.fromString(rows.getString(1)),
                    UUID.fromString(rows.getString(2)), rows.getLong(3)))
            } }
        }
    }

    fun finishSale(player: UUID, credit: SaleCredit) {
        storage.connection.connection.use { c ->
            c.updateStatement("DELETE FROM guild_market_advancement_delivery WHERE player_id = ? AND guild_id = ? AND sale_id = ?",
                player.toString(), credit.guild.toString(), credit.sale.toString())
        }
    }

    /** Call exactly once per real guild event, not once per online recipient. */
    fun apply(guild: UUID, event: UUID, requirement: String, target: String?, amount: Int,
        checks: Map<UUID, GuildAchievementIpEligibility.Decision> = emptyMap()): Result {
        return applyEvent(guild, event, requirement, target, amount, checks) { _, _ -> }
    }

    internal fun applyEvent(guild: UUID, event: UUID, requirement: String, target: String?, amount: Int,
        checks: Map<UUID, GuildAchievementIpEligibility.Decision>,
        onAccepted: (Connection) -> Unit = {},
        onCompleted: (Connection, GuildAchievement) -> Unit): Result {
        require(requirement.matches(Regex("[A-Z0-9_]{1,128}")))
        require(requirement in requirements)
        require(target == null || target.length <= 128)
        require(amount > 0)
        val completionChecks = checks.toMap()
        return storage.connection.connection.use { c -> c.committingTransaction {
            if (maria) {
                checkNotNull(c.selectOne("SELECT id FROM guilds WHERE id = ? FOR UPDATE", guild.toString()) { it.getString(1) })
            } else {
                c.updateStatement("UPDATE guild_achievement_progress_v1 SET progress_json = progress_json WHERE 0")
                checkNotNull(c.selectOne("SELECT id FROM guilds WHERE id = ?", guild.toString()) { it.getString(1) })
            }
            val previous = c.selectOne("SELECT requirement, target, amount FROM guild_achievement_events_v1 WHERE guild_id = ? AND event_id = ?", guild.toString(), event.toString()) {
                Triple(it.getString(1), it.getString(2), it.getInt(3))
            }
            val stored = c.selectOne("SELECT progress_json, completed_json FROM guild_achievement_progress_v1 WHERE guild_id = ?", guild.toString()) {
                val counts = JSONObject(it.getString(1))
                val done = JSONArray(it.getString(2))
                AchievementRecord(counts.keySet().associateWith(counts::getInt), (0 until done.length()).map(done::getString).toSet())
            } ?: AchievementRecord()
            stored.progress.forEach { (id, count) ->
                val definition = requireNotNull(engine.definitions.find { it.id == id })
                require(count in 0..definition.goal)
            }
            require(stored.rewarded.all { id -> engine.definitions.any { it.id == id && stored.progress[id] == it.goal } })
            if (previous != null) {
                require(previous == Triple(requirement, target, amount)) { "Conflicting guild event replay" }
                return@committingTransaction Result(stored.progress, emptySet(), true)
            }
            val change = engine.advance(stored, requirement, target, amount)
            val progress = JSONObject(change.record.progress).toString()
            val completed = JSONArray(change.record.rewarded.sorted()).toString()
            val upsert = if (maria) "INSERT INTO guild_achievement_progress_v1 VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE progress_json = VALUES(progress_json), completed_json = VALUES(completed_json)"
                else "INSERT INTO guild_achievement_progress_v1 VALUES (?, ?, ?) ON CONFLICT(guild_id) DO UPDATE SET progress_json = excluded.progress_json, completed_json = excluded.completed_json"
            c.updateStatement(upsert, guild.toString(), progress, completed)
            c.updateStatement("INSERT INTO guild_achievement_events_v1 VALUES (?, ?, ?, ?, ?)", guild.toString(), event.toString(), requirement, target, amount)
            onAccepted(c)
            change.completed.forEach { node ->
                c.updateStatement("INSERT INTO guild_achievement_completions_v1 VALUES (?, ?, ?)", guild.toString(), node.id, event.toString())
                completionChecks.forEach { (actor, decision) ->
                    c.updateStatement("INSERT INTO guild_achievement_completion_checks_v1 VALUES (?, ?, ?, ?, ?)",
                        guild.toString(), node.id, event.toString(), actor.toString(), decision.name)
                }
                onCompleted(c, node)
            }
            Result(change.record.progress, change.completed.map { it.id }.toSet(), false)
        } }
    }
}
