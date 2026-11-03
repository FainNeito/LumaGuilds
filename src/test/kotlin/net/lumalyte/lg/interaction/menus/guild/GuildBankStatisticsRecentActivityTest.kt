package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import net.badgersmc.nexus.i18n.LangHost
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.i18n.Locale
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.BankStats
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.BankTransaction
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.TransactionType
import net.lumalyte.lg.infrastructure.i18n.LumaGuildsLang
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Material
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockbukkit.mockbukkit.MockBukkit
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises the actual statistics inventory and its recent-activity item. */
internal class GuildBankStatisticsRecentActivityTest {
    @TempDir lateinit var directory: Path

    @AfterEach
    fun cleanup() {
        stopKoin()
        unmockkAll()
        MockBukkit.unmock()
    }

    @Test
    fun recentActivityDistinguishesSystemKnownAndUnknownActors() {
        val server = MockBukkit.mock()
        val player = server.addPlayer("KnownMember")
        val plugin = MockBukkit.createMockPlugin()
        mockkStatic(JavaPlugin::class)
        every { JavaPlugin.getProvidingPlugin(any<Class<*>>()) } returns plugin
        val guild = Guild(UUID.randomUUID(), "Example", createdAt = Instant.EPOCH)
        val bank = mockk<BankService>(relaxed = true)
        val members = mockk<MemberService>(relaxed = true)
        val factory = mockk<MenuFactory>(relaxed = true)
        val transactions = listOf(
            BankTransaction(guildId = guild.id, actorId = UUID(0, 0), type = TransactionType.DEDUCTION, amount = 2),
            BankTransaction(guildId = guild.id, actorId = player.uniqueId, type = TransactionType.DEPOSIT, amount = 100),
            BankTransaction(guildId = guild.id, actorId = UUID.randomUUID(), type = TransactionType.WITHDRAWAL, amount = 5),
        )
        every { bank.getBankStats(guild.id) } returns BankStats(0, 0, 0, 0, 0)
        every { bank.getTransactionHistory(guild.id, null) } returns emptyList()
        every { bank.getTransactionHistory(guild.id, 10) } returns transactions
        val host = object : LangHost {
            override val dataFolder: File = directory.toFile()
            override val resourceClassLoader: ClassLoader = LumaGuildsLang::class.java.classLoader
        }
        val language = LangService(host, Locale("en_US"), LumaGuildsLang::class.java)
        stopKoin()
        startKoin { modules(module { single { bank }; single { members }; single { factory }; single { language } }) }

        GuildBankStatisticsMenu(MenuNavigator(player), player, guild).open()

        val item = player.openInventory.topInventory.getItem(23)!!
        assertEquals(Material.WRITABLE_BOOK, item.type)
        val plain = PlainTextComponentSerializer.plainText()
        val lore = item.itemMeta.lore()!!.map(plain::serialize)
        assertTrue(lore.any { it.contains("System deducted 2") }, lore.toString())
        assertTrue(lore.any { it.contains("KnownMember deposited 100") }, lore.toString())
        assertTrue(lore.any { it.contains("Unknown withdrew 5") }, lore.toString())
    }
}
