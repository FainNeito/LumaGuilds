package net.lumalyte.lg.application.services

import net.lumalyte.lg.config.PrestigeFanfareConfig
import net.lumalyte.lg.domain.rewards.FanfareCue
import net.lumalyte.lg.domain.rewards.PrestigeCompletion
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** REQ-164: who hears what, and when. */
class PrestigeFanfareTest {
    private val guild = UUID.randomUUID()
    private val memberA = UUID.randomUUID()
    private val memberB = UUID.randomUUID()
    private val offlineMember = UUID.randomUUID()
    private val outsider = UUID.randomUUID()

    private class Stage(val onlinePlayers: MutableSet<UUID>) : FanfareStage {
        val tasks = sortedMapOf<Int, MutableList<() -> Unit>>()
        val played = mutableListOf<Pair<UUID, FanfareCue>>()
        val titles = mutableListOf<Triple<UUID, Int, String>>()
        override fun online() = onlinePlayers.toList()
        override fun isOnline(player: UUID) = player in onlinePlayers
        override fun later(ticks: Int, task: () -> Unit) { tasks.getOrPut(ticks) { mutableListOf() }.add(task) }
        override fun play(player: UUID, cue: FanfareCue) { played += player to cue }
        override fun title(player: UUID, tier: Int, guildName: String) { titles += Triple(player, tier, guildName) }
        fun runUntil(tick: Int) { tasks.filterKeys { it <= tick }.values.flatten().forEach { it() }; tasks.keys.removeIf { it <= tick } }
        fun runAll() = runUntil(Int.MAX_VALUE)
    }

    private fun completion(tier: Int) =
        PrestigeCompletion(UUID.randomUUID(), guild, memberA, tier, "bank-1", Instant.now(), false)

    private fun fanfare(stage: Stage, config: PrestigeFanfareConfig = PrestigeFanfareConfig()) = PrestigeFanfare(
        settings = { config },
        members = { if (it == guild) setOf(memberA, memberB, offlineMember) else emptySet() },
        guildName = { if (it == guild) "Everlights" else null },
        stage = stage,
    )

    @Test
    fun `members hear the fanfare and see a title, everyone else hears the short version`() {
        val stage = Stage(mutableSetOf(memberA, memberB, outsider))
        fanfare(stage).prestiged(completion(2))
        stage.runAll()

        assertEquals(19, stage.played.count { it.first == memberA })
        assertEquals(19, stage.played.count { it.first == memberB })
        assertEquals(6, stage.played.count { it.first == outsider })
        assertTrue(stage.played.none { it.first == offlineMember })
        assertEquals(setOf(Triple(memberA, 2, "Everlights"), Triple(memberB, 2, "Everlights")), stage.titles.toSet())
        assertEquals(0.9f, stage.played.first { it.first == memberA }.second.pitch)
    }

    @Test
    fun `cues land on their ticks and a player who leaves stops hearing them`() {
        val stage = Stage(mutableSetOf(memberA, memberB))
        fanfare(stage).prestiged(completion(1))
        stage.runUntil(20)
        val beforeLeaving = stage.played.count { it.first == memberB }
        stage.onlinePlayers.remove(memberB)
        stage.runAll()
        assertEquals(beforeLeaving, stage.played.count { it.first == memberB })
        assertTrue(stage.played.filter { it.first == memberA }.all { it.second.tick in 0..42 })
        assertEquals(19, stage.played.count { it.first == memberA })
    }

    @Test
    fun `prestige six plays the legacy fanfare`() {
        val stage = Stage(mutableSetOf(memberA))
        fanfare(stage).prestiged(completion(6))
        stage.runAll()
        assertEquals(26, stage.played.count { it.first == memberA })
    }

    @Test
    fun `switches turn parts off and a failure never escapes`() {
        val quiet = Stage(mutableSetOf(memberA, outsider))
        fanfare(quiet, PrestigeFanfareConfig(enabled = false)).prestiged(completion(1))
        quiet.runAll()
        assertTrue(quiet.played.isEmpty() && quiet.titles.isEmpty())

        val membersOnly = Stage(mutableSetOf(memberA, outsider))
        fanfare(membersOnly, PrestigeFanfareConfig(serverWide = false, memberTitle = false)).prestiged(completion(1))
        membersOnly.runAll()
        assertTrue(membersOnly.played.none { it.first == outsider })
        assertTrue(membersOnly.titles.isEmpty())

        val broken = object : FanfareStage by Stage(mutableSetOf(memberA)) {
            override fun online(): Collection<UUID> = error("server gone")
        }
        PrestigeFanfare({ PrestigeFanfareConfig() }, { setOf(memberA) }, { "X" }, broken).prestiged(completion(1))
    }
}
