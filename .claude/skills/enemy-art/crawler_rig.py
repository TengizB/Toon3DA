"""CRAWLER rig + clips (UNDEAD, CHAFF, 0.55 walls, fast scuttler). Imported by bestiary.py.

A lab technician who went down on all fours and came back up that way: seen from the front, the head thrust out at
you, the spine arched high behind it, long arms splayed wide and planted, the knees cocked out like a spider's, a
torn lab coat dragging off its back. Sealed (owner, 2026-10-22): a clean-room hood and a full respirator with two
round lenses - soul-white - and no face. Story detail: the coat's breast pocket with its pens, a stencilled lab
bay number on the hood. Same HUMANOID skeleton as every undead, hung upside down: a NEGATIVE spine puts the chest
in front of and below the pelvis, which is exactly a body on all fours seen head-on.
"""
from kit import lag, blob, limb, chain, slab, fold_lines, hand, Humanoid, with_glow, Xf, Figure, Rig, Clip

NARROW = 0.90

SKELETON = Humanoid(pelvis=(80.0, 146.0), spine=-30.0, neck=-6.0, head=-12.0, shoulder_half=17.0, hip_half=13.0,
                    upper_arm=40.0, fore_arm=58.0, hand=9.0, thigh=40.0, shin=82.0, foot=8.0,
                    arm_rest=2.0, leg_rest=1.9)

CRAWLER_POSE = Humanoid.params(
    head=0.06,
    l_el=-1.82, r_el=-1.78, l_wr=0.10, r_wr=0.10,        # elbows cocked up and out, forearms down to the floor
    l_hip=0.04, r_hip=0.0, l_knee=-2.06, r_knee=-2.02,   # knees out wide and high, shins folded back down
)
CRAWLER_POSE.update(
    arch=0.0,                                     # the spine's arch (+ higher hump)
    coat_lag=0.0,                                 # the coat tails lag behind the body (secondary motion)
    l_curl=0.35, r_curl=0.35,
    eye_glow=1.0, dim=0.0,
    flat=0.0,                                     # DEATH: it skids flat (0..1)
    tag=1.0,
)


def _head(j, p):
    hx, hy = j["head"]
    # the head is the nearest thing to the camera: drawn larger than the body behind it
    xf = Xf.scale(1.28, 1.28).then(Xf.turn(j["head_angle"] * 0.4, (0, 0))).then(Xf.move(hx, hy))
    eye = p.eye_glow * (1 - p.dim)
    hood = [
        blob(0, -2, 15.0, 14.0, "labcoat", depth=12, k=5),                    # the clean-room hood, thrust at you
        blob(0, -11, 11.0, 6.0, "labcoat", depth=6, k=6),
        *fold_lines(-12, -2, -9, 10, 3, 5, mat="labcoat", seed=7, angle=0.5),
        *fold_lines(12, -2, 9, 10, 3, 5, mat="labcoat", seed=8, angle=-0.5, side=-1),
    ]
    mask = [
        blob(0, 2, 11.0, 10.0, "rubber", depth=7, k=3),                        # the respirator face-piece
        # two round lenses (this one has two; only the Ghoul's mask has one panoramic visor)
        blob(-5.2, -0.5, 3.8, 3.8, "gunmetal", depth=2.6, prio=50, k=0),
        blob(5.2, -0.5, 3.8, 3.8, "gunmetal", depth=2.6, prio=50, k=0),
        blob(-5.2, -0.5, 2.8, 2.8, "visor", op="add", depth=0.6),
        blob(5.2, -0.5, 2.8, 2.8, "visor", op="add", depth=0.6),
        *with_glow([blob(-5.2, -0.3, 1.7, 1.7, "soul", op="add", depth=0.8),
                    blob(5.2, -0.3, 1.7, 1.7, "soul", op="add", depth=0.8)], eye),
        # twin filter cartridges, low on the cheeks
        blob(-6.5, 8.5, 3.6, 3.2, "gunmetal", depth=3, prio=40, k=1),
        blob(6.5, 8.5, 3.6, 3.2, "gunmetal", depth=3, prio=40, k=1),
        blob(0, 7.5, 2.4, 2.0, "rubber", op="add", depth=1.4),
    ]
    return xf(hood), xf(mask)


def _arm(j, p, side, s):
    sh, el, wr = j[f"{side}_shoulder"], j[f"{side}_elbow"], j[f"{side}_wrist"]
    return [
        limb(*sh, *el, 5.0, 4.2, "labcoat", k=4),
        limb(*el, *wr, 4.2, 3.2, "labcoat", k=4),
        blob(*el, 4.4, 4.0, "labcoat", depth=4, k=3),
        *fold_lines(el[0] - 3, el[1] - 3, el[0] + 3, el[1] + 3, 3, 5, mat="labcoat", seed=13 + s, angle=0.4),
        # the coat's torn cuff, a strip of grey wrist, the latex glove
        limb(wr[0] + (el[0] - wr[0]) * 0.22, wr[1] + (el[1] - wr[1]) * 0.22, *wr, 3.1, 2.8, "deadskin", op="add", depth=0.8),
        *hand(wr, j[f"{side}_hand_angle"], s, 9.0, "rubber", fingers=4, finger_length=15.0, finger_r=1.15,
              palm_r=3.0, spread=0.9, curl=p[f"{side}_curl"]),
    ]


def _leg(j, side, s):
    hp, kn, an, to = j[f"{side}_hip"], j[f"{side}_knee"], j[f"{side}_ankle"], j[f"{side}_toe"]
    return [
        limb(*hp, *kn, 7.0, 5.5, "overalls", k=4),
        limb(*kn, *an, 5.5, 4.0, "overalls", k=4),
        blob(*kn, 5.5, 5.5, "overalls", depth=5, k=3),
        limb(*an, *to, 4.2, 3.4, "rubber", k=3),
        blob(an[0], an[1] + 1.5, 4.8, 3.0, "rubber", depth=3, k=3),
    ]


def crawler_build(p):
    j = SKELETON.joints(p)
    px, py = j["pelvis"]
    cx, cy = j["chest"]
    wx, wy = j["waist"]
    hump = 30 + 8 * p.arch
    flat = p.flat
    back = [
        # the shoulders and the coat's collar around the hood
        blob(*j["l_shoulder"], 9.0, 8.0, "labcoat", depth=8, k=8),
        blob(*j["r_shoulder"], 9.0, 8.0, "labcoat", depth=8, k=8),
        blob(cx, cy - 2, 17.0, 11.0, "labcoat", depth=11, k=9),
        # the upper back climbing away from the camera to the high hump, and the pelvis behind it
        blob(wx, wy - hump * 0.55, 26.0, 21.0, "labcoat", depth=18, k=10),
        blob(wx, wy - hump, 19.0, 11.0, "labcoat", depth=12, k=10),
        blob(px, py - hump * 0.35, 20.0, 12.0, "labcoat", depth=12, k=10),
        # shoulder blades jutting either side of the spine
        blob(cx - 13, cy - 17, 8.0, 6.0, "labcoat", op="add", depth=4.0, angle=0.6),
        blob(cx + 13, cy - 17, 8.0, 6.0, "labcoat", op="add", depth=4.0, angle=-0.6),
        # the coat split down the spine: grey skin, the vertebrae, and the soul light it came back by (R3)
        limb(cx, cy - 12, wx, wy - hump - 6, 3.4, 2.6, "labcoat", op="carve"),
        *[blob(cx + (wx - cx) * t, cy - 12 + (wy - hump - 6 - (cy - 12)) * t, 2.6, 1.9, "deadskin", op="add", depth=2.4)
          for t in (0.05, 0.25, 0.45, 0.65, 0.85)],
        *with_glow([limb(cx - 1.6, cy - 13, wx - 1.4, wy - hump - 4, 0.8, 0.6, "soul", op="add", depth=0.6)],
                   0.8 * p.eye_glow * (1 - p.dim)),
        *fold_lines(cx - 16, cy - 4, wx - 12, wy - hump, 3, 8, mat="labcoat", seed=17, angle=1.0),
        *fold_lines(cx + 16, cy - 4, wx + 12, wy - hump, 3, 8, mat="labcoat", seed=18, angle=-1.0, side=-1),
        # the breast pocket (seen hanging under the collar) with two pens
        slab([(cx - 15, cy + 2), (cx - 7, cy + 2), (cx - 7, cy + 8), (cx - 15, cy + 8)], "labcoat", bevel=0.8,
             op="add", slope=0.6),
        limb(cx - 13, cy - 1, cx - 13, cy + 4, 0.9, 0.9, "gunmetal", op="add", depth=0.8),
        limb(cx - 10.5, cy - 1.5, cx - 10.5, cy + 4, 0.9, 0.9, "darkmetal", op="add", depth=0.8),
    ]
    # the coat tails: torn panels hanging off both flanks and dragging toward the floor, lagging the body
    lag = 4.0 * p.coat_lag
    tails = []
    for s in (-1, 1):
        x0 = px + s * 18
        tails.append(slab([(x0 - s * 4, py - 12), (x0 + s * 10, py - 4), (x0 + s * (16 + lag), py + 40 - 18 * flat),
                           (x0 + s * (9 + lag), py + 50 - 22 * flat), (x0 + s * 4, py + 44 - 20 * flat),
                           (x0 - s * 2 + lag * 0.5, py + 54 - 24 * flat), (x0 - s * 8, py + 28)],
                          "labcoat", bevel=2.2, slope=0.8, k=2))
        tails += fold_lines(x0, py - 6, x0 + s * 9, py + 40, 3, 12, mat="labcoat", seed=19 + s, angle=0.15)
    hood, mask = _head(j, p)
    root = Xf.move(0, 0) if flat < 0.01 else Xf.scale(1.0 + 0.05 * flat, 1.0 - 0.50 * flat, (80, 232))
    layers = [
        root(_leg(j, "l", -1) + _leg(j, "r", 1) + tails),
        root(back),
        root(_arm(j, p, "l", -1) + _arm(j, p, "r", 1)),
        root(hood),
        root(mask),
    ]
    anchors = {
        "ground": (80.0 + p.bx, 232.0),
        "hit_centre": root.pt((cx, cy - 4)),
        "eye": root.pt(j["head"]),
        "mouth": root.pt((j["head"][0], j["head"][1] + 8)),
    }
    stencils = []
    if p.tag > 0.5 and flat < 0.3:
        hx, hy = root.pt(j["head"])
        stencils.append(("L3", hx - 3.5, hy - 14.0, (40, 44, 52)))
    # NARROW (lesson 12.20): the splayed limbs span the whole canvas at rest, so the figure is narrowed about the centre
    # line and lifted a hair off the ground line - every clip then has air to move in
    return Figure(layers, anchors, stencils).transformed(Xf.scale(NARROW, 1.0, (80.0, 232.0)).then(Xf.move(0.0, -2.0)))


CRAWLER = Rig("crawler", crawler_build, CRAWLER_POSE, seed=61)


# ---- clips (R4: it moves WRONG - twitchy, sudden, a spider's rhythm in a person's body) ------------------------------
_REST = dict(head=0.06, l_el=-1.82, r_el=-1.78, l_wr=0.10, r_wr=0.10, l_hip=0.04, r_hip=0.0, l_knee=-2.06,
             r_knee=-2.02, l_sh=0.0, r_sh=0.0, by=0.0, bx=0.0, arch=0.0, l_curl=0.35, r_curl=0.35, eye_glow=1.0)
_REARED = dict(arch=1.0, by=-8.0, l_sh=0.18, r_sh=0.20, l_el=-2.05, r_el=-2.05, head=-0.10, l_curl=0.0, r_curl=0.0,
               eye_glow=1.7)

_IDLE = Clip("IDLE", [
    (0.00, dict(arch=0.0, by=0.0, head=0.06, l_curl=0.35, r_curl=0.35)),
    (0.30, dict(arch=0.4, by=-1.0)),
    (0.42, dict(head=-0.25), "hold"), (0.50, dict(head=0.06)),            # a twitch of the head: sudden, then still
    (0.62, dict(l_curl=0.8), "hold"), (0.70, dict(l_curl=0.35)),          # fingers drum on the floor
    (0.78, dict(r_curl=0.8), "hold"), (0.86, dict(r_curl=0.35)),
    (0.65, dict(arch=0.0, by=0.0)),
], duration=1.0, mode="loop")
IDLE = Clip("IDLE", _IDLE.keys + [(t, {"coat_lag": d["coat_lag"] * 1.5}, e)
                                  for t, d, e in lag(_IDLE, "arch", 0.12, 1.0, out_param="coat_lag", fps=12)],
            duration=1.0, mode="loop")

CRAWLER_CLIPS = [
    IDLE,
    IDLE.variant("DORMANT", speed=0.5, fps=6, eye_glow=0.2, by=2.0, arch=-0.6),
    Clip("ALERT", [        # it snaps up onto its hands, the back arching, the lenses flaring
        (0.00, dict(_REST)),
        (0.10, dict(by=-9.0, arch=1.2, head=-0.20, eye_glow=2.0, l_curl=0.0, r_curl=0.0, coat_lag=-1.0), "out"),
        (0.25, dict(by=-4.0, arch=0.6, coat_lag=1.0)),
        (0.40, dict(_REST, coat_lag=0.0)),
    ], mode="once"),
    Clip("MOVE", [         # the scuttle: diagonal pairs of limbs lift together, the body jitters side to side
        (0.000, dict(l_sh=0.30, l_el=-2.10, r_hip=0.25, r_knee=-2.30, r_sh=0.0, r_el=-1.78, l_hip=0.04, l_knee=-2.06,
                     bx=-3.0, by=-2.0, coat_lag=1.0)),
        (0.083, dict(l_sh=0.0, l_el=-1.82, r_hip=0.0, r_knee=-2.02, bx=0.0, by=1.0)),
        (0.167, dict(r_sh=0.30, r_el=-2.10, l_hip=0.25, l_knee=-2.30, l_sh=0.0, l_el=-1.82, r_hip=0.0, r_knee=-2.02,
                     bx=3.0, by=-2.0, coat_lag=-1.0)),
        (0.250, dict(r_sh=0.0, r_el=-1.78, l_hip=0.04, l_knee=-2.06, bx=0.0, by=1.0)),
    ], duration=0.333, mode="loop"),
    Clip("WIND_UP", [      # it rears onto its haunches, both clawed hands up - held
        (0.00, dict(_REST)),
        (0.30, dict(_REARED), "inout"),
    ], mode="hold"),
    Clip("ATTACK", [       # the leap-swipe: off the floor at you, one long arm raking across
        (0.00, dict(_REST)),
        (0.08, dict(_REARED, by=-16.0, arch=1.3), "out"),
        (0.14, dict(by=-6.0, arch=0.4, r_sh=-0.55, r_el=-1.55, r_wr=-0.3, r_arm_fore=0.35, r_curl=0.9, l_sh=0.10, l_el=-1.9,
                    head=0.10, eye_glow=2.0), "in"),
        (0.22, dict(by=0.0, arch=0.0, r_sh=-0.30, r_el=-1.70, r_arm_fore=0.25, coat_lag=1.2)),
        (0.30, dict(_REST, coat_lag=0.0)),
    ], fps=20, mode="once", events={0.14: "impact"}),
    Clip("HURT_LIGHT", [
        (0.00, dict(by=-3.0, head=-0.30, eye_glow=0.5, arch=0.5), "out"),
        (0.17, dict(by=0.0, head=0.06, eye_glow=1.0, arch=0.0)),
    ], mode="once"),
    Clip("HURT_HEAVY", [   # knocked flat for a moment, limbs splaying, then it scrabbles back up
        (0.00, dict(_REST)),
        (0.08, dict(flat=0.35, l_sh=0.15, r_sh=0.15, arch=-0.6, head=0.30, eye_glow=0.4, coat_lag=-1.2), "out"),
        (0.25, dict(flat=0.10, by=-4.0, arch=0.6, head=-0.10, eye_glow=1.3, coat_lag=1.0)),
        (0.42, dict(_REST, flat=0.0, coat_lag=0.0)),
    ], mode="once"),
    Clip("DEFEND", [       # it folds in on itself: arms drawn under, head tucked, the hump up like a shell
        (0.00, dict(_REST, tag=1.0)),
        (0.33, dict(l_sh=-0.45, r_sh=-0.45, l_el=-1.55, r_el=-1.55, l_arm_fore=0.35, r_arm_fore=0.35, l_hip=0.0, r_hip=0.0, l_knee=-2.25, r_knee=-2.25, arch=1.4, by=-2.0,
                    head=0.20, eye_glow=0.6, l_curl=1.0, r_curl=1.0)),
    ], mode="hold"),
    # WOUNDED: the right arm drags, half folded under it; the twitch is worse
    IDLE.variant("WOUNDED", r_sh=-0.35, r_el=-1.95, r_arm_fore=0.30, r_curl=0.9, by=1.0, arch=-0.3),
    Clip("DEATH", [        # it skids flat and splays, then lies still
        (0.00, dict(_REST, flat=0.0, dim=0.0)),
        (0.10, dict(by=-6.0, arch=1.0, head=-0.30, eye_glow=1.9, coat_lag=-1.0), "out"),
        (0.35, dict(flat=0.85, by=4.0, arch=-0.5, l_sh=0.0, r_sh=0.0, l_el=-1.95, r_el=-1.95, head=0.25,
                    coat_lag=1.2), "in"),
        (0.50, dict(flat=0.75, by=2.0), "out"),
        (0.70, dict(flat=1.0, by=6.0, l_curl=0.7, r_curl=0.6, coat_lag=0.4), "in"),
        (0.90, dict(flat=1.0, coat_lag=0.0), "hold"),
        (0.25, dict(dim=0.0)), (0.75, dict(dim=1.0)),
    ], mode="hold"),
]
