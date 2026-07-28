package server.addons

import extensions.saveChildren
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import server.crypto.ClientExchangeRequest
import server.crypto.CryptoUtils
import server.crypto.PublicKeyResponse
import server.crypto.ServerExchangeResponse

fun Application.configureCrypto() {

    val serverKeyPair = CryptoUtils.generateKeyPair()
    val serverPrivateKey = serverKeyPair.private
    val serverPublicKey = serverKeyPair.public

    routing {
        get("/public-key") {
            val publicKeyBase64 = CryptoUtils.publicKeyToBase64(serverPublicKey)
            call.respond(PublicKeyResponse(publicKeyBase64))
        }

        post("/secure-exchange") {
            val request = call.receive<ClientExchangeRequest>()

            // Восстанавливаем клиентский публичный ключ
            val clientPublicKey = CryptoUtils.base64ToPublicKey(request.clientPublicKey)

            // Расшифровываем сообщение от клиента СЕРВЕРНЫМ приватным ключом
            val decryptedFromClient = CryptoUtils.decrypt(request.encryptedMessage, serverPrivateKey)
            println("[Сервер] Расшифрованное сообщение от клиента: $decryptedFromClient")

            // Формируем ответ и шифруем его КЛИЕНТСКИМ публичным ключом
            val responseText = "Привет, клиент! Твоё сообщение '$decryptedFromClient' получено."
            val encryptedResponse = CryptoUtils.encrypt(responseText, clientPublicKey)

            call.respond(ServerExchangeResponse(encryptedResponse))
        }
    }.saveChildren()
}
