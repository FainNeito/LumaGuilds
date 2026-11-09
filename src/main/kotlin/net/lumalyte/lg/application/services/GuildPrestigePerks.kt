package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.rewards.PrestigePerks
import java.util.UUID

/**
 * REQ-179 to REQ-181: a guild's prestige perks from its cached standing. Any
 * failure reads as "no perk", so a database hiccup never blocks a teleport.
 */
class GuildPrestigePerks(
    private val standings: GuildPrestigeStandings,
    private val members: MemberService,
    private val perks: () -> PrestigePerks,
) {
    /** The perk table as configured now, for menus that describe a tier. */
    fun forConfig(): PrestigePerks = perks()

    fun homeCountdownSeconds(playerId: UUID): Int = perks().homeCountdownSeconds(playerTier(playerId))

    fun allyHomeSlots(guildId: UUID): Int = perks().allyHomeSlots(tier(guildId))

    fun extraGuildStalls(guildId: UUID): Int = perks().extraGuildStalls(tier(guildId))

    private fun playerTier(playerId: UUID): Int? =
        runCatching { members.getPlayerGuilds(playerId).firstOrNull() }.getOrNull()?.let(::tier)

    private fun tier(guildId: UUID): Int? = runCatching { standings.standing(guildId)?.tier }.getOrNull()
}
