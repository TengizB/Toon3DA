---
name: run-game
description: Build, run, drive and screenshot the real toon3D game inside a headless cloud container — the desktop-only "mirror" Gradle build (the repo root build cannot resolve the Android plugin here), a virtual display, a scripted touch-input capture driver (tap world coordinates, take PNG shots, time frames, force a lighting mood), the exact on-screen button coordinates, and the traps that cost hours to discover. Use it whenever you need to build/test, run the game, verify a visual or input change on screen, take screenshots for the owner, or measure frame time.
---

# Run, drive and screenshot toon3D in a cloud container

Everything here was learned the hard way (visual-identity orders 1-2, 2026-10). Read it before trying anything
else — every "obvious" route (plain `./gradlew`, `lwjgl3:run` + clicking, xdotool, java.awt.Robot) fails here.

This is a **dev tool for looking at the game**, not a test: it never enters the Gradle build, CI or the test suite,
and it adds no test. The Testing Policy in `.claude/CLAUDE.md` is unchanged — non-balance work is verified by
"build + run", and this is how "run" is done in a container.

## 1. One-time setup per container (idempotent)

```bash
.claude/skills/run-game/setup.sh
```
Creates `/tmp/toon3d-runtime/mirror` (override with `TOON3D_RUNTIME_DIR`), installs `openbox` if missing, starts
`Xvfb :77` + openbox. Re-run it in every fresh container and after editing `capture/`. Takes ~1 minute.

## 2. Build and test

```bash
cd /tmp/toon3d-runtime/mirror && ./gradlew -q :core:build :lwjgl3:compileJava
```
- **Never `./gradlew` at the repo root here**: the root `buildscript{}` needs the Android Gradle plugin, which this
  container cannot download (403/429). The mirror symlinks `core/ lwjgl3/ assets/ gradle* docs/ story/`, drops the
  android module and routes Maven Central through `maven-central.storage-download.googleapis.com` (the normal host
  answers 429). Edits in the repo are seen by the mirror instantly (symlinks).
- Test results land in the REAL `core/build/test-results/test/`. To list failures:
  `grep -l "<failure" core/build/test-results/test/*.xml`. Compare against the baseline recorded in the current
  checkpoint file (in 2026-10 one pre-existing failure: `narrative.StoryBarkTest`, bark.depth.core.2).
- `:core:build` stops at test failures, so `:lwjgl3:...` may not run after it — compile it separately if needed.
- `balanceSim` (balance work only): `./gradlew :core:balanceSim` from the mirror.

## 3. Run the game and capture screens

```bash
.claude/skills/run-game/run.sh [--out DIR] [--mood MOOD_ID] [--fps N] [--probe cpu|sync] [--gclog FILE] '<script>'
```
`--probe` turns on the dev `RenderPerformanceProbe` (`-Dtoon3d.perfProbe`): a `[perf]` log line every 5 s (rolling
120-frame avg/worst frame ms, per-pass ms and draw calls, draws/binds/shaders/verts, floor upload bytes) and a
two-line readout at the top centre. `cpu` = timing only; `sync` = glFinish after every pass so GPU work is charged
to the pass that queued it (inflates the total — use for attribution only). `--gclog FILE` writes `-Xlog:gc`;
count events with `grep -c Pause FILE`.
`<script>` = `;`-separated steps `SECONDS:COMMAND[:ARGS]`, times since launch:

| Step | Meaning |
|---|---|
| `tap:X:Y` | tap at **world** coordinates — 1280x720, **Y-UP**, exactly the numbers in `TouchConstants` / `StoryUiConstants` |
| `hold:X:Y:SECONDS` | press and hold (held movement repeats) |
| `shot:NAME` | write `DIR/NAME.png` (the real GL back buffer) after that frame |
| `pulse:ITEM_TYPE` | reflection into `World.atmosphereGrade.pulseMuzzleLight` (see muzzle light, below) |
| `call:PATH:METHOD[:INT]` | reflection: invoke a no-arg/int-arg method on `Main.world` (PATH `.`) or a field path from it (`player`, `playerProgress`) — e.g. `call:.:onDescentRequested` (route map), `call:player:applyDamage:9999` (death), `call:playerProgress:addXp:500` (level-up), `call:.:openShop` |
| `set:PATH:VALUE` | reflection: set a boolean/int field reached from `Main.world` (e.g. `set:startRoomChoiceResolved:true`, then `call:.:onDescentRequested` opens the route map from the start room) |
| `equip:INDEX` | reflection: put arsenal weapon INDEX into the loadout and select it (start room arsenal: 0 shotgun, 1 double barrel, 2 plasma, 3 chaingun, ...) — a HUD with a ranged weapon and ammo |
| `hittest:X:Y` | prints which touch buttons' hit areas contain world (X, Y), each tested as if visible (contextual INSPECT / USE too) — proves a tap area without the context that shows the button |
| `bosshud:PERCENT[:STATE]` | reflection: attach a BossHudRenderer with a stand-in boss at PERCENT health (optional `BossVisualState` constant name for the state label) — the boss bar from floor 1 |
| `alertall` | reflection: `alert()` every enemy, so the mini-map shows their threat markers |
| `engage` | reflection: commit the route map's first selectable node (the map is time-seeded, so node taps are not repeatable) — open the map first, then `engage` lands on floor 1 proper |
| `damagenumber:AMOUNT[:true]` | reflection: a floating damage number two tiles ahead (`true` = the kill variant) |
| `eventtext:CODE:TEXT` | reflection: `EventTextSystem.spawnWithColor(TEXT, CODE)` (0 white, 1 green, 2 red, 3 grey, 4 gold, 5 credit, 6 blue, 7 armour, 8 amber; TEXT without `;` or `:`) |
| `status:STATUS_TYPE:TURNS` | reflection: activate one of the player's status effects (e.g. `status:BURNING:3`) — the HUD status icons + debuff list |
| `additem:ITEM_TYPE:QTY` | reflection: `World.itemInventory.tryAdd` — puts items in the bag so the inventory grid and item windows have content (e.g. `additem:MEDKIT_SMALL:3`) |
| `inspectweapon` | reflection: marks the first weapon lying on the floor as the one the player stands on and opens the weapon inspect card (start-room weapons sit at random spots, so walking onto one is not repeatable) |
| `seed:N` | reflection: overwrites `World.runSeed` and drops any generated route map, so the next `call:.:onDescentRequested` builds the SAME map every run (the map is otherwise time-seeded) — use it for before/after map comparisons |
| `shop` | reflection: opens the fabricator on the floor's first vending machine (as `World.openShop` does for the machine in front); give credits first with `call:playerStats:addCredits:N` |
| `event[:EVENT_ID]` | reflection: opens the FACILITY EVENT console on that registered event (the first one when omitted) |
| `ending:VARIANT` | reflection: `World.presentEndingCard(BootCardVariant.VARIANT)` — `ENDING_FREE` / `ENDING_KILL` give the terminal card with the RETURN plate (constant names, not FREE/KILL) |
| `barkspeaker:SPEAKER` | reflection: puts the first registered bark of `AI` / `ORGANIZATION` / `PLANET` on screen through BarkSystem's own `deliver` (no SYSTEM barks exist) — another speaker's panel without reaching their region |
| `spawn:TYPE:FORWARD:RIGHT` | reflection: a **DORMANT** enemy of `EnemyType` TYPE, FORWARD tiles ahead and RIGHT tiles to the player's right (negatives = behind / left), through `EnemyManager.spawnEnemy` (the visual seam is detached for the call, so no ALERT plays). Prints its list index; a blocked tile is refused and said so |
| `alert:INDEX` | wakes enemy INDEX (list order; negative counts from the end, `-1` = last spawned) and reports the ALERTED visual moment, exactly as perception would |
| `damage:INDEX:AMOUNT` | `EnemyManager.applyDamageTo` — the REAL player-hit path (multipliers, Block, shards, death); prints HP before/after |
| `kill:INDEX` | the same path with an overwhelming amount (DEATH, corpse decal, XP) |
| `defend:INDEX` | a **FORCED** brace pose for a capture (state DEFENDING + the DEFEND_STARTED moment; no Block, no turn) — say "forced" when you show it |
| `status:INDEX:STATUS_TYPE:TURNS` | (4 args = an ENEMY; 3 args = the player, above) the REAL status path on enemy INDEX: `BURNING` through `EnemyManager.applyBurningStatus` (also reports STATUS_APPLIED, as a fire weapon does), anything else through World's `StatusEffectController.apply` |
| `block:INDEX:AMOUNT` | a **FORCED** Block buffer on the enemy (no brace, no turn) — the next hit is real and Block eats it (BLOCKED read). `block:INDEX:0` clears what is left |
| `hit:INDEX:AMOUNT:DAMAGE_CLASS` | the real hit path with that `DamageClass` armed exactly as `Weapon.fire()` arms it, so the matchup classifies it (`BALLISTIC` on the Warden or the Harvest Tick = RESISTED) |
| `equipmelee:ITEM_TYPE` | builds the melee weapon as a pickup does (`World.createMeleeWeaponForType`) and selects it, so FIRE swings it for real (`WEAPON_HAMMER`: 50% knockback roll per swing) |
| `logmoments` | wraps the floor's `EnemyVisualListener` so every visual moment prints (`moment STAGGERED …`): proof a reaction came from a real weapon. Issue it once per floor |
| `fx` | prints how many generated effect particles are alive (order-3 pool) |
| `clip:INDEX` | prints `state=… clip=…` — which animated clip (`render/enemyvisual`) the enemy shows, or `LEGACY` |
| `burst:NAME:COUNT:INTERVAL` | COUNT shots `NAME_00…` every INTERVAL s from now (one per frame at most; 0.05 at 30 fps ≈ every other frame) — turn them into a GIF with `burst2gif.sh` |
| `timestart` / `timestop` | average frame time between them (render + `glFinish`); use `--fps 0` |
| `exit` | quit (always end with it) |

`--mood` passes `-Dtoon3d.forceMood` (cool_steel, clinical_white, amber_hot, corrupted_violet, core_white_violet)
so every region can be checked from floor 1. Shots keep the framebuffer's ALPHA, so a translucent UI pixel
composited onto a white/grey canvas reads far lighter than it renders: add `-alpha off` to every
`montage`/`convert` that builds a comparison sheet, and judge colour from the RGBA originals.
Look at the PNGs with the Read tool; send them to the owner with
SendUserFile. ImageMagick (`convert`, `montage`) and Python PIL are installed — use `montage -tile 2x5` for
before/after sheets and `convert X.png -crop WxH+X+Y +repage` for close-ups.

### The standard opening (reach play in ~12 s)
```
2:tap:640:390;4:tap:640:390;9:tap:640:144;11:tap:640:144;13:shot:play;14:exit
```
- **The title needs TWO taps on the first row**: the first only completes the paced text reveal.
- NEW OPERATOR (640,314) is only offered on a PRINTED save (a fresh save has nothing to wipe — the tap
  falls through). A printed save with a chosen story text size (0 NORMAL / 1 LARGE / 2 LARGEST) is a small
  Preferences file: keys `story.reprintCount` 1, `story.schemaVersion` 5, `story.lastSession` (now, ms),
  `story.setting.textSize` N in `/root/.prefs/toon3d_records`. On a printed save the opening is
  `2:tap:640:600;3:tap:640:390;6:tap:640:600;7:tap:640:144`.
- More coordinates (orders 5-6): confirmation PROCEED (470,242) / CANCEL (810,242); pause rows y 526/450/374/298
  (RESUME / ARCHIVE / SETTINGS / ABANDON); settings BACK (640,70); reprint card REPORT (170,148), report BACK
  (640,138); exchange answers x=640 y 450/362/274, the reply's acknowledge (640,450); bark close X (915,667);
  relic offer LEAVE IT (644,100); route console ENGAGE (1113,52), CANCEL (167,52), MAP toggle (1160,586).
- Row 1 is BEGIN DESCENT on a fresh save and CONTINUE DESCENT afterwards — same position.
- `640:144` is the reprint card's CONTINUE (the second tap there is harmless slack).
- Floor 1 is the start-weapon room: weapons on the floor are **random every run** (seeded by time), so a script
  that walks onto "the pistol" is not repeatable. Use `pulse:` or reflection for effects that need a gun.

### Button coordinates (world, Y-up) — from `TouchConstants` / `TouchInputState`
Right combat cluster (centre column x=1100, columns 1004 / 1100 / 1196, rows y=200 / 296 / 392 / 488):

| | x=1004 | x=1100 | x=1196 |
|---|---|---|---|
| y=488 | SWITCH_WEAPON | RELOAD | SKIP_TURN |
| y=392 | STRAFE_LEFT | FORWARD | STRAFE_RIGHT |
| y=296 | ROTATE_LEFT | FIRE | ROTATE_RIGHT |
| y=200 | — | BACK | GUARD |

Left cluster: HEAL (180,296), OPEN_INVENTORY (180,392), INSPECT_WEAPON (180,200, only while standing on a weapon),
PAUSE (48,336). USE_MACHINE (640,360, contextual). Title menu rows (x=640): y=390 / 314 / 238 / 162 / 86 =
BEGIN-or-CONTINUE / NEW OPERATOR / ARCHIVE / SETTINGS / QUIT. Boot-card CONTINUE (640,144).
If a layout constant changes, recompute from the constants — do not trust eyeballed screenshot positions (one
"rotate" in 2026-10 was really STRAFE_RIGHT misread from a screenshot).

### Animated enemies (generated-enemy-art order 2)
Steps run in LIST order, not time order — a `clip:` sweep appended after `exit` never runs. Build long scripts with a
helper that sorts the steps by time. The pattern that shows every core clip of a creature (index 0 = the spawn):
```
13:spawn:SHELL_BRUTE:3:0;13.5:burst:dormant:30:0.05;15.2:alert:0;15.2:burst:alert:24:0.05;
18.3:tap:1196:488;18.3:burst:windup:36:0.05;20.3:tap:1196:488;20.3:burst:attack:30:0.05;
22:damage:0:2;22:burst:hurt_light:12:0.05;23:damage:0:29;23:burst:hurt_heavy:18:0.05;24.3:kill:0;24.3:burst:death:30:0.05
```
(SKIP_TURN advances the world: the Tick commits WIND_UP on the first skip and charges on the second; the Warden shoots on
the second.) A creature spawned off-lane (`spawn:TYPE:4:2`) walks, which is how MOVE is seen. Then
`burst2gif.sh DIR NAME --crop 800x500+239+70 --scale 60 --delay 5 --label "..."` per burst, and
`convert a.gif b.gif … all.gif` for one labelled sequence.

**Reactions and effects (generated-enemy-art order 3).** Same pattern, one segment per outcome:
`damage:0:3` (HURT_LIGHT) · `damage:0:13` on 45 HP (HURT_HEAVY, ≥25%) · `hit:0:4:BALLISTIC` (RESISTED) ·
`block:0:30;…damage:0:5` (BLOCKED) · `block:0:0;damage:0:N` to below 50% HP then a 2.5 s burst (WOUNDED + leak) ·
two SKIP_TURNs with an aligned Warden (ATTACK: glob from the prod emitter, burst at 0.05) · `status:0:STUNNED:2` /
`status:0:BURNING:6` · `kill:0` then a slow burst (REMAINS). Real STAGGER: `equip:0` (shotgun) + FIRE at a Harvest Tick
two tiles ahead (the shotgun one-shots a Warden). Real KNOCKBACK: `equipmelee:WEAPON_HAMMER` + FIRE at a Tick one
tile ahead, several swings, `logmoments` says which swing pushed. Traps: after a kill the indices shift (use `-1`);
forced Block persists until spent or cleared; never pipe `run.sh` into `head` (SIGPIPE ends the game early). ORA's tutorial barks appear on floor 1 — `tap:915:667` closes one.

### Muzzle light / anything needing game state
`pulse:WEAPON_SHOTGUN` (or `WEAPON_PLASMA`, `WEAPON_INCINERATOR`, …) then `shot:` in the SAME second captures the
splash ~1 frame after it fires. For other state, add a step to `capture/CaptureLauncher.java` the same way
(reflection on `Main.world` → a field → a method), then re-run `setup.sh` to copy it into the mirror.

## 4. Measuring things honestly

- **Frame time**: `--fps 0` + `timestart`/`timestop` over ≥5 s. The container GPU is llvmpipe (software, sharing
  the 4 CPUs): numbers are noisy (±1.5 ms) and only RELATIVE comparisons in the same session mean anything. Run
  A/B twice, interleaved. Never present a container number as phone performance.
- **One-off load timings** (e.g. the `EnemySpriteSheets conditioned N sheets in M ms` log) drift 2-3x between
  sessions on this VM — compare against the old code in the same session (`git stash` it, run, `stash pop`).
- **Measure pixels before calling a visual bug**: a faint dark shadow on a near-black floor looks "pale" in a
  downscaled view. Use PIL: `Image.open(a).convert('RGB').getpixel((x, y))` on before/after frames at the same spot.
- **Per-pass costs**: `--probe sync` for attribution (which pass is expensive), `--probe cpu` for CPU-side cost, no
  probe for the real frame time. llvmpipe is a CPU rasterizer: it is triangle/binning-bound, so thousands of thin
  full-width quads cost far more here than on a phone GPU (render-performance-full-hd CP4 learned this the hard
  way) — measure, do not assume.
- **Same-session baselines**: measure the old code from a detached worktree (`git worktree add --detach DIR SHA`,
  then `TOON3D_RUNTIME_DIR=/tmp/other DIR/.claude/skills/run-game/setup.sh` and that tree's `run.sh`), so editing the
  repo cannot leak into the baseline mid-matrix. Reset `/root/.prefs/toon3d_records` before every run (the reprint
  counter and intro beats otherwise change between runs).
- **Pixel-identical checks** cannot compare two runs (floor-1 weapons are random each run): toggle the old path for
  one frame inside ONE run (`set:` a boolean dev field) and mask out pixels that change between two new-path frames
  (animation). Shots closer than one frame apart overwrite each other — space them >= 0.1 s at 30 fps.
- Render resolution: Full HD is the default (`RenderResolution.DEFAULT`). To capture HD, temporarily set
  `DEFAULT = HD` in `util/RenderResolution.java`, run, and revert — or set the persisted key (below).

## 5. Traps (each cost real time — do not rediscover them)

| Trap | Cause → what works |
|---|---|
| Clicks do nothing in `./gradlew lwjgl3:run` | Desktop has no multitouch, so `World.initTouchControls` **returns early and the game ignores all input**. The capture driver's `Gdx.input` proxy answers `isPeripheralAvailable(MultitouchScreen) = true`. |
| xdotool / java.awt.Robot clicks ignored | Same root cause; also GLFW under bare Xvfb never repaints without a window manager. Use the capture driver, not X input. |
| Proxy installed but input still ignored | `Lwjgl3Application` re-assigns `Gdx.input` every frame — the driver re-installs its proxy before each `main.render()`. Keep that line. |
| Screenshots of the X root show a frozen frame | Read the GL back buffer (`ScreenUtils.getFrameBufferPixels`) after `main.render()` — that is what `shot:` does. |
| Window is 1278x695, not 1280x720 | openbox decorations. The driver maps world → window through the FitViewport letterbox; always pass world coordinates. |
| `assets/game-audio/`, `assets/story-audio/` appear in `git status` | The desktop game caches synthesised WAVs in `assets/`. `run.sh` deletes them; if you run the game another way, delete them yourself. Never commit them. |
| Title says BEGIN vs CONTINUE, intro beats differ | The save lives in `/root/.prefs/toon3d_records` (LibGDX Preferences). Delete that file for a fresh-save run; add `<entry key="story.setting.render.resolution">0</entry>`-style keys to force a setting. |
| `pkill -f Lwjgl3Launcher` kills your own shell | The pattern matches the bash command line. Kill by PID: `for p in $(pgrep -x java); do ps -o args= -p $p | grep -q capture.CaptureLauncher && kill $p; done`. |
| Android API pitfalls invisible on desktop | e.g. `ByteBuffer.get(int, byte[], int, int)` (absolute bulk) needs Android API 34 — desktop JDK 21 compiles it happily. Use relative bulk get/put on a `duplicate()`. |

## 6. Who uses this

- **orchestrator / main thread**: every checkpoint whose DONE WHEN says "holds on run", and the screens the owner
  asks for. Record what was and was NOT seen on screen in the checkpoint handover.
- **libgdx-specialist, weapon-creator, game-level-designer**: verify renders and levels on screen before
  reporting done; say "not run" honestly if you did not.
- **reviewer**: read the captured PNGs the hub hands over; ask for a specific capture when a criterion is visual.
