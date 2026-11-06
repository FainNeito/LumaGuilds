package net.lumalyte.lg.domain.achievements

import java.util.UUID
import org.junit.jupiter.api.Test
import kotlin.test.*

class GuildAchievementEventIdTest {
    @Test fun `source identity is stable and scoped to guild and requirement`() {
        val guild = UUID.randomUUID()
        val source = UUID.randomUUID()
        val id = GuildAchievementEventId.fromSource(guild, "GUILD_WAR_WON", source)
        assertEquals(id, GuildAchievementEventId.fromSource(guild, "GUILD_WAR_WON", source))
        assertNotEquals(id, GuildAchievementEventId.fromSource(UUID.randomUUID(), "GUILD_WAR_WON", source))
        assertNotEquals(id, GuildAchievementEventId.fromSource(guild, "GUILD_WAR_DECLARED", source))
        assertNotEquals(id, GuildAchievementEventId.fromSource(guild, "GUILD_WAR_WON", UUID.randomUUID()))
        assertFailsWith<IllegalArgumentException> { GuildAchievementEventId.fromSource(guild, "bad:value", source) }
    }
}
