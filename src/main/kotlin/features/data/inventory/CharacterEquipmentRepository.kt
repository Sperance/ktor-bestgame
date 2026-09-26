package features.data.inventory

import application.enums.EnumCurrencyOrb
import application.enums.EnumEquipmentType
import application.enums.EnumSkillNodeType
import application.enums.EnumRarity
import base.exception.model.CharacterExceptions
import base.exception.model.CurrencyExceptions
import base.exception.model.SkillTreeExceptions
import base.repository.BaseRepository
import base.repository.IndexSpec
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import com.mongodb.kotlin.client.coroutine.ClientSession
import config.MongoFactory.transactionExecute
import config.afterCommit
import config.beforeCommit
import features.logic.hero.heroChanges
import features.caches.EquipmentCache
import features.caches.ItemsCache
import features.caches.SkillTreeCache
import features.data.character.CharacterRepository
import features.data.equipment.equipment_data.Equipment
import extensions.toStableObjectId
import features.data.character.Character
import features.data.equipment.equipment_data.Weapon
import features.logic.bench.BenchRecipe
import features.logic.bench.CraftingBench
import features.logic.equipment.EquipSlots
import features.logic.currency.CurrencyApplier
import features.logic.currency.CurrencyOutcome
import features.logic.stats.EquipmentRequirements
import features.logic.trade.SellOutcome
import features.logic.trade.SellPrice
import features.logic.modifiers.ModifierRoller
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CharacterEquipmentRepository : BaseRepository<CharacterEquipment>(
    entityClass = CharacterEquipment::class
), KoinComponent {
    private val equipmentCache: EquipmentCache by inject()
    private val itemsCache: ItemsCache by inject()
    private val characterRepository: CharacterRepository by inject()
    private val skillTreeCache: SkillTreeCache by inject()

    // Инвентарь и надетое героя - по префиксу (characterId, equippedSlot)
    override val indexes = listOf(IndexSpec.on("characterId", "equippedSlot"), IndexSpec.on("equipmentId"))

    /** Новая копия не бывает волшебной или редкой без аффиксов (0.65.0). */
    override suspend fun admit(entity: CharacterEquipment): CharacterEquipment = entity.also { settle(it) }

    /** Ни одна запись копии не оставляет её ниже дна её редкости (0.65.0). */
    override suspend fun settle(entity: CharacterEquipment) {
        equipmentCache.findById(entity.equipmentId)?.let { ModifierRoller.ensureAffixes(it, entity) }
    }

    /**
     * Старые копии, записанные до правила, - волшебные и редкие ниже дна своей редкости -
     * дороллены при старте (0.65.0).
     *
     * @return сколько копий починено
     */
    suspend fun repairAffixes(session: ClientSession): Int {
        var repaired = 0
        collection.find(session, readFilter(Filters.`in`("rarity", EnumRarity.UNCOMMON.name, EnumRarity.RARE.name))).toList().forEach { item ->
            val template = equipmentCache.findById(item.equipmentId) ?: return@forEach
            if (ModifierRoller.ensureAffixes(template, item)) { update(item, session); repaired++ }
        }
        return repaired
    }

    override suspend fun validateBeforeInsert(entity: CharacterEquipment, session: ClientSession) {
        if (equipmentCache.findById(entity.equipmentId) == null)
            throw CharacterExceptions.funExceptionEquipmentNotFound("validateBeforeInsert", entity.equipmentId)
    }

    /**
     * Экземпляр [inventoryId] этого персонажа. Чужой отвечает тем же «не найден», что и
     * несуществующий: иначе ответ подтверждал бы, что чужой id существует.
     */
    suspend fun requireOwned(characterId: String, inventoryId: String, method: String): CharacterEquipment =
        collection.find(readFilter(Filters.and(Filters.eq("_id", inventoryId), Filters.eq("characterId", characterId)))).firstOrNull()
            ?: throw CharacterExceptions.funExceptionItemNotFound(method, inventoryId)

    /** Шаблон экземпляра из кеша справочника. */
    fun templateOf(item: CharacterEquipment, method: String): Equipment =
        equipmentCache.findById(item.equipmentId) ?: throw CharacterExceptions.funExceptionEquipmentNotFound(method, item.equipmentId)

    /**
     * Весь инвентарь персонажа.
     */
    suspend fun findByCharacter(characterId: String): List<CharacterEquipment> =
        findByFilter(Filters.eq("characterId", characterId))

    override suspend fun validateAfterInsert(entity: CharacterEquipment, session: ClientSession) = touched(entity, session, removed = false)
    override suspend fun validateAfterUpdate(entity: CharacterEquipment, session: ClientSession) = touched(entity, session, removed = false)
    override suspend fun validateAfterDelete(entity: CharacterEquipment, session: ClientSession) = touched(entity, session, removed = true)

    /**
     * Любая запись в инвентарь (с 0.49.0): ревизия героя сдвигается один раз на транзакцию, а
     * журнал запроса узнаёт о вещи после коммита - откат его не касается.
     */
    private suspend fun touched(item: CharacterEquipment, session: ClientSession, removed: Boolean) {
        beforeCommit("inventory:${item.characterId}", session) { characterRepository.bumpInventory(item.characterId, it) }
        val changes = heroChanges() ?: return
        afterCommit { if (removed) changes.remove(item) else changes.upsert(item) }
    }

    /** Коды гнёзд дерева, занятых самоцветами персонажа: одно поле, без документов. */
    suspend fun socketCodes(characterId: String): Set<String> =
        collection.withDocumentClass<org.bson.Document>()
            .find(readFilter(Filters.and(Filters.eq("characterId", characterId), Filters.ne("socketCode", null))))
            .projection(com.mongodb.client.model.Projections.include("socketCode"))
            .toList().mapNotNullTo(HashSet()) { it.getString("socketCode") }

    /**
     * Только надетые предметы персонажа.
     */
    suspend fun findEquipped(characterId: String): List<CharacterEquipment> =
        findByFilter(
            Filters.and(
                Filters.eq("characterId", characterId),
                Filters.ne("equippedSlot", null)
            )
        )

    /**
     * Создаёт новый экземпляр предмета из шаблона и кладёт его в инвентарь персонажа.
     */
    suspend fun addFromEquipment(
        characterId: String,
        equipment: Equipment,
        session: ClientSession
    ): CharacterEquipment = insert(CharacterEquipment.fromEquipment(characterId, equipment), session)

    /** Экземпляры нескольких шаблонов одной командой, в порядке [equipment]. */
    suspend fun addAllFromEquipment(characterId: String, equipment: List<Equipment>, session: ClientSession): List<CharacterEquipment> =
        insertMany(equipment.map { CharacterEquipment.fromEquipment(characterId, it) }, session)

    /** Экземпляр своей редкости (с 0.35.0): упавшая карта катает аффиксы под выпавшую редкость, а не под шаблон. */
    suspend fun addRolled(characterId: String, equipment: Equipment, rarity: EnumRarity, session: ClientSession): CharacterEquipment {
        val real = features.logic.equipment.Jewels.rarity(equipment, rarity)
        return insert(CharacterEquipment(characterId = characterId, equipmentId = equipment._id, params = ModifierRoller.roll(equipment, real), rarity = real), session)
    }

    /**
     * Вставляет самоцвет в гнездо дерева навыков.
     *
     * Самоцвет - обычный экземпляр экипировки со слотом [EnumEquipmentType.JEWEL],
     * поэтому роллы, редкость, сферы и аукцион достались ему даром. Отличается он
     * тем, куда надевается: гнёзд на дереве много, и какое занято, говорит
     * [CharacterEquipment.socketCode], а не слот.
     *
     * Гнездо должно быть взято персонажем и свободно: вставлять камень в узел,
     * которого у игрока нет, значило бы дать бонус за невзятое.
     */
    suspend fun socket(characterId: String, inventoryId: String, nodeCode: String): CharacterEquipment {
        val item = requireOwned(characterId, inventoryId, "socket")

        val template = templateOf(item, "socket")
        if (template.slot != EnumEquipmentType.JEWEL)
            throw SkillTreeExceptions.funExceptionNotJewel("socket", template.code)

        val node = skillTreeCache.findByCode(nodeCode)
            ?: throw SkillTreeExceptions.funExceptionNodeNotFound("socket", nodeCode)
        if (node.type != EnumSkillNodeType.JEWEL_SOCKET)
            throw SkillTreeExceptions.funExceptionNotSocket("socket", nodeCode)

        val character = characterRepository.requireCharacter(characterId, "socket")
        if (character.skillNodes.none { it.code == nodeCode })
            throw SkillTreeExceptions.funExceptionNotTaken("socket", nodeCode)

        val busy = collection.countDocuments(Filters.and(Filters.eq("characterId", characterId), Filters.eq("socketCode", nodeCode), Filters.ne("_id", item._id)))
        if (busy > 0) throw SkillTreeExceptions.funExceptionSocketBusy("socket", nodeCode)

        return transactionExecute("socket") { session ->
            // Запись персонажа с проверкой версии: два параллельных запроса не займут одно гнездо
            characterRepository.update(character, session)
            item.equippedSlot = EnumEquipmentType.JEWEL
            item.socketCode = nodeCode
            update(item, session)
            item
        }
    }

    /**
     * Вынимает самоцвет из гнезда: он возвращается в арсенал и перестаёт считаться.
     */
    suspend fun unsocket(characterId: String, inventoryId: String): CharacterEquipment {
        val item = requireOwned(characterId, inventoryId, "unsocket")

        return transactionExecute("unsocket") { session ->
            item.equippedSlot = null
            item.socketCode = null
            update(item, session)
            item
        }
    }

    /**
     * Продаёт предмет торговцу за золото.
     *
     * Цену назначает [SellPrice], а не клиент, и экземпляр после продажи
     * исчезает: золото и удаление идут одной транзакцией, иначе неудачная
     * запись оставила бы игрока и без предмета, и без денег.
     *
     * Надетое и вставленное в гнездо не продаётся: сначала снимите. Это то же
     * правило, по которому надетый предмет не выставить на аукцион - предмет
     * должен быть в руках, а не в работе.
     */
    suspend fun sellForGold(characterId: String, inventoryId: String): SellOutcome {
        val item = requireOwned(characterId, inventoryId, "sellForGold")

        val template = templateOf(item, "sellForGold")

        if (item.socketCode != null)
            throw CharacterExceptions.funExceptionSellSocketed("sellForGold", template.code)
        if (item.equippedSlot != null)
            throw CharacterExceptions.funExceptionSellEquipped("sellForGold", template.code)

        val character = characterRepository.requireCharacter(characterId, "sellForGold")

        // Характеристики нужны ради STOCK_GOLD: надбавку к цене даёт сам персонаж.
        val stats = characterRepository.calculateStats(character).stats
        val gold = SellPrice.of(template, item.rarity, item.params, stats)

        return transactionExecute("sellForGold $inventoryId") { session ->
            deleteById(inventoryId, session)
            character.money += gold
            characterRepository.update(character, session)
            SellOutcome(inventoryId, template.code, gold, character.money)
        }
    }

    /**
     * Надевает предмет в его слот, снимая то, что этот слот занимает, - и то, с чем
     * он не носится: двуручное оружие освобождает обе руки, лук берёт колчан вместо щита.
     * Правила слотов - в [EquipSlots].
     *
     * @param slot какое из двух колец занять; у остальных предметов не используется
     */
    suspend fun equip(characterId: String, inventoryId: String, slot: EnumEquipmentType? = null): CharacterEquipment {
        val item = requireOwned(characterId, inventoryId, "equip")

        val template = templateOf(item, "equip")
        if (template.slot == EnumEquipmentType.MAP)
            throw CharacterExceptions.funExceptionMapNotWorn("equip", template.code)
        // Самоцвет работает только из гнезда дерева: через слот он дал бы бонус без узла
        if (template.slot == EnumEquipmentType.JEWEL)
            throw CharacterExceptions.funExceptionJewelNotWorn("equip", template.code)

        // Надеть предмет с невыполненными требованиями нельзя. Уже надетый
        // при их потере не слетает - он просто перестаёт работать, см. CharacterStatsCalculator
        val character = characterRepository.requireCharacter(characterId, "equip")
        // Самоцвет в гнезде тоже "надет", но рук и колец не занимает
        val equipped = findEquipped(characterId)
        val stats = characterRepository.calculateStats(character, equipped)
        val unmet = EquipmentRequirements.unmet(template, stats.level, stats.stats)
        if (unmet.isNotEmpty())
            throw CharacterExceptions.funExceptionRequirements(
                "equip",
                "${template.code}: " + unmet.joinToString { "${it.name} ${it.actual}/${it.required}" }
            )

        val worn = equipped.filter { it._id != item._id && it.socketCode == null }
        val target = EquipSlots.target(template.slot, slot, worn.mapNotNull { it.equippedSlot })
        val wornWeapon = worn.firstOrNull { it.equippedSlot == EnumEquipmentType.WEAPON_1H }
            ?.let { (equipmentCache.findById(it.equipmentId) as? Weapon)?.weaponType }
        val freed = EquipSlots.displaced(target, (template as? Weapon)?.weaponType, wornWeapon) + target

        return transactionExecute("equip") { session ->
            // Запись персонажа с проверкой версии: два параллельных запроса не займут один слот
            characterRepository.update(character, session)
            worn.filter { it.equippedSlot in freed }.forEach { occupied ->
                occupied.equippedSlot = null
                update(occupied, session)
            }

            item.equippedSlot = target
            update(item, session)
            item
        }
    }

    /**
     * Снимает предмет, оставляя его в инвентаре.
     */
    suspend fun unequip(characterId: String, inventoryId: String): CharacterEquipment {
        val item = requireOwned(characterId, inventoryId, "unequip")

        // Снятый самоцвет освобождает и гнездо, иначе узел остался бы занят вещью из арсенала
        item.equippedSlot = null
        item.socketCode = null
        transactionExecute("unequip") { session ->
            update(item, session)
        }
        return item
    }

    /**
     * Применяет валютную сферу к предмету инвентаря.
     *
     * Сфера списывается у персонажа и предмет сохраняется в одной транзакции,
     * поэтому неудачная проверка не съедает сферу.
     *
     * @param orbItemId id предмета-сферы в коллекции `Items`
     */
    suspend fun applyOrb(characterId: String, inventoryId: String, orbItemId: String): CurrencyOutcome {
        val item = requireOwned(characterId, inventoryId, "applyOrb")

        val template = templateOf(item, "applyOrb")

        val orbItem = itemsCache.findById(orbItemId)
            ?: throw CharacterExceptions.funExceptionItemNotFound("applyOrb", orbItemId)
        val orb = orbItem.takeIf { it.category == EnumCurrencyOrb.CATEGORY }
            ?.let { EnumCurrencyOrb.byCode(it.subCategory) }
            ?: throw CurrencyExceptions.funExceptionNotCurrency("applyOrb", orbItem.code)

        val character = characterRepository.requireCharacter(characterId, "applyOrb")

        return transactionExecute("applyOrb ${orb.name}") { session ->
            // Сначала списываем сферу: если её нет, предмет даже не трогаем
            characterRepository.spendItem(character, orbItemId, 1, session)

            val outcome = CurrencyApplier.apply(orb, item, template)
            update(outcome.item, session)
            outcome.created?.let { insert(it, session) }
            outcome
        }
    }

    /**
     * Применяет эссенцию к предмету инвентаря (с 0.69.0) - так же, одной транзакцией со списанием.
     *
     * @param essenceItemId id предмета-эссенции в коллекции `Items`
     */
    suspend fun applyEssence(characterId: String, inventoryId: String, essenceItemId: String): CurrencyOutcome {
        val method = "applyEssence"
        val item = requireOwned(characterId, inventoryId, method)
        val template = templateOf(item, method)
        val essenceItem = itemsCache.findById(essenceItemId) ?: throw CharacterExceptions.funExceptionItemNotFound(method, essenceItemId)
        val essence = essenceItem.takeIf { it.category == features.logic.essences.EssenceContent.CATEGORY }
            ?.let { features.logic.essences.EssenceContent.essences[it.code] }
            ?: throw CurrencyExceptions.funExceptionNotCurrency(method, essenceItem.code)
        val character = characterRepository.requireCharacter(characterId, method)
        return transactionExecute("$method ${essence.code}") { session ->
            characterRepository.spendItem(character, essenceItemId, 1, session)
            CurrencyApplier.applyEssence(essence, item, template).also { update(it.item, session) }
        }
    }

    /**
     * Рецепты верстака, известные герою (с 0.46.0): все остальные скрыты, их нужно найти на карте.
     */
    suspend fun bench(characterId: String): List<BenchRecipe> =
        bench(characterRepository.requireCharacter(characterId, "bench"))

    fun bench(character: Character): List<BenchRecipe> {
        val known = character.knownBenchRecipes.toHashSet()
        return CraftingBench.recipes.filter { it.code in known }
    }

    /**
     * Ставит на предмет ремесленный модификатор верстака.
     *
     * Как и сфера, оплата списывается в одной транзакции с сохранением предмета:
     * отказ правила не съедает ни одной сферы.
     */
    suspend fun craft(characterId: String, inventoryId: String, recipeCode: String): CurrencyOutcome {
        val recipe = CraftingBench.recipe(recipeCode)
        val (item, template, character) = benchTarget("craft", characterId, inventoryId)

        return transactionExecute("craft ${recipe.code}") { session ->
            characterRepository.spendItem(character, recipe.orbItemId, recipe.amount, session)
            val outcome = CraftingBench.craft(item, template, recipe, character.knownBenchRecipes)
            update(outcome.item, session)
            outcome
        }
    }

    /**
     * Снимает с предмета ремесленный модификатор за [CraftingBench.UNCRAFT_ORB].
     */
    suspend fun uncraft(characterId: String, inventoryId: String): CurrencyOutcome {
        val (item, template, character) = benchTarget("uncraft", characterId, inventoryId)

        return transactionExecute("uncraft $inventoryId") { session ->
            characterRepository.spendItem(character, CraftingBench.UNCRAFT_ORB.name.toStableObjectId(), 1, session)
            val outcome = CraftingBench.uncraft(item, template)
            update(outcome.item, session)
            outcome
        }
    }

    private suspend fun benchTarget(method: String, characterId: String, inventoryId: String): Triple<CharacterEquipment, Equipment, Character> {
        val item = requireOwned(characterId, inventoryId, method)
        val template = templateOf(item, method)
        val character = characterRepository.requireCharacter(characterId, method)
        return Triple(item, template, character)
    }

    /** Персонажи, у которых есть хоть один экземпляр. */
    suspend fun owners(session: ClientSession): Set<String> =
        collection.distinct(session, "characterId", readFilter(), String::class.java).toList().toSet()

    /**
     * Удаляет весь инвентарь персонажа - вызывается при удалении самого персонажа.
     */
    suspend fun deleteByCharacter(characterId: String, session: ClientSession) {
        collection.deleteMany(session, Filters.eq("characterId", characterId))
    }

    /**
     * Удаляет предметы, ссылающиеся на уже несуществующие шаблоны экипировки.
     *
     * @param equipmentIds id всех актуальных шаблонов
     * @return количество удалённых документов
     */
    suspend fun deleteByMissingEquipment(equipmentIds: Collection<String>, session: ClientSession): Long =
        collection.deleteMany(session, Filters.nin("equipmentId", equipmentIds)).deletedCount

    /**
     * Возвращает в сумку самоцветы из гнёзд, которых больше нет в дереве (0.52.0: дерево построено
     * заново). Самоцвет - вещь игрока: он не пропадает вместе с узлом, а просто перестаёт быть вставленным.
     *
     * @return сколько самоцветов вернулось в сумку
     */
    suspend fun releaseMissingSockets(nodeCodes: Collection<String>, session: ClientSession): Long {
        if (nodeCodes.isEmpty()) return 0
        val stale = Filters.and(Filters.ne("socketCode", null), Filters.nin("socketCode", nodeCodes.toList()))
        return collection.updateMany(session, stale, Updates.set("socketCode", null)).modifiedCount
    }

    /**
     * Снимает с экземпляров модификаторы, описаний которых больше нет в справочнике: предмет
     * остаётся, пропадает только строка, которую уже нечем считать.
     *
     * @param modifierCodes коды всех актуальных описаний
     * @return сколько экземпляров что-то потеряли
     */
    suspend fun pruneMissingModifiers(modifierCodes: Collection<String>, session: ClientSession): Long {
        if (modifierCodes.isEmpty()) return 0
        val stale = org.bson.Document("modifierCode", org.bson.Document("\$nin", modifierCodes.toList()))
        return collection.updateMany(session, Filters.empty(), org.bson.Document("\$pull", org.bson.Document("params", stale))).modifiedCount
    }
}
