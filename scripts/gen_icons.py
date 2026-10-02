#!/usr/bin/env python3
"""
Stained-glass item icons («Витраж», the owner's pick of five mockups).

Rewrites every `equipment.*` and `item.*` key of src/main/resources/icons/icons.json to a coloured
sprite drawn here, one sprite per subtype: a slot's defence and base tier, a weapon's type, a ring's
stone, an essence's kind and tier band, an orb, an egg's biome. A unique gets its own: the subtype's
shape in its theme's glass with its sign; a mythical one also rays. `slot.*` keys are the empty
places of the body in dark glass. Stat and skill sprites become small windows of their hue; their
mono originals live in `icons_mono.json` beside this script, the source they are redrawn from.

A glass sprite carries `"style": "glass"`; its paths carry `color`. The client paints them as:
  * a piece  (alpha 1, no line) — the colour, a diagonal sheen over it, a dark lead line round it;
  * a glaze  (alpha < 1)        — the colour only: a highlight or a shade laid over the pieces;
  * a line   (line > 0)         — a dark lead stroke with the colour inside it.
Rarity is not in the sprite: the client puts a halo of the rarity colour behind it.

Rotations and scales are baked into the path data, so the client needs no transforms.

    pip install svgelements && python3 scripts/gen_icons.py
"""
import hashlib
import json
import math
import re
from pathlib import Path as FsPath

from svgelements import Matrix, Path

ROOT = FsPath(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources"
PREFIX = "gl_"

# ---------------------------------------------------------------------------------------- palette

MAT = {
    "steel": "#b9c2cf", "bronze": "#c98a4a", "runic": "#6fd3c8", "iron": "#8e97a3",
    "wood": "#8a5a34", "darkwood": "#5e3b22", "leather": "#8b5530", "hide": "#a8784a",
    "gold": "#d9a53a", "silver": "#dfe6ee", "bone": "#e4dcc4",
    "cloth": "#3a5fa0", "violet": "#7a4fc0", "paper": "#e6d4a6",
    "glass": "#bfe3ef", "dark": "#26211d", "stone": "#827c73", "white": "#ffffff", "black": "#000000",
}
# A defence's look: the material of its pieces and the colour of its trim.
DEFENCE = {
    "BASE_ARMOUR": ("steel", "#b9c2cf"),
    "BASE_EVASION": ("leather", "#5f9a4e"),
    "BASE_ENERGY_SHIELD": ("violet", "#8a6ae0"),
}
STONES = {
    "IRON": "#8e97a3", "CORAL": "#ef7a5a", "PAUA": "#38b8b0", "RUBY": "#e0304a", "SAPPHIRE": "#3a78e8",
    "TOPAZ": "#f2c53a", "MOONSTONE": "#cfe4f5", "DIAMOND": "#eaf6ff", "AMETHYST": "#9b59d6", "AMBER": "#e89a2a",
    "JADE": "#4fbf7a", "LAPIS": "#2f55c8", "GOLD": "#e8bb45", "ONYX": "#3c3a44",
}
# Any other colour — a unique's, a biome's — is picked from here by a stable hash of the code.
WHEEL = ["#e0405a", "#ef8a3a", "#f2c53a", "#7fcf4a", "#38b8b0", "#3a9ae8", "#5a62e0", "#9b59d6", "#d44fb0", "#c8d6e6"]


def c(x):
    return MAT.get(x, x)


def hashed(code):
    return WHEEL[int(hashlib.md5(code.encode()).hexdigest(), 16) % len(WHEEL)]


def stone(code):
    return next((v for k, v in STONES.items() if k in code), None) or hashed(code)


def shade(hexcol, f):
    r, g, b = (int(hexcol[i:i + 2], 16) for i in (1, 3, 5))
    return "#%02x%02x%02x" % tuple(max(0, min(255, round(v * f))) for v in (r, g, b))


# ---------------------------------------------------------------------------------------- shapes

def circ(x, y, r):
    return f"M{x - r} {y} a{r} {r} 0 1 0 {2 * r} 0 a{r} {r} 0 1 0 {-2 * r} 0Z"


def piece(d, col, **kw):
    return dict(d=d, color=c(col), **kw)


def glaze(d, col="white", alpha=.35):
    return dict(d=d, color=c(col), alpha=alpha)


def line(d, col, width):
    return dict(d=d, color=c(col), line=width)


def sword(blade, guard, grip):
    return [piece("M32 3 L36 10 L35.5 40 L28.5 40 L28 10Z", blade), glaze("M31.3 11 h1.4 v27 h-1.4z", "black", .35),
            piece("M19 40 h26 v5 h-26z", guard), piece("M29.5 45 h5 v11 h-5z", grip), piece(circ(32, 59.5, 3.5), guard)]


def rapier(blade, guard, grip):
    return [piece("M31 3 L33 3 L34 40 L30 40Z", blade), piece("M21 42 C21 34 43 34 43 42 L43 46 L21 46Z", guard),
            piece("M29.5 46 h5 v10 h-5z", grip), piece(circ(32, 59, 3), guard)]


def longsword(blade, guard, grip):
    return [piece("M32 1 L37 8 L36.5 42 L27.5 42 L27 8Z", blade), glaze("M31.3 9 h1.4 v31 h-1.4z", "black", .35),
            piece("M15 42 L49 42 L51 47 L13 47Z", guard), piece("M29.5 47 h5 v12 h-5z", grip), piece(circ(32, 61, 3), guard)]


def greatsword(blade, guard, grip):
    return [piece("M32 2 L41 11 L39.5 42 L24.5 42 L23 11Z", blade), glaze("M32 2 L41 11 L39.5 42 L32 42Z", "black", .2),
            piece("M13 40 h38 v6 h-38z", guard), piece("M29.5 46 h5 v12 h-5z", grip), piece(circ(32, 60.5, 3.2), guard)]


def axe(head, haft):
    return [piece("M30 12 h4 v48 h-4z", haft), piece("M34 10 C47 6 56 13 57 22 C56 31 47 36 34 32Z", head),
            piece("M30 14 L21 19 L30 25Z", head), piece("M29 12 h6 v4 h-6z", "gold")]


def doubleaxe(head, haft):
    return [piece("M30 6 h4 v56 h-4z", haft), piece("M34 10 C46 4 57 12 57 24 C57 34 46 40 34 34Z", head),
            piece("M30 10 C18 4 7 12 7 24 C7 34 18 40 30 34Z", head), piece("M28.5 8 h7 v4 h-7z", "gold"),
            glaze("M34 12 C44 8 52 14 54 22 L34 22Z", "white", .25)]


def wand(gem, shaft):
    return [piece("M30 20 L34 20 L33 61 L31 61Z", shaft), piece("M32 3 L39 13 L32 22 L25 13Z", gem),
            piece("M28.5 19 h7 v4 h-7z", "gold"), glaze("M32 3 L32 22 L25 13Z", "white", .3)]


def staff(orb, shaft):
    """A long two-handed staff: an orb held by two curled prongs over a banded shaft."""
    return [piece("M30.5 18 h3 v42 h-3z", shaft), piece("M24.5 21 C21 13 25 5 31 2.5 C27.5 8 27 14 30 19Z", shaft),
            piece("M39.5 21 C43 13 39 5 33 2.5 C36.5 8 37 14 34 19Z", shaft), piece(circ(32, 11, 6), orb),
            glaze(circ(30, 9, 2), "white", .6), piece("M27.5 19 h9 v4 h-9z", "gold"), piece("M29.5 58 h5 v4.5 h-5z", "gold")]


def sceptre(head, gem, shaft):
    """A one-handed sceptre: a flanged crown head with a set gem, a short grip and a pommel."""
    return [piece("M30 29 h4 v25 h-4z", shaft), piece("M32 3 L43 11 L40.5 27 L23.5 27 L21 11Z", head),
            glaze("M32 3 L32 27 L23.5 27 L21 11Z", "black", .2), piece("M32 9 L37.5 16 L32 23 L26.5 16Z", gem),
            glaze("M32 9 L32 23 L26.5 16Z", "white", .35), piece("M25 26 h14 v4 h-14z", "gold"), piece(circ(32, 57, 3.5), "gold")]


def bow(limb, grip):
    return [piece("M22 5 C45 15 45 49 22 59 L25 61 C50 49 50 15 25 3Z", limb), line("M23 6 V58", "#e6d4a6", 1.2),
            piece("M37 27 h7 v10 h-7z", grip)]


def helmet(style, mat, trim):
    if style == "BASE_EVASION":
        return [piece("M12 42 C12 18 22 7 32 7 C42 7 52 18 52 42 L46 56 L40 44 C38 40 26 40 24 44 L18 56Z", mat),
                piece("M13 36 C24 31 40 31 51 36 L51 41 C40 36 24 36 13 41Z", trim),
                glaze("M20 16 C24 11 28 9 32 9 L32 13 C28 13 24 15 22 19Z")]
    if style == "BASE_ENERGY_SHIELD":
        return [piece("M12 44 L15 20 L24 33 L32 11 L40 33 L49 20 L52 44Z", mat), piece("M11 42 h42 v9 h-42z", "gold"),
                piece("M32 36 L38 43 L32 51 L26 43Z", trim), glaze("M32 36 L32 51 L26 43Z", "white", .35)]
    return [piece("M12 36 C12 18 22 7 32 7 C42 7 52 18 52 36 L52 51 L42 55 L42 39 L22 39 L22 55 L12 51Z", mat),
            piece("M30 6 h4 v30 h-4z", trim), piece("M12 33 h40 v5 h-40z", "gold"), piece("M30 38 h4 v13 h-4z", mat)]


def body(style, mat, trim):
    if style == "BASE_EVASION":
        return [piece("M20 8 L28 12 L32 20 L36 12 L44 8 L54 16 L50 28 L46 26 L46 58 L18 58 L18 26 L14 28 L10 16Z", mat),
                line("M28 24 L36 28 M36 24 L28 28 M28 32 L36 36 M36 32 L28 36", "#e6d4a6", 1.4),
                piece("M18 44 h28 v5 h-28z", trim)]
    if style == "BASE_ENERGY_SHIELD":
        return [piece("M22 6 L32 12 L42 6 L52 14 L48 24 L44 22 L50 60 L14 60 L20 22 L16 24 L12 14Z", mat),
                piece("M22 6 L32 12 L42 6 L40 16 L32 20 L24 16Z", "gold"), piece("M20 34 h24 v5 h-24z", trim),
                piece("M32 22 L36 27 L32 32 L28 27Z", trim)]
    return [piece("M18 10 L26 8 C28 14 36 14 38 8 L46 10 L56 18 L52 30 L48 28 L48 57 L16 57 L16 28 L12 30 L8 18Z", mat),
            piece("M8 18 L18 10 L20 21 L12 30Z", "gold"), piece("M56 18 L46 10 L44 21 L52 30Z", "gold"),
            glaze("M31 16 h2 v26 h-2z", "black", .4), piece("M16 42 h32 v5 h-32z", trim)]


HAND = ("M18 40 L18 22 C18 18 22 18 22 22 L22 12 C22 8 27 8 27 12 L27 10 C27 6 32 6 32 10 L32 12 C32 8 37 8 37 12 "
        "L37 26 L42 20 C45 17 49 20 46 24 L42 34 L42 40Z")


def gloves(style, mat, trim):
    if style == "BASE_ARMOUR":
        return [piece(HAND, mat), piece("M18 27 h19 v4 h-19z", "gold"), piece("M17 40 h26 v18 h-26z", trim)]
    if style == "BASE_EVASION":
        return [piece(HAND, mat), piece("M17 40 h26 v18 h-26z", trim), line("M20 46 H40 M20 52 H40", "#e6d4a6", 1.2)]
    return [piece(HAND, mat), piece("M16 40 h28 v18 h-28z", "gold"), piece("M27 26 L31 31 L27 36 L23 31Z", trim)]


def boots(style, mat, trim):
    if style == "BASE_ARMOUR":
        return [piece("M18 6 h18 v30 L50 42 C57 44 57 54 50 56 L18 56Z", mat), piece("M22 14 h10 v18 h-10z", trim),
                piece("M16 5 h22 v6 h-22z", "gold"), piece("M16 54 h39 v5 h-39z", "dark")]
    if style == "BASE_EVASION":
        return [piece("M18 6 h18 v30 L50 42 C57 44 57 54 50 56 L18 56Z", mat), piece("M16 5 h22 v7 h-22z", trim),
                line("M20 18 H34 M20 24 H34 M20 30 H34", "#e6d4a6", 1.2), piece("M16 54 h39 v5 h-39z", "dark")]
    return [piece("M20 6 h16 v30 L48 44 C54 44 58 40 58 36 C60 44 56 56 48 56 L20 56Z", mat),
            piece("M18 5 h20 v7 h-20z", "gold"), piece("M28 20 L32 25 L28 30 L24 25Z", trim), piece("M18 54 h32 v5 h-32z", "dark")]


def shield(shape, mat, face, trim):
    if shape == "TOWER":
        return [piece("M14 5 h36 v40 L32 60 L14 45Z", mat), piece("M19 10 h26 v33 L32 54 L19 43Z", face),
                piece("M30 12 h4 v38 h-4z", trim), piece("M20 24 h24 v4 h-24z", trim)]
    if shape == "BUCKLER":
        return [piece(circ(32, 32, 24), mat), piece(circ(32, 32, 17), face), piece(circ(32, 32, 7), trim),
                glaze(circ(29, 29, 3), "white", .5)]
    if shape == "SPIRIT":
        return [piece("M32 4 C48 4 54 18 54 32 C54 48 44 60 32 60 C20 60 10 48 10 32 C10 18 16 4 32 4Z", mat),
                piece("M32 10 C43 10 48 20 48 32 C48 44 41 54 32 54 C23 54 16 44 16 32 C16 20 21 10 32 10Z", face),
                piece("M32 20 L40 32 L32 44 L24 32Z", trim), glaze("M32 20 L32 44 L24 32Z", "white", .3)]
    if shape == "ROUND":
        return [piece(circ(32, 32, 26), "gold"), piece(circ(32, 32, 22), face),
                line("M20 14 V50 M32 10 V54 M44 14 V50", "#3a2616", 1.4), piece(circ(32, 32, 6), mat)]
    if shape == "SPIKED":
        return [piece("M32 4 L54 12 C54 36 46 50 32 60 C18 50 10 36 10 12Z", mat),
                piece("M32 10 L48 16 C48 34 42 45 32 53 C22 45 16 34 16 16Z", face),
                piece("M32 22 L36 30 L32 44 L28 30Z", "steel"), piece("M22 26 L30 30 L22 34Z", "steel"),
                piece("M42 26 L34 30 L42 34Z", "steel")]
    return [piece("M32 4 L54 12 C54 36 46 50 32 60 C18 50 10 36 10 12Z", mat),
            piece("M32 10 L48 16 C48 34 42 45 32 53 C22 45 16 34 16 16Z", face),
            piece("M30 15 h4 v34 h-4z", trim), piece("M20 26 h24 v4 h-24z", trim)]


def ring(band, gem):
    return [piece(circ(32, 40, 18) + circ(32, 40, 12.5), band, evenOdd=True), piece("M25 21 h14 v5 h-14z", band),
            piece("M32 5 L42 14 L32 24 L22 14Z", gem), glaze("M32 5 L32 24 L22 14Z", "white", .3)]


def amulet(gem):
    return [piece("M14 5 C14 28 26 36 32 38 C38 36 50 28 50 5 L47 5 C47 26 36 33 32 34 C28 33 17 26 17 5Z", "gold"),
            piece(circ(32, 47, 12), "gold"), piece(circ(32, 47, 7), gem), glaze(circ(30, 45, 2.5), "white", .6)]


def belt(strap, detail):
    parts = [piece("M3 25 h58 v14 h-58z", strap),
             piece("M24 20 h16 v24 h-16z" + "M28 25 h8 v14 h-8z", "gold", evenOdd=True), piece("M31 30 h14 v4 h-14z", "steel")]
    if detail == "chain":
        parts.append(line("M6 32 H22 M42 32 H58", "#dfe6ee", 2.2))
    elif detail == "studs":
        parts += [piece(circ(x, 32, 2.2), "steel") for x in (9, 16, 48, 55)]
    return parts


def flask(liquid, tall):
    if tall:
        return [piece("M27 6 h10 v4 h-1 v10 L44 34 V56 C44 59 42 60 40 60 H24 C22 60 20 59 20 56 V34 L28 20 V10 h-1Z", "glass"),
                piece("M21 38 H43 V56 C43 58 42 59 40 59 H24 C22 59 21 58 21 56Z", liquid),
                piece("M27 2 h10 v6 h-10z", "wood"), glaze("M23 38 V54 H26 V38Z", "white", .55)]
    return [piece("M26 6 h12 v4 h-2 v12 C46 26 50 34 50 42 C50 54 42 60 32 60 C22 60 14 54 14 42 C14 34 18 26 28 22 V10 h-2Z", "glass"),
            piece("M16 40 C22 36 42 44 48 40 C49 52 42 58 32 58 C22 58 15 52 16 40Z", liquid),
            piece("M27 2 h10 v6 h-10z", "wood"), glaze("M20 38 C20 32 24 28 28 27 L28 30 C25 32 23 35 23 38Z", "white", .7)]


def quiver(fletch):
    return [piece("M36 4 L40 5 L37 20 L33 19Z", fletch), piece("M44 7 L48 9 L42 22 L38 20Z", fletch),
            piece("M27 6 L31 6 L31 20 L27 20Z", fletch), piece("M18 20 L44 16 L50 54 L28 60Z", "leather"),
            piece("M18 20 L44 16 L45 23 L19 27Z", "gold"), line("M26 34 L46 30", "#e6d4a6", 1.2)]


def wings(membrane, bone, feathered):
    if feathered:
        return [piece("M32 30 C24 12 10 6 4 8 C8 16 6 24 10 30 C8 36 10 42 16 46 C20 40 26 36 32 34Z", membrane),
                piece("M32 30 C40 12 54 6 60 8 C56 16 58 24 54 30 C56 36 54 42 48 46 C44 40 38 36 32 34Z", membrane),
                line("M10 16 C18 18 24 24 30 32 M54 16 C46 18 40 24 34 32", bone, 1.4), piece(circ(32, 32, 4), "gold")]
    return [piece("M32 30 L20 8 L4 14 L10 22 L4 30 L12 34 L8 44 L22 38 L30 36Z", membrane),
            piece("M32 30 L44 8 L60 14 L54 22 L60 30 L52 34 L56 44 L42 38 L34 36Z", membrane),
            line("M30 32 L20 8 M30 32 L10 22 M30 32 L12 34 M34 32 L44 8 M34 32 L54 22 M34 32 L52 34", bone, 1.4),
            piece(circ(32, 32, 4), "gold")]


def jewel(gem):
    return [piece("M32 6 L54 18 L54 46 L32 58 L10 46 L10 18Z", gem), piece("M32 18 L43 24 L43 40 L32 46 L21 40 L21 24Z", shade(gem, 1.25)),
            glaze("M32 6 L10 18 L21 24 L32 18Z", "white", .4), glaze("M54 46 L32 58 L32 46 L43 40Z", "black", .3)]


def game_map(seal):
    return [piece("M8 12 L24 7 L40 13 L56 8 L56 52 L40 57 L24 51 L8 56Z", "paper"),
            glaze("M24 7 L40 13 L40 57 L24 51Z", "black", .12), line("M14 46 C20 38 26 44 30 36 C33 30 38 32 42 28", "#b8322a", 1.6),
            piece(circ(46, 20, 7), seal), glaze(circ(44.5, 18.5, 2), "white", .5)]


def tool(kind, metal):
    haft = "wood"
    if kind == "TOOL_MINING":
        return [piece("M30 14 h4 v48 h-4z", haft), piece("M6 18 C18 6 46 6 58 18 C46 13 18 13 6 18Z", metal),
                piece("M27 10 h10 v8 h-10z", metal)]
    if kind == "TOOL_HERBALISM":
        return [piece("M28 36 h5 v24 h-5z", haft), piece("M31 38 C18 38 10 26 14 12 C20 22 30 28 44 28 C44 34 38 38 31 38Z", metal),
                piece("M27 34 h7 v4 h-7z", "gold")]
    if kind == "TOOL_WOODCUTTING":
        return [piece("M30 10 h4 v52 h-4z", haft), piece("M34 10 C44 10 52 16 52 26 C52 32 46 34 34 30Z", metal),
                piece("M30 12 L24 14 L24 26 L30 28Z", metal)]
    if kind == "TOOL_SMITHING":
        return [piece("M29.5 22 h5 v40 h-5z", haft), piece("M12 8 h40 v16 h-40z", metal), glaze("M12 8 h40 v5 h-40z", "white", .3),
                piece("M28 6 h8 v20 h-8z", "gold")]
    if kind == "TOOL_ALCHEMY":
        return [piece("M22 60 C12 60 8 52 10 44 C12 36 20 32 26 30 V20 H38 V30 C44 32 52 36 54 44 C56 52 52 60 42 60Z", "glass"),
                piece("M12 46 C20 42 44 50 52 46 C52 54 48 58 42 58 H22 C16 58 12 54 12 46Z", "#6fcf6a"),
                piece("M24 14 h16 v6 h-16z", metal), line("M38 16 C50 12 56 18 58 26", c(metal), 3)]
    if kind == "TOOL_CARTOGRAPHY":
        return [piece("M46 4 C58 10 52 30 38 40 L30 38 C32 24 38 10 46 4Z", "white"),
                line("M44 10 C40 22 36 30 32 38", "#9aa6b4", 1), piece("M30 38 L38 40 L26 60 Z", metal)]
    return [piece("M40 6 L48 14 L22 50 L14 42Z", haft), piece("M14 42 L22 50 L12 58 L8 56 L6 52Z", metal),
            piece("M36 10 L44 18 L40 22 L32 14Z", "gold")]


def essence(col, band):
    hi = shade(col, 1.3)
    if band == 0:
        return [piece("M32 5 L44 22 L40 59 L24 59 L20 22Z", col), glaze("M32 5 L32 59 L40 59 L44 22Z", "black", .3),
                glaze("M32 5 L20 22 L24 59 L27 59 L26 22Z", "white", .35)]
    parts = [piece("M20 30 L26 38 L24 59 L16 59 L14 41Z", col), piece("M44 30 L50 41 L48 59 L40 59 L38 38Z", col),
             piece("M32 3 L42 20 L38 59 L26 59 L22 20Z", hi), glaze("M32 3 L32 59 L38 59 L42 20Z", "black", .3),
             glaze("M32 3 L22 20 L26 59 L28 59 L27 20Z", "white", .35)]
    if band == 2:
        parts.append(piece("M32 28 L36 36 L32 44 L28 36Z", "white"))
    if band == 3:
        parts.append(piece("M24 36 C28 30 36 30 40 36 C36 42 28 42 24 36Z", "black"))
        parts.append(piece(circ(32, 36, 3), "#e0304a"))
    return parts


def book(cover):
    return [piece("M12 8 h36 a4 4 0 0 1 4 4 v44 a4 4 0 0 1 -4 4 h-36z", cover), piece("M47 12 h4 v44 h-4z", "paper"),
            piece("M12 8 h6 v52 h-6z", shade(cover, .55)), piece("M34 18 L43 32 L34 46 L25 32Z", "gold"),
            piece(circ(34, 32, 3.5), shade(cover, 1.4))]


def ore(vein):
    return [piece("M8 44 L14 24 L28 14 L46 18 L56 34 L52 52 L30 58 L12 54Z", "stone"),
            piece("M22 30 L28 26 L30 34 L24 36Z", vein), piece("M38 36 L46 32 L48 42 L40 44Z", vein),
            piece("M30 44 L34 42 L35 48 L31 49Z", vein), glaze("M14 24 L28 14 L30 22 L18 30Z", "white", .2)]


def cut_gem(col):
    return [piece("M18 14 H46 L58 28 L32 58 L6 28Z", col), piece("M18 14 L24 28 H40 L46 14Z", shade(col, 1.3)),
            piece("M6 28 H58 L32 58Z", shade(col, .8)), glaze("M18 14 L6 28 H24Z", "white", .45)]


def mushroom(cap, stem="bone"):
    return [piece("M26 34 h12 v22 C38 60 26 60 26 56Z", stem), piece("M8 36 C8 16 56 16 56 36 C44 32 20 32 8 36Z", cap),
            piece(circ(22, 27, 3), "white"), piece(circ(38, 24, 2.5), "white"), piece(circ(45, 31, 2), "white")]


def flower(petal, center="gold"):
    petal_d = "M32 32 C23 24 24 10 32 6 C40 10 41 24 32 32Z"
    petals = [piece(str(Path(petal_d) * Matrix(f"rotate({k * 72}deg, 32, 32)")), petal) for k in range(5)]
    return [line("M32 40 C30 48 30 54 32 62", "#4f8f3e", 2.5)] + petals + [piece(circ(32, 32, 6), center)]


def sprig(leaf):
    return [line("M32 60 C32 44 30 28 34 6", "#6b8f4e", 2.5),
            piece("M32 46 C22 46 14 40 12 32 C22 32 30 38 32 46Z", leaf), piece("M33 36 C42 36 50 30 52 22 C42 22 35 28 33 36Z", leaf),
            piece("M32 24 C24 24 18 18 18 12 C26 12 31 18 32 24Z", leaf)]


def root(col):
    return [piece("M28 4 h10 l-2 12 C44 24 46 36 38 46 L40 60 L34 50 L28 60 L30 46 C20 38 22 24 30 16Z", col),
            line("M30 26 C34 30 36 34 34 40", "#26211d", 1.2), glaze("M30 16 C24 24 24 34 28 42 L30 42 C28 34 28 24 32 16Z", "white", .25)]


def drop(col):
    return [piece("M32 4 C42 20 52 32 52 42 A20 20 0 0 1 12 42 C12 32 22 20 32 4Z", col),
            glaze("M22 40 C22 32 26 26 30 20 L31 22 C28 28 25 34 25 40Z", "white", .6)]


def log(bark, ring_col):
    return [piece("M10 22 H44 V48 H10Z", bark), line("M16 28 H40 M14 36 H42 M18 43 H38", shade(c(bark), .6), 1.2),
            piece("M44 22 C52 22 56 28 56 35 C56 42 52 48 44 48 C36 48 32 42 32 35 C32 28 36 22 44 22Z", ring_col),
            line("M44 28 C48 28 50 31 50 35 C50 39 48 42 44 42 C40 42 38 39 38 35 C38 31 40 29 44 29", shade(ring_col, .7), 1.2)]


def bark_piece():
    return [piece("M8 20 C20 12 44 12 56 20 L52 46 C40 40 24 40 12 46Z", "darkwood"),
            line("M16 22 L14 40 M26 18 L25 38 M38 18 L39 38 M48 22 L48 40", "#3a2616", 1.4)]


def pouch(powder):
    return [piece("M18 26 C10 36 12 58 32 58 C52 58 54 36 46 26Z", "hide"), piece("M22 18 H42 L46 26 H18Z", "leather"),
            line("M20 25 H44", "#d9a53a", 1.6), piece("M22 18 C24 8 40 8 42 18Z", powder), glaze(circ(32, 13, 3), "white", .5)]


def stone_block(polished):
    if polished:
        return [piece("M10 22 L32 12 L54 22 L54 44 L32 54 L10 44Z", "#a9b0b8"), piece("M10 22 L32 32 L54 22 L32 12Z", "#cfd5dc"),
                piece("M32 32 L54 22 L54 44 L32 54Z", "#8c939c"), glaze("M14 22 L32 14 L36 16 L18 24Z", "white", .5)]
    return [piece("M8 44 L12 26 L26 14 L44 16 L56 30 L54 50 L34 58 L14 54Z", "stone"),
            glaze("M12 26 L26 14 L28 24 L16 32Z", "white", .2), glaze("M34 58 L54 50 L56 30 L44 40Z", "black", .25)]


def egg(shell, spot):
    return [piece("M32 4 C44 4 52 24 52 38 C52 52 43 60 32 60 C21 60 12 52 12 38 C12 24 20 4 32 4Z", shell),
            piece(circ(25, 30, 4.5), spot), piece(circ(41, 44, 5.5), spot), piece(circ(38, 18, 3), spot),
            piece(circ(22, 48, 3), spot), glaze("M20 22 C22 14 26 9 30 8 L30 11 C27 13 24 17 23 23Z", "white", .6)]


# ------------------------------------------------------------------------------------------ orbs

ORB_EMBLEM = {  # the signs of the old client-side orbs, in a 100-unit box centred on 50,50; True = filled
    "ARROW_UP": ("M50 26 L66 46 H56 V72 H44 V46 H34 Z", True),
    "PLUS": ("M44 30 H56 V44 H70 V56 H56 V70 H44 V56 H30 V44 H44 Z", True),
    "SWAP": ("M30 42 H62 V34 L74 46 L62 58 V50 H30 Z M70 58 H38 V66 L26 54 L38 42", False),
    "FLASK": ("M44 28 H56 V44 L70 70 Q72 74 67 74 H33 Q28 74 30 70 L44 44 Z", True),
    "CROWN": ("M28 66 L32 38 L42 52 L50 32 L58 52 L68 38 L72 66 Z", True),
    "SPIRAL": ("M50 50 m0 -4 a4 4 0 1 1 -4 4 a8 8 0 1 1 8 8 a12 12 0 1 1 -12 -12 a16 16 0 1 1 16 16 a20 20 0 1 1 -20 -20", False),
    "STAR": ("M50 26 L56 44 L75 44 L60 55 L66 74 L50 62 L34 74 L40 55 L25 44 L44 44 Z", True),
    "SUN": ("M50 38 a12 12 0 1 1 -0.1 0 Z M50 22 V30 M50 70 V78 M22 50 H30 M70 50 H78 M30 30 L36 36 M64 64 L70 70 M30 70 L36 64 M64 36 L70 30", False),
    "MINUS": ("M30 44 H70 V56 H30 Z", True),
    "WAVE": ("M26 44 Q34 34 42 44 T58 44 T74 44 M26 58 Q34 48 42 58 T58 58 T74 58", False),
    "DROP": ("M50 26 Q66 48 66 58 A16 16 0 0 1 34 58 Q34 48 50 26 Z", True),
    "EYE": ("M24 50 Q50 26 76 50 Q50 74 24 50 Z M50 42 a8 8 0 1 1 -0.1 0 Z", False),
    "CLOVER": ("M50 48 a9 9 0 1 1 0 -1 M50 48 a9 9 0 1 0 0 1 M48 50 a9 9 0 1 1 -1 0 M52 50 a9 9 0 1 0 1 0 M50 56 V76", False),
    "MIRROR": ("M50 24 A16 22 0 1 1 49.9 24 Z M44 34 L40 48 M52 30 L46 50", False),
    "CRACK": ("M50 22 L44 40 L56 48 L42 62 L52 78", False),
    "RIFT": ("M50 22 L58 40 L53 50 L62 62 L50 78 L41 60 L47 50 L39 36 Z", True),
    "HEX": ("M50 26 L71 38 V62 L50 74 L29 62 V38 Z M50 38 L61 44 V56 L50 62 L39 56 V44 Z", True),
    "TENTACLE": ("M36 74 Q30 50 44 40 Q56 32 50 24 M50 74 Q50 54 60 46 Q70 38 64 28 M64 74 Q70 58 76 52", False),
    "MOON": ("M58 26 A24 24 0 1 0 58 74 A18 18 0 1 1 58 26 Z", True),
    "FLAME": ("M50 24 Q66 42 60 56 Q70 52 66 64 A16 16 0 0 1 34 62 Q30 50 42 44 Q42 54 48 54 Q40 38 50 24 Z", True),
    "HEART": ("M50 72 L30 52 A11 11 0 0 1 50 36 A11 11 0 0 1 70 52 Z", True),
    # 1.35.0: a whetstone's bar with its edge, an armour plate with rivets, a lifted veil, a catalyst's stoppered vial.
    "WHET": ("M26 58 L62 34 L74 42 L38 66 Z M34 54 L60 37", True),
    "PLATE": ("M30 30 H70 V52 Q70 68 50 76 Q30 68 30 52 Z M38 38 a3 3 0 1 1 -0.1 0 Z M62 38 a3 3 0 1 1 -0.1 0 Z", True),
    "VEIL": ("M26 34 Q50 22 74 34 L70 70 Q60 62 50 70 Q40 62 30 70 Z M40 46 a3 3 0 1 1 -0.1 0 Z M60 46 a3 3 0 1 1 -0.1 0 Z", True),
    "VIAL": ("M44 24 H56 V30 H54 V42 Q66 48 66 60 A16 16 0 0 1 34 60 Q34 48 46 42 V30 H44 Z", True),
    "SKULL": ("M34 48 A16 16 0 1 1 66 48 V58 H60 V66 H40 V58 H34 Z M42 46 a4 4 0 1 1 -0.1 0 Z M58 46 a4 4 0 1 1 -0.1 0 Z", True),
    "DOTS": ("M40 40 a6 6 0 1 1 -0.1 0 Z M60 40 a6 6 0 1 1 -0.1 0 Z M50 58 a6 6 0 1 1 -0.1 0 Z M34 60 a4 4 0 1 1 -0.1 0 Z M66 60 a4 4 0 1 1 -0.1 0 Z", True),
    "RUNE": ("M50 24 V76 M50 36 L64 26 M50 50 L36 40 M50 50 L64 62", False),
    "GEM": ("M36 36 H64 L74 48 L50 76 L26 48 Z", True),
    "COIN": ("M50 30 a20 20 0 1 1 -0.1 0 Z M50 38 V62 M44 42 H55 Q60 42 60 47 Q60 50 50 50 Q40 50 40 55 Q40 58 45 58 H56", False),
    "CRYSTAL": ("M50 22 L64 40 L58 76 H42 L36 40 Z", True),
    "BOOK": ("M28 32 Q40 28 50 34 Q60 28 72 32 V70 Q60 66 50 72 Q40 66 28 70 Z M50 34 V72", False),
    "PAW": ("M50 52 C62 52 70 62 66 70 C62 76 38 76 34 70 C30 62 38 52 50 52 Z M36 36 a6 7 0 1 1 -0.1 0 Z M64 36 a6 7 0 1 1 -0.1 0 Z M28 50 a5 6 0 1 1 -0.1 0 Z M72 50 a5 6 0 1 1 -0.1 0 Z", True),
}
ORBS = {
    "ORB_OF_TRANSMUTATION": ("#7fb8e8", "ARROW_UP", False), "ORB_OF_AUGMENTATION": ("#6fa0e0", "PLUS", False),
    "ORB_OF_ALTERATION": ("#8a9cff", "SWAP", False), "ORB_OF_ALCHEMY": ("#e8c060", "FLASK", False),
    "REGAL_ORB": ("#e0b040", "CROWN", False), "CHAOS_ORB": ("#d4a030", "SPIRAL", False),
    "EXALTED_ORB": ("#f0e0b0", "STAR", True), "DIVINE_ORB": ("#ffe8a0", "SUN", True),
    "ORB_OF_ANNULMENT": ("#c8d0e0", "MINUS", False), "ORB_OF_SCOURING": ("#b8c8c8", "WAVE", False),
    "BLESSED_ORB": ("#a8e0f0", "DROP", False), "VAAL_ORB": ("#e04030", "EYE", True),
    "ORB_OF_CHANCE": ("#90d070", "CLOVER", False), "MIRROR_OF_KALANDRA": ("#e8f4ff", "MIRROR", True),
    "FRACTURING_ORB": ("#c0a070", "CRACK", False), "SHAPERS_ORB": ("#7fc8f0", "HEX", True),
    "ELDER_ORB": ("#b080e0", "TENTACLE", True), "ABYSS_ORB": ("#8a4fe8", "RIFT", True),
    "ORB_OF_REGRET": ("#9aa8b8", "MOON", False), "EMPOWERING_ORB": ("#f08040", "FLAME", False),
    "MERCY_ORB": ("#80e0c0", "HEART", False), "PERIL_ORB": ("#e05060", "SKULL", False),
    "HORDE_ORB": ("#c09060", "DOTS", False), "MAGUS_ORB": ("#8888ff", "RUNE", False),
    "ELITE_ORB": ("#ffff77", "GEM", False), "BOUNTY_ORB": ("#e8cf94", "COIN", False),
    "TREASURE_ORB": ("#d8b060", "GEM", False), "GILDED_ORB": ("#f0d060", "COIN", True),
    "WARDEN_ORB": ("#b05050", "CROWN", False), "HELMET_SCROLL": ("#c8b8e8", "RUNE", False),
    "GLOVES_SCROLL": ("#b8c8e8", "RUNE", False), "BOOTS_SCROLL": ("#b8e0c8", "RUNE", False),
    "WEAPON_SCROLL": ("#e8c0b0", "RUNE", False), "ESSENCE_ORB": ("#b07fe0", "CRYSTAL", False),
    "SCRIBE_ORB": ("#d8c8a0", "BOOK", False), "GLASSBLOWERS_BAUBLE": ("#9fd8e8", "FLASK", False),
    "PET_ORB_UPGRADE": ("#e8a060", "PAW", False), "PET_ORB_REROLL": ("#d4a030", "PAW", False),
    "PET_ORB_AUGMENT": ("#6fa0e0", "PAW", False), "PET_ORB_DIVINE": ("#ffe8a0", "PAW", True),
    "PET_ORB_ANNUL": ("#c8d0e0", "PAW", False), "PET_ORB_GROWTH": ("#80d070", "PAW", False),
    # 1.35.0: quality, unveiling and the catalysts, each vial in its kind's colour.
    "WHETSTONE": ("#b8b0a0", "WHET", False), "ARMOURERS_SCRAP": ("#98a4b0", "PLATE", False),
    "UNVEILING_ORB": ("#c0a0f0", "VEIL", True),
    "CATALYST_LIFE": ("#e06070", "VIAL", False), "CATALYST_DEFENCE": ("#90a8c0", "VIAL", False),
    "CATALYST_ELEMENTAL": ("#70c8e8", "VIAL", False), "CATALYST_PHYSICAL": ("#c09070", "VIAL", False),
    "CATALYST_CHAOS": ("#80c060", "VIAL", False), "CATALYST_SPEED": ("#e8d070", "VIAL", False),
    "CATALYST_ATTRIBUTE": ("#d0a0e0", "VIAL", False), "CATALYST_CASTER": ("#8090f0", "VIAL", False),
}
# An omen (1.35.0): the orb it changes, drawn as a bone talisman in that orb's colour; the side it keeps to, if any.
OMENS = {
    "SINISTRAL_CHAOS": ("CHAOS_ORB", "L"), "DEXTRAL_CHAOS": ("CHAOS_ORB", "R"),
    "SINISTRAL_EXALTATION": ("EXALTED_ORB", "L"), "DEXTRAL_EXALTATION": ("EXALTED_ORB", "R"), "GREATER_EXALTATION": ("EXALTED_ORB", None),
    "SINISTRAL_CORONATION": ("REGAL_ORB", "L"), "DEXTRAL_CORONATION": ("REGAL_ORB", "R"),
    "SINISTRAL_ANNULMENT": ("ORB_OF_ANNULMENT", "L"), "DEXTRAL_ANNULMENT": ("ORB_OF_ANNULMENT", "R"),
    "LIGHT": ("DIVINE_ORB", None), "CORRUPTION": ("VAAL_ORB", None),
}


def orb(hue, emblem, rays):
    """An orb in a 100 box, scaled to the common 64 by the caller: rays, the disc, a medallion, the sign."""
    parts = []
    if rays:
        for k in range(12):
            a = k * math.pi / 6
            at = lambda r, t: f"{50 + r * math.cos(t):.2f} {50 + r * math.sin(t):.2f}"
            parts.append(piece(f"M{at(36, a - .12)} L{at(48, a)} L{at(36, a + .12)} Z", "#f0e2c0"))
    parts += [piece("M14 50 A36 36 0 1 0 86 50 A36 36 0 1 0 14 50 Z", hue),
              glaze("M50 14 A36 36 0 0 1 80 70 L50 50 Z", "white", .25), glaze("M50 14 A36 36 0 0 0 20 70 L50 50Z", "black", .12),
              piece("M31 50 A19 19 0 1 0 69 50 A19 19 0 1 0 31 50 Z", "#15171a")]
    d, filled = ORB_EMBLEM[emblem]
    sign = str(Path(d) * Matrix("translate(50, 50) scale(0.5) translate(-50, -50)"))
    parts.append(piece(sign, hue) if filled else line(sign, hue, 5))
    parts.append(glaze("M26 36 C30 26 38 20 46 18 L46 22 C39 24 32 30 29 38Z", "white", .55))
    return [dict(p, d=str(Path(p["d"]) * Matrix("scale(0.64)")), **({"line": p["line"] * .64} if "line" in p else {})) for p in parts]


def omen(hue, emblem, side):
    """A talisman in a 100 box, scaled to 64: a cord, a bone plaque in the orb's colour, its sign, and the kept side lit."""
    parts = [line("M50 6 Q50 14 50 18", "#8a5a34", 3),
             piece("M50 16 L78 32 V66 L50 88 L22 66 V32 Z", "bone"),
             piece("M50 24 L70 36 V62 L50 78 L30 62 V36 Z", hue),
             glaze("M50 24 L70 36 V62 L50 50 Z", "white", .22)]
    if side:
        half = "M50 24 L30 36 V62 L50 78 Z" if side == "L" else "M50 24 L70 36 V62 L50 78 Z"
        parts.append(glaze(half, "black", .28))
    d, filled = ORB_EMBLEM[emblem]
    sign = str(Path(d) * Matrix("translate(50, 51) scale(0.45) translate(-50, -50)"))
    parts.append(piece(sign, "#15171a") if filled else line(sign, "#15171a", 2.6))
    parts += [piece(circ(50, 12, 4), "gold"), glaze("M30 36 L50 24 L50 30 L35 39 Z", "white", .5)]
    return [dict(p, d=str(Path(p["d"]) * Matrix("scale(0.64)")), **({"line": p["line"] * .64} if "line" in p else {})) for p in parts]


def collar(band, stud, tag):
    """A pet's collar (1.35.0): an open band with a buckle, studs round it and a hanging tag."""
    parts = [piece(circ(32, 26, 21) + circ(32, 26, 15), band, evenOdd=True),
             piece("M26 44 h12 v7 h-12z", "steel"), piece("M29 46 h6 v3 h-6z", "#15171a")]
    parts += [piece(circ(32 + 18 * math.cos(a), 26 + 18 * math.sin(a), 2), stud) for a in (math.pi * k / 4 for k in (4, 5, 6, 7, 0))]
    parts += [line("M32 51 V54", "gold", 1.6), piece("M32 53 L40 60 L32 63 L24 60Z", tag), glaze("M32 53 L32 63 L24 60Z", "white", .3),
              glaze("M14 20 C16 12 24 7 32 6 L32 10 C25 11 19 15 17 22Z", "white", .35)]
    return parts


def chest(body, band, lock, gem=None):
    """A loot chest (1.71.0): a box under a rounded lid, two bands, a lock plate; some carry a gem on the lid."""
    parts = [piece("M10 30 H54 V54 H10Z", body), piece("M10 30 C10 16 54 16 54 30Z", shade(c(body), .85)),
             piece("M18 18 V54 H23 V18Z", band), piece("M41 18 V54 H46 V18Z", band), piece("M8 29 H56 V33 H8Z", band),
             piece("M27 33 H37 V44 H27Z", lock), piece(circ(32, 38, 1.8), "#15171a")]
    if gem: parts += [piece("M32 18 L36 22 L32 26 L28 22Z", gem), glaze("M32 18 L36 22 L32 22Z", "white", .4)]
    parts += [glaze("M12 31 H30 V36 H12Z", "white", .18), glaze("M12 28 C13 20 24 18 30 18 L30 22 C24 22 16 24 14 29Z", "white", .3)]
    return parts


CHESTS = {"WOODEN": ("wood", "bronze", "bronze", None), "IRONBOUND": ("darkwood", "steel", "steel", None),
          "ANCIENT": ("#6a5a3a", "gold", "gold", "#38b8b0"), "VAAL": ("#5a1a22", "#c8323a", "gold", "#e0304a"),
          "ORBS": ("darkwood", "gold", "gold", "#3a9ae8"), "RELIC": ("#3a2a4a", "gold", "gold", "#e8a030"),
          "ESSENCE": ("#2a3a4a", "silver", "silver", "#9b59d6"), "TREASURY": ("#8a6a2a", "gold", "#f2c53a", "#f2c53a"),
          "ARTISAN": ("wood", "steel", "bronze", "#7fcf4a"), "BEAST": ("leather", "bronze", "bronze", "#d44fb0"),
          "WARLORD": ("#2a2a30", "#c8323a", "steel", "#ef8a3a"), "TRIAL": ("#1e2a3a", "runic", "silver", "#5a62e0")}


COLLARS = {"LEATHER": ("leather", "bronze", "bronze"), "STUDDED": ("leather", "steel", "steel"), "BRASS": ("bronze", "gold", "#e0b040"),
           "RUNED": ("darkwood", "runic", "runic"), "ALPHA": ("gold", "silver", "#e0304a")}


# ------------------------------------------------------------------------------------ sprites table

def num(m):
    v = round(float(m.group()), 2)
    return str(int(v)) if v == int(v) else str(v)


def bake(parts, rot):
    out = []
    for p in parts:
        d = p["d"] if not rot else str(Path(p["d"]) * Matrix(f"rotate({rot}deg, 32, 32)"))
        d = re.sub(r"-?\d+\.?\d*(?:e-?\d+)?", num, d).replace(",", " ")
        q = {"d": d, "color": p["color"]}
        if p.get("alpha", 1) != 1: q["alpha"] = p["alpha"]
        if p.get("line"): q["line"] = round(p["line"], 2)
        if p.get("evenOdd"): q["evenOdd"] = True
        out.append(q)
    return {"viewBox": 64, "style": "glass", "paths": out}


class Atlas:
    def __init__(self):
        self.sprites, self.keys = {}, {}

    def put(self, key, name, parts, rot=0):
        name = PREFIX + name.lower()
        if name not in self.sprites:
            self.sprites[name] = bake(parts, rot)
        self.keys[key] = name


def defences(t):
    order = [b for b in ("BASE_ARMOUR", "BASE_EVASION", "BASE_ENERGY_SHIELD") if b in {x["code"] for x in t.get("base", [])}]
    return (order + [None, None])[:2]


WEAPONS = {
    "SWORD": (sword, 40), "BLADE": (rapier, 40), "LONGSWORD": (longsword, 45), "DOUBLESWORD": (greatsword, 45),
    "AXE": (axe, -25), "DOUBLEAXE": (doubleaxe, -20), "WAND": (wand, 35), "BOW": (bow, 30),
    "STAFF": (staff, 40), "SCEPTRE": (sceptre, 35),
}
ARMOUR = {"HELMET": helmet, "BODY": body, "GLOVES": gloves, "BOOTS": boots}
SHIELD_SHAPES = ["TOWER", "BUCKLER", "SPIRIT", "ROUND", "KITE", "SPIKED"]
SHIELD_BY_DEFENCE = {"BASE_ARMOUR": "TOWER", "BASE_EVASION": "BUCKLER", "BASE_ENERGY_SHIELD": "SPIRIT", None: "KITE"}
FLASK_LIQUID = {"LIFE": "#d33b3b", "MANA": "#3a6fe0", "QUICKSILVER": "#dfe6ee", "RUBY": "#e0304a", "SAPPHIRE": "#3a78e8",
                "TOPAZ": "#f2c53a", "GRANITE": "#8e8a84", "JADE": "#4fbf7a", "SILVER": "#eef2f6", "BASALT": "#4a4540",
                "DIAMOND": "#cfefff", "QUARTZ": "#e8e0f0", "AMETHYST": "#9b59d6", "SULPHUR": "#c8d040"}
MAP_SEAL = {"C1": "#c8c8c8", "C2": "#6fa0e0", "C3": "#4fbf7a", "C4": "#f2c53a", "C5": "#ef8a3a", "C6": "#e0304a",
            "C7": "#9fd8e8", "C8": "#38b8b0", "C9": "#c86ad6"}  # 1.40.0: разбитое небо, утонувшая империя, чертоги богов
TOOL_METAL = {"BRONZE": "bronze", "STEEL": "steel", "RUNIC": "runic"}
BOOK_COVER = {"MARAUDER": "#a8322a", "RANGER": "#3f8a3a", "WITCH": "#3a5fc0", "DUELIST": "#c8742a", "TEMPLAR": "#d8c080",
              "SHADOW": "#2f7f86", "SCION": "#c8ccd4"}
ESSENCE_HUE = {"GREED": "#4fbf7a", "CONTEMPT": "#e0304a", "HATRED": "#6fb8f0", "WOE": "#5a62e0", "FEAR": "#9b59d6",
               "ANGER": "#ef6a3a", "TORMENT": "#d4c040", "SORROW": "#8fa8c8", "RAGE": "#d83a3a", "SUFFERING": "#f08a6a",
               "WRATH": "#f2e04a", "DOUBT": "#a88fd0", "LOATHING": "#8a3a6a", "ZEAL": "#f2c53a", "ANGUISH": "#38b8b0",
               "SPITE": "#7fcf4a", "SCORN": "#c86ad6", "ENVY": "#3aa870", "MISERY": "#4a7ab8", "DREAD": "#6a5a8a",
               "INSANITY": "#b04fd6", "HORROR": "#8a2a3a", "DELIRIUM": "#d8d8e8", "HYSTERIA": "#e07ab0"}
ORE_VEIN = {"COPPER": "#d9793a", "IRON": "#a0a8b0", "SILVER": "#e0e6ee", "MITHRIL": "#7fd0e8", "ADAMANT": "#9b6bd6"}
LOG_BARK = {"BIRCH": ("#d8d2c4", "#e8c890"), "OAK": ("#6a4428", "#c89a60"), "YEW": ("#7a3a26", "#d88a60"),
            "IRONWOOD": ("#4a4a50", "#9a9aa0"), "GHOST_ASH": ("#8a9aa8", "#dce8f0"), "WOOD": ("#6a4428", "#c89a60")}
FLUX = {"BLOOD": "#c02a3a", "STONE": "#9a948c", "FIRE": "#f07a2a", "FROST": "#7fd8f0", "STORM": "#b88af0", "QUICK": "#a8e0a0"}
EGG = {"JUNGLE": "#4f9f3e", "ABYSS": "#6a3ab0", "CRYPT": "#8a8474", "ASH": "#d86a3a", "TEMPLE": "#d8b040", "DESERT": "#d8b078",
       "VOLCANO": "#d83a2a", "CITADEL": "#5a7aa8", "MINES": "#8a6a48", "FROST": "#7fc8f0", "CANYON": "#c8643a",
       "BLIGHT": "#8ab83a", "MIRE": "#5a7a4a", "RUINS": "#9a9488", "HIVE": "#e8a82a", "SHORE": "#3ab8c8", "CAVE": "#5a5660",
       "FOREST": "#2f7a3e",
       # 1.40.0: the lands 71–100.
       "SKYREACH": "#bfe3ef", "GLASSWASTE": "#9fd8e8", "STORMPEAK": "#b88af0", "SUNKEN": "#3a6a8a", "CORAL": "#e86a7a", "TIDEVAULT": "#38b8b0", "GODHALL": "#f2c53a", "ASTRAL": "#5a62e0", "OBLIVION": "#3c3a44"}


# Base tiers by the template's level: < 25 plain, 25–54 worked, 55+ ornate with a set stone.
def tier(t):
    level = t.get("level") or t.get("requiredLevel") or 1
    return 0 if level < 25 else 1 if level < 55 else 2


TIER_MAT = {
    "BASE_ARMOUR": ["#8e97a3", "#b9c2cf", "#dfe6ee"],
    "BASE_EVASION": ["#a8784a", "#8b5530", "#5e3822"],
    "BASE_ENERGY_SHIELD": ["#8a93a8", "#7a4fc0", "#5a2fa0"],
}
TIER_METAL = ["bronze", "steel", "#e8eef6"]
TIER_GUARD = ["#8a5a34", "gold", "gold"]
TIER_STONE = "#e0405a"
# Staves and sceptres (the spell weapons beside the wand): a staff's orb and a sceptre's set gem by tier.
TIER_ORB = ["#6fd3c8", "#3a78e8", "#9b59d6"]
TIER_SCEPTRE_GEM = ["#3a9ae8", "#e0405a", "#f2c53a"]

# A unique's theme, read from its name: its glass, its second colour, the sign it bears and the sign's colour.
THEMES = [
    (("VOID", "ABYSS", "CHASM", "MAW", "HOLLOW", "ENTROPY", "SHROUD", "SHADOW", "NIGHT"), "#5a2a9a", "#2a1f3a", "EYE", "#d08af0"),
    (("EMBER", "MOLTEN", "INFERNAL", "ASH", "PHOENIX", "FIRE", "FLAME", "SEETHING", "FURY"), "#e8602a", "#f2c53a", "FLAME", "#ffd070"),
    (("FROST", "RIME", "ICE", "WINTER", "GLACI", "SNOW"), "#7fd8f0", "#dfe6ee", "SNOW", "#ffffff"),
    (("STORM", "THUNDER", "LIGHTNING"), "#f2e04a", "#6a5ae0", "BOLT", "#fff6a0"),
    (("TIDE", "BRINE", "DROWN", "CONCH", "SHORE", "SERPENT", "QUENCH", "NECTAR"), "#2fa8b0", "#1f5a8a", "WAVE", "#bff0f0"),
    (("BLOOD", "CARNAGE", "HEART", "BUTCHER", "FANG", "HEADHUNTER", "BITE", "GLUTTON", "FEAST"), "#b01a2a", "#3a1a1a", "DROP", "#ff7a7a"),
    (("GRAVE", "SOUL", "DEATH", "BONE", "FALLEN", "SEVENTH", "LAST_BREATH", "HEX", "REMORSE"), "#8aa88a", "#e4dcc4", "SKULL", "#efe6cf"),
    (("STAR", "ASTRA", "CHRONO", "TIME", "ETERNAL", "SANDS", "DAWN", "SUN", "SAINT", "SERAPH", "VASTIRI", "PROMISE"), "#f0d890", "#3a3a8a", "STAR", "#fff6d0"),
    (("FORGE", "SMITH", "TITAN", "BULWARK", "BRAZEN", "MIRROR", "KAOM", "BEREK", "VANGUARD", "MAGNATE"), "#c98a4a", "#6a6e78", "HEX", "#f2c53a"),
]


# 1.36.0: the new uniques are drawn by hand: glass, second colour, sign and the sign's colour.
UNIQUE_ART = {
    # 1.41.0: the uniques of levels 75–100.
    "HERALDS_LAST_BREATH": ("#477ed1", "#935338", "WING", "#f0e2c0"),
    "MATRIARCHS_GLASS_HEART": ("#d17347", "#389293", "PRISM", "#f0e2c0"),
    "THUNDERWYRM_FANG": ("#d18047", "#388993", "BOLT", "#f0e2c0"),
    "BRIDGEKEEPERS_OATH": ("#d14777", "#389358", "GATE", "#f0e2c0"),
    "MIRROR_KNIGHTS_VISOR": ("#475ed1", "#936938", "EYE", "#f0e2c0"),
    "ORRERY_COG": ("#4777d1", "#935838", "COMPASS", "#f0e2c0"),
    "SOVEREIGNS_SKYCROWN": ("#479cd1", "#933f38", "CROWN", "#f0e2c0"),
    "SKYBREAKER_MAUL": ("#55d147", "#6f3893", "MOUNTAIN", "#f0e2c0"),
    "STILL_EYE_OF_THE_STORM": ("#9c47d1", "#769338", "SPIRAL", "#f0e2c0"),
    "PRISM_HUNTERS_TROPHY": ("#b047d1", "#699338", "ARROW", "#f0e2c0"),
    "PRAETORS_DROWNED_SEAL": ("#d147be", "#479338", "SKULL", "#f0e2c0"),
    "CORAL_QUEENS_CARAPACE": ("#d1cc47", "#385693", "SHARD", "#f0e2c0"),
    "VAULT_WARDENS_KEY": ("#47c3d1", "#93384a", "COIN", "#f0e2c0"),
    "ORATORS_SILENT_TONGUE": ("#75d147", "#593893", "LIPS", "#f0e2c0"),
    "PEARL_EATERS_COIL": ("#50d147", "#723893", "WHIRL", "#f0e2c0"),
    "LEGION_TRIBUNES_STANDARD": ("#4749d1", "#937638", "SCALES", "#f0e2c0"),
    "KELP_PRIESTS_VESTMENT": ("#47acd1", "#93383b", "HANDS", "#f0e2c0"),
    "EMPERORS_DROWNED_CROWN": ("#d19747", "#387993", "CROWN", "#f0e2c0"),
    "TIDE_OF_EMPIRES": ("#5ed147", "#693893", "WAVE", "#f0e2c0"),
    "TRENCH_DRAKE_SCALE": ("#47d1c7", "#933859", "FANG", "#f0e2c0"),
    "THRESHOLD_GUARDIANS_WARD": ("#d14797", "#389342", "GATE", "#f0e2c0"),
    "JUDGE_OF_SOULS_SCALES": ("#4783d1", "#935038", "SCALES", "#f0e2c0"),
    "SUNSMITHS_HAMMERHAND": ("#47ccd1", "#933850", "ANVIL", "#f0e2c0"),
    "HOLLOW_GODS_EYE": ("#ce47d1", "#559338", "EYE", "#f0e2c0"),
    "AVATAR_OF_WARS_GIRDLE": ("#d14b47", "#38937b", "HORN", "#f0e2c0"),
    "STARGAZERS_LENS": ("#d14757", "#38936d", "STAR", "#f0e2c0"),
    "CROWN_COLLECTORS_HOARD": ("#d1be47", "#385f93", "COIN", "#f0e2c0"),
    "LAST_GODS_MANTLE": ("#47bed1", "#933847", "SUN", "#f0e2c0"),
    "GODSLAYER": ("#8547d1", "#869338", "SKULL", "#f0e2c0"),
    "WALKER_OF_NOTHINGS_STEP": ("#d147a3", "#38933b", "COMET", "#f0e2c0"),
    "VOID_MOTHERS_BROOD": ("#47d1a5", "#933870", "TENTACLE", "#f0e2c0"),
    "ATLAS_OF_ASCENT": ("#474ed1", "#937338", "MAP", "#f0e2c0"),
    "TIERBREAKER": ("#47d19a", "#933878", "CLAW", "#f0e2c0"),
    "PINNACLE_PLATE": ("#47d152", "#803893", "MOUNTAIN", "#f0e2c0"),
    "EVERCLIMB_GREAVES": ("#6047d1", "#938938", "ROAD", "#f0e2c0"),
    "SUMMIT_SIGIL": ("#478cd1", "#934a38", "TRIAD", "#f0e2c0"),
    "HIGHWATER_MARK": ("#476ed1", "#935e38", "WAVE", "#f0e2c0"),
    "APEX_CIRCLET": ("#8e47d1", "#809338", "STAR", "#f0e2c0"),
    "CLIMBERS_GRIP": ("#475cd1", "#936a38", "CLAW", "#f0e2c0"),
    "ZENITH_BOW": ("#d1ae47", "#386a93", "ARROW", "#f0e2c0"),
    "HAWKSWORN_BAND": ("#d9a53a", "#8a5a34", "CROSSHAIR", "#f0e2c0"),
    "THE_LONE_ARROW": ("#5f9a4e", "#8a5a34", "ARROW", "#f0e2c0"),
    "GALLOWS_GRIP": ("#5e3b22", "#b9c2cf", "NOOSE", "#e4dcc4"),
    "STORMCHASER_BOOTS": ("#3a78e8", "#dfe6ee", "BOLT", "#f2d03a"),
    "CANTORS_CIRCLET": ("#8a6ae0", "#d9a53a", "BELL", "#f0e2c0"),
    "BLACKSAP_CLEAVER": ("#2b1d2e", "#6a3ab0", "ROOT", "#80c060"),
    "SUPPLICANTS_SHAWL": ("#e6d4a6", "#7a4fc0", "HANDS", "#7a4fc0"),
    "DEFLECTORS_BUCKLER": ("#b9c2cf", "#c98a4a", "SWIRL", "#f0e2c0"),
    "OXHIDE_HAUBERK": ("#a8784a", "#5e3b22", "HORN", "#e4dcc4"),
    "GRANITE_WILL": ("#827c73", "#dfe6ee", "MOUNTAIN", "#e4dcc4"),
    "RECOIL_SIGIL": ("#8a6ae0", "#dfe6ee", "RETURN", "#bfe3ef"),
    "THUNDERKISS": ("#f2d03a", "#3a78e8", "LIPS", "#3a78e8"),
    "WINTERS_LONG_BREATH": ("#6fc8f0", "#dfe6ee", "SNOW", "#dfe6ee"),
    "CANKERBLOOM": ("#7fcf4a", "#5a3a8a", "BLOOM", "#c86ad6"),
    "THE_STORMGLASS": ("#bfe3ef", "#3a9ae8", "HOURGLASS", "#f2d03a"),
    "TIDEMOTHERS_SASH": ("#3a9ae8", "#38b8b0", "WAVE", "#dfe6ee"),
    "CHARGEBEARERS_YOKE": ("#c02a3a", "#d9a53a", "CHAIN", "#f0e2c0"),
    "SIGIL_OF_THE_HIDDEN": ("#3c3a44", "#9b59d6", "EYE", "#c86ad6"),
    "CRESTFALL_PAULDRONS": ("#d44fb0", "#e8bb45", "WING", "#f0e2c0"),
    "EXECUTIONERS_ASSIZE": ("#b9c2cf", "#c02a3a", "SCALES", "#e4dcc4"),
    "NESTWARDENS_HOOD": ("#8a5a34", "#e4dcc4", "EGG", "#e4dcc4"),
    "OLD_GREYS_TORC": ("#8e97a3", "#d9a53a", "PAW", "#e4dcc4"),
    "RIMELICHS_PHYLACTERY": ("#6fc8f0", "#3c3a44", "SKULL", "#bfe3ef"),
    "HERALDS_TONGUE": ("#6a3ab0", "#15171a", "TONGUE", "#c86ad6"),
    "KHANS_BLOODPRICE": ("#c02a3a", "#8a5a34", "FANG", "#f0e2c0"),
    "MUMMY_KINGS_WRAPPINGS": ("#e6d4a6", "#d9a53a", "ANKH", "#8a5a34"),
    "PANTHERS_SHADOWSTEP": ("#15171a", "#5f9a4e", "CLAW", "#9fd8e8"),
    "SPORELORDS_CROWN": ("#7fcf4a", "#8a5a34", "MUSHROOM", "#e4dcc4"),
    "FURNACE_HEART_GAUNTLETS": ("#ef6a3a", "#3c3a44", "ANVIL", "#f2c53a"),
    "PLAGUE_DOCTORS_BEAK": ("#3c3a44", "#7fcf4a", "BEAK", "#e4dcc4"),
    "WHISPERERS_LOOP": ("#9b59d6", "#15171a", "SPIRAL", "#c86ad6"),
    "ENDLESS_WARDENS_BULWARK": ("#3c3a44", "#d9a53a", "GATE", "#f2c53a"),
    "GRIN_OF_THE_DEEP": ("#6a3ab0", "#15171a", "GRIN", "#a26bff"),
    "VOIDBARGAIN": ("#15171a", "#8a4fe8", "RIFT", "#a26bff"),
    "CHASM_HUNGER_BELT": ("#5a3a8a", "#7fcf4a", "MAW", "#80c060"),
    "TWINFANG_DIRK": ("#8a4fe8", "#dfe6ee", "TWINFANG", "#f0e2c0"),
    "ABYSSAL_ONSLAUGHT_GREAVES": ("#15171a", "#6a3ab0", "COMET", "#a26bff"),
    "HEART_OF_THE_UNDERTOW": ("#38b8b0", "#15171a", "WHIRL", "#a26bff"),
    "SACRIFICIAL_THORN": ("#e04030", "#15171a", "THORN", "#e04030"),
    "BLOODDEBT_BRACERS": ("#c02a3a", "#d9a53a", "COIN", "#f2c53a"),
    "THE_BRICKED_IDOL": ("#827c73", "#e04030", "IDOL", "#e04030"),
    "OMENWEAVE_MANTLE": ("#e04030", "#3c3a44", "OMEN", "#e8f4ff"),
    "WHETSTONE_HEART": ("#b8b0a0", "#c98a4a", "WHET", "#f0e2c0"),
    "COLLAR_OF_THE_FIRST_HOUND": ("#8b5530", "#c98a4a", "PAW", "#e4dcc4"),
    "PACKLEADERS_BAND": ("#d9a53a", "#8b5530", "HOWL", "#f0e2c0"),
    "IRONJAW_COLLAR": ("#8e97a3", "#b9c2cf", "JAW", "#dfe6ee"),
    "BEASTSOUL_CHOKER": ("#c02a3a", "#8b5530", "HEART", "#f0e2c0"),
    "WILDMOTHERS_GARLAND": ("#5f9a4e", "#e89a2a", "LEAF", "#e4dcc4"),
    "THE_NEST": ("#a8784a", "#efe6cf", "EGG", "#f0e2c0"),
    "WARM_SCALES": ("#d8602a", "#3a8a6a", "FLAME", "#ffd070"),
    "CARTOGRAPHERS_COMPASS": ("#e8bb45", "#8a5a34", "COMPASS", "#f0e2c0"),
    "WAYFARERS_TREADS": ("#a8784a", "#5f9a4e", "ROAD", "#f0e2c0"),
    "ATLAS_WANDERERS_CLOAK": ("#e6d4a6", "#3a78e8", "MAP", "#8a5a34"),
    "PRISMSIGHT_LENS": ("#bfe3ef", "#9b59d6", "PRISM", "#eaf6ff"),
    "SHARDBLOOD_EDGE": ("#9fd8e8", "#c02a3a", "SHARD", "#eaf6ff"),
    "GEODE_OF_PATIENCE": ("#9b59d6", "#827c73", "GEODE", "#eaf6ff"),
    "BURDEN_OF_ATLAS": ("#827c73", "#d9a53a", "GLOBE", "#f0e2c0"),
    "STARFALL_SCEPTRE": ("#1e2240", "#f2d03a", "STAR", "#f2d03a"),
    "ECLIPSE_REGALIA": ("#15171a", "#f2c53a", "ECLIPSE", "#f2c53a"),
    "KINGDOMGRINDER": ("#2b1d2e", "#c02a3a", "MAW", "#e04030"),
    "BULWARK_OF_ENDLESS_DAWN": ("#f2c53a", "#dfe6ee", "SUN", "#f2c53a"),
    "CROWN_OF_SPORES_AND_ASH": ("#ef6a3a", "#7fcf4a", "FLAME", "#7fcf4a"),
    "THE_CHARGED_HEART": ("#c02a3a", "#3a78e8", "TRIAD", "#f2c53a"),
    # Staves and sceptres: the spell weapons beside the wand.
    "FIRSTSPRING_SAPLING": ("#7fcf4a", "#8a5a34", "LEAF", "#f0e2c0"),
    "EMBERSPIRE": ("#e8602a", "#3c3a44", "FLAME", "#ffd070"),
    "RIMEWARD": ("#7fd8f0", "#dfe6ee", "SNOW", "#ffffff"),
    "SKYPIERCER": ("#f2e04a", "#3a3a8a", "BOLT", "#fff6a0"),
    "PILGRIMS_BURDEN": ("#e6d4a6", "#8a5a34", "ROAD", "#8a5a34"),
    "TIDEBINDER": ("#2fa8b0", "#1f5a8a", "WAVE", "#bff0f0"),
    "WITCHWOOD_CROOK": ("#5a3a8a", "#2b1d2e", "THORN", "#80c060"),
    "PRISMSPIRE": ("#bfe3ef", "#9b59d6", "PRISM", "#eaf6ff"),
    "SEVENFOLD_SKY": ("#1e2240", "#f0d890", "STAR", "#fff6d0"),
    "LICHBONE_STAFF": ("#6fc8f0", "#e4dcc4", "SKULL", "#bfe3ef"),
    "WORLDTREE_BOUGH": ("#5f9a4e", "#5e3b22", "ROOT", "#e4dcc4"),
    "VOIDSPIRE": ("#15171a", "#8a4fe8", "RIFT", "#a26bff"),
    "ARCHMAGES_BULWARK": ("#8a6ae0", "#d9a53a", "GATE", "#f0e2c0"),
    "CHIEFTAINS_EMBER": ("#c8742a", "#5e3b22", "HORN", "#f2c53a"),
    "GLACIAL_EDICT": ("#9fd8e8", "#3a5fa0", "SNOW", "#ffffff"),
    "SUNBRAND": ("#f2c53a", "#c98a4a", "FLAME", "#fff6d0"),
    "STORMCROWN_SCEPTRE": ("#3a78e8", "#f2e04a", "BOLT", "#fff6a0"),
    "OATHBINDER": ("#dfe6ee", "#8a5a34", "SCALES", "#f0e2c0"),
    "USURPERS_SCEPTRE": ("#b01a2a", "#d9a53a", "CROWN", "#f2c53a"),
    "PENITENTS_ROD": ("#d8c080", "#3c3a44", "BELL", "#f0e2c0"),
    "TRIUNE_REGALIA": ("#9b59d6", "#e8bb45", "TRIAD", "#f0e2c0"),
    "PYRE_OF_KINGS": ("#d83a3a", "#15171a", "SKULL", "#ffd070"),
    "IRON_MANDATE": ("#8e97a3", "#c02a3a", "ANVIL", "#dfe6ee"),
    "DAWNBRINGER": ("#f2c53a", "#e8602a", "SUN", "#fff6d0"),
    "WORLDSPINE": ("#827c73", "#3a78e8", "GLOBE", "#f0e2c0"),
    "GODKINGS_MANDATE": ("#f0d890", "#6a3ab0", "CROWN", "#f2c53a"),
    # Uniques built around the mechanics taken out of the ordinary affix pools.
    "TREASURE_DELVERS_HOOD": ("#e8b04a", "#5e3b22", "COMPASS", "#fff0b0"),
    "STONEWARDENS_HAUBERK": ("#8e97a3", "#6a5a44", "MOUNTAIN", "#e4dcc4"),
    "BRAWLERS_ABANDON": ("#c8742a", "#3c3a44", "HANDS", "#f2c53a"),
    "DESERTERS_STRIDE": ("#9fd8e8", "#7a5234", "ROAD", "#ffffff"),
    "TORTOISE_OATH": ("#5f9a4e", "#8a5a34", "GEODE", "#e4dcc4"),
    "STORMHAWK_PINIONS": ("#3a78e8", "#e8602a", "BOLT", "#fff6a0"),
    "SCARKNIT_CORD": ("#c02a3a", "#e4dcc4", "NOOSE", "#f0e2c0"),
    "GIANTSLAYERS_TITHE": ("#a8322a", "#8e97a3", "MAW", "#f2c53a"),
    "HOARFROST_SEAL": ("#bfe3ef", "#3a5fa0", "SNOW", "#ffffff"),
    "ORACLES_TALLY": ("#8a6ae0", "#f0d890", "HOURGLASS", "#f0e2c0"),
    "GLASSCUTTERS_PENDANT": ("#dfeef6", "#9fd8e8", "SHARD", "#e0405a"),
    "THE_BARBED_HUNT": ("#b01a2a", "#7fcf4a", "THORN", "#7fcf4a"),
    "SHARD_OF_ABANDON": ("#e0405a", "#15171a", "CLAW", "#f2c53a"),
    "THE_FIRST_CUT": ("#dfe6ee", "#b01a2a", "FANG", "#e0405a"),
    "MERCYS_END": ("#c8ccd4", "#2b1d2e", "CROSSHAIR", "#e0405a"),
    "REAVERS_FERVOUR": ("#d83a3a", "#5e3b22", "HOWL", "#ffd070"),
    "FINAL_ARGUMENT": ("#827c73", "#c02a3a", "ANVIL", "#f2c53a"),
    "TAINTED_RAINBOW": ("#9b59d6", "#7fcf4a", "PRISM", "#f2e04a"),
    "TEMPEST_NEEDLE": ("#f2e04a", "#1e2240", "WHIRL", "#fff6a0"),
    "THE_SLOW_PYRE": ("#ef6a3a", "#2b1d2e", "SPIRAL", "#ffd070"),
    "MARTYRS_SCEPTRE": ("#f0e2c0", "#b01a2a", "ANKH", "#e0405a"),
}


def theme(code):
    if code in UNIQUE_ART:
        return UNIQUE_ART[code]
    for keys, glass, second, sign, sign_col in THEMES:
        if any(k in code for k in keys):
            return glass, second, sign, sign_col
    hue = hashed(code)
    return hue, shade(hue, .45), "RUNE", "#f2c53a"


ACCENT_EMBLEM = {
    "SNOW": ("M50 22 V78 M26 36 L74 64 M74 36 L26 64", False),
    "BOLT": ("M58 20 L34 54 H50 L42 80 L68 44 H52 Z", True),
    # 1.36.0: hand-drawn signs of the new uniques.
    "CROSSHAIR": ("M50 32 a18 18 0 1 1 -0.1 0 Z M50 22 V40 M50 60 V78 M22 50 H40 M60 50 H78", False),
    "ARROW": ("M28 72 L68 32 M68 32 H54 M68 32 V46 M28 72 L22 66 M28 72 L34 78 M35 65 L29 59 M35 65 L41 71", False),
    "NOOSE": ("M50 22 V36 M50 36 a10 13 0 1 0 0.1 0 Z M43 34 H57 M44 29 H56", False),
    "BELL": ("M50 24 Q36 26 36 44 V58 L28 66 H72 L64 58 V44 Q64 26 50 24 Z M44 70 A6 6 0 0 0 56 70 Z", True),
    "ROOT": ("M50 22 V50 M50 50 Q40 58 30 76 M50 50 Q52 62 46 78 M50 50 Q62 58 72 74 M40 62 L30 62 M60 60 L68 54", False),
    "HANDS": ("M48 24 Q40 36 38 56 L34 76 H48 Z M52 24 Q60 36 62 56 L66 76 H52 Z", False),
    "SWIRL": ("M24 30 L50 56 L76 30 M28 70 H72 M50 56 V70", False),
    "HORN": ("M24 30 Q22 58 44 60 H56 Q78 58 76 30 Q70 50 56 50 H44 Q30 50 24 30 Z M44 60 V72 H56 V60 Z", True),
    "MOUNTAIN": ("M20 74 L42 34 L50 48 L58 28 L80 74 Z", True),
    "RETURN": ("M68 42 A20 20 0 1 0 70 58 M68 42 V26 M68 42 H52", False),
    "LIPS": ("M24 50 Q36 34 50 42 Q64 34 76 50 Q64 70 50 68 Q36 70 24 50 Z M24 50 Q50 56 76 50", False),
    "BLOOM": ("".join(f"M{50 + 14 * math.cos(a):.1f} {50 + 14 * math.sin(a) - 9:.1f} a9 9 0 1 1 -0.1 0 Z" for a in (math.pi * (2 * k / 5 - .5) for k in range(5))), True),
    "HOURGLASS": ("M32 24 H68 L52 50 L68 76 H32 L48 50 Z", True),
    "CHAIN": ("M32 36 H46 A8 8 0 0 1 46 52 H32 A8 8 0 0 1 32 36 Z M54 48 H68 A8 8 0 0 1 68 64 H54 A8 8 0 0 1 54 48 Z", False),
    "WING": ("M26 72 Q28 40 76 24 Q66 40 70 46 Q58 50 62 58 Q48 60 50 68 Q38 66 26 72 Z", True),
    "SCALES": ("M50 24 V74 M36 74 H64 M26 36 H74 M26 36 L20 56 H32 Z M74 36 L68 56 H80 Z", False),
    "EGG": ("M50 22 Q70 24 70 54 A20 20 0 0 1 30 54 Q30 24 50 22 Z", True),
    "TONGUE": ("M32 30 Q50 42 68 30 M50 36 V58 Q50 66 40 76 M50 58 Q50 66 60 76", False),
    "FANG": ("M32 26 H68 L55 74 Q50 80 45 74 Z", True),
    "ANKH": ("M50 22 a9 12 0 1 1 -0.1 0 Z M30 48 H70 M50 46 V78", False),
    "CLAW": ("M30 26 Q44 50 34 76 M48 24 Q62 50 52 78 M66 26 Q78 50 70 74", False),
    "MUSHROOM": ("M22 52 Q24 26 50 26 Q76 26 78 52 Z M44 54 H56 V76 H44 Z", True),
    "ANVIL": ("M22 34 H74 Q74 46 60 48 V58 H66 V70 H34 V58 H40 V48 Q26 46 22 34 Z", True),
    "BEAK": ("M28 30 Q52 26 58 40 L80 72 L52 56 Q30 58 28 44 Z", True),
    "GATE": ("M28 76 V40 Q28 24 50 24 Q72 24 72 40 V76 M28 50 H72 M39 30 V76 M50 24 V76 M61 30 V76", False),
    "GRIN": ("M22 42 Q50 82 78 42 Q50 60 22 42 Z M34 51 V58 M42 55 V63 M50 56 V65 M58 55 V63 M66 51 V58", False),
    "MAW": ("M22 32 Q50 20 78 32 L70 44 L63 34 L57 44 L50 34 L43 44 L37 34 L30 44 Z M22 68 Q50 80 78 68 L70 56 L63 66 L57 56 L50 66 L43 56 L37 66 L30 56 Z", True),
    "TWINFANG": ("M28 26 H46 L39 76 Z M54 26 H72 L61 76 Z", True),
    "COMET": ("M64 38 a12 12 0 1 1 -0.1 0 Z M54 44 L22 30 M52 52 L20 56 M56 60 L32 78", False),
    "WHIRL": ("M50 26 A24 24 0 1 1 26 50 M50 36 A14 14 0 1 1 36 50 M50 46 A4 4 0 1 1 46 50", False),
    "THORN": ("M50 22 L56 50 L50 78 L44 50 Z M53 38 L68 30 M47 56 L32 48 M53 64 L66 60", False),
    "IDOL": ("M40 22 H60 V36 H68 V50 H60 V78 H40 V50 H32 V36 H40 Z", True),
    "OMEN": ("M50 22 L78 72 H22 Z M36 56 Q50 44 64 56 Q50 66 36 56 Z", False),
    "JAW": ("M22 32 H78 V46 Q78 74 50 78 Q22 74 22 46 Z", True),
    "HOWL": ("M44 28 A22 22 0 1 0 44 72 A16 16 0 1 1 44 28 Z M60 36 Q68 50 60 64 M70 28 Q82 50 70 72", False),
    "LEAF": ("M24 76 Q24 30 76 24 Q72 72 24 76 Z M24 76 L60 40", False),
    "COMPASS": ("M50 20 L57 50 L50 80 L43 50 Z M20 50 L50 44 L80 50 L50 56 Z", True),
    "ROAD": ("M38 78 L46 22 M62 78 L54 22 M50 74 V66 M50 58 V50 M50 42 V36", False),
    "MAP": ("M22 30 L40 24 L60 30 L78 24 V70 L60 76 L40 70 L22 76 Z M40 24 V70 M60 30 V76", False),
    "PRISM": ("M50 24 L74 70 H26 Z M20 52 L38 49 M62 46 L80 38 M62 52 L80 54 M62 58 L78 70", False),
    "SHARD": ("M42 20 L64 34 L58 80 L38 58 Z", True),
    "GEODE": ("M50 22 L74 36 V64 L50 78 L26 64 V36 Z M40 44 L50 38 L60 44 V56 L50 62 L40 56 Z", False),
    "GLOBE": ("M50 26 a24 24 0 1 1 -0.1 0 Z M26 50 H74 M50 26 Q34 50 50 74 M50 26 Q66 50 50 74", False),
    "ECLIPSE": ("M50 34 a16 16 0 1 1 -0.1 0 Z M50 20 V28 M50 72 V80 M20 50 H28 M72 50 H80 M57 40 a10 10 0 1 1 -0.1 0 Z", False),
    "TRIAD": ("M50 24 a9 9 0 1 1 -0.1 0 Z M31 56 a9 9 0 1 1 -0.1 0 Z M69 56 a9 9 0 1 1 -0.1 0 Z", True),
}


def accent(sign, col, x, y, size):
    """A sign in a dark medallion, [size] wide at x,y: the mark a unique bears."""
    d, filled = ACCENT_EMBLEM.get(sign) or ORB_EMBLEM[sign]
    k = size / 60
    placed = str(Path(d) * Matrix(f"translate({x}, {y}) scale({k}) translate(-50, -50)"))
    return [piece(circ(x, y, size / 2), "#15171a"), piece(placed, col) if filled else line(placed, col, max(1.4, 9 * k))]


def set_stone(x, y, size):
    h = size / 2
    return [piece(f"M{x} {y - h} L{x + h} {y} L{x} {y + h} L{x - h} {y}Z", TIER_STONE), glaze(f"M{x} {y - h} L{x} {y + h} L{x - h} {y}Z", "white", .35)]


def rays(col="#f0e2c0"):
    out = []
    for k in range(12):
        a = k * math.pi / 6
        at = lambda r, t: f"{32 + r * math.cos(t):.2f} {32 + r * math.sin(t):.2f}"
        out.append(piece(f"M{at(22, a - .14)} L{at(31, a)} L{at(22, a + .14)} Z", col))
    return out


# Where a sign or a set stone sits on each drawing, before the drawing is turned: x, y, size.
ANCHOR = {
    "SWORD": (32, 42.5, 9), "BLADE": (32, 41, 9), "LONGSWORD": (32, 44.5, 9), "DOUBLESWORD": (32, 43, 10),
    "AXE": (46, 21, 11), "DOUBLEAXE": (32, 22, 11), "BOW": (40.5, 32, 9), "WAND": None,
    "HELMET": {"BASE_ARMOUR": (32, 22, 12), "BASE_EVASION": (32, 24, 12), "BASE_ENERGY_SHIELD": (32, 43, 11)},
    "BODY": (32, 30, 12), "GLOVES": (27, 31, 11), "BOOTS": {"BASE_ARMOUR": (27, 23, 10), "BASE_EVASION": (27, 22, 11), "BASE_ENERGY_SHIELD": (28, 25, 10)},
    "SHIELD": (32, 32, 16), "BELT": (32, 32, 10), "FLASK_ROUND": (32, 47, 11), "FLASK_TALL": (32, 48, 10),
    "QUIVER": (33, 40, 11), "WINGS": (32, 32, 11),
    # 1.36.0: only the hand-drawn uniques bear a sign on these.
    "RING": (32, 40, 11), "AMULET": (32, 47, 12), "JEWEL": (32, 32, 16), "COLLAR": (32, 26, 14), "WAND_SIGN": (32, 42, 10),
    "STAFF": (32, 38, 10), "SCEPTRE": (32, 42, 9),
}


def anchor(key, style=None):
    a = ANCHOR.get(key)
    return a.get(style or "BASE_ARMOUR") if isinstance(a, dict) else a


def mark(parts, look, where):
    """A unique's sign, or a top tier's set stone, laid on a drawing at [where]."""
    if not where:
        return parts
    if look["unique"]:
        return parts + accent(look["sign"], look["sign_col"], *where)
    if look["tier"] == 2:
        return parts + set_stone(where[0], where[1], where[2] * .8)
    return parts


def equipment(atlas, t):
    code, slot = t["code"], t["slot"]
    key = f"equipment.{code}"
    first, second = defences(t)
    unique = t.get("rarity") in ("UNIQUE", "MYTHICAL")
    glass, glass2, sign, sign_col = theme(code) if unique else (None, None, None, None)
    look = {"unique": unique, "tier": tier(t), "sign": sign, "sign_col": sign_col}
    lv = look["tier"]
    halo = rays() if t.get("rarity") == "MYTHICAL" else []
    name = f"u_{code}" if unique else None

    def drawn(parts, where):
        return mark(parts, look, anchor(where)) if code in UNIQUE_ART else parts

    def put(base_name, parts, rot=0):
        atlas.put(key, name or base_name, halo + parts, rot)

    if slot in ("WEAPON_1H", "WEAPON_2H"):
        wt = t.get("weaponType") or next((w for w in WEAPONS if w in code), "SWORD")
        draw, rot = WEAPONS.get(wt, WEAPONS["SWORD"])
        metal = glass or TIER_METAL[lv]
        guard = glass2 or TIER_GUARD[lv]
        if draw is wand:
            gem = glass or stone(code)
            put(f"wand_{gem[1:]}", drawn(wand(gem, "darkwood"), "WAND_SIGN"), rot)
            return
        if draw is staff:
            orb = glass or TIER_ORB[lv]
            put(f"staff_{orb[1:]}_{lv}", mark(staff(orb, glass2 or ["wood", "darkwood", "darkwood"][lv]), look, anchor(wt)), rot)
            return
        if draw is sceptre:
            parts = sceptre(glass or TIER_METAL[lv], glass2 or TIER_SCEPTRE_GEM[lv], "darkwood")
            put(f"sceptre_{lv}", mark(parts, look, anchor(wt)), rot)
            return
        if draw is bow:
            parts = bow(glass2 or ["wood", "#7a3a26", "#5e3b22"][lv], "leather")
        elif draw in (axe, doubleaxe):
            parts = draw(metal, glass2 or "wood")
        else:
            parts = draw(metal, guard, "leather")
        put(f"{wt}_{lv}", mark(parts, look, anchor(wt)), rot)
    elif slot in ARMOUR:
        style = first or "BASE_EVASION"
        mat = glass or TIER_MAT[style][lv]
        trim = glass2 or (DEFENCE[second][1] if second else ["#a8784a", MAT["gold"], MAT["gold"]][lv])
        put(f"{slot}_{style}_{second}_{lv}", mark(ARMOUR[slot](style, mat, trim), look, anchor(slot, style)))
    elif slot == "SHIELD":
        shape = next((s for s in SHIELD_SHAPES if s in code), SHIELD_BY_DEFENCE.get(first, "KITE"))
        face = glass or (DEFENCE[first][1] if first else "#a8322a")
        mat = glass2 or {"ROUND": "wood", "BUCKLER": "hide"}.get(shape) or TIER_MAT["BASE_ARMOUR"][lv]
        parts = shield(shape, mat, face if shape != "ROUND" or unique else "wood", "gold")
        put(f"shield_{shape}_{face[1:]}_{lv}", mark(parts, look, anchor("SHIELD")))
    elif slot == "RING":
        gem = glass or stone(code)
        put(f"ring_{gem[1:]}", drawn(ring("silver" if "IRON" in code else "gold", gem), "RING"))
    elif slot == "AMULET":
        gem = glass or stone(code)
        put(f"amulet_{gem[1:]}", drawn(amulet(gem), "AMULET"))
    elif slot == "COLLAR":
        kind = next((k for k in COLLARS if k in code), "LEATHER")
        band, stud, tag = COLLARS[kind]
        put(f"collar_{kind}", drawn(collar(glass or band, stud, glass2 or tag), "COLLAR"))
    elif slot == "BELT":
        strap, detail = ("steel", "chain") if "CHAIN" in code else ("cloth", "plain") if "SASH" in code else ("leather", "studs")
        put(f"belt_{detail}", mark(belt(glass2 or strap, detail), look, anchor("BELT")))
    elif slot == "FLASK":
        liquid = glass or next((v for k, v in FLASK_LIQUID.items() if k in code), None) or hashed(code)
        tall = not any(k in code for k in ("LIFE", "MANA"))
        put(f"flask_{'tall' if tall else 'round'}_{liquid[1:]}",
            mark(flask(liquid, tall), look, anchor("FLASK_TALL" if tall else "FLASK_ROUND")))
    elif slot == "QUIVER":
        fletch = glass or ("#f07a2a" if "FIRE" in code else "#7fd8f0" if "FROST" in code else "#b88af0" if "STORM" in code else "#e6d4a6")
        put(f"quiver_{fletch[1:]}", mark(quiver(fletch), look, anchor("QUIVER")))
    elif slot == "WINGS":
        feathered = any(k in code for k in ("FEATHER", "SERAPH", "VASTIRI", "FALLEN_GOD", "PINION"))
        hue = glass or ("#f0ecdc" if feathered else "#7a5234")
        put(f"wings_{'f' if feathered else 'b'}_{hue[1:]}",
            mark(wings(hue, "#d9a53a" if feathered else "#e4dcc4", feathered), look, anchor("WINGS")))
    elif slot == "JEWEL":
        gem = glass if code in UNIQUE_ART else {"CRIMSON": "#d0304a", "VIRIDIAN": "#3aa870", "COBALT": "#3a6fe0"}.get(code.split("_")[0], hashed(code))
        put(f"jewel_{gem[1:]}", drawn(jewel(gem), "JEWEL"))
    elif slot == "MAP":
        circle = re.match(r"MAP_(C\d+)", code)
        seal = MAP_SEAL.get(circle.group(1) if circle else "", "#c8c8c8")
        put(f"map_{seal[1:]}", game_map(seal))
    elif slot.startswith("TOOL_"):
        metal = next((v for k, v in TOOL_METAL.items() if code.startswith(k)), "steel")
        put(f"{slot}_{metal}", tool(slot, metal))


# ------------------------------------------------------------------------------------ empty slots

def shadow(parts):
    """An empty place of the body: the drawing in dark, unpainted glass, without its highlights."""
    return [dict(p, color="#3b352e") if "line" not in p else dict(p, color="#4a4238") for p in parts if p.get("alpha", 1) == 1]


SLOTS = {
    "HELMET": lambda: helmet("BASE_ARMOUR", "steel", "gold"), "BODY": lambda: body("BASE_ARMOUR", "steel", "gold"),
    "GLOVES": lambda: gloves("BASE_ARMOUR", "steel", "gold"), "BOOTS": lambda: boots("BASE_EVASION", "leather", "gold"),
    "RING": lambda: ring("gold", "gold"), "RING_2": lambda: ring("gold", "gold"), "AMULET": lambda: amulet("gold"),
    "BELT": lambda: belt("leather", "plain"), "WEAPON_1H": lambda: (sword("steel", "gold", "leather"), 40),
    "WEAPON_2H": lambda: (longsword("steel", "gold", "leather"), 45), "QUIVER": lambda: quiver("paper"),
    "SHIELD": lambda: shield("KITE", "steel", "steel", "gold"), "WINGS": lambda: wings("leather", "bone", False),
    "JEWEL": lambda: jewel("#808080"), "MAP": lambda: game_map("paper"), "COLLAR": lambda: collar("leather", "steel", "gold"),
    "FLASK": lambda: flask("glass", False), "FLASK_2": lambda: flask("glass", False), "FLASK_3": lambda: flask("glass", False),
    **{s: (lambda s=s: tool(s, "steel")) for s in ("TOOL_MINING", "TOOL_HERBALISM", "TOOL_WOODCUTTING", "TOOL_SMITHING",
                                                  "TOOL_ALCHEMY", "TOOL_CARTOGRAPHY", "TOOL_ENCHANTING")},
}


def slots(atlas):
    for slot, draw in SLOTS.items():
        drawn = draw()
        parts, rot = drawn if isinstance(drawn, tuple) else (drawn, 0)
        atlas.put(f"slot.{slot}", f"slot_{slot}", shadow(parts), rot)


# ------------------------------------------------------------------------------ stats and skills

MONO_SOURCE = FsPath(__file__).resolve().parent / "icons_mono.json"
STAT_HUE = {
    "fire": "#ef6a3a", "cold": "#6fc8f0", "water": "#3a9ae8", "lightning": "#f2d03a", "air": "#bfe3ef", "earth": "#a8784a",
    "chaos": "#9b59d6", "dark": "#8a5ad0", "poison": "#7fcf4a", "physical": "#c8ccd4", "combat": "#c8ccd4",
    "critical": "#f2a03a", "health": "#e0405a", "regen": "#e86a7a", "leech": "#c02a4a", "bleeding": "#b01a2a",
    "mana": "#4a7ae8", "magical": "#6a8af0", "focus": "#8aa8f0", "energy": "#8a6ae0", "shield_energy": "#8a6ae0",
    "armour": "#b9c2cf", "block": "#b9c2cf", "shield": "#b9c2cf", "resist": "#e8c060", "evasion": "#7fcf4a",
    "speed": "#9adf6a", "agility": "#7fcf4a", "strength": "#e0405a", "intellect": "#4a7ae8", "constitution": "#d98a4a",
    "rarity": "#f2c53a", "quantity": "#f2c53a", "gold": "#f2c53a", "experience": "#c8e06a", "light": "#fff0b0",
    "summon": "#b88af0", "stun": "#f0a040", "invisible": "#a0a8b8", "flask": "#e0405a", "crystal": "#9fd8e8",
    "inventory": "#c89a60", "locked": "#8e97a3", "mirror": "#e8f4ff",
}


def stat_glass(sprite, hue):
    """A mono stat or skill drawing as a small window: each outline leaded, filled with its hue."""
    out = []
    for p in sprite["paths"]:
        col = hue if p.get("alpha", 1) >= .9 else shade(hue, .62)
        out.append({"d": p["d"], "color": col, "line": 3.27})
        out.append({"d": p["d"], "color": col, "alpha": .96})
    return {"viewBox": sprite["viewBox"], "style": "glass", "paths": out}


def stats(sprites, wanted):
    """Converts every mono sprite in place; the mono originals are kept beside this script as the source."""
    source = json.loads(MONO_SOURCE.read_text()) if MONO_SOURCE.exists() else {}
    source.update({k: v for k, v in sprites.items() if not k.startswith(PREFIX) and v.get("style", "mono") == "mono"})
    source = {k: v for k, v in source.items() if k in wanted}
    MONO_SOURCE.write_text(json.dumps(source, ensure_ascii=False, indent=2) + "\n")
    for name, sprite in source.items():
        sprites[name] = stat_glass(sprite, STAT_HUE.get(name, "#d9a53a"))


def item(atlas, i):
    code, cat, sub = i["code"], i["category"], i.get("subCategory")
    key = f"item.{code}"
    if code in ORBS:
        hue, emblem, rays = ORBS[code]
        atlas.put(key, f"orb_{code}", orb(hue, emblem, rays))
    elif cat == "OMEN":
        name = code.removeprefix("OMEN_")
        orb_code, side = OMENS[name]
        hue, emblem, _ = ORBS[orb_code]
        atlas.put(key, f"omen_{name}", omen(hue, emblem, side))
    elif cat == "ESSENCE":
        m = re.match(r"ESSENCE_([A-Z_]+?)(?:_(\d+))?$", code)
        name, tier = m.group(1), int(m.group(2) or 0)
        band = 3 if not m.group(2) else 0 if tier <= 2 else 1 if tier <= 5 else 2
        hue = ESSENCE_HUE.get(name, hashed(name))
        atlas.put(key, f"essence_{name}_{band}", essence(hue, band))
    elif cat == "BOOK":
        cover = BOOK_COVER.get(sub, hashed(sub or code))
        atlas.put(key, f"book_{sub}", book(cover))
    elif cat == "PET":
        biome = code.replace("PET_EGG_", "")
        hue = EGG.get(biome, hashed(code))
        atlas.put(key, f"egg_{biome}", egg("#efe6cf", hue))
    elif cat == "CHEST":
        atlas.put(key, f"chest_{sub}", chest(*CHESTS.get(sub, ("wood", "bronze", "bronze", None))))
    elif code.endswith("_ORE"):
        vein = ORE_VEIN.get(code[:-4], hashed(code))
        atlas.put(key, f"ore_{code[:-4]}", ore(vein))
    elif code in ("TOPAZ", "SAPPHIRE", "RUBY"):
        atlas.put(key, f"gem_{code}", cut_gem(STONES[code]))
    elif code.endswith("_LOG"):
        wood = code[:-4]
        bark, rings = LOG_BARK.get(wood, LOG_BARK["WOOD"])
        atlas.put(key, f"log_{wood}", log(bark, rings))
    elif code == "BARK":
        atlas.put(key, "bark", bark_piece())
    elif code.endswith("_FLUX"):
        atlas.put(key, f"flux_{code[:-5]}", pouch(FLUX.get(code[:-5], hashed(code))))
    elif code.startswith("STONE_"):
        atlas.put(key, code, stone_block(code == "STONE_POLISHED"))
    elif sub == "HERBALISM" or cat == "MATERIAL":
        herb = {"WORMWOOD": lambda: sprig("#9ab08a"), "CAVE_MUSHROOM": lambda: mushroom("#8a5a34"),
                "GLOWCAP": lambda: mushroom("#4fd8e0"), "BLOODWORT": lambda: flower("#c02a3a"),
                "SHADEBLOOM": lambda: flower("#5a3a8a", "#c86ad6"), "MOON_LOTUS": lambda: flower("#e8f0ff", "#9fd8e8"),
                "AMBER_RESIN": lambda: drop("#e89a2a"), "VOID_ROOT": lambda: root("#6a3ab0")}
        atlas.put(key, f"herb_{code}", herb.get(code, lambda: sprig(hashed(code)))())


def main():
    content = RES / "content"
    unwrap = lambda doc: doc if isinstance(doc, list) else next(iter(doc.values()))
    templates = unwrap(json.loads((content / "equipment.json").read_text()))
    items = unwrap(json.loads((content / "items.json").read_text()))

    atlas = Atlas()
    for t in templates: equipment(atlas, t)
    for i in items: item(atlas, i)
    slots(atlas)

    target = RES / "icons/icons.json"
    doc = json.loads(target.read_text())
    sprites = {k: v for k, v in doc["sprites"].items() if not k.startswith(PREFIX)}
    icons = dict(doc["icons"])
    icons.update(atlas.keys)
    # A key for a code the content no longer has, and a drawing nobody names, are dropped.
    stat_codes = set(re.findall(r'"code"\s*:\s*"([^"]+)"', (content / "stats.json").read_text()))
    known = {"equipment": {t["code"] for t in templates}, "item": {i["code"] for i in items}, "stat": stat_codes}
    icons = {k: v for k, v in icons.items() if k.split(".", 1)[0] not in known or k.split(".", 1)[1] in known[k.split(".", 1)[0]]}
    named = set(re.findall(r'"icon"\s*:\s*"([^"]+)"', "".join(f.read_text() for f in content.glob("*.json"))))
    wanted = set(icons.values()) | named
    stats(sprites, wanted)
    sprites.update(atlas.sprites)
    sprites = {k: v for k, v in sprites.items() if k in wanted}
    target.write_text(json.dumps({"sprites": sprites, "icons": icons}, ensure_ascii=False, indent=2) + "\n")
    print(f"glass sprites: {len(atlas.sprites)}, keys: {len(atlas.keys)}, total sprites: {len(sprites)}")


if __name__ == "__main__":
    main()
