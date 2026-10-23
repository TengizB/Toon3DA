"""BLIGHT CORRUPTOR rig + clips (UNDEAD, SOLDIER, 1.00 walls, durable carrier, SUMMON). Imported by bestiary.py.

A quarantine worker in a full olive-grey hazmat suit, hood sealed, a two-lens respirator (soul-white light behind
both glasses) - and swollen from inside: spore growth has burst through the suit in soft, soul-lit pods across the
shoulder, the back and the belly. The decontamination tank is still strapped to its back, the sprayer wand still in
its fist. No face is visible. Story detail: the QUARANTINE stencil on the tank. Motion character: laboured breathing,
pods pulsing; a slow heavy trudge; it summons by splitting its pods and releasing what grew inside.
"""
import math

from kit import blob, limb, chain, slab, fold_lines, hand, Humanoid, with_glow, Xf, Figure, Rig, Clip

SKELETON = Humanoid(pelvis=(80.0, 136.0), spine=58.0, neck=6.0, head=15.0, shoulder_half=27.0, hip_half=13.0,
                    upper_arm=33.0, fore_arm=30.0, hand=11.0, thigh=42.0, shin=44.0, foot=10.0,
                    arm_rest=0.30, leg_rest=0.14)

CORRUPTOR_POSE = Humanoid.params(
    torso=0.05, chest=0.06, head=0.12,               # bowed under its own growth, listing to the pod side
    l_sh=0.02, l_el=-0.25, l_wr=-0.1,                # the left hand hangs heavy
    r_sh=-0.05, r_el=-0.85, r_wr=-0.25,              # the right fist holds the sprayer wand across its belly
    l_hip=0.08, r_hip=0.10, l_knee=-0.10, r_knee=-0.12, squat=0.12,
)
CORRUPTOR_POSE.update(
    breath=0.0,                                       # laboured breathing (0 .. 1)
    pods=0.0,                                         # pod pulse (0 rest .. 1 swollen)
    split=0.0,                                        # SUMMON: the pods split and release (0 .. 1)
    pod_glow=1.0, eye_glow=1.0, dim=0.0,
    sag=0.0,                                          # DEATH: it collapses (0 .. 1)
    tag=1.0,
)

# spore pods: (x, y, rx, ry) on the canvas at rest - clustered on the left shoulder, the back (seen over the right
# shoulder) and the belly; one cluster per side keeps it asymmetric
# (joint, dx, dy, rx, ry): each pod rides a joint of the skeleton, so it moves with the body
PODS = (("l_shoulder", 3, -6, 12, 10.5), ("l_shoulder", 15, 5, 8.5, 8), ("l_shoulder", -7, 6, 7, 6.5),
        ("chest", -8, 2, 6, 5.5), ("chest", 15, 12, 8, 7.5),
        ("waist", 10, 14, 11, 10), ("waist", 22, 24, 7, 6.5), ("waist", -1, 27, 6, 5.5), ("r_shoulder", 3, -7, 6.5, 6))


def _head(j, p):
    hx, hy = j["head"]
    xf = Xf.turn(j["head_angle"], (0, 0)).then(Xf.move(hx, hy))
    eye = p.eye_glow * (1 - p.dim)
    hood = [
        blob(0, 0, 17.5, 17.0, "hazmat", depth=14, k=5),                       # the sealed hood
        blob(0, 13, 18.0, 8.0, "hazmat", depth=9, k=6),
        *fold_lines(-15, 0, -11, 14, 3, 6, mat="hazmat", seed=5, angle=0.6),
        *fold_lines(15, 0, 11, 14, 3, 6, mat="hazmat", seed=6, angle=-0.6, side=-1),
    ]
    mask = [
        blob(0, 2, 13.0, 12.0, "rubber", depth=8, k=3),
        blob(-6.0, -1.5, 4.6, 4.4, "gunmetal", depth=3.0, prio=50, k=0),
        blob(6.0, -1.5, 4.6, 4.4, "gunmetal", depth=3.0, prio=50, k=0),
        blob(-6.0, -1.5, 3.4, 3.2, "visor", op="add", depth=0.6),
        blob(6.0, -1.5, 3.4, 3.2, "visor", op="add", depth=0.6),
        *with_glow([blob(-6.0, -1.3, 2.0, 1.9, "soul", op="add", depth=0.8),
                    blob(6.0, -1.3, 2.0, 1.9, "soul", op="add", depth=0.8)], eye),
        # one big central filter, cracked open by a pod growing out of it
        limb(0, 7, 0, 11, 4.0, 5.0, "rubber", op="add", depth=2.8),
        blob(0, 13.0, 6.2, 4.6, "gunmetal", depth=5, prio=30),
        *[limb(-4 + 2 * i, 11.5, -4 + 2 * i, 14.8, 0.45, 0.45, "gunmetal", op="carve") for i in range(5)],
    ]
    return xf(hood), xf(mask)


def _pods(p, j, body_xf):
    pulse = 1.0 + 0.10 * p.pods + 0.06 * p.breath + 0.25 * p.split
    level = p.pod_glow * (0.72 + 0.35 * p.pods + 0.8 * p.split) * (1 - p.dim)
    shapes = []
    for i, (joint, dx, dy, rx, ry) in enumerate(PODS):
        x, y = j[joint][0] + dx, j[joint][1] + dy
        size = pulse * (1.0 - 0.25 * p.split * (i % 2))
        # the suit torn back round each pod: a ragged lip of hazmat rubber the pod bulges out of
        shapes.append(blob(x + 0.5, y + 1.0, rx * 1.28 * size, ry * 1.22 * size, "hazmat", depth=2.2, k=2))
        shapes.append(blob(x, y, rx * size, ry * size, "spore", depth=min(rx, ry) * 0.95 * size, glow=level, k=2, prio=20))
        # dark veins over the pod skin
        shapes += [limb(x - rx * 0.7 * size, y - ry * 0.1, x + rx * 0.1, y - ry * 0.75 * size, 0.55, 0.4, "spore", op="carve"),
                   limb(x + rx * 0.15, y + ry * 0.7 * size, x + rx * 0.75 * size, y - ry * 0.05, 0.5, 0.4, "spore", op="carve")]
        if p.split > 0.05:                                                     # SUMMON: each pod splits open
            shapes.append(limb(x - rx * 0.6, y - ry * 0.2, x + rx * 0.6, y + ry * 0.1, 0.6 + 1.2 * p.split, 0.5 + p.split,
                               "spore", op="carve"))
            shapes += with_glow([blob(x, y, rx * 0.35 * p.split + 0.5, ry * 0.2 * p.split + 0.4, "soul", op="add", depth=0.7)],
                                1.0 + 0.4 * p.split)
    return body_xf(shapes)


def _arm(j, side, s, mat="hazmat"):
    sh, el, wr = j[f"{side}_shoulder"], j[f"{side}_elbow"], j[f"{side}_wrist"]
    return [
        limb(*sh, *el, 10.5, 9.0, mat, k=4),
        limb(*el, *wr, 9.0, 7.5, mat, k=4),
        blob(*el, 8.5, 8.0, mat, depth=7, k=3),
        *fold_lines(el[0] - 4, el[1] - 5, el[0] + 4, el[1] + 5, 3, 7, mat=mat, seed=33 + s, angle=0.25),
        limb(wr[0] + (el[0] - wr[0]) * 0.25, wr[1] + (el[1] - wr[1]) * 0.25, *wr, 7.6, 7.2, "rubber", op="add", depth=1.6),
        *hand(wr, j[f"{side}_hand_angle"], s, 10.0, "rubber", fingers=4, finger_length=11.0, finger_r=2.0,
              palm_r=4.8, spread=0.35, curl=0.6),
    ]


def _leg(j, side, s):
    hp, kn, an, to = j[f"{side}_hip"], j[f"{side}_knee"], j[f"{side}_ankle"], j[f"{side}_toe"]
    return [
        limb(*hp, *kn, 12.5, 10.0, "hazmat", k=5),
        limb(*kn, *an, 10.0, 8.0, "hazmat", k=5),
        blob(*kn, 9.0, 8.5, "hazmat", depth=7, k=4),
        *fold_lines(kn[0] - 4, kn[1] - 6, kn[0] + 4, kn[1] + 6, 3, 8, mat="hazmat", seed=43 + s, angle=-0.3),
        limb(an[0] + (kn[0] - an[0]) * 0.35, an[1] + (kn[1] - an[1]) * 0.35, *an, 8.6, 8.2, "rubber", op="add", depth=1.8),
        limb(*an, *to, 7.5, 5.5, "rubber", k=3),
        blob(an[0] + s * 1.5, an[1] + 2, 9.0, 4.5, "rubber", depth=4, k=3),
    ]


def blight_corruptor_build(p):
    sag = p.sag
    root = Xf.scale(1.0 + 0.12 * sag, 1.0 - 0.38 * sag, (80, 232)) if sag > 0.005 else Xf()
    j = SKELETON.joints(p)
    px, py = j["pelvis"]
    cx, cy = j["chest"]
    wx, wy = j["waist"]
    swell = 1.0 + 0.04 * p.breath
    body_xf = Xf.scale(swell, swell, (wx, wy))
    body = body_xf([
        limb(cx, cy, wx, wy, 25.0, 25.0, "hazmat", k=10),
        limb(wx, wy, px, py, 25.0, 19.0, "hazmat", k=10),
        blob(wx + 3, wy + 10, 25.0, 22.0, "hazmat", depth=18, k=10),           # the belly swollen with growth
        blob(*j["l_shoulder"], 12.0, 10.0, "hazmat", depth=10, k=8),
        blob(*j["r_shoulder"], 11.0, 9.0, "hazmat", depth=9, k=8),
        limb(*j["neck"], cx, cy, 11.0, 14.0, "hazmat", k=6),
        limb(px - 13, py + 2, px + 13, py + 2, 12.0, 12.0, "hazmat", k=6),
        # the suit's taped seams and the zip flap, the straps of the tank over both shoulders
        limb(cx + 2, cy + 2, px + 2, py - 4, 1.8, 1.8, "hazmat", op="add", depth=1.2),
        limb(cx - 16, cy - 4, wx - 14, wy + 14, 2.6, 2.6, "rubber", op="add", depth=1.6),
        limb(cx + 16, cy - 4, wx + 16, wy + 12, 2.6, 2.6, "rubber", op="add", depth=1.6),
        *fold_lines(cx - 20, cy + 6, wx - 22, wy + 18, 4, 10, mat="hazmat", seed=51, angle=0.9),
        *fold_lines(cx + 20, cy + 6, wx + 22, wy + 18, 4, 10, mat="hazmat", seed=52, angle=-0.9, side=-1),
    ])
    hood, mask = _head(j, p)
    # the decon tank on its back: its rounded top shows over the right shoulder, a hose to the wand
    tank = [
        limb(cx + 22, cy - 22, cx + 22, cy + 8, 9.0, 9.0, "gunmetal", k=3),
        blob(cx + 22, cy - 26, 9.0, 5.0, "gunmetal", depth=6, k=3),
        limb(cx + 13, cy - 16, cx + 31, cy - 16, 1.0, 1.0, "gunmetal", op="carve"),
        blob(cx + 22, cy - 31, 3.0, 2.2, "darkmetal", depth=2.5, prio=10),
    ]
    wr = j["r_wrist"]
    a = j["r_hand_angle"]
    grip = (wr[0] + math.sin(a) * 6, wr[1] + math.cos(a) * 6)
    wand_dir = (-0.86, 0.50)
    tip = (grip[0] + wand_dir[0] * 40, grip[1] + wand_dir[1] * 40)
    wand = [limb(grip[0] - wand_dir[0] * 6, grip[1] - wand_dir[1] * 6, *tip, 2.2, 1.9, "darkmetal", k=1, prio=40),
            limb(tip[0] + 3 * 0.86, tip[1] - 3 * 0.5, *tip, 3.0, 3.2, "gunmetal", k=1, prio=45),
            *chain([(grip[0] + 4, grip[1] + 2), (grip[0] + 16, grip[1] + 18), (px + 26, py), (cx + 26, cy + 8)],
                   2.0, 1.9, "rubber", k=2)]
    layers = [
        root(tank),
        root(_leg(j, "l", -1) + _leg(j, "r", 1)),
        root(body + hood),
        root(mask),
        # the pods sit in front of the hanging left arm; a RAISED or forward arm (smash, guard) comes in front of them
        *((root(_pods(p, j, body_xf)), root(_arm(j, "l", -1))) if p.l_el < -1.2 or p.l_arm_fore > 0.3 else
          (root(_arm(j, "l", -1)), root(_pods(p, j, body_xf)))),
        root(_arm(j, "r", 1) + wand),
    ]
    anchors = {
        "ground": (80.0 + p.bx, 232.0),
        "hit_centre": root.pt((wx, wy)),
        "eye": root.pt(j["head"]),
        "mouth": root.pt(tip),
    }
    stencils = []
    if p.tag > 0.5 and sag < 0.3:
        stencils.append(("Q", *root.pt((cx + 20.5, cy - 12.0)), (196, 192, 170)))
    return Figure(layers, anchors, stencils)


BLIGHT_CORRUPTOR = Rig("blight_corruptor", blight_corruptor_build, CORRUPTOR_POSE, seed=71)


# ---- clips (R4: it moves like something too heavy for its own legs; the growth breathes, not the worker) ------------
_REST = dict(torso=0.05, chest=0.06, head=0.12, l_sh=0.02, l_el=-0.25, l_wr=-0.1, r_sh=-0.05, r_el=-0.85, r_wr=-0.25,
             l_hip=0.08, r_hip=0.10, l_knee=-0.10, r_knee=-0.12, squat=0.12, by=0.0, bx=0.0, breath=0.0, pods=0.0,
             split=0.0, eye_glow=1.0, l_arm_fore=0.0, r_arm_fore=0.0)
# the free left fist raised up the front for the smash (lesson 12.17: folded in, never swung out past the edge)
_FIST_UP = dict(l_sh=0.55, l_el=-2.30, l_wr=-0.3, l_arm_fore=0.25, torso=-0.06, chest=-0.04, head=0.0, by=-3.0,
                breath=1.0, pods=1.0, eye_glow=1.6)
_SLAMMED = dict(l_sh=-0.25, l_el=-0.60, l_wr=-0.2, l_arm_fore=0.55, torso=0.10, chest=0.08, head=0.20, by=6.0,
                squat=0.30, breath=0.4, pods=0.6, eye_glow=1.9)

IDLE = Clip("IDLE", [
    (0.00, dict(breath=0.0, head=0.12, by=0.0)),
    (0.70, dict(breath=1.0, head=0.02, by=-1.5)),
    (1.60, dict(breath=0.0)),
    (1.10, dict(head=0.18, by=0.0)),
    (0.20, dict(pods=0.0)), (0.95, dict(pods=1.0)), (1.45, dict(pods=0.1)),   # the pods swell a beat behind the breath
], duration=1.6, mode="loop")

CORRUPTOR_CLIPS = [
    IDLE,
    IDLE.variant("DORMANT", speed=0.5, fps=6, eye_glow=0.25, pod_glow=0.45, head=0.40, squat=0.22, by=4.0),
    Clip("ALERT", [        # it straightens with a wet heave, the pods flaring
        (0.00, dict(_REST)),
        (0.12, dict(by=-4.0, head=-0.10, breath=1.0, pods=1.4, eye_glow=2.0, l_sh=0.12, torso=0.0), "out"),
        (0.30, dict(by=-2.0, pods=0.9, eye_glow=1.5)),
        (0.45, dict(_REST, pods=0.3)),
    ], mode="once"),
    Clip("MOVE", [         # a slow, heavy trudge: one leg at a time, the whole mass rolling over it
        (0.000, dict(l_hip=0.14, l_knee=-0.28, l_leg_fore=0.30, r_hip=0.10, r_knee=-0.12, r_leg_fore=0.0, torso=0.0,
                     by=-1.0, bx=-2.0, pods=0.6)),
        (0.083, dict(l_leg_fore=0.05, by=2.0, bx=0.0, torso=0.04, pods=0.2)),
        (0.167, dict(r_hip=0.16, r_knee=-0.28, r_leg_fore=0.30, l_hip=0.08, l_knee=-0.10, l_leg_fore=0.0, torso=0.10,
                     by=-1.0, bx=2.0, pods=0.6)),
        (0.250, dict(r_leg_fore=0.05, by=2.0, bx=0.0, torso=0.06, pods=0.2)),
    ], duration=0.333, mode="loop"),
    Clip("WIND_UP", [      # the free fist raised over the pods - held
        (0.00, dict(_REST)),
        (0.40, dict(_FIST_UP)),
    ], mode="hold"),
    Clip("ATTACK", [       # the smash: the fist comes down at you with the whole swollen weight behind it
        (0.00, dict(_REST)),
        (0.08, dict(_FIST_UP), "out"),
        (0.14, dict(_SLAMMED), "in"),
        (0.22, dict(_SLAMMED, by=4.0, pods=0.3)),
        (0.30, dict(_REST)),
    ], fps=20, mode="once", events={0.14: "impact"}),
    Clip("SUMMON", [       # the ability: it hunches, the pods swell and SPLIT, and what grew inside is released
        (0.00, dict(_REST)),
        (0.18, dict(squat=0.30, by=6.0, torso=0.12, chest=0.10, head=0.30, breath=1.0, pods=1.6, eye_glow=1.6), "in"),
        (0.30, dict(split=1.0, pods=1.2, by=2.0, torso=0.0, head=-0.05, l_sh=0.30, eye_glow=2.0), "out"),
        (0.45, dict(split=0.8, pods=0.4, breath=0.2)),
        (0.60, dict(_REST, split=0.0)),
    ], mode="once"),
    Clip("HURT_LIGHT", [
        (0.00, dict(by=-1.5, head=-0.05, pods=1.0, eye_glow=0.5), "out"),
        (0.17, dict(by=0.0, head=0.12, pods=0.0, eye_glow=1.0)),
    ], mode="once"),
    Clip("HURT_HEAVY", [   # rocked back on its heels, the pods shuddering
        (0.00, dict(_REST)),
        (0.08, dict(by=-4.0, torso=-0.10, chest=-0.06, head=-0.25, l_sh=0.22, pods=1.5, eye_glow=0.4, split=0.3), "out"),
        (0.25, dict(by=-1.0, torso=0.10, head=0.25, pods=0.6, eye_glow=1.3, split=0.0)),
        (0.42, dict(_REST)),
    ], mode="once"),
    Clip("DEFEND", [       # it hunches over its pods, the free arm folded across them
        (0.00, dict(_REST, tag=1.0)),
        (0.33, dict(l_sh=-0.25, l_el=-1.90, l_wr=-0.3, squat=0.30, by=6.0, torso=0.10, chest=0.12, head=0.35,
                    eye_glow=0.7, pod_glow=0.6)),
    ], mode="hold"),
    # WOUNDED: two pods have split and weep light; it lists, the breathing ragged
    IDLE.variant("WOUNDED", split=0.45, torso=0.12, squat=0.20, by=3.0, head=0.30),
    Clip("DEATH", [        # it goes down under its own weight and the pods burst
        (0.00, dict(_REST, sag=0.0, split=0.0, dim=0.0)),
        (0.12, dict(by=-3.0, head=-0.15, pods=1.6, eye_glow=1.9, breath=1.0), "out"),
        (0.35, dict(squat=0.8, by=20.0, sag=0.3, split=1.0, pods=1.2, torso=0.10, head=0.30, l_sh=0.15), "in"),
        (0.65, dict(sag=1.0, by=26.0, pods=0.0, breath=0.0, head=0.45), "in"),
        (0.80, dict(sag=0.94), "out"),
        (0.90, dict(sag=1.0), "hold"),
        (0.30, dict(dim=0.0)), (0.80, dict(dim=1.0)),
    ], mode="hold"),
]
