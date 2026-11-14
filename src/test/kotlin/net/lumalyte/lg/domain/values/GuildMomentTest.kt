package net.lumalyte.lg.domain.values

import net.lumalyte.lg.domain.entities.WarNotificationKind
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** REQ-233 to REQ-235: each moment has its own sound, matching the approved mockup. */
internal class GuildMomentTest {
    @Test fun `every moment has a sound and the big moments sound different`() {
        GuildMoment.entries.forEach { assertTrue(it.cues.isNotEmpty(), "$it has no sound") }
        val signatures = listOf(GuildMoment.DECLARED, GuildMoment.BEGINS, GuildMoment.VICTORY, GuildMoment.DEFEAT,
            GuildMoment.PEACE, GuildMoment.ALLIANCE).map { m -> m.cues.map { it.sound } }
        assertEquals(signatures.size, signatures.distinct().size, "Moments must not share the same sound")
    }

    @Test fun `a declaration rings two bells then lands a low impact`() {
        val cues = GuildMoment.DECLARED.cues
        val bells = cues.filter { it.sound == "block.bell.use" }
        assertEquals(2, bells.size)
        assertEquals(0L, bells[0].delayTicks)
        assertTrue(bells[1].delayTicks in 3L..5L, "second bell about 200 ms later")
        assertTrue(bells[1].pitch < bells[0].pitch && bells[1].volume < bells[0].volume, "second bell lower and quieter")
        assertTrue(cues.any { it.sound != "block.bell.use" && it.pitch < 1f }, "low impact underneath")
    }

    @Test fun `a war kill pairs a slowed crit with a quick level up`() {
        val (crit, chime) = GuildMoment.KILL.cues
        assertEquals("entity.player.attack.crit", crit.sound)
        assertTrue(crit.pitch < 1f)
        assertEquals("entity.player.levelup", chime.sound)
        assertTrue(chime.pitch > 1f)
    }

    @Test fun `war notifications map onto mockup moments`() {
        assertEquals(GuildMoment.DECLARED, GuildMoment.of(WarNotificationKind.DECLARATION_RECEIVED))
        assertEquals(GuildMoment.DECLARED, GuildMoment.of(WarNotificationKind.DECLARATION_SENT))
        assertEquals(GuildMoment.BEGINS, GuildMoment.of(WarNotificationKind.WAR_ACCEPTED))
        assertEquals(GuildMoment.VICTORY, GuildMoment.of(WarNotificationKind.VICTORY))
        assertEquals(GuildMoment.DEFEAT, GuildMoment.of(WarNotificationKind.DEFEAT))
        assertNotEquals(GuildMoment.VICTORY.cues, GuildMoment.DEFEAT.cues)
    }

    @Test fun `the declaring guild is named first whichever side receives it`() {
        assertEquals("Pengu Family" to "Enthusiast",
            GuildMoment.declaringFirst(WarNotificationKind.DECLARATION_RECEIVED, own = "Enthusiast", opponent = "Pengu Family"))
        assertEquals("Pengu Family" to "Enthusiast",
            GuildMoment.declaringFirst(WarNotificationKind.DECLARATION_SENT, own = "Pengu Family", opponent = "Enthusiast"))
    }
}
