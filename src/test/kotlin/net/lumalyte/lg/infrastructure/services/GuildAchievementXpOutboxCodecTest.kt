package net.lumalyte.lg.infrastructure.services

import java.util.UUID
import net.lumalyte.lg.application.services.GuildAchievementXpCoordinator
import net.lumalyte.lg.domain.achievements.GuildAchievementXpPolicy
import org.json.JSONArray
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GuildAchievementXpOutboxCodecTest {
    private val guild = UUID.fromString("8ee82c27-7a7c-40c2-9a4b-5c9c347be76e")
    private val transaction = GuildAchievementXpPolicy.transactionId(guild, "first_war")
    private val intent = GuildAchievementXpCoordinator.Intent(guild, "first_war", 50, transaction)

    @Test fun `original guild and receipt survive serialization`() {
        assertEquals(listOf(intent), GuildAchievementXpOutboxCodec.decode(GuildAchievementXpOutboxCodec.encode(listOf(intent))))
    }

    @Test fun `malformed or duplicate pending intents fail closed`() {
        assertFailsWith<IllegalArgumentException> { GuildAchievementXpOutboxCodec.encode(listOf(intent, intent)) }
        assertFailsWith<IllegalArgumentException> { GuildAchievementXpOutboxCodec.encode(listOf(intent.copy(amount = 9999))) }
        assertFailsWith<IllegalArgumentException> { GuildAchievementXpOutboxCodec.encode(listOf(intent.copy(guildId = UUID.randomUUID()))) }
        assertFailsWith<IllegalArgumentException> { GuildAchievementXpOutboxCodec.decode(JSONArray("[{}]")) }
    }
}
