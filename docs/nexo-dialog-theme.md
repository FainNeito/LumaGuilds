# Guild dialog artwork

Java menus, readers and inputs share an optional compact emblem drawn specifically
for dialogs. A central seal with engraved wings replaces cropped inventory bands.
The native title appears once. Real guild banners, text and controls remain native
widgets. No background glyphs are painted behind variable-length text.

## Pack contract

Each style supplies guild_dialog_<style>_crest_v3: a 96x12 transparent PNG with
height 12 and ascent 7 in lumaguilds:dialogs. Styles remain enthusia, frostbound,
verdant, voidlight, obsidian, halloween and christmas. Selecting a style does not
grant a holiday unlock or emoji permission.

The emblem reserves two native text lines using the existing guild_dialog_spacer.
Both registrations must match their IDs and font, contain one character, and have
is_emoji=false. Missing, invalid or old-only Nexo assets retain complete plain
dialogs. Legacy bands and card overlays are never used as a fallback. Assets are
resolved afresh on each screen.

Private assets and conflict-checked fixed characters live in Enthusia Pack under
java/nexo/glyphs/enthusia/lumaguilds_dialog_art_v3.yml and
java/nexo/pack/assets/lumaguilds/textures/dialogs/v3/<style>/crest.png.
The existing spacer definition is reused without duplicate registration. Keep
legacy artwork for rollback to older builds. No global button texture replacement.

## Delivery and acceptance

Nexo owns pack delivery. Do not infer client pack delivery solely from backend
status flags when a proxy can retain packs across transfer. After a separately
authorized activation, test actual Minecraft clients across GUI scales, all styles
and Vanilla, missing assets, real banners, long text, fees, input focus, navigation
and proxy transfers. Native buttons and untrusted-dialog indicators stay client owned.

The interactive preview embeds these exact emblem PNGs and cached Minecraft font
artwork with sample data. Responsive browser layout is not a Minecraft screenshot
or client acceptance. No deployment, reload or restart is authorized by this change.
