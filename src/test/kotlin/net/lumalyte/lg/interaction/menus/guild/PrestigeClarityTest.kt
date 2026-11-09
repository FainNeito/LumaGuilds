package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.application.services.GuildPrestigeService
import net.lumalyte.lg.domain.rewards.PrestigeOverview
import net.lumalyte.lg.domain.rewards.PrestigeQuote
import net.lumalyte.lg.domain.rewards.RewardCatalog
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.bedrock.BedrockPrestigeConfirmationMenu
import net.lumalyte.lg.interaction.menus.bedrock.BedrockPrestigeSelectionMenu
import org.geysermc.cumulus.form.ModalForm
import org.geysermc.cumulus.form.SimpleForm
import org.junit.jupiter.api.Test
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.assertTrue

/** REQ-173: prestige choices and the confirmation say what the kept reward does and what prestige gives. */
internal class PrestigeClarityTest : GuildRankMenuFixture() {
    private val catalog = RewardCatalog.chapterTwo()
    private val prestige = mockk<GuildPrestigeService>()
    private val quote = PrestigeQuote(UUID.randomUUID(), guild.id, UUID.randomUUID(), "bank-1", 20_000, 3, 1)

    private fun bind() {
        bindRank("Founder")
        every { prestige.overview(guild.id) } returns
            PrestigeOverview(true, 100, 1, 6, 20_000, listOf(catalog.find("bank-1")!!, catalog.find("ally-homes")!!))
        loadKoinModules(module { single { prestige }; single { catalog } })
    }

    @Test
    fun `java confirmation explains the kept reward, the home slot and the badge`() {
        bind()
        val lore = GuildPrestigeConfirmationMenu(player, quote, "Bank Extension 1", {}, {}).createGui()
            .let { gui -> gui.show(player); player.openInventory.topInventory.getItem(4)!!.lore()!! }
            .joinToString("\n") { plain(PlainTextComponentSerializer.plainText().serialize(it)) }
        assertTrue("bank capacity: +1" in lore, lore)
        assertTrue("one permanent home slot" in lore, lore)
        assertTrue("prestige ii badge" in lore, lore)
        assertTrue("buy them again" in lore, lore)
        assertTrue("2x xp" in lore, lore)
    }

    @Test
    fun `confirmation says whether XP carries over`() {
        bind()
        fun javaLore() = GuildPrestigeConfirmationMenu(player, quote, "Bank Extension 1", {}, {}).createGui()
            .let { gui -> gui.show(player); player.openInventory.topInventory.getItem(4)!!.lore()!! }
            .joinToString("\n") { plain(PlainTextComponentSerializer.plainText().serialize(it)) }
        assertTrue("xp goes back to 0" in javaLore())
        val config = org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.ConfigService>()
        every { config.loadConfig() } returns net.lumalyte.lg.config.MainConfig().apply {
            progression = progression.copy(prestige = progression.prestige.copy(
                carryOver = net.lumalyte.lg.config.PrestigeCarryOverConfig(enabled = true, landingLevel = 5),
            ))
        }
        val lore = javaLore()
        assertTrue("carries over, up to the start of level 5" in lore, lore)
        val bedrock = plain((BedrockPrestigeConfirmationMenu(MenuNavigator(player), player, quote, "Bank Extension 1", {}, {},
            Logger.getAnonymousLogger()).getForm() as ModalForm).content())
        assertTrue("carries over, up to the start of level 5" in bedrock, bedrock)
    }

    @Test
    fun `bedrock confirmation says the same`() {
        bind()
        val content = plain((BedrockPrestigeConfirmationMenu(MenuNavigator(player), player, quote, "Bank Extension 1", {}, {},
            Logger.getAnonymousLogger()).getForm() as ModalForm).content())
        assertTrue("bank capacity: +1" in content, content)
        assertTrue("one permanent home slot" in content, content)
        assertTrue("prestige ii badge" in content, content)
    }

    @Test
    fun `bedrock reward choices say what each reward does`() {
        bind()
        val buttons = (BedrockPrestigeSelectionMenu(MenuNavigator(player), player, guild, Logger.getAnonymousLogger()).getForm() as SimpleForm)
            .buttons().map { plain(it!!.text()) }
        assertTrue("bank capacity: +1" in buttons[0], buttons[0])
        assertTrue("allied guild homes" in buttons[1], buttons[1])
    }

    companion object {
        private const val SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ"

        /** The server font turns static text into small caps; compare letters, not glyphs. */
        fun plain(text: String) = text.map { c -> SMALL.indexOf(c).let { if (it >= 0) 'a' + it else c } }
            .joinToString("").lowercase()
    }
}
