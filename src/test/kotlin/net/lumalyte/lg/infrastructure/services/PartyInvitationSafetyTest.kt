package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.persistence.PartyRepository
import net.lumalyte.lg.application.persistence.PartyRequestRepository
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.PartyRequest
import net.lumalyte.lg.domain.entities.RankPermission
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class PartyInvitationSafetyTest {
    @Test fun `accepting a legacy guild invite never adds an outsider to an internal chat channel`() {
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()
        val inviter = UUID.randomUUID()
        val accepter = UUID.randomUUID()
        val internal = Party(name = "Guild_Chat", id = UUID.randomUUID(), guildIds = setOf(from), leaderId = inviter, createdAt = Instant.now())
        val request = PartyRequest.create(from, to, inviter)
        val parties = mockk<PartyRepository>(relaxed = true)
        val requests = mockk<PartyRequestRepository>(relaxed = true)
        val members = mockk<MemberService>(relaxed = true)
        every { members.hasPermission(accepter, to, RankPermission.MANAGE_RELATIONS) } returns true
        every { requests.getById(request.id) } returns request
        every { requests.update(any()) } returns true
        every { parties.getActivePartiesByGuild(from) } returns setOf(internal)
        every { parties.update(any()) } returns true
        every { parties.add(any()) } returns true
        val service = PartyServiceBukkit(parties, requests, members, mockk<GuildService>(relaxed = true), mockk<LangService>(relaxed = true))
        val accepted = assertNotNull(service.acceptPartyRequest(request.id, to, accepter))
        assertNotEquals(internal.id, accepted.id, "an internal channel must never become a multi-guild party")
        verify(exactly = 0) { parties.update(match { it.id == internal.id }) }
    }
}
