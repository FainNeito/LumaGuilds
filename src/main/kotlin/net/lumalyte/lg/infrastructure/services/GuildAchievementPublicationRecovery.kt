package net.lumalyte.lg.infrastructure.services

import java.util.concurrent.atomic.AtomicBoolean
import net.lumalyte.lg.infrastructure.persistence.guilds.ExperienceAwardRepositorySQL

/** Unbound worker; the publisher must deduplicate effects by transaction ID. */
internal class GuildAchievementPublicationRecovery(
    private val repository: ExperienceAwardRepositorySQL,
    private val publish: (ExperienceAwardRepositorySQL.AchievementPublication) -> Boolean,
) {
    private val running = AtomicBoolean(false)

    /** Failed or ambiguous publication remains pending. Concurrent runs on this worker never overlap. */
    fun recover(limit: Int): Int {
        require(limit in 1..100)
        if (!running.compareAndSet(false, true)) return 0
        return try {
            var acknowledged = 0
            repository.pendingAchievementPublications(limit).forEach { item ->
                if (runCatching { publish(item) }.getOrDefault(false) &&
                    runCatching { repository.acknowledgeAchievementPublication(item.transactionId) }.getOrDefault(false)) {
                    acknowledged++
                }
            }
            acknowledged
        } finally {
            running.set(false)
        }
    }
}
