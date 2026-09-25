package config

import extensions.printLog
import kotlinx.coroutines.*
import java.lang.management.ManagementFactory
import java.lang.management.OperatingSystemMXBean
import java.lang.management.MemoryMXBean
import java.lang.management.RuntimeMXBean
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.milliseconds

object SystemMonitor {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    // Beans для сбора метрик
    private val runtimeMXBean: RuntimeMXBean = ManagementFactory.getRuntimeMXBean()
    private val memoryMXBean: MemoryMXBean = ManagementFactory.getMemoryMXBean()
    private val osMXBean: OperatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean()

    /**
     * Запуск мониторинга с интервалом 1 час
     */
    fun start(intervalHours: Long = 1) {
        if (isRunning) {
            printLog("[SystemMonitor] SystemMonitor already running", true)
            return
        }

        isRunning = true
        printLog("[SystemMonitor] 🚀 SystemMonitor started. Interval: $intervalHours hour(s)", true)

        scope.launch {
            while (isRunning) {
                try {
                    collectAndLogMetrics()

                    // Ждём указанное количество часов
                    val delayMs = intervalHours * 60 * 60 * 1000
                    delay(delayMs.milliseconds)

                } catch (e: CancellationException) {
                    printLog("[SystemMonitor] SystemMonitor cancelled", true)
                    break
                } catch (e: Exception) {
                    e.printStackTrace()
                    printLog("[SystemMonitor] SystemMonitor error: ${e.message}", true)
                    delay(5000.milliseconds) // Ждём 5 секунд перед повторной попыткой
                }
            }
        }
    }

    /**
     * Остановка мониторинга
     */
    fun stop() {
        isRunning = false
        scope.cancel()
        printLog("[SystemMonitor] 🛑 SystemMonitor stopped", true)
    }

    /**
     * Сбор метрик и вывод в лог
     */
    private fun collectAndLogMetrics() {
        val timestamp = LocalDateTime.now().format(formatter)

        val logMessage = buildString {
            appendLine()
            appendLine("=".repeat(80))
            appendLine("📊 SYSTEM METRICS [$timestamp]")
            appendLine("=".repeat(80))

            // Процесс
            appendLine("🔹 PROCESS:")
            appendLine("  • PID: ${getProcessId()}")
            appendLine("  • Uptime: ${formatUptime(runtimeMXBean.uptime)}")
            appendLine("  • Thread count: ${Thread.activeCount()}")

            // Память JVM
            appendLine("🔹 JVM MEMORY:")
            appendLine("  • Total heap: ${formatBytes(memoryMXBean.heapMemoryUsage.committed)}")
            appendLine("  • Used heap: ${formatBytes(memoryMXBean.heapMemoryUsage.used)}")
            appendLine("  • Max heap: ${formatBytes(memoryMXBean.heapMemoryUsage.max)}")
            appendLine("  • Heap usage: ${memoryMXBean.heapMemoryUsage.used * 100 / memoryMXBean.heapMemoryUsage.max}%")

            // Non-heap memory
            appendLine("  • Non-heap used: ${formatBytes(memoryMXBean.nonHeapMemoryUsage.used)}")
            appendLine("  • Non-heap max: ${formatBytes(memoryMXBean.nonHeapMemoryUsage.max)}")

            appendLine("=".repeat(80))
        }

        // Выводим в лог
        printLog("[SystemMonitor] $logMessage", true)
    }

    /**
     * Получение PID процесса
     */
    private fun getProcessId(): String {
        return try {
            val pid = ManagementFactory.getRuntimeMXBean().name.split("@")[0]
            pid
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * Форматирование времени работы
     */
    private fun formatUptime(millis: Long): String {
        val days = millis / (24 * 60 * 60 * 1000)
        val hours = (millis % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000)
        val minutes = (millis % (60 * 60 * 1000)) / (60 * 1000)
        val seconds = (millis % (60 * 1000)) / 1000

        return if (days > 0) {
            "${days}d ${hours}h ${minutes}m ${seconds}s"
        } else {
            "${hours}h ${minutes}m ${seconds}s"
        }
    }

    /**
     * Форматирование размера в байтах
     */
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.2f".format(bytes / (1024.0 * 1024.0))} MB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }

}
