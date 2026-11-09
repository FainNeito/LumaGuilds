# Guild party invitation and consent

## Requirements

- When a guild invites another guild to a party, the invitee must not gain membership, channel access or other party benefits until an authorized member accepts the invitation.
- Java and Bedrock must send a real persistent invitation and report success only after it is saved; failed invitations must not be described as sent.
- Acceptance must address the exact party that originated the invitation, preserve internal single-guild channels, check current membership and permissions, and reject expired, cancelled, duplicate, full or otherwise invalid requests.
- Existing requests, party records, permissions and single-guild channels must remain readable; persistence and acceptance must survive restart and safely handle partial failure and duplicate submission.
- Both clients need clear, consistent controls, truthful outcomes, and navigation back to party management.

## Existing evidence

Java PartyCreationMenu constructs the initial Party from all selected guilds and only afterwards calls inviteToParty. That method rejects targets already in the party. BedrockPartyCreationMenu similarly creates a Party containing the selected guild and reports an invitation, but does not send a request. PartyServiceBukkit.acceptPartyRequest chooses the first active party for the sending guild, including private internal channels. The current PartyRequest has no party identifier, so creating a singleton and merely sending the existing request does not guarantee acceptance into the intended party.

## Verification plan

Reproduce both entry points; test pending/accepted/declined/expired states, exact destination when multiple internal channels exist, authorization, party capacity, duplicate submissions, persistence across restart, database errors, and preservation of existing data on SQLite and MariaDB. Validate Java and Bedrock interaction and create a mobile-accessible interactive preview for menu changes. Do not deploy until shared TEST handoff and applicable integration gates are met.

## Status

Investigation and requirements only. No gameplay behavior has been changed. A durable association and atomic or recoverable acceptance design must be established before implementation.
