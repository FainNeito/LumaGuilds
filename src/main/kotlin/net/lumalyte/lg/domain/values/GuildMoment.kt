package net.lumalyte.lg.domain.values

import net.lumalyte.lg.domain.entities.WarNotificationKind

/** One sound in a moment: a vanilla sound key, played [delayTicks] after the moment starts. */
data class SoundCue(val sound: String, val volume: Float, val pitch: Float, val delayTicks: Long = 0)

/** REQ-233 to REQ-235: war and diplomacy moments from the approved war notification mockup. */
enum class GuildMoment(val cues: List<SoundCue>) {
    /** Two bells, the second lower and quieter 200 ms later, over a restrained low impact. */
    DECLARED(listOf(
        SoundCue("block.bell.use", 1.0f, 1.0f, 0),
        SoundCue("block.bell.use", 0.8f, 0.89f, 4),
        SoundCue("block.respawn_anchor.deplete", 0.6f, 0.6f, 4),
    )),

    /** A war drum. */
    BEGINS(listOf(
        SoundCue("block.note_block.basedrum", 1.0f, 0.6f, 0),
        SoundCue("block.note_block.basedrum", 1.0f, 0.6f, 5),
        SoundCue("block.note_block.basedrum", 1.0f, 0.5f, 10),
        SoundCue("entity.ender_dragon.growl", 0.3f, 1.0f, 12),
    )),

    VICTORY(listOf(
        SoundCue("ui.toast.challenge_complete", 1.0f, 1.0f, 0),
        SoundCue("entity.player.levelup", 0.6f, 1.2f, 6),
    )),

    DEFEAT(listOf(
        SoundCue("block.beacon.deactivate", 1.0f, 0.8f, 0),
        SoundCue("block.bell.resonate", 0.6f, 0.6f, 4),
    )),

    PEACE(listOf(
        SoundCue("block.amethyst_block.chime", 1.0f, 1.0f, 0),
        SoundCue("block.amethyst_block.chime", 1.0f, 1.3f, 4),
        SoundCue("block.note_block.chime", 0.7f, 1.0f, 8),
    )),

    /** Not in the mockup; follows its style. */
    ALLIANCE(listOf(
        SoundCue("block.note_block.bell", 0.8f, 1.2f, 0),
        SoundCue("block.note_block.bell", 0.8f, 1.5f, 4),
        SoundCue("entity.player.levelup", 0.5f, 1.5f, 8),
    )),

    /** The mockup's chosen war-kill cue: a slowed critical hit, then a sped-up level-up. */
    KILL(listOf(
        SoundCue("entity.player.attack.crit", 1.0f, 0.7f, 0),
        SoundCue("entity.player.levelup", 0.6f, 1.6f, 2),
    ));

    companion object {
        fun of(kind: WarNotificationKind): GuildMoment = when (kind) {
            WarNotificationKind.DECLARATION_RECEIVED, WarNotificationKind.DECLARATION_SENT -> DECLARED
            WarNotificationKind.WAR_ACCEPTED -> BEGINS
            WarNotificationKind.VICTORY -> VICTORY
            WarNotificationKind.DEFEAT -> DEFEAT
        }

        /** The declaring guild's name first, then the defending guild's. */
        fun declaringFirst(kind: WarNotificationKind, own: String, opponent: String): Pair<String, String> =
            if (kind == WarNotificationKind.DECLARATION_RECEIVED) opponent to own else own to opponent
    }
}
