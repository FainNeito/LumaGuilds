package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import java.time.Instant
import java.util.UUID
import net.lumalyte.lg.application.services.GuildAchievementXpCoordinator
import net.lumalyte.lg.domain.achievements.GuildAchievement
import net.lumalyte.lg.domain.achievements.GuildAchievementIpEligibility.PeerHistory
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildAchievementAddressRepositorySQL.Observation
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GuildAchievementXpCompletionBridgeTest {
    private val guild = UUID.randomUUID()
    private val actor = UUID.randomUUID()
    private val offline = UUID.randomUUID()
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val node = GuildAchievement("first_war", null, "First War", listOf("Win"), "WAR", null, 1, 50,
        emptyList(), "Combat", "SHIELD", "GOAL", 0f, 0f)

    @Test fun `offline historical match denies payout and incomplete history fails closed`() {
        val player = mockk<Player>()
        every { player.uniqueId } returns actor
        val coordinator = GuildAchievementXpCoordinator(listOf(node)) { _, _, _, _ -> GuildAchievementXpCoordinator.Result.AWARDED }
        var current = Observation("same", now)
        var history = PeerHistory(setOf("same", "newer"), complete = true)
        val bridge = GuildAchievementXpCompletionBridge(coordinator, { setOf(actor, offline) },
            { if (it == actor) current else null }, { if (it == offline) history else null }, Duration.ofDays(30)) { now }
        assertEquals(GuildAchievementXpCoordinator.Prepared.Denied(GuildAchievementXpCoordinator.Result.SHARED_ADDRESS),
            bridge.prepare(player, guild, node))
        history = PeerHistory(setOf("other"), complete = false)
        assertEquals(GuildAchievementXpCoordinator.Prepared.Denied(GuildAchievementXpCoordinator.Result.UNKNOWN_ADDRESS),
            bridge.prepare(player, guild, node))
        history = PeerHistory(setOf("other"), complete = true)
        assertIs<GuildAchievementXpCoordinator.Prepared.Eligible>(bridge.prepare(player, guild, node))
        current = Observation("same", now.minus(Duration.ofDays(31)))
        assertEquals(GuildAchievementXpCoordinator.Prepared.Denied(GuildAchievementXpCoordinator.Result.UNKNOWN_ADDRESS),
            bridge.prepare(player, guild, node))
    }
}
