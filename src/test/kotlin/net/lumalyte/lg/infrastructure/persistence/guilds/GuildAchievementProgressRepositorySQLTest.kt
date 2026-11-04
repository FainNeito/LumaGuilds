package net.lumalyte.lg.infrastructure.persistence.guilds

import java.nio.file.Path
import java.util.UUID
import java.time.Instant
import java.util.concurrent.TimeUnit
import net.lumalyte.lg.domain.achievements.GuildAchievement
import net.lumalyte.lg.domain.achievements.GuildAchievementEventId
import net.lumalyte.lg.domain.achievements.GuildAchievementIpEligibility
import net.lumalyte.lg.infrastructure.services.GuildAchievementGuildEventProcessor
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
        val completionEvent = UUID.randomUUID()
        val completion = repository().apply(guild, completionEvent, "CROP_BREAK", null, 1)
        assertEquals(setOf("milestone"), completion.completed)
        assertEquals(listOf(GuildAchievementProgressRepositorySQL.Completion("milestone", completionEvent, "CROP_BREAK", null, 1)), repository().completions(guild))
        assertTrue(repository().completions(other).isEmpty())
        assertEquals(1, storage.connection.getFirstRow("SELECT COUNT(*) AS n FROM guild_achievement_completions_v1 WHERE guild_id = ?", guild.toString()).getInt("n"))
        assertEquals(1, repository().apply(other, UUID.randomUUID(), "CROP_BREAK", null, 1).progress["milestone"])
        assertTrue(repository().apply(guild, UUID.randomUUID(), "CROP_BREAK", null, 1).completed.isEmpty())
    }

    @Test fun `absent guild cannot acquire progress`() {
        assertFailsWith<IllegalStateException> { repository().apply(UUID.randomUUID(), UUID.randomUUID(), "CROP_BREAK", null, 1) }
    }

    @Test fun `orphaned completion cannot be read as a valid completion`() {
        val repo = repository()
        val event = UUID.randomUUID()
        repo.apply(guild, event, "CROP_BREAK", null, 2)
        storage.connection.executeUpdate("DELETE FROM guild_achievement_events_v1 WHERE guild_id = ? AND event_id = ?", guild.toString(), event.toString())
        assertFailsWith<IllegalArgumentException> { repo.completions(guild) }
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

    @Test fun `same authoritative event replay remains stable across runtime recreation`() {
        val source = UUID.randomUUID()
        val firstId = GuildAchievementEventId.fromSource(guild, "CROP_BREAK", source)
        assertFalse(repository().apply(guild, firstId, "CROP_BREAK", null, 1).duplicate)
        val afterRestart = GuildAchievementEventId.fromSource(guild, "CROP_BREAK", source)
        assertTrue(repository().apply(guild, afterRestart, "CROP_BREAK", null, 1).duplicate)
        assertTrue(repository().completions(guild).isEmpty())
    }

    @Test fun `completion checks persist with the original event and cannot be replaced by replay`() {
        val blocked = UUID.randomUUID()
        val eligible = UUID.randomUUID()
        val event = UUID.randomUUID()
        val initial = mapOf(blocked to GuildAchievementIpEligibility.Decision.SHARED, eligible to GuildAchievementIpEligibility.Decision.ELIGIBLE)
        val repo = repository()
        repo.apply(guild, event, "CROP_BREAK", null, 2, initial)
        repository().apply(guild, event, "CROP_BREAK", null, 2, mapOf(blocked to GuildAchievementIpEligibility.Decision.ELIGIBLE))
        assertEquals(initial, repository().completionChecks(guild, "milestone").associate { it.actor to it.decision })
        assertTrue(repository().completionChecks(other, "milestone").isEmpty())
    }

    @Test fun `failed check insert rolls back event progress and completion`() {
        val repo = repository()
        val event = UUID.randomUUID()
        storage.connection.executeUpdate("CREATE TRIGGER reject_check BEFORE INSERT ON guild_achievement_completion_checks_v1 BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        assertFails { repo.apply(guild, event, "CROP_BREAK", null, 2, mapOf(UUID.randomUUID() to GuildAchievementIpEligibility.Decision.UNKNOWN)) }
        assertTrue(repo.completions(guild).isEmpty())
        assertEquals(0, storage.connection.getFirstRow("SELECT COUNT(*) AS n FROM guild_achievement_events_v1").getInt("n"))
    }

    @Test fun `guild event pipeline ignores history and records completion participants once`() {
        val actor = UUID.randomUUID()
        val peer = UUID.randomUUID()
        val distinct = UUID.randomUUID()
        val time = Instant.parse("2026-11-04T00:00:00Z")
        val repo = repository()
        fun processor() = GuildAchievementGuildEventProcessor(repo, time) { time.plusSeconds(60) }
        val histories = mapOf(actor to GuildAchievementIpEligibility.PeerHistory(setOf("same"), true),
            peer to GuildAchievementIpEligibility.PeerHistory(setOf("same"), true),
            distinct to GuildAchievementIpEligibility.PeerHistory(setOf("other"), true))
        val event = GuildAchievementGuildEventProcessor.Event(guild, UUID.randomUUID(), time, "CROP_BREAK", null, 2,
            setOf(actor, peer, distinct), setOf(actor, peer, distinct),
            mapOf(actor to "same", peer to "same", distinct to "other"), histories)
        assertNull(processor().process(event.copy(occurredAt = time.minusSeconds(1))))
        assertTrue(repo.completions(guild).isEmpty())
        assertEquals(setOf("milestone"), processor().process(event)!!.completed)
        assertTrue(processor().process(event.copy(current = emptyMap()))!!.duplicate)
        val checks = repo.completionChecks(guild, "milestone").associate { it.actor to it.decision }
        assertEquals(GuildAchievementIpEligibility.Decision.SHARED, checks[actor])
        assertEquals(GuildAchievementIpEligibility.Decision.SHARED, checks[peer])
        assertEquals(GuildAchievementIpEligibility.Decision.ELIGIBLE, checks[distinct])
        assertFailsWith<IllegalArgumentException> { processor().process(event.copy(occurredAt = time.plusSeconds(61))) }
    }
}
