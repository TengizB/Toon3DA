"""Enemy sprite kit -- sculpted height fields, one shared light rig. See SKILL.md.

A sprite is a stack of LAYERS (back to front). Inside a layer every shape is a signed HEIGHT field and
the shapes are merged with a smooth maximum, so a leg grows out of a body instead of being glued on.
The surface normal comes from the gradient of the merged height, which gives real form shading,
crevice occlusion and specular highlights. Detail (veins, seams, rivets, grain) is height too.
"""
import math
import numpy as np
from PIL import Image

LIGHT = np.array([-0.50, -0.72, 0.48]); LIGHT /= np.linalg.norm(LIGHT)
HALF = LIGHT + np.array([0, 0, 1.0]); HALF /= np.linalg.norm(HALF)
RIM = np.array([0.80, -0.35, 0.10]); RIM /= np.linalg.norm(RIM)
RIM_COLOR = np.array([0.42, 0.86, 0.98])        # FACILITY_CYAN -- the machine lights everything
BAYER = (np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0) - 0.47


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


class Shape:
    def __init__(self, kind, mat, op="union", k=4.0, prio=0.0, **g):
        self.kind, self.mat, self.op, self.k, self.prio, self.g = kind, mat, op, k, prio, g


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
    out = []
    for s in shapes:
        g = dict(s.g)
        if s.kind == "ellipse":
            g["cx"] = 2 * axis - g["cx"]; g["angle"] = -g["angle"]
        elif s.kind == "capsule":
            g["x1"] = 2 * axis - g["x1"]; g["x2"] = 2 * axis - g["x2"]
        else:
            p = g["pts"].copy(); p[:, 0] = 2 * axis - p[:, 0]; g["pts"] = p[::-1]
            g["tilt"] = (-g["tilt"][0], g["tilt"][1])
        out.append(Shape(s.kind, s.mat, s.op, s.k, s.prio, **g))
    return out


def _height(s, X, Y):
    g = s.g
    if s.kind == "ellipse":
        c, sn = math.cos(g["angle"]), math.sin(g["angle"])
        dx, dy = X - g["cx"], Y - g["cy"]
        u = (dx * c + dy * sn) / g["rx"]; v = (-dx * sn + dy * c) / g["ry"]
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


def _smax(a, b, k):
    if k <= 0:
        return np.maximum(a, b)
    h = np.clip(k - np.abs(a - b), 0, None) / k
    return np.maximum(a, b) + h * h * k * 0.25


def value_noise(h, w, cell, rng):
    gh, gw = int(h / cell) + 3, int(w / cell) + 3
    grid = rng.random((gh, gw))
    Y, X = np.mgrid[0:h, 0:w] / cell
    y0, x0 = Y.astype(int), X.astype(int); fy, fx = Y - y0, X - x0
    fy, fx = fy * fy * (3 - 2 * fy), fx * fx * (3 - 2 * fx)
    a = grid[y0, x0] * (1 - fx) + grid[y0, x0 + 1] * fx
    b = grid[y0 + 1, x0] * (1 - fx) + grid[y0 + 1, x0 + 1] * fx
    return a * (1 - fy) + b * fy - 0.5


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


def render(layers, width, height, seed=3, glow_radius=5, glow_alpha=0.55):
    """layers: list of lists of Shape, back to front. Returns RGBA PIL image."""
    rng = np.random.default_rng(seed)
    Y, X = np.mgrid[0:height, 0:width].astype(float) + 0.5
    mat_names = list(MATERIALS)
    layer_id = np.full((height, width), -1)
    Hfinal = np.zeros((height, width)); Mfinal = np.full((height, width), -1); Ifinal = np.zeros((height, width))
    grain_cache = {}

    for li, shapes in enumerate(layers):
        H = np.full((height, width), -1e3); best = np.full((height, width), -1e9)
        M = np.full((height, width), -1); peak = np.zeros((height, width))
        for s in shapes:
            h = _height(s, X, Y)
            mi = mat_names.index(s.mat)
            if s.op == "union":
                H = _smax(H, h, s.k)
                score = h + s.prio
                win = (h > 0) & (score > best)
                best = np.where(win, score, best); M = np.where(win, mi, M)
                depth = s.g.get("depth", max(s.g.get("r1", 0), s.g.get("r2", 0)) or s.g.get("bevel", 4))
                peak = np.where(win, np.clip(h / max(depth, 1e-3), 0, 1), peak)
            elif s.op == "add":       # raised detail painted with its own material (veins, rivets)
                bump = np.clip(h, 0, None)
                H = H + bump
                painted = (bump > 0) & (H > 0)
                M = np.where(painted, mi, M)
                depth = s.g.get("depth", max(s.g.get("r1", 0), s.g.get("r2", 0)) or 2)
                peak = np.where(painted, np.clip(bump / max(depth, 1e-3), 0, 1), peak)
            elif s.op == "carve":     # grooves and seams
                H = H - np.clip(h, 0, None)
        inside = (H > 0) & (M >= 0)
        # surface grain as height, per material
        for mi in np.unique(M[inside]):
            m = MATERIALS[mat_names[mi]]
            if "grain" in m:
                amp, cell = m["grain"]
                key = (cell, li)
                if key not in grain_cache:
                    grain_cache[key] = value_noise(height, width, cell, rng) + 0.5 * value_noise(height, width, cell / 2, rng)
                sel = inside & (M == mi)
                H[sel] += amp * 10 * grain_cache[key][sel]
        layer_id[inside] = li
        Hfinal[inside] = H[inside] + li * 1000   # later layers sit in front
        Mfinal[inside] = M[inside]
        Ifinal[inside] = peak[inside]

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
            v = np.clip(0.22 + 0.74 * Ifinal[sel] ** 1.4 + bay[sel] * 0.10, 0, 0.999)
            emissive_mask |= sel
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
                t = np.clip(Ifinal[sel] ** 0.8 * 1.05 + bay[sel] * 0.1, 0, 0.999)
                gcol = gl[(t * len(gl)).astype(int)]
                rgb[sel] = rgb[sel] * (1 - 0.65 * t[:, None]) + gcol * 0.65 * t[:, None]
                emissive_mask |= sel & (Ifinal > 0.55)

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

    # smooth light spill around emissives (outside the silhouette only)
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
    return Image.fromarray(out.astype(np.uint8), "RGBA").copy()


def stencil(img, text, x, y, color, alpha=255):
    """3x5 pixel stencil digits/letters, painted only onto opaque pixels."""
    font = {"0": "111101101101111", "1": "010110010010111", "2": "111001111100111", "3": "111001011001111",
            "4": "101101111001001", "5": "111100111001111", "6": "111100111101111", "7": "111001010010010",
            "8": "111101111101111", "9": "111101111001111", "-": "000000111000000", "D": "110101101101110",
            "K": "101101110101101", "W": "101101101111101", "R": "110101110101101", "E": "111100110100111"}
    px = img.load()
    for ci, ch in enumerate(text):
        bits = font[ch]
        for r in range(5):
            for c in range(3):
                if bits[r * 3 + c] == "1":
                    qx, qy = x + ci * 4 + c, y + r
                    p = px[qx, qy]
                    if p[3] == 255:
                        px[qx, qy] = (int(p[0] * 0.3 + color[0] * 0.7), int(p[1] * 0.3 + color[1] * 0.7),
                                      int(p[2] * 0.3 + color[2] * 0.7), 255)
