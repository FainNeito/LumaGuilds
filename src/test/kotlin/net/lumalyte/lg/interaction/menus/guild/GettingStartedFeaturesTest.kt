package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.application.persistence.GuildOnboardingRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildGettingStartedService
import net.lumalyte.lg.application.services.GuildStartTopic
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.junit.jupiter.api.Test
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** REQ-174: Getting Started explains parties, bank interest and the vault only while each is on. */
internal class GettingStartedFeaturesTest : GuildRankMenuFixture() {
    private fun details(topic: GuildStartTopic, tweak: MainConfig.() -> Unit): String {
        val config = mockk<ConfigService>()
        every { config.loadConfig() } returns MainConfig().apply(tweak)
        loadKoinModules(module {
            single { config }
            single { mockk<GuildGettingStartedService>(relaxed = true) }
            single { mockk<GuildOnboardingRepository>(relaxed = true) }
        })
        val navigation = GuildGettingStartedNavigation(MenuNavigator(player), player, guild.id)
        return PrestigeClarityTest.plain(PlainTextComponentSerializer.plainText().serialize(navigation.details(topic)))
    }

    @Test
    fun `chat explains parties only while parties are enabled`() {
        assertTrue("/pc" in details(GuildStartTopic.CHAT) { partiesEnabled = true })
        assertFalse("/pc" in details(GuildStartTopic.CHAT) { partiesEnabled = false })
    }

    @Test
    fun `bank states the configured interest and the vault when they are on`() {
        val on = details(GuildStartTopic.BANK) {
            bank.interestRatePercent = 0.005; bank.interestCompoundPeriodHours = 24; vault.bankMode = "BOTH"
        }
        assertTrue("0.5% interest every 24 hours" in on, on)
        assertTrue("/g vault" in on, on)
        val off = details(GuildStartTopic.BANK) { bank.interestRatePercent = 0.0; vault.bankMode = "VIRTUAL" }
        assertFalse("interest" in off, off)
        assertFalse("/g vault" in off, off)
    }
}
