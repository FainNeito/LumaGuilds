package net.lumalyte.lg.infrastructure.services

import java.net.InetAddress
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class GuildAchievementAddressFingerprintTest {
    private fun address(vararg bytes: Int) = InetAddress.getByAddress(bytes.map(Int::toByte).toByteArray())

    @Test fun `same address is comparable but keys and addresses are separated`() {
        val a = GuildAchievementAddressFingerprint(ByteArray(32) { 1 })
        val b = GuildAchievementAddressFingerprint(ByteArray(32) { 2 })
        val ip = address(192, 0, 2, 1)
        assertEquals(a.fingerprint(ip), a.fingerprint(ip))
        assertEquals(a.keyId, GuildAchievementAddressFingerprint(ByteArray(32) { 1 }).keyId)
        assertNotEquals(a.keyId, b.keyId)
        assertNotEquals(a.fingerprint(ip), b.fingerprint(ip))
        assertNotEquals(a.fingerprint(ip), a.fingerprint(address(192, 0, 2, 2)))
        assertEquals(64, a.fingerprint(ip)?.length)
    }

    @Test fun `local placeholder addresses do not count as verified client addresses`() {
        val policy = GuildAchievementAddressFingerprint(ByteArray(32) { 1 })
        assertNull(policy.fingerprint(address(127, 0, 0, 1)))
        assertNull(policy.fingerprint(address(0, 0, 0, 0)))
    }

    @Test fun `IPv4 mapped IPv6 is equivalent to the IPv4 address`() {
        val policy = GuildAchievementAddressFingerprint(ByteArray(32) { 1 })
        val mapped = InetAddress.getByAddress(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, -1, -64, 0, 2, 1))
        assertEquals(policy.fingerprint(address(192, 0, 2, 1)), policy.fingerprint(mapped))
    }

    @Test fun `short key is rejected`() {
        assertFailsWith<IllegalArgumentException> { GuildAchievementAddressFingerprint(ByteArray(31)) }
    }
}
