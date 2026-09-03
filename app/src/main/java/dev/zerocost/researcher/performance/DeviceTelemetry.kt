package dev.zerocost.researcher.performance

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class DeviceTelemetrySample(
    val timestampEpochMs: Long,
    val processPssKb: Int,
    val processRssKb: Long?,
    val nativeHeapAllocatedBytes: Long,
    val systemAvailableBytes: Long,
    val systemTotalBytes: Long,
    val systemLowMemory: Boolean,
    val thermalStatus: Int,
    val batteryPercent: Int?,
    val batteryChargeCounterMicroAh: Int?,
    val batteryTemperatureTenthsC: Int?,
)

data class DeviceTelemetrySummary(
    val samples: Int,
    val peakProcessPssMb: Double,
    val peakProcessRssMb: Double?,
    val peakNativeHeapMb: Double,
    val minimumAvailableRamMb: Double,
    val totalRamMb: Double,
    val lowMemoryObserved: Boolean,
    val maximumThermalStatus: Int,
    val severeOrWorseFraction: Double,
    val batteryStartPercent: Int?,
    val batteryEndPercent: Int?,
    val chargeCounterDeltaMicroAh: Int?,
    val maximumBatteryTemperatureC: Double?,
)

class DeviceTelemetryCollector(private val context: Context) {
    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val powerManager =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val batteryManager =
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    fun sample(): DeviceTelemetrySample {
        val processMemory = Debug.MemoryInfo()
        Debug.getMemoryInfo(processMemory)

        val systemMemory = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(systemMemory)

        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )

        return DeviceTelemetrySample(
            timestampEpochMs = System.currentTimeMillis(),
            processPssKb = processMemory.totalPss,
            processRssKb = readProcessRssKb(),
            nativeHeapAllocatedBytes = Debug.getNativeHeapAllocatedSize(),
            systemAvailableBytes = systemMemory.availMem,
            systemTotalBytes = systemMemory.totalMem,
            systemLowMemory = systemMemory.lowMemory,
            thermalStatus = powerManager.currentThermalStatus,
            batteryPercent = batteryProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 },
            batteryChargeCounterMicroAh = batteryProperty(
                BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER
            ),
            batteryTemperatureTenthsC = batteryIntent
                ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                ?.takeIf { it != Int.MIN_VALUE },
        )
    }

    fun summarize(samples: List<DeviceTelemetrySample>): DeviceTelemetrySummary {
        require(samples.isNotEmpty())

        val start = samples.first()
        val end = samples.last()
        val severeCount = samples.count {
            it.thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
        }
        val rss = samples.mapNotNull(DeviceTelemetrySample::processRssKb)
        val temperatures = samples.mapNotNull(DeviceTelemetrySample::batteryTemperatureTenthsC)

        return DeviceTelemetrySummary(
            samples = samples.size,
            peakProcessPssMb = samples.maxOf { it.processPssKb } / 1024.0,
            peakProcessRssMb = rss.maxOrNull()?.div(1024.0),
            peakNativeHeapMb = samples.maxOf { it.nativeHeapAllocatedBytes } /
                (1024.0 * 1024.0),
            minimumAvailableRamMb = samples.minOf { it.systemAvailableBytes } /
                (1024.0 * 1024.0),
            totalRamMb = samples.maxOf { it.systemTotalBytes } /
                (1024.0 * 1024.0),
            lowMemoryObserved = samples.any { it.systemLowMemory },
            maximumThermalStatus = samples.maxOf { it.thermalStatus },
            severeOrWorseFraction = severeCount.toDouble() / samples.size,
            batteryStartPercent = start.batteryPercent,
            batteryEndPercent = end.batteryPercent,
            chargeCounterDeltaMicroAh = if (
                start.batteryChargeCounterMicroAh != null &&
                end.batteryChargeCounterMicroAh != null
            ) {
                end.batteryChargeCounterMicroAh - start.batteryChargeCounterMicroAh
            } else {
                null
            },
            maximumBatteryTemperatureC = temperatures.maxOrNull()?.div(10.0),
        )
    }

    private fun batteryProperty(property: Int): Int? {
        val value = batteryManager.getIntProperty(property)
        return value.takeIf { it != Int.MIN_VALUE }
    }

    private fun readProcessRssKb(): Long? {
        val status = File("/proc/self/status")
        if (!status.isFile) return null

        return runCatching {
            status.useLines { lines ->
                lines.firstOrNull { it.startsWith("VmRSS:") }
                    ?.substringAfter("VmRSS:")
                    ?.trim()
                    ?.substringBefore(' ')
                    ?.toLongOrNull()
            }
        }.getOrNull()
    }
}

class DeviceTelemetrySession(
    private val collector: DeviceTelemetryCollector,
    private val scope: CoroutineScope,
    private val sampleIntervalMs: Long = 1_000,
) {
    private val samples = mutableListOf<DeviceTelemetrySample>()
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        samples += collector.sample()

        job = scope.launch {
            while (isActive) {
                delay(sampleIntervalMs)
                samples += collector.sample()
            }
        }
    }

    suspend fun stop(): List<DeviceTelemetrySample> {
        job?.cancel()
        job?.join()
        if (
            samples.isEmpty() ||
            System.currentTimeMillis() - samples.last().timestampEpochMs > 250
        ) {
            samples += collector.sample()
        }
        return samples.toList()
    }
}

object ThermalStatusLabel {
    fun label(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "NONE"
        PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
        PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
        PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
        PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
        else -> "UNKNOWN($status)"
    }
}
