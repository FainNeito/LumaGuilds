package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.GuildStallReadService
import net.lumalyte.lg.application.services.StallReadResult
import net.lumalyte.lg.domain.achievements.*
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.Statistic
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.json.JSONArray
import org.json.JSONObject
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildAchievementProgressRepositorySQL
import java.util.UUID

/** Server-thread adapter. Progress, claimed IDs and personal XP share Minecraft's player-data save. */
internal class GuildAchievementRuntime(
    private val plugin: JavaPlugin,
    private val members: MemberRepository,
    private val progression: ProgressionRepository,
    private val stalls: GuildStallReadService = GuildStallReadClient(),
    private val guildXp: GuildAchievementXpCompletionPort? = null,
    private val history: net.lumalyte.lg.infrastructure.persistence.guilds.GuildAdvancementHistorySQL? = null,
    private val guildProgressFactory: ((List<GuildAchievement>) -> GuildAchievementProgressRepositorySQL)? = null,
    private val foundingProcessor: ((GuildAchievementProgressRepositorySQL) -> GuildAchievementGuildEventProcessor)? = null,
) {
    private val key by lazy { NamespacedKey(plugin, "guild_achievements_v1") }
    val engine by lazy { GuildAchievementEngine(loadDefinitions()) }
    private val projection by lazy { GuildAchievementProjection(plugin, engine.definitions) }
    private val dirty = mutableSetOf<UUID>()
    private val failed = mutableSetOf<UUID>()
    private val records = mutableMapOf<UUID, AchievementRecord>()
    private val pendingGuildXp = mutableMapOf<UUID, List<net.lumalyte.lg.application.services.GuildAchievementXpCoordinator.Intent>>()
    // These completions could not be evaluated at completion time. They are never auto-paid.
    private val guildXpReview = mutableMapOf<UUID, List<net.lumalyte.lg.application.services.GuildAchievementXpCoordinator.Intent>>()
    private val reviewRequired = mutableSetOf<UUID>()
    private val ownershipReads = mutableSetOf<UUID>()
    private data class TimeSample(val roster: Map<UUID, java.time.Instant>, val ticks: Map<UUID, Long>) {
        val hours = (ticks.values.sum() / 72000L).coerceAtMost(1000L).toInt()
    }
    private val timeSamples = mutableMapOf<UUID, TimeSample>()
    // Bound repeat writes for stable milestones; the database remains authoritative after eviction.
    private val observedUnlocks = object : LinkedHashMap<Pair<UUID, String>, java.time.Instant>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<UUID, String>, java.time.Instant>?) = size > 4096
    }
    private val observedLevels = mutableMapOf<UUID, Int>()
    private val observedStallOwnership = mutableMapOf<UUID, Boolean>()

    private val guildProgress by lazy { guildProgressFactory?.invoke(engine.definitions) }
    private val founding by lazy { guildProgress?.let { foundingProcessor?.invoke(it) } }
    private val foundingSessions by lazy {
        val secret = ByteArray(32).also(java.security.SecureRandom()::nextBytes)
        GuildAchievementOnlineSnapshot(
            { plugin.config.getBoolean("advancement-guild-xp.trusted-local-session-view", false) && Bukkit.isPrimaryThread() },
            { guild -> members.getByGuild(guild).map { it.playerId }.toSet() },
            GuildAchievementBukkitSessions()::read, GuildAchievementAddressFingerprint(secret),
        )
    }

    /** Called by the create command only after its payment service confirms success. */
    fun confirmedFounding(result: net.lumalyte.lg.application.services.GuildCreationCostResult.Applied, founder: Player) {
        val guild = result.guild
        remember(guild.id, listOf("founding"), guild.createdAt)
        if (!plugin.config.getBoolean("advancement-guild-xp.founding-test-enabled", false)) return
        try {
            val processor = checkNotNull(founding)
            val view = checkNotNull(foundingSessions.capture(guild.id)) { "Session view unavailable" }
            check(founder.uniqueId in view.online) { "Founder session unavailable" }
            val event = GuildAchievementGuildEventProcessor.Event(
                guild.id, guild.id, guild.createdAt, "GUILD_CREATE", null, 1, view.members,
                setOf(founder.uniqueId), view.fingerprints, view.online,
            )
            if (processor.process(event) != null) progression.refreshGuildProgression(guild.id)
        } catch (error: Exception) {
            plugin.logger.warning("Founding XP requires review for guild ${guild.id}: ${error.javaClass.simpleName}")
        }
    }

    fun start() {
        projection.register()
        plugin.server.pluginManager.registerEvents(GuildAchievementListener(this), plugin)
        runCatching { GuildAchievementCompanions(plugin, this).start() }
            .onFailure { plugin.logger.severe("Achievement companion state unavailable; core tracking remains active: ${it.javaClass.simpleName}") }
        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            Bukkit.getOnlinePlayers().forEach { player ->
                if (player.uniqueId in dirty) runCatching { flush(player) }
                retryGuildXp(player)
                recoverSales(player)
            }
            Bukkit.getOnlinePlayers().groupBy { guild(it.uniqueId) }.forEach { (guild, players) ->
                if (guild != null) {
                    refreshStallOwnership(players.first(), guild)
                    observeGuildTotals(guild)
                    players.forEach { player ->
                        initializeRoot(player)
                        read(player)?.let { projection.project(player, it) }
                    }
                }
            }
        }, 100L, 1200L)
    }

    fun stop() {
        Bukkit.getOnlinePlayers().filter { it.uniqueId in dirty }.forEach { flush(it) }
        projection.close()
    }

    fun guild(playerId: UUID): UUID? = members.getGuildsByPlayer(playerId).sortedBy { it.toString() }.firstOrNull()
    fun isMember(playerId: UUID, guildId: UUID): Boolean = guildId in members.getGuildsByPlayer(playerId)
    fun online(guildId: UUID): List<Player> = members.getByGuild(guildId).mapNotNull { Bukkit.getPlayer(it.playerId) }

    /** Let vanilla's join-time advancement reset finish before the first custom definition packet. */
    fun initializeAfterJoin(player: Player) {
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (player.isOnline) initialize(player)
        }, 20L)
    }

    fun initialize(player: Player) {
        read(player) ?: return
        retryGuildXp(player)
        val guild = guild(player.uniqueId) ?: return
        read(player)?.let { record ->
            val pending = engine.pending(record)
            if (pending.completed.isNotEmpty()) deliver(player, record, pending)
        }
        initializeRoot(player)
        observeGuildTotals(guild, creditCrossing = false)
        read(player)?.let { projection.project(player, it) }
        refreshStallOwnership(player, guild)
    }

    private fun initializeRoot(player: Player) {
        val record = read(player) ?: return
        val root = engine.advance(record, "ROOT")
        if (root.record != record) {
            write(player, root.record)
            flush(player)
        }
    }

    private fun remember(guild: UUID, nodes: List<String>, at: java.time.Instant = java.time.Instant.now()) {
        try {
            val store = history ?: return
            val missing = nodes.filter { id -> observedUnlocks[guild to id]?.let { at < it } ?: true }
            if (missing.isEmpty()) return
            store.remember(guild, missing, at)
            missing.forEach { observedUnlocks[guild to it] = at }
        } catch (error: Exception) {
            plugin.logger.warning("Could not record guild advancement history for $guild: ${error.javaClass.simpleName}")
        }
    }

    fun memberJoined(player: Player, guild: UUID) {
        if (!isMember(player.uniqueId, guild)) return
        observeGuildTotals(guild, creditCrossing = false)
        val count = try {
            history?.count(guild) ?: return
        } catch (error: Exception) {
            plugin.logger.warning("Guild advancement summary unavailable for $guild: ${error.javaClass.simpleName}")
            return
        }
        if (count > 0) player.sendMessage(net.kyori.adventure.text.Component.text(
            "Guild advancements already unlocked: $count. View them in Guild Advancements.",
            net.kyori.adventure.text.format.NamedTextColor.GRAY,
        ))
    }

    fun guildUnlockObserved(guild: UUID, node: String): java.time.Instant? = try {
        history?.observed(guild, node)
    } catch (error: Exception) {
        plugin.logger.warning("Guild advancement history unavailable for $guild: ${error.javaClass.simpleName}")
        null
    }

    /** Only events sufficient on their own prove a guild unlock; personal totals can span guilds. */
    private val eventMilestones by lazy { engine.definitions.filter { it.goal == 1 || it.requirement == "KOTH_CONSECUTIVE_CAPTURE" }
        .groupBy { it.requirement } }

    private fun observeEvent(guild: UUID, type: String, target: String?, amount: Int) {
        if (history == null || type == "GUILD_CREATE" || type == "ROOT") return
        val nodes = eventMilestones[type] ?: return
        val unlocked = nodes.filter { it.goal <= amount && engine.matches(it, target) }
        remember(guild, unlocked.map { it.id })
    }

    private fun observeGuildTotals(guild: UUID, creditCrossing: Boolean = true) {
        val level = progression.getGuildProgression(guild)?.currentLevel
        if (level != null) observedLevels.putIfAbsent(guild, level)
        val roster = members.getByGuild(guild).associate { it.playerId to it.joinedAt }
        val sample = TimeSample(roster, roster.keys.associateWith {
            Bukkit.getOfflinePlayer(it).getStatistic(Statistic.PLAY_ONE_MINUTE).toLong()
        })
        val previous = timeSamples[guild]
        if (creditCrossing || previous?.roster != roster) timeSamples[guild] = sample
        val hours = sample.hours
        if (creditCrossing && previous != null && previous.roster == roster && previous.hours < 1000 && hours >= 1000) {
            online(guild).filter { sample.ticks.getValue(it.uniqueId) > previous.ticks.getValue(it.uniqueId) }.forEach {
                signal(it, "GUILD_COMBINED_PLAYTIME", amount = hours, absolute = true, newEvent = true)
            }
        }
        val unlocked = engine.definitions.filter { node ->
            when (node.requirement) {
                "GUILD_LEVEL_UP" -> level != null && level >= (node.target?.toIntOrNull() ?: Int.MAX_VALUE)
                "GUILD_COMBINED_PLAYTIME" -> hours >= node.goal
                else -> false
            }
        }
        remember(guild, unlocked.map { it.id })
    }

    /** Existing ownership qualifies. Unavailable or late data never invents a purchase or an award. */
    internal fun refreshStallOwnership(viewer: Player, guildId: UUID) {
        if (!ownershipReads.add(guildId)) return
        runCatching { stalls.read(guildId, viewer.uniqueId) }.getOrElse {
            ownershipReads.remove(guildId)
            return
        }.whenComplete { result, error ->
            plugin.server.scheduler.runTask(plugin, Runnable {
                ownershipReads.remove(guildId)
                if (error == null && viewer.isOnline && guild(viewer.uniqueId) == guildId && result is StallReadResult.Available) {
                    val owned = result.stalls.any { it.state in setOf("OWNED", "GRACE") }
                    val previous = observedStallOwnership.put(guildId, owned)
                    if (!owned) return@Runnable
                    remember(guildId, listOf("prime_real_estate"))
                    if (previous != false) return@Runnable
                    online(guildId).filter { isMember(it.uniqueId, guildId) }.forEach {
                        signal(it, "GUILD_STALL_OWNED", absolute = true, newEvent = previous == false)
                    }
                }
            })
        }
    }

    fun read(player: Player): AchievementRecord? = runCatching {
        check(Bukkit.isPrimaryThread())
        check(player.uniqueId !in failed) { "Achievement record is quarantined until reconnect/review" }
        records[player.uniqueId]?.let { return@runCatching it }
        val raw = player.persistentDataContainer.get(key, PersistentDataType.STRING) ?: return@runCatching AchievementRecord()
        val json = JSONObject(raw)
        require(json.getInt("schema") == 1)
        val pending = if (json.has("guildXpPending")) json.getJSONArray("guildXpPending") else JSONArray()
        pendingGuildXp[player.uniqueId] = GuildAchievementXpOutboxCodec.decode(pending)
        val review = if (json.has("guildXpReview")) json.getJSONArray("guildXpReview") else JSONArray()
        guildXpReview[player.uniqueId] = GuildAchievementXpOutboxCodec.decode(review)
        check(!json.optBoolean("reviewRequired", false)) { "Ambiguous XP delivery requires administrator review" }
        val counts = json.getJSONObject("progress")
        val progress = counts.keySet().associateWith { id ->
            val count = counts.getInt(id)
            require(count >= 0)
            engine.definitions.find { it.id == id }?.let { require(count <= it.goal) }
            count
        }
        AchievementRecord(progress, strings(json.getJSONArray("rewarded")), strings(json.getJSONArray("receipts")),
            json.optString("tracked").takeIf { it.isNotBlank() && it != "null" }).also { records[player.uniqueId] = it }
    }.onFailure { error ->
        if (failed.add(player.uniqueId)) plugin.logger.warning("Guild achievements unavailable for ${player.uniqueId}: ${error.javaClass.simpleName}; retained data was not cleared")
    }.getOrNull()

    fun track(player: Player, id: String?): Boolean {
        if (guild(player.uniqueId) == null || id != null && engine.definitions.none { it.id == id }) return false
        val before = read(player) ?: return false
        write(player, before.copy(tracked = id))
        flush(player)
        return true
    }

    fun signal(player: Player, type: String, target: String? = null, amount: Int = 1,
        absolute: Boolean = false, receipt: String? = null, newEvent: Boolean = false, allowedNodes: Set<String>? = null) {
        if (!Bukkit.isPrimaryThread()) {
            plugin.server.scheduler.runTask(plugin, Runnable { if (player.isOnline) signal(player, type, target, amount, absolute, receipt, newEvent, allowedNodes) })
            return
        }
        if (!player.isOnline) return
        val guildId = guild(player.uniqueId) ?: return
        if (newEvent) observeEvent(guildId, type, target, amount)
        val before = read(player) ?: return
        val change = engine.advance(before, type, target, amount, absolute, receipt, allowedNodes)
        if (change.record == before) return
        val bridge = guildXp
        val review = mutableListOf<net.lumalyte.lg.application.services.GuildAchievementXpCoordinator.Intent>()
        val prepared = if (newEvent && bridge != null) change.completed.mapNotNull { node ->
            val amount = GuildAchievementXpPolicy.reward(node)
            if (amount == 0) return@mapNotNull null
            val candidate = net.lumalyte.lg.application.services.GuildAchievementXpCoordinator.Intent(
                guildId, node.id, amount, GuildAchievementXpPolicy.transactionId(guildId, node.id))
            runCatching {
                val result = bridge.prepare(player, guildId, node)
                if (result is net.lumalyte.lg.application.services.GuildAchievementXpCoordinator.Prepared.Denied &&
                    result.result in setOf(net.lumalyte.lg.application.services.GuildAchievementXpCoordinator.Result.UNKNOWN_ADDRESS,
                        net.lumalyte.lg.application.services.GuildAchievementXpCoordinator.Result.UNAVAILABLE)) review += candidate
                (result as? net.lumalyte.lg.application.services.GuildAchievementXpCoordinator.Prepared.Eligible)?.intent?.also {
                    require(it.guildId == guildId && it.achievementId == node.id)
                    require(it.amount == GuildAchievementXpPolicy.reward(node) && it.amount > 0)
                    require(it.transactionId == GuildAchievementXpPolicy.transactionId(guildId, node.id))
                }
            }.onFailure {
                review += candidate
                plugin.logger.warning("Guild achievement XP eligibility unavailable; completion retained for review")
            }.getOrNull()
        } else emptyList()
        deliver(player, before, change, prepared, review)
    }

    private fun deliver(player: Player, before: AchievementRecord, change: AchievementChange,
        prepared: List<net.lumalyte.lg.application.services.GuildAchievementXpCoordinator.Intent> = emptyList(),
        review: List<net.lumalyte.lg.application.services.GuildAchievementXpCoordinator.Intent> = emptyList()) {
        val xp = change.completed.sumOf { it.personalXp }
        val oldXp = Triple(player.totalExperience, player.level, player.exp)
        val oldPending = pendingGuildXp[player.uniqueId].orEmpty()
        val oldReview = guildXpReview[player.uniqueId].orEmpty()
        try {
            val pending = (oldPending + prepared).distinctBy { it.transactionId }
            GuildAchievementXpOutboxCodec.encode(pending) // validate before changing player XP
            val reviews = (oldReview + review).distinctBy { it.transactionId }
            GuildAchievementXpOutboxCodec.encode(reviews)
            pendingGuildXp[player.uniqueId] = pending
            guildXpReview[player.uniqueId] = reviews
            // Claimed IDs, pending guild intents and personal experience share the player save.
            write(player, change.record)
            if (xp > 0) player.giveExp(xp)
            if (change.completed.isNotEmpty()) flush(player)
            if (change.completed.isNotEmpty()) projection.project(player, change.record)
            change.completed.forEach { announce(player, it) }
            if (change.completed.isNotEmpty()) retryGuildXp(player)
        } catch (error: Exception) {
            // Only a verified unchanged XP outcome is retryable. Ambiguous outcomes retain claimed IDs.
            if (Triple(player.totalExperience, player.level, player.exp) == oldXp) {
                pendingGuildXp[player.uniqueId] = oldPending
                guildXpReview[player.uniqueId] = oldReview
                write(player, change.record.copy(rewarded = before.rewarded))
                runCatching { flush(player) }
            } else {
                reviewRequired += player.uniqueId
                runCatching { flush(player) }
            }
            failed += player.uniqueId
            plugin.logger.severe("Achievement delivery needs review for ${player.uniqueId}; no automatic replay: ${error.javaClass.simpleName}")
        }
    }

    private fun announce(player: Player, node: GuildAchievement) {
        projection.celebrate(player, node.id)
        player.sendMessage(net.kyori.adventure.text.Component.text(
            "Guild achievement: ${node.title}", net.kyori.adventure.text.format.NamedTextColor.GOLD,
        ))
        node.messages.forEach {
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(it))
        }
        if (node.personalXp > 0) {
            player.sendMessage(net.kyori.adventure.text.Component.text("+${node.personalXp} personal Minecraft XP"))
        }
    }

    fun levelReached(guildId: UUID, level: Int) {
        val previous = observedLevels.put(guildId, level) ?: (level - 1)
        val earned = engine.definitions.filter {
            it.requirement == "GUILD_LEVEL_UP" && (it.target?.toIntOrNull() ?: 0) in (previous + 1)..level
        }
        remember(guildId, earned.map { it.id })
        if (earned.isEmpty()) return
        online(guildId).forEach { signal(it, "GUILD_LEVEL_UP", level.toString(), newEvent = true,
            allowedNodes = earned.mapTo(mutableSetOf()) { node -> node.id }) }
    }

    /** A repeated Market complete callback is the same sale, including after a server restart. */
    fun confirmedMarketSale(guild: UUID, sale: UUID) {
        if (!Bukkit.isPrimaryThread()) {
            plugin.server.scheduler.runTask(plugin, Runnable { confirmedMarketSale(guild, sale) })
            return
        }
        val started = checkNotNull(history?.saleStartedAt(guild, sale)) { "Market sale receipt unavailable" }
        val repository = checkNotNull(guildProgress)
        val requirement = "GUILD_SHOP_SALE"
        val recipients = members.getByGuild(guild).filter {
            it.joinedAt <= started && Bukkit.getPlayer(it.playerId)?.isOnline == true && guild(it.playerId) == guild
        }.associate { it.playerId to it.joinedAt.toEpochMilli() }
        val result = repository.applySale(guild, sale, recipients)
        // A replay can repair a missed history write without advancing personal progress.
        remember(guild, engine.definitions.filter {
            it.requirement == requirement && result.progress.getOrDefault(it.id, 0) >= it.goal
        }.map { it.id })
        recipients.keys.forEach { Bukkit.getPlayer(it)?.let(::recoverSales) }
    }

    private fun recoverSales(player: Player) {
        try {
            val repository = guildProgress ?: return
            for (credit in repository.pendingSales(player.uniqueId)) {
                val membership = members.getByPlayerAndGuild(player.uniqueId, credit.guild)
                if (membership?.joinedAt?.toEpochMilli() != credit.membershipMillis) {
                    repository.finishSale(player.uniqueId, credit)
                    continue
                }
                if (guild(player.uniqueId) != credit.guild) continue
                val receipt = "market:${credit.sale}"
                signal(player, "GUILD_SHOP_SALE", receipt = receipt)
                val record = read(player) ?: return
                val capped = engine.definitions.filter { it.requirement == "GUILD_SHOP_SALE" }.all { it.id in record.rewarded }
                if (receipt !in record.receipts && !capped) return
                if (player.uniqueId in dirty) flush(player)
                repository.finishSale(player.uniqueId, credit)
            }
        } catch (error: Exception) {
            plugin.logger.warning("Market advancement delivery pending for ${player.uniqueId}: ${error.javaClass.simpleName}")
        }
    }

    fun shared(guildId: UUID, type: String, target: String? = null, amount: Int = 1,
        absolute: Boolean = false, receipt: String? = null) {
        if (!Bukkit.isPrimaryThread()) {
            plugin.server.scheduler.runTask(plugin, Runnable { shared(guildId, type, target, amount, absolute, receipt) })
            return
        }
        observeEvent(guildId, type, target, amount)
        online(guildId).forEach { signal(it, type, target, amount, absolute, receipt, newEvent = true) }
    }

    fun projectionAvailable(): Boolean = projection.available

    fun refreshProjection() { if (!projection.available) projection.register() }

    fun leave(player: Player) {
        if (player.uniqueId in dirty) flush(player)
        records.remove(player.uniqueId)
        pendingGuildXp.remove(player.uniqueId)
        guildXpReview.remove(player.uniqueId)
        failed.remove(player.uniqueId)
    }

    private fun write(player: Player, record: AchievementRecord) {
        records[player.uniqueId] = record
        dirty += player.uniqueId
    }

    private fun flush(player: Player) {
        val record = records[player.uniqueId] ?: return
        val json = JSONObject().put("schema", 1).put("progress", JSONObject(record.progress))
            .put("rewarded", JSONArray(record.rewarded.toList())).put("receipts", JSONArray(record.receipts.toList()))
            .put("tracked", record.tracked ?: JSONObject.NULL)
            .put("reviewRequired", player.uniqueId in reviewRequired)
            .put("guildXpPending", GuildAchievementXpOutboxCodec.encode(pendingGuildXp[player.uniqueId].orEmpty()))
            .put("guildXpReview", GuildAchievementXpOutboxCodec.encode(guildXpReview[player.uniqueId].orEmpty()))
        player.persistentDataContainer.set(key, PersistentDataType.STRING, json.toString())
        player.saveData()
        dirty.remove(player.uniqueId)
    }

    private fun retryGuildXp(player: Player) {
        val bridge = guildXp ?: return
        if (player.uniqueId in failed || player.uniqueId in dirty) return
        val before = pendingGuildXp[player.uniqueId].orEmpty()
        if (before.isEmpty()) return
        val remaining = before.filter { intent ->
            val outcome = runCatching { bridge.deliver(intent) }.getOrNull()
            outcome != net.lumalyte.lg.application.services.GuildAchievementXpCoordinator.Result.AWARDED &&
                outcome != net.lumalyte.lg.application.services.GuildAchievementXpCoordinator.Result.DUPLICATE
        }
        if (remaining != before) {
            pendingGuildXp[player.uniqueId] = remaining
            dirty += player.uniqueId
            runCatching { flush(player) }
        }
    }

    private fun strings(array: JSONArray): Set<String> = (0 until array.length()).map { array.getString(it) }.toSet()

    private fun loadDefinitions(): List<GuildAchievement> {
        val raw = requireNotNull(plugin.getResource("guild-achievements.json")).bufferedReader().use { it.readText() }
        val array = JSONObject(raw).getJSONArray("nodes")
        return (0 until array.length()).map { i ->
            val n = array.getJSONObject(i)
            fun list(name: String) = n.getJSONArray(name).let { a -> (0 until a.length()).map { a.getString(it) } }
            GuildAchievement(n.getString("id"), n.optString("parent").takeUnless { it.isBlank() || it == "null" },
                n.getString("title"), list("description"), n.getString("requirement"),
                n.optString("target").takeUnless { it.isBlank() || it == "null" }, n.getInt("goal"),
                n.getInt("personalXp"), list("messages"), n.getString("category"), n.getString("icon"),
                n.getString("frame"), n.getFloat("x"), n.getFloat("y"))
        }
    }
}
