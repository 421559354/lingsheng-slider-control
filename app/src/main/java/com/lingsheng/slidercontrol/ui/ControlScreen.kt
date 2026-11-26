package com.lingsheng.slidercontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ManualDirection { Forward, Reverse }

data class PairedDeviceUi(val address: String, val name: String)

data class ControlUiState(
    val bluetoothReady: Boolean = false,
    val bluetoothPermissionNeeded: Boolean = false,
    val connected: Boolean = false,
    val selectedDeviceAddress: String? = null,
    val pairedDevices: List<PairedDeviceUi> = emptyList(),
    val connectionMessage: String = "先连接已配对的控制板",
    val speedMmPerSecond: String = "5.0",
    val distanceMm: String = "100",
    val repeatCount: String = "3",
    val repeatContinuously: Boolean = true,
    val calibrationReady: Boolean = false,
    val motionEnabled: Boolean = false,
    val busy: Boolean = false,
    val roundTripAvailable: Boolean = false,
    val roundTripUnavailableReason: String = "先连接控制板并完成有效校准",
    val roundTripStatusMessage: String = "",
)

@Composable
fun ControlScreen(
    state: ControlUiState,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRequestBluetoothPermission: () -> Unit,
    onSpeedChange: (String) -> Unit,
    onDistanceChange: (String) -> Unit,
    onRepeatCountChange: (String) -> Unit,
    onRepeatModeChange: (Boolean) -> Unit,
    onManualStart: (ManualDirection) -> Unit,
    onManualStop: () -> Unit,
    onRoundTripStart: () -> Unit,
    onStop: () -> Unit,
    onOpenExamples: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(onOpenExamples, onOpenSettings)
            ConnectionCard(state, onConnect, onDisconnect, onOpenBluetoothSettings, onRequestBluetoothPermission)
            SpeedCard(state.speedMmPerSecond, !state.busy, onSpeedChange)
            SafetyNotice()
            ManualCard(state.motionEnabled && !state.busy, onManualStart, onManualStop)
            RoundTripCard(
                state = state,
                onDistanceChange = onDistanceChange,
                onRepeatCountChange = onRepeatCountChange,
                onRepeatModeChange = onRepeatModeChange,
                onStart = onRoundTripStart,
            )
            Text(
                "松开正反向按键即停止。没有控制板位置反馈时，本页不显示虚构的实时位置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
        Button(
            onClick = onStop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .height(60.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
            Text("■   停止", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SafetyNotice() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(
            "手动移动不受最大行程设置限制，请全程看护。松开按键或点击停止只会排队发送停止帧，应用无法确认电机已停稳；未验证断连停机和实体限位前，请保持断电开关可触及。",
            modifier = Modifier.padding(14.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun Header(onOpenExamples: () -> Unit, onOpenSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text("滑轨控制", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("蓝牙步进电机 · 日常控制", color = MaterialTheme.colorScheme.secondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenExamples, shape = RoundedCornerShape(14.dp)) { Text("案例") }
            OutlinedButton(onClick = onOpenSettings, shape = RoundedCornerShape(14.dp)) { Text("设置") }
        }
    }
}

@Composable
private fun ConnectionCard(
    state: ControlUiState,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRequestBluetoothPermission: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp).background(
                        if (state.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        CircleShape,
                    ),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    when {
                        state.connected -> "已连接"
                        state.busy -> "连接中"
                        else -> "未连接"
                    },
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    state.pairedDevices.firstOrNull { it.address == state.selectedDeviceAddress }?.name
                        ?: "选择已配对设备",
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                state.connectionMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.bluetoothPermissionNeeded) {
                    OutlinedButton(onClick = onRequestBluetoothPermission, shape = RoundedCornerShape(12.dp)) {
                        Text("授予蓝牙权限")
                    }
                }
                state.pairedDevices.forEach { device ->
                    OutlinedButton(
                        onClick = {
                            if (state.connected && device.address == state.selectedDeviceAddress) onDisconnect()
                            else onConnect(device.address)
                        },
                        enabled = state.bluetoothReady && !state.busy,
                        shape = RoundedCornerShape(12.dp),
                    ) { Text(if (state.connected && device.address == state.selectedDeviceAddress) "断开" else "连接 ${device.name}") }
                }
                OutlinedButton(onClick = onOpenBluetoothSettings, shape = RoundedCornerShape(12.dp)) {
                    Text("蓝牙配对")
                }
            }
        }
    }
}

@Composable
private fun SpeedCard(value: String, enabled: Boolean, onValueChange: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("运行速度", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("毫米 / 秒", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { onValueChange("-") }, enabled = enabled, shape = CircleShape, modifier = Modifier.size(48.dp)) { Text("−", fontSize = 20.sp) }
                Spacer(Modifier.width(24.dp))
                Text(value, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(24.dp))
                OutlinedButton(onClick = { onValueChange("+") }, enabled = enabled, shape = CircleShape, modifier = Modifier.size(48.dp)) { Text("+", fontSize = 20.sp) }
            }
            Text("速度会按你的步数/毫米校准换算成电机脉冲间隔。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun ManualCard(
    enabled: Boolean,
    onStart: (ManualDirection) -> Unit,
    onStop: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("手动移动", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (!enabled) {
            Text(
                "请先连接控制板并完成行程校准；运动命令会在确认协议后开放。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HoldButton("←   反向", ManualDirection.Reverse, enabled, onStart, onStop, Modifier.weight(1f))
            HoldButton("正向   →", ManualDirection.Forward, enabled, onStart, onStop, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HoldButton(
    label: String,
    direction: ManualDirection,
    enabled: Boolean,
    onStart: (ManualDirection) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(70.dp)
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(onPress = {
                        onStart(direction)
                        tryAwaitRelease()
                        onStop()
                    })
                }
            },
        shape = RoundedCornerShape(18.dp),
        color = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline,
    ) {
        Box(contentAlignment = Alignment.Center) { Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun RoundTripCard(
    state: ControlUiState,
    onDistanceChange: (String) -> Unit,
    onRepeatCountChange: (String) -> Unit,
    onRepeatModeChange: (Boolean) -> Unit,
    onStart: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AB 两点循环", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("板端程控", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                Text(
                    "A 点固定为控制板位置 0。开始会覆盖“200+”程控槽位；请先确认零点与滑轨 A 端一致并留好安全余量。",
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.distanceMm,
                    onValueChange = onDistanceChange,
                    label = { Text("A → B 距离") },
                    suffix = { Text("mm") },
                    singleLine = true,
                    modifier = Modifier.weight(1.2f),
                )
                OutlinedTextField(
                    value = state.repeatCount,
                    onValueChange = onRepeatCountChange,
                    enabled = !state.repeatContinuously,
                    label = { Text("往返次数") },
                    singleLine = true,
                    modifier = Modifier.weight(0.8f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onRepeatModeChange(true) },
                    shape = RoundedCornerShape(12.dp),
                ) { Text(if (state.repeatContinuously) "✓ 持续" else "持续") }
                OutlinedButton(
                    onClick = { onRepeatModeChange(false) },
                    shape = RoundedCornerShape(12.dp),
                ) { Text(if (!state.repeatContinuously) "✓ 指定次数" else "指定次数") }
            }
            Text(
                "下次写入速度：${state.speedMmPerSecond} 毫米/秒。调整上方运行速度后，停止并重新写入即可更新 AB 保存速度。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                when {
                    !state.calibrationReady -> "请先在设置里填写步数/毫米和最大可用行程。"
                    !state.roundTripAvailable -> state.roundTripUnavailableReason
                    else -> "连续模式使用控制板的 99 无限循环；指定次数为 1–98 次。B 点步数会在启动前校验并换算为板端程控参数。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Button(
                onClick = onStart,
                enabled = state.motionEnabled && state.roundTripAvailable && !state.busy,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("写入并开始 AB 循环") }
            if (state.roundTripStatusMessage.isNotBlank()) {
                Text(
                    state.roundTripStatusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
