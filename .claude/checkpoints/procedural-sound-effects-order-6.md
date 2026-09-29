# CHECKPOINT — procedural-sound-effects order 6 (missing sounds + HL-style retune)

**PURPOSE:** the live resume record for `.claude/agents/ideas/procedural-sound-effects-order-6.txt`.
This session may be cut off by a usage limit at any moment. Whoever picks the work up next reads
THIS FILE FIRST and continues from the first step whose box is not ticked. Everything ticked is
committed and pushed.

**BRANCH:** `claude/game-sound-effects-t8p30e`
**IDEA:** `.claude/agents/ideas/procedural-sound-effects-order-6.txt` — the spec. Its rules (R…)
and acceptance criteria (A…) are referred to by number below and are NOT repeated here.
**LANE:** full lane / `audio` + `enemy` (one listener moment) + `world` + `util` (GameMath, SoundConstants).
**TESTS:** none — not balance-bearing (CLAUDE.md Testing Policy). `./gradlew test` and
`./gradlew balanceSim` must be identical before/after (R2).
**STARTED:** 2026-09-29
**BASELINE:** branched from `41605e7`.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `procedural-sound-effects-order-6/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD.
4. `git status` — a dirty tree is UNVERIFIED work: finish it to the step's DONE WHEN, or `git restore` it.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `procedural-sound-effects-order-6/CPn:`,
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
- [x] **CP1** — synthesis toolkit.
      DONE WHEN: WaveformKind.METAL, SoundDefinition.roomEcho, synthesiser echo + end fade,
      GameMath metalBarSample/echoTailSeconds/endFadeGain and the new SoundConstants exist;
      `./gradlew build` green.
- [x] **CP2** — content.
      DONE WHEN: six new ids registered, EnemyVoiceMoment.WIND_UP + fallback binding, listed rows
      retuned, offline render shows all sounds <= 1.0 s ending on 0; build green.
- [x] **CP3** — firing sites.
      DONE WHEN: A1-A6 wired; build + test green; balanceSim bands unchanged.
- [ ] **CP4** — docs + gate.
      DONE WHEN: docs/sound-system.txt + CLAUDE.md updated; reviewer PASS; idea STATUS IMPLEMENTED.

## NOTES CARRIED FORWARD

- **ENV:** `./gradlew` cannot run in the repo (403 on dl.google.com for the Android Gradle plugin).
  Gate via a desktop-only MIRROR build in the scratchpad: symlinks to core/lwjgl3/assets/gradle*,
  `settings.gradle` = `include 'lwjgl3','core'`, root `build.gradle` without `buildscript{}` and with
  `configure(subprojects)`. Android module is not compiled (this work touches no android/ code).
- **CP1 handover:** `GameMath.metalBarSample(f0, t, damping)`, `echoTailSeconds(delay, feedback,
  silence)`, `endFadeGain(i, total, fade)`; `WaveformKind.METAL` (uses `frequency(...)`);
  `SoundDefinition.Builder.roomEcho(delay, feedback, dampingHz, wetMix)` — echo params join the cache
  key ONLY when present, so untouched recipes keep their cached WAVs; total duration = dry + tail,
  capped at 1.0 s, and echoed sounds get a 30 ms end fade. Constants: cap 72,
  `GAME_SFX_METAL_PARTIAL_DAMPING`, `GAME_SFX_ECHO_SILENCE_LEVEL`, `GAME_SFX_END_FADE_SECONDS`,
  `GAME_SFX_LOW_HEALTH_FRACTION` (0.25, used in CP3).
- **CP2 handover:** 70 sounds registered (cap 72). Offline render (scratch harness
  `scratchpad/harness/Render.java`, NOT committed): every sound <= 1.0 s, peak <= 92 % after soft
  clip. Last sample is 0 for all but three, which end at +/-2 LSB (~-84 dBFS, inaudible): FIRE_CHAINSAW
  and RAILGUN_CHARGE (pre-existing, untouched) and ENEMY_WIND_UP (long 0.25 s attack; the percussive
  envelope reaches exactly 0 one sample past the buffer). A8 is read as "no audible end step".
  `EnemyVoiceMoment.WIND_UP` exists; the catalog loops FAMILY_MOMENTS (ALERT/ATTACK/DEATH) only and
  binds WIND_UP via `bindEnemyVoiceFallback` -> ENEMY_WIND_UP. Nothing fires the new ids yet (CP3).
- **CP3 handover:** Wired: `EnemyManager.announceWindUp` at the 3 `setWindUp` commits + the first
  self-destruct prime; `World.triggerAutoDocHeal` -> HEAL_STATION; `applyInventoryConsumableEffect`
  -> PLAYER_HEAL; `updateLowHealthWarningSound` (own latch `healthAboveLowWarningSound`, called beside
  the low-health bark) -> LOW_HEALTH_WARNING; `updateLogTerminals` + `updateEventStations` ->
  TERMINAL_ACCESS; `openInventory` (before the phase change) / `closeInventory` (after
  `setSuppressed(false)`) -> UI_MENU_OPEN / _CLOSE. Mirror `core:test`: 449 tests, only the
  pre-existing `StoryBarkTest` bark.depth.core.2 failure. `balanceSim`: passes, and
  `summary.txt` is BYTE-IDENTICAL to a baseline run of 41605e7 (R2 proven).
