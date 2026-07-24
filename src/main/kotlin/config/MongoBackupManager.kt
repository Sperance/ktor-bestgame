package config

import MONGO_URI
import extensions.printLog
import kotlinx.coroutines.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.concurrent.Volatile
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class MongoBackupManager(
    private val maxDays: Int,
    private val maxBackupsCount: Int,
    private val compress: Boolean = true
) {
    private val mongodumpPath: String by lazy { findMongodumpPath() }

    private val mongoUri: String = MONGO_URI
    private val backupDir: String = "backups"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    private var isRunning = true

    // Состояние
    @Volatile
    private var lastBackupTime: Long = 0L

    /**
     * Запуск автоматического создания бэкапов
     */
    fun start() {
        printLog("[MongoBackupManager] MongoBackupManager started. Creating backups every $maxDays days", true)

        if (!checkMongodumpAvailable()) {
            printLog("[MongoBackupManager] ❌ mongodump not found! Please install MongoDB tools. [https://www.mongodb.com/try/download/database-tools]. Put file to 'libs' project folder", true)
            return
        }

        scope.launch {
            while (isRunning) {
                try {
                    val delayMs = maxDays * 24L * 60 * 60 * 1000
                    val lastBackup = getLastBackupInfo()

                    printLog("[MongoBackupManager] lastBackup: $lastBackup", true)

                    // Проверяем, нужно ли создавать бэкап сейчас
                    val shouldCreateNow = if (lastBackup == null) {
                        // Нет бэкапов вообще
                        printLog("[MongoBackupManager] No backups found. Performing initial backup.", true)
                        true
                    } else {
                        // Проверяем, был ли бэкап сегодня
                        val lastBackupDate = Instant.fromEpochMilliseconds(lastBackup.created)
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                            .date

                        val today = Clock.System.now()
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                            .date

                        if (lastBackupDate != today) {
                            printLog("[MongoBackupManager] Last backup was ${lastBackupDate}, not today ($today). Creating new backup.", true)
                            true
                        } else {
                            printLog("[MongoBackupManager] Backup already exists for today. Waiting $maxDays days for next backup.", true)
                            false
                        }
                    }

                    if (shouldCreateNow) {
                        if (isRunning) {
                            createBackup()
                        }
                    } else {
                        // Ждём maxDays дней
                        printLog("[MongoBackupManager] Waiting ${delayMs / (24 * 60 * 60 * 1000)} days until next backup...", true)
                        delay(delayMs.milliseconds)
                    }

                } catch (e: CancellationException) {
                    printLog("[MongoBackupManager] Backup task cancelled", true)
                    break
                } catch (e: Exception) {
                    printLog("[MongoBackupManager] Error in backup schedule: ${e.message}", true)
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Создание дампа MongoDB
     */
    suspend fun createBackup(): Boolean = withContext(Dispatchers.IO) {
        try {
            printLog("[MongoBackupManager] Starting MongoDB backup...", true)

            val timestamp = System.currentTimeMillis()
            val backupName = generateBackupName()
            val backupPath = File(backupDir, backupName)

            // Создаём директорию для бэкапа
            if (!backupPath.exists()) {
                backupPath.mkdirs()
            }

            // Строим команду mongodump
            val command = buildMongodumpCommand(backupPath)

            printLog("[MongoBackupManager] Executing command: ${command.joinToString(" ")}", true)

            // Выполняем команду
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val exitCode = process.waitFor()

            // Читаем вывод
            val output = process.inputStream.bufferedReader().readText()

            if (exitCode == 0) {
                printLog("[MongoBackupManager] ✅ MongoDB backup created successfully: ${backupPath.absolutePath}", true)

                // Если нужно сжатие, архивируем
                if (compress) {
                    compressBackup(backupPath)
                }

                lastBackupTime = timestamp

                // Удаляем старые бэкапы
                cleanupOldBackups()

                // Логируем информацию о бэкапе
                logBackupInfo(backupPath)

                return@withContext true
            } else {
                printLog("[MongoBackupManager] ❌ Backup failed with code $exitCode", true)
                printLog("[MongoBackupManager] Output: $output", true)
                isRunning = false
                return@withContext false
            }

        } catch (e: Exception) {
            e.printStackTrace()
            printLog("[MongoBackupManager] Failed to create backup", true)
            isRunning = false
            return@withContext false
        }
    }

    /**
     * Формирование команды mongodump
     */
    private fun buildMongodumpCommand(backupPath: File): List<String> {
        val command = mutableListOf<String>()

        // Используем mongodumpPath (полный путь!)
        val path = mongodumpPath
        command.add(path)

        // Если путь содержит пробелы, оборачиваем в кавычки
        // command.add("\"$path\"")

        // ... остальные параметры
        val uriParts = parseMongoUri()
        if (uriParts["host"] != null) {
            command.add("--host")
            command.add(uriParts["host"]!!)
        }
        if (uriParts["port"] != null) {
            command.add("--port")
            command.add(uriParts["port"]!!)
        }
        if (uriParts["database"] != null) {
            command.add("--db")
            command.add(uriParts["database"]!!)
        }
        if (uriParts["username"] != null && uriParts["password"] != null) {
            command.add("--username")
            command.add(uriParts["username"]!!)
            command.add("--password")
            command.add(uriParts["password"]!!)
        }

        command.add("--out")
        command.add(backupPath.absolutePath)
        command.add("--gzip")
        command.add("--numParallelCollections")
        command.add("4")
        command.add("--forceTableScan")

        // Логируем команду с полными путями
        printLog("[MongoBackupManager] Executing: ${command.joinToString(" ")}", true)

        return command
    }

    /**
     * Сжатие бэкапа в ZIP
     */
    private fun compressBackup(backupPath: File) {
        try {
            val zipPath = File(backupDir, "${backupPath.name}.zip")

            printLog("[MongoBackupManager] Compressing backup to ${zipPath.name}", true)

            java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipPath)).use { zos ->
                backupPath.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val relativePath = backupPath.toURI().relativize(file.toURI()).path
                        zos.putNextEntry(java.util.zip.ZipEntry(relativePath))
                        file.inputStream().use { input ->
                            input.copyTo(zos)
                        }
                        zos.closeEntry()
                    }
                }
            }

            // Удаляем оригинальную директорию после успешного сжатия
            backupPath.deleteRecursively()

            printLog("[MongoBackupManager] ✅ Backup compressed: ${zipPath.absolutePath}", true)

        } catch (e: Exception) {
            e.printStackTrace()
            printLog("[MongoBackupManager] Failed to compress backup", true)
        }
    }

    /**
     * Удаление старых бэкапов
     */
    private fun cleanupOldBackups() {
        try {
            val backupFiles = File(backupDir).listFiles { file ->
                file.isFile && file.name.startsWith("backup_") && file.name.endsWith(".zip")
            } ?: return

            // Сортируем по времени создания (новые сверху)
            val sortedBackups = backupFiles.sortedByDescending { it.lastModified() }

            // Удаляем старые, если превышен лимит
            if (sortedBackups.size > maxBackupsCount) {
                val toDelete = sortedBackups.drop(maxBackupsCount)
                toDelete.forEach { file ->
                    val deleted = file.delete()
                    if (deleted) {
                        printLog("[MongoBackupManager] Deleted old backup: ${file.name}", true)
                    }
                }
            }

        } catch (e: Exception) {
            printLog("[MongoBackupManager] Failed to cleanup old backups", true)
        }
    }

    /**
     * Логирование информации о бэкапе
     */
    private fun logBackupInfo(backupPath: File) {
        try {
            val size = backupPath.walkTopDown().sumOf { file ->
                if (file.isFile) file.length() else 0
            }

            val sizeFormatted = when {
                size > 1024 * 1024 * 1024 -> "${size / (1024 * 1024 * 1024)} GB"
                size > 1024 * 1024 -> "${size / (1024 * 1024)} MB"
                size > 1024 -> "${size / 1024} KB"
                else -> "$size B"
            }

            val fileCount = backupPath.walkTopDown().count { it.isFile }

            printLog("[MongoBackupManager] Backup info: Size=$sizeFormatted, Files=$fileCount, Path=${backupPath.absolutePath}", true)

        } catch (e: Exception) {
            e.printStackTrace()
            printLog("[MongoBackupManager] Failed to log backup info", true)
        }
    }

    /**
     * Генерация имени бэкапа
     */
    private fun generateBackupName(): String {
        val timestamp = LocalDateTime.now().format(dateFormatter)
        return "backup_${timestamp}"
    }

    private fun findMongodumpPath(): String {
        // 1. Сначала проверяем папку проекта
        val projectDir = System.getProperty("user.dir")
        val logFile = File(projectDir, "libs/mongodump.exe")

        if (logFile.exists() && logFile.canExecute()) {
            printLog("[MongoBackupManager] Found mongodump in project: ${logFile.absolutePath}", true)
            return logFile.absolutePath
        }

        return "mongodump"
    }

    private fun checkMongodumpAvailable(): Boolean {
        return try {
            val path = mongodumpPath
            val process = ProcessBuilder(path, "--version")
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            val output = process.inputStream.bufferedReader().readText()

            if (exitCode == 0) {
                printLog("[MongoBackupManager] ✅ mongodump ready: ${output.take(100)}", true)
                true
            } else {
                printLog("[MongoBackupManager] ❌ mongodump check failed with code: $exitCode", true)
                false
            }
        } catch (e: Exception) {
            printLog("[MongoBackupManager] ❌ mongodump check error: ${e.message}", true)
            false
        }
    }

    /**
     * Парсинг URI MongoDB
     */
    private fun parseMongoUri(): MutableMap<String, String> {
        val result = mutableMapOf<String, String>()

        try {
            // Простой парсинг для формата mongodb://[username:password@]host[:port]/database
            val uri = mongoUri.replace("mongodb://", "")

            val authParts = uri.split("@")
            if (authParts.size == 2) {
                val credentials = authParts[0].split(":")
                if (credentials.size == 2) {
                    result["username"] = credentials[0]
                    result["password"] = credentials[1]
                }
                val hostDb = authParts[1].split("/")
                val hostPort = hostDb[0].split(":")
                result["host"] = hostPort[0]
                if (hostPort.size == 2) {
                    result["port"] = hostPort[1]
                }
                if (hostDb.size == 2) {
                    result["database"] = hostDb[1]
                }
            } else {
                val hostDb = uri.split("/")
                val hostPort = hostDb[0].split(":")
                result["host"] = hostPort[0]
                if (hostPort.size == 2) {
                    result["port"] = hostPort[1]
                }
                if (hostDb.size == 2) {
                    result["database"] = hostDb[1]
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            printLog("[MongoBackupManager] Failed to parse MongoDB URI", true)
        }

        return result
    }

    /**
     * Создание бэкапа прямо сейчас (ручной вызов)
     */
    suspend fun createBackupNow(): Boolean {
        printLog("[MongoBackupManager] Manual backup requested", true)
        return createBackup()
    }

    /**
     * Получение информации о последнем бэкапе
     */
    fun getLastBackupInfo(): BackupInfo? {
        val backupFiles = File(backupDir).listFiles { file ->
            file.isFile && file.name.startsWith("backup_") && file.name.endsWith(".zip")
        } ?: return null

        val latest = backupFiles.maxByOrNull { it.lastModified() } ?: return null

        return BackupInfo(
            fileName = latest.name,
            size = latest.length(),
            created = latest.lastModified(),
            path = latest.absolutePath
        )
    }

    /**
     * Получение списка всех бэкапов
     */
    fun getAllBackups(): List<BackupInfo> {
        val backupFiles = File(backupDir).listFiles { file ->
            file.isFile && file.name.startsWith("backup_") && file.name.endsWith(".zip")
        } ?: return emptyList()

        return backupFiles.map { file ->
            BackupInfo(
                fileName = file.name,
                size = file.length(),
                created = file.lastModified(),
                path = file.absolutePath
            )
        }.sortedByDescending { it.created }
    }

    /**
     * Остановка менеджера
     */
    fun shutdown() {
        isRunning = false
        scope.cancel()
        printLog("[MongoBackupManager] MongoBackupManager stopped", true)
    }

    /**
     * Информация о бэкапе
     */
    data class BackupInfo(
        val fileName: String,
        val size: Long,
        val created: Long,
        val path: String
    ) {
        val sizeFormatted: String get() = when {
            size > 1024 * 1024 * 1024 -> "${size / (1024 * 1024 * 1024)} GB"
            size > 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            size > 1024 -> "${size / 1024} KB"
            else -> "$size B"
        }

        val createdFormatted: String get() =
            Instant.fromEpochMilliseconds(created)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .toString()
    }
}