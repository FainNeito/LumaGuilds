# Long-term guild advancements

Goal: give guilds advancements that take months to earn (REQ-224 to REQ-226).

## Approach
- Most new advancements reuse activity keys the plugin already tracks, so they are new
  rows in `guild-achievements.json` chained after the current ones on the same tree row.
- Two new guild-wide keys, checked on join and each minute like level and playtime:
  `GUILD_MEMBER_COUNT` (current members) and `GUILD_AGE_DAYS` (whole days since creation).
- Remove the 1,000-hour ceiling on combined playtime; the inspector text uses each node's goal.

## Notes
- Collective counters (blocks, mobs, crops, smelting, sales) stay personal records credited
  to online members, as today. Playtime, members, age and level read the guild's real totals.
- Vanguard Status moves to the end of the level chain (25, 50, 75, then 100).
