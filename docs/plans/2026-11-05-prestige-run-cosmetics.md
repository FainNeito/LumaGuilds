# Prestige run cosmetics

Requirements REQ-158 to REQ-161 in `../requirements.md`. Status: spec for
operator approval; nothing is implemented yet.

## Why

Guild Outposts are permanent once bought (`RewardDefinition.permanentOnPurchase`
for `HomeCapacity`). A guild that bought them on its first climb already owns
them on every later climb, so after a prestige nearly every 10th level unlocks
nothing new. The rest of the re-climb rebuys bank, cooldown and fee upgrades the
guild owned before.

The fix is a cosmetic track for climbs after a prestige. It adds no power,
changes no prices, XP or capacities, and gives each run its own set.

## Runs

A guild's run number is its prestige count plus one. Run I is the first climb
and has no cosmetic track. Runs II to VII each have a themed set; Run VII is the
climb after Prestige VI and is the last set.

| Run | After | Theme | Colours |
|---|---|---|---|
| II | Prestige I | Gilded | gold, black |
| III | Prestige II | Ember | red, orange |
| IV | Prestige III | Winged | white, silver |
| V | Prestige IV | Tidal | blue, cyan |
| VI | Prestige V | Radiant | yellow, white |
| VII | Prestige VI | Crowned | purple, gold |

## Checkpoints

Every run uses the same ten checkpoint slots, so a guild learns the pattern once
and each run changes only the theme.

| Level | Slot | Gilded (II) | Ember (III) | Winged (IV) | Tidal (V) | Radiant (VI) | Crowned (VII) |
|---|---|---|---|---|---|---|---|
| 10 | Nameplate style | Gilded nameplate | Ember nameplate | Winged nameplate | Tidal nameplate | Radiant nameplate | Crowned nameplate |
| 20 | Display banner | Gold on black, border | Orange on red, flames | White on light grey, wings | Cyan on blue, waves | Yellow on white, sun | Gold on purple, crown |
| 30 | Celebration pose | Victory | Rally | Salute | Guard | Toast | Coronation |
| 40 | Display armor trim | Wayfinder, gold | Rib, redstone | Flow, quartz | Tide, lapis | Raiser, resin | Silence, amethyst |
| 50 | Spotlight colour | Gold | Red | White | Blue | Yellow | Purple |
| 60 | Chronicle page | Run opened | Run opened | Run opened | Run opened | Run opened | Run opened |
| 70 | Display aura | Gold sparkle | Embers | Drifting feathers | Bubbles | Glow | Enchant runes |
| 80 | Chronicle page | Milestones | Milestones | Milestones | Milestones | Milestones | Milestones |
| 90 | Plinth block | Gilded blackstone | Nether bricks | Quartz | Prismarine bricks | Shroomlight | Purpur |
| 100 | Run trophy | Run II completed | Run III completed | Run IV completed | Run V completed | Run VI completed | Run VII completed |

- **Poses** animate only vanilla joints: the head, the arms at the shoulder and
  a whole-body hop. No elbows, knees or waist bends. Victory is the pose already
  prototyped in `prestige.bbmodel`.
- **Display armor trim** applies to the Hall NPC's armor only. It is never an
  item and never changes a player's armor.
- **Chronicle pages** go in the guild's Hall lectern book. "Run opened" records
  the run number, the date the prestige that started it was committed, and the
  member count then. "Milestones" lists the dates this run reached levels 10
  to 80. The run trophy page adds the completion date.
- **Auras** are a few ambient vanilla particles around the NPC, shown only to
  players near the Hall.

## Rules

- **Earning.** A guild earns a checkpoint cosmetic automatically and for free
  the first time it reaches that level on that run. There is nothing to buy.
- **Keeping.** Cosmetics are permanent. Prestige, chapter rollover and losing
  levels never remove them, and earlier runs' cosmetics stay selectable.
- **Recording.** Unlocks use the existing `guild_cosmetic_unlocks` ledger
  (REQ-121) with type `PRESTIGE_<SLOT>`, a key such as `GILDED_NAMEPLATE`, and
  source `prestige_run_2`. Its `(guild, type, key)` primary key makes grants
  idempotent. `unlocked_at` doubles as the date the run reached the checkpoint,
  which feeds the Chronicle pages.
- **Telling the guild.** Online members of that guild get one guild-only
  message per unlock. There is no server broadcast.
- **Seeing them.** A "Prestige collection" view in the Chronicle (Java and
  Bedrock) shows every run's set with each item locked or unlocked, and the run
  the guild is on now.
- **Using them.** Choosing which unlocked cosmetics the guild's Hall display
  wears belongs to the Hall of Prestige work, not this spec.

## Guilds that prestiged before this ships

On upgrade, each guild receives:

- the full sets for runs it has already completed (a guild with prestige count
  `p` finished runs II to `p`, since prestige needs level 100), and
- the checkpoints of its current run at or below its current level.

These grants use the upgrade time as `unlocked_at` and source
`prestige_run_<n>_backfill`, so Chronicle pages show "before records began"
instead of an invented date. Today only Everlights has prestiged, so the
backfill gives them the Gilded checkpoints up to their current level.

## Out of scope

- The Hall of Prestige itself (NPCs, lectern, armor-set registration) and
  applying these cosmetics to it.
- Any power, price, XP or capacity change.
- A track after Run VII.
- The display NPC's animation files other than the existing Victory prototype.
