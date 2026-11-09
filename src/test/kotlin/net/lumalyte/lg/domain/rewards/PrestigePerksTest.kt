package net.lumalyte.lg.domain.rewards

import net.lumalyte.lg.config.PrestigePerksConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

/** REQ-179, REQ-180, REQ-181: practical perks unlock at prestige tiers. */
class PrestigePerksTest {
    private val perks = PrestigePerks(PrestigePerksConfig())

    @Test
    fun `home countdown is 5 seconds until Prestige I, then 2`() {
        assertEquals(5, perks.homeCountdownSeconds(0))
        assertEquals(2, perks.homeCountdownSeconds(1))
        assertEquals(2, perks.homeCountdownSeconds(6))
    }

    @Test
    fun `an unreadable tier gets no perk`() {
        assertEquals(5, perks.homeCountdownSeconds(null))
        assertEquals(1, perks.allyHomeSlots(null))
        assertEquals(0, perks.extraGuildStalls(null))
    }

    @Test
    fun `second allied home unlocks at Prestige II`() {
        assertEquals(listOf(1, 1, 2, 2), (0..3).map(perks::allyHomeSlots))
    }

    @Test
    fun `extra guild stall unlocks at Prestige III`() {
        assertEquals(listOf(0, 0, 0, 1, 1), (0..4).map(perks::extraGuildStalls))
    }

    @Test
    fun `tiers and countdown follow config`() {
        val custom = PrestigePerks(PrestigePerksConfig(quickHomeTier = 2, quickHomeSeconds = 3, secondAllyHomeTier = 4, extraStallTier = 1))
        assertEquals(5, custom.homeCountdownSeconds(1))
        assertEquals(3, custom.homeCountdownSeconds(2))
        assertEquals(1, custom.allyHomeSlots(3))
        assertEquals(1, custom.extraGuildStalls(1))
    }

    @Test
    fun `quick countdown must be between 1 and 5 seconds`() {
        assertThrows<IllegalArgumentException> { PrestigePerksConfig(quickHomeSeconds = 0) }
        assertThrows<IllegalArgumentException> { PrestigePerksConfig(quickHomeSeconds = 6) }
    }

    @Test
    fun `each tier lists the perks it starts`() {
        assertEquals(listOf(PrestigePerk.QUICK_HOME), perks.unlockedAt(1))
        assertEquals(emptyList(), perks.unlockedAt(2)) // second allied home lists once it ships (REQ-180)
        assertEquals(listOf(PrestigePerk.EXTRA_STALL), perks.unlockedAt(3))
        assertEquals(emptyList(), perks.unlockedAt(4))
    }
}
