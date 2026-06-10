# toon3D — Doom-like 3D Game (LibGDX)

First-person pseudo-3D dungeon-crawler roguelike. World is a flat 2D tile grid; raycasting fires one ray per screen column, draws a vertical stripe whose height = `screenHeight / perpWallDistance`. No 3D polygons anywhere.

**Current state:** 2D tile levels (.txt), first-person wall rendering with texture mapping + distance shading, camera-plane DDA, mini-map overlay, tile-based player movement (Doom RPG style).

**Inspiration & design context:** See `docs/doom-rpg-reference.txt` and `docs/roguelike-design-pillars.txt`.

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

## Project Structure

```
toon3D/
├── core/src/main/java/ge/tbegvadze/toon3d/
│   ├── Main.java               # ApplicationListener — entry point (root package only)
│   ├── util/Constants.java     # ALL game-wide constants
│   ├── util/GameMath.java      # ALL math formulas as static methods with derivation comments
│   ├── level/Level.java        # Tile-based level grid
│   ├── level/LevelLoader.java  # Loads Level from .txt asset file
│   ├── entity/Player.java      # Player position, direction, FOV
│   ├── render/                 # Renderers (WallRenderer, LevelRenderer, RayCaster, etc.)
│   ├── input/PlayerController.java
│   ├── door/                   # Door state and animation
│   └── world/World.java        # Top-level simulation — owns Level and renderers
├── lwjgl3/src/            # Desktop launcher
├── assets/levels/         # Level .txt files
└── docs/                  # Extended reference (not needed at runtime)
```

## Package Rules

Root package (`ge.tbegvadze.toon3d`) is reserved for `Main.java` only.

| Package | Purpose |
|---|---|
| `…toon3d.util` | Stateless utilities: `Constants`, `GameMath` |
| `…toon3d.level` | Level data and loading |
| `…toon3d.entity` | Game entities / ECS components |
| `…toon3d.screen` | LibGDX `Screen` implementations |
| `…toon3d.render` | Renderers, shaders, batch helpers |
| `…toon3d.world` | Top-level simulation objects |
| `…toon3d.input` | Input processors |
| `…toon3d.door` | Door state/animation |

New class: pick the most specific matching package. If none fits, add a subpackage and document it here.

## Automated Code Review

A `PostToolUse` hook runs `code-reviewer` automatically after every Write/Edit to a `.java` file. No manual trigger needed.

## Agent Roster

| Agent | When to use |
|---|---|
| `creative-game-designer` | New mechanics, enemies, items, progression, level concepts, creative direction. **Consult before implementing any new gameplay feature.** |
| `game-level-designer` | Creating or modifying level `.txt` files. **Always use when a new level file is needed.** |
| `weapon-creator` | Implementing any weapon end-to-end: constants, Weapon subclass, marchShot logic, FrameBuffer procedural sprite in WeaponHudRenderer, World wiring. Also use to add/improve a procedural sprite for an existing weapon. See `docs/weapon-creation-guide.txt`. |
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

### `util/Constants.java`
Single source of truth for every magic number. **Never hardcode a value elsewhere.** Add to `Constants` first, then reference it.

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

### `level/Level.java`
2D tile grid. **Full tile symbol reference: `docs/tile-symbols.txt`** — single source of truth for every character used in level files.

Quick summary of categories:
- **Walls (18):** `x c v t w h j G k N Q S M Z U X D F`
- **Doors (4):** `d R Y B`
- **Floor lighting (4):** `(space) l u f`
- **Special (3):** `p` (start), `P` (column), `>` (exit)
- **Solid props (13):** `g E T L C # % & = @ I J W`
- **Walkable decals (5):** `m s . O e`
- **Pickups (7):** `r y b + H a A`
- **Enemy spawns (5):** `1 2 3 4 5` (replaced with floor at load time)

`Level.isWall(char)` is the single authority on which chars are solid — both `WallRenderer` and `PlayerController` call it. When adding a new wall type, add it there first.

Tile grid indexed by `(x, y)` where `(0, 0)` = bottom-left tile (Y-up). Package-private constructor — always instantiate via `LevelLoader`.

#### STRICT RULE — Adding a new tile symbol

**Every new symbol must be introduced in a single commit that includes ALL of:**
1. An entry in `docs/tile-symbols.txt` (correct section, description, notes).
2. The symbol added to `Level.java` in the appropriate method (`isWall()`, `isPropSolid()`, etc.).
3. Texture/sprite lookup wired in `WallRenderer.java` or `PropRenderer.java`.
4. Any new `Constants` entries (texture path, height multiplier, etc.).
5. The symbol count updated in the SYMBOL BUDGET section of `docs/tile-symbols.txt`.

**Never use a symbol in a level file or in game code that is not already in `docs/tile-symbols.txt`.**

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
```

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

HUD constants (Constants.java):
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
./gradlew test          # Tests
```

## Dependencies

- LibGDX core + backends
- Ashley (ECS) — if entity-component system used
- Box2D — if physics needed
- FreeType — if runtime font rendering needed

Check `build.gradle` for exact versions before adding dependencies.
