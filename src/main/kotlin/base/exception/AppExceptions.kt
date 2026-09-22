package base.exception

import kotlinx.serialization.Serializable

/**
 * Отказ сервера.
 *
 * [message] - готовое английское предложение, [errorCode] - код, по которому
 * клиент находит свой шаблон в словаре. Шаблон почти всегда с дыркой
 * ("Уровень {0} слишком мал"), а сама дырка заполнена тут, внутри [message],
 * поэтому клиенту нужны ещё и аргументы - иначе он вынужден показывать
 * английское предложение целиком.
 *
 * [messageArgs] - это они: значения ровно в том порядке, в каком шаблон их ждёт.
 */
@Serializable
open class BaseException(
    override val message: String?,
    val errorClass: String,
    val errorMethod: String?,
    val errorCode: String,
    val messageArgs: List<String> = emptyList()
) : RuntimeException(message)