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

# --- мана, умения класса, фляги, эссенции, карты (0.69.0)
inc("STOCK_MANA", "maximum Mana", "максимума маны")
t("STOCK_MANA", "ADD", "+{v} to maximum Mana per {n} {s}", "+{v} к максимуму маны за каждые {n} {s}", "per")
inc("STOCK_MANA_REGEN", "Mana Regeneration rate", "скорости регенерации маны")
t("STOCK_MANA_ON_KILL", "ADD", "+{v} Mana gained on kill", "+{v} маны за убийство")
t("STOCK_MANA_ON_HIT", "ADD", "+{v} Mana gained for each enemy hit", "+{v} маны за каждый удар по врагу")
t("STOCK_LEECH_MANA", "ADD", "{v}% of Damage leeched as Mana", "{v}% урона крадётся маной")
t("STOCK_SKILL_COST", "ADD", "Skills cost {v}% less Mana", "Умения тратят на {v}% меньше маны")
t("STOCK_RESERVATION", "ADD", "{v}% increased Reservation Efficiency of Auras", "{v}% увеличение эффективности резерва аур")
for stat, en, ru in [("STOCK_SKILL_LEVEL", "all Class Skills", "всех умений"), ("STOCK_ATTACK_LEVEL", "Attack Skills", "атакующих умений"),
                     ("STOCK_SPELL_LEVEL", "Spell Skills", "чар"), ("STOCK_WARCRY_LEVEL", "Warcry Skills", "кличей"),
                     ("STOCK_CURSE_LEVEL", "Curse Skills", "проклятий"), ("STOCK_AURA_LEVEL", "Aura Skills", "аур"),
                     ("STOCK_PASSIVE_LEVEL", "Passive Skills", "пассивных умений")]:
    t(stat, "ADD", f"+{{v}} to Level of {en}", f"+{{v}} к уровню {ru}")
inc("STOCK_SPELL_DAMAGE", "Spell Damage", "урона чар")
inc("STOCK_COOLDOWN_RECOVERY", "Cooldown Recovery Rate of Skills", "скорости перезарядки умений")
inc("STOCK_SKILL_DAMAGE", "Damage of Class Skills", "урона умений")
inc("STOCK_AURA_EFFECT", "effect of Auras", "эффекта аур")
inc("STOCK_WARCRY_EFFECT", "effect of Warcries", "эффекта кличей")
t("STOCK_WARCRY_EFFECT", "ADD", "Warcries are {v}% stronger", "Кличи сильнее на {v}%")
inc("STOCK_CURSE_EFFECT", "effect of Curses", "эффекта проклятий")
inc("STOCK_SKILL_HEALING", "Healing from Skills", "силы лечения умений")
t("STOCK_SKILL_TARGETS", "ADD", "Attack Skills hit {v} additional target", "Атакующие умения поражают ещё {v} цель")
inc("STOCK_DAMAGE", "Damage", "урона")
t("STOCK_DAMAGE", "ADD", "Deals {v}% more Damage", "Наносит на {v}% больше урона")
inc("STOCK_FLASK_CHARGES_GAINED", "Flask Charges gained", "получаемых зарядов фляг")
inc("STOCK_FLASK_DURATION", "Flask effect Duration", "длительности фляг")
inc("STOCK_FLASK_EFFECT", "effect of Flasks", "эффекта фляг")
t("STOCK_FLASK_CHARGES_USED", "ADD", "{v}% reduced Flask Charges used", "{v}% уменьшение расхода зарядов фляг")
inc("STOCK_FLASK_LIFE_RECOVERY", "Life Recovery from Flasks", "восстановления здоровья флягами")
inc("STOCK_FLASK_RECOVERY", "Flask Recovery", "восстановления фляги")
t("STOCK_FLASK_CHARGES_PER_KILL", "ADD", "+{v} Flask Charges gained on kill", "+{v} заряда фляг за убийство")
t("FLASK_CHARGES", "ADD", "+{v} to maximum Charges", "+{v} к максимуму зарядов")
t("FLASK_CHARGES_PER_USE", "ADD", "Uses {v} Charges per sip", "Тратит {v} зарядов за глоток")
t("FLASK_DURATION", "ADD", "+{v} seconds to Duration", "+{v} с к длительности действия")
t("FLASK_LIFE", "ADD", "Recovers {v} Life", "Восстанавливает {v} здоровья")
t("FLASK_MANA", "ADD", "Recovers {v} Mana", "Восстанавливает {v} маны")
t("FLASK_INSTANT", "ADD", "{v}% of Recovery applied Instantly", "{v}% восстановления сразу")
t("FLASK_CHARGE_ON_CRIT", "ADD", "{v}% chance to gain a Flask Charge on Critical Strike", "{v}% шанс получить заряд при критическом ударе")
t("FLASK_CHARGE_WHEN_HIT", "ADD", "{v}% chance to gain a Flask Charge when Hit", "{v}% шанс получить заряд, получив удар")
t("FLASK_LIFE_TO_SHIELD", "ADD", "{v}% of Life Recovery also applies to Energy Shield", "{v}% восстановленного здоровья идёт и в энергощит")
t("FLASK_LIFE_TO_MANA", "ADD", "{v}% of Life Recovery also restores Mana", "{v}% восстановленного здоровья возвращается маной")
t("FLASK_LOW_LIFE_RECOVERY", "ADD", "{v}% more Recovery while on Low Life", "На {v}% больше восстановления при здоровье ниже 35%")
t("FLASK_DURATION_PER_KILL", "ADD", "+{v} seconds to Duration for each kill during the effect", "+{v} с к действию за каждое убийство во время действия")
t("FLASK_NO_CHARGE_CHANCE", "ADD", "{v}% chance not to consume Charges", "{v}% шанс не потратить заряды")
t("FLASK_SIP_MANA", "ADD", "Each sip also restores {v}% of maximum Mana", "Глоток восстанавливает и {v}% маны")
t("FLASK_SIP_SHIELD", "ADD", "Each sip restores {v}% of maximum Energy Shield", "Глоток сразу восстанавливает {v}% энергощита")
t("FLASK_AUTO_LOW_LIFE", "ADD", "Used automatically when Life falls below {v}%", "Пьётся сама, когда здоровья меньше {v}%")
t("FLASK_USES_ALL", "ADD", "Each sip consumes all Charges", "Глоток тратит все заряды")
t("FLASK_INVULNERABLE", "ADD", "Invulnerable for {v} seconds", "Неуязвимость на {v} с")
t("FLASK_SKILLS_FREE", "ADD", "Skills cost no Mana during the effect", "Умения не тратят ману во время действия")
t("FLASK_HITS_CURSE", "ADD", "Hits apply a random Curse of your class during the effect", "Во время действия удары накладывают случайное проклятие класса")
for stat, en, ru in [("STOCK_IMMUNE_BLEED", "Bleeding", "кровотечению"), ("STOCK_IMMUNE_FREEZE", "Chill and Freeze", "холоду и заморозке"),
                     ("STOCK_IMMUNE_IGNITE", "Ignite", "поджогу"), ("STOCK_IMMUNE_SHOCK", "Shock", "шоку"), ("STOCK_IMMUNE_POISON", "Poison", "отравлению"),
                     ("STOCK_IMMUNE_CURSE", "Curses", "проклятиям"), ("STOCK_IMMUNE_STUN", "Stun", "оглушению")]:
    t(stat, "ADD", f"Immune to {en}", f"Невосприимчивость к {ru}")
t("STOCK_LIFE_REGEN_PERCENT", "ADD", "Regenerate {v}% of Life per second", "Восстанавливает {v}% здоровья в секунду")
t("STOCK_SHIELD_OF_LIFE", "ADD", "Energy Shield equal to {v}% of Life", "Энергощит в {v}% здоровья")
t("STOCK_LOW_LIFE_SPEED", "ADD", "{v}% increased Attack Speed while below half Life", "{v}% увеличение скорости атаки, пока здоровья меньше половины")
t("STOCK_MANA_BURN", "ADD", "Hits burn {v}% of the target's Mana", "Удары сжигают {v}% маны цели")
t("STOCK_BORROW_SKILLS", "ADD", "Uses the Skills of other Monsters", "Применяет умения других монстров")
t("AURA_WEAKEN", "ADD", "Nearby enemies deal {v}% less Damage", "Враги рядом наносят на {v}% меньше урона")
t("AURA_CRIT", "ADD", "Nearby enemies have {v}% reduced Critical Strike Chance", "Враги рядом: на {v}% меньше шанс критического удара")
t("AURA_COOLDOWN", "ADD", "Skills of nearby enemies recover {v}% slower", "Умения врагов рядом перезаряжаются на {v}% медленнее")
t("STOCK_FREE_SKILL_CHANCE", "ADD", "{v}% chance to use a ready Skill without Mana", "{v}% шанс применить готовое умение без маны")
inc("STOCK_DAMAGE_VS_CURSED", "Damage against Cursed enemies", "урона по проклятым")
t("STOCK_CURSE_ON_HIT", "ADD", "{v}% chance to Curse the attacker with your Curse when Hit", "Получив удар — {v}% шанс проклясть врага своим проклятием")
t("STOCK_WARCRY_SPEED", "ADD", "Warcries grant {v}% increased Attack Speed", "Кличи дают {v}% увеличение скорости атаки")
t("STOCK_WARCRY_HEAL", "ADD", "Warcries recover {v}% of Life", "Кличи восстанавливают {v}% здоровья")
t("MAP_FLASK_CHARGES", "ADD", "Players' Flasks gain {v}% fewer Charges", "Фляги героя получают на {v}% меньше зарядов")
t("MAP_HERO_MANA_REGEN", "ADD", "Players have {v}% less Mana Regeneration", "Герой: на {v}% меньше регенерация маны")
t("MAP_MONSTER_CAST", "ADD", "Monsters cast and recover Skills {v}% faster", "Монстры колдуют и перезаряжают умения на {v}% быстрее")
t("MAP_SKILL_COST", "ADD", "Players' Skills cost {v}% more Mana", "Умения героя дороже на {v}%")
t("MAP_CRYSTALS", "ADD", "+{v} Essence Crystals", "+{v} кристалла эссенций")
t("MAP_BOOKS", "INCREASED", "{v}% increased chance of Skill Books", "{v}% больше шанс книг умений")

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
    # 0.69.0: мана, умения класса, фляги, эссенции
    "STOCK_DAMAGE": ("Damage", "Урон"), "STOCK_SKILL_DAMAGE": ("Skill Damage", "Урон умений"), "STOCK_SPELL_DAMAGE": ("Spell Damage", "Урон чар"),
    "STOCK_DOT_TAKEN": ("Damage over Time Taken", "Получаемый урон со временем"), "STOCK_CRITICAL_TAKEN": ("Critical Multiplier Taken", "Множитель крита по цели"),
    "STOCK_COOLDOWN_RECOVERY": ("Cooldown Recovery", "Перезарядка умений"), "STOCK_LEECH_MANA": ("Mana Leech", "Кража маны"),
    "STOCK_MANA_ON_KILL": ("Mana on Kill", "Мана за убийство"), "STOCK_MANA_ON_HIT": ("Mana on Hit", "Мана за удар"),
    "STOCK_SKILL_COST": ("Skill Cost Reduction", "Снижение цены умений"), "STOCK_RESERVATION": ("Reservation Efficiency", "Эффективность резерва"),
    "STOCK_LIFE_REGEN_PERCENT": ("Life Regeneration %", "Регенерация здоровья, %"),
    "STOCK_BLEED_TAKEN": ("Bleed Chance Taken", "Шанс кровотечения по цели"), "STOCK_SHOCK_TAKEN": ("Shock Effect Taken", "Сила шока по цели"),
    "STOCK_SHIELD_OF_LIFE": ("Shield of Life", "Щит из здоровья"), "STOCK_LOW_LIFE_SPEED": ("Low Life Speed", "Скорость при ранении"),
    "STOCK_MANA_BURN": ("Mana Burn", "Сжигание маны"), "STOCK_BORROW_SKILLS": ("Borrowed Skills", "Чужие умения"),
    "AURA_WEAKEN": ("Aura: Weakness", "Аура: слабость"), "AURA_CRIT": ("Aura: Doubt", "Аура: сомнение"), "AURA_COOLDOWN": ("Aura: Misery", "Аура: несчастье"),
    "MAP_FLASK_CHARGES": ("Map: Flask Charges", "Карта: заряды фляг"), "MAP_HERO_MANA_REGEN": ("Map: Mana Regeneration", "Карта: регенерация маны"),
    "MAP_MONSTER_CAST": ("Map Monster Casting", "Колдовство монстров карты"), "MAP_SKILL_COST": ("Map: Skill Cost", "Карта: цена умений"),
    "MAP_CRYSTALS": ("Map Essence Crystals", "Кристаллы эссенций карты"), "MAP_BOOKS": ("Map Skill Books", "Книги умений карты"),
    "ATLAS_CRYSTAL_CHANCE": ("Atlas: Crystal Chance", "Атлас: шанс кристаллов"), "ATLAS_CRYSTAL_ESSENCES": ("Atlas: Extra Essence", "Атлас: лишняя эссенция"),
    "ATLAS_CRYSTAL_TIER": ("Atlas: Higher Essences", "Атлас: эссенции выше"), "ATLAS_CRYSTALS": ("Atlas: Crystals", "Атлас: кристаллы"),
    "ATLAS_CRYSTALS_MORE": ("Atlas: More Crystals", "Атлас: больше кристаллов"), "ATLAS_GUARDIAN_POWER": ("Atlas: Guardian Power", "Атлас: сила стражей"),
    "ATLAS_BOOKS": ("Atlas: Skill Books", "Атлас: книги умений"), "ATLAS_BOOKS_OWN": ("Atlas: Own Class Books", "Атлас: книги своего класса"),
    "ATLAS_FLASK_CHARGES": ("Atlas: Flask Charges", "Атлас: заряды фляг"), "ATLAS_FLASK_DURATION": ("Atlas: Flask Duration", "Атлас: длительность фляг"),
    "ATLAS_FLASK_RARE": ("Atlas: Charges from Rares", "Атлас: заряды за редких"), "ATLAS_MANA_REGEN": ("Atlas: Mana Regeneration", "Атлас: регенерация маны"),
    "ATLAS_SKILL_LEVEL": ("Atlas: Skill Level", "Атлас: уровень умений"),
    "STOCK_SKILL_LEVEL": ("Level of all Skills", "Уровень всех умений"), "STOCK_ATTACK_LEVEL": ("Level of Attacks", "Уровень атак"),
    "STOCK_SPELL_LEVEL": ("Level of Spells", "Уровень чар"), "STOCK_WARCRY_LEVEL": ("Level of Warcries", "Уровень кличей"),
    "STOCK_CURSE_LEVEL": ("Level of Curses", "Уровень проклятий"), "STOCK_AURA_LEVEL": ("Level of Auras", "Уровень аур"),
    "STOCK_PASSIVE_LEVEL": ("Level of Passives", "Уровень пассивных умений"),
    "STOCK_AURA_EFFECT": ("Aura Effect", "Эффект аур"), "STOCK_WARCRY_EFFECT": ("Warcry Effect", "Эффект кличей"),
    "STOCK_CURSE_EFFECT": ("Curse Effect", "Эффект проклятий"), "STOCK_SKILL_HEALING": ("Skill Healing", "Лечение умений"),
    "STOCK_SKILL_TARGETS": ("Skill Targets", "Цели умений"), "STOCK_FREE_SKILL_CHANCE": ("Free Skill Chance", "Шанс умения без маны"),
    "STOCK_DAMAGE_VS_CURSED": ("Damage vs Cursed", "Урон по проклятым"), "STOCK_CURSE_ON_HIT": ("Curse when Hit", "Проклятие в ответ"),
    "STOCK_WARCRY_SPEED": ("Warcry Speed", "Скорость от кличей"), "STOCK_WARCRY_HEAL": ("Warcry Healing", "Лечение кличей"),
    "STOCK_FLASK_CHARGES_GAINED": ("Flask Charges Gained", "Заряды фляг"), "STOCK_FLASK_DURATION": ("Flask Duration", "Длительность фляг"),
    "STOCK_FLASK_EFFECT": ("Flask Effect", "Эффект фляг"), "STOCK_FLASK_CHARGES_USED": ("Flask Charges Used", "Расход зарядов"),
    "STOCK_FLASK_LIFE_RECOVERY": ("Flask Life Recovery", "Восстановление флягами жизни"), "STOCK_FLASK_RECOVERY": ("Flask Recovery", "Восстановление фляги"),
    "STOCK_FLASK_CHARGES_PER_KILL": ("Flask Charges per Kill", "Заряды за убийство"),
    "FLASK_CHARGES": ("Charges", "Заряды"), "FLASK_CHARGES_PER_USE": ("Charges per Use", "Зарядов за глоток"), "FLASK_DURATION": ("Duration", "Длительность"),
    "FLASK_LIFE": ("Life Recovered", "Восстанавливает здоровья"), "FLASK_MANA": ("Mana Recovered", "Восстанавливает маны"),
    "FLASK_INSTANT": ("Instant Recovery", "Восстановление сразу"), "FLASK_CHARGE_ON_CRIT": ("Charge on Crit", "Заряд при крите"),
    "FLASK_CHARGE_WHEN_HIT": ("Charge when Hit", "Заряд от удара"), "FLASK_LIFE_TO_SHIELD": ("Life to Shield", "Здоровье в щит"),
    "FLASK_LIFE_TO_MANA": ("Life to Mana", "Здоровье в ману"), "FLASK_LOW_LIFE_RECOVERY": ("Low Life Recovery", "Восстановление при ранении"),
    "FLASK_DURATION_PER_KILL": ("Duration per Kill", "Длительность за убийство"), "FLASK_NO_CHARGE_CHANCE": ("Free Sip Chance", "Глоток без зарядов"),
    "FLASK_SIP_MANA": ("Mana per Sip", "Мана за глоток"), "FLASK_SIP_SHIELD": ("Shield per Sip", "Щит за глоток"),
    "FLASK_AUTO_LOW_LIFE": ("Automatic Sip", "Глоток сам"), "FLASK_USES_ALL": ("Uses all Charges", "Тратит все заряды"),
    "FLASK_INVULNERABLE": ("Invulnerability", "Неуязвимость"), "FLASK_SKILLS_FREE": ("Free Skills", "Умения без маны"),
    "FLASK_HITS_CURSE": ("Cursing Hits", "Проклинающие удары"),
    "STOCK_IMMUNE_BLEED": ("Bleed Immunity", "Невосприимчивость к кровотечению"), "STOCK_IMMUNE_FREEZE": ("Freeze Immunity", "Невосприимчивость к холоду"),
    "STOCK_IMMUNE_IGNITE": ("Ignite Immunity", "Невосприимчивость к поджогу"), "STOCK_IMMUNE_SHOCK": ("Shock Immunity", "Невосприимчивость к шоку"),
    "STOCK_IMMUNE_POISON": ("Poison Immunity", "Невосприимчивость к яду"), "STOCK_IMMUNE_CURSE": ("Curse Immunity", "Невосприимчивость к проклятиям"),
    "STOCK_IMMUNE_STUN": ("Stun Immunity", "Невосприимчивость к оглушению"),
}

EXTRA = {
    "enum.EnumModifierSource.MONSTER": ("Monster", "Монстр"),
    "enum.EnumModifierSource.ESSENCE": ("Essence", "Эссенция"),
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
