package net.lumalyte.lg.domain.rewards

import kotlin.math.pow
import kotlin.math.roundToInt

/** Who a fanfare is for: the prestiging guild's members, or everyone else online. */
enum class FanfareAudience { MEMBERS, SERVER }

/** One vanilla sound at [tick] (20 per second) after the fanfare starts. */
data class FanfareCue(val tick: Int, val sound: String, val volume: Float, val pitch: Float)

/**
 * REQ-164: the approved prestige fanfare (mockup v3), built only from vanilla
 * sounds. Members hear a horn call and bell toll, a rising bell arpeggio, a second
 * toll and the descending minor flute motif, resolving on a minor chord; Prestige VI
 * gets a longer legacy version. Everyone else hears a short distant horn and chord.
 */
object PrestigeFanfareScore {
    /** The opening horn's pitch for tiers I to VI. */
    private val TIER_PITCH = floatArrayOf(0.85f, 0.9f, 0.95f, 1.0f, 1.05f, 0.8f)
    private const val TIER = -1f

    /** Note-block pitch for semitone [n] (0 to 24, F# to F#). */
    private fun np(n: Int): Float = (2.0.pow((n - 12) / 12.0) * 1000).roundToInt() / 1000f

    private fun c(tick: Int, sound: String, volume: Float, pitch: Float) = FanfareCue(tick, sound, volume, pitch)

    private val MEMBERS = listOf(
        c(0, "item.goat_horn.sound.1", .6f, TIER), c(0, "block.bell.use", .55f, .5f), c(0, "block.note_block.bass", .8f, np(0)),
        c(8, "block.note_block.bell", .5f, np(12)), c(11, "block.note_block.bell", .5f, np(15)), c(14, "block.note_block.bell", .55f, np(19)),
        c(14, "block.beacon.power_select", .4f, .8f), c(17, "block.note_block.bell", .6f, np(24)),
        c(20, "block.bell.use", .65f, .5f), c(20, "block.note_block.bass", .8f, np(0)), c(20, "block.note_block.chime", .45f, np(24)),
        c(26, "block.note_block.flute", .5f, np(19)), c(30, "block.note_block.flute", .5f, np(15)),
        c(34, "block.note_block.flute", .55f, np(12)), c(34, "block.beacon.activate", .45f, .7f),
        c(42, "block.note_block.chime", .35f, np(12)), c(42, "block.note_block.chime", .35f, np(15)),
        c(42, "block.note_block.chime", .35f, np(19)), c(42, "block.bell.resonate", .35f, .5f),
    )

    private val LEGACY = listOf(
        c(0, "item.goat_horn.sound.0", .7f, TIER), c(0, "block.bell.use", .6f, .5f), c(0, "block.note_block.bass", .8f, np(0)),
        c(14, "item.goat_horn.sound.1", .6f, .9f),
        c(22, "block.note_block.bell", .5f, np(12)), c(25, "block.note_block.bell", .55f, np(15)), c(28, "block.note_block.bell", .6f, np(19)),
        c(28, "block.beacon.power_select", .5f, .7f), c(31, "block.note_block.bell", .65f, np(24)),
        c(34, "block.bell.use", .75f, .5f), c(34, "block.bell.resonate", .5f, .5f), c(34, "block.note_block.bass", .85f, np(0)),
        c(34, "block.note_block.chime", .5f, np(24)), c(34, "entity.firework_rocket.large_blast", .35f, .8f),
        c(42, "block.note_block.flute", .55f, np(19)), c(46, "block.note_block.flute", .55f, np(15)), c(50, "block.note_block.flute", .6f, np(12)),
        c(50, "block.end_gateway.spawn", .4f, .8f),
        c(54, "ui.toast.challenge_complete", .55f, .7f),
        c(62, "block.beacon.activate", .6f, .6f),
        c(66, "block.note_block.chime", .4f, np(12)), c(66, "block.note_block.chime", .4f, np(15)),
        c(66, "block.note_block.chime", .4f, np(19)), c(66, "block.note_block.chime", .4f, np(24)),
        c(66, "block.note_block.bass", .8f, np(0)), c(66, "block.bell.resonate", .4f, .5f),
    )

    private val SERVER = listOf(
        c(0, "item.goat_horn.sound.1", .3f, .9f), c(0, "block.bell.use", .3f, .5f),
        c(20, "block.note_block.chime", .25f, np(12)), c(20, "block.note_block.chime", .25f, np(15)),
        c(20, "block.note_block.chime", .25f, np(19)), c(20, "block.bell.resonate", .25f, .5f),
    )

    fun cues(tier: Int, audience: FanfareAudience): List<FanfareCue> {
        require(tier in 1..6) { "Prestige tier must be 1 to 6" }
        val score = when {
            audience == FanfareAudience.SERVER -> SERVER
            tier == 6 -> LEGACY
            else -> MEMBERS
        }
        return score.map { if (it.pitch == TIER) it.copy(pitch = TIER_PITCH[tier - 1]) else it }
    }
}
