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
        for i in range(5):
            t = i / 4
            ox = curl * math.sin(t * math.pi) * (1 - 0.6 * p.limp) + swing * 30 * t * t + gather * t
            pts.append((x + ox, 146 + length * t * (1 + 0.08 * p.limp)))
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

TYRANT_CLIPS = []
