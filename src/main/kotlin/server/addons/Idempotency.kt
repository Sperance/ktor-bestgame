package server.addons

import base.exception.model.IdempotencyExceptions
import extensions.printLog
import features.data.idempotency.IdempotentReplyStore
import features.logic.auth.caller
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.httpMethod
import io.ktor.server.response.ApplicationSendPipeline
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.util.AttributeKey
import kotlinx.coroutines.CancellationException

/** Ответ команды ровно таким, каким он ушёл в первый раз: статус, тип и тело. */
class StoredReply(val status: Int, val contentType: String?, val body: ByteArray)

/** Чем кончилась попытка занять ключ. */
sealed interface Claim {
    /** Ключ свободен и теперь наш: команда выполняется. */
    data object Taken : Claim

    /** Тот же ключ выполняется прямо сейчас другим запросом. */
    data object Busy : Claim

    /** Команда уже выполнена: её ответ повторяется без выполнения. */
    class Done(val reply: StoredReply) : Claim
}

/** Хранилище ответов по паре (аккаунт, ключ). Живут они [Idempotency.TTL_HOURS] часов. */
interface ReplyStore {
    suspend fun claim(account: String, key: String): Claim
    suspend fun complete(account: String, key: String, reply: StoredReply)
    suspend fun release(account: String, key: String)
}

object Idempotency {
    /** Заголовок запроса: ключ команды, UUID от клиента. */
    const val HEADER = "Idempotency-Key"

    /** Заголовок ответа-повтора: `true`, если команда не выполнялась, а ответ взят из хранилища. */
    const val REPLAY_HEADER = "Idempotent-Replay"

    /** Сколько живёт сохранённый ответ. */
    const val TTL_HOURS = 24L

    private val FORMAT = Regex("[A-Za-z0-9_-]{8,64}")

    fun valid(key: String): Boolean = FORMAT.matches(key)

    internal val Pending = AttributeKey<Pair<String, String>>("idempotency")
}

/**
 * Повтор команды без повторного выполнения (1.28.0). POST с заголовком [Idempotency.HEADER] от
 * вошедшего аккаунта выполняется один раз: его ответ - статус и тело, включая бизнес-отказ 4xx, -
 * хранится сутки и на тот же ключ отдаётся как есть, с [Idempotency.REPLAY_HEADER]. Пока первый
 * запрос выполняется, ключ помечен «в работе»: запросы одного героя и так идут по очереди
 * (см. [features.logic.hero.HeroLocks]), а прочий одновременный дубль получает 409 `IDEM_001`.
 * Сбой 5xx или ответ, который не сохранить, ключ освобождают - повтор выполнит команду заново.
 *
 * Ставится после очереди героя: ответ сохраняется ещё под её замком, и дубль в очереди видит готовый.
 */
fun Application.installIdempotency(
    store: ReplyStore = IdempotentReplyStore,
    accountOf: suspend (ApplicationCall) -> String? = { caller()?.user?._id },
) {
    intercept(ApplicationCallPipeline.Call) {
        if (call.request.httpMethod != HttpMethod.Post) return@intercept
        val key = call.request.headers[Idempotency.HEADER] ?: return@intercept
        val account = accountOf(call) ?: return@intercept
        if (!Idempotency.valid(key)) throw IdempotencyExceptions.funExceptionBadKey("idempotency", key.take(80))
        when (val claim = store.claim(account, key)) {
            Claim.Busy -> throw IdempotencyExceptions.funExceptionBusy("idempotency", key)
            is Claim.Done -> {
                call.response.header(Idempotency.REPLAY_HEADER, "true")
                call.respondBytes(claim.reply.body, claim.reply.contentType?.let(ContentType::parse), HttpStatusCode.fromValue(claim.reply.status))
                finish()
            }
            Claim.Taken -> call.attributes.put(Idempotency.Pending, account to key)
        }
    }

    // Ответ ловится у самой отправки: сюда приходят и ответы маршрута, и отказы из StatusPages.
    sendPipeline.intercept(ApplicationSendPipeline.After) { content ->
        val (account, key) = call.attributes.getOrNull(Idempotency.Pending) ?: return@intercept
        call.attributes.remove(Idempotency.Pending)
        val outgoing = content as? OutgoingContent
        val status = outgoing?.status ?: call.response.status() ?: HttpStatusCode.OK
        val body = (outgoing as? OutgoingContent.ByteArrayContent)?.bytes()
        try {
            if (body == null || status.value >= 500) store.release(account, key)
            else store.complete(account, key, StoredReply(status.value, outgoing?.contentType?.toString(), body))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Ответ всё равно уходит: без записи повтор лишь выполнит команду заново или получит 409
            printLog("[Idempotency] $account/$key not stored: ${e.message}", true)
        }
    }
}
