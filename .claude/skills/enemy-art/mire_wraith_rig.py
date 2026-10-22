"""MIRE WRAITH rig + clips (ABERRATION, SOLDIER, ranged acid artillery). Imported by bestiary.py.

The SECOND design (owner, 2026-10-22: the first "looks like an egg ... weak" - kept as sump_wretch_rig.py). Something that
grew in the facility sump and learned to hunt from it: a gaunt, drowned torso rising out of a column of its own sludge,
hunched forward over you, two long arms - one hooked into the mire, one raised and reaching. Its head is a LAMPREY MAW
turned at you, rings of teeth around a throat that glows with acid; the swollen acid gullet under it is the gun. Glands
blister along its shoulders, tatters of skin hang from its arms, and the sump's drainage hose is fused into its back
(the clamp stencilled S4).
"""
import math

from kit import blob, limb, chain, slab, spike, teeth_row, riveted_plate, mirror, with_glow, Xf, Figure, Rig, Clip, jiggle, lerp, lerp_pt

W, H, AX = 160, 240, 80
BASE = (80, 228)                  # the column leans and scales about its puddle
CHEST = (80, 118)
MAW = (80, 94)                    # the maw's centre (it faces the viewer)
L_SH, L_EL, L_HAND = (46, 94), (22, 128), (30, 192)       # the left arm hooks down into the mire
R_SH, R_EL, R_HAND = (114, 98), (138, 82), (128, 46)      # the right arm is raised, reaching

WRAITH_POSE = dict(
    cx=0.0, cy=0.0,                              # whole creature offset
    bx=0.0, by=0.0, bs=1.0, lean=0.0,            # torso: offset, scale toward the viewer, roll about the base
    head_x=0.0, head_y=0.0, head_s=1.0,          # the head: offset, and how far it juts at you (scale)
    maw=0.0,                                     # the maw: 0 rest, 1 flared wide, - clenched
    gullet=0.0, gullet_glow=1.0,                 # the acid gullet: + swollen, - squeezed; its light
    gland=0.0,                                   # shoulder glands pulse
    l_sh=0.0, l_el=0.0, r_sh=0.0, r_el=0.0,      # arms: shoulder swing, elbow fold (rad)
    claw=0.0,                                    # the hooks curl (+) / splay (-)
    sway=0.0, sway_jig=0.0,                      # the tatters and the column sway; the tatters' lag
    splash=0.0,                                  # HURT: the sludge column splashes outward (0..1)
    melt=0.0,                                    # DEATH: it slumps back into the mire (0..1)
    drip=0.0,                                    # drip phase (0..1)
    eye_glow=1.0, dim=0.0,
    tag=1.0,
)


def _arm(shoulder, elbow, hand, sh, el, claw, sway, side):
    """One long arm: a gaunt upper arm, a forearm, three bone hooks, and tatters of skin hanging from the forearm.
    sh turns the whole arm about the shoulder, el the forearm about the elbow (image space: + = clockwise).
    -> (shapes, posed hand)."""
    arm_xf = Xf.turn(sh, shoulder)
    fore_xf = Xf.turn(el, elbow).then(arm_xf)
    mid = lerp_pt(shoulder, elbow, 0.4)
    upper = [limb(*shoulder, *elbow, 9.0, 6.0, "flesh_dk", k=5), blob(shoulder[0], shoulder[1], 11, 10, "flesh_dk", k=6),
             blob(mid[0], mid[1], 8.5, 7.0, "flesh_dk", k=5),                                       # the wasted bicep
             blob(elbow[0], elbow[1], 6.5, 6.5, "flesh", k=4),
             *spike(elbow[0], elbow[1], elbow[0] + side * 9, elbow[1] - 6, 3.0, "bone", tip=0.4, segments=3, k=2)]   # elbow spur
    dx, dy = hand[0] - elbow[0], hand[1] - elbow[1]
    length = math.hypot(dx, dy) or 1.0
    ux, uy = dx / length, dy / length
    nx, ny = -uy, ux
    fore = [limb(*elbow, *hand, 6.5, 4.2, "flesh_dk", k=4),
            blob(elbow[0] + dx * 0.3, elbow[1] + dy * 0.3, 7, 5.5, "flesh_dk", k=4),
            *chain([(elbow[0] + nx * 2, elbow[1] + ny * 2), (hand[0] + nx * 1.5, hand[1] + ny * 1.5)], 1.0, 0.8, "flesh", op="add", depth=0.9)]
    hooks = []
    for i, spread in enumerate((-0.55, 0.0, 0.55)):
        a = math.atan2(uy, ux) + spread * (1 - 0.6 * claw)
        tip = (hand[0] + math.cos(a) * 17, hand[1] + math.sin(a) * 17)
        hooks += spike(*hand, *tip, 2.8, "bone", bend=side * (5 + 6 * claw) * (1 if i != 1 else 0.6), tip=0.3, segments=4, k=1)
    # tatters: strips of skin hanging from the forearm, lagging the arm (sway)
    tatters = []
    for i, t in enumerate((0.25, 0.5, 0.75)):
        top = (elbow[0] + dx * t, elbow[1] + dy * t + 2)
        length_t = 14 + 6 * i
        swing = (sway + 0.3 * math.sin(i * 1.7)) * 10
        tatters += chain([top, (top[0] + swing * 0.4, top[1] + length_t * 0.5), (top[0] + swing, top[1] + length_t)],
                         2.6, 1.0, "flesh_dk", k=2)
    return arm_xf(upper) + fore_xf(fore + hooks + tatters), fore_xf.pt(hand)


def mire_wraith_build(p):
    root = Xf.move(p.cx, p.cy)
    m = p.melt
    body_xf = (Xf.scale(p.bs * (1 + 0.12 * m) * (1 + 0.06 * p.splash), p.bs * (1 - 0.70 * m) * (1 - 0.05 * p.splash), BASE)
               .then(Xf.turn(p.lean, BASE)).then(Xf.move(p.bx, p.by)).then(root))
    head_xf = Xf.scale(p.head_s, p.head_s, MAW).then(Xf.move(p.head_x, p.head_y)).then(body_xf)
    sway = p.sway + 1.5 * p.sway_jig

    # ---- the mire it rises from, and the column of sludge that is its lower body (lumpy, sagging, dripping)
    puddle = [blob(80, 226, 56 + 16 * m + 6 * p.splash, 7 + 2 * m, "flesh_dk", depth=5, k=8),
              blob(42 - 6 * m, 228, 15, 5, "flesh_dk", depth=3, k=6), blob(120 + 6 * m, 227, 16, 5, "flesh_dk", depth=3, k=6)]
    sw = 4 * sway
    column = [
        # one tapering mass of sludge (lesson 12.1: merged with the torso, never stacked)
        *chain([(80, 214), (77 + sw * 0.4, 192), (81 + sw * 0.7, 168), (80, 146)], 17 + 5 * p.splash, 23, "flesh_dk", k=16),
        blob(80, 218, 34 + 6 * p.splash, 10, "flesh_dk", depth=9, k=14),
        blob(63 + sw * 0.5, 204, 7, 9, "flesh_dk", depth=6, k=10),            # sagging lumps of sludge
        blob(99 + sw * 0.5, 186, 6, 8, "flesh_dk", depth=6, k=10),
        # wet runnels of sludge down the column
        *[shape for i, x in enumerate((68, 79, 91)) for shape in
          chain([(x, 160), (x + 2 + sw * 0.5, 186), (x - 1 + sw * 0.3, 212 + (i % 2) * 5)], 1.5, 1.0, "flesh", op="add",
                depth=1.0)],
    ]
    # ---- the gaunt torso, hunched over you: shoulders high, ribs through thin wet skin, a caved chest
    torso = [
        blob(80, 138, 29, 22, "flesh_dk", depth=20, k=14),                    # ribcage
        blob(48, 92, 19, 15, "flesh_dk", depth=15, k=10),                     # left shoulder, hunched up
        blob(112, 96, 17, 14, "flesh_dk", depth=14, k=10),                    # right shoulder
        blob(80, 104, 30, 18, "flesh_dk", depth=16, k=10),                    # the hunched back behind the head
        blob(64, 132, 7, 10, "flesh_dk", op="carve", depth=3), blob(96, 132, 7, 10, "flesh_dk", op="carve", depth=3),
    ]
    for i in range(4):
        y = 128 + i * 7
        torso += chain([(78, y - 3), (64, y), (56, y + 7)], 1.7, 1.1, "bone", op="add", depth=1.5)
        torso += chain([(82, y - 3), (96, y), (104, y + 7)], 1.7, 1.1, "bone", op="add", depth=1.5)
    torso += [limb(80, 120, 80, 160, 1.6, 1.2, "bone", op="add", depth=1.4)]          # the sternum ridge
    # the SHROUD: a flat, ragged drape of skin hanging from each shoulder round the column (it sways after the body)
    for side in (-1, 1):
        sx = lambda x: 80 + side * x
        hem = [(sx(46) + sw * 1.2, 190), (sx(40) + sw * 1.1, 180), (sx(34) + sw * 1.0, 198), (sx(28) + sw * 0.9, 184),
               (sx(22) + sw * 0.8, 194)]
        drape = [(sx(22), 104), (sx(40), 98), (sx(50), 112), (sx(52) + sw * 0.6, 150)] + hem + [(sx(18) + sw * 0.5, 150)]
        torso += [slab(drape, "flesh_dk", bevel=3.0, slope=0.8, prio=-6, k=6)]
        torso += [limb(sx(30), 110, sx(36) + sw, 184, 0.9, 0.7, "flesh_dk", op="carve"),
                  limb(sx(42), 112, sx(44) + sw, 176, 0.9, 0.7, "flesh_dk", op="carve")]
    # acid glands blistering along the shoulders (asymmetric: more on the left)
    glow = p.gullet_glow * (1 - p.dim)
    glands = with_glow([blob(x, y, r * (1 + 0.1 * p.gland), r * 0.9 * (1 + 0.1 * p.gland), "membrane", op="add", depth=r * 0.8)
                        for x, y, r in ((40, 86, 5.5), (52, 80, 4.5), (34, 96, 3.5), (60, 88, 3.0), (118, 90, 4.0), (124, 98, 3.0))],
                       min(1.1, 0.8 + 0.25 * p.gland) * glow)
    # glowing acid veins down the chest from the gullet
    glands += with_glow(chain([(74, 132), (70, 146), (72, 158)], 1.0, 0.7, "ichor", op="add", depth=0.6)
                        + chain([(86, 132), (91, 148), (88, 160)], 1.0, 0.7, "ichor", op="add", depth=0.6),
                        min(1.0, 0.6 + 0.3 * p.gullet) * glow)
    # ---- the acid gullet: a veined throat pouch sagging under the maw - the gun
    g = p.gullet
    gullet = [blob(80, 121 + 2 * g, 13 * (1 + 0.2 * g), 9 * (1 + 0.3 * g), "membrane", depth=7, k=7,
                   glow=min(1.1, (0.75 + 0.3 * g) * glow)),
              blob(80, 116, 15, 6, "flesh_dk", depth=6, k=6),                 # the pouch's throat skin
              *[shape for d in (-7, 0, 7) for shape in
                chain([(80 + d, 114), (80 + d * 1.4, 122), (80 + d * 1.1, 129 + 2 * g)], 0.7, 0.5, "flesh_dk", op="add",
                      depth=0.5)]]
    # ---- the head: no face but the LAMPREY MAW turned at you, a hood of skull over it with a cluster of eyes
    eye = p.eye_glow * (1 - p.dim)
    mo = p.maw
    ring_r = 15.0 + 4.5 * mo
    head = [
        blob(80, 74, 18, 13, "flesh_dk", depth=12, k=8),                      # the skull hood
        blob(80, 94, ring_r + 4, ring_r + 3, "flesh", depth=12, k=8),          # the maw's thick lip
        blob(80, 94, ring_r * 0.82, ring_r * 0.78, "flesh_dk", op="carve", depth=8),
        blob(80, 94, ring_r * 0.66, ring_r * 0.62, "pupil", op="add", depth=0.6),            # the throat
        *with_glow([blob(80, 95, ring_r * (0.20 + 0.25 * max(0.0, mo)), ring_r * (0.18 + 0.22 * max(0.0, mo)),
                         "ichor", op="add", depth=0.8)], min(1.1, 0.8 + 0.4 * max(0.0, mo)) * glow),   # acid deep in it
        *with_glow([blob(x, y, r, r * 0.8, "ichor", op="add", depth=r) for x, y, r in
                    ((70, 70, 2.2), (90, 69, 2.4), (75, 64, 1.5), (86, 63, 1.4), (95, 74, 1.3))], eye),
        limb(80, 60, 80, 70, 1.2, 1.0, "flesh_dk", op="carve"),
        *[limb(80 + math.cos(a) * (ring_r + 2.5), 94 + math.sin(a) * (ring_r + 2), 80 + math.cos(a) * (ring_r + 6),
               94 + math.sin(a) * (ring_r + 5), 0.8, 0.6, "flesh_dk", op="carve") for a in [i * math.pi / 5 for i in range(10)]],
    ]
    # rings of teeth round the maw, raking in at the throat (raised details ON the lip: same layer)
    for ring, (radius, count, length, r) in enumerate(((ring_r * 0.92, 14, 5.0, 1.3), (ring_r * 0.70, 10, 3.6, 1.0))):
        for i in range(count):
            a = 2 * math.pi * (i + 0.5 * ring) / count
            bx, by = 80 + math.cos(a) * radius, 94 + math.sin(a) * radius * 0.95
            reach = radius - length * (1 + 0.2 * min(0.0, mo))
            tx, ty = 80 + math.cos(a + 0.18) * reach, 94 + math.sin(a + 0.18) * reach * 0.95
            head += spike(bx, by, tx, ty, r, "bone", tip=0.25, segments=2, k=1, op="add")
    if p.drip > 0.02:                                                         # acid drools from the maw
        run = 4 + 14 * p.drip
        head += with_glow([limb(84, 110, 84.5, 110 + run, 1.2, 0.8, "ichor", op="add", depth=1.0)], 1.0 * glow)

    # ---- arms
    left_arm, _ = _arm(L_SH, L_EL, L_HAND, p.l_sh, p.l_el, p.claw, sway, -1)
    right_arm, r_hand = _arm(R_SH, R_EL, R_HAND, p.r_sh, p.r_el, p.claw, sway, 1)

    # ---- the drainage hose, fused into its back on the left, arching over the shoulder, torn and dripping
    hose_pts = [(56, 92), (46, 64), (32, 54), (20, 66), (18, 84)]
    hose = chain(hose_pts, 4.6, 4.0, "darkmetal", k=3)
    hose += [blob(lerp(hose_pts[i][0], hose_pts[i + 1][0], f), lerp(hose_pts[i][1], hose_pts[i + 1][1], f), 5.1, 5.1,
                  "darkmetal", op="add", depth=0.8) for i in range(4) for f in (0.35, 0.8)]
    hose += riveted_plate([(36, 50), (45, 52), (43, 62), (34, 60)], "gunmetal", spacing=14, inset=1.3, rivet_r=0.8, bevel=1.4,
                          op="add")
    drop_y = 88 + 12 * p.drip
    hose += with_glow([blob(18, 86, 2.8, 2.2, "ichor", depth=2, prio=10),
                       limb(18, 88, 18.3, max(89.0, drop_y - 2), 1.2, 0.7, "ichor", prio=10),
                       blob(18.4, drop_y + 1, 1.4, 1.7, "ichor", prio=10)], 1.0 * (1 - p.dim))

    layers = [
        body_xf(hose),
        root(puddle),
        body_xf(torso + column + glands),
        body_xf(left_arm),
        body_xf(gullet),
        head_xf(head),
        body_xf(right_arm),
    ]
    maw_pt = head_xf.pt(MAW)
    anchors = {
        "ground": (80.0 + p.cx, 232.0 + p.cy),
        "hit_centre": body_xf.pt(CHEST),
        "eye": head_xf.pt((80, 68)),
        "muzzle": maw_pt,                               # the acid leaves the maw
    }
    stencils = []
    if p.tag > 0.5 and m < 0.3:
        sx, sy = body_xf.pt((37, 53))
        stencils.append(("S4", sx, sy, (230, 228, 220)))
    return Figure(layers, anchors, stencils)


MIRE_WRAITH = Rig("mire_wraith", mire_wraith_build, WRAITH_POSE, seed=47)


# IDLE: the gullet throbs, the maw flexes, the raised hooks curl and open, the column sways and the shroud follows a
# beat behind (jiggle), the shoulder glands pulse, acid drools
_IDLE = Clip("IDLE", [
    (0.00, dict(gullet=0.0, maw=0.05, claw=0.0, sway=-0.25, gland=0.0, r_sh=0.0, head_y=0.0)),
    (0.40, dict(maw=0.3, claw=0.6), "inout"),
    (0.75, dict(gullet=0.6, sway=0.25, gland=1.0, r_sh=-0.05, head_y=1.5)),
    (1.05, dict(maw=0.05, claw=-0.2)),
    (1.20, dict(drip=1.0), "hold"), (1.30, dict(drip=0.0)), (0.30, dict(drip=0.0)),
], duration=1.5, mode="loop")
IDLE = Clip("IDLE", _IDLE.keys + jiggle(_IDLE, "sway", 1.5, out_param="sway_jig", freq=1.8, damping=0.25, gain=0.8, fps=12),
            duration=1.5, mode="loop")
_DRAWN = dict(gullet=1.0, maw=0.8, head_s=0.93, head_y=-3.0, by=-3.0, gullet_glow=1.15, eye_glow=1.5, claw=0.8, r_sh=-0.1)
_REST = dict(gullet=0.0, maw=0.05, head_s=1.0, head_x=0.0, head_y=0.0, by=0.0, bx=0.0, lean=0.0, gullet_glow=1.0,
             eye_glow=1.0, claw=0.0, r_sh=0.0, r_el=0.0, l_sh=0.0, l_el=0.0, splash=0.0)

WRAITH_CLIPS = [
    IDLE,
    IDLE.variant("DORMANT", speed=0.5, fps=6, head_y=7.0, head_s=0.95, by=5.0, maw=-0.3, r_el=-2.0, r_sh=0.0,
                 gullet_glow=0.45, eye_glow=0.3, claw=0.9),
    Clip("ALERT", [        # it rears out of the mire: the raised hooks lift, the maw flares, the gullet lights
        (0.00, dict(_REST, r_el=-2.0, maw=-0.2)),
        (0.15, dict(by=-7.0, bs=1.04, maw=1.1, r_sh=-0.08, r_el=-0.25, claw=-0.8, gullet=0.7, gullet_glow=1.2, eye_glow=1.8,
                    head_s=1.06), "out"),
        (0.30, dict(by=-5.0, maw=0.8, sway=0.4)),
        (0.45, dict(_REST, maw=0.2, eye_glow=1.2, sway=0.0)),
    ], mode="once"),
    Clip("MOVE", [         # it surges through the sludge: a lunge and a lag, the shroud and the column sloshing after
        (0.000, dict(lean=-0.04, bx=-2.0, by=0.0, sway=-0.5, splash=0.25, l_sh=0.08)),
        (0.167, dict(lean=0.04, bx=2.0, by=-3.0, sway=0.5, splash=0.0, l_sh=-0.06)),
    ], duration=0.333, mode="loop"),
    Clip("WIND_UP", [      # the telegraph: the gullet swells and lights, the maw opens, the head draws back - held
        (0.00, dict(_REST)),
        (0.50, _DRAWN, "inout"),
    ], mode="hold"),
    Clip("ATTACK", [       # the spit: the head JUTS at you, the maw flares on the emit frame, the gullet empties
        (0.00, dict(_DRAWN)),
        (0.06, dict(head_s=1.16, head_y=4.0, maw=1.25, gullet=-0.7, gullet_glow=1.1, by=2.0, eye_glow=1.9), "out"),
        (0.16, dict(head_s=1.06, maw=0.6, gullet=-0.4, by=-1.0)),
        (0.30, dict(_REST, maw=0.15)),
    ], fps=20, mode="once", events={0.06: "emit"}),
    Clip("HURT_LIGHT", [   # the sludge splashes, the maw clenches
        (0.00, dict(splash=0.5, maw=-0.5, head_y=-3.0, sway=0.6, eye_glow=0.6), "out"),
        (0.17, dict(splash=0.0, maw=0.05, head_y=0.0, sway=0.0, eye_glow=1.0)),
    ], mode="once"),
    Clip("HURT_HEAVY", [   # knocked back: the whole column splashes outward, the arms flail, it pulls itself together
        (0.00, dict(_REST, cy=0.0)),
        (0.08, dict(cy=-3.0, splash=1.0, lean=-0.07, maw=-0.6, head_y=-5.0, head_s=0.92, r_sh=-0.05, r_el=-0.7, l_sh=-0.25,
                    sway=1.0, eye_glow=0.4, gullet=-0.4), "out"),
        (0.25, dict(cy=-1.0, splash=0.4, lean=-0.02, sway=-0.6, eye_glow=1.2)),
        (0.42, dict(_REST, cy=0.0, sway=0.0)),
    ], mode="once"),
    Clip("DEFEND", [       # it hunches: the head sinks into the shoulders, the raised arm comes down across the maw
        (0.00, dict(_REST, tag=1.0)),
        (0.33, dict(head_y=8.0, head_s=0.9, by=6.0, maw=-0.6, r_sh=0.0, r_el=-1.45, claw=0.9, gullet=-0.3,
                    gullet_glow=0.7, eye_glow=0.7, l_sh=-0.15)),
    ], mode="hold"),
    IDLE.variant("WOUNDED", keys=[
        (0.00, dict(drip=0.0)), (0.60, dict(drip=1.0), "hold"), (0.70, dict(drip=0.0)), (1.20, dict(drip=1.0), "hold"),
        (1.30, dict(drip=0.0)),
    ], lean=0.05, by=4.0, r_sh=0.05, r_el=-2.0, splash=0.2, head_y=3.0),
    Clip("DEATH", [        # the arms go slack, the glow goes out, it slumps back into the mire it came from
        (0.00, dict(_REST, melt=0.0, dim=0.0)),
        (0.10, dict(by=-5.0, maw=1.3, head_s=1.08, eye_glow=1.8, r_sh=-0.15), "out"),
        (0.35, dict(melt=0.35, maw=0.6, r_sh=0.05, r_el=-1.6, l_sh=-0.3, l_el=-0.3, splash=0.6, sway=0.5), "in"),
        (0.65, dict(melt=0.95, maw=0.3, r_sh=0.1, r_el=-2.4, l_sh=-0.45, splash=0.3, sway=-0.3), "in"),
        (0.75, dict(melt=0.9), "inout"),
        (0.85, dict(melt=1.0, splash=0.0, sway=0.0), "hold"),
        (0.20, dict(dim=0.0)),
        (0.75, dict(dim=1.0)),
    ], mode="hold"),
    Clip("DEBUFF_PLAYER", [   # ability - the acid volley: the gullet swells huge and the maw spits, twice
        (0.00, dict(_REST)),
        (0.15, dict(_DRAWN, gullet=1.4, head_s=0.9), "inout"),
        (0.22, dict(head_s=1.14, head_y=4.0, maw=1.25, gullet=0.4, by=2.0), "out"),
        (0.32, dict(head_s=0.96, maw=0.7, gullet=1.0, by=-2.0), "inout"),
        (0.40, dict(head_s=1.12, maw=1.2, gullet=-0.6, by=2.0), "out"),
        (0.60, dict(_REST, maw=0.15)),
    ], mode="once", events={0.22: "emit"}),
]
