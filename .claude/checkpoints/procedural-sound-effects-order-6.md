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
- [ ] **CP1** — synthesis toolkit.
      DONE WHEN: WaveformKind.METAL, SoundDefinition.roomEcho, synthesiser echo + end fade,
      GameMath metalBarSample/echoTailSeconds/endFadeGain and the new SoundConstants exist;
      `./gradlew build` green.
- [ ] **CP2** — content.
      DONE WHEN: six new ids registered, EnemyVoiceMoment.WIND_UP + fallback binding, listed rows
      retuned, offline render shows all sounds <= 1.0 s ending on 0; build green.
- [ ] **CP3** — firing sites.
      DONE WHEN: A1-A6 wired; build + test green; balanceSim bands unchanged.
- [ ] **CP4** — docs + gate.
      DONE WHEN: docs/sound-system.txt + CLAUDE.md updated; reviewer PASS; idea STATUS IMPLEMENTED.

## NOTES CARRIED FORWARD

- (none yet)
