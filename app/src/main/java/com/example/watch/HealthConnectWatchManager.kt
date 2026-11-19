package com.example.watch

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private const val TAG = "HealthConnectWatchMgr"

class HealthConnectWatchManager(private val context: Context) {

    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class)
    )

    suspend fun checkAvailability(): HealthConnectAvailability {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return HealthConnectAvailability.UNAVAILABLE
        }
        return checkAvailabilityApi26()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun checkAvailabilityApi26(): HealthConnectAvailability {
        return try {
            when (HealthConnectClient.getSdkStatus(context)) {
                HealthConnectClient.SDK_AVAILABLE -> {
                    val client = HealthConnectClient.getOrCreate(context)
                    val granted = client.permissionController.getGrantedPermissions()
                    if (granted.containsAll(requiredPermissions)) {
                        HealthConnectAvailability.READY
                    } else {
                        HealthConnectAvailability.NEEDS_PERMISSIONS
                    }
                }
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                    HealthConnectAvailability.NOT_INSTALLED
                else ->
                    HealthConnectAvailability.UNAVAILABLE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Health Connect status", e)
            HealthConnectAvailability.UNAVAILABLE
        }
    }

    suspend fun fetchTodayOverallMetrics(): Result<WatchOverallMetrics> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return Result.failure(UnsupportedOperationException("Health Connect requires Android 8.0 (API 26) or higher."))
        }
        return fetchMetricsApi26()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun fetchMetricsApi26(): Result<WatchOverallMetrics> {
        return try {
            val status = HealthConnectClient.getSdkStatus(context)
            if (status != HealthConnectClient.SDK_AVAILABLE) {
                return Result.failure(IllegalStateException("Health Connect is not available on this device."))
            }

            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            if (!granted.containsAll(requiredPermissions)) {
                return Result.failure(SecurityException("Health Connect read permissions have not been granted yet."))
            }

            val zone = ZoneId.systemDefault()
            val now = Instant.now()
            val startOfToday = LocalDate.now(zone).atStartOfDay(zone).toInstant()
            val sleepWindowStart = now.minus(24, ChronoUnit.HOURS)

            val activityResponse = client.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL,
                        HeartRateRecord.BPM_AVG,
                        RestingHeartRateRecord.BPM_AVG,
                        TotalCaloriesBurnedRecord.ENERGY_TOTAL
                    ),
                    timeRangeFilter = TimeRangeFilter.between(startOfToday, now)
                )
            )

            val sleepResponse = client.aggregate(
                AggregateRequest(
                    metrics = setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(sleepWindowStart, now)
                )
            )

            val steps = activityResponse[StepsRecord.COUNT_TOTAL]
            val avgHr = activityResponse[HeartRateRecord.BPM_AVG]
            val restingHr = activityResponse[RestingHeartRateRecord.BPM_AVG]
            val burnedKcal = activityResponse[TotalCaloriesBurnedRecord.ENERGY_TOTAL]
                ?.inKilocalories
                ?.roundToInt()
            val sleepMinutes = sleepResponse[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes()

            Result.success(
                WatchOverallMetrics(
                    stepsToday = steps,
                    avgHeartRateBpm = avgHr,
                    restingHeartRateBpm = restingHr,
                    activeBurnedKcal = burnedKcal,
                    sleepDurationMinutes = sleepMinutes,
                    lastSyncedAt = System.currentTimeMillis(),
                    sourceLabel = "Health Connect"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to aggregate Health Connect metrics", e)
            Result.failure(e)
        }
    }
}
