"""REVENANT rig + clips (UNDEAD, BRUISER, 1.05 walls, fast heavy melee + a telegraphed lunge). Imported by bestiary.py.

Organization security, still in the riot armour it died in: a full helmet whose single visor is cracked and dark
except for the cold soul light behind it, heavy pauldrons, a chest plate stencilled with its unit number, a shock
baton in the right fist. No face: the helmet never comes off. Motion character: a soldier's stance it still
remembers - alert, square, then a heavy charging stride and an overhead smash.
"""
import math

from kit import blob, limb, chain, slab, riveted_plate, fold_lines, hand, Humanoid, with_glow, Xf, Figure, Rig, Clip

SKELETON = Humanoid(pelvis=(80.0, 134.0), spine=56.0, neck=6.0, head=15.0, shoulder_half=25.0, hip_half=12.0,
                    upper_arm=34.0, fore_arm=31.0, hand=10.0, thigh=46.0, shin=46.0, foot=10.0,
                    arm_rest=0.22, leg_rest=0.13)

REVENANT_POSE = Humanoid.params(
    torso=-0.04, chest=0.03, head=-0.20,
    l_sh=0.05, l_el=-0.75, l_wr=-0.15,                 # the empty left fist up at the belt, guarding
    r_sh=0.12, r_el=-0.40, r_wr=0.10,                  # the baton low in the right fist, tip raised, ready
    l_hip=0.12, r_hip=0.12, l_knee=-0.14, r_knee=-0.12, squat=0.14,
)
REVENANT_POSE.update(
    baton_angle=0.0,                               # the baton turned in the fist (radians, + = further over)
    baton=0.0,                                     # baton glow (0 dead .. 1 charged): the shock tip crackles
    eye_glow=1.0, dim=0.0,
    crack=1.0,                                     # the visor crack (it was cracked when it died; WOUNDED widens it)
    fall=0.0,                                     # DEATH: the whole body collapses toward the floor (0..1)
    tag=1.0,
)


def _helmet(j, p):
    hx, hy = j["head"]
    xf = Xf.turn(j["head_angle"], (0, 0)).then(Xf.move(hx, hy))
    eye = p.eye_glow * (1 - p.dim)
    shell = [
        blob(0, -1, 16.5, 17.0, "darkmetal", depth=14, k=4),                    # the helmet shell
        blob(0, 13, 15.5, 7.0, "darkmetal", depth=8, k=5),                      # the neck guard
        limb(-16, 6, 16, 6, 1.0, 1.0, "darkmetal", op="carve"),
        limb(0, -17.5, 0, -8, 2.2, 2.0, "darkmetal", op="add", depth=1.4),      # the crest ridge
    ]
    visor = [
        # one wide, dark riot visor; the soul light behind it shows only through the crack and as two cold eyes
        slab([(-13, -7), (13, -7), (14.5, 2), (10, 9), (-10, 9), (-14.5, 2)], "visor", bevel=2.0, slope=1.1, prio=60,
             k=0, tilt=(0.0, 0.10)),
        limb(-13.5, -8, 13.5, -8, 1.3, 1.3, "darkmetal", prio=70, k=0),
        *with_glow([blob(-5.0, -0.5, 2.4, 1.5, "soul", op="add", depth=0.9),
                    blob(5.0, -0.5, 2.4, 1.5, "soul", op="add", depth=0.9)], eye),
    ]
    c = 0.5 + 0.5 * p.crack
    visor += [limb(-9, -5, -4, 0, 0.5, 0.5, "visor", op="carve"), limb(-4, 0, -6, 6 * c, 0.5, 0.4, "visor", op="carve"),
              limb(-4, 0, 2, 3 * c, 0.45, 0.35, "visor", op="carve"),
              *with_glow([limb(-8.6, -4.6, -4.2, -0.2, 0.55, 0.5, "soul", op="add", depth=0.6),
                          limb(-4.2, -0.2, -5.8, 5.5 * c, 0.5, 0.4, "soul", op="add", depth=0.5)], 0.85 * eye)]
    return xf(shell), xf(visor)


def _arm(j, side, s):
    sh, el, wr = j[f"{side}_shoulder"], j[f"{side}_elbow"], j[f"{side}_wrist"]
    return [
        limb(*sh, *el, 8.5, 7.5, "overalls", k=4),
        limb(*el, *wr, 7.5, 6.5, "overalls", k=4),
        # the forearm guard and the elbow cop: plated
        limb(el[0] + (wr[0] - el[0]) * 0.15, el[1] + (wr[1] - el[1]) * 0.15, *wr, 7.8, 6.8, "darkmetal", op="add", depth=1.6),
        blob(*el, 7.0, 6.0, "darkmetal", depth=5, k=2, prio=20),
        *hand(wr, j[f"{side}_hand_angle"], s, 9.0, "rubber", fingers=4, finger_length=9.0, finger_r=1.9,
              palm_r=4.6, spread=0.35, curl=0.85),
    ]


def _pauldron(j, side, s):
    """A domed riot pauldron, two lames under it - a separate layer so it reads IN FRONT of the arm."""
    x, y = j[f"{side}_shoulder"]
    return [
        blob(x + 3 * s, y - 1, 15.0, 11.5, "darkmetal", depth=10, k=3, angle=0.35 * s),
        blob(x + 6 * s, y + 9, 12.5, 5.5, "darkmetal", depth=6, k=1, angle=0.45 * s),
        limb(x - 8 * s, y + 5, x + 16 * s, y + 9, 0.8, 0.8, "darkmetal", op="carve"),
        blob(x + 10 * s, y - 4, 1.4, 1.4, "gunmetal", op="add", depth=1.0),
        blob(x - 4 * s, y - 7, 1.4, 1.4, "gunmetal", op="add", depth=1.0),
    ]


def _leg(j, side, s):
    hp, kn, an, to = j[f"{side}_hip"], j[f"{side}_knee"], j[f"{side}_ankle"], j[f"{side}_toe"]
    return [
        limb(*hp, *kn, 10.0, 8.0, "overalls", k=4),
        limb(*kn, *an, 8.0, 6.5, "overalls", k=4),
        blob(*kn, 7.5, 7.5, "darkmetal", depth=6, k=2, prio=20),                # knee guard
        limb(kn[0] + (an[0] - kn[0]) * 0.2, kn[1] + (an[1] - kn[1]) * 0.2, *an, 8.0, 6.8, "darkmetal", op="add", depth=1.6),
        limb(*an, *to, 6.8, 5.0, "rubber", k=3),
        blob(an[0] + s * 1.5, an[1] + 2, 7.5, 4.0, "rubber", depth=4, k=3),
    ]


def revenant_build(p):
    j = SKELETON.joints(p)
    px, py = j["pelvis"]
    cx, cy = j["chest"]
    wx, wy = j["waist"]
    body = [
        limb(cx, cy, wx, wy, 21.0, 16.0, "overalls", k=8),
        limb(wx, wy, px, py, 16.0, 15.0, "overalls", k=8),
        limb(*j["neck"], cx, cy, 8.0, 10.0, "overalls", k=5),
        limb(px - 12, py, px + 12, py, 11.0, 11.0, "overalls", k=6),
        *fold_lines(wx - 14, wy + 4, wx + 14, wy + 4, 4, 6, mat="overalls", seed=41, angle=0.1),
    ]
    plate = [slab([(cx - 18, cy - 6), (cx + 18, cy - 6), (cx + 21, cy + 14), (wx + 14, wy + 6),
                   (wx - 14, wy + 6), (cx - 21, cy + 14)], "darkmetal", bevel=3.0, op="add", tilt=(0.0, -0.05)),
             # a breach punched through the plate: the soul it runs on leaks out of it (R3: the wound it came back by)
             blob(cx + 9, wy - 6, 4.0, 5.0, "darkmetal", op="carve", depth=2.5),
             *with_glow([blob(cx + 9, wy - 6, 2.6, 3.4, "soul", op="add", depth=1.0)], 0.9 * p.eye_glow * (1 - p.dim)),
             limb(cx + 5, wy - 11, cx + 13, wy - 1, 0.5, 0.5, "darkmetal", op="carve")]
    plate += [limb(cx - 14, cy + 2, cx + 14, cy + 2, 0.7, 0.7, "gunmetal", op="carve"),
              # the belt with its pouches
              limb(px - 17, py - 6, px + 17, py - 6, 3.4, 3.4, "rubber", op="add", depth=2.0),
              blob(px - 10, py - 4, 4.0, 4.0, "rubber", op="add", depth=2.2), blob(px + 9, py - 4, 4.0, 4.0, "rubber", op="add", depth=2.2)]
    shell, visor = _helmet(j, p)
    # the shock baton in the right fist: a grip, a shaft, a conductive tip lit by the soul it runs on now
    wr = j["r_wrist"]
    import math
    a = j["r_hand_angle"]
    d = (math.sin(a), math.cos(a))
    grip = (wr[0] + d[0] * 7, wr[1] + d[1] * 7)
    # the baton runs through the fist, its tip angled up and outward (baton_angle turns it: the overhead smash)
    ba = a + 0.75 + p.baton_angle
    bdir = (math.sin(ba), -math.cos(ba))
    b0 = (grip[0] - bdir[0] * 9, grip[1] - bdir[1] * 9)
    b1 = (grip[0] + bdir[0] * 34, grip[1] + bdir[1] * 34)
    baton = [limb(*b0, *b1, 2.6, 2.4, "darkmetal", k=1, prio=50),
             limb(b0[0] + bdir[0] * 26, b0[1] + bdir[1] * 26, *b1, 2.9, 2.9, "gunmetal", k=1, prio=55),
             *with_glow([blob(b1[0], b1[1], 2.2, 2.2, "soul", op="add", depth=1.0)], 0.4 + 1.0 * p.baton)]
    layers = [
        _leg(j, "l", -1) + _leg(j, "r", 1),
        body + plate + shell,
        visor,
        _arm(j, "l", -1) + _pauldron(j, "l", -1),
        _arm(j, "r", 1) + _pauldron(j, "r", 1),
        baton,
    ]
    anchors = {
        "ground": (80.0 + p.bx, 232.0),
        "hit_centre": ((cx + px) / 2, (cy + py) / 2),
        "eye": j["head"],
        "mouth": b1,
    }
    stencils = []
    if p.tag > 0.5:
        stencils.append(("S-07", cx - 15.5, cy + 3.0, (214, 212, 204)))
    fig = Figure(layers, anchors, stencils)
    if p.fall > 0.005:     # DEATH: the body collapses toward the floor (and the viewer) about the ground line
        fig = fig.transformed(Xf.scale(1.0 + 0.06 * p.fall, 1.0 - 0.70 * p.fall, (80.0, 232.0)))
    return fig


REVENANT = Rig("revenant", revenant_build, REVENANT_POSE, seed=59)


# ---- clips: a soldier's drill it still remembers - square, alert, heavy; it barely flinches ------------------------
_REST = dict(torso=-0.04, chest=0.03, head=-0.20, l_sh=0.05, l_el=-0.75, l_wr=-0.15, r_sh=0.12, r_el=-0.40, r_wr=0.10,
             l_hip=0.12, r_hip=0.12, l_knee=-0.14, r_knee=-0.12, squat=0.14, by=0.0, bx=0.0, baton_angle=0.0,
             baton=0.0, eye_glow=1.0, l_arm_fore=0.0, r_arm_fore=0.0)
# the baton raised high: the arm straight up over the helmet, the baton laid back across the top of the head (angles
# solved numerically so the tip stays on the canvas; the swing up and down passes FORESHORTENED, toward the viewer,
# so the arm never sweeps out past the canvas edge - lesson 12.17)
_RAISED = dict(r_sh=2.85, r_el=0.1, r_wr=-0.2, r_arm_fore=0.0, baton_angle=0.75 - 2 * math.pi, torso=0.05, chest=0.04, head=-0.05, l_sh=0.25,
               l_el=-0.5, squat=0.20, by=2.0, baton=1.0, eye_glow=1.6)
# the smash landed: the arm down in front at the viewer, the baton forward, the body behind it
_SMASHED = dict(r_sh=0.0, r_el=-0.55, r_wr=0.2, r_arm_fore=0.45, baton_angle=-1.5, torso=-0.06, chest=-0.04, head=-0.25,
                l_sh=0.20, l_el=-0.3, squat=0.30, by=8.0, baton=1.4, eye_glow=1.9)

REVENANT_CLIPS = [
    Clip("IDLE", [         # an alert combat stance: weight shifting, the head scanning, the baton tip flicking
        (0.00, dict(torso=-0.04, head=-0.20, by=0.0, baton_angle=0.0, l_sh=0.05)),
        (0.45, dict(torso=-0.01, head=-0.06, by=1.0, baton_angle=0.10, l_sh=0.08)),
        (0.80, dict(torso=-0.06, head=-0.28, by=0.0, baton_angle=-0.05)),
        (0.95, dict(baton=0.6), "hold"), (1.05, dict(baton=0.0)),
    ], duration=1.2, mode="loop"),
]
REVENANT_CLIPS += [
    REVENANT_CLIPS[0].variant("DORMANT", speed=0.5, fps=6, eye_glow=0.2, head=0.25, squat=0.22, by=4.0, l_sh=0.0,
                              l_el=-0.2, r_sh=0.02, r_el=-0.1),
    Clip("ALERT", [        # the head snaps up, the baton comes up to guard, the eyes flare
        (0.00, dict(_REST, eye_glow=1.0)),
        (0.10, dict(head=0.05, by=-3.0, r_sh=0.30, r_el=-1.10, baton_angle=0.5, baton=1.2, eye_glow=2.0, l_sh=0.20), "out"),
        (0.25, dict(by=-1.0, eye_glow=1.5)),
        (0.40, dict(_REST, baton=0.3)),
    ], mode="once"),
    Clip("MOVE", [         # a heavy charging stride: knees high, the whole armoured mass rocking side to side
        (0.000, dict(l_hip=0.20, l_knee=-0.35, l_leg_fore=0.40, r_hip=0.10, r_knee=-0.08, r_leg_fore=0.0, torso=-0.08,
                     by=-3.0, bx=-2.0, l_sh=-0.05, r_sh=0.22, head=-0.12)),
        (0.083, dict(l_leg_fore=0.10, by=2.0, bx=0.0, torso=-0.03)),
        (0.167, dict(r_hip=0.20, r_knee=-0.33, r_leg_fore=0.40, l_hip=0.10, l_knee=-0.08, l_leg_fore=0.0, torso=0.02,
                     by=-3.0, bx=2.0, l_sh=0.15, r_sh=0.02, head=-0.25)),
        (0.250, dict(r_leg_fore=0.10, by=2.0, bx=0.0, torso=-0.02)),
    ], duration=0.333, mode="loop"),
    Clip("WIND_UP", [      # the lunge's telegraph: the baton raised high and crackling, crouched to spring - held
        (0.00, dict(_REST)),
        (0.15, dict(r_sh=1.10, r_el=-0.9, r_arm_fore=0.95, baton_angle=-1.1, by=1.0, baton=0.5), "linear"),
        (0.32, dict(r_sh=2.30, r_el=-0.5, r_arm_fore=0.80, baton_angle=-3.0, baton=0.8), "linear"),
        (0.45, dict(_RAISED), "inout"),
    ], mode="hold"),
    Clip("ATTACK", [       # the overhead smash
        (0.00, dict(_REST)),
        (0.04, dict(r_sh=1.6, r_el=-1.2, r_arm_fore=0.95, baton_angle=-0.6, by=1.0), "linear"),
        (0.08, dict(_RAISED, r_sh=2.45, r_el=-1.0, r_arm_fore=0.55), "out"),
        (0.11, dict(r_sh=1.40, r_el=-0.6, r_arm_fore=0.85, baton_angle=-1.8, by=4.0), "linear"),
        (0.14, dict(_SMASHED), "in"),
        (0.22, dict(_SMASHED, by=6.0, baton=0.8)),
        (0.30, dict(_REST)),
    ], fps=20, mode="once", events={0.14: "impact"}),
    Clip("HURT_LIGHT", [   # armour takes it: a twitch of the head, no step back
        (0.00, dict(head=-0.06, by=-1.0, eye_glow=0.6), "out"),
        (0.17, dict(head=-0.20, by=0.0, eye_glow=1.0)),
    ], mode="once"),
    Clip("HURT_HEAVY", [   # barely rocks: the torso turns with the blow, a half step, and it squares up again
        (0.00, dict(_REST)),
        (0.08, dict(by=-3.0, torso=-0.12, chest=-0.05, head=-0.40, l_sh=0.25, r_sh=0.18, r_el=-0.5, eye_glow=0.4,
                    crack=1.8), "out"),
        (0.25, dict(by=-1.0, torso=0.02, head=-0.10, eye_glow=1.3, crack=1.0)),
        (0.42, dict(_REST)),
    ], mode="once"),
    Clip("DEFEND", [       # both forearms up across the visor, crouched behind the pauldrons
        (0.00, dict(_REST, tag=1.0)),
        (0.33, dict(l_sh=-0.10, l_el=-2.30, l_wr=-0.3, r_sh=-0.05, r_el=-2.20, r_wr=-0.2, baton_angle=0.6, squat=0.35,
                    by=10.0, head=0.0, eye_glow=0.7)),
    ], mode="hold"),
    REVENANT_CLIPS[0].variant("WOUNDED", crack=2.0, torso=-0.10, squat=0.22, by=3.0, l_sh=0.18, l_el=-0.1, l_wr=0.0,
                              head=-0.35),
    Clip("DEATH", [        # drops to its knees, holds a beat, then goes forward onto its face
        (0.00, dict(_REST, fall=0.0, dim=0.0)),
        (0.10, dict(head=0.10, by=-2.0, eye_glow=1.8, r_sh=0.15, r_el=-0.5, baton_angle=-0.5), "out"),
        (0.35, dict(squat=1.0, by=36.0, l_hip=0.30, r_hip=0.28, l_knee=-0.6, r_knee=-0.6, l_sh=0.10, r_sh=0.15,
                    l_el=-0.1, r_el=-0.1, head=0.35, torso=0.0, baton_angle=-0.6), "in"),
        (0.55, dict(by=38.0, head=0.25)),
        (0.80, dict(fall=1.0, by=40.0, l_hip=0.12, r_hip=0.10, l_sh=-0.05, r_sh=0.10, head=0.5), "in"),
        (0.90, dict(fall=0.95), "out"),
        (1.00, dict(fall=1.0), "hold"),
        (0.30, dict(dim=0.0)), (0.85, dict(dim=1.0)),
    ], mode="hold"),
]
