#!/usr/bin/env python3
"""Generates the placeholder textures of the mod (block textures 16x16 and the sprites of the puzzle GUI).

Run from the project root:  python3 tools/gen_textures.py
Every sprite is its own small PNG so it can be replaced by hand-drawn art without touching code.
"""
import math
import os
import random
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "common", "src", "main", "resources",
                    "assets", "athanorium", "textures")
BLOCK = os.path.join(ROOT, "block")
GUI = os.path.join(ROOT, "gui", "puzzle")
os.makedirs(BLOCK, exist_ok=True)
os.makedirs(GUI, exist_ok=True)


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


INK = rgb("1a1426")
STONE_D = rgb("231a36")
STONE = rgb("30244a")
STONE_L = rgb("40315f")
VIOLET = rgb("8f6fe0")
VIOLET_L = rgb("b9a2ff")
GOLD = rgb("e2b04a")
GOLD_D = rgb("9c7426")
CYAN = rgb("5fd8e0")
WHITE = rgb("f1ecff")
GREEN = rgb("6fe0a0")
ORANGE = rgb("ff9a4a")
RED = rgb("ff6b6b")
GREY = rgb("b7aed0")


def new(w=16, h=16, fill=(0, 0, 0, 0)):
    return Image.new("RGBA", (w, h), fill)


def save(img, folder, name):
    img.save(os.path.join(folder, name + ".png"))


# ---------------------------------------------------------------- block textures (16x16)

def stone_base(seed):
    rnd = random.Random(seed)
    img = new(fill=STONE)
    px = img.load()
    for y in range(16):
        for x in range(16):
            r = rnd.random()
            px[x, y] = STONE_D if r < 0.18 else STONE_L if r > 0.86 else STONE
    return img


def frame(img, col=GOLD_D, corner=GOLD):
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=col)
    for x, y in ((0, 0), (15, 0), (0, 15), (15, 15)):
        img.putpixel((x, y), corner)
    return img


def small_hex(d, cx, cy, col, fill=None):
    pts = [(cx, cy - 4), (cx + 3, cy - 2), (cx + 3, cy + 2), (cx, cy + 4), (cx - 3, cy + 2), (cx - 3, cy - 2)]
    d.polygon(pts, outline=col, fill=fill)


def block_textures():
    top = frame(stone_base(1))
    d = ImageDraw.Draw(top)
    # hex field seen from above: seven small cells, the middle one glowing
    for cx, cy in ((7, 7), (4, 3), (11, 3), (4, 11), (11, 11)):
        pass
    d.polygon([(8, 2), (13, 5), (13, 10), (8, 13), (7, 13), (2, 10), (2, 5), (7, 2)], outline=VIOLET, fill=STONE_D)
    d.line([(7, 5), (8, 5)], fill=CYAN)
    d.rectangle([6, 6, 9, 9], fill=CYAN)
    d.line([(7, 10), (8, 10)], fill=CYAN)
    d.rectangle([7, 7, 8, 8], fill=WHITE)
    for x, y in ((4, 5), (11, 5), (4, 10), (11, 10)):
        top.putpixel((x, y), GOLD)
    save(top, BLOCK, "developer_block_top")

    side = frame(stone_base(2))
    d = ImageDraw.Draw(side)
    d.line([(1, 4), (14, 4)], fill=STONE_D)
    d.line([(1, 11), (14, 11)], fill=STONE_D)
    # rune band
    for x in (3, 7, 11):
        d.line([(x, 6), (x, 9)], fill=VIOLET)
        side.putpixel((x + 1, 6), VIOLET_L)
        side.putpixel((x + 1, 9), VIOLET)
    save(side, BLOCK, "developer_block_side")

    front = frame(stone_base(3))
    d = ImageDraw.Draw(front)
    d.rectangle([3, 3, 12, 12], fill=INK, outline=VIOLET)
    # a tiny molecule on the "screen": two atoms and a bond
    d.line([(6, 8), (9, 8)], fill=GOLD)
    d.rectangle([5, 7, 6, 8], fill=ORANGE)
    d.rectangle([9, 7, 10, 8], fill=CYAN)
    front.putpixel((4, 4), VIOLET_L)
    front.putpixel((11, 11), VIOLET_L)
    d.line([(6, 14), (9, 14)], fill=GOLD)
    save(front, BLOCK, "developer_block_front")

    bottom = frame(stone_base(4), col=STONE_D, corner=GOLD_D)
    d = ImageDraw.Draw(bottom)
    d.rectangle([5, 5, 10, 10], outline=STONE_D)
    save(bottom, BLOCK, "developer_block_bottom")


# ---------------------------------------------------------------- hex tiles (28x32, pointy top, size 16)

HEX_W, HEX_H = 28, 32


def hex_half_width(y):
    """Half width of the hex in pixel row y (0..31)."""
    if y < 8:
        return 1 + (y * 13) // 7
    if y > 23:
        return 1 + ((31 - y) * 13) // 7
    return 14


def hex_tile(fill, edge, inner=None, alpha_fill=255):
    img = new(HEX_W, HEX_H)
    px = img.load()
    for y in range(HEX_H):
        hw = hex_half_width(y)
        for x in range(14 - hw, 14 + hw):
            px[x, y] = fill[:3] + (alpha_fill,)
    # edge: pixels whose neighbour is outside
    out = img.copy()
    opx = out.load()
    for y in range(HEX_H):
        for x in range(HEX_W):
            if px[x, y][3] == 0:
                continue
            border = False
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if nx < 0 or ny < 0 or nx >= HEX_W or ny >= HEX_H or px[nx, ny][3] == 0:
                    border = True
            if border:
                opx[x, y] = edge
    if inner is not None:
        # second ring one pixel further in
        ring = out.copy()
        rpx = ring.load()
        for y in range(HEX_H):
            for x in range(HEX_W):
                if opx[x, y] == edge or opx[x, y][3] == 0:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if opx[x + dx, y + dy] == edge:
                        rpx[x, y] = inner
        out = ring
    return out


def hex_tiles():
    save(hex_tile(rgb("2a2042"), rgb("4a3a72")), GUI, "hex")
    wall = hex_tile(rgb("14101e"), rgb("6a5a3a"))
    d = ImageDraw.Draw(wall)
    for y in range(9, 24, 4):
        d.line([(6, y), (21, y)], fill=rgb("241c30"))
    for x, y in ((9, 9), (17, 9), (13, 13), (9, 17), (17, 17), (13, 21)):
        d.line([(x, y), (x, y + 3)], fill=rgb("241c30"))
    save(wall, GUI, "hex_wall")
    save(hex_tile(WHITE, WHITE, alpha_fill=0), GUI, "hex_outline")  # tinted in code: hover, selection, error
    save(hex_tile(WHITE, WHITE, alpha_fill=70), GUI, "hex_fill")  # tinted in code
    track = hex_tile(rgb("3a3024"), rgb("b08a4a"), inner=rgb("6a5230"))
    d = ImageDraw.Draw(track)
    d.ellipse([9, 11, 18, 20], outline=rgb("b08a4a"))
    save(track, GUI, "track")
    inp = hex_tile(rgb("23303f"), CYAN, inner=rgb("2f6f78"))
    save(inp, GUI, "io_input")
    outp = hex_tile(rgb("3d3320"), GOLD, inner=GOLD_D)
    save(outp, GUI, "io_output")

    def glyph(name, base, edge, draw_symbol, origin):
        img = hex_tile(base, edge, inner=tuple(int(c * 0.6) for c in edge[:3]) + (255,))
        d = ImageDraw.Draw(img)
        draw_symbol(d, origin)
        save(img, GUI, name)

    def bind(d, origin):
        d.ellipse([10, 12, 17, 19], outline=GOLD)
        d.line([(13, 14), (14, 14)], fill=GOLD)
        d.line([(13, 17), (14, 17)], fill=GOLD)
        d.line([(12, 15), (15, 15)], fill=WHITE)
        d.line([(12, 16), (15, 16)], fill=WHITE)

    def unbind(d, origin):
        d.ellipse([10, 12, 17, 19], outline=RED)
        d.line([(11, 13), (16, 18)], fill=WHITE)
        d.line([(16, 13), (11, 18)], fill=WHITE)

    def multi(d, origin):
        if origin:
            d.ellipse([9, 11, 18, 20], outline=GOLD, fill=GOLD_D)
            d.line([(13, 13), (14, 13)], fill=WHITE)
            d.line([(11, 15), (16, 15)], fill=WHITE)
            d.line([(11, 16), (16, 16)], fill=WHITE)
            d.line([(13, 18), (14, 18)], fill=WHITE)
            d.line([(13, 13), (13, 18)], fill=WHITE)
            d.line([(14, 13), (14, 18)], fill=WHITE)
        else:
            d.ellipse([10, 12, 17, 19], outline=GOLD)

    def disposer(d, origin):
        if origin:
            d.ellipse([8, 10, 19, 21], fill=INK, outline=VIOLET_L)
            d.ellipse([11, 13, 16, 18], fill=rgb("000000"), outline=VIOLET)
        else:
            d.line([(11, 15), (16, 15)], fill=rgb("5a4a80"))
            d.line([(11, 16), (16, 16)], fill=rgb("5a4a80"))

    glyph("glyph_bind_a", rgb("3b3220"), GOLD, bind, True)
    glyph("glyph_bind_b", rgb("3b3220"), GOLD, bind, False)
    glyph("glyph_unbind_a", rgb("3f2228"), RED, unbind, True)
    glyph("glyph_unbind_b", rgb("3f2228"), RED, unbind, False)
    glyph("glyph_multi_bind_a", rgb("3b3220"), GOLD, multi, True)
    glyph("glyph_multi_bind_b", rgb("332a1c"), GOLD_D, multi, False)
    glyph("glyph_disposer_a", rgb("1d1630"), VIOLET, disposer, True)
    glyph("glyph_disposer_b", rgb("221a38"), rgb("5a4a80"), disposer, False)
    unknown = hex_tile(rgb("3a1f3a"), rgb("ff5fd0"))
    d = ImageDraw.Draw(unknown)
    d.line([(11, 12), (16, 12)], fill=WHITE)
    d.line([(16, 12), (16, 15)], fill=WHITE)
    d.line([(13, 15), (16, 15)], fill=WHITE)
    d.line([(13, 15), (13, 17)], fill=WHITE)
    d.point((13, 20), fill=WHITE)
    d.point((14, 20), fill=WHITE)
    save(unknown, GUI, "part_unknown_tile")


# ---------------------------------------------------------------- atoms (16x16)

def atom(name, body, light, dark, symbol):
    img = new()
    d = ImageDraw.Draw(img)
    d.ellipse([1, 1, 14, 14], fill=body, outline=dark)
    d.arc([2, 2, 13, 13], 180, 290, fill=light)
    symbol(d)
    save(img, GUI, "atom_" + name)


def tri_up(d, bar):
    d.polygon([(7, 4), (8, 4), (11, 10), (4, 10)], outline=INK)
    if bar:
        d.line([(5, 8), (10, 8)], fill=INK)


def tri_down(d, bar):
    d.polygon([(4, 5), (11, 5), (8, 11), (7, 11)], outline=INK)
    if bar:
        d.line([(5, 7), (10, 7)], fill=INK)


def atoms():
    atom("fire", rgb("ff7a3c"), rgb("ffd08a"), rgb("8a2f10"), lambda d: tri_up(d, False))
    atom("air", rgb("d6ecff"), WHITE, rgb("5f7fa0"), lambda d: tri_up(d, True))
    atom("water", rgb("3c9cff"), rgb("a8dcff"), rgb("143f8a"), lambda d: tri_down(d, False))
    atom("earth", rgb("7fbf5a"), rgb("c9f0a0"), rgb("2f5a1f"), lambda d: tri_down(d, True))

    def q(d):
        d.line([(6, 5), (9, 5)], fill=INK)
        d.line([(9, 5), (9, 7)], fill=INK)
        d.line([(7, 8), (9, 8)], fill=INK)
        d.point((7, 9), fill=INK)
        d.point((7, 11), fill=INK)

    atom("unknown", rgb("c070c0"), rgb("ffc0ff"), rgb("5a205a"), q)
    bond = new(16, 4)
    d = ImageDraw.Draw(bond)
    d.rectangle([0, 0, 15, 3], fill=rgb("8a7a5a"))
    d.line([(0, 1), (15, 1)], fill=rgb("f3e3b0"))
    d.line([(0, 2), (15, 2)], fill=rgb("d9c58a"))
    save(bond, GUI, "bond")


# ---------------------------------------------------------------- arms

def arms():
    base = new()
    d = ImageDraw.Draw(base)
    d.ellipse([1, 1, 14, 14], fill=rgb("5a5470"), outline=INK)
    d.ellipse([3, 3, 12, 12], fill=rgb("8a84a8"), outline=rgb("3a3550"))
    d.ellipse([6, 6, 9, 9], fill=GOLD, outline=GOLD_D)
    save(base, GUI, "arm_base")
    piston = base.copy()
    d = ImageDraw.Draw(piston)
    d.ellipse([3, 3, 12, 12], fill=rgb("6aa888"), outline=rgb("2f5040"))
    d.ellipse([6, 6, 9, 9], fill=GOLD, outline=GOLD_D)
    save(piston, GUI, "arm_base_piston")
    seg = new(16, 6)
    d = ImageDraw.Draw(seg)
    d.rectangle([0, 0, 15, 5], fill=rgb("3a3550"))
    d.rectangle([0, 1, 15, 4], fill=rgb("9a94b8"))
    d.line([(0, 2), (15, 2)], fill=rgb("c9c4e0"))
    save(seg, GUI, "arm_segment")
    # grabbers: drawn pointing right, centre of the texture is the grabbed hex
    for name, gap in (("grabber_open", 5), ("grabber_closed", 2)):
        g = new()
        d = ImageDraw.Draw(g)
        d.arc([2, 2, 13, 13], 40 + gap * 6, 320 - gap * 6, fill=INK, width=3)
        d.arc([3, 3, 12, 12], 40 + gap * 6, 320 - gap * 6, fill=rgb("c9c4e0"), width=1)
        if name == "grabber_closed":
            d.arc([2, 2, 13, 13], 300, 60, fill=GOLD_D, width=2)
        save(g, GUI, name)


# ---------------------------------------------------------------- icons (16x16)

def arc_arrow(d, bbox, start, end, col, head_at_end, width=2):
    d.arc(bbox, start, end, fill=col, width=width)
    cx = (bbox[0] + bbox[2]) / 2
    cy = (bbox[1] + bbox[3]) / 2
    r = (bbox[2] - bbox[0]) / 2
    ang = math.radians(end if head_at_end else start)
    px, py = cx + r * math.cos(ang), cy + r * math.sin(ang)
    # tangent direction (clockwise in image coordinates when following increasing angle)
    sign = 1 if head_at_end else -1
    tx, ty = -math.sin(ang) * sign, math.cos(ang) * sign
    nx, ny = math.cos(ang), math.sin(ang)
    tip = (px + tx * 3, py + ty * 3)
    a = (px + nx * 3, py + ny * 3)
    b = (px - nx * 3, py - ny * 3)
    d.polygon([tip, a, b], fill=col)


def icon(name, painter):
    img = new()
    painter(ImageDraw.Draw(img))
    save(img, GUI, name)


def arrow_right(d, y, x0, x1, col):
    d.line([(x0, y), (x1, y)], fill=col, width=2)
    d.polygon([(x1 + 1, y - 3), (x1 + 4, y), (x1 + 4, y + 1), (x1 + 1, y + 4)], fill=col)


def arrow_left(d, y, x0, x1, col):
    d.line([(x0, y), (x1, y)], fill=col, width=2)
    d.polygon([(x0 - 1, y - 3), (x0 - 4, y), (x0 - 4, y + 1), (x0 - 1, y + 4)], fill=col)


def instruction_icons():
    def grab(d):
        d.arc([2, 3, 13, 14], 200, 340, fill=GOLD, width=2)
        d.line([(3, 8), (3, 11)], fill=GOLD, width=2)
        d.line([(12, 8), (12, 11)], fill=GOLD, width=2)
        d.ellipse([5, 7, 10, 12], fill=WHITE, outline=GOLD_D)

    def drop(d):
        d.arc([2, 0, 13, 11], 200, 340, fill=GOLD, width=2)
        d.line([(2, 5), (2, 7)], fill=GOLD, width=2)
        d.line([(13, 5), (13, 7)], fill=GOLD, width=2)
        d.ellipse([5, 9, 10, 14], fill=WHITE, outline=GOLD_D)
        d.line([(7, 4), (8, 4)], fill=GOLD)
        d.line([(7, 6), (8, 6)], fill=GOLD)

    icon("instr_grab", grab)
    icon("instr_drop", drop)
    icon("instr_rotate_cw", lambda d: arc_arrow(d, [3, 3, 12, 12], 150, 400, CYAN, True))
    icon("instr_rotate_ccw", lambda d: arc_arrow(d, [3, 3, 12, 12], 140, 390, CYAN, False))

    def pivot(cw):
        def p(d):
            arc_arrow(d, [3, 3, 12, 12], 150 if cw else 140, 400 if cw else 390, VIOLET_L, cw)
            d.ellipse([6, 6, 9, 9], fill=WHITE, outline=VIOLET)
        return p

    icon("instr_pivot_cw", pivot(True))
    icon("instr_pivot_ccw", pivot(False))

    def extend(d):
        d.line([(2, 3), (2, 12)], fill=GREEN, width=2)
        arrow_right(d, 7, 4, 9, GREEN)

    def retract(d):
        d.line([(2, 3), (2, 12)], fill=GREEN, width=2)
        arrow_left(d, 7, 9, 13, GREEN)

    icon("instr_extend", extend)
    icon("instr_retract", retract)

    def chevrons(right):
        def p(d):
            for x in (3, 8):
                if right:
                    d.line([(x, 3), (x + 4, 7)], fill=ORANGE, width=2)
                    d.line([(x + 4, 8), (x, 12)], fill=ORANGE, width=2)
                else:
                    d.line([(x + 4, 3), (x, 7)], fill=ORANGE, width=2)
                    d.line([(x, 8), (x + 4, 12)], fill=ORANGE, width=2)
        return p

    icon("instr_track_forward", chevrons(True))
    icon("instr_track_back", chevrons(False))

    def wait(d):
        d.polygon([(4, 2), (11, 2), (8, 7), (11, 13), (4, 13), (7, 7)], outline=GREY)
        d.line([(3, 2), (12, 2)], fill=GREY)
        d.line([(3, 13), (12, 13)], fill=GREY)
        d.line([(6, 11), (9, 11)], fill=WHITE)
        d.line([(7, 10), (8, 10)], fill=WHITE)

    icon("instr_wait", wait)

    def repeat(d):
        d.line([(3, 4), (11, 4)], fill=WHITE, width=2)
        d.polygon([(11, 1), (14, 4), (14, 5), (11, 8)], fill=WHITE)
        d.line([(4, 11), (12, 11)], fill=WHITE, width=2)
        d.polygon([(4, 8), (1, 11), (1, 12), (4, 15)], fill=WHITE)

    icon("instr_repeat", repeat)

    def reset(d):
        d.line([(12, 2), (12, 9)], fill=WHITE, width=2)
        d.line([(5, 9), (12, 9)], fill=WHITE, width=2)
        d.polygon([(5, 5), (1, 9), (1, 10), (5, 14)], fill=WHITE)

    icon("instr_reset", reset)

    def unknown(d):
        d.rectangle([2, 2, 13, 13], outline=rgb("ff5fd0"))
        d.line([(6, 5), (9, 5)], fill=WHITE)
        d.line([(9, 5), (9, 7)], fill=WHITE)
        d.line([(7, 8), (9, 8)], fill=WHITE)
        d.point((7, 9), fill=WHITE)
        d.point((7, 11), fill=WHITE)

    icon("instr_unknown", unknown)


def part_icons():
    def arm_icon(angles, piston=False):
        def p(d):
            for a in angles:
                r = math.radians(a)
                x, y = 7.5 + 6 * math.cos(r), 7.5 - 6 * math.sin(r)
                d.line([(7.5, 7.5), (x, y)], fill=rgb("c9c4e0"), width=2)
                d.ellipse([x - 1.5, y - 1.5, x + 1.5, y + 1.5], fill=GOLD)
            d.ellipse([5, 5, 10, 10], fill=rgb("6aa888") if piston else rgb("8a84a8"), outline=INK)
        return p

    icon("part_arm", arm_icon([0]))
    icon("part_double_arm", arm_icon([0, 180]))
    icon("part_triple_arm", arm_icon([90, 210, 330]))
    icon("part_hex_arm", arm_icon([0, 60, 120, 180, 240, 300]))
    icon("part_piston_arm", arm_icon([0], True))

    def mini_hex(d, cx, cy, fill, edge):
        d.polygon([(cx, cy - 4), (cx + 3, cy - 2), (cx + 3, cy + 2), (cx, cy + 4), (cx - 1, cy + 4),
                   (cx - 4, cy + 2), (cx - 4, cy - 2), (cx - 1, cy - 4)], fill=fill, outline=edge)

    def track(d):
        for cx in (4, 11):
            mini_hex(d, cx, 8, rgb("3a3024"), rgb("b08a4a"))
        d.line([(5, 8), (10, 8)], fill=GOLD)

    def bind(d):
        mini_hex(d, 4, 8, rgb("3b3220"), GOLD)
        mini_hex(d, 12, 8, rgb("3b3220"), GOLD)
        d.line([(6, 7), (9, 7)], fill=WHITE)
        d.line([(6, 8), (9, 8)], fill=WHITE)

    def unbind(d):
        mini_hex(d, 4, 8, rgb("3f2228"), RED)
        mini_hex(d, 12, 8, rgb("3f2228"), RED)
        d.line([(6, 6), (9, 9)], fill=WHITE)
        d.line([(9, 6), (6, 9)], fill=WHITE)

    def multi(d):
        mini_hex(d, 8, 8, GOLD_D, GOLD)
        for a in (0, 120, 240):
            r = math.radians(a)
            x, y = 7.5 + 6 * math.cos(r), 7.5 - 6 * math.sin(r)
            d.line([(7.5, 7.5), (x, y)], fill=WHITE)
            d.ellipse([x - 1.5, y - 1.5, x + 1.5, y + 1.5], fill=GOLD)

    def disposer(d):
        d.ellipse([1, 1, 14, 14], fill=rgb("1d1630"), outline=VIOLET)
        d.ellipse([4, 4, 11, 11], fill=INK, outline=VIOLET_L)
        d.ellipse([6, 6, 9, 9], fill=rgb("000000"))

    def inp(d):
        mini_hex(d, 8, 8, rgb("23303f"), CYAN)
        d.line([(8, 5), (8, 10)], fill=WHITE)
        d.line([(6, 8), (8, 10)], fill=WHITE)
        d.line([(10, 8), (8, 10)], fill=WHITE)

    def outp(d):
        mini_hex(d, 8, 8, rgb("3d3320"), GOLD)
        d.line([(8, 6), (8, 11)], fill=WHITE)
        d.line([(6, 8), (8, 6)], fill=WHITE)
        d.line([(10, 8), (8, 6)], fill=WHITE)

    icon("part_track", track)
    icon("part_bind", bind)
    icon("part_unbind", unbind)
    icon("part_multi_bind", multi)
    icon("part_disposer", disposer)
    icon("part_input", inp)
    icon("part_output", outp)


def control_icons():
    def play(d):
        d.polygon([(4, 2), (13, 7), (13, 8), (4, 13)], fill=GREEN)

    def pause(d):
        d.rectangle([3, 2, 6, 13], fill=GOLD)
        d.rectangle([9, 2, 12, 13], fill=GOLD)

    def step(d):
        d.polygon([(2, 2), (9, 7), (9, 8), (2, 13)], fill=CYAN)
        d.rectangle([11, 2, 13, 13], fill=CYAN)

    def stop(d):
        d.rectangle([3, 3, 12, 12], fill=RED)

    def undo(d):
        arc_arrow(d, [3, 4, 12, 13], 180, 400, WHITE, False)

    def redo(d):
        arc_arrow(d, [3, 4, 12, 13], 140, 360, WHITE, True)

    def savei(d):
        d.rectangle([2, 2, 13, 13], fill=rgb("4a7fd0"), outline=INK)
        d.rectangle([4, 2, 10, 6], fill=WHITE)
        d.rectangle([8, 3, 9, 5], fill=rgb("4a7fd0"))
        d.rectangle([4, 9, 11, 13], fill=rgb("d9e4ff"))

    def discard(d):
        d.line([(3, 3), (12, 12)], fill=RED, width=2)
        d.line([(12, 3), (3, 12)], fill=RED, width=2)

    def listi(d):
        for y in (3, 7, 11):
            d.rectangle([2, y, 3, y + 1], fill=GOLD)
            d.rectangle([5, y, 13, y + 1], fill=WHITE)

    def rot_ccw(d):
        arc_arrow(d, [3, 3, 12, 12], 140, 390, GOLD, False)

    def rot_cw(d):
        arc_arrow(d, [3, 3, 12, 12], 150, 400, GOLD, True)

    def plus(d):
        d.rectangle([7, 3, 8, 12], fill=GREEN)
        d.rectangle([3, 7, 12, 8], fill=GREEN)

    def minus(d):
        d.rectangle([3, 7, 12, 8], fill=ORANGE)

    def trash(d):
        d.rectangle([4, 5, 11, 13], outline=RED)
        d.line([(3, 4), (12, 4)], fill=RED)
        d.line([(6, 2), (9, 2)], fill=RED)
        d.line([(6, 7), (6, 11)], fill=RED)
        d.line([(9, 7), (9, 11)], fill=RED)

    def close(d):
        d.line([(4, 4), (11, 11)], fill=WHITE, width=2)
        d.line([(11, 4), (4, 11)], fill=WHITE, width=2)

    for name, p in (("ctl_play", play), ("ctl_pause", pause), ("ctl_step", step), ("ctl_stop", stop),
                    ("ctl_undo", undo), ("ctl_redo", redo), ("ctl_save", savei), ("ctl_discard", discard),
                    ("ctl_puzzles", listi), ("ctl_rotate_ccw", rot_ccw), ("ctl_rotate_cw", rot_cw),
                    ("ctl_longer", plus), ("ctl_shorter", minus), ("ctl_remove", trash), ("ctl_close", close)):
        icon(name, p)


def background():
    rnd = random.Random(7)
    img = new(64, 64, rgb("171122"))
    px = img.load()
    for _ in range(26):
        x, y = rnd.randrange(64), rnd.randrange(64)
        px[x, y] = rnd.choice([rgb("3a2f5a"), rgb("4a3d70"), rgb("2c2446")])
    for _ in range(4):
        x, y = rnd.randrange(2, 62), rnd.randrange(2, 62)
        px[x, y] = rgb("8f7bd0")
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            px[x + dx, y + dy] = rgb("3a2f5a")
    save(img, GUI, "background")


if __name__ == "__main__":
    block_textures()
    hex_tiles()
    atoms()
    arms()
    instruction_icons()
    part_icons()
    control_icons()
    background()
    print("textures written to", os.path.normpath(ROOT))
