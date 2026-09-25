package features.logic.pools

import base.entity.StockEntity
import extensions.toStableObjectId
import kotlinx.serialization.Serializable

/** Справочник, из которого пул тянет записи: его коды и лежат в [Pool.entries]. */
enum class EnumPoolTarget {
    /** Модификаторы предметов (`ModifierDefinition`). */
    MODIFIER,

    /** Модификаторы монстров кампании (`campaign.json`). */
    MONSTER,

    /** Шаблоны экипировки (`Equipment`): обычные базы, уникалки и мифические предметы. */
    EQUIPMENT,
}

/**
 * Вид пула. Уникалки и мифические предметы - те же шаблоны экипировки, но их пулы лежат
 * отдельно: пул `drop` у баз, уникалок и мифических предметов - три документа, которые при
 * тяге сливаются в один, а сид проверяет, что в пул вида попали только записи своей редкости.
 */
@Serializable
enum class EnumPoolKind(val target: EnumPoolTarget) {
    MODIFIER(EnumPoolTarget.MODIFIER),
    MONSTER(EnumPoolTarget.MONSTER),
    EQUIPMENT(EnumPoolTarget.EQUIPMENT),
    UNIQUE(EnumPoolTarget.EQUIPMENT),
    MYTHIC(EnumPoolTarget.EQUIPMENT),
}

/**
 * Пул (коллекция Mongo `Pool`, с 0.56.0): один документ на пару (вид, тег).
 *
 * До 0.56.0 каждая запись сама несла свои `pools`; теперь все пулы лежат в одном месте
 * (`content/pools.json`) и правятся администратором как справочник. Источники по-прежнему
 * называют теги - `helmet`, `local:armor`, `influence:SHAPER`, `drop`, `boss:<код>`.
 *
 * @property code тег пула
 * @property entries код записи -> вес; вес 0 исключает запись из тяги по этому тегу
 */
@Serializable
data class Pool(
    val code: String,
    val kind: EnumPoolKind,
    val entries: Map<String, Int> = emptyMap(),
    override var _id: String = idOf(kind, code),
) : StockEntity {
    companion object {
        /** Стабильный `_id`: пулы пересеваются на каждом старте. */
        fun idOf(kind: EnumPoolKind, code: String): String = "pool:${kind.name}:$code".toStableObjectId()
    }
}
