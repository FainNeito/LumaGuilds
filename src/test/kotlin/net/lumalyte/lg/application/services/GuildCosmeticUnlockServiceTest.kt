package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildCosmeticUnlockRepository
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildCosmeticUnlock
import net.lumalyte.lg.utils.GuiTheme
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** REQ-094: unlock/revoke policy and holiday theme availability. */
class GuildCosmeticUnlockServiceTest {
    private val guildId = UUID.randomUUID()
    private val now = Instant.parse("2026-10-20T12:00:00Z")
    private val guilds = mockk<GuildRepository>(relaxed = true)
    private val unlocks = InMemoryUnlocks()
    private val service = GuildCosmeticUnlockService(guilds, unlocks) { now }

    private fun guild(theme: GuiTheme = GuiTheme.NEUTRAL) = Guild(id = guildId, name = "Enthusiasts", createdAt = now, guiTheme = theme)

    @Test
    fun `holiday themes are locked until unlocked and progression themes never are`() {
        every { guilds.getById(guildId) } returns guild()
        assertTrue(GuiTheme.HAUNTED_HALL.requiresUnlock)
        assertTrue(GuiTheme.WINTER_LODGE.requiresUnlock)
        assertFalse(GuiTheme.EMBERSTONE.requiresUnlock)

        assertFalse(service.isThemeAvailable(guildId, GuiTheme.HAUNTED_HALL))
        assertTrue(service.isThemeAvailable(guildId, GuiTheme.EMBERSTONE))

        assertTrue(service.unlock(guildId, "menu_theme", "haunted_hall", "Haunted Hall '26", "event:halloween-2026"))
        assertTrue(service.isThemeAvailable(guildId, GuiTheme.HAUNTED_HALL))
        assertFalse(service.isThemeAvailable(guildId, GuiTheme.WINTER_LODGE))
        assertEquals("Haunted Hall '26", service.themeDisplayName(guildId, GuiTheme.HAUNTED_HALL))
        assertEquals(GuiTheme.EMBERSTONE.displayName, service.themeDisplayName(guildId, GuiTheme.EMBERSTONE))
    }

    @Test
    fun `unlock is idempotent and records normalised values`() {
        every { guilds.getById(guildId) } returns guild()
        assertTrue(service.unlock(guildId, " menu_theme ", "haunted_hall", "Haunted Hall '26", "src"))
        assertTrue(service.unlock(guildId, "MENU_THEME", "HAUNTED_HALL", "Other", "src"))
        assertEquals(setOf("HAUNTED_HALL"), service.unlockedKeys(guildId, "menu_theme"))
        assertEquals(GuildCosmeticUnlock(guildId, "MENU_THEME", "HAUNTED_HALL", "Haunted Hall '26", "src", now),
            unlocks.get(guildId, "MENU_THEME", "HAUNTED_HALL"))
    }

    @Test
    fun `unknown keys are stored for forward compatibility`() {
        every { guilds.getById(guildId) } returns guild()
        assertTrue(service.unlock(guildId, "BADGE", "HALLOWEEN_2026", "Halloween 2026 Badge", "src"))
        assertTrue(service.unlock(guildId, "MENU_THEME", "SPRING_GARDEN", "Spring Garden", "src"))
        assertEquals(setOf("SPRING_GARDEN"), service.unlockedKeys(guildId, "MENU_THEME"))
    }

    @Test
    fun `missing guild and invalid input are rejected`() {
        every { guilds.getById(guildId) } returns null
        assertFalse(service.unlock(guildId, "MENU_THEME", "HAUNTED_HALL", "x", "src"))
        assertFalse(service.revoke(guildId, "MENU_THEME", "HAUNTED_HALL"))

        every { guilds.getById(guildId) } returns guild()
        assertFalse(service.unlock(guildId, "MENU_THEME", " ", "x", "src"))
        assertFalse(service.unlock(guildId, "MENU_THEME", "K".repeat(65), "x", "src"))
        assertFalse(service.unlock(guildId, "", "HAUNTED_HALL", "x", "src"))
        assertTrue(unlocks.getForGuild(guildId).isEmpty())
    }

    @Test
    fun `blank display name falls back to the key`() {
        every { guilds.getById(guildId) } returns guild()
        assertTrue(service.unlock(guildId, "MENU_THEME", "WINTER_LODGE", "  ", "src"))
        assertEquals("WINTER_LODGE", unlocks.get(guildId, "MENU_THEME", "WINTER_LODGE")?.displayName)
    }

    @Test
    fun `revoking the equipped holiday theme resets only the theme`() {
        every { guilds.getById(guildId) } returns guild(GuiTheme.HAUNTED_HALL)
        every { guilds.updateGuiTheme(guildId, GuiTheme.HAUNTED_HALL, GuiTheme.NEUTRAL) } returns true
        service.unlock(guildId, "MENU_THEME", "HAUNTED_HALL", "Haunted Hall '26", "src")

        assertTrue(service.revoke(guildId, "menu_theme", "haunted_hall"))
        assertTrue(service.revoke(guildId, "MENU_THEME", "HAUNTED_HALL"))

        verify(atLeast = 1) { guilds.updateGuiTheme(guildId, GuiTheme.HAUNTED_HALL, GuiTheme.NEUTRAL) }
        // A full-record write would overwrite concurrent changes to the guild.
        verify(exactly = 0) { guilds.update(any()) }
        assertFalse(service.isThemeAvailable(guildId, GuiTheme.HAUNTED_HALL))
    }

    @Test
    fun `revoking a theme the guild is not using leaves its theme alone`() {
        every { guilds.getById(guildId) } returns guild(GuiTheme.EMBERSTONE)
        service.unlock(guildId, "MENU_THEME", "HAUNTED_HALL", "Haunted Hall '26", "src")
        assertTrue(service.revoke(guildId, "MENU_THEME", "HAUNTED_HALL"))
        verify(exactly = 0) { guilds.update(any()) }
    }

    @Test
    fun `persistence failure is reported`() {
        every { guilds.getById(guildId) } returns guild()
        val failing = mockk<GuildCosmeticUnlockRepository> {
            every { get(any(), any(), any()) } returns null
            every { saveIfAbsent(any()) } returns false
        }
        assertFalse(GuildCosmeticUnlockService(guilds, failing) { now }.unlock(guildId, "MENU_THEME", "HAUNTED_HALL", "x", "src"))
    }

    private class InMemoryUnlocks : GuildCosmeticUnlockRepository {
        private val rows = linkedMapOf<Triple<UUID, String, String>, GuildCosmeticUnlock>()
        override fun getForGuild(guildId: UUID) = rows.values.filter { it.guildId == guildId }
        override fun get(guildId: UUID, type: String, key: String) = rows[Triple(guildId, type, key)]
        override fun saveIfAbsent(unlock: GuildCosmeticUnlock): Boolean {
            rows.putIfAbsent(Triple(unlock.guildId, unlock.type, unlock.key), unlock)
            return true
        }
        override fun delete(guildId: UUID, type: String, key: String): Boolean {
            rows.remove(Triple(guildId, type, key))
            return true
        }
    }
}
