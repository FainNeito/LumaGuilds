package net.lumalyte.lg.application.services

import net.lumalyte.lg.config.PrestigeFanfareConfig
import net.lumalyte.lg.domain.rewards.FanfareAudience
import net.lumalyte.lg.domain.rewards.FanfareCue
import net.lumalyte.lg.domain.rewards.PrestigeCompletion
import net.lumalyte.lg.domain.rewards.PrestigeFanfareScore
import org.slf4j.LoggerFactory
import java.util.UUID

/** Where the fanfare plays; the Bukkit adapter maps this to the scheduler and players. */
interface FanfareStage {
    fun online(): Collection<UUID>
    fun isOnline(player: UUID): Boolean
    /** Runs [task] on the server thread [ticks] from now. */
    fun later(ticks: Int, task: () -> Unit)
    fun play(player: UUID, cue: FanfareCue)
    fun title(player: UUID, tier: Int, guildName: String)
}

/**
 * REQ-164: plays the prestige fanfare after each newly committed prestige. The
 * prestige service only notifies on a new commit, so a replayed receipt never
 * plays it again. Any failure is logged and contained.
 */
class PrestigeFanfare(
    private val settings: () -> PrestigeFanfareConfig,
    private val members: (UUID) -> Collection<UUID>,
    private val guildName: (UUID) -> String?,
    private val stage: FanfareStage,
) : GuildPrestigeNotifier {
    private val logger = LoggerFactory.getLogger(PrestigeFanfare::class.java)

    override fun prestiged(completion: PrestigeCompletion) {
        runCatching { start(completion) }
            .onFailure { logger.warn("Prestige fanfare failed for guild ${completion.guildId}", it) }
    }

    private fun start(completion: PrestigeCompletion) {
        val config = settings()
        if (!config.enabled) return
        val online = stage.online().toSet()
        val guildMembers = members(completion.guildId).toSet()
        val listening = guildMembers intersect online
        schedule(listening, PrestigeFanfareScore.cues(completion.tier, FanfareAudience.MEMBERS))
        if (config.memberTitle) {
            val name = guildName(completion.guildId)
            if (name != null) stage.later(0) {
                listening.filter(stage::isOnline).forEach { stage.title(it, completion.tier, name) }
            }
        }
        if (config.serverWide) {
            schedule(online - guildMembers, PrestigeFanfareScore.cues(completion.tier, FanfareAudience.SERVER))
        }
    }

    private fun schedule(players: Set<UUID>, cues: List<FanfareCue>) {
        if (players.isEmpty()) return
        cues.groupBy { it.tick }.toSortedMap().forEach { (tick, batch) ->
            stage.later(tick) {
                for (player in players) {
                    if (!stage.isOnline(player)) continue
                    batch.forEach { stage.play(player, it) }
                }
            }
        }
    }
}
