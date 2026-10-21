# CHECKPOINT — generated-enemy-art order 1: animation kit, Warden + Tick clips

**PURPOSE:** the live resume record for `.claude/agents/ideas/generated-enemy-art-order-1.txt`. This session may be cut
off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE FIRST and
continues from the first step whose box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `ccr-ee443f6b-5vglbc` (restarted from `origin/master` cb0c4a6 — its earlier PR #317 was merged)
**IDEA:** `.claude/agents/ideas/generated-enemy-art-order-1.txt` — the spec. Its rules (R1-R4) and acceptance
criteria (A1-A6) are referred to by number below and are NOT repeated here.
**LANE:** full lane / offline tooling only: `.claude/skills/enemy-art/` + `assets/textures/enemies/generated/` + docs.
**TESTS:** none — not balance-bearing; no game code (Testing Policy, series G12). Verified by previews,
determinism (A3) and `./gradlew build` unaffected (A6).
**STARTED:** 2026-10-21
**BASELINE:** branched from `cb0c4a6`. No Java changes in this order, so the build state is the baseline's.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `generated-enemy-art-order-1/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `generated-enemy-art-order-1/CPn:`, **with this
file's tick in the same commit as the work**. Never more than one checkpoint uncommitted. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- No Java / runtime changes (SCOPE OUT; order 2 owns the runtime).
- Generated in code only, deterministic, seeded (guide N1, R4); nothing post-edited (R1).
- Never open the old image-model enemy textures (guide N2 / G1).
- One light rig, one style lock (G3); binary alpha in every shipped PNG (A4); atlas <= 2048x2048.
- Poses only — the approved LOOK does not change; rest-pose change goes to the owner (SCOPE OUT).
- OWNER GATE: at CP4 GIFs + strips are sent; the final outputs are NOT committed until the owner approves (CP5).
  Owner instruction for this run: stop at the gate and wait.
- Docs: guide section 7 + SKILL.md updated in the same commit as the final kit (CP6).

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [x] **CP1** — kit renders a rig at a pose; shape-local grain; anchors reported; a single test clip renders.
      DONE WHEN: `kit.py` has Rig/Clip/easing + per-shape grain + anchors, and a test clip renders frames.
- [x] **CP2** — packer + manifest + glow atlas + GIF preview work on a test clip.
      DONE WHEN: a test creature writes `<name>.atlas.png`, `.glow.png`, `.clips.json`, per-clip GIFs and a strip.
- [ ] **CP3** — warden rig + full clip set (standard 9 + DORMANT + WOUNDED) generated; GIFs saved (scratch, not shipped).
- [ ] **CP4** — harvest_tick rig + full clip set generated; GIFs saved; both sent to the owner.
- [ ] **CP5** — owner approval recorded in DECISIONS (stop here until it is).
- [ ] **CP6** — outputs committed (old stills removed); guide section 7 + SKILL.md updated; reviewer PASS;
      STATUS: IMPLEMENTED.

## NOTES CARRIED FORWARD

(none yet)

**CP1 (kit core):** `kit.py` now has `Xf` (affine transforms: move/turn/scale/reflect_x/segment, `a.then(b)`,
callable on a shape list, `.pt()`), `Shape.frame` (canvas -> shape-local 3x3; grain sampled there with an
integer lattice hash `value_noise_at`, seeded per material via crc32 — never Python `hash()`), per-shape
`glow` (0 dead / 1 rest / >1 flare; `GLOW_DEAD` = 0.05), `Pose`/`Figure`/`Rig`/`Clip` (per-parameter tracks,
easings linear/in/out/inout/hold, modes loop/once/hold, `variant()`), and `render(..., return_glow=True)`.
Rest-pose re-render of both stills: identical alpha, mean RGB diff 0.4 (warden) / 2.2 (tick) — expected (A2 re-approval).
Packer/manifest/previews were written in the same pass; CP2 verifies them.

**CP2 (bake):** `kit.write_creature(rig, clips, out_dir, preview_dir)` -> `<name>.atlas.png`, `.glow.png`,
`.clips.json` (+ `<name>_<clip>.gif`, `<name>_strip.png`). Verified on a scratch test rig: binary alpha in both
atlases, byte-identical on re-run. Consecutive identical frames merge (durations sum); identical frames
across clips share one atlas rect. One-shot tracks whose first key is late ease in from the rig default.
Manifest coords: image space, top-left origin, y DOWN (order 2 must flip for LibGDX world space).
