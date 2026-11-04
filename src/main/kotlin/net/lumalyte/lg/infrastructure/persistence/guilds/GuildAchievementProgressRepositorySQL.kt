package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import java.util.UUID
import net.lumalyte.lg.domain.achievements.AchievementRecord
import net.lumalyte.lg.domain.achievements.GuildAchievement
import net.lumalyte.lg.domain.achievements.GuildAchievementEngine
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

    init {
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
    }

    /** Call exactly once per real guild event, not once per online recipient. */
    fun apply(guild: UUID, event: UUID, requirement: String, target: String?, amount: Int): Result {
        require(requirement.matches(Regex("[A-Z0-9_]{1,128}")))
        require(requirement in requirements)
        require(target == null || target.length <= 128)
        require(amount > 0)
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
            change.completed.forEach { node ->
                c.updateStatement("INSERT INTO guild_achievement_completions_v1 VALUES (?, ?, ?)", guild.toString(), node.id, event.toString())
            }
            Result(change.record.progress, change.completed.map { it.id }.toSet(), false)
        } }
    }
}
