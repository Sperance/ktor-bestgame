package features.data.character.character_data

import kotlinx.serialization.Serializable

/**
 * Простой (стакающийся) предмет сумки персонажа - как он ходит по API.
 *
 * В Mongo сумка с 0.49.0 лежит картой `bag: {itemId: amount}`: трата - точечный `$inc`
 * по ключу, без разбора строк и без записи документа целиком.
 */
@Serializable
data class CharacterItems(
    var itemId: String,
    var amount: Long
)

/** Сумка списком стаков для ответа; пустые стаки наружу не выходят. */
fun Map<String, Long>.toCharacterItems(): MutableList<CharacterItems> =
    entries.filter { it.value > 0 }.mapTo(mutableListOf()) { CharacterItems(it.key, it.value) }
