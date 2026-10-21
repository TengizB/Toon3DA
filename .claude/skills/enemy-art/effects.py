"""Generated enemy EFFECTS (generated-enemy-art order 3): small particle sprites sculpted by the same kit,
from the same materials, plus the burst RECIPES that say how the runtime throws them. See SKILL.md and
docs/sprite-art-creation-guide.txt section 9.

Usage:  python3 effects.py OUT_DIR [--preview DIR] [set ...]
  writes, per effect SET: <set>.fx.png (every frame of every sprite, trimmed and packed, binary alpha) and
  <set>.fx.json (the FX MANIFEST: sprites -> frames, bursts -> emitters, creatures -> where they emit).
  --preview  also writes <set>_fx_sheet.png: every sprite frame at 4x on the game's near-black.

SETS. One per enemy FAMILY, named exactly as the family enum in lower case ("machine", "insect"), plus the
shared "status" set (fire, smoke, toxin bubbles, the dazed stars) that every family uses for statuses whose
colour is semantic (art-direction V5), not a family ink. A later family order adds ONE entry to SETS.

THE RULES (guide 9):
  - A family set uses ONLY its family's materials and ink (machine: metal + cyan + white-hot flare; insect:
    chitin + bone + ichor). No gore: sparks, ichor, dust - never blood.
  - EMISSIVE sprites keep only their emissive pixels (no dark outline, which would read as soot on a 2 px
    spark) and are drawn UNGRADED by the runtime, NORMAL alpha blend - the same rule as the body's glow
    atlas (order 2, D2). Non-emissive sprites (debris, chitin, smoke) keep the kit outline and are graded
    with the body.
  - Deterministic: every shape position comes from a seeded generator; the same script writes byte-
    identical files. Never regenerates a creature (bestiary.py does that).

COORDINATES in the manifest: frame rects are ATLAS pixels (image space, y down, as the creature manifest);
"pivot" is where the particle's centre sits inside its trimmed rect. Every emitter number is in CANVAS
PIXELS of the creature that throws it (the runtime converts with that creature's own pixel scale), so a
spark is the same size as the sprite's pixels at any distance: speed px/s, gravity px/s^2 (+ = down),
angle degrees (0 = right on screen, 90 = up), toward = px/s toward the viewer.
"""
import json
import math
import os
import random
import sys

from PIL import Image, ImageDraw

import kit
from kit import blob, limb, slab

FX_VERSION = 1


# ---------------------------------------------------------------------------------------------------
# Sprites
# ---------------------------------------------------------------------------------------------------
class Sprite:
    """One particle kind. `build(orientation, frame) -> layers` on a size x size canvas.
    mode "life": the frames play once over the particle's lifetime (a spark cools, a puff grows);
    mode "loop": the frames cycle at `fps` (debris tumbling, a glob wobbling).
    orientations > 1: the frames are pre-rendered at that many angles over 180 degrees (0 = horizontal);
    the runtime picks the one nearest the particle's on-screen velocity (pixel art is never rotated)."""

    def __init__(self, name, size, frames, build, emissive, mode="life", fps=12, orientations=1, seed=11):
        self.name, self.size, self.frames, self.build = name, size, frames, build
        self.emissive, self.mode, self.fps, self.orientations, self.seed = emissive, mode, fps, orientations, seed


def _axis(orientation, orientations):
    angle = math.pi * orientation / orientations           # 0 .. 180 degrees, image space (y down)
    return math.cos(angle), -math.sin(angle)


def _fade(frame, frames, start, end):
    return start + (end - start) * (frame / max(1, frames - 1))


def _jagged(points_rng, x1, y1, x2, y2, segments, wobble):
    pts = [(x1, y1)]
    for i in range(1, segments):
        t = i / segments
        nx, ny = -(y2 - y1), (x2 - x1)
        n = math.hypot(nx, ny) or 1.0
        off = points_rng.uniform(-wobble, wobble)
        pts.append((x1 + (x2 - x1) * t + nx / n * off, y1 + (y2 - y1) * t + ny / n * off))
    pts.append((x2, y2))
    return pts


# --- machine -----------------------------------------------------------------------------------------
def _spark(o, f, n=4, size=11):
    """A white-hot streak that cools to the cyan ink and shortens over its life."""
    c = size / 2
    ax, ay = _axis(o, 4)
    length = _fade(f, n, 4.2, 1.2)
    mat = "flare" if f < n - 1 else "cyan"
    glow = _fade(f, n, 2.2, 1.1)
    return [[limb(c - ax * length, c - ay * length, c + ax * length, c + ay * length, 0.9, 0.7, mat, glow=glow)]]


def _arc(o, f):
    """The cyan arc that crawls across the hull: three jagged strokes, re-jittered every frame."""
    rng = random.Random(400 + f)
    shapes = []
    for start, end in (((3, 9), (14, 4)), ((14, 4), (25, 10)), ((10, 7), (17, 13))):
        pts = _jagged(rng, *start, *end, 4, 1.8)
        for a, b in zip(pts, pts[1:]):
            shapes.append(limb(*a, *b, 0.8, 0.7, "cyan", glow=_fade(f, 3, 1.9, 1.1)))
    return [shapes]


def _debris(o, f, size=9, mat="gunmetal"):
    """A torn hull fleck, tumbling (four rotations)."""
    c = size / 2
    pts = [(-3.0, -1.6), (2.6, -2.2), (3.2, 1.4), (-1.8, 2.2)]
    r = Xturn(f * math.pi / 4)
    return [[slab([(c + x, c + y) for x, y in (r(p) for p in pts)], mat, bevel=1.2)]]


def _panel(o, f, size=17):
    """A hull panel blown off in the death burst: livery plate, one rivet, tumbling."""
    c = size / 2
    pts = [(-6.0, -3.5), (5.5, -4.2), (6.2, 3.6), (-5.4, 4.0)]
    r = Xturn(f * math.pi / 4)
    rivet = r((2.5, -0.5))
    return [[slab([(c + x, c + y) for x, y in (r(p) for p in pts)], "livery", bevel=1.6),
             blob(c + rivet[0], c + rivet[1], 1.2, 1.2, "darkmetal", op="add", depth=1.0)]]


def _hard_spark(o, f):
    """RESISTED on metal: one hard four-point star, white-hot, collapsing."""
    c, n = 7.5, 3
    arm = _fade(f, n, 6.5, 2.5)
    glow = _fade(f, n, 2.4, 1.3)
    return [[limb(c - arm, c, c + arm, c, 1.1, 0.5, "flare", glow=glow),
             limb(c, c - arm, c, c + arm, 1.1, 0.5, "flare", glow=glow),
             blob(c, c, 1.6, 1.6, "flare", glow=glow + 0.3)]]


def _glob(o, f):
    """The Warden's projectile: a corrosive glob of the Organization's cyan-tinted agent (AS1), wobbling."""
    c = 6.5
    rx, ry = ((4.2, 3.6), (3.7, 4.1), (4.0, 3.8))[f]
    return [[blob(c, c, rx, ry, "cyan", glow=1.0), blob(c - 1.2, c - 1.0, 1.4, 1.2, "flare", op="add", depth=1.0, glow=1.6)]]


def _trail(o, f):
    """A drop shed by the glob, dripping as it falls."""
    c = 3.5
    stretch = (2.6, 1.9, 1.2)[f]
    return [[limb(c, c - stretch, c, c + 0.6, 0.7, 1.2, "cyan", glow=_fade(f, 3, 1.2, 0.8))]]


def _muzzle(o, f):
    """The emit flash on the prod's tip: an eight-ray cyan burst with a white-hot heart."""
    c, n = 10.5, 3
    ray = _fade(f, n, 9.0, 3.5)
    shapes = []
    for i in range(8):
        a = i * math.pi / 4
        length = ray * (1.0 if i % 2 == 0 else 0.6)
        shapes.append(limb(c, c, c + math.cos(a) * length, c + math.sin(a) * length, 1.3, 0.5, "cyan",
                           glow=_fade(f, n, 1.9, 1.2)))
    shapes.append(blob(c, c, _fade(f, n, 3.2, 1.6), _fade(f, n, 3.2, 1.6), "flare", glow=2.0))
    return [shapes]


# --- insect ------------------------------------------------------------------------------------------
def _ichor(o, f):
    """A coral ichor droplet: stretched as it leaves the wound, rounding, then a bead."""
    c = 4.5
    stretch, radius = ((3.0, 1.3), (1.6, 1.6), (0.6, 1.2))[f]
    return [[limb(c, c - stretch, c, c + 0.4, 0.8, radius, "ichor", glow=_fade(f, 3, 1.3, 0.9))]]


def _fleck(o, f, mat="chitin"):
    """A flake of shell, tumbling."""
    c = 3.5
    pts = [(-2.4, -1.0), (2.0, -1.6), (2.4, 1.2), (-1.6, 1.4)]
    r = Xturn(f * math.pi / 4)
    return [[slab([(c + x, c + y) for x, y in (r(p) for p in pts)], mat, bevel=1.0)]]


def _grit(o, f):
    """Grit kicked up by dug-in legs: a speck of shell dust."""
    c = 2.5
    return [[blob(c + (0.3 if f else 0), c, 1.6, 1.2 if f else 1.6, "bone", depth=1.0)]]


def _drip(o, f):
    """A drip from the burst blister, lengthening before it lets go."""
    c = 2.5
    run = (1.4, 2.6, 3.4)[f]
    return [[limb(c, 1.5, c, 1.5 + run, 0.8, 1.1, "ichor", glow=1.1)]]


def _mist(o, f):
    """The death pop: ichor mist as the blisters fail - a puff that swells and dims."""
    c, n = 8.5, 4
    rng = random.Random(900 + f)
    r = _fade(f, n, 3.0, 7.0)
    glow = _fade(f, n, 1.2, 0.35)
    shapes = [blob(c, c, r, r * 0.85, "ichor", glow=glow)]
    for _ in range(3):
        a = rng.uniform(0, 2 * math.pi)
        shapes.append(blob(c + math.cos(a) * r * 0.6, c + math.sin(a) * r * 0.5, r * 0.45, r * 0.4, "ichor", glow=glow))
    return [shapes]


def _chip(o, f):
    """RESISTED on shell: a dull chip of dark chitin with a pale broken edge, tumbling."""
    c = 4.5
    pts = [(-3.2, -1.4), (2.8, -2.4), (3.4, 1.0), (-2.2, 2.4)]
    r = Xturn(f * math.pi / 4)
    edge = r((3.0, -0.6))
    return [[slab([(c + x, c + y) for x, y in (r(p) for p in pts)], "chitin_dk", bevel=1.3),
             blob(c + edge[0], c + edge[1], 1.0, 1.0, "bone", op="add", depth=0.8)]]


def _dust(o, f):
    """The dull puff a resisted hit knocks off a shell: bone-coloured dust, no light of its own."""
    c, n = 4.5, 3
    r = _fade(f, n, 1.8, 3.6)
    return [[blob(c, c, r, r * 0.8, "bone", depth=r * 0.4), blob(c + r * 0.6, c - r * 0.3, r * 0.5, r * 0.45, "bone", depth=r * 0.3)]]


# --- status (shared, semantic colours) ---------------------------------------------------------------
def _flame(o, f):
    """A flame lick: rises, peaks, gutters. Fire is a status colour (V5), so it is the same on every family."""
    n = 5
    height = (4.0, 7.0, 9.0, 7.0, 3.5)[f]
    width = (2.0, 2.6, 2.4, 1.9, 1.3)[f]
    glow = (1.4, 1.7, 1.5, 1.1, 0.8)[f]
    cx, base = 6.5, 15.0
    return [[limb(cx, base, cx + 0.6, base - height, width, 0.5, "magma", glow=glow),
             limb(cx, base - 0.5, cx - 0.4, base - height * 0.55, width * 0.55, 0.4, "flare", op="add", depth=1.0, glow=glow + 0.4)]]


def _smoke(o, f):
    """Smoke from a burning body: a dark puff that swells as it rises (graded like the body)."""
    c, n = 6.5, 4
    r = _fade(f, n, 2.2, 5.2)
    return [[blob(c, c, r, r * 0.85, "darkmetal", depth=r * 0.22, k=3),
             blob(c + r * 0.5, c - r * 0.4, r * 0.55, r * 0.5, "darkmetal", depth=r * 0.18, k=3)]]


def _bubble(o, f):
    """A toxin bubble rising off a POISONED body."""
    c = 3.0
    r = (1.2, 1.8, 2.2)[f]
    return [[blob(c, c, r, r, "toxin", glow=_fade(f, 3, 1.3, 0.9)), blob(c - r * 0.35, c - r * 0.35, 0.6, 0.6, "flare", op="add", depth=0.6, glow=1.5)]]


def _stun(o, f):
    """The dazed stars over a STUNNED head: a four-point twinkle in the hit-flash white."""
    c = 3.5
    arm = (2.8, 1.6)[f]
    return [[limb(c - arm, c, c + arm, c, 0.8, 0.4, "flare", glow=1.8), limb(c, c - arm, c, c + arm, 0.8, 0.4, "flare", glow=1.8)]]


def Xturn(angle):
    ca, sa = math.cos(angle), math.sin(angle)
    return lambda p: (p[0] * ca - p[1] * sa, p[0] * sa + p[1] * ca)


# ---------------------------------------------------------------------------------------------------
# Bursts: emitter recipes. Every number in creature canvas pixels (see the module docstring).
# ---------------------------------------------------------------------------------------------------
def emitter(sprite, count, life, speed, angle=90.0, spread=180.0, toward=(0.0, 0.0), gravity=0.0, drag=0.0,
            radius=0.0, orbit=None):
    """`orbit` = (radius px, period s): the particles circle their spawn point (seen edge-on, so the circle is
    flattened) instead of flying - the dazed stars. Particles of one orbiting emitter are spread evenly round it."""
    out = dict(sprite=sprite, count=list(count), life=list(life), speed=list(speed), angle=angle, spread=spread,
               toward=list(toward), gravity=gravity, drag=drag, radius=radius)
    if orbit is not None:
        out["orbit"] = list(orbit)
    return out


def burst(*emitters, interval=None):
    """A burst is a list of emitters fired together; `interval` [min, max] s makes it REPEAT while its
    condition holds (wounded leak, a status, a held wind-up)."""
    out = {"emitters": list(emitters)}
    if interval is not None:
        out["interval"] = list(interval)
    return out


def placement(anchor, offset=(0.0, 0.0), radius=0.0):
    """Where a creature emits a kind of effect: a manifest anchor of the CURRENT frame + a canvas offset,
    spread over `radius` px. Anchors: ground, hit_centre, eye, emitter (muzzle or mouth)."""
    return dict(anchor=anchor, offset=[float(offset[0]), float(offset[1])], radius=float(radius))


SETS = {
    # ---------------------------------------------------------------------------------------- MACHINE
    "machine": dict(
        sprites=[
            Sprite("SPARK", 11, 4, _spark, emissive=True, orientations=4),
            Sprite("ARC", 28, 3, _arc, emissive=True),
            Sprite("DEBRIS", 9, 4, lambda o, f: _debris(o, f), emissive=False, mode="loop", fps=14),
            Sprite("DEBRIS_DARK", 9, 4, lambda o, f: _debris(o, f, mat="darkmetal"), emissive=False, mode="loop", fps=11),
            Sprite("PANEL", 17, 4, _panel, emissive=False, mode="loop", fps=9),
            Sprite("HARD_SPARK", 15, 3, _hard_spark, emissive=True),
            Sprite("GLOB", 13, 3, _glob, emissive=True, mode="loop", fps=12),
            Sprite("TRAIL", 7, 3, _trail, emissive=True),
            Sprite("MUZZLE", 21, 3, _muzzle, emissive=True),
        ],
        bursts={
            # HURT: a spray of white-hot sparks + a cyan arc across the hull (VISUAL DESIGN).
            "HIT": burst(emitter("SPARK", (6, 8), (0.22, 0.40), (90, 190), spread=360, toward=(20, 60), gravity=420, drag=1.5, radius=4),
                         emitter("ARC", (1, 1), (0.12, 0.16), (0, 0), radius=6)),
            "HIT_HEAVY": burst(emitter("SPARK", (10, 13), (0.25, 0.45), (110, 240), spread=360, toward=(30, 90), gravity=420, drag=1.2, radius=6),
                               emitter("ARC", (2, 2), (0.14, 0.20), (0, 0), radius=12),
                               emitter("DEBRIS", (2, 3), (0.45, 0.70), (60, 120), angle=90, spread=150, toward=(10, 40), gravity=520, radius=4)),
            # RESISTED: one hard spark and a ringing shove (the shove is the recoil, a runtime rule).
            "RESIST": burst(emitter("HARD_SPARK", (1, 1), (0.16, 0.20), (0, 0), toward=(40, 40)),
                            emitter("SPARK", (1, 2), (0.12, 0.18), (140, 200), spread=360, gravity=300)),
            "KILL": burst(emitter("SPARK", (14, 18), (0.30, 0.55), (120, 280), spread=360, toward=(40, 110), gravity=420, drag=1.0, radius=8),
                          emitter("ARC", (2, 3), (0.18, 0.26), (0, 0), radius=14),
                          emitter("HARD_SPARK", (1, 1), (0.18, 0.22), (0, 0))),
            # DEATH: hull panels and a cyan flash (on the killing frame).
            "DEATH": burst(emitter("PANEL", (2, 3), (0.70, 1.00), (70, 140), angle=90, spread=160, toward=(10, 50), gravity=560, radius=10),
                           emitter("DEBRIS", (4, 6), (0.55, 0.85), (60, 160), spread=360, toward=(10, 40), gravity=560, radius=10),
                           emitter("DEBRIS_DARK", (3, 4), (0.55, 0.85), (60, 160), spread=360, gravity=560, radius=10),
                           emitter("MUZZLE", (1, 1), (0.18, 0.22), (0, 0))),
            # WOUNDED: intermittent sparks from the cracked visor.
            "LEAK": burst(emitter("SPARK", (2, 4), (0.25, 0.45), (50, 120), angle=-60, spread=120, gravity=380, radius=2),
                          emitter("ARC", (0, 1), (0.08, 0.12), (0, 0), radius=1),
                          interval=(0.30, 0.80)),
            # WIND_UP: the prod crackles.
            "CHARGE": burst(emitter("SPARK", (1, 2), (0.08, 0.14), (30, 70), spread=360, radius=3),
                            emitter("ARC", (0, 1), (0.08, 0.10), (0, 0), radius=2), interval=(0.10, 0.22)),
            "MUZZLE": burst(emitter("MUZZLE", (1, 1), (0.15, 0.15), (0, 0))),
            "PROJECTILE": burst(emitter("GLOB", (1, 1), (0.30, 0.30), (0, 0))),
            "TRAIL": burst(emitter("TRAIL", (1, 1), (0.22, 0.34), (5, 15), angle=-90, spread=60, gravity=260), interval=(0.025, 0.04)),
            # BLEED on a machine: it sheds hot fragments rather than fluid.
            "BLEED": burst(emitter("SPARK", (1, 2), (0.14, 0.22), (40, 90), angle=-90, spread=90, gravity=420, radius=4),
                           interval=(0.30, 0.60)),
        },
        creatures={
            "warden": dict(leak=placement("eye", (-6, -3), 3), burn=placement("hit_centre", (0, 44), 13),
                           charge=placement("emitter", (0, 0), 2), stun=placement("eye", (0, -30), 15),
                           poison=placement("hit_centre", (0, 0), 18), body=placement("hit_centre", (0, 0), 6)),
        },
    ),
    # ---------------------------------------------------------------------------------------- INSECT
    "insect": dict(
        sprites=[
            Sprite("ICHOR", 9, 3, _ichor, emissive=True),
            Sprite("FLECK", 7, 4, lambda o, f: _fleck(o, f), emissive=False, mode="loop", fps=14),
            Sprite("FLECK_DARK", 7, 4, lambda o, f: _fleck(o, f, mat="chitin_dk"), emissive=False, mode="loop", fps=11),
            Sprite("GRIT", 5, 2, _grit, emissive=False, mode="loop", fps=8),
            Sprite("DRIP", 5, 3, _drip, emissive=True),
            Sprite("MIST", 17, 4, _mist, emissive=True),
            Sprite("CHIP", 9, 4, _chip, emissive=False, mode="loop", fps=12),
            Sprite("DUST", 9, 3, _dust, emissive=False),
        ],
        bursts={
            # HURT: coral ichor droplets + chitin flecks.
            "HIT": burst(emitter("ICHOR", (5, 7), (0.30, 0.50), (70, 160), spread=360, toward=(20, 60), gravity=520, drag=1.0, radius=5),
                         emitter("FLECK", (2, 3), (0.40, 0.60), (60, 130), spread=360, toward=(10, 40), gravity=560, radius=5)),
            "HIT_HEAVY": burst(emitter("ICHOR", (9, 12), (0.35, 0.60), (90, 210), spread=360, toward=(30, 90), gravity=520, drag=0.8, radius=8),
                               emitter("FLECK", (3, 4), (0.45, 0.70), (70, 160), spread=360, toward=(10, 50), gravity=560, radius=8),
                               emitter("FLECK_DARK", (2, 3), (0.45, 0.70), (70, 160), spread=360, gravity=560, radius=8)),
            # RESISTED: a dull chip off the shell.
            "RESIST": burst(emitter("CHIP", (1, 1), (0.40, 0.50), (60, 90), angle=60, spread=60, toward=(20, 30), gravity=520),
                            emitter("DUST", (1, 2), (0.20, 0.28), (5, 15), spread=360, radius=2)),
            "KILL": burst(emitter("ICHOR", (13, 16), (0.40, 0.65), (110, 250), spread=360, toward=(40, 110), gravity=520, drag=0.8, radius=10),
                          emitter("FLECK", (4, 5), (0.50, 0.75), (80, 180), spread=360, toward=(10, 50), gravity=560, radius=10),
                          emitter("FLECK_DARK", (3, 4), (0.50, 0.75), (80, 180), spread=360, gravity=560, radius=10)),
            # DEATH: a pop of ichor mist as the blisters fail.
            "DEATH": burst(emitter("MIST", (4, 5), (0.45, 0.65), (10, 30), angle=90, spread=120, gravity=-40, radius=22),
                           emitter("ICHOR", (6, 8), (0.40, 0.60), (60, 140), angle=90, spread=140, gravity=520, radius=18)),
            # WOUNDED: drips from the burst blister.
            "LEAK": burst(emitter("DRIP", (1, 1), (0.40, 0.55), (0, 10), angle=-90, spread=20, gravity=420, radius=1.5),
                          interval=(0.35, 0.90)),
            # WIND_UP: grit kicked up by the dug-in legs.
            "CHARGE": burst(emitter("GRIT", (3, 5), (0.30, 0.45), (50, 120), angle=90, spread=110, gravity=480, radius=44),
                            emitter("DUST", (1, 1), (0.30, 0.40), (5, 15), angle=90, spread=60, gravity=-20, radius=44),
                            interval=(0.06, 0.12)),
            "BLEED": burst(emitter("DRIP", (1, 1), (0.40, 0.55), (0, 10), angle=-90, spread=20, gravity=420, radius=10),
                           interval=(0.22, 0.45)),
        },
        creatures={
            "harvest_tick": dict(leak=placement("hit_centre", (24, -50), 1.5), burn=placement("hit_centre", (0, -66), 34),
                                 charge=placement("ground", (0, -3), 0), stun=placement("eye", (0, -26), 17),
                                 poison=placement("hit_centre", (0, -40), 30), body=placement("hit_centre", (0, -10), 10)),
        },
    ),
    # ---------------------------------------------------------------------------------------- STATUS (shared)
    "status": dict(
        sprites=[
            Sprite("FLAME", 16, 5, _flame, emissive=True),
            Sprite("SMOKE", 13, 4, _smoke, emissive=False),
            Sprite("BUBBLE", 6, 3, _bubble, emissive=True),
            Sprite("STUN", 7, 2, _stun, emissive=True, mode="loop", fps=8),
        ],
        bursts={
            "BURNING": burst(emitter("FLAME", (2, 3), (0.30, 0.45), (10, 30), angle=90, spread=40, gravity=-90, radius=1),
                             emitter("SMOKE", (0, 1), (0.70, 1.00), (15, 30), angle=90, spread=30, gravity=-30, radius=1),
                             interval=(0.07, 0.14)),
            "BURNING_ONSET": burst(emitter("FLAME", (5, 7), (0.30, 0.45), (30, 80), angle=90, spread=70, gravity=-90, radius=1)),
            "POISONED": burst(emitter("BUBBLE", (1, 1), (0.50, 0.80), (12, 26), angle=90, spread=40, gravity=-20, radius=1),
                              interval=(0.18, 0.35)),
            # STUNNED: three stars circling the head; each wave lives exactly one interval, so they never blink.
            "STUNNED": burst(emitter("STUN", (3, 3), (0.60, 0.60), (0, 0), orbit=(15, 1.2)), interval=(0.60, 0.60)),
        },
        creatures={},
    ),
}


# ---------------------------------------------------------------------------------------------------
# Bake
# ---------------------------------------------------------------------------------------------------
def render_sprite(sprite):
    """-> [[(trimmed RGBA, (left, top))] per frame] per orientation."""
    out = []
    for o in range(sprite.orientations):
        frames = []
        for f in range(sprite.frames):
            layers = sprite.build(o, f)
            img, glow = kit.render(layers, sprite.size, sprite.size, seed=sprite.seed, glow_radius=0, return_glow=True)
            if sprite.emissive:
                img = glow                       # emissive pixels only: no soot outline on a spark
            box = img.getchannel("A").getbbox() or (0, 0, 1, 1)
            frames.append((img.crop(box), (box[0], box[1])))
        out.append(frames)
    return out


def write_set(name, spec, out_dir, preview_dir=None, log=print):
    os.makedirs(out_dir, exist_ok=True)
    rendered = [(s, render_sprite(s)) for s in spec["sprites"]]
    flat = [img for _, orients in rendered for frames in orients for img, _ in frames]
    aw, ah, positions = kit.pack([im.size for im in flat])
    atlas = Image.new("RGBA", (aw, ah), (0, 0, 0, 0))
    for im, (x, y) in zip(flat, positions):
        atlas.paste(im, (x, y))
    manifest = {"version": FX_VERSION, "set": name, "atlas": f"{name}.fx.png", "atlasSize": [aw, ah],
                "sprites": {}, "bursts": spec["bursts"], "creatures": spec["creatures"]}
    cursor = 0
    for s, orients in rendered:
        entry = {"emissive": s.emissive, "mode": s.mode, "fps": s.fps, "frames": []}
        for frames in orients:
            row = []
            for im, (left, top) in frames:
                x, y = positions[cursor]; cursor += 1
                pivot = (s.size / 2.0 - left, s.size / 2.0 - top)
                row.append({"rect": [x, y, im.width, im.height], "pivot": [kit._r(pivot[0], 1), kit._r(pivot[1], 1)]})
            entry["frames"].append(row)
        manifest["sprites"][s.name] = entry
    _check(manifest)
    atlas.save(os.path.join(out_dir, f"{name}.fx.png"), optimize=False)
    with open(os.path.join(out_dir, f"{name}.fx.json"), "w", newline="\n") as fh:
        fh.write(fx_json(manifest))
    log(f"wrote {name}: {len(spec['sprites'])} sprites, {len(flat)} frames, atlas {aw}x{ah}, "
        f"{len(spec['bursts'])} bursts")
    if preview_dir:
        write_preview(name, rendered, preview_dir)


def _check(manifest):
    """Every emitter names a sprite of ITS OWN set (a family never borrows another family's art)."""
    for burst_name, b in manifest["bursts"].items():
        for e in b["emitters"]:
            assert e["sprite"] in manifest["sprites"], f"{manifest['set']}.{burst_name}: unknown sprite {e['sprite']}"


def fx_json(manifest):
    """Readable, diff-friendly JSON (one sprite, burst or creature per line); LibGDX's JsonReader reads it."""
    compact = lambda v: json.dumps(v, separators=(",", ":"))
    lines = ["{"]
    for k in ("version", "set", "atlas", "atlasSize"):
        lines.append(f' "{k}": {compact(manifest[k])},')
    for section in ("sprites", "bursts", "creatures"):
        items = list(manifest[section].items())
        lines.append(f' "{section}": {{')
        for i, (k, v) in enumerate(items):
            lines.append(f'  "{k}": {compact(v)}' + ("," if i < len(items) - 1 else ""))
        lines.append(" }" + ("," if section != "creatures" else ""))
    lines.append("}")
    return "\n".join(lines) + "\n"


def write_preview(name, rendered, preview_dir, scale=4):
    os.makedirs(preview_dir, exist_ok=True)
    cell = max(s.size for s, _ in rendered) * scale + 8
    rows = sum(len(o) for _, o in rendered)
    cols = max(s.frames for s, _ in rendered)
    sheet = Image.new("RGB", (120 + cols * cell, rows * cell + 8), kit.BACKGROUND[:3])
    draw = ImageDraw.Draw(sheet)
    row = 0
    for s, orients in rendered:
        for oi, frames in enumerate(orients):
            y = 4 + row * cell
            draw.text((6, y + 4), s.name + (f" o{oi}" if len(orients) > 1 else ""), fill=(230, 228, 220))
            draw.text((6, y + 18), ("emissive " if s.emissive else "") + s.mode, fill=(140, 150, 160))
            for fi, (im, (left, top)) in enumerate(frames):
                canvas = Image.new("RGBA", (s.size, s.size), kit.BACKGROUND)
                canvas.alpha_composite(im, (left, top))
                sheet.paste(canvas.convert("RGB").resize((s.size * scale, s.size * scale), Image.NEAREST),
                            (120 + fi * cell, y))
            row += 1
    sheet.save(os.path.join(preview_dir, f"{name}_fx_sheet.png"), optimize=False)


if __name__ == "__main__":
    args = sys.argv[1:]
    out = args.pop(0)
    preview = None
    if "--preview" in args:
        i = args.index("--preview"); preview = args[i + 1]; del args[i:i + 2]
    only = [a for a in args if not a.startswith("--")] or list(SETS)
    for set_name, set_spec in SETS.items():
        if set_name in only:
            write_set(set_name, set_spec, out, preview_dir=preview)
