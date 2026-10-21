"""PLAGUE HULK rig + clips (ABERRATION, SOLDIER). Imported by bestiary.py.

A mass of harvested tissue that grew in a containment tank until it burst it. It still wears the tank's broken
steel collar ring around its middle, flesh bulging over and under it. Wide, no neck, two enormous forearms
knuckled on the floor, ichor blisters across its left shoulder. A stencilled tank number on the collar.
"""
import math

from kit import blob, limb, chain, teeth_row, mirror, with_glow, Xf, Figure, Rig, Clip, jiggle, lerp, lerp_pt

W, H, AX = 160, 240, 80
CORE = (80, 136)                  # the body scales and leans about the collar's middle
SHOULDER = (48, 100)               # left arm root (the right arm is the mirror)
ELBOW = (20, 146)                 # left elbow at rest
KNUCKLE = (33, 214)               # left fist centre at rest (knuckled on the floor)

HULK_POSE = dict(
    cx=0.0, cy=0.0,                              # whole creature offset
    bx=0.0, by=0.0, bs=1.0, lean=0.0,            # body: offset, scale toward the viewer, roll about the collar
    sx=1.0, sy=1.0,                              # squash / stretch of the body (the slam squash, the swell)
    breath=0.0,                                  # chest heave (0 rest .. 1 full breath)
    roll=0.0, roll_jig=0.0,                      # flesh rolling over the collar (+ down over it); its lagging jiggle
    ripple=0.0,                                  # HURT: a wave through the mass (0..1 phase; 0 = none)
    l_sh=0.0, l_el=0.0, r_sh=0.0, r_el=0.0,      # arms: shoulder raise (+ up/out), elbow fold (+ fist in over the head)
    l_fist=1.0, r_fist=1.0,                      # fist scale (squash on the slam: < 1 tall, > 1 wide)
    jaw=0.0,                                     # mouth slit open
    eye_glow=1.0, blister=0.0, blister_glow=1.0, dim=0.0,   # eyes; blister pulse; blister light; death dimming
    swell=0.0,                                   # SELF_DESTRUCT priming: 0 rest, 1 primed, 2 about to go
    burst=0.0,                                   # the self-destruct burst (0..1): the mass tears open
    sag=0.0,                                     # DEATH: it sags and spreads (0..1)
    tear=0.0,                                    # WOUNDED: a split above the collar leaking ichor (0..1)
    drip=0.0,                                    # ichor drip from the split (0..1 phase)
    tag=1.0,                                     # 0 drops the collar stencil when it is covered (lesson 12.12)
)


def _arm(sh, el, fist, side_shapes=True):
    """The LEFT arm in canvas space (the right one is built with its own angles, then mirrored): a short thick
    upper arm, an enormous forearm and a knuckled fist. sh swings the whole arm about the shoulder (+ = up and
    out, clockwise on screen), el folds the forearm about the elbow (+ = the fist comes in over the head).
    -> (shapes, posed fist centre)."""
    arm_xf = Xf.turn(sh, SHOULDER)
    fore_xf = Xf.turn(el, ELBOW).then(arm_xf)
    upper = [
        limb(*SHOULDER, *ELBOW, 12, 10, "flesh", k=7),
        blob(SHOULDER[0] - 2, SHOULDER[1] + 2, 12, 12, "flesh", depth=11, k=7),
        blob(ELBOW[0] + 1, ELBOW[1], 11, 11, "flesh_dk", depth=10, k=6),
        # a vein down the upper arm
        *chain([(40, 108), (31, 122), (24, 136)], 1.6, 1.2, "flesh_dk", op="add", depth=1.3),
    ]
    fist_w, fist_h = 20 * fist ** 0.5, 14 / fist ** 0.5
    fore = [
        limb(*ELBOW, KNUCKLE[0] - 1, KNUCKLE[1] - 10, 13, 19, "flesh", k=9),
        blob(28, 180, 17, 24, "flesh", depth=17, k=9),                       # the club forearm's swollen belly
        blob(KNUCKLE[0], KNUCKLE[1], fist_w, fist_h, "flesh_dk", depth=13, k=7),
        # knuckles: four calloused ridges facing the viewer
        *[blob(KNUCKLE[0] - 11 + i * 7.2, KNUCKLE[1] + 3 - abs(i - 1.5) * 1.4, 3.8, 3.2, "bone", op="add", depth=2.0)
          for i in range(4)],
        *chain([(19, 160), (20, 180), (27, 199)], 1.6, 1.2, "flesh_dk", op="add", depth=1.3),
        blob(35, 170, 4.5, 6, "flesh_dk", op="add", depth=2.2),             # a growth on the forearm
    ]
    return arm_xf(upper) + fore_xf(fore), fore_xf.pt(KNUCKLE)


def plague_hulk_build(p):
    root = Xf.move(p.cx, p.cy)
    swell = 1.0 + 0.07 * p.swell + 0.035 * p.breath
    spread = 1.0 + 0.30 * p.sag
    body_xf = (Xf.scale(p.bs * p.sx * swell * spread, p.bs * p.sy * (swell ** 0.6) * (1 - 0.32 * p.sag), (80, 232))
               .then(Xf.turn(p.lean, CORE)).then(Xf.move(p.bx, p.by)).then(root))
    wave = math.sin(p.ripple * math.pi * 2) * (1 - p.ripple) * 4.0 if p.ripple > 0 else 0.0

    roll = 4.0 * p.roll + 3.0 * p.roll_jig
    # ---- the mass: shoulders, chest, belly - one organic lump, no neck
    mass = [
        blob(50, 90, 30, 25, "flesh", depth=24, k=12),                        # left shoulder hump (the bigger one)
        blob(110, 93, 26, 21, "flesh", depth=22, k=12),                       # right shoulder hump
        blob(80, 108, 50, 32, "flesh", depth=30, k=12),                       # upper chest
        blob(80, 140, 50, 30, "flesh", depth=30, k=12),                       # chest at the collar
        blob(80, 180, 45, 36, "flesh", depth=28, k=12),                       # the belly
        blob(80 + wave, 74, 20, 18, "flesh", depth=17, k=10),                 # the head, sunk between the humps
        blob(118, 78, 9, 7, "flesh_dk", op="add", depth=4),                   # a tumour on the right hump
        blob(126, 86, 5, 4, "flesh_dk", op="add", depth=2.5),
        blob(58, 128, 14, 18, "flesh_dk", op="add", depth=3),                 # bruising under the arm
        blob(108, 182, 12, 10, "flesh_dk", op="add", depth=2.5),
        # flesh bulging OVER the collar (it rolls with the breath) and UNDER it
        blob(56 + wave, 120 + roll, 18, 9, "flesh", op="add", depth=5),
        blob(84 - wave, 122 + roll, 20, 9, "flesh", op="add", depth=6),
        blob(108 + wave, 121 + roll, 15, 8, "flesh", op="add", depth=5),
        blob(64, 150 - 0.5 * roll, 17, 8, "flesh", op="add", depth=5),
        blob(98, 151 - 0.5 * roll, 19, 9, "flesh", op="add", depth=5),
        # skin folds carved across the belly
        limb(52, 190, 74, 198, 1.4, 1.4, "flesh", op="carve"),
        limb(86, 200, 108, 192, 1.4, 1.4, "flesh", op="carve"),
        limb(60, 168, 72, 172, 1.0, 1.0, "flesh", op="carve"),
    ]
    # ---- the tank's collar ring: the front arc of a steel band around the middle, broken on the right
    ring = []
    arc = [(80 + 49 * math.cos(a), 137 + 10 * math.sin(a)) for a in [math.pi * (1 - i / 12) for i in range(13)]]
    arc = [(x, y) for x, y in arc]
    for i in range(len(arc) - 1):
        if i in (8,):                                                         # the break
            continue
        ring.append(limb(*arc[i], *arc[i + 1], 6.0, 6.0, "gunmetal", k=2, prio=30))
    ring += [limb(arc[i][0], arc[i][1] - 4, arc[i][0], arc[i][1] + 4, 0.9, 0.9, "gunmetal", op="carve") for i in (2, 5, 10)]
    ring += [blob(arc[i][0], arc[i][1] - 3.2, 1.3, 1.3, "darkmetal", op="add", depth=1.1) for i in (1, 3, 4, 6, 9, 11)]
    ring += [blob(arc[i][0], arc[i][1] + 3.4, 1.3, 1.3, "darkmetal", op="add", depth=1.1) for i in (1, 3, 4, 6, 9, 11)]
    # the snapped ends, peeled outward
    ring += [limb(*arc[8], arc[8][0] + 3, arc[8][1] - 8, 4.0, 2.2, "gunmetal", k=1, prio=30),
             limb(*arc[9], arc[9][0] - 2, arc[9][1] + 8, 4.0, 2.2, "gunmetal", k=1, prio=30)]
    # ---- the face: three small sunken ichor eyes (asymmetric) and a slit mouth with a row of teeth
    eye = p.eye_glow * (1 - p.dim)
    face = [
        *with_glow([blob(x + wave, y, r, r * 0.8, "ichor", op="add", depth=r) for x, y, r in
                    ((72, 70, 2.8), (88, 68, 2.4), (93, 75, 1.6))], eye),
        blob(72 + wave, 70, 4.6, 3.6, "flesh_dk", op="carve", depth=1.0),
        limb(68 + wave, 84 + p.jaw, 94 + wave, 83 + p.jaw, 1.6 + 2.2 * p.jaw, 1.4 + 2.0 * p.jaw, "flesh_dk", op="carve"),
        limb(69 + wave, 84 + p.jaw * 0.6, 93 + wave, 83 + p.jaw * 0.6, 1.5 + 2.4 * p.jaw, 1.3 + 2.2 * p.jaw, "pupil",
             op="add", depth=0.5),
        *teeth_row(70 + wave, 82.0, 92 + wave, 81.0, 6, 2.8 + 1.5 * p.jaw, 1.2, side=-1, seed=5, jitter=0.35, op="add"),
        *teeth_row(72 + wave, 86.5 + 1.2 * p.jaw, 90 + wave, 85.5 + 1.2 * p.jaw, 5, 2.2 + 1.2 * p.jaw, 1.0, side=1, seed=6, jitter=0.35, op="add"),
    ]
    # ---- ichor blisters across the left shoulder (they pulse; priming swells and lights them each turn)
    pulse = 1.0 + 0.08 * p.blister + 0.16 * p.swell
    level = p.blister_glow * (1.0 + 0.3 * p.blister + 0.45 * p.swell) * (1 - p.dim)
    blisters = []
    for i, (x, y, rx, ry, d) in enumerate(((46, 80, 9, 8, 6), (60, 72, 7, 6, 5), (36, 94, 6, 6, 4), (56, 90, 5, 4, 3),
                                           (66, 82, 4, 4, 3), (40, 68, 4, 3.5, 3))):
        size = pulse * (1 - 0.15 * min(1.0, max(0.0, p.dim * 6 - i)))
        blisters.append(blob(x, y, rx * size, ry * size, "membrane", op="add", depth=d * size, glow=level))
    # priming: the skin stretches - pale strain lines across the belly, and new blisters break the surface
    if p.swell > 0.05:
        s = min(1.0, p.swell)
        blisters += [blob(96, 100, 6 * s, 5 * s, "membrane", op="add", depth=4 * s, glow=level),
                     blob(64, 176, 7 * s, 6 * s, "membrane", op="add", depth=4 * s, glow=level),
                     blob(102, 172, 5 * s, 5 * s, "membrane", op="add", depth=3 * s, glow=level)]
        mass += [limb(56, 164, 66, 196, 0.9, 0.7, "flesh_dk", op="carve"),
                 limb(100, 160, 92, 198, 0.9, 0.7, "flesh_dk", op="carve")]
    if p.swell > 1.05:                                                        # about to go: the seams light up
        s = min(1.0, p.swell - 1.0)
        blisters += with_glow([limb(56, 164, 66, 196, 0.8 * s + 0.3, 0.6 * s + 0.3, "ichor", op="add", depth=0.8),
                               limb(100, 160, 92, 198, 0.8 * s + 0.3, 0.6 * s + 0.3, "ichor", op="add", depth=0.8)],
                              1.0 + 0.6 * s)
    # wounded: a split above the collar, leaking ichor down over the steel
    if p.tear > 0.05:
        mass += [limb(94, 112, 108, 118, 1.6 * p.tear, 1.0 * p.tear, "flesh_dk", op="carve")]
        blisters += with_glow([limb(96, 113, 106, 117, 1.1 * p.tear, 0.8 * p.tear, "ichor", op="add", depth=0.8)], 1.2)
        if p.drip > 0.02:
            drop = 120 + 18 * p.drip
            blisters += with_glow([limb(103, 117, 103.5, drop - 1.5, 1.0, 0.7, "ichor", op="add", depth=1.0),
                                   blob(103.6, drop, 1.4, 1.7 + 0.5 * p.drip, "ichor", op="add", depth=1.2)], 1.1)
    # the burst: the mass tears open along the strain lines, ichor everywhere, the collar flies off its break
    if p.burst > 0.02:
        b = p.burst
        mass += [blob(80, 150, 30 * b, 40 * b, "flesh_dk", op="carve", depth=18 * b)]
        blisters += with_glow([blob(80, 150, 24 * b, 32 * b, "ichor", op="add", depth=4 * b)], 1.6 * (1.2 - b))

    legs = [
        limb(63, 196, 60, 222, 13, 12, "flesh", k=7), blob(59, 225, 13, 5.5, "flesh_dk", depth=6, k=5),
        limb(97, 196, 100, 222, 13, 12, "flesh", k=7), blob(101, 225, 13, 5.5, "flesh_dk", depth=6, k=5),
    ]

    left_arm, left_fist = _arm(p.l_sh, p.l_el, p.l_fist)
    right_raw, right_fist = _arm(p.r_sh, p.r_el, p.r_fist)
    right_arm = mirror(right_raw, AX)
    right_fist = (2 * AX - right_fist[0], right_fist[1])
    arm_xf = (Xf.scale(p.bs * (1 + 0.05 * p.swell) * (1 + 0.18 * p.sag), p.bs * (1 - 0.12 * p.sag), (80, 232))
              .then(Xf.move(p.bx * 0.6, p.by * 0.6)).then(root))

    collar_layer = ring if p.burst < 0.5 else Xf.move(14 * p.burst, 10 * p.burst).then(Xf.turn(0.4 * p.burst, (110, 140)))(ring)
    layers = [
        root(legs),
        body_xf(mass + face + blisters),
        body_xf(collar_layer),
        arm_xf(left_arm),
        arm_xf(right_arm),
    ]
    anchors = {
        "ground": (80.0 + p.cx, 232.0 + p.cy),
        "hit_centre": body_xf.pt((80, 130)),
        "eye": body_xf.pt((80, 71)),
        "mouth": arm_xf.pt(lerp_pt(left_fist, right_fist, 0.5)),
    }
    stencils = []
    if p.tag > 0.5 and p.burst < 0.3:
        sx, sy = body_xf.pt((68, 136))
        stencils.append(("T-09", sx, sy, (230, 228, 220)))
    return Figure(layers, anchors, stencils)


PLAGUE_HULK = Rig("plague_hulk", plague_hulk_build, HULK_POSE, seed=31)

HULK_CLIPS = []
