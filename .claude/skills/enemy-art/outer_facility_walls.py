"""REGION A - THE OUTER FACILITY / the Habitation Rings (mood cool_steel). The region set's builders and its
REGIONS row (walls.py). Design brief: generated-wall-art-order-1.txt VISUAL DESIGN + docs/wall-art-creation-
guide.txt section 16 A.

An evacuated company town. Everything is Organization issue and recently lived in; the wrongness is small -
notices torn off, name plates scratched out, a hairline seep at a seam on the deep floors.

TRIM LINES (every A wall honours them; a room wall changes only what is BETWEEN them):
  cornice 0-30 (gunmetal beam)  cable tray 24-33 (cable bundle + rod)  panel field 40-196
  bump rail 205 (rubber)        kick plate 214-255 (riveted gunmetal)
"""
import random

import kit
from kit import blob, limb, slab, chain, chamfer_rect
from walls import Face, wall, backing, band, rail, panel, pipe_run, grille

LETTER = "a"
SEED = 101                     # ONE render seed for the whole set: the shared trim gets the same grain on every
                               # face, so two different walls meet with the same pixels along their trim lines
TRIM = {"cornice": [0, 30], "cableTray": [24, 33], "panelField": [40, 196], "bumpRail": 205, "kickPlate": [214, 255]}
BREACH = {"base": 0.0, "step": 0.03}
STENCIL = (70, 74, 82)         # dark stencil paint on the livery panels
STENCIL_LT = (150, 156, 164)   # pale stencil paint on dark metal

# Region A's own environment materials during development (moved into kit.py before shipping - CP4)
kit.MATERIALS.setdefault("grime", dict(ramp=kit.ramp("15140f", "26241f", "39372f", "4e4b42", "666256", "7f7a6c"),
                                       spec=(0.08, 10), grain=(0.06, 2)))


# ---------------------------------------------------------------------------------------------------
# The shared grammar
# ---------------------------------------------------------------------------------------------------
def trim(bracket_xs=(40, 216)):
    """The region's trim: the backing, the cornice beam, the riveted kick plate (layer 0) and, in front, the
    cable tray and the bump rail on its brackets (layer 1). Every A wall starts from this."""
    l0 = [backing("darkmetal", bevel=0.5),
          band(-4, 30, "gunmetal", bevel=3, slope=0.9, prio=3),
          band(214, 249, "gunmetal", bevel=3, slope=0.9, prio=3),             # the kick plate ...
          band(251, 260, "gunmetal", bevel=1.5, slope=0.9, prio=3)]           # ... and the floor strip: the joint
    for x in range(16, 256, 32):                                          # kick-plate rivets, period 32
        l0.append(blob(x, 222, 1.8, 1.8, "darkmetal", op="add", depth=1.6))
    l1 = [rail(205, 6, "rubber"),                                         # the bump rail
          rail(24, 4, "rubber"),                                          # the cable bundle in the tray
          rail(33, 2.5, "gunmetal")]
    for x in bracket_xs:
        l1.append(slab([(x - 5, 199), (x + 5, 199), (x + 5, 214), (x - 5, 214)], "gunmetal", bevel=2, k=0))
    return l0, l1


def grime_foot(rnd, x0, x1, y=192, amount=1.0):
    """Grime at a panel's foot: low raised smears in the grime material, just above the rail (lesson 15.8).
    Low contrast by construction (R-2): the grime ramp sits within two steps of livery_dk."""
    out = []
    x = x0 + rnd.uniform(2, 8)
    while x < x1 - 6:
        w = rnd.uniform(6, 16) * amount
        out.append(blob(min(x + w / 2, x1 - 4), y + rnd.uniform(-1.5, 2.5), w / 2, rnd.uniform(1.6, 3.2),
                        "grime", op="add", depth=0.25))
        x += w + rnd.uniform(4, 14)
    return out


def ring_panels(l0, rnd, panels=((4, 124), (132, 252)), vents=(4, 4), field=(40, 196), seam=112, grime=1.0,
                inset=(150, 186)):
    """The hall's two painted livery panels: chamfered like the UI plates, a mid seam, a lower inset with
    vent slots, grime at the foot."""
    for (x0, x1), slots in zip(panels, vents):
        l0.append(panel(x0, field[0], x1, field[1], "livery_dk", chamfer=10, bevel=5, slope=0.8, k=0, prio=2))
        l0.append(slab(chamfer_rect(x0 + 16, inset[0], x1 - 16, inset[1], 4), "livery_dk", bevel=2, slope=0.6, op="carve"))
        for row in range(slots):
            y = inset[0] + 7 + row * 7
            l0.append(limb(x0 + 24, y, x1 - 24, y, 1.6, 1.6, "darkmetal", op="carve"))
        l0.append(limb(x0 + 3, seam, x1 - 3, seam, 1.2, 1.2, "livery_dk", op="carve"))
        if grime:
            l0 += grime_foot(rnd, x0 + 4, x1 - 4, field[1] - 5, grime)


# ---------------------------------------------------------------------------------------------------
# HALL
# ---------------------------------------------------------------------------------------------------
HALL_BASE_SERIALS = (("R-07", "07"), ("R-08", "08"), ("R-12", "12"))


def hall_base(variant):
    """HALL BASE (the approved ring panel): two livery panels, vent inset, bump rail, kick plate. Variants are
    seeded differences that do not move the eye: a dent, a different vent count, a scuffed kick plate, the
    serial number. NO focal point (E4)."""
    def build(glow):
        rnd = random.Random(1000 + variant)
        l0, l1 = trim()
        ring_panels(l0, rnd, vents=((4, 4), (4, 3), (3, 4))[variant])
        if variant == 1:                                                   # a shallow dent in the right panel
            l0.append(blob(198, 84, 9, 6, "livery_dk", depth=0.5, op="carve", angle=0.3))
        if variant == 2:                                                   # scuffs on the kick plate
            for x, d in ((58, 9), (71, 6), (180, 11)):
                l0.append(limb(x, 236, x + d, 233, 0.8, 0.8, "gunmetal", op="carve"))
        left, right = HALL_BASE_SERIALS[variant]
        return Face([l0, l1], stencils=[(left, 22, 50, STENCIL), (right, 146, 50, STENCIL)])
    return build


def hall_structure(glow):
    """STRUCTURE: a gunmetal pilaster with a ring serial, the rail running behind it. The rhythm of a hall run
    (every 4th tile) and the frame of every door."""
    rnd = random.Random(1010)
    l0, l1 = trim(bracket_xs=(30, 226))
    ring_panels(l0, rnd, panels=((10, 90), (166, 246)), vents=(3, 3))
    pil = [panel(98, 22, 158, 216, "gunmetal", chamfer=6, bevel=5, slope=0.9, k=0, prio=5)]
    pil.append(limb(128, 46, 128, 196, 2.2, 2.2, "gunmetal", op="carve"))             # the centre groove
    for y in (40, 70, 100, 130, 160, 190):                                           # rivets down both flanks
        pil.append(blob(106, y, 1.7, 1.7, "darkmetal", op="add", depth=1.5))
        pil.append(blob(150, y, 1.7, 1.7, "darkmetal", op="add", depth=1.5))
    pil.append(slab(chamfer_rect(92, 14, 164, 30, 4), "gunmetal", bevel=3, slope=0.9, k=0, prio=6))    # capital
    pil.append(slab(chamfer_rect(92, 208, 164, 226, 4), "gunmetal", bevel=3, slope=0.9, k=0, prio=6))  # foot
    return Face([l0, l1, pil], stencils=[("R1", 113, 172, STENCIL_LT), ("S-07", 112, 180, STENCIL_LT)], focal=pil)


def hall_light(glow):
    """LIGHT: a recessed cyan strip lamp in the panel field behind a wire cage. Some flicker at runtime (R7)."""
    rnd = random.Random(1020)
    l0, l1 = trim()
    ring_panels(l0, rnd)
    housing = slab(chamfer_rect(58, 54, 198, 96, 6), "darkmetal", bevel=4, slope=0.8, op="carve")
    l0.append(housing)
    lamp = [slab(chamfer_rect(66, 62, 190, 88, 4), "darkmetal", bevel=2, slope=0.7, k=0),
            limb(76, 75, 180, 75, 4.5, 4.5, "cyan", glow=glow),                     # the tube
            limb(70, 75, 76, 75, 5.5, 5.5, "gunmetal"), limb(180, 75, 186, 75, 5.5, 5.5, "gunmetal")]   # end caps
    cage = [limb(64, 60, 192, 60, 1.3, 1.3, "gunmetal"), limb(64, 90, 192, 90, 1.3, 1.3, "gunmetal")]
    for x in range(80, 181, 20):
        cage.append(limb(x, 60, x, 90, 1.2, 1.2, "gunmetal"))
    return Face([l0, lamp, cage, l1], stencils=[("R-07", 22, 102, STENCIL)], focal=[housing] + lamp + cage)


def hall_service(glow):
    """SERVICE: an exposed riser pipe pair through a cut-out in the panels, with clamps and a valve wheel."""
    rnd = random.Random(1030)
    l0, l1 = trim()
    ring_panels(l0, rnd)
    cut = [slab(chamfer_rect(82, 44, 174, 194, 8), "gunmetal", bevel=3, slope=0.9, k=0, prio=4),
           slab(chamfer_rect(88, 50, 168, 188, 6), "darkmetal", bevel=4, slope=0.8, op="carve")]
    l0 += cut
    pipes = pipe_run([(110, 30), (110, 210)], 8, "gunmetal", clamps=((110, 62), (110, 170)), clamp_w=3)
    pipes += pipe_run([(146, 30), (146, 120), (150, 132), (150, 210)], 5.5, "darkmetal", clamp_mat="gunmetal",
                      clamps=((146, 62), (150, 170)), clamp_w=3)
    wheel = [blob(110, 116, 15, 15, "rust", depth=3), blob(110, 116, 11, 11, "rust", op="carve", depth=3),
             blob(110, 116, 4, 4, "gunmetal", depth=4)]
    for dx, dy in ((0, -1), (0.87, 0.5), (-0.87, 0.5)):
        wheel.append(limb(110, 116, 110 + dx * 12, 116 + dy * 12, 1.6, 1.6, "rust"))
    return Face([l0, pipes, wheel, l1], stencils=[("C-2", 156, 182, STENCIL_LT)], focal=cut + pipes + wheel)


def hall_vent(glow):
    """VENT: a full-height access hatch with a grille and a pull handle."""
    rnd = random.Random(1040)
    l0, l1 = trim()
    frame = [slab(chamfer_rect(36, 38, 220, 198, 10), "darkmetal", bevel=3, slope=0.8, k=0, prio=2)]
    hatch = [panel(44, 44, 212, 192, "livery_dk", chamfer=8, bevel=5, slope=0.8, k=0, prio=3)]
    hatch.append(slab(chamfer_rect(60, 58, 196, 124, 4), "livery_dk", bevel=2, slope=0.7, op="carve"))
    hatch += grille(66, 62, 190, 120, 8, r=2.0)
    for y in (64, 172):                                                              # hinges
        hatch.append(slab([(44, y - 7), (54, y - 7), (54, y + 7), (44, y + 7)], "gunmetal", bevel=2, k=0, prio=5))
    hatch += grime_foot(rnd, 48, 208, 186)
    handle = [limb(192, 136, 192, 174, 3.0, 3.0, "gunmetal"), limb(184, 136, 192, 136, 2.2, 2.2, "gunmetal"),
              limb(184, 174, 192, 174, 2.2, 2.2, "gunmetal")]
    return Face([l0 + frame + hatch, handle, l1], stencils=[("V-3", 66, 140, STENCIL)], focal=frame + hatch + handle)


def hall_marker(glow):
    """MARKER: an evacuation board - empty frames, torn notices, an arrow, "R1". Story you notice (W8)."""
    rnd = random.Random(1050)
    l0, l1 = trim()
    ring_panels(l0, rnd)
    board = [slab(chamfer_rect(30, 46, 226, 190, 8), "gunmetal", bevel=4, slope=0.9, k=0),
             slab(chamfer_rect(38, 54, 218, 182, 6), "gunmetal", bevel=2, slope=0.8, op="carve"),
             slab(chamfer_rect(38, 54, 218, 182, 6), "darkmetal", bevel=1.5, slope=0.8, op="add", depth=0.4)]
    marks = []
    for x0, y0 in ((50, 64), (98, 64)):                                              # two empty frames
        marks.append(slab([(x0, y0), (x0 + 38, y0), (x0 + 38, y0 + 46), (x0, y0 + 46)], "gunmetal", bevel=2, op="add", depth=1.2))
        marks.append(slab([(x0 + 4, y0 + 4), (x0 + 34, y0 + 4), (x0 + 34, y0 + 42), (x0 + 4, y0 + 42)], "gunmetal", bevel=1.5, op="carve"))
    # torn notices: only the strips under their pins are left
    for x0, y0, w in ((150, 62, 52), (54, 124, 40), (104, 126, 30)):
        pts = [(x0, y0), (x0 + w, y0)]
        x = x0 + w
        while x > x0:
            x -= rnd.uniform(5, 10)
            pts.append((max(x, x0), y0 + rnd.uniform(6, 16)))
        marks.append(slab(pts, "livery_dk", bevel=1.2, slope=1.0, op="add", depth=0.8))
        marks.append(blob(x0 + 4, y0 + 3, 1.6, 1.6, "rust", op="add", depth=1.2))
        marks.append(blob(x0 + w - 4, y0 + 3, 1.6, 1.6, "rust", op="add", depth=1.2))
    # the arrow (painted on: a thin raised livery shape), pointing along the ring
    arrow = [(150, 150), (176, 136), (176, 145), (204, 145), (204, 155), (176, 155), (176, 164)]
    marks.append(slab(arrow, "livery_dk", bevel=1.0, slope=1.0, op="add", depth=0.6))
    return Face([l0, board + marks, l1], stencils=[("R1", 150, 98, STENCIL_LT, 3), ("EVAC", 150, 120, STENCIL_LT),
                                                    ("R1-07", 52, 170, STENCIL_LT)], focal=board + marks)


def hall_breach(glow):
    """BREACH: the base panel with a cracked seam weeping a thin ichor line (emissive, 1-2 px) - region B
    leaking upward. The seep is focal, kept inside the face."""
    def build():
        rnd = random.Random(1060)
        l0, l1 = trim()
        ring_panels(l0, rnd)
        crack = chain([(140, 112), (151, 106), (162, 113), (170, 108), (181, 116)], 1.6, 1.0, "darkmetal", op="carve")
        crack += chain([(162, 113), (165, 126), (161, 138)], 1.3, 0.8, "darkmetal", op="carve")
        seep = chain([(162, 114), (163, 130), (161, 146), (163, 162), (162, 178), (163, 194)], 1.3, 0.9, "ichor",
                     op="add", k=2, glow=0.95 * glow)
        stain = [blob(163, 154, 3.5, 40, "grime", op="add", depth=0.25)]
        l0 += crack + stain + seep
        return Face([l0, l1], stencils=[("R-07", 22, 50, STENCIL), ("07", 146, 50, STENCIL)], focal=crack + seep + stain)
    return build()


# ---------------------------------------------------------------------------------------------------
# The region row (walls.py REGIONS)
# ---------------------------------------------------------------------------------------------------
FLICKER = [(1.0, 0.40), (0.25, 0.08), (1.0, 0.20), (0.0, 0.12)]   # R7: a failing tube

WALLS = [
    wall(LETTER, "HALL", "BASE", hall_base(0), variant=0),
    wall(LETTER, "HALL", "BASE", hall_base(1), variant=1),
    wall(LETTER, "HALL", "BASE", hall_base(2), variant=2),
    wall(LETTER, "HALL", "STRUCTURE", hall_structure),
    wall(LETTER, "HALL", "LIGHT", hall_light, frames=FLICKER, animate="some"),
    wall(LETTER, "HALL", "SERVICE", hall_service),
    wall(LETTER, "HALL", "VENT", hall_vent),
    wall(LETTER, "HALL", "MARKER", hall_marker),
    wall(LETTER, "HALL", "BREACH", hall_breach),
]

REGION = dict(letter=LETTER, seed=SEED, trim=TRIM, breach=BREACH, walls=WALLS, doors=[], columns=[],
              surfaces={"floor": None, "ceiling": None})
