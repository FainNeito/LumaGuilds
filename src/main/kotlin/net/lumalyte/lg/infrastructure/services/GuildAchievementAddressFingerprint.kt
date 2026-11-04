package net.lumalyte.lg.infrastructure.services

import java.net.InetAddress
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Produces a comparison token without retaining or logging the source address. */
internal class GuildAchievementAddressFingerprint(key: ByteArray) {
    private val secret = key.copyOf().also { require(it.size >= 32) }

    /** Public key-generation marker; different secrets cannot be mixed silently. */
    val keyId: String = mac("lumaguilds:achievement-ip:key-id:v1".toByteArray(Charsets.UTF_8)).take(32)

    fun fingerprint(address: InetAddress): String? {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isMulticastAddress) return null
        val bytes = canonicalAddress(address.address)
        return mac("lumaguilds:achievement-ip:v1:".toByteArray(Charsets.UTF_8) + bytes)
    }

    private fun mac(input: ByteArray): String {
        val digest = Mac.getInstance("HmacSHA256")
        digest.init(SecretKeySpec(secret, "HmacSHA256"))
        return HexFormat.of().formatHex(digest.doFinal(input))
    }

    private fun canonicalAddress(bytes: ByteArray): ByteArray {
        require(bytes.size == 4 || bytes.size == 16)
        val mapped = bytes.size == 16 && bytes.take(10).all { it == 0.toByte() } &&
            bytes[10] == 0xff.toByte() && bytes[11] == 0xff.toByte()
        return if (mapped) bytes.copyOfRange(12, 16) else bytes
    }
}
