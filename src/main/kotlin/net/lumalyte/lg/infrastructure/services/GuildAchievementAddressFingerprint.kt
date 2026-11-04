package net.lumalyte.lg.infrastructure.services

import java.net.InetAddress
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Produces a comparison token without retaining or logging the source address. */
internal class GuildAchievementAddressFingerprint(key: ByteArray) {
    private val secret = key.copyOf().also { require(it.size >= 32) }

    fun fingerprint(address: InetAddress): String? {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isMulticastAddress) return null
        val bytes = canonicalAddress(address.address)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        mac.update("lumaguilds:achievement-ip:v1:".toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(mac.doFinal(bytes))
    }

    private fun canonicalAddress(bytes: ByteArray): ByteArray {
        require(bytes.size == 4 || bytes.size == 16)
        val mapped = bytes.size == 16 && bytes.take(10).all { it == 0.toByte() } &&
            bytes[10] == 0xff.toByte() && bytes[11] == 0xff.toByte()
        return if (mapped) bytes.copyOfRange(12, 16) else bytes
    }
}
