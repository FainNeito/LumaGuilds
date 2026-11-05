package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.UUID
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GuildAchievementBukkitSessionsTest {
    private fun player(id: UUID, online: Boolean, address: InetSocketAddress?): Player = mockk {
        every { uniqueId } returns id
        every { isOnline } returns online
        every { getAddress() } returns address
    }

    @Test fun `captures current sessions and retains unknown addresses`() {
        val one = UUID.randomUUID()
        val two = UUID.randomUUID()
        val address = InetAddress.getByName("198.51.100.30")
        val sessions = GuildAchievementBukkitSessions({ true }, { listOf(player(one, true, InetSocketAddress(address, 25565)), player(two, true, null)) })
        assertEquals(mapOf(one to address, two to null), sessions.read())
    }

    @Test fun `rejects off-thread changed and duplicate sessions`() {
        val id = UUID.randomUUID()
        assertFailsWith<IllegalStateException> { GuildAchievementBukkitSessions({ false }, { emptyList() }).read() }
        assertFailsWith<IllegalStateException> { GuildAchievementBukkitSessions({ true }, { listOf(player(id, false, null)) }).read() }
        assertFailsWith<IllegalStateException> { GuildAchievementBukkitSessions({ true }, { listOf(player(id, true, null), player(id, true, null)) }).read() }
    }
}
