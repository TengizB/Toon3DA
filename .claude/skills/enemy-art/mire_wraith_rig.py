"""MIRE WRAITH rig + clips (ABERRATION, SOLDIER). Imported by bestiary.py.

A drooping, half-liquid thing from the facility sump: a translucent sagging belly of glowing acid glands, a
hunched draped body under a sagging hood, a long spout hanging from under the hood and aimed at the player, a
length of ribbed drainage hose fused into its back (the sump's clamp still on it, stencilled). It has no legs:
it slumps on a spreading puddle of itself.
"""
import math

from kit import blob, limb, chain, riveted_plate, mirror, with_glow, Xf, Figure, Rig, Clip, jiggle, lerp, lerp_pt

W, H, AX = 160, 240, 80
BASE = (80, 226)                  # the body scales and leans about its puddle

WRAITH_POSE = dict(
    cx=0.0, cy=0.0,                              # whole creature offset
    bx=0.0, by=0.0, bs=1.0, lean=0.0,            # body: offset, scale toward the viewer, roll about the base
    slosh=0.0, slosh_jig=0.0,                    # the belly's sideways slosh (px) and its lagging wobble
    draw=0.0,                                    # WIND-UP: the belly draws up (0..1)
    squeeze=0.0,                                 # ATTACK: the belly contracts (0..1)
    gland=0.0, gland_glow=1.0,                   # gland pulse; gland light
    aim=0.0,                                     # the spout lifts toward the player (0 hanging .. 1 levelled)
    spit=0.0,                                    # the spout's mouth opens and flares (0..1)
    splash=0.0,                                  # HURT: the mass splashes outward (0..1)
    melt=0.0,                                    # DEATH: it slumps into a puddle (0..1)
    hood=0.0, hood_jig=0.0,                      # the hood droops forward (+) / lifts (-); its lag
    drip=0.0,                                    # the torn hose drips (0..1 phase)
    eye_glow=1.0, dim=0.0,
    tag=1.0,
)


def mire_wraith_build(p):
    root = Xf.move(p.cx, p.cy)
    m = p.melt
    body_xf = (Xf.scale(p.bs * (1 + 0.55 * m) * (1 + 0.10 * p.splash), p.bs * (1 - 0.72 * m) * (1 - 0.07 * p.splash), BASE)
               .then(Xf.turn(p.lean, BASE)).then(Xf.move(p.bx, p.by)).then(root))
    hose_xf = (Xf.scale(p.bs * (1 + 0.05 * m), p.bs * (1 - 0.72 * m), BASE).then(Xf.turn(p.lean, BASE))
               .then(Xf.move(p.bx - 14 * m, p.by)).then(root))
    slosh = p.slosh + 2.5 * p.slosh_jig

    # ---- the puddle it slumps on (spreads when it melts)
    puddle = [
        blob(80, 224, 58 + 20 * m, 9 + 2 * m, "flesh_dk", depth=6, k=8),
        blob(46 - 8 * m, 226, 16, 6, "flesh_dk", depth=4, k=6),
        blob(116 + 8 * m, 225, 18, 6, "flesh_dk", depth=4, k=6),
        *with_glow([blob(70 + 30 * i, 223, 3 + 2 * m, 1.6 + 0.6 * m, "membrane", op="add", depth=1.2) for i in range(2)],
                   p.gland_glow * (1 - p.dim)),
    ]
    # ---- the hunched, draped body and the sagging hood
    hood_droop = 6 * p.hood + 3 * p.hood_jig
    draped = [
        blob(80, 132, 38, 34, "flesh_dk", depth=26, k=12),
        blob(80, 96 + hood_droop * 0.4, 30, 24, "flesh_dk", depth=20, k=12),          # the hood's crown
        blob(80, 112 + hood_droop, 26, 12, "flesh", depth=14, k=10),                  # the hood's lip, overhanging
        # drapes: folds of skin hanging from the shoulders down over the belly's sides
        *chain([(48, 108), (38, 146), (40, 184), (48, 214)], 9, 6, "flesh_dk", k=8),
        *chain([(114, 110), (124, 150), (122, 188), (112, 214)], 10, 6, "flesh_dk", k=8),
        limb(52, 118, 46, 176, 1.3, 1.0, "flesh_dk", op="carve"),
        limb(110, 120, 116, 178, 1.3, 1.0, "flesh_dk", op="carve"),
        limb(66, 84, 64, 104, 1.2, 1.0, "flesh_dk", op="carve"),
        limb(94, 84, 98, 104, 1.2, 1.0, "flesh_dk", op="carve"),
    ]
    eye = p.eye_glow * (1 - p.dim)
    draped += with_glow([blob(x, 104 + hood_droop * 0.6, r, r * 0.7, "ichor", op="add", depth=r) for x, r in
                         ((70, 2.0), (90, 2.0), (95, 1.3))], eye)

    # ---- the belly: a translucent sac of acid glands, sagging between the drapes
    rise = -22 * p.draw + 8 * p.squeeze
    width = 1.0 - 0.14 * p.squeeze - 0.06 * p.draw
    belly_c = (80 + slosh, 180 + rise * 0.5)
    # capped: past ~1 the glow ramp tops out in near-white, and white belongs to the hit flash (V5)
    level = min(1.12, p.gland_glow * (1 + 0.2 * p.gland + 0.25 * p.draw + 0.2 * p.squeeze)) * (1 - p.dim)
    belly = [
        blob(belly_c[0], belly_c[1], 40 * width, 36 + rise * -0.25, "membrane", depth=24, k=10, glow=level * 0.32),
        *with_glow([blob(belly_c[0] + dx * width, belly_c[1] + dy, r * (1 + 0.08 * p.gland), r * 0.9 * (1 + 0.08 * p.gland),
                         "membrane", op="add", depth=r * 0.9)
                    for dx, dy, r in ((-16, -8, 10), (12, -12, 9), (-3, 9, 11), (19, 10, 8), (-22, 15, 7))], level * 0.95),
        # the sac's skin: a few creases where it hangs from the body
        limb(belly_c[0] - 24, belly_c[1] - 26, belly_c[0] - 30, belly_c[1] - 6, 1.0, 0.8, "membrane", op="carve"),
        limb(belly_c[0] + 22, belly_c[1] - 26, belly_c[0] + 30, belly_c[1] - 8, 1.0, 0.8, "membrane", op="carve"),
    ]
    # ---- the spout: hangs from under the hood, curls forward; its mouth faces the player
    aim = p.aim
    root_pt = (80, 114 + hood_droop)
    mid = (87, 134 + hood_droop * 0.5 - 6 * aim)
    tip = (81, 152 - 12 * aim + 4 * p.squeeze)
    spout = chain([root_pt, mid, tip], 9.0, 7.0, "flesh", k=6)
    mouth_r = 8.0 + 2.6 * p.spit
    spout += [
        blob(tip[0], tip[1] + 2, mouth_r, mouth_r * (0.75 + 0.2 * aim), "flesh", depth=6, k=4),
        blob(tip[0], tip[1] + 2, mouth_r * 0.55, mouth_r * 0.42 * (0.75 + 0.2 * aim), "pupil", op="add", depth=0.8),
        *with_glow([blob(tip[0], tip[1] + 2.4, mouth_r * 0.35 * (0.4 + p.spit), mouth_r * 0.25 * (0.4 + p.spit), "ichor",
                         op="add", depth=0.8)], (0.7 + 1.4 * p.spit) * (1 - p.dim)),
        *[limb(root_pt[0] - 6 + i * 4, root_pt[1] + 2 + i * 6, root_pt[0] + 6 + i * 3, root_pt[1] + 3 + i * 6, 0.8, 0.8,
               "flesh_dk", op="carve") for i in range(3)],                            # ring creases
    ]
    # ---- the drainage hose, fused into its back on the right, arching over the shoulder, torn and dripping
    hose_pts = [(108, 104), (118, 74), (132, 66), (142, 84), (140, 108)]
    hose = chain(hose_pts, 5.2, 4.6, "darkmetal", k=3)
    hose += [blob(lerp(hose_pts[i][0], hose_pts[i + 1][0], f), lerp(hose_pts[i][1], hose_pts[i + 1][1], f), 5.8, 5.8,
                  "darkmetal", op="add", depth=0.8) for i in range(4) for f in (0.35, 0.8)]
    hose += riveted_plate([(124, 63), (133, 61), (135, 72), (126, 74)], "gunmetal", spacing=14, inset=1.3, rivet_r=0.8,
                          bevel=1.4, op="add")                                          # the sump clamp
    drop_y = 112 + 14 * p.drip
    hose += with_glow([blob(140, 110, 3.2, 2.4, "ichor", depth=2, prio=10),
                       limb(140, 112, 140.3, max(113.0, drop_y - 2), 1.3, 0.7, "ichor", prio=10),
                       blob(140.4, drop_y + 1, 1.5, 1.8 + 0.5 * p.drip, "ichor", prio=10)], 1.0 * (1 - p.dim))
    # the hose's root, fused: flesh grown over the pipe end
    fuse = [blob(108, 106, 9, 7, "flesh_dk", op="add", depth=3)]

    layers = [
        hose_xf(hose),
        root(puddle),
        body_xf(draped + fuse),
        body_xf(belly),
        body_xf(spout),
    ]
    tip_pt = body_xf.pt((tip[0], tip[1] + 2))
    anchors = {
        "ground": (80.0 + p.cx, 232.0 + p.cy),
        "hit_centre": body_xf.pt((80, 150)),
        "eye": body_xf.pt((80, 104 + hood_droop * 0.6)),
        "muzzle": tip_pt,                           # the acid leaves the spout's mouth
    }
    stencils = []
    if p.tag > 0.5 and m < 0.3:
        sx, sy = hose_xf.pt((125.5, 64.5))
        stencils.append(("S4", sx, sy, (230, 228, 220)))
    return Figure(layers, anchors, stencils)


MIRE_WRAITH = Rig("mire_wraith", mire_wraith_build, WRAITH_POSE, seed=43)


# IDLE: the belly sloshes side to side and wobbles after itself (jiggle), the glands pulse, the hood sags and
# settles a beat behind, the torn hose drips
_IDLE = Clip("IDLE", [
    (0.00, dict(slosh=-3.0, gland=0.0, by=0.0, hood=0.0, lean=-0.01)),
    (0.75, dict(slosh=3.0, gland=1.0, by=1.5, hood=0.6, lean=0.01)),
    (1.30, dict(drip=1.0), "hold"), (1.40, dict(drip=0.0)), (0.10, dict(drip=0.0)),
], duration=1.5, mode="loop")
IDLE = Clip("IDLE", _IDLE.keys
            + jiggle(_IDLE, "slosh", 1.5, out_param="slosh_jig", freq=2.6, damping=0.22, gain=0.6, fps=12)
            + jiggle(_IDLE, "by", 1.5, out_param="hood_jig", freq=2.0, damping=0.3, gain=0.8, fps=12),
            duration=1.5, mode="loop")
_DRAWN = dict(draw=1.0, aim=1.0, gland=1.0, gland_glow=1.25, by=-4.0, hood=-0.6, eye_glow=1.5, spit=0.2)

WRAITH_CLIPS = [
    IDLE,
    IDLE.variant("DORMANT", speed=0.5, fps=6, by=5.0, gland_glow=0.4, eye_glow=0.3, hood=1.2, bs=0.97),
    Clip("ALERT", [        # it heaves up out of its puddle, the hood lifts, the glands flare, the spout comes up
        (0.00, dict(by=0.0, hood=0.0, gland=0.0, aim=0.0, eye_glow=1.0, slosh=0.0)),
        (0.15, dict(by=-8.0, bs=1.04, hood=-1.0, gland=1.0, gland_glow=1.25, aim=0.8, eye_glow=1.8, slosh=-4.0), "out"),
        (0.30, dict(by=-5.0, slosh=3.0, hood=-0.6)),
        (0.45, dict(by=0.0, bs=1.0, hood=0.0, gland=0.3, gland_glow=1.0, aim=0.3, eye_glow=1.1, slosh=0.0)),
    ], mode="once"),
    Clip("MOVE", [         # it oozes: a surge, the belly lagging and sloshing after it
        (0.000, dict(by=0.0, bs=1.0, lean=-0.04, slosh=-5.0, splash=0.15, hood=0.3)),
        (0.167, dict(by=-3.0, bs=1.02, lean=0.04, slosh=5.0, splash=0.0, hood=-0.2)),
    ], duration=0.333, mode="loop"),
    Clip("WIND_UP", [      # the belly draws up, the glands brighten, the spout levels at you - held
        (0.00, dict(draw=0.0, aim=0.0, gland=0.0, gland_glow=1.0, by=0.0, hood=0.0, eye_glow=1.0, spit=0.0)),
        (0.50, _DRAWN, "inout"),
    ], mode="hold"),
    Clip("ATTACK", [       # the spit: the belly contracts, the spout flares on the emit frame, it recoils
        (0.00, dict(_DRAWN)),
        (0.06, dict(squeeze=1.0, draw=0.4, spit=1.0, gland_glow=1.6, by=2.0, slosh=0.0), "out"),
        (0.16, dict(squeeze=0.6, spit=0.5, by=-2.0, slosh=-3.0, gland_glow=1.2)),
        (0.30, dict(draw=0.0, squeeze=0.0, aim=0.2, spit=0.0, gland=0.0, gland_glow=1.0, by=0.0, hood=0.0, eye_glow=1.0,
                    slosh=2.0)),
    ], fps=20, mode="once", events={0.06: "emit"}),
    Clip("HURT_LIGHT", [   # it splashes and re-forms
        (0.00, dict(splash=0.6, slosh=4.0, hood=0.8, eye_glow=0.6), "out"),
        (0.17, dict(splash=0.0, slosh=0.0, hood=0.0, eye_glow=1.0)),
    ], mode="once"),
    Clip("HURT_HEAVY", [   # knocked back: the whole mass splashes outward, then pulls itself back together
        (0.00, dict(cy=0.0, splash=0.0, lean=0.0, slosh=0.0)),
        (0.08, dict(cy=-3.0, splash=1.0, lean=-0.06, slosh=-7.0, hood=1.2, gland_glow=1.5, eye_glow=0.4, by=4.0), "out"),
        (0.25, dict(cy=-1.0, splash=0.4, lean=-0.02, slosh=5.0, hood=0.4, gland_glow=1.1, eye_glow=1.2, by=1.0)),
        (0.42, dict(cy=0.0, splash=0.0, lean=0.0, slosh=0.0, hood=0.0, gland_glow=1.0, eye_glow=1.0, by=0.0)),
    ], mode="once"),
    Clip("DEFEND", [       # it hunches into itself: hood down over the spout, belly tucked, glands dimmed
        (0.00, dict(by=0.0, hood=0.0, draw=0.0, gland_glow=1.0, aim=0.0, tag=1.0)),
        (0.33, dict(by=8.0, bs=0.95, hood=2.0, draw=0.45, gland_glow=0.6, eye_glow=0.5, aim=0.0, lean=0.02)),
    ], mode="hold"),
    IDLE.variant("WOUNDED", keys=[
        (0.00, dict(drip=0.0)), (0.55, dict(drip=1.0), "hold"), (0.62, dict(drip=0.0)), (1.10, dict(drip=1.0), "hold"),
        (1.17, dict(drip=0.0)),
        (0.00, dict(gland_glow=1.0), "hold"), (0.40, dict(gland_glow=0.55), "hold"), (0.50, dict(gland_glow=1.0), "hold"),
    ], lean=0.05, by=4.0, splash=0.2),
    Clip("DEATH", [        # the glands go out one after another, it slumps into a puddle that stays
        (0.00, dict(melt=0.0, dim=0.0, by=0.0, hood=0.0, splash=0.0, eye_glow=1.0, gland_glow=1.0)),
        (0.10, dict(by=-5.0, hood=-0.8, gland_glow=1.6, eye_glow=1.7, splash=0.3), "out"),
        (0.40, dict(melt=0.45, by=0.0, hood=1.5, splash=0.6, slosh=-4.0), "in"),
        (0.70, dict(melt=1.0, splash=0.2, slosh=3.0), "in"),
        (0.80, dict(melt=0.95, slosh=-1.0), "inout"),
        (0.90, dict(melt=1.0, slosh=0.0), "hold"),
        (0.20, dict(dim=0.0)),
        (0.80, dict(dim=1.0)),
    ], mode="hold"),
    Clip("DEBUFF_PLAYER", [   # ability - the acid volley: the belly draws up and contracts hard, the spout spits
        (0.00, dict(draw=0.0, aim=0.0, squeeze=0.0, spit=0.0, gland_glow=1.0, by=0.0)),
        (0.15, dict(_DRAWN, draw=1.2, gland_glow=1.45), "inout"),
        (0.25, dict(squeeze=1.2, draw=0.3, spit=1.0, gland_glow=1.7, by=3.0), "out"),
        (0.38, dict(squeeze=0.4, spit=0.4, by=-2.0, slosh=-4.0)),
        (0.55, dict(draw=0.0, squeeze=0.0, aim=0.2, spit=0.0, gland=0.0, gland_glow=1.0, by=0.0, hood=0.0, eye_glow=1.0,
                    slosh=0.0)),
    ], mode="once", events={0.25: "emit"}),
]
