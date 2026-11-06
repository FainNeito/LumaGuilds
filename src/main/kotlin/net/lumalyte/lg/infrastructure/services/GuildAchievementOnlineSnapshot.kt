package net.lumalyte.lg.infrastructure.services

import java.net.InetAddress
import java.util.UUID

/** A caller must attest that local sessions and forwarded client addresses are trustworthy. */
internal class GuildAchievementOnlineSnapshot(
    private val trustedNetworkView: () -> Boolean,
    private val members: (UUID) -> Set<UUID>,
    private val sessions: () -> Map<UUID, InetAddress?>,
    private val fingerprint: GuildAchievementAddressFingerprint,
) {
    data class Snapshot(val members: Set<UUID>, val online: Set<UUID>, val fingerprints: Map<UUID, String?>)

    /** Raw addresses are converted immediately and are not retained in the result. */
    fun capture(guildId: UUID): Snapshot? = runCatching {
        if (!trustedNetworkView()) return null
        val roster = members(guildId).toSet()
        val onlineSessions = sessions().toMap().filterKeys { it in roster }
        val fingerprints = onlineSessions.mapValues { (_, address) -> address?.let(fingerprint::fingerprint) }
        // The roster may be backed by data outside the server thread. Do not
        // authorize an award from a snapshot spanning a membership change.
        if (members(guildId).toSet() != roster || !trustedNetworkView()) return null
        Snapshot(roster, onlineSessions.keys.toSet(), fingerprints)
    }.getOrNull()
}
