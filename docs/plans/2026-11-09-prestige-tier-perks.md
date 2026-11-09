# Prestige tier perks and allied-home access

Requirements REQ-179 to REQ-182. Decisions (Fain, 2026-11-09), from the Discord
"Prestige Suggestions" thread: practical rewards unlock permanently at prestige
tiers; guild-owned stalls get a cap of 1 with +1 from prestige; allied-home
access control is for every guild, not just prestiged ones.

## Tier perks (permanent, by current prestige tier)

| Tier (default) | Perk | Config |
|---|---|---|
| I | Home and allied-home teleport countdown drops from 5 s to 2 s | `progression.prestige.perks.quick_home_tier`, `quick_home_seconds` |
| II | A second allied-home slot | `second_ally_home_tier` |
| III | One more guild-owned market stall (cap 1 becomes 2) | `extra_stall_tier` |

- The perk follows the guild's current prestige tier from the cached standing
  (30 s). A failed read means no perk, never a crash.
- The prestige confirmation and the Chronicle prestige guide list which perk
  the next tier unlocks.
- Finding while scoping: the countdown is hard-coded to 5 s in
  `TeleportationService`; `guild.home_teleport_warmup_seconds` is read but
  never used. Non-prestiged guilds keep 5 s; war-banner teleports keep 5 s.

## Guild stall cap (EnthusiaMarket)

- Guild-owned stalls are unlimited today (only solo players have limit groups).
- New: a guild may own at most `1 + bonus` stalls, where the bonus comes from
  LumaGuilds (1 at Prestige III by default). Market config
  `guild-stall-limit: 1` (`-1` keeps today's unlimited behaviour).
- Guilds already above the cap keep every stall they own; they just can't buy
  another until they are under it. Staff bypass still applies.

## Second allied home (Prestige II)

- Today a guild has one allied home (`allyHome`) with an allow-list of allied
  guilds. Prestige II adds a second slot; both share the same access rules.
- Set, move, remove and teleport work for both slots on Java and Bedrock; a
  guild that drops below tier II (disband only, since tiers never go down)
  keeps nothing extra.

## Allied-home access control (every guild)

The host guild decides who may use its allied homes, on one page (Java and
Bedrock):

1. **Which allies** (exists today): toggle each allied guild.
2. **Which of their ranks**: for each allowed ally, pick the ranks of that
   guild that may use it. Default: every rank that has "Use ally homes" in
   its own guild (today's behaviour).
3. **Blocked players**: add or remove individual players; a blocked player
   can never use the host's allied homes, whatever their rank.

Visitors still need "Use ally homes" in their own guild. Click-time and
countdown-end checks use the current rules (REQ-141 behaviour).

## Out of scope

- Changing who may set allied homes (still Manage home).
- Prestige perks beyond these three.
