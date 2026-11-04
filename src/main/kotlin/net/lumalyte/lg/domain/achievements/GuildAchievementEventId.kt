package net.lumalyte.lg.domain.achievements

import java.nio.charset.StandardCharsets
import java.util.UUID

/** Namespaces authoritative event IDs by guild and requirement; never synthesize from mutable state. */
internal object GuildAchievementEventId {
    fun fromSource(guild: UUID, requirement: String, source: UUID): UUID {
        require(requirement.matches(Regex("[A-Z0-9_]{1,128}")))
        return UUID.nameUUIDFromBytes("lumaguilds:achievement-event:v1:$guild:$requirement:$source".toByteArray(StandardCharsets.UTF_8))
    }
}
