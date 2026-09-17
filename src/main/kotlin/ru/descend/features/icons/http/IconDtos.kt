package ru.descend.features.icons.http

import kotlinx.serialization.Serializable
import ru.descend.domain.icons.IconArt
import ru.descend.domain.icons.IconCatalog
import ru.descend.domain.icons.IconCategory
import ru.descend.domain.icons.IconRenderer

/** Описание одной иконки для клиента: где взять картинку и какими цветами рисовать свою. */
@Serializable
@kotlinx.serialization.SerialName("features.icons.IconDescriptor")
data class IconDescriptor(
    val id: String,
    val title: String,
    val category: IconCategory,
    val tint: String,
    val deep: String,
    val url: String,
    val keywords: List<String> = emptyList()
) {
    companion object {
        fun of(art: IconArt) = IconDescriptor(art.id, art.title, art.category, art.tint, art.deep,
            IconCatalog.url(art.id), art.keywords)
    }
}

/** Список набора. По [version] клиент понимает, что кэш можно не сбрасывать. */
@Serializable
@kotlinx.serialization.SerialName("features.icons.IconManifest")
data class IconManifest(
    val set: String = IconRenderer.SET_ID,
    val revision: Int = IconRenderer.SET_REVISION,
    val version: String = IconCatalog.version,
    val format: String = "svg",
    val viewBox: Int = IconRenderer.VIEW_BOX,
    val sprite: String = "/api/v1/icons/sprite.svg",
    val fallback: String = IconCatalog.FALLBACK,
    val total: Int,
    val categories: Map<IconCategory, Int>,
    val icons: List<IconDescriptor>
)

/**
 * Таблицы соответствий: по ним клиент подбирает иконку сам, без запроса на каждый предмет.
 *
 * Ключи — это ровно те значения, которые уже приходят в остальных ответах API:
 * идентификаторы статов, теги каталога, классы предметов, enum'ы оружия, слотов,
 * редкости, валют, узлов дерева и действий боя.
 */
@Serializable
@kotlinx.serialization.SerialName("features.icons.IconBindingTables")
data class IconBindingTables(
    val version: String = IconCatalog.version,
    val fallback: String = IconCatalog.FALLBACK,
    val stats: Map<String, String>,
    val tags: Map<String, String>,
    val modifierSources: Map<String, String>,
    val itemClasses: Map<String, String>,
    val weapons: Map<String, String>,
    val slots: Map<String, String>,
    val rarities: Map<String, String>,
    val poeRarities: Map<String, String>,
    val currencies: Map<String, String>,
    val passiveKinds: Map<String, String>,
    val battleActions: Map<String, String>,
    val battleStatuses: Map<String, String>,
    val combatElements: Map<String, String>,
    val modifiers: Map<String, String>,
    val bases: Map<String, String>
)
