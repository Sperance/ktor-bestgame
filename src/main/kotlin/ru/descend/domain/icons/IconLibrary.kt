package ru.descend.domain.icons

import ru.descend.domain.icons.IconCategory.*
import ru.descend.domain.icons.IconPalette.ASH_D
import ru.descend.domain.icons.IconPalette.ASH_T
import ru.descend.domain.icons.IconPalette.BLUE_D
import ru.descend.domain.icons.IconPalette.BLUE_T
import ru.descend.domain.icons.IconPalette.BRONZE_D
import ru.descend.domain.icons.IconPalette.BRONZE_T
import ru.descend.domain.icons.IconPalette.CRIMSON_D
import ru.descend.domain.icons.IconPalette.CRIMSON_T
import ru.descend.domain.icons.IconPalette.CYAN_D
import ru.descend.domain.icons.IconPalette.CYAN_T
import ru.descend.domain.icons.IconPalette.GOLD_D
import ru.descend.domain.icons.IconPalette.GOLD_T
import ru.descend.domain.icons.IconPalette.GREEN_D
import ru.descend.domain.icons.IconPalette.GREEN_T
import ru.descend.domain.icons.IconPalette.ORANGE_D
import ru.descend.domain.icons.IconPalette.ORANGE_T
import ru.descend.domain.icons.IconPalette.PURPLE_D
import ru.descend.domain.icons.IconPalette.PURPLE_T
import ru.descend.domain.icons.IconPalette.RED_D
import ru.descend.domain.icons.IconPalette.RED_T
import ru.descend.domain.icons.IconPalette.SILVER_D
import ru.descend.domain.icons.IconPalette.SILVER_T
import ru.descend.domain.icons.IconPalette.STEEL_D
import ru.descend.domain.icons.IconPalette.STEEL_T
import ru.descend.domain.icons.IconPalette.TEAL_D
import ru.descend.domain.icons.IconPalette.TEAL_T
import ru.descend.domain.icons.IconPalette.VIOLET_D
import ru.descend.domain.icons.IconPalette.VIOLET_T
import ru.descend.domain.icons.IconPalette.YELLOW_D
import ru.descend.domain.icons.IconPalette.YELLOW_T

/**
 * Рисованный набор иконок сервера.
 *
 * Каждая иконка — оригинальный вектор в сетке 64x64, никаких внешних ресурсов
 * и никаких артов Path of Exile. Рисунок живёт внутри группы, которая задаёт
 * обводку `url(#edge)` и скругления, а `url(#core)` даёт объём заливкой.
 */
internal object IconLibrary {

    private fun art(id: String, title: String, category: IconCategory, tint: String, deep: String,
                    body: String, vararg keywords: String) = IconArt(id, title, category, tint, deep, body, keywords.toList())

    /** Оружие: клинки, древковое, дальний бой. Сталь для железа, бронза для дерева, фиалка для магии. */
    private val weapons = listOf(
        art("weapon-sword", "Меч", WEAPON, STEEL_T, STEEL_D,
            """<path d="M50 11 L31 36 L26 31 Z" fill="url(#core)"/><path d="M47 15 L29 33"/><path d="M22 27 L35 40"/><path d="M29 35 L21 43"/><circle cx="19" cy="45" r="2.4"/>""",
            "sword", "меч", "one hand sword"),
        art("weapon-longsword", "Длинный меч", WEAPON, STEEL_T, STEEL_D,
            """<path d="M52 10 L28 38 L22 32 Z" fill="url(#core)"/><path d="M49 14 L27 35"/><path d="M19 26 L34 41"/><path d="M26 37 L17 46"/><path d="M15 47 l3-3 3 3 -3 3 z" fill="url(#core)"/>""",
            "longsword", "длинный меч"),
        art("weapon-thrusting-sword", "Рапира", WEAPON, SILVER_T, SILVER_D,
            """<path d="M53 9 L29 33"/><path d="M50 10 L52 12"/><path d="M26 28 L34 36"/><path d="M25 31 Q17 39 25 45"/><path d="M29 36 L21 44"/><circle cx="19" cy="46" r="2.2"/>""",
            "rapier", "рапира", "thrusting"),
        art("weapon-two-hand-sword", "Двуручный меч", WEAPON, STEEL_T, STEEL_D,
            """<path d="M50 9 L33 40 L22 29 Z" fill="url(#core)"/><path d="M46 14 L30 34"/><path d="M18 25 L35 42"/><path d="M28 39 L18 49"/><path d="M15 45 L21 51"/>""",
            "two hand sword", "двуручный"),
        art("weapon-axe", "Топор", WEAPON, STEEL_T, STEEL_D,
            """<path d="M35 54 L35 11"/><path d="M30 53 L40 53"/><path d="M36 12 Q54 16 54 26 Q54 36 36 34 Q43 23 36 12 Z" fill="url(#core)"/>""",
            "axe", "топор"),
        art("weapon-two-hand-axe", "Секира", WEAPON, STEEL_T, STEEL_D,
            """<path d="M32 12 L32 54"/><path d="M30 15 Q12 19 12 30 Q12 39 30 35 Q24 25 30 15 Z" fill="url(#core)"/><path d="M34 15 Q52 19 52 30 Q52 39 34 35 Q40 25 34 15 Z" fill="url(#core)"/><path d="M27 48 L37 48"/>""",
            "two hand axe", "секира"),
        art("weapon-mace", "Булава", WEAPON, ASH_T, ASH_D,
            """<path d="M19 51 L36 31"/><circle cx="42" cy="22" r="10" fill="url(#core)"/><path d="M42 9 L42 35"/><path d="M29 22 L55 22"/><path d="M34 14 L50 30"/><path d="M50 14 L34 30"/><path d="M16 48 L22 54"/>""",
            "mace", "булава"),
        art("weapon-two-hand-mace", "Молот", WEAPON, ASH_T, ASH_D,
            """<path d="M32 30 L32 54"/><path d="M27 51 L37 51"/><path d="M18 13 L46 13 L46 31 L18 31 Z" fill="url(#core)"/><path d="M24 13 L24 31"/><path d="M40 13 L40 31"/><path d="M18 17 L14 22 L18 27"/><path d="M46 17 L50 22 L46 27"/>""",
            "two hand mace", "молот"),
        art("weapon-sceptre", "Скипетр", WEAPON, GOLD_T, GOLD_D,
            """<path d="M20 51 L34 32"/><path d="M17 48 L23 54"/><path d="M33 33 L29 21 L35 24 L39 12 L45 22 L49 18 L46 31 Z" fill="url(#core)"/><circle cx="39" cy="23" r="3.2"/>""",
            "sceptre", "скипетр"),
        art("weapon-staff", "Посох", WEAPON, BRONZE_T, BRONZE_D,
            """<path d="M16 52 L40 25"/><path d="M45 6 L53 15 L48 28 L40 28 L37 15 Z" fill="url(#core)"/><path d="M37 15 L53 15"/><path d="M45 6 L40 15 L44 28"/><path d="M26 40 L31 45"/><path d="M20 46 L25 51"/>""",
            "staff", "посох"),
        art("weapon-warstaff", "Боевой посох", WEAPON, BRONZE_T, BRONZE_D,
            """<path d="M15 53 L40 27"/><path d="M38 25 Q46 6 57 11 Q51 23 38 25 Z" fill="url(#core)"/><path d="M13 51 l4-4 4 4 -4 4 z" fill="url(#core)"/><path d="M25 42 L30 47"/>""",
            "warstaff", "боевой посох"),
        art("weapon-bow", "Лук", WEAPON, BRONZE_T, BRONZE_D,
            """<path d="M22 10 Q50 32 22 54"/><path d="M22 10 L22 54"/><path d="M13 32 L45 32"/><path d="M38 27 L45 32 L38 37"/><path d="M13 32 L18 28"/><path d="M13 32 L18 36"/>""",
            "bow", "лук"),
        art("weapon-wand", "Жезл", WEAPON, VIOLET_T, VIOLET_D,
            """<path d="M17 49 L35 31"/><path d="M40 26 L33 20 L40 13 L47 20 Z" fill="url(#core)"/><path d="M52 12 l0 7"/><path d="M48.5 15.5 l7 0"/><path d="M27 35 L32 40"/>""",
            "wand", "жезл"),
        art("weapon-claw", "Коготь", WEAPON, SILVER_T, SILVER_D,
            """<path d="M17 40 Q13 50 21 54 Q30 57 33 48 Z" fill="url(#core)"/><path d="M20 37 Q31 22 46 14"/><path d="M25 41 Q37 28 51 22"/><path d="M30 46 Q42 36 54 33"/>""",
            "claw", "коготь"),
        art("weapon-dagger", "Кинжал", WEAPON, STEEL_T, STEEL_D,
            """<path d="M45 13 L30 34 L26 30 Z" fill="url(#core)"/><path d="M42 17 L29 31"/><path d="M23 27 L33 37"/><path d="M29 34 L22 41"/><circle cx="20" cy="43" r="2.2"/>""",
            "dagger", "кинжал"),
        art("weapon-rune-dagger", "Рунный кинжал", WEAPON, VIOLET_T, VIOLET_D,
            """<path d="M45 13 L30 34 L26 30 Z" fill="url(#core)"/><path d="M23 27 L33 37"/><path d="M29 34 L22 41"/><circle cx="20" cy="43" r="2.2"/><circle cx="37" cy="24" r="4.6"/><path d="M34.5 24 L39.5 24"/><path d="M37 21.5 L37 26.5"/>""",
            "rune dagger", "рунный"),
        art("weapon-blade", "Клинок", WEAPON, STEEL_T, STEEL_D,
            """<path d="M51 11 Q36 16 23 33 Q34 30 44 22 Q50 17 51 11 Z" fill="url(#core)"/><path d="M20 30 L29 39"/><path d="M26 36 L18 44"/><circle cx="16" cy="46" r="2.2"/>""",
            "blade", "клинок", "sabre")
    )

    /** Броня и слоты снаряжения. */
    private val armour = listOf(
        art("armour-helmet", "Шлем", ARMOUR, BRONZE_T, BRONZE_D,
            """<path d="M18 46 L18 30 Q18 14 32 14 Q46 14 46 30 L46 46 L38 42 L32 50 L26 42 Z" fill="url(#core)"/><path d="M32 18 L32 40"/><path d="M22 32 L28 34"/><path d="M42 32 L36 34"/>""",
            "helmet", "шлем"),
        art("armour-body", "Доспех", ARMOUR, BRONZE_T, BRONZE_D,
            """<path d="M16 22 L28 14 L32 20 L36 14 L48 22 L44 34 L42 52 L22 52 L20 34 Z" fill="url(#core)"/><path d="M32 22 L32 48"/><path d="M23 34 L41 34"/>""",
            "body armour", "нагрудник", "доспех"),
        art("armour-gloves", "Перчатки", ARMOUR, BRONZE_T, BRONZE_D,
            """<path d="M24 24 Q24 14 31 14 L40 14 Q46 14 46 24 L46 38 Q46 52 35 52 Q24 52 24 38 Z" fill="url(#core)"/><path d="M24 30 Q15 30 15 36 Q15 42 24 42"/><path d="M31 15 L31 26"/><path d="M38 15 L38 26"/><path d="M24 44 L46 44"/>""",
            "gloves", "перчатки"),
        art("armour-boots", "Сапоги", ARMOUR, BRONZE_T, BRONZE_D,
            """<path d="M24 12 L34 12 L33 36 Q33 40 38 43 L48 48 L48 54 L22 54 L22 40 Z" fill="url(#core)"/><path d="M24 21 L33 21"/><path d="M23 29 L33 29"/><path d="M24 48 L46 48"/>""",
            "boots", "сапоги"),
        art("armour-shield", "Щит", ARMOUR, STEEL_T, STEEL_D,
            """<path d="M32 12 L50 19 Q50 42 32 53 Q14 42 14 19 Z" fill="url(#core)"/><path d="M32 18 L32 46"/><path d="M20 28 L44 28"/>""",
            "shield", "щит"),
        art("armour-quiver", "Колчан", ARMOUR, BRONZE_T, BRONZE_D,
            """<path d="M22 24 L42 24 L38 54 L26 54 Z" fill="url(#core)"/><path d="M27 24 L27 12"/><path d="M33 24 L33 8"/><path d="M39 24 L39 14"/><path d="M24 15 L27 10 L30 15"/><path d="M30 12 L33 6 L36 12"/><path d="M36 17 L39 12 L42 17"/><path d="M23 33 L41 33"/>""",
            "quiver", "колчан"),
        art("armour-belt", "Пояс", ARMOUR, BRONZE_T, BRONZE_D,
            """<path d="M10 26 L54 26 L54 40 L10 40 Z" fill="url(#core)"/><path d="M24 21 L40 21 L40 45 L24 45 Z"/><path d="M32 33 L48 33"/>""",
            "belt", "пояс"),
        art("armour-wings", "Крылья", ARMOUR, SILVER_T, SILVER_D,
            """<path d="M31 50 L14 32 Q12 18 16 14 Q26 22 31 34 Z" fill="url(#core)"/><path d="M33 50 L50 32 Q52 18 48 14 Q38 22 33 34 Z" fill="url(#core)"/>""",
            "wings", "крылья")
    )

    /** Украшения. */
    private val jewellery = listOf(
        art("jewellery-ring", "Кольцо", JEWELLERY, GOLD_T, GOLD_D,
            """<circle cx="32" cy="41" r="13"/><path d="M32 10 L42 22 L32 34 L22 22 Z" fill="url(#core)"/><path d="M25 22 L39 22"/>""",
            "ring", "кольцо"),
        art("jewellery-amulet", "Амулет", JEWELLERY, GOLD_T, GOLD_D,
            """<path d="M18 12 Q12 36 32 33 Q52 36 46 12"/><path d="M32 30 L44 42 L32 54 L20 42 Z" fill="url(#core)"/><circle cx="32" cy="42" r="4"/>""",
            "amulet", "амулет")
    )

    /** Сферы. Общая форма шара, но у каждой свой знак и свой цвет. */
    private fun orb(id: String, title: String, tint: String, deep: String, sigil: String, vararg keywords: String) =
        art(id, title, CURRENCY, tint, deep,
            """<circle cx="32" cy="33" r="17" fill="url(#core)"/><path d="M22 25 Q26 20 32 19"/>$sigil""", *keywords)

    private val currency = listOf(
        orb("currency-transmutation", "Сфера превращения", BLUE_T, BLUE_D,
            """<path d="M32 25 L39 38 L25 38 Z"/>""", "transmutation", "превращение"),
        orb("currency-augmentation", "Сфера улучшения", CYAN_T, CYAN_D,
            """<path d="M32 25 L32 41"/><path d="M24 33 L40 33"/>""", "augmentation", "улучшение"),
        orb("currency-alteration", "Сфера изменения", TEAL_T, TEAL_D,
            """<path d="M25 30 Q32 22 39 30"/><path d="M39 36 Q32 44 25 36"/><path d="M36 27 L40 30 L37 33"/><path d="M28 39 L24 36 L27 33"/>""",
            "alteration", "изменение"),
        orb("currency-alchemy", "Сфера алхимии", GREEN_T, GREEN_D,
            """<path d="M32 23 L42 41 L22 41 Z"/><path d="M26 34 L38 34"/>""", "alchemy", "алхимия"),
        orb("currency-chaos", "Сфера хаоса", PURPLE_T, PURPLE_D,
            """<path d="M39 29 Q37 22 30 25 Q21 29 24 38 Q28 46 37 43 Q43 40 42 34"/>""", "chaos", "хаос"),
        orb("currency-regal", "Королевская сфера", VIOLET_T, VIOLET_D,
            """<path d="M22 41 L24 25 L28 32 L32 23 L36 32 L40 25 L42 41 Z"/>""", "regal", "королевская"),
        orb("currency-exalted", "Высшая сфера", GOLD_T, GOLD_D,
            """<path d="M32 20 L35 30 L45 33 L35 36 L32 46 L29 36 L19 33 L29 30 Z"/>""", "exalted", "высшая"),
        orb("currency-scouring", "Сфера очищения", ASH_T, ASH_D,
            """<path d="M22 42 Q32 28 44 22"/><path d="M25 46 Q34 34 44 29"/><path d="M30 48 Q37 41 44 37"/>""", "scouring", "очищение"),
        orb("currency-annulment", "Сфера аннулирования", SILVER_T, SILVER_D,
            """<circle cx="32" cy="33" r="9"/><path d="M24 33 L40 33"/>""", "annulment", "аннулирование"),
        orb("currency-divine", "Божественная сфера", YELLOW_T, YELLOW_D,
            """<circle cx="32" cy="33" r="6"/><path d="M32 20 L32 24"/><path d="M32 42 L32 46"/><path d="M19 33 L23 33"/><path d="M41 33 L45 33"/><path d="M23 24 L26 27"/><path d="M41 42 L38 39"/><path d="M41 24 L38 27"/><path d="M23 42 L26 39"/>""",
            "divine", "божественная"),
        orb("currency-blessed", "Благословенная сфера", VIOLET_T, VIOLET_D,
            """<path d="M32 21 Q42 33 32 43 Q22 33 32 21 Z"/><path d="M28 35 Q32 39 36 35"/>""", "blessed", "благословенная"),
        orb("currency-fracturing", "Сфера раскола", ORANGE_T, ORANGE_D,
            """<path d="M22 27 L30 33 L26 40 L34 44"/><path d="M30 33 L41 26"/><path d="M34 44 L42 39"/>""", "fracturing", "раскол"),
        orb("currency-mirror", "Зеркало Каландры", SILVER_T, SILVER_D,
            """<path d="M32 17 L32 49"/><path d="M28 23 L22 33 L28 43"/><path d="M36 23 L42 33 L36 43"/>""", "mirror", "зеркало")
    )

    /** Характеристики: ресурсы, защита, урон, скорость, атрибуты, сопротивления. */
    private val stats = listOf(
        art("stat-life", "Здоровье", STAT, RED_T, RED_D,
            """<path d="M32 50 Q14 38 14 26 Q14 16 23 16 Q30 16 32 23 Q34 16 41 16 Q50 16 50 26 Q50 38 32 50 Z" fill="url(#core)"/>""",
            "life", "здоровье", "жизнь"),
        art("stat-mana", "Мана", STAT, BLUE_T, BLUE_D,
            """<path d="M32 12 Q48 32 44 42 Q40 52 32 52 Q24 52 20 42 Q16 32 32 12 Z" fill="url(#core)"/><path d="M26 40 Q30 46 36 44"/>""",
            "mana", "мана"),
        art("stat-energy-shield", "Энергощит", STAT, CYAN_T, CYAN_D,
            """<path d="M32 12 L48 21 L48 41 L32 50 L16 41 L16 21 Z"/><path d="M32 20 L40 25 L40 37 L32 42 L24 37 L24 25 Z" fill="url(#core)"/>""",
            "energy shield", "энергощит", "щит энергии"),
        art("stat-armour", "Броня", STAT, STEEL_T, STEEL_D,
            """<path d="M32 12 L48 18 Q48 40 32 52 Q16 40 16 18 Z" fill="url(#core)"/><path d="M20 25 L44 25"/><path d="M19 34 L45 34"/>""",
            "armour", "броня", "защита"),
        art("stat-evasion", "Уклонение", STAT, GREEN_T, GREEN_D,
            """<path d="M18 22 Q34 32 18 42"/><path d="M30 17 Q48 32 30 47"/><path d="M42 14 Q56 32 42 50"/>""",
            "evasion", "уклонение"),
        art("stat-block", "Блок", STAT, STEEL_T, STEEL_D,
            """<path d="M32 12 L48 19 Q48 40 32 52 Q16 40 16 19 Z"/><path d="M22 32 L42 32"/><path d="M26 24 L38 40"/>""",
            "block", "блок"),
        art("stat-physical-damage", "Физический урон", STAT, ASH_T, ASH_D,
            """<path d="M32 12 L36 26 L50 22 L40 32 L50 42 L36 38 L32 52 L28 38 L14 42 L24 32 L14 22 L28 26 Z" fill="url(#core)"/>""",
            "physical", "физический урон"),
        art("stat-fire-damage", "Урон огнём", STAT, ORANGE_T, ORANGE_D,
            """<path d="M32 10 Q40 22 38 28 Q44 26 44 34 Q44 48 32 52 Q20 48 20 34 Q20 24 28 18 Q30 24 28 28 Q34 22 32 10 Z" fill="url(#core)"/>""",
            "fire", "огонь"),
        art("stat-cold-damage", "Урон холодом", STAT, CYAN_T, CYAN_D,
            """<path d="M32 12 L32 52"/><path d="M15 22 L49 42"/><path d="M49 22 L15 42"/><path d="M27 17 L32 22 L37 17"/><path d="M27 47 L32 42 L37 47"/><path d="M16 29 L18 23 L24 25"/><path d="M48 35 L46 41 L40 39"/><path d="M48 29 L46 23 L40 25"/><path d="M16 35 L18 41 L24 39"/>""",
            "cold", "холод", "лёд"),
        art("stat-lightning-damage", "Урон молнией", STAT, YELLOW_T, YELLOW_D,
            """<path d="M37 10 L20 34 L30 34 L26 54 L44 28 L33 28 Z" fill="url(#core)"/>""",
            "lightning", "молния"),
        art("stat-chaos-damage", "Урон хаосом", STAT, PURPLE_T, PURPLE_D,
            """<path d="M32 11 L44 26 Q48 38 38 46 L32 52 L26 46 Q16 38 20 26 Z" fill="url(#core)"/><path d="M26 30 L32 36 L38 30"/><path d="M27 41 L37 41"/>""",
            "chaos", "хаос"),
        art("stat-elemental-damage", "Стихийный урон", STAT, GOLD_T, GOLD_D,
            """<path d="M32 8 Q37 17 35 21 Q39 19 39 24 Q39 31 32 33 Q25 31 25 24 Q25 19 30 16 Q31 19 30 21 Q34 17 32 8 Z" fill="url(#core)"/><path d="M18 38 L18 56"/><path d="M11 42 L25 52"/><path d="M25 42 L11 52"/><path d="M49 36 L41 48 L47 48 L44 57 L53 44 L47 44 Z" fill="url(#core)"/>""",
            "elemental", "стихийный"),
        art("stat-spell-damage", "Урон заклинаний", STAT, VIOLET_T, VIOLET_D,
            """<circle cx="32" cy="32" r="16"/><path d="M32 20 L35 29 L44 32 L35 35 L32 44 L29 35 L20 32 L29 29 Z" fill="url(#core)"/>""",
            "spell", "заклинание"),
        art("stat-damage", "Урон", STAT, RED_T, RED_D,
            """<path d="M16 48 L44 18"/><path d="M48 48 L20 18"/><path d="M40 14 L50 12 L48 22 Z" fill="url(#core)"/><path d="M24 14 L14 12 L16 22 Z" fill="url(#core)"/><path d="M14 44 L20 50"/><path d="M50 44 L44 50"/>""",
            "damage", "урон"),
        art("stat-attack-speed", "Скорость атаки", STAT, ORANGE_T, ORANGE_D,
            """<path d="M46 14 L28 34"/><path d="M24 30 L32 38"/><path d="M28 34 L17 46"/><path d="M38 20 Q50 24 48 38"/><path d="M33 13 Q47 15 50 27"/>""",
            "attack speed", "скорость атаки"),
        art("stat-cast-speed", "Скорость сотворения", STAT, VIOLET_T, VIOLET_D,
            """<path d="M44 20 Q24 16 22 32 Q20 46 34 48 Q46 48 46 38"/><path d="M39 12 L45 20 L36 23"/><path d="M52 28 l0 8"/><path d="M48 32 l8 0"/>""",
            "cast speed", "скорость заклинаний"),
        art("stat-movement-speed", "Скорость передвижения", STAT, GREEN_T, GREEN_D,
            """<path d="M28 16 L36 16 L35 34 Q35 38 40 41 L48 46 L48 52 L26 52 L26 38 Z" fill="url(#core)"/><path d="M11 24 L21 24"/><path d="M9 33 L19 33"/><path d="M13 42 L21 42"/>""",
            "movement", "скорость передвижения"),
        art("stat-critical", "Критический удар", STAT, YELLOW_T, YELLOW_D,
            """<circle cx="32" cy="32" r="12"/><path d="M32 8 L32 17"/><path d="M32 47 L32 56"/><path d="M8 32 L17 32"/><path d="M47 32 L56 32"/><path d="M32 24 L35 30 L41 32 L35 34 L32 40 L29 34 L23 32 L29 30 Z" fill="url(#core)"/>""",
            "critical", "крит"),
        art("stat-accuracy", "Меткость", STAT, TEAL_T, TEAL_D,
            """<circle cx="36" cy="27" r="14"/><circle cx="36" cy="27" r="7"/><circle cx="36" cy="27" r="2.6" fill="url(#core)"/><path d="M12 52 L33 31"/><path d="M28 30 L34 30 L34 36"/><path d="M12 52 L12 45"/><path d="M12 52 L19 52"/>""",
            "accuracy", "меткость"),
        art("stat-strength", "Сила", STAT, RED_T, RED_D,
            """<path d="M14 26 L14 38"/><path d="M50 26 L50 38"/><path d="M22 19 L22 45"/><path d="M42 19 L42 45"/><path d="M22 32 L42 32"/>""",
            "strength", "сила"),
        art("stat-dexterity", "Ловкость", STAT, GREEN_T, GREEN_D,
            """<path d="M52 12 L20 44"/><path d="M52 12 L38 14 L50 26 Z" fill="url(#core)"/><path d="M20 44 L14 42 L16 48 L22 50 L20 44 Z" fill="url(#core)"/><path d="M28 28 L36 36"/>""",
            "dexterity", "ловкость"),
        art("stat-intelligence", "Интеллект", STAT, BLUE_T, BLUE_D,
            """<path d="M32 10 L44 24 L38 50 L26 50 L20 24 Z" fill="url(#core)"/><path d="M20 24 L44 24"/><path d="M32 10 L26 24 L32 50"/><path d="M38 24 L32 50"/>""",
            "intelligence", "интеллект"),
        art("stat-attributes", "Атрибуты", STAT, GOLD_T, GOLD_D,
            """<circle cx="32" cy="20" r="7" fill="url(#core)"/><circle cx="19" cy="43" r="7" fill="url(#core)"/><circle cx="45" cy="43" r="7" fill="url(#core)"/><path d="M27 26 L24 36"/><path d="M37 26 L40 36"/><path d="M26 44 L38 44"/>""",
            "attributes", "атрибуты", "all attributes"),
        art("stat-fire-resistance", "Сопротивление огню", STAT, ORANGE_T, ORANGE_D,
            """<path d="M32 12 L48 18 Q48 40 32 52 Q16 40 16 18 Z"/><path d="M32 21 Q37 28 35 32 Q39 30 39 35 Q39 43 32 46 Q25 43 25 35 Q25 29 30 26 Q31 30 30 32 Q34 28 32 21 Z" fill="url(#core)"/>""",
            "fire resistance", "сопротивление огню"),
        art("stat-cold-resistance", "Сопротивление холоду", STAT, CYAN_T, CYAN_D,
            """<path d="M32 12 L48 18 Q48 40 32 52 Q16 40 16 18 Z"/><path d="M32 21 L32 45"/><path d="M22 27 L42 39"/><path d="M42 27 L22 39"/>""",
            "cold resistance", "сопротивление холоду"),
        art("stat-lightning-resistance", "Сопротивление молнии", STAT, YELLOW_T, YELLOW_D,
            """<path d="M32 12 L48 18 Q48 40 32 52 Q16 40 16 18 Z"/><path d="M36 20 L26 34 L32 34 L29 47 L39 31 L33 31 Z" fill="url(#core)"/>""",
            "lightning resistance", "сопротивление молнии"),
        art("stat-chaos-resistance", "Сопротивление хаосу", STAT, PURPLE_T, PURPLE_D,
            """<path d="M32 12 L48 18 Q48 40 32 52 Q16 40 16 18 Z"/><path d="M32 21 L39 31 Q41 39 32 46 Q23 39 25 31 Z" fill="url(#core)"/><path d="M28 34 L36 34"/>""",
            "chaos resistance", "сопротивление хаосу"),
        art("stat-all-resistance", "Все сопротивления", STAT, GOLD_T, GOLD_D,
            """<path d="M32 12 L48 18 Q48 40 32 52 Q16 40 16 18 Z"/><circle cx="26" cy="29" r="3" fill="url(#core)"/><circle cx="38" cy="29" r="3" fill="url(#core)"/><circle cx="32" cy="40" r="3" fill="url(#core)"/>""",
            "all resistance", "все сопротивления"),
        art("stat-life-regeneration", "Восстановление здоровья", STAT, GREEN_T, GREEN_D,
            """<path d="M32 48 Q16 37 16 26 Q16 17 24 17 Q30 17 32 23 Q34 17 40 17 Q48 17 48 26 Q48 37 32 48 Z" fill="url(#core)"/><path d="M32 24 L32 37"/><path d="M26 30 L38 30"/>""",
            "life regeneration", "реген здоровья"),
        art("stat-mana-regeneration", "Восстановление маны", STAT, BLUE_T, BLUE_D,
            """<path d="M34 20 Q47 36 43 44 Q39 52 33 52 Q25 52 22 44 Q19 36 34 20 Z" fill="url(#core)"/><path d="M14 32 Q13 13 33 11"/><path d="M27 8 L34 11 L31 18"/>""",
            "mana regeneration", "реген маны"),
        art("stat-leech", "Похищение", STAT, CRIMSON_T, CRIMSON_D,
            """<path d="M32 16 Q46 32 42 42 Q38 50 32 50 Q26 50 22 42 Q18 32 32 16 Z" fill="url(#core)"/><path d="M26 27 L28 35 L30 27"/><path d="M34 27 L36 35 L38 27"/>""",
            "leech", "похищение", "вампиризм"),
        art("stat-rarity", "Редкость находок", STAT, GOLD_T, GOLD_D,
            """<path d="M32 15 L45 26 L38 48 L26 48 L19 26 Z" fill="url(#core)"/><path d="M19 26 L45 26"/><path d="M32 15 L26 26 L32 48"/><path d="M38 26 L32 48"/><path d="M51 11 l0 7"/><path d="M47.5 14.5 l7 0"/>""",
            "rarity", "редкость"),
        art("stat-quantity", "Количество находок", STAT, GOLD_T, GOLD_D,
            """<path d="M16 20 Q16 14 32 14 Q48 14 48 20 Q48 26 32 26 Q16 26 16 20 Z" fill="url(#core)"/><path d="M16 20 L16 32 Q16 38 32 38 Q48 38 48 32 L48 20"/><path d="M16 32 L16 42 Q16 48 32 48 Q48 48 48 42 L48 32"/>""",
            "quantity", "количество"),
        art("stat-stun", "Оглушение", STAT, YELLOW_T, YELLOW_D,
            """<circle cx="32" cy="36" r="13" fill="url(#core)"/><path d="M24 30 Q30 23 38 29"/><path d="M17 12 l2 5 5 2 -5 2 -2 5 -2 -5 -5 -2 5 -2 z"/><path d="M45 14 l1.6 4 4 1.6 -4 1.6 -1.6 4 -1.6 -4 -4 -1.6 4 -1.6 z"/>""",
            "stun", "оглушение"),
        art("stat-energy", "Энергия", STAT, CYAN_T, CYAN_D,
            """<circle cx="32" cy="32" r="10" fill="url(#core)"/><circle cx="32" cy="32" r="16"/><path d="M32 8 L32 14"/><path d="M32 50 L32 56"/><path d="M8 32 L14 32"/><path d="M50 32 L56 32"/>""",
            "energy", "энергия"),
        art("stat-gold", "Золото", STAT, GOLD_T, GOLD_D,
            """<circle cx="32" cy="32" r="16" fill="url(#core)"/><circle cx="32" cy="32" r="11"/><path d="M27 26 L37 26"/><path d="M32 26 L32 40"/><path d="M27 40 L37 40"/>""",
            "gold", "золото", "деньги"),
        art("stat-experience", "Опыт", STAT, TEAL_T, TEAL_D,
            """<path d="M14 48 L24 48 L24 34 L14 34 Z"/><path d="M27 48 L37 48 L37 26 L27 26 Z"/><path d="M40 48 L50 48 L50 18 L40 18 Z" fill="url(#core)"/>""",
            "experience", "опыт"),
        art("stat-inventory", "Инвентарь", STAT, BRONZE_T, BRONZE_D,
            """<path d="M20 26 Q20 14 32 14 Q44 14 44 26 L46 48 Q46 52 42 52 L22 52 Q18 52 18 48 Z" fill="url(#core)"/><path d="M26 26 Q26 20 32 20 Q38 20 38 26"/><path d="M19 34 L45 34"/>""",
            "inventory", "инвентарь"),
        art("stat-aura", "Аура", STAT, GOLD_T, GOLD_D,
            """<circle cx="32" cy="32" r="6" fill="url(#core)"/><circle cx="32" cy="32" r="14"/><path d="M18 18 Q32 8 46 18"/><path d="M18 46 Q32 56 46 46"/>""",
            "aura", "аура"),
        art("stat-curse", "Проклятие", STAT, PURPLE_T, PURPLE_D,
            """<path d="M12 32 Q32 14 52 32 Q32 50 12 32 Z"/><circle cx="32" cy="32" r="6" fill="url(#core)"/><path d="M20 42 L16 50"/><path d="M32 46 L32 54"/><path d="M44 42 L48 50"/>""",
            "curse", "проклятие")
    )

    /** Происхождение модификатора: префикс, суффикс, врождённый, уникальный. */
    private val affixes = listOf(
        art("affix-prefix", "Префикс", AFFIX, TEAL_T, TEAL_D,
            """<path d="M40 16 L20 32 L40 48"/><path d="M47 16 L47 48"/>""", "prefix", "префикс"),
        art("affix-suffix", "Суффикс", AFFIX, VIOLET_T, VIOLET_D,
            """<path d="M24 16 L44 32 L24 48"/><path d="M17 16 L17 48"/>""", "suffix", "суффикс"),
        art("affix-implicit", "Врождённое свойство", AFFIX, ASH_T, ASH_D,
            """<path d="M12 25 L52 25"/><path d="M12 39 L52 39"/><circle cx="32" cy="32" r="4" fill="url(#core)"/>""",
            "implicit", "врождённый"),
        art("affix-unique", "Уникальное свойство", AFFIX, ORANGE_T, ORANGE_D,
            """<path d="M32 13 L36 26 L49 26 L38 34 L42 47 L32 39 L22 47 L26 34 L15 26 L28 26 Z" fill="url(#core)"/>""",
            "unique", "уникальный"),
        art("affix-corrupted", "Осквернение", AFFIX, RED_T, RED_D,
            """<path d="M32 10 L44 18 L44 40 L32 54 L20 40 L20 18 Z"/><path d="M26 18 L34 28 L28 34 L38 44"/>""",
            "corrupted", "осквернение"),
        art("affix-enchantment", "Чары", AFFIX, BLUE_T, BLUE_D,
            """<path d="M16 42 Q32 14 48 42"/><path d="M19 48 L19 42"/><path d="M45 48 L45 42"/><path d="M32 17 l0 8"/><path d="M28 21 l8 0"/><circle cx="23" cy="33" r="2" fill="url(#core)"/><circle cx="41" cy="33" r="2" fill="url(#core)"/>""",
            "enchantment", "чары"),
        art("affix-fractured", "Раскол", AFFIX, ORANGE_T, ORANGE_D,
            """<path d="M20 11 L30 28 L22 34 L36 40 L28 53"/><path d="M30 28 L46 20"/><path d="M36 40 L48 36"/>""",
            "fractured", "раскол"),
        art("affix-quality", "Качество", AFFIX, CYAN_T, CYAN_D,
            """<path d="M32 13 L45 32 L32 51 L19 32 Z" fill="url(#core)"/><path d="M32 24 L32 40"/><path d="M24 32 L40 32"/>""",
            "quality", "качество")
    )

    /** Редкости. Одна огранка, разное число искр и свой цвет. */
    private fun gem(id: String, title: String, tint: String, deep: String, pips: String, vararg keywords: String) =
        art(id, title, RARITY, tint, deep,
            """<path d="M32 12 L46 25 L38 50 L26 50 L18 25 Z" fill="url(#core)"/><path d="M18 25 L46 25"/><path d="M32 12 L26 25"/><path d="M38 25 L32 50"/>$pips""", *keywords)

    private val rarities = listOf(
        gem("rarity-normal", "Обычный", ASH_T, ASH_D, "", "normal", "обычный", "common"),
        gem("rarity-magic", "Магический", BLUE_T, BLUE_D, """<circle cx="32" cy="35" r="2.6"/>""", "magic", "магический", "uncommon"),
        gem("rarity-rare", "Редкий", YELLOW_T, YELLOW_D, """<circle cx="27" cy="35" r="2.6"/><circle cx="37" cy="35" r="2.6"/>""", "rare", "редкий"),
        gem("rarity-epic", "Эпический", VIOLET_T, VIOLET_D, """<circle cx="25" cy="34" r="2.4"/><circle cx="32" cy="38" r="2.4"/><circle cx="39" cy="34" r="2.4"/>""", "epic", "эпический"),
        gem("rarity-unique", "Уникальный", ORANGE_T, ORANGE_D, """<circle cx="25" cy="33" r="2.2"/><circle cx="32" cy="37" r="2.2"/><circle cx="39" cy="33" r="2.2"/><circle cx="32" cy="45" r="2.2"/>""", "unique", "уникальный", "legendary"),
        gem("rarity-mythical", "Мифический", CRIMSON_T, CRIMSON_D, """<circle cx="24" cy="33" r="2"/><circle cx="31" cy="31" r="2"/><circle cx="38" cy="33" r="2"/><circle cx="28" cy="41" r="2"/><circle cx="35" cy="41" r="2"/>""", "mythical", "мифический")
    )

    /** Дерево пассивных умений. */
    private val passives = listOf(
        art("passive-origin", "Начало дерева", PASSIVE, GOLD_T, GOLD_D,
            """<circle cx="32" cy="32" r="10" fill="url(#core)"/><circle cx="32" cy="32" r="18"/><path d="M32 6 L32 13"/><path d="M32 51 L32 58"/><path d="M6 32 L13 32"/><path d="M51 32 L58 32"/><path d="M15 15 L20 20"/><path d="M44 44 L49 49"/><path d="M49 15 L44 20"/><path d="M20 44 L15 49"/>""",
            "origin", "начало"),
        art("passive-small", "Малый узел", PASSIVE, ASH_T, ASH_D,
            """<circle cx="32" cy="32" r="9" fill="url(#core)"/><circle cx="32" cy="32" r="16"/>""", "small", "малый"),
        art("passive-notable", "Крупный узел", PASSIVE, BRONZE_T, BRONZE_D,
            """<path d="M32 12 L48 22 L48 42 L32 52 L16 42 L16 22 Z"/><path d="M32 22 L42 32 L32 42 L22 32 Z" fill="url(#core)"/>""",
            "notable", "крупный"),
        art("passive-keystone", "Ключевой узел", PASSIVE, VIOLET_T, VIOLET_D,
            """<path d="M22 12 L42 12 L52 22 L52 42 L42 52 L22 52 L12 42 L12 22 Z"/><circle cx="32" cy="28" r="6" fill="url(#core)"/><path d="M29 33 L28 44 L36 44 L35 33"/>""",
            "keystone", "ключевой")
    )

    /** Поход и бой. */
    private val combat = listOf(
        art("combat-battle", "Бой", COMBAT, STEEL_T, STEEL_D,
            """<path d="M16 48 L42 20"/><path d="M48 48 L24 21"/><path d="M38 13 Q52 17 48 30 Q42 24 35 19 Z" fill="url(#core)"/><path d="M20 11 L31 14 L28 25 Z" fill="url(#core)"/>""",
            "battle", "бой"),
        art("combat-monster", "Монстр", COMBAT, GREEN_T, GREEN_D,
            """<path d="M16 28 Q16 14 26 16 L32 22 L38 16 Q48 14 48 28 Q48 44 32 52 Q16 44 16 28 Z" fill="url(#core)"/><circle cx="25" cy="31" r="2.6"/><circle cx="39" cy="31" r="2.6"/><path d="M24 41 L28 37 L32 41 L36 37 L40 41"/>""",
            "monster", "монстр"),
        art("combat-boss", "Босс", COMBAT, RED_T, RED_D,
            """<path d="M19 31 Q19 15 32 15 Q45 15 45 31 Q45 40 40 44 L40 50 L24 50 L24 44 Q19 40 19 31 Z" fill="url(#core)"/><circle cx="26" cy="31" r="3.2"/><circle cx="38" cy="31" r="3.2"/><path d="M28 41 L32 45 L36 41"/><path d="M17 20 L10 9 L21 13"/><path d="M47 20 L54 9 L43 13"/>""",
            "boss", "босс"),
        art("combat-zone", "Зона", COMBAT, TEAL_T, TEAL_D,
            """<path d="M12 18 L26 12 L38 18 L52 12 L52 46 L38 52 L26 46 L12 52 Z"/><path d="M26 12 L26 46"/><path d="M38 18 L38 52"/><path d="M32 21 Q38 21 38 27 Q38 32 32 40 Q26 32 26 27 Q26 21 32 21 Z" fill="url(#core)"/>""",
            "zone", "зона", "поход"),
        art("combat-loot", "Добыча", COMBAT, GOLD_T, GOLD_D,
            """<path d="M12 28 Q12 16 32 16 Q52 16 52 28 L52 48 L12 48 Z" fill="url(#core)"/><path d="M12 32 L52 32"/><path d="M28 32 L28 42 L36 42 L36 32"/><circle cx="32" cy="37" r="2"/>""",
            "loot", "добыча", "лут"),
        art("combat-potion", "Зелье", COMBAT, RED_T, RED_D,
            """<path d="M26 10 L38 10 L38 24 L46 40 Q48 52 36 52 L28 52 Q16 52 18 40 L26 24 Z" fill="url(#core)"/><path d="M19 38 L45 38"/><path d="M24 10 L40 10"/>""",
            "potion", "зелье", "флакон"),
        art("combat-attack", "Атака", COMBAT, ORANGE_T, ORANGE_D,
            """<path d="M50 15 L30 34"/><path d="M26 30 L34 38"/><path d="M30 34 L16 48"/><path d="M45 6 l2 6 6 2 -6 2 -2 6 -2 -6 -6 -2 6 -2 z" fill="url(#core)"/>""",
            "attack", "атака"),
        art("combat-power", "Мощный удар", COMBAT, YELLOW_T, YELLOW_D,
            """<path d="M45 17 L28 34"/><path d="M24 30 L32 38"/><path d="M28 34 L16 46"/><path d="M36 12 Q50 16 52 30"/><path d="M33 6 Q52 11 55 31"/>""",
            "power", "мощный удар"),
        art("combat-guard", "Защита", COMBAT, STEEL_T, STEEL_D,
            """<path d="M32 12 L48 19 Q48 40 32 52 Q16 40 16 19 Z" fill="url(#core)"/><path d="M11 26 L20 26"/><path d="M11 36 L20 36"/><path d="M53 26 L44 26"/><path d="M53 36 L44 36"/>""",
            "guard", "защита", "блок"),
        art("combat-flee", "Побег", COMBAT, ASH_T, ASH_D,
            """<path d="M16 12 L33 12 L33 52 L16 52"/><path d="M25 32 L50 32"/><path d="M42 24 L50 32 L42 40"/>""",
            "flee", "побег"),
        art("combat-victory", "Победа", COMBAT, GOLD_T, GOLD_D,
            """<path d="M32 12 L35 23 L46 23 L37 30 L40 41 L32 34 L24 41 L27 30 L18 23 L29 23 Z" fill="url(#core)"/><path d="M17 35 Q13 47 32 53 Q51 47 47 35"/>""",
            "victory", "победа"),
        art("combat-defeat", "Поражение", COMBAT, ASH_T, ASH_D,
            """<path d="M32 10 L32 40"/><path d="M24 20 L40 20"/><path d="M26 44 L38 44"/><path d="M12 54 L23 46 L32 53 L41 46 L52 54"/>""",
            "defeat", "поражение")
    )

    /** Предметы и элементы интерфейса. */
    private val items = listOf(
        art("item-generic", "Предмет", ITEM, BRONZE_T, BRONZE_D,
            """<path d="M22 22 L42 22 L48 50 L16 50 Z" fill="url(#core)"/><path d="M26 22 Q26 12 32 12 Q38 12 38 22"/><path d="M17 34 L47 34"/>""",
            "item", "предмет"),
        art("item-map", "Карта", ITEM, TEAL_T, TEAL_D,
            """<path d="M12 18 L26 12 L38 18 L52 12 L52 46 L38 52 L26 46 L12 52 Z" fill="url(#core)"/><path d="M26 12 L26 46"/><path d="M38 18 L38 52"/>""",
            "map", "карта"),
        art("item-scroll", "Свиток", ITEM, BRONZE_T, BRONZE_D,
            """<path d="M22 16 L46 16 L46 48 L22 48 Z" fill="url(#core)"/><path d="M22 16 Q15 16 15 21 Q15 26 22 26"/><path d="M46 48 Q53 48 53 43 Q53 38 46 38"/><path d="M28 27 L40 27"/><path d="M28 34 L40 34"/><path d="M28 41 L37 41"/>""",
            "scroll", "свиток"),
        art("item-gem", "Самоцвет", ITEM, GREEN_T, GREEN_D,
            """<path d="M32 10 L48 26 L38 52 L26 52 L16 26 Z" fill="url(#core)"/><path d="M16 26 L48 26"/><path d="M32 10 L26 26"/><path d="M32 10 L38 26"/><path d="M26 26 L32 52"/><path d="M38 26 L32 52"/>""",
            "gem", "самоцвет"),
        art("item-flask", "Флакон", ITEM, CRIMSON_T, CRIMSON_D,
            """<path d="M24 12 L40 12 L40 22 Q52 34 48 46 Q46 52 38 52 L26 52 Q18 52 16 46 Q12 34 24 22 Z" fill="url(#core)"/><path d="M17 40 Q32 34 47 40"/><circle cx="27" cy="45" r="2"/><circle cx="36" cy="46" r="2.6"/>""",
            "flask", "флакон")
    )

    private val ui = listOf(
        art("ui-character", "Герой", UI, GOLD_T, GOLD_D,
            """<circle cx="32" cy="22" r="10" fill="url(#core)"/><path d="M14 52 Q14 34 32 34 Q50 34 50 52"/>""", "character", "герой"),
        art("ui-craft", "Ремесло", UI, BRONZE_T, BRONZE_D,
            """<path d="M14 32 L46 32 Q42 40 34 40 L34 45 L44 51 L18 51 L26 45 L26 40 Q18 40 14 32 Z" fill="url(#core)"/><path d="M30 24 L44 15"/><path d="M42 10 L53 17 L49 24 L38 17 Z" fill="url(#core)"/>""",
            "craft", "ремесло", "крафт"),
        art("ui-stash", "Хранилище", UI, BRONZE_T, BRONZE_D,
            """<path d="M12 16 L52 16 L52 48 L12 48 Z"/><path d="M12 32 L52 32"/><path d="M32 16 L32 48"/><path d="M19 24 L25 24"/><path d="M39 24 L45 24"/><path d="M19 40 L25 40"/><path d="M39 40 L45 40"/>""",
            "stash", "хранилище"),
        art("ui-catalog", "Каталог", UI, BRONZE_T, BRONZE_D,
            """<path d="M12 16 Q22 12 32 18 Q42 12 52 16 L52 48 Q42 44 32 50 Q22 44 12 48 Z" fill="url(#core)"/><path d="M32 18 L32 50"/><path d="M18 26 L27 29"/><path d="M37 29 L46 26"/>""",
            "catalog", "каталог"),
        art("ui-unknown", "Без иконки", UI, ASH_T, ASH_D,
            """<path d="M32 10 L52 32 L32 54 L12 32 Z"/><path d="M26 27 Q26 18 32 18 Q38 18 38 25 Q38 31 32 33 L32 38"/><circle cx="32" cy="44" r="2.4" fill="url(#core)"/>""",
            "unknown", "неизвестно")
    )

    val all: List<IconArt> = weapons + armour + jewellery + currency + stats + affixes + rarities + passives + combat + items + ui
}
