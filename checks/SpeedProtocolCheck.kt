import com.lingsheng.slidercontrol.bluetooth.BluetoothSerialSession
import com.lingsheng.slidercontrol.motion.MotionCalibration
import com.lingsheng.slidercontrol.motion.MotionConfiguration
import com.lingsheng.slidercontrol.motion.SliderCoordinator
import com.lingsheng.slidercontrol.protocol.LinshengProtocol
import com.lingsheng.slidercontrol.protocol.MotionDirection
import com.lingsheng.slidercontrol.protocol.SliderCommand

private fun expectFrame(actual: ByteArray, vararg expected: Int) {
    check(actual.map { it.toInt() and 255 } == expected.toList()) {
        "expected ${expected.toList()}, got ${actual.map { it.toInt() and 255 }}"
    }
}

fun main() {
    var failures = 0
    var checks = 0
    fun case(name: String, body: () -> Unit) {
        checks++
        try {
            body()
            println("PASS $name")
        } catch (error: Throwable) {
            failures++
            println("FAIL $name: ${error.message}")
        }
    }

    // Independent fixtures from C0060.m9412$ / fasongquanshu in the official APK.
    // Command 87 uses 00, slow-extension, interval-high, interval-low.
    val speedFixtures = listOf(
        30L to intArrayOf(7, 87, 0, 0, 0, 30),
        1124L to intArrayOf(7, 87, 0, 0, 4, 100),
        562L to intArrayOf(7, 87, 0, 0, 2, 50),
        281L to intArrayOf(7, 87, 0, 0, 1, 25),
        49000L to intArrayOf(7, 87, 0, 0, 191, 104),
        49001L to intArrayOf(7, 87, 0, 1, 0, 1),
        98000L to intArrayOf(7, 87, 0, 2, 0, 0),
        12495000L to intArrayOf(7, 87, 0, 255, 0, 0),
    )
    speedFixtures.forEach { (interval, frame) ->
        case("official speed frame at $interval us") {
            expectFrame(LinshengProtocol.encode(7, SliderCommand.SetSpeed(interval)), *frame)
        }
    }
    case("reject unsupported speed without sending") {
        for (interval in listOf(0L, 29L, 12495001L)) {
            check(runCatching { LinshengProtocol.encode(0, SliderCommand.SetSpeed(interval)) }.isFailure)
        }
    }

    val transport = BluetoothSerialSession()
    var configuration = MotionConfiguration(5.0, MotionCalibration(178.0, 500.0))
    val coordinator = SliderCoordinator(transport, { 0 }, { configuration })
    case("AB takes latest speed on each write, preserving distance and repeat") {
        check(coordinator.startRoundTrip(100.0, 3).accepted)
        expectFrame(transport.written[0], 0, 70, 210, 8, 4, 100)
        val first = transport.written.map { it.copyOf() }
        configuration = configuration.copy(speedMillimetresPerSecond = 20.0)
        transport.written.clear()
        check(coordinator.startRoundTrip(100.0, 3).accepted)
        expectFrame(transport.written[0], 0, 70, 210, 8, 1, 25)
        check(first.size == transport.written.size)
        first.drop(1).zip(transport.written.drop(1)).forEach { (before, after) ->
            check(before.contentEquals(after))
        }
        expectFrame(transport.written[2], 0, 70, 212, 9, 69, 136) // 100 mm * 178 = 17800 steps.
        expectFrame(transport.written[4], 0, 70, 214, 14, 1, 3)
        expectFrame(transport.written.last(), 0, 8, 0, 0, 0, 0)
    }
    case("main screen live speed reaches official command 87 format") {
        transport.written.clear()
        check(coordinator.setSpeed(10.0).accepted)
        expectFrame(transport.written.single(), 0, 87, 0, 0, 2, 50)
    }
    case("manual control applies the same interval as AB before moving") {
        transport.written.clear()
        check(coordinator.beginManual(MotionDirection.Forward).accepted)
        expectFrame(transport.written[0], 0, 87, 0, 0, 1, 25)
        expectFrame(transport.written[1], 0, 2, 0, 0, 0, 0)
    }
    println("$checks checks, $failures failures (offline; no controller connected)")
    check(failures == 0) { "Speed protocol regression failed" }
}
