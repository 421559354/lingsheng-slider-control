package com.lingsheng.slidercontrol.examples

/** Reads numbered controller program rows. Infrared and external-input setup codes stay in the source text. */
object ProgramRecipeParser {
    private val programLine = Regex("^\\s*(\\d{6,7})\\b(?:\\s*//\\s*(.*))?\\s*$")
    private val setupLine = Regex("^\\s*(1500\\d{2})\\b")
    private val mappedOpcodes = (3..17).toSet()
    private val multiControllerExamples = setOf("002", "031", "032", "043", "044")

    fun parse(entry: ExampleEntry, source: String): ExampleParseResult {
        if (entry.id in multiControllerExamples) {
            return ExampleParseResult(null, "此案例需要多块控制板或红外一拖多；保留原文和演示资料供接线、设置时参考")
        }
        val notes = mutableListOf<String>()
        if (Regex("(?m)^\\s*10[0-2]\\d{3}\\b").containsMatchIn(source)) {
            notes += "原文含开机、红外或外接触发设置码；App 只发送程控组，由你主动选择并启动，不会写入这些触发设置。"
        }
        if (source.contains("外接按键") || source.contains("外接输入") || source.contains("限位")) {
            notes += "原文涉及外接按键或限位；App 的启动按钮不代替外部接线与触发。"
        }
        val problems = mutableListOf<String>()
        val variants = mutableListOf<ExampleProgramPackage>()
        var groups = mutableListOf<ProgramGroupRecipe>()
        var currentPreset: ProgramPresetGroup? = null
        var rows = mutableListOf<ProgramInstruction>()
        var groupError: String? = null
        var ignoreFollowingRows = false

        fun finishGroup() {
            val preset = currentPreset ?: return
            when {
                groupError != null -> problems += "${preset.label}：$groupError"
                rows.isEmpty() -> problems += "${preset.label}：没有可识别的程控行"
                preset.runCommandId == 4 -> notes += "${preset.label} 的启动编号 4 有两个目标，暂不通过蓝牙直接启动；原文步骤仍可查看。"
                else -> groups += ProgramGroupRecipe(preset, rows.toList())
            }
            currentPreset = null
            rows = mutableListOf()
            groupError = null
            ignoreFollowingRows = false
        }

        fun finishVariant() {
            if (groups.isEmpty()) return
            val candidate = ExampleProgramPackage(groups.toList(), groups.last().preset.setupCode)
            when (val result = ProgramFramePlanner.plan(candidate, 0)) {
                is ProgramPlanResult.Ready -> variants += candidate
                is ProgramPlanResult.Rejected -> problems += result.reason
            }
            groups = mutableListOf()
        }

        source.lineSequence().forEach { rawLine ->
            val setup = setupLine.find(rawLine)
            if (setup != null) {
                finishGroup()
                val setupCode = setup.groupValues[1].toInt()
                if (groups.any { it.preset.setupCode == setupCode }) finishVariant()
                currentPreset = presetFor(setupCode)
                if (currentPreset == null) problems += "预置组 $setupCode 的蓝牙启动映射未确认"
                return@forEach
            }
            val match = programLine.find(rawLine) ?: return@forEach
            val preset = currentPreset ?: return@forEach
            if (ignoreFollowingRows) return@forEach
            val code = match.groupValues[1]
            // Every program row is seven digits; six-digit values are direct or trigger settings.
            if (code.length == 6) return@forEach
            val note = match.groupValues.getOrElse(2) { "" }
            val parsed = parseInstruction(code, note)
            if (parsed == null) {
                groupError = groupError ?: "程控行 $code 的操作码或参数格式无法确认"
            } else if (rows.any { it.rowIndex == parsed.rowIndex }) {
                if (parsed.rowIndex == 0 && rows.lastOrNull()?.rowIndex == 9) {
                    notes += "${preset.label} 后面重复了行号但未重新打开预置组；只转换第一段完整程控，后段保留原文。"
                    ignoreFollowingRows = true
                } else {
                    groupError = groupError ?: "槽位 ${parsed.rowIndex} 重复"
                }
            } else {
                rows += parsed
                if (!durationCommentMatches(parsed.opcode, parsed.value, note)) {
                    notes += "${preset.label} 第 ${parsed.rowIndex + 1} 行的数字与文字延时说明不一致；蓝牙按数字指令执行，请在预览中核对。"
                }
            }
        }
        finishGroup()
        finishVariant()
        if (problems.isNotEmpty() && variants.isNotEmpty()) {
            notes += "未转换部分：${problems.distinct().joinToString("；")}。这些内容仅保留在原文中。"
        }
        return if (variants.isEmpty()) {
            ExampleParseResult(null, problems.firstOrNull() ?: "原文没有可确认的单板蓝牙程控组；请查看完整说明", notes.distinct())
        } else {
            ExampleParseResult(variants.first(), null, notes.distinct(), variants.drop(1))
        }
    }

    private fun parseInstruction(code: String, note: String): ProgramInstruction? {
        if (code.length !in 6..7) return null
        val row = code.substring(0, 1).toIntOrNull() ?: return null
        val remainder = code.substring(1)
        val opcode = if (remainder.startsWith('1')) remainder.take(2).toIntOrNull() else remainder.take(1).toIntOrNull()
        if (opcode == null || opcode !in mappedOpcodes || row !in 0..9) return null
        val payload = remainder.drop(if (opcode >= 10) 2 else 1)
        val value = when {
            opcode == 14 -> {
                if (payload.length != 4) return null
                val function = payload.take(2).toIntOrNull() ?: return null
                val argument = payload.takeLast(2).toIntOrNull() ?: return null
                if (function !in 0..17) return null
                if (function == 0 && argument !in setOf(1, 2, 3, 4, 43) && argument !in 7..29) return null
                if (function == 11 && argument !in 0..5) return null
                (function shl 8) or argument
            }
            opcode in 10..13 || opcode in 15..17 -> {
                if (payload.length != 4) return null
                val duration = payload.take(2).toIntOrNull() ?: return null
                val nextCommand = payload.takeLast(2).toIntOrNull() ?: return null
                if (nextCommand != 0 && nextCommand != 1 && nextCommand != 43 && nextCommand !in 7..29) return null
                (duration shl 8) or nextCommand
            }
            else -> {
                if (payload.length != 5) return null
                payload.toIntOrNull() ?: return null
            }
        }
        if (value !in 0..65_535 || opcode == 8 && value !in 30..49_000) return null
        return ProgramInstruction(row, opcode, value, code, note.trim())
    }

    private fun durationCommentMatches(opcode: Int, value: Int, note: String): Boolean {
        val unit = when (opcode) {
            10 -> "小时"
            11 -> "分钟"
            12 -> "秒"
            13 -> "毫秒"
            else -> return true
        }
        val match = Regex("(\\d+)\\s*$unit").find(note) ?: return true
        val stated = match.groupValues[1].toIntOrNull() ?: return false
        val decoded = (value ushr 8) * if (opcode == 13) 10 else 1
        return stated == decoded
    }

    private fun presetFor(setupCode: Int): ProgramPresetGroup? {
        if (setupCode in 150001..150020) {
            val index = setupCode - 150001
            return ProgramPresetGroup(setupCode, "数字预置组 ${index + 1}", index * 10, 10, 29 - index)
        }
        return when (setupCode) {
            150021 -> ProgramPresetGroup(setupCode, "100+", 200, 10, 7)
            150022 -> ProgramPresetGroup(setupCode, "200+", 210, 10, 8)
            150023 -> ProgramPresetGroup(setupCode, "EQ", 220, 10, 9)
            150024 -> ProgramPresetGroup(setupCode, "绿色播放组 1", 230, 10, 4)
            150025 -> ProgramPresetGroup(setupCode, "绿色播放组 2", 240, 10, 4)
            else -> null
        }
    }
}
