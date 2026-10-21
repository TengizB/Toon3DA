"""PLAGUE HULK rig + clips (ABERRATION, SOLDIER). Imported by bestiary.py.

A mass of harvested tissue that grew in a containment tank until it burst it. It still wears the tank's broken
steel collar ring around its middle, flesh bulging over and under it. Wide, no neck, two enormous forearms
knuckled on the floor, ichor blisters across its left shoulder. A stencilled tank number on the collar.
"""
import math

from kit import blob, limb, chain, teeth_row, mirror, with_glow, Xf, Figure, Rig, Clip, jiggle, lerp, lerp_pt

W, H, AX = 160, 240, 80
# Designed on the 160x240 grid, then NARROWED about the centre line so the swinging arms and the death spread stay on
# the canvas (the Stalker's STAND idea, sideways): every layer, anchor and stencil.
NARROW = 0.86
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
    swell = 1.0 + 0.09 * p.swell + 0.035 * p.breath
    spread = 1.0 + 0.18 * p.sag
    body_xf = (Xf.scale(p.bs * p.sx * (1 + 0.06 * p.swell + 0.035 * p.breath) * spread, p.bs * p.sy * swell * (1 - 0.32 * p.sag), (80, 232))
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
    pulse = 1.0 + 0.08 * p.blister + 0.28 * p.swell
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
    if p.swell > 0.5:                                                         # the strain lines light from inside
        s = min(1.0, (p.swell - 0.5) / 1.5)
        blisters += with_glow([limb(56, 164, 66, 196, 0.9 + 1.0 * s, 0.7 + 0.8 * s, "ichor", op="add", depth=0.8),
                               limb(100, 160, 92, 198, 0.9 + 1.0 * s, 0.7 + 0.8 * s, "ichor", op="add", depth=0.8),
                               limb(70, 104, 64, 122, 0.6 + 0.8 * s, 0.5 + 0.6 * s, "ichor", op="add", depth=0.6)],
                              0.5 + 0.3 * s)
        mass += [limb(70, 104, 64, 122, 0.9, 0.7, "flesh_dk", op="carve")]
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
        # the strain lines tear open into one ragged split down the belly, its lips curling out
        mass += [limb(78, 150 - 20 * b, 82, 196 + 4 * b, 2 + 7 * b, 2 + 5 * b, "flesh_dk", op="carve"),
                 limb(70, 158, 66 - 6 * b, 190, 2.5 * b, 1.5 * b, "flesh_dk", op="add"),
                 limb(90, 158, 94 + 6 * b, 190, 2.5 * b, 1.5 * b, "flesh_dk", op="add")]
        blisters += with_glow([limb(79, 152 - 18 * b, 81, 194, 1.2 + 2.2 * b, 1.0 + 1.6 * b, "ichor", op="add", depth=1.0)],
                              0.7 + 0.4 * (1 - b))

    legs = [
        limb(63, 196, 60, 222, 13, 12, "flesh", k=7), blob(59, 225, 13, 5.5, "flesh_dk", depth=6, k=5),
        limb(97, 196, 100, 222, 13, 12, "flesh", k=7), blob(101, 225, 13, 5.5, "flesh_dk", depth=6, k=5),
    ]

    left_arm, left_fist = _arm(p.l_sh, p.l_el, p.l_fist)
    right_raw, right_fist = _arm(p.r_sh, p.r_el, p.r_fist)
    right_arm = mirror(right_raw, AX)
    right_fist = (2 * AX - right_fist[0], right_fist[1])
    arm_xf = (Xf.scale(p.bs * (1 + 0.03 * p.swell) * (1 + 0.08 * p.sag), p.bs * (1 - 0.12 * p.sag), (80, 232))
              .then(Xf.move(p.bx * 0.6, p.by * (0.6 + 0.3 * p.sag))).then(root))

    collar_layer = ring if p.burst < 0.5 else Xf.move(14 * p.burst, 10 * p.burst).then(Xf.turn(0.4 * p.burst, (110, 140)))(ring)
    layers = [
        root(legs),
        body_xf(mass + face + blisters),
        body_xf(collar_layer),
        arm_xf(left_arm),
        arm_xf(right_arm),
    ]
    narrow = Xf.scale(NARROW, 1.0, (80, 232))
    layers = [narrow(layer) for layer in layers]
    anchors = {
        "ground": narrow.pt((80.0 + p.cx, 232.0 + p.cy)),
        "hit_centre": narrow.pt(body_xf.pt((80, 130))),
        "eye": narrow.pt(body_xf.pt((80, 71))),
        "mouth": narrow.pt(arm_xf.pt(lerp_pt(left_fist, right_fist, 0.5))),
    }
    stencils = []
    if p.tag > 0.5 and p.burst < 0.3:
        sx, sy = narrow.pt(body_xf.pt((70, 136)))
        stencils.append(("T-09", sx, sy, (230, 228, 220)))
    return Figure(layers, anchors, stencils)


PLAGUE_HULK = Rig("plague_hulk", plague_hulk_build, HULK_POSE, seed=31)


# Key poses
_REST_ARMS = dict(l_sh=0.0, l_el=0.0, r_sh=0.0, r_el=0.0, l_fist=1.0, r_fist=1.0)
_FISTS_HIGH = dict(l_sh=0.0, l_el=-2.75, r_sh=0.0, r_el=-2.75, by=-4.0, sy=1.04, sx=0.97, jaw=1.0, eye_glow=1.6, tag=1.0)
_SLAMMED = dict(l_sh=-0.32, l_el=0.05, r_sh=-0.32, r_el=0.05, l_fist=1.45, r_fist=1.45, by=10.0, sy=0.88, sx=1.08,
                jaw=0.2, eye_glow=1.9)
_PRIMED = dict(swell=1.0, l_sh=0.12, r_sh=0.12, l_el=-0.3, r_el=-0.3, by=-3.0, jaw=0.9, eye_glow=1.7, blister=1.0)

# IDLE: heavy breathing; the flesh rolls over the collar a beat behind the breath (jiggle); blisters pulse;
# the weight shifts from one fist to the other.
_IDLE = Clip("IDLE", [
    (0.00, dict(breath=0.0, blister=0.0, lean=0.0, l_sh=0.0, r_sh=0.0, jaw=0.0)),
    (0.55, dict(breath=1.0, blister=1.0, lean=0.015, l_sh=0.025, jaw=0.25)),
    (0.70, dict(r_sh=-0.02), "inout"),
    (1.20, dict(breath=0.0, blister=0.0, lean=-0.01, l_sh=0.0, r_sh=0.0, jaw=0.0)),
], duration=1.2, mode="loop")
IDLE = Clip("IDLE", _IDLE.keys + jiggle(_IDLE, "breath", 1.2, out_param="roll_jig", freq=2.2, damping=0.3, gain=1.6, fps=12),
            duration=1.2, mode="loop")

HULK_CLIPS = [
    IDLE,
    IDLE.variant("DORMANT", speed=0.5, fps=6, by=6.0, eye_glow=0.25, blister_glow=0.35, lean=0.03),
    Clip("ALERT", [        # it heaves upright, the eyes ignite, a bellow (jaw wide)
        (0.00, dict(_REST_ARMS, by=0.0, sy=1.0, eye_glow=1.0, jaw=0.0, breath=0.0)),
        (0.15, dict(by=-7.0, sy=1.05, breath=1.0, jaw=1.4, eye_glow=1.9, blister=1.0, l_sh=0.18, r_sh=0.18), "out"),
        (0.30, dict(by=-5.0, roll=1.0, jaw=1.1)),
        (0.45, dict(_REST_ARMS, by=0.0, sy=1.0, breath=0.3, roll=0.0, jaw=0.2, eye_glow=1.2, blister=0.3)),
    ], mode="once"),
    Clip("MOVE", [         # a lurching knuckle-walk: one fist swings forward, the mass rolls after it
        (0.000, dict(l_sh=-0.16, l_el=-0.12, r_sh=0.06, lean=-0.05, bx=-3.0, by=0.0, roll=-0.6, roll_jig=0.0)),
        (0.083, dict(l_sh=0.0, l_el=0.0, r_sh=0.0, lean=0.0, bx=0.0, by=-3.0, roll=0.8, roll_jig=0.6)),
        (0.167, dict(r_sh=-0.16, r_el=-0.12, l_sh=0.06, lean=0.05, bx=3.0, by=0.0, roll=-0.6, roll_jig=0.0)),
        (0.250, dict(r_sh=0.0, r_el=0.0, l_sh=0.0, lean=0.0, bx=0.0, by=-3.0, roll=0.8, roll_jig=-0.6)),
    ], duration=0.333, mode="loop"),
    Clip("WIND_UP", [      # AREA_STRIKE's telegraph: both fists clasped high over its head - held all turn
        (0.00, dict(_REST_ARMS, by=0.0, sy=1.0, sx=1.0, jaw=0.0, eye_glow=1.0, tag=1.0)),
        (0.22, dict(l_sh=-0.2, l_el=-1.6, r_sh=-0.2, r_el=-1.6, by=-2.0), "linear"),   # up the front, never out
        (0.50, _FISTS_HIGH, "inout"),
    ], mode="hold"),
    Clip("ATTACK", [       # a hammer blow: the right fist swings up and smashes down in front
        (0.00, dict(_REST_ARMS, by=0.0, sy=1.0, sx=1.0, lean=0.0, eye_glow=1.0, jaw=0.0)),
        (0.08, dict(r_sh=0.0, r_el=-2.6, lean=-0.06, by=-4.0, jaw=0.8, eye_glow=1.5), "out"),
        (0.14, dict(r_sh=-0.36, r_el=0.05, r_fist=1.4, lean=0.05, by=6.0, sy=0.94, sx=1.04, eye_glow=1.8), "in"),
        (0.22, dict(r_fist=1.2, by=4.0, sy=0.97)),
        (0.30, dict(_REST_ARMS, by=0.0, sy=1.0, sx=1.0, lean=0.0, eye_glow=1.0, jaw=0.0)),
    ], fps=20, mode="once", events={0.14: "impact"}),
    Clip("HURT_LIGHT", [   # the mass ripples
        (0.00, dict(ripple=0.25, by=-2.0, eye_glow=0.6, roll=0.8), "out"),
        (0.17, dict(ripple=0.0, by=0.0, eye_glow=1.0, roll=0.0)),
    ], mode="once"),
    Clip("HURT_HEAVY", [   # knocked back: a big wave through the flesh, the fists skid, the split opens
        (0.00, dict(cy=0.0, ripple=0.0, lean=0.0, sx=1.0, sy=1.0, tear=0.0)),
        (0.08, dict(cy=-4.0, ripple=0.3, lean=-0.06, sx=1.06, sy=0.94, l_sh=0.25, r_sh=0.25, jaw=1.2, eye_glow=0.4,
                    roll=1.0, tear=0.6), "out"),
        (0.25, dict(cy=-2.0, ripple=0.7, lean=-0.03, sx=0.98, sy=1.02, roll=-0.6, eye_glow=1.2)),
        (0.42, dict(cy=0.0, ripple=0.0, lean=0.0, sx=1.0, sy=1.0, l_sh=0.0, r_sh=0.0, jaw=0.0, eye_glow=1.0, roll=0.0,
                    tear=0.0)),
    ], mode="once"),
    Clip("DEFEND", [       # braces: both forearms raised across the face and chest, crouched behind them
        (0.00, dict(_REST_ARMS, by=0.0, sy=1.0, eye_glow=1.0, tag=1.0)),
        (0.08, dict(tag=0.0), "hold"),
        (0.33, dict(l_sh=-0.35, l_el=-2.1, r_sh=-0.35, r_el=-2.1, by=8.0, sy=0.95, sx=1.03, eye_glow=0.7, jaw=-0.2)),
    ], mode="hold"),
    IDLE.variant("WOUNDED", keys=[
        (0.00, dict(drip=0.05)), (0.95, dict(drip=1.0), "hold"), (1.05, dict(drip=0.05)),
    ], tear=1.0, lean=0.04, by=3.0, l_sh=0.08),
    Clip("DEATH", [        # it sags and spreads: the arms give, the mass settles into a heap round its collar
        (0.00, dict(_REST_ARMS, by=0.0, sag=0.0, eye_glow=1.0, dim=0.0, jaw=0.0, lean=0.0)),
        (0.10, dict(by=-4.0, jaw=1.3, eye_glow=1.7, l_sh=0.2, r_sh=0.2), "out"),
        (0.35, dict(l_sh=0.15, r_sh=0.12, l_el=-0.45, r_el=-0.4, by=3.0, sag=0.45, lean=0.03), "in"),
        (0.65, dict(l_sh=-0.05, r_sh=-0.1, l_el=-0.9, r_el=-0.75, by=5.0, sag=0.95, lean=0.06, jaw=0.4), "in"),
        (0.75, dict(by=1.0, sag=0.88), "inout"),
        (0.85, dict(by=4.0, sag=1.0), "hold"),
        (0.20, dict(dim=0.0)),
        (0.80, dict(dim=1.0)),
    ], mode="hold"),
    Clip("AREA_STRIKE", [  # ability: from the clasped wind-up, both fists down - a squash, the floor takes it
        (0.00, dict(_FISTS_HIGH)),
        (0.06, dict(by=-7.0, sy=1.07, sx=0.95), "in"),
        (0.14, dict(_SLAMMED), "in"),
        (0.20, dict(by=7.0, sy=0.92, sx=1.05, roll=1.0, roll_jig=1.0, l_fist=1.25, r_fist=1.25), "out"),
        (0.28, dict(roll=-0.5, roll_jig=-0.6)),
        (0.40, dict(_REST_ARMS, by=0.0, sy=1.0, sx=1.0, jaw=0.0, eye_glow=1.0, roll=0.0, roll_jig=0.0)),
    ], fps=20, mode="once", events={0.14: "impact"}),
    # SELF_DESTRUCT (R4): never the brace. PRIMING is HELD by the SELF_DESTRUCTING state - it swells, the arms
    # hang out from the stretched mass, the blisters brighten; SELF_DESTRUCT is the ability clip each countdown
    # turn plays - it swells further and the strain lines light up, and stays there (one level brighter per turn).
    Clip("PRIMING", [
        (0.00, dict(_REST_ARMS, swell=0.0, by=0.0, jaw=0.0, eye_glow=1.0, blister=0.0, tag=1.0)),
        (0.12, dict(swell=0.35, by=-2.0, jaw=0.5), "out"),
        (0.20, dict(swell=0.25), "inout"),
        (0.50, _PRIMED, "inout"),
    ], mode="hold"),
    Clip("SELF_DESTRUCT", [
        (0.00, dict(_PRIMED)),
        (0.10, dict(swell=1.45, by=-5.0, l_sh=0.2, r_sh=0.2, jaw=1.3, eye_glow=2.0, blister=1.4), "out"),
        (0.20, dict(swell=1.25), "inout"),
        (0.42, dict(_PRIMED, swell=2.0, by=-4.0, jaw=1.2, eye_glow=1.9, blister=1.3, l_sh=0.2, r_sh=0.2)),
    ], mode="hold"),
    # the self-destruct DEATH variant: it bursts - a flash of ichor from the strain lines, the collar flies off its
    # break, and what is left slumps in a split heap (its remains)
    Clip("DEATH_BURST", [
        (0.00, dict(_PRIMED, swell=2.0, burst=0.0, sag=0.0, dim=0.0, l_sh=0.2, r_sh=0.2)),
        (0.08, dict(swell=2.3, by=-6.0, jaw=1.5, eye_glow=2.2), "out"),
        (0.16, dict(burst=0.75, swell=1.2, by=4.0, jaw=0.6), "out"),
        (0.40, dict(burst=1.0, swell=0.4, sag=0.7, by=4.0, l_sh=0.15, r_sh=0.2, l_el=-0.2, r_el=-0.3), "in"),
        (0.62, dict(swell=0.0, sag=1.0, by=4.0, jaw=0.3, l_sh=0.1, r_sh=0.15), "hold"),
        (0.16, dict(dim=0.0)),
        (0.55, dict(dim=1.0)),
    ], mode="hold", variant_of="DEATH"),
]
