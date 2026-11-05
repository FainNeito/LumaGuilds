package net.lumalyte.lg.infrastructure.services

import java.net.InetAddress
import java.util.UUID
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/** Unbound adapter; it cannot establish whether proxy forwarding or network coverage is trustworthy. */
internal class GuildAchievementBukkitSessions(
    private val onServerThread: () -> Boolean = Bukkit::isPrimaryThread,
    private val onlinePlayers: () -> Collection<Player> = { Bukkit.getOnlinePlayers() },
) {
    /** Read all sessions once on the server thread; invalid entries make the snapshot unavailable. */
    fun read(): Map<UUID, InetAddress?> {
        check(onServerThread()) { "Session snapshot requires server thread" }
        val result = mutableMapOf<UUID, InetAddress?>()
        onlinePlayers().forEach { player ->
            check(player.isOnline) { "Online roster changed while capturing sessions" }
            val id = player.uniqueId
            check(id !in result) { "Duplicate online session" }
            result[id] = player.address?.address
        }
        return result
    }
}
