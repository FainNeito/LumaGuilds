package net.lumalyte.lg.infrastructure.services

import java.time.Duration
import java.time.Instant
import java.util.UUID
import net.lumalyte.lg.application.services.GuildAchievementXpCoordinator
import net.lumalyte.lg.domain.achievements.GuildAchievement
import net.lumalyte.lg.domain.achievements.GuildAchievementIpEligibility
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildAchievementAddressRepositorySQL
import org.bukkit.entity.Player

/** Prototype adapter; callers must supply verified client-address observations from the active key. */
internal class GuildAchievementXpCompletionBridge(
    private val coordinator: GuildAchievementXpCoordinator,
    private val members: (UUID) -> Set<UUID>,
    private val observations: (UUID) -> GuildAchievementAddressRepositorySQL.Observation?,
    private val histories: (UUID) -> GuildAchievementIpEligibility.PeerHistory?,
    private val maxAge: Duration,
    private val now: () -> Instant = Instant::now,
) : GuildAchievementXpCompletionPort {
    init { require(!maxAge.isNegative && !maxAge.isZero) }

    override fun prepare(player: Player, guildId: UUID, node: GuildAchievement): GuildAchievementXpCoordinator.Prepared {
        val at = now()
        val guildMembers = members(guildId)
        val actor = player.uniqueId
        val current = observations(actor)?.takeIf {
            !it.observedAt.isAfter(at) && Duration.between(it.observedAt, at) <= maxAge
        }?.fingerprint
        val peerHistories = (guildMembers - actor).associateWith(histories)
        return coordinator.prepareWithHistory(guildId, actor, node, true, guildMembers, current, peerHistories)
    }

    override fun deliver(intent: GuildAchievementXpCoordinator.Intent): GuildAchievementXpCoordinator.Result = coordinator.deliver(intent)
}
