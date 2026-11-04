package net.lumalyte.lg.infrastructure.services

import java.util.UUID
import net.lumalyte.lg.application.services.GuildAchievementXpCoordinator
import net.lumalyte.lg.domain.achievements.GuildAchievement
import org.bukkit.entity.Player

/** Optional bridge; implementations must validate the completion-time membership and address snapshot. */
internal interface GuildAchievementXpCompletionPort {
    fun prepare(player: Player, guildId: UUID, node: GuildAchievement): GuildAchievementXpCoordinator.Prepared
    fun deliver(intent: GuildAchievementXpCoordinator.Intent): GuildAchievementXpCoordinator.Result
}
