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
- [ ] **CP1** — the performance probe.
      DONE WHEN: `RenderPerformanceProbe` exists and is wired into `World.render`; with `-Dtoon3d.perfProbe=cpu` the
      log prints per-pass ms + draw calls every 5 s and the readout draws top-centre; with the property absent
      nothing is drawn or logged; `run.sh` accepts `--probe`; build green.
- [ ] **CP2** — the investigation.
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

(none yet)
