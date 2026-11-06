package net.lumalyte.lg.domain.achievements

import java.util.UUID
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class GuildAchievementXpPolicyTest {
    private fun node(id: String, frame: String) = GuildAchievement(
        id, null, id, listOf(id), "ROOT", null, 1, 0, emptyList(), "Test", "SHIELD", frame, 0f, 0f,
    )

    @Test fun `root and unknown tiers do not receive a payout`() {
        assertEquals(0, GuildAchievementXpPolicy.reward(node("guilds_root", "TASK")))
        assertEquals(0, GuildAchievementXpPolicy.reward(node("other", "UNKNOWN")))
    }

    @Test fun `milestone payouts remain modest against bundled quest defaults`() {
        assertEquals(25, GuildAchievementXpPolicy.reward(node("task", "TASK")))
        assertEquals(50, GuildAchievementXpPolicy.reward(node("goal", "GOAL")))
        assertEquals(100, GuildAchievementXpPolicy.reward(node("challenge", "CHALLENGE")))
    }

    @Test fun `receipt belongs to guild and achievement independent of member`() {
        val guild = UUID.fromString("8ee82c27-7a7c-40c2-9a4b-5c9c347be76e")
        val other = UUID.fromString("74cf4db0-99f8-44e6-abcc-1566713e37b8")
        val receipt = GuildAchievementXpPolicy.transactionId(guild, "first_war")
        assertEquals(receipt, GuildAchievementXpPolicy.transactionId(guild, "first_war"))
        assertNotEquals(receipt, GuildAchievementXpPolicy.transactionId(other, "first_war"))
        assertNotEquals(receipt, GuildAchievementXpPolicy.transactionId(guild, "next_war"))
        assertFailsWith<IllegalArgumentException> { GuildAchievementXpPolicy.transactionId(guild, "../bad") }
    }
}
