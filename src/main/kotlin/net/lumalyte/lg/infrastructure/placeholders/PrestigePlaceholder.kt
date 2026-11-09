package net.lumalyte.lg.infrastructure.placeholders

import net.lumalyte.lg.domain.rewards.PrestigeStanding
import net.lumalyte.lg.domain.values.PrestigeNumeral
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** REQ-155 formatting. An unreadable standing is blank, never a fabricated tier. */
object PrestigePlaceholder {
    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)

    /** Blank below tier I, unlike [PrestigeNumeral], so placeholder users can hide it. */
    fun roman(tier: Int): String = if (tier in 1..6) PrestigeNumeral.of(tier) else ""

    fun badge(tier: Int, badges: List<String>): String = if (tier <= 0) "" else badges.getOrElse(tier) { "" }

    fun value(identifier: String, standing: PrestigeStanding?, badges: List<String>, maxCount: Int): String {
        if (identifier == "guild_prestige_max") return maxCount.toString()
        if (standing == null) return ""
        return when (identifier) {
            "guild_prestige" -> standing.tier.toString()
            "guild_prestige_roman" -> roman(standing.tier)
            "guild_prestige_badge" -> badge(standing.tier, badges)
            "guild_prestige_first_tier" -> standing.firstTier.toString()
            "guild_prestige_last_date" -> standing.lastAppliedAt?.let(DATE::format) ?: ""
            else -> ""
        }
    }
}
