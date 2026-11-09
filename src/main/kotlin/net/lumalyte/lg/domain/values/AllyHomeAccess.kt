package net.lumalyte.lg.domain.values

import java.util.UUID

/**
 * REQ-182: a host guild's own rules for its allied homes, on top of which allies
 * may visit. [visitorRanks] maps an allied guild to the ranks of that guild the
 * host admits; an ally with no entry has every rank admitted. [blockedPlayers]
 * are refused whatever their rank. Visitors still need "Use ally homes" in their
 * own guild; that check stays with the guild service.
 */
data class AllyHomeAccess(
    val visitorRanks: Map<UUID, Set<UUID>> = emptyMap(),
    val blockedPlayers: Set<UUID> = emptySet(),
) {
    fun admits(visitorGuildId: UUID, visitorRankId: UUID, playerId: UUID): Boolean =
        playerId !in blockedPlayers && (visitorRanks[visitorGuildId]?.contains(visitorRankId) ?: true)
}
