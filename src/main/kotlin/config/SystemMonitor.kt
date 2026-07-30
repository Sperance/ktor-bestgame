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

    // Дополнительные метрики (если доступны)
    private val osBean: OperatingSystemMXBean? = runCatching {
        ManagementFactory.getOperatingSystemMXBean()
    }.getOrNull()

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
    private suspend fun collectAndLogMetrics() {
        val timestamp = LocalDateTime.now().format(formatter)
        val metrics = collectMetrics()

        val logMessage = buildString {
            appendLine()
            appendLine("=".repeat(80))
            appendLine("📊 SYSTEM METRICS [$timestamp]")
            appendLine("=".repeat(80))
            appendLine()

            // Процесс
            appendLine("🔹 PROCESS:")
            appendLine("  • PID: ${getProcessId()}")
            appendLine("  • Uptime: ${formatUptime(runtimeMXBean.uptime)}")
            appendLine("  • Thread count: ${Thread.activeCount()}")
            appendLine()

            // Память JVM
            appendLine("🔹 JVM MEMORY:")
            appendLine("  • Total heap: ${formatBytes(memoryMXBean.heapMemoryUsage.committed)}")
            appendLine("  • Used heap: ${formatBytes(memoryMXBean.heapMemoryUsage.used)}")
            appendLine("  • Max heap: ${formatBytes(memoryMXBean.heapMemoryUsage.max)}")
            appendLine("  • Heap usage: ${memoryMXBean.heapMemoryUsage.used * 100 / memoryMXBean.heapMemoryUsage.max}%")
            appendLine()

            // Non-heap memory
            appendLine("  • Non-heap used: ${formatBytes(memoryMXBean.nonHeapMemoryUsage.used)}")
            appendLine("  • Non-heap max: ${formatBytes(memoryMXBean.nonHeapMemoryUsage.max)}")
            appendLine()

            // Система
            appendLine("🔹 SYSTEM:")
            appendLine("  • OS: ${osMXBean.name} (${osMXBean.arch})")
            appendLine("  • CPU cores: ${osMXBean.availableProcessors}")
            appendLine("  • System load avg: ${"%.2f".format(osMXBean.systemLoadAverage)}")

            // Дополнительные метрики (если доступны)
            try {
                val totalMemory = getTotalSystemMemory()
                val freeMemory = getFreeSystemMemory()
                if (totalMemory > 0) {
                    appendLine("  • Total system memory: ${formatBytes(totalMemory)}")
                    appendLine("  • Free system memory: ${formatBytes(freeMemory)}")
                    appendLine("  • Memory usage: ${(totalMemory - freeMemory) * 100 / totalMemory}%")
                }
            } catch (e: Exception) {
                // Игнорируем, если метрики недоступны
            }

            // GC
            appendLine()
            appendLine("🔹 GARBAGE COLLECTION:")
            val gcBeans = ManagementFactory.getGarbageCollectorMXBeans()
            gcBeans.forEach { gc ->
                appendLine("  • ${gc.name}: ${gc.collectionCount} collections, ${gc.collectionTime}ms")
            }

            appendLine()
            appendLine("=".repeat(80))
        }

        // Выводим в лог
        printLog("[SystemMonitor] $logMessage", true)
    }

    /**
     * Сбор метрик в структуру данных
     */
    fun collectMetrics(): SystemMetrics {
        return SystemMetrics(
            timestamp = LocalDateTime.now(),
            pid = getProcessId(),
            uptime = runtimeMXBean.uptime,
            threadCount = Thread.activeCount(),
            heapMemory = MemoryUsage(
                total = memoryMXBean.heapMemoryUsage.committed,
                used = memoryMXBean.heapMemoryUsage.used,
                max = memoryMXBean.heapMemoryUsage.max
            ),
            nonHeapMemory = MemoryUsage(
                total = memoryMXBean.nonHeapMemoryUsage.committed,
                used = memoryMXBean.nonHeapMemoryUsage.used,
                max = memoryMXBean.nonHeapMemoryUsage.max
            ),
            cpuCores = osMXBean.availableProcessors,
            systemLoadAverage = osMXBean.systemLoadAverage,
            gcInfo = ManagementFactory.getGarbageCollectorMXBeans().map { gc ->
                GCInfo(gc.name, gc.collectionCount, gc.collectionTime)
            }
        )
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

    /**
     * Получение общей системной памяти
     */
    private fun getTotalSystemMemory(): Long {
        return try {
            val osBean = ManagementFactory.getOperatingSystemMXBean()
            val method = osBean.javaClass.getMethod("getTotalPhysicalMemorySize")
            method.invoke(osBean) as Long
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Получение свободной системной памяти
     */
    private fun getFreeSystemMemory(): Long {
        return try {
            val osBean = ManagementFactory.getOperatingSystemMXBean()
            val method = osBean.javaClass.getMethod("getFreePhysicalMemorySize")
            method.invoke(osBean) as Long
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Получение нагрузки на CPU
     */
    fun getCpuLoad(): Double {
        return try {
            val osBean = ManagementFactory.getOperatingSystemMXBean()
            val method = osBean.javaClass.getMethod("getSystemLoadAverage")
            (method.invoke(osBean) as Double) / osBean.availableProcessors
        } catch (e: Exception) {
            -1.0
        }
    }
}

// Data classes для метрик
data class SystemMetrics(
    val timestamp: LocalDateTime,
    val pid: String,
    val uptime: Long,
    val threadCount: Int,
    val heapMemory: MemoryUsage,
    val nonHeapMemory: MemoryUsage,
    val cpuCores: Int,
    val systemLoadAverage: Double,
    val gcInfo: List<GCInfo>
)

data class MemoryUsage(
    val total: Long,
    val used: Long,
    val max: Long
) {
    val usagePercent: Int get() = if (max > 0) (used * 100 / max).toInt() else 0
}

data class GCInfo(
    val name: String,
    val count: Long,
    val time: Long
)