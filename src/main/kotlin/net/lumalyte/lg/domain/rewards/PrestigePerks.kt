package net.lumalyte.lg.domain.rewards

import net.lumalyte.lg.config.PrestigePerksConfig
import net.lumalyte.lg.config.PrestigePerksConfigDefaults

/**
 * REQ-179 to REQ-181: the practical perks a guild has at a prestige tier. A `null`
 * tier means the tier could not be read, which gives no perk.
 */
class PrestigePerks(private val config: PrestigePerksConfig) {
    fun homeCountdownSeconds(tier: Int?): Int =
        if (reached(tier, config.quickHomeTier)) config.quickHomeSeconds else PrestigePerksConfigDefaults.STANDARD_COUNTDOWN

    fun allyHomeSlots(tier: Int?): Int = if (reached(tier, config.secondAllyHomeTier)) 2 else 1

    fun extraGuildStalls(tier: Int?): Int = if (reached(tier, config.extraStallTier)) 1 else 0

    /** Perks that start at exactly [tier], in the order menus list them. */
    fun unlockedAt(tier: Int): List<PrestigePerk> = listOfNotNull(
        PrestigePerk.QUICK_HOME.takeIf { tier == config.quickHomeTier },
        PrestigePerk.EXTRA_STALL.takeIf { tier == config.extraStallTier },
    )

    val quickHomeSeconds: Int get() = config.quickHomeSeconds

    private fun reached(tier: Int?, needed: Int): Boolean = tier != null && tier >= needed
}

enum class PrestigePerk { QUICK_HOME, EXTRA_STALL }
