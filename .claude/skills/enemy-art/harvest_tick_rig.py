"""HARVEST TICK rig + clips (INSECT, SHELL_BRUTE). Imported by bestiary.py."""
from kit import blob, limb, chain, mirror, Xf, Figure, Rig, Clip, lerp, lerp_pt

W, H, AX = 160, 240, 80
BODY_PIVOT = (80, 175)
ABDOMEN_PIVOT = (80, 112)

LEGS = ("fl", "fr", "ml", "mr", "rl", "rr")
TICK_POSE = dict(
    cx=0.0, cy=0.0,                         # whole creature offset (knock-back moves the feet too)
    bx=0.0, by=0.0, bs=1.0, tilt=0.0,       # body (head + thorax): offset, scale toward the viewer, roll
    sx=1.0, sy=1.0,                         # squash / stretch of the body group (lunge smear)
    ab_y=0.0, ab_s=1.0, curl=0.0,           # abdomen raise (negative = up), scale, curled over the head (> 0.5)
    jaw=0.0,                                # mandibles: + open, - snapped shut
    eye_glow=1.0, blister=0.0, blister_glow=1.0, dim=0.0,   # eyes; blister pulse; blister light; death dimming
    burst=0.0, leak=0.0,                    # the right blister burst (0..1); its leak drip phase
    drip=0.0, hose=0.0,                     # torn hose drip phase (0..1), hose sway (rad)
    tuck=0.0, splay=0.0,                    # all legs folded in / thrown out
    **{f"{leg}_{axis}": 0.0 for leg in LEGS for axis in ("x", "y")},   # per-foot offsets (x outward, y down)
)

# left-side rest chains: hip, knee, ankle, foot (the right side is the mirror)
REST_LEGS = {
    "r": dict(pts=[(58, 128), (24, 84), (12, 140), (6, 196)], r=(5.5, 1.6), mat="chitin_dk", knee=(6, "chitin"),
              spurs=[(1, (17, 112), (10, 108), 2.0, 0.6)]),
    "m": dict(pts=[(50, 150), (16, 118), (8, 172), (4, 226)], r=(7.0, 1.8), mat="chitin", knee=(7.5, "chitin_dk"),
              spurs=[(1, (10, 150), (4, 146), 2.0, 0.5), (2, (8, 176), (3, 174), 1.6, 0.4)]),   # tip 2 px in from the still: it touched the canvas edge
    "f": dict(pts=[(58, 168), (30, 150), (24, 192), (21, 230)], r=(7.5, 2.0), mat="chitin", knee=(8, "chitin_dk"),
              spurs=[(1, (25, 176), (16, 172), 2.3, 0.5), (2, (23, 200), (15, 199), 1.8, 0.4)]),
}
TUCK_SCALE = (1.0, 0.55, 0.38, 0.28)       # how far each joint pulls toward the hip when the leg folds
SPLAY_PUSH = (0.0, 6.0, 3.0, 0.0)          # knees thrown out; the feet have no room past the canvas


def _mirror_pt(p):
    return (2 * AX - p[0], p[1])


def _leg(p, key, side, hip_xf):
    """Posed points of one leg. The hip rides the body; the foot stays planted (plus its own offset);
    the knee and ankle follow both, so the body can bob while the feet grip the floor."""
    spec = REST_LEGS[key[0]]
    rest = spec["pts"] if side < 0 else [_mirror_pt(q) for q in spec["pts"]]
    fx, fy = getattr(p, f"{key}_x"), getattr(p, f"{key}_y")
    hip = hip_xf.pt(rest[0])
    hip_d = (hip[0] - rest[0][0], hip[1] - rest[0][1])
    foot_d = (side * fx + p.cx, fy + p.cy)
    lift = min(fy, 0.0)
    weights = ((1.0, 0.0), (0.65, 0.35), (0.3, 0.7), (0.0, 1.0))
    pts = []
    for (wh, wf), q in zip(weights, rest):
        pts.append((q[0] + wh * hip_d[0] + wf * foot_d[0], q[1] + wh * hip_d[1] + wf * foot_d[1]))
    pts[1] = (pts[1][0], pts[1][1] + 0.5 * lift)                        # a lifted foot lifts its knee
    for j in range(1, 4):
        tucked = (hip[0] + (pts[j][0] - hip[0]) * TUCK_SCALE[j], hip[1] + (pts[j][1] - hip[1]) * TUCK_SCALE[j] + 6 * j)
        pts[j] = lerp_pt(pts[j], tucked, p.tuck)
        pts[j] = (pts[j][0] + side * SPLAY_PUSH[j] * p.splay, pts[j][1] + 0.25 * SPLAY_PUSH[j] * p.splay)
    return rest, pts


def _leg_shapes(p, key, side, hip_xf):
    spec = REST_LEGS[key[0]]
    rest, pts = _leg(p, key, side, hip_xf)
    shapes = chain(pts, spec["r"][0], spec["r"][1], spec["mat"], k=5 if key[0] == "r" else 6)
    shapes.append(blob(pts[1][0], pts[1][1], spec["knee"][0], spec["knee"][0], spec["knee"][1]))
    for seg, a, b, r1, r2 in spec["spurs"]:
        if side > 0:
            a, b = _mirror_pt(a), _mirror_pt(b)
        ride = Xf.segment(rest[seg], rest[seg + 1], pts[seg], pts[seg + 1], stretch=False)
        shapes += ride([limb(*a, *b, r1, r2, "chitin_dk", k=3)])
    return shapes, rest, pts


def harvest_tick_build(p):
    """INSECT. Bred from the planet's tissue, gorged on the harvest; the Organization's feeding port is
    still bolted into its back, its hose torn off. Tagged on its right front leg; all six legs."""
    body_xf = (Xf.scale(p.bs * p.sx, p.bs * p.sy, BODY_PIVOT).then(Xf.turn(p.tilt, BODY_PIVOT))
               .then(Xf.move(p.bx + p.cx, p.by + p.cy)))
    # the abdomen follows the body's lunge only partly: it is the heavy end that stays behind
    ab_xf = (Xf.scale(p.ab_s, p.ab_s, ABDOMEN_PIVOT).then(Xf.move(0, p.ab_y))
             .then(Xf.scale((p.bs * p.sx) ** 0.3, (p.bs * p.sy) ** 0.3, BODY_PIVOT)).then(Xf.turn(p.tilt * 0.6, BODY_PIVOT))
             .then(Xf.move(p.bx * 0.7 + p.cx, p.by * 0.8 + p.cy)))

    # blisters: (cx, cy, rx, ry, depth); index 2 is the one that bursts
    pulse = 1.0 + 0.07 * p.blister
    sacs = []
    for i, (x, y, rx, ry, d) in enumerate(((57, 98, 14, 16, 9), (80, 86, 16, 18, 10), (103, 98, 14, 16, 9),
                                           (68, 120, 8, 7, 5), (95, 124, 6, 5, 4))):
        level = p.blister_glow * (1.0 + 0.35 * p.blister)
        size = pulse
        out = min(1.0, max(0.0, p.dim * 5 - (4 - i)))         # in death they go out one by one, and sag
        level *= 1.0 - out
        size *= 1.0 - 0.18 * out
        if i == 2 and p.burst > 0:
            size *= 1.0 - 0.45 * p.burst
            level *= 1.0 - 0.6 * p.burst
        sacs.append(blob(x, y, rx * size, ry * size, "sac", op="add", depth=d * size, glow=level))
    abdomen = [
        blob(80, 112, 56, 46, "chitin", depth=40, k=10),
        *sacs,
        *[limb(26 + i * 12, 128 + abs(i - 4) * -1.2 + (i - 4) ** 2 * 0.9, 38 + i * 12,
               128 + abs(i - 3) * -1.2 + (i - 3) ** 2 * 0.9, 2.2, 2.2, "chitin", op="carve") for i in range(8)],
        *[limb(36 + i * 11, 144 + (i - 4) ** 2 * 0.6, 47 + i * 11, 144 + (i - 3) ** 2 * 0.6, 1.8, 1.8,
               "chitin", op="carve") for i in range(8)],
        # feeding port
        blob(80, 64, 12, 7, "gunmetal", depth=8, k=3, prio=20),
        limb(80, 52, 80, 62, 8, 9, "gunmetal", k=2, prio=20),
        blob(80, 51, 8, 4, "darkmetal", depth=3, k=0, prio=25),
        blob(73, 60, 1.6, 1.6, "livery", op="add", depth=1.5),
        blob(87, 60, 1.6, 1.6, "livery", op="add", depth=1.5),
    ]
    if p.burst > 0.05:                                         # the burst blister bleeds ichor down the shell
        run = 6 + 16 * p.burst
        abdomen += [limb(104, 104, 106, 104 + run, 1.6, 1.0, "ichor", op="add", depth=1.4, glow=p.blister_glow),
                    limb(100, 106, 99, 104 + run * 0.6, 1.2, 0.8, "ichor", op="add", depth=1.2, glow=p.blister_glow)]

    drop_y = 33 + 13 * p.drip
    hose = Xf.turn(p.hose, (80, 50))([
        *chain([(80, 50), (74, 36), (60, 28), (48, 30)], 4.2, 3.6, "darkmetal", k=3),
        *[blob(74 - i * 4, 36 - i * 2.2, 4.8, 4.8, "darkmetal", op="add", depth=0.8) for i in range(4)],
        blob(47, 30, 3, 3.6, "ichor", depth=3, prio=10),
        limb(46, 33, 45.5, max(34.0, drop_y - 2), 1.4, 0.7, "ichor", prio=10),
        blob(45.3, drop_y + 1, 1.6, 1.9 + 0.6 * p.drip, "ichor", prio=10),
    ])

    body = [
        blob(80, 160, 34, 23, "chitin", depth=20, k=8),
        blob(80, 184, 22, 16, "chitin_dk", depth=14, k=9),
        limb(56, 156, 104, 156, 1.6, 1.6, "chitin", op="carve"),
        limb(80, 140, 80, 176, 1.4, 1.4, "chitin", op="carve"),
        *[blob(x, y, r, r, "ichor", op="add", depth=r, glow=p.eye_glow) for x, y, r in
          ((71, 178, 3.4), (89, 178, 3.4), (65, 184, 2.4), (95, 184, 2.4), (75, 172, 2.0), (85, 172, 2.0))],
    ]

    jaw_angle = 0.32 * p.jaw
    jaw_left = (Xf.turn(jaw_angle, (66, 186))(chain([(66, 186), (48, 204), (50, 226)], 4.2, 1.8, "chitin", k=4)) +
                Xf.turn(jaw_angle * 1.2, (72, 192))(chain([(72, 192), (63, 210), (72, 222)], 5.0, 0.8, "bone", k=3)))
    jaws = jaw_left + mirror(jaw_left, AX)

    legs = {}
    hips = {"f": body_xf, "m": body_xf, "r": ab_xf}
    rests = {}
    for leg in LEGS:
        side = -1 if leg[1] == "l" else 1
        shapes, rest, pts = _leg_shapes(p, leg, side, hips[leg[0]])
        legs[leg] = shapes; rests[leg] = (rest, pts)

    # the Organization's tag: a clamp band riding the right front leg's upper segment
    rest, pts = rests["fr"]
    ride = Xf.segment(rest[0], rest[1], pts[0], pts[1])
    tag_clamp = ride([
        limb(112.5, 161.5, 119.5, 157.0, 7.6, 7.6, "gunmetal", k=1, prio=30),
        limb(113.5, 154.0, 117.5, 166.0, 0.8, 0.8, "gunmetal", op="carve"),
    ])
    tag_pos = ride.pt((112, 157))

    abdomen_layer = ab_xf(abdomen)
    body_layer = body_xf(body)
    layers = [
        legs["rl"] + legs["rr"],
        abdomen_layer if p.curl <= 0.5 else [],
        ab_xf(hose),
        legs["ml"] + legs["mr"],
        body_layer,
        legs["fl"] + legs["fr"] + tag_clamp,
        body_xf(jaws),
        abdomen_layer if p.curl > 0.5 else [],             # curled: the abdomen comes over the head
    ]
    anchors = {
        "ground": (80.0 + p.cx, 232.0 + p.cy),
        "hit_centre": body_xf.pt((80, 165)),
        "eye": body_xf.pt((80, 178)),
        "mouth": body_xf.pt((80, 214)),
    }
    # the stencil is painted after rendering, so it must not land on the abdomen when that covers the leg
    stencils = [] if p.curl > 0.5 else [("K7", tag_pos[0], tag_pos[1], (230, 228, 220))]
    return Figure(layers, anchors, stencils)


HARVEST_TICK = Rig("harvest_tick", harvest_tick_build, TICK_POSE, seed=11)


def _feet(a_x=0.0, a_y=0.0, b_x=0.0, b_y=0.0):
    """Tripod gait: A = front-left, middle-right, rear-left; B = the other three."""
    return {"fl_x": a_x, "fl_y": a_y, "mr_x": min(a_x, 0.0) - (2.0 if a_y < 0 else 0.0), "mr_y": a_y * 0.6, "rl_x": min(a_x, 0.0), "rl_y": a_y,
            "fr_x": b_x, "fr_y": b_y, "ml_x": min(b_x, 0.0) - (2.0 if b_y < 0 else 0.0), "ml_y": b_y * 0.6, "rr_x": min(b_x, 0.0), "rr_y": b_y}


# IDLE: blisters pulse, legs shift weight one after another, mandibles flex, the hose drips
_idle_keys = [
    (0.00, dict(blister=0.0, jaw=0.0, by=0.0, drip=0.0, hose=0.0)),
    (0.75, dict(blister=1.0, jaw=0.22, by=1.0, hose=0.03)),
    (1.25, dict(drip=1.0), "hold"),
    (1.33, dict(drip=0.0)),
]
for i, leg in enumerate(("fl", "mr", "rl", "fr", "ml", "rr")):
    t = 0.25 * i
    _idle_keys += [(t, {f"{leg}_y": 0.0}), (t + 0.12, {f"{leg}_y": -3.0}), (t + 0.25, {f"{leg}_y": 0.0})]
IDLE = Clip("IDLE", _idle_keys, duration=1.5, mode="loop")

_windup_end = dict(by=12.0, bs=0.85, ab_y=-16.0, ab_s=1.05, jaw=0.65, eye_glow=1.45, blister=1.0,
                   fl_x=5.0, fl_y=2.0, fr_x=5.0, fr_y=2.0, ml_x=-2.0, mr_x=-2.0)

TICK_CLIPS = [
    IDLE,
    IDLE.variant("DORMANT", speed=0.5, fps=6, by=8.0, tuck=0.3, blister_glow=0.35, eye_glow=0.3, ab_y=4.0),
    Clip("ALERT", [
        (0.00, dict(by=0.0, ab_y=0.0, jaw=0.0, eye_glow=1.0, blister=0.0, fl_y=0.0, fr_y=0.0)),
        (0.17, dict(by=-20.0, bs=1.06, ab_y=8.0, jaw=1.3, eye_glow=1.8, blister=1.0, fl_y=-34.0, fr_y=-34.0, fl_x=-6.0, fr_x=-6.0), "out"),
        (0.33, dict(by=-17.0, jaw=1.1, fl_y=-28.0, fr_y=-28.0), "inout"),
        (0.50, dict(by=0.0, bs=1.0, ab_y=0.0, jaw=0.3, eye_glow=1.2, blister=0.5, fl_y=0.0, fr_y=0.0, fl_x=0.0, fr_x=0.0)),
    ], mode="once"),
    Clip("MOVE", [
        (0.000, dict(tilt=-0.035, by=0.0, **_feet(a_y=-10.0, a_x=3.0, b_y=0.0, b_x=-2.0))),
        (0.083, dict(tilt=0.0, by=-2.0, **_feet(a_y=0.0, a_x=0.0, b_y=0.0, b_x=0.0))),
        (0.167, dict(tilt=0.035, by=0.0, **_feet(a_y=0.0, a_x=-2.0, b_y=-10.0, b_x=3.0))),
        (0.250, dict(tilt=0.0, by=-2.0, **_feet(a_y=0.0, a_x=0.0, b_y=0.0, b_x=0.0))),
    ], duration=0.333, mode="loop"),
    Clip("WIND_UP", [
        (0.00, dict(by=0.0, bs=1.0, ab_y=0.0, ab_s=1.0, jaw=0.0, eye_glow=1.0, blister=0.0)),
        (0.50, _windup_end, "inout"),
    ], mode="hold"),
    Clip("ATTACK", [
        (0.00, dict(_windup_end, sx=1.0, sy=1.0)),
        (0.05, dict(bs=1.3, sx=0.9, sy=1.1, by=-5.0, ml_x=-6.0, mr_x=-6.0, rl_x=-3.0, rr_x=-3.0, jaw=1.2, ab_y=2.0, fl_x=-6.0, fr_x=-6.0, fl_y=4.0, fr_y=4.0), "out"),
        (0.10, dict(bs=1.26, sx=1.1, sy=0.92, ml_x=-7.0, mr_x=-7.0, jaw=-0.35, eye_glow=1.8), "out"),
        (0.30, dict(bs=1.0, sx=1.0, sy=1.0, by=0.0, ab_y=0.0, ab_s=1.0, jaw=0.0, eye_glow=1.0, blister=0.0,
                    fl_x=0.0, fl_y=0.0, fr_x=0.0, fr_y=0.0, ml_x=0.0, mr_x=0.0, rl_x=0.0, rr_x=0.0)),
    ], fps=20, mode="once"),
    Clip("HURT_LIGHT", [
        (0.00, dict(by=-3.0, tilt=-0.05, blister=-1.5, jaw=0.5, eye_glow=0.5), "out"),
        (0.17, dict(by=0.0, tilt=0.0, blister=0.0, jaw=0.0, eye_glow=1.0)),
    ], mode="once"),
    Clip("HURT_HEAVY", [
        (0.00, dict(cy=0.0, bs=1.0, splay=0.0, burst=0.0, tilt=0.0, jaw=0.0)),
        (0.08, dict(cy=-5.0, bs=0.88, splay=1.0, burst=1.0, tilt=-0.08, jaw=1.0, eye_glow=0.4, ab_y=-6.0, ml_x=-4.0, mr_x=-4.0), "out"),
        (0.25, dict(cy=-3.0, bs=0.93, splay=0.6, tilt=-0.04, eye_glow=1.2)),
        (0.42, dict(cy=0.0, bs=1.0, splay=0.0, burst=0.0, tilt=0.0, jaw=0.0, eye_glow=1.0, ab_y=0.0, ml_x=0.0, mr_x=0.0)),
    ], mode="once"),
    Clip("DEFEND", [
        (0.00, dict(tuck=0.0, by=0.0, bs=1.0, ab_y=0.0, ab_s=1.0, curl=0.0, jaw=0.0, eye_glow=1.0)),
        (0.17, dict(curl=1.0), "hold"),
        (0.33, dict(tuck=0.6, by=12.0, bs=0.84, ab_y=46.0, ab_s=0.92, jaw=-0.25, eye_glow=0.5, curl=1.0)),
    ], mode="hold"),
    IDLE.variant("WOUNDED", keys=[
        (0.00, dict(leak=0.0)), (1.5, dict(leak=1.0)),
    ], burst=1.0, rr_x=-6.0, rr_y=6.0, tilt=0.04),
    Clip("DEATH", [
        (0.00, dict(by=0.0, tuck=0.0, eye_glow=1.0, dim=0.0, jaw=0.0, ab_y=0.0, blister=0.0)),
        (0.08, dict(by=-6.0, jaw=1.2, eye_glow=1.6, splay=0.6), "out"),
        (0.25, dict(eye_glow=0.0, splay=0.0), "in"),
        (0.50, dict(by=10.0, ab_y=26.0, tuck=0.85, jaw=0.4, bs=0.9), "in"),
        (0.58, dict(by=6.0), "inout"),
        (0.67, dict(by=10.0, tuck=1.0, jaw=-0.2), "hold"),
        (0.25, dict(dim=0.0)),
        (0.92, dict(dim=1.0)),
    ], mode="hold"),
]
