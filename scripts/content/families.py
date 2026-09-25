# -*- coding: utf-8 -*-
"""
Семейства модификаторов (0.66.0) - источник для gen_content.py.

Каждое семейство: код, источник, эффекты, сетка тиров, значения лучшего и худшего тира,
пулы природного описания и варианты (LOCAL/CRAFTED/IMPLICIT/CORRUPTED/ENCHANT) со своими тирами.
Числа - Path of Exile, сжатый под item level 71: тир 1 открывается на 68, дно - на 1.
"""

# ---------- сетки тиров: уровни от лучшего к худшему ----------
G10 = [68, 60, 52, 44, 36, 29, 22, 15, 8, 1]
G8 = [66, 56, 46, 37, 28, 19, 10, 1]
G6 = [64, 51, 38, 26, 13, 1]
G5 = [62, 47, 32, 17, 1]
G4 = [60, 40, 20, 1]
G3 = [55, 28, 1]
G2 = [40, 1]
G1 = [1]
# карты и монстры: по уровню карты (1..20)
M4 = [17, 11, 6, 1]
M3 = [15, 8, 1]
MOB4 = [16, 10, 5, 1]
MOB3 = [12, 6, 1]
# верстак: шесть тиров, уровень не важен - тир выбирает игрок
BENCH = [1, 1, 1, 1, 1, 1]
# имплиситы и зачарования
IMP = [45, 20, 1]
ENCH = [50, 25, 1]
COR = [40, 1]

FAMILIES = []


def fam(code, source, effects, grid, top, bottom, pools=None, tags=(), variants=None, group=None, local=False,
        influence=None, crafted=False, minRarity=None, precision=0, weight_ratio=0.72):
    FAMILIES.append(dict(code=code, source=source, effects=effects, grid=grid, top=top, bottom=bottom,
                         pools=pools or {}, tags=list(tags), variants=variants or {}, group=group, local=local,
                         influence=influence, crafted=crafted, minRarity=minRarity, precision=precision,
                         weight_ratio=weight_ratio))


def eff(stat, op="ADD", per=None, amount=1.0):
    e = {"stat": stat, "operation": op}
    if per:
        e["perStat"] = per
        e["perAmount"] = amount
    return e


# Слоты брони, украшений, оружия
ARMOUR = ["helmet", "body", "gloves", "boots", "shield", "wings"]
JEWELLERY = ["amulet", "ring", "belt"]
ALL_SLOTS = ARMOUR + JEWELLERY + ["quiver", "weapon", "jewel", "map"]


def P(**pools):
    """Пулы природного описания: тег -> вес. `armour`, `jewellery`, `weapon` - общие пулы, их включают слоты."""
    return pools


def LOCAL(*pool_tags, weight=1000, grid=None, top=None, bottom=None):
    return {"LOCAL": dict(pools={t: weight for t in pool_tags}, grid=grid, top=top, bottom=bottom)}


def CRAFTED(top, bottom, local=False, slots=()):
    """Верстачный вариант: [local] - считается внутри предмета, [slots] - слоты рецепта (пусто - любой)."""
    return {"CRAFTED": dict(pools={}, grid=BENCH, top=top, bottom=bottom, local=local, slots=list(slots))}


ARMOUR_SLOTS = ["HELMET", "BODY", "GLOVES", "BOOTS", "SHIELD", "WINGS"]
WEAPON_SLOTS = ["WEAPON_1H", "WEAPON_2H"]


def IMPLICIT(top, bottom, grid=IMP):
    return {"IMPLICIT": dict(pools={}, grid=grid, top=top, bottom=bottom)}


def CORRUPTED(top, bottom, *slots, weight=100):
    return {"CORRUPTED": dict(pools={f"corruption:{s}": weight for s in slots}, grid=COR, top=top, bottom=bottom)}


def ENCHANT(top, bottom, *slots, weight=100):
    return {"ENCHANT": dict(pools={f"enchant:{s}": weight for s in slots}, grid=ENCH, top=top, bottom=bottom)}


def V(*parts):
    out = {}
    for p in parts:
        out.update(p)
    return out


# =====================================================================
# ЗАПАС: здоровье, энергощит
# =====================================================================
fam("ADD_MAXIMUM_LIFE", "PREFIX", [eff("STOCK_HEALTH")], G10, [[105, 120]], [[8, 14]],
    P(armour=1000, jewellery=1000, quiver=700, weapon=0),
    tags=["life", "defences"],
    variants=V(CRAFTED([[95, 105]], [[6, 12]]), IMPLICIT([[40, 50]], [[10, 15]]),
               CORRUPTED([[70, 90]], [[25, 35]], "body", "belt", "amulet")))
fam("INCREASED_MAXIMUM_LIFE", "PREFIX", [eff("STOCK_HEALTH", "INCREASED")], G5, [[8, 10]], [[2, 3]],
    P(jewel=800, belt=150),
    tags=["life", "defences"],
    variants=V(CORRUPTED([[6, 8]], [[3, 4]], "body", "belt")))
fam("ADD_MAXIMUM_LIFE_AND_LIFE_REGENERATION", "PREFIX", [eff("STOCK_HEALTH"), eff("STOCK_HEALTH_REGEN")], G6, [[60, 70], [6, 8]], [[8, 12], [1, 1.5]],
    P(body=400, belt=400, amulet=250), tags=["life", "regen", "hybrid", "defences"], precision=1)
fam("ADD_ENERGY_SHIELD", "PREFIX", [eff("STOCK_ENERGY_SHIELD")], G10, [[38, 45]], [[3, 5]],
    P(jewellery=800, shield=0, body=0),
    tags=["energy_shield", "defences"],
    variants=V(LOCAL("local:energy_shield", grid=G10, top=[[95, 115]], bottom=[[5, 9]]), CRAFTED([[30, 36]], [[3, 5]]),
               IMPLICIT([[20, 26]], [[6, 9]]), CORRUPTED([[35, 45]], [[15, 20]], "amulet", "ring")))
fam("INCREASED_ENERGY_SHIELD", "PREFIX", [eff("STOCK_ENERGY_SHIELD", "INCREASED")], G4, [[10, 12]], [[3, 4]],
    P(jewel=700, amulet=200),
    tags=["energy_shield", "defences"],
    variants=V(LOCAL("local:energy_shield", grid=G10, top=[[90, 100]], bottom=[[6, 12]]), CRAFTED([[70, 80]], [[6, 12]], local=True, slots=ARMOUR_SLOTS)))
fam("ADD_ENERGY_SHIELD_AND_LIFE", "PREFIX", [eff("STOCK_ENERGY_SHIELD"), eff("STOCK_HEALTH")], G5, [[40, 50], [30, 40]], [[3, 5], [4, 8]],
    {"local:energy_shield": 500}, tags=["energy_shield", "life", "hybrid", "defences"], local=True)

# =====================================================================
# ЗАЩИТА: броня, уклонение, гибриды, блок, оглушение
# =====================================================================
fam("ADD_ARMOUR", "PREFIX", [eff("STOCK_ARMOR")], G10, [[300, 360]], [[6, 12]],
    P(belt=800, ring=300),
    tags=["armour", "defences"],
    variants=V(LOCAL("local:armor", grid=G10, top=[[380, 450]], bottom=[[8, 16]]), IMPLICIT([[90, 120]], [[15, 25]]),
               CORRUPTED([[200, 260]], [[60, 90]], "belt", "body")))
fam("INCREASED_ARMOUR", "PREFIX", [eff("STOCK_ARMOR", "INCREASED")], G4, [[10, 12]], [[3, 4]],
    P(jewel=700, belt=200),
    tags=["armour", "defences"],
    variants=V(LOCAL("local:armor", grid=G10, top=[[90, 100]], bottom=[[6, 13]]), CRAFTED([[70, 80]], [[6, 12]], local=True, slots=ARMOUR_SLOTS)))
fam("ADD_EVASION_RATING", "PREFIX", [eff("STOCK_EVASION")], G10, [[300, 360]], [[6, 12]],
    P(ring=300, belt=0),
    tags=["evasion", "defences"],
    variants=V(LOCAL("local:evasion", grid=G10, top=[[380, 450]], bottom=[[8, 16]]), IMPLICIT([[90, 120]], [[15, 25]]),
               CORRUPTED([[200, 260]], [[60, 90]], "wings", "boots")))
fam("INCREASED_EVASION_RATING", "PREFIX", [eff("STOCK_EVASION", "INCREASED")], G4, [[10, 12]], [[3, 4]],
    P(jewel=700, wings=300),
    tags=["evasion", "defences"],
    variants=V(LOCAL("local:evasion", grid=G10, top=[[90, 100]], bottom=[[6, 13]]), CRAFTED([[70, 80]], [[6, 12]], local=True, slots=ARMOUR_SLOTS)))
fam("ADD_ARMOUR_AND_EVASION", "PREFIX", [eff("STOCK_ARMOR"), eff("STOCK_EVASION")], G8, [[200, 240], [200, 240]], [[6, 10], [6, 10]],
    {"local:armor+evasion": 1000}, tags=["armour", "evasion", "hybrid", "defences"], local=True)
fam("ADD_ARMOUR_AND_ENERGY_SHIELD", "PREFIX", [eff("STOCK_ARMOR"), eff("STOCK_ENERGY_SHIELD")], G8, [[200, 240], [40, 50]], [[6, 10], [2, 4]],
    {"local:armor+energy_shield": 1000}, tags=["armour", "energy_shield", "hybrid", "defences"], local=True)
fam("ADD_EVASION_AND_ENERGY_SHIELD", "PREFIX", [eff("STOCK_EVASION"), eff("STOCK_ENERGY_SHIELD")], G8, [[200, 240], [40, 50]], [[6, 10], [2, 4]],
    {"local:energy_shield+evasion": 1000}, tags=["evasion", "energy_shield", "hybrid", "defences"], local=True)
fam("INCREASED_ARMOUR_AND_EVASION", "PREFIX", [eff("STOCK_ARMOR", "INCREASED"), eff("STOCK_EVASION", "INCREASED")], G4, [[8, 10], [8, 10]], [[3, 4], [3, 4]],
    P(jewel=250), tags=["armour", "evasion", "hybrid", "defences"],
    variants=V(LOCAL("local:armor+evasion", grid=G8, top=[[70, 80], [70, 80]], bottom=[[6, 12], [6, 12]])))
fam("INCREASED_ARMOUR_AND_ENERGY_SHIELD", "PREFIX", [eff("STOCK_ARMOR", "INCREASED"), eff("STOCK_ENERGY_SHIELD", "INCREASED")], G4, [[8, 10], [8, 10]], [[3, 4], [3, 4]],
    P(jewel=250), tags=["armour", "energy_shield", "hybrid", "defences"],
    variants=V(LOCAL("local:armor+energy_shield", grid=G8, top=[[70, 80], [70, 80]], bottom=[[6, 12], [6, 12]])))
fam("INCREASED_EVASION_AND_ENERGY_SHIELD", "PREFIX", [eff("STOCK_EVASION", "INCREASED"), eff("STOCK_ENERGY_SHIELD", "INCREASED")], G4, [[8, 10], [8, 10]], [[3, 4], [3, 4]],
    P(jewel=250), tags=["evasion", "energy_shield", "hybrid", "defences"],
    variants=V(LOCAL("local:energy_shield+evasion", grid=G8, top=[[70, 80], [70, 80]], bottom=[[6, 12], [6, 12]])))
fam("ADD_ARMOUR_AND_LIFE", "PREFIX", [eff("STOCK_ARMOR"), eff("STOCK_HEALTH")], G5, [[120, 150], [30, 40]], [[8, 14], [4, 8]],
    {"local:armor": 500}, tags=["armour", "life", "hybrid", "defences"], local=True)
fam("ADD_EVASION_RATING_AND_LIFE", "PREFIX", [eff("STOCK_EVASION"), eff("STOCK_HEALTH")], G5, [[120, 150], [30, 40]], [[8, 14], [4, 8]],
    {"local:evasion": 500}, tags=["evasion", "life", "hybrid", "defences"], local=True)
fam("INCREASED_ARMOUR_AND_STUN_THRESHOLD", "PREFIX", [eff("STOCK_ARMOR", "INCREASED"), eff("STOCK_STUN_THRESHOLD")], G5, [[50, 60], [15, 20]], [[6, 12], [2, 4]],
    {"local:armor": 500}, tags=["armour", "stun", "hybrid", "defences"], local=True)
fam("INCREASED_EVASION_RATING_AND_STUN_THRESHOLD", "PREFIX", [eff("STOCK_EVASION", "INCREASED"), eff("STOCK_STUN_THRESHOLD")], G5, [[50, 60], [15, 20]], [[6, 12], [2, 4]],
    {"local:evasion": 500}, tags=["evasion", "stun", "hybrid", "defences"], local=True)
fam("INCREASED_ENERGY_SHIELD_AND_STUN_THRESHOLD", "PREFIX", [eff("STOCK_ENERGY_SHIELD", "INCREASED"), eff("STOCK_STUN_THRESHOLD")], G5, [[50, 60], [15, 20]], [[6, 12], [2, 4]],
    {"local:energy_shield": 500}, tags=["energy_shield", "stun", "hybrid", "defences"], local=True)
fam("ADD_BLOCK_CHANCE", "SUFFIX", [eff("STOCK_BLOCK_CHANCE")], G5, [[7, 9]], [[1, 2]],
    P(shield=1000, jewel=200), tags=["block", "defences"],
    variants=V(CRAFTED([[5, 6]], [[1, 2]], slots=["SHIELD"]), CORRUPTED([[6, 8]], [[3, 4]], "shield"), IMPLICIT([[4, 5]], [[1, 2]])))
fam("ADD_STUN_THRESHOLD", "PREFIX", [eff("STOCK_STUN_THRESHOLD")], G5, [[30, 40]], [[3, 6]],
    P(body=600, belt=600, shield=300), tags=["stun", "defences"],
    variants=V(IMPLICIT([[15, 20]], [[3, 5]])))
fam("INCREASED_STUN_THRESHOLD", "PASSIVE", [eff("STOCK_STUN_THRESHOLD", "INCREASED")], G1, [[1, 1]], [[1, 1]], P(), tags=["stun", "passive"])
fam("AVOID_STUN", "SUFFIX", [eff("STOCK_AVOID_STUN")], G4, [[25, 30]], [[5, 8]],
    P(helmet=400, boots=400, belt=300, shield=300), tags=["stun", "defences"],
    variants=V(ENCHANT([[30, 40]], [[10, 15]], "helmet")))
fam("ADD_PHYSICAL_DAMAGE_REDUCTION", "PREFIX", [eff("STOCK_PHYSICAL_REDUCTION")], G3, [[5, 6]], [[1, 2]],
    P(body=250, shield=250, belt=150), tags=["armour", "defences"],
    variants=V(CORRUPTED([[6, 8]], [[3, 4]], "body", "shield")))

# =====================================================================
# СОПРОТИВЛЕНИЯ
# =====================================================================
for el, tag in [("FIRE", "fire"), ("COLD", "cold"), ("LIGHTNING", "lightning")]:
    fam(f"ADD_{el}_RESISTANCE", "SUFFIX", [eff(f"STOCK_RESIST_{el}")], G10, [[42, 48]], [[6, 11]],
        P(armour=1000, jewellery=1000, quiver=400, weapon=200), tags=["resistance", "elemental", tag],
        variants=V(CRAFTED([[36, 40]], [[6, 10]]), IMPLICIT([[20, 30]], [[8, 12]]),
                   CORRUPTED([[35, 45]], [[15, 20]], "ring", "amulet", "gloves", "boots", "helmet")))
fam("ADD_CHAOS_RESISTANCE", "SUFFIX", [eff("STOCK_RESIST_CHAOS")], G8, [[31, 35]], [[5, 10]],
    P(armour=300, jewellery=300, quiver=150, weapon=80), tags=["resistance", "chaos"],
    variants=V(CRAFTED([[25, 28]], [[5, 8]]), IMPLICIT([[15, 20]], [[5, 8]]), CORRUPTED([[30, 40]], [[12, 18]], "belt", "amulet", "body")))
fam("ADD_ALL_ELEMENTAL_RESISTANCES", "SUFFIX", [eff("STOCK_RESIST_ALL")], G8, [[16, 18]], [[3, 5]],
    P(armour=300, jewellery=500, quiver=100), tags=["resistance", "elemental"],
    variants=V(CRAFTED([[12, 14]], [[3, 5]]), IMPLICIT([[10, 14]], [[4, 6]]), CORRUPTED([[14, 18]], [[6, 8]], "amulet", "body", "shield")))
fam("ADD_ALL_RESISTANCES", "SUFFIX", [eff("STOCK_RESIST_ALL"), eff("STOCK_RESIST_CHAOS")], G3, [[10, 12], [8, 10]], [[3, 4], [2, 3]],
    P(amulet=150, belt=150, body=100), tags=["resistance", "elemental", "chaos", "hybrid"])
for a, b in [("FIRE", "COLD"), ("FIRE", "LIGHTNING"), ("COLD", "LIGHTNING")]:
    fam(f"ADD_{a}_AND_{b}_RESISTANCES", "SUFFIX", [eff(f"STOCK_RESIST_{a}"), eff(f"STOCK_RESIST_{b}")], G6, [[22, 26], [22, 26]], [[4, 8], [4, 8]],
        P(armour=350, jewellery=350, quiver=150), tags=["resistance", "elemental", a.lower(), b.lower(), "hybrid"])
for el in ["FIRE", "COLD", "LIGHTNING"]:
    fam(f"ADD_{el}_AND_CHAOS_RESISTANCES", "SUFFIX", [eff(f"STOCK_RESIST_{el}"), eff("STOCK_RESIST_CHAOS")], G5, [[18, 22], [10, 13]], [[4, 7], [2, 4]],
        P(armour=200, jewellery=200), tags=["resistance", "elemental", "chaos", el.lower(), "hybrid"])
for el, tag in [("FIRE", "fire"), ("COLD", "cold"), ("LIGHTNING", "lightning"), ("CHAOS", "chaos")]:
    fam(f"ADD_MAXIMUM_{el}_RESISTANCE", "SUFFIX", [eff(f"STOCK_RESIST_MAX_{el}")], G3, [[3, 4]], [[1, 1]],
        P(shield=150, helmet=100, amulet=100), tags=["resistance", tag, "defences"],
        variants=V(CORRUPTED([[3, 5]], [[1, 2]], "shield", "helmet", "amulet")))
fam("ADD_MAXIMUM_ELEMENTAL_RESISTANCES", "SUFFIX", [eff("STOCK_RESIST_MAX_ALL")], G3, [[2, 3]], [[1, 1]],
    P(shield=80, amulet=60), tags=["resistance", "elemental", "defences"],
    variants=V(CORRUPTED([[2, 3]], [[1, 2]], "shield", "amulet", "body")))

# =====================================================================
# АТРИБУТЫ
# =====================================================================
for attr, stat in [("STRENGTH", "STOCK_STRENGTH"), ("DEXTERITY", "STOCK_AGILITY"), ("INTELLIGENCE", "STOCK_INTELLECT")]:
    fam(f"ADD_{attr}", "SUFFIX", [eff(stat)], G8, [[46, 52]], [[6, 10]],
        P(armour=800, jewellery=800, weapon=400, quiver=200), tags=["attribute", attr.lower()],
        variants=V(CRAFTED([[38, 42]], [[6, 10]]), IMPLICIT([[20, 25]], [[8, 12]]), CORRUPTED([[30, 40]], [[12, 18]], "ring", "amulet", "belt", "weapon")))
fam("ADD_CONSTITUTION", "SUFFIX", [eff("STOCK_CONSTITUTION")], G5, [[20, 24]], [[4, 6]], P(belt=200, body=150), tags=["attribute", "constitution"])
fam("ADD_STRENGTH_AND_DEXTERITY", "SUFFIX", [eff("STOCK_STRENGTH"), eff("STOCK_AGILITY")], G5, [[24, 28], [24, 28]], [[4, 7], [4, 7]],
    P(armour=250, jewellery=250, weapon=200), tags=["attribute", "strength", "dexterity", "hybrid"])
fam("ADD_STRENGTH_AND_INTELLIGENCE", "SUFFIX", [eff("STOCK_STRENGTH"), eff("STOCK_INTELLECT")], G5, [[24, 28], [24, 28]], [[4, 7], [4, 7]],
    P(armour=250, jewellery=250, weapon=200), tags=["attribute", "strength", "intelligence", "hybrid"])
fam("ADD_DEXTERITY_AND_INTELLIGENCE", "SUFFIX", [eff("STOCK_AGILITY"), eff("STOCK_INTELLECT")], G5, [[24, 28], [24, 28]], [[4, 7], [4, 7]],
    P(armour=250, jewellery=250, weapon=200), tags=["attribute", "dexterity", "intelligence", "hybrid"])
fam("ADD_ALL_ATTRIBUTES", "SUFFIX", [eff("STOCK_STRENGTH"), eff("STOCK_AGILITY"), eff("STOCK_INTELLECT")], G5, [[18, 22], [18, 22], [18, 22]], [[3, 5], [3, 5], [3, 5]],
    P(amulet=500, ring=200, belt=200, jewel=150), tags=["attribute", "hybrid"],
    variants=V(IMPLICIT([[10, 14], [10, 14], [10, 14]], [[4, 6], [4, 6], [4, 6]]), CORRUPTED([[14, 18], [14, 18], [14, 18]], [[6, 8], [6, 8], [6, 8]], "amulet")))

# =====================================================================
# УРОН: физический, стихийный, хаос - плоский и процентный
# =====================================================================
fam("ADD_PHYSICAL_DAMAGE", "PREFIX", [eff("STOCK_ATTACK_PHYSICAL")], G8, [[14, 24]], [[1, 3]],
    P(ring=700, amulet=400, gloves=500, quiver=600),
    tags=["damage", "physical", "attack"],
    variants=V(LOCAL("local:attack_physical", grid=G10, top=[[26, 48]], bottom=[[1, 3]]), IMPLICIT([[6, 10]], [[2, 4]]),
               CORRUPTED([[12, 20]], [[4, 8]], "ring", "gloves", "weapon"), ENCHANT([[10, 16]], [[3, 6]], "gloves")))
fam("INCREASED_PHYSICAL_DAMAGE", "PREFIX", [eff("STOCK_ATTACK_PHYSICAL", "INCREASED")], G4, [[14, 16]], [[4, 6]],
    P(jewel=700, amulet=250),
    tags=["damage", "physical", "attack"],
    variants=V(LOCAL("local:attack_physical", grid=G10, top=[[150, 170]], bottom=[[10, 20]]), CRAFTED([[110, 130]], [[10, 20]], local=True, slots=WEAPON_SLOTS),
               IMPLICIT([[10, 14]], [[4, 6]])))
fam("INCREASED_PHYSICAL_DAMAGE_AND_CRITICAL_MULTIPLIER", "PREFIX", [eff("STOCK_ATTACK_PHYSICAL", "INCREASED"), eff("STOCK_CRITICAL_MULTIPLIER")], G5, [[70, 80], [20, 25]], [[10, 15], [5, 8]],
    {"local:attack_physical": 400}, tags=["damage", "physical", "critical", "hybrid", "attack"], local=True)
for el, tag in [("FIRE", "fire"), ("COLD", "cold"), ("LIGHTNING", "lightning")]:
    fam(f"ADD_{el}_DAMAGE", "PREFIX", [eff(f"STOCK_ATTACK_{el}")], G8, [[16, 30]], [[1, 3]],
        P(ring=700, amulet=400, gloves=500, quiver=600),
        tags=["damage", "elemental", tag, "attack"],
        variants=V(LOCAL("local:attack_physical", grid=G10, top=[[30, 56]], bottom=[[2, 5]]), IMPLICIT([[6, 12]], [[2, 4]]),
                   CORRUPTED([[14, 26]], [[4, 8]], "ring", "gloves", "weapon", "quiver"), ENCHANT([[12, 22]], [[3, 6]], "gloves", "weapon")))
    fam(f"INCREASED_{el}_DAMAGE", "PREFIX", [eff(f"STOCK_ATTACK_{el}", "INCREASED")], G8, [[30, 36]], [[6, 10]],
        P(weapon=500, amulet=300, jewel=400, ring=150), tags=["damage", "elemental", tag],
        variants=V(CRAFTED([[24, 28]], [[6, 10]]), ENCHANT([[20, 30]], [[8, 12]], "weapon")))
fam("ADD_CHAOS_DAMAGE", "PREFIX", [eff("STOCK_ATTACK_CHAOS")], G8, [[12, 22]], [[1, 2]],
    P(ring=400, amulet=250, gloves=300, quiver=400), tags=["damage", "chaos", "attack"],
    variants=V(LOCAL("local:attack_physical", grid=G8, top=[[24, 40]], bottom=[[1, 3]]), CORRUPTED([[10, 18]], [[3, 6]], "ring", "weapon")))
fam("INCREASED_CHAOS_DAMAGE", "PREFIX", [eff("STOCK_ATTACK_CHAOS", "INCREASED")], G6, [[28, 34]], [[6, 10]],
    P(weapon=300, amulet=200, jewel=300), tags=["damage", "chaos"])
fam("INCREASED_ELEMENTAL_DAMAGE", "PREFIX", [eff("STOCK_ATTACK_FIRE", "INCREASED"), eff("STOCK_ATTACK_COLD", "INCREASED"), eff("STOCK_ATTACK_LIGHTNING", "INCREASED")], G6,
    [[24, 28], [24, 28], [24, 28]], [[5, 8], [5, 8], [5, 8]],
    P(weapon=400, amulet=400, ring=150, jewel=300, quiver=200), tags=["damage", "elemental", "hybrid"],
    variants=V(CRAFTED([[18, 22], [18, 22], [18, 22]], [[5, 8], [5, 8], [5, 8]]), CORRUPTED([[20, 26], [20, 26], [20, 26]], [[8, 12], [8, 12], [8, 12]], "amulet", "weapon")))
fam("INCREASED_ELEMENTAL_DAMAGE_WITH_ATTACKS", "PREFIX", [eff("STOCK_ATTACK_FIRE", "INCREASED"), eff("STOCK_ATTACK_COLD", "INCREASED"), eff("STOCK_ATTACK_LIGHTNING", "INCREASED")], G8,
    [[36, 42], [36, 42], [36, 42]], [[6, 10], [6, 10], [6, 10]],
    P(weapon=300, gloves=200, quiver=250), tags=["damage", "elemental", "hybrid", "attack"], group="INCREASED_ELEMENTAL_DAMAGE")
fam("ADD_PHYSICAL_AND_FIRE_DAMAGE", "PREFIX", [eff("STOCK_ATTACK_PHYSICAL"), eff("STOCK_ATTACK_FIRE")], G5, [[8, 14], [10, 18]], [[1, 2], [1, 3]],
    P(ring=200, quiver=200, gloves=150), tags=["damage", "physical", "fire", "hybrid", "attack"])
fam("ADD_COLD_AND_LIGHTNING_DAMAGE", "PREFIX", [eff("STOCK_ATTACK_COLD"), eff("STOCK_ATTACK_LIGHTNING")], G5, [[8, 16], [4, 24]], [[1, 2], [1, 4]],
    P(ring=200, quiver=200, gloves=150), tags=["damage", "cold", "lightning", "hybrid", "attack"])

# =====================================================================
# КРИТ, СКОРОСТЬ
# =====================================================================
fam("INCREASED_CRITICAL_STRIKE_CHANCE", "SUFFIX", [eff("STOCK_CRITICAL_CHANCE", "INCREASED")], G6, [[35, 38]], [[10, 14]],
    P(weapon=700, amulet=500, ring=200, quiver=400, jewel=400, gloves=150), tags=["critical"],
    variants=V(CRAFTED([[28, 32]], [[10, 14]]), IMPLICIT([[20, 25]], [[8, 12]]), CORRUPTED([[30, 36]], [[12, 16]], "amulet", "weapon", "quiver"), ENCHANT([[25, 35]], [[10, 15]], "weapon")))
fam("ADD_CRITICAL_STRIKE_MULTIPLIER", "SUFFIX", [eff("STOCK_CRITICAL_MULTIPLIER")], G6, [[35, 38]], [[10, 14]],
    P(weapon=700, amulet=500, ring=100, quiver=400, jewel=400, gloves=150), tags=["critical"],
    variants=V(CRAFTED([[28, 32]], [[10, 14]]), IMPLICIT([[20, 25]], [[8, 12]]), CORRUPTED([[30, 36]], [[12, 16]], "amulet", "weapon", "gloves")))
fam("INCREASED_CRITICAL_DAMAGE", "SUFFIX", [eff("STOCK_CRITICAL_DAMAGE", "INCREASED")], G4, [[20, 24]], [[6, 8]],
    P(amulet=200, jewel=300), tags=["critical"])
fam("ADD_CRITICAL_VAMPIRE", "SUFFIX", [eff("STOCK_CRITICAL_VAMPIRE")], G3, [[2, 3]], [[0.5, 1]],
    P(weapon=150, amulet=100, jewel=100), tags=["critical", "leech"], precision=1)
fam("INCREASED_ATTACK_SPEED", "SUFFIX", [eff("STOCK_ATTACK_SPEED", "INCREASED")], G6, [[13, 15]], [[3, 5]],
    P(gloves=800, quiver=500, ring=0, amulet=0, jewel=400), tags=["speed", "attack"],
    variants=V(LOCAL("local:attack_speed", grid=G8, top=[[26, 28]], bottom=[[5, 7]]), CRAFTED([[20, 22]], [[5, 7]], local=True, slots=WEAPON_SLOTS),
               CORRUPTED([[10, 14]], [[4, 6]], "gloves", "quiver", "ring"), ENCHANT([[12, 16]], [[4, 6]], "gloves")))
fam("INCREASED_MOVEMENT_SPEED", "PREFIX", [eff("STOCK_MOVEMENT_SPEED", "INCREASED")], G8, [[35, 35]], [[10, 10]],
    P(boots=1000, wings=300), tags=["speed"],
    variants=V(CRAFTED([[25, 25]], [[8, 8]], slots=["BOOTS", "WINGS"]), IMPLICIT([[15, 15]], [[5, 5]]), CORRUPTED([[25, 30]], [[10, 15]], "boots", "wings"), ENCHANT([[16, 20]], [[6, 8]], "boots")))
fam("INCREASED_LIGHT_RADIUS_AND_MOVEMENT_SPEED", "SUFFIX", [eff("STOCK_LIGHT_RADIUS", "INCREASED"), eff("STOCK_MOVEMENT_SPEED", "INCREASED")], G3, [[20, 25], [8, 10]], [[6, 10], [3, 4]],
    P(boots=250, wings=250, helmet=150), tags=["light", "speed", "hybrid"])

# =====================================================================
# РЕГЕНЕРАЦИЯ, ВАМПИРИЗМ, ВОССТАНОВЛЕНИЕ
# =====================================================================
fam("ADD_LIFE_REGENERATION", "SUFFIX", [eff("STOCK_HEALTH_REGEN")], G8, [[20, 25]], [[1, 2]],
    P(armour=500, belt=500, ring=300, amulet=300, jewel=0), tags=["regen", "life"], precision=1,
    variants=V(CRAFTED([[16, 20]], [[1, 2]]), IMPLICIT([[6, 9]], [[1, 2]]), CORRUPTED([[15, 22]], [[4, 8]], "boots", "belt", "ring"), ENCHANT([[12, 18]], [[3, 5]], "boots")))
fam("INCREASED_LIFE_REGENERATION", "SUFFIX", [eff("STOCK_HEALTH_REGEN", "INCREASED")], G5, [[40, 50]], [[10, 15]],
    P(jewel=400, belt=200, boots=200), tags=["regen", "life"])
fam("INCREASED_ENERGY_REGENERATION", "SUFFIX", [eff("STOCK_ENERGY_REGEN", "INCREASED")], G5, [[40, 50]], [[10, 15]],
    P(jewel=300, amulet=200, helmet=200), tags=["regen", "energy_shield"])
fam("ADD_ENERGY_REGENERATION", "SUFFIX", [eff("STOCK_ENERGY_REGEN")], G5, [[8, 10]], [[1, 2]],
    P(amulet=200, ring=200, helmet=200), tags=["regen", "energy_shield"], precision=1)
fam("ADD_PHYSICAL_LIFE_LEECH", "SUFFIX", [eff("STOCK_LEECH_PHYSICAL")], G5, [[1.2, 1.5]], [[0.2, 0.4]],
    P(weapon=500, ring=300, amulet=300, gloves=250, quiver=250), tags=["leech", "physical"], precision=1,
    variants=V(CRAFTED([[1, 1.2]], [[0.2, 0.4]]), IMPLICIT([[0.6, 0.8]], [[0.2, 0.3]]), CORRUPTED([[1.2, 1.6]], [[0.4, 0.6]], "gloves", "ring", "weapon")))
fam("ADD_LIFE_LEECH", "SUFFIX", [eff("STOCK_LEECH_ALL")], G4, [[0.8, 1]], [[0.2, 0.3]],
    P(amulet=200, weapon=200, jewel=200), tags=["leech"], precision=1)
fam("ADD_LIFE_ON_HIT", "SUFFIX", [eff("STOCK_HEALTH_ON_HIT")], G5, [[14, 18]], [[1, 3]],
    P(gloves=500, ring=400, quiver=300, weapon=300, amulet=150), tags=["life", "leech"],
    variants=V(CRAFTED([[10, 14]], [[1, 3]]), CORRUPTED([[12, 18]], [[4, 6]], "gloves", "ring"), ENCHANT([[10, 15]], [[3, 5]], "gloves")))
fam("ADD_LIFE_ON_KILL", "SUFFIX", [eff("STOCK_HEALTH_ON_KILL")], G5, [[30, 40]], [[3, 6]],
    P(gloves=500, ring=300, weapon=300, boots=300, belt=200), tags=["life"],
    variants=V(CORRUPTED([[30, 45]], [[10, 15]], "gloves", "boots")))
fam("INCREASED_RECOVERY_RATE", "SUFFIX", [eff("STOCK_RECOVERY_RATE")], G4, [[15, 18]], [[4, 6]],
    P(belt=400, shield=300, amulet=200, boots=200, jewel=200), tags=["regen", "leech", "life"],
    variants=V(CRAFTED([[10, 12]], [[3, 5]]), CORRUPTED([[15, 20]], [[6, 8]], "belt", "shield")))
fam("INCREASED_SHIELD_RECHARGE", "SUFFIX", [eff("STOCK_SHIELD_RECHARGE")], G4, [[30, 36]], [[8, 12]],
    P(helmet=250, shield=250, amulet=200, jewel=150), tags=["energy_shield", "regen"])

# =====================================================================
# СОСТОЯНИЯ: шансы, урон, защита от них, длительность
# =====================================================================
for ail, stat, tag, slots in [("IGNITE", "IGNITE", "fire", dict(weapon=400, gloves=300, ring=150, quiver=250, jewel=200)),
                              ("FREEZE", "FREEZE", "cold", dict(weapon=400, gloves=300, ring=150, quiver=250, jewel=200)),
                              ("SHOCK", "SHOCK", "lightning", dict(weapon=400, gloves=300, ring=150, quiver=250, jewel=200)),
                              ("POISON", "POISON", "chaos", dict(weapon=350, gloves=300, ring=100, quiver=250, jewel=200)),
                              ("BLEED", "BLEED", "physical", dict(weapon=350, gloves=300, quiver=250, jewel=200))]:
    fam(f"CHANCE_TO_{ail}", "SUFFIX", [eff(f"STOCK_{stat}_CHANCE")], G5, [[18, 22]], [[3, 6]],
        P(**slots), tags=["ailment", tag],
        variants=V(CRAFTED([[12, 15]], [[3, 6]]), CORRUPTED([[18, 25]], [[6, 10]], "weapon", "gloves"), ENCHANT([[15, 20]], [[5, 8]], "weapon", "gloves")))
for ail, tag in [("BURNING", "fire"), ("POISON", "chaos"), ("BLEED", "physical")]:
    fam(f"INCREASED_{ail}_DAMAGE", "PREFIX", [eff(f"STOCK_{ail}_DAMAGE")], G6, [[40, 48]], [[8, 14]],
        P(weapon=350, amulet=200, ring=150, quiver=200, jewel=250, gloves=100), tags=["ailment", tag, "damage"],
        variants=V(CRAFTED([[30, 36]], [[8, 12]])))
for ail, tag in [("IGNITE", "fire"), ("CHILL", "cold"), ("FREEZE", "cold"), ("SHOCK", "lightning"), ("POISON", "chaos"), ("BLEED", "physical")]:
    fam(f"AVOID_{ail}", "SUFFIX", [eff(f"STOCK_AVOID_{ail}")], G4, [[28, 35]], [[6, 10]],
        P(helmet=200, boots=250, belt=200, shield=200, wings=200, amulet=100), tags=["ailment", tag, "defences"],
        variants=V(CORRUPTED([[35, 50]], [[15, 20]], "boots", "helmet", "belt")))
    fam(f"REDUCED_{ail}_DURATION_ON_SELF", "SUFFIX", [eff(f"STOCK_{ail}_DURATION_ON_SELF")], G4, [[30, 35]], [[8, 12]],
        P(helmet=200, boots=200, belt=150, shield=150, wings=150), tags=["ailment", tag, "defences"],
        variants=V(ENCHANT([[30, 40]], [[10, 15]], "helmet", "boots")))
fam("AVOID_ELEMENTAL_AILMENTS", "SUFFIX", [eff("STOCK_AVOID_IGNITE"), eff("STOCK_AVOID_CHILL"), eff("STOCK_AVOID_SHOCK")], G3, [[20, 25], [20, 25], [20, 25]], [[6, 8], [6, 8], [6, 8]],
    P(helmet=100, boots=100, belt=100, shield=100), tags=["ailment", "elemental", "defences", "hybrid"])
fam("INCREASED_AILMENT_DURATION", "SUFFIX", [eff("STOCK_AILMENT_DURATION")], G5, [[30, 36]], [[8, 12]],
    P(weapon=300, gloves=250, quiver=200, amulet=150, jewel=200), tags=["ailment"],
    variants=V(CRAFTED([[22, 26]], [[8, 12]]), ENCHANT([[25, 35]], [[10, 15]], "gloves")))
for ail, tag in [("IGNITE", "fire"), ("CHILL", "cold"), ("FREEZE", "cold"), ("SHOCK", "lightning"), ("POISON", "chaos"), ("BLEED", "physical")]:
    fam(f"INCREASED_{ail}_DURATION", "SUFFIX", [eff(f"STOCK_{ail}_DURATION")], G4, [[40, 50]], [[10, 15]],
        P(weapon=150, gloves=120, ring=80, quiver=120, jewel=150), tags=["ailment", tag])
fam("INCREASED_DAMAGE_VS_AILED", "PREFIX", [eff("STOCK_DAMAGE_VS_AILED")], G5, [[24, 28]], [[6, 9]],
    P(weapon=300, amulet=250, gloves=200, quiver=200, jewel=250), tags=["ailment", "damage"],
    variants=V(CRAFTED([[18, 22]], [[6, 9]]), CORRUPTED([[20, 28]], [[8, 12]], "amulet", "weapon", "gloves")))
for ail, tag in [("BURNING", "fire"), ("CHILLED", "cold"), ("SHOCKED", "lightning"), ("POISONED", "chaos"), ("BLEEDING", "physical")]:
    fam(f"INCREASED_DAMAGE_VS_{ail}", "PREFIX", [eff(f"STOCK_DAMAGE_VS_{ail}")], G4, [[40, 50]], [[10, 15]],
        P(weapon=150, amulet=100, ring=100, quiver=100, jewel=150), tags=["ailment", tag, "damage"])

# =====================================================================
# ПРОБИВАНИЕ, ШИПЫ, ОТРАЖЕНИЕ, ПОЛУЧАЕМЫЙ УРОН
# =====================================================================
for el, tag in [("FIRE", "fire"), ("COLD", "cold"), ("LIGHTNING", "lightning"), ("CHAOS", "chaos")]:
    fam(f"PENETRATE_{el}_RESISTANCE", "SUFFIX", [eff(f"STOCK_PENETRATE_{el}")], G5, [[12, 15]], [[3, 5]],
        P(weapon=250, amulet=200, quiver=200, ring=100, jewel=200), tags=["penetration", tag, "damage"],
        variants=V(CRAFTED([[8, 10]], [[3, 5]]), CORRUPTED([[10, 14]], [[4, 6]], "amulet", "weapon", "quiver"), ENCHANT([[8, 12]], [[3, 5]], "weapon")))
fam("PENETRATE_ELEMENTAL_RESISTANCES", "SUFFIX", [eff("STOCK_PENETRATE_ELEMENTAL")], G4, [[8, 10]], [[2, 3]],
    P(weapon=150, amulet=150, quiver=100, jewel=150), tags=["penetration", "elemental", "damage"],
    variants=V(CORRUPTED([[8, 12]], [[3, 5]], "amulet", "weapon")))
fam("ADD_THORNS", "SUFFIX", [eff("STOCK_THORNS")], G6, [[60, 80]], [[3, 6]],
    P(body=400, shield=500, belt=150, helmet=100), tags=["thorns", "physical", "defences"],
    variants=V(CRAFTED([[40, 55]], [[3, 6]]), IMPLICIT([[20, 30]], [[4, 8]]), CORRUPTED([[50, 80]], [[15, 25]], "body", "shield")))
fam("ADD_REFLECT", "SUFFIX", [eff("STOCK_REFLECT")], G4, [[15, 20]], [[3, 5]],
    P(shield=300, body=150, jewel=100), tags=["thorns", "defences"],
    variants=V(CORRUPTED([[15, 25]], [[6, 10]], "shield")))
fam("REDUCED_DAMAGE_TAKEN", "SUFFIX", [eff("STOCK_DAMAGE_TAKEN")], G4, [[-6, -5]], [[-2, -1]],
    P(body=150, shield=150, belt=100, jewel=150), tags=["defences"],
    variants=V(CORRUPTED([[-8, -6]], [[-4, -3]], "body", "belt", "jewel")))
fam("REDUCED_PHYSICAL_DAMAGE_TAKEN", "SUFFIX", [eff("STOCK_PHYSICAL_TAKEN")], G4, [[-10, -8]], [[-3, -2]],
    P(body=200, shield=200, belt=150, helmet=100), tags=["defences", "physical"])
fam("REDUCED_ELEMENTAL_DAMAGE_TAKEN", "SUFFIX", [eff("STOCK_ELEMENTAL_TAKEN")], G4, [[-10, -8]], [[-3, -2]],
    P(body=200, shield=200, belt=150, wings=150, helmet=100), tags=["defences", "elemental"])
fam("REDUCED_CHAOS_DAMAGE_TAKEN", "SUFFIX", [eff("STOCK_CHAOS_TAKEN")], G3, [[-12, -10]], [[-4, -3]],
    P(body=120, belt=120, amulet=100), tags=["defences", "chaos"])

# =====================================================================
# ДОБЫЧА, ОПЫТ, ЗОЛОТО, СВЕТ, ПРОВОКАЦИЯ
# =====================================================================
fam("INCREASED_ITEM_RARITY", "SUFFIX", [eff("STOCK_RARITY", "INCREASED")], G6, [[24, 28]], [[6, 10]],
    P(helmet=300, gloves=300, boots=300, amulet=400, ring=400), tags=["rarity", "loot"],
    variants=V(IMPLICIT([[12, 16]], [[6, 9]]), CORRUPTED([[20, 28]], [[8, 12]], "helmet", "amulet", "ring")))
fam("PREFIX_INCREASED_ITEM_RARITY", "PREFIX", [eff("STOCK_RARITY", "INCREASED")], G5, [[18, 22]], [[5, 8]],
    P(amulet=250, ring=250, helmet=150), tags=["rarity", "loot"], group="INCREASED_ITEM_RARITY")
fam("INCREASED_ITEM_QUANTITY", "SUFFIX", [eff("STOCK_QUANTITY", "INCREASED")], G4, [[8, 10]], [[2, 3]],
    P(amulet=120, ring=120, helmet=80, boots=80, gloves=80), tags=["quantity", "loot"],
    variants=V(CORRUPTED([[8, 12]], [[3, 5]], "amulet", "ring")))
fam("INCREASED_CHEST_QUANTITY", "SUFFIX", [eff("STOCK_CHEST_QUANTITY", "INCREASED")], G3, [[25, 30]], [[8, 12]],
    P(belt=150, gloves=100, boots=100), tags=["loot"])
fam("INCREASED_CHEST_QUANTITY_AND_ITEM_RARITY", "SUFFIX", [eff("STOCK_CHEST_QUANTITY", "INCREASED"), eff("STOCK_RARITY", "INCREASED")], G3, [[15, 20], [10, 14]], [[5, 8], [3, 5]],
    P(belt=100, amulet=80), tags=["loot", "rarity", "hybrid"])
fam("INCREASED_SELL_VALUE", "SUFFIX", [eff("STOCK_GOLD", "INCREASED")], G4, [[20, 25]], [[5, 8]],
    P(belt=200, ring=150, amulet=150), tags=["gold", "loot"],
    variants=V(CORRUPTED([[20, 30]], [[8, 12]], "belt", "ring")))
fam("INCREASED_SELL_VALUE_AND_ITEM_QUANTITY", "SUFFIX", [eff("STOCK_GOLD", "INCREASED"), eff("STOCK_QUANTITY", "INCREASED")], G3, [[12, 16], [4, 6]], [[4, 6], [1, 2]],
    P(belt=100, amulet=80, ring=80), tags=["gold", "quantity", "loot", "hybrid"])
fam("INCREASED_EXPERIENCE_GAIN", "PREFIX", [eff("STOCK_EXPERIENCE", "INCREASED")], G3, [[5, 6]], [[1, 2]],
    P(amulet=100, helmet=80, wings=80), tags=["experience"],
    variants=V(CORRUPTED([[5, 8]], [[2, 3]], "amulet", "helmet")))
fam("INCREASED_LIGHT_RADIUS", "SUFFIX", [eff("STOCK_LIGHT_RADIUS", "INCREASED")], G3, [[25, 30]], [[8, 12]],
    P(helmet=300, wings=300, amulet=150, ring=100), tags=["light"],
    variants=V(IMPLICIT([[15, 20]], [[6, 10]]), ENCHANT([[20, 30]], [[8, 12]], "helmet")))
fam("ADD_TAUNT", "CORRUPTION", [eff("STOCK_TAUNT")], G1, [[1, 1]], [[1, 1]], {"corruption:shield": 30, "corruption:body": 30}, tags=["defences", "corruption"])

# =====================================================================
# ГИБРИДЫ «ПЛЮС ЗА МИНУС» (RISK_): цена одна на всех тирах
# =====================================================================
fam("RISK_PHYSICAL_DAMAGE_FOR_LIFE", "PREFIX", [eff("STOCK_ATTACK_PHYSICAL", "INCREASED"), eff("STOCK_HEALTH")], G3, [[40, 49], [-30, -30]], [[20, 29], [-30, -30]],
    P(amulet=120, ring=120, jewel=100), tags=["risk", "damage", "physical", "life", "hybrid"])
fam("RISK_ATTACK_SPEED_FOR_RESISTANCES", "SUFFIX", [eff("STOCK_ATTACK_SPEED", "INCREASED"), eff("STOCK_RESIST_ALL")], G3, [[12, 15], [-12, -12]], [[6, 8], [-12, -12]],
    P(gloves=120, quiver=120, jewel=100), tags=["risk", "speed", "resistance", "hybrid"])
fam("RISK_BLOCK_FOR_CRITICAL_MULTIPLIER", "SUFFIX", [eff("STOCK_BLOCK_CHANCE"), eff("STOCK_CRITICAL_MULTIPLIER")], G3, [[8, 10], [-20, -20]], [[3, 5], [-20, -20]],
    P(shield=150), tags=["risk", "block", "critical", "hybrid"])
fam("RISK_CRITICAL_MULTIPLIER_FOR_LIFE", "SUFFIX", [eff("STOCK_CRITICAL_MULTIPLIER"), eff("STOCK_HEALTH")], G3, [[40, 48], [-25, -25]], [[20, 26], [-25, -25]],
    P(amulet=120, weapon=120, jewel=100), tags=["risk", "critical", "life", "hybrid"])
fam("RISK_LEECH_FOR_RESISTANCES", "SUFFIX", [eff("STOCK_LEECH_ALL"), eff("STOCK_RESIST_ALL")], G3, [[1.5, 2], [-10, -10]], [[0.6, 1], [-10, -10]],
    P(ring=120, amulet=100, weapon=100), tags=["risk", "leech", "resistance", "hybrid"], precision=1)
fam("RISK_DAMAGE_FOR_DAMAGE_TAKEN", "PREFIX", [eff("STOCK_ATTACK_PHYSICAL", "INCREASED"), eff("STOCK_DAMAGE_TAKEN")], G3, [[45, 55], [8, 8]], [[20, 28], [8, 8]],
    P(weapon=120, jewel=100), tags=["risk", "damage", "physical", "hybrid"])
fam("RISK_ATTACK_SPEED_FOR_ARMOUR", "SUFFIX", [eff("STOCK_ATTACK_SPEED", "INCREASED"), eff("STOCK_ARMOR", "INCREASED")], G3, [[14, 16], [-20, -20]], [[6, 8], [-20, -20]],
    P(gloves=120, jewel=100), tags=["risk", "speed", "armour", "hybrid"])
fam("RISK_RARITY_FOR_LIFE", "SUFFIX", [eff("STOCK_RARITY", "INCREASED"), eff("STOCK_HEALTH")], G3, [[40, 50], [-20, -20]], [[15, 22], [-20, -20]],
    P(helmet=120, amulet=100, ring=100), tags=["risk", "rarity", "life", "hybrid"])
fam("RISK_ELEMENTAL_DAMAGE_FOR_CHAOS_RESISTANCE", "PREFIX", [eff("STOCK_ATTACK_FIRE", "INCREASED"), eff("STOCK_ATTACK_COLD", "INCREASED"), eff("STOCK_ATTACK_LIGHTNING", "INCREASED"), eff("STOCK_RESIST_CHAOS")], G3,
    [[30, 36], [30, 36], [30, 36], [-15, -15]], [[12, 16], [12, 16], [12, 16], [-15, -15]],
    P(weapon=100, amulet=100, jewel=100), tags=["risk", "damage", "elemental", "chaos", "hybrid"])
fam("RISK_MOVEMENT_SPEED_FOR_EVASION", "PREFIX", [eff("STOCK_MOVEMENT_SPEED", "INCREASED"), eff("STOCK_EVASION", "INCREASED")], G3, [[25, 30], [-25, -25]], [[10, 14], [-25, -25]],
    P(boots=120, wings=120), tags=["risk", "speed", "evasion", "hybrid"])
fam("RISK_RECOVERY_FOR_MAXIMUM_RESISTANCES", "SUFFIX", [eff("STOCK_RECOVERY_RATE"), eff("STOCK_RESIST_MAX_ALL")], G3, [[25, 30], [-2, -2]], [[10, 14], [-2, -2]],
    P(belt=120, shield=100), tags=["risk", "regen", "resistance", "hybrid"])
fam("RISK_LIFE_FOR_ENERGY_SHIELD", "PREFIX", [eff("STOCK_HEALTH", "INCREASED"), eff("STOCK_ENERGY_SHIELD", "INCREASED")], G3, [[12, 15], [-30, -30]], [[5, 7], [-30, -30]],
    P(body=100, belt=100, jewel=80), tags=["risk", "life", "energy_shield", "hybrid"])

# =====================================================================
# ПОРЧА: чистые минусы (источник CORRUPTION, только пул порчи)
# =====================================================================
def curse(code, effects, top, bottom, *slots, tags=(), precision=0, weight=60):
    fam(code, "CORRUPTION", effects, COR, top, bottom, {f"corruption:{s}": weight for s in slots}, tags=["corruption", "curse"] + list(tags), precision=precision)


curse("CURSE_MAXIMUM_LIFE", [eff("STOCK_HEALTH", "INCREASED")], [[-12, -10]], [[-8, -6]], "body", "belt", "amulet", "helmet", tags=["life"])
curse("CURSE_ALL_RESISTANCES", [eff("STOCK_RESIST_ALL")], [[-12, -10]], [[-8, -6]], "ring", "amulet", "gloves", "boots", "shield", "body", tags=["resistance"])
curse("CURSE_ATTACK_SPEED", [eff("STOCK_ATTACK_SPEED", "INCREASED")], [[-10, -8]], [[-6, -4]], "gloves", "weapon", "quiver", "ring", tags=["speed"])
curse("CURSE_DAMAGE_TAKEN", [eff("STOCK_DAMAGE_TAKEN")], [[8, 10]], [[6, 8]], "body", "shield", "belt", "helmet", tags=["defences"])
curse("CURSE_RECOVERY", [eff("STOCK_RECOVERY_RATE")], [[-25, -20]], [[-15, -10]], "belt", "boots", "ring", tags=["regen"])
curse("CURSE_MOVEMENT_SPEED", [eff("STOCK_MOVEMENT_SPEED", "INCREASED")], [[-12, -10]], [[-8, -5]], "boots", "wings", tags=["speed"])
curse("CURSE_DEFENCES", [eff("STOCK_ARMOR", "INCREASED"), eff("STOCK_EVASION", "INCREASED"), eff("STOCK_ENERGY_SHIELD", "INCREASED")], [[-20, -15], [-20, -15], [-20, -15]], [[-12, -8], [-12, -8], [-12, -8]], "body", "helmet", "gloves", "boots", "shield", "wings", tags=["defences"])
curse("CURSE_CRITICAL_CHANCE", [eff("STOCK_CRITICAL_CHANCE", "INCREASED")], [[-30, -25]], [[-20, -15]], "weapon", "amulet", "quiver", "gloves", tags=["critical"])
curse("CURSE_GOLD", [eff("STOCK_GOLD", "INCREASED")], [[-25, -20]], [[-15, -10]], "belt", "ring", "amulet", tags=["gold"])
curse("CURSE_LIGHT_RADIUS", [eff("STOCK_LIGHT_RADIUS", "INCREASED")], [[-30, -25]], [[-20, -15]], "helmet", "wings", tags=["light"])
curse("CURSE_STUN", [eff("STOCK_STUN_THRESHOLD", "INCREASED")], [[-40, -30]], [[-25, -20]], "body", "belt", "shield", tags=["stun"])
curse("CURSE_ATTRIBUTES", [eff("STOCK_STRENGTH"), eff("STOCK_AGILITY"), eff("STOCK_INTELLECT")], [[-12, -10], [-12, -10], [-12, -10]], [[-8, -6], [-8, -6], [-8, -6]], "amulet", "ring", "belt", "weapon", tags=["attribute"])
curse("CURSE_PHYSICAL_DAMAGE", [eff("STOCK_ATTACK_PHYSICAL", "INCREASED")], [[-20, -15]], [[-12, -8]], "weapon", "quiver", "ring", "gloves", tags=["damage", "physical"])
curse("CURSE_JEWEL_LIFE", [eff("STOCK_HEALTH", "INCREASED")], [[-6, -5]], [[-4, -3]], "jewel", tags=["life"])
curse("CURSE_MAP_LOOT", [eff("MAP_QUANTITY", "INCREASED")], [[-12, -10]], [[-8, -6]], "map", tags=["map"])

# =====================================================================
# САМОЦВЕТЫ: свой пул, малые проценты, 1-2 тира (0.66.0)
# =====================================================================
J2 = [30, 1]


def jewel(code, source, effects, top, bottom, weight=100, tags=(), precision=0):
    fam(code, source, effects, J2, top, bottom, {"jewel": weight}, tags=["jewel"] + list(tags), precision=precision)


jewel("JEWEL_INCREASED_MAXIMUM_LIFE", "PREFIX", [eff("STOCK_HEALTH", "INCREASED")], [[6, 8]], [[4, 5]], 300, ["life"])
jewel("JEWEL_INCREASED_ENERGY_SHIELD", "PREFIX", [eff("STOCK_ENERGY_SHIELD", "INCREASED")], [[8, 10]], [[5, 7]], 250, ["energy_shield"])
jewel("JEWEL_INCREASED_ARMOUR", "PREFIX", [eff("STOCK_ARMOR", "INCREASED")], [[12, 16]], [[6, 10]], 250, ["armour"])
jewel("JEWEL_INCREASED_EVASION", "PREFIX", [eff("STOCK_EVASION", "INCREASED")], [[12, 16]], [[6, 10]], 250, ["evasion"])
jewel("JEWEL_INCREASED_ARMOUR_AND_EVASION", "PREFIX", [eff("STOCK_ARMOR", "INCREASED"), eff("STOCK_EVASION", "INCREASED")], [[8, 10], [8, 10]], [[4, 6], [4, 6]], 200, ["armour", "evasion", "hybrid"])
jewel("JEWEL_INCREASED_PHYSICAL_DAMAGE", "PREFIX", [eff("STOCK_ATTACK_PHYSICAL", "INCREASED")], [[14, 18]], [[8, 12]], 250, ["damage", "physical"])
for el, tag in [("FIRE", "fire"), ("COLD", "cold"), ("LIGHTNING", "lightning"), ("CHAOS", "chaos")]:
    jewel(f"JEWEL_INCREASED_{el}_DAMAGE", "PREFIX", [eff(f"STOCK_ATTACK_{el}", "INCREASED")], [[14, 18]], [[8, 12]], 200, ["damage", tag])
jewel("JEWEL_INCREASED_ELEMENTAL_DAMAGE", "PREFIX", [eff("STOCK_ATTACK_FIRE", "INCREASED"), eff("STOCK_ATTACK_COLD", "INCREASED"), eff("STOCK_ATTACK_LIGHTNING", "INCREASED")], [[10, 12], [10, 12], [10, 12]], [[6, 8], [6, 8], [6, 8]], 200, ["damage", "elemental", "hybrid"])
jewel("JEWEL_INCREASED_ATTACK_SPEED", "SUFFIX", [eff("STOCK_ATTACK_SPEED", "INCREASED")], [[4, 6]], [[2, 3]], 200, ["speed", "attack"])
jewel("JEWEL_INCREASED_CRITICAL_STRIKE_CHANCE", "SUFFIX", [eff("STOCK_CRITICAL_CHANCE", "INCREASED")], [[12, 15]], [[6, 9]], 200, ["critical"])
jewel("JEWEL_ADD_CRITICAL_STRIKE_MULTIPLIER", "SUFFIX", [eff("STOCK_CRITICAL_MULTIPLIER")], [[12, 15]], [[6, 9]], 200, ["critical"])
jewel("JEWEL_ADD_ALL_ELEMENTAL_RESISTANCES", "SUFFIX", [eff("STOCK_RESIST_ALL")], [[10, 12]], [[6, 8]], 250, ["resistance", "elemental"])
for el, tag in [("FIRE", "fire"), ("COLD", "cold"), ("LIGHTNING", "lightning"), ("CHAOS", "chaos")]:
    jewel(f"JEWEL_ADD_{el}_RESISTANCE", "SUFFIX", [eff(f"STOCK_RESIST_{el}")], [[14, 18]], [[8, 12]], 200, ["resistance", tag])
for attr, stat in [("STRENGTH", "STOCK_STRENGTH"), ("DEXTERITY", "STOCK_AGILITY"), ("INTELLIGENCE", "STOCK_INTELLECT")]:
    jewel(f"JEWEL_ADD_{attr}", "SUFFIX", [eff(stat)], [[14, 18]], [[8, 12]], 200, ["attribute", attr.lower()])
jewel("JEWEL_ADD_ALL_ATTRIBUTES", "SUFFIX", [eff("STOCK_STRENGTH"), eff("STOCK_AGILITY"), eff("STOCK_INTELLECT")], [[6, 8], [6, 8], [6, 8]], [[3, 5], [3, 5], [3, 5]], 150, ["attribute", "hybrid"])
jewel("JEWEL_CHANCE_TO_AILMENTS", "SUFFIX", [eff("STOCK_IGNITE_CHANCE"), eff("STOCK_FREEZE_CHANCE"), eff("STOCK_SHOCK_CHANCE")], [[6, 8], [6, 8], [6, 8]], [[3, 5], [3, 5], [3, 5]], 150, ["ailment", "elemental", "hybrid"])
jewel("JEWEL_CHANCE_TO_POISON_AND_BLEED", "SUFFIX", [eff("STOCK_POISON_CHANCE"), eff("STOCK_BLEED_CHANCE")], [[6, 8], [6, 8]], [[3, 5], [3, 5]], 150, ["ailment", "chaos", "physical", "hybrid"])
jewel("JEWEL_INCREASED_AILMENT_DURATION", "SUFFIX", [eff("STOCK_AILMENT_DURATION")], [[15, 20]], [[8, 12]], 150, ["ailment"])
jewel("JEWEL_INCREASED_DAMAGE_VS_AILED", "PREFIX", [eff("STOCK_DAMAGE_VS_AILED")], [[10, 14]], [[5, 8]], 150, ["ailment", "damage"])
jewel("JEWEL_PENETRATE_ELEMENTAL_RESISTANCES", "SUFFIX", [eff("STOCK_PENETRATE_ELEMENTAL")], [[4, 6]], [[2, 3]], 120, ["penetration", "elemental"])
jewel("JEWEL_INCREASED_RECOVERY_RATE", "SUFFIX", [eff("STOCK_RECOVERY_RATE")], [[8, 10]], [[4, 6]], 150, ["regen"])
jewel("JEWEL_REDUCED_DAMAGE_TAKEN", "SUFFIX", [eff("STOCK_DAMAGE_TAKEN")], [[-3, -2]], [[-2, -1]], 100, ["defences"])
jewel("JEWEL_ADD_LIFE_LEECH", "SUFFIX", [eff("STOCK_LEECH_ALL")], [[0.5, 0.7]], [[0.2, 0.4]], 150, ["leech"], precision=1)
jewel("JEWEL_ADD_REFLECT", "SUFFIX", [eff("STOCK_REFLECT")], [[5, 8]], [[3, 4]], 100, ["thorns"])
jewel("JEWEL_INCREASED_LIFE_REGENERATION", "SUFFIX", [eff("STOCK_HEALTH_REGEN", "INCREASED")], [[20, 25]], [[10, 15]], 150, ["regen", "life"])
jewel("JEWEL_ADD_BLOCK_CHANCE", "SUFFIX", [eff("STOCK_BLOCK_CHANCE")], [[2, 3]], [[1, 2]], 100, ["block"])
jewel("JEWEL_INCREASED_MOVEMENT_SPEED", "PREFIX", [eff("STOCK_MOVEMENT_SPEED", "INCREASED")], [[4, 5]], [[2, 3]], 100, ["speed"])
jewel("JEWEL_INCREASED_ITEM_RARITY", "SUFFIX", [eff("STOCK_RARITY", "INCREASED")], [[8, 10]], [[4, 6]], 100, ["rarity", "loot"])
jewel("JEWEL_INCREASED_EXPERIENCE_GAIN", "PREFIX", [eff("STOCK_EXPERIENCE", "INCREASED")], [[2, 3]], [[1, 2]], 60, ["experience"])
jewel("JEWEL_AVOID_STUN", "SUFFIX", [eff("STOCK_AVOID_STUN")], [[10, 14]], [[5, 8]], 100, ["stun", "defences"])
jewel("JEWEL_INCREASED_BURNING_DAMAGE", "PREFIX", [eff("STOCK_BURNING_DAMAGE")], [[16, 20]], [[8, 12]], 120, ["ailment", "fire", "damage"])
jewel("JEWEL_INCREASED_POISON_DAMAGE", "PREFIX", [eff("STOCK_POISON_DAMAGE")], [[16, 20]], [[8, 12]], 120, ["ailment", "chaos", "damage"])
jewel("JEWEL_INCREASED_BLEED_DAMAGE", "PREFIX", [eff("STOCK_BLEED_DAMAGE")], [[16, 20]], [[8, 12]], 120, ["ailment", "physical", "damage"])
jewel("JEWEL_INCREASED_CRITICAL_DAMAGE", "SUFFIX", [eff("STOCK_CRITICAL_DAMAGE", "INCREASED")], [[8, 10]], [[4, 6]], 120, ["critical"])
jewel("JEWEL_ADD_THORNS", "SUFFIX", [eff("STOCK_THORNS")], [[15, 20]], [[6, 10]], 80, ["thorns", "physical"])
jewel("JEWEL_ADD_MAXIMUM_LIFE", "PREFIX", [eff("STOCK_HEALTH")], [[25, 30]], [[12, 18]], 200, ["life"])
jewel("JEWEL_ADD_ENERGY_SHIELD", "PREFIX", [eff("STOCK_ENERGY_SHIELD")], [[12, 15]], [[5, 8]], 150, ["energy_shield"])

# =====================================================================
# ВЛИЯНИЕ: Создатель - защита и стихии, Древний - здоровье, хаос, крит; по слотам
# =====================================================================
def inf(code, who, source, effects, grid, top, bottom, slots, tags=(), precision=0):
    pools = {f"influence:{who}:{s}": 100 for s in slots} if slots else {f"influence:{who}": 100}
    fam(code, source, effects, grid, top, bottom, pools, tags=["influence"] + list(tags), influence=who, precision=precision)


S, E = "SHAPER", "ELDER"
inf("SHAPER_INCREASED_MAXIMUM_LIFE", S, "PREFIX", [eff("STOCK_HEALTH", "INCREASED")], G4, [[7, 9]], [[3, 4]], ["body", "belt", "helmet"], ["life"])
inf("SHAPER_INCREASED_MAXIMUM_ENERGY_SHIELD", S, "PREFIX", [eff("STOCK_ENERGY_SHIELD", "INCREASED")], G4, [[10, 12]], [[4, 6]], ["body", "helmet", "shield", "amulet"], ["energy_shield"])
inf("SHAPER_ADD_ALL_RESISTANCES", S, "SUFFIX", [eff("STOCK_RESIST_ALL")], G4, [[12, 14]], [[4, 6]], [], ["resistance"])
inf("SHAPER_INCREASED_DEFENCES", S, "PREFIX", [eff("STOCK_ARMOR", "INCREASED"), eff("STOCK_EVASION", "INCREASED"), eff("STOCK_ENERGY_SHIELD", "INCREASED")], G4, [[20, 25], [20, 25], [20, 25]], [[8, 10], [8, 10], [8, 10]], ["body", "helmet", "gloves", "boots", "shield"], ["defences", "hybrid"])
inf("SHAPER_PENETRATE_ELEMENTAL", S, "SUFFIX", [eff("STOCK_PENETRATE_ELEMENTAL")], G4, [[10, 12]], [[4, 6]], ["weapon", "quiver", "amulet"], ["penetration", "elemental"])
inf("SHAPER_INCREASED_ELEMENTAL_DAMAGE", S, "PREFIX", [eff("STOCK_ATTACK_FIRE", "INCREASED"), eff("STOCK_ATTACK_COLD", "INCREASED"), eff("STOCK_ATTACK_LIGHTNING", "INCREASED")], G4, [[30, 36], [30, 36], [30, 36]], [[12, 16], [12, 16], [12, 16]], ["weapon", "ring", "amulet"], ["damage", "elemental", "hybrid"])
inf("SHAPER_ADD_MAXIMUM_ELEMENTAL_RESISTANCES", S, "SUFFIX", [eff("STOCK_RESIST_MAX_ALL")], G3, [[3, 4]], [[1, 2]], ["shield", "helmet", "body"], ["resistance", "elemental"])
inf("SHAPER_AVOID_ELEMENTAL_AILMENTS", S, "SUFFIX", [eff("STOCK_AVOID_IGNITE"), eff("STOCK_AVOID_CHILL"), eff("STOCK_AVOID_SHOCK")], G3, [[35, 45], [35, 45], [35, 45]], [[15, 20], [15, 20], [15, 20]], ["boots", "helmet", "belt"], ["ailment", "hybrid"])
inf("SHAPER_INCREASED_ATTACK_SPEED", S, "SUFFIX", [eff("STOCK_ATTACK_SPEED", "INCREASED")], G4, [[8, 10]], [[3, 4]], ["ring", "amulet", "gloves"], ["speed"])
inf("SHAPER_INCREASED_MOVEMENT_SPEED", S, "PREFIX", [eff("STOCK_MOVEMENT_SPEED", "INCREASED")], G3, [[12, 15]], [[5, 8]], ["boots", "wings", "belt"], ["speed"])
inf("SHAPER_REDUCED_ELEMENTAL_DAMAGE_TAKEN", S, "SUFFIX", [eff("STOCK_ELEMENTAL_TAKEN")], G3, [[-10, -8]], [[-4, -3]], ["body", "shield", "belt"], ["defences", "elemental"])
inf("SHAPER_INCREASED_SHIELD_RECHARGE", S, "SUFFIX", [eff("STOCK_SHIELD_RECHARGE")], G3, [[40, 50]], [[15, 20]], ["helmet", "shield", "amulet"], ["energy_shield"])
inf("SHAPER_INCREASED_ITEM_RARITY", S, "SUFFIX", [eff("STOCK_RARITY", "INCREASED")], G3, [[20, 25]], [[8, 12]], ["helmet", "gloves", "boots"], ["rarity"])
inf("SHAPER_ADD_THORNS", S, "SUFFIX", [eff("STOCK_THORNS")], G3, [[60, 80]], [[20, 30]], ["body", "shield"], ["thorns"])
inf("SHAPER_INCREASED_CRITICAL_STRIKE_CHANCE", S, "SUFFIX", [eff("STOCK_CRITICAL_CHANCE", "INCREASED")], G3, [[25, 30]], [[10, 14]], ["weapon", "quiver"], ["critical"])

inf("ELDER_INCREASED_CRITICAL_DAMAGE", E, "PREFIX", [eff("STOCK_CRITICAL_DAMAGE", "INCREASED")], G4, [[20, 24]], [[6, 9]], [], ["critical"])
inf("ELDER_ADD_CRITICAL_STRIKE_MULTIPLIER", E, "SUFFIX", [eff("STOCK_CRITICAL_MULTIPLIER")], G4, [[28, 32]], [[10, 14]], ["weapon", "amulet", "quiver", "gloves"], ["critical"])
inf("ELDER_ADD_LEECH", E, "SUFFIX", [eff("STOCK_LEECH_ALL")], G4, [[1, 1.3]], [[0.4, 0.6]], ["ring", "amulet", "gloves", "weapon"], ["leech"], precision=1)
inf("ELDER_ADD_MAXIMUM_LIFE", E, "PREFIX", [eff("STOCK_HEALTH")], G4, [[70, 85]], [[25, 35]], ["body", "belt", "helmet", "boots", "shield"], ["life"])
inf("ELDER_ADD_CHAOS_RESISTANCE", E, "SUFFIX", [eff("STOCK_RESIST_CHAOS")], G4, [[25, 30]], [[10, 14]], ["ring", "amulet", "belt", "body"], ["resistance", "chaos"])
inf("ELDER_INCREASED_CHAOS_DAMAGE", E, "PREFIX", [eff("STOCK_ATTACK_CHAOS", "INCREASED")], G4, [[30, 36]], [[12, 16]], ["weapon", "amulet", "ring"], ["damage", "chaos"])
inf("ELDER_CHANCE_TO_POISON_AND_BLEED", E, "SUFFIX", [eff("STOCK_POISON_CHANCE"), eff("STOCK_BLEED_CHANCE")], G3, [[15, 20], [15, 20]], [[6, 9], [6, 9]], ["weapon", "gloves", "quiver"], ["ailment", "hybrid"])
inf("ELDER_INCREASED_DAMAGE_VS_AILED", E, "PREFIX", [eff("STOCK_DAMAGE_VS_AILED")], G4, [[20, 25]], [[8, 12]], ["weapon", "amulet", "gloves", "quiver"], ["ailment", "damage"])
inf("ELDER_ADD_LIFE_ON_KILL", E, "SUFFIX", [eff("STOCK_HEALTH_ON_KILL")], G3, [[40, 55]], [[15, 20]], ["gloves", "boots", "ring"], ["life"])
inf("ELDER_INCREASED_RECOVERY_RATE", E, "SUFFIX", [eff("STOCK_RECOVERY_RATE")], G3, [[15, 20]], [[6, 8]], ["belt", "shield", "boots", "helmet"], ["regen"])
inf("ELDER_REDUCED_DAMAGE_TAKEN", E, "SUFFIX", [eff("STOCK_DAMAGE_TAKEN")], G3, [[-6, -5]], [[-3, -2]], ["body", "shield", "belt"], ["defences"])
inf("ELDER_INCREASED_AILMENT_DURATION", E, "SUFFIX", [eff("STOCK_AILMENT_DURATION")], G3, [[30, 40]], [[12, 16]], ["weapon", "gloves", "quiver", "ring"], ["ailment"])
inf("ELDER_PENETRATE_CHAOS", E, "SUFFIX", [eff("STOCK_PENETRATE_CHAOS")], G3, [[12, 15]], [[5, 7]], ["weapon", "amulet", "quiver"], ["penetration", "chaos"])
inf("ELDER_INCREASED_STUN_THRESHOLD", E, "PREFIX", [eff("STOCK_STUN_THRESHOLD", "INCREASED")], G3, [[30, 40]], [[12, 16]], ["body", "belt", "helmet", "wings"], ["stun"])
inf("ELDER_INCREASED_ITEM_QUANTITY", E, "SUFFIX", [eff("STOCK_QUANTITY", "INCREASED")], G3, [[6, 8]], [[2, 3]], ["helmet", "boots", "wings", "amulet"], ["quantity"])

# =====================================================================
# ИНСТРУМЕНТЫ: общие и по профессиям
# =====================================================================
T6 = [45, 36, 27, 18, 9, 1]
fam("WORK_SPEED", "PREFIX", [eff("STOCK_WORK_SPEED", "INCREASED")], T6, [[24, 28]], [[5, 8]], P(tool=1000), tags=["work"])
fam("WORK_YIELD", "PREFIX", [eff("STOCK_WORK_YIELD")], T6, [[40, 50]], [[8, 12]], P(tool=1000), tags=["work"])
fam("WORK_LUCK", "SUFFIX", [eff("STOCK_WORK_LUCK")], T6, [[40, 50]], [[8, 12]], P(tool=1000), tags=["work"])
fam("WORK_EXPERIENCE", "SUFFIX", [eff("STOCK_WORK_EXPERIENCE", "INCREASED")], T6, [[30, 36]], [[6, 10]], P(tool=800), tags=["work"])
fam("WORK_FIND", "SUFFIX", [eff("STOCK_WORK_FIND", "INCREASED")], T6, [[30, 36]], [[6, 10]], P(tool=800), tags=["work"])
fam("WORK_SPEED_AND_YIELD", "PREFIX", [eff("STOCK_WORK_SPEED", "INCREASED"), eff("STOCK_WORK_YIELD")], G3, [[12, 15], [20, 25]], [[4, 6], [6, 10]], P(tool=300), tags=["work", "hybrid"])
fam("WORK_LUCK_AND_FIND", "SUFFIX", [eff("STOCK_WORK_LUCK"), eff("STOCK_WORK_FIND", "INCREASED")], G3, [[20, 25], [15, 20]], [[6, 10], [4, 6]], P(tool=300), tags=["work", "hybrid"])
for prof, first, second in [("mining", "STOCK_WORK_YIELD", "STOCK_WORK_LUCK"), ("herbalism", "STOCK_WORK_FIND", "STOCK_WORK_YIELD"), ("woodcutting", "STOCK_WORK_SPEED", "STOCK_WORK_YIELD"),
                            ("smithing", "STOCK_WORK_LUCK", "STOCK_WORK_EXPERIENCE"), ("alchemy", "STOCK_WORK_EXPERIENCE", "STOCK_WORK_FIND"), ("cartography", "STOCK_WORK_SPEED", "STOCK_WORK_LUCK"),
                            ("enchanting", "STOCK_WORK_LUCK", "STOCK_WORK_SPEED")]:
    def op(stat):
        return "INCREASED" if stat in ("STOCK_WORK_SPEED", "STOCK_WORK_EXPERIENCE", "STOCK_WORK_FIND") else "ADD"
    fam(f"WORK_{prof.upper()}_MASTERY", "PREFIX", [eff(first, op(first)), eff(second, op(second))], G3, [[25, 30], [15, 20]], [[8, 12], [5, 8]],
        {f"tool:{prof}": 500}, tags=["work", "hybrid", prof])

# =====================================================================
# КАРТЫ: вред герою, усиление монстров, награды (по уровню карты)
# =====================================================================
def mapmod(code, source, effects, top, bottom, weight=100, tags=(), grid=M4):
    fam(code, source, effects, grid, top, bottom, {"map": weight}, tags=["map"] + list(tags))


mapmod("MAP_MONSTER_LIFE", "PREFIX", [eff("MAP_MONSTER_LIFE", "INCREASED")], [[30, 40]], [[8, 12]], 120)
mapmod("MAP_MONSTER_DAMAGE", "PREFIX", [eff("MAP_MONSTER_DAMAGE", "INCREASED")], [[25, 32]], [[6, 10]], 120)
mapmod("MAP_MONSTER_SPEED", "PREFIX", [eff("MAP_MONSTER_SPEED", "INCREASED")], [[20, 25]], [[5, 8]], 100)
mapmod("MAP_MONSTER_RESIST", "PREFIX", [eff("MAP_MONSTER_RESIST")], [[30, 40]], [[8, 12]], 100)
mapmod("MAP_PACK_SIZE", "PREFIX", [eff("MAP_PACK_SIZE", "INCREASED")], [[25, 35]], [[6, 10]], 120)
mapmod("MAP_MONSTER_RARITY", "PREFIX", [eff("MAP_MONSTER_RARITY", "INCREASED")], [[60, 80]], [[15, 25]], 100)
mapmod("MAP_MONSTER_PENETRATION", "PREFIX", [eff("MAP_MONSTER_PENETRATION")], [[15, 20]], [[4, 6]], 80)
mapmod("MAP_MONSTER_REFLECT", "PREFIX", [eff("MAP_MONSTER_REFLECT")], [[12, 15]], [[3, 5]], 60)
mapmod("MAP_MONSTER_CRITICAL", "PREFIX", [eff("MAP_MONSTER_CRITICAL")], [[15, 20]], [[4, 6]], 80)
mapmod("MAP_MONSTER_AILMENTS", "PREFIX", [eff("MAP_MONSTER_AILMENTS")], [[25, 35]], [[8, 12]], 80)
mapmod("MAP_MONSTER_ARMOUR", "PREFIX", [eff("MAP_MONSTER_ARMOUR", "INCREASED")], [[60, 80]], [[15, 25]], 80)
mapmod("MAP_MONSTER_LEECH", "PREFIX", [eff("MAP_MONSTER_LEECH")], [[4, 6]], [[1, 2]], 60)
mapmod("MAP_MONSTER_STUN", "PREFIX", [eff("MAP_MONSTER_STUN")], [[80, 100]], [[30, 40]], 60)
mapmod("MAP_MONSTER_MAGIC_MIN", "PREFIX", [eff("MAP_MONSTER_MAGIC_MIN")], [[1, 1]], [[1, 1]], 40, grid=[10])
mapmod("MAP_HERO_LIGHT", "SUFFIX", [eff("MAP_HERO_LIGHT", "INCREASED")], [[30, 40]], [[10, 15]], 80)
mapmod("MAP_HERO_RESIST", "SUFFIX", [eff("MAP_HERO_RESIST")], [[25, 30]], [[6, 10]], 100)
mapmod("MAP_HERO_SLOW", "PREFIX", [eff("MAP_HERO_SLOW", "INCREASED")], [[20, 25]], [[5, 8]], 80)
mapmod("MAP_HERO_REGEN", "SUFFIX", [eff("MAP_HERO_REGEN", "INCREASED")], [[60, 80]], [[20, 30]], 80)
mapmod("MAP_HERO_DAMAGE_TAKEN", "SUFFIX", [eff("MAP_HERO_DAMAGE_TAKEN")], [[15, 20]], [[4, 6]], 80)
mapmod("MAP_HERO_RECOVERY", "SUFFIX", [eff("MAP_HERO_RECOVERY")], [[30, 40]], [[10, 15]], 80)
mapmod("MAP_HERO_MAX_RESIST", "SUFFIX", [eff("MAP_HERO_MAX_RESIST")], [[8, 10]], [[2, 3]], 60)
mapmod("MAP_HERO_DEFENCES", "SUFFIX", [eff("MAP_HERO_DEFENCES")], [[30, 40]], [[10, 15]], 80)
mapmod("MAP_HERO_BLOCK", "SUFFIX", [eff("MAP_HERO_BLOCK")], [[20, 25]], [[6, 10]], 60)
mapmod("MAP_HERO_CRIT", "SUFFIX", [eff("MAP_HERO_CRIT")], [[40, 50]], [[15, 20]], 60)
mapmod("MAP_HERO_HASTE", "SUFFIX", [eff("MAP_HERO_HASTE")], [[15, 20]], [[5, 8]], 40, ["reward"])
mapmod("MAP_HERO_ATTACK_SPEED", "SUFFIX", [eff("MAP_HERO_ATTACK_SPEED")], [[10, 14]], [[3, 5]], 40, ["reward"])
mapmod("MAP_HERO_LIFE", "SUFFIX", [eff("MAP_HERO_LIFE")], [[12, 15]], [[4, 6]], 40, ["reward"])
mapmod("MAP_HERO_LEECH", "SUFFIX", [eff("MAP_HERO_LEECH")], [[2, 3]], [[0.5, 1]], 40, ["reward"])
mapmod("MAP_CHESTS", "SUFFIX", [eff("MAP_CHESTS")], [[2, 3]], [[1, 1]], 60, ["reward"], grid=[10, 1])
mapmod("MAP_FOUNTAINS", "SUFFIX", [eff("MAP_FOUNTAINS")], [[2, 2]], [[1, 1]], 40, ["reward"], grid=[10, 1])
mapmod("MAP_QUANTITY", "SUFFIX", [eff("MAP_QUANTITY", "INCREASED")], [[14, 18]], [[4, 6]], 80, ["reward"])
mapmod("MAP_RARITY", "SUFFIX", [eff("MAP_RARITY", "INCREASED")], [[25, 30]], [[6, 10]], 80, ["reward"])
mapmod("MAP_EXPERIENCE", "SUFFIX", [eff("MAP_EXPERIENCE", "INCREASED")], [[10, 14]], [[3, 5]], 60, ["reward"])
mapmod("MAP_GOLD", "SUFFIX", [eff("MAP_GOLD", "INCREASED")], [[30, 40]], [[8, 12]], 60, ["reward"])
mapmod("MAP_BOSS_POWER", "PREFIX", [eff("MAP_BOSS_POWER")], [[40, 50]], [[10, 15]], 50)
# Ручная работа картографа (сверх аффиксов)
fam("HC_MAP_QUANTITY", "HANDCRAFTED", [eff("MAP_QUANTITY", "INCREASED")], M3, [[10, 12]], [[3, 5]], {"handcrafted:map": 100}, tags=["map", "handcrafted", "reward"])
fam("HC_MAP_RARITY", "HANDCRAFTED", [eff("MAP_RARITY", "INCREASED")], M3, [[15, 20]], [[5, 8]], {"handcrafted:map": 100}, tags=["map", "handcrafted", "reward"])
fam("HC_MAP_EXPERIENCE", "HANDCRAFTED", [eff("MAP_EXPERIENCE", "INCREASED")], M3, [[6, 8]], [[2, 3]], {"handcrafted:map": 80}, tags=["map", "handcrafted", "reward"])
fam("HC_MAP_CHESTS", "HANDCRAFTED", [eff("MAP_CHESTS")], [10, 1], [[2, 2]], [[1, 1]], {"handcrafted:map": 60}, tags=["map", "handcrafted", "reward"])
fam("HC_MAP_GOLD", "HANDCRAFTED", [eff("MAP_GOLD", "INCREASED")], M3, [[20, 25]], [[6, 10]], {"handcrafted:map": 60}, tags=["map", "handcrafted", "reward"])
fam("HC_MAP_FOUNTAINS", "HANDCRAFTED", [eff("MAP_FOUNTAINS")], [10, 1], [[2, 2]], [[1, 1]], {"handcrafted:map": 50}, tags=["map", "handcrafted", "reward"])
# Строки «Алхимия»
for code, stat, val in [("ALC_MAP_PACK", "MAP_PACK_SIZE", [20, 25]), ("ALC_MAP_MAGIC", "MAP_MAGIC_MONSTERS", [40, 50]), ("ALC_MAP_RARE", "MAP_RARE_MONSTERS", [40, 50]),
                        ("ALC_MAP_LOOT", "MAP_QUANTITY", [8, 10]), ("ALC_MAP_CHESTS", "MAP_CHESTS", [2, 3]), ("ALC_MAP_GOLD", "MAP_GOLD", [25, 30]), ("ALC_MAP_BOSS", "MAP_BOSS_POWER", [30, 40])]:
    op = "ADD" if stat in ("MAP_CHESTS", "MAP_BOSS_POWER") else "INCREASED"
    fam(code, "ALCHEMY", [eff(stat, op)], G1, [val], [val], {}, tags=["map", "alchemy"])

# =====================================================================
# РУЧНАЯ РАБОТА КУЗНЕЦА: броня и оружие
# =====================================================================
HC = [40, 21, 1]
for code, stat, top, bottom, pool in [("HC_LIFE", "STOCK_HEALTH", [45, 60], [15, 25], "armour"), ("HC_ARMOUR", "STOCK_ARMOR", [40, 50], [10, 15], "armour"),
                                      ("HC_EVASION", "STOCK_EVASION", [40, 50], [10, 15], "armour"), ("HC_ENERGY_SHIELD", "STOCK_ENERGY_SHIELD", [25, 35], [5, 10], "armour"),
                                      ("HC_RESIST_ALL", "STOCK_RESIST_ALL", [10, 12], [4, 6], "armour"), ("HC_STUN", "STOCK_STUN_THRESHOLD", [15, 20], [4, 6], "armour"),
                                      ("HC_THORNS", "STOCK_THORNS", [30, 40], [8, 12], "armour"), ("HC_REGEN", "STOCK_HEALTH_REGEN", [8, 10], [2, 3], "armour")]:
    op = "INCREASED" if stat in ("STOCK_ARMOR", "STOCK_EVASION", "STOCK_ENERGY_SHIELD") else "ADD"
    fam(code, "HANDCRAFTED", [eff(stat, op)], HC, [top], [bottom], {"handcrafted:smith": 100, f"handcrafted:smith:{pool}": 100}, tags=["handcrafted"])
for code, stat, top, bottom in [("HC_FIRE", "STOCK_ATTACK_FIRE", [12, 20], [3, 6]), ("HC_COLD", "STOCK_ATTACK_COLD", [12, 20], [3, 6]), ("HC_LIGHTNING", "STOCK_ATTACK_LIGHTNING", [10, 24], [2, 7]),
                                ("HC_CHAOS", "STOCK_ATTACK_CHAOS", [10, 16], [2, 5]), ("HC_PHYSICAL", "STOCK_ATTACK_PHYSICAL", [10, 16], [2, 5]),
                                ("HC_CRITICAL", "STOCK_CRITICAL_MULTIPLIER", [15, 20], [5, 8]), ("HC_PENETRATION", "STOCK_PENETRATE_ELEMENTAL", [5, 7], [2, 3]), ("HC_LEECH", "STOCK_LEECH_PHYSICAL", [0.6, 0.8], [0.2, 0.3])]:
    fam(code, "HANDCRAFTED", [eff(stat)], HC, [top], [bottom], {"handcrafted:smith": 100, "handcrafted:smith:weapon": 100}, tags=["handcrafted"], precision=1 if stat == "STOCK_LEECH_PHYSICAL" else 0)
fam("HC_SPEED", "HANDCRAFTED", [eff("STOCK_ATTACK_SPEED", "INCREASED")], HC, [[8, 10]], [[3, 4]], {"handcrafted:smith": 100, "handcrafted:smith:weapon": 100, "handcrafted:smith:armour": 40}, tags=["handcrafted"])

# =====================================================================
# ПАССИВКИ ДЕРЕВА И БАЗЫ (значения задаёт узел или шаблон)
# =====================================================================
for code, stat, op, per, amount in [("CONVERT_STRENGTH_TO_LIFE", "STOCK_HEALTH", "ADD", "STOCK_STRENGTH", 2), ("CONVERT_DEXTERITY_TO_EVASION", "STOCK_EVASION", "ADD", "STOCK_AGILITY", 1),
                                    ("CONVERT_INTELLIGENCE_TO_ENERGY_SHIELD", "STOCK_ENERGY_SHIELD", "INCREASED", "STOCK_INTELLECT", 5), ("CONVERT_STRENGTH_TO_PHYSICAL_DAMAGE", "STOCK_ATTACK_PHYSICAL", "INCREASED", "STOCK_STRENGTH", 5)]:
    fam(code, "PASSIVE", [eff(stat, op, per, amount)], G1, [[1, 1]], [[1, 1]], {}, tags=["passive", "conversion"])
for code, stat, op in [("PASSIVE_SET_CRITICAL_STRIKE_CHANCE", "STOCK_CRITICAL_CHANCE", "SET"), ("PASSIVE_SET_MAXIMUM_LIFE", "STOCK_HEALTH", "SET"), ("PASSIVE_SET_EVASION_RATING", "STOCK_EVASION", "SET"),
                       ("PASSIVE_SET_CHAOS_RESISTANCE", "STOCK_RESIST_CHAOS", "SET"), ("PASSIVE_SET_LIFE_REGENERATION", "STOCK_HEALTH_REGEN", "SET"), ("PASSIVE_SET_CRITICAL_DAMAGE", "STOCK_CRITICAL_DAMAGE", "SET"),
                       ("PASSIVE_MORE_ARMOUR", "STOCK_ARMOR", "MORE"), ("PASSIVE_MORE_EVASION_RATING", "STOCK_EVASION", "MORE"), ("PASSIVE_MORE_MAXIMUM_LIFE", "STOCK_HEALTH", "MORE"),
                       ("PASSIVE_MORE_PHYSICAL_DAMAGE", "STOCK_ATTACK_PHYSICAL", "MORE"), ("PASSIVE_MORE_STUN_THRESHOLD", "STOCK_STUN_THRESHOLD", "MORE"), ("PASSIVE_MORE_LIFE_LEECH", "STOCK_LEECH_PHYSICAL", "MORE")]:
    fam(code, "PASSIVE", [eff(stat, op)], G1, [[1, 1]], [[1, 1]], {}, tags=["passive"])
fam("PASSIVE_MORE_ELEMENTAL_DAMAGE", "PASSIVE", [eff("STOCK_ATTACK_FIRE", "MORE"), eff("STOCK_ATTACK_COLD", "MORE"), eff("STOCK_ATTACK_LIGHTNING", "MORE")], G1, [[1, 1], [1, 1], [1, 1]], [[1, 1], [1, 1], [1, 1]], {}, tags=["passive"])
for code, stat in [("BASE_ARMOUR", "STOCK_ARMOR"), ("BASE_EVASION", "STOCK_EVASION"), ("BASE_ENERGY_SHIELD", "STOCK_ENERGY_SHIELD"), ("BASE_PHYSICAL_DAMAGE", "STOCK_ATTACK_PHYSICAL"),
                   ("BASE_ATTACK_SPEED", "STOCK_ATTACK_SPEED"), ("BASE_BLOCK_CHANCE", "STOCK_BLOCK_CHANCE")]:
    fam(code, "IMPLICIT", [eff(stat)], G1, [[1, 1]], [[1, 1]], {}, tags=["base", "implicit"], local=True)
# Имплиситы баз, которых нет среди аффиксов
fam("IMPLICIT_ADD_MAXIMUM_MANA", "IMPLICIT", [eff("STOCK_MANA")], IMP, [[30, 40]], [[10, 15]], {}, tags=["implicit", "mana"])
fam("IMPLICIT_INCREASED_SPELL_DAMAGE", "IMPLICIT", [eff("STOCK_ATTACK_MAGICAL", "INCREASED")], IMP, [[20, 25]], [[8, 12]], {}, tags=["implicit", "caster"])

# =====================================================================
# МОНСТРЫ: тиры по уровню карты; MAGIC - у волшебных и редких, RARE - у редких, UNIQUE - только босс
# =====================================================================
def mob(code, effects, top, bottom, rarity="MAGIC", weight=100, tags=(), boss=None, grid=MOB4, precision=0):
    pools = {}
    if rarity != "UNIQUE":
        pools["monster"] = weight
    pools["boss"] = boss if boss is not None else weight
    fam(code, "MONSTER", effects, grid, top, bottom, pools, tags=["monster"] + list(tags), minRarity=rarity, precision=precision)


mob("MOB_TOUGH", [eff("STOCK_HEALTH", "INCREASED")], [[80, 100]], [[40, 60]], weight=120, tags=["life"])
mob("MOB_HASTED", [eff("STOCK_ATTACK_SPEED", "INCREASED")], [[35, 45]], [[20, 30]], weight=90, tags=["speed"])
mob("MOB_STRONG", [eff("STOCK_ATTACK_PHYSICAL", "INCREASED")], [[50, 65]], [[30, 40]], weight=120, tags=["damage"])
mob("MOB_FLAMING", [eff("STOCK_ATTACK_FIRE")], [[5, 7]], [[2, 3]], weight=80, tags=["fire"])
mob("MOB_FREEZING", [eff("STOCK_ATTACK_COLD")], [[5, 7]], [[2, 3]], weight=80, tags=["cold"])
mob("MOB_SHOCKING", [eff("STOCK_ATTACK_LIGHTNING")], [[5, 7]], [[2, 3]], weight=80, tags=["lightning"])
mob("MOB_VENOMOUS", [eff("STOCK_ATTACK_CHAOS")], [[4, 5]], [[1, 2]], weight=60, tags=["chaos"])
mob("MOB_ARMOURED", [eff("STOCK_ARMOR")], [[40, 55]], [[15, 25]], weight=90, tags=["armour"])
mob("MOB_EVASIVE", [eff("STOCK_EVASION")], [[40, 55]], [[15, 25]], weight=90, tags=["evasion"])
mob("MOB_REGENERATING", [eff("STOCK_HEALTH_REGEN")], [[2, 3]], [[0.5, 1]], weight=70, tags=["regen"], precision=1)
mob("MOB_WARDED", [eff("STOCK_RESIST_ALL")], [[30, 40]], [[12, 20]], weight=70, tags=["resistance"])
mob("MOB_TAUNTING", [eff("STOCK_TAUNT"), eff("STOCK_HEALTH", "INCREASED")], [[1, 1], [35, 45]], [[1, 1], [20, 25]], weight=60, tags=["taunt"])
mob("MOB_SWIFT", [eff("STOCK_MOVEMENT_SPEED", "INCREASED")], [[30, 40]], [[15, 20]], weight=60, tags=["speed"])
mob("MOB_STURDY", [eff("STOCK_STUN_THRESHOLD")], [[15, 20]], [[5, 8]], weight=70, tags=["stun"])
mob("MOB_SHIELDED", [eff("STOCK_ENERGY_SHIELD")], [[20, 28]], [[8, 12]], weight=60, tags=["energy_shield"])
mob("MOB_FIREPROOF", [eff("STOCK_RESIST_FIRE"), eff("STOCK_ATTACK_FIRE")], [[50, 60], [3, 4]], [[25, 35], [1, 2]], weight=50, tags=["fire", "resistance"])
mob("MOB_FROSTBORN", [eff("STOCK_RESIST_COLD"), eff("STOCK_ATTACK_COLD")], [[50, 60], [3, 4]], [[25, 35], [1, 2]], weight=50, tags=["cold", "resistance"])
mob("MOB_STORMBORN", [eff("STOCK_RESIST_LIGHTNING"), eff("STOCK_ATTACK_LIGHTNING")], [[50, 60], [3, 4]], [[25, 35], [1, 2]], weight=50, tags=["lightning", "resistance"])
mob("MOB_BLIGHTED", [eff("STOCK_RESIST_CHAOS"), eff("STOCK_ATTACK_CHAOS")], [[50, 60], [2, 3]], [[25, 35], [1, 1]], weight=50, tags=["chaos", "resistance"])
mob("MOB_BURNING", [eff("STOCK_IGNITE_CHANCE"), eff("STOCK_BURNING_DAMAGE")], [[30, 40], [40, 60]], [[15, 20], [20, 30]], weight=50, tags=["ailment", "fire"])
mob("MOB_CHILLING", [eff("STOCK_FREEZE_CHANCE"), eff("STOCK_CHILL_DURATION")], [[25, 35], [40, 60]], [[10, 15], [20, 30]], weight=50, tags=["ailment", "cold"])
mob("MOB_CRACKLING", [eff("STOCK_SHOCK_CHANCE"), eff("STOCK_SHOCK_DURATION")], [[25, 35], [40, 60]], [[10, 15], [20, 30]], weight=50, tags=["ailment", "lightning"])
mob("MOB_TOXIC", [eff("STOCK_POISON_CHANCE"), eff("STOCK_POISON_DAMAGE")], [[30, 40], [40, 60]], [[15, 20], [20, 30]], weight=50, tags=["ailment", "chaos"])
mob("MOB_GORING", [eff("STOCK_BLEED_CHANCE"), eff("STOCK_BLEED_DAMAGE")], [[30, 40], [40, 60]], [[15, 20], [20, 30]], weight=50, tags=["ailment", "physical"])
mob("MOB_STALWART", [eff("STOCK_BLOCK_CHANCE")], [[25, 30]], [[12, 18]], "RARE", 60, ["block"])
mob("MOB_VAMPIRIC", [eff("STOCK_LEECH_ALL")], [[5, 7]], [[2, 3]], "RARE", 60, ["leech"])
mob("MOB_DEADLY", [eff("STOCK_CRITICAL_CHANCE"), eff("STOCK_CRITICAL_MULTIPLIER")], [[15, 20], [60, 80]], [[8, 10], [30, 40]], "RARE", 60, ["critical"])
mob("MOB_BERSERK", [eff("STOCK_ATTACK_PHYSICAL", "MORE"), eff("STOCK_ATTACK_SPEED", "INCREASED")], [[40, 50], [25, 30]], [[20, 30], [12, 18]], "RARE", 50, ["damage", "speed"])
mob("MOB_JUGGERNAUT", [eff("STOCK_ARMOR", "INCREASED"), eff("STOCK_STUN_THRESHOLD")], [[150, 200], [15, 20]], [[70, 100], [5, 8]], "RARE", 50, ["armour", "stun"])
mob("MOB_ELEMENTALIST", [eff("STOCK_ATTACK_FIRE"), eff("STOCK_ATTACK_COLD"), eff("STOCK_ATTACK_LIGHTNING")], [[4, 5], [4, 5], [4, 5]], [[1, 2], [1, 2], [1, 2]], "RARE", 50, ["elemental"])
mob("MOB_UNDYING", [eff("STOCK_HEALTH", "MORE"), eff("STOCK_HEALTH_REGEN")], [[60, 80], [3, 4]], [[35, 50], [1, 2]], "RARE", 40, ["life", "regen"])
mob("MOB_ARCANE", [eff("STOCK_ATTACK_MAGICAL"), eff("STOCK_MANA"), eff("STOCK_CAST_SPEED", "INCREASED")], [[5, 6], [30, 40], [30, 40]], [[2, 3], [15, 20], [15, 20]], "RARE", 40, ["caster"])
mob("MOB_PIERCING", [eff("STOCK_PENETRATE_ELEMENTAL")], [[20, 25]], [[8, 12]], "RARE", 50, ["penetration"])
mob("MOB_THORNY", [eff("STOCK_THORNS")], [[25, 35]], [[6, 10]], "RARE", 50, ["thorns"])
mob("MOB_MIRRORED", [eff("STOCK_REFLECT")], [[20, 30]], [[8, 12]], "RARE", 40, ["thorns"])
mob("MOB_RELENTLESS", [eff("STOCK_AVOID_STUN"), eff("STOCK_AVOID_FREEZE")], [[80, 100], [80, 100]], [[40, 50], [40, 50]], "RARE", 50, ["stun", "defences"])
mob("MOB_STEADFAST", [eff("STOCK_DAMAGE_TAKEN")], [[-25, -20]], [[-12, -8]], "RARE", 50, ["defences"])
mob("MOB_HUNGERING", [eff("STOCK_HEALTH_ON_HIT")], [[8, 12]], [[3, 5]], "RARE", 50, ["leech"])
mob("MOB_MERCILESS", [eff("STOCK_DAMAGE_VS_AILED")], [[40, 60]], [[20, 30]], "RARE", 50, ["ailment", "damage"])
mob("MOB_LINGERING", [eff("STOCK_AILMENT_DURATION")], [[60, 80]], [[30, 40]], "RARE", 40, ["ailment"])
mob("MOB_WITHERING", [eff("AURA_RESIST")], [[20, 25]], [[8, 12]], "RARE", 40, ["aura"])
mob("MOB_CRUSHING", [eff("AURA_DAMAGE_TAKEN")], [[15, 20]], [[6, 10]], "RARE", 40, ["aura"])
mob("MOB_SLOWING", [eff("AURA_SLOW")], [[15, 20]], [[6, 10]], "RARE", 40, ["aura"])
mob("MOB_DRAINING", [eff("AURA_RECOVERY")], [[30, 40]], [[12, 20]], "RARE", 40, ["aura"])
mob("MOB_VICIOUS", [eff("STOCK_CRITICAL_DAMAGE", "INCREASED")], [[40, 50]], [[20, 30]], "RARE", 40, ["critical"])
mob("MOB_MASSIVE", [eff("STOCK_HEALTH", "MORE"), eff("STOCK_MOVEMENT_SPEED", "INCREASED")], [[50, 60], [-20, -20]], [[30, 40], [-20, -20]], "RARE", 40, ["life", "speed"])
mob("MOB_GLASS", [eff("STOCK_ATTACK_PHYSICAL", "MORE"), eff("STOCK_HEALTH", "INCREASED")], [[70, 90], [-30, -30]], [[40, 50], [-30, -30]], "RARE", 40, ["damage", "life"])
# Босс: только в пуле боссов
mob("BOSS_COLOSSAL", [eff("STOCK_HEALTH", "MORE"), eff("STOCK_STUN_THRESHOLD", "INCREASED")], [[60, 80], [80, 100]], [[30, 40], [40, 60]], "UNIQUE", 0, ["life", "stun"], boss=100)
mob("BOSS_DEVASTATING", [eff("STOCK_ATTACK_PHYSICAL", "MORE"), eff("STOCK_ATTACK_FIRE", "MORE"), eff("STOCK_ATTACK_COLD", "MORE"), eff("STOCK_ATTACK_LIGHTNING", "MORE"), eff("STOCK_ATTACK_CHAOS", "MORE")],
    [[40, 50]] * 5, [[20, 25]] * 5, "UNIQUE", 0, ["damage"], boss=100)
mob("BOSS_SIEGE", [eff("STOCK_ARMOR", "INCREASED"), eff("STOCK_EVASION", "INCREASED"), eff("STOCK_RESIST_ALL")], [[100, 150], [100, 150], [30, 40]], [[50, 70], [50, 70], [15, 20]], "UNIQUE", 0, ["defences"], boss=100)
mob("BOSS_DREAD", [eff("AURA_DAMAGE_TAKEN"), eff("AURA_RESIST")], [[20, 25], [20, 25]], [[10, 12], [10, 12]], "UNIQUE", 0, ["aura"], boss=80)
mob("BOSS_EXECUTIONER", [eff("STOCK_CRITICAL_CHANCE"), eff("STOCK_CRITICAL_MULTIPLIER"), eff("STOCK_DAMAGE_VS_AILED")], [[20, 25], [80, 100], [40, 50]], [[10, 12], [40, 50], [20, 25]], "UNIQUE", 0, ["critical"], boss=80)
mob("BOSS_PLAGUEBEARER", [eff("STOCK_POISON_CHANCE"), eff("STOCK_BLEED_CHANCE"), eff("STOCK_AILMENT_DURATION")], [[50, 60], [50, 60], [80, 100]], [[25, 30], [25, 30], [40, 50]], "UNIQUE", 0, ["ailment"], boss=80)
mob("BOSS_TEMPEST", [eff("STOCK_IGNITE_CHANCE"), eff("STOCK_FREEZE_CHANCE"), eff("STOCK_SHOCK_CHANCE"), eff("STOCK_PENETRATE_ELEMENTAL")], [[40, 50], [40, 50], [40, 50], [25, 30]], [[20, 25], [20, 25], [20, 25], [10, 15]], "UNIQUE", 0, ["ailment", "penetration"], boss=80)
mob("BOSS_IMMORTAL", [eff("STOCK_HEALTH_REGEN"), eff("STOCK_LEECH_ALL"), eff("STOCK_RECOVERY_RATE")], [[5, 6], [8, 10], [50, 60]], [[2, 3], [4, 5], [25, 30]], "UNIQUE", 0, ["regen", "leech"], boss=80, precision=1)
mob("BOSS_MIRROR_SHELL", [eff("STOCK_REFLECT"), eff("STOCK_THORNS"), eff("STOCK_DAMAGE_TAKEN")], [[30, 40], [40, 60], [-20, -15]], [[15, 20], [15, 25], [-10, -8]], "UNIQUE", 0, ["thorns", "defences"], boss=80)
mob("BOSS_WARLORD", [eff("STOCK_ATTACK_SPEED", "INCREASED"), eff("STOCK_MOVEMENT_SPEED", "INCREASED"), eff("AURA_SLOW")], [[40, 50], [30, 40], [20, 25]], [[20, 25], [15, 20], [10, 12]], "UNIQUE", 0, ["speed", "aura"], boss=80)
