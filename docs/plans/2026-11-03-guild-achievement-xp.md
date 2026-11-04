# Guild achievement XP: design and acceptance boundaries

Status: design in progress. No runtime implementation or release approval is established by this document. User selected future completions only on 2026-11-03; previously completed achievements must not be paid retroactively.

## Context

Guild achievement progress and personal Minecraft XP are currently stored per player. Shared events may reach several members, so awarding guild XP for every personal completion would multiply the guild payout. Current bundled weekly quest defaults award 500, 1,500, 3,000, or 5,000 guild XP, with an additional full-set reward. Production configuration may differ.

## Requirements

- When an eligible achievement is earned, the system shall award a small, explicitly displayed amount of guild XP at most once per guild and achievement ID, regardless of member count, repeated events, reconnects, or player membership changes.
- Guild XP and its idempotency record must be durable and atomic within the existing progression store. A failure or restart between player-data persistence and guild-XP delivery must not silently lose the award or grant it twice; pending awards must retain their original guild and achievement identity.
- The existing experience transaction cleanup prunes old rows according to `progression.transaction_retention_days`; a deterministic ID in that table alone is not a permanent once-per-guild receipt. Use a durable achievement-specific ledger protected from that cleanup and commit it atomically with the award, or demonstrate an equivalent permanent mechanism before wiring payouts.
- Personal Minecraft XP, current advancement IDs, historical progress, quest rewards, prestige, and other XP sources must retain their existing semantics. Unavailable or ambiguous persistence shall fail closed and surface a recoverable/reviewable state.
- Do not backfill existing completions or award XP merely because existing state is observed during initialization. Never infer that personal player state is complete guild-wide historical data. Selection of eligible events, deployment cutoff, configuration and cap semantics require design before integration.
- Proposed defaults: TASK 25, GOAL 50, CHALLENGE 100 guild XP; the root awards zero. Across the current 29 non-root nodes the proposed lifetime maximum is 1,725 XP per guild, before any explicitly defined bonus semantics. These figures are preliminary and must be compared against actual deployment configuration and abuse cases before release.
- The menu and advancement details must accurately distinguish personal Minecraft XP from guild XP; changes need the required interactive mobile-accessible preview and native-client acceptance.

## Verification to prepare

Prove duplicate member completions, replay, membership change, player save and database failures, ambiguous commit, retry, shutdown/restart, old data, privacy, and the chosen historical policy. Verify real SQLite and MariaDB atomic behavior, the production-compatible build, and existing XP/source-accounting contracts. Quest comparisons must use actual deployment configuration before balancing is finalized.

## Initial evidence

The focused `GuildAchievementXpPolicyTest` ran three tests successfully after supplying the companion API JARs used by the existing local integration checkout. The first attempt failed compilation because the clean worktree lacked those ignored local dependencies; no test ran on that attempt. The policy is not connected to advancement delivery, and these tests do not establish award persistence, future-only behavior, multi-member handling, or MariaDB compatibility.

The isolated SQL prototype adds a permanent guild-and-achievement receipt in the same transaction as XP and ordinary history. Its focused SQLite repository and policy suites passed after adding regression cases for history pruning, rollback on ledger-insert failure, and simultaneous requests from multiple members. This is not wired to the runtime or an upgrade migration; MariaDB, actual restart, cutover/backfill, lifecycle publication, and end-to-end delivery remain unverified. An initial compilation attempt exposed missing exhaustive `ACHIEVEMENT` source mappings; those mappings were added before the successful test run.
