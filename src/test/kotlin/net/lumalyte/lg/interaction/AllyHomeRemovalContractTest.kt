package net.lumalyte.lg.interaction

import co.aikar.commands.annotation.Subcommand
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.commands.GuildCommand
import net.lumalyte.lg.interaction.help.HelpTopics
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.guild.RankPermissionCatalog
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** REQ-158: ally-home teleportation has no player-facing surface left. */
class AllyHomeRemovalContractTest {
    private val retired = setOf("allyhome", "setallyhome", "removeallyhome")

    @Test
    fun `guild command exposes no ally home subcommands`() {
        val names = GuildCommand::class.java.declaredMethods
            .mapNotNull { it.getAnnotation(Subcommand::class.java)?.value }
            .flatMap { it.split('|', ' ') }
            .map { it.lowercase() }
            .toSet()
        assertTrue(names.isNotEmpty())
        assertTrue(names.none { it in retired }, "ally-home subcommands remain: ${names intersect retired}")
    }

    @Test
    fun `help, permissions and rank editor no longer mention ally homes`() {
        val helpCommands = HelpTopics.all.flatMap { topic -> topic.commands.map { it.id } }
        assertTrue(helpCommands.none { it in retired })
        assertFalse(Files.readString(Path.of("src/main/resources/plugin.yml")).contains("lumaguilds.guild.allyhome"))
        RankPermissionCatalog.categories(claimsEnabled = true).values.flatten().let {
            assertFalse(RankPermission.USE_ALLY_HOMES in it)
        }
    }

    @Test
    fun `ally home access menus are gone`() {
        assertTrue(MenuFactory::class.java.declaredMethods.none { it.name == "createAllyHomeAccessMenu" })
        listOf(
            "src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/AllyHomeAccessMenu.kt",
            "src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock/BedrockAllyHomeAccessMenu.kt",
        ).forEach { assertFalse(Files.exists(Path.of(it)), "$it still exists") }
    }
}
