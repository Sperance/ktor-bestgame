import application.enums.EnumAuctionLotKind
import features.logic.locale.ModifierText
import application.enums.EnumMonsterRarity
import application.enums.EnumAuctionLotStatus
import application.enums.EnumCurrencyOrb
import application.enums.EnumInfluence
import application.enums.EnumEquipmentType
import application.enums.EnumEquipmentWeapon
import application.enums.EnumModifierOperation
import application.enums.EnumModifierSource
import application.enums.EnumRarity
import application.enums.EnumSkillNodeType
import application.enums.EnumStatBattle
import application.enums.EnumStatBool
import application.enums.EnumStatProfession
import application.enums.EnumStatStock
import application.enums.EnumUserRoles
import base.exception.ApplicationExceptions
import base.exception.BaseException
import base.exception.BaseRepositoryExceptions
import base.exception.BaseRouteExceptions
import base.exception.model.AuctionExceptions
import base.exception.model.AuthExceptions
import base.exception.model.CampaignExceptions
import base.exception.model.CharacterExceptions
import base.exception.model.CurrencyExceptions
import base.exception.model.EquipmentExceptions
import base.exception.model.ItemsExceptions
import base.exception.model.LocaleExceptions
import base.exception.model.ModifierExceptions
import base.exception.model.ProgressionExceptions
import base.exception.model.RedemptionCodesExceptions
import base.exception.model.SkillTreeExceptions
import base.exception.model.UserExceptions
import config.CurrencySeeder
import config.EquipmentSeeder
import config.ItemsSeeder
import config.ModifierSeeder
import config.ProgressionSeeder
import config.SkillTreeSeeder
import config.UniqueEquipmentSeeder
import features.logic.campaign.CampaignContent
import features.logic.campaign.EnumLootKind
import features.logic.locale.LocaleCache
import features.logic.locale.LocaleKey
import features.logic.modifiers.ModifierDefinition
import org.junit.Test
import java.security.MessageDigest
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredMembers

/**
 * Локализация: словари и справочники не должны расходиться.
 *
 * Коды рождаются в сидерах и перечислениях, а текст правится руками в
 * `resources/locale`. Этот тест - единственное, что держит их вместе:
 * он собирает ожидаемые ключи из кода и сверяет с обоими файлами.
 *
 * Mongo не нужна: сидеры чистые, словари читаются из ресурсов.
 */
class LocalizationTest {

    private val definitions: List<ModifierDefinition> =
        ModifierSeeder.seedDefinitions() + UniqueEquipmentSeeder.seedDefinitions()

    init {
        if (LocaleCache.isEmpty()) LocaleCache.initializeCache()
    }

    /**
     * Перечисления, чьи подписи обязаны быть в словаре.
     */
    private val enums: Map<String, List<Enum<*>>> = mapOf(
        "EnumRarity" to EnumRarity.entries,
        "EnumEquipmentType" to EnumEquipmentType.entries,
        "EnumEquipmentWeapon" to EnumEquipmentWeapon.entries,
        "EnumSkillNodeType" to EnumSkillNodeType.entries,
        "EnumModifierSource" to EnumModifierSource.entries,
        "EnumModifierOperation" to EnumModifierOperation.entries,
        "EnumCurrencyOrb" to EnumCurrencyOrb.entries,
        "EnumInfluence" to EnumInfluence.entries,
        "EnumAuctionLotKind" to EnumAuctionLotKind.entries,
        "EnumAuctionLotStatus" to EnumAuctionLotStatus.entries,
        "EnumUserRoles" to EnumUserRoles.entries,
        "EnumStatStock" to EnumStatStock.entries,
        "EnumStatBool" to EnumStatBool.entries,
        "EnumStatProfession" to EnumStatProfession.entries,
        "EnumStatBattle" to EnumStatBattle.entries,
        "EnumMonsterRarity" to EnumMonsterRarity.entries,
        "EnumLootKind" to EnumLootKind.entries,
    )

    /**
     * Объекты с сообщениями об ошибках. Коды из них идут в ключи `error.*`.
     */
    private val exceptionObjects: List<KClass<*>> = listOf(
        ApplicationExceptions::class, BaseRepositoryExceptions::class, BaseRouteExceptions::class,
        AuctionExceptions::class, AuthExceptions::class, CampaignExceptions::class, CharacterExceptions::class, CurrencyExceptions::class,
        EquipmentExceptions::class, ItemsExceptions::class, LocaleExceptions::class,
        ModifierExceptions::class, ProgressionExceptions::class, base.exception.model.PoolExceptions::class,
        RedemptionCodesExceptions::class, SkillTreeExceptions::class, UserExceptions::class,
        base.exception.model.ProfessionExceptions::class, base.exception.model.AtlasExceptions::class,
        base.exception.model.SkillExceptions::class,
    )

    /**
     * Ключи сообщений о применении сфер. Живут в CurrencyApplier и
     * перечислены здесь: добавили сообщение - добавьте и сюда, иначе
     * его некому будет поймать.
     */
    private val currencyKeys = listOf(
        "upgraded", "rerolled", "augmented", "regal", "divine", "blessed", "annulled",
        "scoured", "vaal_modifier", "vaal_nothing", "vaal_rare", "vaal_shift", "chance_unique", "chance_rarity", "mirrored",
        "scoured_fractured", "fractured", "influenced", "crafted", "uncrafted",
        "empowered", "mercy", "peril", "alchemy_line", "enchanted", "bauble", "essence",
    ).map { "${LocaleKey.CURRENCY}.$it" }

    /**
     * Служебные ответы сервера: он отдаёт ключ вместо готовой фразы.
     */
    private val systemKeys = listOf(
        "success", "no_changes", "deleted", "access_denied", "blocked",
    ).map { "system.$it" }

    /**
     * Все ключи, которые обязан покрывать каждый словарь.
     */
    private fun expectedKeys(): Set<String> {
        val keys = mutableSetOf<String>()

        // Текст модификатора (0.66.0) собирается из шаблонов эффектов: словарь держит шаблон на
        // стат и операцию, а строку на код - только как исключение для особой формулировки.
        definitions.forEach { keys.add(LocaleKey.modifierName(it.code)) }
        keys.addAll(ModifierText.keys(definitions))

        EquipmentSeeder(definitions).seed().forEach {
            keys.add(LocaleKey.equipmentName(it.code))
            keys.add(LocaleKey.equipmentTrade(it.code))
            keys.add(LocaleKey.equipmentDescription(it.code))
        }

        SkillTreeSeeder.seed(definitions).forEach { node ->
            keys.add(LocaleKey.skillNodeName(node.code))
            // Описание есть у всего, кроме малых узлов: им хватает названия
            if (node.type != EnumSkillNodeType.SMALL) keys.add(LocaleKey.skillNodeDescription(node.code))
        }

        ProgressionSeeder.seedClasses(definitions).forEach {
            keys.add(LocaleKey.className(it.code))
            keys.add(LocaleKey.classDescription(it.code))
        }

        (CurrencySeeder.seed() + ItemsSeeder.seed()).forEach {
            keys.add(LocaleKey.itemName(it.code))
            keys.add(LocaleKey.itemTrade(it.code))
            keys.add(LocaleKey.itemDescription(it.code))
        }

        // Кампания: регионы карты мира, зоны с описанием, монстры и их модификаторы
        CampaignContent.file.regions.forEach { region ->
            keys.add(LocaleKey.regionName(region.code))
            region.zones.forEach {
                keys.add(LocaleKey.mapName(it.code))
                keys.add(LocaleKey.mapDescription(it.code))
            }
        }
        CampaignContent.file.monsters.forEach { keys.add(LocaleKey.monsterName(it.code)) }

        // Ремёсла (0.37.0): профессии с описанием и их работы
        features.logic.crafts.CraftsContent.file.professions.forEach { profession ->
            keys.add(LocaleKey.professionName(profession.code))
            keys.add(LocaleKey.professionDescription(profession.code))
            profession.jobs.forEach { keys.add(LocaleKey.jobName(it.code)) }
        }

        // Атлас (0.60.0): название каждого узла
        features.logic.atlas.AtlasContent.tree.nodes.forEach { keys.add(LocaleKey.atlasNodeName(it.code)) }

        // Умения классов и монстров, ступени эссенций и стражи кристаллов (0.69.0)
        val book = features.logic.skills.SkillContent.book
        (book.skills.map { it.code } + book.monsterSkills.map { it.code }).forEach { keys.add(LocaleKey.skillName(it)) }
        val essences = features.logic.essences.EssenceContent.book
        essences.tiers.forEach { keys.add(LocaleKey.essenceTier(it.code)) }
        (essences.kinds + essences.specials).forEach { keys.add(LocaleKey.essenceMonster(it.code)) }

        enums.forEach { (name, values) -> values.forEach { keys.add(LocaleKey.enumLabel(name, it.name)) } }

        errorCodes().forEach { keys.add(LocaleKey.error(it)) }

        keys.addAll(currencyKeys)
        keys.addAll(systemKeys)

        return keys
    }

    /**
     * Коды ошибок, собранные вызовом каждой фабрики исключений.
     */
    private fun errorCodes(): Set<String> = exceptionObjects.flatMapTo(mutableSetOf()) { cls ->
        val instance = cls.objectInstance ?: return@flatMapTo emptyList()

        cls.declaredMembers
            .filterIsInstance<KFunction<*>>()
            .filter { it.name.startsWith("funException") }
            .mapNotNull { function ->
                val args = function.parameters.associateWith { parameter ->
                    if (parameter.type.classifier == cls) instance else "?"
                }
                (function.callBy(args) as? BaseException)?.errorCode
            }
    }

    // ==================== Проверки ====================

    @Test
    fun the_manifest_lists_the_languages_that_really_exist() {
        val manifest = LocaleCache.manifest()

        // Список языков не зашит: добавили язык - добавили файл и строку в манифест,
        // а тест проверяет, что за каждой объявленной строкой стоит читаемый словарь
        assert(manifest.languages.isNotEmpty()) { "манифест не объявляет ни одного языка" }
        assert(manifest.default in manifest.languages.map { it.code }) {
            "язык по умолчанию '${manifest.default}' не объявлен в манифесте"
        }
        manifest.languages.forEach {
            assert(it.label.isNotBlank()) { "${it.code}: нет подписи для меню выбора языка" }
            assert(LocaleCache.bundle(it.code).size > 0) { "${it.code}: словарь пуст или не прочитан" }
        }
    }

    @Test
    fun the_manifest_hashes_match_what_is_served() {
        // Отпечаток - единственное, по чему клиент понимает, что словарь обновился.
        // Отдаётся склейка общего и языкового файла, поэтому и отпечаток - от неё.
        LocaleCache.manifest().languages.forEach { language ->
            val actual = hashOf(LocaleCache.document(language.code))
            assert(language.hash == actual) {
                "${language.code}: в манифесте '${language.hash}', у отдаваемого словаря '$actual'"
            }
        }
    }

    @Test
    fun every_dictionary_covers_every_key_the_code_asks_for() {
        val expected = expectedKeys()

        LocaleCache.languages().forEach { language ->
            val bundle = LocaleCache.bundle(language)

            val missing = expected - bundle.keys
            val extra = bundle.keys - expected

            assert(missing.isEmpty()) { "$language: нет ${missing.size} ключей, например ${missing.take(5)}" }
            assert(extra.isEmpty()) { "$language: лишние ${extra.size} ключей, например ${extra.take(5)}" }
        }
    }

    @Test
    fun the_languages_have_exactly_the_same_keys() {
        val sets = LocaleCache.languages().associateWith { LocaleCache.bundle(it).keys }
        val reference = sets.values.first()

        sets.forEach { (language, keys) ->
            assert(keys == reference) {
                "$language расходится с остальными: ${(keys - reference) + (reference - keys)}"
            }
        }
    }

    /**
     * Торговое имя предмета, экипировки и сферы — английское, одно на все языки, как в PoE: им
     * ищут на аукционе. С 0.45.0 оно лежит один раз в `common.json` как `.trade`, а само `.name`
     * переводится; в английском словаре имя и торговое имя совпадают.
     */
    @Test
    fun traded_names_live_once_in_the_common_file() {
        val common = file(LocaleCache.COMMON)
        assert(common.isNotEmpty()) { "общий словарь пуст" }
        val stray = common.keys.filterNot(LocaleCache.commonKey::matches)
        assert(stray.isEmpty()) { "в ${LocaleCache.COMMON} попало переводимое: ${stray.take(5)}" }

        LocaleCache.languages().forEach { language ->
            val own = file("$language.json")
            val repeated = own.keys.filter(LocaleCache.commonKey::matches)
            assert(repeated.isEmpty()) { "$language.json повторяет имена из ${LocaleCache.COMMON}: ${repeated.take(5)}" }
            common.forEach { (key, name) ->
                assert(LocaleCache.bundle(language)[key] == name) { "$language: $key не дошёл до словаря" }
            }
        }
        val english = file("en.json")
        common.forEach { (key, trade) ->
            val name = key.removeSuffix(".${LocaleKey.TRADE}") + ".${LocaleKey.NAME}"
            assert(english[name] == trade) { "en: $name расходится с торговым именем «$trade»" }
        }
    }

    /** Файл словаря как он лежит в ресурсах, до склейки. */
    private fun file(name: String): Map<String, String> =
        kotlinx.serialization.json.Json.parseToJsonElement(javaClass.classLoader.getResource("${LocaleCache.FOLDER}/$name")!!.readText())
            .let { it as kotlinx.serialization.json.JsonObject }
            .mapValues { (it.value as kotlinx.serialization.json.JsonPrimitive).content }

    @Test
    fun nothing_is_left_untranslated_or_empty() {
        LocaleCache.languages().forEach { language ->
            val bundle = LocaleCache.bundle(language)

            bundle.keys.forEach { key ->
                assert(bundle[key].isNotBlank()) { "$language: пустая строка у $key" }
            }
        }
    }

    @Test
    fun a_placeholder_never_disappears_in_translation() {
        // Число подставляется по номеру, поэтому набор {0}, {1}... обязан
        // совпадать во всех языках - иначе значение просто потеряется
        val placeholder = Regex("\\{\\|?\\d+\\|?}")
        val reference = LocaleCache.bundle(LocaleCache.defaultLanguage())

        LocaleCache.languages().filterNot { it == reference.language }.forEach { language ->
            val bundle = LocaleCache.bundle(language)

            reference.keys.forEach { key ->
                val expected = placeholder.findAll(reference[key]).map { it.value }.toSet()
                val actual = placeholder.findAll(bundle[key]).map { it.value }.toSet()

                assert(expected == actual) {
                    "$key: в ${reference.language} $expected, в $language $actual"
                }
            }
        }
    }

    @Test
    fun a_composite_modifier_has_a_placeholder_for_every_effect() {
        val placeholder = Regex("\\{\\|?(\\d+)\\|?}")

        definitions.forEach { definition ->
            val key = LocaleKey.modifierName(definition.code)

            LocaleCache.languages().forEach { language ->
                val text = LocaleCache.bundle(language)[key]
                val indexes = placeholder.findAll(text).map { it.groupValues[1].toInt() }.toSet()

                // Значение эффекта, которому некуда подставиться, игрок не увидит
                assert(indexes.all { it < definition.effects.size }) {
                    "$language / $key: номер плейсхолдера вне эффектов ${definition.effects.size}: $text"
                }
            }
        }
    }

    /** Шаблон эффекта (0.66.0) держит ровно один `{v}`; склейка и подпись источника - без него. */
    @Test
    fun every_effect_template_carries_its_value() {
        val templates = ModifierText.keys(definitions).filter { it.startsWith(ModifierText.SECTION + ".") && it != ModifierText.JOIN }
        assert(templates.isNotEmpty())
        LocaleCache.languages().forEach { language ->
            val bundle = LocaleCache.bundle(language)
            templates.forEach { key ->
                val text = bundle[key]
                assert(text.count { it == '{' } == text.count { it == '}' } && key.contains(".per") == text.contains("{s}")) { "$language / $key: $text" }
            }
        }
    }

    /** Каждое описание собирается в строку: модификатор без текста игрок увидел бы как код. */
    @Test
    fun every_definition_renders_from_its_templates() {
        LocaleCache.languages().forEach { language ->
            val bundle = LocaleCache.bundle(language)
            definitions.forEach { definition ->
                val text = bundle[LocaleKey.modifierName(definition.code)]
                assert(text != LocaleKey.modifierName(definition.code) && !text.contains("{v}")) { "$language: ${definition.code} reads «$text»" }
            }
        }
    }

    private fun hashOf(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)
}
