"""EYE TYRANT rig + clips (ABERRATION, CHAFF). Imported by bestiary.py.

One huge eye that was being studied, hanging in a cradle of fleshy lids and petals. A surgical speculum
(gunmetal) still holds its lids open, its thumbscrew on the right, a specimen tag wired to it; nerve tendrils
trail beneath. The ichor iris points at the player. The RENDERER hovers this type, so it stands on the canvas
floor (guide 6): the tendrils reach the ground line and dangle when it floats.
"""
import math

from kit import blob, limb, chain, eyeball, riveted_plate, with_glow, Xf, Figure, Rig, Clip, jiggle, lerp, lerp_pt

W, H, AX = 160, 240, 80
EYE = (80, 100)                   # the eye's centre at rest
EYE_R = 33.0

TYRANT_POSE = dict(
    cx=0.0, cy=0.0,                              # whole creature offset
    bx=0.0, by=0.0, bs=1.0, tilt=0.0,            # body: offset, scale toward the viewer, roll about the eye (rad)
    look_x=0.0, look_y=0.0,                      # where the iris points (-1..1 of its travel)
    pupil=0.42, slit=1.0,                        # pupil size (/ iris); pupil width (1 round -> 0.1 a line)
    lid_top=0.0, lid_bot=0.0,                    # lids: + closing over the eye, - peeled back past the speculum
    iris_glow=1.0, dim=0.0,                      # iris light; death dimming
    cloud=0.0,                                   # DEATH: the eye clouds over (0..1)
    petal=0.0,                                   # the petals breathe (+ open, - close)
    wilt=0.0,                                    # DEATH: the cradle wilts (0..1)
    sway=0.0, sway_jig=0.0,                      # tendril sway (rad) and its lagging whip
    limp=0.0,                                    # tendrils hang limp and gather (0..1)
    slump=0.0,                                   # DEATH: on the floor, the tendrils splay out sideways (0..1)
    weep=0.0, drip=0.0,                          # WOUNDED: ichor weeping from the lower lid (0..1) and its drip phase
    rattle=0.0,                                  # HURT: the speculum rattles (0..1)
    tag=1.0,
)

# petals radiating behind the eye: (angle from straight up, clockwise, rad; length; width; material)
PETALS = ((-2.3, 30, 20, "flesh_dk"), (-1.5, 38, 22, "flesh"), (-0.75, 40, 21, "flesh_dk"), (0.0, 36, 21, "flesh"),
          (0.7, 41, 21, "flesh_dk"), (1.45, 37, 22, "flesh"), (2.25, 31, 20, "flesh_dk"))
# nerve tendrils: root x on the cradle's underside, length, curl, thickness, sway phase
TENDRILS = ((58, 52, -9, 4.6, 0.0), (69, 70, 6, 5.2, 1.3), (81, 78, -5, 5.6, 2.4), (92, 64, 8, 5.0, 3.7),
            (103, 48, -7, 4.4, 5.0))


def _petal_shapes(angle, length, width, mat, open_amount, wilt):
    """One petal, a long fleshy lobe pointing out from behind the eye. Wilting turns it down and shrinks it."""
    droop = wilt * (1.2 if angle < 0 else -1.2) * (0.6 + 0.4 * abs(math.sin(angle)))
    a = angle + droop
    reach = EYE_R * 0.7 + length * (1 + 0.12 * open_amount) * (1 - 0.25 * wilt)
    dx, dy = math.sin(a), -math.cos(a)
    tip = (EYE[0] + dx * reach, EYE[1] + dy * reach)
    mid = (EYE[0] + dx * reach * 0.62, EYE[1] + dy * reach * 0.62)
    return [blob(mid[0], mid[1], length * 0.62, width * (1 - 0.2 * wilt) * 0.5, mat, depth=9, angle=a - math.pi / 2, k=6),
            limb(*mid, *tip, width * 0.32, 3.5, mat, k=6),
            limb(mid[0] - dx * 4, mid[1] - dy * 4, tip[0] - dx * 4, tip[1] - dy * 4, 1.0, 0.6, "flesh_dk", op="carve")]


def eye_tyrant_build(p):
    root = Xf.move(p.cx, p.cy)
    body_xf = (Xf.scale(p.bs, p.bs, EYE).then(Xf.turn(p.tilt, EYE)).then(Xf.move(p.bx, p.by)).then(root))
    rattle = (1.2 if p.rattle > 0.5 else -1.2) * min(1.0, p.rattle * 2)

    petals = []
    for angle, length, width, mat in PETALS:
        petals += _petal_shapes(angle, length, width, mat, p.petal, p.wilt)

    # the cradle: a fleshy cup holding the eye from below, veined, narrowing to where the tendrils hang
    cradle = [
        blob(80, 120, 42, 30, "flesh", depth=22, k=10),
        blob(80, 144, 28, 16, "flesh_dk", depth=14, k=10),
        *chain([(50, 116), (60, 134), (74, 146)], 1.5, 1.1, "flesh_dk", op="add", depth=1.2),
        *chain([(112, 112), (104, 134), (90, 148)], 1.5, 1.1, "flesh_dk", op="add", depth=1.2),
    ]

    tendrils = []
    for x, length, curl, thick, phase in TENDRILS:
        swing = p.sway * math.sin(phase) + p.sway_jig * math.cos(phase)
        gather = (80 - x) * 0.35 * p.limp
        pts = []
        spread_dir = -1 if x < 81 else 1
        for i in range(5):
            t = i / 4
            ox = curl * math.sin(t * math.pi) * (1 - 0.6 * p.limp) + swing * 30 * t * t + gather * t
            px, py = x + ox, 146 + length * t * (1 + 0.08 * p.limp)
            # slumped on the floor: the tendril folds out sideways instead of hanging (it would go through the floor)
            sx, sy = x + spread_dir * length * t * (0.55 + 0.15 * phase / 5), 146 + length * t * 0.12 + 6 * t
            pts.append((lerp(px, sx, p.slump), lerp(py, sy, p.slump)))
        tendrils += chain(pts, thick, 1.4, "flesh_dk", k=4)
        tendrils.append(blob(pts[-1][0], pts[-1][1], 2.2, 2.6, "flesh", depth=2, k=2))     # a nerve bulb at the tip

    lid_top = p.lid_top
    lid_bot = p.lid_bot
    eye = eyeball(EYE[0], EYE[1], EYE_R, look=(p.look_x, p.look_y), iris=0.56, pupil=p.pupil * (1 + 0.6 * p.cloud),
                  slit=p.slit, lid_top=lid_top, lid_bottom=lid_bot, lids=True, lid_mat="flesh",
                  glow=p.iris_glow * (1 - p.dim), depth=EYE_R * 0.75, prio=10)
    if p.cloud > 0.02:      # the eye clouds over: a milky film spreads from the centre over the iris
        eye.append(blob(EYE[0] + p.look_x * 10, EYE[1] + p.look_y * 10, EYE_R * 0.62 * p.cloud, EYE_R * 0.58 * p.cloud,
                        "sclera", op="add", depth=1.5))
    if p.weep > 0.02:       # wounded: ichor weeping from the lower lid's corner, running down the cradle
        run = 8 + 22 * p.weep
        eye += with_glow([limb(66, 128, 64, 128 + run * 0.6, 1.4, 1.0, "ichor", op="add", depth=1.0),
                          limb(64, 128 + run * 0.6, 65, 128 + run, 1.0, 0.8, "ichor", op="add", depth=1.0)], 1.0)
        if p.drip > 0.02:
            eye += with_glow([blob(65, 128 + run + 3 + 10 * p.drip, 1.4, 1.8, "ichor", prio=20)], 1.0)
    # veins over the sclera, running in from the rim
    eye += [limb(*a, *b, 0.7, 0.5, "flesh_dk", op="add", depth=0.5) for a, b in
            (((50, 108), (58, 106)), ((58, 106), (62, 110)), ((108, 92), (101, 95)), ((110, 104), (104, 103)),
             ((66, 126), (70, 120)))]

    # the speculum: two curved blades hooked under the lids, a side bar and a thumbscrew on the right
    sx = rattle
    top_y = EYE[1] - EYE_R * (1 - 2 * min(0.0, lid_top)) - 1 + 4 * max(0.0, lid_top)
    bot_y = EYE[1] + EYE_R * (1 - 2 * min(0.0, lid_bot)) + 1 - 4 * max(0.0, lid_bot)
    top_arc = [(EYE[0] - 30 + i * 10 + sx, top_y + 4 - 0.11 * (i * 10 - 30) ** 2 / 9 * 1.0) for i in range(7)]
    bot_arc = [(EYE[0] - 28 + i * 9.3 + sx, bot_y - 4 + 0.11 * (i * 9.3 - 28) ** 2 / 9 * 1.0) for i in range(7)]
    speculum = chain(top_arc, 2.4, 2.4, "gunmetal", k=1) + chain(bot_arc, 2.4, 2.4, "gunmetal", k=1)
    bar_x = EYE[0] + 36 + sx
    speculum += [
        limb(top_arc[-1][0], top_arc[-1][1], bar_x, top_arc[-1][1] + 6, 2.4, 2.4, "gunmetal", k=1),
        limb(bot_arc[-1][0], bot_arc[-1][1], bar_x, bot_arc[-1][1] - 6, 2.4, 2.4, "gunmetal", k=1),
        limb(bar_x, top_arc[-1][1] + 6, bar_x, bot_arc[-1][1] - 6, 3.0, 3.0, "gunmetal", k=1),
        # the thumbscrew and its knurled knob
        limb(bar_x, EYE[1] - 2, bar_x + 10, EYE[1] - 2, 1.6, 1.6, "darkmetal", k=1),
        blob(bar_x + 12, EYE[1] - 2, 3.4, 4.6, "gunmetal", depth=3, k=1),
        *[limb(bar_x + 10.5, EYE[1] - 5.5 + i * 2.3, bar_x + 13.5, EYE[1] - 5.5 + i * 2.3, 0.5, 0.5, "darkmetal",
               op="carve") for i in range(4)],
        # the hooks biting the lids
        limb(top_arc[0][0], top_arc[0][1], top_arc[0][0] - 3, top_arc[0][1] + 5, 2.0, 1.2, "gunmetal", k=1),
        limb(bot_arc[0][0], bot_arc[0][1], bot_arc[0][0] - 3, bot_arc[0][1] - 5, 2.0, 1.2, "gunmetal", k=1),
    ]
    # the specimen tag wired to the side bar
    tag_xf = Xf.turn(0.12 + 0.3 * p.sway, (bar_x, bot_arc[-1][1] - 6))
    tag = tag_xf([limb(bar_x, bot_arc[-1][1] - 6, bar_x + 2, bot_arc[-1][1] + 6, 0.6, 0.6, "darkmetal", k=0),
                  *riveted_plate([(bar_x - 6, bot_arc[-1][1] + 6), (bar_x + 9, bot_arc[-1][1] + 6),
                                  (bar_x + 9, bot_arc[-1][1] + 15), (bar_x - 6, bot_arc[-1][1] + 15)], "livery",
                                 rivet_mat="darkmetal", spacing=20, inset=1.4, rivet_r=0.8, bevel=1.2)])
    tag_pos = tag_xf.pt((bar_x - 4.5, bot_arc[-1][1] + 8))

    layers = [
        body_xf(petals),
        body_xf(tendrils),
        body_xf(cradle),
        body_xf(eye),
        body_xf(speculum),
        body_xf(tag),
    ]
    pupil_at = body_xf.pt((EYE[0] + p.look_x * (EYE_R - EYE_R * 0.56 * 0.9), EYE[1] + p.look_y * (EYE_R - EYE_R * 0.56 * 0.9)))
    anchors = {
        "ground": (80.0 + p.cx, 232.0 + p.cy),
        "hit_centre": body_xf.pt(EYE),
        "eye": pupil_at,
        "muzzle": pupil_at,                        # the beam leaves the pupil (A5)
    }
    stencils = []
    if p.tag > 0.5:
        stencils.append(("E3", tag_pos[0], tag_pos[1], (40, 42, 46)))
    return Figure(layers, anchors, stencils)


EYE_TYRANT = Rig("eye_tyrant", eye_tyrant_build, TYRANT_POSE, seed=37)


_LOOK = dict(look_x=0.0, look_y=0.0, pupil=0.42, slit=1.0, lid_top=0.0, lid_bot=0.0)

# IDLE: the pupil saccades (sharp jumps, held), the petals breathe, the tendrils sway and whip a beat behind the bob
_IDLE = Clip("IDLE", [
    (0.00, dict(look_x=0.0, look_y=0.0), "hold"),
    (0.30, dict(look_x=0.0, look_y=0.0), "out"),
    (0.36, dict(look_x=-0.45, look_y=0.15), "hold"),
    (0.72, dict(look_x=-0.45, look_y=0.15), "out"),
    (0.78, dict(look_x=0.3, look_y=-0.25), "hold"),
    (1.08, dict(look_x=0.3, look_y=-0.25), "out"),
    (1.14, dict(look_x=0.0, look_y=0.0), "hold"),
    (0.00, dict(petal=0.0, by=0.0, sway=-0.12, pupil=0.42)),
    (0.75, dict(petal=1.0, by=-3.0, sway=0.12, pupil=0.38)),
], duration=1.5, mode="loop")
IDLE = Clip("IDLE", _IDLE.keys + jiggle(_IDLE, "by", 1.5, out_param="sway_jig", freq=1.6, damping=0.25, gain=0.06, fps=12),
            duration=1.5, mode="loop")
_DEATH_END = dict(cloud=1.0, dim=1.0, wilt=1.0, limp=1.0, slump=1.0, by=68.0, bs=0.92, tilt=0.25, pupil=0.7,
                  lid_top=0.22, lid_bot=0.1, sway=0.0)

TYRANT_CLIPS = [
    IDLE,
    IDLE.variant("DORMANT", speed=0.5, fps=6, lid_top=0.36, lid_bot=0.24, iris_glow=0.6, petal=-0.6, limp=0.4,
                 look_x=0.0, look_y=0.35),
    Clip("ALERT", [        # the iris snaps onto you: the lids fly open, the pupil pinches, then settles
        (0.00, dict(_LOOK, look_x=-0.5, look_y=0.3, iris_glow=1.0, petal=0.0, lid_top=0.2, lid_bot=0.15)),
        (0.08, dict(look_x=0.0, look_y=0.0, pupil=0.22, iris_glow=1.45, petal=1.4, lid_top=-0.1, lid_bot=-0.08, by=-4.0), "out"),
        (0.25, dict(pupil=0.3, iris_glow=1.3, petal=1.0, by=-2.0)),
        (0.42, dict(_LOOK, iris_glow=1.1, petal=0.3, by=0.0)),
    ], mode="once"),
    Clip("MOVE", [         # it drifts: a bob forward, the tendrils trail and whip
        (0.000, dict(by=0.0, tilt=0.0, sway=-0.3, petal=0.3)),
        (0.167, dict(by=-4.0, tilt=0.03, sway=0.3, petal=-0.2)),
    ], duration=0.333, mode="loop"),
    Clip("WIND_UP", [      # the telegraph: the pupil narrows to a line down its lane, the lids peel back, held
        (0.00, dict(_LOOK, iris_glow=1.0, petal=0.0)),
        (0.25, dict(slit=0.35, lid_top=-0.08, lid_bot=-0.06, petal=0.8), "inout"),
        (0.50, dict(_LOOK, slit=0.1, pupil=0.48, lid_top=-0.14, lid_bot=-0.1, iris_glow=1.35, petal=1.2, by=-2.0)),
    ], mode="hold"),
    Clip("ATTACK", [       # the beam: the iris flares on the emit frame, the whole eye recoils
        (0.00, dict(_LOOK, slit=0.1, pupil=0.48, lid_top=-0.14, lid_bot=-0.1, iris_glow=1.35, petal=1.2)),
        (0.05, dict(slit=0.6, pupil=0.3, lid_top=-0.14, lid_bot=-0.1, iris_glow=2.1, by=-3.0, bs=1.05, petal=1.4), "out"),
        (0.15, dict(by=3.0, bs=0.98, iris_glow=1.3, lid_top=0.08, lid_bot=0.0, petal=0.6)),
        (0.30, dict(_LOOK, by=0.0, bs=1.0, iris_glow=1.0, petal=0.0)),
    ], fps=20, mode="once", events={0.05: "emit"}),
    Clip("HURT_LIGHT", [   # the lids try to shut against the speculum, which rattles
        (0.00, dict(lid_top=0.22, lid_bot=0.18, rattle=1.0, pupil=0.3, by=-2.0, iris_glow=0.6), "out"),
        (0.17, dict(lid_top=0.0, lid_bot=0.0, rattle=0.0, pupil=0.42, by=0.0, iris_glow=1.0)),
    ], mode="once"),
    Clip("HURT_HEAVY", [   # knocked back: the lids slam on the speculum, the cradle tilts, the tendrils whip
        (0.00, dict(cy=0.0, tilt=0.0, lid_top=0.0, lid_bot=0.0, rattle=0.0, sway=0.0)),
        (0.08, dict(cy=-5.0, tilt=-0.12, lid_top=0.34, lid_bot=0.26, rattle=1.0, pupil=0.25, iris_glow=0.4, sway=0.5,
                    look_x=0.3, look_y=-0.3, petal=-0.6), "out"),
        (0.25, dict(cy=-2.0, tilt=-0.05, lid_top=0.15, lid_bot=0.1, rattle=0.0, sway=-0.3, iris_glow=1.2)),
        (0.42, dict(_LOOK, cy=0.0, tilt=0.0, sway=0.0, iris_glow=1.0, petal=0.0)),
    ], mode="once"),
    Clip("DEFEND", [       # braces: the petals fold over the eye, the lids clamp as far as the speculum lets them
        (0.00, dict(_LOOK, petal=0.0, iris_glow=1.0, tag=1.0)),
        (0.33, dict(lid_top=0.36, lid_bot=0.28, petal=-1.6, iris_glow=0.7, by=3.0, limp=0.3, pupil=0.3)),
    ], mode="hold"),
    IDLE.variant("WOUNDED", keys=[
        (0.00, dict(drip=0.05)), (1.30, dict(drip=1.0), "hold"), (1.40, dict(drip=0.05)),
    ], weep=1.0, tilt=0.06, lid_top=0.12, limp=0.25),
    Clip("DEATH", [        # the eye clouds over, the cradle wilts, it drops (the renderer lowers its hover) and
        (0.00, dict(_LOOK, cloud=0.0, dim=0.0, wilt=0.0, limp=0.0, slump=0.0, by=0.0, iris_glow=1.0)),   # sprawls
        (0.10, dict(pupil=0.2, iris_glow=1.4, lid_top=-0.1, petal=1.2, by=-4.0), "out"),
        (0.35, dict(cloud=0.6, dim=0.6, wilt=0.5, limp=0.6, pupil=0.6, lid_top=0.15, by=20.0, petal=0.0), "in"),
        (0.55, dict(_DEATH_END, by=71.0), "in"),
        (0.65, dict(by=64.0, slump=0.9), "out"),
        (0.80, _DEATH_END, "hold"),
    ], mode="hold"),
    Clip("DEBUFF_PLAYER", [   # ability: the iris contracts to a pinpoint, the lids peel all the way back
        (0.00, dict(_LOOK, iris_glow=1.0, petal=0.0)),
        (0.15, dict(pupil=0.12, lid_top=-0.18, lid_bot=-0.14, iris_glow=1.5, petal=1.6, by=-3.0, bs=1.04), "out"),
        (0.35, dict(pupil=0.1, iris_glow=1.6), "inout"),
        (0.55, dict(_LOOK, iris_glow=1.1, petal=0.2, by=0.0, bs=1.0)),
    ], mode="once"),
]
