"""IRON STALKER rig + clips (INSECT, MINI_ELITE). Imported by bestiary.py.

A harvest-line insect the Organization tried to use as a guard animal: bred from the planet's tissue, then
FITTED with riveted iron plates to survive the pens. It outgrew them; chitin has grown over their edges and
split around the chest plate, where an ichor seam glows. Reared on four rear legs, the front pair raised as
two long scythe blades. A stencilled pen number on the chest plate; a snapped restraint collar on the neck.
"""
import math

from kit import blob, limb, chain, spike, riveted_plate, mirror, with_glow, Xf, Figure, Rig, Clip, lerp, lerp_pt

W, H, AX = 160, 240, 80
WAIST = (80, 132)                 # the body (thorax, head, blades) leans and scales about the waist
NECK = (80, 64)
SHOULDER = (60, 82)               # left blade arm root (the right one is the mirror)
ELBOW = (36, 30)                  # left blade arm elbow at rest: the blade hangs from here

LEGS = ("hl", "hr", "ml", "mr")   # hind / mid, left / right
STALKER_POSE = dict(
    cx=0.0, cy=0.0,                              # whole creature offset
    bx=0.0, by=0.0, bs=1.0, lean=0.0,            # body: offset, scale toward the viewer, roll about the waist (rad)
    pel=0.0,                                     # pelvis drop (the abdomen and the hips follow the body only partly)
    head=0.0,                                    # head tilt (rad) - the listening cock
    l_sh=0.0, l_el=0.0, l_reach=1.0,             # left blade arm: shoulder swing (+ out/down), blade swing (+ tip out), reach scale
    r_sh=0.0, r_el=0.0, r_reach=1.0,             # right blade arm
    l_smear=0.0, r_smear=0.0,                    # sweep smear: the arc the blade tip just travelled (rad)
    eye_glow=1.0, seam_glow=1.0, dim=0.0,        # eyes; the ichor seam; death dimming
    lift=0.0,                                    # BUFF_SELF: shoulder plates lift off the body (0..1)
    ring=0.0,                                    # HURT: plates ring (a 1-px shiver)
    tear=0.0,                                    # the left shoulder plate torn half off, hanging by one rivet (0..1)
    drip=0.0,                                    # ichor drip from the seam (0..1 phase; 0 = none)
    collar=0.0,                                  # the snapped restraint chain swinging (rad)
    tuck=0.0, splay=0.0,                         # all legs folded in / thrown out
    tag=1.0,                                     # 0 drops the pen-number stencil when its plate is covered (lesson 12.12)
    **{f"{leg}_{axis}": 0.0 for leg in LEGS for axis in ("x", "y")},   # per-foot offsets (x outward, y down)
)

# left-side rest chains: hip, knee, ankle, foot (the right side is the mirror). Both pairs stand high-kneed.
REST_LEGS = {
    "h": dict(pts=[(68, 158), (36, 130), (24, 184), (20, 229)], r=(6.0, 1.6), mat="chitin_dk", knee=(5.5, "chitin"),
              spurs=[(1, (30, 154), (22, 151), 2.0, 0.5)]),
    "m": dict(pts=[(72, 144), (52, 148), (50, 194), (50, 230)], r=(6.5, 1.8), mat="chitin", knee=(6.5, "chitin_dk"),
              spurs=[(1, (51, 166), (44, 164), 2.0, 0.5), (2, (50, 206), (44, 205), 1.6, 0.4)]),
}
TUCK_SCALE = (1.0, 0.55, 0.40, 0.30)
SPLAY_PUSH = (0.0, 8.0, 5.0, 0.0)


def _mirror_pt(p):
    return (2 * AX - p[0], p[1])


def _leg_shapes(p, key, side, hip_xf):
    """Posed leg: the hip rides the pelvis, the foot stays planted (plus its own offset), the knee and
    ankle follow both - the body bobs while the feet grip the floor (the Harvest Tick's method)."""
    spec = REST_LEGS[key[0]]
    rest = spec["pts"] if side < 0 else [_mirror_pt(q) for q in spec["pts"]]
    fx, fy = getattr(p, f"{key}_x"), getattr(p, f"{key}_y")
    hip = hip_xf.pt(rest[0])
    hip_d = (hip[0] - rest[0][0], hip[1] - rest[0][1])
    foot_d = (side * fx + p.cx, fy + p.cy)
    lift = min(fy, 0.0)
    weights = ((1.0, 0.0), (0.65, 0.35), (0.3, 0.7), (0.0, 1.0))
    pts = [(q[0] + wh * hip_d[0] + wf * foot_d[0], q[1] + wh * hip_d[1] + wf * foot_d[1]) for (wh, wf), q in zip(weights, rest)]
    pts[1] = (pts[1][0], pts[1][1] + 0.6 * lift)
    for j in range(1, 4):
        tucked = (hip[0] + (pts[j][0] - hip[0]) * TUCK_SCALE[j], hip[1] + (pts[j][1] - hip[1]) * TUCK_SCALE[j] + 7 * j)
        pts[j] = lerp_pt(pts[j], tucked, p.tuck)
        pts[j] = (pts[j][0] + side * SPLAY_PUSH[j] * p.splay, pts[j][1] + 0.3 * SPLAY_PUSH[j] * p.splay)
    shapes = chain(pts, spec["r"][0], spec["r"][1], spec["mat"], k=5)
    shapes.append(blob(pts[1][0], pts[1][1], spec["knee"][0], spec["knee"][0], spec["knee"][1]))
    for seg, a, b, r1, r2 in spec["spurs"]:
        if side > 0:
            a, b = _mirror_pt(a), _mirror_pt(b)
        ride = Xf.segment(rest[seg], rest[seg + 1], pts[seg], pts[seg + 1], stretch=False)
        shapes += ride([limb(*a, *b, r1, r2, "chitin_dk", k=3)])
    return shapes


def _blade_arm(sh, el, reach, smear):
    """The LEFT raptorial arm in canvas space (the right arm is built with its own angles and mirrored):
    a plated femur from the shoulder up to the elbow, then the scythe blade hanging from the elbow, its
    bone-pale cutting edge on the inside. -> (shapes, the posed blade tip)."""
    arm_xf = Xf.turn(-sh, SHOULDER).then(Xf.scale(reach, reach, SHOULDER))
    blade_xf = Xf.turn(el, ELBOW).then(arm_xf)
    femur = [
        limb(*SHOULDER, *ELBOW, 6.5, 5.0, "chitin", k=4),
        blob(SHOULDER[0], SHOULDER[1], 7.5, 7.5, "chitin_dk"),
        blob(ELBOW[0], ELBOW[1], 5.5, 5.5, "chitin_dk", k=4),
        # femur spines, raking forward along the arm
        limb(46, 54, 38, 50, 1.8, 0.4, "chitin_dk", k=2),
        limb(52, 66, 44, 63, 1.8, 0.4, "chitin_dk", k=2),
        # a riveted iron cuff on the femur: the pens' fittings reached the arms too
        *riveted_plate([(44, 46), (52, 42), (58, 58), (50, 63)], "darkmetal", rivet_mat="gunmetal", spacing=9,
                       inset=1.8, rivet_r=1.0, bevel=1.6, op="add"),
    ]
    tip = (42.0, 128.0)
    blade = spike(ELBOW[0], ELBOW[1] + 2, tip[0], tip[1], 7.0, "chitin_dk", bend=13.0, tip=0.6, segments=7,
                  edge="bone", edge_side=-1.0, k=3)
    # the blade's inner face: a pale, serrated cutting edge with a row of teeth
    blade += spike(ELBOW[0] + 4, ELBOW[1] + 10, tip[0] + 1, tip[1] - 6, 2.6, "bone", bend=11.0, tip=0.5, segments=6, k=1)
    blade += [limb(*q, q[0] + 4.0, q[1] + 2.0, 1.0, 0.3, "bone", k=1) for q in ((40, 64), (41, 82), (42, 100))]
    # the hooked point curls back inward
    blade += [limb(tip[0], tip[1], tip[0] + 6.0, tip[1] - 6.0, 1.4, 0.4, "bone", k=1)]
    shapes = arm_xf(femur) + blade_xf(blade)
    if smear > 0.02:
        # the arc the tip just swept (a smear frame): bone streaks behind the posed tip, about the shoulder
        streak = []
        steps = 4
        for i in range(steps):
            a0 = -smear * i / steps
            a1 = -smear * (i + 1) / steps
            p0 = Xf.turn(-a0, SHOULDER).pt(blade_xf.pt(tip))
            p1 = Xf.turn(-a1, SHOULDER).pt(blade_xf.pt(tip))
            r = 2.2 * (1 - i / steps) + 0.5
            streak.append(limb(*p0, *p1, r, max(0.5, r - 0.5), "bone", k=0))
        shapes += streak
    return shapes, blade_xf.pt(tip)


def iron_stalker_build(p):
    root = Xf.move(p.cx, p.cy)
    body_xf = (Xf.scale(p.bs, p.bs, WAIST).then(Xf.turn(p.lean, WAIST)).then(Xf.move(p.bx, p.by)).then(root))
    pelvis_xf = Xf.move(p.bx * 0.5, p.by * 0.45 + p.pel).then(root)
    head_xf = Xf.turn(p.head, NECK).then(body_xf)
    shiver = (0.9 if p.ring > 0.5 else -0.9) * min(1.0, p.ring * 2)

    # ---- abdomen: segmented, slung low behind the waist, read between the legs
    abdomen = [
        blob(80, 162, 19, 19, "chitin_dk", depth=16, k=8),
        blob(80, 144, 11, 9, "chitin", depth=9, k=7),
        *[limb(64 + i * 3, 158 + i * 6, 96 - i * 3, 158 + i * 6, 1.2, 1.2, "chitin_dk", op="carve") for i in range(3)],
        blob(90, 168, 3.4, 2.8, "sac", op="add", depth=3, glow=p.seam_glow * (1 - p.dim)),
    ]

    # ---- thorax, neck: tall, plated, a narrow waist
    eye = p.eye_glow * (1 - p.dim)
    seam = p.seam_glow * (1 - 0.85 * p.dim)
    thorax = [
        limb(80, 140, 80, 118, 6.5, 7.5, "chitin", k=6),                       # the narrow waist
        blob(80, 102, 20, 27, "chitin", depth=18, k=8),
        blob(80, 80, 25, 11, "chitin", depth=11, k=8),
        limb(80, 74, 80, 60, 6.5, 5.5, "chitin_dk", k=5),                      # neck
        limb(64, 116, 96, 116, 1.2, 1.2, "chitin", op="carve"),
        limb(66, 124, 94, 124, 1.0, 1.0, "chitin", op="carve"),
    ]
    # the chest plate, fitted ON the thorax (it follows the body's curve); stencilled with a pen number
    chest_pts = [(63, 88), (97, 86), (99, 106), (82, 117), (62, 108)]
    chest = riveted_plate(chest_pts, "gunmetal", spacing=10, inset=2.2, rivet_r=1.2, bevel=2.2, op="add")
    # chitin has grown over the plate's edges (the creature outgrew its fittings)
    overgrowth = [blob(62, 98, 3.4, 6.0, "chitin", op="add", depth=3.2), blob(95, 87, 5.0, 2.6, "chitin", op="add", depth=2.6),
                  blob(70, 113, 4.2, 2.6, "chitin", op="add", depth=2.6)]
    # the ichor seam where the shell split around the plate (right flank: it breaks the symmetry)
    seam_pts = [(100, 90), (103, 97), (101, 104), (104, 110), (99, 116)]
    seam_shapes = with_glow([limb(*a, *b, 1.9, 1.6, "ichor", op="add", depth=1.8) for a, b in zip(seam_pts, seam_pts[1:])],
                            seam)
    if p.drip > 0.02:
        drop = 118 + 12 * p.drip
        seam_shapes += with_glow([limb(99, 116, 99.5, drop - 1.5, 1.1, 0.7, "ichor", op="add", depth=1.0),
                                  blob(99.6, drop, 1.4, 1.7 + 0.5 * p.drip, "ichor", op="add", depth=1.2)], seam)
    # the snapped restraint collar on the neck, a stub of chain hanging from the break
    collar = riveted_plate([(68, 63), (92, 63), (93, 70), (67, 70)], "gunmetal", spacing=12, inset=1.6, rivet_r=0.9,
                           bevel=1.4, op="add")
    collar += [limb(86, 63, 88, 70, 0.9, 0.9, "gunmetal", op="carve")]           # the break
    chain_xf = Xf.turn(p.collar, (91, 70))
    collar_chain = chain_xf([blob(91, 74, 2.6, 3.2, "darkmetal", depth=2.0, k=1), blob(91, 74, 1.0, 1.6, "darkmetal", op="carve"),
                             blob(92, 80, 2.2, 2.8, "darkmetal", depth=1.8, k=1), blob(92, 80, 0.8, 1.3, "darkmetal", op="carve")])

    head = [
        blob(80, 50, 13, 11, "chitin_dk", depth=10, k=6),
        blob(80, 57, 9, 6, "chitin", depth=6, k=5),
        # the armoured brow: a riveted helm plate over the crown
        *riveted_plate([(67, 47), (74, 39), (86, 39), (93, 47), (88, 51), (72, 51)], "darkmetal", rivet_mat="gunmetal",
                       spacing=10, inset=1.6, rivet_r=0.9, bevel=1.6, op="add"),
        # ichor eyes, set LOW under the helm
        *with_glow([blob(x, y, r, r * 0.85, "ichor", op="add", depth=r) for x, y, r in
                    ((74, 55, 2.6), (86, 55, 2.6), (70, 52.5, 1.5), (90, 52.5, 1.5))], eye),
        # small mandibles
        *spike(76, 60, 73, 68, 1.8, "bone", bend=-1.5, segments=3, k=2),
        *spike(84, 60, 87, 68, 1.8, "bone", bend=1.5, segments=3, k=2),
    ]

    # ---- shoulder plates (pauldrons): separate riveted pieces, so they can lift, ring and tear
    lift_up, lift_out = 5.0 * p.lift, 4.0 * p.lift
    pauldron = riveted_plate([(50, 75), (60, 70), (70, 74), (68, 86), (58, 91), (50, 86)], "darkmetal", rivet_mat="gunmetal",
                             spacing=11, inset=1.8, rivet_r=1.0, bevel=3.0, slope=0.7, tilt=(-0.14, -0.06), k=1)
    pauldron += [blob(66, 89, 4.0, 2.2, "chitin", op="add", depth=1.6)]          # shell grown over its lower edge
    left_paul_xf = Xf.move(-lift_out + shiver, -lift_up)
    if p.tear > 0.01:                       # torn half off: it swings down about its last rivet
        left_paul_xf = Xf.turn(-1.25 * p.tear, (66, 86)).then(Xf.move(0, 4 * p.tear)).then(left_paul_xf)
    right_paul_xf = Xf.move(lift_out - shiver, -lift_up)
    pauldrons = left_paul_xf(pauldron) + right_paul_xf(mirror(pauldron, AX))

    # ---- dorsal spines rising behind the shoulders (mini-elite: spiked)
    spines = spike(72, 82, 63, 26, 4.6, "chitin_dk", bend=-4.0, tip=0.5, segments=5, k=3) + \
        spike(66, 84, 52, 52, 3.2, "chitin_dk", bend=-3.0, tip=0.5, segments=4, k=3)
    spines = spines + mirror(spines, AX)

    # ---- legs
    legs = {}
    for leg in LEGS:
        side = -1 if leg[1] == "l" else 1
        legs[leg] = _leg_shapes(p, leg, side, pelvis_xf)

    # ---- blades
    left_arm, left_tip = _blade_arm(p.l_sh, p.l_el, p.l_reach, p.l_smear)
    right_raw, right_tip = _blade_arm(p.r_sh, p.r_el, p.r_reach, p.r_smear)
    right_arm = mirror(right_raw, AX)
    right_tip = (2 * AX - right_tip[0], right_tip[1])

    chest_layer = thorax + chest + overgrowth + seam_shapes + collar
    layers = [
        legs["hl"] + legs["hr"],
        pelvis_xf(abdomen),
        body_xf(spines),
        legs["ml"] + legs["mr"],
        body_xf(chest_layer) + head_xf(head),
        body_xf(collar_chain),
        body_xf(left_arm),
        body_xf(right_arm),
        body_xf(pauldrons),
    ]
    anchors = {
        "ground": (80.0 + p.cx, 232.0 + p.cy),
        "hit_centre": body_xf.pt((80, 100)),
        "eye": head_xf.pt((80, 55)),
        "mouth": body_xf.pt(lerp_pt(left_tip, right_tip, 0.5)),
    }
    stencils = []
    if p.tag > 0.5:
        sx, sy = body_xf.pt((68, 93))
        stencils.append(("P-31", sx, sy, (230, 228, 220)))
    return Figure(layers, anchors, stencils)


IRON_STALKER = Rig("iron_stalker", iron_stalker_build, STALKER_POSE, seed=23)

STALKER_CLIPS = []
