package net.lumalyte.lg.api.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/**
 * Fired when a guild's banner is set or updated by a member.
 */
class GuildBannerSetEvent(
    val guildId: UUID,
    val playerId: UUID,
) : Event() {
    internal var registeredFromDefault: Boolean = false
        private set

    companion object {
        internal fun afterSave(guildId: UUID, playerId: UUID, registeredFromDefault: Boolean) =
            GuildBannerSetEvent(guildId, playerId).also { it.registeredFromDefault = registeredFromDefault }

        @JvmStatic
        private val handlers = HandlerList()
        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
    override fun getHandlers(): HandlerList = Companion.handlers
}
