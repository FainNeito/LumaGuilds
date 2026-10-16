# Purpose-built dialog artwork

Authoritative origin/main freshly fetched: 283d75f1e1016d415aa5afdfac840252f0391846.
Continuation base cd949644 retains the combined, unmerged TEST dialog stack.
No deployment, restart, pack rebuild or production operation is authorized by this task.

## Spec
- DA-001: WHEN a themed dialog renders, THE SYSTEM SHALL use a compact,
  independently drawn dialog emblem instead of cropped inventory headers,
  footers, separators or background strips.
- DA-002: WHEN a menu, reader or input renders, THE SYSTEM SHALL use the same
  emblem contract and native title without duplicate branding or framed text.
- DA-003: IF the new emblem or spacer is unavailable or invalid, THE SYSTEM
  SHALL retain the complete plain dialog without legacy artwork fallback.
- DA-004: THE SYSTEM SHALL preserve actions, quotes, content, banners,
  permissions, selected themes and pause=false/NONE transition behavior.

## Prove / engine / architecture / refine
The supplied client screenshots show legacy inventory bands enclosing disconnected
native widgets. Source confirms body() injects header/divider/footer while
referenceBody() and card() follow a different contract. Regression checks assert
the new asset IDs and absence of legacy calls. Assets are deterministic pixel art,
not cropped inventory textures; transparent margins and bounded native line
reservation avoid overlaying controls. No domain/application changes.

Task tag: TDD (shared theme contract), INFRA (private artwork package).
Project-local EARS/state helpers were searched for and are absent. This scoped
requirement/task/evidence record is manual; no automated SPEAR validator claimed.

- [x] Theme regression proof: two assertions failed against old theme behavior, then passed
- [x] Shared implementation and all seven style assets; pack lint passes with existing warnings
- [x] Focused and full existing checks: {'tests': 1953, 'failures': 0, 'errors': 0, 'skipped': 16}
- [x] Responsive interactive preview: 72 theme/screen/width combinations, fee validation and Discord round trip
- [x] Reviewable source delivery: FainNeito/LumaGuilds PR #5 and Enthusia-Pack PR #1, stacked drafts; canonical parent reconciliation still pending
- [ ] In-game GUI-scale / client-pack acceptance (not browser proof)

Private PNGs remain outside the public source repository.
