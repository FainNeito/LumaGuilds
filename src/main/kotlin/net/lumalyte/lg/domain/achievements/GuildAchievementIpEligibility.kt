package net.lumalyte.lg.domain.achievements

import java.util.UUID

/** Callers must provide trusted, prevalidated opaque observations, not raw IP addresses. */
object GuildAchievementIpEligibility {
    enum class Decision { ELIGIBLE, SHARED, UNKNOWN }

    fun decide(actor: UUID, members: Set<UUID>, observations: Map<UUID, String?>): Decision {
        if (actor !in members) return Decision.UNKNOWN
        val fingerprint = observations[actor]?.takeIf { it.isNotBlank() } ?: return Decision.UNKNOWN
        val peers = members - actor
        if (peers.any { observations[it]?.takeIf(String::isNotBlank) == fingerprint }) return Decision.SHARED
        if (peers.any { observations[it].isNullOrBlank() }) return Decision.UNKNOWN
        return Decision.ELIGIBLE
    }
}
