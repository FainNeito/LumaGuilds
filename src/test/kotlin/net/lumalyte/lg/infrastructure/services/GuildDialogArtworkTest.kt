package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.text.Component
import net.lumalyte.lg.utils.GuiTheme
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class GuildDialogArtworkTest {
    @Test
    fun newSkinNeverResolvesCroppedInventoryBandsOrCardOverlays() {
        val requested = mutableListOf<String>()
        val skin =
            assertNotNull(
                GuildDialogTheme { id ->
                    requested += id
                    ResolvedNexoGlyph("\uE400", "lumaguilds:dialogs", false, id)
                }.resolve(GuiTheme.ENTHUSIA),
            )
        assertNull(skin.card(listOf(Component.text("No painted background behind text"))))
        assertEquals(listOf("guild_dialog_enthusia_crest_v3", "guild_dialog_spacer"), requested)
    }

    @Test
    fun legacyPackFallsBackToPlainWithoutLosingContent() {
        assertNull(
            GuildDialogTheme { id ->
                if (id.endsWith("crest_v3")) null else ResolvedNexoGlyph("\uE400", "lumaguilds:dialogs", false, id)
            }.resolve(GuiTheme.ENTHUSIA),
        )
    }
}
