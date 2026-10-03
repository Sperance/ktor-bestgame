package config

import extensions.printLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bson.Document
import org.bson.json.JsonMode
import org.bson.json.JsonWriterSettings
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/**
 * Бэкапы базы драйвером (1.42.0), без внешних утилит MongoDB: раз в [intervalDays] дней каждая
 * коллекция ложится в архив `backups/backup_<время>.zip` файлом `<коллекция>.jsonl` - документ на
 * строку в каноническом Extended JSON, так что типы (даты, long) переживают выгрузку, а файл
 * читает и `mongoimport`, и любой скрипт. Хранится не больше [keep] архивов, старые удаляются.
 */
class MongoBackupManager(
    private val intervalDays: Int,
    private val keep: Int,
    private val directory: File = File("backups"),
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build()

    fun start() {
        printLog("[Backup] every $intervalDays days, keeping $keep", true)
        scope.launch {
            while (isActive) {
                try {
                    val due = (latest()?.lastModified() ?: 0L) + intervalDays.days.inWholeMilliseconds
                    val wait = due - System.currentTimeMillis()
                    if (wait <= 0) createBackup() else delay(minOf(wait, 12.hours.inWholeMilliseconds).milliseconds)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    printLog("[Backup] ❌ ${e.message}", true)
                    delay(1.hours)
                }
            }
        }
    }

    /** Выгружает все коллекции в новый архив; возвращает его. */
    suspend fun createBackup(): File = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val target = File(directory, "$PREFIX${LocalDateTime.now().format(STAMP)}$SUFFIX")
        val partial = File(directory, "${target.name}.part")
        val database = MongoFactory.getDatabase()
        var documents = 0
        ZipOutputStream(partial.outputStream().buffered()).use { zip ->
            database.listCollectionNames().toList().sorted().forEach { name ->
                zip.putNextEntry(ZipEntry("$name.jsonl"))
                val writer = zip.bufferedWriter()
                database.getCollection(name, Document::class.java).find().collect { document ->
                    writer.write(document.toJson(json))
                    writer.newLine()
                    documents++
                }
                writer.flush()
                zip.closeEntry()
            }
        }
        check(partial.renameTo(target)) { "rename ${partial.name}" }
        prune()
        printLog("[Backup] ✅ ${target.name}: $documents documents, ${target.length() / 1024} KB", true)
        target
    }

    fun shutdown() = scope.cancel()

    private fun archives(): List<File> = directory.listFiles { file -> file.isFile && file.name.startsWith(PREFIX) && file.name.endsWith(SUFFIX) }
        ?.sortedByDescending(File::lastModified).orEmpty()

    private fun latest(): File? = archives().firstOrNull()

    private fun prune() = archives().drop(keep).forEach { if (it.delete()) printLog("[Backup] removed ${it.name}", true) }

    private companion object {
        const val PREFIX = "backup_"
        const val SUFFIX = ".zip"
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    }
}
