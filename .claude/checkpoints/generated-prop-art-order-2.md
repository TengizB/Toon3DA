# CHECKPOINT — generated props 2: AI core node

**PURPOSE:** the live resume record for `.claude/agents/ideas/generated-prop-art-order-2.txt`. This session may be cut
off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE FIRST and
continues from the first step whose box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `ccr-404ba29e-qibhqp`
**IDEA:** `.claude/agents/ideas/generated-prop-art-order-2.txt` (+ master `generated-prop-art.txt`) — the spec. Rules
(R…, PS…) and acceptance criteria (A…) are referred to by number below and are NOT repeated here.
**LANE:** full lane — kit (`.claude/skills/enemy-art`) + `render` (+ `render/propvisual`, new) + `tileset/render.tilesetgfx` registrations + docs.
**TESTS:** none — not balance-bearing (Testing Policy; V7). Verified by build + run-game screenshots.
**STARTED:** 2026-10-23
**BASELINE:** branched from `468ee31`.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `generated-prop-art-order-2/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD.
4. `git status` — a dirty tree is UNVERIFIED work: finish it to the step's DONE WHEN, or restore and redo.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `generated-prop-art-order-2/CPn:`, with this file's tick in the
same commit. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- No gameplay change: no symbol, placement, solidity, LOG_FOUND or portal behaviour change (V4).
- New kit materials appended LAST; enemies + walls must regenerate byte-identical.
- No allocation in render(); every Texture disposed; constants in RenderConstants; formulas in GameMath.
- register() rows, never a switch, for the prop -> sheet binding.
- Old terminal / bio-pod / portal Pixmap generators deleted (PS6). Never copy the old textures (PS brief).
- No new tests.

## STEP LEDGER

- [x] **CP0** — idea + this file committed and pushed.
- [x] **CP1** — ai_core baked into assets/textures/props/generated/; previews sent; order-1 sheets unchanged.
- [ ] **CP2** — registry row, height 1.0, old generator deleted; mirror build green (StoryBarkTest aside).
- [ ] **CP3** — in-engine screenshots sent; docs updated.
- [ ] **CP4** — owner approval in DECISIONS; STATUS IMPLEMENTED.

## NOTES CARRIED FORWARD

- CP1: make_ai_core() = one static kit render (ai_core_body) + per-frame numpy paint (window, LEDs, readout,
  cable pulse); ~7 s for the whole bake. Deterministic (two bakes cmp-identical); terminal + bio_pod re-bake identical.
