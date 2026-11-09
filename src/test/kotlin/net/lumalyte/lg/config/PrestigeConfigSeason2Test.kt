package net.lumalyte.lg.config

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PrestigeConfigSeason2Test {
    @Test
    fun `season two default allows six lifetime prestiges`() {
        val config = PrestigeConfig()
        assertEquals(true, config.enabled)
        assertEquals(6, config.maxCount)
        assertEquals(
            listOf(2_500L, 5_000L, 7_500L, 10_000L, 12_500L, 15_000L),
            config.fees,
        )
        assertEquals(15_000L, config.feeFor(5))
        assertEquals(2.0, config.climbBoost)
    }

    @Test
    fun `prestige maximum cannot exceed six`() {
        assertFailsWith<IllegalArgumentException> {
            PrestigeConfig(
                maxCount = 7,
                fees = listOf(10_000L, 20_000L, 30_000L, 40_000L, 50_000L, 60_000L, 70_000L),
            )
        }
    }
}
