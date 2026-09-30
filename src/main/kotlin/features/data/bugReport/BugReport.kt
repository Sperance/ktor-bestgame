package features.data.bugReport

import base.entity.VersionedEntity
import extensions.now
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

/** Что стало с отчётом: владелец меняет это поле прямо в Mongo. */
@Serializable
enum class BugStatus { NEW, DONE, WONTFIX }

/**
 * Отчёт об ошибке (1.46.0), как его прислала кнопка «Жучок»: текст игрока, где он был ([screen] и [context] -
 * вкладка, окно, герой, зона, версии, устройство) и хвост журнала запросов клиента без токенов. [userId] - аккаунт
 * живой сессии, если игрок вошёл; [address] - адрес отправителя. Интерфейса у коллекции нет - её читают в Mongo.
 */
@Serializable
data class BugReport(
    val text: String = "",
    val screen: String = "",
    val context: Map<String, String> = emptyMap(),
    val requests: List<String> = emptyList(),
    val userId: String? = null,
    val address: String = "",
    var status: BugStatus = BugStatus.NEW,

    override var _id: String = ObjectId().toHexString(),
    override var version: Long = 0,
    override var deleted: Boolean = false,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override var updatedAt: LocalDateTime = LocalDateTime.now(),
) : VersionedEntity

/** Тело отчёта с клиента; пределы - в [BugReportRepository]. */
@Serializable
data class BugReportRequest(val text: String, val screen: String = "", val context: Map<String, String> = emptyMap(), val requests: List<String> = emptyList())
