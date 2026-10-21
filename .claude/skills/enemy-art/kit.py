"""Enemy sprite kit -- sculpted height fields, one shared light rig, rigs and clips. See SKILL.md and
docs/sprite-art-creation-guide.txt (sections 3 and 7).

A sprite is a stack of LAYERS (back to front). Inside a layer every shape is a signed HEIGHT field and
the shapes are merged with a smooth maximum, so a leg grows out of a body instead of being glued on.
The surface normal comes from the gradient of the merged height, which gives real form shading,
crevice occlusion and specular highlights. Detail (veins, seams, rivets, grain) is height too.

ANIMATION: a creature is a RIG -- build(pose) -> Figure (layers + anchors + stencils). A CLIP is a set
of keyframes on the rig's pose parameters; every frame is rendered by the same light rig, then the
frames are trimmed, de-duplicated and packed into an atlas, a glow atlas and a JSON manifest.
"""
import json
import math
import os
import zlib

import numpy as np
from PIL import Image, ImageDraw

LIGHT = np.array([-0.50, -0.72, 0.48]); LIGHT /= np.linalg.norm(LIGHT)
HALF = LIGHT + np.array([0, 0, 1.0]); HALF /= np.linalg.norm(HALF)
RIM = np.array([0.80, -0.35, 0.10]); RIM /= np.linalg.norm(RIM)
RIM_COLOR = np.array([0.42, 0.86, 0.98])        # FACILITY_CYAN -- the machine lights everything
BAYER = (np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0) - 0.47
BACKGROUND = (14, 18, 24, 255)                  # the game's near-black, for previews and sheets
ATLAS_LIMIT = 2048
ATLAS_PADDING = 2
MANIFEST_VERSION = 1


def ramp(*codes):
    return np.array([[int(c[i:i + 2], 16) for i in (0, 2, 4)] for c in codes], float)


# material: ramp (6 steps, hue-shifted: cool shadows, warm lights), specular (strength, shininess),
#           grain (amplitude, cell), outline tint, emissive / translucent-glow
MATERIALS = {
    "gunmetal":  dict(ramp=ramp("0d1219", "1a2430", "2b3a49", "45596b", "6f8597", "a9bccb"), spec=(0.55, 36), grain=(0.02, 5)),
    "darkmetal": dict(ramp=ramp("07090d", "10151c", "1b222c", "2a333f", "3f4a57", "5f6b78"), spec=(0.45, 30), grain=(0.02, 5)),
    "livery":    dict(ramp=ramp("24262b", "45484e", "6b6e72", "989a9b", "c4c4c0", "e6e3da"), spec=(0.35, 24), grain=(0.03, 4)),
    "chitin":    dict(ramp=ramp("140a0e", "2c1418", "4a2220", "6e382b", "98583c", "c58758"), spec=(0.50, 22), grain=(0.05, 2)),
    "chitin_dk": dict(ramp=ramp("0c0709", "1c0e11", "301a19", "4a2a23", "693f30", "8f5c42"), spec=(0.45, 20), grain=(0.04, 2)),
    "bone":      dict(ramp=ramp("221d18", "3e362c", "625642", "8d7e62", "b9aa8a", "e2d8bd"), spec=(0.25, 16), grain=(0.03, 2)),
    "basalt":    dict(ramp=ramp("0e0f14", "1a1d24", "292d36", "3c424c", "56606a", "7d8892"), spec=(0.05, 8), grain=(0.10, 2.5)),
    "basalt_lt": dict(ramp=ramp("15171d", "242830", "363b45", "4d5560", "6d7782", "98a3ab"), spec=(0.05, 8), grain=(0.10, 2.5)),
    "cyan":   dict(ramp=ramp("0a3346", "11587a", "1f8db5", "4cc6e8", "a6ecff", "f2fdff"), emissive=True),
    "magma":  dict(ramp=ramp("4a0e05", "8a2408", "c94a0e", "f08a24", "ffc35c", "fff2c8"), emissive=True),
    "ichor":  dict(ramp=ramp("4e1426", "8f2c3d", "d0504f", "f57f66", "ffb293", "ffeadb"), emissive=True),
    # translucent membrane over a glowing interior
    "sac":    dict(ramp=ramp("2a1018", "4d1d27", "7c3138", "b0524c", "dd8a70", "f8c6a8"), glow="ichor", spec=(0.6, 40), grain=(0.03, 3)),
}

# An emissive (or translucent) shape whose glow level is at or below this is DEAD: it is shaded at the
# bottom of its ramp and contributes nothing to the glow atlas (a powered-down eye, a dimmed blister).
GLOW_DEAD = 0.05


# ---------------------------------------------------------------------------------------------------
# Shapes
# ---------------------------------------------------------------------------------------------------
class Shape:
    """One primitive. `frame` maps canvas coordinates to the shape's OWN material coordinates (3x3
    affine); grain is sampled there, so texture sticks to a moving part. `glow` scales an emissive or
    translucent shape's light (0 = dead, 1 = rest, > 1 = flare)."""

    def __init__(self, kind, mat, op="union", k=4.0, prio=0.0, glow=1.0, frame=None, **g):
        self.kind, self.mat, self.op, self.k, self.prio, self.glow, self.g = kind, mat, op, k, prio, glow, g
        self.frame = frame if frame is not None else _intrinsic_frame(kind, g)

    def copy(self, **g):
        merged = dict(self.g); merged.update(g)
        return Shape(self.kind, self.mat, self.op, self.k, self.prio, self.glow, self.frame.copy(), **merged)


def _intrinsic_frame(kind, g):
    if kind == "ellipse":
        return Xf.turn(-g["angle"]).m @ Xf.move(-g["cx"], -g["cy"]).m
    if kind == "capsule":
        axis = math.atan2(g["y2"] - g["y1"], g["x2"] - g["x1"])
        return Xf.turn(-axis).m @ Xf.move(-g["x1"], -g["y1"]).m
    cx, cy = g["pts"].mean(axis=0)
    return Xf.move(-cx, -cy).m


def blob(cx, cy, rx, ry, mat, depth=None, angle=0.0, **kw):
    return Shape("ellipse", mat, cx=cx, cy=cy, rx=rx, ry=ry, depth=depth or min(rx, ry), angle=angle, **kw)


def limb(x1, y1, x2, y2, r1, r2, mat, **kw):
    return Shape("capsule", mat, x1=x1, y1=y1, x2=x2, y2=y2, r1=r1, r2=r2, **kw)


def slab(points, mat, bevel=4.0, slope=0.9, tilt=(0.0, 0.0), **kw):
    return Shape("poly", mat, pts=np.array(points, float), bevel=bevel, slope=slope, tilt=tilt, **kw)


def chain(points, r1, r2, mat, **kw):
    """A bent limb: consecutive capsules with a radius tapering from r1 to r2."""
    out = []
    n = len(points) - 1
    for i in range(n):
        ra = r1 + (r2 - r1) * i / n; rb = r1 + (r2 - r1) * (i + 1) / n
        out.append(limb(*points[i], *points[i + 1], ra, rb, mat, **kw))
    return out


def mirror(shapes, axis):
    """Bilateral symmetry: reflect shapes about the vertical line x = axis. Design one half."""
    return Xf.reflect_x(axis)(shapes)


# ---------------------------------------------------------------------------------------------------
# Transforms (rigging). Image space: origin top-left, y DOWN, so a positive angle turns CLOCKWISE.
# ---------------------------------------------------------------------------------------------------
class Xf:
    """A 2D affine transform. `a.then(b)` applies a, then b. Calling it on a list of shapes returns
    transformed copies; `.pt((x, y))` transforms a point."""

    def __init__(self, m=None):
        self.m = np.eye(3) if m is None else m

    @staticmethod
    def move(dx, dy):
        return Xf(np.array([[1, 0, dx], [0, 1, dy], [0, 0, 1.0]]))

    @staticmethod
    def turn(angle, pivot=(0.0, 0.0)):
        c, s = math.cos(angle), math.sin(angle)
        r = Xf(np.array([[c, -s, 0], [s, c, 0], [0, 0, 1.0]]))
        return Xf.move(-pivot[0], -pivot[1]).then(r).then(Xf.move(*pivot))

    @staticmethod
    def scale(sx, sy=None, pivot=(0.0, 0.0)):
        sy = sx if sy is None else sy
        s = Xf(np.array([[sx, 0, 0], [0, sy, 0], [0, 0, 1.0]]))
        return Xf.move(-pivot[0], -pivot[1]).then(s).then(Xf.move(*pivot))

    @staticmethod
    def reflect_x(axis):
        return Xf(np.array([[-1, 0, 2 * axis], [0, 1, 0], [0, 0, 1.0]]))

    @staticmethod
    def segment(rest_a, rest_b, new_a, new_b):
        """The similarity that carries the rest segment a->b onto the posed one (for parts riding a limb)."""
        ra = math.atan2(rest_b[1] - rest_a[1], rest_b[0] - rest_a[0])
        na = math.atan2(new_b[1] - new_a[1], new_b[0] - new_a[0])
        rl = math.hypot(rest_b[0] - rest_a[0], rest_b[1] - rest_a[1]) or 1e-6
        nl = math.hypot(new_b[0] - new_a[0], new_b[1] - new_a[1])
        return (Xf.move(-rest_a[0], -rest_a[1]).then(Xf.turn(na - ra)).then(Xf.scale(nl / rl))
                .then(Xf.move(*new_a)))

    def then(self, other):
        return Xf(other.m @ self.m)

    def pt(self, p):
        v = self.m @ np.array([p[0], p[1], 1.0])
        return (float(v[0]), float(v[1]))

    def pts(self, points):
        return [self.pt(p) for p in points]

    def __call__(self, shapes):
        return [self._shape(s) for s in shapes]

    def _shape(self, s):
        lin = self.m[:2, :2]
        det = abs(np.linalg.det(lin)) or 1e-9
        uniform = math.sqrt(det)
        g = dict(s.g)
        if s.kind == "ellipse":
            g["cx"], g["cy"] = self.pt((g["cx"], g["cy"]))
            e1 = lin @ np.array([math.cos(g["angle"]), math.sin(g["angle"])])
            e2 = lin @ np.array([-math.sin(g["angle"]), math.cos(g["angle"])])
            g["rx"] = g["rx"] * float(np.linalg.norm(e1)); g["ry"] = g["ry"] * float(np.linalg.norm(e2))
            g["angle"] = math.atan2(e1[1], e1[0])
            if np.linalg.det(lin) < 0:          # a reflection: keep the ellipse right-handed
                g["angle"] = math.atan2(-e1[1], -e1[0])
            g["depth"] = g["depth"] * uniform
        elif s.kind == "capsule":
            g["x1"], g["y1"] = self.pt((g["x1"], g["y1"]))
            g["x2"], g["y2"] = self.pt((g["x2"], g["y2"]))
            g["r1"] = g["r1"] * uniform; g["r2"] = g["r2"] * uniform
        else:
            pts = np.array(self.pts(g["pts"]))
            g["pts"] = pts[::-1] if np.linalg.det(lin) < 0 else pts
            tilt = np.linalg.inv(lin).T @ np.array(g["tilt"])
            g["tilt"] = (float(tilt[0]), float(tilt[1]))
        frame = s.frame @ np.linalg.inv(self.m)
        return Shape(s.kind, s.mat, s.op, s.k, s.prio, s.glow, frame, **g)


def scaled(shapes, factor, pivot):
    return Xf.scale(factor, factor, pivot)(shapes)


def lerp(a, b, t):
    return a + (b - a) * t


def lerp_pt(a, b, t):
    return (lerp(a[0], b[0], t), lerp(a[1], b[1], t))


def with_glow(shapes, level):
    """Set the glow level of every shape in the list (emissive and translucent shapes read it)."""
    for s in shapes:
        s.glow = level
    return shapes


# ---------------------------------------------------------------------------------------------------
# Height fields, noise
# ---------------------------------------------------------------------------------------------------
def _height(s, X, Y):
    g = s.g
    if s.kind == "ellipse":
        c, sn = math.cos(g["angle"]), math.sin(g["angle"])
        dx, dy = X - g["cx"], Y - g["cy"]
        u = (dx * c + dy * sn) / max(g["rx"], 1e-3); v = (-dx * sn + dy * c) / max(g["ry"], 1e-3)
        q = np.sqrt(u * u + v * v)
        return np.where(q < 1, g["depth"] * np.sqrt(np.clip(1 - q * q, 0, 1)), g["depth"] * (1 - q))
    if s.kind == "capsule":
        ax, ay, bx, by = g["x1"], g["y1"], g["x2"], g["y2"]
        vx, vy = bx - ax, by - ay; L2 = vx * vx + vy * vy or 1e-6
        t = np.clip(((X - ax) * vx + (Y - ay) * vy) / L2, 0, 1)
        r = g["r1"] + (g["r2"] - g["r1"]) * t
        d = np.hypot(X - ax - t * vx, Y - ay - t * vy)
        q = d / np.maximum(r, 1e-3)
        return np.where(q < 1, r * np.sqrt(np.clip(1 - q * q, 0, 1)), r * (1 - q))
    pts = g["pts"]; n = len(pts)
    inside = np.zeros(X.shape, bool); dist = np.full(X.shape, 1e9)
    for i in range(n):
        (x1, y1), (x2, y2) = pts[i], pts[(i + 1) % n]
        inside ^= ((y1 > Y) != (y2 > Y)) & (X < (x2 - x1) * (Y - y1) / ((y2 - y1) or 1e-9) + x1)
        vx, vy = x2 - x1, y2 - y1; L2 = vx * vx + vy * vy or 1e-9
        t = np.clip(((X - x1) * vx + (Y - y1) * vy) / L2, 0, 1)
        dist = np.minimum(dist, np.hypot(X - x1 - t * vx, Y - y1 - t * vy))
    cx, cy = pts.mean(axis=0)
    plateau = g["bevel"] * g["slope"] + g["tilt"][0] * (X - cx) + g["tilt"][1] * (Y - cy)
    inner = np.minimum(dist * g["slope"], plateau)
    return np.where(inside, np.maximum(inner, 0.01), -dist)


def _depth_of(s, fallback):
    return s.g.get("depth", max(s.g.get("r1", 0), s.g.get("r2", 0)) or s.g.get("bevel", fallback))


def _smax(a, b, k):
    if k <= 0:
        return np.maximum(a, b)
    h = np.clip(k - np.abs(a - b), 0, None) / k
    return np.maximum(a, b) + h * h * k * 0.25


def _hash01(ix, iy, seed):
    """Deterministic lattice hash -> [0, 1). Integer-only, so it is identical on every machine."""
    h = (ix.astype(np.int64) * 374761393 + iy.astype(np.int64) * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    h = h ^ (h >> 16)
    return h / 4294967296.0


def value_noise_at(U, V, cell, seed):
    """Smooth value noise in [-0.5, 0.5) sampled at arbitrary (U, V) -- any coordinate frame."""
    x, y = U / cell, V / cell
    x0, y0 = np.floor(x), np.floor(y)
    fx, fy = x - x0, y - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    x0, y0 = x0.astype(np.int64), y0.astype(np.int64)
    a = _hash01(x0, y0, seed) * (1 - fx) + _hash01(x0 + 1, y0, seed) * fx
    b = _hash01(x0, y0 + 1, seed) * (1 - fx) + _hash01(x0 + 1, y0 + 1, seed) * fx
    return a * (1 - fy) + b * fy - 0.5


def _material_seed(seed, mat):
    return (seed * 7919 + zlib.crc32(mat.encode())) & 0x7FFFFFFF


def _grain(s, X, Y, seed):
    """The material's grain in the shape's OWN coordinates (guide 7.5): it moves with the part."""
    m = MATERIALS[s.mat]
    if "grain" not in m:
        return None
    cell = m["grain"][1]
    f = s.frame
    U = f[0, 0] * X + f[0, 1] * Y + f[0, 2]
    V = f[1, 0] * X + f[1, 1] * Y + f[1, 2]
    ms = _material_seed(seed, s.mat)
    return value_noise_at(U, V, cell, ms) + 0.5 * value_noise_at(U, V, cell / 2, ms + 1)


def _blur(a, r):
    out = a.copy()
    for axis in (0, 1):
        acc = np.zeros_like(out)
        for s in range(-r, r + 1):
            acc += np.roll(out, s, axis)
        out = acc / (2 * r + 1)
    return out


def _shift(a, sx, sy, fill):
    out = np.full_like(a, fill)
    h, w = a.shape[:2]
    ys, yd = (slice(0, h - sy), slice(sy, h)) if sy >= 0 else (slice(-sy, h), slice(0, h + sy))
    xs, xd = (slice(0, w - sx), slice(sx, w)) if sx >= 0 else (slice(-sx, w), slice(0, w + sx))
    out[yd, xd] = a[ys, xs]
    return out


# ---------------------------------------------------------------------------------------------------
# The renderer
# ---------------------------------------------------------------------------------------------------
def render(layers, width, height, seed=3, glow_radius=5, glow_alpha=0.55, return_glow=False):
    """layers: list of lists of Shape, back to front. Returns an RGBA PIL image, or (image, glow image)
    when return_glow is set: the glow image holds the emissive pixels only (binary alpha)."""
    Y, X = np.mgrid[0:height, 0:width].astype(float) + 0.5
    mat_names = list(MATERIALS)
    layer_id = np.full((height, width), -1)
    Hfinal = np.zeros((height, width)); Mfinal = np.full((height, width), -1); Ifinal = np.zeros((height, width))
    Gfinal = np.ones((height, width))

    for li, shapes in enumerate(layers):
        H = np.full((height, width), -1e3); best = np.full((height, width), -1e9)
        M = np.full((height, width), -1); peak = np.zeros((height, width)); glow = np.ones((height, width))
        grain_acc = np.zeros((height, width)); grain_w = np.zeros((height, width))
        for s in shapes:
            h = _height(s, X, Y)
            mi = mat_names.index(s.mat)
            if s.op == "union":
                H = _smax(H, h, s.k)
                score = h + s.prio
                win = (h > 0) & (score > best)
                best = np.where(win, score, best); M = np.where(win, mi, M)
                peak = np.where(win, np.clip(h / max(_depth_of(s, 4), 1e-3), 0, 1), peak)
                glow = np.where(win, s.glow, glow)
                weight = np.clip(h, 0, None) ** 2
            elif s.op == "add":       # raised detail painted with its own material (veins, rivets)
                bump = np.clip(h, 0, None)
                H = H + bump
                painted = (bump > 0) & (H > 0)
                M = np.where(painted, mi, M)
                peak = np.where(painted, np.clip(bump / max(_depth_of(s, 2), 1e-3), 0, 1), peak)
                glow = np.where(painted, s.glow, glow)
                weight = np.where(painted, 1e4, 0.0)
            else:                     # carve: grooves and seams
                H = H - np.clip(h, 0, None)
                continue
            if weight.any():
                gr = _grain(s, X, Y, seed)
                if gr is not None:
                    grain_acc += weight * gr; grain_w += weight
        inside = (H > 0) & (M >= 0)
        # surface grain as height, per material, sampled in each shape's own frame and blended
        grain = np.where(grain_w > 0, grain_acc / np.maximum(grain_w, 1e-12), 0.0)
        for mi in np.unique(M[inside]):
            m = MATERIALS[mat_names[mi]]
            if "grain" in m:
                sel = inside & (M == mi)
                H[sel] += m["grain"][0] * 10 * grain[sel]
        layer_id[inside] = li
        Hfinal[inside] = H[inside] + li * 1000   # later layers sit in front
        Mfinal[inside] = M[inside]
        Ifinal[inside] = peak[inside]
        Gfinal[inside] = glow[inside]

    solid = layer_id >= 0
    # normals from the height gradient inside each layer
    Hn = np.where(solid, Hfinal - layer_id * 1000, 0)
    gx = np.zeros_like(Hn); gy = np.zeros_like(Hn)
    for sx, sy, arr in ((1, 0, gx), (0, 1, gy)):
        fwd = _shift(Hn, -sx, -sy, 0); bwd = _shift(Hn, sx, sy, 0)
        same_f = _shift(layer_id, -sx, -sy, -1) == layer_id
        same_b = _shift(layer_id, sx, sy, -1) == layer_id
        fwd = np.where(same_f, fwd, np.minimum(Hn, 0) - 0.0); bwd = np.where(same_b, bwd, 0)
        arr[:] = (fwd - bwd) * 0.5
    N = np.dstack([-gx, -gy, np.ones_like(gx)])
    N /= np.linalg.norm(N, axis=2, keepdims=True)

    diffuse = np.clip(N @ LIGHT, 0, 1)
    wrap = np.clip((N @ LIGHT + 0.35) / 1.35, 0, 1)             # soft terminator
    rim = (np.clip(N @ RIM, 0, 1) ** 2) * (1 - N[..., 2]) ** 0.6
    # cavity occlusion: lower than the neighbourhood -> darker
    cav = np.clip((_blur(Hn, 3) - Hn) / 6.0, 0, 1)
    # cast occlusion: pixels just behind a nearer layer
    occl = np.zeros((height, width))
    for d in (1, 2, 3):
        for sx, sy in ((0, -d), (d, -d), (-d, -d), (d, 0), (-d, 0)):
            occl = np.maximum(occl, (_shift(layer_id, sx, sy, -1) > layer_id) * (1 - (d - 1) / 3))
    bay = BAYER[Y.astype(int) % 4, X.astype(int) % 4]

    rgb = np.zeros((height, width, 3)); shade_idx = np.full((height, width), -1)
    emissive_mask = np.zeros((height, width), bool)
    for mi in np.unique(Mfinal[solid]):
        m = MATERIALS[mat_names[mi]]; sel = solid & (Mfinal == mi)
        r = m["ramp"]
        if m.get("emissive"):
            v = (0.22 + 0.74 * Ifinal[sel] ** 1.4) * Gfinal[sel]
            v = np.clip(v + bay[sel] * 0.10, 0, 0.999)
            emissive_mask |= sel & (Gfinal > GLOW_DEAD)
        else:
            v = 0.06 + 0.62 * wrap[sel] + 0.30 * diffuse[sel] - 0.55 * cav[sel] - 0.30 * occl[sel]
            v = np.clip(v + bay[sel] * 0.08, 0, 0.999)
        idx = (v * len(r)).astype(int)
        shade_idx[sel] = idx + mi * 10
        rgb[sel] = r[idx]
        if not m.get("emissive"):
            strength, shiny = m.get("spec", (0, 1))
            sp = strength * np.clip(N[sel] @ HALF, 0, 1) ** shiny
            hot = sp > 0.28
            col = rgb[sel]
            col[hot] = col[hot] * 0.35 + r[-1] * 0.65 + 25
            rgb[sel] = col + (rim[sel][:, None] * RIM_COLOR * 140)
            if "glow" in m:   # translucent membrane: the interior glow shows through, strongest where thick
                gl = MATERIALS[m["glow"]]["ramp"]
                level = np.clip(Gfinal[sel], 0, 1.4)
                t = np.clip((Ifinal[sel] ** 0.8 * 1.05 + bay[sel] * 0.1) * level, 0, 0.999)
                gcol = gl[(t * len(gl)).astype(int)]
                rgb[sel] = rgb[sel] * (1 - 0.65 * t[:, None]) + gcol * 0.65 * t[:, None]
                emissive_mask |= sel & (Ifinal > 0.55) & (Gfinal > 0.5)
                # the shade index must see the glow, or cleanup would merge lit and unlit membrane
                shade_idx[sel] = shade_idx[sel] + 100 * (t * 6).astype(int)

    # cleanup: an isolated pixel (all 4 neighbours agree on another shade) takes the neighbours' colour
    for _ in range(2):
        n = [_shift(shade_idx, sx, sy, -2) for sx, sy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
        lonely = solid & (n[0] == n[1]) & (n[1] == n[2]) & (n[2] == n[3]) & (n[0] != shade_idx) & (n[0] >= 0)
        if lonely.any():
            src = _shift(rgb, 1, 0, 0)
            rgb[lonely] = src[lonely]; shade_idx[lonely] = n[0][lonely]

    # interior edge where a nearer layer overlaps: darken the far side
    for sx, sy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        nb = _shift(layer_id, sx, sy, -1)
        edge = solid & (nb > layer_id) & ~emissive_mask & ~_shift(emissive_mask, sx, sy, False)
        rgb[edge] *= 0.45

    out = np.zeros((height, width, 4))
    out[..., :3] = np.clip(rgb, 0, 255); out[..., 3] = solid * 255

    # selective exterior outline: the darkest step of the neighbouring material, a touch lighter on the lit side
    ring = np.zeros((height, width), bool); ring_col = np.zeros((height, width, 3))
    for sx, sy, lit in ((1, 0, 0), (-1, 0, 1), (0, 1, 0), (0, -1, 1)):
        nb_solid = _shift(solid, sx, sy, False)
        nb_mat = _shift(Mfinal, sx, sy, -1)
        new = nb_solid & ~solid & ~ring
        for mi in np.unique(nb_mat[new]):
            m = MATERIALS[mat_names[mi]]
            base = m["ramp"][1] if m.get("emissive") else m["ramp"][0] * (0.75 if not lit else 1.0)
            sel = new & (nb_mat == mi)
            ring_col[sel] = base
        ring |= new
    out[ring, :3] = ring_col[ring]; out[ring, 3] = 255

    glow_img = None
    if return_glow:
        g = np.zeros((height, width, 4))
        g[emissive_mask, :3] = out[emissive_mask, :3]; g[emissive_mask, 3] = 255
        glow_img = Image.fromarray(g.astype(np.uint8), "RGBA")

    # smooth light spill around emissives (outside the silhouette only) -- presentation only
    if glow_radius:
        em = _blur(emissive_mask.astype(float), glow_radius)
        em = _blur(em, glow_radius // 2 + 1)
        spill = (out[..., 3] == 0) & (em > 0.02)
        glow_col = np.zeros(3); count = 0
        for mi in np.unique(Mfinal[emissive_mask]):
            m = MATERIALS[mat_names[mi]]
            glow_col += (MATERIALS[m["glow"]] if "glow" in m else m)["ramp"][3]; count += 1
        glow_col /= max(count, 1)
        out[spill, :3] = glow_col
        out[spill, 3] = np.clip(em[spill] * 3.0, 0, 1) * 255 * glow_alpha
    img = Image.fromarray(out.astype(np.uint8), "RGBA").copy()
    return (img, glow_img) if return_glow else img


STENCIL_FONT = {"0": "111101101101111", "1": "010110010010111", "2": "111001111100111", "3": "111001011001111",
                "4": "101101111001001", "5": "111100111001111", "6": "111100111101111", "7": "111001010010010",
                "8": "111101111101111", "9": "111101111001111", "-": "000000111000000", "D": "110101101101110",
                "K": "101101110101101", "W": "101101101111101", "R": "110101110101101", "E": "111100110100111"}


def stencil(img, text, x, y, color, alpha=255):
    """3x5 pixel stencil digits/letters, painted only onto opaque pixels."""
    px = img.load()
    x, y = int(round(x)), int(round(y))
    for ci, ch in enumerate(text):
        bits = STENCIL_FONT[ch]
        for r in range(5):
            for c in range(3):
                if bits[r * 3 + c] == "1":
                    qx, qy = x + ci * 4 + c, y + r
                    if not (0 <= qx < img.width and 0 <= qy < img.height):
                        continue
                    p = px[qx, qy]
                    if p[3] == 255:
                        px[qx, qy] = (int(p[0] * 0.3 + color[0] * 0.7), int(p[1] * 0.3 + color[1] * 0.7),
                                      int(p[2] * 0.3 + color[2] * 0.7), 255)


# ---------------------------------------------------------------------------------------------------
# Rigs, poses, clips
# ---------------------------------------------------------------------------------------------------
class Pose(dict):
    """Named pose parameters (plain floats) with the rig's defaults behind them: p.jaw, p["jaw"]."""

    def __getattr__(self, name):
        try:
            return self[name]
        except KeyError:
            raise AttributeError(f"pose has no parameter '{name}' (add it to the rig's defaults)")


class Figure:
    """What a rig builds for one pose: layers (back to front), named anchors {name: (x, y)} on the
    canvas, and stencils [(text, x, y, colour)] painted after rendering."""

    def __init__(self, layers, anchors=None, stencils=None):
        self.layers, self.anchors, self.stencils = layers, anchors or {}, stencils or []


class Rig:
    """A creature: build(pose) -> Figure, the pose defaults, the canvas and the render seed."""

    def __init__(self, name, build, defaults, seed, width=160, height=240, ground_y=232):
        self.name, self.build, self.defaults, self.seed = name, build, dict(defaults), seed
        self.width, self.height, self.ground_y = width, height, ground_y

    def pose(self, **values):
        unknown = set(values) - set(self.defaults)
        if unknown:
            raise KeyError(f"{self.name}: unknown pose parameters {sorted(unknown)}")
        p = Pose(self.defaults); p.update(values)
        return p

    def render(self, pose, halo=False):
        """-> (RGBA image, glow image, anchors) for one pose."""
        fig = self.build(pose)
        img, glow = render(fig.layers, self.width, self.height, seed=self.seed,
                           glow_radius=5 if halo else 0, return_glow=True)
        for text, x, y, colour in fig.stencils:
            stencil(img, text, x, y, colour)
        return img, glow, fig.anchors


EASINGS = {
    "linear": lambda t: t,
    "in": lambda t: t * t,
    "out": lambda t: 1 - (1 - t) * (1 - t),
    "inout": lambda t: t * t * (3 - 2 * t),
    "hold": lambda t: 0.0,
}
MODES = ("loop", "once", "hold")   # loop forever | play once, then the runtime moves on | hold the last frame


class Clip:
    """Keyframes on pose parameters. keys = [(time_seconds, {param: value}, easing), ...] -- the easing
    shapes the segment LEAVING that key. Each parameter is its own track: it interpolates between the
    keys that name it, so a clip names only what moves. Parameters a clip never names use the rig's
    defaults. `overrides` pin parameters to a constant for the whole clip."""

    def __init__(self, name, keys, duration=None, fps=12, mode="loop", variant_of=None, overrides=None):
        assert mode in MODES, mode
        self.name, self.fps, self.mode, self.variant_of = name, fps, mode, variant_of
        self.keys = sorted([(float(k[0]), dict(k[1]), k[2] if len(k) > 2 else "inout") for k in keys],
                           key=lambda k: k[0])
        for _, _, ease in self.keys:
            assert ease in EASINGS, ease
        self.duration = float(duration if duration is not None else self.keys[-1][0])
        self.overrides = dict(overrides or {})

    def variant(self, name, speed=1.0, keys=(), **overrides):
        """A variant of this clip: time stretched by 1/speed, extra keys merged in, parameters pinned."""
        scaled_keys = [(t / speed, v, e) for t, v, e in self.keys]
        merged = dict(self.overrides); merged.update(overrides)
        return Clip(name, scaled_keys + list(keys), self.duration / speed, self.fps, self.mode,
                    self.variant_of or self.name, merged)

    def value(self, param, t, default):
        if param in self.overrides:
            return self.overrides[param]
        track = [(kt, v[param], e) for kt, v, e in self.keys if param in v]
        if not track:
            return default
        if self.mode == "loop" and len(track) > 1:
            # wrap: the segment after the last key runs to the first key one period later
            track = [(kt - self.duration, val, e) for kt, val, e in track[-1:]] + track + \
                    [(kt + self.duration, val, e) for kt, val, e in track[:1]]
        elif self.mode != "loop" and track[0][0] > 0:
            # a one-shot track that starts late eases in from the rig's default at t = 0
            track = [(0.0, default, "inout")] + track
        if t <= track[0][0]:
            return track[0][1]
        for (t0, v0, e0), (t1, v1, _) in zip(track, track[1:]):
            if t0 <= t <= t1:
                u = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
                return v0 + (v1 - v0) * EASINGS[e0](u)
        return track[-1][1]

    def sample(self, rig):
        """-> [(pose, frame_duration_seconds)]. A loop samples n frames over the period (the frame at t =
        duration would repeat frame 0); a one-shot samples n frames from t = 0 to t = duration inclusive
        so its last frame IS the end pose. Every frame lasts duration / n, so the clip lasts `duration`."""
        n = max(2, int(round(self.duration * self.fps)))
        if self.mode == "loop":
            times = [self.duration * i / n for i in range(n)]
        else:
            times = [self.duration * i / (n - 1) for i in range(n)]
        names = set(rig.defaults)
        out = []
        for t in times:
            values = {p: self.value(p, t, rig.defaults[p]) for p in names}
            out.append((rig.pose(**values), self.duration / n))
        return out


# ---------------------------------------------------------------------------------------------------
# Baking: render every clip, trim, de-duplicate, pack, manifest, previews
# ---------------------------------------------------------------------------------------------------
def _pose_key(pose):
    return tuple(sorted((k, round(float(v), 5)) for k, v in pose.items()))


def _trim(img):
    box = img.getchannel("A").getbbox()
    if box is None:
        box = (0, 0, 1, 1)
    return box


def bake(rig, clips, halo=False, log=print):
    """Render every clip of a rig. -> dict with frames (unique trimmed images), and per clip the frame
    list [(unique_index, duration, anchors)]. Consecutive identical frames merge into one longer frame."""
    cache = {}
    uniques = []          # [(rgba_trimmed, glow_trimmed, (left, top))]
    unique_index = {}     # bytes signature -> index
    baked = []
    for clip in clips:
        frames = []
        for pose, duration in clip.sample(rig):
            key = _pose_key(pose)
            if key not in cache:
                img, glow, anchors = rig.render(pose, halo=halo)
                box = _trim(img)
                _check_edges(rig, clip, img, log)
                rgba, gl = img.crop(box), glow.crop(box)
                sig = (box, rgba.tobytes(), gl.tobytes())
                if sig not in unique_index:
                    unique_index[sig] = len(uniques)
                    uniques.append((rgba, gl, (box[0], box[1]), img, glow))
                cache[key] = (unique_index[sig], anchors)
            idx, anchors = cache[key]
            if frames and frames[-1][0] == idx and frames[-1][2] == anchors:
                frames[-1] = (idx, frames[-1][1] + duration, anchors)
            else:
                frames.append((idx, duration, anchors))
        baked.append((clip, frames))
        log(f"  {rig.name}.{clip.name}: {len(frames)} frames, {clip.duration:.2f} s, {clip.mode}")
    return uniques, baked


def _check_edges(rig, clip, img, log):
    a = np.asarray(img.getchannel("A"))
    if a[0].any() or a[-1].any() or a[:, 0].any() or a[:, -1].any():
        log(f"  WARNING {rig.name}.{clip.name}: a part touches the canvas edge -- clamp the pose, never grow the canvas")


def pack(sizes):
    """Shelf-pack rectangles (w, h). -> (atlas_w, atlas_h, [(x, y)]) with power-of-two sides, the
    smallest area that fits; raises if nothing within ATLAS_LIMIT x ATLAS_LIMIT does."""
    order = sorted(range(len(sizes)), key=lambda i: (-sizes[i][1], -sizes[i][0], i))
    best = None
    side = 64
    widths = []
    while side <= ATLAS_LIMIT:
        widths.append(side); side *= 2
    for aw in widths:
        x = y = shelf = 0
        pos = [None] * len(sizes)
        ok = True
        for i in order:
            w, h = sizes[i][0] + ATLAS_PADDING, sizes[i][1] + ATLAS_PADDING
            if w > aw:
                ok = False; break
            if x + w > aw:
                x, y, shelf = 0, y + shelf, 0
            pos[i] = (x, y); x += w; shelf = max(shelf, h)
        if not ok:
            continue
        used_h = y + shelf
        ah = 64
        while ah < used_h:
            ah *= 2
        if ah > ATLAS_LIMIT:
            continue
        if best is None or aw * ah < best[0] * best[1] or (aw * ah == best[0] * best[1] and abs(aw - ah) < abs(best[0] - best[1])):
            best = (aw, ah, pos)
    if best is None:
        raise ValueError(f"atlas budget exceeded: frames do not fit in {ATLAS_LIMIT}x{ATLAS_LIMIT}")
    return best


def _r(v, nd=4):
    return float(round(v, nd))


def write_creature(rig, clips, out_dir, preview_dir=None, log=print):
    """Bake a rig's clips and write <name>.atlas.png, <name>.glow.png, <name>.clips.json (and previews)."""
    os.makedirs(out_dir, exist_ok=True)
    uniques, baked = bake(rig, clips, log=log)
    aw, ah, positions = pack([u[0].size for u in uniques])
    atlas = Image.new("RGBA", (aw, ah), (0, 0, 0, 0))
    glow = Image.new("RGBA", (aw, ah), (0, 0, 0, 0))
    has_glow = []
    for (rgba, gl, _, _, _), (x, y) in zip(uniques, positions):
        atlas.paste(rgba, (x, y)); glow.paste(gl, (x, y))
        has_glow.append(gl.getchannel("A").getbbox() is not None)
    manifest = {
        "version": MANIFEST_VERSION,
        "creature": rig.name,
        "canvas": [rig.width, rig.height],
        "groundY": rig.ground_y,
        "atlas": f"{rig.name}.atlas.png",
        "glow": f"{rig.name}.glow.png",
        "atlasSize": [aw, ah],
        "clips": {},
    }
    for clip, frames in baked:
        entry = {"mode": clip.mode, "fps": clip.fps, "duration": _r(clip.duration)}
        if clip.variant_of:
            entry["variantOf"] = clip.variant_of
        entry["frames"] = []
        for idx, duration, anchors in frames:
            rgba, _, (left, top), _, _ = uniques[idx]
            x, y = positions[idx]
            entry["frames"].append({
                "rect": [x, y, rgba.width, rgba.height],
                "offset": [left, top],
                "duration": _r(duration),
                "glow": has_glow[idx],
                "anchors": {k: [_r(v[0], 1), _r(v[1], 1)] for k, v in sorted(anchors.items())},
            })
        manifest["clips"][clip.name] = entry
    atlas.save(os.path.join(out_dir, f"{rig.name}.atlas.png"), optimize=False)
    glow.save(os.path.join(out_dir, f"{rig.name}.glow.png"), optimize=False)
    with open(os.path.join(out_dir, f"{rig.name}.clips.json"), "w", newline="\n") as fh:
        fh.write(manifest_json(manifest))
    log(f"wrote {rig.name}: {len(uniques)} unique frames, atlas {aw}x{ah}")
    if preview_dir:
        write_previews(rig, uniques, baked, preview_dir)
    return manifest


def manifest_json(manifest):
    """Readable, diff-friendly JSON: one frame per line. Plain JSON -- LibGDX's JsonReader reads it."""
    compact = lambda v: json.dumps(v, separators=(",", ":"))
    head = {k: v for k, v in manifest.items() if k != "clips"}
    lines = ["{"] + [f' "{k}": {compact(v)},' for k, v in head.items()] + [' "clips": {']
    names = list(manifest["clips"])
    for ci, name in enumerate(names):
        clip = manifest["clips"][name]
        lines.append(f'  "{name}": {{')
        for k, v in clip.items():
            if k != "frames":
                lines.append(f'   "{k}": {compact(v)},')
        lines.append('   "frames": [')
        frames = clip["frames"]
        for fi, frame in enumerate(frames):
            lines.append("    " + compact(frame) + ("," if fi < len(frames) - 1 else ""))
        lines.append("   ]")
        lines.append("  }" + ("," if ci < len(names) - 1 else ""))
    lines += [" }", "}"]
    return "\n".join(lines) + "\n"


def _frame_on_canvas(rig, uniques, idx, with_glow=False):
    rgba, gl, (left, top), _, _ = uniques[idx]
    canvas = Image.new("RGBA", (rig.width, rig.height), BACKGROUND)
    canvas.alpha_composite(rgba, (left, top))
    return canvas


def write_previews(rig, uniques, baked, preview_dir, scale=3, strip_scale=2):
    """One animated GIF per clip (nearest, `scale`x, one-shots hold their last frame for a beat) and one
    strip sheet with every clip as a labelled row of frames."""
    os.makedirs(preview_dir, exist_ok=True)
    for clip, frames in baked:
        images, durations = [], []
        for idx, duration, _ in frames:
            im = _frame_on_canvas(rig, uniques, idx).convert("RGB")
            images.append(im.resize((rig.width * scale, rig.height * scale), Image.NEAREST))
            durations.append(max(20, int(round(duration * 1000))))
        if clip.mode != "loop":
            durations[-1] = max(durations[-1], 900)
        if len(images) == 1:
            images.append(images[0]); durations.append(durations[0])
        images[0].save(os.path.join(preview_dir, f"{rig.name}_{clip.name.lower()}.gif"), save_all=True,
                       append_images=images[1:], duration=durations, loop=0, disposal=1, optimize=False)
    label_w, pad = 150, 6
    fw, fh = rig.width * strip_scale, rig.height * strip_scale
    cols = max(len(f) for _, f in baked)
    sheet = Image.new("RGB", (label_w + cols * (fw + pad) + pad, len(baked) * (fh + pad + 14) + pad), BACKGROUND[:3])
    draw = ImageDraw.Draw(sheet)
    for row, (clip, frames) in enumerate(baked):
        y = pad + row * (fh + pad + 14)
        draw.text((8, y + 4), clip.name, fill=(230, 228, 220))
        draw.text((8, y + 18), f"{clip.mode}  {clip.duration:.2f}s", fill=(140, 150, 160))
        draw.text((8, y + 32), f"{len(frames)} frames", fill=(140, 150, 160))
        for col, (idx, duration, anchors) in enumerate(frames):
            im = _frame_on_canvas(rig, uniques, idx).convert("RGB").resize((fw, fh), Image.NEAREST)
            x = label_w + pad + col * (fw + pad)
            sheet.paste(im, (x, y))
            draw.rectangle([x, y, x + fw - 1, y + fh - 1], outline=(40, 48, 58))
            draw.text((x + 4, y + fh + 1), f"{int(round(duration * 1000))}ms", fill=(120, 130, 140))
            for name, (ax, ay) in anchors.items():
                px, py = x + ax * strip_scale, y + ay * strip_scale
                draw.line([px - 3, py, px + 3, py], fill=(250, 90, 200)); draw.line([px, py - 3, px, py + 3], fill=(250, 90, 200))
    sheet.save(os.path.join(preview_dir, f"{rig.name}_strip.png"), optimize=False)
