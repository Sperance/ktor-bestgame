package features.icons

import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.*
import org.xml.sax.InputSource
import ru.descend.domain.enums.EnumEquipmentType
import ru.descend.domain.enums.EnumEquipmentWeapon
import ru.descend.domain.enums.EnumRarity
import ru.descend.domain.icons.IconCatalog
import ru.descend.domain.icons.IconResolver
import ru.descend.features.combat.domain.BattleAction
import ru.descend.features.combat.domain.BattleStatus
import ru.descend.features.combat.domain.CombatWorld
import ru.descend.features.equipment.model.Weapon
import ru.descend.features.icons.IconBindings
import ru.descend.features.passives.model.PassiveNodeKind
import ru.descend.features.poe.catalog.PoeCatalog
import ru.descend.features.poe.catalog.objects
import ru.descend.features.poe.catalog.string
import ru.descend.features.poe.domain.PoeCurrency

/** Набор иконок — часть контракта API: он обязан быть полным, валидным и предсказуемым. */
class IconCatalogTest {

    private fun parse(xml: String) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(InputSource(xml.reader()))

    @Test fun everyIconRendersValidSvgInBothVariants() {
        assertTrue(IconCatalog.icons.size >= 100, "Набор должен покрывать весь контент: ${IconCatalog.icons.size}")
        for (art in IconCatalog.icons) {
            for (framed in listOf(true, false)) {
                val svg = assertNotNull(IconCatalog.svg(art.id, framed), art.id)
                val root = parse(svg).documentElement
                assertEquals("svg", root.tagName, art.id)
                assertEquals("0 0 64 64", root.getAttribute("viewBox"), art.id)
                assertFalse(svg.contains("http://") && !svg.contains("http://www.w3.org/2000/svg"), "Внешние ссылки в ${art.id}")
                assertFalse(svg.contains("<image", true), "Растровые вставки в ${art.id}")
                assertTrue(svg.contains("url(#edge-${art.id})"), art.id)
            }
        }
    }

    @Test fun spriteContainsEverySymbolAndParses() {
        val sprite = IconCatalog.sprite
        parse(sprite)
        IconCatalog.icons.forEach { assertTrue(sprite.contains("""id="icon-${it.id}""""), it.id) }
        // Градиенты разведены по иконкам: в одном документе не должно быть повторных идентификаторов.
        val ids = Regex("""id="([^"]+)"""").findAll(sprite).map { it.groupValues[1] }.toList()
        assertEquals(ids.size, ids.distinct().size, "Дубликаты id в спрайте")
    }

    @Test fun renderingIsDeterministicAndIconsDiffer() {
        val first = requireNotNull(IconCatalog.svg("weapon-sword"))
        assertEquals(first, IconCatalog.svg("weapon-sword"))
        assertEquals(IconCatalog.etag("weapon-sword"), IconCatalog.etag("weapon-sword"))
        assertNotEquals(IconCatalog.etag("weapon-sword"), IconCatalog.etag("weapon-axe"))
        assertNotEquals(IconCatalog.etag("weapon-sword", framed = true), IconCatalog.etag("weapon-sword", framed = false))
        assertEquals(32, IconCatalog.version.length)
    }

    @Test fun capabilitiesAdvertiseTheSetTheServerActuallyServes() {
        val capabilities = ru.descend.features.poe.domain.PoeCapabilities()
        assertTrue(capabilities.icons)
        assertEquals(IconCatalog.version, capabilities.iconSetVersion)
        assertEquals(IconCatalog.icons.size, capabilities.iconCount)
        assertEquals("/api/v1/icons", capabilities.iconsEndpoint)
        assertTrue(capabilities.apiRevision >= 4)
    }

    @Test fun bindingsPointAtExistingIcons() {
        IconBindings.validate()
        assertTrue(IconCatalog.exists(IconCatalog.FALLBACK))
    }

    @Test fun everyEnumOfTheGameHasItsOwnIcon() {
        EnumEquipmentWeapon.entries.forEach { assertTrue(IconCatalog.exists(IconResolver.forWeapon(it)), it.name) }
        assertEquals(EnumEquipmentWeapon.entries.size, EnumEquipmentWeapon.entries.map(IconResolver::forWeapon).distinct().size,
            "У каждого типа оружия свой рисунок")
        EnumEquipmentType.entries.forEach { assertTrue(IconCatalog.exists(IconResolver.forSlot(it)), it.name) }
        EnumRarity.entries.forEach { assertTrue(IconCatalog.exists(IconResolver.forRarity(it)), it.name) }
        assertEquals(EnumRarity.entries.size, EnumRarity.entries.map(IconResolver::forRarity).distinct().size)
        PoeCurrency.entries.forEach { assertTrue(IconCatalog.exists(IconBindings.currencies.getValue(it)), it.name) }
        assertEquals(PoeCurrency.entries.size, IconBindings.currencies.values.distinct().size, "У каждой сферы свой рисунок")
        PassiveNodeKind.entries.forEach { assertTrue(IconCatalog.exists(IconBindings.passiveKinds.getValue(it)), it.name) }
        BattleAction.entries.forEach { assertTrue(IconCatalog.exists(IconBindings.battleActions.getValue(it)), it.name) }
        BattleStatus.entries.forEach { assertTrue(IconCatalog.exists(IconBindings.battleStatuses.getValue(it)), it.name) }
    }

    @Test fun everyCatalogModifierResolvesToAMeaningfulIcon() {
        val catalog = PoeCatalog.bundled
        val unresolved = catalog.mods.keys.filter { id ->
            val icon = IconBindings.bundledModifiers.getValue(id)
            !IconCatalog.exists(icon) || icon == IconCatalog.FALLBACK
        }
        assertTrue(unresolved.isEmpty(), "Модификаторы без иконки: $unresolved")
        // Иконка отражает смысл модификатора, а не только его тип аффикса.
        val statIcons = catalog.mods.values.flatMap { it.objects("stats") }.map { it.string("id") }
            .distinct().associateWith { IconResolver.forStat(it) }
        val missingStats = statIcons.filterValues { it == null || !IconCatalog.exists(it) }.keys
        assertTrue(missingStats.isEmpty(), "Статы без иконки: $missingStats")
    }

    @Test fun everyCatalogBaseResolvesToAMeaningfulIcon() {
        val unresolved = PoeCatalog.bundled.bases.filterValues { base ->
            IconBindings.forBase(base).let { !IconCatalog.exists(it) || it == IconCatalog.FALLBACK }
        }.keys
        assertTrue(unresolved.isEmpty(), "Базовые предметы без иконки: $unresolved")
    }

    @Test fun catalogEquipmentCarriesItsIcon() {
        val catalog = PoeCatalog.bundled
        for ((name, expected) in listOf("Rusted Sword" to "weapon-sword", "Iron Hat" to "armour-helmet", "Iron Ring" to "jewellery-ring")) {
            val id = catalog.bases.entries.first { it.value.string("name") == name }.key
            val item = catalog.equipment(id)
            assertEquals(expected, item.icon, name)
            assertTrue(IconCatalog.exists(requireNotNull(item.icon)))
        }
        val weapon = catalog.releasedWearables().keys.map(catalog::equipment).filterIsInstance<Weapon>().first()
        assertEquals(IconResolver.forWeapon(weapon.weaponType), weapon.icon)
    }

    @Test fun legacyDocumentsWithoutAStoredIconStillGetOne() {
        val catalog = PoeCatalog.bundled
        val id = catalog.bases.entries.first { it.value.string("name") == "Rusted Sword" }.key
        val legacy = catalog.equipment(id).also { it.icon = null }
        assertEquals("weapon-sword", IconBindings.resolvedIcon(legacy), "Иконка берётся по базе каталога")
        legacy.poeBaseId = null
        assertEquals("weapon-sword", IconBindings.resolvedIcon(legacy), "Без базы — по типу оружия")
        val item = ru.descend.features.items.model.Items(name = "Chaos Orb", category = "POE",
            subCategory = "StackableCurrency", poeBaseId = null)
        assertEquals("currency-chaos", IconBindings.resolvedIcon(item))
    }

    @Test fun resolverPicksTheStatBehindTheModifier() {
        val cases = mapOf(
            "base_maximum_life" to "stat-life",
            "base_fire_damage_resistance_%" to "stat-fire-resistance",
            "attack_minimum_added_cold_damage" to "stat-cold-damage",
            "local_base_physical_damage_reduction_rating" to "stat-armour",
            "local_attack_speed_+%" to "stat-attack-speed",
            "critical_strike_chance_+%" to "stat-critical",
            "spell_damage_+%" to "stat-spell-damage",
            "additional_all_attributes" to "stat-attributes",
            "unknown_fire_penetration_%" to "stat-fire-damage",
            "unknown_maximum_mana_+%" to "stat-mana"
        )
        cases.forEach { (stat, icon) -> assertEquals(icon, IconResolver.forStat(stat), stat) }
        assertNull(IconResolver.forStat("completely_unrelated_property"))
    }

    @Test fun combatWorldIsFullyIllustrated() {
        val decorated = IconBindings.decorate(CombatWorld.initial)
        decorated.zones.forEach { zone ->
            assertTrue(IconCatalog.exists(requireNotNull(zone.icon)), zone.id)
            (zone.monsters + zone.boss).forEach { assertTrue(IconCatalog.exists(requireNotNull(it.icon)), it.id) }
            assertEquals("combat-boss", zone.boss.icon)
        }
    }
}
