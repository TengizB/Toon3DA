---
description: Refine a request into an implementation-ready idea file in .claude/agents/ideas/
argument-hint: <describe the feature, mechanic, enemy, fix or work you want>
---

Delegate to the `creative-game-designer` agent. Raw request:

**$ARGUMENTS**

The designer must:

1. Read what it needs first: the related existing idea files in `.claude/agents/ideas/` (do not design something already IMPLEMENTED; do not silently contradict one still NOT IMPLEMENTED), the `docs/` file for each subsystem touched, and `.claude/CLAUDE.md`'s design constraints and Testing Policy.
2. Restate the request in one sentence, separating what I said from what I actually need.
3. Work the gap checklist: turn economy, touch controls, cardinal geometry, rules and numbers, teaching, story, feedback, persistence, generation, edge cases, effect on existing features, out of scope.
4. **Decide by default.** Ask me only what has no obvious answer, is expensive to reverse, AND only I can answer, in one batch, each with a proposed default so "defaults are fine" is a valid reply. Everything else goes under ASSUMPTIONS. If the request is small and unambiguous, ask nothing. If it is short-lane work, say so and write nothing.
5. Route any feasibility question through the `orchestrator` to the right spoke. Never ask a spoke to design the feature.
6. Write `.claude/agents/ideas/<kebab-name>.txt` following `.claude/agents/ideas/_TEMPLATE.txt` exactly: the four header lines (STATUS: NOT IMPLEMENTED, CATEGORY, TITLE, CREATED, IMPLEMENTED: -), checkable acceptance criteria, an explicit SCOPE OUT list, an implementation plan naming real classes, packages, spokes and registries, the TESTS line per the Testing Policy, and every assumption stated. Split into `<part>-order-<n>.txt` files if it will not fit one session.
7. **Cut the plan into a CHECKPOINTS section** — each a **state that can be checked** ("`EnemyType.LURKER` exists and `./gradlew build` is green"), never a task ("add the lurker"). One coherent artifact each, each leaving the build green; 4–10 is normal, twenty means split. See `.claude/checkpoints/README.md`.
8. **Write no code and touch nothing outside `.claude/agents/ideas/`.** Do not create the checkpoint file — that is the implementer's.

The bar: could the orchestrator build this without asking the designer anything — and could a fresh session with no memory of this conversation pick it up mid-build and tell what is done?

Then report the file path, what was designed, the assumptions I should sanity-check, and any open question with its default.
