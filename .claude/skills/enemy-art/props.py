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


PROPS = {"terminal": make_terminal, "bio_pod": make_pod, "portal": make_portal, "ai_core": make_ai_core}


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
