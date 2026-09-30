import base.exception.ApplicationExceptions
import base.exception.BaseException
import base.exception.BaseRepositoryExceptions
import base.exception.BaseRouteExceptions
import base.exception.model.AuctionExceptions
import base.exception.model.AuthExceptions
import base.exception.model.CampaignExceptions
import base.exception.model.CharacterExceptions
import base.exception.model.CurrencyExceptions
import base.exception.model.GuildExceptions
import base.exception.model.IdempotencyExceptions
import base.exception.model.LocaleExceptions
import base.exception.model.ProfessionExceptions
import base.exception.model.ProgressionExceptions
import base.exception.model.QuestExceptions
import base.exception.model.RedemptionCodesExceptions
import base.exception.model.SkillExceptions
import base.exception.model.SkillTreeExceptions
import base.exception.model.UserExceptions
import com.sperance.exileforge.rules.content.GuildLogKind
import com.sperance.exileforge.rules.content.GuildMode
import com.sperance.exileforge.rules.content.GuildRole
import com.sperance.exileforge.rules.content.Influence
import com.sperance.exileforge.rules.content.MonsterRarity
import com.sperance.exileforge.rules.content.Op
import com.sperance.exileforge.rules.content.Orb
import com.sperance.exileforge.rules.content.QuestKind
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.SkillNodeType
import com.sperance.exileforge.rules.content.Slot
import com.sperance.exileforge.rules.content.Source
import com.sperance.exileforge.rules.content.VariantKind
import com.sperance.exileforge.rules.content.WeaponType
import com.sperance.exileforge.rules.text.LocaleKey
import com.sperance.exileforge.rules.text.ModifierText
import config.ContentStore
import features.data.auction.LotKind
import features.data.auction.LotStatus
import features.logic.locale.LocaleCache
import org.junit.Test
import java.security.MessageDigest
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredMembers

/**
 * Локализация: словари и контент не должны расходиться. Коды рождаются в файлах контента и в коде
 * ошибок, текст правится руками в `resources/locale`; этот тест собирает ожидаемые ключи и сверяет с
 * каждым языком. Текст модификаторов собирают правила `rules` - их проверяет тест модуля.
 */
class LocalizationTest {

    private val index = ContentStore.load().index

    init {
        if (LocaleCache.isEmpty()) LocaleCache.initializeCache()
    }

    /** Перечисления, чьи подписи обязаны быть в словаре, под прежними именами клиента. */
    private val enums: Map<String, List<String>> = mapOf(
        "EnumRarity" to Rarity.entries.map { it.name },
        "EnumEquipmentType" to Slot.entries.map { it.name },
        "EnumEquipmentWeapon" to WeaponType.entries.map { it.name },
        "EnumSkillNodeType" to SkillNodeType.entries.map { it.name },
        "EnumModifierSource" to Source.entries.map { it.name },
        "EnumModifierOperation" to Op.entries.map { it.name },
        "EnumCurrencyOrb" to Orb.entries.map { it.name },
        "EnumInfluence" to Influence.entries.map { it.name },
        "EnumAuctionLotKind" to LotKind.entries.map { it.name },
        "EnumAuctionLotStatus" to LotStatus.entries.map { it.name },
        "EnumMonsterRarity" to MonsterRarity.entries.map { it.name },
    )

    private val exceptionObjects: List<KClass<*>> = listOf(
        ApplicationExceptions::class, BaseRepositoryExceptions::class, BaseRouteExceptions::class, AuctionExceptions::class, AuthExceptions::class,
        CampaignExceptions::class, CharacterExceptions::class, CurrencyExceptions::class, LocaleExceptions::class, ProgressionExceptions::class,
        RedemptionCodesExceptions::class, SkillTreeExceptions::class, UserExceptions::class, ProfessionExceptions::class, SkillExceptions::class, GuildExceptions::class,
        QuestExceptions::class, IdempotencyExceptions::class,
    )

    private val currencyKeys = listOf(
        "upgraded", "rerolled", "augmented", "regal", "divine", "blessed", "annulled", "scoured", "vaal_modifier", "vaal_nothing", "vaal_rare",
        "vaal_shift", "chance_unique", "chance_rarity", "mirrored", "scoured_fractured", "fractured", "influenced", "crafted", "uncrafted",
        "empowered", "mercy", "peril", "alchemy_line", "bauble", "essence",
    ).map { "${LocaleKey.CURRENCY}.$it" }

    private val systemKeys = listOf("success", "no_changes", "deleted", "access_denied", "blocked").map { "system.$it" }

    private fun expectedKeys(): Set<String> = buildSet {
        index.templates.values.forEach { add(LocaleKey.equipmentName(it.code)); add(LocaleKey.equipmentTrade(it.code)); add(LocaleKey.equipmentDescription(it.code)) }
        index.items.values.forEach { add(LocaleKey.itemName(it.code)); add(LocaleKey.itemTrade(it.code)); add(LocaleKey.itemDescription(it.code)) }
        index.tree.byCode.values.forEach { node ->
            add(LocaleKey.skillNodeName(node.code))
            if (node.type != SkillNodeType.SMALL) add(LocaleKey.skillNodeDescription(node.code))
        }
        index.classes.classes.forEach { add(LocaleKey.className(it.code)); add(LocaleKey.classDescription(it.code)) }
        index.campaign.regions.forEach { region ->
            add(LocaleKey.regionName(region.code))
            region.zones.forEach { add(LocaleKey.mapName(it.code)); add(LocaleKey.mapDescription(it.code)) }
        }
        index.monsters.keys.forEach { add(LocaleKey.monsterName(it)) }
        index.professions.professions.forEach { profession ->
            add(LocaleKey.professionName(profession.code)); add(LocaleKey.professionDescription(profession.code))
            profession.jobs.forEach { add(LocaleKey.jobName(it.code)) }
        }
        index.atlas.nodes.forEach { add(LocaleKey.atlasNodeName(it.code)) }
        (index.skills.skills.map { it.code } + index.skills.monsterSkills.map { it.code }).forEach { add(LocaleKey.skillName(it)) }
        index.essences.tiers.forEach { add(LocaleKey.essenceTier(it.code)) }
        (index.essences.kinds + index.essences.specials).forEach { add(LocaleKey.essenceMonster(it.code)) }
        // Подпись каждой характеристики: по ней общий шаблон собирает строку стата без своего шаблона
        index.stats.stats.forEach { add(LocaleKey.statLabel(it)) }
        enums.forEach { (name, values) -> values.forEach { add(LocaleKey.enumLabel(name, it)) } }
        errorCodes().forEach { add(LocaleKey.error(it)) }
        index.guilds.factions.forEach { add(LocaleKey.guildFactionName(it.code)); add(LocaleKey.guildFactionDescription(it.code)) }
        index.guilds.ranks.forEach { add(LocaleKey.guildRank(it.code)) }
        // Название задания в ответе «сдать всё» (1.22.0) - ключ словаря: у каждой цели и шага сюжета он свой
        index.quests.goals.forEach { add(LocaleKey.questTitle(QuestKind.DAILY, it.code)) }
        index.quests.story.flatMap { it.steps }.forEach { add(LocaleKey.questTitle(QuestKind.STORY, it.code)) }
        index.guilds.emblems.forEach { add(LocaleKey.guildEmblem(it)) }
        GuildRole.entries.forEach { add(LocaleKey.guildRole(it)) }
        GuildMode.entries.forEach { add(LocaleKey.guildMode(it)) }
        GuildLogKind.entries.forEach { add(LocaleKey.guildLog(it)) }
        addAll(currencyKeys)
        addAll(systemKeys)
    }

    private fun errorCodes(): Set<String> = exceptionObjects.flatMapTo(mutableSetOf()) { cls ->
        val instance = cls.objectInstance ?: return@flatMapTo emptyList()
        cls.declaredMembers.filterIsInstance<KFunction<*>>().filter { it.name.startsWith("funException") }.mapNotNull { function ->
            val args = function.parameters.associateWith { parameter -> if (parameter.type.classifier == cls) instance else "?" }
            (function.callBy(args) as? BaseException)?.errorCode
        }
    }

    @Test
    fun the_manifest_lists_the_languages_that_really_exist() {
        val manifest = LocaleCache.manifest()
        assert(manifest.languages.isNotEmpty()) { "манифест не объявляет ни одного языка" }
        assert(manifest.default in manifest.languages.map { it.code }) { "язык по умолчанию '${manifest.default}' не объявлен" }
        manifest.languages.forEach {
            assert(it.label.isNotBlank()) { "${it.code}: нет подписи для меню выбора языка" }
            assert(LocaleCache.bundle(it.code).size > 0) { "${it.code}: словарь пуст" }
        }
    }

    @Test
    fun the_manifest_hashes_match_what_is_served() {
        LocaleCache.manifest().languages.forEach { language ->
            val actual = MessageDigest.getInstance("SHA-256").digest(LocaleCache.document(language.code).toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
            assert(language.hash == actual) { "${language.code}: в манифесте '${language.hash}', у словаря '$actual'" }
        }
    }

    @Test
    fun every_dictionary_covers_every_key_the_content_asks_for() {
        val expected = expectedKeys()
        LocaleCache.languages().forEach { language ->
            val missing = expected - LocaleCache.bundle(language).keys
            assert(missing.isEmpty()) { "$language: нет ${missing.size} ключей, например ${missing.take(8)}" }
        }
    }

    @Test
    fun the_languages_have_exactly_the_same_keys() {
        val sets = LocaleCache.languages().associateWith { LocaleCache.bundle(it).keys }
        val reference = sets.values.first()
        sets.forEach { (language, keys) -> assert(keys == reference) { "$language расходится с остальными: ${((keys - reference) + (reference - keys)).take(8)}" } }
    }

    /** Каждое описание модификатора собирается в строку на каждом языке - иначе игрок увидел бы код. */
    @Test
    fun every_modifier_renders_in_every_language() {
        LocaleCache.languages().forEach { language ->
            val bundle = LocaleCache.bundle(language)
            val text = ModifierText(index.stats) { key -> bundle.strings[key] }
            val missing = index.definitions.filter { text.template(it) == null }.map { it.code }
            assert(missing.isEmpty()) { "$language: без текста ${missing.size} описаний, например ${missing.take(8)}" }
        }
    }

    /** Общий шаблон «Метка: +v» - запасной выход, а не текст: каждой характеристике модификатора нужен свой (1.14.0). */
    @Test
    fun every_stat_used_by_a_modifier_has_its_own_template() {
        LocaleCache.languages().forEach { language ->
            val strings = LocaleCache.bundle(language).strings
            val missing = index.definitions
                .filter { strings[LocaleKey.modifierName(it.code)] == null && (it.variant == VariantKind.NATURAL || strings[LocaleKey.modifierName(it.family)] == null) }
                .flatMap { def -> def.effects.mapIndexed { i, effect -> ModifierText.templateKey(effect, ModifierText.negative(def, i)) } }
                .filter { it !in strings }.distinct()
            assert(missing.isEmpty()) { "$language: нет ${missing.size} шаблонов, например ${missing.take(8)}" }
        }
    }

    @Test
    fun nothing_is_left_untranslated_or_empty() {
        LocaleCache.languages().forEach { language ->
            val bundle = LocaleCache.bundle(language)
            bundle.keys.forEach { key -> assert(bundle[key].isNotBlank()) { "$language: пустая строка у $key" } }
        }
    }

    @Test
    fun a_placeholder_never_disappears_in_translation() {
        val placeholder = Regex("\\{\\|?\\d+\\|?}")
        val reference = LocaleCache.bundle(LocaleCache.defaultLanguage())
        LocaleCache.languages().filterNot { it == reference.language }.forEach { language ->
            val bundle = LocaleCache.bundle(language)
            reference.keys.forEach { key ->
                val expected = placeholder.findAll(reference[key]).map { it.value }.toSet()
                val actual = placeholder.findAll(bundle[key]).map { it.value }.toSet()
                assert(expected == actual) { "$key: в ${reference.language} $expected, в $language $actual" }
            }
        }
    }
}
