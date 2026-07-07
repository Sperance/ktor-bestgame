package server.crypto

import kotlinx.serialization.Serializable
import java.security.*
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import java.util.Base64

@Serializable
data class PublicKeyResponse(val publicKey: String)

@Serializable
data class ClientExchangeRequest(
    val clientPublicKey: String,  // Base64-encoded X.509 public key
    val encryptedMessage: String  // Base64-encrypted data
)

@Serializable
data class ServerExchangeResponse(
    val encryptedResponse: String // Base64-encrypted response
)

// ---------- Крипто-утилиты ----------
object CryptoUtils {
    fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(4096)
        return generator.generateKeyPair()
    }

    fun publicKeyToBase64(publicKey: PublicKey): String =
        Base64.getEncoder().encodeToString(publicKey.encoded)

    fun base64ToPublicKey(base64: String): PublicKey {
        val bytes = Base64.getDecoder().decode(base64)
        val spec = X509EncodedKeySpec(bytes)
        val factory = KeyFactory.getInstance("RSA")
        return factory.generatePublic(spec)
    }

    fun encrypt(message: String, publicKey: PublicKey): String {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encrypted = cipher.doFinal(message.toByteArray())
        return Base64.getEncoder().encodeToString(encrypted)
    }

    fun decrypt(encryptedBase64: String, privateKey: PrivateKey): String {
        val encryptedBytes = Base64.getDecoder().decode(encryptedBase64)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        val decrypted = cipher.doFinal(encryptedBytes)
        return String(decrypted)
    }
}