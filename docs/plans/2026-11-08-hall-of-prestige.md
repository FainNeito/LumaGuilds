# Hall of Prestige

Requirements REQ-166 to REQ-171 in `../requirements.md`. Status: spec for
operator approval; nothing is implemented yet. Ships in the same release as the
prestige run cosmetics (`2026-11-05-prestige-run-cosmetics.md`), which are only
visible here.

Decisions (Fain, 2026-11-08): fixed pedestals filled by the top-ranked guilds;
the guild picks whose skin its statue wears; armor is set by prestige tier.
On 2026-11-09 Fain picked the Guard pose as the statue default after testing it
on TEST (the sword angle there is `pri_right_item` (153.2, -10.8, 19.3) in the
model source's convention).

## What it is

A room that staff build, usually at spawn, with a row of pedestals. LumaGuilds
fills each pedestal with one prestiged guild's statue: an animated player NPC
in tiered armor on a plinth, with a nameplate, a banner, a spotlight, an aura,
a run trophy and a lectern that holds the guild's prestige book. The plugin
places and updates everything on the pedestal; staff own the room around it.

## Pedestals and ranking

- Staff stand where a statue should be and run
  `/lumaguilds hall pedestal set <n>`. That saves the statue position and
  facing. Further subcommands save the pedestal's other spots, all relative to
  the statue: `plinth` (up to 9 blocks the plinth block replaces), `banner`
  (one wall or standing banner spot), `lectern`, `trophy` and `light` (where
  the spotlight starts). Spots that are not set are simply skipped.
  Saving a `plinth` or `banner` spot also records the blocks there as staff
  built them, so the plugin can always put them back.
- Pedestal 1 is the top spot. The default is 6 pedestals; there is no hard cap,
  but every statue costs packets, so the TEST cost check below sets the limit.
- Guilds are ranked by prestige count (highest first), then current level, then
  the time they reached their current prestige (earliest first). Only guilds
  with at least Prestige I appear. Disbanded guilds leave the Hall (their
  tombstone record stays, REQ-163).
- The Hall refreshes when a prestige is committed and every 5 minutes
  (`hall.refresh_minutes`). Only pedestals whose guild or look changed are
  rebuilt, so statues don't flicker.
- An empty pedestal shows nothing: no NPC, and plinth blocks stay as staff
  built them.

## The statue

| Part | What sets it |
|---|---|
| Skin | A member the guild picks; the owner until someone picks. If that member leaves the guild, it falls back to the owner. |
| Armor | Prestige tier: I–II iron, III–IV diamond, V gold, VI netherite. Full set, never an item anyone can take. |
| Held items | A sword of the same material. The Victory pose also holds a matching axe in the off hand, as in the prototype. |
| Armor trim | The guild's chosen trim cosmetic (pattern and material). None until chosen. |
| Pose | The chosen pose cosmetic. Before one is chosen, the Guard pose Fain approved on TEST: hands resting on a sword planted point-down, head turning slowly. |
| Nameplate | Two lines above the head: the guild name in the chosen nameplate style with the chosen title flourish, then `Prestige IV` in Roman numerals. Default style is plain white, no flourish. |

Poses animate only vanilla joints (head, arms at the shoulder, whole-body hop),
as agreed for the prototype.

## The pedestal's other parts

| Cosmetic | How it shows | Default |
|---|---|---|
| Display banner | The banner block at the `banner` spot, in the chosen design. | Spot left as staff built it |
| Spotlight colour | A thin column of coloured dust from the `light` spot down onto the statue. | Off |
| Display aura | A few ambient particles around the statue. | Off |
| Plinth block | The `plinth` blocks turn into the chosen block. | Left as staff built them |
| Run trophy | A slowly turning item on the `trophy` spot: Gilded totem of undying, Ember fire charge, Winged elytra, Tidal heart of the sea, Radiant beacon, Crowned nether star. | Nothing |
| Hall sound | Plays to a player who clicks the statue. | No sound |

Particles, the turning trophy and the click sound reach only players within 24
blocks of the pedestal (`hall.view_distance`). When a pedestal changes guild,
the plinth and banner go back to what staff built before the new guild's look
is applied, so nothing from the last guild is left behind.

## Choosing the look

A **Hall display** page, next to the Prestige collection in the Chronicle (Java
and Bedrock), shows the guild's statue settings. The guild owner, or a rank
with Manage guild settings, can:

- pick which member's skin the statue wears, and
- for each of the ten slots, pick any owned cosmetic of that slot, or the
  default.

Everyone in the guild can view the page. A guild that isn't in the Hall yet can
still set its look; it shows as soon as the guild takes a pedestal.

## The lectern book

Clicking the pedestal's lectern opens a read-only book for that guild. Nobody
can take or edit it. Pages:

1. **Cover:** guild name, current prestige in Roman numerals, date of the
   first prestige.
2. **One spread per run**, in order, for Run II onward:
   - *Run opened:* run number, date the prestige that started it was committed,
     member count at that moment.
   - *Milestones:* the date the run first reached each of levels 10 to 90.
   - *Trophy:* the date the run reached level 100, or "In progress".
3. Dates recorded before this feature shipped read "Before records began".

To fill this, the plugin starts recording two things it doesn't keep today:
the member count when each prestige is committed, and the first time each run
reaches each checkpoint level. The checkpoint record is the same one the run
cosmetics already need for their picks (REQ-158), so it is stored once.

## Lifecycle

- Statues are Citizens NPCs that LumaGuilds creates in a registry of its own
  that Citizens never saves, with a BetterModel player model for the pose.
  Nothing has to be cleaned out of Citizens' `saves.yml`.
- On start the plugin builds every pedestal; on stop it removes its NPCs and
  item displays and puts plinth and banner spots back. A crash leaves at most
  the plinth and banner changes behind, and the next start rebuilds them from
  the saved staff state.
- `/npc limb` is not saved, so the plugin restarts the pose animation itself
  every time a statue spawns (chunk load, restart, refresh).
- Citizens and BetterModel are soft dependencies. Without Citizens the Hall is
  off and the log says why once. Without BetterModel the statues stand still
  with no animation.
- Bedrock players (through Geyser) are expected not to see BetterModel's
  animation. They get the plain Citizens NPC in the same armor; the TEST check
  below confirms what they really see.

## Commands and permissions

| Command | Permission | Does |
|---|---|---|
| `/lumaguilds hall pedestal set <n>` and `... <n> <part>` | `lumaguilds.admin.hall` | Save a pedestal or one of its spots from where you stand |
| `/lumaguilds hall pedestal remove <n>` | `lumaguilds.admin.hall` | Remove a pedestal and restore its spots |
| `/lumaguilds hall refresh` | `lumaguilds.admin.hall` | Re-rank and rebuild now |
| `/lumaguilds hall list` | `lumaguilds.admin.hall` | Show each pedestal, its spots and its guild |

Players need no permission to view the Hall or read the books.

## Still to check on TEST

From the NPC prototype bundle, before building on it:

1. Done 2026-11-09: the Guard pose looks right on Java. BetterModel turns x and
   y the opposite way from Blockbench's preview, so the model source flips them
   on export.
2. Tiered armor, trims and held items follow the moving limbs.
3. What Bedrock players see.
4. TPS and `/spark` cost with 6 animated statues in view. This sets the default
   pedestal count.

## Out of scope

- Building the room itself; staff do that.
- The website Hall of Prestige page and the Discord post.
- Pose animations other than Guard, which are art work done while building
  this, one per pose cosmetic.
- Statues for guilds below Prestige I or for disbanded guilds.
