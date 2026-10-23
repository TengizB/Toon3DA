"""PROPS - environment objects generated with the kit (the generated-prop-art series; guide section 14).

A prop is a short loop of FRAMES on one canvas whose bottom row is the floor (and, for a full-height prop, whose
top row is the ceiling). Every frame is baked SMOOTH (kit.render smooth=2: no dither, no grain speckle,
anti-aliased edges - the wall bake), split into a BODY image and a GLOW image (the emissive pixels plus a soft
neon BLOOM around them), and the frames are packed into a grid:

    <id>.prop.png   body frames, RGBA          (graded and shaded in game, like every prop)
    <id>.glow.png   glow frames, RGBA          (drawn ungraded on top: the neon never greys out)
    <id>.prop.json  {"version", "id", "frameWidth", "frameHeight", "frameCount", "columns", "frameSeconds"}

Frame k sits at grid column k % columns, row k // columns. A still prop is a one-frame sheet.

Content that is not a sculpted surface - a screen's text, a portal's vortex - is PAINTED into the rendered frame
with numpy after the kit render (like kit.stencil): generated in code, seeded, deterministic.

    python3 props.py OUT_DIR [--preview PREVIEW_DIR] [prop ...]
"""
import json
import math
import os
import random
import sys

import numpy as np
from PIL import Image

import kit
from kit import blob, limb, slab, chain, chamfer_rect

SMOOTH_SAMPLES = 2
SHEET_LIMIT = 2048
MANIFEST_VERSION = 1


# ---------------------------------------------------------------------------------------------------
# Shared helpers
# ---------------------------------------------------------------------------------------------------
def bake(layers, width, height, seed):
    """One frame: (body RGBA float array, glow RGBA float array), smooth bake, no presentation spill."""
    img, glow = kit.render(layers, width, height, seed=seed, glow_radius=0, return_glow=True, smooth=SMOOTH_SAMPLES)
    return np.asarray(img, float).copy(), np.asarray(glow, float).copy()


def over(dst, src, alpha_scale=1.0):
    """Alpha-composite src over dst (both RGBA float 0-255, same size), in place."""
    sa = src[..., 3:] / 255.0 * alpha_scale
    da = dst[..., 3:] / 255.0
    out_a = sa + da * (1 - sa)
    rgb = (src[..., :3] * sa + dst[..., :3] * da * (1 - sa)) / np.maximum(out_a, 1e-6)
    dst[..., :3] = rgb
    dst[..., 3:] = out_a * 255


def paint(body, glow, mask, colour, glow_too=True, strength=1.0):
    """Paint a colour into the frame where mask (float 0-1) is set: the body gets it over its pixels, and the
    glow gets it as an emissive pixel. strength scales the coverage."""
    m = np.clip(mask * strength, 0, 1)[..., None]
    col = np.array(colour, float)
    body[..., :3] = body[..., :3] * (1 - m) + col * m
    body[..., 3:] = np.maximum(body[..., 3:], m * 255)
    if glow_too:
        ga = glow[..., 3:] / 255.0
        na = np.maximum(ga, m)
        glow[..., :3] = (glow[..., :3] * ga * (1 - m) + col * m) / np.maximum(ga * (1 - m) + m, 1e-6)
        glow[..., 3:] = na * 255


def bloom(glow, radius, strength, cap=0.6):
    """The neon BLOOM: a soft halo in the ink's own colour around every glowing pixel, kept in the glow image
    (alpha < 1) so the game draws it ungraded with the core. Cores stay untouched."""
    a = glow[..., 3] / 255.0
    pad = radius * 3
    ap = np.pad(a, pad); cp = np.pad(glow[..., :3] * a[..., None], ((pad, pad), (pad, pad), (0, 0)))
    blur_a = kit._blur(kit._blur(ap, radius), radius // 2 + 1)[pad:-pad, pad:-pad]
    blur_c = np.dstack([kit._blur(kit._blur(cp[..., c], radius), radius // 2 + 1)[pad:-pad, pad:-pad]
                        for c in range(3)])
    halo_rgb = blur_c / np.maximum(blur_a, 1e-6)[..., None]
    halo_a = np.clip(blur_a * strength, 0, cap)
    take = halo_a > a
    glow[take, :3] = halo_rgb[take]
    glow[take, 3] = halo_a[take] * 255


def finish(arr):
    """Float RGBA -> uint8 image; fully transparent pixels carry no colour."""
    out = np.clip(np.rint(arr), 0, 255).astype(np.uint8)
    out[out[..., 3] == 0, :3] = 0
    return Image.fromarray(out, "RGBA").copy()


class Prop:
    """One prop: id, canvas, frame count, frame seconds and frame(k) -> (body array, glow array)."""

    def __init__(self, prop_id, width, height, frames, frame_seconds, frame):
        self.id, self.width, self.height = prop_id, width, height
        self.frames, self.frame_seconds, self.frame = frames, frame_seconds, frame


# ---------------------------------------------------------------------------------------------------
# COMPUTER TERMINAL ('T') - a free-standing Organization console with a phosphor-green screen
# ---------------------------------------------------------------------------------------------------
TERM_W, TERM_H = 160, 200
TERM_FRAMES, TERM_FRAME_SECONDS = 32, 0.125
TERM_SCREEN = (36, 22, 124, 80)              # x0, y0, x1, y1 of the lit screen (inside the bezel)
TERM_LINE_H, TERM_CHAR_W = 7, 4
PHOSPHOR_HI = (150, 255, 160)
PHOSPHOR = (60, 235, 100)
PHOSPHOR_LO = (20, 120, 50)
SCREEN_BLACK = (4, 14, 8)
LETTERS = sorted(kit.STENCIL_FONT)


def terminal_body(lamps):
    """The console, with the three lamps' glow levels and the lit keys' glow level."""
    lamp_g, lamp_c, lamp_r, keys = lamps
    l0 = [  # pedestal: foot, column, and the lab-white front panel
        slab(chamfer_rect(26, 186, 134, 199, 3), "darkmetal", bevel=3, slope=0.9),
        slab(chamfer_rect(44, 110, 116, 190, 4), "gunmetal", bevel=5, slope=0.9),
        slab(chamfer_rect(52, 128, 108, 180, 3), "livery_dk", bevel=3, slope=0.9, op="add", depth=1.2),
        limb(56, 186, 104, 186, 1.6, 1.6, "rubber", op="add"),
        slab(chamfer_rect(58, 182, 102, 185, 1), "rad", bevel=1.2, slope=0.9, op="add", depth=1.0, glow=1.0),
    ]
    for vy in (136, 142, 148):                                                       # vent slots
        l0.append(limb(60, vy, 100, vy, 1.0, 1.0, "darkmetal", op="carve"))
    cable = chain([(118, 150), (130, 162), (138, 178), (142, 197)], 3.2, 3.6, "rubber")
    deck = [slab([(28, 98), (132, 98), (142, 124), (18, 124)], "darkmetal", bevel=4, slope=0.9)]
    rnd = random.Random(7)
    lit = {(1, 0): "neon_cyan", (2, 0): "neon_cyan", (7, 0): "rad", (3, 1): "neon_cyan", (8, 1): "rad"}
    for kx in range(10):
        for ky in range(2):
            x0 = 30 + kx * 10 + ky * 2 - 2; y0 = 104 + ky * 9
            mat = lit.get((kx, ky))
            if mat:
                deck.append(slab([(x0, y0), (x0 + 7, y0), (x0 + 7, y0 + 6), (x0, y0 + 6)], mat, bevel=1.5, op="add",
                                 depth=1.2, glow=keys))
            else:
                deck.append(slab([(x0, y0), (x0 + 7, y0), (x0 + 7, y0 + 6), (x0, y0 + 6)], "gunmetal", bevel=1.5,
                                 op="add", depth=1.3))
    housing = [
        slab(chamfer_rect(20, 6, 140, 98, 10), "gunmetal", bevel=8, slope=0.9),
        slab(chamfer_rect(30, 16, 130, 86, 6), "darkmetal", bevel=4, slope=0.9, op="carve"),
        slab(chamfer_rect(33, 19, 127, 83, 4), "pupil", bevel=1.5, slope=0.8, op="add", depth=0.5),
        limb(46, 10, 114, 10, 1.0, 1.0, "darkmetal", op="carve"),                   # top vent
        limb(46, 13, 114, 13, 1.0, 1.0, "darkmetal", op="carve"),
        blob(116, 92, 3.2, 3.2, "phosphor", op="add", glow=lamp_g),                  # indicator lamps
        blob(126, 92, 3.2, 3.2, "neon_cyan", op="add", glow=lamp_c),
        blob(106, 92, 3.2, 3.2, "rad", op="add", glow=lamp_r),
    ]
    for rx in (26, 134):                                                              # corner rivets
        for ry in (14, 90):
            housing.append(blob(rx, ry, 1.6, 1.6, "darkmetal", op="add"))
    return [l0, cable, deck, housing]


def terminal_lines(count, seed=4211):
    """The text the screen scrolls through: lines of pseudo-words in the stencil letters."""
    rnd = random.Random(seed)
    cols = (TERM_SCREEN[2] - TERM_SCREEN[0] - 4) // TERM_CHAR_W
    lines = []
    for _ in range(count):
        n = rnd.choice((6, 9, 12, 14, 17, 19, 21, cols))
        s = ""
        while len(s) < n:
            s += "".join(rnd.choice(LETTERS) for _ in range(rnd.randint(2, 6))) + " "
        if rnd.random() < 0.2:
            s = "  " + s
        lines.append(s[:min(n, cols)].rstrip())
    return lines


def draw_text(mask, text, x, y):
    for ci, ch in enumerate(text):
        bits = kit.STENCIL_FONT.get(ch)
        if not bits:
            continue
        for r in range(5):
            for c in range(3):
                if bits[r * 3 + c] == "1":
                    qx, qy = x + ci * TERM_CHAR_W + c, y + r
                    if 0 <= qy < mask.shape[0] and 0 <= qx < mask.shape[1]:
                        mask[qy, qx] = 1.0


def terminal_screen(body, glow, k, lines):
    x0, y0, x1, y1 = TERM_SCREEN
    h, w = body.shape[:2]
    scr = np.zeros((h, w)); scr[y0:y1, x0:x1] = 1
    paint(body, glow, scr, SCREEN_BLACK, glow_too=False)
    # the header bar (static): an inverse strip with a dark label cut into it
    head = np.zeros((h, w)); head[y0 + 2:y0 + 9, x0 + 2:x1 - 2] = 1
    label = np.zeros((h, w)); draw_text(label, "ORG LOG", x0 + 4, y0 + 3); draw_text(label, "07", x1 - 14, y0 + 3)
    paint(body, glow, head * (1 - label), PHOSPHOR_LO)
    # the scrolling lines: one line every two frames; the stream is cyclic so the loop is seamless
    visible = (y1 - (y0 + 12)) // TERM_LINE_H
    first = k // 2
    text = np.zeros((h, w)); bright = np.zeros((h, w))
    for row in range(visible):
        line = lines[(first + row) % len(lines)]
        ty = y0 + 12 + row * TERM_LINE_H
        if row == visible - 1:                                   # the newest line, being typed, brightest
            draw_text(bright, line, x0 + 3, ty)
            if (k // 4) % 2 == 0:                                # blinking block cursor
                cx = x0 + 3 + len(line) * TERM_CHAR_W + 1
                bright[ty:ty + 5, cx:cx + 3] = 1
        else:
            draw_text(text, line, x0 + 3, ty)
    paint(body, glow, text, PHOSPHOR)
    paint(body, glow, bright, PHOSPHOR_HI)
    # scanlines: every other screen row a little darker (body and glow)
    rows = np.zeros((h, w)); rows[y0:y1:2, x0:x1] = 1
    body[..., :3] *= (1 - 0.22 * rows)[..., None]
    glow[..., :3] *= (1 - 0.22 * rows)[..., None]
    # a faint phosphor wash over the whole glass (body only: the screen reads lit even far away)
    wash = scr * 0.10
    body[..., :3] = body[..., :3] * (1 - wash[..., None]) + np.array(PHOSPHOR_LO) * wash[..., None]


def terminal_lamps(k):
    green = 1.2
    cyan = 1.3 if (k // 4) % 2 == 0 else 0.35
    rad = 1.4 if (k % 8) < 2 or 4 <= (k % 8) < 5 else 0.3
    return (green, cyan, rad, 1.1)


def make_terminal():
    lines = terminal_lines(TERM_FRAMES // 2)
    cache = {}

    def frame(k):
        lamps = terminal_lamps(k)
        if lamps not in cache:
            cache[lamps] = bake(terminal_body(lamps), TERM_W, TERM_H, seed=311)
        body, glow = (a.copy() for a in cache[lamps])
        terminal_screen(body, glow, k, lines)
        img = finish(body)
        kit.stencil(img, "TRM07", 62, 158, (70, 74, 82))
        body = np.asarray(img, float).copy()
        bloom(glow, 3, 1.6, cap=0.5)
        return body, glow

    return Prop("terminal", TERM_W, TERM_H, TERM_FRAMES, TERM_FRAME_SECONDS, frame)


# ---------------------------------------------------------------------------------------------------
# SPECIMEN TUBE / BIO-POD ('&') - floor to ceiling, neon cyan fluid, a grown specimen drifting in it
# ---------------------------------------------------------------------------------------------------
POD_W, POD_H = 112, 256
POD_FRAMES, POD_FRAME_SECONDS = 24, 0.125
POD_TUBE = (20, 36, 92, 214)                 # x0, y0, x1, y1 of the glass
POD_CX = 56


FLUID_DEEP = np.array([0.0, 52.0, 66.0])
FLUID_MID = np.array([0.0, 170.0, 200.0])
FLUID_HOT = np.array([60.0, 238.0, 255.0])
FLUID_CORE = np.array([200.0, 255.0, 255.0])


def pod_fluid(k):
    """The fluid column, painted: a cylinder of neon cyan light - deep at the glass, hot down the middle, with
    slow vertical caustic bands that drift over the loop. -> (rgb, alpha) on the pod canvas."""
    x0, y0, x1, y1 = POD_TUBE
    t = 2 * math.pi * k / POD_FRAMES
    yy, xx = np.mgrid[0:POD_H, 0:POD_W].astype(float) + 0.5
    s = (xx - POD_CX) / ((x1 - x0) / 2)
    inside = (np.abs(s) <= 1) & (yy >= y0 - 6) & (yy <= y1 + 6)
    b = 0.30 + 0.70 * np.clip(1 - s * s, 0, 1) ** 0.9
    b += 0.07 * np.sin(yy * 0.11 + 3.0 * s + t) + 0.05 * np.sin(yy * 0.23 - 2.0 * s - 2 * t)
    b *= 0.92 + 0.08 * math.sin(t)                                                  # the fluid breathes
    b = np.clip(b, 0, 1)
    rgb = np.where((b < 0.55)[..., None],
                   FLUID_DEEP + (FLUID_MID - FLUID_DEEP) * (b / 0.55)[..., None],
                   np.where((b < 0.88)[..., None],
                            FLUID_MID + (FLUID_HOT - FLUID_MID) * ((b - 0.55) / 0.33)[..., None],
                            FLUID_HOT + (FLUID_CORE - FLUID_HOT) * ((b - 0.88) / 0.12)[..., None]))
    rim = np.clip((np.abs(s) - 0.90) / 0.10, 0, 1)[..., None]                       # the glass wall, edge-on
    rgb = rgb * (1 - 0.75 * rim) + np.array([20.0, 58.0, 66.0]) * 0.75 * rim
    return rgb, inside.astype(float)


def specimen(k):
    """A hunched, grown creature on a feeding line plugged into a harvest port on its back - no face (series
    rule). It drifts, turns and its tendrils trail over the loop."""
    t = 2 * math.pi * k / POD_FRAMES
    dy = 5 * math.sin(t); turn = 0.07 * math.sin(t + 0.8); sway = math.sin(t * 2 + 0.4)
    cx, cy = POD_CX + 2, 126 + dy
    xf = kit.Xf.turn(turn, (cx, cy))
    port = (cx + 12, cy - 12)
    line = chain([(POD_CX + 6, 40), (POD_CX + 9 + 2 * sway, 72), (port[0] + 1, port[1] - 6)], 1.8, 1.6, "rubber")
    body = [
        blob(cx + 2, cy - 4, 15, 23, "flesh", angle=0.30),                              # hunched torso
        blob(cx - 8, cy - 32, 12, 15, "flesh", angle=-0.55),                            # long, bowed, faceless cranium
        blob(cx - 2, cy - 20, 7, 8, "flesh"),                                           # neck
    ]
    body += chain([(cx - 2, cy - 40), (cx + 4, cy - 36), (cx + 10, cy - 26), (cx + 15, cy - 10),
                   (cx + 16, cy + 8), (cx + 10, cy + 22)], 3.0, 2.2, "flesh_dk")      # the dorsal ridge
    body += chain([(cx + 4, cy + 10), (cx - 10, cy + 22), (cx + 2, cy + 36), (cx - 4, cy + 42)], 6.5, 3.5, "flesh")  # leg
    arm = chain([(cx - 6, cy - 14), (cx - 15, cy + 4), (cx - 6, cy + 16)], 3.6, 2.6, "flesh_dk")
    claws = [limb(cx - 6, cy + 16, cx + 2 + 3 * i, cy + 21 - i, 1.0, 0.4, "bone") for i in range(3)]
    ridges = [blob(cx + 9 + 1.8 * i, cy - 30 + 10 * i, 2.0, 2.4, "bone", op="add") for i in range(5)]
    harvest = [blob(port[0], port[1], 4.5, 4.5, "darkmetal", op="add"), blob(port[0], port[1], 2.0, 2.0, "neon_cyan", op="add", glow=0.9)]
    tendrils = []
    for j, (ox, ln) in enumerate(((-4, 34), (4, 44), (10, 28))):
        w = math.sin(t + j * 1.7)
        tendrils.extend(chain([(cx + ox, cy + 26), (cx + ox + 5 * w, cy + 26 + ln * 0.5),
                               (cx + ox + 9 * w, cy + 26 + ln)], 2.4, 0.7, "flesh_dk"))
    return [line, xf(tendrils), xf(body + ridges + harvest), xf(arm + claws)]


def bubbles(k):
    rnd = random.Random(9031)
    x0, y0, x1, y1 = POD_TUBE
    span = (y1 - 8) - (y0 + 8)
    out = []
    for i in range(14):
        bx = rnd.uniform(x0 + 8, x1 - 8)
        r = rnd.choice((1.3, 1.6, 2.0, 2.6))
        laps = rnd.choice((1, 1, 2))
        start = rnd.uniform(0, span)
        y = (y1 - 8) - ((start + span * laps * k / POD_FRAMES) % span)
        wob = 1.5 * math.sin(k * 0.9 + i)
        out.append(blob(bx + wob, y, r, r, "neon_cyan", glow=1.15))
    return out


def pod_front(k):
    """Caps, collar, conduits into the ceiling, plinth, ring lights, glass sheen and bubbles."""
    t = 2 * math.pi * k / POD_FRAMES
    ring = 1.0 + 0.3 * math.sin(t)
    hazard = 1.25 if (k // 6) % 2 == 0 else 0.55
    x0, y0, x1, y1 = POD_TUBE
    top = [
        limb(36, 0, 36, 20, 4.5, 4.5, "darkmetal"), limb(76, 0, 76, 20, 4.5, 4.5, "darkmetal"),   # conduits up
        limb(56, 0, 56, 18, 6.5, 6.5, "gunmetal"),
        slab(chamfer_rect(12, 10, 100, 28, 4), "gunmetal", bevel=5, slope=0.9),      # ceiling collar
        slab(chamfer_rect(10, 26, 102, 38, 3), "darkmetal", bevel=4, slope=0.9),     # upper cap ring
        slab(chamfer_rect(16, 38, 96, 42, 1), "neon_cyan", bevel=1.5, slope=0.9, glow=ring),
    ]
    for rx in (16, 36, 56, 76, 96):
        top.append(blob(rx, 32, 1.7, 1.7, "gunmetal", op="add"))
    bottom = [
        slab(chamfer_rect(16, 210, 96, 214, 1), "neon_cyan", bevel=1.5, slope=0.9, glow=ring),
        slab(chamfer_rect(10, 213, 102, 226, 3), "darkmetal", bevel=4, slope=0.9),   # lower cap ring
        slab([(8, 226), (104, 226), (108, 255), (4, 255)], "gunmetal", bevel=5, slope=0.9),   # plinth
        slab(chamfer_rect(30, 232, 82, 246, 2), "livery", bevel=2, slope=0.9, op="add", depth=1.0),
        slab(chamfer_rect(10, 248, 102, 251, 1), "rad", bevel=1.2, slope=0.9, op="add", depth=1.0, glow=hazard),
    ]
    for vx in (16, 22, 90, 96):
        bottom.append(limb(vx, 232, vx, 244, 0.9, 0.9, "darkmetal", op="carve"))
    for rx in (16, 36, 56, 76, 96):
        bottom.append(blob(rx, 219, 1.7, 1.7, "gunmetal", op="add"))
    return [bubbles(k), top, bottom]


def make_pod():
    def frame(k):
        rgb, inside = pod_fluid(k)
        body = np.zeros((POD_H, POD_W, 4)); glow = np.zeros((POD_H, POD_W, 4))
        body[..., :3] = rgb; body[..., 3] = inside * 255
        glow[..., :3] = rgb; glow[..., 3] = inside * 235
        # the specimen, backlit by the fluid: a dark teal silhouette with a cyan rim, cut out of the glow
        sp, sg = bake(specimen(k), POD_W, POD_H, seed=519)
        sa = sp[..., 3] / 255.0
        lum = sp[..., :3].mean(axis=2, keepdims=True) / 255.0
        sp[..., :3] = np.array([6.0, 30.0, 38.0]) + np.array([40.0, 120.0, 130.0]) * lum ** 1.2
        over(body, sp, alpha_scale=0.94)
        glow[..., 3] *= (1 - 0.94 * sa)
        backlit = np.clip(sa - kit._shift(sa, -2, 0, 0.0), 0, 1) + np.clip(sa - kit._shift(sa, 2, 0, 0.0), 0, 1)
        paint(body, glow, np.clip(backlit, 0, 1) * 0.55, (110, 240, 255))
        over(glow, sg)
        # the steel in front: caps, collar, conduits up into the ceiling, plinth, ring lights, sheen, bubbles
        fb, fg = bake(pod_front(k), POD_W, POD_H, seed=521)
        over(body, fb)
        glow[..., 3] *= (1 - fb[..., 3] / 255.0)
        over(glow, fg)
        # the glass: two soft vertical highlights over the fluid (painted, part-transparent)
        yy, xx = np.mgrid[0:POD_H, 0:POD_W].astype(float)
        x0, y0, x1, y1 = POD_TUBE
        span = ((yy >= y0) & (yy <= y1 - 4)).astype(float) * (1 - fb[..., 3] / 255.0)
        streak = np.exp(-((xx - (x0 + 11)) / 1.6) ** 2) * 0.45 + np.exp(-((xx - (x1 - 9)) / 1.1) ** 2) * 0.22
        paint(body, glow, streak * span, (225, 255, 255))
        img = finish(body)
        kit.stencil(img, "SPC12", 46, 237, (70, 74, 82))
        body = np.asarray(img, float).copy()
        bloom(glow, 4, 1.3, cap=0.45)
        return body, glow

    return Prop("bio_pod", POD_W, POD_H, POD_FRAMES, POD_FRAME_SECONDS, frame)


# ---------------------------------------------------------------------------------------------------
# EXIT PORTAL ('>') - a rectangular walk-in gate, floor to ceiling, a cyan energy field on a steel platform
# ---------------------------------------------------------------------------------------------------
PORTAL_W, PORTAL_H = 224, 256
PORTAL_FRAMES, PORTAL_FRAME_SECONDS = 16, 1.0 / 12
PORTAL_PLATFORM_Y = 222                      # top of the steel platform the gate stands on
PORTAL_OPENING = (60, 44, 164, PORTAL_PLATFORM_Y)   # x0, y0, x1, y1: the doorway a person walks into
PORTAL_HEADER_LAMPS = 6


def portal_frame(k):
    """The steel: platform, two pillars, the header beam into the ceiling, light strips round the doorway."""
    x0, y0, x1, y1 = PORTAL_OPENING
    edge = 1.15 + 0.25 * math.sin(2 * math.pi * k / PORTAL_FRAMES)
    platform = [
        slab([(4, PORTAL_PLATFORM_Y), (220, PORTAL_PLATFORM_Y), (222, 238), (2, 238)], "gunmetal", bevel=5, slope=0.9),
        slab([(0, 236), (224, 236), (224, 255), (0, 255)], "darkmetal", bevel=4, slope=0.9),       # the step
        slab(chamfer_rect(40, 240, 184, 244, 1), "neon_cyan", bevel=1.2, slope=0.9, op="add", depth=1.0, glow=1.2),
        slab(chamfer_rect(x0 - 2, PORTAL_PLATFORM_Y + 2, x1 + 2, PORTAL_PLATFORM_Y + 7, 1), "darkmetal", bevel=1.5,
             op="carve"),                                                                          # threshold groove
    ]
    for tx in range(10, 216, 14):                                                     # tread plates on the step
        platform.append(slab([(tx, 247), (tx + 9, 247), (tx + 9, 252), (tx, 252)], "gunmetal", bevel=1.2, op="add",
                             depth=1.0))
    for rx in (12, 40, 184, 212):
        platform.append(blob(rx, 229, 1.8, 1.8, "darkmetal", op="add"))
    pillars = []
    for px0, px1 in ((22, x0), (x1, 202)):
        pillars += [
            slab(chamfer_rect(px0, 0, px1, PORTAL_PLATFORM_Y + 2, 3), "gunmetal", bevel=6, slope=0.9),
            slab(chamfer_rect(px0 + 7, 60, px1 - 7, 196, 3), "livery_dk", bevel=3, slope=0.9, op="add", depth=1.2),
            limb((px0 + px1) / 2, 70, (px0 + px1) / 2, 186, 1.6, 1.6, "darkmetal", op="carve"),     # conduit slot
        ]
        for ry in (52, 104, 156, 204):                                                # clamp bands
            pillars.append(slab(chamfer_rect(px0 - 3, ry, px1 + 3, ry + 7, 2), "darkmetal", bevel=2.5, slope=0.9))
        for ry in (80, 130, 176):                                                     # status lamps
            pillars.append(blob((px0 + px1) / 2, ry, 2.2, 2.2, "neon_cyan", op="add", glow=edge))
    header = [
        slab([(10, 0), (214, 0), (214, 30), (10, 30)], "gunmetal", bevel=6, slope=0.9),             # beam into ceiling
        slab(chamfer_rect(x0 - 6, 28, x1 + 6, y0 + 2, 3), "darkmetal", bevel=4, slope=0.9),        # emitter housing
        limb(40, 0, 40, 12, 4, 4, "darkmetal"), limb(184, 0, 184, 12, 4, 4, "darkmetal"),
    ]
    for i in range(PORTAL_HEADER_LAMPS):                                              # chasing emitter lamps
        lx = x0 + 10 + i * (x1 - x0 - 20) / (PORTAL_HEADER_LAMPS - 1)
        g = 1.5 if (i - k // 2) % 3 == 0 else 0.55
        header.append(blob(lx, 37, 3.0, 2.4, "neon_cyan", op="add", glow=g))
    strips = [                                                                         # light strips round the doorway
        limb(x0 + 1.5, y0 + 2, x0 + 1.5, y1 - 2, 1.6, 1.6, "neon_cyan", glow=edge),
        limb(x1 - 1.5, y0 + 2, x1 - 1.5, y1 - 2, 1.6, 1.6, "neon_cyan", glow=edge),
        limb(x0 + 2, y0 + 1.5, x1 - 2, y0 + 1.5, 1.6, 1.6, "neon_cyan", glow=edge),
    ]
    return [header, pillars, platform, strips]


def portal_field(k):
    """The doorway's energy, painted: a cyan field you could step into - rectangles of light receding to a bright
    vanishing core (they slide inward by exactly one spacing per loop, so the loop is seamless), a hot rim at the
    frame, faint vertical shimmer and motes rising. -> (rgb, alpha, intensity) on the portal canvas."""
    x0, y0, x1, y1 = PORTAL_OPENING
    yy, xx = np.mgrid[0:PORTAL_H, 0:PORTAL_W].astype(float) + 0.5
    inside = ((xx >= x0) & (xx < x1) & (yy >= y0) & (yy < y1)).astype(float)
    vx, vy = (x0 + x1) / 2, y0 + (y1 - y0) * 0.46                                   # vanishing point (eye height)
    u = np.where(xx < vx, (vx - xx) / (vx - x0), (xx - vx) / (x1 - vx))
    v = np.where(yy < vy, (vy - yy) / (vy - y0), (yy - vy) / (y1 - vy))
    d = np.clip(np.maximum(u, v), 0, 1)                                                # 0 at the core, 1 at the frame
    depth = np.log(d + 0.06)                                                           # perspective-ish spacing
    phase = k / PORTAL_FRAMES
    rings = np.cos(2 * math.pi * (depth * 2.2 + phase)) * 0.5 + 0.5
    inten = 0.20 + 0.30 * rings ** 6 * (0.3 + 0.7 * d)
    inten += 1.10 * np.exp(-(d / 0.20) ** 2)                                           # the bright core
    inten += 0.55 * np.exp(-((1 - d) / 0.07) ** 2)                                     # hot rim at the frame
    inten += 0.06 * np.sin(xx * 0.9 + 2 * math.pi * phase * 2) * (1 - d)              # vertical shimmer
    rnd = random.Random(4103)
    span = y1 - y0
    for i in range(16):                                                                # motes rising through the field
        mx = rnd.uniform(x0 + 6, x1 - 6); start = rnd.uniform(0, span); laps = rnd.choice((1, 1, 2))
        my = y1 - ((start + span * laps * phase) % span)
        inten += 0.8 * np.exp(-((xx - mx) ** 2 / 1.4 + (yy - my) ** 2 / 4.0))
    ramp = kit.MATERIALS["neon_cyan"]["ramp"]
    v_idx = np.clip(inten, 0, 1.3) / 1.3 * (len(ramp) - 1)
    lo = np.floor(v_idx).astype(int); hi = np.minimum(lo + 1, len(ramp) - 1); f = (v_idx - lo)[..., None]
    rgb = ramp[lo] * (1 - f) + ramp[hi] * f
    return rgb, inside


def make_portal():
    def frame(k):
        steel, steel_glow = bake(portal_frame(k), PORTAL_W, PORTAL_H, seed=613)
        rgb, inside = portal_field(k)
        body = np.zeros((PORTAL_H, PORTAL_W, 4)); glow = np.zeros((PORTAL_H, PORTAL_W, 4))
        body[..., :3] = rgb; body[..., 3] = inside * 255
        glow[..., :3] = rgb; glow[..., 3] = inside * 255
        # the field's light pools on the platform in front of the doorway (glow only: it never greys out)
        x0, y0, x1, y1 = PORTAL_OPENING
        yy, xx = np.mgrid[0:PORTAL_H, 0:PORTAL_W].astype(float)
        pool = np.exp(-(((xx - (x0 + x1) / 2) / 62) ** 2) - (((yy - (y1 + 6)) / 7) ** 2)) * (yy >= y1)
        over(body, steel)
        glow[..., 3] *= (1 - steel[..., 3] / 255.0)
        over(glow, steel_glow)
        paint(body, glow, pool * 0.55, (120, 240, 255))
        img = finish(body)
        kit.stencil(img, "EXIT-0", 100, 14, (150, 156, 164))
        body = np.asarray(img, float).copy()
        bloom(glow, 5, 1.4, cap=0.5)
        return body, glow

    return Prop("portal", PORTAL_W, PORTAL_H, PORTAL_FRAMES, PORTAL_FRAME_SECONDS, frame)


# ---------------------------------------------------------------------------------------------------
# AI CORE NODE ('J') - the PATTERN ARCHIVE: a floor-to-ceiling compute column storing the reprint patterns
# ---------------------------------------------------------------------------------------------------
CORE_W, CORE_H = 144, 256
CORE_FRAMES, CORE_FRAME_SECONDS = 24, 0.1
CORE_WINDOW = (42, 46, 102, 198)             # x0, y0, x1, y1: the glass down the front
CORE_HEART = (72, 120)                       # the pulsing core in the middle of the wafer stack
CORE_WAFERS = 9
CORE_GLASS = (6, 12, 16)
CORE_RAD_HOT = (235, 255, 150)
LED_ON = {"phosphor": (110, 255, 130), "neon_cyan": (90, 240, 255), "rad": (215, 255, 90)}


def ai_core_body():
    """The static steel: plinth, chassis, side rails, collar, cables into the ceiling, readout plate."""
    x0, y0, x1, y1 = CORE_WINDOW
    plinth = [
        slab([(10, 226), (134, 226), (138, 255), (6, 255)], "gunmetal", bevel=5, slope=0.9),
        slab(chamfer_rect(40, 232, 104, 246, 2), "livery_dk", bevel=2, slope=0.9, op="add", depth=1.0),
        slab(chamfer_rect(10, 249, 134, 252, 1), "rad", bevel=1.2, slope=0.9, op="add", depth=1.0, glow=1.1),
    ]
    for vx in (16, 22, 122, 128):
        plinth.append(limb(vx, 232, vx, 245, 0.9, 0.9, "darkmetal", op="carve"))
    chassis = [
        slab(chamfer_rect(26, 22, 118, 228, 4), "darkmetal", bevel=5, slope=0.9),
        slab(chamfer_rect(x0 - 6, y0 - 6, x1 + 6, y1 + 6, 4), "gunmetal", bevel=4, slope=0.9, op="add", depth=1.5),
        slab(chamfer_rect(x0, y0, x1, y1, 3), "darkmetal", bevel=3, slope=0.9, op="carve"),
        slab(chamfer_rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 3), "pupil", bevel=1.0, slope=0.8, op="add", depth=0.4),
        slab(chamfer_rect(46, 206, 98, 222, 2), "livery_dk", bevel=2, slope=0.9, op="add", depth=1.2),   # readout
    ]
    rails = []
    for rx0, rx1 in ((14, 30), (114, 130)):
        rails += [slab(chamfer_rect(rx0, 20, rx1, 230, 3), "gunmetal", bevel=4, slope=0.9),
                  slab(chamfer_rect(rx0 + 4, 44, rx1 - 4, 204, 2), "darkmetal", bevel=1.5, slope=0.9, op="carve")]
        for ry in (32, 214):
            rails.append(blob((rx0 + rx1) / 2, ry, 1.8, 1.8, "darkmetal", op="add"))
    collar = [
        slab(chamfer_rect(8, 8, 136, 26, 4), "gunmetal", bevel=5, slope=0.9),
        slab(chamfer_rect(20, 0, 124, 10, 2), "darkmetal", bevel=3, slope=0.9),
        limb(44, 0, 44, 12, 5, 5, "rubber"), limb(72, 0, 72, 12, 6.5, 6.5, "rubber"), limb(100, 0, 100, 12, 5, 5, "rubber"),
    ]
    for rx in (14, 40, 72, 104, 130):
        collar.append(blob(rx, 17, 1.6, 1.6, "darkmetal", op="add"))
    return [plinth, chassis, rails, collar]


def ai_core_window(body, glow, k, yy, xx):
    """The archive inside the glass, painted: dark glass, cyan-edged memory wafers, the data spine with pulses
    climbing it, and the rad-lime core breathing in the middle."""
    x0, y0, x1, y1 = CORE_WINDOW
    t = 2 * math.pi * k / CORE_FRAMES
    win = ((xx >= x0 + 2) & (xx < x1 - 2) & (yy >= y0 + 2) & (yy < y1 - 2)).astype(float)
    paint(body, glow, win, CORE_GLASS, glow_too=False)
    hx, hy = CORE_HEART
    # a faint cyan haze in the glass, strongest down the middle column
    haze = np.exp(-((xx - hx) / 14.0) ** 2) * win
    paint(body, glow, haze * 0.18, (40, 170, 190))
    # memory wafers: plates seen a little from above - a dim top face, a bright edge-lit lip, node dots
    spacing = (y1 - y0 - 8) / CORE_WAFERS
    for i in range(CORE_WAFERS):
        wy = y0 + 7 + i * spacing
        if abs(wy + 2 - hy) < 22:
            continue                                                                   # the heart's gap
        shimmer = 0.75 + 0.25 * math.sin(t * 2 + i * 1.3)
        inset = 2 * np.clip(1 - np.abs(yy - wy) / 4, 0, 1)                             # top face narrows
        top = ((yy >= wy - 4) & (yy < wy) & (xx >= x0 + 6 + 4) & (xx < x1 - 6 - 4)).astype(float)
        lip = ((yy >= wy) & (yy < wy + 2) & (xx >= x0 + 6) & (xx < x1 - 6)).astype(float)
        under = ((yy >= wy + 2) & (yy < wy + 4) & (xx >= x0 + 7) & (xx < x1 - 7)).astype(float)
        paint(body, glow, top * 0.45 * shimmer, (30, 110, 128))
        paint(body, glow, lip * shimmer, (120, 245, 255))
        paint(body, glow, under * 0.5, (8, 40, 48), glow_too=False)
        for nx in range(x0 + 10, x1 - 8, 8):                                           # node dots on the lip
            lit = random.Random(i * 97 + nx + (k // 3) * 13).random() < 0.5
            if lit:
                d = np.zeros(body.shape[:2]); d[int(wy) - 2:int(wy) - 1, nx:nx + 2] = 1
                paint(body, glow, d, (200, 255, 255))
    # the data spine and its pulses (they climb a whole window height per loop: seamless)
    spine = np.exp(-((xx - hx) / 1.1) ** 2) * win
    paint(body, glow, spine * 0.8, (110, 240, 255))
    span = y1 - y0
    for j in range(4):
        py = y1 - ((j * span / 4 + span * k / CORE_FRAMES) % span)
        pulse = np.exp(-((xx - hx) ** 2 / 2.5 + (yy - py) ** 2 / 10.0)) * win
        paint(body, glow, np.clip(pulse * 1.4, 0, 1), (230, 255, 255))
    # the core: a rad-lime halo, an outer ring with three arcs orbiting (one third turn per loop: seamless),
    # an inner ring, and a white-hot heart, all breathing
    breathe = 0.9 + 0.1 * math.sin(t)
    r = np.hypot(xx - hx, yy - hy)
    ang = np.arctan2(yy - hy, xx - hx)
    halo = np.exp(-(r / (20 * breathe)) ** 2) * win
    arcs = (np.cos(3 * (ang - t / 3)) > 0.35).astype(float)
    outer = np.clip(1 - np.abs(r - 16) / 1.6, 0, 1) * arcs * win
    inner = np.clip(1 - np.abs(r - 9 * breathe) / 2.2, 0, 1) * win
    heart = np.exp(-(r / (5.5 * breathe)) ** 2) * win
    paint(body, glow, halo * 0.55, (120, 190, 30))
    paint(body, glow, outer, (190, 255, 70))
    paint(body, glow, inner, (215, 255, 110))
    paint(body, glow, heart, CORE_RAD_HOT)


def ai_core_leds(body, glow, k):
    """Status LEDs down both rail slots: a seeded pattern that steps every two frames (12 states per loop)."""
    step = k // 2
    for side, cx in enumerate((22, 122)):
        for row in range(20):
            y = 50 + row * 8
            rnd = random.Random(side * 1000 + row * 31 + step * 7)
            if rnd.random() < 0.45:
                continue
            ink = "rad" if rnd.random() < 0.12 else rnd.choice(("phosphor", "neon_cyan"))
            m = np.zeros(body.shape[:2]); m[y:y + 2, cx - 1:cx + 2] = 1
            paint(body, glow, m, LED_ON[ink])


def ai_core_readout(body, glow, k):
    """A small phosphor bar graph on the readout plate under the window."""
    rnd = random.Random(55)
    phases = [rnd.uniform(0, 2 * math.pi) for _ in range(10)]
    t = 2 * math.pi * k / CORE_FRAMES
    m = np.zeros(body.shape[:2])
    for i, ph in enumerate(phases):
        hgt = int(2 + 8 * (0.5 + 0.5 * math.sin(t * (1 + i % 3) + ph)))
        x = 50 + i * 4.6
        m[220 - hgt:220, int(x):int(x) + 3] = 1
    paint(body, glow, m, (90, 240, 120))


def make_ai_core():
    base = bake(ai_core_body(), CORE_W, CORE_H, seed=709)
    yy, xx = np.mgrid[0:CORE_H, 0:CORE_W].astype(float)

    def frame(k):
        body, glow = base[0].copy(), base[1].copy()
        ai_core_window(body, glow, k, yy, xx)
        ai_core_leds(body, glow, k)
        ai_core_readout(body, glow, k)
        # data pulses carried on up the centre cable into the ceiling
        cable = ((xx >= 70) & (xx < 75) & (yy < 12)).astype(float)
        cy = 12 - ((k * 3) % 12)
        paint(body, glow, cable * np.exp(-((yy - cy) / 1.5) ** 2), (150, 245, 255))
        img = finish(body)
        kit.stencil(img, "PTN-ARC", 46, 236, (150, 156, 164))
        body = np.asarray(img, float).copy()
        bloom(glow, 4, 1.3, cap=0.45)
        return body, glow

    return Prop("ai_core", CORE_W, CORE_H, CORE_FRAMES, CORE_FRAME_SECONDS, frame)


# ---------------------------------------------------------------------------------------------------
# GENERATOR ('%') - a heavy power unit whose heat is the accent (the HOT IRON ink, owner D4)
# ---------------------------------------------------------------------------------------------------
GEN_W, GEN_H = 176, 216
GEN_FRAMES, GEN_FRAME_SECONDS = 16, 0.1
GEN_BAY = (46, 96, 130, 176)                 # x0, y0, x1, y1: the coil bay behind the guard bars
GEN_FAN = (88, 50, 24)                       # centre x, centre y, radius of the turbine face
GEN_STACKS = (34, 142)                       # exhaust stack centre x
GEN_BARS = range(52, 128, 11)                # guard bar x positions


def hot_iron(v):
    """The hot-iron ramp at intensity v (0-1, array): the kit ink, interpolated."""
    r = kit.MATERIALS["hot_iron"]["ramp"]
    pos = np.clip(v, 0, 1) * (len(r) - 1)
    lo = np.floor(pos).astype(int); hi = np.minimum(lo + 1, len(r) - 1); f = (pos - lo)[..., None]
    return r[lo] * (1 - f) + r[hi] * f


def generator_body():
    """The static steel: skid, housing, coil bay with guard bars, fin stacks, turbine housing, exhaust stacks,
    control plate."""
    x0, y0, x1, y1 = GEN_BAY
    fx, fy, fr = GEN_FAN
    skid = [slab([(12, 198), (164, 198), (168, 215), (8, 215)], "darkmetal", bevel=4, slope=0.9),
            slab(chamfer_rect(14, 206, 162, 209, 1), "rad", bevel=1.2, slope=0.9, op="add", depth=1.0, glow=1.0)]
    for fxp in (16, 160):
        skid.append(slab(chamfer_rect(fxp - 7, 210, fxp + 7, 215, 1), "gunmetal", bevel=2, slope=0.9))
    fins = []
    for side_x0, side_x1 in ((6, 24), (152, 170)):
        fins.append(slab(chamfer_rect(side_x0, 82, side_x1, 198, 2), "darkmetal", bevel=2.5, slope=0.9))
        for fy_ in range(86, 196, 6):
            fins.append(slab([(side_x0 - 1, fy_), (side_x1 + 1, fy_), (side_x1 + 1, fy_ + 3), (side_x0 - 1, fy_ + 3)],
                             "gunmetal", bevel=1.2, slope=0.9))
    housing = [
        slab(chamfer_rect(22, 70, 154, 200, 6), "gunmetal", bevel=6, slope=0.9),
        slab(chamfer_rect(x0 - 5, y0 - 5, x1 + 5, y1 + 5, 3), "darkmetal", bevel=3, slope=0.9, op="add", depth=1.2),
        slab(chamfer_rect(x0, y0, x1, y1, 2), "darkmetal", bevel=2, slope=0.9, op="carve"),
        slab(chamfer_rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 2), "pupil", bevel=1.0, slope=0.8, op="add", depth=0.4),
        slab(chamfer_rect(56, 182, 120, 196, 2), "livery_dk", bevel=2, slope=0.9, op="add", depth=1.2),   # control plate
    ]
    for rx in (28, 148):
        for ry in (76, 194):
            housing.append(blob(rx, ry, 1.8, 1.8, "darkmetal", op="add"))
    bars = [limb(bx, y0 - 2, bx, y1 + 2, 1.8, 1.8, "gunmetal") for bx in GEN_BARS]
    bars += [limb(x0 - 2, (y0 + y1) / 2, x1 + 2, (y0 + y1) / 2, 1.6, 1.6, "gunmetal")]
    turbine = [
        slab(chamfer_rect(48, 22, 128, 78, 8), "gunmetal", bevel=5, slope=0.9),
        blob(fx, fy, fr + 4, fr + 4, "darkmetal", depth=3),
        blob(fx, fy, fr, fr, "pupil", op="carve", depth=2),
        blob(fx, fy, 5, 5, "gunmetal", depth=3),                                       # hub
    ]
    stacks = []
    for sx in GEN_STACKS:
        stacks += [limb(sx, 12, sx, 72, 7, 7, "darkmetal"),
                   slab(chamfer_rect(sx - 9, 8, sx + 9, 14, 2), "gunmetal", bevel=2, slope=0.9),
                   slab(chamfer_rect(sx - 9, 40, sx + 9, 45, 1), "gunmetal", bevel=1.5, slope=0.9)]
    return [skid, fins, stacks, housing, turbine, bars]


def generator_frame(body, glow, k, steel_alpha, yy, xx):
    t = 2 * math.pi * k / GEN_FRAMES
    x0, y0, x1, y1 = GEN_BAY
    open_bay = ((xx >= x0 + 2) & (xx < x1 - 2) & (yy >= y0 + 2) & (yy < y1 - 2)).astype(float)
    behind = open_bay * (1 - steel_alpha)                        # only where no guard bar covers the bay
    # the induction windings: horizontal bands, hottest in a band that rolls up the bay once per loop
    band = np.sin((yy - y0) * 0.9) * 0.5 + 0.5                                         # individual windings
    roll = ((y1 - yy) / (y1 - y0) - k / GEN_FRAMES) % 1.0
    wave = np.exp(-((roll - 0.5) / 0.18) ** 2)
    heat = np.clip(0.30 + 0.35 * band + 0.45 * wave + 0.06 * math.sin(t * 2), 0, 1)
    edge = np.clip(np.minimum(xx - x0, x1 - xx) / 14.0, 0, 1)                           # cooler at the sides
    v = heat * (0.45 + 0.55 * edge)
    col = hot_iron(v)
    paint(body, glow, behind * 0.98, (0, 0, 0), glow_too=False)
    m = behind[..., None]
    body[..., :3] = body[..., :3] * (1 - m) + col * m
    ga = glow[..., 3:] / 255.0
    glow[..., :3] = glow[..., :3] * (1 - m) + col * m
    glow[..., 3:] = np.maximum(ga, behind[..., None]) * 255
    # the turbine: a hot-iron glow behind four dark blades turning a quarter turn per loop (seamless)
    fx, fy, fr = GEN_FAN
    r = np.hypot(xx - fx, yy - fy); ang = np.arctan2(yy - fy, xx - fx)
    disc = ((r < fr - 1) & (r > 5)).astype(float)
    blades = (np.cos(4 * (ang - (math.pi / 2) * k / GEN_FRAMES) + r * 0.08) > 0.35).astype(float) * disc
    disc_heat = hot_iron(0.55 + 0.25 * np.clip(1 - r / fr, 0, 1) + 0.05 * math.sin(t))
    hot = disc * (1 - blades)
    hm = hot[..., None]
    body[..., :3] = body[..., :3] * (1 - hm) + disc_heat * hm
    glow[..., :3] = glow[..., :3] * (1 - hm) + disc_heat * hm
    glow[..., 3] = np.maximum(glow[..., 3], hot * 235)
    paint(body, glow, blades, (22, 26, 32), glow_too=False)
    glow[..., 3] *= (1 - blades)
    # the exhaust lips glow, and embers drift up from them and fade (each ember one whole climb per loop)
    rnd = random.Random(3307)
    for sx in GEN_STACKS:
        lip = np.exp(-(((xx - sx) / 6.0) ** 2) - (((yy - 9) / 2.2) ** 2))
        paint(body, glow, np.clip(lip * 1.3, 0, 1), tuple(hot_iron(np.array(0.85 + 0.1 * math.sin(t + sx)))))
        for e in range(5):
            ex = sx + rnd.uniform(-5, 5); phase = rnd.uniform(0, 1)
            life = (phase + k / GEN_FRAMES) % 1.0
            ey = 6 - life * 34
            if ey < 0.5:
                continue
            ember = np.exp(-((xx - ex - 2 * math.sin(life * 6 + e)) ** 2 + (yy - ey) ** 2) / 0.9) * (1 - life)
            paint(body, glow, np.clip(ember * 1.5, 0, 1), tuple(hot_iron(np.array(0.95 - 0.5 * life))))
    # the control plate: a gauge with a trembling needle, two cyan lamps, a blinking hot-iron warning lamp
    gx, gy = 70, 189
    dial = (np.hypot(xx - gx, yy - gy) < 5).astype(float)
    paint(body, glow, dial, (200, 205, 200), glow_too=False)
    needle_ang = -2.2 + 0.25 * math.sin(t * 3) + 0.08 * math.sin(t * 7)
    for step in range(5):
        px, py = gx + step * math.cos(needle_ang), gy + step * math.sin(needle_ang)
        nd = np.zeros(body.shape[:2]); nd[int(round(py)), int(round(px))] = 1
        paint(body, glow, nd, (230, 70, 20))
    for i, lx in enumerate((88, 98)):
        lamp = np.exp(-(((xx - lx) ** 2 + (yy - 189) ** 2) / 3.0))
        paint(body, glow, np.clip(lamp * 1.5, 0, 1), (90, 240, 255))
    warn_on = (k // 4) % 2 == 0
    lamp = np.exp(-(((xx - 110) ** 2 + (yy - 189) ** 2) / 3.2))
    paint(body, glow, np.clip(lamp * (1.6 if warn_on else 0.5), 0, 1),
          tuple(hot_iron(np.array(0.8 if warn_on else 0.35))), glow_too=warn_on)


def make_generator():
    base = bake(generator_body(), GEN_W, GEN_H, seed=811)
    bars_only = bake([[limb(bx, GEN_BAY[1] - 2, bx, GEN_BAY[3] + 2, 1.8, 1.8, "gunmetal") for bx in GEN_BARS]
                      + [limb(GEN_BAY[0] - 2, (GEN_BAY[1] + GEN_BAY[3]) / 2, GEN_BAY[2] + 2,
                              (GEN_BAY[1] + GEN_BAY[3]) / 2, 1.6, 1.6, "gunmetal")]], GEN_W, GEN_H, seed=811)
    steel_alpha = bars_only[0][..., 3] / 255.0
    yy, xx = np.mgrid[0:GEN_H, 0:GEN_W].astype(float)

    def frame(k):
        body, glow = base[0].copy(), base[1].copy()
        generator_frame(body, glow, k, steel_alpha, yy, xx)
        img = finish(body)
        kit.stencil(img, "GEN-04", 64, 74, (150, 156, 164))
        body = np.asarray(img, float).copy()
        bloom(glow, 4, 1.4, cap=0.5)
        return body, glow

    return Prop("generator", GEN_W, GEN_H, GEN_FRAMES, GEN_FRAME_SECONDS, frame)


# ---------------------------------------------------------------------------------------------------
# Shared for order 4: the drum, periodic noise, ink ramps
# ---------------------------------------------------------------------------------------------------
DRUM_W, DRUM_H = 112, 168
DRUM_X0, DRUM_X1 = 14, 98                    # the drum's sides
DRUM_TOP, DRUM_BOTTOM = 22, 164              # lid line, foot line
DRUM_CX = (DRUM_X0 + DRUM_X1) / 2


def ink(name, v):
    """Any kit ink's ramp at intensity v (array or scalar 0-1), interpolated."""
    r = kit.MATERIALS[name]["ramp"]
    pos = np.clip(np.asarray(v, float), 0, 1) * (len(r) - 1)
    lo = np.floor(pos).astype(int); hi = np.minimum(lo + 1, len(r) - 1); f = (pos - lo)[..., None]
    return r[lo] * (1 - f) + r[hi] * f


def periodic_noise(xx, yy, cell, period_x, period_y, seed):
    """Smooth value noise that tiles every period_x / period_y CELLS - so a field scrolled by a whole period loops
    seamlessly. Integer hash (kit._hash01), deterministic."""
    u, v = xx / cell, yy / cell
    ix, iy = np.floor(u).astype(int), np.floor(v).astype(int)
    fx, fy = u - ix, v - iy
    sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)

    def h(a, b):
        return kit._hash01(np.mod(a, period_x), np.mod(b, period_y), seed)
    top = h(ix, iy) * (1 - sx) + h(ix + 1, iy) * sx
    bot = h(ix, iy + 1) * (1 - sx) + h(ix + 1, iy + 1) * sx
    return top * (1 - sy) + bot * sy


def drum_bake(paint_mat, rib_mat, extra_front, seed):
    """A steel drum: the body as a tall ellipsoid cropped to the drum rows (pure cylinder shading), then lid, ribs
    and foot rendered over it. -> (body, glow) arrays."""
    rx = (DRUM_X1 - DRUM_X0) / 2
    shell, shell_glow = bake([[blob(DRUM_CX, (DRUM_TOP + DRUM_BOTTOM) / 2, rx, 2000, paint_mat, depth=rx * 0.9)]],
                             DRUM_W, DRUM_H, seed)
    rows = np.zeros((DRUM_H, 1)); rows[DRUM_TOP + 4:DRUM_BOTTOM - 2] = 1
    shell[..., 3] *= rows
    lid = [blob(DRUM_CX, DRUM_TOP + 4, rx, 7, rib_mat, depth=4),
           blob(DRUM_CX, DRUM_TOP + 3, rx - 6, 4.5, "darkmetal", op="carve", depth=2),
           blob(DRUM_CX - 22, DRUM_TOP + 3, 5, 2.5, "gunmetal", depth=2.5)]                   # the bung
    ribs = []
    for ry in (DRUM_TOP + 10, 70, 116, DRUM_BOTTOM - 8):
        ribs.append(limb(DRUM_X0 + 1, ry, DRUM_X1 - 1, ry, 3.4, 3.4, rib_mat, k=0))
    foot = [slab([(DRUM_X0 + 2, DRUM_BOTTOM - 4), (DRUM_X1 - 2, DRUM_BOTTOM - 4), (DRUM_X1 - 4, DRUM_BOTTOM + 3),
                  (DRUM_X0 + 4, DRUM_BOTTOM + 3)], "darkmetal", bevel=2, slope=0.9)]
    front, front_glow = bake([foot, ribs + lid, extra_front], DRUM_W, DRUM_H, seed + 1)
    over(shell, front)
    shell_glow[..., 3] *= (1 - front[..., 3] / 255.0)
    over(shell_glow, front_glow)
    return shell, shell_glow


# ---------------------------------------------------------------------------------------------------
# EXPLOSIVE BARREL ('E') - oxide-red hazard drum; hot-iron relief slots breathe: pressure building
# ---------------------------------------------------------------------------------------------------
BARREL_FRAMES, BARREL_FRAME_SECONDS = 16, 0.1
EXPL_SLOTS = [(26, 92), (42, 94), (56, 95), (70, 94), (86, 92)]


def make_explosive_barrel():
    front = [slab(chamfer_rect(30, 128, 82, 146, 2), "livery_dk", bevel=2, slope=0.9, op="add", depth=1.0)]
    base = drum_bake("paint_oxide", "darkmetal", front, seed=901)
    yy, xx = np.mgrid[0:DRUM_H, 0:DRUM_W].astype(float)

    def frame(k):
        body, glow = base[0].copy(), base[1].copy()
        t = 2 * math.pi * k / BARREL_FRAMES
        breathe = 0.65 + 0.35 * (0.5 + 0.5 * math.sin(t))
        for i, (sx, sy) in enumerate(EXPL_SLOTS):                                      # pressure-relief slots
            slot = np.clip(1 - np.maximum(np.abs(xx - sx) / 3.0, np.abs(yy - sy) / 8.0), 0, 1) ** 0.5
            heat = breathe * (0.85 + 0.15 * math.sin(t * 2 + i))
            col = ink("hot_iron", np.clip(slot * heat, 0, 1))
            m = (slot > 0.05).astype(float)[..., None]
            body[..., :3] = body[..., :3] * (1 - m) + col * m
            glow[..., :3] = glow[..., :3] * (1 - m) + col * m
            glow[..., 3] = np.maximum(glow[..., 3], m[..., 0] * 255)
        seam = np.exp(-((yy - (DRUM_TOP + 10)) / 1.2) ** 2) * ((xx > DRUM_X0 + 10) & (xx < DRUM_X1 - 10))
        paint(body, glow, seam * 0.55 * breathe, tuple(ink("hot_iron", 0.75)))
        lamp_on = (k // 4) % 2 == 0                                                     # warning lamp on the lid
        lamp = np.exp(-(((xx - (DRUM_CX + 20)) ** 2) + (yy - (DRUM_TOP + 2)) ** 2) / 4.0)
        paint(body, glow, np.clip(lamp * (1.6 if lamp_on else 0.4), 0, 1),
              tuple(ink("hot_iron", 0.85 if lamp_on else 0.3)), glow_too=lamp_on)
        img = finish(body)
        kit.stencil(img, "VOLATILE", 30, 133, (200, 196, 186))
        body = np.asarray(img, float).copy()
        bloom(glow, 4, 1.3, cap=0.45)
        return body, glow

    return Prop("explosive_barrel", DRUM_W, DRUM_H, BARREL_FRAMES, BARREL_FRAME_SECONDS, frame)


# ---------------------------------------------------------------------------------------------------
# RADIOACTIVE BARREL ('g') - a lab-grey drum leaking rad-lime light
# ---------------------------------------------------------------------------------------------------
def trefoil_mask(xx, yy, cx, cy, r):
    """The radiation trefoil: three 60-degree blades and a hub."""
    d = np.hypot(xx - cx, yy - cy); a = np.arctan2(yy - cy, xx - cx)
    blades = (np.cos(3 * (a + math.pi / 2)) > 0.5) & (d > r * 0.28) & (d < r)
    hub = d < r * 0.18
    return (blades | hub).astype(float)


def make_radioactive_barrel():
    band = [slab(chamfer_rect(DRUM_X0 + 1, 82, DRUM_X1 - 1, 110, 1), "rad", bevel=1.5, slope=0.9, glow=0.6)]
    base = drum_bake("livery", "gunmetal", band, seed=905)
    yy, xx = np.mgrid[0:DRUM_H, 0:DRUM_W].astype(float)
    tref = trefoil_mask(xx, yy, DRUM_CX, 96, 10)

    def frame(k):
        body, glow = base[0].copy(), base[1].copy()
        t = 2 * math.pi * k / BARREL_FRAMES
        pulse = 0.75 + 0.25 * math.sin(t)
        paint(body, glow, tref, (20, 26, 16), glow_too=False)
        glow[..., 3] *= (1 - tref)
        # glowing cracks at a dented seam just under the top rib
        crack = np.zeros((DRUM_H, DRUM_W))
        for (x0, y0, x1, y1) in ((60, 36, 66, 48), (66, 48, 63, 58), (66, 48, 74, 54)):
            n = 12
            for s_ in range(n + 1):
                px = x0 + (x1 - x0) * s_ / n; py = y0 + (y1 - y0) * s_ / n
                crack = np.maximum(crack, np.exp(-((xx - px) ** 2 + (yy - py) ** 2) / 0.9))
        paint(body, glow, np.clip(crack * 1.4 * pulse, 0, 1), tuple(ink("rad", 0.8)))
        # a glowing drip running down the flank once per loop, into a puddle at the foot
        life = k / BARREL_FRAMES
        dy = 58 + life * (DRUM_BOTTOM - 58)
        trail = np.exp(-((xx - 66) / 1.2) ** 2) * ((yy > 58) & (yy < dy)) * 0.55
        drop = np.exp(-((xx - 66) ** 2 / 2.0 + (yy - dy) ** 2 / 4.0))
        paint(body, glow, np.clip(trail + drop * 1.3, 0, 1), tuple(ink("rad", 0.85)))
        puddle = np.exp(-(((xx - 70) / 20) ** 2) - (((yy - (DRUM_BOTTOM + 1)) / 2.2) ** 2))
        paint(body, glow, np.clip(puddle * 1.2 * pulse, 0, 1), tuple(ink("rad", 0.7)))
        img = finish(body)
        kit.stencil(img, "RAD-07", 44, 124, (70, 74, 82))
        body = np.asarray(img, float).copy()
        bloom(glow, 4, 1.4, cap=0.45)
        return body, glow

    return Prop("radioactive_barrel", DRUM_W, DRUM_H, BARREL_FRAMES, BARREL_FRAME_SECONDS, frame)


# ---------------------------------------------------------------------------------------------------
# FIRE ('i') - burning debris with flames from a seamless looping noise field (hot-iron ramp)
# ---------------------------------------------------------------------------------------------------
FIRE_W, FIRE_H = 160, 72
FIRE_FRAMES, FIRE_FRAME_SECONDS = 16, 1.0 / 12


def make_fire():
    rnd = random.Random(1201)
    debris = []
    for i in range(9):
        x = 14 + i * 16 + rnd.uniform(-4, 4)
        debris.append(blob(x, 66 + rnd.uniform(-2, 2), rnd.uniform(9, 14), rnd.uniform(4, 6), "basalt",
                           angle=rnd.uniform(-0.3, 0.3)))
    bed, _ = bake([debris], FIRE_W, FIRE_H, seed=1203)
    yy, xx = np.mgrid[0:FIRE_H, 0:FIRE_W].astype(float)
    cell = 9.0
    period_y = 8                                        # the field tiles every 8 cells = 72 px: one scroll per loop
    bed_a = bed[..., 3] / 255.0
    cracks = periodic_noise(xx, yy, 4.0, 64, 64, 1207)

    def frame(k):
        scroll = (k / FIRE_FRAMES) * period_y * cell
        # tongues: the field is stretched vertically (sampled at half height) so flames read tall and licking
        n1 = periodic_noise(xx, (yy + scroll) * 0.5, cell * 0.5, 64, period_y, 1211)
        n2 = periodic_noise(xx * 1.7, (yy + scroll) * 0.85, cell * 0.85, 64, period_y, 1213)
        rise = np.clip((FIRE_H - yy) / FIRE_H, 0, 1)                                    # 0 at the floor, 1 at top
        columns = 0.5 + 0.5 * np.sin(xx * 0.11 + 0.7 * np.sin(xx * 0.037))
        flame = (0.65 * n1 + 0.35 * n2) * (0.70 + 0.45 * columns) + 0.50 - 1.30 * rise ** 0.85
        flame = np.clip(flame, 0, 1)
        alpha = np.clip((flame - 0.12) / 0.10, 0, 1)
        col = ink("hot_iron", np.clip(flame * 1.25, 0, 1))
        body = np.zeros((FIRE_H, FIRE_W, 4)); body[..., :3] = col; body[..., 3] = alpha * 255
        glow = body.copy()
        # the debris bed sits in front of the flame roots, its cracks glowing
        bedc = bed.copy()
        glowing = (cracks > 0.62) * bed_a
        bedc[..., :3] = bedc[..., :3] * (1 - glowing[..., None]) + ink("hot_iron", 0.7) * glowing[..., None]
        over(body, bedc)
        glow[..., 3] *= (1 - bed_a * (1 - glowing))
        gc = np.zeros_like(glow); gc[..., :3] = ink("hot_iron", 0.7); gc[..., 3] = glowing * 255
        over(glow, gc)
        # sparks lifting (each a whole climb per loop)
        rnd2 = random.Random(1217)
        for e in range(10):
            ex = rnd2.uniform(10, FIRE_W - 10); ph = rnd2.uniform(0, 1)
            life = (ph + k / FIRE_FRAMES) % 1.0
            ey = 58 - life * 56
            sp = np.exp(-((xx - ex - 3 * math.sin(life * 7 + e)) ** 2 + (yy - ey) ** 2) / 0.8) * (1 - life)
            paint(body, glow, np.clip(sp * 1.6, 0, 1), tuple(ink("hot_iron", 0.95)))
        bloom(glow, 3, 1.2, cap=0.4)
        return body, glow

    return Prop("fire", FIRE_W, FIRE_H, FIRE_FRAMES, FIRE_FRAME_SECONDS, frame)


# ---------------------------------------------------------------------------------------------------
# TOXIC POOL ('q') - a low glowing toxin pool, crusted rim, bubbles, vapour
# ---------------------------------------------------------------------------------------------------
TOXIC_W, TOXIC_H = 160, 48
TOXIC_FRAMES, TOXIC_FRAME_SECONDS = 16, 0.1


def make_toxic():
    yy, xx = np.mgrid[0:TOXIC_H, 0:TOXIC_W].astype(float)
    cx, cy, rx, ry = 80, 39, 74, 8.5
    q = np.sqrt(((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2)
    edge_noise = periodic_noise(xx, yy, 6.0, 64, 64, 1301)
    pool = (q < 0.88 + 0.12 * edge_noise).astype(float)
    crust = ((q < 1.0 + 0.12 * edge_noise) & (pool < 0.5)).astype(float)

    def frame(k):
        t = 2 * math.pi * k / TOXIC_FRAMES
        phase = k / TOXIC_FRAMES * 4 * 6.0                                               # caustics drift a whole period
        ripple = periodic_noise(xx + phase, yy * 2.2, 6.0, 32, 64, 1303)
        v = np.clip(0.45 + 0.35 * ripple + 0.25 * (1 - q) + 0.05 * math.sin(t), 0, 1)
        col = ink("toxin", v * 0.95)
        body = np.zeros((TOXIC_H, TOXIC_W, 4)); body[..., :3] = col; body[..., 3] = pool * 255
        glow = body.copy()
        paint(body, glow, crust, (24, 30, 20), glow_too=False)
        rnd = random.Random(1307)
        for b in range(7):                                                                 # bubbles swell and pop
            bx = rnd.uniform(cx - rx * 0.7, cx + rx * 0.7); by = cy + rnd.uniform(-3, 3); ph = rnd.uniform(0, 1)
            life = (ph + k / TOXIC_FRAMES) % 1.0
            r = 1 + 3.5 * life
            if life > 0.85:
                continue                                                                   # popped
            ring = np.clip(1 - np.abs(np.hypot(xx - bx, (yy - by) * 1.3) - r) / 0.9, 0, 1)
            paint(body, glow, ring, tuple(ink("toxin", 0.97)))
        for w in range(4):                                                                 # vapour lifting
            wx = 30 + w * 34; life = ((w * 0.27) + k / TOXIC_FRAMES) % 1.0
            wy = 34 - life * 30
            wisp = np.exp(-(((xx - wx - 4 * math.sin(life * 5 + w)) / 5) ** 2) - (((yy - wy) / 3) ** 2)) * (1 - life)
            paint(body, glow, np.clip(wisp * 0.45, 0, 1), tuple(ink("toxin", 0.75)))
        bloom(glow, 3, 1.2, cap=0.4)
        return body, glow

    return Prop("toxic", TOXIC_W, TOXIC_H, TOXIC_FRAMES, TOXIC_FRAME_SECONDS, frame)


# ---------------------------------------------------------------------------------------------------
# SECURITY CAMERA ('#') - the Organization's eye, HANGING FROM THE CEILING (order 5): the first ceiling-mounted
# prop. Full-height canvas (R1): the top row is the ceiling line, the camera is painted in the top rows and
# everything below it is fully transparent (so it casts no floor shadow and does not occlude below itself).
# ---------------------------------------------------------------------------------------------------
CAM_W, CAM_H = 128, 256
CAM_FRAMES, CAM_FRAME_SECONDS = 48, 0.1
CAM_CX = 64
CAM_PIVOT_Y = 40                              # the yoke's pivot under the drop arm
CAM_HEAD_Y = 58                               # the housing's centre line


def camera_mount():
    """The static steel: a riveted ceiling plate flush with the top row, a drop arm with a clamp collar."""
    plate = [slab(chamfer_rect(38, -3, 90, 8, 2), "gunmetal", bevel=3, slope=0.9)]
    for rx in (43, 85):
        plate.append(blob(rx, 4, 1.6, 1.6, "darkmetal", op="add"))
    arm = [limb(CAM_CX, 6, CAM_CX, CAM_PIVOT_Y - 4, 3.6, 3.6, "darkmetal"),
           slab(chamfer_rect(CAM_CX - 7, 18, CAM_CX + 7, 26, 2), "gunmetal", bevel=2, slope=0.9),
           limb(CAM_CX - 7, 22, CAM_CX + 7, 22, 0.8, 0.8, "darkmetal", op="add")]
    return [plate, arm]


def camera_pose(yaw):
    """The head at pan pose yaw (-1 = looking left, 1 = looking right): geometry shared by the render and the
    per-frame paint. Sideways the housing shows its length; facing the viewer it foreshortens to its front."""
    side = 1.0 if yaw >= 0 else -1.0
    turn = abs(yaw)
    length = 24 + 34 * turn
    cx = CAM_CX + yaw * 7
    drop = 7 * turn                                     # the lens end tips down: it watches the floor
    x_back, x_front = cx - side * length / 2, cx + side * length / 2
    lens_r = 9.5 * (1 - 0.55 * turn)
    lens_x = cx + yaw * (length / 2 - 3)
    lens_y = CAM_HEAD_Y + 2 + drop
    return dict(side=side, turn=turn, length=length, cx=cx, drop=drop, x_back=x_back, x_front=x_front,
                lens_r=lens_r, lens_x=lens_x, lens_y=lens_y)


def camera_head(yaw):
    p = camera_pose(yaw)
    side, turn, drop = p["side"], p["turn"], p["drop"]
    xb, xf, cx = p["x_back"], p["x_front"], p["cx"]
    top, bottom = CAM_HEAD_Y - 11, CAM_HEAD_Y + 11
    # the cable loop: out of the housing's back, sagging, then up into the ceiling behind the plate
    cable_x = xb - side * 2 if turn > 0.25 else CAM_CX + 16
    cable = chain([(cable_x, CAM_HEAD_Y - 2), (cable_x - side * 10, CAM_HEAD_Y + 6), (CAM_CX + 24, 30),
                   (CAM_CX + 20, 12), (CAM_CX + 18, 2)], 2.2, 2.0, "rubber")
    yoke_w = 3 + 9 * (1 - turn)                          # the U-bracket turns with the head
    yoke = [limb(CAM_CX - yoke_w, CAM_PIVOT_Y - 3, CAM_CX + yoke_w, CAM_PIVOT_Y - 3, 2.4, 2.4, "gunmetal"),
            limb(CAM_CX - yoke_w, CAM_PIVOT_Y - 3, CAM_CX - yoke_w, top + 3, 2.2, 2.2, "gunmetal"),
            limb(CAM_CX + yoke_w, CAM_PIVOT_Y - 3, CAM_CX + yoke_w, top + 3, 2.2, 2.2, "gunmetal"),
            blob(CAM_CX, CAM_PIVOT_Y - 4, 4, 4, "darkmetal", depth=3)]
    lo, hi = (xb, xf) if side > 0 else (xf, xb)
    dl = 0 if side > 0 else drop                          # the end that tips down is the lens end
    dr = drop if side > 0 else 0
    housing = [slab([(lo, top + dl), (hi, top + dr), (hi, bottom + dr), (lo, bottom + dl)], "livery_dk",
                    bevel=5, slope=0.9),
               slab([(lo + 2, bottom + dl - 5), (hi - 2, bottom + dr - 5), (hi - 2, bottom + dr - 3),
                     (lo + 2, bottom + dl - 3)], "darkmetal", bevel=1, slope=0.9, op="add", depth=0.8)]
    # the sun hood over the lens: a lip across the top of the front, reaching past it when seen side-on
    hood_len = 10 + 8 * turn
    h0, h1 = sorted((xf - side * hood_len, xf + side * 5 * turn))
    h0, h1 = (min(h0, cx - 13), max(h1, cx + 13)) if turn < 0.35 else (h0, h1)
    hood = [slab([(h0, top + drop - 3), (h1, top + drop - 3), (h1, top + drop + 3), (h0, top + drop + 3)],
                 "gunmetal", bevel=2, slope=0.9)]
    lx, ly, lr = p["lens_x"], p["lens_y"], p["lens_r"]
    lens = [blob(lx, ly, lr + 2.5, 11.5, "gunmetal", depth=4),
            blob(lx, ly, lr, 9.0, "pupil", op="carve", depth=2),
            blob(lx, ly, lr * 0.8, 7.2, "pupil", op="add", depth=0.5)]
    rec_x = cx - yaw * (p["length"] * 0.28) if turn > 0.2 else cx + 9
    rec = [blob(rec_x, top + (dl + dr) / 2 + 6, 2.6, 2.6, "darkmetal", op="add", depth=1.0)]
    return [[*cable], yoke, housing + rec, hood, lens], p


def make_camera():
    mount = bake(camera_mount(), CAM_W, CAM_H, seed=1301)
    yy, xx = np.mgrid[0:CAM_H, 0:CAM_W].astype(float)
    heads = {}

    def head(yaw):
        key = round(yaw, 4)
        if key not in heads:
            layers, pose = camera_head(yaw)
            heads[key] = (bake(layers, CAM_W, CAM_H, seed=1303), pose)
        return heads[key]

    def frame(k):
        t = 2 * math.pi * k / CAM_FRAMES
        yaw = math.sin(t)                                                       # seamless: one sweep per loop
        (hb, hg), p = head(yaw)
        body, glow = mount[0].copy(), mount[1].copy()
        over(body, hb)
        glow[..., 3] *= (1 - hb[..., 3] / 255.0)
        over(glow, hg)
        lx, ly, lr = p["lens_x"], p["lens_y"], p["lens_r"]
        # the lens: a neon cyan iris ring in the dark glass, a white glint
        ex, ey = (xx - lx) / max(lr * 0.8, 1e-3), (yy - ly) / 7.2
        rr = np.hypot(ex, ey)
        ring = np.exp(-((rr - 0.62) / 0.13) ** 2) * (rr < 1)
        paint(body, glow, np.clip(ring * 1.2, 0, 1), tuple(ink("neon_cyan", 0.62)))
        core = np.exp(-(rr / 0.22) ** 2)
        paint(body, glow, np.clip(core * 0.8, 0, 1), tuple(ink("neon_cyan", 0.85)))
        glint = np.exp(-((xx - (lx - lr * 0.35)) ** 2 + (yy - (ly - 3)) ** 2) / 1.1)
        paint(body, glow, np.clip(glint * 1.4, 0, 1), (250, 252, 255))
        # the REC lamp: hot iron, blinking - on for 6 frames, off for 6
        rec_x = p["cx"] - yaw * (p["length"] * 0.28) if p["turn"] > 0.2 else p["cx"] + 9
        rec_y = CAM_HEAD_Y - 11 + p["drop"] / 2 + 6
        rec_on = (k // 6) % 2 == 0
        lamp = np.exp(-((xx - rec_x) ** 2 + (yy - rec_y) ** 2) / 2.2)
        paint(body, glow, np.clip(lamp * (1.7 if rec_on else 0.6), 0, 1),
              tuple(ink("hot_iron", 0.85 if rec_on else 0.3)), glow_too=rec_on)
        img = finish(body)
        kit.stencil(img, "SEC-02", 52, 1, (150, 156, 164))
        body = np.asarray(img, float).copy()
        bloom(glow, 3, 1.3, cap=0.5)
        # the SCAN: a faint cyan cone fanning down from the lens, sweeping with the pan (glow only, low alpha)
        aim = math.pi / 2 - yaw * 0.32                                          # straight down, swung by the pan
        ang = np.arctan2(yy - ly, xx - lx)
        dist = np.hypot(xx - lx, yy - ly)
        off = np.abs((ang - aim + math.pi) % (2 * math.pi) - math.pi)
        cone = np.clip(1 - off / 0.30, 0, 1) ** 1.5 * np.clip((dist - lr) / 10, 0, 1) * np.clip(1 - dist / 120, 0, 1)
        cone *= np.clip(np.minimum(xx, CAM_W - 1 - xx) / 14.0, 0, 1)                # never cut by the canvas edge
        cone *= 0.22 * (1 - body[..., 3] / 255.0)
        col = ink("neon_cyan", 0.55)
        ga = glow[..., 3] / 255.0
        add = cone > ga
        glow[add, :3] = col
        glow[add, 3] = cone[add] * 255
        return body, glow

    return Prop("camera", CAM_W, CAM_H, CAM_FRAMES, CAM_FRAME_SECONDS, frame)


PROPS = {"terminal": make_terminal, "bio_pod": make_pod, "portal": make_portal, "ai_core": make_ai_core, "generator": make_generator,
         "explosive_barrel": make_explosive_barrel, "radioactive_barrel": make_radioactive_barrel,
         "fire": make_fire, "toxic": make_toxic, "camera": make_camera}


# ---------------------------------------------------------------------------------------------------
# The bake: frame grid, manifest, previews
# ---------------------------------------------------------------------------------------------------
def write_prop(prop, out_dir, preview_dir=None, log=print):
    columns = max(1, min(prop.frames, SHEET_LIMIT // prop.width))
    rows = (prop.frames + columns - 1) // columns
    if rows * prop.height > SHEET_LIMIT:
        raise ValueError(f"{prop.id}: {prop.frames} frames do not fit a {SHEET_LIMIT} sheet")
    body_sheet = Image.new("RGBA", (columns * prop.width, rows * prop.height), (0, 0, 0, 0))
    glow_sheet = Image.new("RGBA", body_sheet.size, (0, 0, 0, 0))
    frames = []
    for k in range(prop.frames):
        body, glow = prop.frame(k)
        bi, gi = finish(body), finish(glow)
        x, y = (k % columns) * prop.width, (k // columns) * prop.height
        body_sheet.paste(bi, (x, y)); glow_sheet.paste(gi, (x, y))
        frames.append((bi, gi))
    os.makedirs(out_dir, exist_ok=True)
    body_sheet.save(os.path.join(out_dir, f"{prop.id}.prop.png"), optimize=True)
    glow_sheet.save(os.path.join(out_dir, f"{prop.id}.glow.png"), optimize=True)
    manifest = {"version": MANIFEST_VERSION, "id": prop.id, "frameWidth": prop.width, "frameHeight": prop.height,
                "frameCount": prop.frames, "columns": columns, "frameSeconds": round(prop.frame_seconds, 4)}
    with open(os.path.join(out_dir, f"{prop.id}.prop.json"), "w") as fh:
        fh.write(json.dumps(manifest, indent=2) + "\n")
    log(f"{prop.id}: {prop.frames} frames {prop.width}x{prop.height}, sheet {body_sheet.size[0]}x{body_sheet.size[1]}")
    if preview_dir:
        write_previews(prop, frames, preview_dir)


def composite(body, glow, background=kit.BACKGROUND, glow_alpha=1.0):
    """A preview frame as the game draws it at full light: body over the near-black, glow over that."""
    bg = Image.new("RGBA", body.size, background)
    bg.alpha_composite(body)
    g = np.asarray(glow, float).copy(); g[..., 3] *= glow_alpha
    bg.alpha_composite(Image.fromarray(g.astype(np.uint8), "RGBA"))
    return bg


def write_previews(prop, frames, preview_dir, scale=3):
    os.makedirs(preview_dir, exist_ok=True)
    shots = [composite(b, g).resize((prop.width * scale, prop.height * scale), Image.NEAREST) for b, g in frames]
    shots[0].save(os.path.join(preview_dir, f"{prop.id}.gif"), save_all=True, append_images=shots[1:],
                  duration=int(prop.frame_seconds * 1000), loop=0, disposal=2)
    shots[0].convert("RGB").save(os.path.join(preview_dir, f"{prop.id}.png"))


def contact_sheet(preview_dir, ids, out, scale=2):
    ims = [Image.open(os.path.join(preview_dir, f"{i}.png")) for i in ids]
    ims = [im.resize((im.width * scale // 3, im.height * scale // 3), Image.NEAREST) for im in ims]
    w = sum(im.width + 24 for im in ims) + 24; h = max(im.height for im in ims) + 48
    sheet = Image.new("RGB", (w, h), kit.BACKGROUND[:3]); x = 24
    for im in ims:
        sheet.paste(im, (x, h - 24 - im.height)); x += im.width + 24
    sheet.save(out)


def main(argv):
    if not argv:
        print(__doc__); return 1
    out_dir = argv[0]; preview_dir = None; names = []
    i = 1
    while i < len(argv):
        if argv[i] == "--preview":
            preview_dir = argv[i + 1]; i += 2
        else:
            names.append(argv[i]); i += 1
    names = names or list(PROPS)
    for name in names:
        write_prop(PROPS[name](), out_dir, preview_dir)
    if preview_dir:
        contact_sheet(preview_dir, names, os.path.join(preview_dir, "sheet.png"))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
