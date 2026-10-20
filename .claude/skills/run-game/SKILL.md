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
.claude/skills/run-game/run.sh [--out DIR] [--mood MOOD_ID] [--fps N] '<script>'
```
`<script>` = `;`-separated steps `SECONDS:COMMAND[:ARGS]`, times since launch:

| Step | Meaning |
|---|---|
| `tap:X:Y` | tap at **world** coordinates — 1280x720, **Y-UP**, exactly the numbers in `TouchConstants` / `StoryUiConstants` |
| `hold:X:Y:SECONDS` | press and hold (held movement repeats) |
| `shot:NAME` | write `DIR/NAME.png` (the real GL back buffer) after that frame |
| `pulse:ITEM_TYPE` | reflection into `World.atmosphereGrade.pulseMuzzleLight` (see muzzle light, below) |
| `call:PATH:METHOD[:INT]` | reflection: invoke a no-arg/int-arg method on `Main.world` (PATH `.`) or a field path from it (`player`, `playerProgress`) — e.g. `call:.:onDescentRequested` (route map), `call:player:applyDamage:9999` (death), `call:playerProgress:addXp:500` (level-up), `call:.:openShop` |
| `set:PATH:VALUE` | reflection: set a boolean/int field reached from `Main.world` (e.g. `set:startRoomChoiceResolved:true`, then `call:.:onDescentRequested` opens the route map from the start room) |
| `timestart` / `timestop` | average frame time between them (render + `glFinish`); use `--fps 0` |
| `exit` | quit (always end with it) |

`--mood` passes `-Dtoon3d.forceMood` (cool_steel, clinical_white, amber_hot, corrupted_violet, core_white_violet)
so every region can be checked from floor 1. Look at the PNGs with the Read tool; send them to the owner with
SendUserFile. ImageMagick (`convert`, `montage`) and Python PIL are installed — use `montage -tile 2x5` for
before/after sheets and `convert X.png -crop WxH+X+Y +repage` for close-ups.

### The standard opening (reach play in ~12 s)
```
2:tap:640:390;4:tap:640:390;9:tap:640:144;11:tap:640:144;13:shot:play;14:exit
```
- **The title needs TWO taps on the first row**: the first only completes the paced text reveal.
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
