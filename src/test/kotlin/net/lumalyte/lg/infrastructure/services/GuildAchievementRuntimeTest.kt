package net.lumalyte.lg.infrastructure.services

import io.mockk.*
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.GuildAchievementXpCoordinator
import net.lumalyte.lg.domain.achievements.GuildAchievementXpPolicy
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import java.util.UUID
import kotlin.test.*

internal class GuildAchievementRuntimeTest {
    @AfterEach fun cleanup() { unmockkAll(); MockBukkit.unmock() }

    @Test fun `market replay and joining after sale cannot grant personal sale credit`() {
        val server = MockBukkit.mock()
        val plugin = spyk(MockBukkit.createMockPlugin("LumaGuilds"))
        every { plugin.getResource("guild-achievements.json") } answers { javaClass.classLoader.getResourceAsStream("guild-achievements.json") }
        val guild = UUID.randomUUID()
        val sale = UUID.randomUUID()
        val player = server.addPlayer()
        val at = java.time.Instant.now()
        val members = mockk<MemberRepository>()
        every { members.getByGuild(guild) } returns setOf(net.lumalyte.lg.domain.entities.Member(
            player.uniqueId, guild, UUID.randomUUID(), at.plusSeconds(1)))
        val history = mockk<net.lumalyte.lg.infrastructure.persistence.guilds.GuildAdvancementHistorySQL>(relaxed = true)
        every { history.saleStartedAt(guild, sale) } returns at
        val progress = mockk<net.lumalyte.lg.infrastructure.persistence.guilds.GuildAchievementProgressRepositorySQL>()
        val reached = net.lumalyte.lg.infrastructure.persistence.guilds.GuildAchievementProgressRepositorySQL.Result(
            mapOf("art_of_the_deal" to 1, "supply_chain" to 100), setOf("supply_chain"), false)
        every { progress.apply(guild, any(), "GUILD_SHOP_SALE", null, 1) } returnsMany listOf(reached, reached.copy(duplicate = true))
        val runtime = GuildAchievementRuntime(plugin, members, mockk(), history = history, guildProgressFactory = { progress })
        repeat(2) { runtime.confirmedMarketSale(guild, sale) }
        verify(exactly = 1) { history.remember(guild, listOf("art_of_the_deal", "supply_chain"), any()) }
        verify(exactly = 1) { members.getByGuild(guild) }
        assertTrue(runtime.read(player)!!.progress.isEmpty())
    }

    @Test fun `confirmed founding routes the new guild and only its founder with a trusted session`() {
        val server = MockBukkit.mock()
        val plugin = spyk(MockBukkit.createMockPlugin("LumaGuilds"))
        every { plugin.getResource("guild-achievements.json") } answers { javaClass.classLoader.getResourceAsStream("guild-achievements.json") }
        val player = spyk(server.addPlayer())
        every { player.isOnline } returns true
        every { player.address } returns java.net.InetSocketAddress("192.0.2.15", 25565)
        mockkStatic(org.bukkit.Bukkit::class)
        every { org.bukkit.Bukkit.isPrimaryThread() } returns true
        every { org.bukkit.Bukkit.getOnlinePlayers() } returns listOf(player)
        val guild = net.lumalyte.lg.domain.entities.Guild(UUID.randomUUID(), "FoundingTest", createdAt = java.time.Instant.now())
        val applied = net.lumalyte.lg.application.services.GuildCreationCostResult.Applied(guild, 2000)
        val members = mockk<MemberRepository>()
        every { members.getByGuild(guild.id) } returns setOf(net.lumalyte.lg.domain.entities.Member(
            player.uniqueId, guild.id, UUID.randomUUID(), guild.createdAt))
        val processor = mockk<GuildAchievementGuildEventProcessor>()
        val event = slot<GuildAchievementGuildEventProcessor.Event>()
        every { processor.process(capture(event)) } returns null
        val runtime = GuildAchievementRuntime(plugin, members, mockk(), guildProgressFactory = { mockk() }, foundingProcessor = { processor })
        runtime.confirmedFounding(applied, player)
        plugin.config.set("advancement-guild-xp.founding-test-enabled", true)
        runtime.confirmedFounding(applied, player)
        verify(exactly = 0) { processor.process(any()) }
        plugin.config.set("advancement-guild-xp.trusted-local-session-view", true)
        runtime.confirmedFounding(applied, player)
        verify(exactly = 1) { processor.process(any()) }
        assertEquals(guild.id, event.captured.guild)
        assertEquals(guild.id, event.captured.source)
        assertEquals(guild.createdAt, event.captured.occurredAt)
        assertEquals(setOf(player.uniqueId), event.captured.participants)
        assertEquals("GUILD_CREATE", event.captured.requirement)
        assertNotNull(event.captured.current[player.uniqueId])
    }

    @Test fun `unknown eligibility is retained for review and never automatically paid`() {
        val server = MockBukkit.mock()
        val plugin = spyk(MockBukkit.createMockPlugin("LumaGuilds"))
        every { plugin.getResource("guild-achievements.json") } answers { javaClass.classLoader.getResourceAsStream("guild-achievements.json") }
        val player = spyk(server.addPlayer())
        every { player.isOnline } returns true
        every { player.saveData() } just Runs
        every { player.giveExp(any<Int>()) } just Runs
        val guild = UUID.randomUUID()
        val members = mockk<MemberRepository>()
        every { members.getGuildsByPlayer(player.uniqueId) } returns setOf(guild)
        val bridge = mockk<GuildAchievementXpCompletionPort>()
        every { bridge.prepare(player, guild, any()) } returns GuildAchievementXpCoordinator.Prepared.Denied(GuildAchievementXpCoordinator.Result.UNKNOWN_ADDRESS)
        val runtime = GuildAchievementRuntime(plugin, members, mockk(), guildXp = bridge)
        runtime.signal(player, "GUILD_WAR_WON", receipt = "war-review", newEvent = true)
        val key = NamespacedKey(plugin, "guild_achievements_v1")
        val saved = org.json.JSONObject(player.persistentDataContainer.get(key, PersistentDataType.STRING)!!)
        assertEquals("to_the_victor", saved.getJSONArray("guildXpReview").getJSONObject(0).getString("achievement"))
        assertEquals(0, saved.getJSONArray("guildXpPending").length())
        assertTrue("to_the_victor" in runtime.read(player)!!.rewarded)
        val reopened = GuildAchievementRuntime(plugin, members, mockk(), guildXp = bridge)
        assertTrue("to_the_victor" in reopened.read(player)!!.rewarded)
        verify(exactly = 0) { bridge.deliver(any()) }
        verify(exactly = 1) { player.giveExp(300) }
    }

    @Test fun `initial projection waits until after the vanilla join reset`() {
        val server = MockBukkit.mock()
        val plugin = spyk(MockBukkit.createMockPlugin("LumaGuilds"))
        every { plugin.getResource("guild-achievements.json") } answers { javaClass.classLoader.getResourceAsStream("guild-achievements.json") }
        val player = spyk(server.addPlayer())
        every { player.isOnline } returns true
        every { player.saveData() } just Runs
        val guild = UUID.randomUUID()
        val members = mockk<MemberRepository>()
        every { members.getGuildsByPlayer(player.uniqueId) } returns setOf(guild)
        every { members.getByGuild(guild) } returns emptySet()
        val progression = mockk<ProgressionRepository>()
        every { progression.getGuildProgression(guild) } returns mockk { every { currentLevel } returns 20 }
        val stalls = mockk<net.lumalyte.lg.application.services.GuildStallReadService>()
        every { stalls.read(guild, player.uniqueId) } returns java.util.concurrent.CompletableFuture.completedFuture(
            net.lumalyte.lg.application.services.StallReadResult.Unavailable)
        val runtime = GuildAchievementRuntime(plugin, members, progression, stalls)
        runtime.initializeAfterJoin(player)
        assertTrue(runtime.read(player)!!.progress.isEmpty())
        server.scheduler.performTicks(19L)
        assertTrue(runtime.read(player)!!.progress.isEmpty())
        server.scheduler.performTicks(2L)
        assertEquals(setOf("guilds_root"), runtime.read(player)!!.rewarded)
        runtime.levelReached(guild, 21)
        runtime.leave(player)
        runtime.initialize(player)
        assertEquals(setOf("guilds_root"), runtime.read(player)!!.rewarded)
        verify(exactly = 0) { player.sendMessage(any<net.kyori.adventure.text.Component>()) }
        verify(exactly = 0) { player.giveExp(any<Int>()) }
    }

    @Test fun `existing owned stall qualifies once but unavailable unowned and stale results do not`() {
        val server = MockBukkit.mock()
        val plugin = spyk(MockBukkit.createMockPlugin("LumaGuilds"))
        every { plugin.getResource("guild-achievements.json") } answers { javaClass.classLoader.getResourceAsStream("guild-achievements.json") }
        val player = spyk(server.addPlayer())
        every { player.isOnline } returns true
        every { player.saveData() } just Runs
        mockkStatic(org.bukkit.Bukkit::class)
        every { org.bukkit.Bukkit.getPlayer(player.uniqueId) } returns player
        val guild = UUID.randomUUID()
        var memberships = setOf(guild)
        val members = mockk<MemberRepository>()
        every { members.getGuildsByPlayer(player.uniqueId) } answers { memberships }
        every { members.getByGuild(guild) } returns setOf(net.lumalyte.lg.domain.entities.Member(
            player.uniqueId, guild, UUID.randomUUID(), java.time.Instant.EPOCH))
        val stalls = mockk<net.lumalyte.lg.application.services.GuildStallReadService>()
        var next = java.util.concurrent.CompletableFuture<net.lumalyte.lg.application.services.StallReadResult>()
        every { stalls.read(guild, player.uniqueId) } answers { next }
        val runtime = GuildAchievementRuntime(plugin, members, mockk(), stalls)
        fun result(state: String) = net.lumalyte.lg.application.services.StallReadResult.Available(listOf(
            net.lumalyte.lg.application.services.GuildStallInfo("stall", "region", "world", state, 1, 60, null, null, null, emptyList())))
        fun complete(value: net.lumalyte.lg.application.services.StallReadResult) {
            runtime.refreshStallOwnership(player, guild)
            next.complete(value)
            server.scheduler.performOneTick()
            next = java.util.concurrent.CompletableFuture()
        }
        complete(result("OWNED"))
        assertNull(runtime.read(player)!!.progress["prime_real_estate"])
        complete(net.lumalyte.lg.application.services.StallReadResult.Unavailable)
        complete(result("UNOWNED"))
        assertNull(runtime.read(player)!!.progress["prime_real_estate"])
        runtime.refreshStallOwnership(player, guild)
        memberships = emptySet()
        next.complete(result("OWNED"))
        server.scheduler.performOneTick()
        assertNull(runtime.read(player)!!.progress["prime_real_estate"])
        memberships = setOf(guild)
        next = java.util.concurrent.CompletableFuture()
        complete(result("GRACE"))
        complete(result("OWNED"))
        assertEquals(1, runtime.read(player)!!.progress["prime_real_estate"])
        assertTrue("prime_real_estate" in runtime.read(player)!!.rewarded)
        verify(exactly = 0) { player.giveExp(any<Int>()) }
    }

    @Test fun `completion and track survive a new runtime without paying again`() {
        val server = MockBukkit.mock()
        val plugin = spyk(MockBukkit.createMockPlugin("LumaGuilds"))
        every { plugin.getResource("guild-achievements.json") } answers {
            javaClass.classLoader.getResourceAsStream("guild-achievements.json")
        }
        val player = spyk(server.addPlayer())
        every { player.isOnline } returns true
        every { player.saveData() } just Runs
        var paid = 0
        every { player.giveExp(any<Int>()) } answers { paid += firstArg<Int>() }
        val guild = UUID.randomUUID()
        val members = mockk<MemberRepository>()
        every { members.getGuildsByPlayer(player.uniqueId) } returns setOf(guild)
        val progress = mockk<ProgressionRepository>()
        val first = GuildAchievementRuntime(plugin, members, progress)
        first.signal(player, "GUILD_WAR_WON", receipt = "war-123")
        assertEquals(300, paid)
        assertTrue(first.track(player, "to_the_victor"))
        val reopened = GuildAchievementRuntime(plugin, members, progress)
        assertEquals("to_the_victor", reopened.read(player)!!.tracked)
        reopened.signal(player, "GUILD_WAR_WON", receipt = "war-123")
        reopened.signal(player, "GUILD_WAR_WON", receipt = "war-456")
        assertEquals(300, paid)
        assertEquals(30, reopened.engine.definitions.size)
        assertEquals(30, reopened.engine.definitions.map { it.x to it.y }.distinct().size,
            "Vanilla advancement frames must not overlap")
    }

    @Test fun `malformed saved data is preserved and awards fail closed`() {
        val server = MockBukkit.mock()
        val plugin = spyk(MockBukkit.createMockPlugin("LumaGuilds"))
        every { plugin.getResource("guild-achievements.json") } answers { javaClass.classLoader.getResourceAsStream("guild-achievements.json") }
        val player = spyk(server.addPlayer())
        every { player.isOnline } returns true
        val key = NamespacedKey(plugin, "guild_achievements_v1")
        player.persistentDataContainer.set(key, PersistentDataType.STRING, "invalid retained data")
        val members = mockk<MemberRepository>()
        every { members.getGuildsByPlayer(player.uniqueId) } returns setOf(UUID.randomUUID())
        val subject = GuildAchievementRuntime(plugin, members, mockk())
        subject.signal(player, "GUILD_WAR_WON")
        assertNull(subject.read(player))
        assertEquals("invalid retained data", player.persistentDataContainer.get(key, PersistentDataType.STRING))
        verify(exactly = 0) { player.giveExp(any<Int>()) }
    }

    @Test fun `pending guild award survives personal save and runtime recreation`() {
        val server = MockBukkit.mock()
        val plugin = spyk(MockBukkit.createMockPlugin("LumaGuilds"))
        every { plugin.getResource("guild-achievements.json") } answers { javaClass.classLoader.getResourceAsStream("guild-achievements.json") }
        val player = spyk(server.addPlayer())
        every { player.isOnline } returns true
        every { player.saveData() } just Runs
        every { player.giveExp(any<Int>()) } just Runs
        val guild = UUID.randomUUID()
        var memberships = setOf(guild)
        val members = mockk<MemberRepository>()
        every { members.getGuildsByPlayer(player.uniqueId) } answers { memberships }
        every { members.getByGuild(guild) } returns emptySet()
        val progression = mockk<ProgressionRepository>()
        every { progression.getGuildProgression(guild) } returns null
        val stalls = mockk<net.lumalyte.lg.application.services.GuildStallReadService>()
        every { stalls.read(guild, player.uniqueId) } returns java.util.concurrent.CompletableFuture.completedFuture(
            net.lumalyte.lg.application.services.StallReadResult.Unavailable)
        val bridge = mockk<GuildAchievementXpCompletionPort>()
        val intent = GuildAchievementXpCoordinator.Intent(guild, "to_the_victor", 50,
            GuildAchievementXpPolicy.transactionId(guild, "to_the_victor"))
        every { bridge.prepare(player, guild, match { it.id == "to_the_victor" }) } returns GuildAchievementXpCoordinator.Prepared.Eligible(intent)
        every { bridge.deliver(intent) } returnsMany listOf(GuildAchievementXpCoordinator.Result.UNAVAILABLE, GuildAchievementXpCoordinator.Result.AWARDED)
        val first = GuildAchievementRuntime(plugin, members, progression, stalls, bridge)
        first.signal(player, "GUILD_WAR_WON", receipt = "war-1", newEvent = true)
        val key = NamespacedKey(plugin, "guild_achievements_v1")
        val saved = org.json.JSONObject(player.persistentDataContainer.get(key, PersistentDataType.STRING)!!)
        assertEquals(1, saved.getJSONArray("guildXpPending").length())
        memberships = emptySet()
        val reopened = GuildAchievementRuntime(plugin, members, progression, stalls, bridge)
        reopened.initialize(player)
        val after = org.json.JSONObject(player.persistentDataContainer.get(key, PersistentDataType.STRING)!!)
        assertEquals(0, after.getJSONArray("guildXpPending").length())
        verify(exactly = 1) { bridge.prepare(player, guild, match { it.id == "to_the_victor" }) }
        verify(exactly = 2) { bridge.deliver(intent) }
        verify(exactly = 1) { player.giveExp(300) }
    }

    @Test fun `historical synchronization never prepares guild XP`() {
        val server = MockBukkit.mock()
        val plugin = spyk(MockBukkit.createMockPlugin("LumaGuilds"))
        every { plugin.getResource("guild-achievements.json") } answers { javaClass.classLoader.getResourceAsStream("guild-achievements.json") }
        val player = spyk(server.addPlayer())
        every { player.isOnline } returns true
        every { player.saveData() } just Runs
        every { player.giveExp(any<Int>()) } just Runs
        val guild = UUID.randomUUID()
        val members = mockk<MemberRepository>()
        every { members.getGuildsByPlayer(player.uniqueId) } returns setOf(guild)
        val bridge = mockk<GuildAchievementXpCompletionPort>()
        val runtime = GuildAchievementRuntime(plugin, members, mockk(), mockk(), bridge)
        runtime.signal(player, "GUILD_WAR_WON", receipt = "old-war", newEvent = false)
        verify(exactly = 0) { bridge.prepare(any(), any(), any()) }
        verify(exactly = 0) { bridge.deliver(any()) }
        verify(exactly = 1) { player.giveExp(300) }
    }

    @Test fun `wrong guild reward intent is rejected without withholding personal XP`() {
        val server = MockBukkit.mock()
        val plugin = spyk(MockBukkit.createMockPlugin("LumaGuilds"))
        every { plugin.getResource("guild-achievements.json") } answers { javaClass.classLoader.getResourceAsStream("guild-achievements.json") }
        val player = spyk(server.addPlayer())
        every { player.isOnline } returns true
        every { player.saveData() } just Runs
        every { player.giveExp(any<Int>()) } just Runs
        val guild = UUID.randomUUID()
        val wrongGuild = UUID.randomUUID()
        val members = mockk<MemberRepository>()
        every { members.getGuildsByPlayer(player.uniqueId) } returns setOf(guild)
        val bridge = mockk<GuildAchievementXpCompletionPort>()
        val wrong = GuildAchievementXpCoordinator.Intent(wrongGuild, "to_the_victor", 50,
            GuildAchievementXpPolicy.transactionId(wrongGuild, "to_the_victor"))
        every { bridge.prepare(player, guild, any()) } returns GuildAchievementXpCoordinator.Prepared.Eligible(wrong)
        val runtime = GuildAchievementRuntime(plugin, members, mockk(), mockk(), bridge)
        runtime.signal(player, "GUILD_WAR_WON", receipt = "war-2", newEvent = true)
        verify(exactly = 0) { bridge.deliver(any()) }
        verify(exactly = 1) { player.giveExp(300) }
        val key = NamespacedKey(plugin, "guild_achievements_v1")
        val saved = org.json.JSONObject(player.persistentDataContainer.get(key, PersistentDataType.STRING)!!)
        assertEquals(0, saved.getJSONArray("guildXpPending").length())
    }

    @Test fun `malformed pending guild xp remains quarantined rather than silently discarded`() {
        val server = MockBukkit.mock()
        val plugin = spyk(MockBukkit.createMockPlugin("LumaGuilds"))
        every { plugin.getResource("guild-achievements.json") } answers { javaClass.classLoader.getResourceAsStream("guild-achievements.json") }
        val player = spyk(server.addPlayer())
        every { player.isOnline } returns true
        val key = NamespacedKey(plugin, "guild_achievements_v1")
        val original = """{"schema":1,"progress":{},"rewarded":[],"receipts":[],"guildXpPending":"invalid"}"""
        player.persistentDataContainer.set(key, PersistentDataType.STRING, original)
        val members = mockk<MemberRepository>()
        every { members.getGuildsByPlayer(player.uniqueId) } returns setOf(UUID.randomUUID())
        val runtime = GuildAchievementRuntime(plugin, members, mockk())
        runtime.signal(player, "GUILD_WAR_WON", newEvent = true)
        assertNull(runtime.read(player))
        assertEquals(original, player.persistentDataContainer.get(key, PersistentDataType.STRING))
        verify(exactly = 0) { player.giveExp(any<Int>()) }
    }

    @Test fun `new stall ownership after a nonowner baseline prepares guild XP`() {
        val server = MockBukkit.mock()
        val plugin = spyk(MockBukkit.createMockPlugin("LumaGuilds"))
        every { plugin.getResource("guild-achievements.json") } answers { javaClass.classLoader.getResourceAsStream("guild-achievements.json") }
        val player = spyk(server.addPlayer())
        every { player.isOnline } returns true
        every { player.saveData() } just Runs
        mockkStatic(org.bukkit.Bukkit::class)
        every { org.bukkit.Bukkit.getPlayer(player.uniqueId) } returns player
        val guild = UUID.randomUUID()
        val members = mockk<MemberRepository>()
        every { members.getGuildsByPlayer(player.uniqueId) } returns setOf(guild)
        every { members.getByGuild(guild) } returns setOf(net.lumalyte.lg.domain.entities.Member(
            player.uniqueId, guild, UUID.randomUUID(), java.time.Instant.EPOCH))
        val stalls = mockk<net.lumalyte.lg.application.services.GuildStallReadService>()
        var state = "UNOWNED"
        every { stalls.read(guild, player.uniqueId) } answers {
            java.util.concurrent.CompletableFuture.completedFuture(net.lumalyte.lg.application.services.StallReadResult.Available(listOf(
                net.lumalyte.lg.application.services.GuildStallInfo("stall", "region", "world", state, 1, 60, null, null, null, emptyList()))))
        }
        val bridge = mockk<GuildAchievementXpCompletionPort>()
        val intent = GuildAchievementXpCoordinator.Intent(guild, "prime_real_estate", 50,
            GuildAchievementXpPolicy.transactionId(guild, "prime_real_estate"))
        every { bridge.prepare(player, guild, match { it.id == "prime_real_estate" }) } returns GuildAchievementXpCoordinator.Prepared.Eligible(intent)
        every { bridge.deliver(intent) } returns GuildAchievementXpCoordinator.Result.AWARDED
        val runtime = GuildAchievementRuntime(plugin, members, mockk(), stalls, bridge)
        runtime.refreshStallOwnership(player, guild)
        server.scheduler.performOneTick()
        verify(exactly = 0) { bridge.prepare(any(), any(), any()) }
        state = "OWNED"
        runtime.refreshStallOwnership(player, guild)
        server.scheduler.performOneTick()
        verify(exactly = 1) { bridge.prepare(player, guild, match { it.id == "prime_real_estate" }) }
        verify(exactly = 1) { bridge.deliver(intent) }
    }
}
