package net.lumalyte.lg.domain.achievements

import java.util.UUID
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GuildAchievementIpEligibilityTest {
    private val actor = UUID.fromString("669803db-acb0-4d96-a24c-99ff1fda02ed")
    private val peer = UUID.fromString("3a6cfa52-5c73-4c26-a347-c843f0aba289")

    @Test fun `offline peer with matching known observation blocks guild xp`() {
        assertEquals(GuildAchievementIpEligibility.Decision.SHARED,
            GuildAchievementIpEligibility.decide(actor, setOf(actor, peer), mapOf(actor to "h1", peer to "h1")))
    }

    @Test fun `different known observations remain eligible`() {
        assertEquals(GuildAchievementIpEligibility.Decision.ELIGIBLE,
            GuildAchievementIpEligibility.decide(actor, setOf(actor, peer), mapOf(actor to "h1", peer to "h2")))
    }

    @Test fun `missing or invalid membership does not silently pass`() {
        assertEquals(GuildAchievementIpEligibility.Decision.UNKNOWN,
            GuildAchievementIpEligibility.decide(actor, setOf(actor, peer), mapOf(actor to "h1")))
        assertEquals(GuildAchievementIpEligibility.Decision.UNKNOWN,
            GuildAchievementIpEligibility.decide(actor, setOf(peer), mapOf(actor to "h1", peer to "h2")))
    }
}
