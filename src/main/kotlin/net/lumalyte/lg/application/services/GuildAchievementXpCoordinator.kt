package net.lumalyte.lg.application.services

import java.util.UUID
import net.lumalyte.lg.domain.achievements.GuildAchievement
import net.lumalyte.lg.domain.achievements.GuildAchievementIpEligibility
import net.lumalyte.lg.domain.achievements.GuildAchievementXpPolicy

/** Receives a trusted completion-time snapshot; the caller owns its durable delivery. */
internal class GuildAchievementXpCoordinator(nodes: List<GuildAchievement>, private val sink: Sink) {
    private val definitions = nodes.associateBy { it.id }.also { require(it.size == nodes.size) }
    fun interface Sink {
        fun award(guildId: UUID, achievementId: String, amount: Int, transactionId: UUID): Result
    }

    enum class Result { AWARDED, DUPLICATE, SHARED_ADDRESS, UNKNOWN_ADDRESS, HISTORICAL, NO_REWARD, UNAVAILABLE }

    data class Intent(val guildId: UUID, val achievementId: String, val amount: Int, val transactionId: UUID)

    sealed interface Prepared {
        data class Eligible(val intent: Intent) : Prepared
        data class Denied(val result: Result) : Prepared
    }

    fun complete(
        guildId: UUID,
        actor: UUID,
        node: GuildAchievement,
        isNewEvent: Boolean,
        members: Set<UUID>,
        observations: Map<UUID, String?>,
    ): Result {
        return when (val prepared = prepare(guildId, actor, node, isNewEvent, members, observations)) {
            is Prepared.Denied -> prepared.result
            is Prepared.Eligible -> deliver(prepared.intent)
        }
    }

    /** Prepare while the completion-time membership and address snapshot is still authoritative. */
    fun prepare(
        guildId: UUID,
        actor: UUID,
        node: GuildAchievement,
        isNewEvent: Boolean,
        members: Set<UUID>,
        observations: Map<UUID, String?>,
    ): Prepared {
        if (!isNewEvent) return Prepared.Denied(Result.HISTORICAL)
        require(definitions[node.id] == node)
        val amount = GuildAchievementXpPolicy.reward(node)
        if (amount == 0) return Prepared.Denied(Result.NO_REWARD)
        when (GuildAchievementIpEligibility.decide(actor, members, observations)) {
            GuildAchievementIpEligibility.Decision.SHARED -> return Prepared.Denied(Result.SHARED_ADDRESS)
            GuildAchievementIpEligibility.Decision.UNKNOWN -> return Prepared.Denied(Result.UNKNOWN_ADDRESS)
            GuildAchievementIpEligibility.Decision.ELIGIBLE -> Unit
        }
        return Prepared.Eligible(Intent(guildId, node.id, amount, GuildAchievementXpPolicy.transactionId(guildId, node.id)))
    }

    /** Delivery can be retried only from the original durable, completion-time intent. */
    fun deliver(intent: Intent): Result {
        val node = requireNotNull(definitions[intent.achievementId])
        require(intent.amount > 0 && intent.amount == GuildAchievementXpPolicy.reward(node))
        require(intent.transactionId == GuildAchievementXpPolicy.transactionId(intent.guildId, intent.achievementId))
        return sink.award(intent.guildId, intent.achievementId, intent.amount, intent.transactionId)
    }
}
