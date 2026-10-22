"""REGION A - THE OUTER FACILITY / the Habitation Rings (mood cool_steel). The region set's builders and its
REGIONS row (walls.py). Design brief: generated-wall-art-order-1.txt VISUAL DESIGN + docs/wall-art-creation-
guide.txt section 16 A.

An evacuated company town. Everything is Organization issue and recently lived in; the wrongness is small -
notices torn off, name plates scratched out, a hairline seep at a seam on the deep floors.

TRIM LINES (every A wall honours them; a room wall changes only what is BETWEEN them):
  cornice 0-30 (gunmetal beam)  cable tray 24-33 (cable bundle + rod)  panel field 40-196
  bump rail 205 (rubber)        kick plate 214-255 (riveted gunmetal)
"""
import math
import random

import kit
from kit import blob, limb, slab, chain, chamfer_rect, riveted_plate
from walls import Face, Entry, wall, backing, band, rail, panel, pipe_run, grille, lock_panel, \
    grammar2d, surface_backing

LETTER = "a"
SEED = 101                     # ONE render seed for the whole set: the shared trim gets the same grain on every
                               # face, so two different walls meet with the same pixels along their trim lines
TRIM = {"cornice": [0, 30], "cableTray": [24, 33], "panelField": [40, 196], "bumpRail": 205, "kickPlate": [214, 255]}
BREACH = {"base": 0.0, "step": 0.03}
STENCIL = (70, 74, 82)         # dark stencil paint on the livery panels
STENCIL_LT = (150, 156, 164)   # pale stencil paint on dark metal

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
# ROOM SETS - same trim, a different building between the lines (R5: read by shape and material)
# ---------------------------------------------------------------------------------------------------
def rounded_rect(x0, y0, x1, y1, r, steps=4):
    """Corner points of a rectangle with round corners (radius r)."""
    pts = []
    for cx, cy, a0 in ((x1 - r, y0 + r, -90), (x1 - r, y1 - r, 0), (x0 + r, y1 - r, 90), (x0 + r, y0 + r, 180)):
        for k in range(steps + 1):
            a = math.radians(a0 + 90 * k / steps)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


# MEDICAL - clean white livery, rounded corners, a muted teal "care" band at rail height (NO cross)
def medical_base(variant):
    def build(glow):
        l0, l1 = trim(bracket_xs=(64, 192))
        l0.append(slab(rounded_rect(6, 40, 250, 182, 16), "livery", bevel=5, slope=0.7, k=0, prio=2))
        for x in (87, 169):                                                      # wipe-clean panel joints
            l0.append(limb(x, 48, x, 174, 1.0, 1.0, "livery", op="carve"))
        for x, y in ((18, 52), (238, 52), (18, 170), (238, 170)):                 # flush fasteners
            l0.append(blob(x, y, 2.2, 2.2, "livery", op="carve", depth=0.8))
        l0.append(band(186, 197, "paint_teal", bevel=2, slope=0.9, prio=3))     # the care band
        stencils = [("M-04" if variant == 0 else "M-09", 20, 160, STENCIL)]
        if variant == 1:                                                          # a scuffed fastener, a swab mark
            l0.append(limb(176, 128, 190, 124, 0.7, 0.7, "livery", op="carve"))
        return Face([l0, l1], stencils=stencils)
    return build


def medical_accent(glow):
    """ACCENT: a wall dispenser unit beside a sealed scrub hatch."""
    l0, l1 = trim(bracket_xs=(64, 192))
    l0.append(slab(rounded_rect(6, 40, 250, 182, 16), "livery", bevel=5, slope=0.7, k=0, prio=2))
    l0.append(band(186, 197, "paint_teal", bevel=2, slope=0.9, prio=3))
    disp = [slab(rounded_rect(26, 58, 112, 172, 10), "livery", bevel=6, slope=0.9, k=0),
            slab(rounded_rect(40, 120, 98, 140, 4), "livery", bevel=3, slope=0.8, op="carve"),     # the dispensing slot
            slab(rounded_rect(36, 70, 102, 108, 5), "capsule", bevel=2, slope=0.8, op="add", depth=0.8, glow=0.0),   # a dark window
            blob(96, 158, 3.2, 3.2, "cyan", depth=2.5, op="add", glow=glow)]                      # the status lamp
    hatch = [slab(rounded_rect(134, 62, 230, 170, 12), "rubber", bevel=4, slope=0.9, k=0),
             slab(rounded_rect(140, 68, 224, 164, 9), "livery", bevel=5, slope=0.8, k=0, prio=2)]
    for x, y in ((146, 74), (218, 74), (146, 158), (218, 158)):                   # the seal dogs
        hatch.append(slab([(x - 4, y - 4), (x + 4, y - 4), (x + 4, y + 4), (x - 4, y + 4)], "gunmetal", bevel=2, op="add", depth=2))
    hatch.append(limb(160, 116, 204, 116, 2.4, 2.4, "gunmetal", op="add", depth=3))  # the bar handle
    return Face([l0, disp + hatch, l1], stencils=[("SCRUB", 162, 138, STENCIL)], focal=disp + hatch)


# SECURITY - dark gunmetal plating, a chevron-grooved lower half (carved, never painted yellow)
def security_base(variant):
    def build(glow):
        l0, l1 = trim()
        l0 += riveted_plate([(6, 40), (250, 40), (250, 112), (6, 112)], "gunmetal", spacing=30, inset=6, rivet_r=1.7,
                            bevel=4, k=0, prio=2)
        l0.append(slab([(6, 118), (250, 118), (250, 196), (6, 196)], "darkmetal", bevel=4, slope=0.9, k=0, prio=2))
        for n in range(8):                                                        # the chevrons, 30 px pitch
            cx = 16 + n * 30
            l0 += chain([(cx, 132), (cx + 12, 157), (cx, 182)], 2.2, 2.2, "darkmetal", op="carve")
        if variant == 1:
            l0.append(limb(150, 60, 196, 74, 0.8, 0.8, "gunmetal", op="carve"))    # a gouge
        return Face([l0, l1], stencils=[("SEC-2" if variant == 0 else "SEC-4", 16, 100, STENCIL_LT)])
    return build


def security_accent(glow):
    """ACCENT: a weapons locker niche behind a mesh - two rifles racked, a slot empty."""
    l0, l1 = trim()
    l0 += riveted_plate([(6, 40), (250, 40), (250, 112), (6, 112)], "gunmetal", spacing=30, inset=6, rivet_r=1.7,
                        bevel=4, k=0, prio=2)
    l0.append(slab([(6, 118), (250, 118), (250, 196), (6, 196)], "darkmetal", bevel=4, slope=0.9, k=0, prio=2))
    frame = [slab(chamfer_rect(44, 46, 212, 194, 8), "gunmetal", bevel=5, slope=0.9, k=0),
             slab(chamfer_rect(52, 54, 204, 186, 6), "gunmetal", bevel=6, slope=0.9, op="carve")]
    rack = []
    for x in (82, 128):                                                           # two rifles, a third slot empty
        rack += [limb(x, 70, x, 150, 3.2, 3.2, "darkmetal"), slab([(x - 6, 150), (x + 6, 150), (x + 7, 176), (x - 5, 176)],
                 "darkmetal", bevel=3, k=2), limb(x + 3, 112, x + 9, 122, 2.0, 2.0, "gunmetal")]
    rack += [limb(60, 66, 196, 66, 2.4, 2.4, "gunmetal"), limb(60, 178, 196, 178, 2.4, 2.4, "gunmetal")]
    rack.append(blob(174, 74, 4, 3, "darkmetal", op="add", depth=1))                # the empty slot's clip
    mesh = []
    for x in range(60, 200, 12):
        mesh.append(limb(x, 56, x, 184, 0.9, 0.9, "gunmetal"))
    for y in range(62, 184, 12):
        mesh.append(limb(54, y, 202, y, 0.9, 0.9, "gunmetal"))
    return Face([l0 + frame, rack, mesh, l1], stencils=[("ARMS", 172, 160, STENCIL_LT)], focal=frame + rack + mesh)


# CONTAINMENT - heavy insulated panels (quilted pads) with frost-pale seams
def containment_pads(l0, rnd, variant=0):
    for x0, x1 in ((6, 126), (130, 250)):
        for y0, y1 in ((40, 90), (94, 144), (148, 196)):
            l0.append(slab(rounded_rect(x0, y0, x1, y1, 8), "insulation", bevel=9, slope=0.45, k=0, prio=2))
            l0.append(blob((x0 + x1) / 2, (y0 + y1) / 2, 2.0, 2.0, "insulation", op="carve", depth=1.5))   # the quilt button
    for y in (92, 146):                                                           # rime in the inner seams
        l0.append(limb(14, y, 242, y, 1.3, 1.1, "frost", op="add", k=1))
    l0.append(limb(128, 46, 128, 190, 1.3, 1.1, "frost", op="add", k=1))


def containment_base(variant):
    def build(glow):
        rnd = random.Random(1400 + variant)
        l0, l1 = trim()
        containment_pads(l0, rnd, variant)
        if variant == 1:                                                          # one pad's stitched repair
            for k in range(5):
                l0.append(limb(176 + k * 6, 110, 176 + k * 6, 118, 0.7, 0.7, "insulation", op="carve"))
        return Face([l0, l1], stencils=[("C-11" if variant == 0 else "C-14", 16, 182, STENCIL)])
    return build


def containment_accent(glow):
    """ACCENT: a sealed porthole hatch with frosted glass, dogged shut."""
    rnd = random.Random(1410)
    l0, l1 = trim()
    containment_pads(l0, rnd)
    port = [blob(128, 116, 66, 66, "gunmetal", depth=7, k=0),
            blob(128, 116, 52, 52, "gunmetal", depth=6, op="carve"),
            blob(128, 116, 50, 50, "frost", depth=3, k=0, prio=3)]
    for k in range(8):
        a = math.radians(k * 45 + 22.5)
        port.append(blob(128 + math.cos(a) * 59, 116 + math.sin(a) * 59, 3.2, 3.2, "darkmetal", op="add", depth=2.5))
    for a0 in (200, 340):                                                         # frost crazing on the glass
        a = math.radians(a0)
        port.append(limb(128 + math.cos(a) * 12, 116 + math.sin(a) * 12, 128 + math.cos(a) * 44, 116 + math.sin(a) * 44,
                         0.8, 0.6, "frost", op="carve"))
    dogs = [limb(128, 44, 128, 58, 4, 4, "gunmetal"), limb(128, 174, 128, 188, 4, 4, "gunmetal"),
            blob(214, 70, 3.5, 3.5, "cyan", depth=2.5, glow=glow)]                # the seal's status lamp
    return Face([l0, port, dogs, l1], stencils=[("SEALED", 104, 196 - 12, STENCIL)], focal=port + dogs)


# MACHINERY - breaker cabinets and conduit bundles, darkmetal
def machinery_cabinets(l0, rnd, variant=0):
    for x0, x1 in ((6, 124), (132, 250)):
        l0.append(panel(x0, 66, x1, 196, "darkmetal", chamfer=4, bevel=5, slope=0.9, k=0, prio=2))
        l0.append(limb((x0 + x1) / 2, 72, (x0 + x1) / 2, 190, 1.2, 1.2, "darkmetal", op="carve"))   # the door split
        l0 += grille(x0 + 12, 74, x1 - 12, 96, 4, r=1.4)
        for hx in ((x0 + x1) / 2 - 7, (x0 + x1) / 2 + 7):
            l0.append(limb(hx, 132, hx, 148, 1.8, 1.8, "gunmetal", op="add", depth=1.5))           # handles


def machinery_conduits():
    return [rail(46, 4.2, "rubber"), rail(55, 3.4, "gunmetal"), rail(62, 2.6, "rubber")]


def machinery_base(variant):
    def build(glow):
        rnd = random.Random(1500 + variant)
        l0, l1 = trim()
        machinery_cabinets(l0, rnd, variant)
        clamps = [slab([(x - 3, 40), (x + 3, 40), (x + 3, 67), (x - 3, 67)], "gunmetal", bevel=3, slope=1.5, k=0)
                  for x in ((48, 208), (88, 176))[variant]]
        return Face([l0, machinery_conduits() + clamps, l1],
                    stencils=[("PWR-3" if variant == 0 else "PWR-5", 18, 182, STENCIL_LT)])
    return build


def machinery_accent(glow):
    """ACCENT: a big breaker panel - a lever, a row of breakers, a status lamp and a gauge."""
    rnd = random.Random(1510)
    l0, l1 = trim()
    machinery_cabinets(l0, rnd)
    box = [panel(36, 70, 220, 194, "gunmetal", chamfer=6, bevel=6, slope=0.9, k=0),
           slab(chamfer_rect(48, 100, 170, 140, 3), "gunmetal", bevel=2, slope=0.8, op="carve")]
    for k in range(6):                                                            # the breakers
        x = 56 + k * 19
        box.append(slab([(x, 106), (x + 11, 106), (x + 11, 134), (x, 134)], "darkmetal", bevel=2, slope=1.2, op="add", depth=2))
        box.append(slab([(x + 2, 108 + (k % 3) * 4), (x + 9, 108 + (k % 3) * 4), (x + 9, 116 + (k % 3) * 4), (x + 2, 116 + (k % 3) * 4)],
                        "livery_dk", bevel=1, op="add", depth=1.4))
    box += [blob(78, 166, 12, 12, "darkmetal", depth=4, op="add"), blob(78, 166, 8, 8, "livery", depth=2, op="add"),
            limb(78, 166, 84, 160, 0.9, 0.9, "darkmetal", op="add")]               # the gauge
    lever = [slab(chamfer_rect(184, 92, 206, 182, 4), "darkmetal", bevel=3, k=0),
             limb(195, 160, 195, 104, 3.2, 3.2, "gunmetal"), blob(195, 102, 6, 6, "rubber", depth=5),
             blob(140, 164, 3.5, 3.5, "cyan", depth=2.5, glow=glow)]
    return Face([l0, machinery_conduits() + box, lever, l1], stencils=[("MAIN", 120, 82, STENCIL_LT)], focal=box + lever)


# RESEARCH - dark narrow panels with thin cyan data seams (continuous lines: R-3)
def research_panels(l0, variant=0):
    xs = ((4, 66), (70, 126), (130, 192), (196, 252)) if variant == 0 else ((4, 90), (94, 160), (164, 252))
    for x0, x1 in xs:
        l0.append(panel(x0, 40, x1, 196, "darkmetal", chamfer=3, bevel=4, slope=0.9, k=0, prio=2))
        l0.append(limb(x0 + 6, 160, x1 - 6, 160, 0.9, 0.9, "darkmetal", op="carve"))


def research_seams(glow):
    return [rail(108, 1.1, "cyan", glow=0.8 * glow), rail(114, 0.8, "cyan", glow=0.55 * glow)]


def research_base(variant):
    def build(glow):
        l0, l1 = trim()
        research_panels(l0, variant)
        return Face([l0, research_seams(glow), l1], stencils=[("LAB-3" if variant == 0 else "LAB-6", 12, 182, STENCIL_LT)])
    return build


def research_accent(glow):
    """ACCENT: a wall terminal - a dark bezel, a few lines of cyan text, a keyboard ledge."""
    l0, l1 = trim()
    research_panels(l0)
    term = [panel(44, 46, 212, 152, "gunmetal", chamfer=8, bevel=6, slope=0.9, k=0),
            slab(chamfer_rect(56, 56, 200, 132, 4), "gunmetal", bevel=4, slope=0.9, op="carve"),
            slab(chamfer_rect(58, 58, 198, 130, 4), "pupil", bevel=1, slope=0.8, op="add", depth=0.5)]
    rnd = random.Random(1610)
    for k in range(6):                                                            # the text lines
        y = 68 + k * 10
        length = rnd.choice((40, 64, 88, 104, 52))
        term.append(limb(68, y, 68 + length, y, 1.3, 1.3, "cyan", op="add", glow=0.75 * glow, depth=0.8))
    ledge = [slab([(56, 158), (200, 158), (206, 186), (50, 186)], "darkmetal", bevel=3, slope=0.9, k=0)]
    for kx in range(10):
        for ky in range(2):
            ledge.append(slab([(64 + kx * 13, 164 + ky * 10), (73 + kx * 13, 164 + ky * 10), (73 + kx * 13, 171 + ky * 10),
                               (64 + kx * 13, 171 + ky * 10)], "gunmetal", bevel=1.5, op="add", depth=1.4))
    return Face([l0, research_seams(glow), term + ledge, l1], focal=term + ledge)


# STORAGE - corrugated roll-up shutters in guide channels
def storage_shutter(l0, y_bottom=192, gap=False):
    l0.append(slab([(6, 40), (250, 40), (250, 196), (6, 196)], "darkmetal", bevel=3, k=0, prio=1))
    for y in range(44, y_bottom, 9):                                              # the slats
        l0.append(slab([(14, y), (242, y), (242, y + 7), (14, y + 7)], "gunmetal", bevel=3.5, slope=1.0, k=0, prio=2))
    l0.append(slab([(12, y_bottom - 2), (244, y_bottom - 2), (244, y_bottom + 6), (12, y_bottom + 6)], "gunmetal",
                   bevel=4, slope=1.0, k=0, prio=3))                                # the bottom bar
    for x in (8, 248):
        l0.append(slab([(x - 5, 38), (x + 5, 38), (x + 5, 198), (x - 5, 198)], "darkmetal", bevel=4, slope=1.0, k=0, prio=4))


def storage_base(variant):
    def build(glow):
        l0, l1 = trim()
        storage_shutter(l0)
        if variant == 1:                                                          # a dented slat
            l0.append(blob(88, 103, 14, 4, "gunmetal", op="carve", depth=1.0))
        return Face([l0, l1], stencils=[("S-14" if variant == 0 else "S-17", 22, 80, STENCIL_LT)])
    return build


def storage_accent(glow):
    """ACCENT: a shutter rolled half up - a dark gap, a crate's edge inside."""
    l0, l1 = trim()
    storage_shutter(l0, y_bottom=116)
    inside = [slab([(16, 124), (240, 124), (240, 196), (16, 196)], "darkmetal", bevel=6, slope=0.4, op="carve"),
              slab(chamfer_rect(60, 150, 168, 198, 3), "livery_dk", bevel=3, slope=0.9, op="add", depth=2),
              limb(60, 170, 168, 170, 1.0, 1.0, "livery_dk", op="carve")]
    return Face([l0 + inside, l1], stencils=[("S-14", 22, 80, STENCIL_LT), ("C-0441", 76, 156, STENCIL)], focal=inside)


# SIGNATURE - CREW QUARTERS: rows of crew lockers, name plates scratched off
def lockers(l0, variant=0, ajar=None):
    for n, (x0, x1) in enumerate(((6, 64), (66, 124), (132, 190), (192, 250))):
        if n == ajar:
            continue
        l0.append(panel(x0, 40, x1, 196, "livery_dk", chamfer=3, bevel=4, slope=0.9, k=0, prio=2))
        l0 += grille(x0 + 10, 48, x1 - 10, 64, 3, r=1.2)
        plate = slab([(x0 + 12, 76), (x1 - 12, 76), (x1 - 12, 86), (x0 + 12, 86)], "livery", bevel=1.2, op="add", depth=1.0)
        l0.append(plate)
        rnd = random.Random(1700 + variant * 10 + n)
        for k in range(3):                                                        # the name scratched out
            y = 78 + rnd.uniform(0, 6)
            l0.append(limb(x0 + 14, y, x1 - 14, y + rnd.uniform(-2, 2), 0.6, 0.6, "livery", op="carve"))
        l0.append(limb(x1 - 10, 118, x1 - 10, 136, 1.6, 1.6, "gunmetal", op="add", depth=1.4))      # the latch


def signature_base(variant):
    def build(glow):
        l0, l1 = trim(bracket_xs=(65, 191))
        lockers(l0, variant)
        if variant == 1:                                                          # a pried latch
            l0.append(limb(178, 122, 184, 130, 0.8, 0.8, "livery_dk", op="carve"))
        return Face([l0, l1], stencils=[("Q7-1", 14, 182, STENCIL), ("Q7-2", 74, 182, STENCIL)])
    return build


def signature_accent(glow):
    """ACCENT: one locker door ajar - dark inside, a scarf left on its hook (no figure: E5)."""
    l0, l1 = trim(bracket_xs=(65, 191))
    lockers(l0, ajar=2)
    cavity = [slab(chamfer_rect(132, 40, 190, 196, 3), "darkmetal", bevel=5, slope=0.4, op="carve")]
    shelf = [slab([(134, 70), (188, 70), (188, 76), (134, 76)], "darkmetal", bevel=2, slope=1.0, k=0)]
    hook = [limb(160, 86, 160, 94, 1.6, 1.6, "gunmetal"), limb(160, 94, 165, 93, 1.4, 1.4, "gunmetal")]
    # the scarf: a flat folded band over the hook, two short tails with a fringe - cloth, nothing more
    scarf = [slab([(152, 92), (170, 92), (169, 99), (153, 99)], "cloth", bevel=1.6, slope=0.9, k=0),
             slab([(152, 97), (161, 97), (160, 142), (151, 141)], "cloth", bevel=1.6, slope=0.9, k=0),   # the long tail
             slab([(161, 97), (170, 97), (167, 112), (160, 108)], "cloth", bevel=1.6, slope=0.9, k=0)]   # the short fold
    for k in range(4):
        scarf.append(limb(151.5 + k * 2.4, 142, 151.5 + k * 2.4, 146, 0.5, 0.5, "cloth"))
    for y in (114, 120):                                                          # a faded stripe pair
        scarf.append(limb(153, y, 159, y, 0.6, 0.6, "cloth", op="carve"))
    hook = shelf + hook
    door = [slab([(190, 40), (214, 46), (214, 190), (190, 196)], "livery_dk", bevel=3, slope=0.9, k=0, prio=3)]
    door.append(limb(200, 60, 200, 176, 0.8, 0.8, "livery_dk", op="carve"))
    return Face([l0 + cavity, hook + scarf, door, l1], stencils=[("Q7-3", 136, 182, STENCIL)], focal=cavity + hook + scarf + door)


# ---------------------------------------------------------------------------------------------------
# DOORS - a painted livery sliding door with a porthole and rubber seal (an authored CLOSED door; the
# slide is the engine's). Keycard doors add the lock panel and a frame stripe in the exact keycard ink.
# ---------------------------------------------------------------------------------------------------
def door(ink=None):
    def build(glow):
        frame = [backing("darkmetal", bevel=0.5),
                 slab([(4, -4), (252, -4), (252, 38), (4, 38)], "gunmetal", bevel=4, slope=0.9, k=0, prio=3),   # header
                 slab([(4, -4), (30, -4), (30, 260), (4, 260)], "gunmetal", bevel=4, slope=0.9, k=0, prio=3),   # jambs
                 slab([(226, -4), (252, -4), (252, 260), (226, 260)], "gunmetal", bevel=4, slope=0.9, k=0, prio=3)]
        for y in range(54, 250, 40):
            frame += [blob(17, y, 1.8, 1.8, "darkmetal", op="add", depth=1.6), blob(239, y, 1.8, 1.8, "darkmetal", op="add", depth=1.6)]
        seal = [slab([(30, 38), (226, 38), (226, 258), (30, 258)], "rubber", bevel=3, slope=0.9, k=0)]
        leaf = [panel(36, 42, 220, 262, "livery_dk", chamfer=6, bevel=6, slope=0.9, k=0, prio=2),
                slab(chamfer_rect(46, 196, 210, 246, 4), "gunmetal", bevel=3, slope=0.9, op="add", depth=2.5),   # kick strip
                limb(60, 150, 108, 150, 3.0, 3.0, "livery_dk", op="carve")]                                         # grab recess
        for x in range(66, 200, 24):
            leaf.append(limb(x, 206, x + 10, 236, 1.2, 1.2, "gunmetal", op="carve"))
        leaf.append(blob(128, 98, 31, 31, "livery_dk", depth=5, op="carve"))                                 # porthole bore
        port = [blob(128, 98, 29, 29, "rubber", depth=4, k=0), blob(128, 98, 22, 22, "rubber", depth=4, op="carve"),
                blob(128, 98, 22, 22, "capsule", depth=2, k=0, prio=3, glow=0.0),
                limb(114, 86, 122, 81, 1.0, 1.0, "frost", op="add", depth=0.5)]                                    # a glint scratch
        stencils = [("R1-D", 46, 52, STENCIL)]
        if ink:
            lamp = lock_panel(196, 150, ink, w=26, h=42, glow=glow)
            frame.append(slab([(34, 24), (222, 24), (222, 31), (34, 31)], ink, bevel=1.5, slope=1.0, op="add", depth=1.2,
                              glow=glow))                                                                       # the frame stripe
        else:
            lamp = [slab(chamfer_rect(186, 138, 206, 162, 3), "darkmetal", bevel=2.5, slope=0.8, k=0),
                    blob(196, 150, 3.5, 3.5, "cyan", depth=2.5, op="add", glow=glow)]
        return Face([frame, seal, leaf, port, lamp], stencils=stencils)
    return build


# COLUMNS - drawn by the column path as a wall stripe, so authored as a 256x256 face
def column_round(glow):
    """A painted steel pillar with the bump rail ring: a cylinder (a vertical capsule as wide as the face)."""
    # a FACETED cylinder: eight painted staves, each a slab tilted to the cylinder's slope at its centre, so the
    # shading falls in clean bands (a smooth dome dithers into blotches across its slow gradient)
    body = []
    for n in range(8):
        x0, x1 = n * 32, n * 32 + 32
        u = ((x0 + x1) / 2 - 128) / 128
        body.append(slab([(x0, -4), (x1, -4), (x1, 260), (x0, 260)], "livery_dk", bevel=2.5, slope=16,
                         tilt=(-1.1 * u / math.sqrt(1 - u * u * 0.98), 0.0), k=0))
    body += [slab([(-4, -4), (260, -4), (260, 24), (-4, 24)], "gunmetal", bevel=2, slope=0.9, op="add", depth=3),
             slab([(-4, 199), (260, 199), (260, 211), (-4, 211)], "rubber", bevel=2, slope=0.9, op="add", depth=4),
             slab([(-4, 222), (260, 222), (260, 260), (-4, 260)], "gunmetal", bevel=2, slope=0.9, op="add", depth=3)]
    return Face([body], stencils=[("R1", 118, 120, STENCIL)])


def column_square(glow):
    """A boxed cable riser: steel corner posts, a ribbed access cover, the cables showing through a slot."""
    l0 = [backing("darkmetal", bevel=0.5), band(-4, 26, "gunmetal", bevel=3, prio=3), band(222, 260, "gunmetal", bevel=3, prio=3)]
    l0 += [slab([(4, -4), (28, -4), (28, 260), (4, 260)], "gunmetal", bevel=4, slope=0.9, k=0, prio=4),
           slab([(228, -4), (252, -4), (252, 260), (228, 260)], "gunmetal", bevel=4, slope=0.9, k=0, prio=4)]
    cover = [panel(36, 34, 220, 214, "livery_dk", chamfer=5, bevel=5, slope=0.9, k=0, prio=2),
             slab(chamfer_rect(110, 50, 146, 198, 4), "livery_dk", bevel=5, slope=0.8, op="carve")]
    cables = [limb(118, 30, 118, 218, 5, 5, "rubber"), limb(128, 30, 128, 218, 4.2, 4.2, "rubber"),
              limb(138, 30, 138, 218, 5, 5, "rubber")]
    for y in (70, 132, 180):
        cables.append(limb(108, y, 148, y, 2.2, 2.2, "gunmetal"))
    for x0, x1 in ((48, 100), (156, 208)):
        cover += grille(x0, 52, x1, 196, 9, r=1.3)
    return Face([l0, cover, cables], stencils=[("RSR-2", 52, 200, STENCIL)])


# ---------------------------------------------------------------------------------------------------
# SURFACES - 64x64, tiling in both axes, no glow. Floors 0.10-0.24, ceilings 0.06-0.18 mean luminance.
# ---------------------------------------------------------------------------------------------------
def floor():
    """Dark deck plates with a worn rubber runner: two plates per tile, bolts, the runner across them."""
    shapes = [surface_backing("darkmetal")]
    for y0, y1 in ((2, 30), (34, 62)):
        shapes.append(slab(chamfer_rect(2, y0, 62, y1, 2), "darkmetal", bevel=2.5, slope=0.8, k=0, prio=1))
        shapes += [blob(7, y0 + 5, 1.5, 1.5, "gunmetal", op="add", depth=1.0), blob(57, y1 - 5, 1.5, 1.5, "gunmetal", op="add", depth=1.0)]
    shapes += grammar2d([slab([(-6, 22), (70, 22), (70, 42), (-6, 42)], "rubber", bevel=1.4, slope=0.8, prio=3)])   # the runner
    for x, w in ((14, 9), (40, 6)):                                               # worn through to the plate
        shapes.append(blob(x, 32, w, 4, "rubber", op="carve", depth=0.7))
    return Face([shapes])


def ceiling():
    """Ceiling panels between a cable tray and a strip-lamp housing (the lens is dark: no surface glow)."""
    shapes = [surface_backing("concrete_dk"),
              slab(chamfer_rect(2, 2, 62, 40, 2), "concrete_dk", bevel=2, slope=0.8, k=0, prio=1)]
    shapes += grammar2d([slab([(-6, 44), (70, 44), (70, 62), (-6, 62)], "concrete_dk", bevel=2, slope=0.9, prio=2)])  # the tray
    shapes += grammar2d([limb(-8, 50, 72, 50, 2.6, 2.6, "rubber"), limb(-8, 56, 72, 56, 2.2, 2.2, "rubber")])
    shapes += [slab(chamfer_rect(16, 14, 48, 28, 3), "darkmetal", bevel=2.5, slope=0.9, op="add", depth=2),
               slab(chamfer_rect(20, 17, 44, 25, 2), "capsule", bevel=1.2, slope=0.8, op="add", depth=0.6, glow=0.0)]
    return Face([shapes])


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
ROOMS = (("MEDICAL", medical_base, medical_accent), ("SECURITY", security_base, security_accent),
         ("CONTAINMENT", containment_base, containment_accent), ("MACHINERY", machinery_base, machinery_accent),
         ("RESEARCH", research_base, research_accent), ("STORAGE", storage_base, storage_accent),
         ("SIGNATURE", signature_base, signature_accent))
for group, base, accent in ROOMS:
    WALLS += [wall(LETTER, group, "BASE", base(0), variant=0), wall(LETTER, group, "BASE", base(1), variant=1),
              wall(LETTER, group, "ACCENT", accent)]

DOORS = [Entry(f"door_{LETTER}_plain", door()), Entry(f"door_{LETTER}_red", door("key_red")),
         Entry(f"door_{LETTER}_yellow", door("key_yellow")), Entry(f"door_{LETTER}_blue", door("key_blue"))]
COLUMNS = [Entry(f"column_{LETTER}_round", column_round), Entry(f"column_{LETTER}_square", column_square)]

REGION = dict(letter=LETTER, seed=SEED, trim=TRIM, breach=BREACH, walls=WALLS, doors=DOORS, columns=COLUMNS,
              surfaces={"floor": floor, "ceiling": ceiling})
