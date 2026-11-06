package net.lumalyte.lg.domain.achievements

import java.nio.charset.StandardCharsets
import java.util.UUID

/** Preliminary one-time guild award rules; delivery is not wired by this policy. */
object GuildAchievementXpPolicy {
    fun reward(node: GuildAchievement): Int = if (node.id == "guilds_root") 0 else when (node.frame) {
        "TASK" -> 25
        "GOAL" -> 50
        "CHALLENGE" -> 100
        else -> 0
    }

    /** Stable across members and restarts; separate guilds and achievements never share a receipt. */
    fun transactionId(guildId: UUID, achievementId: String): UUID {
        require(achievementId.matches(Regex("[a-z0-9_]+")))
        return UUID.nameUUIDFromBytes("lumaguilds:achievement-xp:v1:$guildId:$achievementId".toByteArray(StandardCharsets.UTF_8))
    }
}
