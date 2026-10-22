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
               .then(Xf.move(p.hx + p.bx * 0.5, p.hy + p.by * 0.6 + sink * 0.55)).then(root))

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


def _feet(a_y=0.0, a_x=0.0, b_y=0.0, b_x=0.0):
    """A bound: A = left fore + right hind, B = the other diagonal."""
    return {"fl_x": a_x, "fl_y": a_y, "hr_x": a_x, "hr_y": a_y, "fr_x": b_x, "fr_y": b_y, "hl_x": b_x, "hl_y": b_y}


# IDLE: twitchy and irregular - the jaw clacks twice, the head jerks, the spine ridge shivers a beat behind it
_IDLE = Clip("IDLE", [
    (0.00, dict(jaw=0.0, hx=0.0, hy=0.0, breath=0.0, tilt=0.0)),
    (0.17, dict(jaw=0.0), "linear"),
    (0.22, dict(jaw=0.3), "linear"),
    (0.27, dict(jaw=-0.03), "linear"),
    (0.33, dict(jaw=0.25), "linear"),
    (0.38, dict(jaw=0.0), "hold"),
    (0.50, dict(hx=0.0, tilt=0.0), "out"),
    (0.55, dict(hx=-3.0, tilt=-0.05), "hold"),
    (0.85, dict(hx=-3.0, tilt=-0.05), "out"),
    (0.92, dict(hx=2.0, tilt=0.03, hy=1.0), "hold"),
    (0.60, dict(breath=1.0)),
    (1.05, dict(hl_y=0.0)), (1.12, dict(hl_y=-3.0)), (1.20, dict(hl_y=0.0)),
], duration=1.33, mode="loop")
IDLE = Clip("IDLE", _IDLE.keys + jiggle(_IDLE, "hx", 1.33, out_param="hackle_jig", freq=3.0, damping=0.25, gain=0.25, fps=12),
            duration=1.33, mode="loop")
_LUNGE = dict(hs=1.28, hy=10.0, by=4.0, jaw=1.15, eye_glow=1.6, fl_y=4.0, fr_y=4.0, hl_x=-4.0, hr_x=-4.0, hackle=1.0)

BITER_CLIPS = [
    IDLE,
    IDLE.variant("DORMANT", speed=0.5, fps=6, by=8.0, hy=6.0, hs=0.96, eye_glow=0.3, jaw=0.0, hackle=-0.5, breath=0.0),
    Clip("ALERT", [        # it snaps up, the jaw splits wide, the spine bristles
        (0.00, dict(by=0.0, hy=0.0, jaw=0.0, hackle=0.0, eye_glow=1.0, hs=1.0)),
        (0.12, dict(by=-6.0, hy=-6.0, hs=1.06, jaw=1.1, hackle=1.3, eye_glow=1.8, fl_y=-6.0, fr_y=-6.0), "out"),
        (0.28, dict(jaw=0.7, hackle=1.0, fl_y=0.0, fr_y=0.0)),
        (0.42, dict(by=0.0, hy=0.0, hs=1.0, jaw=0.1, hackle=0.4, eye_glow=1.2)),
    ], mode="once"),
    Clip("MOVE", [         # a fast loping bound: the diagonals swap, the body pitches, the jaw hangs open
        (0.000, dict(by=2.0, hy=4.0, tilt=-0.03, jaw=0.25, **_feet(a_y=-12.0, a_x=2.0))),
        (0.083, dict(by=-4.0, hy=-3.0, tilt=0.0, jaw=0.35, **_feet())),
        (0.167, dict(by=2.0, hy=4.0, tilt=0.03, jaw=0.25, **_feet(b_y=-12.0, b_x=2.0))),
        (0.250, dict(by=-4.0, hy=-3.0, tilt=0.0, jaw=0.35, **_feet())),
    ], duration=0.333, mode="loop"),
    Clip("WIND_UP", [      # coils low, jaw half open, head drawn back - held
        (0.00, dict(by=0.0, hy=0.0, hs=1.0, jaw=0.0, hackle=0.0, eye_glow=1.0)),
        (0.50, dict(by=10.0, hy=8.0, hs=0.92, jaw=0.55, hackle=1.2, eye_glow=1.5, hl_x=4.0, hr_x=4.0, fl_x=3.0, fr_x=3.0)),
    ], mode="hold"),
    Clip("ATTACK", [       # the lunging bite: the head shoots at you split wide, then snaps shut
        (0.00, dict(by=0.0, hy=0.0, hs=1.0, jaw=0.2, eye_glow=1.0)),
        (0.06, dict(_LUNGE), "out"),
        (0.12, dict(hs=1.32, hy=12.0, jaw=-0.05, eye_glow=1.9), "out"),
        (0.20, dict(hs=1.15, hy=6.0, jaw=0.15)),
        (0.30, dict(by=0.0, hy=0.0, hs=1.0, jaw=0.1, eye_glow=1.0, fl_y=0.0, fr_y=0.0, hl_x=0.0, hr_x=0.0, hackle=0.3)),
    ], fps=20, mode="once", events={0.12: "impact"}),
    Clip("HURT_LIGHT", [   # a yelp: the head jerks back, the jaw gapes
        (0.00, dict(hy=-4.0, hs=0.95, jaw=0.6, tilt=-0.06, eye_glow=0.5), "out"),
        (0.17, dict(hy=0.0, hs=1.0, jaw=0.0, tilt=0.0, eye_glow=1.0)),
    ], mode="once"),
    Clip("HURT_HEAVY", [   # knocked back: it skids, legs splaying, the jaw flapping
        (0.00, dict(cy=0.0, tilt=0.0, jaw=0.0, hs=1.0)),
        (0.08, dict(cy=-5.0, tilt=-0.1, jaw=0.9, hs=0.88, hy=-6.0, eye_glow=0.4, fl_x=6.0, fr_x=6.0, hl_x=5.0, hr_x=5.0), "out"),
        (0.25, dict(cy=-2.0, tilt=-0.04, jaw=0.4, hs=0.95, hy=-2.0, eye_glow=1.2)),
        (0.42, dict(cy=0.0, tilt=0.0, jaw=0.0, hs=1.0, hy=0.0, eye_glow=1.0, fl_x=0.0, fr_x=0.0, hl_x=0.0, hr_x=0.0)),
    ], mode="once"),
    Clip("DEFEND", [       # it ducks: head down and in, jaw clamped, spine ridge up
        (0.00, dict(by=0.0, hy=0.0, hs=1.0, jaw=0.0, hackle=0.0, tag=1.0)),
        (0.33, dict(by=10.0, hy=14.0, hs=0.88, jaw=-0.05, hackle=1.5, eye_glow=0.7, fl_x=4.0, fr_x=4.0)),
    ], mode="hold"),
    IDLE.variant("WOUNDED", jaw=0.35, tilt=0.07, hl_y=4.0, hl_x=-3.0, by=3.0, eye_glow=0.85),
    Clip("DEATH", [        # it skids, the legs give, it folds onto its belly, jaw slack
        (0.00, dict(by=0.0, hy=0.0, hs=1.0, fold=0.0, jaw=0.0, tilt=0.0, eye_glow=1.0, dim=0.0, cy=0.0)),
        (0.10, dict(jaw=1.1, hs=1.08, hy=4.0, eye_glow=1.7, cy=-2.0), "out"),
        (0.30, dict(tilt=0.12, fold=0.35, jaw=0.6, hy=2.0, cx=-3.0), "in"),
        (0.55, dict(tilt=0.18, fold=0.9, jaw=0.45, hy=0.0, cx=-4.0, hackle=-0.8), "in"),
        (0.62, dict(fold=0.82), "out"),
        (0.72, dict(fold=1.0, tilt=0.2, jaw=0.5), "hold"),
        (0.25, dict(dim=0.0)),
        (0.70, dict(dim=1.0)),
    ], mode="hold"),
]
