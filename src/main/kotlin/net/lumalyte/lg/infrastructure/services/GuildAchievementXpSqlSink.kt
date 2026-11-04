package net.lumalyte.lg.infrastructure.services

import java.time.Instant
import java.util.UUID
import net.lumalyte.lg.application.services.GuildAchievementXpCoordinator
import net.lumalyte.lg.domain.entities.ExperienceAwardRequest
import net.lumalyte.lg.domain.entities.ExperienceAwardResult
import net.lumalyte.lg.domain.values.ExperienceSource
import net.lumalyte.lg.infrastructure.persistence.guilds.ExperienceAwardRepositorySQL

/** Prototype adapter: caller is responsible for durable event delivery and level-up publication. */
internal class GuildAchievementXpSqlSink(
    private val repository: ExperienceAwardRepositorySQL,
    private val now: () -> Instant = Instant::now,
) : GuildAchievementXpCoordinator.Sink {
    override fun award(guildId: UUID, achievementId: String, amount: Int, transactionId: UUID): GuildAchievementXpCoordinator.Result {
        val request = ExperienceAwardRequest(guildId, null, ExperienceSource.ACHIEVEMENT, amount, now(), transactionId = transactionId)
        return when (repository.awardAchievementAtomically(request, achievementId, amount)) {
            is ExperienceAwardResult.Awarded -> GuildAchievementXpCoordinator.Result.AWARDED
            ExperienceAwardResult.Duplicate -> GuildAchievementXpCoordinator.Result.DUPLICATE
            is ExperienceAwardResult.NoAllowance, is ExperienceAwardResult.Rejected -> GuildAchievementXpCoordinator.Result.UNAVAILABLE
        }
    }
}
