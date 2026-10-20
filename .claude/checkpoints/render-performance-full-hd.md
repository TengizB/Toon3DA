# CHECKPOINT — render performance: Full HD smooth again (floor, walls)

**PURPOSE:** the live resume record for `.claude/agents/ideas/render-performance-full-hd.txt`. This session may be
cut off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE FIRST and continues from the
first step whose box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `claude/render-performance-full-hd-sy1e90`
**IDEA:** `.claude/agents/ideas/render-performance-full-hd.txt` — the spec. Its rules (R1-R21) and acceptance
criteria (A1-A18) are referred to by number below and are NOT repeated here.
**LANE:** full lane — `render` (+ `render/tilesetgfx`), `util` (GameMath, RenderConstants, RenderResolution),
`world` (probe brackets), run-game skill tooling, docs.
**TESTS:** none new — render-only, not balance-bearing (CLAUDE.md Testing Policy). Verified by the mirror build +
the run-game capture driver (frame timing, PIL checks, montages).
**STARTED:** 2026-10-20
**BASELINE:** branched from `a46876f`; baseline build/test result recorded in NOTES at CP1.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `render-performance-full-hd/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `render-performance-full-hd/CPn:`, **with this file's tick
in the same commit as the work**. Never more than one checkpoint uncommitted. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- No custom ShaderProgram (series rule V2) — SpriteBatch default shader only.
- FULL HD stays the default; never lower 1920 columns / 540 rows / the default tier (R21 stop rule instead).
- No allocation inside any render() path; every Disposable disposed by its owner.
- Every formula a `GameMath` static with the Formula/Derivation/Edge cases block; numbers in constants files.
- CLAUDE.md naming (no abbreviations); no keyboard input; no tile symbol / persistence / story / balance change.
- Walls and billboards pixel-identical (A13); floor within ±4/255 mean per channel (A9).
- Container timings are a llvmpipe PROXY, same-session A/B interleaved, never presented as phone numbers.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [x] **CP1** — the performance probe.
      DONE WHEN: `RenderPerformanceProbe` exists and is wired into `World.render`; with `-Dtoon3d.perfProbe=cpu` the
      log prints per-pass ms + draw calls every 5 s and the readout draws top-centre; with the property absent
      nothing is drawn or logged; `run.sh` accepts `--probe`; build green.
- [x] **CP2** — the investigation.
      DONE WHEN: this file holds the B0/B1 table for P1-P4 (P5/P6 filled or marked not stageable), the per-pass
      breakdown, GC counts, C1-C8 ranked by measured share, the R17/R18 go/no-go decisions, and the names of the
      saved B0 reference captures. No game code changed.
- [ ] **CP3** — formulas + material textures.
      DONE WHEN: GameMath holds the span-walk, span-factor, base/excess-split and UV-rebase methods (comment
      blocks), FloorCeilingRenderer builds + disposes the 4-level Repeat/Nearest material Textures per floor (not
      yet drawn); build green; the game looks exactly like B0.
- [ ] **CP4** — the span floor/ceiling.
      DONE WHEN: floor + ceiling drawn as spans (R1-R9); backbuffer/Pixmap/backdrop Texture/upload/thread pool
      gone; RenderResolution carries only the row count; A2, A3, A4, A7, A9, A10, A11, A12, A14 hold (numbers
      recorded here); build green.
- [ ] **CP5** — walls.
      DONE WHEN: opaque-level base pass unblended (R16); R17/R18 built iff CP2 said go; A5 + A13 vs CP4 captures
      hold; build green.
- [ ] **CP6** — billboards + allocations.
      DONE WHEN: occluder/aura loops range-ordered (R19); CP2's render-path allocations removed (R20); A6 (if
      stageable), A8, A13 vs CP5 hold; build green.
- [ ] **CP7** — AFTER numbers + docs.
      DONE WHEN: AFTER columns filled for every pose; A1 holds (or the R21 stop report written + owner told); docs
      per plan step 8 updated; build + test show no new failure.
- [ ] **CP8** — gate.
      DONE WHEN: reviewer PASS (or PASS WITH FIXES, fixes committed); IMPLEMENTATION NOTE with final table under
      the idea header; STATUS: IMPLEMENTED with the date.

## NOTES CARRIED FORWARD

**CP1 (closed).** Baseline at `a46876f`: mirror `:core:build` = 480 tests, 1 failure
(`narrative.StoryBarkTest`, pre-existing — same as the render-quality checkpoint). That is the A17 baseline.
- Probe API (World owns one, `final`, constructed with the World): `beginFrame()` / `begin(Pass)` / `end(Pass)` /
  `addUploadBytes(long)` / `endFrame(camera)` / `dispose()` (calls `GLProfiler.disable()` so the next World's probe
  does not wrap ours). `World.render` is now `beginFrame(); renderFrame(camera); endFrame(camera)`.
- Passes: floor, walls, props, shop, enemies (enemy + attack effects + impact world effects), vignette, weapon,
  screenfx (impact overlays + guard + hit/status vignettes), minimap, hud, overlays (touch, text, all modals, fade).
- `FloorCeilingRenderer.getUploadBytesLastFrame()` reports the backdrop bytes (2,073,600 at FULL_HD); CP4 makes it 0.
- Log line: `[perf] cpu frames 120 | frame avg A worst W ms | pass ms floor=... | pass dc floor=... | draws D binds B
  shaders S verts V upload U B/frame`. `run.sh --probe cpu|sync`, `--gclog FILE` (count with `grep -c Pause`).
- First look (cool_steel P1, cpu mode): World.render CPU 5.9 ms of which floor 3.3, walls 1.05; capture frame
  (with glFinish) 14.7 ms ⇒ most of the frame is llvmpipe GPU work after submission — sync mode is needed to
  attribute it (CP2). Draw calls 59/frame: walls 5, props 19, shop 26 (!), floor 1.

**CP2 (closed) — INVESTIGATION.** Method: `/tmp/claude-0/scripts/measure.sh` (scratch, not committed) plays one
script per run: standard opening, P1 static 14-24 s, P2 hold ROTATE_RIGHT 25-35 s, P3 hold FORWARD 36-46 s, P4 a
shotgun pulse every second 47-57 s, P5 `set:gameState.redAlert:true` then 59-64 s; `--fps 0`, `--gclog`; the save is
reset before every run (reprintCount 1, `story.setting.render.resolution` 1 = FULL_HD for B0, 0 = HD for B1). B0/B1
were measured from a detached worktree at CP1 (`084f2d3`, probe off) so later edits could not leak into the
baseline. Interleaved B0,B1 per mood, two reps. llvmpipe proxy, ±1.5 ms; never phone numbers. After P2 the walk
ends facing the start-room shop machine, so P3-P5 are dominated by it (consistent across A/B).

| pose (mood)        | B0 FULL_HD ms | B1 HD ms |
|--------------------|---------------|----------|
| P1 cool_steel      | 14.81 | 10.44 |
| P1 clinical_white  | 14.37 |  9.85 |
| P1 amber_hot       | 14.12 |  9.74 |
| P1 corrupted_violet| 14.10 | 10.31 |
| P1 core_white_viol | 14.34 |  9.97 |
| P2 rotate (5-mood) | 14.46 | 10.19 |
| P3 walk (5-mood)   | 18.24 | 13.94 |
| P4 muzzle (5-mood) | 18.89 | 14.86 |
| P5 alert (5-mood)  | 17.74 | 14.20 |
| P6 occluded        | not stageable (floor 1 has no enemy behind a prop at a scriptable spot) | |
| GC events, whole run (cool_steel, 2 runs) | 4, 4 | 4, 4 |

Per-pass (probe, cool_steel, P1 window), B0 FULL_HD vs B1 HD:
- cpu mode: World.render CPU 5.48 vs 3.06 ms; floor **3.15 vs 0.68**; walls 0.99 vs 0.72; props 0.80 vs 0.59;
  upload 2,073,600 vs 230,400 B/frame. Draw calls: floor 1, walls 5 (3 in P2, 1 in P3+), props 19, shop 0-97.
- sync mode (GPU attributed): frame 18.50 vs 13.75; floor **6.20 vs 2.72**; walls 3.44 vs 2.80; props 2.97 vs
  2.43; vignette 2.46 vs 2.48; minimap 0.93; hud 0.91; overlays 0.91.
- Wall base-pass submission share (temporary nanoTime split inside WallRenderer in the worktree, reverted):
  45%, 51%, 52%, 45%, 35%, 27% of the wall pass in the P1/P2 windows, 2.5-4% in P3+ (median ≈ 31%).

C1-C8 ranked by measured share of the B0 sync frame (18.5 ms):
1. **C1+C2+C3 floor/ceiling** — 6.2 ms (34%); +3.5 ms over HD = most of the FULL_HD premium (CPU fill 3.15 ms +
   2 MB/frame upload).
2. **C5 walls** — 3.44 ms (19%); +0.64 ms over HD (1.5x columns). Submission is ~45% of the CPU wall pass in P1/P2.
3. **C6 billboards** — props 2.97 ms (16%), shop machine up to 96 draw calls when close (texture + blend switches
   per layer per machine, plus per-column occluder interleave on the body).
4. **C8 full-screen blended quads** — the view vignette alone is 2.46 ms on llvmpipe (fill-rate); OUT of scope to
   change (art), noted for a follow-up.
5. **C4 threads** — not separable; the floor pool's 3 workers disappear with C1 (A7: B0 runs 3 floor + 3 wall workers).
6. **C7 load-time double allocation** — one-off per floor, not on the frame; removed by CP4 (falls out of R1).
7. **C8 GC** — 4 GC events per whole run at both tiers, none attributable to the frame ⇒ R20 list is empty (no
   render-path allocation found by measurement); CP6 still code-reads the touched render() paths.

DECISIONS: **R17 W-ATLAS = NO-GO** (median wall draw calls P1-P3 ≈ 3, threshold 12 — not built).
**R18 W-MERGE = GO** (base-pass submission ≈ 31% median > 25%) — CP5 builds the exact-equal column merge.

B0 reference captures (scratchpad `b0-reference/`): `p1-<mood>.png` ×5 (B0 FULL_HD P1), `hd-p1-<mood>.png` ×5
(B1), `p2a..p2d.png` (P2 frames), `p4.png` (muzzle), `p5.png` (alert). Full runs: `/tmp/claude-0/baseline/`.
CP3-CP6 drafts were written while the matrix ran and parked as patches in `/tmp/claude-0/scripts/cp{3,5,6}.patch`
(+ the CP4 draft `FloorCeilingRenderer.java`, `SurfaceMaterialTextures.java`) — scratch only; the tree was kept
clean so CP2 contains no game code.
