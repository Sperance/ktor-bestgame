package config

import extensions.printLog
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.FileOutputStream
import java.time.Duration
import java.time.LocalDateTime
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds

object LogManager {

    // Конфигурация
    private const val LOG_DIRECTORY = "logs"
    private const val ARCHIVE_DIRECTORY = "logs_archive"
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM")
    private const val MAX_LOG_AGE_DAYS = 30

    // Состояние
    @Volatile
    private var currentDate: LocalDate = LocalDate.now()
    @Volatile
    private var writer: BufferedWriter? = null
    private val logChannel = Channel<String>(UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = true

    // Инициализация
    init {
        createDirectories()
        initializeWriter()
        startLogProcessor()
        scheduleDailyRotation()
        cleanupOldLogs()
    }

    /**
     * Главный метод для записи лога
     */
    fun log(message: String) {
        println(message)
        if (!isRunning) return

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
        val formattedMessage = "[$timestamp] $message"

        logChannel.trySend(formattedMessage)
    }

    /**
     * Создание директорий
     */
    private fun createDirectories() {
        File(LOG_DIRECTORY).mkdirs()
        File(ARCHIVE_DIRECTORY).mkdirs()
    }

    /**
     * Инициализация writer для текущего дня
     */
    private fun initializeWriter() {
        try {
            writer?.close()
            val logFile = getLogFile()
            writer = BufferedWriter(FileWriter(logFile, true))
            currentDate = LocalDate.now()
        } catch (e: Exception) {
            printLog("Failed to initialize log writer: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Получение файла лога для текущей даты
     */
    private fun getLogFile(): File {
        val dateStr = LocalDate.now().format(DATE_FORMAT)
        return File(LOG_DIRECTORY, "log_$dateStr.log")
    }

    /**
     * Обработчик логов (потребитель канала)
     */
    private fun startLogProcessor() {
        scope.launch {
            for (message in logChannel) {
                try {
                    if (LocalDate.now() != currentDate) {
                        rotateLog()
                    }

                    writer?.let {
                        it.write(message)
                        it.newLine()
                        it.flush()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    printLog("Failed to write log: ${e.message}")
                }
            }
        }
    }

    /**
     * Ротация лога при смене дня
     */
    private fun rotateLog() {
        try {
            writer?.close()
            initializeWriter()
            cleanupOldLogs()
            archiveMonthLogs()
        } catch (e: Exception) {
            printLog("Failed to rotate log: ${e.message}")
        }
    }

    /**
     * Планировщик ежедневной ротации
     */
    private fun scheduleDailyRotation() {
        scope.launch {
            while (isRunning) {
                val now = LocalDateTime.now()
                val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
                val delay = Duration.between(now, midnight).toMillis()

                if (delay > 0) {
                    delay(delay.milliseconds)
                }

                if (isRunning) {
                    rotateLog()
                }
            }
        }
    }

    /**
     * Архивирование логов за месяц (для файлов старше 30 дней)
     */
    private fun archiveMonthLogs() {
        try {
            val logDir = File(LOG_DIRECTORY)
            val logFiles = logDir.listFiles { file ->
                file.isFile && file.name.startsWith("log_") && file.name.endsWith(".log")
            } ?: return

            val thresholdDate = LocalDate.now().minusDays(MAX_LOG_AGE_DAYS.toLong())

            // Группируем файлы по месяцам
            val filesByMonth = logFiles
                .filter { file ->
                    try {
                        val dateStr = file.name.removePrefix("log_").removeSuffix(".log")
                        val fileDate = LocalDate.parse(dateStr, DATE_FORMAT)
                        fileDate.isBefore(thresholdDate)
                    } catch (e: Exception) {
                        false
                    }
                }
                .groupBy { file ->
                    try {
                        val dateStr = file.name.removePrefix("log_").removeSuffix(".log")
                        val fileDate = LocalDate.parse(dateStr, DATE_FORMAT)
                        YearMonth.from(fileDate)
                    } catch (e: Exception) {
                        null
                    }
                }
                .filterKeys { it != null }

            // Архивируем каждую группу
            filesByMonth.forEach { (yearMonth, files) ->
                if (yearMonth != null && files.isNotEmpty()) {
                    archiveLogsForMonth(yearMonth, files)
                }
            }
        } catch (e: Exception) {
            printLog("Failed to archive old logs: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Архивирование логов за конкретный месяц
     */
    private fun archiveLogsForMonth(yearMonth: YearMonth, files: List<File>) {
        try {
            val archiveFileName = "logs_${yearMonth.format(MONTH_FORMAT)}.zip"
            val archiveFile = File(ARCHIVE_DIRECTORY, archiveFileName)

            // Если архив уже существует, добавляем в него
            if (archiveFile.exists()) {
                // Для простоты создаём новый архив, удаляя старый
                archiveFile.delete()
            }

            ZipOutputStream(FileOutputStream(archiveFile)).use { zos ->
                files.forEach { file ->
                    try {
                        zos.putNextEntry(ZipEntry(file.name))
                        file.inputStream().use { input ->
                            input.copyTo(zos)
                        }
                        zos.closeEntry()

                        // Удаляем оригинальный файл после успешного архивирования
                        val deleted = file.delete()
                        if (deleted) {
                            printLog("Archived and removed: ${file.name} -> ${archiveFile.name}")
                        }
                    } catch (e: Exception) {
                        printLog("Failed to archive file: ${file.name}")
                        e.printStackTrace()
                    }
                }
            }

            printLog("✅ Archived ${files.size} log files to ${archiveFile.name}")

        } catch (e: Exception) {
            printLog("Failed to create archive for month $yearMonth")
            e.printStackTrace()
        }
    }

    /**
     * Удаление архивов старше 30 дней
     */
    private fun cleanupOldLogs() {

        //Пока не удалляем ничего
        if (true) return

        try {
            val archiveDir = File(ARCHIVE_DIRECTORY)
            val archiveFiles = archiveDir.listFiles { file ->
                file.isFile && file.name.startsWith("logs_") && file.name.endsWith(".zip")
            } ?: return

            val thresholdDate = LocalDate.now().minusDays(MAX_LOG_AGE_DAYS.toLong())

            archiveFiles.forEach { file ->
                try {
                    val dateStr = file.name.removePrefix("logs_").removeSuffix(".zip")
                    val yearMonth = YearMonth.parse(dateStr, MONTH_FORMAT)

                    // Проверяем, что последний день месяца старше порога
                    val lastDayOfMonth = yearMonth.atEndOfMonth()
                    if (lastDayOfMonth.isBefore(thresholdDate)) {
                        val deleted = file.delete()
                        if (deleted) {
                            printLog("Deleted old archive: ${file.name}")
                        }
                    }
                } catch (e: Exception) {
                    printLog("Failed to parse archive file: ${file.name}")
                }
            }
        } catch (e: Exception) {
            printLog("Failed to cleanup old archives: ${e.message}")
        }
    }

    /**
     * Принудительное архивирование текущих логов
     */
    fun forceArchiveMonth(yearMonth: YearMonth? = null) {
        val targetMonth = yearMonth ?: YearMonth.now()
        val logDir = File(LOG_DIRECTORY)
        val logFiles = logDir.listFiles { file ->
            file.isFile && file.name.startsWith("log_") && file.name.endsWith(".log")
        }?.filter { file ->
            try {
                val dateStr = file.name.removePrefix("log_").removeSuffix(".log")
                val fileDate = LocalDate.parse(dateStr, DATE_FORMAT)
                YearMonth.from(fileDate) == targetMonth
            } catch (e: Exception) {
                false
            }
        } ?: emptyList()

        if (logFiles.isNotEmpty()) {
            archiveLogsForMonth(targetMonth, logFiles)
        } else {
            printLog("No log files found for month $targetMonth")
        }
    }

    /**
     * Закрытие ресурсов при завершении
     */
    fun shutdown() {
        isRunning = false
        scope.launch {
            logChannel.close()
            writer?.close()
            scope.cancel()
        }
    }

    /**
     * Получение текущего файла лога
     */
    fun getCurrentLogFile(): File = getLogFile()

    /**
     * Получение списка всех лог-файлов
     */
    fun getLogFiles(): List<File> {
        val logDir = File(LOG_DIRECTORY)
        return logDir.listFiles { file ->
            file.isFile && file.name.startsWith("log_") && file.name.endsWith(".log")
        }?.sortedBy { it.name } ?: emptyList()
    }

    /**
     * Получение списка всех архивов
     */
    fun getArchiveFiles(): List<File> {
        val archiveDir = File(ARCHIVE_DIRECTORY)
        return archiveDir.listFiles { file ->
            file.isFile && file.name.startsWith("logs_") && file.name.endsWith(".zip")
        }?.sortedBy { it.name } ?: emptyList()
    }
}