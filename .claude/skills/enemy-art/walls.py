"""The generated WALL builder (generated-wall-art series; THE METHOD: docs/wall-art-creation-guide.txt).

Usage:  python3 walls.py OUT_DIR [--preview DIR] [region ...]
  default    bake every face of every region named (default: all): <region>.walls.png (the atlas: every
             wall, door and column face + mips 128 and 64), <region>.glow.png (emissive pixels only, same
             packing, plus the extra glow frames of animated faces), <region>.walls.json (the manifest the
             game reads - guide section 12), <region>.floor.png and <region>.ceiling.png (64x64 surfaces)
  --preview  also write the owner previews into DIR (never committed): a contact sheet per group at 2x,
             the hall strip, one strip per room family, the 6-copy BASE strips, each focal wall alone between
             bases, the doors at 1x, the surfaces tiled 3x3, a GIF per animated face, and a seam report

A wall is one TILE FACE, 256x256, row 0 = the ceiling line, row 255 = the floor line, made with the enemy kit
(kit.py: the same light rig, materials, cleanup and outline). It TILES horizontally with every wall of its
region set: every shape is stamped at x - 256, x and x + 256 on a 768-wide canvas and the middle face is
cropped (kit.tile_copies; guide section 4). Surfaces do the same on a 3x3 canvas.

A REGION is one row of REGIONS: its letter, its trim lines, its breach base/step, its walls (group, slot,
variant, builder), doors, columns and the two surface builders. A region's builders live in
<region>_walls.py (outer_facility_walls.py, ...), the way a creature's rig lives in <name>_rig.py.
Every builder is `builder(glow) -> Face`: `glow` scales the face's emissive light (1 = rest), so an
animated face is baked once per glow frame and only that frame's glow image is kept.
"""
import json
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw

if __name__ == "__main__":
    sys.modules["walls"] = sys.modules["__main__"]   # the region modules import this file as `walls`
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kit  # noqa: E402
from kit import blob, limb, slab, chain, riveted_plate, chamfer_rect  # noqa: E402

FACE = 256                 # a wall face is FACE x FACE
SURFACE = 64               # a floor / ceiling is SURFACE x SURFACE, tiling in both axes
MIPS = 3                   # 256, 128, 64 (guide 12: past ~8 tiles the fog covers the rest)
PADDING = 2                # duplicated edge texels around every atlas rect (Nearest never bleeds)
ATLAS_LIMIT = 2048
MANIFEST_VERSION = 1
FOCAL_MARGIN = 8           # a focal shape keeps >= this many px from the tile edge (guide section 4)
GLOW_FRAME_FLOOR = 0.06    # a glow frame "off" is baked just above kit.GLOW_DEAD, so the lamp's dark rim
                           # is in that frame's glow image and covers the lit lamp of the base face
GROUPS = ("HALL", "MEDICAL", "SECURITY", "CONTAINMENT", "MACHINERY", "RESEARCH", "STORAGE", "SIGNATURE")
HALL_SLOTS = ("BASE", "STRUCTURE", "LIGHT", "SERVICE", "VENT", "MARKER", "BREACH")
ROOM_SLOTS = ("BASE", "ACCENT")


class Face:
    """What a builder returns: layers (back to front, in FACE coordinates 0..255), stencils painted after
    the render [(text, x, y, colour)] or scaled [(text, x, y, colour, scale)], and the FOCAL shapes the edge
    rule checks (they must stay FOCAL_MARGIN px inside the face)."""

    def __init__(self, layers, stencils=(), focal=()):
        self.layers, self.stencils, self.focal = layers, list(stencils), list(focal)


def surface_backing(mat, bevel=1.0, overlap=6):
    """The backing slab of one SURFACE tile, overlapping its 8 neighbours (see grammar2d)."""
    return grammar2d([slab([(-overlap, -overlap), (SURFACE + overlap, -overlap), (SURFACE + overlap, SURFACE + overlap),
                            (-overlap, SURFACE + overlap)], mat, bevel=bevel, slope=0.5)])[0]


class Entry:
    """One face of a region set: a wall (group/slot/variant), a door or a column. `frames` animates its
    GLOW layer: [(glow level, seconds), ...]; `animate` is "all" / "some" / None (guide section 10)."""

    def __init__(self, id, builder, group=None, slot=None, variant=0, frames=None, animate=None):
        self.id, self.builder, self.group, self.slot, self.variant = id, builder, group, slot, variant
        self.frames, self.animate = frames, animate


def wall(region_letter, group, slot, builder, variant=None, frames=None, animate=None):
    """A wall row with its STABLE id: wall_<region letter>_<group>_<slot>[_<variant>] (guide 12)."""
    suffix = "" if variant is None else f"_{variant}"
    return Entry(f"wall_{region_letter}_{group.lower()}_{slot.lower()}{suffix}", builder, group, slot,
                 variant or 0, frames, animate)


# ---------------------------------------------------------------------------------------------------
# Wall primitives (face coordinates: x 0..255 left to right, y 0 = ceiling line .. 255 = floor line)
# ---------------------------------------------------------------------------------------------------
BRUSHED = np.array([[0.0, 0.0, 0.0], [0.0, 1.0, 0.0], [0.0, 0.0, 1.0]])


def grammar(shapes):
    """Make shapes that CROSS the tile edge (the backing, trim bands, rails) safe to overlap their own copies:
    a hard union (k=0 - a smooth max of two equal heights would raise the overlap by k/4) and grain sampled in
    a BRUSHED frame (it varies with y only), so both copies carry the same grain where they overlap (a frame
    that moves with the copy would blend two different grains there). Lesson 15.14. Never carve or add
    across the edge: overlapping carves and adds SUM."""
    for s in shapes:
        s.k = 0
        s.frame = BRUSHED.copy()
    return shapes


FLAT = np.array([[0.0, 0.0, 0.0], [0.0, 0.0, 0.0], [0.0, 0.0, 1.0]])


def grammar2d(shapes):
    """grammar() for a SURFACE (it tiles in both axes, so no frame that moves with a copy is safe): a hard
    union and a FLAT grain frame (the material's grain is constant on the shape - its surface variety comes
    from the shapes and their bevels instead)."""
    for s in shapes:
        s.k = 0
        s.frame = FLAT.copy()
    return shapes


def backing(mat, bevel=1.0, overlap=12, **kw):
    """The full-face backing slab of ONE face, overlapping its neighbours by `overlap` px so the merge has
    no seam - never one slab spanning the canvas (its grain would not repeat; guide section 4)."""
    return grammar([slab([(-overlap, -4), (FACE + overlap, -4), (FACE + overlap, FACE + 4), (-overlap, FACE + 4)],
                         mat, bevel=bevel, slope=0.5, **kw)])[0]


def band(y0, y1, mat, bevel=3, slope=0.9, overlap=12, **kw):
    """A horizontal band across the whole face and into its neighbours (a cornice, a kick plate): shared
    GRAMMAR, so it may cross the tile edge (guide section 4). Two bands with a gap between them make a joint."""
    return grammar([slab([(-overlap, y0), (FACE + overlap, y0), (FACE + overlap, y1), (-overlap, y1)], mat,
                         bevel=bevel, slope=slope, **kw)])[0]


def rail(y, r, mat, **kw):
    """A horizontal rod across the face and into its neighbours (a bump rail, a cable, a pipe)."""
    return grammar([limb(-20, y, FACE + 20, y, r, r, mat, **kw)])[0]


def panel(x0, y0, x1, y1, mat, chamfer=8, bevel=5, slope=0.8, **kw):
    """A chamfered panel (the UiKit plate shape) as a bevelled slab."""
    return slab(chamfer_rect(x0, y0, x1, y1, chamfer), mat, bevel=bevel, slope=slope, **kw)


def tile_field(x0, y0, x1, y1, size, mat, gap=4, rnd=None, missing=0.0, cracks=0.0, bevel=2.2):
    """A field of square glazed tiles (`size` px pitch, `gap` px grout). With an rnd: a fraction `missing`
    left out (the backing shows) and `cracks` cracked."""
    out = []
    y = y0
    while y + size <= y1 + 0.5:
        x = x0
        while x + size <= x1 + 0.5:
            if rnd is not None and rnd.random() < missing:
                x += size
                continue
            a, b = x + gap / 2, y + gap / 2
            out.append(slab([(a, b), (a + size - gap, b), (a + size - gap, b + size - gap), (a, b + size - gap)], mat,
                            bevel=bevel, slope=1.0, k=0, prio=1))
            if rnd is not None and rnd.random() < cracks:
                cx = a + rnd.uniform(0.2, 0.8) * (size - gap)
                out.append(limb(cx, b + 2, cx + rnd.uniform(-0.3, 0.3) * size, b + size - gap - 2, 0.9, 0.6, mat, op="carve"))
            x += size
        y += size
    return out


def pipe_run(points, r, mat, clamp_mat="darkmetal", clamps=(), clamp_w=None, **kw):
    """A pipe along `points` (a chain of constant radius) with clamp collars (half-height clamp_w) at the given points."""
    out = chain(points, r, r, mat, **kw)
    half = clamp_w or 3
    for x, y in clamps:                      # a collar: a short block standing just proud of the pipe
        out.append(slab([(x - r - 2, y - half), (x + r + 2, y - half), (x + r + 2, y + half), (x - r - 2, y + half)],
                        clamp_mat, bevel=(r + 1.5) / 1.5, slope=1.5, k=0, prio=2))
    return out


def grille(x0, y0, x1, y1, slats, mat="darkmetal", horizontal=True, r=1.6):
    """Slots carved into whatever surface lies under the box: `slats` evenly spaced grooves."""
    out = []
    for n in range(slats):
        t = (n + 0.5) / slats
        if horizontal:
            y = y0 + (y1 - y0) * t
            out.append(limb(x0, y, x1, y, r, r, mat, op="carve"))
        else:
            x = x0 + (x1 - x0) * t
            out.append(limb(x, y0, x, y1, r, r, mat, op="carve"))
    return out


def tear_edge(rnd, y_min, y_max, top=-4, step=(14, 30)):
    """The polygon of a sheet of plating torn along a ragged edge between y_min and y_max (region D)."""
    pts = [(-12, top), (FACE + 12, top), (FACE + 12, y_min)]
    x = FACE + 12
    while x > -12:
        x -= rnd.uniform(*step)
        pts.append((max(x, -12), rnd.uniform(y_min, y_max)))
    return pts


def vein(rnd, x, y, angle, length, r, mat="ichor", n=8, **kw):
    """A wandering vein: a tapering chain that turns a little at every step (raised `add` by default)."""
    pts = [(x, y)]
    for _ in range(n):
        angle += rnd.uniform(-0.45, 0.45)
        x += math.cos(angle) * length / n
        y += math.sin(angle) * length / n
        pts.append((x, y))
    kw.setdefault("op", "add")
    kw.setdefault("k", 3)
    return chain(pts, r, r * 0.4, mat, **kw)


def lock_panel(cx, cy, ink, w=24, h=40, housing="darkmetal", glow=1.0):
    """A keycard LOCK PANEL: a dark housing, a card slot and a lit lamp in the keycard ink (key_red /
    key_yellow / key_blue: emissive, exact colours - guide 11.1). -> shapes (focal)."""
    x0, y0, x1, y1 = cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2
    return [slab(chamfer_rect(x0, y0, x1, y1, 4), housing, bevel=3, slope=0.8, k=0, prio=4),
            slab(chamfer_rect(x0 + 5, y0 + 5, x1 - 5, y0 + h * 0.45, 2), ink, bevel=1.5, slope=1.0, op="add", glow=glow),
            limb(x0 + 6, y1 - 9, x1 - 6, y1 - 9, 1.4, 1.4, housing, op="carve")]


# ---------------------------------------------------------------------------------------------------
# Baking
# ---------------------------------------------------------------------------------------------------
def _bbox_x(shape):
    g = shape.g
    if shape.kind == "ellipse":
        r = max(g["rx"], g["ry"])
        return g["cx"] - r, g["cx"] + r
    if shape.kind == "capsule":
        r = max(g["r1"], g["r2"])
        return min(g["x1"], g["x2"]) - r, max(g["x1"], g["x2"]) + r
    return float(g["pts"][:, 0].min()), float(g["pts"][:, 0].max())


def _check_face(name, face, log):
    """The bake's warnings: a shape reaching more than one face beyond its own breaks the tiling (its copy
    cannot cover it); a FOCAL shape within FOCAL_MARGIN of the edge breaks the EDGE RULE."""
    for layer in face.layers:
        for s in layer:
            x0, x1 = _bbox_x(s)
            if x0 < -FACE + 8 or x1 > 2 * FACE - 8:
                log(f"  WARNING {name}: a shape spans more than one face beyond its own ({x0:.0f}..{x1:.0f})")
    for s in face.focal:
        x0, x1 = _bbox_x(s)
        if x0 < FOCAL_MARGIN or x1 > FACE - FOCAL_MARGIN:
            log(f"  WARNING {name}: a FOCAL shape crosses the edge margin ({x0:.0f}..{x1:.0f}) - edge rule")


def _scaled_stencil(img, text, x, y, colour, scale):
    """kit.stencil's 3x5 glyphs at an integer scale (signage), with the same 70% paint on opaque pixels."""
    px = img.load()
    for ci, ch in enumerate(text):
        if ch == " ":
            continue
        bits = kit.STENCIL_FONT[ch]
        for r in range(5):
            for c in range(3):
                if bits[r * 3 + c] != "1":
                    continue
                for sy in range(scale):
                    for sx in range(scale):
                        qx, qy = x + (ci * 4 + c) * scale + sx, y + r * scale + sy
                        if 0 <= qx < img.width and 0 <= qy < img.height:
                            p = px[qx, qy]
                            px[qx, qy] = (int(p[0] * 0.3 + colour[0] * 0.7), int(p[1] * 0.3 + colour[1] * 0.7),
                                          int(p[2] * 0.3 + colour[2] * 0.7), 255)


def _scale_glow(layers, level):
    """Scale the light of every emissive / translucent shape (a glow frame); a level of 0 is baked as
    GLOW_FRAME_FLOOR so the dead lamp's pixels still belong to the frame."""
    level = max(level, GLOW_FRAME_FLOOR)
    for layer in layers:
        for s in layer:
            m = kit.MATERIALS[s.mat]
            if (m.get("emissive") or "glow" in m) and s.glow > 0:
                s.glow = s.glow * level
    return layers


def bake_face(face, seed, glow=1.0):
    """Render one wall face on the 3-face canvas and crop the middle. -> (RGB image, RGBA glow image)."""
    layers = [kit.tile_copies(layer, FACE, 3, 1) for layer in face.layers]
    if glow != 1.0:
        layers = _scale_glow(layers, glow)
    img, glow_img = kit.render(layers, 3 * FACE, FACE, seed=seed, glow_radius=0, return_glow=True)
    img = img.crop((FACE, 0, 2 * FACE, FACE))
    glow_img = glow_img.crop((FACE, 0, 2 * FACE, FACE))
    for st in face.stencils:
        text, x, y, colour = st[:4]
        if len(st) > 4 and st[4] > 1:
            _scaled_stencil(img, text, int(x), int(y), colour, st[4])
        else:
            kit.stencil(img, text, x, y, colour)
    return img.convert("RGB"), glow_img


def bake_surface(face, seed):
    """Render a floor / ceiling on a 3x3 canvas and crop the middle tile. -> RGB image."""
    layers = [kit.tile_copies(layer, SURFACE, 3, 3) for layer in face.layers]
    img = kit.render(layers, 3 * SURFACE, 3 * SURFACE, seed=seed, glow_radius=0)
    img = img.crop((SURFACE, SURFACE, 2 * SURFACE, 2 * SURFACE))
    for st in face.stencils:
        kit.stencil(img, *st[:4])
    return img.convert("RGB")


def mip_chain(img, glow):
    """Mips by 2x2 box filter: [(rgb, glow)] for 256, 128, 64. A glow texel survives a mip step when at
    least two of its four parents glow; its colour is the mean of the glowing parents (binary alpha kept)."""
    out = [(img, glow)]
    rgb = np.asarray(img, float)
    gl = np.asarray(glow, float)
    for _ in range(MIPS - 1):
        h, w = rgb.shape[0] // 2, rgb.shape[1] // 2
        rgb = rgb.reshape(h, 2, w, 2, 3).mean(axis=(1, 3))
        a = (gl[..., 3] > 0).astype(float).reshape(h, 2, w, 2)
        cols = (gl[..., :3] * (gl[..., 3:] > 0)).reshape(h, 2, w, 2, 3).sum(axis=(1, 3))
        count = a.sum(axis=(1, 3))
        g = np.zeros((h, w, 4))
        keep = count >= 2
        g[keep, :3] = cols[keep] / count[keep][:, None]
        g[keep, 3] = 255
        gl = g
        out.append((Image.fromarray(np.round(rgb).astype(np.uint8), "RGB"),
                    Image.fromarray(np.round(g).astype(np.uint8), "RGBA")))
    return out


# ---------------------------------------------------------------------------------------------------
# Packing (a deterministic skyline, bottom-left) and the atlas writer
# ---------------------------------------------------------------------------------------------------
def skyline_pack(sizes, side):
    """Place (w, h) rects (already including padding) in the given order on a side x side sheet, each at
    the lowest, then left-most, position the skyline allows. -> [(x, y)] or None if one does not fit."""
    sky = [(0, side, 0)]    # (x, width, y) segments
    out = []
    for w, h in sizes:
        best = None
        for i in range(len(sky)):
            x = sky[i][0]
            if x + w > side:
                break
            y, covered, j = 0, 0, i
            while covered < w:
                y = max(y, sky[j][2]); covered = sky[j][0] + sky[j][1] - x; j += 1
            if y + h <= side and (best is None or (y, x) < (best[1], best[0])):
                best = (x, y)
        if best is None:
            return None
        x, y = best
        out.append(best)
        new = []
        for sx, sw, sy in sky:
            end = sx + sw
            if end <= x or sx >= x + w:
                new.append((sx, sw, sy))
            else:
                if sx < x:
                    new.append((sx, x - sx, sy))
                if end > x + w:
                    new.append((x + w, end - x - w, sy))
        new.append((x, w, y + h))
        new.sort()
        merged = []
        for seg in new:
            if merged and merged[-1][2] == seg[2] and merged[-1][0] + merged[-1][1] == seg[0]:
                merged[-1] = (merged[-1][0], merged[-1][1] + seg[1], seg[2])
            else:
                merged.append(seg)
        sky = merged
    return out


def _paste_padded(sheet, im, x, y):
    """Paste im at (x, y) and fill PADDING px around it with its duplicated edge texels."""
    w, h = im.size
    big = Image.new(im.mode, (w + 2 * PADDING, h + 2 * PADDING))
    a = np.asarray(im)
    padded = np.pad(a, ((PADDING, PADDING), (PADDING, PADDING)) + ((0, 0),) * (a.ndim - 2), mode="edge")
    big = Image.fromarray(padded, im.mode)
    sheet.paste(big, (x - PADDING, y - PADDING))


def write_region(name, out_dir, preview_dir=None, log=print):
    region = REGIONS[name]
    seed = region["seed"]
    entries = region["walls"] + region["doors"] + region["columns"]
    baked = []   # per entry: mips [(rgb, glow)], extra glow frames [glow images]
    for e in entries:
        face = e.builder(1.0)
        _check_face(e.id, face, log)
        img, glow = bake_face(face, seed)
        extra = []
        if e.frames:
            for level, _ in e.frames[1:]:
                extra.append(bake_face(e.builder(1.0), seed, glow=level)[1])
        coverage = float((np.asarray(glow)[..., 3] > 0).mean())
        if coverage > 0.06:
            log(f"  WARNING {e.id}: glow covers {coverage:.1%} of the face (budget ~6%)")
        baked.append((mip_chain(img, glow), extra, coverage))
        log(f"  {e.id}: glow {coverage:.2%}" + (f", {len(e.frames)} glow frames" if e.frames else ""))

    # pack: every 256 face and glow frame first, then the 128 mips, then the 64 mips (each group in
    # manifest order) on the smallest power-of-two square that holds them
    items = []   # (kind, entry index, sub)  kind: face / frame / mip
    for i, (mips, extra, _) in enumerate(baked):
        items.append(("face", i, 0))
        items += [("frame", i, k) for k in range(len(extra))]
    for level in range(1, MIPS):
        items += [("mip", i, level) for i in range(len(baked))]
    sizes = [((FACE >> (sub if kind == "mip" else 0)) + 2 * PADDING,) * 2 for kind, _, sub in items]
    side, positions = 256, None
    while side <= ATLAS_LIMIT and positions is None:
        positions = skyline_pack(sizes, side)
        if positions is None:
            side *= 2
    if positions is None:
        raise ValueError(f"{name}: the region set does not fit a {ATLAS_LIMIT}x{ATLAS_LIMIT} atlas")
    atlas = Image.new("RGB", (side, side), (0, 0, 0))
    glow_atlas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    rects = {}
    for (kind, i, sub), (x, y) in zip(items, positions):
        x, y = x + PADDING, y + PADDING
        mips, extra, _ = baked[i]
        if kind == "face":
            _paste_padded(atlas, mips[0][0], x, y)
            _paste_padded(glow_atlas, mips[0][1], x, y)
        elif kind == "frame":
            _paste_padded(atlas, mips[0][0], x, y)        # the atlas stays opaque under a glow-frame slot
            _paste_padded(glow_atlas, extra[sub], x, y)
        else:
            _paste_padded(atlas, mips[sub][0], x, y)
            _paste_padded(glow_atlas, mips[sub][1], x, y)
        rects[(kind, i, sub)] = [x, y]

    def record(i, e):
        mips, extra, coverage = baked[i]
        has_glow = coverage > 0
        glow_rect = rects[("face", i, 0)] if has_glow else None
        rec = {"id": e.id}
        if e.group:
            rec.update(group=e.group, slot=e.slot, variant=e.variant)
        rec.update(rect=rects[("face", i, 0)], mipRects=[rects[("mip", i, lv)] for lv in range(1, MIPS)],
                   glowRect=glow_rect)
        if e.frames:
            rec["glowFrames"] = [{"rect": glow_rect, "duration": e.frames[0][1]}] + \
                [{"rect": rects[("frame", i, k)], "duration": e.frames[k + 1][1]} for k in range(len(extra))]
        else:
            rec["glowFrames"] = None
        rec["animate"] = e.animate
        return rec

    walls_n, doors_n = len(region["walls"]), len(region["doors"])
    records = [record(i, e) for i, e in enumerate(entries)]
    manifest = {
        "version": MANIFEST_VERSION, "region": name, "size": FACE, "mips": MIPS,
        "atlas": f"{name}.walls.png", "glow": f"{name}.glow.png", "atlasSize": [side, side],
        "trim": region["trim"], "breach": region["breach"],
        "walls": records[:walls_n], "doors": records[walls_n:walls_n + doors_n],
        "columns": records[walls_n + doors_n:],
        "surfaces": {"floor": f"{name}.floor.png", "ceiling": f"{name}.ceiling.png"},
    }
    os.makedirs(out_dir, exist_ok=True)
    atlas.save(os.path.join(out_dir, f"{name}.walls.png"), optimize=False)
    glow_atlas.save(os.path.join(out_dir, f"{name}.glow.png"), optimize=False)
    with open(os.path.join(out_dir, f"{name}.walls.json"), "w", newline="\n") as fh:
        fh.write(manifest_json(manifest))
    surfaces = {}
    for kind in ("floor", "ceiling"):
        builder = region["surfaces"][kind]
        if builder is None:
            continue
        surf = bake_surface(builder(), seed)
        surf.save(os.path.join(out_dir, f"{name}.{kind}.png"), optimize=False)
        surfaces[kind] = surf
        log(f"  {name}.{kind}: mean luminance {luminance(surf):.3f}")
    log(f"wrote {name}: {walls_n} walls, {doors_n} doors, {len(entries) - walls_n - doors_n} columns, "
        f"atlas {side}x{side}")
    if preview_dir:
        write_previews(name, entries, baked, surfaces, preview_dir)
    return manifest


def manifest_json(manifest):
    """Readable, diff-friendly JSON: one face per line. Plain JSON - LibGDX's JsonReader reads it."""
    compact = lambda v: json.dumps(v, separators=(",", ":"))
    lists = ("walls", "doors", "columns")
    head = [k for k in manifest if k not in lists + ("surfaces",)]
    lines = ["{"] + [f' "{k}": {compact(manifest[k])},' for k in head]
    for k in lists:
        lines.append(f' "{k}": [')
        rows = manifest[k]
        lines += ["  " + compact(r) + ("," if i < len(rows) - 1 else "") for i, r in enumerate(rows)]
        lines.append(" ],")
    lines.append(f' "surfaces": {compact(manifest["surfaces"])}')
    lines.append("}")
    return "\n".join(lines) + "\n"


def luminance(img):
    a = np.asarray(img.convert("RGB"), float) / 255.0
    return float((a[..., 0] * 0.2126 + a[..., 1] * 0.7152 + a[..., 2] * 0.0722).mean())


# ---------------------------------------------------------------------------------------------------
# Previews (for the owner; never committed)
# ---------------------------------------------------------------------------------------------------
BG = kit.BACKGROUND[:3]
LABEL = (230, 228, 220)
DIM = (140, 150, 160)


def seam_ratio(a, b=None):
    """How visible the join a|b is: the mean colour step across the join over the mean step between
    neighbouring columns inside the faces (1.0 = the join is as smooth as the face itself)."""
    b = a if b is None else b
    pa, pb = np.asarray(a, float), np.asarray(b, float)
    join = np.abs(pa[:, -1] - pb[:, 0]).mean()
    inner = (np.abs(np.diff(pa, axis=1)).mean() + np.abs(np.diff(pb, axis=1)).mean()) / 2
    return join / max(inner, 1e-6)


def strip(images, scale=1, gap=0):
    w = sum(i.width for i in images) * scale + gap * (len(images) - 1)
    h = images[0].height * scale
    out = Image.new("RGB", (w, h), BG)
    x = 0
    for im in images:
        out.paste(im.resize((im.width * scale, im.height * scale), Image.NEAREST), (x, 0))
        x += im.width * scale + gap
    return out


def labelled_rows(rows, scale=1, pad=10, label_h=16):
    """rows = [(title, image)] -> one sheet, each image under its title."""
    w = max(im.width for _, im in rows) + 2 * pad
    h = sum(im.height + label_h + pad for _, im in rows) + pad
    sheet = Image.new("RGB", (w, h), BG)
    d = ImageDraw.Draw(sheet)
    y = pad
    for title, im in rows:
        d.text((pad, y), title, fill=LABEL)
        sheet.paste(im, (pad, y + label_h))
        y += im.height + label_h + pad
    return sheet


def contact_sheet(cells, scale=2, columns=3, pad=12, label_h=16):
    """cells = [(label, image)] -> a labelled grid at `scale`."""
    cw, ch = cells[0][1].width * scale, cells[0][1].height * scale
    rows = (len(cells) + columns - 1) // columns
    sheet = Image.new("RGB", (columns * (cw + pad) + pad, rows * (ch + pad + label_h) + pad), BG)
    d = ImageDraw.Draw(sheet)
    for n, (label, im) in enumerate(cells):
        x, y = pad + (n % columns) * (cw + pad), pad + (n // columns) * (ch + pad + label_h)
        d.text((x, y), label, fill=LABEL)
        sheet.paste(im.resize((cw, ch), Image.NEAREST), (x, y + label_h))
    return sheet


def write_previews(name, entries, baked, surfaces, out):
    os.makedirs(out, exist_ok=True)
    face = {e.id: baked[i][0][0][0] for i, e in enumerate(entries)}
    by = {}
    for e in entries:
        if e.group:
            by.setdefault((e.group, e.slot), []).append(e.id)
    report = []

    # contact sheets per group at 2x
    for group in GROUPS:
        ids = [e.id for e in entries if e.group == group]
        if ids:
            contact_sheet([(i, face[i]) for i in ids]).save(os.path.join(out, f"{name}_{group.lower()}_sheet.png"))
    others = [e.id for e in entries if not e.group]
    if others:
        contact_sheet([(i, face[i]) for i in others], columns=3).save(os.path.join(out, f"{name}_doors_columns_sheet.png"))
        strip([face[i] for i in others], gap=16).save(os.path.join(out, f"{name}_doors_columns_1x.png"))

    def base_cycle(group, n):
        bases = by.get((group, "BASE"), [])
        return [bases[(k * 2 + k // 3) % len(bases)] for k in range(n)] if bases else []

    rows = []
    # 6-copy base strips (each variant alone, then the variants mixed) - the repetition test (guide 8.3 R-1)
    for group in GROUPS:
        bases = by.get((group, "BASE"), [])
        for b in bases:
            im = strip([face[b]] * 6)
            rows.append((f"{b} x6", im))
            report.append(f"{b}: self-seam {seam_ratio(face[b]):.2f}")
        if len(bases) > 1:
            rows.append((f"{group} BASE variants mixed", strip([face[i] for i in base_cycle(group, 6)])))
    if rows:
        labelled_rows(rows).save(os.path.join(out, f"{name}_base_strips.png"))

    # the hall run in a plausible sequence, and each room family's run
    hall_seq = []
    for slot in ("BASE", "BASE", "STRUCTURE", "BASE", "LIGHT", "BASE", "SERVICE", "BASE", "BASE", "MARKER",
                 "BASE", "VENT", "BASE", "STRUCTURE", "BASE", "BREACH", "BASE"):
        ids = by.get(("HALL", slot))
        if ids:
            hall_seq.append(ids[len([s for s in hall_seq if s.startswith(ids[0][:-2])]) % len(ids)] if slot == "BASE" else ids[0])
    runs = []
    if hall_seq:
        runs.append(("HALL run", strip([face[i] for i in hall_seq])))
    for group in GROUPS[1:]:
        accent = by.get((group, "ACCENT"))
        bases = base_cycle(group, 4)
        if bases and accent:
            seq = [bases[0], bases[1], accent[0], bases[2], bases[3]]
            runs.append((f"{group} run (BASE BASE ACCENT BASE BASE)", strip([face[i] for i in seq])))
            for a, b in zip(seq, seq[1:]):
                report.append(f"{group} join {a} | {b}: {seam_ratio(face[a], face[b]):.2f}")
            # the room meeting the hall at a doorway
            hb = by.get(("HALL", "BASE"))
            if hb:
                report.append(f"{group} meets HALL: {seam_ratio(face[hb[0]], face[bases[0]]):.2f} / "
                              f"{seam_ratio(face[bases[0]], face[hb[0]]):.2f}")
    if runs:
        labelled_rows(runs).save(os.path.join(out, f"{name}_runs.png"))
        for a, b in zip(hall_seq, hall_seq[1:]):
            report.append(f"HALL join {a} | {b}: {seam_ratio(face[a], face[b]):.2f}")

    # each focal wall alone between bases (guide 8.3 R-4)
    focal = []
    for (group, slot), ids in by.items():
        if slot != "BASE":
            bases = base_cycle(group, 4)
            for i in ids:
                focal.append((f"{i} alone", strip([face[bases[0]], face[bases[1]], face[i], face[bases[2]], face[bases[3]]])))
    if focal:
        labelled_rows(focal).save(os.path.join(out, f"{name}_focal_alone.png"))

    # surfaces tiled 3x3 at 2x
    for kind, surf in surfaces.items():
        t = Image.new("RGB", (surf.width * 3, surf.height * 3))
        for r in range(3):
            for c in range(3):
                t.paste(surf, (c * surf.width, r * surf.height))
        t.resize((t.width * 3, t.height * 3), Image.NEAREST).save(os.path.join(out, f"{name}_{kind}_3x3.png"))
        report.append(f"{kind}: mean luminance {luminance(surf):.3f}, self-seam x {seam_ratio(surf):.2f}, "
                      f"y {seam_ratio(surf.transpose(Image.Transpose.TRANSPOSE)):.2f}")

    # one GIF per animated face: the base face with each glow frame laid over it (NORMAL blend)
    for i, e in enumerate(entries):
        if not e.frames:
            continue
        mips, extra, _ = baked[i]
        frames, durations = [], []
        for k, (level, seconds) in enumerate(e.frames):
            g = mips[0][1] if k == 0 else extra[k - 1]
            im = mips[0][0].convert("RGBA")
            im.alpha_composite(g)
            frames.append(im.convert("RGB").resize((FACE * 2, FACE * 2), Image.NEAREST))
            durations.append(int(round(seconds * 1000)))
        frames[0].save(os.path.join(out, f"{e.id}_flicker.gif"), save_all=True, append_images=frames[1:],
                       duration=durations, loop=0, disposal=1, optimize=False)
    with open(os.path.join(out, f"{name}_seams.txt"), "w") as fh:
        fh.write("\n".join(report) + "\n")


# ---------------------------------------------------------------------------------------------------
# The regions (one row each; their builders live in <region>_walls.py)
# ---------------------------------------------------------------------------------------------------
import outer_facility_walls as outer_facility  # noqa: E402

REGIONS = {
    "outer_facility": outer_facility.REGION,
}


if __name__ == "__main__":
    args = sys.argv[1:]
    out = args.pop(0)
    preview = None
    if "--preview" in args:
        i = args.index("--preview"); preview = args[i + 1]; del args[i:i + 2]
    only = [a for a in args if not a.startswith("--")] or list(REGIONS)
    for region_name in only:
        write_region(region_name, out, preview_dir=preview)
