package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import java.net.InetAddress
import java.util.UUID
import net.lumalyte.lg.application.services.GuildAchievementXpCoordinator
import net.lumalyte.lg.domain.achievements.GuildAchievement
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertFalse

class GuildAchievementXpCompletionBridgeTest {
    private val guild = UUID.randomUUID()
    private val actor = UUID.randomUUID()
    private val offline = UUID.randomUUID()
    private val node = GuildAchievement("first_war", null, "First War", listOf("Win"), "WAR", null, 1, 50,
        emptyList(), "Combat", "SHIELD", "GOAL", 0f, 0f)

    @Test fun `only online peers affect eligibility and missing online addresses fail closed`() {
        val player = mockk<Player>()
        every { player.uniqueId } returns actor
        val coordinator = GuildAchievementXpCoordinator(listOf(node)) { _, _, _, _ -> GuildAchievementXpCoordinator.Result.AWARDED }
        var peerOnline = true
        var trusted = true
        val address = InetAddress.getByName("198.51.100.1")
        var peerAddress: InetAddress? = address
        val collector = GuildAchievementOnlineSnapshot({ trusted }, { setOf(actor, offline) },
            { if (peerOnline) mapOf(actor to address, offline to peerAddress) else mapOf(actor to address) },
            GuildAchievementAddressFingerprint(ByteArray(32) { 7 }))
        val bridge = GuildAchievementXpCompletionBridge(coordinator, collector::capture)
        assertEquals(GuildAchievementXpCoordinator.Prepared.Denied(GuildAchievementXpCoordinator.Result.SHARED_ADDRESS),
            bridge.prepare(player, guild, node))
        peerAddress = null
        assertEquals(GuildAchievementXpCoordinator.Prepared.Denied(GuildAchievementXpCoordinator.Result.UNKNOWN_ADDRESS),
            bridge.prepare(player, guild, node))
        peerOnline = false
        assertIs<GuildAchievementXpCoordinator.Prepared.Eligible>(bridge.prepare(player, guild, node))
        trusted = false
        assertEquals(GuildAchievementXpCoordinator.Prepared.Denied(GuildAchievementXpCoordinator.Result.UNKNOWN_ADDRESS),
            bridge.prepare(player, guild, node))
    }

    @Test fun `snapshot excludes unrelated sessions and never exposes addresses`() {
        val address = InetAddress.getByName("198.51.100.20")
        val stranger = UUID.randomUUID()
        val collector = GuildAchievementOnlineSnapshot({ true }, { setOf(actor, offline) },
            { mapOf(actor to address, stranger to address) }, GuildAchievementAddressFingerprint(ByteArray(32) { 9 }))
        val result = requireNotNull(collector.capture(guild))
        assertEquals(setOf(actor), result.online)
        assertEquals(setOf(actor), result.fingerprints.keys)
        assertFalse(result.toString().contains(address.hostAddress))
    }

    @Test fun `untrusted or unavailable network view produces no snapshot`() {
        val fingerprint = GuildAchievementAddressFingerprint(ByteArray(32) { 4 })
        assertNull(GuildAchievementOnlineSnapshot({ false }, { error("not called") }, { error("not called") }, fingerprint).capture(guild))
        assertNull(GuildAchievementOnlineSnapshot({ true }, { setOf(actor) }, { error("offline") }, fingerprint).capture(guild))
    }

    @Test fun `membership or trust changes during collection fail closed`() {
        val fingerprint = GuildAchievementAddressFingerprint(ByteArray(32) { 4 })
        val address = InetAddress.getByName("198.51.100.30")
        var reads = 0
        val changingRoster = GuildAchievementOnlineSnapshot({ true },
            { if (reads++ == 0) setOf(actor) else setOf(actor, offline) }, { mapOf(actor to address) }, fingerprint)
        assertNull(changingRoster.capture(guild))
        var trustedReads = 0
        val changingTrust = GuildAchievementOnlineSnapshot({ trustedReads++ == 0 },
            { setOf(actor) }, { mapOf(actor to address) }, fingerprint)
        assertNull(changingTrust.capture(guild))
    }
}
