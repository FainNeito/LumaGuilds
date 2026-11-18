package net.lumalyte.lg.infrastructure.services

import io.mockk.*
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import java.util.UUID
import kotlin.test.*

internal class GuildAchievementRuntimeTest {
    @AfterEach fun cleanup() { unmockkAll(); MockBukkit.unmock() }

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
        every { progression.getGuildProgression(guild) } returns null
        val stalls = mockk<net.lumalyte.lg.application.services.GuildStallReadService>()
        every { stalls.read(guild, player.uniqueId) } returns java.util.concurrent.CompletableFuture.completedFuture(
            net.lumalyte.lg.application.services.StallReadResult.Unavailable)
        val runtime = GuildAchievementRuntime(plugin, members, progression, stalls)
        runtime.initializeAfterJoin(player)
        assertTrue(runtime.read(player)!!.progress.isEmpty())
        server.scheduler.performTicks(19L)
        assertTrue(runtime.read(player)!!.progress.isEmpty())
        server.scheduler.performTicks(2L)
        assertEquals(1, runtime.read(player)!!.progress["guilds_root"])
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

    @Test fun `combined playtime uses stored values for offline members and never reads their stats`() {
        val server = MockBukkit.mock()
        val plugin = spyk(MockBukkit.createMockPlugin("LumaGuilds"))
        every { plugin.getResource("guild-achievements.json") } answers { javaClass.classLoader.getResourceAsStream("guild-achievements.json") }
        // Bukkit.getPlayer returns the server's own player, so the statistic is set before spying.
        val player = spyk(server.addPlayer().apply { setStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE, 72_000 * 400) })
        every { player.isOnline } returns true
        every { player.saveData() } just Runs
        val guild = UUID.randomUUID()
        val offline = UUID.randomUUID()
        val leaver = UUID.randomUUID()
        val roster = mutableSetOf(player.uniqueId, offline, leaver)
        val members = mockk<MemberRepository>()
        every { members.getGuildsByPlayer(player.uniqueId) } returns setOf(guild)
        every { members.getByGuild(guild) } answers { roster.map {
            net.lumalyte.lg.domain.entities.Member(it, guild, UUID.randomUUID(), java.time.Instant.EPOCH)
        }.toSet() }
        val progression = mockk<ProgressionRepository>()
        every { progression.getGuildProgression(guild) } returns null
        val stalls = mockk<net.lumalyte.lg.application.services.GuildStallReadService>()
        every { stalls.read(any(), any()) } returns java.util.concurrent.CompletableFuture.completedFuture(
            net.lumalyte.lg.application.services.StallReadResult.Unavailable)
        val store = net.lumalyte.lg.domain.values.MemberPlaytimeStore.InMemory(
            mutableMapOf(offline to 72_000L * 300, leaver to 72_000L * 200))
        mockkStatic(org.bukkit.Bukkit::class)
        val runtime = GuildAchievementRuntime(plugin, members, progression, stalls, playtimeStore = store)
        val goal = runtime.engine.definitions.first { it.requirement == "GUILD_COMBINED_PLAYTIME" && it.goal == 1000 }
        runtime.initialize(player)
        assertEquals(900, runtime.read(player)!!.progress[goal.id])
        roster -= leaver
        assertEquals(72_000L * 700, runtime.playtime.memberTicks(guild)!!.sum())
        verify(exactly = 0) { org.bukkit.Bukkit.getOfflinePlayer(any<UUID>()) }
        assertEquals(72_000L * 400, store.loadAll()[player.uniqueId])
    }
}
