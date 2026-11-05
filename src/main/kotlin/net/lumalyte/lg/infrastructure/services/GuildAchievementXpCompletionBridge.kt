package net.lumalyte.lg.infrastructure.services

import java.util.UUID
import net.lumalyte.lg.application.services.GuildAchievementXpCoordinator
import net.lumalyte.lg.domain.achievements.GuildAchievement
import org.bukkit.entity.Player

/** Prototype adapter; snapshot provider must represent trusted, current network sessions. */
internal class GuildAchievementXpCompletionBridge(
    private val coordinator: GuildAchievementXpCoordinator,
    private val snapshot: (UUID) -> GuildAchievementOnlineSnapshot.Snapshot?,
) : GuildAchievementXpCompletionPort {
    override fun prepare(player: Player, guildId: UUID, node: GuildAchievement): GuildAchievementXpCoordinator.Prepared {
        val current = runCatching { snapshot(guildId) }.getOrNull()
            ?: return GuildAchievementXpCoordinator.Prepared.Denied(GuildAchievementXpCoordinator.Result.UNKNOWN_ADDRESS)
        return coordinator.prepareOnline(guildId, player.uniqueId, node, true, current.members, current.online, current.fingerprints)
    }

    override fun deliver(intent: GuildAchievementXpCoordinator.Intent): GuildAchievementXpCoordinator.Result = coordinator.deliver(intent)
}
