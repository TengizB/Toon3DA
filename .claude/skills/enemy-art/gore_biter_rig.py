"""GORE BITER rig + clips (ABERRATION, CHAFF). Imported by bestiary.py.

A lean runner that is mostly mouth: the head splits VERTICALLY into a two-halved toothed jaw. A low forward
crouch on long digitigrade legs, the hunched back behind the head, ribs showing through thin flesh, a pen
ear-tag clipped to its left ear. Built to be seen five at a time, so it stays simple.
"""
import math

from kit import blob, limb, chain, spike, teeth_row, mirror, with_glow, Xf, Figure, Rig, Clip, jiggle, lerp, lerp_pt

W, H, AX = 160, 240, 80
HINGE = (80, 120)                 # the jaw halves part about the top of the head
HEAD = (80, 156)                  # the head's centre at rest

BITER_POSE = dict(
    cx=0.0, cy=0.0,                              # whole creature offset
    bx=0.0, by=0.0, bs=1.0, tilt=0.0,            # body (hunched back): offset, scale toward the viewer, roll
    hx=0.0, hy=0.0, hs=1.0,                      # head: offset, scale toward the viewer (the lunge)
    jaw=0.0,                                     # the split: 0 closed .. 1 wide (the two halves part)
    hackle=0.0, hackle_jig=0.0,                  # the spine ridge bristles; its lagging jiggle
    breath=0.0,                                  # ribs heave
    eye_glow=1.0, dim=0.0,
    fold=0.0,                                    # DEATH: legs fold, it drops onto its belly (0..1)
    **{f"{leg}_{axis}": 0.0 for leg in ("fl", "fr", "hl", "hr") for axis in ("x", "y")},   # foot offsets (x outward, y down)
    tag=1.0,
)

# left-side rest chains (the right side is the mirror): fore = beside the head, hind = digitigrade, splayed out
REST_FORE = [(56, 128), (44, 166), (46, 204), (42, 230)]
REST_HIND = [(52, 100), (22, 140), (32, 180), (20, 229)]


def _mirror_pt(q):
    return (2 * AX - q[0], q[1])


def _leg(rest, foot_dx, foot_dy, hip_xf, side, fold):
    """The hip rides the body, the foot stays planted (plus its offset); the joints follow both. `fold` folds
    the leg under the dropping body (death)."""
    rest = rest if side < 0 else [_mirror_pt(q) for q in rest]
    hip = hip_xf.pt(rest[0])
    hip_d = (hip[0] - rest[0][0], hip[1] - rest[0][1])
    foot_d = (side * foot_dx, foot_dy)
    weights = ((1.0, 0.0), (0.6, 0.4), (0.25, 0.75), (0.0, 1.0))
    pts = [(q[0] + wh * hip_d[0] + wf * foot_d[0], q[1] + wh * hip_d[1] + wf * foot_d[1]) for (wh, wf), q in zip(weights, rest)]
    pts[1] = (pts[1][0], pts[1][1] + 0.5 * min(foot_dy, 0.0))
    if fold > 0:
        for j in (1, 2, 3):
            pts[j] = (pts[j][0] + side * 10 * fold * (j / 3), pts[j][1] + (232 - 6 * (3 - j) - pts[j][1]) * fold * (0.4 + 0.2 * j))
    return pts


def _jaw_half(open_amount):
    """The LEFT jaw half (the right is its mirror): a tapered lobe hinged at the crown, a bony cheek ridge on the
    outside, the teeth along its inner edge pointing across the split, so a shut jaw is a zip of teeth.
    -> (half shapes, teeth shapes) in canvas space."""
    angle = 0.42 * open_amount
    xf = Xf.turn(angle, (77, HINGE[1] + 2))
    half = [
        blob(67, 146, 15, 25, "flesh", depth=16, k=8),
        limb(69, 160, 76, 196, 12, 4, "flesh", k=8),                     # tapering to the chin point
        limb(57, 134, 62, 178, 2.4, 1.6, "bone", op="add", depth=2.0),    # the cheek ridge
        limb(77.5, 124, 78.5, 194, 1.6, 1.2, "flesh_dk", op="carve"),     # the inner lip
        blob(66, 172, 5, 8, "flesh_dk", op="add", depth=2.0),
    ]
    teeth = teeth_row(78, 128, 78.5, 192, 8, 5.0 + 1.5 * open_amount, 1.4, side=-1, rake=0.12, seed=12, jitter=0.4)
    return xf(half), xf(teeth)


def gore_biter_build(p):
    root = Xf.move(p.cx, p.cy)
    sink = 46 * p.fold
    body_xf = (Xf.scale(p.bs, p.bs, (80, 120)).then(Xf.turn(p.tilt, (80, 120))).then(Xf.move(p.bx, p.by + sink)).then(root))
    head_xf = (Xf.scale(p.hs, p.hs, HEAD).then(Xf.turn(p.tilt * 0.5, HEAD))
               .then(Xf.move(p.hx + p.bx * 0.5, p.hy + p.by * 0.6 + sink * 0.82)).then(root))

    heave = 1.0 + 0.04 * p.breath
    bristle = p.hackle + p.hackle_jig
    back = [
        blob(80, 104, 37 * heave, 27, "flesh_dk", depth=22, k=10),
        blob(80, 88, 24, 16, "flesh", depth=15, k=10),
        # the spine ridge: a row of bony spurs that bristle when it is roused
        *[s for i in range(5) for s in spike(70 + i * 5, 84 - abs(i - 2) * 1.5, 68 + i * 6, 72 - abs(i - 2) * 3 - 4 * bristle,
                                             3.4, "bone", bend=-1.5, tip=1.0, segments=3, k=3)],
    ]
    # ribs: bone hoops curving round both flanks, thin flesh sunk between them
    ribs = []
    for i in range(4):
        y = 92 + i * 9
        hoop = [(76, y), (62, y + 2 + 1.5 * p.breath), (50, y + 9), (47, y + 16)]
        ribs += chain(hoop, 1.8, 1.2, "bone", op="add", depth=1.6)
        ribs += [limb(70, y + 5, 52, y + 14, 1.2, 1.0, "flesh_dk", op="carve")]
    ribs = ribs + mirror(ribs, AX)
    left_half, left_teeth = _jaw_half(p.jaw)
    right_half, right_teeth = mirror(left_half, AX), mirror(left_teeth, AX)
    # the throat between the halves: dark and wet, only seen when the jaw parts
    throat = [blob(80, 160, 6 + 14 * p.jaw, 28 + 3 * p.jaw, "flesh_dk", depth=6, k=4),
              blob(80, 158, 3 + 10 * p.jaw, 20 + 4 * p.jaw, "pupil", op="add", depth=1.0),
              blob(80, 182 - 4 * p.jaw, 3 + 6 * p.jaw, 4 + 4 * p.jaw, "flesh", op="add", depth=2.0),   # the tongue
              *teeth_row(72 - 8 * p.jaw, 136, 88 + 8 * p.jaw, 136, 6, 2.5 + 2 * p.jaw, 1.0, side=1, seed=13, op="add")]
    eye = p.eye_glow * (1 - p.dim)
    eyes_left = with_glow([blob(x, y, r, r * 0.8, "ichor", op="add", depth=r) for x, y, r in
                           ((70, 128, 2.6), (63, 135, 1.8))], eye)
    eyes_right = with_glow([blob(x, y, r, r * 0.8, "ichor", op="add", depth=r) for x, y, r in
                            ((90, 128, 2.6), (97, 135, 1.8), (93, 121, 1.3))], eye)       # one extra: asymmetry
    jaw_angle = 0.42 * p.jaw
    eyes_left = Xf.turn(jaw_angle, (76, HINGE[1] + 2))(eyes_left)
    eyes_right = Xf.turn(-jaw_angle, (84, HINGE[1] + 2))(eyes_right)
    # the left ear with the pen tag clipped through it
    ear_xf = Xf.turn(jaw_angle, (76, HINGE[1] + 2))
    ear = ear_xf([blob(56, 120, 9, 5, "flesh_dk", depth=4, angle=-0.6, k=4),
                  limb(50, 124, 49, 132, 0.8, 0.8, "darkmetal", k=0),
                  blob(48, 138, 6.5, 5.0, "livery", depth=2.0, k=1, prio=30)])
    tag_pos = ear_xf.pt((44.5, 136))

    fore_l = _leg(REST_FORE, p.fl_x, p.fl_y, body_xf, -1, p.fold)
    fore_r = _leg(REST_FORE, p.fr_x, p.fr_y, body_xf, 1, p.fold)
    hind_l = _leg(REST_HIND, p.hl_x, p.hl_y, body_xf, -1, p.fold)
    hind_r = _leg(REST_HIND, p.hr_x, p.hr_y, body_xf, 1, p.fold)

    def leg_shapes(pts, r1, r2, mat, claw_side):
        shapes = chain(pts, r1, r2, mat, k=4)
        shapes.append(blob(pts[1][0], pts[1][1], r1 * 0.9, r1 * 0.9, "flesh_dk", k=4))
        fx, fy = pts[-1]
        shapes += [limb(fx, fy, fx + claw_side * c, fy + 2.5, 1.3, 0.4, "bone", k=1) for c in (-4, 0, 4)]
        return shapes

    layers = [
        root(leg_shapes(hind_l, 9.5, 2.8, "flesh_dk", -1) + leg_shapes(hind_r, 9.5, 2.8, "flesh_dk", 1)),
        body_xf(back + ribs),
        root(leg_shapes(fore_l, 6.5, 2.6, "flesh", -1) + leg_shapes(fore_r, 6.5, 2.6, "flesh", 1)),
        head_xf(throat),
        head_xf(left_half + eyes_left + ear),
        head_xf(right_half + eyes_right),
        head_xf(left_teeth + right_teeth),          # in front: a shut jaw shows the zip of teeth across the seam
    ]
    anchors = {
        "ground": (80.0 + p.cx, 232.0 + p.cy),
        "hit_centre": head_xf.pt((80, 148)),
        "eye": head_xf.pt((80, 128)),
        "mouth": head_xf.pt((80, 162)),
    }
    stencils = []
    if p.tag > 0.5:
        x, y = head_xf.pt(tag_pos)
        stencils.append(("12", x, y, (40, 42, 46)))
    return Figure(layers, anchors, stencils)


GORE_BITER = Rig("gore_biter", gore_biter_build, BITER_POSE, seed=41)

BITER_CLIPS = []
