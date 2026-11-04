package net.lumalyte.lg.domain.achievements

import java.util.UUID
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    @Test fun `older offline peer address still blocks and unknown history fails closed`() {
        val members = setOf(actor, peer)
        assertEquals(GuildAchievementIpEligibility.Decision.SHARED,
            GuildAchievementIpEligibility.decideWithHistory(actor, members, "old", mapOf(peer to GuildAchievementIpEligibility.PeerHistory(setOf("old", "new"), true))))
        assertEquals(GuildAchievementIpEligibility.Decision.ELIGIBLE,
            GuildAchievementIpEligibility.decideWithHistory(actor, members, "other", mapOf(peer to GuildAchievementIpEligibility.PeerHistory(setOf("old", "new"), true))))
        assertEquals(GuildAchievementIpEligibility.Decision.UNKNOWN,
            GuildAchievementIpEligibility.decideWithHistory(actor, members, "other", emptyMap()))
        assertEquals(GuildAchievementIpEligibility.Decision.UNKNOWN,
            GuildAchievementIpEligibility.decideWithHistory(actor, members, "other", mapOf(peer to GuildAchievementIpEligibility.PeerHistory(setOf(""), true))))
        assertEquals(GuildAchievementIpEligibility.Decision.UNKNOWN,
            GuildAchievementIpEligibility.decideWithHistory(actor, members, "other", mapOf(peer to GuildAchievementIpEligibility.PeerHistory(setOf("old"), false))))
    }

    @Test fun `shared participants are blocked without excluding unrelated participant`() {
        val third = UUID.randomUUID()
        val members = setOf(actor, peer, third)
        val history = mapOf(actor to GuildAchievementIpEligibility.PeerHistory(setOf("same"), true),
            peer to GuildAchievementIpEligibility.PeerHistory(setOf("same"), true),
            third to GuildAchievementIpEligibility.PeerHistory(setOf("distinct"), true))
        val decisions = GuildAchievementIpEligibility.decideParticipants(members, members,
            mapOf(actor to "same", peer to "same", third to "distinct"), history)
        assertEquals(GuildAchievementIpEligibility.Decision.SHARED, decisions[actor])
        assertEquals(GuildAchievementIpEligibility.Decision.SHARED, decisions[peer])
        assertEquals(GuildAchievementIpEligibility.Decision.ELIGIBLE, decisions[third])
        assertFailsWith<IllegalArgumentException> {
            GuildAchievementIpEligibility.decideParticipants(setOf(UUID.randomUUID()), members, emptyMap(), history)
        }
    }
}
