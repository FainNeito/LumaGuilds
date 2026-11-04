package net.lumalyte.lg.infrastructure.persistence.guilds

import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertFalse
import net.lumalyte.lg.domain.achievements.GuildAchievementIpEligibility

class GuildAchievementAddressRepositorySQLTest {
    @TempDir lateinit var directory: Path
    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var repository: GuildAchievementAddressRepositorySQL
    private val player = UUID.fromString("b103e306-5581-474d-a497-7371945078c7")
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val token = "a".repeat(64)
    private val keyId = "1".repeat(32)

    @BeforeEach fun setUp() {
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        repository = GuildAchievementAddressRepositorySQL(storage)
    }

    @AfterEach fun tearDown() { storage.connection.close(5, TimeUnit.SECONDS) }

    @Test fun `offline observation survives repository recreation and ignores stale updates`() {
        assertNull(repository.read(player, keyId))
        repository.observe(player, token, keyId, now)
        repository.observe(player, "b".repeat(64), keyId, now.minusSeconds(1))
        assertEquals(GuildAchievementAddressRepositorySQL.Observation(token, now), GuildAchievementAddressRepositorySQL(storage).read(player, keyId))
        assertNull(repository.read(player, "2".repeat(32)))
        repository.observe(player, "c".repeat(64), keyId, now)
        assertNull(repository.read(player, keyId))
        repository.observe(player, "d".repeat(64), keyId, now.plusSeconds(1))
        assertEquals(GuildAchievementAddressRepositorySQL.Observation("d".repeat(64), now.plusSeconds(1)), repository.read(player, keyId))
    }

    @Test fun `rejects raw and invalid address observations`() {
        assertFailsWith<IllegalArgumentException> { repository.observe(player, "192.0.2.10", keyId, now) }
        assertFailsWith<IllegalArgumentException> { repository.observe(player, "X".repeat(64), keyId, now) }
        assertNull(repository.read(player, keyId))
    }

    @Test fun `retains keyed observations across restarts without claiming complete history`() {
        val newer = "b".repeat(64)
        repository.observe(player, token, keyId, now)
        repository.observe(player, newer, keyId, now.plusSeconds(1))
        val reopened = GuildAchievementAddressRepositorySQL(storage)
        val history = reopened.readHistory(player, keyId)
        assertEquals(setOf(token, newer), history.observations)
        assertFalse(history.complete)
        assertEquals(emptySet(), reopened.readHistory(player, "2".repeat(32)).observations)
        assertFalse(reopened.readHistory(player, "2".repeat(32)).complete)
    }

    @Test fun `last observation and history roll back together`() {
        storage.connection.executeUpdate("CREATE TRIGGER reject_latest BEFORE INSERT ON guild_achievement_address_observations BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        assertFailsWith<Exception> { repository.observe(player, token, keyId, now) }
        assertNull(repository.read(player, keyId))
        assertEquals(emptySet(), repository.readHistory(player, keyId).observations)
    }

    @Test fun `incomplete stored history blocks matching prior address but never proves uniqueness`() {
        val actor = UUID.randomUUID()
        val members = setOf(actor, player)
        repository.observe(player, token, keyId, now)
        repository.observe(player, "b".repeat(64), keyId, now.plusSeconds(1))
        val histories = mapOf(player to GuildAchievementAddressRepositorySQL(storage).readHistory(player, keyId))
        assertEquals(GuildAchievementIpEligibility.Decision.SHARED,
            GuildAchievementIpEligibility.decideWithHistory(actor, members, token, histories))
        assertEquals(GuildAchievementIpEligibility.Decision.UNKNOWN,
            GuildAchievementIpEligibility.decideWithHistory(actor, members, "c".repeat(64), histories))
    }

    @Test fun `forget removes every key generation and keeps eligibility unknown`() {
        repository.observe(player, token, keyId, now)
        repository.observe(player, "b".repeat(64), "2".repeat(32), now.plusSeconds(1))
        repository.forget(player)
        assertNull(repository.read(player, "2".repeat(32)))
        assertEquals(emptySet(), repository.readHistory(player, keyId).observations)
        assertEquals(emptySet(), repository.readHistory(player, "2".repeat(32)).observations)
        assertFalse(repository.readHistory(player, keyId).complete)
    }

    @Test fun `failed forget rolls back deletion of history`() {
        repository.observe(player, token, keyId, now)
        storage.connection.executeUpdate("CREATE TRIGGER reject_forget BEFORE DELETE ON guild_achievement_address_observations BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        assertFailsWith<Exception> { repository.forget(player) }
        assertEquals(setOf(token), repository.readHistory(player, keyId).observations)
        assertEquals(GuildAchievementAddressRepositorySQL.Observation(token, now), repository.read(player, keyId))
    }
}
