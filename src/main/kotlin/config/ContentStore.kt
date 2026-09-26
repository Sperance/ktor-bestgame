package config

import com.sperance.exileforge.rules.content.ContentFiles
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.ContentLoader
import extensions.printLog
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/** Манифест контента внутри `static/index.json`: отпечаток всего мира и отпечаток каждого чанка. */
@Serializable
data class ContentManifest(val hash: String, val chunks: Map<String, String>)

/** Один чанк контента как он отдаётся: текст файла, его отпечаток и сжатое тело. */
class ContentChunk(val name: String, val text: String, val hash: String) {
    val gzip: ByteArray by lazy {
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(text.toByteArray()) } }.toByteArray()
    }
}

/**
 * Контент сервера (1.0.0): файлы `resources/content` - единственный источник правил мира, Mongo их
 * не хранит. Читаются и проверяются один раз при старте правилами `rules`; те же тексты уходят
 * клиенту чанками `/content/<файл>` с отпечатком каждого, так что клиент качает лишь изменившиеся.
 */
class ContentStore(val index: ContentIndex, texts: Map<String, String>) {
    private val chunks: Map<String, ContentChunk> = texts.mapValues { (name, text) -> ContentChunk(name, text, index.content.hashes.getValue(name)) }

    val manifest: ContentManifest = ContentManifest(index.hash, ContentFiles.ALL.associateWith { chunks.getValue(it).hash })

    fun chunk(name: String): ContentChunk? = chunks[name]

    companion object {
        fun load(): ContentStore {
            val texts = ContentFiles.ALL.associateWith { ContentResource.read(it) }
            val index = ContentLoader.load { texts.getValue(it) }
            printLog("[ContentStore] loaded: ${index.definitions.size} modifiers, ${index.templates.size} templates, ${index.items.size} items, ${index.zones.size} zones, hash=${index.hash.take(12)}")
            return ContentStore(index, texts)
        }
    }
}
