#!/usr/bin/env python3
"""
Генератор дерева навыков (0.51.0): неровное колесо в духе POE.

Scion в центре, шесть классов по кругу со своими ветвями, кластерами, мастерствами,
атрибутными узлами, гнёздами и двумя keystone на класс; соседние классы связаны мостами.
Результат детерминирован (фиксированное зерно): пишет src/main/resources/skilltree/tree.json
и ключи skilltree.* в locale/ru.json и locale/en.json (старые ключи дерева удаляются,
названия стартовых узлов сохраняются).

    python3 scripts/gen_skill_tree.py
"""
import json, math, random
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TREE = ROOT / "src/main/resources/skilltree/tree.json"
LOCALES = {"ru": ROOT / "src/main/resources/locale/ru.json", "en": ROOT / "src/main/resources/locale/en.json"}
rng = random.Random(51)

# Значение бонуса на малом узле и на notable; число значений = число эффектов модификатора.
V = {
    "ADD_MAXIMUM_LIFE": ([10], [30]), "INCREASED_MAXIMUM_LIFE": ([4], [10]), "ADD_LIFE_REGENERATION": ([2], [6]),
    "ADD_LIFE_ON_KILL": ([3], [8]), "ADD_LIFE_ON_HIT": ([1], [3]), "ADD_PHYSICAL_LIFE_LEECH": ([1], [2]),
    "ADD_ARMOUR": ([18], [50]), "INCREASED_ARMOUR": ([8], [22]), "INCREASED_STUN_THRESHOLD": ([10], [25]),
    "AVOID_STUN": ([4], [10]), "ADD_PHYSICAL_DAMAGE_REDUCTION": ([1], [3]),
    "ADD_EVASION_RATING": ([18], [50]), "INCREASED_EVASION_RATING": ([8], [22]), "INCREASED_MOVEMENT_SPEED": ([2], [6]),
    "ADD_ENERGY_SHIELD": ([8], [25]), "INCREASED_ENERGY_SHIELD": ([8], [22]), "INCREASED_ENERGY_REGENERATION": ([8], [20]),
    "INCREASED_ARMOUR_AND_EVASION": ([6, 6], [16, 16]), "INCREASED_ARMOUR_AND_ENERGY_SHIELD": ([6, 6], [16, 16]),
    "INCREASED_EVASION_AND_ENERGY_SHIELD": ([6, 6], [16, 16]),
    "INCREASED_PHYSICAL_DAMAGE": ([8], [22]), "INCREASED_ATTACK_SPEED": ([3], [8]), "ADD_BLOCK_CHANCE": ([2], [5]),
    "INCREASED_CRITICAL_STRIKE_CHANCE": ([10], [28]), "ADD_CRITICAL_STRIKE_MULTIPLIER": ([6], [18]),
    "INCREASED_ELEMENTAL_DAMAGE": ([6, 6, 6], [16, 16, 16]), "INCREASED_FIRE_DAMAGE": ([8], [22]),
    "INCREASED_COLD_DAMAGE": ([8], [22]), "INCREASED_LIGHTNING_DAMAGE": ([8], [22]), "INCREASED_CHAOS_DAMAGE": ([8], [22]),
    "ADD_CHAOS_DAMAGE": ([3], [10]),
    "CHANCE_TO_IGNITE": ([3], [8]), "CHANCE_TO_FREEZE": ([3], [8]), "CHANCE_TO_SHOCK": ([3], [8]),
    "CHANCE_TO_POISON": ([3], [8]), "CHANCE_TO_BLEED": ([3], [8]),
    "INCREASED_BURNING_DAMAGE": ([8], [20]), "INCREASED_POISON_DAMAGE": ([8], [20]), "INCREASED_BLEED_DAMAGE": ([8], [20]),
    "ADD_FIRE_RESISTANCE": ([8], [20]), "ADD_COLD_RESISTANCE": ([8], [20]), "ADD_LIGHTNING_RESISTANCE": ([8], [20]),
    "ADD_CHAOS_RESISTANCE": ([6], [16]), "ADD_ALL_ELEMENTAL_RESISTANCES": ([4], [12]), "AVOID_POISON": ([6], [15]),
    "ADD_STRENGTH": ([8], [20]), "ADD_DEXTERITY": ([8], [20]), "ADD_INTELLIGENCE": ([8], [20]), "ADD_ALL_ATTRIBUTES": ([4, 4, 4], [10, 10, 10]),
    "ADD_STRENGTH_AND_DEXTERITY": ([6, 6], [14, 14]), "ADD_DEXTERITY_AND_INTELLIGENCE": ([6, 6], [14, 14]), "ADD_STRENGTH_AND_INTELLIGENCE": ([6, 6], [14, 14]),
    "INCREASED_ITEM_RARITY": ([5], [12]), "INCREASED_ITEM_QUANTITY": ([3], [8]), "INCREASED_SELL_VALUE": ([8], [20]),
    "INCREASED_LIGHT_RADIUS": ([8], [15]), "INCREASED_CHEST_QUANTITY": ([6], [15]), "INCREASED_EXPERIENCE_GAIN": ([2], [5]),
    "WORK_SPEED": ([6], [12]), "WORK_YIELD": ([8], [15]), "WORK_LUCK": ([6], [12]), "WORK_EXPERIENCE": ([10], [20]), "WORK_FIND": ([12], [25]),
}

# Темы: статы малых узлов и notable, три варианта мастерства (по два бонуса), имена notable.
THEMES = {
    "life": dict(ru="Здоровье", en="Life", stats=["ADD_MAXIMUM_LIFE", "INCREASED_MAXIMUM_LIFE", "ADD_LIFE_REGENERATION", "ADD_LIFE_ON_KILL"],
        mastery=[[("INCREASED_MAXIMUM_LIFE", [8]), ("ADD_LIFE_REGENERATION", [5])], [("ADD_LIFE_ON_KILL", [12]), ("ADD_MAXIMUM_LIFE", [20])], [("ADD_LIFE_ON_HIT", [4]), ("ADD_PHYSICAL_LIFE_LEECH", [1])]],
        names=[("Бычье сердце", "Heart of the Ox"), ("Второе дыхание", "Second Wind"), ("Живучесть", "Tenacity"), ("Кровь предков", "Ancestral Blood"), ("Неугасимый", "Unquenched"), ("Крепкий хребет", "Iron Spine"), ("Жар жизни", "Vital Heat"), ("Упорство", "Perseverance"), ("Горячая кровь", "Hot Blood"), ("Выносливость", "Endurance"), ("Сердце льва", "Lionheart"), ("Живой щит", "Living Shield")]),
    "armour": dict(ru="Броня", en="Armour", stats=["ADD_ARMOUR", "INCREASED_ARMOUR", "INCREASED_STUN_THRESHOLD", "ADD_PHYSICAL_DAMAGE_REDUCTION"],
        mastery=[[("INCREASED_ARMOUR", [30]), ("ADD_ARMOUR", [60])], [("ADD_PHYSICAL_DAMAGE_REDUCTION", [4]), ("INCREASED_STUN_THRESHOLD", [20])], [("AVOID_STUN", [15]), ("ADD_ARMOUR", [40])]],
        names=[("Каменная кожа", "Stone Skin"), ("Несдвигаемый", "Unmovable"), ("Железная стена", "Iron Wall"), ("Панцирь", "Carapace"), ("Кованая плоть", "Forged Flesh"), ("Бастион", "Bastion"), ("Тяжёлая рука", "Heavy Hand"), ("Твердыня", "Stronghold"), ("Гранит", "Granite"), ("Латник", "Man-at-Arms"), ("Скала", "The Rock"), ("Щитоносец", "Shieldbearer")]),
    "evasion": dict(ru="Уклонение", en="Evasion", stats=["ADD_EVASION_RATING", "INCREASED_EVASION_RATING", "INCREASED_MOVEMENT_SPEED", "INCREASED_ARMOUR_AND_EVASION"],
        mastery=[[("INCREASED_EVASION_RATING", [30]), ("ADD_EVASION_RATING", [60])], [("INCREASED_MOVEMENT_SPEED", [6]), ("AVOID_POISON", [20])], [("INCREASED_ATTACK_SPEED", [6]), ("ADD_EVASION_RATING", [40])]],
        names=[("Ветер в спину", "Tailwind"), ("Охотничий шаг", "Hunter's Stride"), ("Тень листвы", "Leaf Shadow"), ("Лёгкая броня", "Light Harness"), ("Быстрые ноги", "Quick Feet"), ("Неуловимый", "Elusive"), ("Прыжок рыси", "Lynx Leap"), ("Шёпот ветра", "Wind Whisper"), ("Танец клинков", "Blade Dance"), ("Тихий шаг", "Silent Step"), ("Туман", "Mist"), ("Змеиный изгиб", "Serpent Coil"), ("Акробат", "Acrobat")]),
    "shield": dict(ru="Энергощит", en="Energy Shield", stats=["ADD_ENERGY_SHIELD", "INCREASED_ENERGY_SHIELD", "INCREASED_ENERGY_REGENERATION", "INCREASED_EVASION_AND_ENERGY_SHIELD"],
        mastery=[[("INCREASED_ENERGY_SHIELD", [25]), ("ADD_ENERGY_SHIELD", [30])], [("INCREASED_ENERGY_REGENERATION", [30]), ("ADD_ENERGY_SHIELD", [20])], [("ADD_CHAOS_RESISTANCE", [15]), ("INCREASED_ENERGY_SHIELD", [12])]],
        names=[("Сосуд", "Vessel"), ("Кокон", "Cocoon"), ("Полный сосуд", "Brimming Vessel"), ("Мерцающий покров", "Shimmering Veil"), ("Эфирный щит", "Aether Ward"), ("Звёздная оболочка", "Starshell"), ("Ясный разум", "Clear Mind"), ("Отражение", "Reflection"), ("Кристалл воли", "Crystal Will"), ("Лунный свет", "Moonlight"), ("Эгида разума", "Mind Aegis"), ("Тихая гладь", "Still Waters")]),
    "attack": dict(ru="Атака", en="Attack", stats=["INCREASED_PHYSICAL_DAMAGE", "INCREASED_ATTACK_SPEED", "CHANCE_TO_BLEED", "ADD_BLOCK_CHANCE"],
        mastery=[[("INCREASED_ATTACK_SPEED", [8]), ("INCREASED_PHYSICAL_DAMAGE", [12])], [("CHANCE_TO_BLEED", [10]), ("INCREASED_BLEED_DAMAGE", [25])], [("ADD_LIFE_ON_HIT", [3]), ("INCREASED_ATTACK_SPEED", [5])]],
        names=[("Клинок и темп", "Blade and Tempo"), ("Сокрушение", "Crushing Blow"), ("Кровавый долг", "Blood Debt"), ("Стена щитов", "Shield Wall"), ("Боевая выучка", "Drill"), ("Частый бой", "Rapid Strikes"), ("Раскол", "Cleave"), ("Разрыв плоти", "Flesh Ripper"), ("Жажда битвы", "Battle Lust"), ("Мельница", "Whirlwind"), ("Натиск", "Onslaught"), ("Бойня", "Carnage")]),
    "crit": dict(ru="Критический удар", en="Critical Strike", stats=["INCREASED_CRITICAL_STRIKE_CHANCE", "ADD_CRITICAL_STRIKE_MULTIPLIER", "INCREASED_ATTACK_SPEED", "ADD_DEXTERITY"],
        mastery=[[("INCREASED_CRITICAL_STRIKE_CHANCE", [35]), ("ADD_CRITICAL_STRIKE_MULTIPLIER", [8])], [("ADD_CRITICAL_STRIKE_MULTIPLIER", [22]), ("INCREASED_ATTACK_SPEED", [3])], [("INCREASED_CRITICAL_STRIKE_CHANCE", [20]), ("ADD_LIFE_ON_KILL", [10])]],
        names=[("Слабое место", "Weak Spot"), ("Удар в спину", "Backstab"), ("Холодный расчёт", "Cold Calculation"), ("Точный глаз", "True Eye"), ("Смертельный изгиб", "Lethal Arc"), ("Роковой миг", "Fatal Moment"), ("Острый ум", "Keen Mind"), ("Безжалостный", "Merciless"), ("Охотничий инстинкт", "Hunter's Instinct"), ("Игла", "Needle"), ("Добивание", "Coup de Grace"), ("Верный глаз", "Steady Eye")]),
    "elemental": dict(ru="Стихии", en="Elements", stats=["INCREASED_ELEMENTAL_DAMAGE", "INCREASED_FIRE_DAMAGE", "INCREASED_COLD_DAMAGE", "INCREASED_LIGHTNING_DAMAGE", "CHANCE_TO_IGNITE", "CHANCE_TO_FREEZE", "CHANCE_TO_SHOCK"],
        mastery=[[("INCREASED_ELEMENTAL_DAMAGE", [20, 20, 20]), ("ADD_ALL_ELEMENTAL_RESISTANCES", [6])], [("CHANCE_TO_IGNITE", [10]), ("INCREASED_BURNING_DAMAGE", [25])], [("CHANCE_TO_FREEZE", [8]), ("CHANCE_TO_SHOCK", [8])]],
        names=[("Кара", "Retribution"), ("Злое слово", "Hex Word"), ("Пламенное сердце", "Burning Heart"), ("Ледяной укус", "Frostbite"), ("Искра гнева", "Spark of Wrath"), ("Грозовой фронт", "Stormfront"), ("Три стихии", "Trinity"), ("Горнило", "Crucible"), ("Буран", "Blizzard"), ("Раскат", "Thunderclap"), ("Пепел", "Ashes"), ("Призма", "Prism")]),
    "chaos": dict(ru="Хаос", en="Chaos", stats=["ADD_CHAOS_DAMAGE", "INCREASED_CHAOS_DAMAGE", "CHANCE_TO_POISON", "INCREASED_POISON_DAMAGE", "ADD_CHAOS_RESISTANCE"],
        mastery=[[("INCREASED_CHAOS_DAMAGE", [25]), ("ADD_CHAOS_RESISTANCE", [12])], [("CHANCE_TO_POISON", [12]), ("INCREASED_POISON_DAMAGE", [25])], [("AVOID_POISON", [30]), ("ADD_CHAOS_RESISTANCE", [15])]],
        names=[("Гниение", "Decay"), ("Привычка к яду", "Venom Tolerance"), ("Ядовитый шёпот", "Toxic Whisper"), ("Тёмная кровь", "Dark Blood"), ("Скверна", "Blight"), ("Пустота", "Void Touch"), ("Чёрный лотос", "Black Lotus"), ("Жало", "Sting"), ("Мор", "Pestilence"), ("Трясина", "Mire"), ("Разложение", "Rot"), ("Змеиный яд", "Serpent Venom")]),
    "resist": dict(ru="Сопротивления", en="Resistances", stats=["ADD_ALL_ELEMENTAL_RESISTANCES", "ADD_FIRE_RESISTANCE", "ADD_COLD_RESISTANCE", "ADD_LIGHTNING_RESISTANCE", "ADD_CHAOS_RESISTANCE"],
        mastery=[[("ADD_ALL_ELEMENTAL_RESISTANCES", [12])], [("ADD_ALL_ELEMENTAL_RESISTANCES", [5]), ("ADD_CHAOS_RESISTANCE", [10])], [("AVOID_STUN", [10]), ("ADD_ALL_ELEMENTAL_RESISTANCES", [8])]],
        names=[("Освящение", "Consecration"), ("Негорючий", "Fireproof"), ("Закалка", "Tempered"), ("Громоотвод", "Lightning Rod"), ("Благословение", "Blessing"), ("Святая земля", "Hallowed Ground"), ("Стойкость духа", "Spirit Ward"), ("Оберег", "Talisman"), ("Литания", "Litany"), ("Святой огонь", "Holy Fire"), ("Покров веры", "Mantle of Faith"), ("Нерушимый обет", "Unbroken Vow")]),
    "wealth": dict(ru="Богатство", en="Wealth", stats=["INCREASED_ITEM_RARITY", "INCREASED_ITEM_QUANTITY", "INCREASED_SELL_VALUE", "INCREASED_LIGHT_RADIUS", "INCREASED_CHEST_QUANTITY", "INCREASED_EXPERIENCE_GAIN"],
        mastery=[[("INCREASED_ITEM_RARITY", [15]), ("INCREASED_SELL_VALUE", [15])], [("INCREASED_ITEM_QUANTITY", [8]), ("INCREASED_EXPERIENCE_GAIN", [3])], [("INCREASED_CHEST_QUANTITY", [20]), ("INCREASED_LIGHT_RADIUS", [15])]],
        names=[("Жадность", "Greed"), ("Чутьё кладоискателя", "Treasure Sense"), ("Счастливая монета", "Lucky Coin"), ("Факел в ночи", "Torch in the Night")]),
    "craft": dict(ru="Ремесло", en="Craft", stats=["WORK_SPEED", "WORK_YIELD", "WORK_LUCK", "WORK_EXPERIENCE", "WORK_FIND"],
        mastery=[[("WORK_SPEED", [15]), ("WORK_EXPERIENCE", [10])], [("WORK_YIELD", [15]), ("WORK_LUCK", [8])], [("WORK_FIND", [20]), ("WORK_EXPERIENCE", [20])]],
        names=[("Мастер на все руки", "Jack of All Trades"), ("Подмастерье", "Apprentice"), ("Верная рука", "Steady Hand"), ("Хозяйство", "Homestead")]),
}

CLASSES = [
    # code, короткий префикс кодов, стартовый код, основной атрибут, темы кластеров, keystone
    ("STR", "MAR", "STR_START", "ADD_STRENGTH", ["life", "armour", "attack", "resist"]),
    ("STR_DEX", "DUE", "STR_DEX_START", "ADD_STRENGTH_AND_DEXTERITY", ["attack", "life", "evasion", "crit"]),
    ("DEX", "RAN", "DEX_START", "ADD_DEXTERITY", ["evasion", "attack", "crit", "elemental"]),
    ("DEX_INT", "SHA", "DEX_INT_START", "ADD_DEXTERITY_AND_INTELLIGENCE", ["crit", "chaos", "shield", "evasion"]),
    ("INT", "WIT", "INT_START", "ADD_INTELLIGENCE", ["shield", "elemental", "chaos", "resist"]),
    ("STR_INT", "TEM", "STR_INT_START", "ADD_STRENGTH_AND_INTELLIGENCE", ["elemental", "armour", "resist", "shield"]),
]
ATTR_CODES = {"ADD_STRENGTH_AND_DEXTERITY": ["ADD_STRENGTH", "ADD_DEXTERITY"], "ADD_DEXTERITY_AND_INTELLIGENCE": ["ADD_DEXTERITY", "ADD_INTELLIGENCE"],
              "ADD_STRENGTH_AND_INTELLIGENCE": ["ADD_STRENGTH", "ADD_INTELLIGENCE"]}

KEYSTONES = {
    "MAR": [("Несокрушимый", "Unbreakable", [("PASSIVE_MORE_MAXIMUM_LIFE", [25.0]), ("ADD_LIFE_REGENERATION", [5])],
             "Запас жизни больше на четверть сверх всего прочего.", "A quarter more life on top of everything else."),
            ("Кровь титана", "Titan's Blood", [("PASSIVE_MORE_STUN_THRESHOLD", [100.0]), ("PASSIVE_MORE_ARMOUR", [50.0]), ("PASSIVE_SET_EVASION_RATING", [0.0])],
             "Вдвое крепче против оглушения и на половину больше брони, но уклонения нет вовсе.", "Twice the stun threshold and half again the armour, but no evasion at all.")],
    "DUE": [("Ненасытный", "Insatiable", [("PASSIVE_MORE_LIFE_LEECH", [60.0]), ("ADD_PHYSICAL_LIFE_LEECH", [1])],
             "Вампиризм сильнее на шестьдесят процентов.", "Leech is sixty percent stronger."),
            ("Мясник", "Butcher", [("PASSIVE_MORE_PHYSICAL_DAMAGE", [20.0]), ("CHANCE_TO_BLEED", [20]), ("PASSIVE_SET_LIFE_REGENERATION", [0.0])],
             "Физический урон больше на пятую часть, удары режут до крови, но жизнь не восстанавливается сама.", "A fifth more physical damage and bleeding blows, but life never regenerates.")],
    "RAN": [("Ускользающий", "Evasive", [("PASSIVE_SET_EVASION_RATING", [2000.0]), ("INCREASED_MOVEMENT_SPEED", [5])],
             "Уклонение всегда равно двум тысячам.", "Evasion is always two thousand."),
            ("Лёгкая поступь", "Light Step", [("PASSIVE_MORE_EVASION_RATING", [30.0]), ("INCREASED_MOVEMENT_SPEED", [10]), ("PASSIVE_SET_LIFE_REGENERATION", [0.0])],
             "Уклонение больше на треть и шаг быстрее, но жизнь не восстанавливается сама.", "A third more evasion and a quicker stride, but life never regenerates.")],
    "SHA": [("Совершенный удар", "Perfect Strike", [("PASSIVE_SET_CRITICAL_STRIKE_CHANCE", [40.0]), ("ADD_CRITICAL_STRIKE_MULTIPLIER", [10])],
             "Шанс критического удара всегда сорок процентов.", "Critical strike chance is always forty percent."),
            ("Тысяча порезов", "Thousand Cuts", [("INCREASED_ATTACK_SPEED", [15]), ("CHANCE_TO_POISON", [25]), ("PASSIVE_SET_CRITICAL_DAMAGE", [0.0])],
             "Атаки быстрее и отравляют, но критический удар не усиливается сверх базы.", "Faster, poisoning attacks, but critical strikes deal no extra damage beyond the base.")],
    "WIT": [("Хрупкий разум", "Chaos Inoculation", [("PASSIVE_SET_MAXIMUM_LIFE", [1.0]), ("PASSIVE_SET_CHAOS_RESISTANCE", [100.0]), ("INCREASED_ENERGY_SHIELD", [20])],
             "Жизнь всегда одна единица, но хаос не ранит, а щит крепче.", "Life is always one, but chaos cannot harm you and the shield is stronger."),
            ("Стихийная буря", "Elemental Storm", [("PASSIVE_MORE_ELEMENTAL_DAMAGE", [20.0, 20.0, 20.0]), ("CHANCE_TO_SHOCK", [10]), ("PASSIVE_SET_CRITICAL_STRIKE_CHANCE", [0.0])],
             "Стихийный урон больше на пятую часть, но критических ударов не бывает.", "A fifth more elemental damage, but no critical strikes.")],
    "TEM": [("Пламенный суд", "Fiery Judgement", [("PASSIVE_MORE_ELEMENTAL_DAMAGE", [30.0, 30.0, 30.0]), ("ADD_FIRE_RESISTANCE", [10])],
             "Стихийный урон больше на тридцать процентов.", "Thirty percent more elemental damage."),
            ("Небесный щит", "Heavenly Aegis", [("ADD_MAXIMUM_ELEMENTAL_RESISTANCES", [3]), ("PASSIVE_MORE_ARMOUR", [30.0]), ("PASSIVE_SET_CRITICAL_DAMAGE", [0.0])],
             "Предел стихийных сопротивлений выше и брони больше, но критический удар не усиливается сверх базы.", "Higher elemental resistance caps and more armour, but critical strikes deal no extra damage beyond the base.")],
}

SMALL_NAMES = {
    "ADD_MAXIMUM_LIFE": ("Здоровье", "Life"), "INCREASED_MAXIMUM_LIFE": ("Живучесть", "Vitality"), "ADD_LIFE_REGENERATION": ("Восстановление", "Recovery"),
    "ADD_LIFE_ON_KILL": ("Добыча жизни", "Life Harvest"), "ADD_ARMOUR": ("Броня", "Armour"), "INCREASED_ARMOUR": ("Закалённая броня", "Hardened Armour"),
    "INCREASED_STUN_THRESHOLD": ("Стойкость", "Steadfast"), "ADD_PHYSICAL_DAMAGE_REDUCTION": ("Толстая кожа", "Thick Skin"),
    "ADD_EVASION_RATING": ("Уклонение", "Evasion"), "INCREASED_EVASION_RATING": ("Ловкий уход", "Deft Dodge"), "INCREASED_MOVEMENT_SPEED": ("Быстрый шаг", "Swift Step"),
    "INCREASED_ARMOUR_AND_EVASION": ("Выучка", "Drill"), "ADD_ENERGY_SHIELD": ("Энергощит", "Energy Shield"), "INCREASED_ENERGY_SHIELD": ("Сияние", "Radiance"),
    "INCREASED_ENERGY_REGENERATION": ("Перезарядка", "Recharge"), "INCREASED_EVASION_AND_ENERGY_SHIELD": ("Покров", "Veil"),
    "INCREASED_ARMOUR_AND_ENERGY_SHIELD": ("Обет", "Vow"), "INCREASED_PHYSICAL_DAMAGE": ("Сила удара", "Force"), "INCREASED_ATTACK_SPEED": ("Темп", "Tempo"),
    "CHANCE_TO_BLEED": ("Кровопускание", "Bloodletting"), "ADD_BLOCK_CHANCE": ("Щит", "Shield"), "INCREASED_CRITICAL_STRIKE_CHANCE": ("Точность", "Precision"),
    "ADD_CRITICAL_STRIKE_MULTIPLIER": ("Жестокость", "Brutality"), "ADD_DEXTERITY": ("Ловкость", "Dexterity"), "ADD_STRENGTH": ("Сила", "Strength"),
    "ADD_INTELLIGENCE": ("Интеллект", "Intelligence"), "INCREASED_ELEMENTAL_DAMAGE": ("Стихии", "Elements"), "INCREASED_FIRE_DAMAGE": ("Огонь", "Fire"),
    "INCREASED_COLD_DAMAGE": ("Холод", "Cold"), "INCREASED_LIGHTNING_DAMAGE": ("Молния", "Lightning"), "CHANCE_TO_IGNITE": ("Поджог", "Ignite"),
    "CHANCE_TO_FREEZE": ("Заморозка", "Freeze"), "CHANCE_TO_SHOCK": ("Шок", "Shock"), "ADD_CHAOS_DAMAGE": ("Хаос", "Chaos"),
    "INCREASED_CHAOS_DAMAGE": ("Тьма", "Darkness"), "CHANCE_TO_POISON": ("Яд", "Poison"), "INCREASED_POISON_DAMAGE": ("Отрава", "Venom"),
    "ADD_CHAOS_RESISTANCE": ("Иммунитет", "Immunity"), "ADD_ALL_ELEMENTAL_RESISTANCES": ("Сопротивление", "Resistance"),
    "ADD_FIRE_RESISTANCE": ("Огнеупорность", "Fire Ward"), "ADD_COLD_RESISTANCE": ("Морозостойкость", "Cold Ward"), "ADD_LIGHTNING_RESISTANCE": ("Заземление", "Grounding"),
    "INCREASED_ITEM_RARITY": ("Удача", "Fortune"), "INCREASED_ITEM_QUANTITY": ("Добыча", "Plunder"), "INCREASED_SELL_VALUE": ("Торговля", "Trade"),
    "INCREASED_LIGHT_RADIUS": ("Свет", "Light"), "INCREASED_CHEST_QUANTITY": ("Сундуки", "Chests"), "INCREASED_EXPERIENCE_GAIN": ("Опыт", "Experience"),
    "WORK_SPEED": ("Сноровка", "Knack"), "WORK_YIELD": ("Урожай", "Yield"), "WORK_LUCK": ("Удачный день", "Lucky Day"),
    "WORK_EXPERIENCE": ("Опыт ремесла", "Craft Lore"), "WORK_FIND": ("Находки", "Finds"), "ADD_ALL_ATTRIBUTES": ("Равновесие", "Balance"),
    "ADD_STRENGTH_AND_DEXTERITY": ("Сила и ловкость", "Strength and Dexterity"), "ADD_DEXTERITY_AND_INTELLIGENCE": ("Ловкость и интеллект", "Dexterity and Intelligence"),
    "ADD_STRENGTH_AND_INTELLIGENCE": ("Сила и интеллект", "Strength and Intelligence"),
}

nodes = {}
edges = set()
names = {"ru": {}, "en": {}}


def bonus(code, notable=False):
    return {"code": code, "values": V[code][1 if notable else 0]}


def add(code, kind, x, y, params=(), options=None, cost=1):
    assert code not in nodes, code
    nodes[code] = {"code": code, "type": kind, "cost": 0 if kind == "START" else cost,
                   "positionX": int(round(x)), "positionY": int(round(y)), "connections": [], "params": list(params)}
    if options is not None:
        nodes[code]["options"] = options
    return code


def link(a, b):
    if a != b and (b, a) not in edges:
        edges.add((a, b))


def name(code, ru, en, dru=None, den=None):
    names["ru"][f"skilltree.{code}.name"] = ru
    names["en"][f"skilltree.{code}.name"] = en
    if dru is not None:
        names["ru"][f"skilltree.{code}.description"] = dru
        names["en"][f"skilltree.{code}.description"] = den


def polar(r, deg):
    a = math.radians(deg)
    return r * math.cos(a), r * math.sin(a)


def jitter(v, spread):
    return v + rng.uniform(-spread, spread)


def small(code, x, y, stats):
    """Малый узел: два бонуса из темы, названный по первому."""
    picked = rng.sample(stats, 2) if len(stats) > 1 else stats * 2
    add(code, "SMALL", x, y, [bonus(c) for c in picked])
    ru, en = SMALL_NAMES[picked[0]]
    name(code, ru, en)
    return code


def attribute(code, x, y):
    options = [[bonus("ADD_STRENGTH")], [bonus("ADD_DEXTERITY")], [bonus("ADD_INTELLIGENCE")]]
    add(code, "ATTRIBUTE", x, y, [], options)
    name(code, "Атрибут", "Attribute", "Выберите +8 к силе, ловкости или интеллекту.", "Choose +8 to Strength, Dexterity or Intelligence.")
    return code


used_names = set()


def notable_name(theme):
    for ru, en in THEMES[theme]["names"]:
        if ru not in used_names:
            used_names.add(ru)
            return ru, en
    raise SystemExit(f"out of notable names for {theme}")


def cluster(prefix, theme, cx, cy, entry, facing):
    """Кластер: неровное кольцо малых узлов с двумя-тремя notable и мастерством в центре."""
    t = THEMES[theme]
    count = rng.choice([5, 6, 6, 7])
    radius = rng.uniform(70, 105)
    turn = facing + 180 + rng.uniform(-20, 20)
    notable_at = sorted(rng.sample(range(1, count), 3 if count == 7 else 2))
    ring = []
    for i in range(count):
        x, y = polar(radius * rng.uniform(.85, 1.15), turn + i * 360 / count + rng.uniform(-10, 10))
        code = f"{prefix}_{i}"
        if i in notable_at:
            picked = rng.sample(t["stats"], 3)
            add(code, "NOTABLE", cx + x, cy + y, [bonus(c, True) for c in picked])
            ru, en = notable_name(theme)
            name(code, ru, en, f"Крупный узел темы «{t['ru']}».", f"A notable of the {t['en']} theme.")
        else:
            small(code, cx + x, cy + y, t["stats"])
        ring.append(code)
    for i in range(count):
        link(ring[i], ring[(i + 1) % count])
    mastery = add(f"{prefix}_M", "MASTERY", cx, cy, [],
                  [[{"code": c, "values": v} for c, v in option] for option in t["mastery"]])
    name(mastery, f"Мастерство: {t['ru'].lower()}", f"{t['en']} Mastery",
         "Выберите один из трёх эффектов. Берётся после любого соседнего крупного узла.",
         "Choose one of three effects. Taken after any adjacent notable.")
    for i in notable_at:
        link(mastery, ring[i])
    link(entry, ring[0])
    return ring[count // 2]


def path(prefix, start, r0, r1, deg0, deg1, steps, stats, attr_at=None):
    """Тропа малых узлов с неровным шагом; возвращает последний узел."""
    prev = start
    for i in range(1, steps + 1):
        t = i / (steps + 1)
        r = r0 + (r1 - r0) * t + rng.uniform(-18, 18)
        d = deg0 + (deg1 - deg0) * t + rng.uniform(-2.5, 2.5)
        x, y = polar(r, d)
        code = f"{prefix}_{i}"
        attribute(code, x, y) if i == attr_at else small(code, x, y, stats)
        link(prev, code)
        prev = code
    return prev


# ==================== Scion ====================
add("SCION_START", "START", 0, 0)
ring_points = []
for k, (cls, pre, start, attr, themes) in enumerate(CLASSES):
    deg = -90 + 60 * k + rng.uniform(-4, 4)
    spoke = small(f"SCN_S{k}", *polar(jitter(170, 15), deg), ["ADD_ALL_ATTRIBUTES", "INCREASED_MAXIMUM_LIFE", "INCREASED_EXPERIENCE_GAIN"])
    link("SCION_START", spoke)
    point = small(f"SCN_R{k}", *polar(jitter(330, 15), deg), ["ADD_ALL_ATTRIBUTES", "ADD_ALL_ELEMENTAL_RESISTANCES", "ADD_MAXIMUM_LIFE"])
    link(spoke, point)
    ring_points.append((point, deg))
for k in range(6):
    a, da = ring_points[k]
    b, db = ring_points[(k + 1) % 6]
    if db < da:
        db += 360
    mid = [small(f"SCN_R{k}_{j}", *polar(jitter(335, 20), da + (db - da) * j / 3), ["ADD_ALL_ATTRIBUTES", "ADD_MAXIMUM_LIFE", "ADD_ALL_ELEMENTAL_RESISTANCES"]) for j in (1, 2)]
    link(a, mid[0]); link(mid[0], mid[1]); link(mid[1], b)
# Две ветви Scion внутрь колец: богатство и ремесло
for code, theme, deg in (("SCN_W", "wealth", 0), ("SCN_C", "craft", 180)):
    end = path(code + "P", "SCN_R0_1" if deg == 0 else "SCN_R3_1", 300, 180, deg - 60, deg - 30, 2, THEMES[theme]["stats"])
    cluster(code, theme, *polar(130, deg - 20), end, deg - 20)
socket = add("SCN_J", "JEWEL_SOCKET", *polar(250, 150))
name(socket, "Гнездо потомка", "Scion's Socket", "Гнездо для самоцвета.", "A socket for a jewel.")
link(socket, "SCN_R3_2")

# ==================== Classes ====================
outer = []
for k, (cls, pre, start, attr, themes) in enumerate(CLASSES):
    base = -90 + 60 * k + 30 + rng.uniform(-7, 7)
    r_start = jitter(560, 40)
    add(start, "START", *polar(r_start, base))
    link(start, path(f"{pre}_IN", ring_points[k][0], 330, r_start, ring_points[k][1], base, 2, [attr] + THEMES[themes[0]]["stats"][:2]))
    stats_base = ATTR_CODES.get(attr, [attr])
    spreads = sorted(rng.sample([-25, -16, -7, 3, 12, 22], 4))
    tails = []
    keys = KEYSTONES[pre]
    for b, spread in enumerate(spreads):
        theme = themes[b]
        stats = THEMES[theme]["stats"] + stats_base
        deg = base + spread + rng.uniform(-3, 3)
        length = rng.randint(4, 7)
        r_cluster = r_start + 90 + length * 58 + rng.uniform(-30, 30)
        end = path(f"{pre}_B{b}", start, r_start, r_cluster - 110, base + spread * .35, deg, length, stats, attr_at=rng.choice([2, 3]))
        exit_ = cluster(f"{pre}_C{b}", theme, *polar(r_cluster, deg), end, deg)
        tails.append((end, deg, r_cluster))
        # Хвост после кластера: keystone, гнездо или ещё один notable
        tail = path(f"{pre}_T{b}", exit_, r_cluster + 90, r_cluster + 260, deg, deg + rng.uniform(-6, 6), rng.randint(1, 3), stats)
        tr = r_cluster + 330 + rng.uniform(-30, 40)
        tdeg = deg + rng.uniform(-5, 5)
        if b in (0, 3):
            kru, ken, params, dru, den = keys[0 if b == 0 else 1]
            code = add(f"{pre}_K{b}", "KEYSTONE", *polar(tr, tdeg), [{"code": c, "values": v} for c, v in params])
            name(code, kru, ken, dru, den)
        elif b == 1:
            code = add(f"{pre}_J{b}", "JEWEL_SOCKET", *polar(tr, tdeg))
            name(code, f"Гнездо: {THEMES[theme]['ru'].lower()}", f"{THEMES[theme]['en']} Socket", "Гнездо для самоцвета.", "A socket for a jewel.")
        else:
            picked = rng.sample(stats, 4)
            code = add(f"{pre}_N{b}", "NOTABLE", *polar(tr, tdeg), [bonus(c, True) for c in picked])
            ru, en = notable_name(theme)
            name(code, ru, en, f"Крупный узел темы «{THEMES[theme]['ru']}».", f"A notable of the {THEMES[theme]['en']} theme.")
        link(tail, code)
    # Поперечные тропы между соседними ветвями - петли, как в POE
    for b in range(3):
        if rng.random() < .6:
            (ea, da, ra), (eb, db, rb) = tails[b], tails[b + 1]
            mid = small(f"{pre}_X{b}", *polar((ra + rb) / 2 - 130, (da + db) / 2), stats_base + ["ADD_MAXIMUM_LIFE"])
            link(ea, mid); link(mid, eb)
    outer.append((tails[0], tails[-1]))

# Мосты между соседними классами через атрибутный узел
for k in range(6):
    (a_end, a_deg, a_r) = outer[k][1]
    (b_end, b_deg, b_r) = outer[(k + 1) % 6][0]
    if b_deg < a_deg:
        b_deg += 360
    x1, y1 = polar((a_r + b_r) / 2 - 60, a_deg + (b_deg - a_deg) * .33)
    x2, y2 = polar((a_r + b_r) / 2 - 40, a_deg + (b_deg - a_deg) * .66)
    p1 = small(f"BR{k}_1", x1, y1, ["ADD_MAXIMUM_LIFE", "ADD_ALL_ELEMENTAL_RESISTANCES", "INCREASED_ATTACK_SPEED"])
    p2 = attribute(f"BR{k}_2", x2, y2)
    link(a_end, p1); link(p1, p2); link(p2, b_end)

# ==================== Relaxation ====================
# Ветви одного класса выходят из одной точки и местами ложатся друг на друга: узлы, что ближе
# MIN_GAP, расталкиваются, пока не разойдутся. Связи от этого не меняются, только картинка.
MIN_GAP = 46
pts = {c: [float(n["positionX"]), float(n["positionY"])] for c, n in nodes.items()}
fixed = {"SCION_START"}
codes = list(pts)
for _ in range(80):
    moved = False
    for i, a in enumerate(codes):
        for b in codes[i + 1:]:
            dx, dy = pts[b][0] - pts[a][0], pts[b][1] - pts[a][1]
            d = math.hypot(dx, dy)
            if d >= MIN_GAP:
                continue
            if d < 1e-6:
                dx, dy, d = rng.uniform(-1, 1), rng.uniform(-1, 1), 1.0
            push = (MIN_GAP - d) / 2 / d
            for c, sign in ((a, -1), (b, 1)):
                if c not in fixed:
                    pts[c][0] += sign * dx * push
                    pts[c][1] += sign * dy * push
            moved = True
    if not moved:
        break
for c, (x, y) in pts.items():
    nodes[c]["positionX"], nodes[c]["positionY"] = int(round(x)), int(round(y))

# ==================== Output ====================
for a, b in edges:
    nodes[a]["connections"].append(b)
# Мастерство - лист: соседи ведут к нему, не от него
for n in nodes.values():
    n["connections"].sort()
TREE.write_text(json.dumps({"nodes": list(nodes.values())}, ensure_ascii=False, indent=2) + "\n")

for lang, file in LOCALES.items():
    data = json.loads(file.read_text())
    keys = list(data)
    at = next(i for i, k in enumerate(keys) if k.startswith("skilltree."))
    keep = {k: v for k, v in data.items() if k.startswith("skilltree.") and k.split(".")[1].endswith("START")}
    tree = dict(sorted({**{k: v for k, v in names[lang].items() if k not in keep}, **keep}.items()))
    rest = [(k, v) for k, v in data.items() if not k.startswith("skilltree.")]
    # Ключи дерева встают туда, где стояли прежние: остальной файл не двигается
    before = [(k, v) for k, v in rest if keys.index(k) < at]
    after = [(k, v) for k, v in rest if keys.index(k) > at]
    file.write_text(json.dumps(dict(before + list(tree.items()) + after), ensure_ascii=False, indent=2) + "\n")

types = {}
for n in nodes.values():
    types[n["type"]] = types.get(n["type"], 0) + 1
print(len(nodes), "nodes", types, len(edges), "edges")
