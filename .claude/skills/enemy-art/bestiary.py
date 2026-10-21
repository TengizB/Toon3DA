"""The generated bestiary: one function per sprite, 160 x 240 px, ground line near y = 232.

Usage:  python3 bestiary.py OUT_DIR [--halo] [sprite ...]
  --halo   also paint the soft translucent glow around emissives (presentation sheets only: the game
           alpha-cuts enemy textures at 0.50, so shipped textures are rendered WITHOUT it).
"""
import sys
from kit import blob, limb, slab, chain, mirror, render, stencil

W, H, AX = 160, 240, 80


def harvest_tick():
    """INSECT. Bred from the planet's tissue, gorged on the harvest; the Organization's
    feeding port is still bolted into its back, its hose torn off. Tagged, and missing a leg."""
    rear_legs = [
        *chain([(58, 128), (24, 84), (12, 140), (6, 196)], 5.5, 1.6, "chitin_dk", k=5),
        blob(24, 84, 6, 6, "chitin"),
        limb(17, 112, 10, 108, 2.0, 0.6, "chitin_dk", k=3),       # spur
    ]
    abdomen = [
        blob(80, 112, 56, 46, "chitin", depth=40, k=10),
        blob(57, 98, 14, 16, "sac", op="add", depth=9),
        blob(80, 86, 16, 18, "sac", op="add", depth=10),
        blob(103, 98, 14, 16, "sac", op="add", depth=9),
        blob(68, 120, 8, 7, "sac", op="add", depth=5),
        blob(95, 124, 6, 5, "sac", op="add", depth=4),
        # ridge grooves wrapping the abdomen
        *[limb(26 + i * 12, 128 + abs(i - 4) * -1.2 + (i - 4) ** 2 * 0.9, 38 + i * 12,
               128 + abs(i - 3) * -1.2 + (i - 3) ** 2 * 0.9, 2.2, 2.2, "chitin", op="carve") for i in range(8)],
        *[limb(36 + i * 11, 144 + (i - 4) ** 2 * 0.6, 47 + i * 11, 144 + (i - 3) ** 2 * 0.6, 1.8, 1.8,
               "chitin", op="carve") for i in range(8)],
        # feeding port
        blob(80, 64, 12, 7, "gunmetal", depth=8, k=3, prio=20),
        limb(80, 52, 80, 62, 8, 9, "gunmetal", k=2, prio=20),
        blob(80, 51, 8, 4, "darkmetal", depth=3, k=0, prio=25),
        blob(73, 60, 1.6, 1.6, "livery", op="add", depth=1.5),
        blob(87, 60, 1.6, 1.6, "livery", op="add", depth=1.5),
    ]
    middle_legs = [
        *chain([(50, 150), (16, 118), (8, 172), (4, 226)], 7, 1.8, "chitin", k=6),
        blob(16, 118, 7.5, 7.5, "chitin_dk"),
        limb(10, 150, 2, 146, 2.0, 0.5, "chitin_dk", k=3),
        limb(8, 176, 1, 174, 1.6, 0.4, "chitin_dk", k=3),
    ]
    torn_hose = [
        *chain([(80, 50), (74, 36), (60, 28), (48, 30)], 4.2, 3.6, "darkmetal", k=3),
        *[blob(74 - i * 4, 36 - i * 2.2, 4.8, 4.8, "darkmetal", op="add", depth=0.8) for i in range(4)],
        blob(47, 30, 3, 3.6, "ichor", depth=3, prio=10),             # still dripping
        limb(46, 33, 45, 46, 1.4, 0.6, "ichor", prio=10),
    ]
    body = [
        blob(80, 160, 34, 23, "chitin", depth=20, k=8),
        blob(80, 184, 22, 16, "chitin_dk", depth=14, k=9),
        limb(56, 156, 104, 156, 1.6, 1.6, "chitin", op="carve"),
        limb(80, 140, 80, 176, 1.4, 1.4, "chitin", op="carve"),
        # eye cluster
        *[blob(x, y, r, r, "ichor", op="add", depth=r) for x, y, r in
          ((71, 178, 3.4), (89, 178, 3.4), (65, 184, 2.4), (95, 184, 2.4), (75, 172, 2.0), (85, 172, 2.0))],
    ]
    front_left = [
        *chain([(58, 168), (30, 150), (24, 192), (21, 230)], 7.5, 2.0, "chitin", k=6),
        blob(30, 150, 8, 8, "chitin_dk"),
        limb(25, 176, 16, 172, 2.3, 0.5, "chitin_dk", k=3),
        limb(23, 200, 15, 199, 1.8, 0.4, "chitin_dk", k=3),
    ]
    # the Organization's tag: a clamp band on the right front leg
    tag_clamp = [
        limb(112.5, 161.5, 119.5, 157.0, 7.6, 7.6, "gunmetal", k=1, prio=30),
        limb(113.5, 154.0, 117.5, 166.0, 0.8, 0.8, "gunmetal", op="carve"),
    ]
    jaws = [
        *chain([(66, 186), (48, 204), (50, 226)], 4.2, 1.8, "chitin", k=4),
        *chain([(72, 192), (63, 210), (72, 222)], 5.0, 0.8, "bone", k=3),
    ]
    jaws = jaws + mirror(jaws, AX)
    layers = [
        rear_legs + mirror(rear_legs, AX),
        abdomen,
        torn_hose,
        middle_legs + mirror(middle_legs, AX),
        body,
        front_left + mirror(front_left, AX) + tag_clamp,
        jaws,
    ]
    img = render(layers, W, H, seed=11)
    stencil(img, "K7", 112, 157, (230, 228, 220))
    return img


def warden():
    """MACHINE. The Organization's riot drone: a hovering hull with one sweeping slit eye, a grabbing
    claw, a stun prod, and its own torn cabling hanging out of the belly."""
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
    fins = [
        slab([(52, 74), (34, 92), (30, 126), (40, 132), (54, 112)], "gunmetal", bevel=4, tilt=(-0.15, 0)),
        limb(36, 96, 34, 124, 1, 1, "gunmetal", op="carve"),
        blob(38, 104, 1.4, 1.4, "livery", op="add", depth=1.2),
    ]
    visor = [
        blob(80, 94, 27, 11, "darkmetal", depth=6, k=2),
        limb(62, 94, 98, 94, 3.4, 3.4, "cyan", k=0, prio=10),
        blob(88, 94, 5.5, 5.5, "cyan", depth=5, prio=20),
    ]
    arm_left = [
        blob(46, 128, 7, 7, "darkmetal"),
        *chain([(46, 128), (32, 152), (40, 176)], 4.8, 3.6, "gunmetal", k=4),
        blob(32, 152, 4.6, 4.6, "darkmetal", op="add", depth=1.5),
        *chain([(40, 176), (34, 190), (38, 198)], 3.0, 1.2, "darkmetal", k=2),
        *chain([(42, 177), (48, 190), (44, 199)], 3.0, 1.2, "darkmetal", k=2),
    ]
    arm_right = [
        blob(114, 128, 7, 7, "darkmetal"),
        *chain([(114, 128), (128, 150), (122, 172)], 4.8, 3.6, "gunmetal", k=4),
        blob(128, 150, 4.6, 4.6, "darkmetal", op="add", depth=1.5),
        limb(118, 170, 134, 208, 3.2, 2.6, "darkmetal", k=1),
        limb(119, 176, 121, 180, 4, 4, "livery", op="add", depth=1),
        blob(135, 210, 3.2, 3.2, "cyan", depth=3, prio=10),
        limb(135, 210, 141, 202, 0.9, 0.5, "cyan", prio=10),
        limb(135, 210, 128, 214, 0.9, 0.5, "cyan", prio=10),
        limb(135, 210, 140, 218, 0.9, 0.5, "cyan", prio=10),
    ]
    underside = [
        limb(80, 146, 80, 186, 10, 2, "cyan", k=0),
        *chain([(70, 146), (66, 162), (71, 176), (68, 188)], 1.8, 1.4, "darkmetal", k=1),
        *chain([(91, 146), (95, 158), (93, 166)], 1.8, 1.6, "darkmetal", k=1),
        blob(93, 167, 1.6, 1.6, "cyan", prio=5),
    ]
    antenna = [
        limb(96, 48, 108, 18, 1.6, 1.1, "darkmetal", k=1),
        blob(108, 17, 2.4, 2.4, "cyan"),
        limb(66, 46, 62, 34, 1.4, 1.2, "darkmetal", k=1),
    ]
    layers = [antenna, fins + mirror(fins, AX), underside, hull, visor, arm_left + arm_right]
    img = render(layers, W, H, seed=5)
    stencil(img, "W-07", 72, 104 + 22, (232, 232, 226))
    return img


# sprite name -> builder. A new enemy sprite is one function plus one row here.
ENEMIES = [("warden", warden), ("harvest_tick", harvest_tick)]

if __name__ == "__main__":
    import kit
    args = sys.argv[1:]
    out = args.pop(0)
    halo = "--halo" in args
    only = [a for a in args if a != "--halo"] or [n for n, _ in ENEMIES]
    if not halo:
        _render = kit.render
        render = lambda layers, w, h, seed=3, glow_radius=5, glow_alpha=0.55: _render(layers, w, h, seed=seed, glow_radius=0)
    for name, build in ENEMIES:
        if name in only:
            build().save(f"{out}/{name}.png"); print("wrote", name)
