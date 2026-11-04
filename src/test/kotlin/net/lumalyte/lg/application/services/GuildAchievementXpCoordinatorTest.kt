package net.lumalyte.lg.application.services

import java.util.UUID
import net.lumalyte.lg.domain.achievements.GuildAchievement
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class GuildAchievementXpCoordinatorTest {
    private val guild = UUID.fromString("8ee82c27-7a7c-40c2-9a4b-5c9c347be76e")
    private val actor = UUID.fromString("669803db-acb0-4d96-a24c-99ff1fda02ed")
    private val peer = UUID.fromString("3a6cfa52-5c73-4c26-a347-c843f0aba289")
    private val node = GuildAchievement("first_war", null, "First War", listOf("Win"), "WAR", null, 1, 50,
        emptyList(), "Combat", "SHIELD", "GOAL", 0f, 0f)

    @Test fun `historical shared and unknown events never reach payout sink`() {
        var calls = 0
        val service = GuildAchievementXpCoordinator(listOf(node)) { _, _, _, _ -> calls++; GuildAchievementXpCoordinator.Result.AWARDED }
        val members = setOf(actor, peer)
        assertEquals(GuildAchievementXpCoordinator.Result.HISTORICAL,
            service.complete(guild, actor, node, false, members, mapOf(actor to "a", peer to "b")))
        assertEquals(GuildAchievementXpCoordinator.Result.SHARED_ADDRESS,
            service.complete(guild, actor, node, true, members, mapOf(actor to "a", peer to "a")))
        assertEquals(GuildAchievementXpCoordinator.Result.UNKNOWN_ADDRESS,
            service.complete(guild, actor, node, true, members, mapOf(actor to "a")))
        assertEquals(0, calls)
    }

    @Test fun `different eligible members submit the same one-time guild receipt`() {
        val seen = mutableSetOf<UUID>()
        val service = GuildAchievementXpCoordinator(listOf(node)) { _, _, amount, transaction ->
            assertEquals(50, amount)
            if (seen.add(transaction)) GuildAchievementXpCoordinator.Result.AWARDED else GuildAchievementXpCoordinator.Result.DUPLICATE
        }
        val members = setOf(actor, peer)
        val observations = mapOf(actor to "a", peer to "b")
        assertEquals(GuildAchievementXpCoordinator.Result.AWARDED, service.complete(guild, actor, node, true, members, observations))
        assertEquals(GuildAchievementXpCoordinator.Result.DUPLICATE, service.complete(guild, peer, node, true, members, observations))
    }

    @Test fun `retry uses original guild and rejects tampered pending rewards`() {
        var calls = 0
        val service = GuildAchievementXpCoordinator(listOf(node)) { actualGuild, _, amount, _ ->
            assertEquals(guild, actualGuild)
            assertEquals(50, amount)
            if (calls++ == 0) GuildAchievementXpCoordinator.Result.UNAVAILABLE else GuildAchievementXpCoordinator.Result.AWARDED
        }
        val prepared = assertIs<GuildAchievementXpCoordinator.Prepared.Eligible>(
            service.prepare(guild, actor, node, true, setOf(actor, peer), mapOf(actor to "a", peer to "b")),
        )
        assertEquals(GuildAchievementXpCoordinator.Result.UNAVAILABLE, service.deliver(prepared.intent))
        assertEquals(GuildAchievementXpCoordinator.Result.AWARDED, service.deliver(prepared.intent))
        assertFailsWith<IllegalArgumentException> { service.deliver(prepared.intent.copy(amount = 100)) }
        assertFailsWith<IllegalArgumentException> { service.deliver(prepared.intent.copy(guildId = UUID.randomUUID())) }
    }
}
