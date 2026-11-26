package com.lingsheng.slidercontrol.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

data class PairedBluetoothDevice(val address: String, val name: String)

enum class BluetoothSessionPhase { Unavailable, Disconnected, Connecting, Connected, Error }

data class BluetoothSessionState(
    val phase: BluetoothSessionPhase = BluetoothSessionPhase.Disconnected,
    val deviceAddress: String? = null,
    val deviceName: String? = null,
    val message: String = "未连接",
)

class BluetoothSerialSession(
    context: Context,
    private val onStateChanged: (BluetoothSessionState) -> Unit,
    private val onBytesReceived: (ByteArray) -> Unit,
) : AutoCloseable {
    private data class PendingWrite(
        val bytes: ByteArray,
        val delayAfterWriteMillis: Long = 0,
    )

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter
    private val connector = Executors.newSingleThreadExecutor()
    private val timer: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val pendingWrites = LinkedBlockingDeque<PendingWrite>()
    private val lock = Any()

    @Volatile private var currentState = BluetoothSessionState()
    @Volatile private var connectingSocket: BluetoothSocket? = null
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var input: InputStream? = null
    @Volatile private var output: OutputStream? = null
    @Volatile private var active = false
    private var connectTimeout: ScheduledFuture<*>? = null
    private var readerThread: Thread? = null
    private var writerThread: Thread? = null

    fun state(): BluetoothSessionState = currentState

    fun hasConnectPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun pairedDevices(): List<PairedBluetoothDevice> {
        if (adapter == null || !hasConnectPermission()) return emptyList()
        return try {
            adapter.bondedDevices
                .map { PairedBluetoothDevice(it.address, it.name ?: "蓝牙设备") }
                .sortedBy { it.name.lowercase() }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    fun bluetoothEnabled(): Boolean = try {
        adapter?.isEnabled == true
    } catch (_: SecurityException) {
        false
    }

    fun connect(address: String) {
        if (adapter == null) {
            updateState(BluetoothSessionState(BluetoothSessionPhase.Unavailable, message = "此设备不支持蓝牙"))
            return
        }
        if (!hasConnectPermission()) {
            updateState(BluetoothSessionState(BluetoothSessionPhase.Error, message = "需要允许蓝牙连接权限"))
            return
        }
        if (!bluetoothEnabled()) {
            updateState(BluetoothSessionState(BluetoothSessionPhase.Error, message = "请先打开手机蓝牙"))
            return
        }

        val device = try {
            adapter.bondedDevices.firstOrNull { it.address == address }
        } catch (_: SecurityException) {
            null
        }
        if (device == null) {
            updateState(BluetoothSessionState(BluetoothSessionPhase.Error, message = "设备未配对，请到系统蓝牙设置中配对"))
            return
        }

        disconnect(updateState = false)
        updateState(BluetoothSessionState(BluetoothSessionPhase.Connecting, address, safeName(device), "正在连接 ${safeName(device)}…"))
        connector.execute {
            var candidate: BluetoothSocket? = null
            try {
                candidate = device.createRfcommSocketToServiceRecord(SPP_UUID)
                connectingSocket = candidate
                val socketForTimeout = candidate
                connectTimeout = timer.schedule({
                    if (connectingSocket === socketForTimeout && socket == null) {
                        try { socketForTimeout.close() } catch (_: IOException) { }
                    }
                }, CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)

                candidate.connect()
                connectTimeout?.cancel(false)
                synchronized(lock) {
                    socket = candidate
                    connectingSocket = null
                    input = candidate.inputStream
                    output = candidate.outputStream
                    active = true
                }
                updateState(BluetoothSessionState(BluetoothSessionPhase.Connected, address, safeName(device), "已连接 ${safeName(device)}"))
                startIoThreads()
            } catch (error: SecurityException) {
                safelyClose(candidate)
                connectingSocket = null
                updateState(BluetoothSessionState(BluetoothSessionPhase.Error, address, safeName(device), "蓝牙权限不足，请重新授权"))
            } catch (error: IOException) {
                safelyClose(candidate)
                connectingSocket = null
                connectTimeout?.cancel(false)
                updateState(BluetoothSessionState(BluetoothSessionPhase.Error, address, safeName(device), "连接失败：${error.localizedMessage ?: "请确认设备已配对并在附近"}"))
            }
        }
    }

    fun send(bytes: ByteArray): Boolean {
        if (!active || socket == null) return false
        synchronized(pendingWrites) {
            if (!active || socket == null) return false
            pendingWrites.offerLast(PendingWrite(bytes.copyOf()))
        }
        return true
    }

    /** Queue controller program rows at the same cadence used by the official app. */
    fun sendProgramSequence(
        instructionFrames: List<ByteArray>,
        startFrame: ByteArray,
        intervalMillis: Long = 500,
    ): Boolean {
        require(intervalMillis >= 0) { "程控发送间隔不能小于 0" }
        if (!active || socket == null) return false
        synchronized(pendingWrites) {
            if (!active || socket == null) return false
            instructionFrames.forEach { frame ->
                pendingWrites.offerLast(PendingWrite(frame.copyOf(), intervalMillis))
            }
            pendingWrites.offerLast(PendingWrite(startFrame.copyOf()))
        }
        return true
    }

    /** Drop any queued motion and put stop ahead of every command not yet written. */
    fun sendStop(bytes: ByteArray): Boolean {
        if (!active || socket == null) return false
        synchronized(pendingWrites) {
            if (!active || socket == null) return false
            pendingWrites.clear()
            pendingWrites.offerFirst(PendingWrite(bytes.copyOf()))
        }
        return true
    }

    fun disconnect() = disconnect(updateState = true)

    private fun disconnect(updateState: Boolean) {
        active = false
        pendingWrites.clear()
        connectTimeout?.cancel(false)
        connectTimeout = null
        val oldConnecting = connectingSocket
        val oldSocket = socket
        connectingSocket = null
        socket = null
        closeQuietly(input)
        closeQuietly(output)
        input = null
        output = null
        safelyClose(oldConnecting)
        if (oldSocket !== oldConnecting) safelyClose(oldSocket)
        readerThread?.interrupt()
        writerThread?.interrupt()
        readerThread = null
        writerThread = null
        if (updateState) updateState(BluetoothSessionState(BluetoothSessionPhase.Disconnected, message = "已断开连接"))
    }

    private fun startIoThreads() {
        readerThread = Thread(::readLoop, "slider-bt-reader").apply { isDaemon = true; start() }
        writerThread = Thread(::writeLoop, "slider-bt-writer").apply { isDaemon = true; start() }
    }

    private fun readLoop() {
        val source = input ?: return
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        try {
            while (active) {
                val count = source.read(buffer)
                if (count < 0) break
                if (count > 0) {
                    val chunk = buffer.copyOf(count)
                    mainHandler.post { onBytesReceived(chunk) }
                }
            }
            if (active) failRemote("控制板已断开连接")
        } catch (error: IOException) {
            if (active) failRemote("蓝牙串口读取失败：${error.localizedMessage ?: "连接中断"}")
        }
    }

    private fun writeLoop() {
        try {
            while (active) {
                val next = pendingWrites.takeFirst()
                val destination = output ?: break
                destination.write(next.bytes)
                destination.flush()
                if (next.delayAfterWriteMillis > 0) Thread.sleep(next.delayAfterWriteMillis)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (error: IOException) {
            if (active) failRemote("蓝牙串口发送失败：${error.localizedMessage ?: "连接中断"}")
        }
    }

    private fun failRemote(message: String) {
        disconnect(updateState = false)
        updateState(BluetoothSessionState(BluetoothSessionPhase.Error, message = message))
    }

    private fun updateState(next: BluetoothSessionState) {
        currentState = next
        mainHandler.post { onStateChanged(next) }
    }

    private fun safeName(device: BluetoothDevice): String = try {
        device.name ?: "蓝牙设备"
    } catch (_: SecurityException) {
        "蓝牙设备"
    }

    private fun closeQuietly(resource: AutoCloseable?) {
        try { resource?.close() } catch (_: Exception) { }
    }

    private fun safelyClose(socket: BluetoothSocket?) {
        try { socket?.close() } catch (_: IOException) { }
    }

    override fun close() {
        disconnect(updateState = false)
        connector.shutdownNow()
        timer.shutdownNow()
    }

    private companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val CONNECT_TIMEOUT_SECONDS = 20L
    }
}
