# CHECKPOINT — generated-enemy-art order 2: the animated enemy runtime

**PURPOSE:** the live resume record for `.claude/agents/ideas/generated-enemy-art-order-2.txt`. This session may be cut
off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE FIRST and
continues from the first step whose box is not ticked. Everything ticked is committed and pushed.

**BRANCH:** `ccr-3e3cd62b-w39s9t` (equal to `origin/master` a99b493 at start — order 1's PR #318 merged)
**IDEA:** `.claude/agents/ideas/generated-enemy-art-order-2.txt` — the spec. Its rules (R1-R6) and acceptance
criteria (A1-A9) are referred to by number below and are NOT repeated here.
**LANE:** full lane / `enemy` (seam only) + `render/enemyvisual` (new) + `render/EnemyRenderer` + `world` + run-game
capture tooling + docs.
**TESTS:** none — not balance-bearing (series G12, Testing Policy). Verified by the mirror build + run-game captures.
**STARTED:** 2026-10-21
**BASELINE:** branched from `a99b493`. Known pre-existing test failure: `narrative.StoryBarkTest`
(`humourLivesNearTheSurfaceAndDiesNearTheCore`) — not ours, not to be fixed. Anything else red is reported.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `generated-enemy-art-order-2/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `generated-enemy-art-order-2/CPn:`, **with this
file's tick in the same commit as the work**. Never more than one checkpoint uncommitted. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- G9 / R2: visuals are cosmetic; the animator never writes to Enemy / EnemyManager / World; no rule, timing,
  turn or action-lock change. `enemy/` never imports `render/` (A8).
- The seam follows `enemy/EnemyVoiceListener` exactly: no-op/null default, World owns the impl, payload reused.
- R6: no allocation in render() or per-frame update; GL only on the render thread; every Texture disposed.
- register() rows, never a switch (EnemyVisualRegistry, EnemyAnimationRules as data).
- Build/verify with the run-game MIRROR build (`/tmp/toon3d-runtime/mirror`), never the repo-root gradlew.
- No new tests (G12). Constants in the matching Constants file (heavy-hurt 25% -> EffectConstants, AS2).
- OWNER GATE at CP5: burst GIFs/screens of ACID_DRONE and SHELL_BRUTE for every core clip, default mood +
  one other, sent; then STOP until the owner approves (owner instruction for this run).
- Docs at CP6: guide 8 + 10, art-direction 16, enemy-system.txt, CLAUDE.md package row.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [x] **CP1** — the seam exists with a no-op default, fired at every site; build green; sim/ unchanged.
      DONE WHEN: `enemy/EnemyVisualListener` + `EnemyVisualMoment` + payload exist, EnemyManager fires every
      moment of the idea's list at its commit site, nothing in `sim/` changed, mirror build compiles.
- [x] **CP2** — registry + manifest parser + per-floor atlas cache load the two creatures; build green.
- [x] **CP3** — animator + rules + dying pool; EnemyBodyDrawer draws IDLE in-engine for both types.
- [x] **CP4** — every core clip plays from its moment in-engine; glow pass; legacy types unchanged.
- [ ] **CP5** — capture tooling; burst GIFs sent; owner approval recorded (stop until it is).
- [ ] **CP6** — performance measured; docs; reviewer PASS; STATUS: IMPLEMENTED.

## NOTES CARRIED FORWARD

**CP1 (seam):** `enemy/EnemyVisualListener.onEnemyVisualMoment(EnemyVisualEvent)`; `EnemyManager.setEnemyVisualListener`.
The payload is ONE reused `EnemyVisualEvent` (enemy, moment, amount/healthFraction/outcome/absorbed/blocked/source*,
from*/to*, ability, status, deathCause). Sites: ALERTED (phaseA x2, hunter, spawnEnemyInternal), WIND_UP_STARTED
(announceWindUp = every committed wind-up), ATTACK_COMMITTED (melee, ranged after the shard check, lance, charge, sow),
ABILITY_USED (executeSpecial, before dispatch — area strike / debuff / summon / self-destruct ride this, never also
ATTACK), MOVE_STARTED (executeMove after a real step; the charge's whole rush is ONE move fired after the loop),
DAMAGED (applyDamageTo incl. shard-absorbed path, lance ally splash, reflect), STAGGERED (stagger + riposte),
KNOCKED_BACK (tryPushEnemy), DEFEND_STARTED, STATUS_APPLIED (burn, burn stack, vulnerable, exposed, empowered),
DIED (killEnemy(enemy, cause) fires FIRST, enemy still on its tile). NOT fired: DoT tick damage (order 3 — status
visuals), boss moves (BossFloorController; bosses unmigrated). DECISION: ordinary enemies never slid between tiles
(only bosses use Enemy.beginSlide). R2 forbids the visual layer writing Enemy, so MOVE_STARTED carries from/to and the
visual layer owns a cosmetic slide for migrated types (CP3/CP4).
Baseline build: only `narrative.StoryBarkTest` red (known).

**CP2 (data):** `render/enemyvisual/EnemyVisualRegistry` (bootstrap rows ACID_DRONE->warden, SHELL_BRUTE->harvest_tick),
`EnemyClipId` (the 11 standard clips; manifest names), `EnemyClipManifest.parse(FileHandle)` (immutable; frames keep
IMAGE-space coords; `Clip.frameIndexAt(seconds)` / `isFinishedAt`; anchors GROUND/HIT_CENTRE/EYE/EMITTER(muzzle|mouth)),
`EnemyAtlasCache` (per floor; `ensureLoaded(type)` render thread, `visualFor(type)`, failed creature logged once ->
legacy, R5). Paths: `EnemyConstants.ENEMY_GENERATED_DIRECTORY` + `ENEMY_GENERATED_MANIFEST_SUFFIX`. Verified in-engine
(with CP3's wiring in the tree): `loaded warden (1024x2048 + glow) in 178 ms`, `loaded harvest_tick (2048x2048 + glow)
in 257 ms`.

**CP3 (animator + draw):** `EnemyVisualLayer` (NEW facade, not in the idea's class list: the per-floor owner of the
cache, the animator pool (IntMap by enemy id, `ENEMY_VISUAL_ANIMATOR_POOL_CAPACITY`), the `DyingEnemyPool`; it IS the
`EnemyVisualListener`; `update(dt)` is called beside `enemyManager.advanceHitFlash` in World, so it freezes with the
world). `EnemyAnimationRules` = R1 as rows (`Kind` enum with per-constant `apply`) + state->held-clip table.
`EnemyAnimator.currentFrame(enemy)` picks the highest of one-shot / held state / slide MOVE / IDLE|DORMANT; idle phase =
frac(id*0.618)*IDLE duration (R3). `EnemyBodyDrawer.prepare/drawColumns` places the CANVAS as the billboard and the
frame at its manifest offset. EnemyRenderer: migrated types skip the lunge; glow rides the muzzle-light additive
bracket; dying instances are merged into pass 1 farthest-first (`drawDyingFartherThan`), so no other pass changed.
`GameMath.distanceFadeAlpha` (glow fade). Capture steps spawn/alert/damage/kill/clip/burst are already in
CaptureLauncher (committed here, documented in SKILL.md at CP5). Verified: 1 Warden + 2 Ticks spawned dormant draw
their DORMANT loop, frames advance between shots, ticks out of phase. Build: 480 tests, only StoryBarkTest red.

**CP4 (clips from moments, verified in-engine):** clip prints every 0.1 s (`clip:INDEX`; time-SORTED scripts — steps run
in list order, so a helper sorts them) showed: DORMANT -> `alert` -> ALERT (0.42/0.5 s) -> IDLE; Warden ATTACK on each
ranged turn then IDLE; Warden MOVE while closing (off-lane spawn); Tick WIND_UP from its commit (15.2 s) held for the
whole player turn to the charge (16.7 s), then ATTACK with the rush slide (MOVE tail); `damage` 2/45 -> HURT_LIGHT,
15/45 and 32/95 -> HURT_HEAVY (25% rule); `kill` -> DEATH from the dying pool, corpse frame held, then the sim's corpse
decal. A5: glow pixels identical colour under cool_steel vs corrupted_violet at 6-7 tiles. A6: GHOUL / CRAWLER /
PLAGUE_HULK / IRON_STALKER spawned identically on master a99b493 (worktree + its own runtime dir) and here: bodies
match; residual diffs <= 20 levels everywhere incl. the bare far wall (random floor weapons' light spill), so not
attributable to the renderer; every changed line in EnemyRenderer's live loop is gated on `animator != null`.
NOT exercised in-engine: STAGGERED / KNOCKED_BACK / DEFEND (rows exist; neither creature defends, stagger needs the
shotgun). Capture `spawn` now tolerates a tree without the listener field (baseline runs).
