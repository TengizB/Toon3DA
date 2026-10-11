# CP5 CONTRACT — balance-overhaul-order-5: the build made visible (render only)

Spec: `/home/user/Toon3DA/.claude/agents/ideas/balance-overhaul-order-5.txt` — B8 COMMUNICATION, VISUAL DESIGN,
A5. Live state + API: `/home/user/Toon3DA/.claude/checkpoints/balance-overhaul-order-5.md` (NOTES CARRIED
FORWARD). Project rules: `/home/user/Toon3DA/.claude/CLAUDE.md` (no allocation in render(), every Disposable
disposed, constants in the right Constants file, naming, touch-only, coordinate system, viewport rules).

## Build
1. **Level-up overlay** (`progression/LevelUpOverlayRenderer` — it lives in `progression` today; leave it there
   unless moving it to `render` is trivial; say which):
   - Perk plates (`setOfferedPerks(Perk[], int, PlayerBuild)`): a TAG CHIP (small rounded plate in the tag's
     colour, tag name in it), a SYNERGY marker = amber corner ribbon on plates where `PerkDeck.isSynergy(perk,
     carriedTags)` (World must pass the carried tags — add a parameter or setter; World already has
     `carriedBuildTags()`), RANK PIPS (maxRank pips, filled = current rank, the NEXT rank highlighted — i.e. what
     you'd own after the pick), the one-line effect. Plates keep the existing level-up card size (thumb-sized).
   - Keystone mode (`setOfferedKeystones`): distinct GOLD DOUBLE BORDER frame on each plate, "KEYSTONE" header,
     gain line, cost line in red (0.95, 0.30, 0.25) — cost colour already exists, move it to a constant if it is
     a literal.
   - Tag colours: the order-3 damage-class palette (`WeaponConstants.DAMAGE_CLASS_COLOR_*`) for the weapon tags
     (CLOSE_QUARTERS = SPREAD colour, MARKSMAN = BALLISTIC, PYRO = FIRE, DEMOLITION = EXPLOSIVE, ENERGY = ENERGY,
     BLADE = BLADE); BULWARK blue, VITALITY green, SCAVENGER brass. Colours as constants (ProgressionConstants
     or HudConstants — pick the file whose table fits, add to CLAUDE.md's constants table only if a NEW file is
     created). Mapping tag -> colour as DATA (array indexed by BuildTag.ordinal), never a switch. Replace the CP2
     placeholder colour cycle.
2. **Inventory BUILD page** (`render/InventoryOverlayRenderer` and its World touch wiring): a BUILD page/tab
   reachable by touch from the inventory overlay (a thumb-sized tab/plate next to the existing header buttons,
   like the CODEX button), listing picked perks GROUPED BY TAG (tag chip + perk name + rank pips) and the
   keystones taken (gold frame, name, gain + cost line). Empty state text when nothing is picked. Scroll if it
   can overflow (or prove it cannot: 26 perks max + 4 keystones). Read-only.
3. **Mini-map barrels**: `render/LevelRenderer` draws explosive barrels as dots when
   `BuildStats.barrelsOnMinimap()` (Sapper r1). LevelRenderer must get the flag via a setter from World (no
   progression import in hot path beyond a boolean).
4. Any text strings: UI text constants (not story string ids), consistent with how the overlay/inventory hold
   their labels today.

## Boundaries
- Render + the minimal World touch/wiring for the new tab and setters. No balance numbers, no gameplay changes,
  no sim changes. Do not touch WeaponAbility/WeaponRoller (another builder may be finishing CP4 — if a file you
  need is being edited, coordinate by only touching the methods you need).
- No allocation in render(): pre-build strings/GlyphLayouts on state change, reuse Color objects.
- No new tests. Do not commit; do not `git add`. Use the Edit tool (the per-file code-reviewer hook runs).

## Verify
Mirror: `cd /tmp/claude-0/-home-user-Toon3DA/48c7ae85-305d-55bc-9e8b-bb4641640da1/scratchpad/mirror && ./gradlew
:core:test :lwjgl3:compileJava` (only StoryBarkTest red). No display in this container: if you can render
offscreen headless (e.g. a LWJGL3 hidden window is NOT available) skip it and say "desktop check owed"; describe
precisely what to look at.

## Handover
Files; new constants; the World wiring (methods, tap regions); what the desktop check must look at (A5 + layout
clearances); any decision taken.
