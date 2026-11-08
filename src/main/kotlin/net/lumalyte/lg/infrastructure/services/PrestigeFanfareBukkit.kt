package net.lumalyte.lg.infrastructure.services

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.title.Title
import net.lumalyte.lg.application.services.FanfareStage
import net.lumalyte.lg.domain.rewards.FanfareCue
import org.bukkit.Bukkit
import org.bukkit.SoundCategory
import org.bukkit.plugin.Plugin
import java.time.Duration
import java.util.UUID

/** REQ-164: plays fanfare cues to players with the Bukkit scheduler. */
class PrestigeFanfareBukkit(private val plugin: Plugin, private val lang: LangService) : FanfareStage {
    override fun online(): Collection<UUID> = Bukkit.getOnlinePlayers().map { it.uniqueId }

    override fun isOnline(player: UUID): Boolean = Bukkit.getPlayer(player)?.isOnline == true

    override fun later(ticks: Int, task: () -> Unit) {
        if (ticks <= 0) Bukkit.getScheduler().runTask(plugin, Runnable { task() })
        else Bukkit.getScheduler().runTaskLater(plugin, Runnable { task() }, ticks.toLong())
    }

    override fun play(player: UUID, cue: FanfareCue) {
        val target = Bukkit.getPlayer(player) ?: return
        target.playSound(target, cue.sound, SoundCategory.MASTER, cue.volume, cue.pitch)
    }

    override fun title(player: UUID, tier: Int, guildName: String) {
        val target = Bukkit.getPlayer(player) ?: return
        target.showTitle(
            Title.title(
                lang.msg("notification.progression.prestige.title", "tier" to ROMAN[tier]),
                lang.msg("notification.progression.prestige.subtitle", "guild" to guildName),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3500), Duration.ofMillis(1000)),
            )
        )
    }

    private companion object {
        val ROMAN = listOf("", "I", "II", "III", "IV", "V", "VI")
    }
}
