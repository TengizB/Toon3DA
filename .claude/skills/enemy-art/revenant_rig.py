"""REVENANT rig + clips (UNDEAD, BRUISER, 1.05 walls, fast heavy melee + a telegraphed lunge). Imported by bestiary.py.

Organization security, still in the riot armour it died in: a full helmet whose single visor is cracked and dark
except for the cold soul light behind it, heavy pauldrons, a chest plate stencilled with its unit number, and a
tall RIOT SHIELD on the left arm (owner, 2026-10-22: no club; the shield replaced the baton). The right hand is a plain
armoured fist that is never articulated. No face: the helmet never comes off. Motion character: a soldier's drill it
still remembers - alert behind the shield, a heavy charging stride, and the SHIELD BASH: the whole body driving one
rigid plate at you. A rigid plate moved with the body has no fingers, wrist or handle to go wrong.
"""
import math

from kit import blob, limb, chain, slab, chamfer_rect, riveted_plate, fold_lines, Humanoid, with_glow, Xf, Figure, Rig, Clip

# the shield's rest centre on the canvas (left of the body, held at the forearm), its half width / half height
SHIELD_REST = (48.0, 146.0)
SHIELD_HALF = (20.0, 37.0)

SKELETON = Humanoid(pelvis=(80.0, 134.0), spine=56.0, neck=6.0, head=15.0, shoulder_half=25.0, hip_half=12.0,
                    upper_arm=34.0, fore_arm=31.0, hand=10.0, thigh=46.0, shin=46.0, foot=10.0,
                    arm_rest=0.22, leg_rest=0.13)

REVENANT_POSE = Humanoid.params(
    torso=-0.04, chest=0.03, head=-0.20,
    l_sh=0.15, l_el=-0.50, l_wr=-0.15,                 # the left hand at the shield's grip, behind its centre
    r_sh=0.12, r_el=-0.40, r_wr=0.10,                  # the right fist hanging, ready
    l_hip=0.12, r_hip=0.12, l_knee=-0.14, r_knee=-0.12, squat=0.14,
)
REVENANT_POSE.update(
    shield_x=0.0, shield_y=0.0,                    # the shield's offset from its rest centre (px)
    shield_scale=1.0,                              # > 1: driven toward the viewer (the bash), < 1: drawn back
    shield_tilt=0.0,                               # its roll (radians, + clockwise on screen)
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
        *_fist(wr, j[f"{side}_hand_angle"], s),
    ]


def _fist(wrist, angle, side):
    """An armoured riot GAUNTLET, closed: one solid rounded fist laid along the hand's direction, a knuckle guard
    across its far end, the thumb wrapped over on the inner side. Never separate finger chains - curled fingers on
    a turned wrist broke into lumps that read as a deformed hand (owner, 2026-10-22)."""
    dx, dy = side * math.sin(angle), math.cos(angle)                 # along the hand
    nx, ny = -dy * side, dx * side                                   # across it, toward the thumb (inner) side
    cx, cy = wrist[0] + dx * 6.5, wrist[1] + dy * 6.5
    along = math.atan2(dy, dx)
    return [
        limb(*wrist, cx, cy, 6.2, 6.0, "rubber", k=2),                                   # the gauntlet's cuff into the fist
        blob(cx, cy, 6.6, 5.6, "rubber", depth=5.2, angle=along, k=2),                   # the closed fist
        limb(cx + dx * 4.2 - nx * 4.6, cy + dy * 4.2 - ny * 4.6, cx + dx * 4.2 + nx * 4.6, cy + dy * 4.2 + ny * 4.6,
             1.8, 1.8, "darkmetal", op="add", depth=1.4),                                # the knuckle guard
        blob(cx + nx * 4.4 - dx * 0.6, cy + ny * 4.4 - dy * 0.6, 2.6, 2.2, "rubber", op="add", depth=1.4,
             angle=along),                                                               # the thumb over the grip
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


def _shield(p):
    """The RIOT SHIELD: a tall chamfered plate of smoked polycarbonate (the visor glass) in a gunmetal rim, a brace
    across its middle, old impact scars, and the Organization's RIOT stencil. Built about its own centre and moved as
    ONE rigid part: offset, scale toward the viewer, roll. -> (shapes, centre)."""
    hw, hh = SHIELD_HALF
    centre = (SHIELD_REST[0] + p.shield_x, SHIELD_REST[1] + p.shield_y)
    xf = Xf.scale(p.shield_scale, p.shield_scale).then(Xf.turn(p.shield_tilt)).then(Xf.move(*centre))
    plate = [
        slab(chamfer_rect(-hw, -hh, hw, hh, 5.0), "visor", bevel=2.6, slope=1.0, prio=40, k=0, tilt=(0.10, 0.0)),
        # the rim: four gunmetal edges, chamfered corners
        *[limb(*a, *b, 1.6, 1.6, "gunmetal", op="add", depth=1.4) for a, b in
          zip(chamfer_rect(-hw + 1, -hh + 1, hw - 1, hh - 1, 4.5), chamfer_rect(-hw + 1, -hh + 1, hw - 1, hh - 1, 4.5)[1:] +
              chamfer_rect(-hw + 1, -hh + 1, hw - 1, hh - 1, 4.5)[:1])],
        limb(-hw + 3, 4, hw - 3, 4, 1.4, 1.4, "gunmetal", op="add", depth=1.0),          # the brace
        blob(0, 4, 3.0, 2.4, "darkmetal", op="add", depth=1.4),                          # its boss
        # old impact scars in the polycarbonate
        limb(-9, -20, -3, -12, 0.45, 0.4, "visor", op="carve"), limb(-3, -12, -7, -5, 0.4, 0.35, "visor", op="carve"),
        limb(6, 14, 11, 22, 0.45, 0.4, "visor", op="carve"),
    ]
    return xf(plate), centre, xf


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
    shield, shield_centre, shield_xf = _shield(p)
    layers = [
        _leg(j, "l", -1) + _leg(j, "r", 1),
        body + plate + shell,
        visor,
        _arm(j, "r", 1) + _pauldron(j, "r", 1),
        _arm(j, "l", -1) + _pauldron(j, "l", -1),
        shield,                                    # in front of everything: the shield is held out before the body
    ]
    anchors = {
        "ground": (80.0 + p.bx, 232.0),
        "hit_centre": ((cx + px) / 2, (cy + py) / 2),
        "eye": j["head"],
        "mouth": shield_centre,                  # the emitter: the shield's face (the bash lands there)
    }
    stencils = []
    if p.tag > 0.5 and p.shield_x < 12.0:        # the chest stencil only while the shield is not over the chest (12.12)
        stencils.append(("S-07", cx - 15.5 + 16, cy + 3.0, (214, 212, 204)))
    if p.tag > 0.5:
        if p.shield_scale > 0.9:
            sx, sy = shield_xf.pt((-7.5, -28.0))
            stencils.append(("RIOT", sx, sy, (206, 204, 196)))
    fig = Figure(layers, anchors, stencils)
    if p.fall > 0.005:     # DEATH: the body collapses toward the floor (and the viewer) about the ground line
        fig = fig.transformed(Xf.scale(1.0 + 0.06 * p.fall, 1.0 - 0.70 * p.fall, (80.0, 232.0)))
    return fig


REVENANT = Rig("revenant", revenant_build, REVENANT_POSE, seed=59)


# ---- clips: a soldier's drill it still remembers - square, alert behind the shield, heavy; it barely flinches ------
_REST = dict(torso=-0.04, chest=0.03, head=-0.20, l_sh=0.15, l_el=-0.50, l_wr=-0.15, r_sh=0.12, r_el=-0.40, r_wr=0.10,
             l_hip=0.12, r_hip=0.12, l_knee=-0.14, r_knee=-0.12, squat=0.14, by=0.0, bx=0.0, shield_x=0.0, shield_y=0.0,
             shield_scale=1.0, shield_tilt=0.0, eye_glow=1.0, l_arm_fore=0.0, r_arm_fore=0.0)
# THE SHIELD BASH (owner, 2026-10-22: no club; the old baton hand deformed). The attack is ONE rigid plate moved by
# the body - no hand is articulated in view, so no frame can deform it:
#   braced   - crouched behind the shield, brought to the centre, its rim at the chin: only the eyes over it
#   drawn    - the shield pulled back a hand's width (smaller), the body coiled
#   slammed  - the shield driven at the viewer (bigger), the body lunging in behind it, the impact frame
_BRACED = dict(shield_x=28.0, shield_y=-24.0, shield_scale=1.05, shield_tilt=0.0, squat=0.30, by=8.0, torso=0.0,
               chest=0.02, head=0.0, l_sh=0.05, l_el=-1.75, r_sh=0.25, r_el=-0.90, eye_glow=1.6)
_DRAWN = dict(_BRACED, shield_x=26.0, shield_y=-20.0, shield_scale=0.92, by=4.0, squat=0.22, torso=-0.03, head=-0.08,
              l_sh=0.05, l_el=-1.45,
              eye_glow=1.8)
_SLAMMED = dict(_BRACED, shield_x=30.0, shield_y=-16.0, shield_scale=1.34, shield_tilt=0.04, by=12.0, squat=0.36,
                l_sh=-0.05, l_el=-1.55,
                torso=0.03, chest=0.04, head=0.08, eye_glow=2.0)

REVENANT_CLIPS = [
    Clip("IDLE", [         # an alert stance behind the shield: weight shifting, the head scanning, the shield settling
        (0.00, dict(torso=-0.04, head=-0.20, by=0.0, shield_y=0.0, shield_tilt=0.0)),
        (0.45, dict(torso=-0.01, head=-0.06, by=1.0, shield_y=1.5, shield_tilt=0.02)),
        (0.80, dict(torso=-0.06, head=-0.28, by=0.0, shield_y=0.5, shield_tilt=-0.02)),
    ], duration=1.2, mode="loop"),
]
REVENANT_CLIPS += [
    # DORMANT: the shield sagging off the forearm, the head down
    REVENANT_CLIPS[0].variant("DORMANT", speed=0.5, fps=6, eye_glow=0.2, head=0.25, squat=0.22, by=4.0, shield_y=10.0,
                              shield_tilt=-0.18, r_sh=0.02, r_el=-0.1),
    Clip("ALERT", [        # the head snaps up and the shield comes up to guard, the eyes flare
        (0.00, dict(_REST, eye_glow=1.0)),
        (0.10, dict(head=0.05, by=-3.0, shield_x=10.0, shield_y=-12.0, shield_tilt=0.05, l_sh=0.15, l_el=-0.90,
                    eye_glow=2.0), "out"),
        (0.25, dict(by=-1.0, shield_y=-6.0, eye_glow=1.5)),
        (0.40, dict(_REST)),
    ], mode="once"),
    Clip("MOVE", [         # a heavy charging stride: knees high, the armoured mass rocking, the shield riding it
        (0.000, dict(l_hip=0.20, l_knee=-0.35, l_leg_fore=0.40, r_hip=0.10, r_knee=-0.08, r_leg_fore=0.0, torso=-0.08,
                     by=-3.0, bx=-2.0, r_sh=0.22, head=-0.12, shield_x=-2.0, shield_y=-2.0, shield_tilt=-0.03)),
        (0.083, dict(l_leg_fore=0.10, by=2.0, bx=0.0, torso=-0.03, shield_y=2.0)),
        (0.167, dict(r_hip=0.20, r_knee=-0.33, r_leg_fore=0.40, l_hip=0.10, l_knee=-0.08, l_leg_fore=0.0, torso=0.02,
                     by=-3.0, bx=2.0, r_sh=0.02, head=-0.25, shield_x=2.0, shield_y=-2.0, shield_tilt=0.03)),
        (0.250, dict(r_leg_fore=0.10, by=2.0, bx=0.0, torso=-0.02, shield_y=2.0)),
    ], duration=0.333, mode="loop"),
    Clip("WIND_UP", [      # the lunge's telegraph: it crouches behind the raised shield, eyes over the rim - held
        (0.00, dict(_REST)),
        (0.25, dict(_BRACED, shield_scale=1.0, by=5.0), "inout"),
        (0.45, dict(_BRACED), "inout"),
    ], mode="hold"),
    Clip("ATTACK", [       # the SHIELD BASH: draw back, drive the plate at the viewer, recover
        (0.00, dict(_REST)),
        (0.08, dict(_DRAWN), "out"),
        (0.14, dict(_SLAMMED), "in"),
        (0.22, dict(_SLAMMED, shield_scale=1.28, by=10.0)),
        (0.30, dict(_REST)),
    ], fps=20, mode="once", events={0.14: "impact"}),
    Clip("HURT_LIGHT", [   # armour takes it: the shield jolts, the head twitches, no step back
        (0.00, dict(head=-0.06, by=-1.0, shield_x=-3.0, shield_tilt=-0.06, eye_glow=0.6), "out"),
        (0.17, dict(head=-0.20, by=0.0, shield_x=0.0, shield_tilt=0.0, eye_glow=1.0)),
    ], mode="once"),
    Clip("HURT_HEAVY", [   # the shield is knocked aside, the torso turns with the blow, then it squares up again
        (0.00, dict(_REST)),
        (0.08, dict(by=-3.0, torso=-0.12, chest=-0.05, head=-0.40, r_sh=0.18, r_el=-0.5, shield_x=-8.0, shield_y=-4.0,
                    shield_tilt=-0.22, eye_glow=0.4, crack=1.8), "out"),
        (0.25, dict(by=-1.0, torso=0.02, head=-0.10, shield_x=-2.0, shield_tilt=-0.05, eye_glow=1.3, crack=1.0)),
        (0.42, dict(_REST)),
    ], mode="once"),
    Clip("DEFEND", [       # the shield planted square in front, crouched low behind it
        (0.00, dict(_REST, tag=1.0)),
        (0.33, dict(shield_x=29.0, shield_y=-10.0, shield_scale=1.08, shield_tilt=0.0, squat=0.35, by=10.0, head=0.10,
                    l_sh=-0.10, l_el=-1.20, r_sh=0.25, r_el=-0.90, eye_glow=0.7)),
    ], mode="hold"),
    # WOUNDED: the visor crack widens, the shield arm sags, it lists
    REVENANT_CLIPS[0].variant("WOUNDED", crack=2.0, torso=-0.10, squat=0.22, by=3.0, head=-0.35, shield_y=9.0,
                              shield_tilt=-0.20),
    Clip("DEATH", [        # drops to its knees, the shield slides off the arm to the floor, then it goes forward
        (0.00, dict(_REST, fall=0.0, dim=0.0)),
        (0.10, dict(head=0.10, by=-2.0, eye_glow=1.8, shield_y=-4.0), "out"),
        (0.35, dict(squat=1.0, by=36.0, l_hip=0.30, r_hip=0.28, l_knee=-0.6, r_knee=-0.6, r_sh=0.15, r_el=-0.1,
                    head=0.35, torso=0.0, shield_x=-6.0, shield_y=40.0, shield_tilt=-0.35), "in"),
        (0.55, dict(by=38.0, head=0.25, shield_y=46.0, shield_tilt=-0.55)),
        (0.80, dict(fall=1.0, by=40.0, l_hip=0.12, r_hip=0.10, r_sh=0.10, head=0.5, shield_y=48.0, shield_tilt=-0.6), "in"),
        (0.90, dict(fall=0.95), "out"),
        (1.00, dict(fall=1.0), "hold"),
        (0.30, dict(dim=0.0)), (0.85, dict(dim=1.0)),
    ], mode="hold"),
]
