package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.ExperienceAwardRepository
import net.lumalyte.lg.domain.entities.ExperienceAwardRequest
import net.lumalyte.lg.domain.entities.ExperienceAwardResult
import net.lumalyte.lg.domain.values.ExperiencePolicy
import net.lumalyte.lg.domain.values.PeriodWindow
import net.lumalyte.lg.domain.values.ProgressionCurve
import net.lumalyte.lg.domain.values.ExperienceSource
import net.lumalyte.lg.domain.values.CapPeriod
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

class ExperienceAwardRepositorySQL(
    private val storage: Storage<Database>,
    private val curveProvider: () -> ProgressionCurve,
) : ExperienceAwardRepository {

    data class AchievementPublication(val transactionId: UUID, val guildId: UUID, val leveledUpTo: Int?)

    constructor(storage: Storage<Database>, curve: ProgressionCurve) : this(storage, { curve })

    private val mariaDb = storage.dialect == SqlDialect.MARIADB
    private val timestampFormats: ExperienceTimestampFormats

    init {
        createTables()
        timestampFormats = storage.connection.connection.use { ExperienceTimestampFormats.inspect(it, mariaDb) }
    }

    override fun awardAtomically(
        request: ExperienceAwardRequest,
        policy: ExperiencePolicy,
        requestedXp: Int,
        window: PeriodWindow?,
    ): ExperienceAwardResult {
        require(requestedXp > 0) { "Requested XP must be positive" }
        require(request.source == policy.source) { "Request source must match policy source" }
        require(policy.isCapped == (window != null)) { "Cap window must match policy period" }
        val curve = curveProvider()

        return storage.connection.connection.use { connection ->
            connection.committingTransaction {
                reserveWriter(connection, request.guildId)
                ExperienceAwardTransaction(connection, mariaDb, curve, timestampFormats).award(request, policy, requestedXp, window)
            }
        }
    }

    /** Keep the permanent achievement receipt in the same transaction as progression and XP history. */
    fun awardAchievementAtomically(
        request: ExperienceAwardRequest,
        achievementId: String,
        amount: Int,
    ): ExperienceAwardResult {
        require(request.source == ExperienceSource.ACHIEVEMENT && amount > 0)
        require(achievementId.matches(Regex("[a-z0-9_]+")))
        require(request.transactionId == net.lumalyte.lg.domain.achievements.GuildAchievementXpPolicy.transactionId(request.guildId, achievementId))
        val policy = ExperiencePolicy(ExperienceSource.ACHIEVEMENT, "ACHIEVEMENT", 1, 0, CapPeriod.UNLIMITED, true)
        return storage.connection.connection.use { connection ->
            connection.committingTransaction {
                reserveWriter(connection, request.guildId)
                val claimed = connection.selectOne(
                    "SELECT 1 FROM guild_achievement_xp_awards WHERE guild_id = ? AND achievement_id = ?",
                    request.guildId.toString(), achievementId,
                ) { true } ?: false
                if (claimed) return@committingTransaction ExperienceAwardResult.Duplicate
                val result = ExperienceAwardTransaction(connection, mariaDb, curveProvider(), timestampFormats).award(request, policy, amount, null)
                check(result is ExperienceAwardResult.Awarded) { "Achievement award receipt conflicts with XP history" }
                connection.updateStatement(
                    "INSERT INTO guild_achievement_xp_awards (guild_id, achievement_id, transaction_id) VALUES (?, ?, ?)",
                    request.guildId.toString(), achievementId, request.transactionId.toString(),
                )
                connection.updateStatement(
                    "INSERT INTO guild_achievement_xp_publications (transaction_id, guild_id, leveled_up_to, acknowledged) VALUES (?, ?, ?, 0)",
                    request.transactionId.toString(), request.guildId.toString(), result.leveledUpTo,
                )
                result
            }
        }
    }

    /** Read-only recovery record; acknowledgment belongs after successful cache and notification handling. */
    fun pendingAchievementPublication(transactionId: UUID): AchievementPublication? =
        storage.connection.connection.use { connection ->
            connection.selectOne(
                "SELECT guild_id, leveled_up_to FROM guild_achievement_xp_publications WHERE transaction_id = ? AND acknowledged = 0",
                transactionId.toString(),
            ) { row ->
                val level = row.getInt("leveled_up_to").let { if (row.wasNull()) null else it }
                AchievementPublication(transactionId, UUID.fromString(row.getString("guild_id")), level)
            }
        }

    /** A worker can drain pending rows in batches without needing the original player online. */
    fun pendingAchievementPublications(limit: Int): List<AchievementPublication> {
        require(limit in 1..100)
        return storage.connection.connection.use { connection ->
            connection.prepareStatement("SELECT transaction_id, guild_id, leveled_up_to FROM guild_achievement_xp_publications WHERE acknowledged = 0 ORDER BY transaction_id LIMIT ?").use { statement ->
                statement.setInt(1, limit)
                statement.executeQuery().use { rows -> buildList {
                    while (rows.next()) {
                        val level = rows.getInt("leveled_up_to").let { if (rows.wasNull()) null else it }
                        add(AchievementPublication(UUID.fromString(rows.getString("transaction_id")), UUID.fromString(rows.getString("guild_id")), level))
                    }
                } }
            }
        }
    }

    /** Idempotent acknowledgement; a completed notification is not itself an XP award. */
    fun acknowledgeAchievementPublication(transactionId: UUID): Boolean =
        storage.connection.connection.use { connection ->
            connection.updateStatement("UPDATE guild_achievement_xp_publications SET acknowledged = 1 WHERE transaction_id = ? AND acknowledged = 0", transactionId.toString()) == 1
        }

    private fun reserveWriter(connection: Connection, guildId: UUID) {
        if (mariaDb) {
            val level =
                connection.selectOne("SELECT level FROM guilds WHERE id = ? FOR UPDATE", guildId.toString()) {
                    it.getInt("level")
                }
            checkNotNull(level) { "Guild $guildId does not exist" }
        } else {
            // Reserve the SQLite writer before opening a SELECT snapshot.
            connection.updateStatement("UPDATE guild_experience_source_usage SET awarded_xp = awarded_xp WHERE 0")
        }
    }

    /** Caller holds the guild lock and commits caps, progression and its companion ledger together. */
    internal fun awardInTransaction(
        connection: Connection,
        request: ExperienceAwardRequest,
        policy: ExperiencePolicy,
        requestedXp: Int,
    ): ExperienceAwardResult {
        val transaction = ExperienceAwardTransaction(connection, mariaDb, curveProvider(), timestampFormats)
        return transaction.award(request, policy, requestedXp, policy.windowContaining(request.occurredAt))
    }

    override fun getAwardedXpByPool(guildId: UUID, at: java.time.Instant): Map<String, Int> {
        val sql =
            "SELECT source_pool, awarded_xp FROM guild_experience_source_usage " +
                "WHERE guild_id = ? AND period_start <= ? AND period_end > ?"
        return storage.connection.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, guildId.toString())
                statement.setLong(2, at.toEpochMilli())
                statement.setLong(PERIOD_END_PARAMETER, at.toEpochMilli())
                statement.executeQuery().use { it.experiencePools() }
            }
        }
    }

    private fun createTables() {
        createUsageTable()
        createProgressionTable()
        createReceiptTable()
        storage.connection.executeUpdate(
            """CREATE TABLE IF NOT EXISTS guild_achievement_xp_awards (
                guild_id VARCHAR(36) NOT NULL,
                achievement_id VARCHAR(128) NOT NULL,
                transaction_id VARCHAR(36) NOT NULL UNIQUE,
                PRIMARY KEY (guild_id, achievement_id)
            )""".trimIndent(),
        )
        storage.connection.executeUpdate(
            """CREATE TABLE IF NOT EXISTS guild_achievement_xp_publications (
                transaction_id VARCHAR(36) PRIMARY KEY,
                guild_id VARCHAR(36) NOT NULL,
                leveled_up_to INTEGER,
                acknowledged INTEGER NOT NULL DEFAULT 0
            )""".trimIndent(),
        )
    }

    private fun createUsageTable() {
        storage.connection.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS guild_experience_source_usage (
                guild_id VARCHAR(36) NOT NULL,
                source_pool VARCHAR(64) NOT NULL,
                period_start BIGINT NOT NULL,
                period_end BIGINT NOT NULL,
                awarded_xp INT NOT NULL DEFAULT 0,
                PRIMARY KEY (guild_id, source_pool, period_start)
            )
            """.trimIndent(),
        )
    }

    private fun createProgressionTable() {
        storage.connection.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS guild_progression (
                guild_id VARCHAR(36) PRIMARY KEY,
                total_experience INT NOT NULL DEFAULT 0,
                current_level INT NOT NULL DEFAULT 1,
                experience_this_level INT NOT NULL DEFAULT 0,
                experience_for_next_level INT NOT NULL DEFAULT 0,
                last_level_up BIGINT,
                total_level_ups INT NOT NULL DEFAULT 0,
                unlocked_perks TEXT NOT NULL,
                created_at BIGINT NOT NULL,
                last_updated BIGINT NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun createReceiptTable() {
        storage.connection.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS experience_transactions (
                id VARCHAR(36) PRIMARY KEY,
                guild_id VARCHAR(36) NOT NULL,
                amount INT NOT NULL,
                source VARCHAR(64) NOT NULL,
                description TEXT,
                actor_id VARCHAR(36),
                timestamp BIGINT NOT NULL
            )
            """.trimIndent(),
        )
    }
}

private fun ResultSet.experiencePools(): Map<String, Int> {
    val pools = mutableMapOf<String, Int>()
    while (next()) pools[getString("source_pool")] = getInt("awarded_xp")
    return pools
}

private const val PERIOD_END_PARAMETER = 3
