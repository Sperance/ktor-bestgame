package features.logic.locale

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/** Текст в gzip (1.53.1): статичные документы сжимаются при старте, а не на каждом запросе. */
object Gzip {
    fun of(text: String): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(text.toByteArray()) } }.toByteArray()
}
