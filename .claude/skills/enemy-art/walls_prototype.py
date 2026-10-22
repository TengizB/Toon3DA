"""PROTOTYPE wall textures for the wall-visual rework (.claude/agents/ideas/generated-wall-art.txt),
shown to the owner on 2026-10-22 - built with the enemy-art kit (same light rig, same height-field
shapes, same material library, same outline + cleanup). The record of the five prototypes; order 1
turns it into walls.py and order 10 deletes it. Nothing here is shipped.

    python3 walls_prototype.py /tmp/walls [A_ B_ ...]     # writes <name>.png + <name>.glow.png

A wall is one TILE FACE: 256x256, row 0 = ceiling line, row 255 = floor line. It must TILE
horizontally (the next tile's face sits right beside it), so every shape is stamped three times
(x - W, x, x + W) on a 3W-wide canvas and the middle W is cropped: smooth-max, grain, cavity blur
(np.roll wraps a 3W canvas = periodic) and the Bayer dither (W % 4 == 0) are then all seamless.
"""
import math
import os
import random
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kit  # noqa: E402
from kit import blob, limb, slab, chain, spike, riveted_plate, ramp, Xf  # noqa: E402

W = H = 256

# ---- ENVIRONMENT MATERIALS (appended LAST so every enemy sprite stays byte-identical) -------------
kit.MATERIALS.update({
    # poured structural concrete: warm grey, matte, coarse grain
    "concrete":  dict(ramp=ramp("15161a", "24252a", "37373b", "4d4c4e", "686562", "8c8780"), spec=(0.04, 8), grain=(0.07, 3)),
    "concrete_dk": dict(ramp=ramp("0b0c0f", "141519", "1e1f24", "2b2b30", "3b3a3e", "4f4d4f"), spec=(0.03, 8), grain=(0.06, 2.5)),
    # glazed abattoir tile: pale greige-celadon, glossy, almost no grain
    "ceramic":   dict(ramp=ramp("1c2221", "323b39", "4f5a56", "717c76", "97a19a", "c3c8bf"), spec=(0.65, 40), grain=(0.015, 6)),
    "rubber":    dict(ramp=ramp("060708", "0d0e10", "15171a", "1e2125", "2a2e33", "3a3f45"), spec=(0.15, 10), grain=(0.02, 3)),
    # oxidised steel: brown-umber, never a saturated orange
    "rust":      dict(ramp=ramp("140c0a", "2a1812", "432619", "5f3a24", "7d5435", "9f7552"), spec=(0.10, 10), grain=(0.09, 2)),
    # SOUL: the harvested planet-soul as printer fuel - pale, cold, low saturation (guide 4.4's planned ink)
    "soul":      dict(ramp=ramp("2a2c3a", "4a4e63", "767c94", "a5abc1", "d2d6e2", "f3f4f7"), emissive=True),
    # nacre: the Core's mineral-bone, pearl grey with a faint violet shadow (never a semantic purple)
    "nacre":     dict(ramp=ramp("17141c", "2b2633", "45404d", "665f6b", "8f8790", "bdb5b6"), spec=(0.45, 30), grain=(0.03, 3)),
    # translucent capsule glass over the soul light
    "capsule":   dict(ramp=ramp("0f1418", "1a2228", "28343b", "3a4a52", "566b73", "7f979d"), glow="soul", spec=(0.7, 60), grain=(0.0, 4)),
})


def tiled(shapes):
    """Stamp shapes at x - W, x, x + W (the canvas is 3W wide, the tile lives in the middle)."""
    out = []
    for dx in (0, W, 2 * W):
        out += Xf.move(dx, 0)(shapes)
    return out


def bake(layers, seed=11, stencils=()):
    img, glow = kit.render([tiled(layer) for layer in layers], 3 * W, H, seed=seed, glow_radius=0, return_glow=True)
    img = img.crop((W, 0, 2 * W, H))
    glow = glow.crop((W, 0, 2 * W, H))
    for text, x, y, col in stencils:
        kit.stencil(img, text, x, y, col)
    return img, glow


def backing(mat, bevel=1.0, overlap=12, **kw):
    """A full-face backing slab per tile, overlapping its neighbours so the merge has no seam."""
    return slab([(-overlap, -4), (W + overlap, -4), (W + overlap, H + 4), (-overlap, H + 4)], mat, bevel=bevel, slope=0.5, k=0, **kw)


def chamfer_rect(x0, y0, x1, y1, c):
    """A rectangle with 45-degree chamfered corners - the UiKit panel shape, so walls and chrome rhyme."""
    return [(x0 + c, y0), (x1 - c, y0), (x1, y0 + c), (x1, y1 - c), (x1 - c, y1), (x0 + c, y1), (x0, y1 - c), (x0, y0 + c)]


# ===================================================================================================
# A - THE HABITATION RINGS: an evacuated company town. Painted Organization panels, rubber bump rail,
#     riveted kick plate, a cable tray under the ceiling. Calm, ordered, recently lived-in.
# ===================================================================================================
def ring_panel():
    L0 = [backing("darkmetal", bevel=0.5)]
    # two painted livery panels per tile, chamfered like the UI plates
    for x0, x1 in ((10, 124), (132, 246)):
        L0.append(slab(chamfer_rect(x0, 40, x1, 196, 10), "livery", bevel=5, slope=0.8, k=0, prio=2))
        L0.append(slab(chamfer_rect(x0 + 16, 150, x1 - 16, 186, 4), "livery", bevel=2, slope=0.6, op="carve"))
        # vent slots in the lower inset
        for row in range(4):
            y = 157 + row * 7
            L0.append(limb(x0 + 24, y, x1 - 24, y, 1.6, 1.6, "darkmetal", op="carve"))
    # seam between upper and lower half of each panel
    L0.append(limb(-20, 112, W + 20, 112, 1.2, 1.2, "livery", op="carve"))
    # cornice: a gunmetal beam under the ceiling with a cable tray
    L0.append(slab([(-12, -4), (W + 12, -4), (W + 12, 30), (-12, 30)], "gunmetal", bevel=3, slope=0.9, k=0, prio=3))
    # kick plate at the floor
    L0 += riveted_plate([(-12, 214), (W + 12, 214), (W + 12, H + 4), (-12, H + 4)], "gunmetal", spacing=32, inset=6, rivet_r=1.8, bevel=3, k=0, prio=3)
    L1 = [
        limb(-20, 205, W + 20, 205, 6, 6, "rubber"),                      # the bump rail
        limb(-20, 24, W + 20, 24, 4, 4, "rubber"),                        # cable bundle in the tray
        limb(-20, 33, W + 20, 33, 2.5, 2.5, "gunmetal"),
    ]
    # rail brackets
    for x in (40, 216):
        L1.append(slab([(x - 5, 199), (x + 5, 199), (x + 5, 214), (x - 5, 214)], "gunmetal", bevel=2, k=0))
    stencils = [("R-07", 22, 50, (70, 74, 82)), ("07", 146, 50, (70, 74, 82))]
    return bake([L0, L1], seed=21, stencils=stencils)


# ===================================================================================================
# B - THE HARVESTING GALLERIES: an industrial abattoir. Glazed tile to shoulder height, a hook rail,
#     concrete above, a steel drain at the floor. Something was grown and cut here at scale.
# ===================================================================================================
def gallery_tile():
    rnd = random.Random(7)
    L0 = [backing("concrete", bevel=0.5)]
    # glazed tile field (rows 70..236), 32 px tiles, a few chipped
    for row in range(5):
        for col in range(8):
            x0, y0 = col * 32 + 2, 70 + row * 32 + 2
            if rnd.random() < 0.08:
                continue                                                  # missing tile: concrete shows
            L0.append(slab([(x0, y0), (x0 + 28, y0), (x0 + 28, y0 + 28), (x0, y0 + 28)], "ceramic", bevel=2.2, slope=1.0, k=0, prio=1))
            if rnd.random() < 0.12:                                       # a crack
                cx = x0 + rnd.uniform(6, 22)
                L0.append(limb(cx, y0 + 2, cx + rnd.uniform(-8, 8), y0 + 26, 0.9, 0.6, "ceramic", op="carve"))
    # the grime line: the field under the hook stained by what dripped
    for col in range(3):
        x = 128 + (col - 1) * 9 + rnd.uniform(-2, 2)
        L0.append(limb(x, 64, x + rnd.uniform(-2, 2), 140 + rnd.uniform(0, 70), 3.5, 1.2, "flesh_dk", op="add", k=2))
    L0.append(blob(128, 72, 22, 8, "flesh_dk", depth=0.8, op="add"))
    # drain gutter
    L0.append(slab([(-12, 238), (W + 12, 238), (W + 12, H + 4), (-12, H + 4)], "darkmetal", bevel=2, k=0, prio=3))
    for x in range(4, W, 12):
        L0.append(limb(x, 243, x, 252, 2.0, 2.0, "darkmetal", op="carve"))
    # concrete form-tie holes above the tile
    for x in (32, 96, 160, 224):
        L0.append(blob(x, 34, 3.2, 3.2, "concrete", op="carve"))
    L1 = [limb(-20, 52, W + 20, 52, 5, 5, "gunmetal"), limb(-20, 58, W + 20, 58, 2, 2, "darkmetal")]
    # the hook: trolley on the rail, chain, hook
    L1.append(slab([(118, 44), (138, 44), (138, 62), (118, 62)], "gunmetal", bevel=2, k=0))
    L1 += chain([(128, 62), (128, 78), (127, 94), (128, 106)], 2.4, 2.0, "darkmetal")
    L1 += spike(128, 104, 118, 132, 3.2, "gunmetal", bend=-10, tip=1.0)
    L1 += spike(118, 131, 112, 122, 2.0, "gunmetal", tip=0.6)
    stencils = [("G2-14", 8, 12, (150, 146, 138))]
    return bake([L0, L1], seed=33, stencils=stencils)


# ===================================================================================================
# C - THE RELIQUARY (feature wall, emissive): a Cradle print bay. A recessed glass capsule lit by the
#     rendered soul, a half-formed body curled inside, feed lines from the ceiling. Heavy riveted steel.
# ===================================================================================================
def reliquary_plate():
    """C hall BASE: heavy riveted plate in uneven widths, a fuel manifold under the ceiling, a floor channel
    with a thin trickle of rendered soul. No focal object - it must repeat along a run without a beat."""
    L0 = [backing("darkmetal", bevel=0.5)]
    for x0, x1 in ((-12, 92), (96, 168), (172, W + 12)):
        L0 += riveted_plate([(x0, 40), (x1, 40), (x1, 226), (x0, 226)], "gunmetal", spacing=24, inset=6, rivet_r=1.6, bevel=3, k=0, prio=1)
    L0.append(slab([(-12, 230), (W + 12, 230), (W + 12, H + 4), (-12, H + 4)], "darkmetal", bevel=2, k=0, prio=2))
    L0.append(limb(-20, 243, W + 20, 243, 3.0, 3.0, "darkmetal", op="carve"))
    L1 = [limb(-20, 243, W + 20, 243, 1.3, 1.3, "soul", glow=0.85),               # the soul trickle in the channel
          limb(-20, 14, W + 20, 14, 7, 7, "gunmetal"), limb(-20, 30, W + 20, 30, 3.5, 3.5, "rust")]
    for x in (60, 188):                                                            # manifold clamps
        L1.append(slab([(x - 7, 4), (x + 7, 4), (x + 7, 36), (x - 7, 36)], "darkmetal", bevel=2, k=0))
    stencils = [("KR-03", 104, 210, (120, 128, 140))]
    return bake([L0, L1], seed=45, stencils=stencils)


def soul_condenser():
    """C SIGNATURE ACCENT (replaces the rejected cradle bay - no body, no figure): a refinery condenser. A
    glass column of rendered soul, coiled filtration line around it, valves and a pressure gauge. The light is
    the story: this is what the Cradles burn."""
    L0 = [backing("darkmetal", bevel=0.5)]
    L0 += riveted_plate([(-12, -4), (64, -4), (64, H + 4), (-12, H + 4)], "gunmetal", spacing=26, inset=6, rivet_r=1.6, bevel=3, k=0, prio=1)
    L0 += riveted_plate([(192, -4), (W + 12, -4), (W + 12, H + 4), (192, H + 4)], "gunmetal", spacing=26, inset=6, rivet_r=1.6, bevel=3, k=0, prio=1)
    L0.append(slab(chamfer_rect(70, 20, 186, 240, 14), "darkmetal", bevel=6, slope=0.7, op="carve"))
    # the column: glass with the soul inside, hottest in the middle, settling dregs at the bottom
    L1 = [slab(chamfer_rect(98, 44, 158, 214, 10), "capsule", bevel=3, slope=0.5, k=0),
          blob(128, 124, 22, 76, "soul", depth=16, glow=1.0, k=0, prio=5),
          blob(128, 200, 24, 10, "soul", depth=6, glow=0.6, k=0, prio=5)]
    # filtration coil wound around the column (in front), valves, gauge
    L2 = []
    for i in range(6):
        y = 64 + i * 26
        L2.append(limb(94, y, 162, y + 12, 3.2, 3.2, "gunmetal"))
    L2 += [slab(chamfer_rect(88, 30, 168, 50, 5), "gunmetal", bevel=3, k=0),            # top cap
           slab(chamfer_rect(88, 208, 168, 230, 5), "gunmetal", bevel=3, k=0),          # base
           limb(128, -4, 128, 30, 6, 6, "gunmetal"),                                     # feed from the manifold
           limb(168, 220, W + 20, 220, 4, 4, "gunmetal"),                                # outflow to the next bay
           blob(178, 120, 9, 9, "darkmetal", depth=5), blob(178, 120, 5, 5, "livery", depth=3, op="add"),   # gauge
           limb(178, 120, 181, 115, 1.0, 1.0, "darkmetal", op="add"),
           blob(78, 170, 6, 6, "rust", depth=4), limb(78, 170, 78, 220, 2.5, 2.5, "gunmetal"),             # valve + line
           blob(178, 70, 3.5, 3.5, "cyan", depth=3)]                                    # status lamp
    stencils = [("KR-03", 16, 236, (120, 128, 140))]
    return bake([L0, L1, L2], seed=46, stencils=stencils)


# ===================================================================================================
# D - THE WOUND: the facility gives way to the being's body. Plating torn and pushed aside, living
#     tissue bulging through, ichor veins in the seams, a drill bit still buried in it.
# ===================================================================================================
def wound_breach(drill=False):
    rnd = random.Random(5)
    L0 = [backing("flesh_dk", bevel=0.5)]
    # tissue: dense swollen fibre bundles filling the face, bound by creases - muscle under strain
    for i in range(13):
        x = i * 20 + rnd.uniform(-5, 5)
        top, bottom = rnd.uniform(40, 70), rnd.uniform(240, 275)
        pts = [(x, top)]
        for k in range(1, 4):
            pts.append((x + rnd.uniform(-9, 9), top + (bottom - top) * k / 3))
        L0 += chain(pts, rnd.uniform(12, 16), rnd.uniform(14, 19), "flesh", k=10)
    for i in range(12):   # constrictions across the bundles
        x, y = rnd.uniform(0, W), rnd.uniform(90, 245)
        L0.append(limb(x - 16, y + rnd.uniform(-4, 4), x + 16, y + rnd.uniform(-4, 4), 2.0, 2.0, "flesh_dk", op="carve"))
    # glands pushing through, lit from inside
    for x, y, rx, ry in ((60, 176, 19, 23), (176, 214, 16, 19), (214, 120, 11, 13)):
        L0.append(blob(x, y, rx, ry, "membrane", depth=9, op="add", glow=1.05))
    # ichor veins: wandering trunks feeding the glands, thick at the trunk
    def vein(x, y, angle, length, r, n=8):
        pts = [(x, y)]
        for _ in range(n):
            angle += rnd.uniform(-0.45, 0.45)
            x += math.cos(angle) * length / n; y += math.sin(angle) * length / n
            pts.append((x, y))
        return chain(pts, r, r * 0.4, "ichor", op="add", k=3)
    L0 += vein(-14, 140, 0.25, 90, 4.2) + vein(270, 150, 3.0, 70, 4.2) + vein(60, 176, -1.9, 70, 3.2)
    L0 += vein(176, 214, -1.3, 100, 3.4) + vein(60, 176, 0.6, 90, 2.6) + vein(214, 120, -2.5, 60, 2.2)
    # the torn plating along the top: irregular tear, one sheet peeled forward with a rusted lip
    tear = [(-12, -4), (W + 12, -4), (W + 12, 50)]
    x = W + 12
    while x > -12:
        x -= rnd.uniform(14, 30)
        tear.append((max(x, -12), rnd.uniform(50, 84)))
    L1 = riveted_plate(tear, "gunmetal", spacing=30, inset=7, rivet_r=1.6, bevel=4, k=0)
    L1.append(slab([(-12, -4), (W + 12, -4), (W + 12, 14), (-12, 14)], "darkmetal", bevel=2, k=0, prio=2))
    L1.append(limb(-20, 6, W + 20, 6, 2.5, 2.5, "rust"))
    L2 = []
    # the drill: a dead bit buried in the tissue, flutes and collar
    if not drill:   # the drill is a FOCAL POINT: never on a base wall (it repeats along a run)
        return bake([L0, L1], seed=57, stencils=[("D-41", 10, 22, (120, 126, 134))])
    L3 = spike(252, 252, 132, 176, 20, "gunmetal", bend=-8, tip=2.0, segments=8, edge="darkmetal")
    for t in (0.18, 0.36, 0.54, 0.72):
        x, y = 252 + (132 - 252) * t, 252 + (176 - 252) * t
        L3.append(limb(x - 9, y - 12, x + 9, y + 12, 2.0, 2.0, "darkmetal", op="carve"))
    L3.append(limb(252, 252, 238, 243, 24, 22, "darkmetal"))
    stencils = [("D-41", 10, 22, (120, 126, 134))]
    return bake([L0, L1, L2, L3], seed=57, stencils=stencils)


# ===================================================================================================
# E - THE CORE: the being's mind. No facility left. A lattice of nacre-bone growing like a neural mesh,
#     soul light pulsing in the hollows between - the thing every Cradle on the way down was burning.
# ===================================================================================================
def core_lattice():
    rnd = random.Random(3)
    L0 = [backing("flesh_dk", bevel=0.5)]
    # deep hollows of soul light between the lattice (emissive, low)
    for _ in range(9):
        L0.append(blob(rnd.uniform(0, W), rnd.uniform(20, 240), rnd.uniform(10, 18), rnd.uniform(12, 22), "soul", depth=4, glow=0.75, k=4))
    # the lattice: branching nacre struts, curved, merging at nodes
    nodes = [(0, 40), (64, 20), (128, 52), (192, 24), (256, 40), (34, 110), (100, 128), (160, 104), (226, 124),
             (0, 190), (66, 206), (132, 186), (196, 214), (256, 190), (30, 256), (100, 256), (170, 256), (236, 256),
             (34, -10), (100, 0), (170, -6), (236, 0)]
    edges = [(0, 1), (1, 2), (2, 3), (3, 4), (0, 5), (1, 5), (2, 6), (2, 7), (3, 7), (4, 8), (3, 8), (5, 6), (6, 7), (7, 8),
             (5, 9), (5, 10), (6, 10), (6, 11), (7, 11), (7, 12), (8, 12), (8, 13), (9, 10), (10, 11), (11, 12), (12, 13),
             (9, 14), (10, 14), (10, 15), (11, 15), (11, 16), (12, 16), (12, 17), (13, 17), (18, 0), (18, 1), (19, 1),
             (19, 2), (20, 2), (20, 3), (21, 3), (21, 4)]
    L1 = []
    for a, b in edges:
        (x1, y1), (x2, y2) = nodes[a], nodes[b]
        mx, my = (x1 + x2) / 2 + rnd.uniform(-8, 8), (y1 + y2) / 2 + rnd.uniform(-8, 8)
        L1 += chain([(x1, y1), (mx, my), (x2, y2)], rnd.uniform(5, 7), rnd.uniform(4, 6), "nacre", k=8)
    for x, y in nodes:
        L1.append(blob(x, y, 10, 10, "nacre", depth=9, k=8))
        L1.append(blob(x - 1, y - 1, 3, 3, "soul", depth=2, op="add", glow=0.9))   # a lit synapse in each node
    return bake([L0, L1], seed=69)


WALLS = [("A_habitation_ring_panel", ring_panel), ("B_gallery_tile", gallery_tile), ("C_reliquary_plate", reliquary_plate), ("C_soul_condenser", soul_condenser),
         ("D_wound_breach", wound_breach), ("D_wound_drill_accent", lambda: wound_breach(drill=True)), ("E_core_lattice", core_lattice)]

if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    only = sys.argv[2:]
    for name, fn in WALLS:
        if only and not any(o in name for o in only):
            continue
        img, glow = fn()
        img.convert("RGB").save(os.path.join(out, name + ".png"))
        glow.save(os.path.join(out, name + ".glow.png"))
        print("wrote", name)
