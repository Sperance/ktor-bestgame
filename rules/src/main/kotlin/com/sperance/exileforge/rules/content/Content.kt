package com.sperance.exileforge.rules.content

import com.sperance.exileforge.rules.ContentException
import com.sperance.exileforge.rules.RulesJson
import com.sperance.exileforge.rules.fail
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.table.TableKind
import com.sperance.exileforge.rules.table.TableSet
import com.sperance.exileforge.rules.table.TablesFile
import com.sperance.exileforge.rules.table.Weighted
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Файлы контента - они же чанки клиента: имя файла и есть имя чанка. */
object ContentFiles {
    const val STATS = "stats.json"
    const val MODIFIERS = "modifiers.json"
    const val TABLES = "tables.json"
    const val EQUIPMENT = "equipment.json"
    const val ITEMS = "items.json"
    const val CAMPAIGN = "campaign.json"
    const val ATLAS = "atlas.json"
    const val TREE = "tree.json"
    const val CLASSES = "classes.json"
    const val SKILLS = "skills.json"
    const val ESSENCES = "essences.json"
    const val POWERS = "powers.json"
    const val PROFESSIONS = "professions.json"
    const val RULES = "rules.json"
    const val ACHIEVEMENTS = "achievements.json"
    const val PETS = "pets.json"

    val ALL = listOf(STATS, MODIFIERS, TABLES, EQUIPMENT, ITEMS, CAMPAIGN, ATLAS, TREE, CLASSES, SKILLS, ESSENCES, POWERS, PROFESSIONS, RULES, ACHIEVEMENTS, PETS)
}

/** Рецепт верстака: верстачное описание в одном тире и его цена; выводится из CRAFTED-вариантов и лестницы цен правил. */
@Serializable
data class BenchRecipe(
    val code: String,
    val modifier: String,
    val tier: Int,
    val source: Source,
    val group: String,
    val values: List<Range>,
    val orb: Orb,
    val amount: Long,
    val slots: List<Slot> = emptyList(),
) {
    fun fits(slot: Slot): Boolean = slots.isEmpty() || slot in slots
}

/** Все файлы контента, прочитанные и разобранные; [hashes] - отпечаток каждого файла как он отдан. */
class Content(
    val stats: StatsFile,
    val modifiers: ModifiersFile,
    val tables: TablesFile,
    val equipment: EquipmentFile,
    val items: ItemsFile,
    val campaign: CampaignFile,
    val atlas: AtlasTree,
    val tree: TreeFile,
    val classes: ClassesFile,
    val skills: SkillBook,
    val essences: EssenceBook,
    val powers: PowerBook,
    val professions: CraftsFile,
    val rules: EngineRules,
    val achievements: AchievementsFile = AchievementsFile(),
    val pets: PetsFile = PetsFile(),
    val hashes: Map<String, String> = emptyMap(),
) {
    /** Отпечаток всего контента: по нему сервер и клиент узнают, что видят один мир. */
    val hash: String by lazy { sha256(ContentFiles.ALL.joinToString(",") { "$it=${hashes[it].orEmpty()}" }) }
}

/**
 * Загрузка контента из текста файлов ([read] отдаёт текст по имени) с проверкой каждого файла и
 * всех ссылок между ними. Битый файл - [ContentException] на старте, а не тихая дыра в игре.
 */
object ContentLoader {
    fun load(read: (String) -> String): ContentIndex {
        val texts = ContentFiles.ALL.associateWith(read)
        fun <T> parse(name: String, serializer: KSerializer<T>): T = try {
            RulesJson.decodeFromString(serializer, texts.getValue(name))
        } catch (e: ContentException) { throw e } catch (e: Exception) { fail("$name: ${e.message}") }

        val content = Content(
            stats = parse(ContentFiles.STATS, StatsFile.serializer()),
            modifiers = parse(ContentFiles.MODIFIERS, ModifiersFile.serializer()),
            tables = parse(ContentFiles.TABLES, TablesFile.serializer()),
            equipment = parse(ContentFiles.EQUIPMENT, EquipmentFile.serializer()),
            items = parse(ContentFiles.ITEMS, ItemsFile.serializer()),
            campaign = parse(ContentFiles.CAMPAIGN, CampaignFile.serializer()),
            atlas = parse(ContentFiles.ATLAS, AtlasTree.serializer()),
            tree = parse(ContentFiles.TREE, TreeFile.serializer()),
            classes = parse(ContentFiles.CLASSES, ClassesFile.serializer()),
            skills = parse(ContentFiles.SKILLS, SkillBook.serializer()),
            essences = parse(ContentFiles.ESSENCES, EssenceBook.serializer()),
            powers = parse(ContentFiles.POWERS, PowerBook.serializer()),
            professions = parse(ContentFiles.PROFESSIONS, CraftsFile.serializer()),
            rules = parse(ContentFiles.RULES, EngineRules.serializer()),
            achievements = parse(ContentFiles.ACHIEVEMENTS, AchievementsFile.serializer()),
            pets = parse(ContentFiles.PETS, PetsFile.serializer()),
            hashes = texts.mapValues { sha256(it.value) },
        )
        return ContentIndex(content).also { it.validate() }
    }
}

/**
 * Контент с индексами: описания по коду и семейству, лестницы тиров, таблицы, шаблоны, предметы,
 * монстры, зоны, графы атласа и дерева, рецепты верстака. Один экземпляр на версию контента.
 */
class ContentIndex(val content: Content) {
    val stats = StatRegistry(content.stats.stats)
    val rules: EngineRules get() = content.rules
    val campaign: CampaignFile get() = content.campaign
    val classes: ClassesFile get() = content.classes
    val skills: SkillBook get() = content.skills
    val skillRules = SkillRules(content.skills)
    val essences: EssenceBook get() = content.essences
    val powers: PowerBook get() = content.powers
    val professions: CraftsFile get() = content.professions
    val atlas: AtlasTree get() = content.atlas
    val achievements: AchievementsFile get() = content.achievements
    val pets: PetsFile get() = content.pets
    val hash: String get() = content.hash

    val families: Map<String, ModifierFamily> = (content.modifiers.families + content.equipment.templates.flatMap { it.uniqueFamilies() }).associateBy { it.code }
    val definitions: List<ModifierDef> = families.values.flatMap { it.definitions() }
    private val byCode: Map<String, ModifierDef> = definitions.associateBy { it.code }
    private val ladders: Map<String, TierLadder> = definitions.associate { it.code to TierLadder(it.tiers) }
    val monsterModifiers: List<ModifierDef> = definitions.filter { it.monster }

    val tables = TableSet(content.tables.tables)

    val templates: Map<String, ItemTemplate> = content.equipment.templates.associateBy { it.code }
    val templatesBySlot: Map<Slot, List<ItemTemplate>> = content.equipment.templates.groupBy { it.slot }
    val items: Map<String, Item> = content.items.items.associateBy { it.code }
    val itemsByCategory: Map<String, List<Item>> = content.items.items.groupBy { it.category }
    val monsters: Map<String, Monster> = content.campaign.monsters.associateBy { it.code }
    val zones: Map<String, Zone> = content.campaign.zones.associateBy { it.code }
    val world = WorldGraph(content.campaign.zones)
    val atlasGraph = AtlasGraph(content.atlas.nodes)
    val tree = TreeGraph(content.tree.nodes)

    /** Рецепты верстака: каждый CRAFTED-вариант на каждом тире лестницы цен. */
    val bench: List<BenchRecipe> = definitions.filter { it.crafted && it.affix }.flatMap { def ->
        def.tiers.indices.mapNotNull { index ->
            val cost = rules.bench.costs.getOrNull(index) ?: return@mapNotNull null
            BenchRecipe("${def.code}_T${index + 1}", def.code, index + 1, def.source, def.groupKey, def.tiers[index].values, cost.orb, cost.amount, def.slots)
        }
    }
    private val benchByCode: Map<String, BenchRecipe> = bench.associateBy { it.code }

    private val modifierPools = ConcurrentHashMap<List<String>, List<Weighted<ModifierDef>>>()
    private val affixPools = ConcurrentHashMap<List<String>, List<Weighted<ModifierDef>>>()
    private val templatePools = ConcurrentHashMap<List<String>, List<Weighted<ItemTemplate>>>()

    fun modifier(code: String): ModifierDef? = byCode[code]
    fun template(code: String): ItemTemplate? = templates[code]
    fun item(code: String): Item? = items[code]
    fun monster(code: String): Monster? = monsters[code]
    fun zone(code: String): Zone? = zones[code]
    fun heroClass(code: String): HeroClass? = classes.resolved(code)
    fun recipe(code: String): BenchRecipe? = benchByCode[code]
    fun essence(itemCode: String): Essence? = essences.essences[itemCode]
    fun orb(itemCode: String): Orb? = items[itemCode]?.takeIf { it.category == Item.CURRENCY }?.let { Orb.of(it.code) }

    /** Описания таблиц [tags] с весами, в порядке описаний; один раз на набор тегов. */
    fun modifierPool(tags: List<String>): List<Weighted<ModifierDef>> = modifierPools.getOrPut(tags) { tables.of(definitions, tags) { it.code } }

    /** То же, только аффиксы с тирами и не верстачные - всё, что катают сферы. */
    fun affixPool(tags: List<String>): List<Weighted<ModifierDef>> = affixPools.getOrPut(tags) { modifierPool(tags).filter { it.value.affix && !it.value.crafted && it.value.rolls } }

    fun templatePool(tags: List<String>): List<Weighted<ItemTemplate>> = templatePools.getOrPut(tags) { tables.of(content.equipment.templates, tags) { it.code } }

    /** Шаблоны таблиц, доступные по требуемому уровню на [level]: что падает на локации этого уровня. */
    fun templatePoolUpTo(tags: List<String>, level: Int): List<Weighted<ItemTemplate>> = templatePool(tags).filter { it.value.requiredLevel <= level }

    /** Взвешенный ролл тира описания [code] на уровне [level]: номер и тир; ни одного открытого - самый слабый. */
    fun rollTier(code: String, level: Int, dice: Dice): Pair<Int, Tier>? = ladders[code]?.pick(level) { total -> dice.nextLong(total) }

    /** Места аффиксов копии редкости [rarity] в слоте [slot]. */
    fun limits(rarity: Rarity, slot: Slot): RarityLimits = rules.limits(rarity, slot)

    fun exists(kind: TableKind, code: String): Boolean = when (kind) {
        TableKind.MODIFIER -> code in byCode
        TableKind.TEMPLATE -> code in templates
        TableKind.ITEM -> code in items
        TableKind.MONSTER -> code in monsters
        TableKind.VALUE -> true
        TableKind.LOOT -> false
    }

    fun validate() {
        stats.validate()
        rules.validate()
        families.values.forEach { family -> family.problem()?.let { fail("modifiers: $it") } }
        if (definitions.size != byCode.size) fail("modifiers: duplicate definition codes")
        definitions.forEach { def ->
            def.effects.forEach { effect ->
                if (effect.stat !in stats) fail("modifiers: ${def.code} names stat ${effect.stat}")
                effect.perStat?.let { source ->
                    if (source !in stats) fail("modifiers: ${def.code} converts from unknown $source")
                    if (stats.order(source) >= stats.order(effect.stat)) fail("modifiers: ${def.code} converts $source into ${effect.stat} counted earlier")
                }
            }
        }
        tables.validateRefs(::exists)
        content.equipment.templates.let { list ->
            if (list.map { it.code }.toSet().size != list.size) fail("equipment: duplicate codes")
            list.forEach { template ->
                if (template.slot == Slot.RING_2 || template.slot == Slot.FLASK_2 || template.slot == Slot.FLASK_3) fail("equipment: ${template.code} sits in a worn-only slot")
                template.fixed.forEach { code -> if (code !in byCode) fail("equipment: ${template.code} fixes unknown $code") }
                template.base.forEach { line ->
                    val def = byCode[line.code] ?: fail("equipment: ${template.code} bases on unknown ${line.code}")
                    if (line.values.size != def.effects.size) fail("equipment: base ${line.code} of ${template.code}")
                }
                template.tables.forEach { tag -> if (tables.kind(tag) != TableKind.MODIFIER) fail("equipment: ${template.code} rolls from unknown table $tag") }
                if (template.unique != template.lines.isNotEmpty()) fail("equipment: lines of ${template.code}")
                if (template.kind == TemplateKind.WEAPON && template.weaponType == null) fail("equipment: weapon type of ${template.code}")
                if (template.level < 1 || template.requiredLevel < 1) fail("equipment: level of ${template.code}")
            }
        }
        content.items.items.let { list -> if (list.map { it.code }.toSet().size != list.size) fail("items: duplicate codes") }
        Orb.entries.forEach { if (items[it.name]?.category != Item.CURRENCY) fail("items: orb ${it.name} has no item") }
        essences.validate(::modifier)
        essences.essences.keys.forEach { if (items[it]?.category != Item.ESSENCE) fail("items: essence $it has no item") }
        skills.validate(stats, classes.classes.map { it.code })
        skills.skills.forEach { if (items[it.book]?.category != Item.BOOK) fail("items: book of ${it.code} has no item") }
        powers.validate(stats)
        tree.validate(::modifier)
        classes.validate(stats, ::modifier, tree)
        classes.classes.filter { it.weapon.isNotBlank() }.forEach { heroClass ->
            val weapon = template(heroClass.weapon) ?: fail("classes: weapon ${heroClass.weapon} of ${heroClass.code}")
            if (weapon.weaponType == null || weapon.unique || weapon.requiredLevel > 1) fail("classes: ${heroClass.weapon} of ${heroClass.code} is no plain first-level weapon")
        }
        classes.classes.forEach { heroClass ->
            val armour = heroClass.armour.map { code -> template(code) ?: fail("classes: armour $code of ${heroClass.code}") }
            if (armour.any { it.kind != TemplateKind.ARMOR || it.unique || it.requiredLevel > 1 } || armour.map { it.slot }.toSet().size != armour.size)
                fail("classes: armour of ${heroClass.code} is not plain first-level armour, one to a slot")
        }
        atlasGraph.validate(atlas, stats, ::modifier)
        professions.validate({ it in items }, ::template, { it in zones }, { classes.heroClass(it) != null })
        rules.bench.costs.forEach { if (items[it.orb.name] == null) fail("rules: bench orb ${it.orb}") }
        achievements.validate()
        pets.validate(this)
        CampaignValidator(this).validate()
    }
}

fun sha256(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

/** Проверка кампании: мир, монстры, таблицы, правила - как их читает движок. */
private class CampaignValidator(private val index: ContentIndex) {
    private val content = index.campaign
    private val stats = index.stats

    fun validate() {
        (content.defaults.keys + content.growth.keys).forEach(::stat)
        if (content.rarities.map { it.rarity }.toSet() != MonsterRarity.entries.toSet()) fail("campaign: rarities")
        content.rarities.forEach { rarity ->
            if (rarity.modifiers.size != 2 || rarity.modifiers[0] > rarity.modifiers[1] || rarity.modifierPower <= 0 || rarity.statScale < 0) fail("campaign: rarity ${rarity.rarity}")
            rarity.lines.forEach { line -> if (index.modifier(line.code)?.effects?.size != line.values.size) fail("campaign: line ${line.code} of ${rarity.rarity}") }
        }
        if (index.tables.kind(content.rarityTable) != TableKind.VALUE) fail("campaign: rarity table ${content.rarityTable}")
        index.monsterModifiers.forEach { if (it.minRarity == MonsterRarity.NORMAL) fail("campaign: modifier ${it.code} on a normal monster") }
        content.zones.forEach { zone ->
            val pool = index.modifierPool(zone.tables).map { it.value }.filter { it.monster }
            content.rarities.filter { it.modifiers[1] > 0 }.forEach { rarity ->
                if (pool.count { (it.minRarity ?: MonsterRarity.MAGIC) <= rarity.rarity } < rarity.modifiers[1]) fail("campaign: pool of ${rarity.rarity} on ${zone.code}")
            }
        }
        content.bosses.let { rule ->
            if (rule.rolls.size != 2 || rule.rolls[0] < 0 || rule.rolls[0] > rule.rolls[1] || rule.tierReach < 0 || rule.modifiers.isEmpty()) fail("campaign: bosses rolls")
            if (index.modifierPool(rule.modifiers).size < rule.rolls[1]) fail("campaign: boss pool")
            if (rule.respawnHours <= 0 || rule.uniqueChance !in 0.0..1.0 || rule.ownUniqueChance !in 0.0..1.0 || rule.tables.isEmpty()) fail("campaign: bosses")
            rule.tables.forEach(::templateTable)
        }
        val monsterCodes = content.monsters.map { it.code }
        if (monsterCodes.toSet().size != monsterCodes.size) fail("campaign: monster codes")
        content.monsters.forEach { monster ->
            monster.stats.keys.forEach(::stat)
            lootTable(monster.loot, monster.code)
            monster.skills.forEach { if (index.skills.monsterByCode[it] == null) fail("campaign: skill $it of ${monster.code}") }
            if (monster.skills.isNotEmpty() && (monster.stats[CoreStat.MANA.code] ?: 0.0) <= 0) fail("campaign: skilled ${monster.code} without mana")
            if ((monster.stats[CoreStat.ATTACK_MAGICAL.code] ?: 0.0) > 0 && (monster.stats[CoreStat.MANA.code] ?: 0.0) <= 0) fail("campaign: caster ${monster.code} without mana")
            monster.tables.forEach(::templateTable)
        }
        validateCombat(content.combat)
        val codes = content.zones.map { it.code }
        if (codes.toSet().size != codes.size) fail("campaign: map codes")
        validateWorld()
        val bossModifiers = index.modifierPool(content.bosses.modifiers).map { it.value.code }.toSet()
        content.zones.forEach { zone ->
            if (zone.monsters.size !in 2..4) fail("campaign: monsters of ${zone.code}")
            zone.monsters.forEach { if (it !in index.monsters) fail("campaign: monster $it") }
            if (zone.count.size != 2 || zone.count[0] < 1 || zone.count[0] > zone.count[1]) fail("campaign: count of ${zone.code}")
            if (zone.light <= 0) fail("campaign: light of ${zone.code}")
            if (zone.size !in 32..160) fail("campaign: size of ${zone.code}")
            if (zone.tables.isEmpty()) fail("campaign: modifier tables of ${zone.code}")
            zone.tables.forEach { if (index.tables.kind(it) != TableKind.MODIFIER) fail("campaign: table $it of ${zone.code}") }
            lootTable(zone.chestLoot, zone.code)
            if (index.monster(zone.boss)?.boss != true) fail("campaign: boss of ${zone.code}")
            if (zone.monsters.any { index.monster(it)?.boss == true }) fail("campaign: boss among monsters of ${zone.code}")
            if (index.monster(zone.corrupted)?.corrupted != true) fail("campaign: corrupted guardian of ${zone.code}")
            if (zone.monsters.any { index.monster(it)?.corrupted == true }) fail("campaign: corrupted guardian among monsters of ${zone.code}")
            if (index.template("MAP_${zone.code}")?.slot != Slot.MAP) fail("campaign: no map template for ${zone.code}")
        }
        val finaleBosses = content.zones.filter { it.finale }.map { it.boss }.toSet()
        val leaders = content.abyss?.leaders.orEmpty().toSet()
        content.monsters.filter { it.boss && it.code !in leaders }.forEach { boss ->
            if ((boss.code in finaleBosses && boss.tables.isEmpty()) || boss.fixed.isEmpty() || boss.fixed.any { it !in bossModifiers }) fail("campaign: boss ${boss.code}")
        }
        val bosses = content.zones.map { it.boss }
        if (bosses.toSet().size != bosses.size) fail("campaign: a boss guards two zones")
        content.monsters.filter { it.corrupted }.forEach { guardian ->
            if (guardian.boss) fail("campaign: corrupted boss ${guardian.code}")
            if (guardian.tables.isNotEmpty() || guardian.fixed.any { it !in bossModifiers }) fail("campaign: corrupted ${guardian.code}")
        }
        if (content.services.summonPerLevel <= 0) fail("campaign: services")
        content.fountains.let { if (it.count.size != 2 || it.count[0] < 0 || it.count[0] > it.count[1] || it.heal !in 0.0..100.0) fail("campaign: fountains") }
        content.corruption.let { rule -> if (rule.chance !in 0.0..1.0 || rule.uniqueChance !in 0.0..1.0 || rule.tables.isEmpty()) fail("campaign: corruption"); rule.tables.forEach(::templateTable) }
        content.vaal.let { rule ->
            val pool = index.modifierPool(listOf(rule.pool))
            if (rule.mods.size != 2 || rule.mods[0] < 1 || rule.mods[0] > rule.mods[1] || rule.mods[1] > pool.size || rule.power <= 0 || rule.reward < 0 || rule.perMod < 0) fail("campaign: vaal")
        }
        content.maps.let { rule ->
            if (listOf(rule.dropChance, rule.bossChance, rule.nextChance).any { it !in 0.0..1.0 }) fail("campaign: maps")
            if (index.tables.kind(rule.rarities) != TableKind.VALUE) fail("campaign: maps.rarities")
            rule.risk.forEach { (name, weight) -> stat(name); if (weight <= 0) fail("campaign: maps.risk $name") }
            if (rule.rarityBonus.values.any { it < 0 }) fail("campaign: maps.rarityBonus")
        }
        content.chests.let { if (it.count.size != 2 || it.count[0] < 0 || it.count[0] > it.count[1] || it.refreshHours <= 0 || it.quantity <= 0) fail("campaign: chests") }
        content.abyss?.let(::validateAbyss)
        content.desecration?.let(::validateDesecration)
        val forms = content.monsters.map { it.form }.toSet()
        content.behaviour.forms.keys.forEach { if (it !in forms) fail("campaign: behaviour of form $it") }
        (listOf(content.behaviour.default, content.bosses.behaviour) + content.behaviour.forms.values + content.monsters.mapNotNull { it.behaviour }).forEach(::validateBehaviour)
        index.essences.crystals.modifiers.forEach { if (index.tables.kind(it) != TableKind.MODIFIER) fail("essences: crystal table $it") }
        if (index.tables.kind(index.essences.crystals.vaal) != TableKind.VALUE) fail("essences: vaal table")
    }

    private fun stat(name: String) { if (name !in stats) fail("campaign: stat $name") }
    private fun templateTable(tag: String) { if (index.tables.kind(tag) != TableKind.TEMPLATE) fail("campaign: template table $tag") }
    private fun lootTable(tag: String, owner: String) { if (index.tables.kind(tag) != TableKind.LOOT) fail("campaign: loot table $tag of $owner") }

    private fun validateWorld() {
        val world = content.world
        if (world.width <= 0 || world.height <= 0) fail("world: size")
        fun inside(x: Int, y: Int) = x in 0..world.width && y in 0..world.height
        if (content.regions.isEmpty()) fail("world: regions")
        val order = content.regions.withIndex().associate { (i, region) -> region.code to i }
        if (order.size != content.regions.size) fail("world: region codes")
        val regionOf = content.regions.flatMap { region -> region.zones.map { it.code to order.getValue(region.code) } }.toMap()
        val starts = content.zones.filter { it.from.isEmpty() }
        if (starts.size != 1 || regionOf.getValue(starts[0].code) != 0) fail("world: one start in the first region")
        content.regions.forEach { region ->
            if (region.zones.isEmpty()) fail("world: zones of ${region.code}")
            if (!inside(region.label.x, region.label.y)) fail("world: label of ${region.code}")
            val finale = region.zones.filter { it.finale }
            if (finale.size != 1 || finale[0].level != region.zones.maxOf { it.level }) fail("world: finale of ${region.code}")
        }
        content.zones.forEach { zone ->
            if (!inside(zone.x, zone.y)) fail("world: place of ${zone.code}")
            if (zone.from.toSet().size != zone.from.size) fail("world: links of ${zone.code}")
            zone.from.forEach { code ->
                val source = index.zone(code) ?: fail("world: link $code of ${zone.code}")
                if (source.level >= zone.level) fail("world: link $code of ${zone.code} goes down")
                val step = regionOf.getValue(zone.code) - regionOf.getValue(code)
                if (step != 0 && !(step == 1 && source.finale)) fail("world: link $code of ${zone.code} skips a region")
            }
        }
        index.world.unreachable().takeIf { it.isNotEmpty() }?.let { fail("world: unreachable $it") }
    }

    private fun validateDesecration(rule: DesecrationRule) {
        if (rule.count.size != 2 || rule.count[0] < 0 || rule.count[0] > rule.count[1] || rule.radius <= 0 || rule.trail < 0 || rule.growth < 0 || rule.minLevel < 1) fail("desecration: rule")
        if (rule.kinds.isEmpty() || rule.kinds.map { it.code }.toSet().size != rule.kinds.size) fail("desecration: kinds")
        if (rule.kinds.map { it.group }.toSet() != DesecrationGroup.entries.toSet()) fail("desecration: groups")
        rule.kinds.forEach { kind ->
            if (kind.weight <= 0 || kind.lines.isEmpty() || kind.lines.keys.any { it !in DesecrationRule.HERO_LINES }) fail("desecration: kind ${kind.code}")
        }
        stat(DesecrationRule.GUARD)
    }

    private fun validateAbyss(rule: AbyssRule) {
        val zoned = content.zones.flatMap { it.monsters + it.boss + it.corrupted }.toSet()
        if (rule.chance !in 0.0..100.0 || rule.minLevel < 1 || rule.refreshHours <= 0) fail("abyss: rule")
        if (rule.waves.isEmpty() || rule.hoard.size != rule.waves.size) fail("abyss: depths")
        if (rule.depth.size != 2 || rule.depth[0] < 1 || rule.depth[0] > rule.depth[1] || rule.depth[1] > rule.waves.size) fail("abyss: depth")
        if (rule.monsters.isEmpty()) fail("abyss: monsters")
        rule.monsters.forEach { code -> val monster = index.monster(code) ?: fail("abyss: monster $code"); if (monster.boss || monster.corrupted || code in zoned) fail("abyss: monster $code") }
        rule.waves.forEachIndexed { i, wave -> if (wave.count.size != 2 || wave.count[0] < 1 || wave.count[0] > wave.count[1] || wave.level < 0 || wave.magic < 0 || wave.rare < 0 || wave.magic + wave.rare > 100) fail("abyss: wave ${i + 1}") }
        rule.hoard.forEachIndexed { i, hoard ->
            if (hoard.items.size != 2 || hoard.items[0] < 0 || hoard.items[0] > hoard.items[1] || hoard.orbs.size != 2 || hoard.orbs[0] < 0 || hoard.orbs[0] > hoard.orbs[1] || hoard.rare !in 0.0..100.0 || hoard.unique !in 0.0..100.0 || hoard.experience < 0) fail("abyss: hoard ${i + 1}")
        }
        if (index.tables.kind(rule.orbs) != TableKind.ITEM) fail("abyss: orbs table")
        if (rule.uniques.isEmpty() || rule.tables.isEmpty() || rule.modifiers.isEmpty()) fail("abyss: tables")
        (rule.uniques + rule.tables).forEach(::templateTable)
        if (rule.rolls.size != 2 || rule.rolls[0] < 0 || rule.rolls[0] > rule.rolls[1] || rule.tierReach < 0) fail("abyss: rolls")
        val pool = index.modifierPool(rule.modifiers).map { it.value }
        content.rarities.filter { it.modifiers[1] > 0 }.forEach { rarity -> if (pool.count { (it.minRarity ?: MonsterRarity.MAGIC) <= rarity.rarity } < rarity.modifiers[1]) fail("abyss: pool of ${rarity.rarity}") }
        if (pool.size < rule.rolls[1]) fail("abyss: leader pool")
        val codes = pool.map { it.code }.toSet()
        rule.leaders.forEach { code ->
            val leader = index.monster(code) ?: fail("abyss: leader $code")
            if (!leader.boss || leader.corrupted || code in zoned || leader.fixed.isEmpty() || leader.fixed.any { it !in codes }) fail("abyss: leader $code")
        }
    }

    private fun validateBehaviour(rule: BehaviourRule) {
        if (rule.type !in BehaviourRule.types) fail("behaviour: type ${rule.type}")
        if (rule.chaseSpeed <= 0 || rule.wanderSpeed < 0 || rule.sight <= 0 || rule.giveUp <= 0 || rule.wanderRadius < 0 || rule.wake < 0) fail("behaviour: ${rule.type}")
        if (rule.type in setOf("AMBUSH", "SLEEP") && rule.wake <= 0) fail("behaviour: ${rule.type} without wake")
        if (rule.type in setOf("WANDER", "PATROL") && (rule.wanderSpeed <= 0 || rule.wanderRadius <= 0)) fail("behaviour: ${rule.type} standing still")
    }

    private fun validateCombat(rules: CombatRules) {
        val ailments = stats.ailments()
        val damage = stats.stats.map { it.code }.filter { it.startsWith("STOCK_ATTACK_") }.toSet()
        fun positive(value: Double, name: String) { if (value <= 0) fail("combat: $name") }
        fun percent(value: Double, name: String) { if (value !in 0.0..100.0) fail("combat: $name") }
        percent(rules.variance, "variance"); percent(rules.resistCap, "resistCap")
        rules.ceilings.all.forEach { percent(it.base, "ceiling ${it.raise}"); percent(it.hard, "ceiling ${it.raise}"); if (it.hard < it.base || it.raise !in stats) fail("combat: ceiling ${it.raise}") }
        percent(rules.resistHardCap, "resistHardCap"); percent(rules.ailmentDurationCap, "ailmentDurationCap")
        if (rules.resistHardCap < rules.resistCap) fail("combat: resistHardCap")
        positive(rules.unarmed.damage, "unarmed.damage"); positive(rules.unarmed.speed, "unarmed.speed")
        percent(rules.critical.chance, "critical.chance"); if (rules.critical.multiplier < 100) fail("combat: critical.multiplier")
        positive(rules.armour.factor, "armour.factor")
        positive(rules.evasion.base, "evasion.base")
        if (rules.evasion.perLevel < 0 || rules.stun.share < 0 || rules.stun.duration < 0) fail("combat: stun")
        if (rules.shield.rechargeDelay < 0 || rules.shield.rechargePerSecond < 0) fail("combat: shield")
        if (rules.retreat.delay < 0) fail("combat: retreat.delay")
        if (rules.death.fromLevel < 1) fail("combat: death.fromLevel")
        percent(rules.death.experienceShare, "death.experienceShare")
        rules.ailments.forEach { rule ->
            if (rule.ailment !in ailments) fail("combat: ailment ${rule.ailment}")
            if (rule.type !in damage) fail("combat: ailment ${rule.ailment} by ${rule.type}")
            percent(rule.chance, "ailment ${rule.ailment} chance"); percent(rule.threshold, "ailment ${rule.ailment} threshold")
            rule.heroChance?.let { percent(it, "ailment ${rule.ailment} heroChance") }
            if (rule.magnitude < 0 || rule.duration <= 0) fail("combat: ailment ${rule.ailment}")
        }
        if (rules.ailments.map { it.ailment }.toSet().size != rules.ailments.size) fail("combat: ailments")
    }
}
