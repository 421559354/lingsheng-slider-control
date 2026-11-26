package com.lingsheng.slidercontrol

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lingsheng.slidercontrol.bluetooth.BluetoothSerialSession
import com.lingsheng.slidercontrol.bluetooth.BluetoothSessionPhase
import com.lingsheng.slidercontrol.bluetooth.BluetoothSessionState
import com.lingsheng.slidercontrol.bluetooth.PairedBluetoothDevice
import com.lingsheng.slidercontrol.examples.ExampleCatalog
import com.lingsheng.slidercontrol.examples.LoadedExample
import com.lingsheng.slidercontrol.examples.ProgramRecipeParser
import com.lingsheng.slidercontrol.motion.MotionCalibration
import com.lingsheng.slidercontrol.motion.MotionConfiguration
import com.lingsheng.slidercontrol.motion.SliderCoordinator
import com.lingsheng.slidercontrol.protocol.MotionDirection
import com.lingsheng.slidercontrol.storage.SliderPreferences
import com.lingsheng.slidercontrol.ui.ControlScreen
import com.lingsheng.slidercontrol.ui.ControlUiState
import com.lingsheng.slidercontrol.ui.ExampleDetailScreen
import com.lingsheng.slidercontrol.ui.ExampleLibraryScreen
import com.lingsheng.slidercontrol.ui.ManualDirection
import com.lingsheng.slidercontrol.ui.PairedDeviceUi
import com.lingsheng.slidercontrol.ui.SettingsScreen
import com.lingsheng.slidercontrol.ui.SettingsUiState
import com.lingsheng.slidercontrol.ui.SliderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val preferences = remember { SliderPreferences(applicationContext) }
            val saved = remember { preferences.load() }
            var sessionState by remember { mutableStateOf(BluetoothSessionState()) }
            var actionMessage by remember { mutableStateOf("") }
            var roundTripStatusMessage by remember { mutableStateOf("") }
            var showSettings by remember { mutableStateOf(false) }
            var showExampleLibrary by remember { mutableStateOf(false) }
            var selectedExample by remember { mutableStateOf<LoadedExample?>(null) }
            var exampleSearch by remember { mutableStateOf("") }
            var exampleCategory by remember { mutableStateOf<String?>(null) }
            var speed by remember { mutableStateOf(saved.speedMillimetresPerSecond) }
            var deviceAddressByte by remember { mutableStateOf(saved.deviceAddressByte) }
            var lastDeviceAddress by remember { mutableStateOf(saved.bluetoothAddress) }
            var stepsPerMm by remember { mutableStateOf(saved.stepsPerMillimetre) }
            var maximumTravel by remember { mutableStateOf(saved.maximumTravelMillimetres) }
            var distanceMm by remember { mutableStateOf("100") }
            var repeatCount by remember { mutableStateOf("3") }
            var repeatContinuously by remember { mutableStateOf(true) }
            val pairedDevices = remember { mutableStateListOf<PairedBluetoothDevice>() }
            val examples = remember {
                ExampleCatalog.load(applicationContext).map { entry ->
                    val source = ExampleCatalog.loadText(applicationContext, entry)
                    LoadedExample(entry, source, ProgramRecipeParser.parse(entry, source))
                }
            }
            val session = remember {
                BluetoothSerialSession(
                    context = applicationContext,
                    onStateChanged = {
                        sessionState = it
                        if (it.phase != BluetoothSessionPhase.Connected) actionMessage = ""
                    },
                    onBytesReceived = { /* Response framing is intentionally not guessed. */ },
                )
            }
            val coordinator = remember(session) {
                SliderCoordinator(
                    session = session,
                    address = { deviceAddressByte.toIntOrNull() ?: -1 },
                    configuration = {
                        MotionConfiguration(
                            speedMillimetresPerSecond = speed.toDoubleOrNull() ?: Double.NaN,
                            calibration = MotionCalibration(
                                stepsPerMillimetre = stepsPerMm.toDoubleOrNull() ?: Double.NaN,
                                maximumTravelMillimetres = maximumTravel.toDoubleOrNull() ?: Double.NaN,
                            ),
                        )
                    },
                )
            }

            fun refreshPairedDevices() {
                pairedDevices.clear()
                pairedDevices.addAll(session.pairedDevices())
                if (!session.hasConnectPermission()) {
                    sessionState = BluetoothSessionState(BluetoothSessionPhase.Error, message = "请允许蓝牙连接权限以显示已配对设备")
                } else if (!session.bluetoothEnabled()) {
                    sessionState = BluetoothSessionState(BluetoothSessionPhase.Error, message = "请先打开手机蓝牙")
                }
            }

            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) refreshPairedDevices()
                else sessionState = BluetoothSessionState(BluetoothSessionPhase.Error, message = "未授予蓝牙连接权限，无法读取已配对设备")
            }

            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                if (session.hasConnectPermission()) refreshPairedDevices()
            }
            LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
                if (sessionState.phase == BluetoothSessionPhase.Connected) coordinator.stop()
            }
            DisposableEffect(session) {
                onDispose { session.close() }
            }

            val calibration = MotionCalibration(
                stepsPerMillimetre = stepsPerMm.toDoubleOrNull() ?: Double.NaN,
                maximumTravelMillimetres = maximumTravel.toDoubleOrNull() ?: Double.NaN,
            )
            val requestedRepeatCount = if (repeatContinuously) null else repeatCount.toIntOrNull() ?: 0
            val roundTripUnavailableReason = coordinator.roundTripUnavailableReason(
                distanceMillimetres = distanceMm.toDoubleOrNull() ?: Double.NaN,
                repeatCount = requestedRepeatCount,
            )
            val uiState = ControlUiState(
                bluetoothReady = session.hasConnectPermission() && session.bluetoothEnabled(),
                bluetoothPermissionNeeded = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !session.hasConnectPermission(),
                connected = sessionState.phase == BluetoothSessionPhase.Connected,
                selectedDeviceAddress = sessionState.deviceAddress ?: lastDeviceAddress,
                pairedDevices = pairedDevices.map { PairedDeviceUi(it.address, it.name) },
                connectionMessage = sessionState.message.takeIf {
                    sessionState.phase == BluetoothSessionPhase.Error || sessionState.phase == BluetoothSessionPhase.Unavailable
                } ?: actionMessage.ifBlank { sessionState.message },
                speedMmPerSecond = speed,
                distanceMm = distanceMm,
                repeatCount = repeatCount,
                repeatContinuously = repeatContinuously,
                calibrationReady = calibration.isValid(),
                motionEnabled = coordinator.motionReady(),
                busy = sessionState.phase == BluetoothSessionPhase.Connecting,
                roundTripAvailable = roundTripUnavailableReason == null,
                roundTripUnavailableReason = roundTripUnavailableReason ?: "",
                roundTripStatusMessage = roundTripStatusMessage,
            )
            val categories = examples.map { it.entry.category }.distinct()
            val matchingExampleIds = ExampleCatalog.search(
                entries = examples.map { it.entry },
                query = exampleSearch,
                category = exampleCategory,
                sourceTextById = examples.associate { it.entry.id to it.sourceText },
            ).mapTo(mutableSetOf()) { it.id }
            val visibleExamples = examples.filter { it.entry.id in matchingExampleIds }

            SliderTheme {
                when {
                    showSettings -> {
                    SettingsScreen(
                        state = SettingsUiState(deviceAddressByte, stepsPerMm, maximumTravel),
                        onDeviceAddressChange = { deviceAddressByte = it },
                        onStepsPerMmChange = { stepsPerMm = it },
                        onMaxTravelChange = { maximumTravel = it },
                        onSave = {
                            preferences.saveCalibration(stepsPerMm, maximumTravel)
                            preferences.saveDeviceAddressByte(deviceAddressByte)
                            showSettings = false
                        },
                        onBack = { showSettings = false },
                    )
                    }
                    selectedExample != null -> {
                        ExampleDetailScreen(
                            example = requireNotNull(selectedExample),
                            address = deviceAddressByte.toIntOrNull() ?: -1,
                            connected = sessionState.phase == BluetoothSessionPhase.Connected,
                            actionMessage = actionMessage,
                            onRunRequest = { plan -> actionMessage = coordinator.runExample(plan).message },
                            onBack = { selectedExample = null; showExampleLibrary = true },
                            onStop = { actionMessage = coordinator.stop().message },
                        )
                    }
                    showExampleLibrary -> {
                        ExampleLibraryScreen(
                            examples = visibleExamples,
                            categories = categories,
                            query = exampleSearch,
                            selectedCategory = exampleCategory,
                            connected = sessionState.phase == BluetoothSessionPhase.Connected,
                            actionMessage = actionMessage,
                            onSearch = { exampleSearch = it },
                            onCategorySelect = { exampleCategory = it },
                            onSelect = { selectedExample = it; showExampleLibrary = false },
                            onBack = { showExampleLibrary = false },
                            onStop = { actionMessage = coordinator.stop().message },
                        )
                    }
                    else -> ControlScreen(
                        state = uiState,
                        onConnect = { address ->
                            actionMessage = ""
                            if (session.hasConnectPermission()) {
                                lastDeviceAddress = address
                                preferences.saveDeviceAddress(address)
                                session.connect(address)
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            }
                        },
                        onDisconnect = { session.disconnect(); actionMessage = "" },
                        onOpenBluetoothSettings = { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                        onRequestBluetoothPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            }
                        },
                        onSpeedChange = { change ->
                            val current = speed.toDoubleOrNull() ?: 5.0
                            speed = when (change) {
                                "+" -> (current + 0.5).toString()
                                "-" -> (current - 0.5).coerceAtLeast(0.5).toString()
                                else -> change
                            }
                            preferences.saveSpeed(speed)
                            actionMessage = if (sessionState.phase == BluetoothSessionPhase.Connected) {
                                speed.toDoubleOrNull()?.let { coordinator.setSpeed(it).message }.orEmpty()
                            } else {
                                ""
                            }
                        },
                        onDistanceChange = { distanceMm = it; roundTripStatusMessage = "" },
                        onRepeatCountChange = { repeatCount = it; roundTripStatusMessage = "" },
                        onRepeatModeChange = { repeatContinuously = it; roundTripStatusMessage = "" },
                        onManualStart = { direction ->
                            actionMessage = coordinator.beginManual(
                                if (direction == ManualDirection.Forward) MotionDirection.Forward else MotionDirection.Reverse,
                            ).message
                        },
                        onManualStop = { actionMessage = coordinator.endManual().message },
                        onRoundTripStart = {
                            val result = coordinator.startRoundTrip(
                                distanceMm.toDoubleOrNull() ?: Double.NaN,
                                requestedRepeatCount,
                            )
                            actionMessage = result.message
                            roundTripStatusMessage = result.message
                        },
                        onStop = {
                            actionMessage = coordinator.stop().message
                            roundTripStatusMessage = actionMessage
                        },
                        onOpenExamples = { showExampleLibrary = true },
                        onOpenSettings = { showSettings = true },
                    )
                }
            }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !session.hasConnectPermission()) {
                    permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    refreshPairedDevices()
                }
            }
        }
    }
}
