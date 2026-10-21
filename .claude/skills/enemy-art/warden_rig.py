"""WARDEN rig + clips (MACHINE, ACID_DRONE). Imported by bestiary.py."""
from kit import blob, limb, slab, chain, mirror, with_glow, Xf, Figure, Rig, Clip, lerp

W, H, AX = 160, 240, 80
HULL_PIVOT = (80, 100)

WARDEN_POSE = dict(
    hx=0.0, hy=0.0, sink=0.0, tilt=0.0, zs=1.0,          # hull: offset, extra drop, roll (rad), depth scale
    eye_x=0.44, eye_open=1.0, eye_glow=1.0, slit_glow=1.0,  # pupil position along the slit (-1..1), lid, light
    plume=1.0, plume_glow=1.0,                           # thrust plume length factor and light
    ant=0.0,                                             # antenna sway (rad), lags the hull
    l_sh=0.0, l_el=0.0, claw=0.0,                        # claw arm: shoulder, elbow (rad), claw opening
    r_sh=0.0, r_el=0.0, prod=0.0, flash=0.0,             # prod arm: shoulder, elbow, crackle, muzzle flash
    panel=0.0, panel_fall=0.0, sparks=0.0,               # belly panel ajar (0..1), fallen to the floor, sparks
    crack=0.0, cable=0.0, slack=0.0,                     # cracked visor (wounded), belly cable swing, cables crumpled
)


def _sparks(cx, cy, size, seedling):
    """A small cyan crackle: three jagged strokes around a point (cosmetic, part of the clip)."""
    out = []
    dirs = ((-1.0, -0.6), (0.9, -0.8), (0.7, 0.9), (-0.8, 0.7))
    for i, (dx, dy) in enumerate(dirs[seedling % 2:seedling % 2 + 3]):
        mx, my = cx + dx * size * 0.55 + (0.9 if i % 2 else -0.9), cy + dy * size * 0.5
        out.append(limb(cx, cy, mx, my, 0.9, 0.6, "cyan", prio=12))
        out.append(limb(mx, my, cx + dx * size, cy + dy * size, 0.6, 0.4, "cyan", prio=12))
    return out


def warden_build(p):
    """The Organization's riot drone: a hovering hull with one sweeping slit eye, a grabbing claw, a
    stun prod, and its own torn cabling hanging out of the belly."""
    hull_xf = (Xf.turn(p.tilt, HULL_PIVOT).then(Xf.scale(p.zs, p.zs, HULL_PIVOT))
               .then(Xf.move(p.hx, p.hy + p.sink)))

    hull = [
        blob(80, 96, 31, 46, "gunmetal", depth=28, k=6),
        blob(80, 56, 22, 11, "darkmetal", depth=10, k=6, prio=6),
        limb(58, 66, 102, 66, 3.2, 3.2, "livery", op="add", depth=1.5),
        blob(80, 140, 17, 9, "darkmetal", depth=8, k=5, prio=4),
        limb(50, 72, 110, 72, 1.3, 1.3, "gunmetal", op="carve"),
        limb(52, 120, 108, 120, 1.3, 1.3, "gunmetal", op="carve"),
        limb(64, 122, 64, 136, 1.1, 1.1, "gunmetal", op="carve"),
        limb(96, 122, 96, 136, 1.1, 1.1, "gunmetal", op="carve"),
        *[blob(x, y, 1.5, 1.5, "livery", op="add", depth=1.4) for x, y in
          ((56, 76), (104, 76), (55, 116), (105, 116), (70, 130), (90, 130))],
    ]
    if p.panel > 0.05 or p.panel_fall > 0.0:          # the belly panel is out: show the dark bay behind it
        hull.append(slab([(65, 122), (95, 122), (95, 136), (65, 136)], "darkmetal", bevel=1.5, op="add"))
        hull += [limb(70, 124, 74, 134, 0.8, 0.8, "darkmetal", op="carve"), limb(86, 124, 90, 134, 0.8, 0.8, "darkmetal", op="carve")]

    fins = [
        slab([(52, 74), (34, 92), (30, 126), (40, 132), (54, 112)], "gunmetal", bevel=4, tilt=(-0.15, 0)),
        limb(36, 96, 34, 124, 1, 1, "gunmetal", op="carve"),
        blob(38, 104, 1.4, 1.4, "livery", op="add", depth=1.2),
    ]
    fins = fins + mirror(fins, AX)

    open_ = max(p.eye_open, 0.15)
    visor = [
        blob(80, 94, 27, 11, "darkmetal", depth=6, k=2),
        limb(62, 94, 98, 94, 3.4 * min(open_, 1.25), 3.4 * min(open_, 1.25), "cyan", k=0, prio=10, glow=p.slit_glow),
        blob(80 + p.eye_x * 18, 94, 5.5, 5.5 * open_, "cyan", depth=5, prio=20, glow=p.eye_glow),
    ]
    if p.crack > 0.5:                                  # wounded: a cracked visor
        visor += [
            limb(66, 86, 73, 93, 0.7, 0.6, "darkmetal", op="add", depth=1.2),
            limb(73, 93, 70, 99, 0.6, 0.5, "darkmetal", op="add", depth=1.2),
            limb(73, 93, 79, 97, 0.6, 0.5, "darkmetal", op="add", depth=1.2),
            limb(79, 97, 84, 103, 0.5, 0.4, "darkmetal", op="add", depth=1.2),
        ]

    # claw arm: shoulder pivot, then the forearm about the elbow, the two fingers about the wrist
    l_sh, l_el, l_wr = (46, 128), (32, 152), (40, 176)
    l_fore = Xf.turn(p.l_el, l_el)
    f_open = 0.15 + 0.45 * p.claw
    finger_a = Xf.turn(f_open - 0.15, l_wr)(chain([(40, 176), (34, 190), (38, 198)], 3.0, 1.2, "darkmetal", k=2))
    finger_b = Xf.turn(-(f_open - 0.15), l_wr)(chain([(42, 177), (48, 190), (44, 199)], 3.0, 1.2, "darkmetal", k=2))
    l_arm_xf = Xf.turn(p.l_sh, l_sh)
    arm_left = l_arm_xf([
        blob(46, 128, 7, 7, "darkmetal"),
        limb(46, 128, 32, 152, 4.8, 4.2, "gunmetal", k=4),
        blob(32, 152, 4.6, 4.6, "darkmetal", op="add", depth=1.5),
        *l_fore([limb(32, 152, 40, 176, 4.2, 3.6, "gunmetal", k=4), *finger_a, *finger_b]),
    ])

    # prod arm: the stun prod rides the forearm; its tip is the MUZZLE anchor
    r_sh, r_el, tip = (114, 128), (128, 150), (135, 210)
    r_fore = Xf.turn(p.r_el, r_el)
    r_arm_xf = Xf.turn(p.r_sh, r_sh)
    crackle = 1.0 + 0.9 * p.prod
    power = p.slit_glow                                # the prod runs on the hull's power: it dies with the eye
    prod_parts = [
        limb(128, 150, 122, 172, 4.2, 3.6, "gunmetal", k=4),
        limb(118, 170, 134, 208, 3.2, 2.6, "darkmetal", k=1),
        limb(119, 176, 121, 180, 4, 4, "livery", op="add", depth=1),
        *with_glow([
            blob(135, 210, 3.2 + 1.2 * p.prod, 3.2 + 1.2 * p.prod, "cyan", depth=3, prio=10),
            limb(135, 210, 135 + 6 * crackle, 210 - 8 * crackle, 0.9, 0.5, "cyan", prio=10),
            limb(135, 210, 135 - 7 * crackle, 210 + 4 * crackle, 0.9, 0.5, "cyan", prio=10),
            limb(135, 210, 135 + 5 * crackle, 210 + 8 * crackle, 0.9, 0.5, "cyan", prio=10),
        ], (1.0 + 0.4 * p.prod) * power),
    ]
    if p.prod > 0.3:                                   # charged: an arc climbs the shaft
        prod_parts += [limb(131, 200, 125, 193, 0.7, 0.5, "cyan", prio=11), limb(125, 193, 128, 186, 0.6, 0.4, "cyan", prio=11)]
    arm_right = r_arm_xf([
        blob(114, 128, 7, 7, "darkmetal"),
        limb(114, 128, 128, 150, 4.8, 4.2, "gunmetal", k=4),
        blob(128, 150, 4.6, 4.6, "darkmetal", op="add", depth=1.5),
        *r_fore(prod_parts),
    ])
    muzzle_xf = Xf.turn(p.r_el, r_el).then(r_arm_xf).then(hull_xf)

    plume_len = 40 * p.plume
    crumple_a = Xf.scale(1.0 - 0.65 * p.slack, 1.0 - 0.65 * p.slack, (70, 146))
    crumple_b = Xf.scale(1.0 - 0.65 * p.slack, 1.0 - 0.65 * p.slack, (91, 146))
    underside = [
        *crumple_a.then(Xf.turn(p.cable, (70, 146)))(chain([(70, 146), (66, 162), (71, 176), (68, 188)], 1.8, 1.4, "darkmetal", k=1)),
        *crumple_b.then(Xf.turn(p.cable * 0.7, (91, 146)))([*chain([(91, 146), (95, 158), (93, 166)], 1.8, 1.6, "darkmetal", k=1),
                                                            blob(93, 167, 1.6, 1.6, "cyan", prio=5, glow=p.slit_glow)]),
    ]
    if p.plume > 0.05:
        underside.insert(0, limb(80, 146, 80, 146 + plume_len, 10 * min(1.0, 0.6 + 0.4 * p.plume), 2, "cyan", k=0, glow=p.plume_glow))

    antenna = Xf.turn(p.ant, (96, 48))([limb(96, 48, 108, 18, 1.6, 1.1, "darkmetal", k=1), blob(108, 17, 2.4, 2.4, "cyan", glow=p.slit_glow)]) + \
        Xf.turn(p.ant * 0.8, (66, 46))([limb(66, 46, 62, 34, 1.4, 1.2, "darkmetal", k=1)])

    front = []
    if p.flash > 0.05:                                 # the emit frame: a hot bloom at the prod tip
        r = 3 + 5 * p.flash
        front += [blob(135, 210, r, r, "cyan", depth=r, prio=30, glow=1.0 + 0.6 * p.flash)]
        front = muzzle_xf(front)
    if p.sparks > 0.05:
        front += hull_xf(_sparks(98, 130, 9 * p.sparks, 0) + _sparks(64, 118, 7 * p.sparks, 1))

    # the belly panel, popped ajar on a hit and dropping to the floor in death
    panel_pts = [(65, 121), (95, 121), (95, 137), (65, 137)]
    panel_layer = []
    stencils = []
    panel_xf = None
    if p.panel > 0.05 or p.panel_fall > 0.0:
        ajar = Xf.turn(0.35 * p.panel, (95, 137)).then(Xf.move(2 * p.panel, 3 * p.panel))
        if p.panel_fall > 0.0:      # torn off as the hull lands: it slides off and drops flat on the floor
            bottom = hull_xf.pt((80, 137))
            drop = (232 - 2) - bottom[1]
            panel_xf = ajar.then(hull_xf).then(Xf.turn(-0.35 * p.panel_fall, bottom)).then(
                Xf.scale(1.0, 1.0 - 0.45 * p.panel_fall, bottom)).then(Xf.move(26 * p.panel_fall, drop * p.panel_fall))
        else:
            panel_xf = ajar.then(hull_xf)
    if panel_xf is not None:
        panel_layer = panel_xf([slab(panel_pts, "gunmetal", bevel=2.5, slope=0.9),
                                blob(70, 130, 1.5, 1.5, "livery", op="add", depth=1.4),
                                blob(90, 130, 1.5, 1.5, "livery", op="add", depth=1.4)])
        sx, sy = panel_xf.pt((72, 126))
        stencils.append(("W-07", sx, sy, (232, 232, 226)))
    else:
        sx, sy = hull_xf.pt((72, 126))
        stencils.append(("W-07", sx, sy, (232, 232, 226)))

    layers = [
        hull_xf(antenna),
        hull_xf(fins),
        hull_xf(underside),
        hull_xf(hull),
        hull_xf(visor),
        hull_xf(arm_left + arm_right),
        panel_layer,
        front,
    ]
    anchors = {
        "ground": (80.0, 232.0),
        "hit_centre": hull_xf.pt((80, 100)),
        "eye": hull_xf.pt((80 + p.eye_x * 18, 94)),
        "muzzle": muzzle_xf.pt(tip),
    }
    return Figure(layers, anchors, stencils)


WARDEN = Rig("warden", warden_build, WARDEN_POSE, seed=5)

# --- clips ------------------------------------------------------------------------------------------
_idle_keys = [
    (0.00, dict(hy=0.0, tilt=0.0, ant=0.00, eye_x=0.44, plume=1.00, cable=0.00, claw=0.0)),
    (0.17, dict(plume=0.86)),
    (0.33, dict(hy=-1.0, ant=0.03, plume=1.04, cable=0.04)),
    (0.42, dict(eye_x=0.44), "inout"),
    (0.50, dict(plume=0.88)),
    (0.67, dict(hy=-2.0, tilt=0.015, ant=0.06, plume=1.06, cable=0.07, claw=0.25)),
    (0.83, dict(plume=0.90)),
    (0.92, dict(eye_x=-0.44)),
    (1.00, dict(hy=-1.0, ant=0.02, plume=1.02, cable=0.03)),
    (1.17, dict(plume=0.87)),
]
IDLE = Clip("IDLE", _idle_keys, duration=1.33, mode="loop")

WARDEN_CLIPS = [
    IDLE,
    IDLE.variant("DORMANT", speed=0.5, fps=6, sink=7.0, slit_glow=0.0, eye_glow=0.35, plume=0.16, plume_glow=0.35, eye_open=0.55),
    Clip("ALERT", [
        (0.00, dict(eye_x=0.44, eye_glow=1.0, eye_open=1.0, plume=1.0, l_sh=0.0, r_sh=0.0, hy=0.0)),
        (0.08, dict(eye_x=0.0, eye_glow=1.7, eye_open=1.3, plume=1.5, hy=-3.0), "out"),
        (0.25, dict(l_sh=0.22, r_sh=-0.22, claw=1.0), "inout"),
        (0.42, dict(eye_glow=1.15, eye_open=1.0, plume=1.1, l_sh=0.1, r_sh=-0.1, claw=0.3, hy=-1.0)),
    ], mode="once"),
    Clip("MOVE", [
        (0.00, dict(tilt=0.08, plume=1.35, hy=0.0, hx=0.0, zs=1.0, cable=-0.10)),
        (0.17, dict(tilt=0.11, plume=1.65, hy=-2.0, hx=-2.0, zs=0.985, cable=-0.16)),
    ], duration=0.33, mode="loop"),
    Clip("WIND_UP", [
        (0.00, dict(r_sh=0.0, r_el=0.0, prod=0.0, claw=0.0, eye_open=1.0, eye_x=0.44)),
        (0.17, dict(eye_x=0.0), "out"),
        (0.21, dict(r_sh=0.2, r_el=1.8)),
        (0.42, dict(r_sh=-0.3, r_el=3.58, prod=1.0, claw=1.0, eye_open=0.45, eye_glow=1.3, l_sh=0.25, tilt=-0.04)),
    ], mode="hold"),
    Clip("ATTACK", [
        (0.00, dict(r_sh=-0.3, r_el=3.58, prod=1.0, claw=1.0, eye_open=0.45, eye_glow=1.3, l_sh=0.25, tilt=-0.04, eye_x=0.0,
                    flash=0.0, zs=1.0, hy=0.0)),
        (0.05, dict(r_sh=-0.2, r_el=0.55), "out"),
        (0.10, dict(flash=1.0, prod=0.4, zs=0.95, hy=-5.0, eye_glow=1.7), "out"),
        (0.15, dict(flash=0.35)),
        (0.20, dict(flash=0.0)),
        (0.30, dict(r_sh=0.0, r_el=0.0, prod=0.0, claw=0.0, eye_open=1.0, eye_glow=1.0, l_sh=0.0, tilt=0.0, zs=1.0, hy=0.0)),
    ], fps=20, mode="once", events={0.06: "emit"}),     # the brightest flash frame: the glob leaves the muzzle
    Clip("HURT_LIGHT", [
        (0.00, dict(hx=4.0, tilt=0.07, eye_glow=0.25, hy=-1.0, zs=0.98), "out"),
        (0.17, dict(hx=0.0, tilt=0.0, eye_glow=1.0, hy=0.0, zs=1.0)),
    ], mode="once"),
    Clip("HURT_HEAVY", [
        (0.00, dict(hx=0.0, tilt=0.0, eye_glow=1.0, panel=0.0, sparks=0.0, zs=1.0)),
        (0.08, dict(hx=7.0, tilt=0.28, eye_glow=0.2, panel=1.0, sparks=1.0, zs=0.96, hy=-3.0, l_sh=0.4, r_sh=-0.3), "out"),
        (0.17, dict(sparks=0.6, eye_glow=1.2)),
        (0.25, dict(sparks=0.0, panel=0.6, tilt=0.12, hx=3.0)),
        (0.42, dict(hx=0.0, tilt=0.0, eye_glow=1.0, panel=0.0, zs=1.0, hy=0.0, l_sh=0.0, r_sh=0.0)),
    ], mode="once"),
    Clip("DEFEND", [
        (0.00, dict(l_sh=0.0, l_el=0.0, r_sh=0.0, r_el=0.0, eye_open=1.0, sink=0.0, eye_x=0.44)),
        (0.25, dict(l_sh=-2.35, l_el=-0.95, r_sh=2.30, r_el=1.05, eye_open=0.5, sink=3.0, eye_x=0.0, plume=1.2)),
    ], mode="hold"),
    IDLE.variant("WOUNDED", keys=[
        (0.00, dict(plume=1.0, plume_glow=1.0)), (0.17, dict(plume=0.15, plume_glow=0.5), "hold"),
        (0.33, dict(plume=0.95, plume_glow=1.0)), (0.58, dict(plume=0.2, plume_glow=0.5), "hold"),
        (0.67, dict(plume=0.9, plume_glow=0.9)), (1.08, dict(plume=0.1, plume_glow=0.4), "hold"),
        (1.17, dict(plume=0.95, plume_glow=1.0)),
        (0.00, dict(l_el=0.55)), (0.67, dict(l_el=0.7)),
    ], crack=1.0, l_sh=-0.45, tilt=0.06),
    Clip("DEATH", [
        (0.00, dict(eye_glow=1.0, slit_glow=1.0, plume=1.0, hy=0.0, tilt=0.0, ant=0.0, l_sh=0.0, r_sh=0.0, panel=0.0)),
        (0.08, dict(eye_glow=0.2, slit_glow=0.3, plume=0.3, sparks=1.0, panel=0.8), "hold"),
        (0.17, dict(eye_glow=0.9, sparks=0.4), "hold"),
        (0.25, dict(eye_glow=0.0, slit_glow=0.0, plume=0.0, sparks=0.0), "in"),
        (0.25, dict(hy=0.0, tilt=0.0), "in"),
        (0.62, dict(panel_fall=0.0), "in"),
        (0.50, dict(hy=78.0, zs=0.93, tilt=0.26, ant=0.5, cable=0.3, slack=0.6, l_sh=0.5, r_sh=0.3, l_el=-0.5, r_el=1.0), "out"),
        (0.62, dict(hy=68.0, tilt=0.32), "in"),
        (0.75, dict(hy=76.0, zs=0.87, tilt=0.36, ant=0.7, l_sh=0.42, r_sh=0.4, l_el=-0.9, r_el=1.1, claw=1.0, cable=0.3, slack=1.0), "hold"),
        (0.83, dict(panel_fall=1.0)),
        (0.92, dict(hy=76.0)),
    ], mode="hold"),
]
