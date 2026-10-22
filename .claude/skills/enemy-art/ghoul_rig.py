"""GHOUL rig + clips (UNDEAD, CHAFF, 0.90 walls, slow melee shambler). Imported by bestiary.py.

A decontamination labourer who was sealed in his suit when the galleries flooded with raw soul, and never came out.
Owner brief (2026-10-22): TALL and THIN, legs, arms and fingers far too long for a person; a slate Organization hazard
suit, hood up; a GAS MASK with ONE panoramic glass visor (never two lenses) and RED eyes burning behind it. No face
is ever visible. Story detail: the lanyard ID badge still clipped to the chest (stencil "D-14", decon crew).
Motion character: patient, swaying, the head cocked; it walks like something remembering how.
"""
import math

from kit import blob, limb, chain, slab, fold_lines, hand, Humanoid, with_glow, Xf, Figure, Rig, Clip, lag

SKELETON = Humanoid(pelvis=(80.0, 112.0), spine=58.0, neck=9.0, head=11.0, shoulder_half=15.0, hip_half=7.5,
                    upper_arm=44.0, fore_arm=42.0, hand=12.0, thigh=58.0, shin=60.0, foot=9.0,
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
        # the filter: one squat canister on a short snout under the visor
        limb(0, 7, 0, 11.5, 4.0, 4.8, "rubber", op="add", depth=3.0),
        blob(0, 14.0, 5.8, 4.2, "gunmetal", depth=5, prio=30),
        *[limb(-3.6 + 1.8 * i, 12.6, -3.6 + 1.8 * i, 15.6, 0.45, 0.45, "gunmetal", op="carve") for i in range(5)],
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
        limb(*sh, *el, 5.6, 4.6, "overalls", k=4),
        limb(*el, *wr, 4.6, 3.6, "overalls", k=4),
        blob(*el, 4.4, 4.4, "overalls", depth=4.4, k=3),
        # the elbow bunches the suit; a reflective-less seam down the sleeve
        *fold_lines(el[0] - 3, el[1] - 4, el[0] + 3, el[1] + 4, 3, 5.5, mat="overalls", seed=11 + s, angle=0.2),
        limb(*sh, *el, 0.6, 0.5, "overalls", op="carve"),
        # the rubber gauntlet over the forearm's last third, and the glove: LONG fingers
        limb(wr[0] + (el[0] - wr[0]) * 0.32, wr[1] + (el[1] - wr[1]) * 0.32, *wr, 4.4, 3.9, "rubber", op="add", depth=1.2),
        *hand(wr, j[f"{side}_hand_angle"], s, 11.0, "rubber", fingers=4, finger_length=29.0, finger_r=1.2,
              palm_r=3.3, spread=0.40, curl=curl),
    ]
    return shapes


def _leg(j, side, s):
    hp, kn, an, to = j[f"{side}_hip"], j[f"{side}_knee"], j[f"{side}_ankle"], j[f"{side}_toe"]
    return [
        limb(*hp, *kn, 7.0, 5.0, "overalls", k=4),
        limb(*kn, *an, 5.0, 3.8, "overalls", k=4),
        blob(*kn, 5.2, 5.6, "overalls", depth=5, k=3),
        *fold_lines(kn[0] - 3.5, kn[1] - 5, kn[0] + 3.5, kn[1] + 5, 3, 6, mat="overalls", seed=21 + s, angle=-0.25),
        # the boot: rubber to mid-shin, the toe turned out
        limb(an[0] + (kn[0] - an[0]) * 0.38, an[1] + (kn[1] - an[1]) * 0.38, *an, 4.6, 4.4, "rubber", op="add", depth=1.4),
        limb(*an, *to, 4.6, 3.6, "rubber", k=3),
        blob(an[0] + s * 1.5, an[1] + 1.8, 5.0, 3.0, "rubber", depth=3, k=3),
    ]


def ghoul_build(p):
    j = SKELETON.joints(p)
    px, py = j["pelvis"]
    cx, cy = j["chest"]
    wx, wy = j["waist"]
    torso = [
        # a thin, long torso: the suit hangs off the shoulders and pinches at the belt
        limb(cx, cy - 2, wx, wy, 13.0, 9.5, "overalls", k=8),
        limb(wx, wy, px, py, 9.5, 10.5, "overalls", k=8),
        blob(*j["l_shoulder"], 6.5, 5.5, "overalls", depth=5.5, k=6),
        blob(*j["r_shoulder"], 6.5, 5.5, "overalls", depth=5.5, k=6),
        limb(*j["neck"], cx, cy, 5.0, 6.0, "overalls", k=5),
        # the zip down the front, the belt, the creases pulling from shoulder to hip
        limb(cx, cy + 4, px, py - 6, 0.7, 0.7, "overalls", op="carve"),
        limb(px - 11, py - 7, px + 11, py - 7, 2.2, 2.2, "rubber", op="add", depth=1.6),
        blob(px - 1.0, py - 7, 2.4, 2.0, "darkmetal", op="add", depth=1.2),
        *fold_lines(cx - 10, cy + 6, wx - 4, wy + 10, 3, 9, mat="overalls", seed=31, angle=0.9),
        *fold_lines(cx + 10, cy + 6, wx + 4, wy + 10, 3, 9, mat="overalls", seed=32, angle=-0.9, side=-1),
        # the hips of the suit, the thighs grow out of them
        limb(px - 7, py + 1, px + 7, py + 1, 7.5, 7.5, "overalls", k=6),
    ]
    # the ID badge on its lanyard, clipped left of the zip, swinging a little
    bx, by = cx - 6 + 2.0 * p.badge, cy + 15
    badge = [
        limb(cx - 4, cy - 1, bx, by - 4, 0.5, 0.5, "darkmetal", op="add", depth=0.4),
        slab([(bx - 5.0, by - 4), (bx + 5.0, by - 4), (bx + 5.0, by + 5.0), (bx - 5.0, by + 5.0)], "livery", bevel=0.9,
             slope=1.0, prio=40),
    ]
    hood, mask = _head(j, p)
    # the breathing hose: from the canister down to a pack on the right hip, lagging the head
    hx, hy = j["head"]
    cx2, cy2 = Xf.turn(j["head_angle"], (0, 0)).then(Xf.move(hx, hy)).pt((0, 15))
    sag = 3.0 * p.hood_lag
    hose = chain([(cx2 + 2, cy2 + 2), (cx2 + 9 + sag, cy2 + 16), (cx + 13 + sag, cy + 30), (px + 12, py - 8)],
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
    return Figure(layers, anchors, stencils)


GHOUL = Rig("ghoul", ghoul_build, GHOUL_POSE, seed=53)

GHOUL_CLIPS = []      # CP3 adds the clip set once the owner has approved the concept still
