package features.data.character

import application.enums.EnumStatBattle
import application.enums.EnumStatBool
import application.enums.EnumStatProfession
import extensions.now
import base.entity.VersionedEntity
import features.data.character.character_data.CharacterBattleSkill
import features.data.character.character_data.CharacterBoolSkill
import features.data.character.character_data.CharacterItems
import features.data.character.character_data.toCharacterItems
import features.data.character.character_data.CharacterProfessionSkill
import features.data.character.character_data.CharacterSkillNode
import features.data.character.character_data.GainedRedemtionCodes
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class Character(
    var userId: String,

    var name: String,
    var description: String = "",
    var level: Short = 1,
    var experience: Double = 0.0,
    var money: Long = 0,

    /**
     * Класс персонажа - ссылка на `CharacterClass._id`.
     *
     * Задаёт базу характеристик и стартовый узел дерева навыков.
     * Хранится ссылкой, а не снимком: база класса это константа мира,
     * и её перебалансировка должна доезжать до всех персонажей.
     */
    var classId: String = "",

    /**
     * Взятые узлы дерева навыков - снимки узлов на момент взятия.
     *
     * Снимок, а не ссылка: так бонусы конкретного узла можно править
     * конкретному герою, не трогая дерево и остальных персонажей.
     * Форма дерева при этом общая для всех, см. [CharacterSkillNode].
     *
     * Стартовый узел класса лежит здесь с момента создания персонажа.
     */
    var skillNodes: MutableList<CharacterSkillNode> = mutableListOf(),

    /**
     * Простые (стакающиеся) предметы плоским массивом строк "itemId:amount",
     * например "chaos_orb:50".
     *
     * Экипировка здесь не хранится - каждый её экземпляр это отдельный
     * документ коллекции `CharacterEquipment`.
     */
    /** Сумка: id предмета - сколько (с 0.49.0 картой, трата - точечный `$inc`). */
    var bag: MutableMap<String, Long> = mutableMapOf(),
    var professionSkills: MutableList<CharacterProfessionSkill> = mutableListOf(),
    var battleSkills: MutableList<CharacterBattleSkill> = mutableListOf(),
    var boolSkills: MutableList<CharacterBoolSkill> = mutableListOf(),
    var recipeAccess: MutableList<String> = mutableListOf(),
    var gainedRedemptionCodes: MutableList<GainedRedemtionCodes> = mutableListOf(),

    /**
     * Пройденные зоны карты мира - их коды, с 0.26.0; с 0.67.0 зону проходит убитый босс. Зона
     * открывается, когда пройдена хоть одна из тех, что ведут к ней, см. [features.logic.campaign.CampaignContent.unlocked].
     */
    var campaign: MutableList<String> = mutableListOf(),

    /** Сундуки кампании по кодам карт (с 0.31.0): окно и сколько в нём осталось, см. [features.logic.campaign.CampaignChests]. */
    var chests: MutableMap<String, features.logic.campaign.ChestWindow> = mutableMapOf(),

    /** Когда босс карты вернётся (с 0.32.0), миллисекунды эпохи по кодам карт; до тех пор он мёртв и выход открыт. */
    var bosses: MutableMap<String, Long> = mutableMapOf(),

    /** Карта, с которой герой вошёл в локацию (с 0.35.0); её бонус ложится на добычу только этой локации. */
    var activeMap: features.logic.campaign.ActiveMap? = null,

    /** Профессии героя (с 0.37.0): уровень и опыт по коду профессии. */
    var professions: MutableMap<String, features.logic.crafts.ProfessionProgress> = mutableMapOf(),

    /** Работа, которую герой ведёт сейчас, - одна на героя (с 0.37.0). */
    var work: features.logic.crafts.ActiveWork? = null,

    /** Получил ли герой стартовые сферы и вещи сидинга: иначе он получал бы их на каждом рестарте, растратив. */
    var starterGranted: Boolean = false,

    /** Выдан ли стартовый набор инструментов (с 0.37.0): выдаётся один раз. */
    var toolsGranted: Boolean = false,

    /** Витрина торговца этого героя (с 0.34.0), см. [features.logic.trade.MerchantRules]. */
    var merchant: features.logic.trade.MerchantStock? = null,

    /** Сколько мест под лоты аукциона герой докупил сверх базовых (с 0.34.0). */
    var auctionSlots: Int = 0,

    /** Коды рецептов верстака, найденные на картах (с 0.46.0); только эти можно применить, см. [features.logic.bench.CraftingBench]. */
    var knownBenchRecipes: MutableList<String> = mutableListOf(),

    /** Выпал ли уже рецепт верстака на активной карте (с 0.46.0): не больше одного за заход, сбрасывается входом на карту. */
    var mapRecipeRolled: Boolean = false,

    /** Открыта ли уже осквернённая зона на активной карте (с 0.46.0): не больше одной за заход, сбрасывается входом на карту. */
    var corruptionOpened: Boolean = false,

    /** Ваал-зона активной карты (с 0.57.0), выпавшая за её порталом и ещё не закрытая; см. [features.logic.campaign.VaalZones]. */
    var vaalZone: features.logic.campaign.VaalZone? = null,

    /** Взятые узлы атласа (с 0.60.0), без корня - он взят всегда; см. [features.logic.atlas.AtlasService]. */
    var atlasNodes: MutableList<String> = mutableListOf(),

    /** Засчитанные достижения атласа (с 0.60.0): `boss:<зона>` (с 0.67.0 вместо `exit:`), `rare:<зона>`, `vaal:<зона>` - каждое один раз. */
    var atlasEarned: MutableList<String> = mutableListOf(),

    /**
     * Умения класса (с 0.69.0): изученные уровни, три слота активных и два пассивных с условиями и
     * условия глотков фляг пояса, см. [features.logic.skills.SkillService]. Новый герой знает первое
     * активное и первое пассивное умение своего класса.
     */
    var skills: features.logic.skills.HeroSkills = features.logic.skills.HeroSkills(),

    /** Кристаллы эссенций кампании по кодам карт (с 0.69.0): окно, как у сундуков, и что в каждом, см. [features.logic.campaign.EssenceCrystals]. */
    var crystals: MutableMap<String, features.logic.campaign.CrystalWindow> = mutableMapOf(),

    /** Растёт на каждую запись в инвентарь героя: по нему клиент и лист статов узнают, что вещи сменились. */
    var inventoryRevision: Long = 0,
    override var _id: String = ObjectId().toHexString(),
    override var version: Long = 0,
    override var deleted: Boolean = false,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override var updatedAt: LocalDateTime = LocalDateTime.now(),
) : VersionedEntity {

    /**
     * Простые предметы, разобранные из плоского массива хранения.
     */
    fun parseItems(): MutableList<CharacterItems> = bag.toCharacterItems()

}