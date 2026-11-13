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
        assertEquals(reopened.engine.definitions.size, reopened.engine.definitions.map { it.x to it.y }.distinct().size,
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

    @Test fun `joining checks member count and guild age`() {
        val server = MockBukkit.mock()
        val plugin = spyk(MockBukkit.createMockPlugin("LumaGuilds"))
        every { plugin.getResource("guild-achievements.json") } answers { javaClass.classLoader.getResourceAsStream("guild-achievements.json") }
        val player = spyk(server.addPlayer())
        every { player.isOnline } returns true
        every { player.saveData() } just Runs
        val guild = UUID.randomUUID()
        val members = mockk<MemberRepository>()
        every { members.getGuildsByPlayer(player.uniqueId) } returns setOf(guild)
        every { members.getByGuild(guild) } returns (1..26).map {
            net.lumalyte.lg.domain.entities.Member(UUID.randomUUID(), guild, UUID.randomUUID(), java.time.Instant.EPOCH)
        }.toSet()
        val progression = mockk<ProgressionRepository>()
        every { progression.getGuildProgression(guild) } returns null
        val stalls = mockk<net.lumalyte.lg.application.services.GuildStallReadService>()
        every { stalls.read(guild, player.uniqueId) } returns java.util.concurrent.CompletableFuture.completedFuture(
            net.lumalyte.lg.application.services.StallReadResult.Unavailable)
        val now = java.time.Instant.parse("2026-11-13T00:00:00Z")
        val runtime = GuildAchievementRuntime(plugin, members, progression, stalls,
            createdAt = { if (it == guild) now.minus(java.time.Duration.ofDays(200)).plusSeconds(60) else null }, clock = { now })
        runtime.initialize(player)
        val record = runtime.read(player)!!
        val byGoal = { req: String -> runtime.engine.definitions.filter { it.requirement == req }.associate { it.goal to it.id } }
        val count = byGoal("GUILD_MEMBER_COUNT")
        assertTrue(count.getValue(10) in record.rewarded && count.getValue(25) in record.rewarded)
        assertFalse(count.getValue(50) in record.rewarded)
        assertEquals(26, record.progress[count.getValue(50)])
        val age = byGoal("GUILD_AGE_DAYS")
        assertTrue(age.getValue(30) in record.rewarded && age.getValue(180) in record.rewarded)
        assertEquals(199, record.progress[age.getValue(365)])
    }

    @Test fun `combined play hours have no 1000 hour ceiling`() {
        assertEquals(100_001, GuildAchievementRuntime.playHours(listOf(100_001L * 72_000L)))
        assertEquals(Int.MAX_VALUE, GuildAchievementRuntime.playHours(listOf(Long.MAX_VALUE / 2, Long.MAX_VALUE / 2)))
    }
}
