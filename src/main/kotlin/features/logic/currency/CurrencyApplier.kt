package features.logic.currency

import application.enums.EnumCurrencyOrb
import application.enums.EnumCurrencyOrb.BLESSED_ORB
import application.enums.EnumCurrencyOrb.CHAOS_ORB
import application.enums.EnumCurrencyOrb.DIVINE_ORB
import application.enums.EnumCurrencyOrb.ELDER_ORB
import application.enums.EnumCurrencyOrb.EXALTED_ORB
import application.enums.EnumCurrencyOrb.FRACTURING_ORB
import application.enums.EnumCurrencyOrb.MIRROR_OF_KALANDRA
import application.enums.EnumCurrencyOrb.ORB_OF_ALCHEMY
import application.enums.EnumCurrencyOrb.ORB_OF_ALTERATION
import application.enums.EnumCurrencyOrb.ORB_OF_ANNULMENT
import application.enums.EnumCurrencyOrb.ORB_OF_AUGMENTATION
import application.enums.EnumCurrencyOrb.ORB_OF_CHANCE
import application.enums.EnumCurrencyOrb.ORB_OF_REGRET
import application.enums.EnumCurrencyOrb.ORB_OF_SCOURING
import application.enums.EnumCurrencyOrb.ORB_OF_TRANSMUTATION
import application.enums.EnumCurrencyOrb.REGAL_ORB
import application.enums.EnumCurrencyOrb.SHAPERS_ORB
import application.enums.EnumCurrencyOrb.VAAL_ORB
import application.enums.EnumEquipmentType
import application.enums.EnumInfluence
import application.enums.EnumModifierSource
import application.enums.EnumRarity
import base.exception.model.CurrencyExceptions
import extensions.RandomExt
import extensions.to1Digits
import extensions.randomExt
import extensions.weightedRandomExt
import features.caches.EquipmentCache
import features.data.equipment.equipment_data.Equipment
import features.data.inventory.CharacterEquipment
import features.logic.modifiers.Modifier
import features.logic.modifiers.ModifierDefinition
import features.logic.locale.LocaleKey
import features.logic.modifiers.ModifierRoller
import features.logic.pools.Pools
import config.CurrencySeeder
import org.bson.types.ObjectId
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Результат применения сферы.
 *
 * Текста здесь нет: сервер отдаёт ключ сообщения и его аргументы,
 * а собирает фразу клиент по своему файлу локализации.
 *
 * Аргумент, который сам является ключом (код предмета, значение
 * перечисления), клиент переводит; всё остальное - числа - подставляет
 * как есть. Правило одно: нашлось в словаре - перевести, нет - вставить.
 *
 * @property item предмет после применения
 * @property created новый предмет, если сфера его создала (Mirror of Kalandra)
 * @property messageKey ключ сообщения в локализации
 * @property messageArgs значения для {0}, {1}... в шаблоне сообщения
 */
data class CurrencyOutcome(
    val item: CharacterEquipment,
    val created: CharacterEquipment? = null,
    val messageKey: String,
    val messageArgs: List<String> = emptyList(),
)

/**
 * Применение валютных сфер POE к экземпляру предмета.
 *
 * Здесь только правила: что сфера требует от предмета и как его меняет.
 * Списание сферы и сохранение предмета - забота репозитория.
 *
 * Предмет меняется на месте, поэтому вызывающий должен передавать сюда
 * свежий документ и сохранять его сразу после.
 */
object CurrencyApplier : KoinComponent {

    private val equipmentCache: EquipmentCache by inject()

    /**
     * Шанс, что Orb of Chance выдаст уникальный предмет, в процентах.
     */
    private const val CHANCE_UNIQUE_PERCENT = 5
    /** Строк «Алхимия» на карте не больше (с 0.38.0). */
    private const val MAX_ALCHEMY_LINES = 3

    /**
     * Шансы редкостей у Orb of Chance, когда уникалка не выпала.
     */
    private val chanceRarities = listOf(
        EnumRarity.COMMON to 60,
        EnumRarity.UNCOMMON to 30,
        EnumRarity.RARE to 10,
    )

    /**
     * Сколько аффиксов должно быть на предмете, чтобы Fracturing Orb могла закрепить один.
     */
    const val FRACTURE_MIN_AFFIXES = 4

    /**
     * Редкости, на которые ложатся закрепление и влияние: редкий предмет и выше, кроме уникалки.
     */
    private val rareOrBetter = setOf(EnumRarity.RARE)

    /**
     * Применяет сферу к предмету.
     *
     * @param template шаблон предмета - из него берётся пул модификаторов и item level
     * @throws CurrencyExceptions.CurrencyException если сфера к предмету неприменима
     */
    fun apply(orb: EnumCurrencyOrb, item: CharacterEquipment, template: Equipment): CurrencyOutcome {
        val outcome = applyRule(orb, item, template)
        // Самоцвет без аффикса ничего не даёт (0.42.0): сфера, что оставила бы его пустым, отказывает.
        if (features.logic.equipment.Jewels.isJewel(template) && outcome.item._id == item._id && features.logic.equipment.Jewels.empty(template, outcome.item))
            throw CurrencyExceptions.funExceptionJewelEmpty("apply", LocaleKey.equipmentName(template.code))
        return outcome
    }

    private fun applyRule(orb: EnumCurrencyOrb, item: CharacterEquipment, template: Equipment): CurrencyOutcome {
        if (item.corrupted) throw CurrencyExceptions.funExceptionCorrupted("apply", template.code)
        if (item.mirrored) throw CurrencyExceptions.funExceptionMirrored("apply", template.code)
        if (orb.mapOnly && template.slot != EnumEquipmentType.MAP) throw CurrencyExceptions.funExceptionNotMap("apply", LocaleKey.enumLabel("EnumCurrencyOrb", orb.name))

        return when (orb) {
            ORB_OF_TRANSMUTATION -> upgrade(item, template, from = EnumRarity.COMMON, to = EnumRarity.UNCOMMON)
            ORB_OF_ALCHEMY -> upgrade(item, template, from = EnumRarity.COMMON, to = EnumRarity.RARE)
            ORB_OF_ALTERATION -> reroll(item, template, required = EnumRarity.UNCOMMON)
            CHAOS_ORB -> reroll(item, template, required = EnumRarity.RARE)
            ORB_OF_AUGMENTATION -> augment(item, template, required = EnumRarity.UNCOMMON)
            EXALTED_ORB -> augment(item, template, required = EnumRarity.RARE)
            REGAL_ORB -> regal(item, template)
            DIVINE_ORB -> divine(item, template)
            BLESSED_ORB -> blessed(item, template)
            ORB_OF_ANNULMENT -> annul(item, template)
            ORB_OF_SCOURING -> scour(item, template)
            VAAL_ORB -> vaal(item, template)
            ORB_OF_CHANCE -> chance(item, template)
            MIRROR_OF_KALANDRA -> mirror(item, template)
            FRACTURING_ORB -> fracture(item, template)
            SHAPERS_ORB, ELDER_ORB -> influence(item, template, EnumInfluence.byOrb(orb)!!)
            // Единственная сфера, которую тратит не предмет: её списывает дерево навыков
            // за возврат узла, см. CharacterSkillTreeRepository.
            ORB_OF_REGRET -> throw CurrencyExceptions.funExceptionNotForItem("apply", orb.name)
            EnumCurrencyOrb.EMPOWERING_ORB -> empower(item, template)
            EnumCurrencyOrb.MERCY_ORB -> mercy(item, template)
            EnumCurrencyOrb.PERIL_ORB -> peril(item, template)
            EnumCurrencyOrb.HORDE_ORB -> alchemyLine(item, template, "ALC_MAP_PACK")
            EnumCurrencyOrb.MAGUS_ORB -> alchemyLine(item, template, "ALC_MAP_MAGIC")
            EnumCurrencyOrb.ELITE_ORB -> alchemyLine(item, template, "ALC_MAP_RARE")
            EnumCurrencyOrb.BOUNTY_ORB -> alchemyLine(item, template, "ALC_MAP_LOOT")
        }
    }

    /**
     * Результат с ключом сообщения: первым аргументом всегда идёт сам предмет.
     */
    private fun outcome(
        item: CharacterEquipment,
        template: Equipment,
        messageKey: String,
        vararg args: String
    ) = CurrencyOutcome(
        item = item,
        messageKey = messageKey,
        messageArgs = listOf(LocaleKey.equipmentName(template.code)) + args
    )

    // ==================== Правила сфер ====================

    /**
     * Поднимает редкость обычного предмета и роллит аффиксы заново.
     */
    private fun upgrade(
        item: CharacterEquipment,
        template: Equipment,
        from: EnumRarity,
        to: EnumRarity
    ): CurrencyOutcome {
        requireRarity(item, template, from)

        item.rarity = to
        item.params = (permanent(item) + ModifierRoller.rollAffixes(template, to, item.influence)).toMutableList()

        return outcome(item, template, "currency.upgraded", LocaleKey.rarity(to), affixes(item).size.toString())
    }

    /**
     * Перекатывает аффиксы, сохраняя редкость. Закреплённый аффикс остаётся на месте,
     * ремесленный уходит вместе с остальными - как в POE.
     */
    private fun reroll(item: CharacterEquipment, template: Equipment, required: EnumRarity): CurrencyOutcome {
        requireRarity(item, template, required)

        val kept = fractured(item)
        item.params = (permanent(item) + kept + ModifierRoller.rollAffixes(template, item.rarity, item.influence, kept)).toMutableList()

        return outcome(item, template, "currency.rerolled", affixes(item).size.toString())
    }

    /**
     * Добавляет ещё один аффикс, если редкость оставляет место.
     */
    private fun augment(item: CharacterEquipment, template: Equipment, required: EnumRarity): CurrencyOutcome {
        requireRarity(item, template, required)

        val added = ModifierRoller.rollExtraAffix(template, item.rarity, item.params, item.influence)
            ?: throw CurrencyExceptions.funExceptionNoFreeAffix("augment", template.code)

        item.params.add(added)

        return outcome(item, template, "currency.augmented")
    }

    /**
     * Магический предмет становится редким: аффиксы сохраняются, добавляется ещё один
     * и сколько нужно ещё, чтобы дотянуть до минимума редкого.
     */
    private fun regal(item: CharacterEquipment, template: Equipment): CurrencyOutcome {
        requireRarity(item, template, EnumRarity.UNCOMMON)

        item.rarity = EnumRarity.RARE
        ModifierRoller.rollExtraAffix(template, item.rarity, item.params, item.influence)?.let { item.params.add(it) }
        // У редкого своё дно (0.53.0): добавленного одного может не хватить - дороллим до минимума
        ModifierRoller.normalize(template, item.rarity, item.params, item.influence)?.let { item.params = it }

        return outcome(item, template, "currency.regal", affixes(item).size.toString())
    }

    /**
     * Перекатывает значения модификаторов, сохраняя сами модификаторы и их тиры.
     *
     * На уникалке перекатываются её собственные модификаторы, на прочих предметах - аффиксы.
     */
    private fun divine(item: CharacterEquipment, template: Equipment): CurrencyOutcome {
        val rerollable: (ModifierDefinition) -> Boolean =
            if (item.rarity.fixed) {
                { it.source == EnumModifierSource.UNIQUE }
            } else {
                { it.source == EnumModifierSource.PREFIX || it.source == EnumModifierSource.SUFFIX }
            }

        val count = ModifierRoller.definitions(item.params.filterNot { it.fractured }).count(rerollable)
        if (count == 0) throw CurrencyExceptions.funExceptionNoAffixes("divine", template.code)

        item.params = ModifierRoller.rerollValues(item.params, rerollable)

        return outcome(item, template, "currency.divine", count.toString())
    }

    /**
     * Перекатывает значения implicit-модификаторов.
     */
    private fun blessed(item: CharacterEquipment, template: Equipment): CurrencyOutcome {
        val implicits = ModifierRoller.definitions(item.params).count { it.source == EnumModifierSource.IMPLICIT }
        if (implicits == 0) throw CurrencyExceptions.funExceptionNoImplicits("blessed", template.code)

        item.params = ModifierRoller.rerollValues(item.params) { it.source == EnumModifierSource.IMPLICIT }

        return outcome(item, template, "currency.blessed")
    }

    /**
     * Убирает случайный аффикс. Закреплённый не снимается, а ниже минимума редкости сфера не опускает.
     */
    private fun annul(item: CharacterEquipment, template: Equipment): CurrencyOutcome {
        val current = affixes(item).filterNot { it.fractured }
        if (current.isEmpty()) throw CurrencyExceptions.funExceptionNoAffixes("annul", template.code)
        // Ниже минимума редкости предмет не опускается (0.53.0): у волшебного хотя бы один аффикс, у редкого четыре
        if (affixes(item).size <= item.rarity.affixes.first)
            throw CurrencyExceptions.funExceptionAffixMinimum("annul", LocaleKey.equipmentName(template.code), LocaleKey.rarity(item.rarity))

        val removed = current.randomExt()
        item.params.remove(removed)

        return outcome(item, template, "currency.annulled", affixes(item).size.toString())
    }

    /**
     * Снимает все аффиксы и возвращает предмет к обычной редкости.
     *
     * Закреплённый аффикс остаётся, а обычный предмет аффиксов не держит,
     * поэтому такая копия опускается только до магической. Влияние не снимается.
     */
    private fun scour(item: CharacterEquipment, template: Equipment): CurrencyOutcome {
        if (item.rarity.fixed)
            throw CurrencyExceptions.funExceptionRarity("scour", item.rarity.name)

        val kept = fractured(item)
        val target = if (kept.isEmpty()) EnumRarity.COMMON else EnumRarity.UNCOMMON
        if (item.rarity == target && affixes(item).size == kept.size)
            throw CurrencyExceptions.funExceptionNoAffixes("scour", template.code)

        item.rarity = target
        item.params = (permanent(item) + kept).toMutableList()

        return outcome(item, template, if (kept.isEmpty()) "currency.scoured" else "currency.scoured_fractured")
    }

    /**
     * Портит предмет, как в POE (с 0.58.0): после неё ни одна сфера его не тронет, а сама порча
     * выпадает одним из равновероятных исходов [VaalOutcome]. Ложится и на экипировку, и на карты.
     * Имплисит порчи (с 0.59.0) - только у карты: любой модификатор из её собственного пула,
     * вредный или наградный; экипировке остаются три исхода.
     */
    private fun vaal(item: CharacterEquipment, template: Equipment): CurrencyOutcome {
        item.corrupted = true
        val map = template.slot == EnumEquipmentType.MAP
        return when (VaalOutcome.entries.filter { map || it != VaalOutcome.IMPLICIT }.random()) {
            VaalOutcome.NOTHING -> outcome(item, template, "currency.vaal_nothing")
            VaalOutcome.IMPLICIT -> {
                val corruption = Pools.draw(ModifierRoller.affixPool(template))?.let { ModifierRoller.roll(it, template.itemLevel) }
                corruption?.let { item.params.add(it.copy(tier = 0)) }
                outcome(item, template, if (corruption != null) "currency.vaal_modifier" else "currency.vaal_nothing")
            }
            // Уникалку в редкую не превратить, как и в POE: на ней этот исход только портит.
            VaalOutcome.RARE -> if (item.rarity == EnumRarity.UNIQUE) outcome(item, template, "currency.vaal_nothing") else {
                item.rarity = EnumRarity.RARE
                item.params = (permanent(item) + fractured(item) + ModifierRoller.rollAffixes(template, EnumRarity.RARE, item.influence, fractured(item))).toMutableList()
                outcome(item, template, "currency.vaal_rare", affixes(item).size.toString())
            }
            // Каждое значение - своим множителем, и потолок тира ему не указ (как в POE 2).
            VaalOutcome.SHIFT -> {
                item.params = item.params.map { modifier ->
                    modifier.copy(values = modifier.values.map { (it * kotlin.random.Random.nextDouble(VAAL_SHIFT_MIN, VAAL_SHIFT_MAX)).to1Digits() })
                }.toMutableList()
                outcome(item, template, "currency.vaal_shift")
            }
        }
    }

    /**
     * Делает из обычного предмета предмет случайной редкости.
     * С небольшим шансом, как в POE, выдаёт уникалку того же слота.
     */
    private fun chance(item: CharacterEquipment, template: Equipment): CurrencyOutcome {
        requireRarity(item, template, EnumRarity.COMMON)

        // Уникалка того же слота из пулов, которые называет сама сфера
        val unique = Pools.draw(equipmentCache.pool(CurrencySeeder.records[ORB_OF_CHANCE]?.uniquePools.orEmpty()).filter { it.value.slot == template.slot })

        if (unique != null && RandomExt.randomInt(1..100) <= CHANCE_UNIQUE_PERCENT) {
            item.equipmentId = unique._id
            item.rarity = EnumRarity.UNIQUE
            item.influence = null
            item.params = ModifierRoller.roll(unique, EnumRarity.UNIQUE)

            return outcome(item, template, "currency.chance_unique", LocaleKey.equipmentName(unique.code))
        }

        val rarity = chanceRarities.weightedRandomExt { it.second }?.first ?: EnumRarity.COMMON
        item.rarity = rarity
        item.params = (permanent(item) + ModifierRoller.rollAffixes(template, rarity, item.influence)).toMutableList()

        return outcome(item, template, "currency.chance_rarity", LocaleKey.rarity(rarity))
    }

    /**
     * Создаёт неизменяемую копию предмета.
     */
    private fun mirror(item: CharacterEquipment, template: Equipment): CurrencyOutcome {
        // Копия неизменяема, а не порчена: запрет тот же, но состояние своё.
        // До 0.20.0 здесь стояла порча - отдельного флага просто не было.
        val copy = item.copy(
            _id = ObjectId().toHexString(),
            version = 0,
            params = item.params.toMutableList(),
            equippedSlot = null,
            socketCode = null,
            mirrored = true
        )

        return CurrencyOutcome(item, copy, "currency.mirrored", listOf(LocaleKey.equipmentName(template.code)))
    }

    /**
     * Закрепляет случайный аффикс: дальше его не снимает, не перекатывает и не меняет
     * ни одна сфера. Нужен редкий предмет с четырьмя аффиксами и больше, закреплённый
     * аффикс на предмете один, а ремесленный закрепить нельзя - верстак его и так снимает.
     */
    private fun fracture(item: CharacterEquipment, template: Equipment): CurrencyOutcome {
        if (item.rarity !in rareOrBetter)
            throw CurrencyExceptions.funExceptionRarity("fracture", item.rarity.name)
        if (item.params.any { it.fractured })
            throw CurrencyExceptions.funExceptionAlreadyFractured("fracture", template.code)

        val current = affixes(item)
        if (current.size < FRACTURE_MIN_AFFIXES)
            throw CurrencyExceptions.funExceptionTooFewAffixes("fracture", template.code)

        val chosen = current.filterNot { ModifierRoller.isCrafted(it) }.randomExt()
        item.params[item.params.indexOf(chosen)] = chosen.copy(fractured = true)

        return outcome(item, template, "currency.fractured")
    }

    /**
     * Накладывает влияние и сразу добавляет модификатор из его пула - как сферы
     * влияния в POE. Нужны редкий предмет, свободное место и отсутствие другого
     * влияния; самоцвет влиянию не поддаётся.
     */
    private fun influence(item: CharacterEquipment, template: Equipment, influence: EnumInfluence): CurrencyOutcome {
        if (template.slot == EnumEquipmentType.JEWEL || template.slot == EnumEquipmentType.MAP || template.slot.isTool)
            throw CurrencyExceptions.funExceptionNotInfluenceable("influence", template.code)
        if (item.rarity !in rareOrBetter)
            throw CurrencyExceptions.funExceptionRarity("influence", item.rarity.name)
        if (item.influence != null)
            throw CurrencyExceptions.funExceptionAlreadyInfluenced("influence", template.code)

        val added = ModifierRoller.rollInfluenced(template, item.rarity, item.params, influence)
            ?: throw CurrencyExceptions.funExceptionNoFreeAffix("influence", template.code)

        item.influence = influence
        item.params.add(added)

        return outcome(item, template, "currency.influenced", LocaleKey.enumLabel("EnumInfluence", influence.name))
    }

    // ==================== Вспомогательное ====================

    private fun requireRarity(item: CharacterEquipment, template: Equipment, required: EnumRarity) {
        if (item.rarity != required)
            throw CurrencyExceptions.funExceptionRarity("requireRarity", "${template.code}: ${item.rarity}, need $required")
    }

    /**
     * Аффиксы предмета - только их трогают сферы перекатки.
     */
    private fun affixes(item: CharacterEquipment): List<Modifier> = item.params.filter { ModifierRoller.isAffix(it) }

    /**
     * Закреплённые аффиксы - их не трогает ни одна сфера.
     */
    private fun fractured(item: CharacterEquipment): List<Modifier> = item.params.filter { it.fractured }

    /**
     * Постоянные модификаторы предмета: implicit, энчанты, порча, модификаторы уникалок.
     */
    // ==================== Сферы алхимика для карт (0.38.0) ====================

    /** Вредная ли строка карты: её характеристика платит риском. */
    private fun harmful(definition: ModifierDefinition): Boolean {
        val risk = features.logic.campaign.CampaignContent.file.maps.risk.keys
        return definition.effects.any { (it.stat as? Enum<*>)?.name in risk }
    }

    /** Каждый аффикс карты - на тир выше. */
    private fun empower(item: CharacterEquipment, template: Equipment): CurrencyOutcome {
        var raised = 0
        item.params = item.params.mapTo(mutableListOf()) { modifier ->
            if (!ModifierRoller.isAffix(modifier)) modifier else ModifierRoller.raiseTier(modifier)?.also { raised++ } ?: modifier
        }
        if (raised == 0) throw CurrencyExceptions.funExceptionNoAffixes("empower", template.code)
        return outcome(item, template, "currency.empowered")
    }

    /** Снимает случайный вредный аффикс карты. */
    private fun mercy(item: CharacterEquipment, template: Equipment): CurrencyOutcome {
        val candidates = affixes(item).filterNot { it.fractured }.filter { modifier -> ModifierRoller.definitions(listOf(modifier)).any(::harmful) }
        if (candidates.isEmpty()) throw CurrencyExceptions.funExceptionNoHarm("mercy", template.code)
        item.params.remove(candidates.randomExt())
        return outcome(item, template, "currency.mercy")
    }

    /** Добавляет карте вредный аффикс её пула сверх лимита, без повтора группы. */
    private fun peril(item: CharacterEquipment, template: Equipment): CurrencyOutcome {
        val taken = ModifierRoller.definitions(item.params).map { it.family() }.toSet()
        val pool = ModifierRoller.affixPool(template).filter { (it) -> harmful(it) && it.family() !in taken }
        val added = Pools.draw(pool)?.let { ModifierRoller.roll(it, template.itemLevel) }
            ?: throw CurrencyExceptions.funExceptionNoHarm("peril", template.code)
        item.params.add(added)
        return outcome(item, template, "currency.peril")
    }

    /** Строка «Алхимия»: до трёх на карте и без повторов. */
    private fun alchemyLine(item: CharacterEquipment, template: Equipment, code: String): CurrencyOutcome {
        val lines = ModifierRoller.definitions(item.params).filter { it.source == EnumModifierSource.ALCHEMY }
        if (lines.size >= MAX_ALCHEMY_LINES || lines.any { it.code == code }) throw CurrencyExceptions.funExceptionAlchemyFull("alchemy", template.code)
        item.params.add(ModifierRoller.rollCode(code, template.itemLevel) ?: throw CurrencyExceptions.funExceptionAlchemyFull("alchemy", template.code))
        return outcome(item, template, "currency.alchemy_line")
    }

    private fun permanent(item: CharacterEquipment): List<Modifier> = item.params.filterNot { ModifierRoller.isAffix(it) }
}

/** Исходы сферы Ваал (с 0.58.0), равновероятные: ничего, имплисит порчи (только карта, 0.59.0), перекат в редкий, сдвиг значений на ±20%. */
private enum class VaalOutcome { NOTHING, IMPLICIT, RARE, SHIFT }

private const val VAAL_SHIFT_MIN = 0.8
private const val VAAL_SHIFT_MAX = 1.2
