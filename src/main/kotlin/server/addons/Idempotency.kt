package server.addons

import base.exception.BaseException
import base.exception.model.IdempotencyExceptions
import base.route.ApiMongoResponse
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
import io.ktor.server.application.install
import io.ktor.server.application.pluginOrNull
import io.ktor.server.plugins.doublereceive.DoubleReceive
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.ApplicationSendPipeline
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.util.AttributeKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import java.security.MessageDigest

/** Ответ команды ровно таким, каким он ушёл в первый раз: статус, тип и тело. */
class StoredReply(val status: Int, val contentType: String?, val body: ByteArray) {
    /**
     * Тело для повтора - без снимка героя `hero`: он снят на момент первого ответа и мог устареть,
     * а клиент по [Idempotency.REPLAY_HEADER] сам перечитывает героя. Не JSON-объект - как есть.
     */
    fun replayBody(): ByteArray {
        if (contentType?.let(ContentType::parse)?.match(ContentType.Application.Json) != true) return body
        val json = runCatching { AppJson.parseToJsonElement(body.decodeToString()) }.getOrNull() as? JsonObject ?: return body
        if (HERO_FIELD !in json) return body
        return AppJson.encodeToString(JsonObject.serializer(), JsonObject(json - HERO_FIELD)).encodeToByteArray()
    }

    private companion object {
        /** Поле снимка героя в [base.route.ApiMongoResponse]. */
        const val HERO_FIELD = "hero"
    }
}

/** Чем кончилась попытка занять ключ. */
sealed interface Claim {
    /** Ключ свободен и теперь наш: команда выполняется. */
    data object Taken : Claim

    /** Тот же ключ выполняется прямо сейчас другим запросом. */
    data object Busy : Claim

    /** Ключ уже занят запросом с другим методом, путём или параметрами. */
    data object Mismatch : Claim

    /** Команда уже выполнена: её ответ повторяется без выполнения. */
    class Done(val reply: StoredReply) : Claim
}

/**
 * Хранилище ответов по паре (аккаунт, ключ). Живут они [Idempotency.TTL_HOURS] часов. Ключ привязан к
 * отпечатку запроса [Idempotency.fingerprint]: тот же ключ с другим запросом - [Claim.Mismatch].
 */
interface ReplyStore {
    suspend fun claim(account: String, key: String, request: String): Claim
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

    /** Попыток записать ответ и пауза перед первым повтором (дальше - вдвое дольше). */
    private const val COMPLETE_TRIES = 3
    private const val COMPLETE_BACKOFF_MS = 100L

    private val FORMAT = Regex("[A-Za-z0-9_-]{8,64}")

    fun valid(key: String): Boolean = FORMAT.matches(key)

    /**
     * Отпечаток запроса: SHA-256 от метода, пути, параметров, упорядоченных по имени и значению, и (1.46.0) тела -
     * тот же ключ с другим журналом захода больше не получает чужой ответ. Тело читается повторно ([DoubleReceive]).
     */
    suspend fun fingerprint(call: ApplicationCall): String {
        val query = call.request.queryParameters.entries()
            .flatMap { (name, values) -> values.map { name to it } }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .joinToString("&") { (name, value) -> "$name=$value" }
        val body = runCatching { call.receiveText() }.getOrDefault("")
        val text = "${call.request.httpMethod.value} ${call.request.path()}?$query\n$body"
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /**
     * Запись ответа с [COMPLETE_TRIES] попытками. Если база так и не приняла её, ключ остаётся
     * «в работе» и через срок брошенного ключа будет перехвачен: повтор тогда выполнит уже
     * выполненную команду ещё раз. Редкий случай (база недоступна секунды подряд) - потому и повторы.
     */
    internal suspend fun completeWithRetry(store: ReplyStore, account: String, key: String, reply: StoredReply) {
        var pause = COMPLETE_BACKOFF_MS
        repeat(COMPLETE_TRIES - 1) {
            try {
                return store.complete(account, key, reply)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                printLog("[Idempotency] $account/$key complete failed, retrying: ${e.message}", true)
            }
            delay(pause)
            pause *= 2
        }
        store.complete(account, key, reply)
    }

    internal val Pending = AttributeKey<Pair<String, String>>("idempotency")
}

/**
 * Повтор команды без повторного выполнения (1.28.0). POST с заголовком [Idempotency.HEADER] от
 * вошедшего аккаунта выполняется один раз: его ответ - статус и тело, включая бизнес-отказ 4xx, -
 * хранится сутки и на тот же ключ отдаётся с [Idempotency.REPLAY_HEADER], без снимка героя
 * (см. [StoredReply.replayBody]). Ключ привязан к запросу: с другим методом, путём или параметрами -
 * 422 `IDEM_003`. Пока первый запрос выполняется, ключ помечен «в работе»: запросы одного героя и так
 * идут по очереди (см. [features.logic.hero.HeroLocks]), а прочий одновременный дубль получает 409 `IDEM_001`.
 * Сбой 5xx, временный отказ ([BaseException.isTransient]) или ответ, который не сохранить, ключ
 * освобождают - повтор выполнит команду заново.
 *
 * Ставится после очереди героя и сам отвечает бизнес-отказом команды, не дожидаясь StatusPages: иначе
 * отказ ушёл бы уже за пределы замка героя, и дубль из очереди застал бы ключ «в работе» и получил 409.
 * Так ответ - и успешный, и отказ - сохраняется ещё под замком, и дубль в очереди видит готовый.
 */
fun Application.installIdempotency(
    store: ReplyStore = IdempotentReplyStore,
    accountOf: suspend (ApplicationCall) -> String? = { caller()?.user?._id },
) {
    if (pluginOrNull(DoubleReceive) == null) install(DoubleReceive)
    intercept(ApplicationCallPipeline.Call) {
        if (call.request.httpMethod != HttpMethod.Post) return@intercept
        val key = call.request.headers[Idempotency.HEADER] ?: return@intercept
        val account = accountOf(call) ?: return@intercept
        if (!Idempotency.valid(key)) throw IdempotencyExceptions.funExceptionBadKey("idempotency", key.take(80))
        when (val claim = store.claim(account, key, Idempotency.fingerprint(call))) {
            Claim.Busy -> throw IdempotencyExceptions.funExceptionBusy("idempotency", key)
            Claim.Mismatch -> throw IdempotencyExceptions.funExceptionMismatch("idempotency", key)
            is Claim.Done -> {
                call.response.header(Idempotency.REPLAY_HEADER, "true")
                call.respondBytes(claim.reply.replayBody(), claim.reply.contentType?.let(ContentType::parse), HttpStatusCode.fromValue(claim.reply.status))
                finish()
            }
            Claim.Taken -> {
                call.attributes.put(Idempotency.Pending, account to key)
                try {
                    proceed()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    val refusal = refusal(e)?.takeUnless { (_, error) -> error.isTransient }
                    if (refusal == null || call.response.isCommitted) {
                        // Сбой или временный отказ: ключ свободен, ответит StatusPages
                        if (call.attributes.getOrNull(Idempotency.Pending) != null) {
                            call.attributes.remove(Idempotency.Pending)
                            runCatching { store.release(account, key) }
                                .onFailure { printLog("[Idempotency] $account/$key not released: ${it.message}", true) }
                        }
                        throw e
                    }
                    call.respond(refusal.first, ApiMongoResponse.error(refusal.second))
                }
            }
        }
    }

    // Ответ ловится у самой отправки: сюда приходят ответы маршрута и бизнес-отказы, отвеченные выше.
    sendPipeline.intercept(ApplicationSendPipeline.After) { content ->
        val (account, key) = call.attributes.getOrNull(Idempotency.Pending) ?: return@intercept
        call.attributes.remove(Idempotency.Pending)
        val outgoing = content as? OutgoingContent
        val status = outgoing?.status ?: call.response.status() ?: HttpStatusCode.OK
        val body = (outgoing as? OutgoingContent.ByteArrayContent)?.bytes()
        try {
            if (body == null || status.value >= 500) store.release(account, key)
            else Idempotency.completeWithRetry(store, account, key, StoredReply(status.value, outgoing?.contentType?.toString(), body))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Ответ всё равно уходит: без записи повтор лишь выполнит команду заново или получит 409
            printLog("[Idempotency] $account/$key not stored: ${e.message}", true)
        }
    }
}
