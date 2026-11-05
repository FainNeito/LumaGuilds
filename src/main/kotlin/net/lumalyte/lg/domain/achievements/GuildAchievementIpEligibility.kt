package net.lumalyte.lg.domain.achievements

import java.util.UUID

/** Callers must provide trusted, prevalidated opaque observations, not raw IP addresses. */
object GuildAchievementIpEligibility {
    enum class Decision { ELIGIBLE, SHARED, UNKNOWN }
    data class PeerHistory(val observations: Set<String>, val complete: Boolean)

    /** The membership, online roster and observations must be captured at completion time. */
    fun decideOnline(actor: UUID, members: Set<UUID>, online: Set<UUID>, observations: Map<UUID, String?>): Decision {
        if (actor !in members || actor !in online || !members.containsAll(online)) return Decision.UNKNOWN
        return decide(actor, online, observations)
    }

    fun decideOnlineParticipants(participants: Set<UUID>, members: Set<UUID>, online: Set<UUID>,
        observations: Map<UUID, String?>): Map<UUID, Decision> {
        require(participants.all { it in members })
        return participants.associateWith { decideOnline(it, members, online, observations) }
    }

    fun decide(actor: UUID, members: Set<UUID>, observations: Map<UUID, String?>): Decision {
        if (actor !in members) return Decision.UNKNOWN
        val fingerprint = observations[actor]?.takeIf { it.isNotBlank() } ?: return Decision.UNKNOWN
        val peers = members - actor
        if (peers.any { observations[it]?.takeIf(String::isNotBlank) == fingerprint }) return Decision.SHARED
        if (peers.any { observations[it].isNullOrBlank() }) return Decision.UNKNOWN
        return Decision.ELIGIBLE
    }

    /** Compare the actor's current trusted address with every retained peer observation.
     * A missing peer history cannot establish that the actor is unique. */
    fun decideWithHistory(actor: UUID, members: Set<UUID>, current: String?, peerHistory: Map<UUID, PeerHistory?>): Decision {
        if (actor !in members || current.isNullOrBlank()) return Decision.UNKNOWN
        val peers = members - actor
        if (peers.any { current in peerHistory[it]?.observations.orEmpty() }) return Decision.SHARED
        if (peers.any { peerHistory[it]?.let { history -> !history.complete || history.observations.isEmpty() || history.observations.any(String::isBlank) } != false }) return Decision.UNKNOWN
        return Decision.ELIGIBLE
    }

    /** Evaluate actual participants separately so a shared address does not block an unrelated member. */
    fun decideParticipants(participants: Set<UUID>, members: Set<UUID>, current: Map<UUID, String?>,
        peerHistory: Map<UUID, PeerHistory?>): Map<UUID, Decision> {
        require(participants.all { it in members })
        return participants.associateWith { decideWithHistory(it, members, current[it], peerHistory) }
    }
}
