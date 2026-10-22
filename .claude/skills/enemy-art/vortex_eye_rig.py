"""VORTEX EYE rig + clips (UNDEAD, CHAFF, 0.55 walls, short-range caster, DEBUFF_PLAYER). Imported by bestiary.py.

What is left of a researcher: the head and shoulders, still sealed in the lab's white isolation helmet, held up off
the floor by a slow vortex of soul light that pours out of the helmet's two eye ports and winds down beneath it.
No body below the torn coat, no face behind the glass - only the light. The renderer does NOT hover VORTEX_EYE, so
the hover is baked into the canvas (empty rows under the vortex). Story detail: the helmet's research stencil.
Uses the same HUMANOID skeleton, only its neck and head (the bust is hung on chest / neck / head).
"""
import math

from kit import blob, limb, chain, slab, fold_lines, Humanoid, with_glow, Xf, Figure, Rig, Clip

SKELETON = Humanoid(pelvis=(80.0, 158.0), spine=60.0, neck=8.0, head=19.0, shoulder_half=21.0)

VORTEX_POSE = Humanoid.params(head=0.10)
VORTEX_POSE.update(
    bob=0.0,                                      # the bust bobbing on the vortex (px, + down)
    spin=0.0,                                     # vortex phase (0..1 = one turn)
    tight=0.0,                                    # the vortex tightens (DEBUFF / ATTACK telegraph, 0..1)
    lash=0.0,                                     # the vortex lashes forward at the player (0..1)
    pour=1.0,                                     # the soul pouring from the eye ports (0 none .. 1 rest .. 2 flare)
    unravel=0.0,                                  # DEATH: the vortex unravels and the bust drops (0..1)
    eye_glow=1.0, dim=0.0,
    tag=1.0,
)

STRANDS = 3


def _vortex(p, top_y, front):
    """Three soul strands winding down a tapering helix from the torn coat. front=True returns the half of every turn
    nearest the viewer (drawn over the bust's hem), else the far half. Thin strands: a vortex of glow kills form."""
    shapes = []
    level = p.pour * (1 - p.dim) * (1 - 0.8 * p.unravel)
    if level <= 0.02:
        return shapes
    length = 62.0 * (1 - 0.30 * p.tight) * (1 - 0.6 * p.unravel)
    turns = 1.15 + 0.6 * p.tight
    for strand in range(STRANDS):
        span = (1.0, 0.82, 0.66)[strand]                        # uneven strands: a vortex, not a braid
        pts, near = [], []
        for i in range(19):
            t = i / 18 * span
            angle = 2 * math.pi * (turns * t + p.spin + strand / STRANDS + 0.07 * strand * strand)
            radius = (25.0 * (1 - t) ** 1.35 + 2.0) * (1 - 0.45 * p.tight) * (1 + 0.9 * p.unravel * t)
            x = 80 + radius * math.cos(angle) + p.unravel * 26 * t * math.cos(strand * 2.1)
            y = top_y + length * t + radius * 0.20 * math.sin(angle) - p.lash * 10 * (1 - t)
            pts.append((x, y)); near.append(math.sin(angle) > 0)
        for i in range(18):
            if near[i] != front or (i + 2 * strand) % 6 == 5:      # torn: a gap every few segments
                continue
            r = 1.8 * (1 - 0.65 * i / 18) + 0.5
            shapes.append(limb(*pts[i], *pts[i + 1], r, r * 0.9, "soul", k=1, glow=level * (1.1 - 0.5 * i / 18)))
    return shapes


def vortex_eye_build(p):
    root = Xf.move(0, p.bob + 40 * p.unravel ** 1.6)
    j = SKELETON.joints(p)
    cx, cy = j["chest"]
    hx, hy = j["head"]
    head_xf = Xf.turn(j["head_angle"], (0, 0)).then(Xf.move(hx, hy))
    eye = p.eye_glow * (1 - p.dim)
    # the torn shoulders of the lab coat: a ragged hem the vortex pours out of
    hem_y = cy + 30
    coat = [
        blob(cx, cy + 12, 26.0, 18.0, "labcoat", depth=15, k=10),
        blob(j["l_shoulder"][0] + 2, j["l_shoulder"][1] + 7, 9.0, 9.0, "labcoat", depth=9, k=9),
        blob(j["r_shoulder"][0] - 2, j["r_shoulder"][1] + 7, 9.0, 9.0, "labcoat", depth=9, k=9),
        slab([(cx - 29, cy + 12), (cx - 22, hem_y + 2), (cx - 16, hem_y - 4), (cx - 10, hem_y + 5), (cx - 2, hem_y - 2),
              (cx + 7, hem_y + 6), (cx + 15, hem_y - 3), (cx + 22, hem_y + 3), (cx + 29, cy + 12)], "labcoat",
             bevel=3.0, slope=0.9, k=6),
        # torn strips of coat hanging into the vortex, trailing as it turns
        *chain([(cx - 18, hem_y), (cx - 20 + 3 * math.sin(p.spin * 6.28), hem_y + 12), (cx - 17, hem_y + 22)], 2.6, 1.2, "labcoat", k=2),
        *chain([(cx + 11, hem_y + 2), (cx + 14 + 3 * math.cos(p.spin * 6.28), hem_y + 15), (cx + 12, hem_y + 25)], 2.4, 1.1, "labcoat", k=2),
        # lapels and the collar standing up round the helmet's neck ring
        limb(cx - 6, cy - 6, cx - 15, cy + 18, 2.2, 1.6, "labcoat", op="add", depth=1.6),
        limb(cx + 6, cy - 6, cx + 15, cy + 18, 2.2, 1.6, "labcoat", op="add", depth=1.6),
        *fold_lines(cx - 24, cy + 4, cx - 14, hem_y, 3, 8, mat="labcoat", seed=27, angle=0.3),
        *fold_lines(cx + 24, cy + 4, cx + 14, hem_y, 3, 8, mat="labcoat", seed=28, angle=-0.3, side=-1),
        # an ID clip on the lapel
        slab([(cx + 9, cy + 6), (cx + 15, cy + 6), (cx + 15, cy + 12), (cx + 9, cy + 12)], "gunmetal", bevel=0.8,
             op="add", slope=0.8),
    ]
    # the isolation helmet: a white livery dome, a gunmetal neck ring, a dark faceplate with TWO round eye ports
    helmet = [
        blob(0, -1, 20.5, 21.0, "livery", depth=18, k=5),
        limb(-17, 13, 17, 13, 4.2, 4.2, "gunmetal", k=3, prio=20),
        *[blob(-14 + 7 * i, 13, 1.2, 1.2, "darkmetal", op="add", depth=1.0) for i in range(5)],
        limb(0, -19, 0, -9, 1.4, 1.4, "livery", op="carve"),
        # a breather valve on the jaw, off-centre
        blob(10, 8, 3.6, 3.4, "gunmetal", depth=3, prio=30),
        *[limb(8.4 + 1.2 * i, 6, 8.4 + 1.2 * i, 10, 0.35, 0.35, "gunmetal", op="carve") for i in range(3)],
    ]
    face = [
        blob(0, 0.5, 13.5, 11.5, "visor", depth=5, prio=60, k=0),             # the oval window of the helmet
        blob(0, 0.5, 15.0, 13.0, "gunmetal", depth=4.4, prio=55, k=0),       # its gasket ring
        # the two eye ports - gunmetal rings - and the soul pouring out of them
        blob(-5.5, -1.5, 4.4, 4.2, "gunmetal", depth=2.4, prio=70, k=0),
        blob(5.5, -1.5, 4.4, 4.2, "gunmetal", depth=2.4, prio=70, k=0),
        *with_glow([blob(-5.5, -1.5, 3.0, 2.9, "soul", op="add", depth=1.2),
                    blob(5.5, -1.5, 3.0, 2.9, "soul", op="add", depth=1.2)], eye * (0.9 + 0.3 * p.pour)),
    ]
    pour = p.pour * (1 - p.dim)
    if pour > 0.05:
        # the light runs down the glass from each port and off the chin (head space), into the vortex
        face += with_glow([limb(-5.5, 1.5, -6.5, 9.5, 1.5, 1.1, "soul", op="add", depth=0.8),
                           limb(5.5, 1.5, 6.5, 9.5, 1.5, 1.1, "soul", op="add", depth=0.8)], 0.9 * pour)
    stream = []
    layers = [
        root(_vortex(p, hem_y - 4, front=False)),
        root(coat),
        root(stream),
        root(head_xf(helmet)),
        root(head_xf(face)),
        root(_vortex(p, hem_y - 4, front=True)),
    ]
    anchors = {
        "ground": (80.0, 232.0),
        "hit_centre": root.pt((cx, cy + 4)),
        "eye": root.pt((hx, hy - 1.5)),
        "muzzle": root.pt(head_xf.pt((0.0, -1.5))),
    }
    stencils = []
    if p.tag > 0.5:
        sx, sy = root.pt(head_xf.pt((-4.0, -17.0)))
        stencils.append(("R2", sx, sy, (60, 66, 74)))
    return Figure(layers, anchors, stencils)


VORTEX_EYE = Rig("vortex_eye", vortex_eye_build, VORTEX_POSE, seed=67)

VORTEX_CLIPS = []      # CP3 adds the clip set once the owner has approved the concept still
