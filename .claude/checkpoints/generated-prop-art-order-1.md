# CHECKPOINT — generated props 1: terminal, specimen tube, exit portal

**PURPOSE:** the live resume record for `.claude/agents/ideas/generated-prop-art-order-1.txt`. This session may be cut
off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE FIRST and
continues from the first step whose box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `ccr-404ba29e-qibhqp`
**IDEA:** `.claude/agents/ideas/generated-prop-art-order-1.txt` (+ master `generated-prop-art.txt`) — the spec. Rules
(R…, PS…) and acceptance criteria (A…) are referred to by number below and are NOT repeated here.
**LANE:** full lane — kit (`.claude/skills/enemy-art`) + `render` (+ `render/propvisual`, new) + `tileset/render.tilesetgfx` registrations + docs.
**TESTS:** none — not balance-bearing (Testing Policy; V7). Verified by build + run-game screenshots.
**STARTED:** 2026-10-23
**BASELINE:** branched from `72ef102`.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `generated-prop-art-order-1/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD.
4. `git status` — a dirty tree is UNVERIFIED work: finish it to the step's DONE WHEN, or restore and redo.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `generated-prop-art-order-1/CPn:`, with this file's tick in the
same commit. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- No gameplay change: no symbol, placement, solidity, LOG_FOUND or portal behaviour change (V4).
- New kit materials appended LAST; enemies + walls must regenerate byte-identical.
- No allocation in render(); every Texture disposed; constants in RenderConstants; formulas in GameMath.
- register() rows, never a switch, for the prop -> sheet binding.
- Old terminal / bio-pod / portal Pixmap generators deleted (PS6). Never copy the old textures (PS brief).
- No new tests.

## STEP LEDGER

- [x] **CP0** — this file + the idea files committed and pushed.
- [x] **CP1** — neon materials in kit.py, props.py, three sheets in assets/textures/props/generated/, previews sent.
      DONE WHEN: `python3 props.py <dir>` writes the 9 files twice cmp-identical; bestiary/effects/walls regenerate identical.
- [x] **CP2** — render/propvisual + PropRenderer sheet path + constants; old generators deleted.
      DONE WHEN: mirror build green; game draws the three props from their sheets.
- [ ] **CP3** — in-engine screenshots (default + one other mood) sent to the owner.
- [ ] **CP4** — docs (guide §14, SKILL.md, tile-symbols, art-direction, tileset doc, CLAUDE.md).
- [ ] **CP5** — owner approval written into DECISIONS; STATUS IMPLEMENTED.

## NOTES CARRIED FORWARD

- CP1: `python3 props.py` writes the 9 files; two bakes cmp-identical. Kit change = 4 emissive inks appended
  last (phosphor, neon_cyan, rad, neon_violet). Enemy regen check: the committed enemy atlases ALREADY differ
  from what the HEAD kit bakes (pre-existing drift, likely the smooth-bake commit 62991f6); the HEAD kit and
  the new kit bake identical files (crawler checked byte-for-byte), so this order changes nothing there.
  Do NOT re-ship enemy atlases as part of this order.
- Big glowing areas (fluid, vortex, screen text) are painted with numpy from the ink ramps; kit translucent
  materials washed out to pale at that size (tried glass_cyan, removed).
- CP2: mirror build green except the known pre-existing StoryBarkTest failure (bark.depth.core.2, unrelated).
  TextureGeneratorRegistryTest updated (behaviour changed legitimately): generator ids == expectedGeneratorIds()
  = catalog minus GeneratedPropRegistry sprites. In-engine: lookat:T / lookat:& / lookat:> on seed 7 floor 1
  (kill:0 x8 first - enemies stand in front of the terminal; LEAVE IT 644,100 for the relic offer).
