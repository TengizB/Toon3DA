# CHECKPOINT — balance-overhaul order 7: feel pass (navigation + playtest rounds)

**PURPOSE:** the live resume record for `.claude/agents/ideas/balance-overhaul-order-7.txt`. This
session may be cut off by a usage limit at any moment. Whoever picks the work up next reads THIS FILE
FIRST and continues from the first step whose box is not ticked. Everything ticked is committed and
pushed.

**BRANCH:** `ccr-8da8b7d3-my4udr`
**IDEA:** `.claude/agents/ideas/balance-overhaul-order-7.txt` — the spec. Its rules (P1-P10) and
acceptance criteria (A1-A7) are referred to by number below and are NOT repeated here.
**LANE:** full lane — `sim` (SimNavigator / TravelPlanner, TacticalPolicy calibration, ROUND SUMMARY)
+ `util` (BalanceConfig SECTION 18, BalanceSchema waiver deletion) + docs + `.claude/balance-feedback/`.
**TESTS:** balance-bearing — extend `sim/BalanceSimTest` / `util/BalanceAuditTest` only. No new test
files. Both gates (`./gradlew test`, `./gradlew balanceSim`) at every checkpoint that moves code.
**STARTED:** 2026-10-14
**BASELINE:** branched from `b8c9aa5` (order 6 merged, PR #302). Baseline gates recorded in NOTES at CP1.

## HOW TO RESUME AFTER A CUT

1. `git log --oneline -20` — the last `balance-overhaul-order-7/CPn` commit says where the work stopped.
2. The first unticked step below is the next thing to do.
3. Read NOTES CARRIED FORWARD — it holds what commit messages cannot.
4. `git status` — a dirty tree means the cut landed mid-step, and that work is UNVERIFIED:
   finish it to the step's DONE WHEN and verify, or `git restore` it and redo the step.
5. If this file and the git history disagree, THE HISTORY WINS.
6. Continue — and open the reply with the four-line resume report.

## COMMIT PROTOCOL

One commit per checkpoint, pushed immediately, prefixed `balance-overhaul-order-7/CPn:`, **with this
file's tick in the same commit as the work**. Never more than one checkpoint uncommitted; split a big
one into `CPna`/`CPnb` rather than holding it. Never `git add -A`.

## CONSTRAINTS THIS WORK IS BOUND BY (do not re-derive them)

- Numbers only: no new mechanics, systems, content or UI (SCOPE OUT). A finding needing one → new idea file.
- No developer tooling (order 9); P10 logs are optional and never block anything (A7).
- Every tuned number lives in `util/BalanceConfig`; every change carries a CHANGELOG line citing the round analysis.
- A band moves only under P5 evidence (>= 3 owner runs consistently contradicting it).
- OVERRIDE CLAUSE: overridden rule named + replaced + tests/docs updated in the SAME checkpoint;
  never delete/@Ignore/weaken a test — update it to the new rule.
- Rounds wait on the owner's PLAY NOTES (AS1); the main thread saves them verbatim.

## STEP LEDGER

- [ ] **CP0** — this file, committed and pushed before anything else changes.
- [ ] **CP1** — SimNavigator navigation fixes; navigation waiver deleted; every S-band enforced; ROUND SUMMARY page.
      DONE WHEN: stall column < 10% of floors for all three policies; A1, A4 hold; both gates green.
- [ ] **CP2** — round-1 brief (focus points + the P4 form verbatim) at `.claude/balance-feedback/playtest-round-1-brief.md`.
      DONE WHEN: the brief exists; handover says the build is with the owner, waiting on notes.
- [ ] **CP3** — round-1 notes saved verbatim; `playtest-round-1-analysis.md` lists every P3 metric
      (sim value, owner answers, sample sizes) and the tuning proposal.
      DONE WHEN: both files exist; the proposal is put to the owner.
- [ ] **CP4** — owner-approved round-1 tuning in BalanceConfig; sim calibrated per P7; CHANGELOG lines.
      DONE WHEN: both gates green.
- [ ] **CP5** — round 2 brief → notes → analysis → tuning (same states as CP2-CP4); if every metric
      is in band and P9 holds, CP6 is skipped.
- [ ] **CP6** — round 3 complete OR P9 met earlier (else a follow-up idea request listing the gaps).
      DONE WHEN: A2, A3, A5, A7 hold; both gates green; reviewer PASS → STATUS IMPLEMENTED.

## NOTES CARRIED FORWARD

(none yet)
