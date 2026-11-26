package com.lingsheng.slidercontrol.motion

import kotlin.math.round
import kotlin.math.sqrt

data class MotionCalibration(
    val stepsPerMillimetre: Double,
    val maximumTravelMillimetres: Double,
) {
    fun isValid(): Boolean =
        stepsPerMillimetre.isFinite() && stepsPerMillimetre > 0.0 &&
            maximumTravelMillimetres.isFinite() && maximumTravelMillimetres > 0.0
}

data class ProgramPositionFactors(val baseSteps: Int, val multiplier: Int)

object MotionSettings {
    const val DEFAULT_STEPS_PER_REVOLUTION = 6_400L

    fun stepCountForDistance(
        distanceMillimetres: Double,
        calibration: MotionCalibration,
        controllerMaximumSteps: Long? = null,
    ): Long {
        require(calibration.isValid()) { "请先填写有效的步数/毫米和最大可用行程" }
        require(distanceMillimetres.isFinite() && distanceMillimetres > 0.0) { "距离必须大于零" }
        require(distanceMillimetres <= calibration.maximumTravelMillimetres) { "距离超过已设置的最大可用行程" }

        val unroundedSteps = distanceMillimetres * calibration.stepsPerMillimetre
        require(unroundedSteps.isFinite() && unroundedSteps < Long.MAX_VALUE.toDouble()) { "换算后的步数超出范围" }
        val steps = round(unroundedSteps).toLong()
        require(steps > 0L) { "距离太小，换算后不足一步" }
        if (controllerMaximumSteps != null) {
            require(controllerMaximumSteps > 0L && steps <= controllerMaximumSteps) { "步数超过控制板允许的上限" }
        }
        return steps
    }

    fun pulseIntervalMicroseconds(speedMillimetresPerSecond: Double, stepsPerMillimetre: Double): Double {
        require(speedMillimetresPerSecond.isFinite() && speedMillimetresPerSecond > 0.0) { "速度必须大于零" }
        require(stepsPerMillimetre.isFinite() && stepsPerMillimetre > 0.0) { "请先填写有效的步数/毫米" }
        return 1_000_000.0 / (speedMillimetresPerSecond * stepsPerMillimetre)
    }

    /**
     * The controller program stores position values as two 16-bit operands and multiplies them.
     * Prefer the documented 6400-step revolution representation used by the supplied AB example.
     */
    fun programPositionFactors(totalSteps: Long): ProgramPositionFactors {
        require(totalSteps in 1L..65_535L * 65_535L) { "B 点步数超出控制板程控范围" }

        if (totalSteps % DEFAULT_STEPS_PER_REVOLUTION == 0L) {
            val revolutions = totalSteps / DEFAULT_STEPS_PER_REVOLUTION
            if (revolutions in 1L..65_535L) {
                return ProgramPositionFactors(DEFAULT_STEPS_PER_REVOLUTION.toInt(), revolutions.toInt())
            }
        }
        if (totalSteps <= 65_535L) return ProgramPositionFactors(totalSteps.toInt(), 1)

        var base = minOf(65_535L, sqrt(totalSteps.toDouble()).toLong())
        while (base > 1L) {
            if (totalSteps % base == 0L) {
                val multiplier = totalSteps / base
                if (multiplier <= 65_535L) {
                    return ProgramPositionFactors(base.toInt(), multiplier.toInt())
                }
            }
            base--
        }
        throw IllegalArgumentException("B 点步数无法由控制板的两个 16 位程控参数精确表示")
    }
}
