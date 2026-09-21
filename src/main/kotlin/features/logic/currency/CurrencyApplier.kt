package features.logic.currency

import application.enums.EnumCurrencyOrb
import application.enums.EnumCurrencyOrb.BLESSED_ORB
import application.enums.EnumCurrencyOrb.CHAOS_ORB
import application.enums.EnumCurrencyOrb.DIVINE_ORB
import application.enums.EnumCurrencyOrb.EXALTED_ORB
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
import application.enums.EnumCurrencyOrb.VAAL_ORB
import application.enums.EnumModifierSource
import application.enums.EnumRarity
import base.exception.model.CurrencyExceptions
import extensions.RandomExt
import extensions.randomExt
import extensions.weightedRandomExt
import features.caches.EquipmentCache
import features.data.equipment.equipment_data.Equipment
import features.data.inventory.CharacterEquipment
import features.logic.modifiers.Modifier
import features.logic.modifiers.ModifierDefinition
import features.logic.locale.LocaleKey
import features.logic.modifiers.ModifierRoller
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

    /**
     * Шансы редкостей у Orb of Chance, когда уникалка не выпала.
     */
    private val chanceRarities = listOf(
        EnumRarity.COMMON to 60,
        EnumRarity.UNCOMMON to 30,
        EnumRarity.RARE to 10,
    )

    /**
     * Применяет сферу к предмету.
     *
     * @param template шаблон предмета - из него берётся пул модификаторов и item level
     * @throws CurrencyExceptions.CurrencyException если сфера к предмету неприменима
     */
    fun apply(orb: EnumCurrencyOrb, item: CharacterEquipment, template: Equipment): CurrencyOutcome {
        if (item.corrupted) throw CurrencyExceptions.funExceptionCorrupted("apply", template.code)

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
            // Единственная сфера, которую тратит не предмет: её списывает дерево навыков
            // за возврат узла, см. CharacterSkillTreeRepository.
            ORB_OF_REGRET -> throw CurrencyExceptions.funExceptionNotForItem("apply", orb.name)
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
        item.params = (permanent(item) + ModifierRoller.rollAffixes(template, to)).toMutableList()

        return outcome(item, template, "currency.upgraded", LocaleKey.rarity(to), affixes(item).size.toString())
    }

    /**
     * Перекатывает аффиксы, сохраняя редкость.
     */
    private fun reroll(item: CharacterEquipment, template: Equipment, required: EnumRarity): CurrencyOutcome {
        requireRarity(item, template, required)

        item.params = (permanent(item) + ModifierRoller.rollAffixes(template, item.rarity)).toMutableList()

        return outcome(item, template, "currency.rerolled", affixes(item).size.toString())
    }

    /**
     * Добавляет ещё один аффикс, если редкость оставляет место.
     */
    private fun augment(item: CharacterEquipment, template: Equipment, required: EnumRarity): CurrencyOutcome {
        requireRarity(item, template, required)

        val added = ModifierRoller.rollExtraAffix(template, item.rarity, item.params)
            ?: throw CurrencyExceptions.funExceptionNoFreeAffix("augment", template.code)

        item.params.add(added)

        return outcome(item, template, "currency.augmented")
    }

    /**
     * Магический предмет становится редким: аффиксы сохраняются, добавляется ещё один.
     */
    private fun regal(item: CharacterEquipment, template: Equipment): CurrencyOutcome {
        requireRarity(item, template, EnumRarity.UNCOMMON)

        item.rarity = EnumRarity.RARE
        ModifierRoller.rollExtraAffix(template, item.rarity, item.params)?.let { item.params.add(it) }

        return outcome(item, template, "currency.regal", affixes(item).size.toString())
    }

    /**
     * Перекатывает значения модификаторов, сохраняя сами модификаторы и их тиры.
     *
     * На уникалке перекатываются её собственные модификаторы, на прочих предметах - аффиксы.
     */
    private fun divine(item: CharacterEquipment, template: Equipment): CurrencyOutcome {
        val rerollable: (ModifierDefinition) -> Boolean =
            if (item.rarity == EnumRarity.UNIQUE) {
                { it.source == EnumModifierSource.UNIQUE }
            } else {
                { it.source == EnumModifierSource.PREFIX || it.source == EnumModifierSource.SUFFIX }
            }

        val count = ModifierRoller.definitions(item.params).count(rerollable)
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
     * Убирает случайный аффикс.
     */
    private fun annul(item: CharacterEquipment, template: Equipment): CurrencyOutcome {
        val current = affixes(item)
        if (current.isEmpty()) throw CurrencyExceptions.funExceptionNoAffixes("annul", template.code)

        val removed = current.randomExt()
        item.params.remove(removed)

        return outcome(item, template, "currency.annulled", affixes(item).size.toString())
    }

    /**
     * Снимает все аффиксы и возвращает предмет к обычной редкости.
     */
    private fun scour(item: CharacterEquipment, template: Equipment): CurrencyOutcome {
        if (item.rarity == EnumRarity.COMMON && affixes(item).isEmpty())
            throw CurrencyExceptions.funExceptionNoAffixes("scour", template.code)
        if (item.rarity == EnumRarity.UNIQUE)
            throw CurrencyExceptions.funExceptionRarity("scour", item.rarity.name)

        item.rarity = EnumRarity.COMMON
        item.params = permanent(item).toMutableList()

        return outcome(item, template, "currency.scoured")
    }

    /**
     * Портит предмет: вешает модификатор порчи и закрывает его для дальнейших изменений.
     */
    private fun vaal(item: CharacterEquipment, template: Equipment): CurrencyOutcome {
        item.corrupted = true

        val corruption = ModifierRoller.rollCorruption(template.itemLevel)
        if (corruption != null) item.params.add(corruption)

        val key = if (corruption != null) "currency.vaal_modifier" else "currency.vaal_nothing"
        return outcome(item, template, key)
    }

    /**
     * Делает из обычного предмета предмет случайной редкости.
     * С небольшим шансом, как в POE, выдаёт уникалку того же слота.
     */
    private fun chance(item: CharacterEquipment, template: Equipment): CurrencyOutcome {
        requireRarity(item, template, EnumRarity.COMMON)

        val uniques = equipmentCache.getCache()
            .filter { it.rarity == EnumRarity.UNIQUE && it.slot == template.slot }

        if (uniques.isNotEmpty() && RandomExt.randomInt(1..100) <= CHANCE_UNIQUE_PERCENT) {
            val unique = uniques.randomExt()
            item.equipmentId = unique._id
            item.rarity = EnumRarity.UNIQUE
            item.params = ModifierRoller.roll(unique, EnumRarity.UNIQUE)

            return outcome(item, template, "currency.chance_unique", LocaleKey.equipmentName(unique.code))
        }

        val rarity = chanceRarities.weightedRandomExt { it.second }?.first ?: EnumRarity.COMMON
        item.rarity = rarity
        item.params = (permanent(item) + ModifierRoller.rollAffixes(template, rarity)).toMutableList()

        return outcome(item, template, "currency.chance_rarity", LocaleKey.rarity(rarity))
    }

    /**
     * Создаёт неизменяемую копию предмета.
     */
    private fun mirror(item: CharacterEquipment, template: Equipment): CurrencyOutcome {
        val copy = item.copy(
            _id = ObjectId().toHexString(),
            version = 0,
            params = item.params.toMutableList(),
            equippedSlot = null,
            corrupted = true
        )

        return CurrencyOutcome(item, copy, "currency.mirrored", listOf(LocaleKey.equipmentName(template.code)))
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
     * Постоянные модификаторы предмета: implicit, энчанты, порча, модификаторы уникалок.
     */
    private fun permanent(item: CharacterEquipment): List<Modifier> = item.params.filterNot { ModifierRoller.isAffix(it) }
}
