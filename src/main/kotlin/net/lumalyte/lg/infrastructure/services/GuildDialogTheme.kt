package net.lumalyte.lg.infrastructure.services

import io.papermc.paper.registry.data.dialog.body.DialogBody
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.lumalyte.lg.utils.GuiTheme

private const val DIALOG_ART_FONT = "lumaguilds:dialogs"
private const val DIALOG_ART_WIDTH = 280
private val ART_PARTS = listOf("crest_v3")
private val DIALOG_ACCENTS =
    mapOf(
        GuiTheme.FROSTBOUND to NamedTextColor.AQUA,
        GuiTheme.VERDANT to NamedTextColor.GREEN,
        GuiTheme.VOIDLIGHT to NamedTextColor.LIGHT_PURPLE,
        GuiTheme.OBSIDIAN to NamedTextColor.GRAY,
        GuiTheme.CHRISTMAS to NamedTextColor.RED,
    )

/** Optional artwork is resolved as a complete snapshot; missing assets retain the plain UI. */
internal class GuildDialogTheme(
    private val resolver: NexoGlyphResolver = NexoGlyphResolver { NexoPublicGlyphResolver.resolve(it) },
) {
    fun resolve(theme: GuiTheme): GuildDialogSkin? {
        val resolved = theme.resolved()
        return if (resolved.hasBackground) {
            parts(resolved)?.let {
                GuildDialogSkin(it, guildDialogAccent(resolved))
            }
        } else {
            null
        }
    }

    private fun parts(theme: GuiTheme): Map<String, Component>? {
        val parts = mutableMapOf<String, Component>()
        for (part in ART_PARTS) {
            val id = "guild_dialog_${theme.name.lowercase()}_$part"
            parts[part] = glyph(id) ?: return null
        }
        return glyph("guild_dialog_spacer")?.let {
            parts["spacer"] = it
            parts
        }
    }

    private fun glyph(id: String): Component? {
        val glyph = resolveOptionalGlyph(resolver, id)
        return if (glyph != null && glyph.isDialogArt() && glyph.matchesId(id)) {
            Component.text(glyph.character, NamedTextColor.WHITE).font(Key.key(DIALOG_ART_FONT))
                .decoration(TextDecoration.BOLD, false)
                .decoration(TextDecoration.ITALIC, false)
        } else {
            null
        }
    }
}

internal fun ResolvedNexoGlyph.isDialogArt(): Boolean =
    !isEmoji && font == DIALOG_ART_FONT && character.codePointCount(0, character.length) == 1

internal fun ResolvedNexoGlyph.matchesId(expected: String): Boolean = id == null || id == expected

/** Art occupies explicit native text lines and never changes the action hitboxes. */
internal class GuildDialogSkin(
    private val parts: Map<String, Component>,
    val accent: NamedTextColor,
) {
    // Native text stays independent of decorative glyph advances and font metrics.
    fun card(
        @Suppress("UNUSED_PARAMETER") lines: List<Component>,
    ): Component? = null

    fun referenceBody(
        @Suppress("UNUSED_PARAMETER") title: Component,
        subtitle: Component,
        content: List<DialogBody>,
    ): List<DialogBody> {
        val contents =
            if (subtitle == Component.empty()) {
                content
            } else {
                listOf(DialogBody.plainMessage(subtitle, DIALOG_ART_WIDTH)) + content
            }
        return body(contents)
    }

    fun body(content: List<DialogBody>): List<DialogBody> {
        return buildList {
            parts["crest_v3"]?.let { crest ->
                add(
                    DialogBody.plainMessage(
                        crest.append(Component.newline()).append(parts.getValue("spacer")),
                        DIALOG_ART_WIDTH,
                    ),
                )
            }
            addAll(content)
        }
    }

    fun label(text: Component): Component {
        return text.colorIfAbsent(accent)
    }
}

internal fun guildDialogAccent(theme: GuiTheme): NamedTextColor =
    DIALOG_ACCENTS.getOrDefault(theme.resolved(), NamedTextColor.GOLD)
