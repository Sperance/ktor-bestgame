package ru.descend.exileforge.features.data.mail
import com.sperance.exileforge.rules.content.Rarity
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import ru.descend.exileforge.base.entity.VersionedEntity
import ru.descend.exileforge.extensions.now

/** Чьё письмо (1.69.0): от игры о статусе отчёта или от администратора. */
@Serializable
enum class MailKind { SYSTEM, ADMIN }

/** Вещь во вложении (1.69.0): шаблон и редкость; катится в момент, когда герой её забирает, на его уровне. */
@Serializable
data class MailEquipment(val template: String, val rarity: Rarity? = null)

/** Вложение письма (1.69.0): золото, стопки сумки по коду и вещи; забирает его один герой аккаунта, один раз. */
@Serializable
data class MailAttachment(
    val gold: Long = 0,
    val items: Map<String, Long> = emptyMap(),
    val equipment: List<MailEquipment> = emptyList(),
    /** Готовые вещи как есть (1.74.0): товар истёкшего лота возвращается почтой тем же роллом. */
    val instances: List<com.sperance.exileforge.rules.roll.ItemInstance> = emptyList(),
) {
    val empty: Boolean get() = gold <= 0 && items.isEmpty() && equipment.isEmpty() && instances.isEmpty()
}

/**
 * Письмо аккаунту (1.69.0). У системного текст - ключ словаря [key] с [args] (клиент переводит), у письма администратора -
 * [subject] и [body] как написаны. Живёт до [expiresAt] (мс эпохи UTC) - с вложением или без.
 */
@Serializable
data class Mail(
    val userId: String,
    val kind: MailKind = MailKind.ADMIN,
    val key: String = "",
    val args: List<String> = emptyList(),
    val subject: String = "",
    val body: String = "",
    val attachment: MailAttachment = MailAttachment(),
    var read: Boolean = false,
    var claimedBy: String? = null,
    val expiresAt: Long = 0,

    override var _id: String = ObjectId().toHexString(),
    override var version: Long = 0,
    override var deleted: Boolean = false,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override var updatedAt: LocalDateTime = LocalDateTime.now(),
) : VersionedEntity

/** Письмо администратора (1.69.0): [login] - одному аккаунту, пусто - всем. */
@Serializable
data class MailRequest(val login: String = "", val subject: String, val body: String, val attachment: MailAttachment = MailAttachment())
