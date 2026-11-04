package net.lumalyte.lg.infrastructure.persistence.guilds

import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit
import net.lumalyte.lg.domain.achievements.GuildAchievement
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.*

class GuildAchievementProgressRepositorySQLTest {
    @TempDir lateinit var directory: Path
    private lateinit var storage: VirtualThreadSQLiteStorage
    private val guild = UUID.randomUUID()
    private val other = UUID.randomUUID()
    private val node = GuildAchievement("milestone", null, "Milestone", emptyList(), "CROP_BREAK", null,
        2, 0, emptyList(), "guild", "WHEAT", "TASK", 0f, 0f)
    private fun repository() = GuildAchievementProgressRepositorySQL(storage, listOf(node))

    @BeforeEach fun setUp() {
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        storage.connection.executeUpdate("CREATE TABLE guilds (id VARCHAR(36) PRIMARY KEY)")
        storage.connection.executeUpdate("INSERT INTO guilds VALUES (?)", guild.toString())
        storage.connection.executeUpdate("INSERT INTO guilds VALUES (?)", other.toString())
    }
    @AfterEach fun tearDown() { storage.connection.close(5, TimeUnit.SECONDS) }

    @Test fun `progress persists per guild and duplicate events never count twice`() {
        val first = UUID.randomUUID()
        assertEquals(1, repository().apply(guild, first, "CROP_BREAK", null, 1).progress["milestone"])
        assertTrue(repository().apply(guild, first, "CROP_BREAK", null, 1).duplicate)
        assertFailsWith<IllegalArgumentException> { repository().apply(guild, first, "CROP_BREAK", null, 2) }
        val completion = repository().apply(guild, UUID.randomUUID(), "CROP_BREAK", null, 1)
        assertEquals(setOf("milestone"), completion.completed)
        assertEquals(1, storage.connection.getFirstRow("SELECT COUNT(*) AS n FROM guild_achievement_completions_v1 WHERE guild_id = ?", guild.toString()).getInt("n"))
        assertEquals(1, repository().apply(other, UUID.randomUUID(), "CROP_BREAK", null, 1).progress["milestone"])
        assertTrue(repository().apply(guild, UUID.randomUUID(), "CROP_BREAK", null, 1).completed.isEmpty())
    }

    @Test fun `absent guild cannot acquire progress`() {
        assertFailsWith<IllegalStateException> { repository().apply(UUID.randomUUID(), UUID.randomUUID(), "CROP_BREAK", null, 1) }
    }

    @Test fun `completion write failure rolls back progress and event receipt`() {
        val repo = repository()
        val event = UUID.randomUUID()
        storage.connection.executeUpdate("CREATE TRIGGER reject_completion BEFORE INSERT ON guild_achievement_completions_v1 BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        assertFails { repo.apply(guild, event, "CROP_BREAK", null, 2) }
        assertEquals(0, storage.connection.getFirstRow("SELECT COUNT(*) AS n FROM guild_achievement_progress_v1").getInt("n"))
        assertEquals(0, storage.connection.getFirstRow("SELECT COUNT(*) AS n FROM guild_achievement_events_v1").getInt("n"))
        storage.connection.executeUpdate("DROP TRIGGER reject_completion")
        assertEquals(setOf("milestone"), repo.apply(guild, event, "CROP_BREAK", null, 2).completed)
    }

    @Test fun `concurrent replay across repository instances counts one event`() {
        val one = repository()
        val two = repository()
        val event = UUID.randomUUID()
        val start = java.util.concurrent.CountDownLatch(1)
        val calls = listOf(one, two).map { repo -> java.util.concurrent.CompletableFuture.supplyAsync {
            start.await()
            repo.apply(guild, event, "CROP_BREAK", null, 1)
        } }
        start.countDown()
        val results = calls.map { it.get(10, TimeUnit.SECONDS) }
        assertEquals(1, results.count { it.duplicate })
        assertEquals(1, results.last().progress["milestone"])
        assertEquals(setOf("milestone"), one.apply(guild, UUID.randomUUID(), "CROP_BREAK", null, 1).completed)
    }
}
