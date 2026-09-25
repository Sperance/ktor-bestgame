# -*- coding: utf-8 -*-
"""
Генератор словарных строк модификаторов (0.66.0).

Убирает из `locale/en.json` и `locale/ru.json` строки на каждое описание (`modifier.*`)
и кладёт вместо них шаблоны на пару «стат - операция» (`stat.template.*`), из которых сервер на старте
собирает `modifier.<код>.name`, см. features.logic.locale.ModifierText. Заодно - подписи новых
характеристик, сфер, профессии зачарователя, инструментов и прочие ключи 0.66.0.

Запуск из корня сервера: python3 scripts/content/gen_locale.py
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
RES = os.path.join(ROOT, "src", "main", "resources")
CONTENT = os.path.join(RES, "content")
LOCALE = os.path.join(RES, "locale")

# ---------------------------------------------------------------------------------------------
# Шаблоны: (стат, операция, вид) -> (en, ru). Вид: "" обычный, "negative" (значение печатается по
# модулю, знак в тексте), "per" (конверсия: {n} шаг, {s} подпись источника).
# ---------------------------------------------------------------------------------------------
T = {}


def t(stat, op, en, ru, kind=""):
    T[(stat, op, kind)] = (en, ru)


def flat(stat, en, ru_dat, ru_gen=None, neg=True):
    """Плоская прибавка: +{v} to X / +{v} к X; минус - тем же словом."""
    t(stat, "ADD", f"+{{v}} to {en}", f"+{{v}} к {ru_dat}")
    if neg:
        t(stat, "ADD", f"-{{v}} to {en}", f"-{{v}} к {ru_dat}", "negative")


def inc(stat, en, ru_gen, neg=True):
    t(stat, "INCREASED", f"{{v}}% increased {en}", f"{{v}}% увеличение {ru_gen}")
    if neg:
        t(stat, "INCREASED", f"{{v}}% reduced {en}", f"{{v}}% уменьшение {ru_gen}", "negative")


def more(stat, en, ru_gen):
    t(stat, "MORE", f"{{v}}% more {en}", f"{{v}}% больше {ru_gen}")
    t(stat, "MORE", f"{{v}}% less {en}", f"{{v}}% меньше {ru_gen}", "negative")


def setv(stat, en, ru_nom):
    t(stat, "SET", f"{en} is {{v}}", f"{ru_nom}: {{v}}")


def pct(stat, en, ru_dat, neg=True):
    """Процентные пункты: +{v}% to X / +{v}% к X."""
    t(stat, "ADD", f"+{{v}}% to {en}", f"+{{v}}% к {ru_dat}")
    if neg:
        t(stat, "ADD", f"-{{v}}% to {en}", f"-{{v}}% к {ru_dat}", "negative")


# --- запас, защита
flat("STOCK_HEALTH", "maximum Life", "максимуму здоровья")
inc("STOCK_HEALTH", "maximum Life", "максимума здоровья")
more("STOCK_HEALTH", "maximum Life", "максимума здоровья")
setv("STOCK_HEALTH", "Maximum Life", "Максимум здоровья")
t("STOCK_HEALTH", "ADD", "+{v} to maximum Life per {n} {s}", "+{v} к максимуму здоровья за каждые {n} {s}", "per")
flat("STOCK_ENERGY_SHIELD", "maximum Energy Shield", "максимуму энергетического щита")
inc("STOCK_ENERGY_SHIELD", "Energy Shield", "энергетического щита")
t("STOCK_ENERGY_SHIELD", "INCREASED", "{v}% increased Energy Shield per {n} {s}", "{v}% увеличение энергетического щита за каждые {n} {s}", "per")
flat("STOCK_MANA", "maximum Mana", "максимуму маны")
flat("STOCK_ARMOR", "Armour", "броне")
inc("STOCK_ARMOR", "Armour", "брони")
more("STOCK_ARMOR", "Armour", "брони")
flat("STOCK_EVASION", "Evasion Rating", "уклонению")
inc("STOCK_EVASION", "Evasion Rating", "уклонения")
more("STOCK_EVASION", "Evasion Rating", "уклонения")
setv("STOCK_EVASION", "Evasion Rating", "Уклонение")
t("STOCK_EVASION", "ADD", "+{v} to Evasion Rating per {n} {s}", "+{v} к уклонению за каждые {n} {s}", "per")
t("STOCK_BLOCK_CHANCE", "ADD", "+{v}% chance to Block", "+{v}% к шансу блока")
t("STOCK_BLOCK_CHANCE", "ADD", "-{v}% chance to Block", "-{v}% к шансу блока", "negative")
t("STOCK_SPELL_BLOCK", "ADD", "+{v}% chance to Block Spells", "+{v}% к шансу блока заклинаний")
flat("STOCK_STUN_THRESHOLD", "Stun Threshold", "порогу оглушения")
inc("STOCK_STUN_THRESHOLD", "Stun Threshold", "порога оглушения")
more("STOCK_STUN_THRESHOLD", "Stun Threshold", "порога оглушения")
t("STOCK_AVOID_STUN", "ADD", "{v}% chance to avoid being Stunned", "{v}% шанс избежать оглушения")
t("STOCK_PHYSICAL_REDUCTION", "ADD", "{v}% additional Physical Damage Reduction", "{v}% дополнительного снижения физического урона")
t("STOCK_TAUNT", "ADD", "Taunts enemies", "Провоцирует врагов")
# --- сопротивления
for el, en, ru_dat in [("FIRE", "Fire", "огню"), ("COLD", "Cold", "холоду"), ("LIGHTNING", "Lightning", "молнии"), ("CHAOS", "Chaos", "хаосу")]:
    pct(f"STOCK_RESIST_{el}", f"{en} Resistance", f"сопротивлению {ru_dat}")
    pct(f"STOCK_RESIST_MAX_{el}", f"maximum {en} Resistance", f"максимуму сопротивления {ru_dat}")
pct("STOCK_RESIST_ALL", "all Elemental Resistances", "всем сопротивлениям стихиям")
pct("STOCK_RESIST_MAX_ALL", "all maximum Resistances", "максимуму всех сопротивлений")
setv("STOCK_RESIST_CHAOS", "Chaos Resistance", "Сопротивление хаосу")
# --- атрибуты
flat("STOCK_STRENGTH", "Strength", "силе")
flat("STOCK_AGILITY", "Dexterity", "ловкости")
flat("STOCK_INTELLECT", "Intelligence", "интеллекту")
flat("STOCK_CONSTITUTION", "Constitution", "телосложению")
# --- урон
t("STOCK_ATTACK_PHYSICAL", "ADD", "+{v} Physical Damage", "+{v} к физическому урону")
inc("STOCK_ATTACK_PHYSICAL", "Physical Damage", "физического урона")
more("STOCK_ATTACK_PHYSICAL", "Physical Damage", "физического урона")
t("STOCK_ATTACK_PHYSICAL", "INCREASED", "{v}% increased Physical Damage per {n} {s}", "{v}% увеличение физического урона за каждые {n} {s}", "per")
for el, en, ru_gen, ru_dat in [("FIRE", "Fire", "урона от огня", "урону от огня"), ("COLD", "Cold", "урона от холода", "урону от холода"),
                               ("LIGHTNING", "Lightning", "урона от молнии", "урону от молнии"), ("CHAOS", "Chaos", "урона хаосом", "урону хаосом")]:
    t(f"STOCK_ATTACK_{el}", "ADD", f"+{{v}} {en} Damage", f"+{{v}} к {ru_dat}")
    inc(f"STOCK_ATTACK_{el}", f"{en} Damage", ru_gen)
    more(f"STOCK_ATTACK_{el}", f"{en} Damage", ru_gen)
t("STOCK_ATTACK_MAGICAL", "ADD", "+{v} Spell Damage", "+{v} к урону заклинаний")
inc("STOCK_ATTACK_MAGICAL", "Spell Damage", "урона заклинаний")
inc("STOCK_CAST_SPEED", "Cast Speed", "скорости сотворения")
# --- крит, скорость
pct("STOCK_CRITICAL_CHANCE", "Critical Strike Chance", "шансу критического удара")
inc("STOCK_CRITICAL_CHANCE", "Critical Strike Chance", "шанса критического удара")
setv("STOCK_CRITICAL_CHANCE", "Critical Strike Chance", "Шанс критического удара")
pct("STOCK_CRITICAL_MULTIPLIER", "Critical Strike Multiplier", "множителю критического удара")
inc("STOCK_CRITICAL_DAMAGE", "Critical Strike Damage", "урона критических ударов")
setv("STOCK_CRITICAL_DAMAGE", "Critical Strike Damage", "Урон критических ударов")
t("STOCK_CRITICAL_VAMPIRE", "ADD", "{v}% of Damage leeched as Life on Critical Strike", "{v}% урона критическим ударом крадётся здоровьем")
t("STOCK_ATTACK_SPEED", "ADD", "{v} Attacks per Second", "{v} атак в секунду")
inc("STOCK_ATTACK_SPEED", "Attack Speed", "скорости атаки")
inc("STOCK_MOVEMENT_SPEED", "Movement Speed", "скорости передвижения")
inc("STOCK_LIGHT_RADIUS", "Light Radius", "радиуса света")
# --- восстановление
t("STOCK_HEALTH_REGEN", "ADD", "Regenerate {v} Life per second", "Восстанавливает {v} здоровья в секунду")
inc("STOCK_HEALTH_REGEN", "Life Regeneration rate", "скорости восстановления здоровья")
setv("STOCK_HEALTH_REGEN", "Life Regeneration", "Восстановление здоровья")
t("STOCK_ENERGY_REGEN", "ADD", "Regenerate {v} Energy Shield per second", "Восстанавливает {v} энергетического щита в секунду")
inc("STOCK_ENERGY_REGEN", "Energy Shield Regeneration rate", "скорости восстановления энергетического щита")
t("STOCK_LEECH_PHYSICAL", "ADD", "{v}% of Physical Attack Damage leeched as Life", "{v}% физического урона атак крадётся здоровьем")
more("STOCK_LEECH_PHYSICAL", "Life leeched", "кражи здоровья")
t("STOCK_LEECH_ALL", "ADD", "{v}% of Damage leeched as Life", "{v}% урона крадётся здоровьем")
t("STOCK_HEALTH_ON_HIT", "ADD", "+{v} Life gained for each enemy hit", "+{v} здоровья за каждый удар по врагу")
t("STOCK_HEALTH_ON_KILL", "ADD", "+{v} Life gained on kill", "+{v} здоровья за убийство")
t("STOCK_RECOVERY_RATE", "ADD", "{v}% increased Life and Energy Shield Recovery rate", "{v}% увеличение скорости восстановления здоровья и щита")
t("STOCK_RECOVERY_RATE", "ADD", "{v}% reduced Life and Energy Shield Recovery rate", "{v}% уменьшение скорости восстановления здоровья и щита", "negative")
t("STOCK_SHIELD_RECHARGE", "ADD", "{v}% increased Energy Shield Recharge rate", "{v}% увеличение скорости восполнения энергетического щита")
# --- состояния
for ail, en_v, ru_v in [("IGNITE", "Ignite", "поджечь"), ("FREEZE", "Freeze", "заморозить"), ("SHOCK", "Shock", "шокировать"), ("POISON", "Poison", "отравить"), ("BLEED", "cause Bleeding", "вызвать кровотечение")]:
    t(f"STOCK_{ail}_CHANCE", "ADD", f"{{v}}% chance to {en_v}", f"{{v}}% шанс {ru_v}")
t("STOCK_BURNING_DAMAGE", "ADD", "{v}% increased Burning Damage", "{v}% увеличение урона от горения")
t("STOCK_POISON_DAMAGE", "ADD", "{v}% increased Poison Damage", "{v}% увеличение урона от яда")
t("STOCK_BLEED_DAMAGE", "ADD", "{v}% increased Bleeding Damage", "{v}% увеличение урона от кровотечения")
for ail, en_p, ru_p, en_d, ru_d in [("IGNITE", "Ignited", "поджога", "Ignite", "поджога"), ("CHILL", "Chilled", "охлаждения", "Chill", "охлаждения"),
                                    ("FREEZE", "Frozen", "заморозки", "Freeze", "заморозки"), ("SHOCK", "Shocked", "шока", "Shock", "шока"),
                                    ("POISON", "Poisoned", "отравления", "Poison", "яда"), ("BLEED", "Bleeding", "кровотечения", "Bleeding", "кровотечения")]:
    t(f"STOCK_AVOID_{ail}", "ADD", f"{{v}}% chance to avoid being {en_p}", f"{{v}}% шанс избежать {ru_p}")
    t(f"STOCK_{ail}_DURATION_ON_SELF", "ADD", f"{{v}}% reduced {en_d} duration on you", f"{{v}}% уменьшение длительности {ru_d} на вас")
    t(f"STOCK_{ail}_DURATION", "ADD", f"{{v}}% increased {en_d} duration on enemies", f"{{v}}% увеличение длительности {ru_d} на врагах")
t("STOCK_AILMENT_DURATION", "ADD", "{v}% increased duration of Ailments on enemies", "{v}% увеличение длительности состояний на врагах")
t("STOCK_DAMAGE_VS_AILED", "ADD", "{v}% increased Damage against enemies affected by Ailments", "{v}% увеличение урона по врагам под состояниями")
for ail, en_p, ru_p in [("BURNING", "Burning", "горящим"), ("CHILLED", "Chilled", "охлаждённым"), ("SHOCKED", "Shocked", "шокированным"), ("POISONED", "Poisoned", "отравленным"), ("BLEEDING", "Bleeding", "кровоточащим")]:
    t(f"STOCK_DAMAGE_VS_{ail}", "ADD", f"{{v}}% increased Damage against {en_p} enemies", f"{{v}}% увеличение урона по {ru_p} врагам")
# --- пробивание, шипы, получаемый урон
for el, en, ru in [("FIRE", "Fire Resistance", "сопротивления огню"), ("COLD", "Cold Resistance", "сопротивления холоду"), ("LIGHTNING", "Lightning Resistance", "сопротивления молнии"),
                   ("CHAOS", "Chaos Resistance", "сопротивления хаосу"), ("ELEMENTAL", "Elemental Resistances", "сопротивлений стихиям")]:
    t(f"STOCK_PENETRATE_{el}", "ADD", f"Damage penetrates {{v}}% {en}", f"Урон пробивает {{v}}% {ru}")
t("STOCK_THORNS", "ADD", "Reflects {v} Physical Damage to attackers on hit", "Отражает {v} физического урона ударившему")
t("STOCK_REFLECT", "ADD", "Reflects {v}% of Damage taken to attackers", "Отражает {v}% полученного урона ударившему")
for stat, en, ru in [("STOCK_DAMAGE_TAKEN", "Damage taken", "получаемого урона"), ("STOCK_PHYSICAL_TAKEN", "Physical Damage taken", "получаемого физического урона"),
                     ("STOCK_ELEMENTAL_TAKEN", "Elemental Damage taken", "получаемого урона стихиями"), ("STOCK_CHAOS_TAKEN", "Chaos Damage taken", "получаемого урона хаосом")]:
    t(stat, "ADD", f"{{v}}% increased {en}", f"{{v}}% увеличение {ru}")
    t(stat, "ADD", f"{{v}}% reduced {en}", f"{{v}}% уменьшение {ru}", "negative")
# --- ауры монстров
t("AURA_RESIST", "ADD", "Nearby enemies have -{v}% to all Resistances", "Враги рядом теряют {v}% всех сопротивлений")
t("AURA_DAMAGE_TAKEN", "ADD", "Nearby enemies take {v}% increased Damage", "Враги рядом получают на {v}% больше урона")
t("AURA_SLOW", "ADD", "Nearby enemies have {v}% reduced Attack Speed", "Враги рядом бьют на {v}% медленнее")
t("AURA_RECOVERY", "ADD", "Nearby enemies have {v}% reduced Recovery rate", "Враги рядом восстанавливаются на {v}% медленнее")
# --- добыча, опыт, золото
inc("STOCK_RARITY", "Rarity of Items found", "редкости находимых предметов")
inc("STOCK_QUANTITY", "Quantity of Items found", "количества находимых предметов")
inc("STOCK_GOLD", "Gold found", "находимого золота")
inc("STOCK_EXPERIENCE", "Experience gain", "получаемого опыта")
inc("STOCK_CHEST_QUANTITY", "Chests on maps", "числа сундуков на картах")
# --- труд
inc("STOCK_WORK_SPEED", "Work Speed", "скорости работы")
t("STOCK_WORK_YIELD", "ADD", "{v}% chance of an extra unit per cycle", "{v}% шанс лишней единицы за цикл")
t("STOCK_WORK_LUCK", "ADD", "{v}% less chance of an empty cycle", "{v}% меньше шанс пустого цикла")
inc("STOCK_WORK_EXPERIENCE", "Profession Experience", "опыта профессии")
inc("STOCK_WORK_FIND", "chance of side finds", "шанса побочных находок")
# --- карты
t("MAP_MONSTER_LIFE", "INCREASED", "Monsters have {v}% increased Life", "Монстры: {v}% больше здоровья")
t("MAP_MONSTER_DAMAGE", "INCREASED", "Monsters deal {v}% increased Damage", "Монстры: {v}% больше урона")
t("MAP_MONSTER_SPEED", "INCREASED", "Monsters have {v}% increased Attack and Cast Speed", "Монстры: {v}% больше скорости атаки")
t("MAP_MONSTER_RESIST", "ADD", "Monsters have +{v}% to all Resistances", "Монстры: +{v}% ко всем сопротивлениям")
t("MAP_PACK_SIZE", "INCREASED", "{v}% increased Pack Size", "{v}% больше монстров")
t("MAP_MONSTER_RARITY", "INCREASED", "{v}% more Magic and Rare Monsters", "{v}% больше волшебных и редких монстров")
t("MAP_MAGIC_MONSTERS", "INCREASED", "{v}% more Magic Monsters", "{v}% больше волшебных монстров")
t("MAP_RARE_MONSTERS", "INCREASED", "{v}% more Rare Monsters", "{v}% больше редких монстров")
t("MAP_MONSTER_PENETRATION", "ADD", "Monsters penetrate {v}% Elemental Resistances", "Монстры пробивают {v}% сопротивлений стихиям")
t("MAP_MONSTER_REFLECT", "ADD", "Monsters reflect {v}% of Damage taken", "Монстры отражают {v}% полученного урона")
t("MAP_MONSTER_CRITICAL", "ADD", "Monsters have +{v}% to Critical Strike Chance", "Монстры: +{v}% к шансу критического удара")
t("MAP_MONSTER_AILMENTS", "ADD", "Monsters have +{v}% chance to inflict Ailments", "Монстры: +{v}% к шансу наложить состояние")
t("MAP_MONSTER_ARMOUR", "INCREASED", "Monsters have {v}% increased Armour and Evasion", "Монстры: {v}% больше брони и уклонения")
t("MAP_MONSTER_LEECH", "ADD", "Monsters leech {v}% of Damage as Life", "Монстры крадут {v}% урона здоровьем")
t("MAP_MONSTER_STUN", "ADD", "Monsters have {v}% chance to avoid Stun", "Монстры: {v}% шанс избежать оглушения")
t("MAP_MONSTER_MAGIC_MIN", "ADD", "All Monsters are at least Magic", "Все монстры не ниже волшебных")
t("MAP_HERO_LIGHT", "INCREASED", "Players have {v}% reduced Light Radius", "Герой: {v}% меньше радиус света")
t("MAP_HERO_RESIST", "ADD", "Players have -{v}% to all Resistances", "Герой: -{v}% ко всем сопротивлениям")
t("MAP_HERO_REGEN", "INCREASED", "Players have {v}% reduced Life Regeneration", "Герой: {v}% меньше восстановление здоровья")
t("MAP_HERO_SLOW", "INCREASED", "Players have {v}% reduced Movement Speed", "Герой: {v}% меньше скорость передвижения")
t("MAP_HERO_DAMAGE_TAKEN", "ADD", "Players take {v}% increased Damage", "Герой получает на {v}% больше урона")
t("MAP_HERO_RECOVERY", "ADD", "Players have {v}% less Life and Energy Shield Recovery", "Герой: {v}% меньше восстановление здоровья и щита")
t("MAP_HERO_MAX_RESIST", "ADD", "Players have -{v}% to maximum Resistances", "Герой: -{v}% к максимуму сопротивлений")
t("MAP_HERO_DEFENCES", "ADD", "Players have {v}% reduced Armour, Evasion and Energy Shield", "Герой: {v}% меньше брони, уклонения и щита")
t("MAP_HERO_BLOCK", "ADD", "Players have -{v}% chance to Block", "Герой: -{v}% к шансу блока")
t("MAP_HERO_CRIT", "ADD", "Players have {v}% reduced Critical Strike Chance", "Герой: {v}% меньше шанс критического удара")
t("MAP_HERO_HASTE", "ADD", "Players have {v}% increased Movement Speed", "Герой: {v}% больше скорость передвижения")
t("MAP_HERO_ATTACK_SPEED", "ADD", "Players have {v}% increased Attack Speed", "Герой: {v}% больше скорость атаки")
t("MAP_HERO_LIFE", "ADD", "Players have {v}% increased maximum Life", "Герой: {v}% больше максимум здоровья")
t("MAP_HERO_LEECH", "ADD", "Players leech {v}% of Damage as Life", "Герой крадёт {v}% урона здоровьем")
t("MAP_CHESTS", "ADD", "+{v} Chests", "+{v} сундука")
t("MAP_FOUNTAINS", "ADD", "+{v} Fountains", "+{v} источника")
t("MAP_QUANTITY", "INCREASED", "{v}% increased Quantity of Items found", "{v}% больше добычи")
t("MAP_QUANTITY", "INCREASED", "{v}% reduced Quantity of Items found", "{v}% меньше добычи", "negative")
t("MAP_RARITY", "INCREASED", "{v}% increased Rarity of Items found", "{v}% больше редкость добычи")
t("MAP_EXPERIENCE", "INCREASED", "{v}% increased Experience gain", "{v}% больше опыта")
t("MAP_GOLD", "INCREASED", "{v}% increased Gold found", "{v}% больше золота")
t("MAP_BOSS_POWER", "ADD", "Boss has {v}% more Life and Damage and drops {v}% more Items", "Босс: {v}% больше здоровья и урона и на {v}% щедрее")

JOIN = (", ", ", ")

# ---------------------------------------------------------------------------------------------
# Подписи характеристик (enum.EnumStatStock.<X>) для новых статов
# ---------------------------------------------------------------------------------------------
LABELS = {
    "STOCK_PENETRATE_FIRE": ("Fire Penetration", "Пробивание огня"), "STOCK_PENETRATE_COLD": ("Cold Penetration", "Пробивание холода"),
    "STOCK_PENETRATE_LIGHTNING": ("Lightning Penetration", "Пробивание молнии"), "STOCK_PENETRATE_CHAOS": ("Chaos Penetration", "Пробивание хаоса"),
    "STOCK_PENETRATE_ELEMENTAL": ("Elemental Penetration", "Пробивание стихий"),
    "STOCK_DAMAGE_VS_AILED": ("Damage vs Ailed", "Урон по состояниям"), "STOCK_DAMAGE_VS_BURNING": ("Damage vs Burning", "Урон по горящим"),
    "STOCK_DAMAGE_VS_CHILLED": ("Damage vs Chilled", "Урон по охлаждённым"), "STOCK_DAMAGE_VS_SHOCKED": ("Damage vs Shocked", "Урон по шокированным"),
    "STOCK_DAMAGE_VS_POISONED": ("Damage vs Poisoned", "Урон по отравленным"), "STOCK_DAMAGE_VS_BLEEDING": ("Damage vs Bleeding", "Урон по кровоточащим"),
    "STOCK_AILMENT_DURATION": ("Ailment Duration", "Длительность состояний"), "STOCK_IGNITE_DURATION": ("Ignite Duration", "Длительность поджога"),
    "STOCK_CHILL_DURATION": ("Chill Duration", "Длительность охлаждения"), "STOCK_FREEZE_DURATION": ("Freeze Duration", "Длительность заморозки"),
    "STOCK_SHOCK_DURATION": ("Shock Duration", "Длительность шока"), "STOCK_POISON_DURATION": ("Poison Duration", "Длительность яда"),
    "STOCK_BLEED_DURATION": ("Bleeding Duration", "Длительность кровотечения"),
    "STOCK_DAMAGE_TAKEN": ("Damage Taken", "Получаемый урон"), "STOCK_PHYSICAL_TAKEN": ("Physical Damage Taken", "Получаемый физический урон"),
    "STOCK_ELEMENTAL_TAKEN": ("Elemental Damage Taken", "Получаемый урон стихиями"), "STOCK_CHAOS_TAKEN": ("Chaos Damage Taken", "Получаемый урон хаосом"),
    "STOCK_RECOVERY_RATE": ("Recovery Rate", "Скорость восстановления"), "STOCK_SHIELD_RECHARGE": ("Shield Recharge", "Восполнение щита"),
    "STOCK_THORNS": ("Thorns", "Шипы"), "STOCK_REFLECT": ("Reflect", "Отражение"),
    "AURA_RESIST": ("Aura: Resistances", "Аура: сопротивления"), "AURA_DAMAGE_TAKEN": ("Aura: Damage Taken", "Аура: получаемый урон"),
    "AURA_SLOW": ("Aura: Slow", "Аура: замедление"), "AURA_RECOVERY": ("Aura: Recovery", "Аура: восстановление"),
    "MAP_HERO_DAMAGE_TAKEN": ("Map: Damage Taken", "Карта: получаемый урон"), "MAP_HERO_RECOVERY": ("Map: Recovery", "Карта: восстановление героя"),
    "MAP_HERO_MAX_RESIST": ("Map: Maximum Resistances", "Карта: максимум сопротивлений"), "MAP_HERO_DEFENCES": ("Map: Defences", "Карта: защита героя"),
    "MAP_HERO_BLOCK": ("Map: Block", "Карта: блок героя"), "MAP_HERO_CRIT": ("Map: Critical Chance", "Карта: крит героя"),
    "MAP_MONSTER_PENETRATION": ("Map Monster Penetration", "Пробивание монстров карты"), "MAP_MONSTER_REFLECT": ("Map Monster Reflect", "Отражение монстров карты"),
    "MAP_MONSTER_CRITICAL": ("Map Monster Critical", "Крит монстров карты"), "MAP_MONSTER_AILMENTS": ("Map Monster Ailments", "Состояния от монстров карты"),
    "MAP_MONSTER_ARMOUR": ("Map Monster Defences", "Защита монстров карты"), "MAP_MONSTER_LEECH": ("Map Monster Leech", "Вампиризм монстров карты"),
    "MAP_MONSTER_STUN": ("Map Monster Stun Avoidance", "Стойкость монстров карты"), "MAP_MONSTER_MAGIC_MIN": ("Map: Magic Monsters at least", "Карта: монстры не ниже волшебных"),
    "MAP_HERO_HASTE": ("Map: Hero Haste", "Карта: скорость героя"), "MAP_HERO_ATTACK_SPEED": ("Map: Hero Attack Speed", "Карта: скорость атаки героя"),
    "MAP_HERO_LIFE": ("Map: Hero Life", "Карта: здоровье героя"), "MAP_HERO_LEECH": ("Map: Hero Leech", "Карта: вампиризм героя"),
    "MAP_GOLD": ("Map Gold", "Золото карты"), "MAP_FOUNTAINS": ("Map Fountains", "Источники карты"), "MAP_BOSS_POWER": ("Map Boss Power", "Сила босса карты"),
    "ATLAS_MAP_NEXT": ("Atlas: Higher Maps", "Атлас: карты выше"), "ATLAS_MAP_RARE": ("Atlas: Rare Maps", "Атлас: редкие карты"),
    "ATLAS_MAP_AFFIX": ("Atlas: Extra Map Affix", "Атлас: лишний аффикс карты"), "ATLAS_MAP_EFFECT": ("Atlas: Map Modifier Effect", "Атлас: эффект модификаторов карты"),
    "ATLAS_VAAL_UNIQUE": ("Atlas: Vaal Uniques", "Атлас: уникалки Ваал"), "ATLAS_BOSS_LOOT": ("Atlas: Boss Loot", "Атлас: добыча с боссов"),
    "ATLAS_CHEST_LOOT": ("Atlas: Chest Loot", "Атлас: добыча из сундуков"), "ATLAS_RECIPE": ("Atlas: Recipes", "Атлас: рецепты"),
    "ATLAS_GOLD": ("Atlas: Gold", "Атлас: золото"), "ATLAS_MONSTER_MODS": ("Atlas: Rare Monster Modifiers", "Атлас: модификаторы редких монстров"),
}

EXTRA = {
    "enum.EnumModifierSource.MONSTER": ("Monster", "Монстр"),
    "enum.EnumEquipmentType.TOOL_ENCHANTING": ("Stylus", "Стилус"),
    "enum.EnumCurrencyOrb.TREASURE_ORB": ("Treasure Orb", "Сфера сокровищ"), "enum.EnumCurrencyOrb.GILDED_ORB": ("Gilded Orb", "Позолоченная сфера"),
    "enum.EnumCurrencyOrb.WARDEN_ORB": ("Warden Orb", "Сфера стража"),
    "enum.EnumCurrencyOrb.HELMET_SCROLL": ("Helmet Scroll", "Свиток шлема"), "enum.EnumCurrencyOrb.GLOVES_SCROLL": ("Gloves Scroll", "Свиток перчаток"),
    "enum.EnumCurrencyOrb.BOOTS_SCROLL": ("Boots Scroll", "Свиток сапог"), "enum.EnumCurrencyOrb.WEAPON_SCROLL": ("Weapon Scroll", "Свиток оружия"),
    "item.TREASURE_ORB.name": ("Treasure Orb", "Сфера сокровищ"), "item.TREASURE_ORB.description": ("Map alchemy: more chests", "Алхимия карты: больше сундуков"),
    "item.GILDED_ORB.name": ("Gilded Orb", "Позолоченная сфера"), "item.GILDED_ORB.description": ("Map alchemy: more gold", "Алхимия карты: больше золота"),
    "item.WARDEN_ORB.name": ("Warden Orb", "Сфера стража"), "item.WARDEN_ORB.description": ("Map alchemy: the boss is stronger and richer", "Алхимия карты: босс сильнее и щедрее"),
    "item.HELMET_SCROLL.name": ("Helmet Scroll", "Свиток шлема"), "item.HELMET_SCROLL.description": ("Enchants a helmet; one enchantment per item", "Зачаровывает шлем; одно зачарование на предмет"),
    "item.GLOVES_SCROLL.name": ("Gloves Scroll", "Свиток перчаток"), "item.GLOVES_SCROLL.description": ("Enchants gloves; one enchantment per item", "Зачаровывает перчатки; одно зачарование на предмет"),
    "item.BOOTS_SCROLL.name": ("Boots Scroll", "Свиток сапог"), "item.BOOTS_SCROLL.description": ("Enchants boots; one enchantment per item", "Зачаровывает сапоги; одно зачарование на предмет"),
    "item.WEAPON_SCROLL.name": ("Weapon Scroll", "Свиток оружия"), "item.WEAPON_SCROLL.description": ("Enchants a weapon; one enchantment per item", "Зачаровывает оружие; одно зачарование на предмет"),
    "profession.ENCHANTING.name": ("Enchanting", "Зачарование"), "profession.ENCHANTING.description": ("Scrolls that lay an enchantment on gear", "Свитки, что кладут зачарование на снаряжение"),
    "job.HELMET_SCROLL_SCRIBING.name": ("Scribe: Helmet Scroll", "Начертать: свиток шлема"), "job.BOOTS_SCROLL_SCRIBING.name": ("Scribe: Boots Scroll", "Начертать: свиток сапог"),
    "job.GLOVES_SCROLL_SCRIBING.name": ("Scribe: Gloves Scroll", "Начертать: свиток перчаток"), "job.WEAPON_SCROLL_SCRIBING.name": ("Scribe: Weapon Scroll", "Начертать: свиток оружия"),
    "job.TREASURE_BREW.name": ("Brew: Treasure Orb", "Сварить: сфера сокровищ"), "job.GILDED_BREW.name": ("Brew: Gilded Orb", "Сварить: позолоченная сфера"),
    "job.WARDEN_BREW.name": ("Brew: Warden Orb", "Сварить: сфера стража"),
    "equipment.BRONZE_STYLUS.name": ("Bronze Stylus", "Бронзовый стилус"), "equipment.BRONZE_STYLUS.description": ("A scribe's first stylus", "Первый стилус писца"),
    "equipment.STEEL_STYLUS.name": ("Steel Stylus", "Стальной стилус"), "equipment.STEEL_STYLUS.description": ("Cuts runes cleanly into any scroll", "Режет руны чисто на любом свитке"),
    "equipment.RUNIC_STYLUS.name": ("Runic Stylus", "Рунный стилус"), "equipment.RUNIC_STYLUS.description": ("Its own runes glow while it writes", "Его собственные руны светятся, пока он пишет"),
    "currency.enchanted": ("{0} was enchanted", "{0}: наложено зачарование"),
    "error.CR_026": ("{0} does not fit this slot", "{0}: свиток не для этого слота"),
}
COMMON = {
    "item.TREASURE_ORB.trade": "Treasure Orb", "item.GILDED_ORB.trade": "Gilded Orb", "item.WARDEN_ORB.trade": "Warden Orb",
    "item.HELMET_SCROLL.trade": "Helmet Scroll", "item.GLOVES_SCROLL.trade": "Gloves Scroll", "item.BOOTS_SCROLL.trade": "Boots Scroll", "item.WEAPON_SCROLL.trade": "Weapon Scroll",
    "equipment.BRONZE_STYLUS.trade": "Bronze Stylus", "equipment.STEEL_STYLUS.trade": "Steel Stylus", "equipment.RUNIC_STYLUS.trade": "Runic Stylus",
}


def needed_keys():
    """Ключи шаблонов, которые просят описания: по файлам модификаторов, уникалок и мифических предметов."""
    keys = set()
    with open(os.path.join(CONTENT, "modifiers.json"), encoding="utf-8") as src:
        records = json.load(src)["modifiers"]

    def negative(tiers, index):
        ranges = [tier["values"][index] for tier in tiers]
        return all(r[1] <= 0 for r in ranges) and any(r[0] < 0 for r in ranges)

    for record in records:
        tier_sets = [record["tiers"]] + [v.get("tiers") or record["tiers"] for v in record.get("variants", [])]
        for tiers in tier_sets:
            for index, effect in enumerate(record["effects"]):
                kind = "per" if effect.get("perStat") else ("negative" if negative(tiers, index) else "")
                keys.add((effect["stat"], effect["operation"], kind))
        if len(record["effects"]) > 1:
            keys.add(("join",))
    for name, section in [("uniques.json", "uniques"), ("mythics.json", "mythics")]:
        with open(os.path.join(CONTENT, name), encoding="utf-8") as src:
            for item in json.load(src)[section]:
                for line in item["lines"]:
                    for effect in line:
                        kind = "negative" if effect["range"][1] <= 0 and effect["range"][0] < 0 else ""
                        keys.add((effect["stat"], effect["operation"], kind))
                    if len(line) > 1:
                        keys.add(("join",))
    return keys


def key_of(triple):
    if triple == ("join",):
        return "stat.template.join"
    stat, op, kind = triple
    return f"stat.template.{stat}.{op}" + (f".{kind}" if kind else "")


def read(name):
    with open(os.path.join(LOCALE, name), encoding="utf-8") as src:
        return json.load(src)


def write(name, table):
    with open(os.path.join(LOCALE, name), "w", encoding="utf-8") as out:
        json.dump(dict(sorted(table.items())), out, ensure_ascii=False, indent=2)
        out.write("\n")


if __name__ == "__main__":
    needed = needed_keys()
    missing = [k for k in needed if k != ("join",) and k not in T]
    if missing:
        raise SystemExit("no template for: " + ", ".join(sorted(key_of(k) for k in missing)))
    for lang, index in [("en", 0), ("ru", 1)]:
        table = {k: v for k, v in read(f"{lang}.json").items() if not k.startswith(("modifier.", "stat.template."))}
        for triple in needed:
            table[key_of(triple)] = JOIN[index] if triple == ("join",) else T[triple][index]
        for stat, labels in LABELS.items():
            table[f"enum.EnumStatStock.{stat}"] = labels[index]
        for key, values in EXTRA.items():
            table[key] = values[index]
        write(f"{lang}.json", table)
    common = read("common.json")
    common.update(COMMON)
    write("common.json", common)
    print(f"templates {len(needed)}, labels {len(LABELS)}, extra {len(EXTRA)}")
