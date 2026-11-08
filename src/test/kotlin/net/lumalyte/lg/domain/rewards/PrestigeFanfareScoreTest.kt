package net.lumalyte.lg.domain.rewards

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** REQ-164: the approved v3 fanfare, cue for cue. */
class PrestigeFanfareScoreTest {
    private val vanilla = setOf(
        "item.goat_horn.sound.0", "item.goat_horn.sound.1", "ui.toast.challenge_complete",
        "block.bell.use", "block.bell.resonate", "block.note_block.bell", "block.note_block.chime",
        "block.note_block.flute", "block.note_block.bass", "block.beacon.power_select",
        "block.beacon.activate", "entity.firework_rocket.large_blast", "block.end_gateway.spawn",
    )

    @Test
    fun `member fanfare for tiers one to five`() {
        val cues = PrestigeFanfareScore.cues(tier = 1, audience = FanfareAudience.MEMBERS)
        assertEquals(19, cues.size)
        assertEquals(42, cues.maxOf { it.tick })
        assertEquals(FanfareCue(0, "item.goat_horn.sound.1", 0.6f, 0.85f), cues.first())
        assertEquals(1.05f, PrestigeFanfareScore.cues(5, FanfareAudience.MEMBERS).first().pitch)
    }

    @Test
    fun `prestige six gets the longer legacy fanfare`() {
        val cues = PrestigeFanfareScore.cues(tier = 6, audience = FanfareAudience.MEMBERS)
        assertEquals(26, cues.size)
        assertEquals(66, cues.maxOf { it.tick })
        assertEquals(FanfareCue(0, "item.goat_horn.sound.0", 0.7f, 0.8f), cues.first())
    }

    @Test
    fun `everyone else hears a short distant version`() {
        val cues = PrestigeFanfareScore.cues(tier = 3, audience = FanfareAudience.SERVER)
        assertEquals(6, cues.size)
        assertEquals(20, cues.maxOf { it.tick })
        assertTrue(cues.all { it.volume <= 0.3f })
    }

    @Test
    fun `only vanilla sounds at audible volumes and valid pitches`() {
        for (tier in 1..6) for (audience in FanfareAudience.entries) {
            PrestigeFanfareScore.cues(tier, audience).forEach { cue ->
                assertTrue(cue.sound in vanilla, cue.sound)
                assertTrue(cue.volume in 0.05f..1f && cue.pitch in 0.5f..2f, cue.toString())
            }
        }
    }
}
