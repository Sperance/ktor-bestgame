package features.logic.bench

import application.enums.EnumCurrencyOrb
import application.enums.EnumEquipmentType
import application.enums.EnumModifierSource
import application.enums.EnumRarity
import base.exception.model.CurrencyExceptions
import config.ContentResource
import config.ModifierSeeder
import extensions.toStableObjectId
import features.data.equipment.equipment_data.Equipment
import features.data.inventory.CharacterEquipment
import features.logic.currency.CurrencyOutcome
import features.logic.locale.LocaleKey
import features.logic.modifiers.Modifier
import features.logic.modifiers.ModifierDefinition
import features.logic.modifiers.ModifierRoller
import features.logic.modifiers.ModifierTier
import features.logic.modifiers.ModifierTierValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Одна строка верстака: какой ремесленный модификатор какого тира и за сколько сфер.
 *
 * Значения внутри тира роллятся при крафте, как у выпавшего аффикса, а сам тир
 * выбирает игрок - в этом и смысл верстака. [values] отдаются клиенту, чтобы
 * строка читалась как "+(70-79) к здоровью" ещё до крафта.
 *
 * @property code стабильный код рецепта: код модификатора и номер тира
 * @property orbItemId id сферы в коллекции `Items`, которой платят
 * @property slots слоты, на которые рецепт ставится; пусто - на любой
 */
@Serializable
data class BenchRecipe(
    val code: String,
    val modifierId: String,
    val modifierCode: String,
    val tierId: String,
    val tier: Int,
    val source: EnumModifierSource,
    val group: String,
    val values: List<ModifierTierValue>,
    val orb: EnumCurrencyOrb,
    val orbItemId: String,
    val amount: Long,
    val slots: List<EnumEquipmentType> = emptyList(),
) {
    fun fits(slot: EnumEquipmentType): Boolean = slots.isEmpty() || slot in slots
}

/**
 * Верстак ремесленника, как в POE: игрок сам выбирает модификатор и платит за него сферами.
 *
 * Правила здесь, списание сфер и сохранение - в репозитории, одной транзакцией.
 * Ремесленный модификатор на предмете один, встаёт только на свободное место
 * своего вида и не рядом с модификатором своей группы. Снять его можно за
 * [UNCRAFT_ORB] - остальное на предмете при этом не меняется.
 */
object CraftingBench {

    const val FILE = "bench.json"

    /**
     * Сфера, которой платят за снятие ремесленного модификатора.
     */
    val UNCRAFT_ORB = EnumCurrencyOrb.ORB_OF_SCOURING

    /**
     * Редкости, на которые верстак ставит модификатор: у обычного предмета нет мест,
     * у уникального они закрыты.
     */
    private val craftable = setOf(EnumRarity.UNCOMMON, EnumRarity.RARE)

    /** Самый высокий уровень локации кампании, за которым тир рецепта больше не растёт (с 0.46.0). */
    const val MAX_MAP_LEVEL = 20

    /** Сколько тиров рецептов знает верстак (с 0.46.0, было 3 - расширено до PoE-масштаба). */
    const val MAX_TIER = 6

    @Serializable
    private data class RecipeRecord(
        val modifier: String,
        val tier: Int,
        val orb: EnumCurrencyOrb,
        val amount: Long,
        val slots: List<EnumEquipmentType> = emptyList(),
    )

    @Serializable
    private data class BenchDocument(val recipes: List<RecipeRecord> = emptyList())

    /**
     * Все рецепты. Строятся из файла и сидера модификаторов без базы: у описаний
     * и тиров стабильные _id, поэтому ссылки совпадают с тем, что лежит в Mongo.
     */
    val recipes: List<BenchRecipe> by lazy { build(ModifierSeeder.seedDefinitions()) }

    fun build(definitions: List<ModifierDefinition>): List<BenchRecipe> {
        val byCode = definitions.associateBy { it.code }
        val tiers = ModifierSeeder.seedTiers(definitions).associateBy { it.modifierId to it.tier }
        val records = Json { ignoreUnknownKeys = true }
            .decodeFromString(BenchDocument.serializer(), ContentResource.read(FILE)).recipes

        return records.map { record ->
            val definition = byCode[record.modifier]
                ?: throw CurrencyExceptions.funExceptionRecipeNotFound("build", record.modifier)
            val tier = tiers[definition._id to record.tier]
                ?: throw CurrencyExceptions.funExceptionRecipeNotFound("build", "${record.modifier}_T${record.tier}")
            if (!definition.crafted || !definition.isAffix())
                throw CurrencyExceptions.funExceptionRecipeNotFound("build", "${record.modifier} is not a crafted affix")

            BenchRecipe(
                code = "${definition.code}_T${tier.tier}",
                modifierId = definition._id,
                modifierCode = definition.code,
                tierId = tier._id,
                tier = tier.tier,
                source = definition.source,
                group = definition.family(),
                values = tier.values,
                orb = record.orb,
                orbItemId = record.orb.name.toStableObjectId(),
                amount = record.amount,
                slots = record.slots,
            )
        }
    }

    private val byCode: Map<String, BenchRecipe> by lazy { recipes.associateBy { it.code } }

    fun recipe(code: String): BenchRecipe =
        byCode[code] ?: throw CurrencyExceptions.funExceptionRecipeNotFound("recipe", code)

    /**
     * Тир рецепта, который может выпасть на локации этого уровня (с 0.46.0): чем ниже уровень,
     * тем выше тир - шесть равных отрезков между 1 и [MAX_MAP_LEVEL].
     */
    fun tierFor(level: Int): Int =
        (MAX_TIER - (level - 1) * MAX_TIER / MAX_MAP_LEVEL).coerceIn(1, MAX_TIER)

    /**
     * Один незнакомый герою рецепт тира этой карты, если такой есть (с 0.46.0). Чистая функция -
     * решение выпало это или нет, и с каким [random], остаётся вызывающей стороне.
     */
    fun draw(known: List<String>, level: Int, random: kotlin.random.Random): BenchRecipe? =
        recipes.filter { it.tier == tierFor(level) && it.code !in known }.randomOrNull(random)

    /**
     * Ставит ремесленный модификатор. Предмет меняется на месте.
     */
    fun craft(item: CharacterEquipment, template: Equipment, recipe: BenchRecipe, known: List<String>): CurrencyOutcome {
        requireModifiable(item, template)
        if (recipe.code !in known)
            throw CurrencyExceptions.funExceptionRecipeLocked("craft", recipe.code)
        if (item.rarity !in craftable)
            throw CurrencyExceptions.funExceptionRarity("craft", item.rarity.name)
        if (!recipe.fits(template.slot))
            throw CurrencyExceptions.funExceptionRecipeSlot("craft", template.slot.name)

        val current = ModifierRoller.definitions(item.params)
        if (current.any { it.crafted })
            throw CurrencyExceptions.funExceptionAlreadyCrafted("craft", template.code)
        if (current.any { it.family() == recipe.group })
            throw CurrencyExceptions.funExceptionGroupTaken("craft", template.code)

        val (prefixes, suffixes) = ModifierRoller.freeSlots(item.rarity, current)
        val free = if (recipe.source == EnumModifierSource.PREFIX) prefixes else suffixes
        if (free == 0) throw CurrencyExceptions.funExceptionNoFreeAffix("craft", template.code)

        val values = ModifierTier(recipe.modifierId, recipe.tier, recipe.values, _id = recipe.tierId).roll()
        item.params.add(Modifier(recipe.modifierId, values, recipe.tierId, recipe.tier))

        return outcome(item, template, "currency.crafted")
    }

    /**
     * Снимает ремесленный модификатор. Предмет меняется на месте.
     */
    fun uncraft(item: CharacterEquipment, template: Equipment): CurrencyOutcome {
        requireModifiable(item, template)
        val crafted = item.params.filter { ModifierRoller.isCrafted(it) }
        if (crafted.isEmpty()) throw CurrencyExceptions.funExceptionNoCrafted("uncraft", template.code)

        item.params.removeAll(crafted)
        return outcome(item, template, "currency.uncrafted")
    }

    private fun requireModifiable(item: CharacterEquipment, template: Equipment) {
        if (item.corrupted) throw CurrencyExceptions.funExceptionCorrupted("bench", template.code)
        if (item.mirrored) throw CurrencyExceptions.funExceptionMirrored("bench", template.code)
    }

    private fun outcome(item: CharacterEquipment, template: Equipment, key: String) =
        CurrencyOutcome(item = item, messageKey = key, messageArgs = listOf(LocaleKey.equipmentName(template.code)))
}
