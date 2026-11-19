package com.example.ui.components

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.example.ui.theme.ZestPrimary
import com.example.ui.theme.ZestPrimarySoft
import com.example.ui.theme.ZestSecondaryDeep
import com.example.ui.theme.ZestSuccess
import com.example.ui.theme.ZestTabularNumberStyle
import com.example.ui.theme.ZestTertiary
import com.example.watch.BleConnectionStatus
import com.example.watch.BleWatchDevice
import com.example.watch.HealthConnectAvailability
import com.example.watch.WatchOverallMetrics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WatchSummaryCard(
    metrics: WatchOverallMetrics,
    bleStatus: BleConnectionStatus,
    hcAvailability: HealthConnectAvailability,
    isSyncing: Boolean,
    onOpenWatchSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = bleStatus == BleConnectionStatus.CONNECTED
    val statusBadge = when {
        isConnected && !metrics.connectedWatchName.isNullOrBlank() -> "BLE · ${metrics.connectedWatchName}"
        metrics.lastSyncedAt != null -> metrics.sourceLabel
        hcAvailability == HealthConnectAvailability.READY -> "Health Connect Ready"
        else -> "Not Connected"
    }

    ZestSurfaceCard(
        cornerRadius = 24.dp,
        modifier = modifier.testTag("watch_summary_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) ZestPrimarySoft else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Rounded.BluetoothConnected else Icons.Rounded.Watch,
                        contentDescription = "Smartwatch metrics",
                        tint = if (isConnected) ZestPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column {
                    Text(
                        text = "Smartwatch Metrics",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = statusBadge,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isConnected || metrics.lastSyncedAt != null) ZestSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            ZestSoftPillButton(
                text = if (isSyncing) "Syncing..." else "Connect / Sync",
                icon = Icons.Rounded.Sync,
                onClick = onOpenWatchSheet,
                testTag = "open_watch_sheet_button"
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        val hrText = when {
            metrics.liveHeartRateBpm != null -> "${metrics.liveHeartRateBpm} bpm (live)"
            metrics.avgHeartRateBpm != null -> "${metrics.avgHeartRateBpm} bpm avg"
            else -> "-- bpm"
        }
        val stepsText = metrics.stepsToday?.let { String.format(Locale.US, "%,d", it) } ?: "--"
        val burnedText = metrics.activeBurnedKcal?.let { "$it kcal" } ?: "-- kcal"
        val sleepText = formatSleepDuration(metrics.sleepDurationMinutes)

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = 2
        ) {
            WatchMetricTile(
                icon = Icons.Rounded.Favorite,
                label = "Heart Rate",
                value = hrText,
                accent = ZestTertiary,
                modifier = Modifier.weight(1f)
            )
            WatchMetricTile(
                icon = Icons.Rounded.DirectionsWalk,
                label = "Steps Today",
                value = stepsText,
                accent = ZestPrimary,
                modifier = Modifier.weight(1f)
            )
            WatchMetricTile(
                icon = Icons.Rounded.LocalFireDepartment,
                label = "Burned Energy",
                value = burnedText,
                accent = ZestSecondaryDeep,
                modifier = Modifier.weight(1f)
            )
            WatchMetricTile(
                icon = Icons.Rounded.Bedtime,
                label = "Sleep",
                value = sleepText,
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }

        if (metrics.batteryPercent != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.BatteryChargingFull,
                    contentDescription = "Watch battery",
                    tint = ZestSuccess,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Watch battery ${metrics.batteryPercent}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WatchMetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    ZestInsetPanel(
        modifier = modifier,
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.merge(ZestTabularNumberStyle),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SmartWatchSheet(
    metrics: WatchOverallMetrics,
    bleStatus: BleConnectionStatus,
    bleStatusMessage: String?,
    discoveredWatches: List<BleWatchDevice>,
    blePermissionsRequired: Array<String>,
    hasBlePermissions: Boolean,
    hcAvailability: HealthConnectAvailability,
    hcRequiredPermissions: Set<String>,
    isSyncing: Boolean,
    onRefreshAvailability: () -> Unit,
    onStartBleScan: () -> Unit,
    onStopBleScan: () -> Unit,
    onConnectWatch: (String) -> Unit,
    onDisconnectWatch: () -> Unit,
    onSyncHealthConnect: () -> Unit,
    onApplyBurnedCalories: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        onRefreshAvailability()
    }

    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            onStartBleScan()
        }
    }

    val hcPermissionLauncher = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        rememberLauncherForActivityResult(
            contract = PermissionController.createRequestPermissionResultContract()
        ) { granted ->
            onRefreshAvailability()
            if (granted.containsAll(hcRequiredPermissions)) {
                onSyncHealthConnect()
            }
        }
    } else {
        null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(ZestPrimarySoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Watch,
                        contentDescription = null,
                        tint = ZestPrimary
                    )
                }
                Column {
                    Text(
                        text = "Smartwatch & Health Sync",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Pair BLE heart rate watches or sync overall Health Connect metrics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Section 1: Health Connect Overall Metrics
            ZestSurfaceCard(cornerRadius = 22.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Health Connect Overall Metrics",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = ZestPrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when (hcAvailability) {
                        HealthConnectAvailability.READY -> "Connected & authorized for steps, heart rate, active calories, and sleep."
                        HealthConnectAvailability.NEEDS_PERMISSIONS -> "Grant Health Connect read access to fetch your watch's daily steps, HR, calories, and sleep."
                        HealthConnectAvailability.NOT_INSTALLED -> "Health Connect app needs installation or update from Play Store on this device."
                        HealthConnectAvailability.UNAVAILABLE -> "Health Connect is unavailable on this Android version or device profile."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("watch_hc_status_text")
                )

                Spacer(modifier = Modifier.height(12.dp))
                when (hcAvailability) {
                    HealthConnectAvailability.READY -> {
                        ZestPrimaryPillButton(
                            text = if (isSyncing) "Syncing Metrics..." else "Sync Watch Metrics",
                            icon = Icons.Rounded.Sync,
                            enabled = !isSyncing,
                            onClick = onSyncHealthConnect,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "watch_hc_sync_button"
                        )
                    }
                    HealthConnectAvailability.NEEDS_PERMISSIONS -> {
                        ZestPrimaryPillButton(
                            text = "Grant Health Connect Access",
                            icon = Icons.Rounded.Sync,
                            enabled = hcPermissionLauncher != null,
                            onClick = {
                                hcPermissionLauncher?.launch(hcRequiredPermissions)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "watch_hc_sync_button"
                        )
                    }
                    else -> {
                        ZestGhostPillButton(
                            text = "Recheck Health Connect",
                            icon = Icons.Rounded.Sync,
                            onClick = onRefreshAvailability,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "watch_hc_sync_button"
                        )
                    }
                }

                if (metrics.lastSyncedAt != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ZestInsetPanel(cornerRadius = 16.dp) {
                        val syncedTime = SimpleDateFormat("h:mm a", Locale.US).format(Date(metrics.lastSyncedAt))
                        Text(
                            text = "Latest Metrics · Synced $syncedTime",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Steps: ${metrics.stepsToday ?: "--"}",
                                style = MaterialTheme.typography.bodySmall.merge(ZestTabularNumberStyle)
                            )
                            Text(
                                text = "Avg HR: ${metrics.avgHeartRateBpm ?: "--"} bpm",
                                style = MaterialTheme.typography.bodySmall.merge(ZestTabularNumberStyle)
                            )
                            Text(
                                text = "Resting HR: ${metrics.restingHeartRateBpm ?: "--"} bpm",
                                style = MaterialTheme.typography.bodySmall.merge(ZestTabularNumberStyle)
                            )
                            Text(
                                text = "Burned: ${metrics.activeBurnedKcal ?: "--"} kcal",
                                style = MaterialTheme.typography.bodySmall.merge(ZestTabularNumberStyle)
                            )
                            Text(
                                text = "Sleep: ${formatSleepDuration(metrics.sleepDurationMinutes)}",
                                style = MaterialTheme.typography.bodySmall.merge(ZestTabularNumberStyle)
                            )
                        }
                    }

                    val burned = metrics.activeBurnedKcal
                    if (burned != null && burned > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        ZestSoftPillButton(
                            text = "Apply +$burned kcal Burned to Target",
                            icon = Icons.Rounded.LocalFireDepartment,
                            onClick = { onApplyBurnedCalories(burned) },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "watch_apply_calories_button"
                        )
                    }
                }
            }

            // Section 2: Direct Bluetooth Low Energy (BLE GATT) Watch Pairing
            ZestSurfaceCard(cornerRadius = 22.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bluetooth,
                        contentDescription = null,
                        tint = ZestPrimary
                    )
                    Text(
                        text = "Direct BLE Smartwatch Pairing",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = bleStatusMessage ?: when (bleStatus) {
                        BleConnectionStatus.CONNECTED -> "Connected to ${metrics.connectedWatchName ?: "Smartwatch"}"
                        BleConnectionStatus.SCANNING -> "Scanning for BLE heart rate watches..."
                        BleConnectionStatus.CONNECTING -> "Connecting over Bluetooth LE..."
                        BleConnectionStatus.BLUETOOTH_OFF -> "Turn on Bluetooth to scan for watches."
                        BleConnectionStatus.UNAVAILABLE -> "BLE radio is unavailable on this device."
                        BleConnectionStatus.ERROR -> "Bluetooth connection encountered an error."
                        BleConnectionStatus.DISCONNECTED -> "Scan for BLE watches supporting standard Heart Rate (0x180D) & Battery (0x180F)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("watch_ble_status_text")
                )

                Spacer(modifier = Modifier.height(12.dp))
                when (bleStatus) {
                    BleConnectionStatus.CONNECTED, BleConnectionStatus.CONNECTING -> {
                        ZestInsetPanel(cornerRadius = 16.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = metrics.connectedWatchName ?: "Connected Watch",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = buildString {
                                            append("Live HR: ${metrics.liveHeartRateBpm?.let { "$it bpm" } ?: "Waiting..."}")
                                            if (metrics.batteryPercent != null) {
                                                append(" · Battery ${metrics.batteryPercent}%")
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ZestPrimary
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        ZestGhostPillButton(
                            text = "Disconnect Watch",
                            onClick = onDisconnectWatch,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "watch_ble_disconnect_button"
                        )
                    }
                    BleConnectionStatus.SCANNING -> {
                        ZestGhostPillButton(
                            text = "Stop Scanning",
                            onClick = onStopBleScan,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "watch_ble_scan_button"
                        )
                    }
                    else -> {
                        ZestSoftPillButton(
                            text = if (hasBlePermissions) "Scan Nearby Watches" else "Grant Nearby Devices & Scan",
                            icon = Icons.Rounded.Bluetooth,
                            onClick = {
                                if (hasBlePermissions) {
                                    onStartBleScan()
                                } else {
                                    blePermissionLauncher.launch(blePermissionsRequired)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "watch_ble_scan_button"
                        )
                    }
                }

                if (discoveredWatches.isNotEmpty() && bleStatus != BleConnectionStatus.CONNECTED) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        discoveredWatches.forEachIndexed { index, device ->
                            Surface(
                                onClick = { onConnectWatch(device.address) },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("watch_device_$index")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = device.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${device.address} · Signal ${device.rssi} dBm",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "Pair",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = ZestPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSleepDuration(minutes: Long?): String {
    if (minutes == null || minutes <= 0L) return "--"
    val hours = minutes / 60
    val remMinutes = minutes % 60
    return if (hours > 0) "${hours}h ${remMinutes}m" else "${remMinutes}m"
}
