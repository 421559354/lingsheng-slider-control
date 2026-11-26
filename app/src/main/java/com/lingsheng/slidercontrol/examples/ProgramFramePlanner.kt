package com.lingsheng.slidercontrol.examples

import com.lingsheng.slidercontrol.protocol.LinshengProtocol
import com.lingsheng.slidercontrol.protocol.SliderCommand

object ProgramFramePlanner {
    private val supportedOpcodes = (3..17).toSet()

    fun plan(recipe: ExampleProgramPackage, address: Int): ProgramPlanResult = runCatching {
        require(address in 0..255) { "控制板地址必须是 0 到 255" }
        require(recipe.groups.isNotEmpty()) { "配方没有程控组" }
        require(recipe.groups.map { it.preset.setupCode }.distinct().size == recipe.groups.size) { "同一预置组不能重复写入" }
        val entry = requireNotNull(recipe.entryGroup) { "配方入口组不存在" }
        require(entry.preset.runCommandId != 4) { "启动编号 4 对应两个绿色预置组，当前无法确认会运行哪一组" }
        val availableRunCommands = recipe.groups.map { it.preset.runCommandId }.toSet()
        recipe.groups.forEach { group ->
            group.instructions.filter { it.opcode == 14 && it.value ushr 8 == 0 }.forEach { instruction ->
                val commandId = instruction.value and 0xFF
                when (commandId) {
                    1, 43 -> Unit
                    2, 3 -> Unit // Direct continuous motion requires an explicit user confirmation below.
                    4 -> error("程控行调用启动编号 4；它对应两个绿色预置组，无法确认目标")
                    in 7..29 -> require(commandId in availableRunCommands) {
                        "程控行调用预置启动编号 $commandId，但目标程控组不在本次将写入的组中"
                    }
                }
            }
            group.instructions.filter { it.opcode in 10..13 || it.opcode in 15..17 }.forEach { instruction ->
                val commandId = instruction.value and 0xFF
                if (commandId in 7..29) require(commandId in availableRunCommands) {
                    "延时结束后调用预置编号 $commandId，但目标程控组不在本次将写入的组中"
                }
            }
        }
        require(hasAcyclicPresetCalls(recipe.groups)) { "程控组之间存在循环调用，无法确认会按有限步骤结束" }
        val writes = recipe.groups.map { group ->
            val preset = group.preset
            require(preset.slotCount in 1..20) { "程控组槽位容量无效" }
            require(preset.slotBase >= 0 && preset.slotBase + preset.slotCount <= 256) { "程控组槽位超出控制器范围" }
            require(group.instructions.map { it.rowIndex }.distinct().size == group.instructions.size) { "程控组内有重复槽位" }
            require(group.instructions.size <= preset.slotCount) { "程控指令超过 ${preset.label} 的槽位容量" }
            val usedRows = group.instructions.map { it.rowIndex }.sorted()
            require(usedRows.isNotEmpty() && usedRows == (0..usedRows.last()).toList()) { "${preset.label} 的程控行不连续，无法安全清理尾部槽位" }
            group.instructions.forEach { instruction ->
                require(instruction.rowIndex in 0 until preset.slotCount) { "程控行号超出 ${preset.label} 槽位范围" }
                require(instruction.opcode in supportedOpcodes) { "操作码 ${instruction.opcode} 未在工作簿中定义" }
                require(instruction.value in 0..LinshengProtocol.MAX_PROGRAM_VALUE) { "程控参数必须在 0 到 65535 之间" }
                if (instruction.opcode in 10..13 || instruction.opcode in 15..17) {
                    val duration = instruction.value ushr 8
                    val nextCommand = instruction.value and 0xFF
                    require(duration in 0..255 && (nextCommand == 0 || nextCommand == 1 || nextCommand == 43 || nextCommand in 7..29)) {
                        "延时参数或结束后调用的命令未确认"
                    }
                }
                if (instruction.opcode == LinshengProtocol.PROGRAM_SPEED_OPCODE) {
                    require(instruction.value in LinshengProtocol.MIN_SPEED_MICROSECONDS_PER_STEP.toInt()..LinshengProtocol.MAX_PROGRAM_SPEED_MICROSECONDS_PER_STEP.toInt()) {
                        "程控速度必须在 30 到 49000 微秒/步之间"
                    }
                }
                if (instruction.opcode == 14) validateSpecialFunction(instruction.value)
            }
            val byRow = group.instructions.associateBy { it.rowIndex }
            val frames = (0 until preset.slotCount).map { row ->
                val instruction = byRow[row]
                LinshengProtocol.encodeProgramInstruction(
                    address = address,
                    slot = preset.slotBase + row,
                    opcode = instruction?.opcode ?: LinshengProtocol.PROGRAM_EMPTY_OPCODE,
                    value = instruction?.value ?: 0,
                )
            }
            ProgramGroupWritePlan(preset, frames)
        }
        val start = LinshengProtocol.encode(address, SliderCommand.RunPresetProgram(entry.preset.runCommandId))
        val confirmations = recipe.groups.flatMap { group ->
            group.instructions.mapNotNull { instruction ->
                if (instruction.opcode in 15..17) return@mapNotNull RecipeConfirmation.ExternalHardware
                if (instruction.opcode != 14) return@mapNotNull null
                val function = instruction.value ushr 8
                val argument = instruction.value and 0xFF
                when {
                    function in 1..2 && argument == 99 -> RecipeConfirmation.InfiniteLoop
                    function in 3..10 || function in 12..17 -> RecipeConfirmation.ExternalHardware
                    function == 0 && argument in 2..3 -> RecipeConfirmation.DirectContinuousMotion
                    else -> null
                }
            }
        }.toSet()
        ProgramWritePlan(writes, entry.preset, start, confirmations)
    }.fold(
        onSuccess = { ProgramPlanResult.Ready(it) },
        onFailure = { ProgramPlanResult.Rejected(it.message ?: "程控配方无效") },
    )

    fun describeInstruction(instruction: ProgramInstruction): String {
        val operation = when (instruction.opcode) {
            3 -> "位置计数累加"
            4 -> "位置计数累减"
            5 -> "正转步数"
            6 -> "反转步数"
            7 -> "指定位置"
            8 -> "速度（微秒/步）"
            9 -> "步数倍率"
            10 -> "延时小时"
            11 -> "延时分钟"
            12 -> "延时秒"
            13 -> "延时 10 毫秒单位"
            14 -> {
                val function = instruction.value ushr 8
                val argument = instruction.value and 0xFF
                val functionName = when (function) {
                    0 -> internalCallDescription(argument)
                    1 -> "副控制器循环"
                    2 -> "主控制器循环"
                    3 -> "端口低脉冲"
                    4 -> "端口输出"
                    in 5..10 -> "端口状态"
                    11 -> "刹车状态"
                    in 12..17 -> "外部输入触发"
                    else -> "未分类特殊功能"
                }
                "$functionName（功能 $function，参数 $argument）"
            }
            15 -> "外部延时秒"
            16 -> "外部延时 ×10"
            17 -> "外部延时 ×100"
            else -> "操作码 ${instruction.opcode}"
        }
        return buildString {
            if (instruction.opcode in 10..13 || instruction.opcode in 15..17) {
                val next = instruction.value and 0xFF
                append("第 ${instruction.rowIndex + 1} 行：$operation = ${instruction.value ushr 8}")
                if (next != 0) append("，结束后调用命令 $next")
            } else {
                append("第 ${instruction.rowIndex + 1} 行：$operation = ${instruction.value}")
            }
            if (instruction.note.isNotBlank()) append("（${instruction.note}）")
        }
    }

    private fun internalCallDescription(commandId: Int): String = when (commandId) {
        1, 43 -> "调用停止命令"
        2 -> "调用正向连续运动"
        3 -> "调用反向连续运动"
        4 -> "调用绿色预置启动（目标组未确认）"
        7 -> "调用 100+ 预置组"
        8 -> "调用 200+ 预置组"
        9 -> "调用 EQ 预置组"
        in 10..29 -> "调用数字预置组 ${30 - commandId}"
        else -> "调用未确认命令 $commandId"
    }

    private fun hasAcyclicPresetCalls(groups: List<ProgramGroupRecipe>): Boolean {
        val edges = groups.associate { group ->
            val calls = group.instructions.mapNotNull { instruction ->
                val isInternalCall = instruction.opcode == 14 && instruction.value ushr 8 == 0
                val isDelayedCall = instruction.opcode in 10..13 || instruction.opcode in 15..17
                if (!isInternalCall && !isDelayedCall) return@mapNotNull null
                (instruction.value and 0xFF).takeIf { it in 7..29 }
            }.toSet()
            group.preset.runCommandId to calls
        }
        val visiting = mutableSetOf<Int>()
        val visited = mutableSetOf<Int>()
        fun visit(commandId: Int): Boolean {
            if (commandId in visiting) return false
            if (commandId in visited) return true
            visiting += commandId
            for (target in edges[commandId].orEmpty()) {
                if (!visit(target)) return false
            }
            visiting -= commandId
            visited += commandId
            return true
        }
        return edges.keys.all(::visit)
    }

    fun hex(frame: ByteArray): String = frame.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    private fun validateSpecialFunction(value: Int) {
        val function = value ushr 8
        val argument = value and 0xFF
        require(function in 0..17) { "特殊功能号 $function 未在工作簿中定义" }
        when (function) {
            0 -> require(argument in setOf(1, 2, 3, 4, 43) || argument in 7..29) {
                "内部调用编号 $argument 未在工作簿中确认"
            }
            11 -> require(argument in 0..5) { "刹车状态参数必须在 0 到 5 之间" }
        }
    }
}
