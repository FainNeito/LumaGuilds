package net.lumalyte.lg.utils

/**
 * GUI background themes for guild menus.
 *
 * Each theme must have a corresponding Nexo font glyph defined in
 * the resource pack glyphs configuration and the texture at
 * assets/minecraft/textures/gui/[theme/]guild_menu_[theme]_[rows]_row.png
 *
 * The NEUTRAL theme always has textures available; other themes are
 * tiered content that guilds unlock through progression.
 *
 * Themes with [requiresUnlock] are holiday themes earned through
 * EnthusiaHolidays guild goals (REQ-094); a guild can only apply one
 * after it has been unlocked through the GuildCosmeticUnlocks API.
 */
enum class GuiTheme(val displayName: String, val requiresUnlock: Boolean = false) {
    NEUTRAL("Default"),
    EMBERSTONE("Emberstone"),
    CARVED_SLATE("Carved Slate"),
    MOSSBOUND("Mossbound"),
    LAVENDER_HALL("Lavender Hall"),
    IRON_ROSE("Iron Rose"),
    HAUNTED_HALL("Haunted Hall", requiresUnlock = true),
    WINTER_LODGE("Winter Lodge", requiresUnlock = true);

    companion object {
        /** Maps the database/storage string back to an enum value. */
        fun fromKey(key: String): GuiTheme =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: NEUTRAL
    }
}
