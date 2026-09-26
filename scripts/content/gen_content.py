# -*- coding: utf-8 -*-
"""
Генератор содержимого модификаторов (0.66.0): из families.py собирает

- content/modifiers.json  - семейства с тирами, весами и вариантами;
- content/pools.json      - пулы модификаторов предметов (с наследованием) и монстров;
                            разделы экипировки, уникалок и мифических предметов берутся из текущего файла;
- content/bench.json      - рецепты верстака по верстачным вариантам.

Запуск из корня сервера: python3 scripts/content/gen_content.py
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
CONTENT = os.path.join(ROOT, "src", "main", "resources", "content")
sys.path.insert(0, HERE)
from families import FAMILIES  # noqa: E402

VARIANT_ORDER = ["LOCAL", "CRAFTED", "IMPLICIT", "CORRUPTED", "ENCHANT"]


def rnd(value, precision):
    return round(value, precision) if precision else int(round(value))


def tiers_of(grid, top, bottom, precision, ratio):
    n = len(grid)
    out = []
    for i, level in enumerate(grid):
        frac = (n - 1 - i) / (n - 1) if n > 1 else 1.0
        values = []
        for t, b in zip(top, bottom):
            lo = rnd(b[0] + (t[0] - b[0]) * frac, precision)
            hi = rnd(b[1] + (t[1] - b[1]) * frac, precision)
            if hi < lo:
                lo, hi = hi, lo
            values.append([lo, hi])
        weight = int(round(1000 * (ratio ** (n - 1 - i))))
        out.append({"level": level, "weight": weight, "values": values})
    return out


def build():
    records = []
    pools = {}      # tag -> code -> weight
    recipes = []
    for f in FAMILIES:
        record = {
            "code": f["code"], "source": f["source"], "effects": f["effects"],
            "tiers": tiers_of(f["grid"], f["top"], f["bottom"], f["precision"], f["weight_ratio"]),
            "tags": f["tags"],
        }
        if f["local"]:
            record["local"] = True
        if f["group"]:
            record["group"] = f["group"]
        if f["influence"]:
            record["influence"] = f["influence"]
        if f["crafted"]:
            record["crafted"] = True
        if f["minRarity"]:
            record["minRarity"] = f["minRarity"]
        for tag, weight in f["pools"].items():
            pools.setdefault(tag, {})[f["code"]] = weight
        variants = []
        for name in VARIANT_ORDER:
            v = f["variants"].get(name)
            if v is None:
                continue
            code = f"{f['code']}@{name}"
            entry = {"variant": name}
            if v.get("grid") is not None:
                entry["tiers"] = tiers_of(v["grid"], v["top"], v["bottom"], f["precision"], f["weight_ratio"])
            if v.get("local"):
                entry["local"] = True
            variants.append(entry)
            for tag, weight in v.get("pools", {}).items():
                pools.setdefault(tag, {})[code] = weight
            if name == "CRAFTED":
                for tier, (orb, amount) in enumerate([("DIVINE_ORB", 1), ("EXALTED_ORB", 1), ("CHAOS_ORB", 1), ("ORB_OF_ALTERATION", 4), ("ORB_OF_TRANSMUTATION", 3), ("ORB_OF_TRANSMUTATION", 1)], start=1):
                    recipe = {"modifier": code, "tier": tier, "orb": orb, "amount": amount}
                    if v.get("slots"):
                        recipe["slots"] = v["slots"]
                    recipes.append(recipe)
        if variants:
            record["variants"] = variants
        records.append(record)
    return records, pools, recipes


ARMOUR = ["helmet", "body", "gloves", "boots", "shield", "wings"]
JEWELLERY = ["amulet", "ring", "belt"]
WEAPONS = ["sword", "axe", "blade", "bow", "wand"]
# Слотовые поправки к общим пулам: как в POE, оружие одного вида любит свои строки.
WEAPON_TWEAKS = {
    "axe": {"CHANCE_TO_BLEED": 900, "INCREASED_BLEED_DAMAGE": 700, "ADD_INTELLIGENCE": 0, "ADD_DEXTERITY_AND_INTELLIGENCE": 0, "ADD_STRENGTH": 700},
    "sword": {"ADD_STRENGTH_AND_DEXTERITY": 600, "ADD_INTELLIGENCE": 0, "INCREASED_CRITICAL_STRIKE_CHANCE": 500},
    "blade": {"CHANCE_TO_POISON": 900, "INCREASED_POISON_DAMAGE": 700, "INCREASED_CRITICAL_STRIKE_CHANCE": 1000, "ADD_CRITICAL_STRIKE_MULTIPLIER": 1000, "ADD_STRENGTH": 0, "ADD_STRENGTH_AND_INTELLIGENCE": 0},
    "bow": {"ADD_STRENGTH": 0, "ADD_STRENGTH_AND_INTELLIGENCE": 0, "ADD_STRENGTH_AND_DEXTERITY": 0, "ADD_DEXTERITY": 900, "CHANCE_TO_BLEED": 500, "INCREASED_ELEMENTAL_DAMAGE_WITH_ATTACKS": 500},
    "wand": {"ADD_STRENGTH": 0, "ADD_STRENGTH_AND_DEXTERITY": 0, "ADD_STRENGTH_AND_INTELLIGENCE": 0, "ADD_INTELLIGENCE": 900,
             "INCREASED_FIRE_DAMAGE": 700, "INCREASED_COLD_DAMAGE": 700, "INCREASED_LIGHTNING_DAMAGE": 700, "INCREASED_CHAOS_DAMAGE": 500,
             "INCREASED_CRITICAL_STRIKE_CHANCE": 900, "PENETRATE_FIRE_RESISTANCE": 400, "PENETRATE_COLD_RESISTANCE": 400, "PENETRATE_LIGHTNING_RESISTANCE": 400,
             "CHANCE_TO_BLEED": 0, "INCREASED_BLEED_DAMAGE": 0},
}


def pools_document(pools, current):
    modifier = {}
    # общие пулы
    for group in ["armour", "jewellery", "weapon"]:
        modifier[group] = dict(sorted(pools.pop(group, {}).items()))
    for slot in ARMOUR:
        modifier[slot] = {"includes": ["armour"], "entries": dict(sorted(pools.pop(slot, {}).items()))}
    for slot in JEWELLERY:
        modifier[slot] = {"includes": ["jewellery"], "entries": dict(sorted(pools.pop(slot, {}).items()))}
    for kind in WEAPONS:
        own = dict(sorted(pools.pop(kind, {}).items()))
        own.update(WEAPON_TWEAKS.get(kind, {}))
        modifier[kind] = {"includes": ["weapon"], "entries": own}
    # пулы инструментов по профессиям включают общий
    for tag in sorted(t for t in pools if t.startswith("tool:")):
        modifier[tag] = {"includes": ["tool"], "entries": dict(sorted(pools.pop(tag).items()))}
    monster = {}
    for tag in sorted(pools):
        entries = dict(sorted(pools[tag].items()))
        if tag in ("monster", "boss", "essence"):
            monster[tag] = entries
        else:
            modifier[tag] = entries
    return {"MODIFIER": modifier, "MONSTER": monster, "EQUIPMENT": current["EQUIPMENT"], "UNIQUE": current["UNIQUE"], "MYTHIC": current["MYTHIC"]}


def dump(path, document):
    with open(path, "w", encoding="utf-8") as out:
        json.dump(document, out, ensure_ascii=False, indent=2)
        out.write("\n")


if __name__ == "__main__":
    records, pools, recipes = build()
    with open(os.path.join(CONTENT, "pools.json"), encoding="utf-8") as src:
        current = json.load(src)
    dump(os.path.join(CONTENT, "modifiers.json"), {"modifiers": records})
    dump(os.path.join(CONTENT, "pools.json"), pools_document(pools, current))
    dump(os.path.join(CONTENT, "bench.json"), {"recipes": recipes})
    definitions = sum(1 + len(r.get("variants", [])) for r in records)
    print(f"families {len(records)}, definitions {definitions}, recipes {len(recipes)}")
