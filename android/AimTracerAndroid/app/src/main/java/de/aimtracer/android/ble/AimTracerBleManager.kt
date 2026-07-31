package de.aimtracer.android.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import de.aimtracer.android.model.DeviceConfiguration
import de.aimtracer.android.model.DevicePowerStatus
import de.aimtracer.android.model.DeviceStatus
import de.aimtracer.android.model.LiveMotionSample
import de.aimtracer.android.model.ShotCapture
import de.aimtracer.android.protocol.AimTracerCodec
import de.aimtracer.android.protocol.AimTracerCommand
import de.aimtracer.android.protocol.ProtocolException
import de.aimtracer.android.protocol.ShotAssembler
import java.util.UUID

object AimTracerBle {
    val SERVICE: UUID = UUID.fromString("7B8A0001-6D5B-4F0D-9C6A-3E0D6C0A1000")
    val STATUS: UUID = UUID.fromString("7B8A0002-6D5B-4F0D-9C6A-3E0D6C0A1000")
    val CONTROL: UUID = UUID.fromString("7B8A0003-6D5B-4F0D-9C6A-3E0D6C0A1000")
    val LIVE: UUID = UUID.fromString("7B8A0004-6D5B-4F0D-9C6A-3E0D6C0A1000")
    val SHOT: UUID = UUID.fromString("7B8A0005-6D5B-4F0D-9C6A-3E0D6C0A1000")
    val CONFIG: UUID = UUID.fromString("7B8A0006-6D5B-4F0D-9C6A-3E0D6C0A1000")
    val POWER: UUID = UUID.fromString("7B8A0007-6D5B-4F0D-9C6A-3E0D6C0A1000")
    val BATTERY_SERVICE: UUID =
        UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
    val BATTERY_LEVEL: UUID =
        UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
    val CCC: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
}

sealed interface ConnectionState {
    val label: String
    val ready: Boolean get() = false

    data object Idle : ConnectionState {
        override val label = "Nicht verbunden"
    }

    data object Scanning : ConnectionState {
        override val label = "Suche läuft"
    }

    data class Connecting(val name: String) : ConnectionState {
        override val label = "Verbinde mit $name"
    }

    data object Discovering : ConnectionState {
        override val label = "Dienste werden geladen"
    }

    data class Ready(val name: String) : ConnectionState {
        override val label = name
        override val ready = true
    }

    data class Unavailable(override val label: String) : ConnectionState
}

data class DiscoveredAimTracer(
    val device: BluetoothDevice,
    val name: String,
    val rssi: Int
)

@SuppressLint("MissingPermission")
class AimTracerBleManager(
    private val context: Context,
    private val onShot: (ShotCapture) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private val bluetoothManager =
        context.getSystemService(BluetoothManager::class.java)
    private val adapter get() = bluetoothManager?.adapter
    private val scanner get() = adapter?.bluetoothLeScanner
    private val shotAssembler = ShotAssembler()

    var connectionState by mutableStateOf<ConnectionState>(ConnectionState.Idle)
        private set
    var devices by mutableStateOf<List<DiscoveredAimTracer>>(emptyList())
        private set
    var status by mutableStateOf<DeviceStatus?>(null)
        private set
    var configuration by mutableStateOf(DeviceConfiguration())
        private set
    var powerStatus by mutableStateOf<DevicePowerStatus?>(null)
        private set
    var liveSamples by mutableStateOf<List<LiveMotionSample>>(emptyList())
        private set
    var lastShot by mutableStateOf<ShotCapture?>(null)
        private set
    var message by mutableStateOf<String?>(null)

    private var gatt: BluetoothGatt? = null
    private var statusCharacteristic: BluetoothGattCharacteristic? = null
    private var controlCharacteristic: BluetoothGattCharacteristic? = null
    private var liveCharacteristic: BluetoothGattCharacteristic? = null
    private var shotCharacteristic: BluetoothGattCharacteristic? = null
    private var configCharacteristic: BluetoothGattCharacteristic? = null
    private var powerCharacteristic: BluetoothGattCharacteristic? = null
    private var batteryLevelCharacteristic: BluetoothGattCharacteristic? = null
    private var notificationQueue = ArrayDeque<BluetoothGattCharacteristic>()
    private var initialReadStage = 0

    fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun startScan() {
        if (!hasRequiredPermissions()) {
            message = "Bluetooth-Berechtigung fehlt."
            return
        }
        if (adapter?.isEnabled != true) {
            connectionState = ConnectionState.Unavailable(
                "Bluetooth ist ausgeschaltet"
            )
            return
        }
        devices = emptyList()
        connectionState = ConnectionState.Scanning
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(AimTracerBle.SERVICE))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        runCatching {
            scanner?.startScan(listOf(filter), settings, scanCallback)
        }.onFailure { fail(it) }
        main.postDelayed({
            if (connectionState == ConnectionState.Scanning) stopScan()
        }, 12_000)
    }

    fun stopScan() {
        runCatching { scanner?.stopScan(scanCallback) }
        if (connectionState == ConnectionState.Scanning) {
            connectionState = ConnectionState.Idle
        }
    }

    fun connect(item: DiscoveredAimTracer) {
        stopScan()
        connectionState = ConnectionState.Connecting(item.name)
        runCatching {
            gatt = item.device.connectGatt(
                context,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
        }.onFailure { fail(it) }
    }

    fun disconnect() {
        gatt?.disconnect()
        resetConnection()
    }

    fun send(command: AimTracerCommand) {
        val characteristic = controlCharacteristic ?: run {
            message = "AimTracer ist nicht vollständig verbunden."
            return
        }
        writeCharacteristic(characteristic, byteArrayOf(command.wireValue))
    }

    fun send(configuration: DeviceConfiguration) {
        val characteristic = configCharacteristic ?: run {
            message = "AimTracer ist nicht vollständig verbunden."
            return
        }
        writeCharacteristic(characteristic, AimTracerCodec.encode(configuration))
    }

    fun close() {
        stopScan()
        gatt?.close()
        resetConnection()
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            main.post {
                val name = runCatching {
                    result.scanRecord?.deviceName ?: result.device.name
                }.getOrNull() ?: "AimTracer"
                val item = DiscoveredAimTracer(result.device, name, result.rssi)
                devices = (devices.filterNot {
                    it.device.address == item.device.address
                } + item).sortedByDescending { it.rssi }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            main.post {
                message = "BLE-Suche fehlgeschlagen (Code $errorCode)."
                connectionState = ConnectionState.Idle
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            callbackGatt: BluetoothGatt,
            statusCode: Int,
            newState: Int
        ) {
            main.post {
                if (
                    statusCode == BluetoothGatt.GATT_SUCCESS &&
                    newState == BluetoothProfile.STATE_CONNECTED
                ) {
                    gatt = callbackGatt
                    connectionState = ConnectionState.Discovering
                    callbackGatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    callbackGatt.close()
                    resetConnection()
                } else if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                    message = "Bluetooth-Verbindung fehlgeschlagen ($statusCode)."
                    callbackGatt.close()
                    resetConnection()
                }
            }
        }

        override fun onServicesDiscovered(
            callbackGatt: BluetoothGatt,
            statusCode: Int
        ) {
            main.post {
                if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                    message = "GATT-Dienste konnten nicht geladen werden."
                    return@post
                }
                val service = callbackGatt.getService(AimTracerBle.SERVICE)
                if (service == null) {
                    message = "AimTracer-Service wurde nicht gefunden."
                    return@post
                }
                statusCharacteristic = service.getCharacteristic(AimTracerBle.STATUS)
                controlCharacteristic = service.getCharacteristic(AimTracerBle.CONTROL)
                liveCharacteristic = service.getCharacteristic(AimTracerBle.LIVE)
                shotCharacteristic = service.getCharacteristic(AimTracerBle.SHOT)
                configCharacteristic = service.getCharacteristic(AimTracerBle.CONFIG)
                powerCharacteristic = service.getCharacteristic(AimTracerBle.POWER)
                batteryLevelCharacteristic = callbackGatt
                    .getService(AimTracerBle.BATTERY_SERVICE)
                    ?.getCharacteristic(AimTracerBle.BATTERY_LEVEL)
                if (
                    statusCharacteristic == null ||
                    controlCharacteristic == null ||
                    liveCharacteristic == null ||
                    shotCharacteristic == null ||
                    configCharacteristic == null
                ) {
                    message = "Mindestens eine GATT-Characteristic fehlt."
                    return@post
                }
                notificationQueue = ArrayDeque(
                    listOfNotNull(
                        statusCharacteristic,
                        liveCharacteristic,
                        shotCharacteristic,
                        powerCharacteristic,
                        batteryLevelCharacteristic
                    )
                )
                enableNextNotification()
            }
        }

        override fun onDescriptorWrite(
            callbackGatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            statusCode: Int
        ) {
            main.post {
                if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                    message = "BLE-Benachrichtigungen konnten nicht aktiviert werden."
                    return@post
                }
                enableNextNotification()
            }
        }

        @Deprecated("Android 13 overload is used when available")
        override fun onCharacteristicChanged(
            callbackGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            handleValue(characteristic.uuid, characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(
            callbackGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleValue(characteristic.uuid, value)
        }

        @Deprecated("Android 13 overload is used when available")
        override fun onCharacteristicRead(
            callbackGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            statusCode: Int
        ) {
            if (statusCode == BluetoothGatt.GATT_SUCCESS) {
                handleRead(characteristic.uuid, characteristic.value ?: return)
            }
        }

        override fun onCharacteristicRead(
            callbackGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            statusCode: Int
        ) {
            if (statusCode == BluetoothGatt.GATT_SUCCESS) {
                handleRead(characteristic.uuid, value)
            }
        }

        override fun onCharacteristicWrite(
            callbackGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            statusCode: Int
        ) {
            if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                main.post {
                    message = "Schreiben auf AimTracer fehlgeschlagen."
                }
            } else if (characteristic.uuid == AimTracerBle.CONFIG) {
                main.post {
                    configCharacteristic?.let(callbackGatt::readCharacteristic)
                }
            }
        }
    }

    private fun enableNextNotification() {
        val callbackGatt = gatt ?: return
        val characteristic = notificationQueue.removeFirstOrNull()
        if (characteristic == null) {
            initialReadStage = 1
            statusCharacteristic?.let(callbackGatt::readCharacteristic)
            val name = runCatching {
                callbackGatt.device.name
            }.getOrNull() ?: "AimTracer"
            connectionState = ConnectionState.Ready(name)
            return
        }
        callbackGatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(AimTracerBle.CCC)
        if (descriptor == null) {
            enableNextNotification()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val descriptorValue = if (
                characteristic.properties and
                BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
            ) {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }
            callbackGatt.writeDescriptor(
                descriptor,
                descriptorValue
            )
        } else {
            val descriptorValue = if (
                characteristic.properties and
                BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
            ) {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }
            @Suppress("DEPRECATION")
            descriptor.value = descriptorValue
            @Suppress("DEPRECATION")
            callbackGatt.writeDescriptor(descriptor)
        }
    }

    private fun handleRead(uuid: UUID, value: ByteArray) {
        handleValue(uuid, value)
        main.post {
            if (uuid == AimTracerBle.STATUS && initialReadStage == 1) {
                initialReadStage = 2
                configCharacteristic?.let { gatt?.readCharacteristic(it) }
            } else if (uuid == AimTracerBle.CONFIG && initialReadStage == 2) {
                initialReadStage = 3
                val next = powerCharacteristic ?: batteryLevelCharacteristic
                next?.let { gatt?.readCharacteristic(it) }
            } else if (uuid == AimTracerBle.POWER && initialReadStage == 3) {
                initialReadStage = 4
                batteryLevelCharacteristic?.let {
                    gatt?.readCharacteristic(it)
                }
            }
        }
    }

    private fun handleValue(uuid: UUID, value: ByteArray) {
        main.post {
            runCatching {
                when (uuid) {
                    AimTracerBle.STATUS -> {
                        status = AimTracerCodec.status(value)
                    }

                    AimTracerBle.LIVE -> {
                        val next = liveSamples + AimTracerCodec.live(value)
                        liveSamples = next.takeLast(180)
                    }

                    AimTracerBle.SHOT -> {
                        shotAssembler.ingest(value)?.let { shot ->
                            lastShot = shot
                            onShot(shot)
                        }
                    }

                    AimTracerBle.CONFIG -> {
                        configuration = AimTracerCodec.configuration(value)
                    }

                    AimTracerBle.POWER -> {
                        powerStatus = AimTracerCodec.powerStatus(value)
                    }

                    AimTracerBle.BATTERY_LEVEL -> {
                        val level = value.firstOrNull()
                            ?.toInt()
                            ?.and(0xFF)
                            ?.coerceIn(0, 100)
                            ?: throw ProtocolException(
                                "BLE-Akkustand fehlt."
                            )
                        val previous = powerStatus
                        powerStatus = DevicePowerStatus(
                            levelPercent = level,
                            isCharging = previous?.isCharging ?: false,
                            externalPowerPresent =
                                previous?.externalPowerPresent ?: false,
                            chargeSignalActive =
                                previous?.chargeSignalActive ?: false,
                            isLow = level <= 20,
                            isCritical = level <= 5,
                            millivolts = previous?.millivolts ?: 0,
                            chargeCurrentMa = previous?.chargeCurrentMa ?: 50
                        )
                    }
                }
            }.onFailure { fail(it) }
        }
    }

    private fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        val callbackGatt = gatt ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            callbackGatt.writeCharacteristic(
                characteristic,
                value,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType =
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            characteristic.value = value
            @Suppress("DEPRECATION")
            callbackGatt.writeCharacteristic(characteristic)
        }
    }

    private fun fail(error: Throwable) {
        main.post {
            message = error.message ?: "Unbekannter Bluetooth-Fehler."
        }
    }

    private fun resetConnection() {
        statusCharacteristic = null
        controlCharacteristic = null
        liveCharacteristic = null
        shotCharacteristic = null
        configCharacteristic = null
        powerCharacteristic = null
        batteryLevelCharacteristic = null
        status = null
        powerStatus = null
        liveSamples = emptyList()
        initialReadStage = 0
        connectionState = ConnectionState.Idle
    }
}
