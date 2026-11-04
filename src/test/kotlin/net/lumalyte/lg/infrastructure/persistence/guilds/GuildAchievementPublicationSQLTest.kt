package net.lumalyte.lg.infrastructure.persistence.guilds

import java.time.Instant
import java.util.UUID
import net.lumalyte.lg.domain.achievements.GuildAchievementXpPolicy
import net.lumalyte.lg.domain.entities.ExperienceAwardRequest
import net.lumalyte.lg.domain.entities.ExperienceAwardResult
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.values.ExperienceSource
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
        assertIs<ExperienceAwardResult.Awarded>(first.awardAchievementAtomically(request, "first_war", 50))
        val restarted = ExperienceAwardRepositorySQL(storage, curve)
        assertEquals(guild, restarted.pendingAchievementPublication(transaction)?.guildId)
        assertEquals(listOf(transaction), restarted.pendingAchievementPublications(10).map { it.transactionId })
        assertEquals(ExperienceAwardResult.Duplicate, restarted.awardAchievementAtomically(request, "first_war", 50))
        assertTrue(restarted.acknowledgeAchievementPublication(transaction))
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
}
