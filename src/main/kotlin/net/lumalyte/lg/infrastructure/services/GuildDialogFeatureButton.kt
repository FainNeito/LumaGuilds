package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.TextDecoration
import net.lumalyte.lg.utils.GuiTheme
import kotlin.math.ceil

/**
 * REQ-236: the gold, full-width "feature" button drawn in a dialog body (e.g. Browse Recruiting
 * Guilds). Art is one 360-wide size from the pack's dialog_action_feature_v1 font.
 */
internal object GuildDialogFeatureButton {
    const val WIDTH = 360
    private const val CONTRACT_ID = "guild_dialog_feature_v1"
    private val font = Key.key("lumaguilds:dialog_action_feature_v1")
    private val TEXT = net.kyori.adventure.text.format.TextColor.color(0xFFF6D8)

    private fun glyph(code: Int) = Component.text(code.toChar(), NamedTextColor.WHITE).font(font)
        .shadowColor(ShadowColor.none()).decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, false)

    private fun pad(pixels: Int): Component = Component.text(buildString {
        for (bit in 0..10) if (pixels and (1 shl bit) != 0) append((0xE010 + bit).toChar())
    }, NamedTextColor.WHITE).font(font).shadowColor(ShadowColor.none())

    fun available(theme: GuiTheme, resolver: NexoGlyphResolver = NexoPublicGlyphResolver): Boolean =
        theme.resolved().hasBackground &&
            resolveOptionalGlyph(resolver, CONTRACT_ID)?.isDialogContract(CONTRACT_ID, "lumaguilds:dialog_action_contract") == true

    /** The button art with [text] centred on it, or null when it cannot be centred exactly. */
    fun label(text: String): Component? {
        val content = Component.text(text, TEXT)
        val row = GuildDialogText.rows(content, (WIDTH - 16) * 2) { _, _ -> null }?.singleOrNull() ?: return null
        val contentWidth = ceil(row.second / 2.0).toInt()
        val inside = WIDTH - 2
        if (contentWidth > inside) return null
        val left = (inside - contentWidth) / 2
        return Component.text().append(glyph(0xE120)).append(glyph(0xE000)).append(glyph(0xE121)).append(glyph(0xE003))
            .append(pad(left)).append(row.first).append(pad(inside - left - contentWidth)).build()
    }

    /** Without the art (vanilla theme or an older pack): bold gold text, still one clickable line. */
    fun fallback(text: String): Component =
        Component.text("» $text «", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true)
}
