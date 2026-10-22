"""GHOUL rig + clips (UNDEAD, CHAFF, 0.90 walls, slow melee shambler). Imported by bestiary.py.

A decontamination labourer who was sealed in his suit when the galleries flooded with raw soul, and never came out.
Owner brief (2026-10-22): TALL and THIN, legs, arms and fingers far too long for a person; a slate Organization hazard
suit, hood up; a GAS MASK with ONE panoramic glass visor (never two lenses) and RED eyes burning behind it. No face
is ever visible. Story detail: the lanyard ID badge still clipped to the chest (stencil "D-14", decon crew).
Motion character: patient, swaying, the head cocked; it walks like something remembering how.
"""
import math

from kit import lerp_pt, blob, limb, chain, slab, fold_lines, hand, Humanoid, with_glow, Xf, Figure, Rig, Clip, lag

SNOUT_END = 30.0                  # the canister's centre below the head centre (head space, px)

# PROPORTION AND THICKNESS (owner, 2026-10-22, from a reference image of a figure in a hazard suit, matched by measuring
# its silhouette: head ~14% of the height, shoulders at ~19%, crotch at ~54%, shoulders ~31% of the height wide, waist
# ~24%, each leg ~10%, each arm ~7%). A long, broad, boxy suited torso on straight column legs. No muscle detail - the
# suit is full over a heavy frame. The owner's own features stay: single visor, red eyes, long mask, long arms + fingers.
ARM_R = (7.8, 7.2, 6.4)           # upper arm at the shoulder, at the elbow, forearm at the wrist
LEG_R = (11.0, 10.0, 8.8)         # thigh at the hip, at the knee, shin at the ankle (columns: little taper)

# Owner, 2026-10-22: the body ~12% longer (spine 58 -> 65) and the arms ~12% longer (44/42 -> 50/47); the pelvis moved
# down 6 px so the head keeps its place on the canvas, the legs giving back ~5% (still far longer than a person's).
SKELETON = Humanoid(pelvis=(80.0, 128.0), spine=71.0, neck=9.0, head=11.0, shoulder_half=22.0, hip_half=11.0,
                    upper_arm=47.0, fore_arm=45.0, hand=12.0, thigh=50.0, shin=51.0, foot=9.0,
                    arm_rest=0.11, leg_rest=0.035)

GHOUL_POSE = Humanoid.params(
    torso=0.025, chest=0.05, neck=0.06, head=0.30,     # a slight list, the head cocked hard to one side
    l_sh=0.04, r_sh=0.13, l_el=-0.16, r_el=-0.30, l_wr=-0.10, r_wr=0.06, r_arm_fore=0.10,
    l_hip=0.07, r_hip=0.04, l_knee=-0.09, r_knee=-0.06, squat=0.03,
)
GHOUL_POSE.update(
    l_curl=0.12, r_curl=0.22,                     # finger curl (0 open .. 1 a grab)
    eye_glow=1.0, dim=0.0,                        # the red eyes; death dimming
    hood_lag=0.0,                                 # the hood / hose lag behind the head (secondary motion)
    badge=0.0,                                    # the ID badge swing on its lanyard (-1 .. 1)
    crack=0.0,                                    # WOUNDED: the visor cracks (0..1), red light leaking through
    fall=0.0,                                     # DEATH: the whole body collapses toward the floor (0..1)
    tag=1.0,                                      # 0 drops the badge stencil when the badge is covered
)


def _head(j, p):
    """Hood + gas mask, built about the head centre on an upright head, then turned with the head."""
    hx, hy = j["head"]
    a = j["head_angle"]
    xf = Xf.turn(a, (0, 0)).then(Xf.move(hx, hy))
    eye = p.eye_glow * (1 - p.dim)
    hood = [
        blob(0, -2, 14.5, 16.0, "overalls", depth=13, k=5),                     # the hood over the skull
        blob(0, 12, 12.0, 7.5, "overalls", depth=8, k=6),                       # its neck skirt
        *fold_lines(-12, 2, -8, 16, 3, 5, mat="overalls", seed=3, angle=0.6),
        *fold_lines(12, 2, 8, 16, 3, 5, mat="overalls", seed=4, angle=-0.6, side=-1),
    ]
    mask = [
        blob(0, 3, 10.0, 11.0, "rubber", depth=7, k=3),                         # the rubber face-piece: no face, ever
        # ONE panoramic visor - a single curved glass shield across both eyes (owner brief); a union part with a
        # hard edge, so it reads as glass set into the rubber, with its own glossy highlight
        slab([(-8.6, -6.5), (8.6, -6.5), (9.6, -1.5), (7.6, 4.0), (-7.6, 4.0), (-9.6, -1.5)], "visor", bevel=1.8,
             slope=1.3, prio=60, k=0, tilt=(0.0, 0.12)),
        limb(-9.0, -7.2, 9.0, -7.2, 1.05, 1.05, "darkmetal", prio=70, k=0),            # the visor's frame
        limb(-7.6, 4.8, 7.6, 4.8, 1.0, 1.0, "darkmetal", prio=70, k=0),
        limb(-9.0, -7.2, -10.2, -1.5, 1.0, 1.0, "darkmetal", prio=70, k=0),
        limb(9.0, -7.2, 10.2, -1.5, 1.0, 1.0, "darkmetal", prio=70, k=0),
        # the LONG respirator (owner, 2026-10-22): a ribbed rubber snout hanging far below the visor, past where a
        # chin would be, ending in a filter canister - it swings with every tilt of the head
        limb(0, 6, 0, SNOUT_END - 3.0, 6.4, 4.4, "rubber", k=2, prio=40),
        # raised corrugation rings: they catch the key light and the cyan rim, so the tube reads as a TUBE
        *[limb(-(6.2 - 0.35 * i), 9.5 + 3.0 * i, 6.2 - 0.35 * i, 9.5 + 3.0 * i, 0.9, 0.9, "rubber", op="add", depth=1.1)
          for i in range(6)],
        blob(0, SNOUT_END, 6.0, 4.6, "gunmetal", depth=5, prio=50, k=1),
        limb(-5.4, SNOUT_END + 3.4, 5.4, SNOUT_END + 3.4, 1.2, 1.2, "darkmetal", prio=55, k=0),
        *[limb(-3.6 + 1.8 * i, SNOUT_END - 2.2, -3.6 + 1.8 * i, SNOUT_END + 2.0, 0.45, 0.45, "gunmetal", op="carve")
          for i in range(5)],
        # the head straps over the hood
        limb(-10.0, -1, -13.0, -11, 1.1, 1.0, "rubber", op="add", depth=1.0),
        limb(10.0, -1, 13.0, -11, 1.1, 1.0, "rubber", op="add", depth=1.0),
        # the RED eyes behind the glass: two narrow burning slits, the only thing alive in the mask
        *with_glow([blob(-4.0, -1.4, 2.7, 1.3, "redeye", op="add", depth=0.9, angle=-0.14),
                    blob(4.0, -1.4, 2.7, 1.3, "redeye", op="add", depth=0.9, angle=0.14)], eye),
    ]
    if p.crack > 0.05:
        c = p.crack
        mask += [limb(2, -5.5, 4.5, -1.5, 0.45, 0.45, "visor", op="carve"),
                 limb(4.5, -1.5, 3.0, 3.8 * c, 0.45, 0.4, "visor", op="carve"),
                 *with_glow([limb(4.6, -1.4, 3.6, 2.5 * c, 0.5, 0.4, "redeye", op="add", depth=0.6)], 0.7 * eye)]
    return xf(hood), xf(mask)


def _arm(j, p, side, s):
    sh, el, wr = j[f"{side}_shoulder"], j[f"{side}_elbow"], j[f"{side}_wrist"]
    curl = p[f"{side}_curl"]
    shapes = [
        limb(*sh, *el, ARM_R[0], ARM_R[1], "overalls", k=4),
        limb(*el, *wr, ARM_R[1], ARM_R[2], "overalls", k=4),
        blob(*el, ARM_R[1], ARM_R[1], "overalls", depth=ARM_R[1], k=3),
        # the elbow bunches the suit; a reflective-less seam down the sleeve
        *fold_lines(el[0] - 3, el[1] - 4, el[0] + 3, el[1] + 4, 3, 5.5, mat="overalls", seed=11 + s, angle=0.2),
        limb(*sh, *el, 0.6, 0.5, "overalls", op="carve"),
        # the rubber gauntlet over the forearm's last third, and the glove: LONG fingers
        limb(wr[0] + (el[0] - wr[0]) * 0.32, wr[1] + (el[1] - wr[1]) * 0.32, *wr, ARM_R[2] + 0.4, ARM_R[2], "rubber", op="add", depth=1.2),
        # the taped cuff where the glove seals to the sleeve
        limb(*lerp_pt(wr, el, 0.34), *lerp_pt(wr, el, 0.28), ARM_R[2] + 1.0, ARM_R[2] + 1.0, "darkmetal", op="add", depth=1.4),
        *hand(wr, j[f"{side}_hand_angle"], s, 11.0, "rubber", fingers=4, finger_length=27.0, finger_r=1.6,
              palm_r=4.6, spread=0.40, curl=curl),
    ]
    return shapes


def _leg(j, side, s):
    hp, kn, an, to = j[f"{side}_hip"], j[f"{side}_knee"], j[f"{side}_ankle"], j[f"{side}_toe"]
    return [
        limb(*hp, *kn, LEG_R[0], LEG_R[1], "overalls", k=4),
        limb(*kn, *an, LEG_R[1], LEG_R[2], "overalls", k=4),
        blob(*kn, LEG_R[1] + 0.5, LEG_R[1] + 1.0, "overalls", depth=LEG_R[1], k=3),
        *fold_lines(kn[0] - 3.5, kn[1] - 5, kn[0] + 3.5, kn[1] + 5, 3, 6, mat="overalls", seed=21 + s, angle=-0.25),
        # the boot: rubber to mid-shin, the toe turned out
        limb(an[0] + (kn[0] - an[0]) * 0.38, an[1] + (kn[1] - an[1]) * 0.38, *an, LEG_R[2] + 0.6, LEG_R[2] + 0.4, "rubber", op="add", depth=1.4),
        limb(*lerp_pt(an, kn, 0.40), *lerp_pt(an, kn, 0.34), LEG_R[2] + 1.2, LEG_R[2] + 1.2, "darkmetal", op="add", depth=1.6),
        limb(*an, *to, 8.4, 6.8, "rubber", k=3),
        blob(an[0] + s * 1.5, an[1] + 1.8, 9.6, 5.4, "rubber", depth=3, k=3),
    ]


def ghoul_build(p):
    j = SKELETON.joints(p)
    px, py = j["pelvis"]
    cx, cy = j["chest"]
    wx, wy = j["waist"]
    torso = [
        # a long torso: the suit hangs off the shoulders and narrows at the belt
        # the suit's boxy trunk: a bevelled block shoulder-to-crotch, rounded by a core capsule inside it
        slab([(cx - 23, cy - 4), (cx + 23, cy - 4), (wx + 22, wy), (px + 20, py - 2), (px - 20, py - 2), (wx - 22, wy)],
             "overalls", bevel=8.0, slope=0.9, k=8),
        limb(cx, cy, px, py - 4, 17.0, 16.0, "overalls", k=8),
        blob(*j["l_shoulder"], 10.0, 8.5, "overalls", depth=8.5, k=7),
        blob(*j["r_shoulder"], 10.0, 8.5, "overalls", depth=8.5, k=7),
        limb(*j["neck"], cx, cy, 6.5, 8.0, "overalls", k=5),
        # the zip down the front, the belt, the creases pulling from shoulder to hip
        limb(cx, cy + 4, px, py - 6, 0.7, 0.7, "overalls", op="carve"),
        limb(px - 20, py - 9, px + 20, py - 9, 2.4, 2.4, "rubber", op="add", depth=1.6),
        blob(px - 1.0, py - 7, 2.4, 2.0, "darkmetal", op="add", depth=1.2),
        *fold_lines(cx - 10, cy + 6, wx - 4, wy + 10, 3, 9, mat="overalls", seed=31, angle=0.9),
        *fold_lines(cx + 10, cy + 6, wx + 4, wy + 10, 3, 9, mat="overalls", seed=32, angle=-0.9, side=-1),
        # the hips of the suit, the thighs grow out of them
        limb(px - 11, py + 2, px + 11, py + 2, 10.0, 10.0, "overalls", k=8),
    ]
    # the ID badge on its lanyard, clipped left of the zip, swinging a little
    bx, by = cx - 8 + 2.0 * p.badge, cy + 24
    badge = [
        limb(cx - 4, cy - 1, bx, by - 4, 0.5, 0.5, "darkmetal", op="add", depth=0.4),
        slab([(bx - 5.0, by - 4), (bx + 5.0, by - 4), (bx + 5.0, by + 5.0), (bx - 5.0, by + 5.0)], "livery", bevel=0.9,
             slope=1.0, prio=40),
    ]
    hood, mask = _head(j, p)
    # the breathing hose: from the canister down to a pack on the right hip, lagging the head
    hx, hy = j["head"]
    cx2, cy2 = Xf.turn(j["head_angle"], (0, 0)).then(Xf.move(hx, hy)).pt((0, SNOUT_END))
    sag = 3.0 * p.hood_lag
    hose = chain([(cx2 + 3, cy2 + 1), (cx2 + 10 + sag, cy2 + 10), (cx + 14 + sag, cy + 32), (px + 12, py - 8)],
                 1.9, 1.7, "rubber", k=2, prio=10)
    hose += [blob(px + 12.5, py - 6, 3.6, 5.2, "gunmetal", depth=4, k=2, prio=12)]
    layers = [
        _leg(j, "l", -1) + _leg(j, "r", 1),
        torso + badge + hood,
        hose,
        mask,
        _arm(j, p, "l", -1),
        _arm(j, p, "r", 1),
    ]
    anchors = {
        "ground": (80.0 + p.bx, 232.0),
        "hit_centre": ((cx + px) / 2, (cy + py) / 2),
        "eye": j["head"],
        "mouth": ((j["l_hand"][0] + j["r_hand"][0]) / 2, (j["l_hand"][1] + j["r_hand"][1]) / 2),
    }
    stencils = []
    if p.tag > 0.5:
        stencils.append(("14", bx - 3.5, by - 1.5, (40, 44, 52)))
    fig = Figure(layers, anchors, stencils)
    if p.fall > 0.005:     # DEATH: the body collapses toward the floor (and the viewer) about the ground line
        fig = fig.transformed(Xf.scale(1.0 + 0.08 * p.fall, 1.0 - 0.72 * p.fall, (80.0, 232.0)))
    return fig


GHOUL = Rig("ghoul", ghoul_build, GHOUL_POSE, seed=53)


# ---- clips (R4: it moves WRONG - patient, swaying, the head never sits straight) ---------------------------------------
_REST = dict(torso=0.025, chest=0.05, neck=0.06, head=0.30, l_sh=0.04, r_sh=0.13, l_el=-0.16, r_el=-0.30, l_wr=-0.10,
             r_wr=0.06, l_arm_fore=0.0, r_arm_fore=0.10, l_hip=0.07, r_hip=0.04, l_knee=-0.09, r_knee=-0.06, squat=0.03,
             by=0.0, bx=0.0, l_curl=0.12, r_curl=0.22, eye_glow=1.0)
# the grab: both arms thrust at the viewer, long fingers wide, then clamped
# the reach: both long arms come up and out toward the viewer, hands at chest height, fingers splayed
_REACH = dict(l_sh=0.85, r_sh=0.85, l_el=-0.75, r_el=-0.75, l_arm_fore=0.50, r_arm_fore=0.50, l_wr=-0.35, r_wr=-0.35,
              l_curl=0.0, r_curl=0.0, head=0.05, torso=0.0, chest=0.0, by=-2.0, eye_glow=1.7)
# the grab: the hands snap in together in front of the chest and clench
_CLAMP = dict(l_sh=0.35, r_sh=0.35, l_el=-1.45, r_el=-1.45, l_arm_fore=0.55, r_arm_fore=0.55, l_wr=-0.3, r_wr=-0.3,
              l_curl=1.0, r_curl=1.0, head=0.0, by=2.0, eye_glow=2.0)

_IDLE = Clip("IDLE", [
    (0.00, dict(torso=0.025, head=0.30, l_sh=0.04, r_sh=0.13, badge=0.0, by=0.0, l_curl=0.12)),
    (0.40, dict(torso=0.045, head=0.42, l_sh=0.08, r_sh=0.10, by=1.0, l_curl=0.22)),
    (0.95, dict(torso=0.00, head=0.20, l_sh=0.02, r_sh=0.16, by=0.0, l_curl=0.08)),
    (1.25, dict(head=0.34, by=0.5)),
    (0.55, dict(r_curl=0.40), "hold"), (0.70, dict(r_curl=0.22)),           # a finger twitch
], duration=1.6, mode="loop")

def _scaled_lag(keys, param, gain, offset=0.0):
    return [(t, {k: (v - offset) * gain for k, v in d.items()}, e) for t, d, e in keys]


IDLE = Clip("IDLE", _IDLE.keys + _scaled_lag(lag(_IDLE, "torso", 0.17, 1.6, out_param="badge", fps=12), "badge", 12.0, 0.025)
            + _scaled_lag(lag(_IDLE, "head", 0.12, 1.6, out_param="hood_lag", fps=12), "hood_lag", 3.0, 0.30),
            duration=1.6, mode="loop")

GHOUL_CLIPS = [
    IDLE,
    # DORMANT: the head hangs, the red eyes almost out
    IDLE.variant("DORMANT", speed=0.5, fps=6, eye_glow=0.22, neck=0.20, by=3.0, l_curl=0.05, r_curl=0.08),
    Clip("ALERT", [        # the head snaps upright, the eyes flare, the fingers spread
        (0.00, dict(_REST, head=0.30, eye_glow=1.0)),
        (0.10, dict(head=-0.10, neck=0.0, by=-3.0, eye_glow=2.0, l_curl=0.0, r_curl=0.0, l_sh=0.20, r_sh=0.26), "out"),
        (0.25, dict(head=0.02, by=-2.0, eye_glow=1.6)),
        (0.45, dict(_REST, head=0.18, eye_glow=1.2)),
    ], mode="once"),
    Clip("MOVE", [         # the shamble: a long leg swings, the other foot scuffs; the whole frame lists after it
        (0.000, dict(l_hip=0.16, l_knee=-0.20, l_leg_fore=0.35, r_hip=0.00, r_knee=-0.02, r_leg_fore=0.0, torso=-0.05,
                     by=-1.5, l_sh=-0.04, r_sh=0.24, head=0.40, badge=-0.6)),
        (0.083, dict(l_leg_fore=0.10, by=1.0, torso=-0.02)),
        (0.167, dict(r_hip=0.13, r_knee=-0.18, r_leg_fore=0.35, l_hip=0.04, l_knee=-0.04, l_leg_fore=0.0, torso=0.07,
                     by=-1.5, l_sh=0.20, r_sh=0.04, head=0.22, badge=0.6)),
        (0.250, dict(r_leg_fore=0.10, by=1.0, torso=0.04)),
    ], duration=0.333, mode="loop"),
    Clip("WIND_UP", [      # the telegraph: both arms come up and out at you, fingers opening wide - held
        (0.00, dict(_REST)),
        (0.30, dict(_REACH, l_sh=0.70, r_sh=0.70, eye_glow=1.5)),
        (0.50, dict(_REACH, eye_glow=1.8)),
    ], mode="hold"),
    Clip("ATTACK", [       # the clumsy two-handed grab: reach, clamp, drag back
        (0.00, dict(_REST)),
        (0.08, dict(_REACH), "out"),
        (0.14, dict(_CLAMP), "in"),
        (0.22, dict(_CLAMP, l_arm_fore=0.35, r_arm_fore=0.35, by=1.0, r_curl=0.85)),
        (0.30, dict(_REST)),
    ], fps=20, mode="once", events={0.14: "impact"}),
    Clip("HURT_LIGHT", [   # a jolt; the head whips the other way
        (0.00, dict(head=-0.05, torso=-0.04, by=-1.5, eye_glow=0.5), "out"),
        (0.17, dict(head=0.30, torso=0.025, by=0.0, eye_glow=1.0)),
    ], mode="once"),
    Clip("HURT_HEAVY", [   # it rocks back on its long legs, arms flung - and keeps coming
        (0.00, dict(_REST)),
        (0.08, dict(by=-5.0, torso=-0.10, chest=-0.06, head=-0.30, l_sh=0.30, r_sh=0.34, l_el=0.1, r_el=0.12, l_curl=0.0,
                    r_curl=0.0, l_hip=0.12, r_hip=0.10, eye_glow=0.4, crack=0.4), "out"),
        (0.25, dict(by=-1.0, torso=0.06, head=0.50, l_sh=0.15, r_sh=0.25, l_el=-0.1, r_el=-0.2, eye_glow=1.3, crack=0.0)),
        (0.42, dict(_REST)),
    ], mode="once"),
    Clip("DEFEND", [       # it folds its long arms across the mask and hunches
        (0.00, dict(_REST, tag=1.0)),
        (0.33, dict(l_sh=-0.30, r_sh=-0.30, l_el=-2.15, r_el=-2.05, l_wr=-0.4, r_wr=-0.4, l_curl=0.6, r_curl=0.6,
                    neck=0.15, head=0.10, squat=0.25, by=8.0, eye_glow=0.7, tag=0.0)),
    ], mode="hold"),
    # WOUNDED: the visor cracked, red leaking; the left arm hangs dead off its shoulder, the head rolled over
    IDLE.variant("WOUNDED", crack=1.0, l_sh=0.0, l_el=0.0, l_wr=0.0, l_curl=0.0, neck=0.12, squat=0.08, by=2.0),
    Clip("DEATH", [        # folds at the knees, sways, and goes down on its face
        (0.00, dict(_REST, squat=0.03, by=0.0, fall=0.0, dim=0.0)),
        (0.12, dict(head=-0.20, eye_glow=1.9, l_sh=0.30, r_sh=0.35, by=-2.0), "out"),
        (0.38, dict(squat=1.0, by=44.0, l_knee=-0.6, r_knee=-0.55, l_hip=0.30, r_hip=0.28, l_sh=0.05, r_sh=0.10,
                    l_el=0.0, r_el=0.0, head=0.45, torso=0.06), "in"),
        (0.52, dict(by=40.0, torso=-0.06, head=0.30)),
        (0.80, dict(fall=1.0, by=46.0, l_hip=0.12, r_hip=0.10, l_sh=-0.10, r_sh=-0.05, l_el=-0.3, r_el=-0.4, l_curl=0.3, r_curl=0.4, head=0.6), "in"),
        (0.92, dict(fall=0.94), "out"),
        (1.00, dict(fall=1.0), "hold"),
        (0.30, dict(dim=0.0)), (0.85, dict(dim=1.0)),
    ], mode="hold"),
]
