package com.lingsheng.slidercontrol.motion

import com.lingsheng.slidercontrol.bluetooth.BluetoothSerialSession
import com.lingsheng.slidercontrol.bluetooth.BluetoothSessionPhase
import com.lingsheng.slidercontrol.examples.ProgramWritePlan
import com.lingsheng.slidercontrol.protocol.LinshengProtocol
import com.lingsheng.slidercontrol.protocol.MotionDirection
import com.lingsheng.slidercontrol.protocol.SliderCommand
import kotlin.math.round

data class MotionConfiguration(
    val speedMillimetresPerSecond: Double,
    val calibration: MotionCalibration,
)

data class ControlActionResult(val accepted: Boolean, val message: String)

class SliderCoordinator(
    private val session: BluetoothSerialSession,
    private val address: () -> Int,
    private val configuration: () -> MotionConfiguration?,
) {
    fun motionReady(): Boolean = runCatching {
        requireConnected()
        LinshengProtocol.encode(address(), SliderCommand.SetSpeed(pulseInterval()))
        true
    }.getOrDefault(false)

    fun roundTripUnavailableReason(distanceMillimetres: Double, repeatCount: Int?): String? = runCatching {
        requireConnected()
        val current = requireNotNull(configuration()) { "请先完成行程校准" }
        require(current.calibration.isValid()) { "请先填写有效的步数/毫米和最大可用行程" }
        val interval = pulseInterval()
        require(interval <= LinshengProtocol.MAX_PROGRAM_SPEED_MICROSECONDS_PER_STEP) {
            "AB 程控速度范围是 30–49000 微秒/步，请调快一些"
        }
        val stepsToB = MotionSettings.stepCountForDistance(
            distanceMillimetres,
            current.calibration,
            LinshengProtocol.MAX_STEP_PARAMETER,
        )
        MotionSettings.programPositionFactors(stepsToB)
        require(repeatCount == null || repeatCount in 1..98) { "往返次数请输入 1 到 98；连续循环请选“持续”" }
    }.exceptionOrNull()?.message

    fun setSpeed(speedMillimetresPerSecond: Double): ControlActionResult = runCatching {
        requireConnected()
        val current = requireNotNull(configuration()) { "请先完成行程校准" }
        val interval = MotionSettings.pulseIntervalMicroseconds(speedMillimetresPerSecond, current.calibration.stepsPerMillimetre)
        val roundedInterval = round(interval).toLong()
        val encoded = LinshengProtocol.encode(address(), SliderCommand.SetSpeed(roundedInterval))
        check(session.send(encoded)) { "蓝牙连接已断开" }
        ControlActionResult(true, "即时调速已排队：$speedMillimetresPerSecond 毫米/秒（$roundedInterval 微秒/步）；AB 保存速度需停止后重新写入")
    }.getOrElse { ControlActionResult(false, it.message ?: "速度设置失败") }

    fun beginManual(direction: MotionDirection): ControlActionResult = runCatching {
        requireConnected()
        val speedFrame = LinshengProtocol.encode(address(), SliderCommand.SetSpeed(pulseInterval()))
        check(session.send(speedFrame)) { "蓝牙连接已断开" }
        val moveFrame = LinshengProtocol.encode(address(), SliderCommand.MoveContinuously(direction))
        check(session.send(moveFrame)) { "蓝牙连接已断开" }
        ControlActionResult(true, "正在移动")
    }.getOrElse { ControlActionResult(false, it.message ?: "无法开始移动") }

    fun endManual(): ControlActionResult = stop()

    fun startRoundTrip(distanceMillimetres: Double, repeatCount: Int?): ControlActionResult {
        return runCatching {
            requireConnected()
            val current = requireNotNull(configuration()) { "请先完成行程校准" }
            require(current.calibration.isValid()) { "请先填写有效的步数/毫米和最大可用行程" }
            val stepsToB = MotionSettings.stepCountForDistance(
                distanceMillimetres,
                current.calibration,
                LinshengProtocol.MAX_STEP_PARAMETER,
            )
            val position = MotionSettings.programPositionFactors(stepsToB)
            val speedInterval = round(
                MotionSettings.pulseIntervalMicroseconds(
                    current.speedMillimetresPerSecond,
                    current.calibration.stepsPerMillimetre,
                ),
            ).toLong()
            require(speedInterval in LinshengProtocol.MIN_SPEED_MICROSECONDS_PER_STEP..
                LinshengProtocol.MAX_PROGRAM_SPEED_MICROSECONDS_PER_STEP
            ) { "AB 程控速度范围是 30–49000 微秒/步，请调快一些" }
            require(repeatCount == null || repeatCount in 1..98) { "往返次数请输入 1 到 98；连续循环请选“持续”" }

            val countValue = repeatCount ?: 99
            val programRows = listOf(
                LinshengProtocol.PROGRAM_SPEED_OPCODE to speedInterval.toInt(),
                LinshengProtocol.PROGRAM_POSITION_OPCODE to 0,
                LinshengProtocol.PROGRAM_MULTIPLIER_OPCODE to position.baseSteps,
                LinshengProtocol.PROGRAM_POSITION_OPCODE to position.multiplier,
                LinshengProtocol.PROGRAM_REPEAT_OPCODE to
                    ((LinshengProtocol.PROGRAM_SECONDARY_REPEAT_FUNCTION shl 8) or countValue),
            ) + List(5) { LinshengProtocol.PROGRAM_EMPTY_OPCODE to 0 }
            val instructions = programRows.mapIndexed { index, (opcode, value) ->
                LinshengProtocol.encodeProgramInstruction(
                    address(),
                    LinshengProtocol.PROGRAM_SLOT_200_PLUS + index,
                    opcode,
                    value,
                )
            }
            // The controller examples require CH after the last programming row to leave setup mode.
            // Empty rows clear residual instructions left by an earlier program in the same 200+ group.
            val finishSettingFrame = LinshengProtocol.encode(address(), SliderCommand.Stop)
            check(
                session.sendProgramSequence(
                    instructionFrames = instructions + finishSettingFrame,
                    startFrame = LinshengProtocol.encode(address(), SliderCommand.RunPreset200Plus),
                ),
            ) { "蓝牙连接已断开，AB 程控未能写入" }
            ControlActionResult(true, "本次 AB 目标速度：${current.speedMillimetresPerSecond} 毫米/秒（$speedInterval 微秒/步，校准 ${current.calibration.stepsPerMillimetre} 步/毫米）。写入已排队，约 5.5 秒后发送“200+”启动；此值不是控制板实测速度。")
        }.getOrElse { ControlActionResult(false, it.message ?: "AB 往返参数无效") }
    }

    fun runExample(plan: ProgramWritePlan): ControlActionResult = runCatching {
        requireConnected()
        // Each preset setup is closed with CH before entering another setup or starting playback.
        val finishSettingFrame = LinshengProtocol.encode(address(), SliderCommand.Stop)
        val instructionFrames = plan.groups.flatMap { it.frames + finishSettingFrame }
        check(session.sendProgramSequence(instructionFrames, plan.startFrame)) {
            "蓝牙连接已断开，程控未能写入"
        }
        val overwritten = plan.groups.joinToString("、") { it.preset.label }
        ControlActionResult(
            true,
            "已排队覆盖 $overwritten；程控按 500 毫秒间隔写入，每组以 CH 结束，完成后发送 ${plan.entryGroup.label} 启动命令。控制板未回报实际位置或执行状态",
        )
    }.getOrElse { ControlActionResult(false, it.message ?: "程控配方发送失败") }

    /** Stop wins over queued commands and clears motion commands not yet written. */
    fun stop(): ControlActionResult {
        val stopFrame = runCatching { LinshengProtocol.encode(address(), SliderCommand.Stop) }
            .getOrElse { return ControlActionResult(false, it.message ?: "控制板地址无效") }
        return if (session.sendStop(stopFrame)) {
            ControlActionResult(true, "停止命令已排队；应用无法确认电机已经停稳")
        } else {
            ControlActionResult(false, "未连接控制板，无法发送停止命令")
        }
    }

    private fun pulseInterval(): Long {
        val current = requireNotNull(configuration()) { "请先填写步数/毫米和最大可用行程" }
        require(current.calibration.isValid()) { "请先填写步数/毫米和最大可用行程" }
        val interval = MotionSettings.pulseIntervalMicroseconds(
            current.speedMillimetresPerSecond,
            current.calibration.stepsPerMillimetre,
        )
        val rounded = round(interval).toLong()
        require(rounded in LinshengProtocol.MIN_SPEED_MICROSECONDS_PER_STEP..LinshengProtocol.MAX_SPEED_MICROSECONDS_PER_STEP) {
            "当前速度超出控制板范围，请调整速度或步数/毫米校准"
        }
        return rounded
    }

    private fun requireConnected() {
        check(session.state().phase == BluetoothSessionPhase.Connected) { "请先连接蓝牙控制板" }
    }
}
