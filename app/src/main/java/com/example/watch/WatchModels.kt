package com.example.watch

enum class BleConnectionStatus {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    BLUETOOTH_OFF,
    UNAVAILABLE,
    ERROR
}

enum class HealthConnectAvailability {
    READY,
    NEEDS_PERMISSIONS,
    NOT_INSTALLED,
    UNAVAILABLE
}

data class BleWatchDevice(
    val name: String,
    val address: String,
    val rssi: Int
)

data class WatchOverallMetrics(
    val stepsToday: Long? = null,
    val avgHeartRateBpm: Long? = null,
    val liveHeartRateBpm: Int? = null,
    val restingHeartRateBpm: Long? = null,
    val activeBurnedKcal: Int? = null,
    val sleepDurationMinutes: Long? = null,
    val batteryPercent: Int? = null,
    val connectedWatchName: String? = null,
    val connectedWatchAddress: String? = null,
    val lastSyncedAt: Long? = null,
    val sourceLabel: String = "Not synced yet"
)
