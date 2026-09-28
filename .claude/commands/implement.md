---
description: Implement an approved idea file from .claude/agents/ideas/ via the hub-and-spoke pipeline
argument-hint: <idea file name, e.g. grenade-launcher or narrative-rework-order-4>
---

Implement the idea file identified by **$ARGUMENTS** (in `.claude/agents/ideas/`), running the `orchestrator` pipeline (`.claude/agents/orchestrator.md`). If the orchestrator is invoked as a subagent and cannot dispatch spokes itself, the main thread drives the pipeline directly, following that same file.

1. Read the idea file in full. If its OPEN QUESTIONS section is non-empty, **stop** and route it back to `creative-game-designer` — do not fill the gap.
2. **Check whether this work is already under way, before editing anything.** Compute `.claude/checkpoints/<idea-file-basename>.md` and read it if it exists.
   - **It exists** → reconcile it against `git log --oneline -20` and `git status`, continue from the first unticked step, and **open the report with the four-line resume report**: the interruption was expected and the work was arranged for it; the last checkpoint reached with its sha; what was lost, honestly; the step being resumed. Where the file and the history disagree, the history wins.
   - **It does not** → **CP0**: write it from the idea's CHECKPOINTS section (shape: `.claude/checkpoints/_TEMPLATE.md`), every box unticked, then commit and push it *before any other file changes*. An older idea without a CHECKPOINTS section is not sent back — derive the breakdown from its TECHNICAL NOTES.
   Then: **one commit per checkpoint, pushed immediately, with the tick in the same commit as the work**, prefixed `<idea>/CPn:`, never `git add -A`, never more than one checkpoint uncommitted, each closed with a handover note.
3. Set `STATUS: IN PROGRESS`.
4. Treat the file as the spec: ACCEPTANCE CRITERIA define done, SCOPE OUT is binding, the IMPLEMENTATION PLAN is the starting point (adjust where reality disagrees, and say so).
5. Dispatch only the spokes the work needs — `java-architect`, `math-expert`, `libgdx-specialist`, `weapon-creator`, `game-level-designer` — each with an exact contract. `weapon-creator-fable` only if I asked for Fable by name.
6. Tests per the CLAUDE.md Testing Policy only: balance-bearing → extend `BalanceAuditTest` / `BalanceSimTest`; everything else → no new tests, and say so.
7. Run `./gradlew build` and `./gradlew test` yourself (plus `./gradlew balanceSim` for any balance change). Quote only failing lines and pass the result to `reviewer`.
8. Gate on `reviewer`; loop findings back to the owning spoke until PASS.
9. Only after PASS: `STATUS: IMPLEMENTED`, `IMPLEMENTED: <date>`, and an IMPLEMENTATION NOTE under the header if the build diverged from the plan.

Report the resume report first if this was resumed, then each acceptance criterion by number (met / not met), real build output, the review verdict, the checkpoint file's last sha, and anything deliberately left out.
