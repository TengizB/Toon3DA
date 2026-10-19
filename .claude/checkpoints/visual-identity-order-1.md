# CHECKPOINT — Visual Identity 1: atmosphere (palette, moods, grade)

**PURPOSE:** the live resume record for `.claude/agents/ideas/visual-identity-order-1.txt`. This session may be cut
off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE FIRST and
continues from the first step whose box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `ccr-1c332c6a-z0q18l`
**IDEA:** `.claude/agents/ideas/visual-identity-order-1.txt` — the spec. Its rules (R1-R13) and acceptance
criteria (A1-A14) are referred to by number below and are NOT repeated here.
**LANE:** full lane / `util` + `render` + `world` (+ docs).
**TESTS:** none — not balance-bearing (CLAUDE.md Testing Policy). Verified by build + run; `./gradlew test`
must stay at baseline.
**STARTED:** 2026-10-19
**BASELINE:** branched from `b0aa2e5`. Mirror build compiles; `test` = 480 tests, **1 pre-existing failure**:
`narrative.StoryBarkTest` ("a joke survived into the deepest strata: bark.depth.core.2", LEVITY vs LORE).
Not ours, not in scope — "green" for this work means "no failure beyond that one".

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `visual-identity-order-1/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `visual-identity-order-1/CPn:`, **with this file's
tick in the same commit as the work**. Never more than one checkpoint uncommitted. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- No allocations in any render()/per-frame path; every Disposable disposed (vignette texture/batch).
- Every value in a constants file (ArtDirection / RenderConstants); every formula in GameMath with the comment block.
- register() rows, never a switch on mood/material id.
- Emissive exemption (R4) — glows, bars, icons, hazards, portal never graded.
- Grade is multiplicative and luminance-neutral (R3); never raise the shade floor.
- Floor worker threads read only immutable per-floor mood data (R13).
- Full identifier names (CLAUDE.md naming table). No new tests. No new persistence keys.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [ ] **CP1** — palette constants + GameMath formulas.
      DONE WHEN: util/ArtDirection has every TECHNICAL NOTES constant; GameMath.atmosphereFogFactor and
      applyRgbShadeToPackedRGBA exist with comment blocks; nothing reads them yet; build green.
- [ ] **CP2** — mood + material registries, AtmosphereGrade, World resolution.
      DONE WHEN: LightingMood/Registry/LightingMoods (5 rows + R11), SurfaceMaterialRegistry/SurfaceMaterials
      (10 generators), AtmosphereGrade exist; World bootstraps, resolves mood (R11/R12), logs
      "Atmosphere mood=<id>"; build + test at baseline.
- [ ] **CP3** — WallRenderer grades walls, doors, columns (R1). DONE WHEN: build green; A3 walls part.
- [ ] **CP4** — FloorCeilingRenderer materials, horizon, per-row grade (R7, R8, R13). DONE WHEN: A3 holds; build green.
- [ ] **CP5** — Prop/ShopMachine/Enemy bodies graded (R1, R2, R4, R5). DONE WHEN: A5-A7 hold; build green.
- [ ] **CP6** — weapon tint (R6) + ViewVignetteRenderer (R10), disposed by World. DONE WHEN: A9, A10; build green.
- [ ] **CP7** — five moods checked via dev override (A4, A8, A12); docs (A14). DONE WHEN: build + test at
      baseline; ready for reviewer.
- [ ] **CP8** — reviewer PASS; STATUS IMPLEMENTED.

## NOTES CARRIED FORWARD

- **ENV (cost time):** `./gradlew` cannot run in the repo (Maven Central 429 on the Android Gradle plugin).
  Gate via the desktop-only MIRROR in the session scratchpad (`<scratchpad>/mirror/`): symlinks to
  core/lwjgl3/assets/gradle/gradlew/gradle.properties/docs/story; `settings.gradle` =
  pluginManagement{googleapis mirror + gradlePluginPortal} + `include 'lwjgl3','core'`; root `build.gradle`
  = the repo's with the `buildscript{}` block deleted, `configure(subprojects)`, and
  `maven { url = "https://maven-central.storage-download.googleapis.com/maven2/" }` FIRST in subprojects
  repositories. Test XML lands in the REAL `core/build/test-results/`.
