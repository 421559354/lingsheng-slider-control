package com.lingsheng.slidercontrol.bluetooth

// Offline transport substitute only. The Android app never compiles this source.
enum class BluetoothSessionPhase { Connected, Disconnected }
data class BluetoothSessionState(val phase: BluetoothSessionPhase)

class BluetoothSerialSession {
    val written = mutableListOf<ByteArray>()
    fun state() = BluetoothSessionState(BluetoothSessionPhase.Connected)
    fun send(bytes: ByteArray): Boolean {
        written += bytes.copyOf()
        return true
    }
    fun sendProgramSequence(instructionFrames: List<ByteArray>, startFrame: ByteArray): Boolean {
        instructionFrames.forEach { send(it) }
        return send(startFrame)
    }
    fun sendStop(bytes: ByteArray) = send(bytes)
}
