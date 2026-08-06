# toon3D — Doom-like 3D Game (LibGDX)

First-person pseudo-3D dungeon-crawler roguelike. World is a flat 2D tile grid; raycasting fires one ray per screen column, draws a vertical stripe whose height = `screenHeight / perpWallDistance`. No 3D polygons anywhere.

**Current state:** 2D tile levels (.txt), first-person wall rendering with texture mapping + distance shading, camera-plane DDA, mini-map overlay, tile-based player movement (Doom RPG style).

**Inspiration & design context:** See `docs/doom-rpg-reference.txt` and `docs/roguelike-design-pillars.txt`.

## ⚠️ PLATFORM: MOBILE ONLY — Android Smartphones

This game runs on **Android phones only**. The `lwjgl3/` desktop launcher exists for development testing only.

- **NO keyboard input ever** — never use `Gdx.input.isKeyPressed()`, `Gdx.input.isKeyJustPressed()`, or `Input.Keys.*`
- **NO key binding constants** — if you ever see a `*_KEY` constant referencing keyboard keys, it is dead/legacy code; remove it
- **Touch controls only** — all player input flows through `TouchInputState` → `TouchControllerRenderer`
- **Touch-friendly targets** — on-screen buttons must be large enough to tap with a thumb
- **Never suggest** keyboard shortcuts, hotkeys, F-keys, or any desktop-only feature

## Tile-Based Player Movement

Player never moves freely. Every action = one tile step or one 90° rotation. Animation plays between states; all input is blocked while animating (action lock). Same model as Doom RPG.

**Platform: Mobile only (smartphones).** No keyboard. All input is touch-based via on-screen buttons.

### Controls

| Touch Button | Action |
|---|---|
| Forward | Step forward (current facing direction) |
| Back | Step backward |
| Rotate Left | Rotate 90° CCW |
| Rotate Right | Rotate 90° CW |
| Strafe Left | Strafe left (no facing change) |
| Strafe Right | Strafe right (no facing change) |
| Fire | Fire equipped weapon |
| Reload | Reload equipped weapon |
| Heal | Use a medkit from inventory |
| Skip Turn | Advance world one turn without moving |
| Open Inventory | Open inventory overlay |
| Switch Weapon | Cycle to next weapon |
| Pause | Open the in-suit menu (resume / archive / settings / abandon run) — top-right corner |

### Action Lifecycle (PlayerController)

1. **IDLE** — `pollInput()` reads `TouchInputState` (held action or consumed tap). Calls `tryMove()` or `startRotation()` → transitions to `MOVING` or `ROTATING`.
2. **MOVING / ROTATING** — each `update(deltaTime)` advances `actionProgress` from 0→1 at `1 / PLAYER_MOVE_DURATION` (or `_ROTATE_DURATION`) per second. Position or angle lerped between source and target. No input read during this phase.
3. **Completion** — `actionProgress` ≥ 1 → snap to exact target (eliminates float drift) → return to IDLE. If button still held, next action starts immediately.

### Movement

`tryMove(moveDirectionX, moveDirectionY)`:
```
targetX = positionX + moveDirectionX × CELL_SIZE
targetY = positionY + moveDirectionY × CELL_SIZE

targetTileColumn = floor(targetX / CELL_SIZE)
targetTileRow    = floor(targetY / CELL_SIZE)
if level.isWall(targetTileColumn, targetTileRow) → block, do nothing
```
Player always centred in tile: `tileColumn × CELL_SIZE + CELL_SIZE / 2`.

**Strafe directions** (rotate facing 90°):
| Strafe | Direction vector |
|---|---|
| Left | `(−directionY, directionX)` — 90° CCW of facing |
| Right | `(directionY, −directionX)` — 90° CW of facing |

### Rotation

```
sourceAngle = atan2(directionY, directionX)
targetAngle = sourceAngle + angleOffsetRadians   // +π/2 for rotate-left, −π/2 for rotate-right

currentAngle = lerp(sourceAngle, targetAngle, actionProgress)
directionX   = cos(currentAngle)
directionY   = sin(currentAngle)
```
On completion: snap each component to `Math.round()` → always exactly `(1,0)`, `(0,1)`, `(−1,0)`, `(0,−1)`. No float drift across rotations.

### Configurable Constants

| Constant | Default | Effect |
|---|---|---|
| `PLAYER_MOVE_DURATION` | `0.12f` s | Slide animation time; lower = snappier |
| `PLAYER_ROTATE_DURATION` | `0.12f` s | Rotate animation time |

## Design Constraints (mandatory for all agents)

- Player always occupies exactly one tile; faces/moves in four cardinal directions only.
- Every player action (move, rotate, attack, use item, interact) advances the world by exactly one turn.
- Enemies act once per player turn — no real-time movement needed.
- Level format: tile grids; full symbol list in `docs/tile-symbols.txt`. Roguelike generator must use only symbols defined there.
- 3D view is purely cosmetic — all game logic (pathfinding, collision, LOS) operates in 2D tile space.
- No free-aiming; player always attacks in current facing direction.
- **Ranged enemies can only attack the player when on the same cardinal line** — the enemy's tile column must equal the player's tile column (same vertical line) OR the enemy's tile row must equal the player's tile row (same horizontal line). Diagonal attacks are never allowed. Enforced by `isSameCardinalLine()` in `EnemyManager.java`. Any new ranged enemy archetype **must** respect this constraint.

## Project Structure

```
toon3D/
├── core/src/main/java/ge/tbegvadze/toon3d/
│   ├── Main.java                        # ApplicationListener — entry point (root package only)
│   ├── util/
│   │   ├── Constants.java               # Core: world size, CELL_SIZE, player, minimap, DDA
│   │   ├── WeaponConstants.java         # All weapon stats, timing, HUD, fire effects
│   │   ├── EnemyConstants.java          # Enemy stats, textures, health bar geometry
│   │   ├── HudConstants.java            # HUD panels, bars, face box, death overlay
│   │   ├── RenderConstants.java         # Wall/floor/ceiling textures, props, red-alert
│   │   ├── LevelGenConstants.java       # Procedural generation params, room types
│   │   ├── ItemConstants.java           # Items, ammo, inventory, medical/armour pickups
│   │   ├── EffectConstants.java         # Screen shake, vignette, ambient light, transitions
│   │   ├── ProgressionConstants.java    # XP, death screen, stats persistence keys
│   │   ├── TouchConstants.java          # On-screen touch button layout
│   │   ├── GameMath.java                # ALL math formulas as static methods
│   │   ├── GameBalance.java             # Tunable difficulty/balance numbers
│   │   └── StatsStore.java              # Persistent stats read/write (Preferences)
│   ├── level/
│   │   ├── Level.java                   # Tile grid — isWall(), isPropSolid(), etc.
│   │   ├── LevelLoader.java             # Parses .txt asset into Level
│   │   ├── LevelGenerator.java          # Procedural dungeon generation
│   │   └── LevelGenConfig.java          # Parameters for LevelGenerator
│   ├── entity/
│   │   ├── Player.java                  # positionX/Y, directionX/Y, FOV
│   │   ├── Weapon.java                  # Abstract base: marchShot(), marchBlast()
│   │   ├── Shotgun.java / DoubleBarrelShotgun.java / PlasmaRifle.java
│   │   ├── Chaingun.java / Railgun.java / Incinerator.java / GrenadeLauncher.java
│   │   ├── PlayerInventory.java         # Bridges Player ↔ Inventory + Loadout
│   │   └── Loadout.java                 # Active weapon slots
│   ├── render/
│   │   ├── WallRenderer.java            # DDA 3D wall projection (1280×720)
│   │   ├── FloorCeilingRenderer.java    # Textured floor & ceiling backdrop
│   │   ├── PropRenderer.java            # Billboard prop sprites
│   │   ├── EnemyRenderer.java           # Enemy billboard sprites + health bars
│   │   ├── WeaponHudRenderer.java       # Procedural weapon sprite (FrameBuffer)
│   │   ├── HudRenderer.java             # Left/right HUD chrome panels
│   │   ├── LevelRenderer.java           # 2D mini-map overlay
│   │   ├── ImpactEffectSystem.java      # Screen shake, kill flash, particles
│   │   ├── ImpactEffectRenderer.java    # Draws impact effect sprites
│   │   ├── InventoryOverlayRenderer.java
│   │   ├── LevelUpOverlayRenderer.java
│   │   ├── FramingScreenRenderer.java   # Title/launch screen, pause + settings menus (order-8)
│   │   ├── InstanceReportRenderer.java  # The run report, folded into the reprint card's screen
│   │   ├── FadeOverlayRenderer.java
│   │   ├── HitVignetteRenderer.java
│   │   ├── EventTextRenderer.java / EventTextSystem.java
│   │   └── RayCaster.java / RayCastResult.java / Renderable.java
│   ├── input/
│   │   ├── PlayerController.java        # IDLE→MOVING/ROTATING state machine
│   │   └── touch/
│   │       ├── TouchInputState.java     # Current held/tapped action
│   │       ├── TouchControllerRenderer.java  # Renders on-screen buttons
│   │       ├── TouchButton.java
│   │       └── TouchAction.java
│   ├── item/
│   │   ├── ItemType.java                # Enum of all item types with metadata
│   │   ├── Inventory.java               # Item slots, stack caps, add/remove
│   │   ├── AmmoType.java                # Ammo categories (bullets, shells, cells, rockets)
│   │   └── ItemStack.java / GroundItem.java / ItemCategory.java
│   ├── enemy/
│   │   ├── EnemyManager.java            # Spawn, AI turns, attacks, death
│   │   ├── Enemy.java                   # Enemy instance state
│   │   ├── EnemyFamily.java            # Bestiary taxonomy (undead/insect/machine/demon/golem)
│   │   └── EnemyType.java / EnemyState.java
│   ├── door/
│   │   ├── DoorManager.java             # Open/close animation, keycard checks
│   │   └── Door.java / DoorState.java
│   ├── hazard/
│   │   └── ExplosiveBarrelManager.java
│   ├── progression/
│   │   ├── PlayerStats.java             # Attributes, XP, current level
│   │   ├── PlayerProgress.java          # Run-level state tracking
│   │   └── Attribute.java / LevelUpReward.java / KillEventListener.java
│   └── world/
│       ├── World.java                   # Top-level sim: owns all managers + renderers
│       ├── TickEventBus.java            # Turn event dispatch
│       ├── GameState.java / HudState.java / RunStats.java
│       └── TickCause.java / TickContext.java / TickSubscriber.java
├── android/                             # Android launcher
├── lwjgl3/                              # Desktop launcher (development testing only)
├── assets/
│   ├── levels/                          # Hand-crafted .txt level files
│   └── textures/                        # Wall, enemy, weapon sprite textures
└── docs/                                # Reference docs — see "Docs Directory" section below
```

## Package Rules

Root package (`ge.tbegvadze.toon3d`) is reserved for `Main.java` only.

| Package | Purpose |
|---|---|
| `…toon3d.util` | Stateless utilities: `Constants*`, `GameMath`, `GameBalance`, `StatsStore` |
| `…toon3d.level` | Level data, loading, and procedural generation |
| `…toon3d.entity` | Player, weapons, loadout |
| `…toon3d.screen` | LibGDX `Screen` implementations |
| `…toon3d.render` | All renderers, FrameBuffer pipelines, batch helpers |
| `…toon3d.world` | Top-level simulation: `World`, tick bus, game state |
| `…toon3d.input` | Input processors; touch sub-package for on-screen controls |
| `…toon3d.door` | Door state and animation |
| `…toon3d.item` | Item types, inventory, ammo definitions |
| `…toon3d.enemy` | Enemy manager, types, AI state |
| `…toon3d.hazard` | Explosive barrels and environmental hazards |
| `…toon3d.progression` | Player stats, XP, attributes, level-up rewards |
| `…toon3d.route` | Branching route-map subsystem: node/generator registries, run-seeded map data model, plus the PRICED map — `NodeEconomics`/`NodeEconomicsRegistry`/`RouteEconomics` (the node EV ledger) and `RouteEconomicsModel` (pricing, path policies, route guarantees). Headless, no LibGDX imports |
| `…toon3d.render.routeicons` | Procedural route-map node icons + region crests: IconPainter/RegionCrestPainter registries, one painter per node type/region, shared RouteGlyphs + RouteIconSupport (ShapeRenderer primitives only, no assets) |
| `…toon3d.tileset` | Symbol/sprite-reuse tileset subsystem (headless, no LibGDX imports): `TileCategory`, `EnvironmentSpriteDefinition` + `EnvironmentSpriteRegistry`, `TilesetRegistries.bootstrap()` — the data-driven art catalog of every wall/column/prop/decal sprite; `SymbolBudget` (FIXED/FLEXIBLE split) + `SymbolCategories`; `LevelPalette` + `LevelPalettes.legacy()` (per-level symbol→category+sprite binding, carried by `Level`); `SymbolAllocator` (deterministic per-level palette engine); `RoomBlueprint` + `RoomBlueprintRegistry` room demands. See `docs/environment-tileset-system.txt`. |
| `…toon3d.sim` | Headless BALANCE SIMULATOR (new-game-balancr order 9): `SimWorld` plays whole runs turn-by-turn through the REAL systems with a scripted `PlayerPolicy` (NAIVE / TACTICAL / HOARDER-START-WEAPON) instead of touch input; `BalanceSimulator` runs the seed matrix, `BehavioralBands` evaluates the six S-* bands, `SimReport` writes the summary. No LibGDX render state; drives the real `PlayerController` through the `ActionSource` seam. See `docs/game-balance-authority.txt` SECTION 7. |
| `…toon3d.render.tilesetgfx` | Render-layer half of the tileset subsystem (MAY import LibGDX): `TextureGeneratorRegistry` (sprite id → procedural texture generator, `TilesetGfxBootstrap`) + `EnvironmentTextureSet` (per-level realized textures for only the palette's sprites; `Disposable`). Turns headless sprite ids into pixels. See `docs/environment-tileset-system.txt`. |
| `…toon3d.narrative` | Story UI headless narrative model (NO LibGDX imports — testable, data-driven). **order-1 foundation:** the four `Speaker`s (each an identity: name string id + `SpeakerIcon` + `TypeStyle`), `StoryLine` (speaker + localisation string id only), `StoryStrings` (the externalised I18N-style string table; `defaults()` + `fromProperties()`), `StoryText.wrapToMaxChars` (pure line wrap), `StorySampleLines`. **order-2 BARK LAYER** (the always-on, non-blocking one-liner channel): `StoryRegion` (the five story regions — the gating axis), `StoryProgress` + `StoryProgressStore` (PERSISTENT deepest-region-reached + one-shot "seen" flags; `InMemoryStoryProgressStore` for tests, `util/StoryStore` for the game), `BarkTrigger`/`BarkPriority`/`BarkDefinition`/`BarkRegistry`/`BarkCatalog` (the moment→line catalog — a new line is ONE `register()` call, never a switch), `BarkStrings`, and `BarkSystem` (selection, region gating, one-shot flags, queue, rate limit, fade/hold clock). **order-3 REPRINT / BOOT CARD** (the modal shown at the start of every run): `BootCardVariant` (the default reprint + the four endgame subversions), `BootCardDefinition`/`BootWakeLine`/`BootCardRegistry`/`BootCardCatalog` (what each card prints + ORA's region-gated greetings — a new line or ending card is ONE `register()` call), `BootCardStrings`, and `BootCardSystem` (variant, line selection, the persistent reprint counter, reveal clock, continue policy). **order-4 EXCHANGES** (the blocking, interactive channel — 2–3 tappable answers): `ExchangeTrigger`/`ExchangeOptionKind` (STANCE / PROBE / CONSEQUENTIAL), `ExchangeOption`/`ExchangeDefinition`/`ExchangeRegistry`/`ExchangeCatalog` (the conversation catalog — a new exchange is ONE `register()` call, never a switch), `ExchangeStrings`, `ExchangeSystem` (selection, region + stance preference, the prompt→reply→chain flow, effects, fade clock), and `Stance` (the three hidden leanings, persisted in `StoryProgress` alongside consequential outcomes and codex unlocks). **order-5 MOMENT CATALOG** (the SCHEDULE — which beat fires at which gameplay moment): `ControlHint` (the six controls ORA teaches, one line each, the first time each is needed — the game's entire tutorial), the `BarkTrigger` moments `RUN_START` / `CONTROL_HINT` / `LOG_FOUND`, the `ExchangeTrigger` moments `LOG_FOUND` / `ORGANIZATION_ORDER` / `QUIET_MOMENT`, and `ExchangeOption.endingVariant` + `ExchangeSystem.consumePendingEndingVariant()` — the seam by which the Core's ending choice names a `BootCardVariant` for `World.presentEndingCard`. **order-6 WRAP-UP** (the archive plus the whole-system rules): `CodexCategory` (the six shelves), `CodexEntry`/`CodexRegistry`/`CodexCatalog`/`CodexStrings` (the ARCHIVE — a new entry is ONE `register()` call), `CodexSystem` (unlocks, tabs, entry detail, scroll, NEW dots, category completion), `StorySettings` + `StoryTextSize` (the three persisted accessibility knobs), and `StoryTelemetry` (in-memory tuning counters — no PII, no I/O). **order-7 ENGINE BACKBONE** (the technical layer orders 1-6 assume): the persistence port's `SCHEMA_VERSION` + `loadSchemaVersion()`/`wipeNarrativeState()`, `StoryProgress.wipeAllNarrativeState()`/`reloadFromStore()` (the "new game" seam — the ONLY thing that moves the story backwards), `StorySettings`' fourth knob (story audio) and `reloadFromStore()`, and the one-shot rule that a blocking exchange is spent when ANSWERED rather than when opened, so an OS interruption never eats a beat. **order-8 FRAMING SCREENS** (what surrounds play): `TitleScreenSystem` (the launch screen — the app opens on the game telling you that you are already dead, printing the REPRINT CARD's own status-line ids, and the endings subvert it), `TitleMenuItem`/`PauseMenuItem` + `FramingMenu` (the menu model, including the CONFIRMATION every consequence must ask for), `FramingStrings`, `StoryProgress.recordEndingReached/getEndingReached` (the ending, filed through the order-4 outcome mechanism — no schema bump), and `world/StartMode` (the RESPAWN / TITLE handshake that makes death -> reprint ONE presentation). **narrative-rework order-2 THE FIRST TEN MINUTES** (the opening rewritten as one comprehensible sequence): `BootCardVariant.FIRST_PRINT` + `forReprintCount()` (the card a save that has never printed anybody gets — four plain lines, no proper nouns, shared by the launch screen), region-banded status blocks on `BootCardDefinition` (`systemLinesFrom`, which is how "soul reserve" waits for the Harvesting Galleries), RESERVED wake lines on `BootWakeLine` (`onlyOnVariant` / `onlyAtReprintCount` — ORA's first-print greeting and the three cards that explain what a death costs), `IntroBeat` (ORA's whole introduction: three one-shot rows on run 1, two on run 2, four distributed across floor one's own moments), and `TitleMenuItem`'s first-run label (BEGIN DESCENT until the player has been printed). **narrative-rework order-3 THE VOCABULARY LADDER** (plain before proper, as data): `StoryTerm` (one row per proper noun this fiction owns — match tokens, the naming line's string id, the earliest `StoryRegion` its word may appear in, the existing trigger the naming hangs off, and the codex entry it archives) and `StoryTermCatalog` (registers one one-shot `STORY_CRITICAL` naming bark per term in a loop; `requestNextIntro()` asks for AT MOST ONE naming per gameplay moment, which is how "one new idea per panel" becomes a property of the schedule; `firstDisallowedTerm`/`isExempt` are the audit, run against the SHIPPED properties file). Adding a proper noun is ONE enum row plus its `story.term.*` string — never a catalog edit and never a new trigger; a word with no row may not appear on any channel at all. `CodexEntry.Builder.unlockedByTermIntro()` reads the term→entry pairing back, so the moment a player is told what a word means the long version is waiting for them. Localisation rule: narrative data references string ids only — never a hardcoded story string in Java. Render lives in `…toon3d.render` (`StoryPanelRenderer`, `StoryBarkRenderer`, `BootCardRenderer`, `StoryExchangeRenderer`, `CodexOverlayRenderer`, `FramingScreenRenderer`, `InstanceReportRenderer`, `StoryAudio` + `StoryCue`, `StoryStringsLoader`); accent colours + geometry live in `util/StoryUiConstants`. **Read `docs/story-ui-system.txt` before touching any of it, and update that doc in the same commit.** What a line may SAY (as opposed to how it is delivered) is governed by the content contract in **`docs/narrative-authority.txt`** — the doctrine, the joke policy, ORA's voice charter and the add-a-line change protocol. |

New class: pick the most specific matching package. If none fits, add a subpackage and document it here.

## Automated Code Review

A `PostToolUse` hook runs `code-reviewer` automatically after every Write/Edit to a `.java` file. No manual trigger needed.

`code-reviewer` reviews correctness, LibGDX practice, dispose safety and math. It must **not** recommend adding tests outside the balance scope defined below — "this needs a unit test" is not a valid review finding for ordinary feature work.

## Testing Policy — MANDATORY

**This project has a small testing budget. The default is: DO NOT write new tests.**

Test code is code — it has to be read, maintained and migrated on every refactor, and this project cannot afford that surface. Tests are spent only where a silent, easily-made edit can wreck the game without anyone noticing.

### The ONLY thing that gets new tests: GAME BALANCE

Balance numbers are one-character edits that break the whole game quietly — no crash, no compile error, no visual difference, just an unplayable run three floors in. That is the entire justification for the test budget, and it does not extend to anything else.

Write or extend a test **only** when the change touches:
- `util/BalanceConfig.java` — any gameplay-affecting number
- `util/BalanceSchema.java` — any rule, band, subject or waiver
- `util/GameBalance.java` — any tunable difficulty/loot number
- `util/GameMath.java` — a balance-bearing formula (eHP / DPT / TTK / TP)
- `sim/` — the balance simulator, its policies or the behavioural bands

For those, **extend the existing test** (`util/BalanceAuditTest`, `sim/BalanceSimTest`) rather than adding a new test file. A new test file in this area needs a reason you can state in one line.

Gates for a balance change (both, every time — see `docs/game-balance-authority.txt` SECTION 7):
```bash
./gradlew test          # fast gate: the whole balance audit
./gradlew balanceSim    # slow gate: the seed matrix + behavioural bands
```

### Everything else ships with NO new tests

Renderers, HUD, story/narrative content, route map, tileset, level generation, weapons, enemies, items, input, doors, hazards, progression, refactors, bug fixes — **no new test files, no new test methods.** Verify the change by building and running the game:
```bash
./gradlew build
./gradlew lwjgl3:run
```

Adding a bark line, a codex entry, a room blueprint, a sprite, a weapon or an enemy is a `register()` call plus its data. It does not come with a test.

### Rules that hold regardless

- **Never delete, disable, `@Ignore` or weaken an existing test to make a change pass.** The existing suite (story, route, tileset, level, enemy) stays green and stays the gate — it is already paid for.
- **Updating an existing test because behaviour legitimately changed is expected and is not "writing a new test."** Do it in the same commit.
- **Never add a test dependency, framework, harness, fixture directory or CI job.** No mocking libraries, no new Gradle test source sets.
- If you genuinely believe something outside the balance scope needs a test, **say so in one sentence and ask** — do not write it unprompted. The answer is usually no.
- A user asking for a test is always sufficient authorisation. This policy governs your own initiative, not their requests.

## Agent Roster

| Agent | When to use |
|---|---|
| `creative-game-designer` | New mechanics, enemies, items, progression, level concepts, creative direction. **Consult before implementing any new gameplay feature.** |
| `game-level-designer` | Creating or modifying level `.txt` files. **Always use when a new level file is needed.** |
| `weapon-creator` | Implementing any weapon end-to-end: constants, Weapon subclass, marchShot logic, FrameBuffer procedural sprite in WeaponHudRenderer, World wiring. Also use to add/improve a procedural sprite for an existing weapon. See `docs/weapon-creation-guide.txt`. |
| `weapon-creator-fable` | Identical to `weapon-creator`, but runs on the Fable model. **Never invoke on your own judgement** — use it only when the user explicitly asks for Fable (or names this agent) by name. Default to `weapon-creator` for all other weapon work. |
| `math-expert` | Any equation, geometry, physics, interpolation, Bezier, collision math |
| `libgdx-specialist` | Rendering, cameras, SpriteBatch, shaders, AssetManager, Screen lifecycle |
| `java-architect` | Class design, patterns (ECS/State/Observer), Gradle, major refactors |
| `code-reviewer` | After writing or significantly changing any Java class |

### Creative Game Designer — Workflow

Ideas stored as txt files in `.claude/agents/ideas/`. Each file = one complete design document.

1. Ask `creative-game-designer` to generate a feature.
2. Agent creates `.txt` in `.claude/agents/ideas/` with full spec.
3. Developer decides whether to build it.
4. If yes: pass idea file path to implementing agents (java-architect, libgdx-specialist, etc.).

**Idea file mandatory header:**
```
STATUS: NOT IMPLEMENTED   ← change to: IN PROGRESS / IMPLEMENTED / REJECTED: <reason>
CATEGORY: [Combat / Enemies / Items / Progression / Level Design / Visual / UI / Systems / Meta]
TITLE: [short title, max 60 chars]
---
```

**When implementing:** read the idea file, follow TECHNICAL NOTES and GAMEPLAY LOOP as spec, update STATUS to `IMPLEMENTED` when done, update file if design changed.

**Before starting any gameplay feature:** check `.claude/agents/ideas/` for an existing design doc. If none exists, invoke `creative-game-designer` first.

## Coordinate System — INVARIANT

**(0, 0) = bottom-left corner of the world. Y increases upward.**

- World size: `Constants.WORLD_WIDTH × Constants.WORLD_HEIGHT` = **1280 × 720** units.
- Enforced by `FitViewport` + `OrthographicCamera` with `viewport.update(w, h, true)` in `resize()`.
- Never use raw screen pixel coordinates. Always work in world units.
- Never introduce a camera or viewport that moves the origin away from bottom-left.
- When using touch/mouse input: always unproject through the camera.

## Key Classes

### `util/Constants*.java` — Split Constant Files
Never hardcode a value anywhere. Always add to the matching constant file first, then reference it.

| File | What it holds |
|---|---|
| `Constants.java` | Core world/grid/player/minimap/raycasting constants |
| `WeaponConstants.java` | Per-weapon stats, timing, fire effects, HUD position |
| `EnemyConstants.java` | Enemy textures, health/damage/speed, health bar geometry |
| `HudConstants.java` | HUD panel dimensions, bar sizes, face box, death overlay |
| `RenderConstants.java` | Wall texture paths, floor/ceiling, props, red-alert pulse |
| `LevelGenConstants.java` | Procedural generation parameters, room type definitions |
| `ItemConstants.java` | Medical/armour pickups, ammo caps, inventory UI |
| `EffectConstants.java` | Screen shake, vignette, ambient lighting, fade durations |
| `ProgressionConstants.java` | XP, death beat, stats preferences key |
| `TouchConstants.java` | On-screen button sizes, positions, alpha values |
| `TilesetConstants.java` | Symbol/sprite-reuse partition: FIXED vs FLEXIBLE symbol tables per category, fixed-sprite exceptions, palette-seed salt |
| `StoryUiConstants.java` | Story UI visual language (order-1): panel rects/anchors (bark, exchange, boot card, codex), `STORY_TEXT_SIZE`/`STORY_LINE_MAX_CHARS`, panel alpha, fade timing, per-speaker accent colours + chip geometry, choice-button size, HUD/touch safe zones, speaker-sting audio params, string-asset path. **Bark layer (order-2):** close-X geometry + swipe distance, min-interval, queue capacity + staleness, no-repeat memory, kill/floor-arrival chances, low-health fraction, idle seconds, backtrack steps, deep-strata interval, per-`BarkTrigger` cooldown table, lore/levity weights, seed salts. **Boot card (order-3):** full-bleed backdrop, the top-down card stack (status panel → counter → ORA panel → CONTINUE), counter prefix + digit padding, reveal/fade timing, `STORY_TEXT_HOLD_SCALE_REDUCED`. **Exchanges (order-4):** the prompt panel + top-anchored answer-plate stack (`STORY_EXCHANGE_PROMPT_TOP_Y` re-spaces the whole modal), min/max option count, plate height + press-state alphas, answer text size + `STORY_EXCHANGE_OPTION_MAX_CHARS`, world-dim colour/alpha, fade timing, selection seed salt. **Moment schedule (order-5):** `STORY_LOG_TERMINAL_SYMBOL` + `STORY_LOG_TAKES_PER_FLOOR`, `STORY_QUIET_MOMENT_MIN_STEPS`, `STORY_ORGANIZATION_ORDER_FLOOR_DELAY`, and the `RUN_START`/`CONTROL_HINT`/`LOG_FOUND` cooldown rows. **Codex + wrap-up (order-6):** the whole `STORY_CODEX_*` frame (panel, header, tab column, body band, footer settings strip, row/detail metrics, scroll drag scale + tap slop, plate styling, chrome string ids), `STORY_TEXT_SIZE_MULTIPLIER` + the three `STORY_SETTING_*` keys, `STORY_COMBAT_SPIKE_AWAKE_ENEMIES`, `STORY_TELEMETRY_FAST_DISMISS_SECONDS`, and the `CODEX_COMPLETE` cooldown row. **Engine backbone (order-7):** the `STORY_CUE_*` interface-cue table (panel open / choice picked / reveal tick) beside the speaker stings, `STORY_SETTING_STORY_AUDIO` + `STORY_CODEX_SETTING_AUDIO_ID`, and the fourth slot in `STORY_CODEX_SETTING_COUNT`. **Framing screens (order-8):** the shared `STORY_FRAME_*` backdrop/scrim + menu-plate geometry, the `STORY_TITLE_*` launch stack (status lines, reveal pacing, the title and its menu anchor), the confirmation panel + PROCEED/CANCEL plates, `STORY_FRAME_RETURN_*` and `STORY_ENDING_MERGE_HOLD_SECONDS` (the two ways an ending resolves), the `STORY_REPORT_*` reprint-report page (which replaced the deleted `HudConstants.DEATH_OVERLAY_*` block), and `STORY_ENDING_OUTCOME_ID`. **The first ten minutes (narrative-rework order-2):** `STORY_BOOT_SYSTEM_MAX_LINES` = 4 (the first-print block), the four-line `STORY_TITLE_STATUS_*` launch notice, a queue capacity sized for floor one's mandatory burst (a `STORY_CRITICAL` beat that finds the queue full of other criticals is DROPPED, so this is a floor, not a pacing knob), and the `STORY_DEATH_STROKE_*` block (hold, fade, anchor, colour, `STORY_DEATH_STROKE_ID`) |

### `util/GameMath.java`
Every non-trivial formula as a `public static` method. **Never implement a formula inline in game code.** Required comment block above every method:
```java
/*
 * Formula: <name>
 * Derivation: <step-by-step math>
 * Edge cases: <division by zero, degenerate input, precision issues>
 */
```
Methods must be pure functions — no side effects, no LibGDX render state.

### `util/GameBalance.java`
Tunable difficulty numbers (enemy damage multipliers, loot rates, etc.) that game designers may want to tweak without touching mechanics code.

### `level/LevelGenerator.java`
Procedural dungeon generator. Takes `LevelGenConfig` and produces a `Level`. All generation params live in `LevelGenConstants.java`.

### `level/Level.java`
2D tile grid. **Full tile symbol reference: `docs/tile-symbols.txt`** — single source of truth for every character used in level files.
> A symbol's CATEGORY is symbol-intrinsic (from `tileset/SymbolBudget` + `SymbolCategories`, backing `isWall()`/`isPropSolid()`/…), but its SPRITE is per-level via the `LevelPalette` the `Level` carries. Hand levels use the LEGACY palette (the historic 1:1 mapping); generated levels vary the sprite per seed. See `docs/environment-tileset-system.txt`.

Quick summary of categories:
- **Walls (18):** `x c v t w h j G k N Q S M Z U X D F`
- **Doors (4):** `d R Y B`
- **Floor lighting (4):** `(space) l u f`
- **Special (3):** `p` (start), `P` (column), `>` (exit)
- **Solid props (13):** `g E T L C # % & = @ I J W`
- **Walkable decals (5):** `m s . O e`
- **Pickups (7):** `r y b + H a A`
- **Enemy spawns (15):** `1 2 3 4 5 ! $ ^ ~ z K V * ( {` (replaced with floor at load time)

`Level.isWall(char)` is the single authority on which chars are solid — both `WallRenderer` and `PlayerController` call it. When adding a new wall type, add it there first.

Tile grid indexed by `(x, y)` where `(0, 0)` = bottom-left tile (Y-up). Package-private constructor — always instantiate via `LevelLoader`.

#### Adding environment art or a room — use a RECIPE (no new symbol)

The symbol/sprite-reuse system decoupled symbols from art, so the common cases need **no new symbol**:
- **New wall/column/prop/decal ART → RECIPE A:** register one `EnvironmentSpriteDefinition` (+ its texture generator). It appears on generated levels automatically.
- **New ROOM → RECIPE B:** register one `RoomBlueprint`. It competes in generation automatically.

Both recipes are ONE registration line each — **zero edits to `LevelGenerator`, the renderers' draw logic, or `Level`** (the same "never a switch statement" discipline as `route/RouteRegistries`). Full copy-pasteable checklists: `docs/environment-tileset-system.txt` §9.

#### STRICT RULE — Adding a new FIXED tile symbol (rare)

Only when a genuinely new FIXED gameplay meaning is needed (not for art/rooms — those are RECIPE A/B). **Every new symbol must be introduced in a single commit that includes ALL of:**
1. An entry in `docs/tile-symbols.txt` (correct section, description, notes).
2. Its CATEGORY wired into `Level.java` (`isWall()`, `isPropSolid()`, … — flexible categories are data-driven via `tileset/TilesetConstants` + `SymbolBudget`; a FIXED gameplay symbol adds its own check).
3. Its FIXED texture/sprite handling in `WallRenderer.java` or `PropRenderer.java`.
4. Any new `Constants` entries (texture path, height multiplier, etc.).
5. The symbol count updated in the SYMBOL BUDGET section of `docs/tile-symbols.txt`.

**Never use a symbol in a level file or in game code that is not already in `docs/tile-symbols.txt`.**

### Tileset subsystem key classes (see `docs/environment-tileset-system.txt`)
- **`tileset/EnvironmentSpriteRegistry`** — the art catalog: register/get environment sprites, `allInCategory()` is the allocator's variety pool. Populated by `TilesetRegistries.bootstrap()`. Adding art = one `register()` line (RECIPE A).
- **`tileset/SymbolAllocator`** — pure, deterministic engine that builds a per-level `LevelPalette` from a floor seed + the rooms actually placed (fixed bindings, room reservations, freed-symbol reclamation, rule-governed variety). Same request ⇒ byte-identical palette.
- **`tileset/LevelPalette`** — the per-level `symbol → (category, sprite id)` binding carried by `Level`. `LevelPalettes.legacy()` reproduces the historic 1:1 mapping (hand levels + the default).
- **`level/RoomBlueprintRegistry`** — registered rooms the generator selects from (`RoomBlueprints.bootstrap()`). Adding a room = one `RoomBlueprint` register line (RECIPE B); no generator-selection edits.
- **`render/tilesetgfx/EnvironmentTextureSet`** — per-level `Disposable` that realizes textures for ONLY the palette's sprites and frees them on level teardown. Owned by `World`'s dispose chain.

### `entity/Player.java`
Fields: `positionX`, `positionY` (world units), `directionX`, `directionY` (unit vector, always length 1), `fieldOfViewRadians`.
**Never mutate `positionX/Y` or `directionX/Y` directly outside `PlayerController`.**

### `input/PlayerController.java`
State machine: `IDLE` → `MOVING` or `ROTATING` → `IDLE`. `actionProgress` (0→1) drives lerp. Only one action at a time.

### `render/WallRenderer.java`
Doom-style 3D view, full 1280×720 screen. Call `setPlayerState(worldX, worldY, dirX, dirY, fovRadians)` before each `render()`. Uses single `SpriteBatch`. Z-buffer: `getZBufferAt(int screenColumn)`. Owns `SpriteBatch`, `wallTexture`, `whitePixelTexture` — all disposed in `dispose()`.
See `docs/wall-renderer-guide.txt` for pipeline details, GameMath methods, and extension guide.

### `render/LevelRenderer.java`
2D mini-map overlay using `ShapeRenderer`. Three passes: filled walls, grid lines, ray fan. Mini-map at `(0,0)–(MINI_MAP_WORLD_SIZE, MINI_MAP_WORLD_SIZE)`. Call `setRayResults(RayCastResult[])` before `render()`.

### `render/RayCaster.java`
Casts `RAY_COUNT` rays across FOV using DDA. All math in tile space. Pre-allocates `RayCastResult[]` — never hold across frames. Signature: `castRays(playerWorldX, playerWorldY, playerDirX, playerDirY, fieldOfViewRadians, level)`.

### `level/LevelLoader.java`
Reads `.txt` asset via `Gdx.files.internal()`. First line = top of world. Shorter lines padded with spaces.

### `render/Renderable.java`
Interface: `void render(OrthographicCamera camera)`. All renderers also implement `Disposable`.

### `render/WeaponHudRenderer.java`
Draws the current weapon sprite procedurally with a FrameBuffer pipeline. Each weapon has its own draw method inside this class. See `docs/weapon-creation-guide.txt` for the full pipeline.

### `render/FloorCeilingRenderer.java`
Draws the textured floor and ceiling backdrop before walls. Must run first in the render order.

### `render/PropRenderer.java`
Billboard prop sprites (barrels, crates, etc.). Uses WallRenderer's Z-buffer to sort props.

### `render/EnemyRenderer.java`
Enemy billboard sprites with procedural health bars drawn on top. See `docs/enemy-health-bars.txt`.

### `world/World.java`
Top-level simulation class. Owns all managers (`EnemyManager`, `DoorManager`, `ExplosiveBarrelManager`, etc.) and all renderers. Orchestrates the tick system.

### `world/TickEventBus.java`
Turn-based event dispatch: every player action fires a tick that subscribers (enemies, reload, etc.) respond to. See `docs/tick-system.txt`.

### `route/RouteMap.java` + `route/RouteMapGenerator.java`
Branching route-map subsystem (headless, no LibGDX). `RouteMap` is the run's forward-only layered DAG + cursor (pure state, owned by `World`); `RouteMapGenerator` builds it deterministically from the run's master seed. Node types, generators, level profiles, affixes, events, and regions are all registered through **`route/RouteRegistries.bootstrap()`** — never a switch statement. **Read `docs/route-map-system.txt` before adding any node type, generator, special level, region, affix, or route event, and update that doc in the same commit.**

### `render/RouteMapOverlayRenderer.java`
The full-screen "FACILITY NAV" console drawn during `RunPhase.ROUTE_SELECT` (procedural ShapeRenderer/BitmapFont, no textures). Node icons + region crests are procedural painters in `render/routeicons/`, registered via `RouteIconBootstrap`. See the OVERLAY VISUAL SPEC / INTERACTION & CONTROLS sections of `docs/route-map-system.txt`.

### `narrative/BarkSystem.java` + `narrative/BarkCatalog.java` (Story UI order-2)
The BARK LAYER: short one-line messages that pop up **during** play without stopping the game. `BarkSystem` is the headless brain (which line, when, one at a time, queue, priority, rate limit, fade clock); `render/StoryBarkRenderer` only draws what it says is active, at the fixed `STORY_BARK_*` anchor. **Adding a line is ONE `register()` call in `BarkCatalog`** — never a switch, and never a hardcoded string (rows carry localisation ids resolved through `StoryStrings`).

**A bark NEVER auto-dismisses.** It stays until the player closes it — tap the X in the panel's top-right corner, or swipe the panel (`World.updateStoryBarkTouch`). Nothing cuts a line short, not even a mandatory beat: a player must never lose a line they were still reading. The rate-limit clock runs only while the screen is FREE, so reading slowly never causes a pile-up; queued non-critical lines go stale and are dropped instead of arriving late.

**Anti-spam rules:** a line said within the last `STORY_BARK_RECENT_MEMORY` barks is ineligible (if that empties a pool, the moment stays silent); every repeatable moment carries 4+ lines per region; kills and floor arrivals are probability-rolled before they even ask; per-trigger cooldowns are minutes, not seconds. **Tone balance:** each row declares a `BarkTone` (`LORE` / `LEVITY`) and lore outweighs levity by selection weight — ORA is dry occasionally, meaningful mostly, and never flippant below the Reliquary. Retune in `StoryUiConstants`, never in selection code.

**ROGUELIKE STORY-GATING (mandatory):** pools are gated by `StoryProgress.getDeepestRegion()` — the deepest `StoryRegion` **ever** reached, persisted via `util/StoryStore`. Death never rewinds the story: ORA's tone, the planet's voice stage and the Organization's escalation only ever move forward, and one-shot beats (region entry, gate orders, first sight of an enemy family) never replay on a later run. Tone shifts are expressed purely by which region band a row is registered in — **never** by an `if (region == …)` in game code. The planet has no Region-1 rows at all; that silence is the design.

Moments are fired from `World` (floor arrival, region entry, kills, low health, deep-strata milestones) and from `world/StoryBarkTickSubscriber` (first sight of an enemy family, backtracking, idle). Barks are suppressed — queued, not dropped — while any hard-pause overlay owns the screen.

### `narrative/BootCardSystem.java` + `narrative/BootCardCatalog.java` (Story UI order-3)
The REPRINT / BOOT CARD: the full-screen modal shown at the start of **every** run — which is every reprint, the very first one included (in-fiction the original self has just died). It pauses the world and is the game's most reliable story channel, so it doubles as a guaranteed beat: SYSTEM status lines, a quiet instance counter, and one region-appropriate ORA wake-up line. `BootCardSystem` is the headless brain; `render/BootCardRenderer` only draws what it reports, reusing the order-1 `StoryPanelRenderer` for both blocks.

**Adding an ORA reprint line — or a whole new ending card — is ONE `register()` call in `BootCardCatalog`**, never a switch and never a hardcoded string. ORA's greeting is gated by the same persistent `StoryProgress.getDeepestRegion()` the barks use (the system shares the bark layer's instance), so **death never rewinds her tone**: cheerful and counting your deaths up top, distracted in the Galleries, cracked in the Reliquary, quiet and protective from the Wound down. A line may carry the `{instance}` token, substituted with the zero-padded reprint number.

**The counter only climbs.** `StoryProgress.recordReprint()` persists through `util/StoryStore`, so it survives death and app restart. It is never celebrated and never annotated — the number growing over a long session is the point.

**The card never auto-advances and never traps.** No timer of any kind: it waits for the player. CONTINUE works from frame zero, even mid-reveal; a tap anywhere else completes the staggered status-line reveal (which the "reduce text hold" accessibility setting shortens).

**The endgame variants subvert it** (`story/06-endings.md`) — FREE/KILL print a depleted reserve and no authorized checkpoint, draw **no CONTINUE**, and refuse to reprint (`isTerminal()`); OBEY prints a promotion and still continues; MERGE presents no card at all. `World.presentEndingCard(variant)` / `isBootCardTerminal()` are the seam order-5 (which ending fires) and order-8 (what surrounds that final screen) call. Only REPRINT is reachable in play today.

### `narrative/ExchangeSystem.java` + `narrative/ExchangeCatalog.java` (Story UI order-4)
The EXCHANGE LAYER: the **blocking** half of the story channel. A handful of moments per region stop the world and put a speaker's prompt on screen with **2–3 big tappable answers**; the player must pick one before control returns. That tap is the point — it turns reading into speaking, which is what makes a player who does not want to read, read. `ExchangeSystem` is the headless brain (which exchange, what an answer does, which reply comes back, the fade clock); `render/StoryExchangeRenderer` only draws what it reports, reusing the order-1 `StoryPanelRenderer` for the prompt. **Adding an exchange is ONE `register()` call in `ExchangeCatalog`** — never a switch, never a hardcoded string.

**Bark vs exchange.** A bark is non-blocking, choice-less and constant (~80% of the story). An exchange stops the world, so it is **rare and deliberate**: every row is one-shot and region-gated, giving a handful per region. If exchanges ever feel frequent, **delete rows** — never add a "skip choices" toggle, because the choice IS the engagement. A test caps the whole catalog.

**No timers, no blank "next".** Nothing in the layer counts down and nothing auto-selects: the game is turn-based with an action lock, so pausing costs the player nothing. The only way past a prompt is an actual answer; the reply that follows has one acknowledge button, which is a receipt for a choice already made. An exchange asked for mid-transition is held PENDING and opens only in play, with the player idle and no bark on screen (`World.openPendingStoryExchange`).

**Three kinds of answer.** `STANCE` (most common, cheap — changes only how the player speaks), `PROBE` (pays curiosity with an answer, a codex unlock, sometimes a small cache), `CONSEQUENTIAL` (rare, recorded permanently under the exchange's `outcomeId`, which order-5/order-8 read back).

**The hidden `Stance` model** — three leanings (PLANET / ORGANIZATION / ORA) persisted in `StoryProgress`. It is **invisible** (no meter, ever) and **never gates**: it only flavours which of several eligible exchanges opens (`stanceAffinity`), and a neutral row is always a valid fallback, so no leaning can silence a moment. Death never wipes it, for the same reason it never rewinds the deepest region.

**The narrative layer never touches the inventory.** A reward is an `ItemType` *name*; `World.applyPendingExchangeReward()` resolves and grants it, which is what keeps `…toon3d.narrative` headless.

### The MOMENT SCHEDULE (Story UI order-5) — which beat fires when
Orders 2–4 built the three channels; order-5 is the **schedule** that decides which gameplay moment each beat hangs off. It adds no new renderer: every entry is a `register()` line in an existing catalog plus one firing site in `World` / `world/StoryBarkTickSubscriber`.

| Moment | Channel | What fires |
|---|---|---|
| Control handed to the player (`RUN_START`) | bark | The **COLD OPEN** — ORA introduces herself. Two one-shot rows, one asked per run start, so a new player meets her on run 1 and hears the second line on run 2. Never again. |
| A control first becomes useful (`CONTROL_HINT`, subject = `ControlHint.name()`) | bark | **THE ENTIRE TUTORIAL.** One line per control, at the moment it matters: MOVE at the cold open, FIRE when something wakes, RELOAD on an empty magazine, HEAL on real damage with a medkit in the bag, SWITCH_WEAPON on a second gun, INVENTORY on something worth stashing. One-shot for the life of the save and `STORY_CRITICAL`, because a dropped hint is a stuck player. **There is no tutorial screen and there must never be one.** |
| Stepping adjacent to a `'T'` computer terminal (`LOG_FOUND`) | bark + exchange | ORA's one-line **take** on what the crew left on it — the facility's paperwork for the ~80% who never open a codex. Same auto-trigger idiom as the heal station and the EVENT console, so reading costs no button. One take per floor. The Galleries' yield report and the Reliquary's cradle note are one-shot `STORY_CRITICAL` **mandatory reveal beats**; the same channel also opens the region's heaviest exchanges (the yield ledger, ORA's trust beat). |
| A quiet moment — 14 fresh tiles walked with nothing awake (`QUIET_MOMENT`) | exchange | The Region-1 **teaching exchange**: the first choice ever offered, deliberately deciding nothing, so a player learns the blocking layer with no enemy in the room. Deliberately NOT the idle timer — someone who put the phone down must never return to a modal they did not open. |
| One floor after a region gate (`ORGANIZATION_ORDER`) | exchange | The Organization's **demand** (Galleries, corrective) and **coercion** (Wound). A floor *after* the gate, never on it: the region-entry exchange already fires there, and back-to-back stops is the pacing failure order-6 Part B names. |
| Deepest region = Core **and** the Core's boss is down | exchange | **THE ENDING** (`ExchangeCatalog.CORE_ENDING_EXCHANGE_ID`, presented by hand). The route map is endless, so "the Core" is many floors — the boss is the forced convergence that makes one of them final (order-7 Part A's edge case). |

**The ending choice.** A **door** with three equally-weighted ways out, each opening a **confirmation** that states its price in one plain line; only a confirmation can commit, and every one carries a way back. All four endings of `story/06-endings.md` are reachable (FREE and KILL share a door because they are the same decision about the harvest, differing only in whether the being survives it) and every answer files under one `outcomeId`. Nothing is ranked, labelled or recommended, and **the hidden `Stance` model neither restricts nor unlocks any of them**.

An option names its ending with `ExchangeOption.endingVariant(BootCardVariant)` — which the builder requires to be `CONSEQUENTIAL`. The narrative layer never ends anything: `World.commitPendingStoryEnding()` reads it back through `ExchangeSystem.consumePendingEndingVariant()` (only once the panel has faded, so the last thing the player reads as themselves is the reply to their own answer) and hands it to `presentEndingCard`. FREE/KILL print a terminal card with no CONTINUE; OBEY's card continues into a fresh print, which is the horror; MERGE presents no card and freezes the world, because the interface stops being the player's. What surrounds those final screens is order-8's.

### `narrative/CodexSystem.java` + `narrative/CodexCatalog.java` (Story UI order-6)
The CODEX: the opt-in **archive** of everything the story channels already said, and the one screen in the game where long text is allowed. `CodexSystem` is the headless brain (what is unlocked, which tab and entry are open, where the body is scrolled, which entries still wear a NEW dot, when a category fills); `render/CodexOverlayRenderer` only draws what it reports. **Adding an entry is ONE `register()` call in `CodexCatalog`** — never a switch, never a hardcoded string.

**It is an archive, not a second script.** Every entry is the LONG version of a beat that already landed on an unskippable channel — the terminal ORA read over your shoulder, the word she used, the thing you asked about. A player who never opens this screen misses no plot point, and `StorySkimmerPathTest` enforces exactly that. Two unlock paths, both permanent through `StoryProgress`: a bark or exchange **delivered** in the world archives the entry it was about (`CodexEntry.unlockedByLine`), and an order-4 PROBE answer names an entry id directly. **Locked entries are never listed** — a tab shows what the player HAS, because a wall of "???" turns an opt-in archive into homework. The `MEMORIES` tab fills off the region-entry beats, so the block on the player's own past lifts exactly as the descent deepens.

**Access is always a MENU, never play.** Today that is the inventory header's CODEX button; `World.openCodex()` remembers the phase it was opened from and `closeCodex()` restores it, which is the same seam order-8's pause menu and title screen will call. Nothing in the screen is on a timer.

**Completion is cosmetic, deliberately.** Filling a category fires one `BarkTrigger.CODEX_COMPLETE` line and marks the tab. It is never gameplay-critical and never required — paying completion in power would turn a thing nobody has to read into a thing everybody has to grind.

### `narrative/TitleScreenSystem` + `render/FramingScreenRenderer` (Story UI order-8)
The FRAMING SCREENS — the screens that BOOK-END play. **There is no cheerful title screen**: the app opens on black and the first line anybody reads is the game telling them they are already dead, printed from the REPRINT CARD's own string ids (`story.boot.system.*`), because the launch screen and the card are the same machine talking. The title resolves under it, then a cold menu — CONTINUE DESCENT (offered only once the player has actually been printed), NEW OPERATOR (the save wipe, behind a confirmation), ARCHIVE, SETTINGS, QUIT. The paced reveal is mood: a tap prints the whole screen at once, and nothing here ever advances itself.

**Death → reprint is TWO beats, then one tap** (restaged by narrative-rework order-2). Fade to black → **the STROKE**: three words on the black and nothing else — no counter, no status block, no button and no ORA (`story.death.stroke` = "THIS BODY DIED", `FramingScreenRenderer.renderDeathStroke`). It reports a DEATH; the card that follows reports a BIRTH, and merging them meant neither landed. Her absence is diegetic and free: she does not get printed, she reloads. The words never change at any depth — the escalation lives in her wake lines on the card. It holds `STORY_DEATH_STROKE_HOLD_SECONDS` and hands over by itself; a tap after the screen is black skips ahead, with a one-second lockout so a player mashing fire cannot blow through their own death. → the reprint card (printed by the world that DIED) → one tap → a fresh run already faded up (`world/StartMode.RESPAWN`). The old full-screen death report is folded into that card's screen as a PAGE (`render/InstanceReportRenderer`, opened by a small REPORT plate), so the run's numbers and the RUN AUTOPSY stay one tap away without a second screen competing for the moment. `DeathOverlayRenderer` and the `HudConstants.DEATH_OVERLAY_*` block are gone.

**The pause menu** (`PauseMenuItem`, opened by the top-right PAUSE touch button) is a hard-pause overlay like every other: barks queue, no exchange opens behind it, the thumb clusters are not drawn. It spoils nothing — the archive is one row down.

**The endings resolve on the framing they subvert.** `World.commitPendingStoryEnding` records the ending permanently (through the order-4 outcome mechanism), so from then on the launch screen prints THAT card's status lines. FREE/KILL get a terminal card with no CONTINUE and a dim RETURN plate that stands the game down; OBEY continues into a fresh print; MERGE shows no card at all and holds the frozen view, fading, for `STORY_ENDING_MERGE_HOLD_SECONDS`. Nothing is ranked, and both descent options stay available afterwards.

### The PACING BUDGET, ANTI-SKIP rule and ACCESSIBILITY settings (Story UI order-6)
The whole-system rules that keep the story UI from annoying anyone:

- **Pacing (Part B).** The rate limits live in `StoryUiConstants`; the rule they could not express is the combat spike. `BarkSystem.setCombatSpike()` **holds** non-critical lines — and freezes their queue ageing, so a long fight cannot silently eat one — until the lull. A `STORY_CRITICAL` beat still lands mid-fight, because a dropped control hint is a stuck player. `World` feeds it `EnemyManager.countAwakeEnemies()`. **If in doubt, cut**: the fix for "too much story" is to delete rows, never to add a skip toggle.
- **Anti-skip (Part C).** We earn attention, never trap it: one tap always advances, and **no channel in the layer advances itself or runs on a timer**. The spine therefore rides the channels a skimmer cannot skip — the boot card, one-shot `STORY_CRITICAL` barks (region entries, gate orders, the two reveal logs, the cold open, every control hint), and the blocking ending exchange. `StorySkimmerPathTest` asserts all of it, so a later order cannot quietly move a spine beat onto an optional channel.
- **Accessibility (Part D).** `narrative/StorySettings` + `StoryTextSize`: text size, boot-card reveal pacing, reduced motion — persisted beside every other narrative flag (`StoryStore` schema v3) and surfaced on the codex's footer strip until order-8 builds a settings screen. `World.applyStoryAccessibilitySettings()` pushes the wrap width into the headless systems and the glyph scale into the renderers from the same setting, so larger text **wraps sooner rather than overflowing**. Nothing critical is ever conveyed by colour alone (the speaker chip carries name + icon too), and there is no "disable timers" toggle because there are no timers to disable.
- **Telemetry (Part E).** `narrative/StoryTelemetry` — in-memory counters only. No identifiers, no I/O, no PII. The number that matters is the fast-dismiss fraction: a line closed inside `STORY_TELEMETRY_FAST_DISMISS_SECONDS` was skipped, not read, and that is how "if in doubt, cut" gets measured.

### The ENGINE BACKBONE (Story UI order-7) — persistence, localisation, audio, lifecycle
The technical layer everything above assumes. **Read `docs/story-ui-system.txt` before touching any of it.**

- **Story-gating (Part A)** — the reconciliation of a LINEAR story with a PERMADEATH roguelike: every pool, tone and one-shot beat is gated by `StoryProgress.getDeepestRegion()`, the deepest `StoryRegion` **ever** reached, persisted. **Death never rewinds the story** — that is the premise (the clone remembers, ORA is never wiped), not a concession to it. Out-of-order branches can only deepen it. The Core's ending is guarded twice (deepest region CORE **and** the boss down), because the route map is endless.
- **Persistence (Part B)** — `narrative/StoryProgressStore` is the port (`SCHEMA_VERSION`, currently 4, plus `loadSchemaVersion()`/`wipeNarrativeState()`); `util/StoryStore` is the Preferences implementation, which migrates on construction and keeps every key under a `story.` prefix in the `StatsStore` file. Writes happen on meaningful change only — never per frame. **"New game" is the ONLY thing that moves the story backwards**: `World.wipeAllPersistentProgress()` clears records, tips and narrative state TOGETHER and re-reads every cache (`StoryProgress.reloadFromStore()`, `StorySettings.reloadFromStore()`, `CodexSystem.refresh()`, `PersistentStats.reset()`). Nothing in a run calls it.
- **Localisation (Part C)** — no story string is hardcoded in Java. `assets/story/story-strings.properties` is the shipped table, read once by `render/StoryStringsLoader` with `StoryStrings.defaults()` as the fallback; a missing id resolves to a visible `!id!` marker, never blank. Ids are stable, lower-case, `story.`-namespaced keys. `StoryLocalizationTest` sweeps every id in every channel against the shipped asset.
- **Audio (Part D)** — `render/StoryAudio` is the ONE world-owned `Disposable` holding all seven procedural tones (four speaker stings + the three `StoryCue`s: PANEL_OPEN, CHOICE_PICKED, REVEAL_TICK) and is injected into the bark, exchange and boot-card renderers; they never build their own. Played from the update path, never from render. **The game must be fully understandable with audio off** — every cue is redundant with something already drawn, and the codex's SOUND knob silences the layer.
- **Precedence & interrupts (Part E)** — a hard-pause overlay always wins (barks queue, ageing freezes, exchanges never open outside `PLAYING`); two story panels never stack; `Main.pause()/resume()` → `World.onApplicationPause()/onApplicationResume()` drops transient touch state (a finger that never released must not answer the prompt it returns into) and re-pushes the text scales after a possible GL-context loss. A blocking exchange is marked seen when **answered**, not when opened, so an OS kill mid-prompt asks the beat again next run instead of spending it.

### `input/touch/TouchInputState.java`
Holds which `TouchAction` is currently held or was just tapped. `PlayerController` polls this each frame.

### `input/touch/TouchControllerRenderer.java`
Renders the on-screen thumb cluster (movement D-pad, action buttons). This is the **only** input source for the player — no keyboard ever.

### `enemy/EnemyManager.java`
Spawns enemies from level spawn points, runs AI turns after each player action, handles attacks, death, and loot drops. See `docs/enemy-system.txt`.

### `item/ItemType.java`
Enum of every item with metadata (category, display name, pickup logic). The canonical list of all pickup item types.

### `progression/PlayerStats.java`
Player level, XP, attribute points. Drives the level-up system. See `docs/xp-level-progression.txt`.

### `util/StatsStore.java`
Reads/writes persistent run statistics via LibGDX `Preferences`. Used for permadeath high-score tracking.

## Where to Find Info

| Topic | File to read |
|---|---|
| Tile symbols, level file format | `docs/tile-symbols.txt` |
| DDA raycasting math proofs | `docs/dda-raycasting-math.txt` |
| Wall renderer pipeline | `docs/wall-renderer-guide.txt` |
| Weapon implementation (end-to-end) | `docs/weapon-creation-guide.txt` |
| Enemy AI, types, stats | `docs/enemy-system.txt` |
| Enemy health bar rendering | `docs/enemy-health-bars.txt` |
| HUD procedural rendering (no textures) | `docs/procedural-vitals-hud.txt` |
| Procedural level generation | `docs/procedural-level-generation.txt` |
| Tileset symbol/sprite reuse (categories, sprite registry, budget, palette, allocator, rooms, texture realization; RECIPE A/B) | `docs/environment-tileset-system.txt` |
| Route map / branching descent (nodes, generators, special levels, regions, events) | `docs/route-map-system.txt` |
| Story UI (barks, exchanges, boot card, codex, story persistence, localisation, story audio) | `docs/story-ui-system.txt` |
| Narrative CONTENT contract: what a line may say, the doctrine, the joke policy, ORA's voice, the comprehension bar | `docs/narrative-authority.txt` |
| Turn/tick system architecture | `docs/tick-system.txt` |
| Game balance — current state, every number/system/formula and known problems | `docs/game-balance-knowledge.txt` |
| Balance CONTRACT: the schema, the waivers, the behavioural bands, THE CHANGE PROTOCOL | `docs/game-balance-authority.txt` |
| XP and leveling system | `docs/xp-level-progression.txt` |
| Level design philosophy | `docs/level-design-context.txt` |
| Doom RPG design inspiration | `docs/doom-rpg-reference.txt` |
| Roguelike design pillars | `docs/roguelike-design-pillars.txt` |
| Feature ideas backlog (64 docs) | `.claude/agents/ideas/` directory |

## Docs Directory (`docs/`)

All 18 reference docs — read these before implementing anything in their domain:

| File | Lines | What it covers |
|---|---|---|
| `tile-symbols.txt` | 231 | Complete tile character reference — walls, doors, floors, props, pickups, enemies + the FIXED/FLEXIBLE model. **Single source of truth for level format.** |
| `environment-tileset-system.txt` | 850 | Symbol/sprite-reuse subsystem: categories, sprite registry, symbol budget, per-level palette, allocator, room blueprints, texture realization, render integration, and RECIPE A (add a sprite) / RECIPE B (add a room). **Single source of truth; update on any tileset/room/palette change.** |
| `route-map-system.txt` | 939 | Branching route-map subsystem: data model + registries, DAG generation & regions, node->floor pipeline, overlay/interaction, special-level profiles, ROUTE ECONOMICS (the priced map: node EV ledger, derived pips, map-gen guarantees, trajectory audit), node/region catalogs, and the extensibility recipes. **Single source of truth; update on any route change.** |
| `story-ui-system.txt` | 432 | The whole story UI MACHINE: the channels, roguelike story-gating, the persisted narrative schema + "new game" wipe, localisation, audio, overlay precedence, the moment schedule, the FRAMING SCREENS (title/launch, death->reprint, pause, endings), and the add-a-line recipes. **Single source of truth; update on any narrative change.** |
| `narrative-authority.txt` | 339 | The narrative CONTENT contract (narrative-rework order 1): the seven failures with evidence, the doctrine D1-D8 (a line teaches / reveals / lands a beat; plain before proper; the gap is the license; useful before funny before sad; say it once; ORA is a friend who is also staff; understand the Organization before hating it; if in doubt cut), the joke policy, the comprehension bar, ORA's voice charter, the series map + status ledger, and the add-a-line CHANGE PROTOCOL. **Read before writing, rewriting or cutting any line.** |
| `game-balance-knowledge.txt` | 842 | Complete map of the CURRENT balance state: the four primitives (eHP/DPT/TTK/TP), every balance-bearing file, all bands/anchors, and the consolidated known-problems list. **Read before touching any balance number.** The old `balance-rule-system.txt` contract doc is DELETED (deprecated); its replacement is created by the `new-game-balancr-order-*.txt` idea series (order 1). |
| `weapon-creation-guide.txt` | 537 | End-to-end weapon implementation: constants → Weapon subclass → marchShot → FrameBuffer sprite → World wiring |
| `enemy-health-bars.txt` | 282 | Health bar geometry, gradient colors, HP text rendering spec |
| `procedural-level-generation.txt` | 208 | Dungeon generator algorithm, room types, wall distribution logic |
| `procedural-vitals-hud.txt` | 202 | HUD rendering pipeline — procedural shapes, no sprite textures |
| `enemy-system.txt` | 401 | Enemy types, AI turn logic, pathfinding, attack resolution |
| `tick-system.txt` | 167 | Turn-based game loop, TickEventBus, TickSubscriber pattern |
| `xp-level-progression.txt` | 146 | Player leveling curve, attribute rewards, level-up card system |
| `dda-raycasting-math.txt` | 152 | DDA algorithm proof, perspective projection, Y-up correction |
| `doom-rpg-reference.txt` | 97 | Doom RPG design reference — tile movement, UI patterns |
| `wall-renderer-guide.txt` | 79 | WallRenderer pipeline, texture mapping, shade calculation |
| `level-design-context.txt` | 65 | Level design philosophy, environment theming |
| `roguelike-design-pillars.txt` | 54 | Core roguelike principles this game follows |

## Naming Conventions — MANDATORY

No abbreviations, single letters, or opaque shorthand in any Java identifier.

| Context | Forbidden | Required |
|---|---|---|
| Coordinate differences | `dx`, `dy` | `differenceX`, `differenceY` |
| Point parameters | `ax`, `ay`, `bx`, `by` | `fromX`, `fromY`, `toX`, `toY` |
| Vector components | `vx`, `vy` | `vectorX`, `vectorY` |
| Circle / shape center | `cx`, `cy` | `centerX`, `centerY` |
| Direction vector fields | `dirX`, `dirY` | `directionX`, `directionY` |
| Rotated vector temporaries | `newDirX`, `newDirY` | `rotatedDirectionX`, `rotatedDirectionY` |
| Interpolation parameters | `a`, `b`, `t` | `start`, `end`, `interpolationFactor` |
| Tile loop indices | `x`, `y`, `i`, `j` | `tileColumn`, `tileRow` |
| Grid line loop indices | `x`, `y` | `gridColumn`, `gridRow` |
| Grid dimensions | `cols`, `rows` | `columnCount`, `rowCount` |
| Level dimensions | `width`, `height` | `levelWidth`, `levelHeight` |
| Per-frame scaled distance | `speed` | `moveDistance` |
| Frame time | `delta` | `deltaTime` |
| DDA delta distances | `ddx`, `ddy` | `deltaDistanceX`, `deltaDistanceY` |
| DDA running distances | `sdx`, `sdy` | `sideDistanceX`, `sideDistanceY` |
| DDA perp wall distance | `dist`, `perpDist` | `perpWallDistance` |
| DDA total travel | `t`, `dist` | `travelDistance` |
| DDA step sign | `sx`, `sy` | `stepColumn`, `stepRow` |
| Ray loop index | `i`, `r` | `rayIndex` |
| FOV half-angle | `half`, `hfov` | `halfFieldOfViewRadians` |
| Per-ray angle offset | `off`, `offset` | `angleOffsetRadians` |
| Camera-plane column param | `cx`, `camX`, `t` | `cameraParameter` |
| Camera plane scale | `scale`, `s` | `planeScale` |
| Camera plane vector | `cx`, `cy`, `cam` | `planeX`, `planeY` |
| Wall stripe height | `h`, `wh`, `wallH` | `lineHeight` |
| Wall stripe Y bounds | `y0`, `y1` | `drawBottom`, `drawTop` |
| Wall brightness | `b`, `bright`, `s` | `shade` |

**Angle variables:** always suffix with `...Radians` or `...Degrees`. Never use Greek letters (`theta`, `phi`).

**General:** spell every word fully (`col` → `column`, `dir` → `direction`, `len` → `length`, `vel` → `velocity`, `pos` → `position`, `rot` → `rotation`, `prev` → `previous`, `temp` → `temporary`). Loop counters describe what they count (`tileColumn`, not `i`).

## Critical Rules

- **No allocations inside `render()`** — use object pools or pre-allocate in `create()`/`show()`.
- **Every `Disposable` must be disposed** — Texture, SpriteBatch, ShapeRenderer, Sound, Music, Skin, etc.
- **Angle units:** LibGDX `MathUtils.sin/cos` = radians; `MathUtils.sinDeg/cosDeg` = degrees. Never mix.
- **Camera:** call `camera.update()` before using `camera.combined` or `camera.unproject()`.
- **Render thread:** all OpenGL/LibGDX draw calls on render thread. Use `Gdx.app.postRunnable()` if needed from another thread.
- **AssetManager:** check `manager.update()` before `manager.get()`.
- **Viewport — call `viewport.apply()` every frame in `Main.render()`, before any draw calls.** `resize()` fires only on OS size events, not on in-frame state resets (e.g. world dispose → recreate after death screen). If `apply()` is missing, the GL viewport stays at raw screen dimensions after any such reset: every draw stretches to fill the screen and `FitViewport.unproject()` maps touch to wrong world coordinates (FitViewport stores its own offset/size, but the GL viewport no longer matches). **Never rely on `resize()` alone to keep the viewport current.**

## Math Conventions

- World coordinates: **(0, 0) = bottom-left, Y-up**.
- Angles: radians internally. Convert at LibGDX API boundaries that require degrees.
- Tile/grid logic: integer math. Physics and rendering: float.

## Rendering Architecture

Render order each frame (World.render):
```
1. FloorCeilingRenderer.render()  — textured floor & ceiling backdrop, full 1280×720
2. WallRenderer.render()          — 3D perspective wall projection on top of backdrop
3. PropRenderer.render()          — billboard prop sprites (barrels, crates, etc.)
4. EnemyRenderer.render()         — enemy billboard sprites (drawn over props)
5. WeaponHudRenderer.render()     — weapon sprite, bottom-centre of screen
6. LevelRenderer.render()         — 2D mini-map overlay, top-left corner
7. Player.render()                — player dot and direction indicator on mini-map
8. HudRenderer.render()           — HUD panels, drawn last (always on top)
9. StoryBarkRenderer.render()     — story bark panel (order-2), PLAYING phase only, above the
                                    HUD/event text but below every modal overlay and the fade
10. StoryExchangeRenderer.render() — blocking exchange panel (order-4), STORY_EXCHANGE phase only.
                                    Brings its own world dim, so it sits above the HUD/event text
                                    and below the boot card and the fade. The thumb clusters are
                                    NOT drawn in this phase
11. CodexOverlayRenderer.render() — the codex / archive (order-6), CODEX_OPEN phase only. Brings
                                    its own scrim over the frozen world, so it sits above the HUD
                                    and every in-run overlay, and below the boot card and the fade.
                                    The thumb clusters are NOT drawn in this phase
12. FramingScreenRenderer         — pause / settings menu (order-8), PAUSE_MENU and SETTINGS_MENU
      .renderMenu()                 phases. Own scrim over the frozen floor, above every in-run
                                    overlay, below the boot card and the fade. No thumb clusters
13. BootCardRenderer.render()     — reprint / boot card (order-3), BOOT_CARD phase only. Brings
                                    its own dark full-bleed background, so it sits above every
                                    other overlay and below only the fade. Order-8 hangs two things
                                    on this screen: InstanceReportRenderer's REPORT page (which
                                    replaces the card while open) and, on a terminal ending card,
                                    FramingScreenRenderer.renderTerminalReturn()
14. FramingScreenRenderer         — the DEATH STROKE (narrative-rework order-2), DEAD phase only.
      .renderDeathStroke()          Drawn ON the finished fade, so it is the only thing on the
                                    screen: three words, no HUD, no card, no ORA
```

The LAUNCH SCREEN (order-8) is not in that stack: while `RunPhase.TITLE` owns the app there is no
run to draw, so `World.render` draws `FramingScreenRenderer.renderTitle()` (plus the archive or the
settings screen, if one is open over it) and returns before the 3D view.

### HUD Layout (HudRenderer)

Two semi-transparent chrome panels; centre gap left clear so the weapon sprite is never occluded.

```
X:  0 ──────── 420    420 ──────── 860    860 ──────── 1280
    LEFT PANEL         (clear gap)          RIGHT PANEL
    face + HP/AR       weapon shows         ammo + weapon
    bars + status      through here         name
```

- **Left panel** (0–420): face box (88×88), HP bar (200px), AR bar (200px), status line.
- **Right panel** (860–1280): large ammo digits, weapon name / max-ammo.
- **Centre gap** (420–860): 440px empty. Weapon sprite is 380px wide, centred at X=450..830 — 30px clear of each panel.
- Panel backgrounds use `HUD_PANEL_ALPHA = 0.82f` (18% see-through).
- No compass; no centre panel.

HUD constants (HudConstants.java):
```
HUD_HEIGHT             = 130f
HUD_LEFT_PANEL_WIDTH   = 420f
HUD_RIGHT_PANEL_WIDTH  = 420f
HUD_PANEL_ALPHA        = 0.82f   panel background transparency
HUD_BAR_WIDTH          = 200f    HP/AR bar width
HUD_LEFT_LABEL_X       = 116f    HP/AR label x within left panel
HUD_LEFT_BAR_X         = 140f    HP/AR bar x within left panel
HUD_FACE_BOX_SIZE      = 88f
HUD_FACE_BOX_LOCAL_X   = 24f
HUD_FACE_BOX_LOCAL_Y   = 30f
```

## DDA Raycasting

**All DDA math in tile space**: `tileX = worldX / CELL_SIZE`

Key invariants:
| Variable | Meaning |
|---|---|
| `deltaDistanceX` | Ray length per one tile crossed in X: `1 / |rayDirectionX|` |
| `deltaDistanceY` | Ray length per one tile crossed in Y: `1 / |rayDirectionY|` |
| `sideDistanceX` | Running total to next vertical grid crossing |
| `sideDistanceY` | Running total to next horizontal grid crossing |
| `perpWallDistance` | `sideDist − deltaDist` = distance to last crossed grid line (the wall face) |

Never pass raw world coordinates to DDA helpers; convert to tile space first.
See `docs/dda-raycasting-math.txt` for all math proofs.

### Configurable Constants
```
RAY_COUNT                    = 60     fan rays for mini-map
RAY_MAX_LENGTH_CELLS         = 20f    max reach in tiles
PLAYER_FIELD_OF_VIEW_DEGREES = 90     default FOV — edit this, not the radians constant
```

## 3D Wall Projection

For each screen column: cast DDA ray → `perpWallDistance` → stripe height → texture column → draw.
See `docs/dda-raycasting-math.txt` for all formula derivations and `docs/wall-renderer-guide.txt` for the renderer pipeline.

**Y-Up Sign Correction — CRITICAL:**
Lodev (Y-down) tutorial uses `cameraParameter = 2 × col / W − 1`.
This project uses **`cameraParameter = 1 − 2 × col / W`** (Y-up correction).
**Never use the Lodev formula directly** — always call `GameMath.cameraPlaneParameter()`.

**Wall stripe render call:**
```java
batch.draw(wallTexture,
           screenColumn * WALL_COLUMN_WIDTH, drawBottom,
           WALL_COLUMN_WIDTH, drawTop - drawBottom,
           texColumn, 0, 1, textureHeight,
           false, false);
```
`srcY = 0` = top of image; LibGDX Y-up maps row 0 to visual top. No manual flip needed.

## Build

```bash
./gradlew lwjgl3:run    # Desktop run
./gradlew build         # Full build
./gradlew test          # Fast gate: unit tests + the whole balance audit (every commit)
./gradlew balanceSim    # Slow gate: plays the balance simulation matrix, checks the
                        # behavioural bands (run on any BalanceConfig change; see
                        # docs/game-balance-authority.txt SECTION 7 — THE CHANGE PROTOCOL)
```

Both gates RUN on every commit; new tests are only ever WRITTEN for balance — see
**Testing Policy — MANDATORY** above. Non-balance work is verified with `./gradlew build`
plus `./gradlew lwjgl3:run`, not with a new test.

## Dependencies

- LibGDX core + backends
- Ashley (ECS) — if entity-component system used
- Box2D — if physics needed
- FreeType — if runtime font rendering needed

Check `build.gradle` for exact versions before adding dependencies.
