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
- [ ] **CP2** — walls.py writes a valid manifest + atlas + glow + mips for the HALL group alone.
      DONE WHEN: `python3 walls.py <dir> outer_facility` writes the files; twice -> byte-identical; hall strips seamless.
- [ ] **CP3** — all six room families + SIGNATURE generated and in the manifest.
      DONE WHEN: manifest lists 30 walls; every room-family strip seamless; 6-copy BASE strips pass.
- [ ] **CP4** — doors, columns, floor, ceiling generated; the five shipped files committed.
      DONE WHEN: A1, A3, A4 hold on the committed files.
- [ ] **CP5** — docs/wall-art-creation-guide.txt (2, 12, 15, 16 A) + SKILL.md + sprite guide pointer updated (A6).
- [ ] **CP6** — owner verdict recorded in DECISIONS (approval, or the requested changes done and re-shown).

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
