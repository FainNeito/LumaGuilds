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

class GuildAchievementAddressRepositorySQLTest {
    @TempDir lateinit var directory: Path
    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var repository: GuildAchievementAddressRepositorySQL
    private val player = UUID.fromString("b103e306-5581-474d-a497-7371945078c7")
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val token = "a".repeat(64)

    @BeforeEach fun setUp() {
        storage = VirtualThreadSQLiteStorage(directory.toFile())
        repository = GuildAchievementAddressRepositorySQL(storage)
    }

    @AfterEach fun tearDown() { storage.connection.close(5, TimeUnit.SECONDS) }

    @Test fun `offline observation survives repository recreation and ignores stale updates`() {
        assertNull(repository.read(player))
        repository.observe(player, token, now)
        repository.observe(player, "b".repeat(64), now.minusSeconds(1))
        repository.observe(player, "c".repeat(64), now)
        assertEquals(GuildAchievementAddressRepositorySQL.Observation(token, now), GuildAchievementAddressRepositorySQL(storage).read(player))
        repository.observe(player, "d".repeat(64), now.plusSeconds(1))
        assertEquals(GuildAchievementAddressRepositorySQL.Observation("d".repeat(64), now.plusSeconds(1)), repository.read(player))
    }

    @Test fun `rejects raw and invalid address observations`() {
        assertFailsWith<IllegalArgumentException> { repository.observe(player, "192.0.2.10", now) }
        assertFailsWith<IllegalArgumentException> { repository.observe(player, "X".repeat(64), now) }
        assertNull(repository.read(player))
    }
}
