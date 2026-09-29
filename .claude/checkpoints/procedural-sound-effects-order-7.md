# CHECKPOINT — procedural-sound-effects order 7 (no cap + every action has a sound)

**PURPOSE:** the live resume record for `.claude/agents/ideas/procedural-sound-effects-order-7.txt`.
This session may be cut off by a usage limit at any moment. Whoever picks the work up next reads
THIS FILE FIRST and continues from the first step whose box is not ticked. Everything ticked is
committed and pushed.

**BRANCH:** `claude/game-sound-effects-t8p30e`
**IDEA:** `.claude/agents/ideas/procedural-sound-effects-order-7.txt` — the spec. Its rules (R…)
and acceptance criteria (A…) are referred to by number below and are NOT repeated here.
**LANE:** full lane / `audio` + `render` (ImpactEffectSystem) + `input` + `world` (World, BossFloorController) + `util`.
**TESTS:** none — not balance-bearing (CLAUDE.md Testing Policy). `./gradlew test` and
`./gradlew balanceSim` must be identical before/after (R2).
**STARTED:** 2026-09-29
**BASELINE:** f0b9ae2 (order 6 complete).

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `procedural-sound-effects-order-7/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD.
4. `git status` — a dirty tree is UNVERIFIED work: finish it to the step's DONE WHEN, or `git restore` it.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `procedural-sound-effects-order-7/CPn:`,
with this file's tick in the same commit. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY

- No .wav/.ogg assets; every sound synthesised (docs/sound-system.txt §1).
- register() rows, never a switch; WaveformKind constants carry their own bodies.
- Every formula in GameMath with the Formula/Derivation/Edge-cases block.
- EnemyManager never imports audio — WIND_UP rides EnemyVoiceListener.
- No voice/VO; no recreated Half-Life assets (inspiration only).
- No new tests (non-balance). Update docs/sound-system.txt in the same work.

## STEP LEDGER

- [x] **CP0** — this file, committed and pushed before anything else changes.
- [x] **CP1** — cap removed; 22 new ids registered; GameAudio.playMenu.
      DONE WHEN: no GAME_SFX_MAX_DISTINCT_SOUNDS; offline render passes; mirror build green.
- [x] **CP2** — player + menu sites (A2-A4). DONE WHEN: wired, build green.
- [x] **CP3** — world, golem, hazard, boss sites (A5-A7). DONE WHEN: wired; test = baseline;
      balanceSim summary byte-identical to the order-6 head.
- [x] **CP4** — docs + reviewer PASS; STATUS IMPLEMENTED.

## NOTES CARRIED FORWARD

- **ENV:** same desktop-only mirror build as order 6 (scratchpad/mirror); offline render harness at
  scratchpad/harness/Render.java. Baseline for this order = f0b9ae2 (order 6 done).
- **CP1 handover:** `GAME_SFX_MAX_DISTINCT_SOUNDS` deleted (it was never read by code). 22 new ids +
  `registerOrderSeven`; 92 sounds total, all <= 1.0 s, peak <= 90 %, last sample within +/-2 LSB
  (harness now checks that, per order 6's amended A8). `GameAudio.playMenu(id)` = playCentred with
  suppression lifted for the call only (still obeys EFFECTS). Nothing fires the new ids yet.
- **CP2 handover:** PlayerController: PLAYER_TURN in startRotation, PLAYER_WAIT in trySkipTurn, the
  stun branch plays PLAYER_STUNNED and calls the new silent `beginSkip()` (event text unchanged),
  PLAYER_GUARD_RAISE in tryGuard, ACTION_DENIED on NO MEDKITS / ALREADY FULL / CLIP FULL. World:
  `playMenuTransitionSound()` (called right after gameAudio.update) + `isMenuPhase` replace order 6's
  explicit inventory blips; UI_CONFIRM on pause row / level-up card / event choice / route ENGAGE,
  UI_FOCUS on route FOCUS_CHANGED, ACTION_DENIED on route INVALID and a failed buy, SHOP_DISPENSE on
  a purchase — all via `playMenu` since those phases are suppressed.
- **CP3 handover:** ImpactEffectSystem: private `playAtTile(id, column, row, size)` + sound on
  block-absorbed / shard shatter+regrow / crust cool+shatter / heavy footfall / ember collapse / lance
  beam / frost shatter / enemy spawned, and NEW overrides `onSpireBorn` / `onSpireShattered`
  (sound only). World's HazardVisualListener is now a lambda: decal + FIRE_IGNITE / TOXIC_SPILL via
  `Level.isHazardFire/isHazardToxic`. BossFloorController: `setGameAudio` (World only; sim null) +
  `playAtBoss`: roar on awaken, roar pitched down (`SoundConstants.GAME_SFX_BOSS_ENRAGE_ROAR_SIZE`)
  on phase 2, wind-up on TELEGRAPH, slam on RESOLVE/CHARGE/MELEE, footfall on DASH/REPOSITION,
  repair on HEAL, ENEMY_SPAWN per summoned minion, BOSS_DEATH on defeat. Mirror core:test = baseline
  (only StoryBarkTest); balanceSim summary byte-identical to baseline.
- **CP4:** reviewer PASS WITH FIXES; both blockers, the non-blocking items and the doc nit fixed (see
  the idea file's IMPLEMENTATION NOTE). STATUS IMPLEMENTED.
