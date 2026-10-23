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
    # EFFECT inks (generated-enemy-art order 3, effects.py). Appended LAST so every earlier material keeps
    # its index and every creature regenerates byte-identical. flare = white-hot (sparks, a hard armour
    # spark, the dazed stars: the hit-flash white of V5); toxin = the POISONED status green (V5 semantic).
    "flare":  dict(ramp=ramp("4a3a2c", "8a6a48", "c99c64", "f2cf96", "fff0d2", "fffdf6"), emissive=True),
    "toxin":  dict(ramp=ramp("0d3313", "15591f", "1f8a2e", "3fc24e", "8fe89a", "e2fbe4"), emissive=True),
    # FLESH (generated-enemy-art order 5, the Aberrations). Appended LAST, like the effect inks, so every earlier
    # material keeps its index and every earlier sprite regenerates byte-identical. Desaturated rose-grey and
    # violet-grey, never a saturated red (G5: gore is wrong anatomy and wet tissue, not blood). `wet` is the
    # WET SHEEN: a second, tight specular lobe (strength, shininess) that leaves a near-white glint on the lit
    # curve of the surface - only these materials carry it, so no earlier sprite changes.
    "flesh":    dict(ramp=ramp("1d141b", "38262f", "5b434c", "846973", "ad9396", "d4c1b8"), spec=(0.28, 16), grain=(0.04, 3), wet=(0.55, 70)),
    "flesh_dk": dict(ramp=ramp("110b13", "221725", "392838", "553e4e", "735a67", "967e84"), spec=(0.32, 18), grain=(0.05, 2.5), wet=(0.60, 80)),
    "sclera":   dict(ramp=ramp("29222a", "4c4249", "7a6d6f", "a69a94", "cdc3b7", "ebe4d8"), spec=(0.40, 30), grain=(0.01, 4), wet=(0.85, 90)),
    # translucent gland membrane over the family's ichor light (the Mire Wraith's acid glands, the Hulk's blisters)
    "membrane": dict(ramp=ramp("1c1019", "36202d", "553342", "7a4e5a", "a17470", "c79f92"), glow="ichor", spec=(0.55, 44), grain=(0.03, 3), wet=(0.70, 80)),
    "pupil":    dict(ramp=ramp("060408", "0c0910", "151019", "1f1924", "2b2331", "3a3042"), spec=(0.60, 60), grain=(0.0, 4), wet=(0.90, 110)),
    # ENVIRONMENT materials (generated-wall-art order 1, walls.py; docs/wall-art-creation-guide.txt section 9).
    # Appended LAST, like every addition before them, so each earlier material keeps its index and every enemy
    # sprite regenerates byte-identical. The first eight are the 2026-10-22 prototypes' (approved in-engine).
    # poured structural concrete: warm grey, matte, coarse grain
    "concrete":    dict(ramp=ramp("15161a", "24252a", "37373b", "4d4c4e", "686562", "8c8780"), spec=(0.04, 8), grain=(0.07, 3)),
    "concrete_dk": dict(ramp=ramp("0b0c0f", "141519", "1e1f24", "2b2b30", "3b3a3e", "4f4d4f"), spec=(0.03, 8), grain=(0.06, 2.5)),
    # glazed abattoir tile: pale greige-celadon, glossy, almost no grain
    "ceramic":     dict(ramp=ramp("1c2221", "323b39", "4f5a56", "717c76", "97a19a", "c3c8bf"), spec=(0.65, 40), grain=(0.015, 6)),
    # bump rails, door seals, cable sheaths: near-black, low specular
    "rubber":      dict(ramp=ramp("060708", "0d0e10", "15171a", "1e2125", "2a2e33", "3a3f45"), spec=(0.15, 10), grain=(0.02, 3)),
    # oxidised steel: umber-brown, never a saturated orange
    "rust":        dict(ramp=ramp("140c0a", "2a1812", "432619", "5f3a24", "7d5435", "9f7552"), spec=(0.10, 10), grain=(0.09, 2)),
    # SOUL: the harvested planet-soul as printer fuel - pale, cold, low saturation (emissive)
    "soul":        dict(ramp=ramp("2a2c3a", "4a4e63", "767c94", "a5abc1", "d2d6e2", "f3f4f7"), emissive=True),
    # nacre: the Core's mineral-bone, pearl grey with a faint violet shadow (never a semantic purple)
    "nacre":       dict(ramp=ramp("17141c", "2b2633", "45404d", "665f6b", "8f8790", "bdb5b6"), spec=(0.45, 30), grain=(0.03, 3)),
    # translucent capsule glass over the soul light (glow=0 on a shape: plain dark glass)
    "capsule":     dict(ramp=ramp("0f1418", "1a2228", "28343b", "3a4a52", "566b73", "7f979d"), glow="soul", spec=(0.7, 60), grain=(0.0, 4)),
    # Region A (the Habitation Rings). livery_dk: the ring corridors' painted panels, one ramp step under the
    # enemies' livery (the prototype read too bright and flat at range - guide 15.8)
    "livery_dk":   dict(ramp=ramp("1b1d22", "2e3137", "45484d", "63666a", "85878a", "a9a9a6"), spec=(0.30, 22), grain=(0.035, 4)),
    # paint_teal: the medical wing's painted "care" band - a muted facility teal, never a saturated green/blue
    "paint_teal":  dict(ramp=ramp("0d1a1d", "16292d", "21393e", "2f4c51", "426267", "5d7d80"), spec=(0.25, 20), grain=(0.03, 4)),
    # insulation: containment's quilted insulated panels - cool grey canvas, matte
    "insulation":  dict(ramp=ramp("111418", "1d2227", "2c3238", "3e454b", "545b60", "6f7579"), spec=(0.06, 10), grain=(0.05, 2.5)),
    # frost: the pale rime on cold seams and frosted glass - cold grey-white, glossy, never pure white
    "frost":       dict(ramp=ramp("1e262b", "36434a", "55646b", "77878d", "9eacb0", "c4cfd1"), spec=(0.50, 30), grain=(0.05, 2)),
    # cloth: the crew's left-behind fabric - faded, dusty, low saturation
    "cloth":       dict(ramp=ramp("16130f", "28221c", "3d342b", "54493d", "6d6052", "897b6b"), spec=(0.03, 6), grain=(0.08, 1.5)),
    # grime: the dirt at a panel's foot and under a seep - a warm dark grey within two steps of livery_dk (R-2)
    "grime":       dict(ramp=ramp("15140f", "26241f", "39372f", "4e4b42", "666256", "7f7a6c"), spec=(0.08, 10), grain=(0.06, 2)),
    # KEYCARD inks: the exact keycard colours (RenderConstants.KEYCARD_AURA_*: red 1.00/0.18/0.16, yellow
    # 1.00/0.86/0.18, blue 0.22/0.58/1.00), emissive, so no mood shifts them. Only on keycard doors (guide 11.1).
    # The upper three steps ARE the exact colour; the lower ones only shade a lamp's rim.
    # steel_lt: the DOORS' brushed light steel - near white, cool, glossy; off every wall's value band (doors are landmarks)
    "steel_lt":    dict(ramp=ramp("30353b", "5a6168", "8a9198", "b5bbc0", "d8dcdf", "f1f3f4"), spec=(0.55, 40), grain=(0.015, 6)),
    "key_red":     dict(ramp=ramp("4d0e0c", "801714", "bf2320", "ff2e29", "ff2e29", "ff2e29"), emissive=True),
    "key_yellow":  dict(ramp=ramp("4d420e", "806e17", "bfa423", "ffdb2e", "ffdb2e", "ffdb2e"), emissive=True),
    "key_blue":    dict(ramp=ramp("112d4d", "1c4a80", "2a6fbf", "3894ff", "3894ff", "3894ff"), emissive=True),
    # THE UNDEAD (generated-enemy-art order 6). Appended LAST, like every addition before them, so each earlier material
    # keeps its index and every earlier sprite regenerates byte-identical. The undead are the facility's own staff, and
    # (owner, 2026-10-22) NO human face is ever visible: every one is sealed in what it wore to work. SOUL (above,
    # wall-art order 1) is the family's glow ink: pale, cold, low saturation, never the purple "special" colour.
    # overalls: the Organization work coverall / hazard suit - a desaturated slate, matte canvas
    "overalls":   dict(ramp=ramp("101318", "1c2129", "2b323b", "3e4650", "57606a", "767f88"), spec=(0.06, 10), grain=(0.06, 2)),
    # hazmat: quarantine rubberised suit - a dull olive-grey (never green, G5), a soft rubber sheen
    "hazmat":     dict(ramp=ramp("131411", "232420", "36372f", "4c4d43", "67675b", "878776"), spec=(0.22, 14), grain=(0.04, 3)),
    # labcoat: the researchers' coat - an off-white one step under livery, cloth, matte, dusty
    "labcoat":    dict(ramp=ramp("1f2124", "3b3e42", "5e6165", "86888a", "adadab", "cfcdc6"), spec=(0.05, 8), grain=(0.06, 2)),
    # visor: smoked respirator / helmet glass - dark, cold, very glossy (the soul light shows through it as added eyes)
    "visor":      dict(ramp=ramp("0a0e13", "141d26", "203039", "31464f", "4a6670", "73919a"), spec=(0.85, 60), grain=(0.0, 4)),
    # deadskin: ash grey with a cold undertone - only ever at a torn cuff or a split seam, never a face
    "deadskin":   dict(ramp=ramp("17171b", "2a2a30", "403f45", "5a585c", "777377", "979195"), spec=(0.10, 12), grain=(0.05, 2)),
    # SPORE pods: a translucent swollen membrane over the soul light (the Blight Corruptor)
    "spore":      dict(ramp=ramp("15161c", "262833", "3b3e4b", "555866", "737686", "9799a6"), glow="soul", spec=(0.45, 36), grain=(0.03, 3)),
    # REDEYE: the tall gas-masked Ghoul's eyes behind its single visor (owner request, 2026-10-22 - the one place an
    # undead burns red instead of soul-white: a deliberate, owner-approved exception to G5 / N4)
    "redeye":     dict(ramp=ramp("3a0806", "6e0f0b", "a8180f", "e0301c", "ff7356", "ffd2c4"), emissive=True),
    # NEON PROP INKS (generated-prop-art order 1, props.py; guide section 14). Appended LAST, like every addition
    # before them, so each earlier material keeps its index and every enemy and wall regenerates byte-identical.
    # The owner's exception to V5 for environment props (generated-prop-art.txt D1): bright accents on the
    # emissive parts of a prop only - never on an enemy, never the keycard inks.
    # phosphor: a CRT terminal's green text
    "phosphor":    dict(ramp=ramp("021a08", "05401a", "0b7a2c", "19c24a", "7dff8c", "e8ffe8"), emissive=True),
    # neon_cyan: specimen fluid, ring lights, backlit keys - brighter and more saturated than the facility cyan
    "neon_cyan":   dict(ramp=ramp("003840", "00687a", "00a8c0", "1ae6ff", "8ef6ff", "effeff"), emissive=True),
    # rad: the radioactive lime of hazard lamps and warning strips
    "rad":         dict(ramp=ramp("263606", "4a6a0a", "80b014", "b8f020", "e2ff70", "faffdc"), emissive=True),
    # neon_violet: the exit portal's energy
    "neon_violet": dict(ramp=ramp("240634", "4a0c66", "8016b0", "b840ff", "e09cff", "fbeaff"), emissive=True),
}

# The wet sheen's glint colour: near-white with the slightest warmth (never pure white: the hit flash owns it).
WET_GLINT = np.array([236.0, 230.0, 222.0])

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


def spike(x1, y1, x2, y2, r, mat, bend=0.0, tip=0.4, segments=5, edge=None, edge_side=1.0, **kw):
    """A tapered SPIKE / BLADE from its base (x1, y1) to its tip (x2, y2), generated-enemy-art order 4.
    The spine is a quadratic curve whose control point sits `bend` px from the midpoint along (-dy, dx)
    (the base->tip direction turned a quarter: for a spike pointing DOWN the screen, + bows it to the
    screen-left). The radius falls from r at the base to `tip` at the point, fast near the tip, so the base
    stays thick (thin parts vanish first at range - guide 5.4). `edge` paints a raised cutting edge of that
    material along one flank (edge_side +1 = the (-dy, dx) side, -1 = the other): the bone-pale blade edge.
    Returns a list of shapes (a chain), so it rides any transform like every other part."""
    dx, dy = x2 - x1, y2 - y1
    length = math.hypot(dx, dy) or 1e-6
    nx, ny = -dy / length, dx / length
    cx, cy = (x1 + x2) / 2 + nx * bend, (y1 + y2) / 2 + ny * bend
    points, radii, normals = [], [], []
    for i in range(segments + 1):
        t = i / segments
        px = (1 - t) ** 2 * x1 + 2 * (1 - t) * t * cx + t * t * x2
        py = (1 - t) ** 2 * y1 + 2 * (1 - t) * t * cy + t * t * y2
        tx = 2 * (1 - t) * (cx - x1) + 2 * t * (x2 - cx)
        ty = 2 * (1 - t) * (cy - y1) + 2 * t * (y2 - cy)
        tl = math.hypot(tx, ty) or 1e-6
        points.append((px, py)); normals.append((-ty / tl, tx / tl))
        radii.append(r * (1 - t) ** 0.9 + tip * t)
    shapes = [limb(*points[i], *points[i + 1], radii[i], radii[i + 1], mat, **kw) for i in range(segments)]
    if edge:
        for i in range(segments):
            off = [(points[j][0] + normals[j][0] * edge_side * 0.55 * radii[j],
                    points[j][1] + normals[j][1] * edge_side * 0.55 * radii[j]) for j in (i, i + 1)]
            shapes.append(limb(*off[0], *off[1], 0.35 * radii[i] + 0.45, 0.35 * radii[i + 1] + 0.45, edge,
                               op="add", depth=0.6 + 0.12 * radii[i]))
    return shapes


def riveted_plate(points, mat, rivet_mat="darkmetal", spacing=8.0, inset=2.6, rivet_r=1.25, bevel=2.5, **kw):
    """A riveted ARMOUR PLATE (generated-enemy-art order 4): a bevelled slab plus rivets set `inset` px in
    from its edge, one at every corner and then every ~`spacing` px along each side. The rivets are raised
    `add` details, so they ride the plate through any transform. Keyword args (op, tilt, prio, k, slope)
    go to the slab: op="add" fits the plate ON a body (it follows the body's curve, edges step up);
    a union slab in its own layer reads as a separate, liftable piece."""
    pts = np.array(points, float)
    centre = pts.mean(axis=0)
    shapes = [slab(points, mat, bevel=bevel, **kw)]
    rivet_points = []
    n = len(pts)
    for i in range(n):
        a, b = pts[i], pts[(i + 1) % n]
        a_in = a + (centre - a) / (np.linalg.norm(centre - a) or 1) * inset * 1.4
        b_in = b + (centre - b) / (np.linalg.norm(centre - b) or 1) * inset * 1.4
        steps = max(1, int(round(np.linalg.norm(b - a) / spacing)))
        for s in range(steps):
            rivet_points.append(a_in + (b_in - a_in) * (s / steps))
    for x, y in rivet_points:
        shapes.append(blob(float(x), float(y), rivet_r, rivet_r, rivet_mat, op="add", depth=rivet_r * 0.9))
    return shapes


def eyeball(cx, cy, r, look=(0.0, 0.0), iris=0.55, pupil=0.42, slit=1.0, lid_top=0.0, lid_bottom=0.0, lids=False,
            sclera="sclera", iris_mat="ichor", lid_mat="flesh", glow=1.0, glint=True, depth=None, prio=0.0, k=4.0):
    """An EYEBALL (generated-enemy-art order 5): a wet sclera dome, an emissive iris (the family ink), a pupil and
    a catch-light glint, optionally with fleshy lids. All in ONE layer with the flesh around it.
      look       (x, y) where the iris points, as a fraction of the free travel (|look| <= 1); the iris
                 foreshortens along that direction, as a disc turning on a sphere does
      iris       iris radius / r;  pupil  pupil radius / iris radius
      slit       pupil WIDTH scale: 1 round, -> 0 a vertical line (the Eye Tyrant's narrowing telegraph)
      lid_top    fraction of the eye's height the upper lid covers (0 open .. 1 shut); lid_bottom from below.
                 With lids=True the lids are always built (at 0 they sit as a fold above / below the eye),
                 so a lid that opens never pops in or out of existence between frames.
      glow       the iris light (0 dead, 1 rest, > 1 flare - with_glow semantics)
    The glint does not follow `look`: it is the light's reflection, fixed toward the key light (upper left).
    Returns a list of shapes (ride any transform; keep them in one layer)."""
    depth = depth if depth is not None else r * 0.85
    shapes = [blob(cx, cy, r, r, sclera, depth=depth, prio=prio, k=k)]
    lx, ly = look
    reach = math.hypot(lx, ly)
    if reach > 1.0:
        lx, ly = lx / reach, ly / reach
        reach = 1.0
    ri = r * iris
    travel = r - ri * 0.9
    ix, iy = cx + lx * travel, cy + ly * travel
    squash = math.sqrt(max(0.15, 1.0 - 0.55 * reach * reach))
    angle = math.atan2(ly, lx) if reach > 1e-6 else 0.0
    shapes.append(blob(ix, iy, ri * squash, ri, iris_mat, op="add", depth=ri * 0.30, angle=angle, glow=glow))
    rp = ri * pupil
    shapes.append(blob(ix, iy, max(0.45, rp * slit * squash), rp, "pupil", op="add", depth=rp * 0.25))
    if glint:
        shapes.append(blob(cx - r * 0.34, cy - r * 0.40, max(0.8, r * 0.09), max(0.8, r * 0.08), "flare",
                           op="add", depth=0.6, glow=1.2))
    # A lid is a CAP of flesh: its outer edge sits a thin fold beyond the eye's rim, its inner edge at the lid line,
    # so at 0 it is the crease of a lid drawn back and at 1 it covers the eye (two lids meeting at 0.5 each).
    fold = r * 0.30
    if lids or lid_top > 0.01:
        outer, inner = cy - r - fold, cy - r + 2 * r * min(lid_top, 1.0)
        ry = max(1.0, (inner - outer) / 2)
        shapes.append(blob(cx, (outer + inner) / 2, r * 1.10, ry, lid_mat, depth=depth * 1.1 + fold * 0.3,
                           prio=prio + 40, k=k))
    if lids or lid_bottom > 0.01:
        outer, inner = cy + r + fold * 0.8, cy + r - 2 * r * min(lid_bottom, 1.0)
        ry = max(1.0, (outer - inner) / 2)
        shapes.append(blob(cx, (outer + inner) / 2, r * 1.06, ry, lid_mat, depth=depth * 1.05 + fold * 0.3,
                           prio=prio + 40, k=k))
    return shapes


def teeth_row(x1, y1, x2, y2, count, length, r, mat="bone", side=1.0, sag=0.0, rake=0.0, jitter=0.25, seed=0,
              tip=0.3, k=1.0, **kw):
    """A TEETH ROW (generated-enemy-art order 5): `count` tapered teeth set along the jaw line (x1, y1) -> (x2, y2),
    each pointing along the line's normal (-dy, dx) * side. `sag` bows the jaw line (px along that normal, at the
    middle), `rake` leans every tooth toward the far end (radians), `jitter` varies length and spacing (seeded, so
    deterministic: a row of identical teeth reads as a zip). Each tooth is a spike(), so it rides any transform."""
    import random
    rng = random.Random(seed)
    dx, dy = x2 - x1, y2 - y1
    length_line = math.hypot(dx, dy) or 1e-6
    nx, ny = -dy / length_line * side, dx / length_line * side
    shapes = []
    for i in range(count):
        t = (i + 0.5 + rng.uniform(-jitter, jitter) * 0.6) / count
        bow = sag * 4 * t * (1 - t)
        bx, by = x1 + dx * t + nx * bow, y1 + dy * t + ny * bow
        size = 1.0 + rng.uniform(-jitter, jitter)
        ca, sa = math.cos(rake), math.sin(rake)
        tx, ty = nx * ca - ny * sa, nx * sa + ny * ca
        shapes += spike(bx, by, bx + tx * length * size, by + ty * length * size, r * (0.85 + 0.3 * size), mat,
                        tip=tip, segments=3, k=k, **kw)
    return shapes


def fold_lines(x1, y1, x2, y2, count, length, width=0.9, mat="overalls", angle=0.5, jitter=0.3, seed=0, side=1.0):
    """CLOTH FOLDS (generated-enemy-art order 6): `count` short carved creases set along (x1, y1) -> (x2, y2), each
    `length` px, leaning `angle` radians off the line's normal (x side), seeded jitter on spacing, length and lean
    (deterministic). Carves, so pass them in the cloth's layer; they ride any transform like every other shape.
    A crease is what turns a smooth capsule into fabric: bunching at the elbow, pulled diagonals across a chest."""
    import random
    rng = random.Random(seed)
    dx, dy = x2 - x1, y2 - y1
    line = math.hypot(dx, dy) or 1e-6
    ux, uy = dx / line, dy / line
    nx, ny = -uy * side, ux * side
    out = []
    for i in range(count):
        t = (i + 0.5 + rng.uniform(-jitter, jitter)) / count
        bx, by = x1 + dx * t, y1 + dy * t
        a = angle * (1 + rng.uniform(-jitter, jitter))
        cx, cy = nx * math.cos(a) + ux * math.sin(a), ny * math.cos(a) + uy * math.sin(a)
        half = length * (1 + rng.uniform(-jitter, jitter)) / 2
        out.append(limb(bx - cx * half, by - cy * half, bx + cx * half, by + cy * half, width, width * 0.6, mat, op="carve"))
    return out


class Humanoid:
    """THE HUMANOID RIG (generated-enemy-art order 6): one skeleton, many bodies. Proportions are bone lengths in
    canvas px; a pose is joint ANGLES (radians), so a walk, a reach or a collapse is keyframes, never a new drawing.

    Conventions (front-facing billboard, image space, y DOWN):
      - every limb angle is measured from STRAIGHT DOWN and is MIRRORED per side: + swings the limb OUTWARD, away
        from the body's centre line, for the screen-left ("l_") and the screen-right ("r_") limb alike, so one key
        set reads on both sides;
      - a child angle (elbow, wrist, knee, ankle) is RELATIVE to its parent; + bends outward, - inward (an elbow
        folding the hand in across the chest is negative);
      - `torso` leans the whole upper body about the pelvis (+ = its top to the screen-right, clockwise); `chest`
        bends the upper spine on top of it; `neck` and `head` likewise;
      - foreshortening: `<limb>_fore` (0..1) shortens a segment pointing toward or away from the viewer (a reach at
        the camera, a knee lifted in a step): length x (1 - fore x 0.75). `<side>_upper_fore` shortens the UPPER ARM
        only (the elbow brought forward at the camera), so a fist raised straight up the front keeps a full-length
        forearm and a full-size hand - shortening the whole arm crushes it into a stub (lesson 12.21).
    joints(p, prefix="") reads pose parameters prefix + name from the pose `p`, with 0 for any it does not find, and
    returns a dict of named canvas points: pelvis, chest, neck, head (the head CENTRE), l_shoulder, l_elbow, l_wrist,
    l_hand (the end of the hand), l_hip, l_knee, l_ankle, l_toe (and r_...), plus the absolute angle of every hand
    and foot ("l_hand_angle" ...). The rig then hangs its own shapes on those points."""

    PARAMS = ("bx", "by", "torso", "chest", "neck", "head",
              "l_sh", "l_el", "l_wr", "r_sh", "r_el", "r_wr", "l_hip", "l_knee", "l_ank", "r_hip", "r_knee", "r_ank",
              "l_arm_fore", "r_arm_fore", "l_leg_fore", "r_leg_fore", "squat", "l_upper_fore", "r_upper_fore")

    def __init__(self, pelvis=(80.0, 120.0), spine=50.0, neck=10.0, head=10.0, shoulder_half=16.0, hip_half=8.0,
                 upper_arm=34.0, fore_arm=32.0, hand=14.0, thigh=50.0, shin=50.0, foot=10.0,
                 arm_rest=0.10, leg_rest=0.03):
        self.pelvis, self.spine, self.neck_len, self.head_len = pelvis, spine, neck, head
        self.shoulder_half, self.hip_half = shoulder_half, hip_half
        self.upper_arm, self.fore_arm, self.hand = upper_arm, fore_arm, hand
        self.thigh, self.shin, self.foot = thigh, shin, foot
        self.arm_rest, self.leg_rest = arm_rest, leg_rest

    @staticmethod
    def params(**defaults):
        """The rig's pose defaults for every skeleton parameter (0 unless given), to merge into a Rig's defaults."""
        out = {name: 0.0 for name in Humanoid.PARAMS}
        out.update(defaults)
        return out

    @staticmethod
    def _dir(angle, side):
        # side -1 = screen-left (outward = toward -x), +1 = screen-right
        return (side * math.sin(angle), math.cos(angle))

    def joints(self, p, prefix=""):
        g = lambda name: float(p.get(prefix + name, 0.0)) if hasattr(p, "get") else 0.0
        out = {}
        squat = g("squat")                                        # 0 stand .. 1 knees folded (legs shorten, pelvis drops)
        px, py = self.pelvis[0] + g("bx"), self.pelvis[1] + g("by")
        out["pelvis"] = (px, py)
        torso, chest = g("torso"), g("chest")
        up = lambda a: (math.sin(a), -math.cos(a))                # an UP vector leaned clockwise by a
        mid_angle = torso
        top_angle = torso + chest
        mid = (px + up(mid_angle)[0] * self.spine * 0.5, py + up(mid_angle)[1] * self.spine * 0.5)
        ch = (mid[0] + up(top_angle)[0] * self.spine * 0.5, mid[1] + up(top_angle)[1] * self.spine * 0.5)
        out["waist"], out["chest"] = mid, ch
        neck_angle = top_angle + g("neck")
        nk = (ch[0] + up(neck_angle)[0] * self.neck_len, ch[1] + up(neck_angle)[1] * self.neck_len)
        head_angle = neck_angle + g("head")
        out["neck"] = nk
        out["head"] = (nk[0] + up(head_angle)[0] * self.head_len, nk[1] + up(head_angle)[1] * self.head_len)
        out["head_angle"] = head_angle
        out["chest_angle"] = top_angle
        # shoulders sit on the line through the chest point, perpendicular to the upper spine
        across = (math.cos(top_angle), math.sin(top_angle))
        hips_across = (math.cos(torso * 0.35), math.sin(torso * 0.35))
        for side, s in (("l", -1), ("r", 1)):
            sh = (ch[0] + across[0] * self.shoulder_half * s, ch[1] + across[1] * self.shoulder_half * s)
            # the upper body's lean carries the arm: outward for one side is the lean's sense on the other
            base = top_angle * (-s)
            a1 = base + self.arm_rest + g(f"{side}_sh")
            fore = 1 - 0.75 * g(f"{side}_arm_fore")
            d = self._dir(a1, s)
            upper = self.upper_arm * fore * (1 - 0.75 * g(f"{side}_upper_fore"))
            el = (sh[0] + d[0] * upper, sh[1] + d[1] * upper)
            a2 = a1 + g(f"{side}_el")
            d = self._dir(a2, s)
            wr = (el[0] + d[0] * self.fore_arm * fore, el[1] + d[1] * self.fore_arm * fore)
            a3 = a2 + g(f"{side}_wr")
            d = self._dir(a3, s)
            hd = (wr[0] + d[0] * self.hand * (0.4 + 0.6 * fore), wr[1] + d[1] * self.hand * (0.4 + 0.6 * fore))
            out.update({f"{side}_shoulder": sh, f"{side}_elbow": el, f"{side}_wrist": wr, f"{side}_hand": hd,
                        f"{side}_hand_angle": a3})
            hp = (px + hips_across[0] * self.hip_half * s, py + hips_across[1] * self.hip_half * s)
            lf = 1 - 0.75 * g(f"{side}_leg_fore")
            b1 = self.leg_rest + g(f"{side}_hip")
            d = self._dir(b1, s)
            thigh = self.thigh * lf * (1 - 0.35 * squat)
            kn = (hp[0] + d[0] * thigh, hp[1] + d[1] * thigh)
            b2 = b1 + g(f"{side}_knee")
            d = self._dir(b2, s)
            shin = self.shin * (1 - 0.30 * squat)
            an = (kn[0] + d[0] * shin, kn[1] + d[1] * shin)
            b3 = g(f"{side}_ank")
            # the foot points outward-and-toward the viewer: a short stub turned off the shin
            ft_angle = b2 + 1.35 + b3
            d = self._dir(ft_angle, s)
            to = (an[0] + d[0] * self.foot, an[1] + d[1] * self.foot * 0.35)
            out.update({f"{side}_hip": hp, f"{side}_knee": kn, f"{side}_ankle": an, f"{side}_toe": to,
                        f"{side}_foot_angle": ft_angle})
        return out


def hand(wrist, angle, side, length, mat, fingers=4, finger_length=None, finger_r=1.3, palm_r=None, spread=0.35,
         curl=0.0, thumb=True, k=2.0, **kw):
    """A HAND (generated-enemy-art order 6) hung on a Humanoid's wrist: a palm along the hand's direction and
    `fingers` chained fingers fanned `spread` radians around it; `curl` (0 open .. 1 a fist / a grab) bends each
    finger's two outer joints inward. Long, thin fingers are what make a hand read at range - give a reaching hand
    finger_length > palm. `angle` / `side` are the joint's absolute angle and side (-1 screen-left, +1 right), as
    Humanoid.joints reports them. -> a list of shapes (keep them in the forearm's layer)."""
    finger_length = finger_length if finger_length is not None else length * 0.9
    palm_r = palm_r if palm_r is not None else finger_r * 2.2
    d = (side * math.sin(angle), math.cos(angle))
    palm_end = (wrist[0] + d[0] * length * 0.55, wrist[1] + d[1] * length * 0.55)
    shapes = [limb(*wrist, *palm_end, palm_r * 0.9, palm_r, mat, k=k, **kw)]
    for i in range(fingers):
        f = (i - (fingers - 1) / 2) / max(1, (fingers - 1) / 2)          # -1 .. 1 across the hand
        a = angle + f * spread * 0.5
        base = (palm_end[0] + side * math.cos(angle) * f * palm_r * 0.75, palm_end[1] - math.sin(angle) * f * palm_r * 0.75)
        seg = finger_length * (1 - 0.18 * abs(f)) / 3
        pts = [base]
        cur = a
        for j in range(3):
            cur = cur - curl * (0.55 + 0.25 * j)                          # each knuckle bends inward
            dd = (side * math.sin(cur), math.cos(cur))
            pts.append((pts[-1][0] + dd[0] * seg, pts[-1][1] + dd[1] * seg))
        shapes += chain(pts, finger_r, finger_r * 0.7, mat, k=1.2, **kw)
    if thumb:
        a = angle - 0.9 - 0.4 * curl
        dd = (side * math.sin(a), math.cos(a))
        tb = (wrist[0] + d[0] * length * 0.25, wrist[1] + d[1] * length * 0.25)
        shapes += chain([tb, (tb[0] + dd[0] * finger_length * 0.35, tb[1] + dd[1] * finger_length * 0.35),
                         (tb[0] + dd[0] * finger_length * 0.6 + d[0] * finger_length * 0.15,
                          tb[1] + dd[1] * finger_length * 0.6 + d[1] * finger_length * 0.15)],
                        finger_r * 1.05, finger_r * 0.75, mat, k=1.2, **kw)
    return shapes


def lag(source, param, delay, duration, out_param=None, fps=12, loop=True, default=0.0):
    """CLOTH LAG (generated-enemy-art order 6): keys for "<param>_lag" = the param's own track `delay` seconds LATE
    (a hem, a sleeve, a hood that follows its limb a frame behind). Unlike jiggle() it has no spring - it is the
    same motion, late, which is what loose cloth over a moving limb does. A loop wraps (the late copy of the start
    is the end of the period). Merge into the clip's keys."""
    drive = source if callable(source) else (lambda t: source.value(param, t, default))
    out_param = out_param or f"{param}_lag"
    n = max(2, int(round(duration * fps)))
    keys = []
    for i in range(n + (0 if loop else 1)):
        t = duration * i / n
        late = t - delay
        late = late % duration if loop else max(0.0, late)
        keys.append((t, {out_param: drive(late)}, "linear"))
    return keys


def chamfer_rect(x0, y0, x1, y1, c):
    """The corner points of a rectangle with 45-degree chamfered corners (generated-wall-art order 1) - the
    UiKit panel shape, so walls and the interface chrome rhyme. Feed it to slab() / riveted_plate()."""
    return [(x0 + c, y0), (x1 - c, y0), (x1, y0 + c), (x1, y1 - c), (x1 - c, y1), (x0 + c, y1), (x0, y1 - c), (x0, y0 + c)]


def tile_copies(shapes, period, columns=3, rows=1):
    """TILING (generated-wall-art order 1): stamp shapes at every (column x period, row x period) offset on a
    canvas `columns` x `rows` periods large. Rendered on that canvas and cropped to the middle period, the
    result tiles seamlessly: the smooth max, the per-shape grain (it rides each copy's own frame), the cavity
    blur (np.roll wraps a periodic canvas) and the Bayer dither (period % 4 == 0) are all translation-
    invariant. Walls tile horizontally (columns=3, rows=1); floors and ceilings in both axes (3 x 3)."""
    out = []
    for row in range(rows):
        for column in range(columns):
            out += Xf.move(column * period, row * period)(shapes)
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
    def segment(rest_a, rest_b, new_a, new_b, stretch=True):
        """The similarity that carries the rest segment a->b onto the posed one (for parts riding a limb).
        stretch=False keeps the part's size (it only turns and moves with the segment)."""
        ra = math.atan2(rest_b[1] - rest_a[1], rest_b[0] - rest_a[0])
        na = math.atan2(new_b[1] - new_a[1], new_b[0] - new_a[0])
        rl = math.hypot(rest_b[0] - rest_a[0], rest_b[1] - rest_a[1]) or 1e-6
        nl = math.hypot(new_b[0] - new_a[0], new_b[1] - new_a[1])
        return (Xf.move(-rest_a[0], -rest_a[1]).then(Xf.turn(na - ra)).then(Xf.scale(nl / rl))
                .then(Xf.move(*new_a))) if stretch else \
            Xf.move(-rest_a[0], -rest_a[1]).then(Xf.turn(na - ra)).then(Xf.move(*new_a))

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
def render(layers, width, height, seed=3, glow_radius=5, glow_alpha=0.55, return_glow=False, smooth=0):
    """layers: list of lists of Shape, back to front. Returns an RGBA PIL image, or (image, glow image)
    when return_glow is set: the glow image holds the emissive pixels only (binary alpha).

    smooth=N (N >= 1; the wall faces): the SMOOTH bake. No Bayer dither, no surface grain, no lonely-pixel
    cleanup, a continuous ramp (interpolated between its six steps instead of snapped to one) and a soft
    specular edge, rendered at N x N samples per pixel and box-filtered down so every edge and diagonal is
    anti-aliased. The sprite look (dither, grain, hard ramp steps) reads as noise on a wall seen in the 3D
    view; the smooth bake keeps the same shapes, light rig and ramps with none of the speckle."""
    ss = max(int(smooth), 1)
    out_w, out_h = width, height
    width, height = width * ss, height * ss
    Y, X = (np.mgrid[0:height, 0:width].astype(float) + 0.5) / ss
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
                if not smooth:
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
        arr[:] = (fwd - bwd) * 0.5 * ss
    N = np.dstack([-gx, -gy, np.ones_like(gx)])
    N /= np.linalg.norm(N, axis=2, keepdims=True)

    diffuse = np.clip(N @ LIGHT, 0, 1)
    wrap = np.clip((N @ LIGHT + 0.35) / 1.35, 0, 1)             # soft terminator
    rim = (np.clip(N @ RIM, 0, 1) ** 2) * (1 - N[..., 2]) ** 0.6
    # cavity occlusion: lower than the neighbourhood -> darker
    cav = np.clip((_blur(Hn, 3 * ss) - Hn) / 6.0, 0, 1)
    # cast occlusion: pixels just behind a nearer layer
    occl = np.zeros((height, width))
    for d in range(1, 3 * ss + 1):
        for sx, sy in ((0, -d), (d, -d), (-d, -d), (d, 0), (-d, 0)):
            occl = np.maximum(occl, (_shift(layer_id, sx, sy, -1) > layer_id) * (1 - (d - 1) / (3 * ss)))
    bay = np.zeros((height, width)) if smooth else BAYER[Y.astype(int) % 4, X.astype(int) % 4]

    def ramp_colour(r, v):
        """The ramp at v: snapped to a step (the sprite look) or interpolated between steps (smooth)."""
        if not smooth:
            return r[(v * len(r)).astype(int)]
        pos = np.clip(v * len(r) - 0.5, 0, len(r) - 1)
        lo = np.floor(pos).astype(int); hi = np.minimum(lo + 1, len(r) - 1)
        f = (pos - lo)[:, None]
        return r[lo] * (1 - f) + r[hi] * f

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
        rgb[sel] = ramp_colour(r, v)
        if not m.get("emissive"):
            strength, shiny = m.get("spec", (0, 1))
            sp = strength * np.clip(N[sel] @ HALF, 0, 1) ** shiny
            col = rgb[sel]
            if smooth:        # a soft highlight edge instead of a hard threshold
                hot_w = np.clip((sp - 0.22) / 0.12, 0, 1)[:, None]
                col = col * (1 - hot_w) + (col * 0.35 + r[-1] * 0.65 + 25) * hot_w
            else:
                hot = sp > 0.28
                col[hot] = col[hot] * 0.35 + r[-1] * 0.65 + 25
            if "wet" in m:    # WET SHEEN (order 5): a tight second lobe -> a near-white glint on the lit curve
                wet_strength, wet_shiny = m["wet"]
                wsp = wet_strength * np.clip(N[sel] @ HALF, 0, 1) ** wet_shiny
                glint = wsp > 0.22
                col[glint] = col[glint] * 0.25 + WET_GLINT * 0.75
                shade_idx[sel] = np.where(glint, shade_idx[sel] + 1000, shade_idx[sel])   # its own shade for cleanup
            rgb[sel] = col + (rim[sel][:, None] * RIM_COLOR * 140)
            if "glow" in m:   # translucent membrane: the interior glow shows through, strongest where thick
                gl = MATERIALS[m["glow"]]["ramp"]
                level = np.clip(Gfinal[sel], 0, 1.4)
                t = np.clip((Ifinal[sel] ** 0.8 * 1.05 + bay[sel] * 0.1) * level, 0, 0.999)
                gcol = ramp_colour(gl, t)
                rgb[sel] = rgb[sel] * (1 - 0.65 * t[:, None]) + gcol * 0.65 * t[:, None]
                emissive_mask |= sel & (Ifinal > 0.55) & (Gfinal > 0.5)
                # the shade index must see the glow, or cleanup would merge lit and unlit membrane
                shade_idx[sel] = shade_idx[sel] + 100 * (t * 6).astype(int)

    # cleanup: an isolated pixel (all 4 neighbours agree on another shade) takes the neighbours' colour
    for _ in range(0 if smooth else 2):
        n = [_shift(shade_idx, sx, sy, -2) for sx, sy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
        lonely = solid & (n[0] == n[1]) & (n[1] == n[2]) & (n[2] == n[3]) & (n[0] != shade_idx) & (n[0] >= 0)
        if lonely.any():
            src = _shift(rgb, 1, 0, 0)
            rgb[lonely] = src[lonely]; shade_idx[lonely] = n[0][lonely]

    # interior edge where a nearer layer overlaps: darken the far side
    edge = np.zeros((height, width), bool)     # one pixel wide at any sample rate
    for sx, sy in [(ux * d, uy * d) for ux, uy in ((1, 0), (-1, 0), (0, 1), (0, -1)) for d in range(1, ss + 1)]:
        nb = _shift(layer_id, sx, sy, -1)
        edge |= solid & (nb > layer_id) & ~emissive_mask & ~_shift(emissive_mask, sx, sy, False)
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

    glow_rgb = out[..., :3]
    if ss > 1:   # box-filter the samples down to pixels (colour weighted by coverage); a pixel glows when
                 # at least half its samples do, in the mean colour of those samples (binary alpha kept)
        def down(a):
            return a.reshape(out_h, ss, out_w, ss, *a.shape[2:]).mean(axis=(1, 3))
        alpha = out[..., 3:] / 255.0
        cover = down(alpha)
        rgb_down = down(out[..., :3] * alpha) / np.maximum(cover, 1e-6)
        em = emissive_mask[..., None].astype(float)
        em_frac = down(em)
        glow_rgb = down(out[..., :3] * em) / np.maximum(em_frac, 1e-6)
        emissive_mask = em_frac[..., 0] >= 0.5
        Mfinal = Mfinal[ss // 2::ss, ss // 2::ss]
        height, width = out_h, out_w
        out = np.zeros((height, width, 4))
        out[..., :3] = rgb_down; out[..., 3] = cover[..., 0] * 255

    glow_img = None
    if return_glow:
        g = np.zeros((height, width, 4))
        g[emissive_mask, :3] = glow_rgb[emissive_mask]; g[emissive_mask, 3] = 255
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
                "K": "101101110101101", "W": "101101101111101", "R": "110101110101101", "E": "111100110100111",
                "P": "110101110100100",
                # order 5 (the Aberrations): tank, specimen, pen and sump marks
                "T": "111010010010010", "S": "111100111001111", "G": "111100101101111", "M": "101111111101101",
                "A": "010101111101101", "B": "110101110101110",
                # generated-wall-art order 1: the rest of the capitals, for wall stencils and signage
                "C": "111100100100111", "H": "101101111101101", "I": "111010010010111", "L": "100100100100111",
                "N": "110101101101101", "O": "010101101101010", "U": "101101101101111", "V": "101101101101010",
                "Y": "101101010010010", "F": "111100110100100", "X": "101101010101101", "Z": "111001010100111",
                "J": "001001001101111", "Q": "010101101110011"}


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

    def transformed(self, xf):
        """A copy carried through one more transform - layers, anchors and stencil positions (generated-enemy-art
        order 6: a whole-body slump or collapse applied after the rig has posed everything)."""
        return Figure([xf(layer) for layer in self.layers], {n: xf.pt(a) for n, a in self.anchors.items()},
                      [(text, *xf.pt((x, y)), colour) for text, x, y, colour in self.stencils])


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
    defaults. `overrides` pin parameters to a constant for the whole clip. `events` = {time_seconds:
    name} marks the frame the runtime acts on ("emit": a ranged shot leaves the muzzle anchor;
    "impact": a melee hit lands) -- written to the manifest on the frame sampled nearest that time."""

    def __init__(self, name, keys, duration=None, fps=12, mode="loop", variant_of=None, overrides=None,
                 events=None):
        assert mode in MODES, mode
        self.events = dict(events or {})
        self.name, self.fps, self.mode, self.variant_of = name, fps, mode, variant_of
        self.keys = sorted([(float(k[0]), dict(k[1]), k[2] if len(k) > 2 else "inout") for k in keys],
                           key=lambda k: k[0])
        for _, _, ease in self.keys:
            assert ease in EASINGS, ease
        self.duration = float(duration if duration is not None else self.keys[-1][0])
        self.overrides = dict(overrides or {})

    def variant(self, name, speed=1.0, keys=(), fps=None, **overrides):
        """A variant of this clip: time stretched by 1/speed, extra keys merged in, parameters pinned.
        A slowed variant usually lowers fps too, so it has no more frames than its source; its frames are
        still new atlas frames whenever its pinned parameters change the picture (DORMANT's dimmed eyes)."""
        scaled_keys = [(t / speed, v, e) for t, v, e in self.keys]
        merged = dict(self.overrides); merged.update(overrides)
        return Clip(name, scaled_keys + list(keys), self.duration / speed, fps or self.fps, self.mode,
                    self.variant_of or self.name, merged, {t / speed: e for t, e in self.events.items()})

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
        times = self.times()
        names = set(rig.defaults)
        out = []
        for t in times:
            values = {p: self.value(p, t, rig.defaults[p]) for p in names}
            out.append((rig.pose(**values), self.duration / len(times)))
        return out

    def times(self):
        """The sample times of sample(), in order."""
        n = max(2, int(round(self.duration * self.fps)))
        if self.mode == "loop":
            return [self.duration * i / n for i in range(n)]
        return [self.duration * i / (n - 1) for i in range(n)]

    def event_at_sample(self):
        """{sample index: event name}: each event lands on the sample nearest its time."""
        times = self.times()
        out = {}
        for t, name in sorted(self.events.items()):
            i = min(range(len(times)), key=lambda j: (abs(times[j] - t), j))
            out[i] = name
        return out


def jiggle(source, param, duration, out_param=None, freq=4.0, damping=0.35, gain=1.0, fps=24, loop=True,
           default=0.0):
    """SECONDARY MOTION (generated-enemy-art order 5): a damped spring that LAGS its parent mass. `source` is a
    Clip (or any callable t -> value); its `param` track drives a spring (natural frequency `freq` Hz, damping
    ratio `damping`), and the keys returned put the spring's OFFSET from its parent - (follower - parent) x gain -
    on `out_param` (default "<param>_jig"). Merge them into a clip: flesh rolls after the breath, a belly sloshes
    after the step, overshooting and settling.
    Every frame is still rendered from its pose alone (a rig has no memory), so the lag is baked into keys here:
    the spring is integrated at 240 Hz and sampled at `fps`. A loop is integrated over four periods and the last
    is kept, so it starts in its steady state and wraps without a seam."""
    drive = source if callable(source) else (lambda t: source.value(param, t, default))
    out_param = out_param or f"{param}_jig"
    omega = 2 * math.pi * freq
    dt = 1.0 / 240
    periods = 4 if loop else 1
    n = max(2, int(round(duration * fps)))
    times = [duration * i / n for i in range(n + 1)]
    last_start = duration * (periods - 1)
    y, v = drive(0.0), 0.0
    keys, next_index = [], 0
    for step in range(int(round(duration * periods / dt)) + 1):
        t = step * dt
        local = t - duration * math.floor(t / duration + 1e-9) if loop else t
        parent = drive(local)
        while next_index < len(times) and t - last_start >= times[next_index] - dt * 0.5:
            keys.append((times[next_index], {out_param: (y - parent) * gain}, "linear"))
            next_index += 1
        acceleration = omega * omega * (parent - y) - 2 * damping * omega * v
        v += acceleration * dt
        y += v * dt
    return keys


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
    list [(unique_index, duration, anchors, event)]. Consecutive identical frames merge into one longer
    frame, unless the later one carries an event."""
    cache = {}
    uniques = []          # [(rgba_trimmed, glow_trimmed, (left, top))]
    unique_index = {}     # bytes signature -> index
    baked = []
    for clip in clips:
        frames = []
        events = clip.event_at_sample()
        for sample_index, (pose, duration) in enumerate(clip.sample(rig)):
            event = events.get(sample_index)
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
            if frames and frames[-1][0] == idx and frames[-1][2] == anchors and event is None:
                frames[-1] = (idx, frames[-1][1] + duration, anchors, frames[-1][3])
            else:
                frames.append((idx, duration, anchors, event))
        baked.append((clip, frames))
        log(f"  {rig.name}.{clip.name}: {len(frames)} frames, {clip.duration:.2f} s, {clip.mode}")
    return uniques, baked


def _check_edges(rig, clip, img, log):
    a = np.asarray(img.getchannel("A"))
    edges = [n for n, e in (("top", a[0]), ("bottom", a[-1]), ("left", a[:, 0]), ("right", a[:, -1])) if e.any()]
    if edges:
        log(f"  WARNING {rig.name}.{clip.name}: a part touches the canvas edge ({', '.join(edges)})"
            f" -- clamp the pose, never grow the canvas")


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
        for idx, duration, anchors, event in frames:
            rgba, _, (left, top), _, _ = uniques[idx]
            x, y = positions[idx]
            frame = {
                "rect": [x, y, rgba.width, rgba.height],
                "offset": [left, top],
                "duration": _r(duration),
                "glow": has_glow[idx],
                "anchors": {k: [_r(v[0], 1), _r(v[1], 1)] for k, v in sorted(anchors.items())},
            }
            if event:
                frame["event"] = event
            entry["frames"].append(frame)
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
        for idx, duration, _, _ in frames:
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
        for col, (idx, duration, anchors, event) in enumerate(frames):
            im = _frame_on_canvas(rig, uniques, idx).convert("RGB").resize((fw, fh), Image.NEAREST)
            x = label_w + pad + col * (fw + pad)
            sheet.paste(im, (x, y))
            draw.rectangle([x, y, x + fw - 1, y + fh - 1], outline=(40, 48, 58))
            draw.text((x + 4, y + fh + 1), f"{int(round(duration * 1000))}ms" + (f"  {event.upper()}" if event else ""),
                      fill=(250, 90, 200) if event else (120, 130, 140))
            for name, (ax, ay) in anchors.items():
                px, py = x + ax * strip_scale, y + ay * strip_scale
                draw.line([px - 3, py, px + 3, py], fill=(250, 90, 200)); draw.line([px, py - 3, px, py + 3], fill=(250, 90, 200))
    sheet.save(os.path.join(preview_dir, f"{rig.name}_strip.png"), optimize=False)
