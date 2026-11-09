# Prestige run cosmetics

Requirements REQ-158 to REQ-161 in `../requirements.md`. Status: spec for
operator approval; nothing is implemented yet.

Decisions (Fain, 2026-11-08): levels 60 and 80 are visible picks instead of
Chronicle pages; the track ships in the same release as the Hall of Prestige;
nameplate styles apply to the Hall statue only.

## Why

Guild Outposts are permanent once bought (`RewardDefinition.permanentOnPurchase`
for `HomeCapacity`). A guild that bought them on its first climb already owns
them on every later climb, so after a prestige nearly every 10th level unlocks
nothing new. The rest of the re-climb rebuys bank, cooldown and fee upgrades the
guild owned before.

The fix is a cosmetic track for climbs after a prestige. It adds no power and
changes no prices, XP or capacities. At each checkpoint the guild picks one
cosmetic for that slot from any theme, so guilds choose their own look, and a
guild that finishes every run ends up owning all of them.

## Runs

A guild's run number is its prestige count plus one. Run I is the first climb
and has no cosmetic track. Runs II to VII (the climbs after Prestige I to VI)
each have ten checkpoint picks, 60 in all.

The cosmetics come in six themes. Themes group the options and give each a
matching look; they are not tied to a run. A run's colour (shown on its Chronicle
pages and run trophy) follows the table, but what the guild picks is free.

| Theme | Colours | Run colour for |
|---|---|---|
| Gilded | gold, black | Run II |
| Ember | red, orange | Run III |
| Winged | white, silver | Run IV |
| Tidal | blue, cyan | Run V |
| Radiant | yellow, white | Run VI |
| Crowned | purple, gold | Run VII |

## Checkpoints

Every run has the same ten checkpoint slots. Each slot offers one option per
theme, six in all:

| Level | Slot | Gilded | Ember | Winged | Tidal | Radiant | Crowned |
|---|---|---|---|---|---|---|---|
| 10 | Nameplate style | Gilded nameplate | Ember nameplate | Winged nameplate | Tidal nameplate | Radiant nameplate | Crowned nameplate |
| 20 | Display banner | Gold on black, border | Orange on red, flames | White on light grey, wings | Cyan on blue, waves | Yellow on white, sun | Gold on purple, crown |
| 30 | Celebration pose | Victory | Rally | Salute | Guard | Toast | Coronation |
| 40 | Display armor trim | Wayfinder, gold | Rib, redstone | Flow, quartz | Tide, lapis | Raiser, resin | Silence, amethyst |
| 50 | Spotlight colour | Gold | Red | White | Blue | Yellow | Purple |
| 60 | Hall sound | Bell toll | Fire charge | Wing flaps | Conduit pulse | Amethyst chime | Enchanting |
| 70 | Display aura | Gold sparkle | Embers | Drifting feathers | Bubbles | Glow | Enchant runes |
| 80 | Title flourish | `✦ Name ✦` | `» Name «` | `❦ Name ❦` | `≈ Name ≈` | `☀ Name ☀` | `♛ Name ♛` |
| 90 | Plinth block | Gilded blackstone | Nether bricks | Quartz | Prismarine bricks | Shroomlight | Purpur |
| 100 | Run trophy | Run II completed | Run III completed | Run IV completed | Run V completed | Run VI completed | Run VII completed |

- **Poses** animate only vanilla joints: the head, the arms at the shoulder and
  a whole-body hop. No elbows, knees or waist bends. Victory is the pose already
  prototyped in `prestige.bbmodel`. Guard holds a sword: both arms angled in to
  rest on a sword planted point-down in front, with slow turns of the head.
- **Display armor trim** applies to the Hall NPC's armor only. It is never an
  item and never changes a player's armor.
- **Nameplate styles** colour the guild name above the Hall statue only. They
  never change chat, tab or EnthusiaTags tags.
- **Hall sounds** are one vanilla sound that plays to a player who clicks the
  guild's statue: `block.bell.use`, `item.firecharge.use`,
  `entity.phantom.flap`, `block.conduit.activate`,
  `block.amethyst_block.resonate` or `block.enchantment_table.use`, at a volume
  that reaches only nearby players.
- **Title flourishes** are symbols around the guild name on the statue nameplate
  and in the title the guild's members see at their next prestige. The symbols
  are in Minecraft's default font, so no resource pack is needed.
- **Chronicle pages** are no longer picks. The Hall lectern book records them
  for every run automatically: "Run opened" (run number, the date of the
  prestige that started it and the member count then), "Milestones" (the dates
  this run reached levels 10 to 90) and the run trophy page with the completion
  date.
- **Auras** are a few ambient vanilla particles around the NPC, shown only to
  players near the Hall.

## Rules

- **Picking.** The first time a guild reaches a checkpoint level on a run, it
  earns one free pick for that slot. The guild owner, or a rank with Manage
  guild settings, chooses any option in that slot the guild does not own yet.
  There is nothing to buy.
- **Unclaimed picks.** A pick waits until someone chooses; it is never lost,
  including across a later prestige. The Chronicle shows how many picks are
  waiting. When only one option is left in a slot, the pick takes it
  automatically.
- **Owning everything.** Six runs times ten checkpoints is 60 picks, one per
  cosmetic, so a guild that completes Run VII owns the whole collection.
- **Wearing.** The display can mix any owned cosmetics, one per slot, from any
  themes.
- **Keeping.** Cosmetics are permanent. Prestige, chapter rollover and losing
  levels never remove them, and earlier runs' cosmetics stay selectable.
- **Recording.** A pick earned is stored as a pending entitlement keyed by
  `(guild, run, slot)`, with the time the checkpoint was reached; that time
  feeds the Chronicle pages. A chosen cosmetic goes in the existing
  `guild_cosmetic_unlocks` ledger (REQ-121) with type `PRESTIGE_<SLOT>`, a key
  such as `GILDED_NAMEPLATE`, and source `prestige_run_<n>`. Both keys make
  earning and choosing idempotent.
- **Telling the guild.** Online members of that guild get one guild-only
  message per unlock. There is no server broadcast.
- **Seeing them.** A "Prestige collection" view in the Chronicle (Java and
  Bedrock) shows all 60 cosmetics by slot and theme, owned or locked, the
  guild's current run, and any picks waiting to be chosen.
- **Using them.** Choosing which unlocked cosmetics the guild's Hall display
  wears belongs to the Hall of Prestige work. Because almost every cosmetic is
  only visible in the Hall, this track ships in the same release as the Hall
  and is not enabled on its own.

## Guilds that prestiged before this ships

On upgrade, each guild receives the picks it would already have earned:

- ten picks for every run it has completed (a guild with prestige count `p`
  finished runs II to `p`, since prestige needs level 100), and
- one pick for each checkpoint of its current run at or below its current
  level.

They arrive as unclaimed picks, so the guild chooses. They carry the upgrade
time and source `prestige_run_<n>_backfill`, so Chronicle pages show "before
records began" instead of an invented date. Today only Everlights has
prestiged, so they get one pick per checkpoint they have passed on Run II.

## Out of scope

- The Hall of Prestige itself (NPCs, lectern, armor-set registration) and
  applying these cosmetics to it. It is specified in
  `2026-11-08-hall-of-prestige.md`, which also says how each cosmetic looks
  there, and is released together with this track.
- Nameplate styles or flourishes on player chat, tab or EnthusiaTags tags.
- Any power, price, XP or capacity change.
- A track after Run VII.
- The display NPC's animation files other than the existing Victory prototype.
