package com.lingsheng.slidercontrol.ui

import android.graphics.BitmapFactory
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.lingsheng.slidercontrol.examples.ExampleProgramPackage
import com.lingsheng.slidercontrol.examples.LoadedExample
import com.lingsheng.slidercontrol.examples.ProgramFramePlanner
import com.lingsheng.slidercontrol.examples.ProgramInstruction
import com.lingsheng.slidercontrol.examples.ProgramPlanResult
import com.lingsheng.slidercontrol.examples.ProgramWritePlan
import com.lingsheng.slidercontrol.examples.RecipeConfirmation

@Composable
fun ExampleDetailScreen(
    example: LoadedExample,
    address: Int,
    connected: Boolean,
    actionMessage: String,
    onRunRequest: (ProgramWritePlan) -> Unit,
    onBack: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val variants = listOfNotNull(example.parseResult.recipe) + example.parseResult.alternatives
    var selectedVariant by remember(example.entry.id) { mutableStateOf(0) }
    val recipe = variants.getOrNull(selectedVariant)
    var selectedEntryCode by remember(example.entry.id, selectedVariant) { mutableStateOf(recipe?.entrySetupCode ?: 0) }
    val editedValues = remember(example.entry.id, selectedVariant) { mutableStateMapOf<String, String>() }
    var showSource by remember(example.entry.id) { mutableStateOf(false) }
    var showConfirmation by remember(example.entry.id) { mutableStateOf(false) }
    var infiniteConfirmed by remember(example.entry.id) { mutableStateOf(false) }
    var externalConfirmed by remember(example.entry.id) { mutableStateOf(false) }
    var continuousMotionConfirmed by remember(example.entry.id) { mutableStateOf(false) }
    val invalidParameter = recipe?.groups.orEmpty().any { group ->
        group.instructions.any { instruction ->
            val entered = editedValues[parameterKey(group.preset.setupCode, instruction)]
            val limit = when {
                instruction.opcode in 10..13 || instruction.opcode in 15..17 -> 255
                instruction.opcode == 14 && (instruction.value ushr 8) in 1..2 -> 99
                else -> 65_535
            }
            entered != null && (entered.toIntOrNull()?.let { it !in 0..limit } ?: true)
        }
    }
    val adjustedRecipe = recipe?.let { applyValues(it, editedValues).copy(entrySetupCode = selectedEntryCode) }
    val planResult = if (adjustedRecipe == null || invalidParameter) {
        ProgramPlanResult.Rejected("请输入 0 到 65535 的整数参数")
    } else {
        ProgramFramePlanner.plan(adjustedRecipe, address)
    }
    val plan = (planResult as? ProgramPlanResult.Ready)?.plan
    val confirmationsSatisfied = plan?.confirmations.orEmpty().all { flag ->
        when (flag) {
            RecipeConfirmation.InfiniteLoop -> infiniteConfirmed
            RecipeConfirmation.ExternalHardware -> externalConfirmed
            RecipeConfirmation.DirectContinuousMotion -> continuousMotionConfirmed
        }
    }

    Box(modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp).padding(bottom = 82.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(12.dp)) { Text("返回") }
                Spacer(Modifier.weight(1f))
                Text(example.entry.category, color = MaterialTheme.colorScheme.secondary)
            }
            Text(example.entry.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("来源：${example.entry.sourcePath}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Text(example.entry.summary, color = MaterialTheme.colorScheme.secondary)

            ExampleMedia(example.entry.id)

            if (recipe == null) {
                NoticeCard(
                    title = "原文说明",
                    body = example.parseResult.unavailableReason ?: "该案例没有可确认的蓝牙程控方案。",
                )
            } else {
                if (variants.size > 1) {
                    Text("选择方案", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        variants.forEachIndexed { index, _ ->
                            FilterChip(selected = selectedVariant == index, onClick = { selectedVariant = index }, label = { Text("方案 ${index + 1}") })
                        }
                    }
                }
                NoticeCard(
                    title = "程控组与覆盖范围",
                    body = "将覆盖：${recipe.groups.joinToString("、") { "${it.preset.label}（${it.preset.slotBase}–${it.preset.slotBase + it.preset.slotCount - 1}）" }}。运行入口：${recipe.entryGroup?.preset?.label ?: "未配置"}。开始前请确认该组允许覆盖。",
                )
                example.parseResult.notes.forEach { note -> NoticeCard(title = "执行范围提醒", body = note) }
                Text("选择启动的程控组", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recipe.groups.forEach { group ->
                        FilterChip(
                            selected = selectedEntryCode == group.preset.setupCode,
                            onClick = { selectedEntryCode = group.preset.setupCode },
                            label = { Text(group.preset.label) },
                        )
                    }
                }
                Text("写入所列全部组后，只发送所选组的启动命令。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                recipe.groups.forEach { group ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Text("${group.preset.label} · 程控步骤", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            group.instructions.forEach { instruction ->
                                InstructionEditor(
                                    groupCode = group.preset.setupCode,
                                    instruction = instruction,
                                    initialValue = editedValues[parameterKey(group.preset.setupCode, instruction)] ?: displayValue(instruction).toString(),
                                    onValueChange = { editedValues[parameterKey(group.preset.setupCode, instruction)] = it },
                                )
                            }
                        }
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("蓝牙帧预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (plan == null) {
                            Text((planResult as? ProgramPlanResult.Rejected)?.reason ?: "配方无效", color = MaterialTheme.colorScheme.error)
                        } else {
                            plan.groups.forEach { group ->
                                Text("${group.preset.label}：${group.frames.size} 条写入帧", style = MaterialTheme.typography.labelLarge)
                                group.frames.forEachIndexed { row, frame ->
                                    Text("${group.preset.slotBase + row}: ${ProgramFramePlanner.hex(frame)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                            Text("启动 ${plan.entryGroup.label}: ${ProgramFramePlanner.hex(plan.startFrame)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (recipe != null) {
                Button(
                    onClick = {
                        infiniteConfirmed = false
                        externalConfirmed = false
                        continuousMotionConfirmed = false
                        showConfirmation = true
                    },
                    enabled = connected && plan != null,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text(if (connected) "写入并运行 ${plan?.entryGroup?.label ?: "此配方"}" else "连接蓝牙后可启动") }
            }

            if (actionMessage.isNotBlank()) Text(actionMessage, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { showSource = !showSource }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text(if (showSource) "收起原始 TXT" else "查看完整原始 TXT")
            }
            if (showSource) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {
                    Text(example.sourceText, modifier = Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Button(
            onClick = onStop,
            enabled = connected,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp).height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) { Text("■   停止", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    }

    if (showConfirmation && plan != null) {
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = { Text("确认覆盖并启动？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("将覆盖 ${plan.groups.joinToString("、") { it.preset.label }} 共 ${plan.groups.sumOf { it.frames.size }} 个程控槽位；每组发送 CH 后运行 ${plan.entryGroup.label}。")
                    if (RecipeConfirmation.InfiniteLoop in plan.confirmations) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = infiniteConfirmed, onCheckedChange = { infiniteConfirmed = it })
                            Text("我已确认该配方会持续/无限循环，停止需发送 CH。")
                        }
                    }
                    if (RecipeConfirmation.ExternalHardware in plan.confirmations) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = externalConfirmed, onCheckedChange = { externalConfirmed = it })
                            Text("我已确认该配方会操作控制板端口或外接硬件。")
                        }
                    }
                    if (RecipeConfirmation.DirectContinuousMotion in plan.confirmations) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = continuousMotionConfirmed, onCheckedChange = { continuousMotionConfirmed = it })
                            Text("我已确认配方会直接启动连续正转/反转；若后续步骤未按预期执行，电机可能继续运动。")
                        }
                    }
                    Text("控制板未回报实际位置或运行完成状态。", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmation = false
                        onRunRequest(plan)
                    },
                    enabled = confirmationsSatisfied,
                ) { Text("覆盖并启动") }
            },
            dismissButton = { TextButton(onClick = { showConfirmation = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun InstructionEditor(
    groupCode: Int,
    instruction: ProgramInstruction,
    initialValue: String,
    onValueChange: (String) -> Unit,
) {
    val edited = instruction.copy(value = initialValue.toIntOrNull()?.let {
        encodeEditedValue(instruction, it)
    } ?: instruction.value)
    val description = ProgramFramePlanner.describeInstruction(edited)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        OutlinedTextField(
            value = initialValue,
            onValueChange = onValueChange,
            label = { Text(parameterLabel(instruction, groupCode)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NoticeCard(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

private fun applyValues(
    recipe: ExampleProgramPackage,
    values: Map<String, String>,
): ExampleProgramPackage = recipe.copy(
    groups = recipe.groups.map { group ->
        group.copy(
            instructions = group.instructions.map { instruction ->
                val key = parameterKey(group.preset.setupCode, instruction)
                values[key]?.toIntOrNull()?.let { input ->
                    val value = encodeEditedValue(instruction, input)
                    instruction.copy(value = value)
                } ?: instruction
            },
        )
    },
)

private fun parameterKey(setupCode: Int, instruction: ProgramInstruction): String =
    "$setupCode-${instruction.rowIndex}"

private fun displayValue(instruction: ProgramInstruction): Int = when {
    instruction.opcode in 10..13 || instruction.opcode in 15..17 -> instruction.value ushr 8
    instruction.opcode == 14 && (instruction.value ushr 8) in 1..2 -> instruction.value and 0xFF
    else -> instruction.value
}

private fun encodeEditedValue(instruction: ProgramInstruction, input: Int): Int = when {
    instruction.opcode in 10..13 || instruction.opcode in 15..17 -> (input shl 8) or (instruction.value and 0xFF)
    instruction.opcode == 14 && (instruction.value ushr 8) in 1..2 -> (instruction.value and 0xFF00) or input
    else -> input
}

private fun parameterLabel(instruction: ProgramInstruction, groupCode: Int): String {
    val kind = when (instruction.opcode) {
        5, 6 -> "移动步数"
        7 -> "目标位置（步）"
        8 -> "每步间隔（微秒，越小越快）"
        9 -> "步数倍率"
        10 -> "延时（小时）"
        11 -> "延时（分钟）"
        12 -> "延时（秒）"
        13 -> "延时（10 毫秒）"
        in 15..17 -> "外部延时参数"
        14 -> if ((instruction.value ushr 8) in 1..2) "循环次数（99 为持续）" else "特殊功能组合值"
        else -> "参数"
    }
    return "$kind · 组 $groupCode 第 ${instruction.rowIndex + 1} 行"
}

@Composable
private fun ExampleMedia(id: String) {
    val context = LocalContext.current
    val media = when (id) {
        "003" -> "examples/media/003.mp4"
        "029" -> "examples/media/029.jpg"
        "043" -> "examples/media/043.mp4"
        "044" -> "examples/media/044.mp4"
        else -> return
    }
    Text("随附演示资料", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    if (media.endsWith(".jpg")) {
        val bitmap = remember(media) { context.assets.open(media).use(BitmapFactory::decodeStream) }
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = "案例随附接线图片", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
    } else {
        val file by produceState<File?>(initialValue = null, key1 = media) {
            value = withContext(Dispatchers.IO) {
                File(context.cacheDir, "example-${id}.mp4").also { target ->
                    if (!target.exists()) context.assets.open(media).use { source ->
                        target.outputStream().use { output -> source.copyTo(output) }
                    }
                }
            }
        }
        if (file == null) {
            Text("正在准备视频…", style = MaterialTheme.typography.bodySmall)
        } else {
            AndroidView(
                factory = { viewContext ->
                    VideoView(viewContext).apply {
                        setVideoPath(file!!.absolutePath)
                        setMediaController(MediaController(viewContext).also { it.setAnchorView(this) })
                        setOnClickListener { if (isPlaying) pause() else start() }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
            Text("点按视频画面播放或暂停", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}
