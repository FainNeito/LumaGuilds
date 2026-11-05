package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.domain.achievements.GuildAchievementXpPolicy
import net.lumalyte.lg.domain.entities.ExperienceAwardRequest
import net.lumalyte.lg.domain.values.ExperienceSource
import net.lumalyte.lg.infrastructure.persistence.guilds.ExperienceAwardRepositorySQL
import java.time.Instant
import java.util.UUID
import net.lumalyte.lg.domain.achievements.GuildAchievementEventId
import net.lumalyte.lg.domain.achievements.GuildAchievementIpEligibility
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildAchievementProgressRepositorySQL

/** Unbound prototype: caller must supply an authenticated event and a trustworthy snapshot. */
internal class GuildAchievementGuildEventProcessor(
    private val repository: GuildAchievementProgressRepositorySQL,
    private val cutover: Instant,
    private val awards: ExperienceAwardRepositorySQL? = null,
    private val now: () -> Instant = Instant::now,
) {
    data class Event(
        val guild: UUID, val source: UUID, val occurredAt: Instant, val requirement: String,
        val target: String?, val amount: Int, val members: Set<UUID>, val participants: Set<UUID>,
        val current: Map<UUID, String?>,
        val online: Set<UUID>,
    )

    /** Historical observations are ignored; only eligible participants in a newly completed event can trigger its guild award. */
    fun process(event: Event): GuildAchievementProgressRepositorySQL.Result? {
        if (event.occurredAt < cutover) return null
        require(!event.occurredAt.isAfter(now())) { "Guild event is in the future" }
        require(event.participants.isNotEmpty())
        val decisions = GuildAchievementIpEligibility.decideOnlineParticipants(
            event.participants.toSet(), event.members.toSet(), event.online.toSet(), event.current.toMap(),
        )
        val id = GuildAchievementEventId.fromSource(event.guild, event.requirement, event.source)
        return repository.applyEvent(event.guild, id, event.requirement, event.target, event.amount, decisions) { connection, node ->
            val amount = GuildAchievementXpPolicy.reward(node)
            if (awards != null && amount > 0 && GuildAchievementIpEligibility.Decision.ELIGIBLE in decisions.values) {
                val receipt = GuildAchievementXpPolicy.transactionId(event.guild, node.id)
                val request = ExperienceAwardRequest(event.guild, null, ExperienceSource.ACHIEVEMENT, amount,
                    event.occurredAt, transactionId = receipt)
                awards.awardAchievementInTransaction(connection, request, node.id, amount)
            }
        }
    }
}
