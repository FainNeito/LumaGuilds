package net.lumalyte.lg.infrastructure.services

import java.util.UUID
import net.lumalyte.lg.application.services.GuildAchievementXpCoordinator
import net.lumalyte.lg.domain.achievements.GuildAchievementXpPolicy
import org.json.JSONArray
import org.json.JSONObject

/** Strict codec for completion-time intents; never serializes IP information. */
internal object GuildAchievementXpOutboxCodec {
    private const val MAX_PENDING = 256

    fun encode(intents: List<GuildAchievementXpCoordinator.Intent>): JSONArray {
        validate(intents)
        return JSONArray(intents.map {
            JSONObject().put("guild", it.guildId.toString()).put("achievement", it.achievementId)
                .put("amount", it.amount).put("transaction", it.transactionId.toString())
        })
    }

    fun decode(array: JSONArray): List<GuildAchievementXpCoordinator.Intent> {
        require(array.length() <= MAX_PENDING)
        val intents = (0 until array.length()).map { index ->
            val value = array.getJSONObject(index)
            require(value.keySet() == setOf("guild", "achievement", "amount", "transaction"))
            require(value.get("amount") is Int)
            val guild = value.getString("guild")
            val transaction = value.getString("transaction")
            require(UUID.fromString(guild).toString() == guild && UUID.fromString(transaction).toString() == transaction)
            GuildAchievementXpCoordinator.Intent(
                UUID.fromString(guild), value.getString("achievement"), value.getInt("amount"), UUID.fromString(transaction),
            )
        }
        validate(intents)
        return intents
    }

    private fun validate(intents: List<GuildAchievementXpCoordinator.Intent>) {
        require(intents.size <= MAX_PENDING)
        require(intents.map { it.transactionId }.distinct().size == intents.size)
        intents.forEach {
            require(it.amount in setOf(25, 50, 100))
            require(it.transactionId == GuildAchievementXpPolicy.transactionId(it.guildId, it.achievementId))
        }
    }
}
