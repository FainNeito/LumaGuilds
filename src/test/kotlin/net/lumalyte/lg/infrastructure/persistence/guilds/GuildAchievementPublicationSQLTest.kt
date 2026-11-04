package net.lumalyte.lg.infrastructure.persistence.guilds

import java.time.Instant
import java.util.UUID
import net.lumalyte.lg.domain.achievements.GuildAchievementXpPolicy
import net.lumalyte.lg.domain.entities.ExperienceAwardRequest
import net.lumalyte.lg.domain.entities.ExperienceAwardResult
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.values.ExperienceSource
import net.lumalyte.lg.domain.values.ExperiencePolicy
import net.lumalyte.lg.domain.values.CapPeriod
import net.lumalyte.lg.domain.values.ProgressionCurve
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import org.junit.jupiter.api.Test
import kotlin.test.*

class GuildAchievementPublicationSQLTest : RewardSqlTestFixture() {
    @Test fun `publication is durable and duplicate payment cannot repeat`() {
        val storage = openStorage()
        migrateProductionSchema(storage)
        val guild = UUID.randomUUID()
        assertTrue(GuildRepositorySQLite(storage).add(Guild(guild, "Publication", createdAt = Instant.now())))
        val curve = ProgressionCurve(500.0, 1.15, 150, 100)
        val transaction = GuildAchievementXpPolicy.transactionId(guild, "first_war")
        val request = ExperienceAwardRequest(guild, null, ExperienceSource.ACHIEVEMENT, 50, Instant.now(), transactionId = transaction)
        val first = ExperienceAwardRepositorySQL(storage, curve)
        val seed = request.copy(source = ExperienceSource.MOB_KILL, transactionId = UUID.randomUUID())
        val policy = ExperiencePolicy(ExperienceSource.MOB_KILL, "MOB_KILL", 1, 0, CapPeriod.UNLIMITED, true)
        assertIs<ExperienceAwardResult.Awarded>(first.awardAtomically(seed, policy, curve.experienceForNextLevel(1) - 25, null))
        val awarded = assertIs<ExperienceAwardResult.Awarded>(first.awardAchievementAtomically(request, "first_war", 50))
        assertNotNull(awarded.leveledUpTo)
        val nextId = GuildAchievementXpPolicy.transactionId(guild, "second_war")
        assertIs<ExperienceAwardResult.Awarded>(first.awardAchievementAtomically(request.copy(transactionId = nextId), "second_war", 50))
        val restarted = ExperienceAwardRepositorySQL(storage, curve)
        assertEquals(guild, restarted.pendingAchievementPublication(transaction)?.guildId)
        assertEquals(awarded.leveledUpTo, restarted.pendingAchievementPublication(transaction)?.leveledUpTo)
        assertEquals(setOf(transaction, nextId), restarted.pendingAchievementPublications(10).map { it.transactionId }.toSet())
        assertEquals(ExperienceAwardResult.Duplicate, restarted.awardAchievementAtomically(request, "first_war", 50))
        assertTrue(restarted.acknowledgeAchievementPublication(transaction))
        assertTrue(restarted.acknowledgeAchievementPublication(nextId))
        assertNull(ExperienceAwardRepositorySQL(storage, curve).pendingAchievementPublication(transaction))
        assertTrue(ExperienceAwardRepositorySQL(storage, curve).pendingAchievementPublications(10).isEmpty())
    }

    @Test fun `failed publication insertion rolls back the award`() {
        val storage = openStorage()
        migrateProductionSchema(storage)
        val guild = UUID.randomUUID()
        assertTrue(GuildRepositorySQLite(storage).add(Guild(guild, "PublicationFailure", createdAt = Instant.now())))
        val repository = ExperienceAwardRepositorySQL(storage, ProgressionCurve(500.0, 1.15, 150, 100))
        val body = if (storage.dialect == SqlDialect.MARIADB) "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'test failure'" else "BEGIN SELECT RAISE(ABORT, 'test failure'); END"
        storage.connection.executeUpdate("CREATE TRIGGER reject_publication BEFORE INSERT ON guild_achievement_xp_publications $body")
        val transaction = GuildAchievementXpPolicy.transactionId(guild, "first_war")
        val request = ExperienceAwardRequest(guild, null, ExperienceSource.ACHIEVEMENT, 50, Instant.now(), transactionId = transaction)
        assertFails { repository.awardAchievementAtomically(request, "first_war", 50) }
        assertNull(repository.pendingAchievementPublication(transaction))
        assertEquals(0, storage.connection.getFirstRow("SELECT COUNT(*) AS n FROM guild_achievement_xp_awards WHERE guild_id = ?", guild.toString()).getInt("n"))
    }

    @Test fun `existing progression creation timestamp survives an award`() {
        val storage = openStorage()
        migrateProductionSchema(storage)
        val guild = UUID.randomUUID()
        assertTrue(GuildRepositorySQLite(storage).add(Guild(guild, "ExistingProgression", createdAt = Instant.now())))
        val created = "2026-01-01 00:00:00"
        storage.connection.executeUpdate("""INSERT INTO guild_progression
            (guild_id, total_experience, current_level, experience_this_level, experience_for_next_level,
             total_level_ups, unlocked_perks, created_at, last_updated) VALUES (?, 0, 1, 0, 575, 0, '[]', ?, ?)""".trimIndent(),
            guild.toString(), created, created)
        fun storedCreated(): String = storage.connection.connection.use { c ->
            c.prepareStatement("SELECT created_at FROM guild_progression WHERE guild_id = ?").use { s ->
                s.setString(1, guild.toString()); s.executeQuery().use { rows -> assertTrue(rows.next()); rows.getString(1) }
            }
        }
        val before = storedCreated()
        val transaction = GuildAchievementXpPolicy.transactionId(guild, "first_war")
        val request = ExperienceAwardRequest(guild, null, ExperienceSource.ACHIEVEMENT, 50, Instant.now(), transactionId = transaction)
        assertIs<ExperienceAwardResult.Awarded>(ExperienceAwardRepositorySQL(storage, ProgressionCurve(500.0, 1.15, 150, 100)).awardAchievementAtomically(request, "first_war", 50))
        assertEquals(before, storedCreated())
    }

    @Test fun `standalone numeric timestamp schema still accepts successive awards`() {
        val storage = openStorage()
        val guild = UUID.randomUUID()
        storage.connection.executeUpdate("CREATE TABLE guilds (id VARCHAR(36) PRIMARY KEY, level INTEGER NOT NULL DEFAULT 1)")
        storage.connection.executeUpdate("INSERT INTO guilds (id) VALUES (?)", guild.toString())
        val repository = ExperienceAwardRepositorySQL(storage, ProgressionCurve(500.0, 1.15, 150, 100))
        fun award(id: String): ExperienceAwardResult {
            val transaction = GuildAchievementXpPolicy.transactionId(guild, id)
            return repository.awardAchievementAtomically(
                ExperienceAwardRequest(guild, null, ExperienceSource.ACHIEVEMENT, 25, Instant.now(), transactionId = transaction), id, 25)
        }
        assertIs<ExperienceAwardResult.Awarded>(award("first"))
        assertIs<ExperienceAwardResult.Awarded>(award("second"))
    }
}
