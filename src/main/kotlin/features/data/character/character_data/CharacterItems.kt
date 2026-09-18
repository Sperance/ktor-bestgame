package features.data.character.character_data

import CONST_ITEM_SEPARATOR
import base.exception.model.CharacterExceptions
import kotlinx.serialization.Serializable

/**
 * Простой (стакающийся) предмет инвентаря персонажа.
 *
 * В Mongo НЕ хранится объектом: в документе персонажа лежит плоский
 * массив строк вида "chaos_orb:50". Этот класс - только представление
 * такой строки в коде и в API.
 */
@Serializable
data class CharacterItems(
    var itemId: String,
    var amount: Long
) {
    /**
     * Строковое представление для хранения в Mongo: "itemId:amount".
     */
    fun toStorage(): String = "$itemId$CONST_ITEM_SEPARATOR$amount"

    companion object {
        /**
         * Разбирает строку хранения "itemId:amount".
         *
         * @throws CharacterExceptions.CharacterException если формат строки некорректен
         */
        fun parse(raw: String): CharacterItems {
            val separator = raw.lastIndexOf(CONST_ITEM_SEPARATOR)
            if (separator <= 0) throw CharacterExceptions.funExceptionItemFormat("parse", raw)

            val itemId = raw.substring(0, separator)
            val amount = raw.substring(separator + 1).toLongOrNull()
                ?: throw CharacterExceptions.funExceptionItemFormat("parse", raw)

            return CharacterItems(itemId, amount)
        }
    }
}

/**
 * Разбирает плоский массив хранения в список предметов.
 */
fun Collection<String>.toCharacterItems(): MutableList<CharacterItems> =
    mapTo(mutableListOf()) { CharacterItems.parse(it) }

/**
 * Сворачивает список предметов обратно в плоский массив хранения.
 */
fun Collection<CharacterItems>.toStorage(): MutableList<String> =
    mapTo(mutableListOf()) { it.toStorage() }
