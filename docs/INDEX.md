# docs/ — Reference Index

Quick lookup: find the right doc before starting any feature or fix.

## Architecture & Math

| File | When to read |
|---|---|
| `dda-raycasting-math.txt` | Touching RayCaster, WallRenderer, or any DDA variable. Contains all math proofs. |
| `wall-renderer-guide.txt` | Modifying WallRenderer pipeline, texture mapping, or shade calculation. |
| `tick-system.txt` | Adding a new turn-based subscriber, modifying game loop, or touching TickEventBus. |

## Level & World

| File | When to read |
|---|---|
| `tile-symbols.txt` | Adding a new tile type, writing a level file, or touching Level.java. **Single source of truth.** |
| `procedural-level-generation.txt` | Modifying LevelGenerator, adding room types, or changing wall distribution. |
| `level-design-context.txt` | Designing a new hand-crafted level or deciding on visual theme. |

## Weapons

| File | When to read |
|---|---|
| `weapon-creation-guide.txt` | Implementing any weapon end-to-end. Read fully before writing a single line. |

## Enemies

| File | When to read |
|---|---|
| `enemy-system.txt` | Adding/modifying enemy types, AI behaviour, attack resolution. |
| `enemy-health-bars.txt` | Touching EnemyRenderer health bar geometry, colors, or HP text. |

## Route Map / Progression

| File | When to read |
|---|---|
| `route-map-system.txt` | Touching the branching route map — adding a node type, a map generator, a special level, a region, an affix, or a route event. **Single source of truth for the route map; update it in the same commit as any route change.** |

## Player Progression

| File | When to read |
|---|---|
| `xp-level-progression.txt` | Touching XP gain, level-up rewards, attribute scaling, or PlayerStats. |

## UI & HUD

| File | When to read |
|---|---|
| `procedural-vitals-hud.txt` | Modifying HudRenderer panels, bars, face box, or any HUD element. |
| `story-ui-system.txt` | Touching anything narrative — a bark, an exchange, the boot card, the codex, a story string, a story sound, narrative persistence, or overlay precedence. **Single source of truth for the story UI MACHINE; update it in the same commit as any story change.** |
| `narrative-authority.txt` | Writing, rewriting or cutting any narrative LINE. The content contract: the three jobs a line must do, plain-before-proper, the joke policy, ORA's voice charter, the comprehension bar, and the add-a-line change protocol. **Read before story-ui-system.txt's recipes; a line that fails this doc does not ship.** |

## Design Context

| File | When to read |
|---|---|
| `doom-rpg-reference.txt` | Any question about movement feel, UI style, or design decisions. |
| `roguelike-design-pillars.txt` | Any question about progression, permadeath, or game loop philosophy. |

## Ideas Backlog

All feature design documents live in `.claude/agents/ideas/` (64 files).

**Before implementing any gameplay feature**, check if a design doc exists there.
If none exists, invoke `creative-game-designer` first.

Numbered `roguelike_order_N_*.txt` files define the implementation roadmap (1–19).
The `branching-facility-route-map-order-N.txt` series (parts 1–11) specifies the
branching route-map subsystem; its shipped behaviour is documented in
`route-map-system.txt`, which every route change must keep current.
Check their `STATUS:` header line to know if a feature is IMPLEMENTED, IN PROGRESS, or NOT IMPLEMENTED.
