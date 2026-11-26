package com.lingsheng.slidercontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingsheng.slidercontrol.storage.DEFAULT_STEPS_PER_MILLIMETRE

data class SettingsUiState(
    val deviceAddressByte: String = "0",
    val stepsPerMm: String = DEFAULT_STEPS_PER_MILLIMETRE,
    val maxTravelMm: String = "",
)

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onDeviceAddressChange: (String) -> Unit,
    onStepsPerMmChange: (String) -> Unit,
    onMaxTravelChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val addressValid = state.deviceAddressByte.toIntOrNull()?.let { it in 0..255 } == true
    val steps = state.stepsPerMm.toDoubleOrNull()
    val maxTravel = state.maxTravelMm.toDoubleOrNull()
    val calibrationBlank = state.stepsPerMm.isBlank() && state.maxTravelMm.isBlank()
    val validSteps = steps?.let { it.isFinite() && it > 0.0 } == true
    val validMaxTravel = maxTravel?.let { it.isFinite() && it > 0.0 } == true
    val calibrationValid = calibrationBlank ||
        (validSteps && validMaxTravel)

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("滑轨设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("返回") }
        }
        Text("行程校准", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = state.deviceAddressByte,
            onValueChange = onDeviceAddressChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("控制板地址（0–255）") },
            isError = !addressValid,
            supportingText = {
                Text(if (addressValid) "默认 0；与控制板设置保持一致" else "请输入 0 到 255 的整数")
            },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.stepsPerMm,
            onValueChange = onStepsPerMmChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("每毫米步数") },
            isError = state.stepsPerMm.isNotBlank() && !validSteps,
            supportingText = { Text("默认 198；根据电机、驱动细分和传动结构测量后可修改。还需填写最大可用行程。") },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.maxTravelMm,
            onValueChange = onMaxTravelChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("最大可用行程（mm）") },
            isError = state.maxTravelMm.isNotBlank() && !validMaxTravel,
            supportingText = { Text("填写已留出两端安全余量的有效行程；与步数/毫米一起填写") },
            singleLine = true,
        )
        Button(
            onClick = onSave,
            enabled = addressValid && calibrationValid,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("保存设置")
        }
        Text("蓝牙配对", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("首次使用请先在 Android 系统蓝牙设置中与控制板配对。应用只会显示已经配对的设备。", color = MaterialTheme.colorScheme.secondary)
        Text("安全提示", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("开始运动前确认滑轨两端无障碍物并留足余量。手动按键松开后应用会发送停止命令。AB 循环以控制板位置 0 为 A 点；开始时会改写“200+”程控组。控制板没有动作反馈，首次请低速、短行程并全程看护。", color = MaterialTheme.colorScheme.secondary)
    }
}
