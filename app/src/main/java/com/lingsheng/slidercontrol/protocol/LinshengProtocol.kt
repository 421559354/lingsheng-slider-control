package com.lingsheng.slidercontrol.protocol

enum class MotionDirection { Forward, Reverse }

sealed interface SliderCommand {
    data class SetSpeed(val microsecondsPerStep: Long) : SliderCommand
    data class MoveContinuously(val direction: MotionDirection) : SliderCommand
    data class MoveSteps(val direction: MotionDirection, val steps: Long) : SliderCommand
    data class MoveToPosition(val stepsFromControllerOrigin: Long) : SliderCommand
    data object RunPreset200Plus : SliderCommand
    data class RunPresetProgram(val commandId: Int) : SliderCommand
    data object Stop : SliderCommand
}

object LinshengProtocol {
    const val PACKET_LENGTH = 6
    const val PROGRAM_SETUP_COMMAND = 70
    const val PROGRAM_SLOT_200_PLUS = 210
    const val PROGRAM_SPEED_OPCODE = 8
    const val PROGRAM_POSITION_OPCODE = 7
    const val PROGRAM_MULTIPLIER_OPCODE = 9
    const val PROGRAM_REPEAT_OPCODE = 14
    const val PROGRAM_SECONDARY_REPEAT_FUNCTION = 1
    /** Empty program rows are encoded as zero by the official program editor. */
    const val PROGRAM_EMPTY_OPCODE = 0
    const val PROGRAM_STOP_VALUE = 43
    const val PRESET_200_PLUS_COMMAND = 8
    const val MAX_PROGRAM_VALUE = 65_535
    const val MIN_SPEED_MICROSECONDS_PER_STEP = 30L
    const val MAX_PROGRAM_SPEED_MICROSECONDS_PER_STEP = 49_000L
    const val MAX_SPEED_MICROSECONDS_PER_STEP = 12_495_000L
    const val MAX_STEP_PARAMETER = 4_294_836_225L

    /** Encode each command's payload; speed has its own wire format. */
    fun encode(address: Int, command: SliderCommand): ByteArray {
        require(address in 0..0xFF) { "控制板地址必须是 0 到 255" }

        val (commandId, value) = when (command) {
            is SliderCommand.SetSpeed -> {
                require(command.microsecondsPerStep in MIN_SPEED_MICROSECONDS_PER_STEP..MAX_SPEED_MICROSECONDS_PER_STEP) {
                    "速度必须在 $MIN_SPEED_MICROSECONDS_PER_STEP 到 $MAX_SPEED_MICROSECONDS_PER_STEP 微秒/步之间"
                }
                // Official APK C0060.m9412$ / fasongquanshu:
                // address, 87, 0, extended-delay count, interval high, interval low.
                // Long delays use units of 49000 us, not a 32-bit integer.
                val interval = command.microsecondsPerStep
                val extension = if (interval <= 49_000L) 0L else interval / 49_000L
                val remainder = if (interval <= 49_000L) interval else interval % 49_000L
                return byteArrayOf(
                    address.toByte(), 87, 0, extension.toByte(),
                    (remainder ushr 8).toByte(), remainder.toByte(),
                )
            }
            is SliderCommand.MoveContinuously -> (if (command.direction == MotionDirection.Forward) 2 else 3) to 0L
            is SliderCommand.MoveSteps -> {
                require(command.steps in 1L..MAX_STEP_PARAMETER) { "步数超出控制板范围" }
                (if (command.direction == MotionDirection.Forward) 91 else 92) to command.steps
            }
            is SliderCommand.MoveToPosition -> {
                require(command.stepsFromControllerOrigin in 0L..MAX_STEP_PARAMETER) { "位置超出控制板范围" }
                88 to command.stepsFromControllerOrigin
            }
            SliderCommand.RunPreset200Plus -> PRESET_200_PLUS_COMMAND to 0L
            is SliderCommand.RunPresetProgram -> {
                require(command.commandId in 7..29) { "程控预置启动编号未确认或目标不唯一" }
                command.commandId to 0L
            }
            SliderCommand.Stop -> 1 to 0L
        }

        return byteArrayOf(
            address.toByte(),
            commandId.toByte(),
            value.toByte(),
            (value ushr 8).toByte(),
            (value ushr 16).toByte(),
            (value ushr 24).toByte(),
        )
    }

    /** Encodes one controller-side program row: address, command 70, slot, opcode, value high, value low. */
    fun encodeProgramInstruction(address: Int, slot: Int, opcode: Int, value: Int): ByteArray {
        require(address in 0..0xFF) { "控制板地址必须是 0 到 255" }
        require(slot in 0..0xFF) { "程控槽位必须是 0 到 255" }
        require(opcode in 0..0xFF) { "程控操作码必须是 0 到 255" }
        require(value in 0..MAX_PROGRAM_VALUE) { "程控参数必须是 0 到 65535" }

        return byteArrayOf(
            address.toByte(),
            PROGRAM_SETUP_COMMAND.toByte(),
            slot.toByte(),
            opcode.toByte(),
            (value ushr 8).toByte(),
            value.toByte(),
        )
    }

}
