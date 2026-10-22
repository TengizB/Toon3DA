# CHECKPOINT — generated-wall-art order 1: the wall kit + the Outer Facility region set

**PURPOSE:** the live resume record for `.claude/agents/ideas/generated-wall-art-order-1.txt`. This session may be cut
off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE FIRST and
continues from the first step whose box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `ccr-314143e1-h2no8v` (created from master `59c8e7a`)
**IDEA:** `.claude/agents/ideas/generated-wall-art-order-1.txt` — the spec (series rules W1-W14 and decisions in
`generated-wall-art.txt`). Its rules (R1-R12) and acceptance criteria (A1-A7, A6b) are referred to by number below
and are NOT repeated here. THE METHOD: `docs/wall-art-creation-guide.txt`.
**LANE:** full lane / enemy-art skill (`kit.py` appended, new `walls.py`) + generated assets under
`assets/textures/walls/generated/` + docs (wall guide, sprite guide pointer, SKILL.md). OFFLINE ONLY — no Java.
**TESTS:** none — not balance-bearing (series W10, Testing Policy). Verified by byte-identical regeneration,
previews (never committed) and run-game captures (throwaway preview hack, never committed).
**STARTED:** 2026-10-22
**BASELINE:** branched from `59c8e7a`. No Java changes in this order, so the Gradle build is unaffected.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `generated-wall-art-order-1/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
   A dirty `core/src/main/java/.../TextureGeneratorRegistry.java` is the in-engine preview hack: `git restore` it.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `generated-wall-art-order-1/CPn:`, **with this
file's tick in the same commit as the work**. Never more than one checkpoint uncommitted. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- OFFLINE ONLY: no Java committed. The in-engine preview hack (TEMP edit of TextureGeneratorRegistry) lives in the
  working tree only and is reverted before every commit.
- kit.py is APPENDED only (materials at the END of MATERIALS, glyphs added to STENCIL_FONT, new helpers). After every
  kit change `bestiary.py` + `effects.py` regenerate every committed enemy file byte-identical (cmp).
- W1/E2: the old wall/door/column/floor textures and generators are never opened.
- E4/W13: no focal point on any BASE; 6-copy strip test for every BASE. E5/W14: no figures. Section 4 EDGE RULE: no
  focal shape crosses a tile edge (>= 8 px margin).
- W5/E6: no saturated decoration colours, no red cross, no SUIT_AMBER; keycard colours only on keycard door lock panel +
  frame stripe, emissive, exact values.
- R11 manifest schema exactly (order 2 consumes it). R12 deterministic.
- Previews never committed (scratchpad only).
- CP6 is a HARD STOP: send the owner the sheets, strips, flicker GIF and cool_steel captures; wait for the verdict.
  Do NOT start order 2 in this session.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [x] **CP1** — kit.py additions in (environment materials, stencil capitals, helpers).
      DONE WHEN: every committed enemy atlas/glow/manifest + fx file regenerates byte-identical.
- [x] **CP2** — walls.py writes a valid manifest + atlas + glow + mips for the HALL group alone.
      DONE WHEN: `python3 walls.py <dir> outer_facility` writes the files; twice -> byte-identical; hall strips seamless.
- [x] **CP3** — all six room families + SIGNATURE generated and in the manifest.
      DONE WHEN: manifest lists 30 walls; every room-family strip seamless; 6-copy BASE strips pass.
- [x] **CP4** — doors, columns, floor, ceiling generated; the five shipped files committed.
      DONE WHEN: A1, A3, A4 hold on the committed files.
- [x] **CP5** — docs/wall-art-creation-guide.txt (2, 12, 15, 16 A) + SKILL.md + sprite guide pointer updated (A6).
- [x] **CP6** — owner verdict recorded in DECISIONS (approval, or the requested changes done and re-shown).

## NOTES CARRIED FORWARD

**CP0:** baseline 59c8e7a.

**CP1:** kit.py APPENDS 16 materials after `pupil`: the 8 prototype ones (concrete, concrete_dk, ceramic, rubber, rust,
soul [emissive], nacre, capsule [translucent, glow=soul; glow=0 on a shape = plain glass]) + region A's livery_dk (ring
panels, one step under livery - lesson 15.8), paint_teal (medical care band), insulation, frost, cloth, and the three
EMISSIVE keycard inks key_red/key_yellow/key_blue whose top 3 ramp steps are the EXACT colours. NOTE: the exact
keycard values live in `RenderConstants.KEYCARD_AURA_*` (not ItemConstants/CombatPalette as R10 says): red
(255,46,41), yellow (255,219,46), blue (56,148,255). STENCIL_FONT gains C H I L N O U V Y F X Z J Q (all capitals now).
Helpers `kit.chamfer_rect(x0,y0,x1,y1,c)` and `kit.tile_copies(shapes, period, columns=3, rows=1)`.
Verified: bestiary.py + effects.py into the scratchpad -> 32 of 32 committed enemy files byte-identical. A full enemy
regen takes ~5.5 min in this container (not ~50 s).

**CP2:** `walls.py` = the bake (`bake_face` 3-face canvas, `bake_surface` 3x3), primitives (backing, band, rail,
grammar, panel, tile_field, pipe_run, grille, tear_edge, vein, lock_panel), `mip_chain`, a deterministic SKYLINE
packer (faces + glow-frame slots first, then the 128 mips, then the 64 mips, each in manifest order; smallest
power-of-two square), the manifest writer (R11 + "atlas"/"glow"/"atlasSize" keys), previews (per-group sheets, base
x6 strips, runs, focal-alone, surfaces 3x3, flicker GIF, `<region>_seams.txt` join ratios). DEVIATION (record in
the IMPLEMENTATION NOTE): region builders live in `outer_facility_walls.py` (REGION row imported by walls.py),
mirroring `<name>_rig.py` + bestiary.py; walls.py aliases itself into sys.modules["walls"] when run as __main__.
LESSON (guide 15.14 at CP5): shapes crossing the tile edge must be `grammar()` - k=0 (smax of equal heights raises
the overlap by k/4) and a BRUSHED grain frame (y only), and never carve/add across the edge (overlaps SUM) - the
first bake showed tiny repeating marks at x~244-268 on the rail and kick plate. A joint = two bands with a gap.
ONE render seed (SEED=101) for the whole A set so trims carry identical grain on every face.
Glow frame level 0 is baked at 0.06 (just above GLOW_DEAD) so the "off" frame holds the dark tube and covers the
lit lamp of the base face. Material `grime` is defined at runtime in outer_facility_walls.py - MOVE IT TO kit.py
(appended) before CP4 and re-run the enemy regen check.
Verified: 9 hall faces, LIGHT 4 frames animate=some (glow 1.07%), two runs byte-identical, join ratios 0.24-0.32.

**CP3:** 30 walls in the manifest (9 HALL + 6 rooms x 3 + SIGNATURE x 3), via the ROOMS table at the bottom of
outer_facility_walls.py. Families by SHAPE: MEDICAL one rounded white livery panel + teal care band (band 186-197,
grammar) / dispenser + scrub hatch; SECURITY riveted gunmetal over carved chevrons / mesh-fronted rifle niche;
CONTAINMENT quilted insulation pads + frost seams / dogged frosted porthole; MACHINERY two darkmetal cabinets +
conduit rails / breaker panel + lever; RESEARCH narrow dark panels + continuous cyan seam lines (grammar rails, R-3) /
wall terminal; STORAGE roll shutter in guide channels / shutter half up + crate edge; SIGNATURE crew lockers with
scratched name plates / one ajar, a short scarf on a hook. E5 note: the first scarf (round hanging chains) read as a
limb, the second (two equal tails) as trousers - the shipped one is a flat band, one long fringed tail + a short
fold (lesson for the guide). 6-copy base strips: pass (only architecture repeats). Deterministic (cmp). No warnings.
Dev helper (not committed): scratchpad/one.py <region> <out.png> <id substrings...> bakes chosen faces fast.

**CP4:** `grime` moved into kit.py (appended after cloth, before the key inks) -> enemy regen 32/32 identical.
Doors: a framed livery sliding leaf, porthole (capsule glass glow=0 in a rubber ring), grab recess, ribbed kick
strip; plain = a cyan status lamp in a small housing; keycard = walls.lock_panel in the exact ink + a header frame
stripe (glow 2.33%). LESSON: a fixture in the SAME layer as a higher-prio/higher plateau panel vanishes (union picks
the panel) - give fixtures their own layer. Columns: round = EIGHT tilted staves (a smooth cylinder dome dithers into
blotches across its slow gradient - lesson), rail ring + caps as adds; square = boxed cable riser. Surfaces use
`walls.grammar2d` (FLAT grain frame: a frame that moves with a copy is unsafe when tiling in both axes) + 
`surface_backing`. Luminance: floor 0.176, ceiling 0.177 (concrete_dk panels + tray; darkmetal read 0.205-0.259).
Shipped: assets/textures/walls/generated/outer_facility.{walls.png (RGB 2048, opaque), glow.png, walls.json,
floor.png, ceiling.png}; a second bake into the scratchpad is byte-identical (A1). A3: 30 walls + 4 doors + 2
columns + surfaces. A4: every join ratio in the seams report <= 1.6 (cross-family joins expected > 1), self-seams
0.27-0.32, surfaces 0.0 / 0.2.

**CP5:** docs/wall-art-creation-guide.txt: section 2 (tools, files, commands, preview list, regen rule), 4 (CROSSING
THE EDGE SAFELY + the bake's focal warning), 5 (region A trim shipped), 9 (region A materials), 10 (glow frame
floor 0.06), 11.1 (exact keycard values from RenderConstants), 11.2 (staves), 12 rewritten as SHIPPED (no [PLANNED:
order 1] marker left), 15.14-15.19 new lessons, 16 A = the shipped design. SKILL.md walls section; sprite guide 13
pointer; CLAUDE.md docs-table line count 456 -> 550. Remaining: CP6 = owner gate (HARD STOP).

**CP4b (after CP5, before the gate):** column_a_round rebuilt: eight planes TANGENT to a circular profile (radius 140,
depth 110), each a slab spanning exactly to its neighbours' intersection lines (+3 px overlap) with slope 200 (a 1 px
edge) - the CP4 staves had grooves that caught the cyan rim as bars, and a max over full-width tangent planes picks
the OUTER planes (the profile is concave). Guide 11.2 / 15.17 still describe it correctly. Ship files re-baked,
byte-identical on a second bake. The in-engine preview hack (TextureGeneratorRegistry.previewFace + WallRenderer door
lines, env TOON3D_WALL_PREVIEW=<faces dir> TOON3D_WALL_PREVIEW_MODE=hall|medical|security) is in the WORKING TREE
ONLY - `git restore` both Java files before any commit.

**CP6 (in progress - WAITING FOR THE OWNER):** gate package sent 2026-10-22 (contact sheets per group, doors/columns,
floor+ceiling 3x3, runs / base x6 / focal-alone strips, LIGHT flicker GIF, in-engine captures under cool_steel).
Captures: seed 7, first route node, enemies killed with kill:0, the preview hack's modes hall / medical (every old
wall id -> medical set) / door (wall_plain* ids -> the four door faces on wall tiles: doors are not wired until order
2, so the door capture shows the FACE at wall scale, not a placed door). The hack only remaps whole old sprite ids, so
no capture shows the P1-P9 placement (order 2). Not seen in-engine: the glow layer (no runtime yet), mips/LOD,
containment/security/research/storage/signature rooms. The Java hack is reverted (tree clean). NEXT: record the
owner's verdict in the idea file's DECISIONS (D1...), apply requested changes + re-show, then tick CP6, reviewer,
STATUS IMPLEMENTED. Do NOT start order 2.

**CP6:** owner APPROVED the set as shown (2026-10-22) -> D1 in the idea file. Next: reviewer gate, then STATUS
IMPLEMENTED + IMPLEMENTATION NOTE. Order 2 is NOT started.
