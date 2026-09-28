---
description: Design and build a small feature in one pass via the hub-and-spoke pipeline (no separate approval step)
argument-hint: <what to build>
---

Build this end to end through the `orchestrator` pipeline (`.claude/agents/orchestrator.md`): **$ARGUMENTS**

Use this only for work small enough that a separate design-approval round would cost more than it saves. Anything that introduces a new mechanic, enemy family, weapon, tile symbol or balance rule goes through `/idea` first — say so and stop.

1. Decide the lane (CLAUDE.md "Two Lanes"). Short-lane → just do it: one spoke or the main thread, `./gradlew build`, commit, push, report. No pipeline.
2. Full lane → read the `docs/` files for the subsystems touched, and ask me about any game rule you would otherwise have to guess.
3. Write a short design — subsystems, contracts between spokes, risks — **and the checkpoint breakdown**, each a state that can be checked. Put it in `.claude/checkpoints/<feature-slug>.md` (shape: `_TEMPLATE.md`) and **commit and push that file before changing anything else**; then one commit per checkpoint, pushed immediately, tick in the same commit, handover note at each boundary. If this is already under way, read that file first, reconcile with `git log` / `git status`, and open with the resume report.
4. Run the pipeline with only the spokes needed; tests only per the Testing Policy.
5. Run `./gradlew build` and `./gradlew test` yourself (plus `./gradlew balanceSim` for balance); gate on `reviewer` until PASS.
6. Report: what works now, files by subsystem, real build output, the review verdict, and anything deliberately left out.
