package id.ns200.cdir7

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlin.math.min

data class DiscoveredBleDevice(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val rssi: Int,
    val isCdiCandidate: Boolean
)

sealed class OtaState {
    object Idle : OtaState()
    data class Preparing(val message: String) : OtaState()
    data class Transferring(
        val bytesTransferred: Int,
        val totalBytes: Int,
        val progressPercent: Float,
        val chunkIndex: Int,
        val totalChunks: Int
    ) : OtaState()
    data class Verifying(val crc32: Long) : OtaState()
    data class Success(val message: String) : OtaState()
    data class Error(val reason: String) : OtaState()
}

/**
 * BleCdiClient:
 * Dedicated GATT client for NS200 CDI R7.2 / R8 (STM32WB55).
 *
 * Requirements:
 * 1. Tanpa PIN / Bonding: Koneksi langsung via BLE GATT tanpa dialog PIN pairing Android.
 * 2. Unpair stale bond: Jika perangkat sebelumnya terlanjur berstatus BOND_BONDING / BOND_BONDED dengan PIN salah,
 *    unpair secara otomatis via removeBond() agar koneksi BLE GATT fresh dan tidak terhalang popup PIN lama.
 * 3. PHY 1M & High Priority: Mengatur connection priority HIGH dan preferred PHY 1M untuk transmisi stabil.
 * 4. Service & CCCD Berurutan: Discover service -> negosiasi MTU 247 -> serial write CCCD (0x2902) untuk Telemetry & Response.
 * 5. ACK Command Queue: Antrean perintah dengan sequence number, hanya 1 perintah aktif (in-flight) per waktu.
 * 6. Retry Terbatas per Command: Maksimal 2x retry per perintah sebelum fail/timeout.
 * 7. Reconnect Eksponensial: Backoff eksponensial (1s, 2s, 4s, 8s, 16s, maks 30s) saat koneksi terputus atau GATT 8.
 * 8. Telemetry v3/v4 20-Byte: Menerima paket 20-byte bergantian CORE / DIAGNOSTIC dengan validasi CRC16.
 * 9. OTA R8 APP.bin Uploader: Karakteristik ...1004 (data 208B chunk) & ...1005 (status) dengan integritas CRC32.
 */
class BleCdiClient(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onState(text: String, connected: Boolean)
        fun onTelemetry(value: Telemetry)
        fun onRawPacket(bytes: ByteArray)
        fun onResponse(value: String)
    }

    private data class Queued(val sequence: Int, val body: String, var retries: Int = 0)

    private val main = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val serviceUuid = UUID.fromString(CdiProtocol.SERVICE)
    private val telemetryUuid = UUID.fromString(CdiProtocol.TELEMETRY)
    private val commandUuid = UUID.fromString(CdiProtocol.COMMAND)
    private val responseUuid = UUID.fromString(CdiProtocol.RESPONSE)
    private val otaDataUuid = UUID.fromString(CdiProtocol.OTA_DATA)
    private val otaStatusUuid = UUID.fromString(CdiProtocol.OTA_STATUS)
    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var gatt: BluetoothGatt? = null
    private var commandChar: BluetoothGattCharacteristic? = null
    private var otaDataChar: BluetoothGattCharacteristic? = null
    private var otaStatusChar: BluetoothGattCharacteristic? = null
    private var lastDevice: BluetoothDevice? = null
    private var manualStop = true
    private var retryCount = 0
    private var sequence = 0
    private var subscriptionsStarted = false
    private var descriptorActive = false

    private val descriptors = ArrayDeque<BluetoothGattDescriptor>()
    private val commands = ArrayDeque<Queued>()
    private var activeCommand: Queued? = null
    private val responseBuffer = StringBuilder()
    private var lastTelemetry = CdiProtocol.emptyTelemetry()

    private var scanTimer: Runnable? = null
    private var phaseTimer: Runnable? = null
    private var reconnectTimer: Runnable? = null
    private var commandTimer: Runnable? = null

    // OTA upload variables
    private val _otaState = MutableStateFlow<OtaState>(OtaState.Idle)
    val otaState: StateFlow<OtaState> = _otaState.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private var otaDataBuffer: ByteArray? = null
    private var otaChunkIndex = 0
    private var otaTotalChunks = 0
    private var otaCancelled = false
    private var otaJob: Runnable? = null

    var gattReady = false
        private set

    var autoReconnect = true

    private val _devices = MutableStateFlow<List<DiscoveredBleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredBleDevice>> = _devices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _pending = MutableStateFlow(0)
    val pendingCommands: StateFlow<Int> = _pending.asStateFlow()

    fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private val scannerCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(type: Int, result: ScanResult) {
            val device = result.device
            val name = result.scanRecord?.deviceName ?: device.name ?: "Unknown BLE"
            val exact = name.equals("NS200-CDI-R7", true)
            val byService = result.scanRecord?.serviceUuids?.any { it.uuid == serviceUuid } == true
            val isCandidate = exact || byService || name.contains("CDI-R7", true) || name.contains("NS200", true)

            val item = DiscoveredBleDevice(
                device = device,
                name = name,
                address = device.address,
                rssi = result.rssi,
                isCdiCandidate = isCandidate
            )

            val list = _devices.value.toMutableList()
            val index = list.indexOfFirst { it.address == item.address }
            if (index >= 0) list[index] = item else list.add(item)
            _devices.value = list.sortedWith(
                compareByDescending<DiscoveredBleDevice> { it.isCdiCandidate }
                    .thenByDescending { it.rssi }
            )

            // Auto-connect if exact target match discovered and not already connected
            if ((exact || byService) && gatt == null && !manualStop) {
                connectDeviceExplicit(device)
            }
        }

        override fun onScanFailed(code: Int) {
            _scanning.value = false
            _busy.value = false
            listener.onState("Scan BLE gagal (kode $code)", false)
        }
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(owner: BluetoothGatt, status: Int, state: Int) {
            if (owner !== gatt) {
                close(owner)
                return
            }

            if (status == BluetoothGatt.GATT_SUCCESS && state == BluetoothProfile.STATE_CONNECTED) {
                cancelReconnect()
                listener.onState("BLE terhubung • inisialisasi PHY 1M & GATT...", false)

                try {
                    owner.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        owner.setPreferredPhy(
                            BluetoothDevice.PHY_LE_1M_MASK,
                            BluetoothDevice.PHY_LE_1M_MASK,
                            BluetoothDevice.PHY_OPTION_NO_PREFERRED
                        )
                    }
                } catch (_: Exception) {}

                // Phase timer in case service discovery hangs
                phaseTimer = Runnable {
                    if (gatt === owner && !gattReady) fail("Timeout service discovery")
                }.also { main.postDelayed(it, 5_000) }

                // Delay 450ms before service discovery for HCI stability
                main.postDelayed({
                    if (gatt === owner && !owner.discoverServices()) {
                        fail("Service discovery gagal dijalankan")
                    }
                }, 450)
            } else {
                val reason = if (status == 8) "GATT 8 / connection timeout" else "status $status"
                closeCurrent()
                if (!manualStop && autoReconnect && lastDevice != null) {
                    reconnect(reason)
                } else {
                    _busy.value = false
                    listener.onState("Disconnected ($reason)", false)
                }
            }
        }

        override fun onServicesDiscovered(owner: BluetoothGatt, status: Int) {
            if (owner !== gatt) return
            cancelPhase()

            if (status != BluetoothGatt.GATT_SUCCESS) {
                return fail("Service discovery gagal (status $status)")
            }

            val service = owner.getService(serviceUuid)
            commandChar = service?.getCharacteristic(commandUuid)
            val teleChar = service?.getCharacteristic(telemetryUuid)
            val respChar = service?.getCharacteristic(responseUuid)
            otaDataChar = service?.getCharacteristic(otaDataUuid)
            otaStatusChar = service?.getCharacteristic(otaStatusUuid)

            if (service == null || teleChar == null || respChar == null || commandChar == null) {
                return fail("Service/karakteristik CDI R7/R8 tidak lengkap")
            }

            listener.onState("GATT siap • negosiasi MTU 247...", false)
            val ok = try {
                owner.requestMtu(247)
            } catch (_: Exception) {
                false
            }

            if (!ok) {
                subscribe(owner)
            } else {
                phaseTimer = Runnable {
                    if (gatt === owner && !subscriptionsStarted) subscribe(owner)
                }.also { main.postDelayed(it, 1_500) }
            }
        }

        override fun onMtuChanged(owner: BluetoothGatt, mtu: Int, status: Int) {
            if (owner !== gatt) return
            cancelPhase()
            subscribe(owner)
        }

        override fun onDescriptorWrite(owner: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            if (owner !== gatt) return
            cancelPhase()
            descriptorActive = false

            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail("Penulisan CCCD gagal (status $status)")
            } else {
                writeNextDescriptor(owner)
            }
        }

        @Deprecated("Android 10-12 callback")
        override fun onCharacteristicChanged(owner: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            consume(c.uuid, c.value ?: ByteArray(0))
        }

        override fun onCharacteristicChanged(
            owner: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            consume(c.uuid, value)
        }
    }

    @SuppressLint("MissingPermission")
    private fun subscribe(owner: BluetoothGatt) {
        if (owner !== gatt || subscriptionsStarted) return
        cancelPhase()

        val service = owner.getService(serviceUuid) ?: return fail("Service R7/R8 hilang saat subscribe")
        descriptors.clear()

        val teleChar = service.getCharacteristic(telemetryUuid)
        val respChar = service.getCharacteristic(responseUuid)

        if (!queueCccd(owner, teleChar) || !queueCccd(owner, respChar)) {
            return fail("CCCD Telemetry / Response tidak tersedia pada GATT")
        }

        // Daftarkan notifikasi status OTA jika ada di GATT
        otaStatusChar?.let { queueCccd(owner, it) }

        subscriptionsStarted = true
        listener.onState("GATT siap • mendaftarkan notifikasi CCCD serial...", false)
        writeNextDescriptor(owner)
    }

    @SuppressLint("MissingPermission")
    private fun queueCccd(owner: BluetoothGatt, c: BluetoothGattCharacteristic?): Boolean {
        if (c == null || !owner.setCharacteristicNotification(c, true)) return false
        val d = c.getDescriptor(cccdUuid) ?: return false
        descriptors.addLast(d)
        return true
    }

    @SuppressLint("MissingPermission")
    private fun writeNextDescriptor(owner: BluetoothGatt) {
        if (owner !== gatt || descriptorActive) return
        val d = descriptors.removeFirstOrNull()

        if (d == null) {
            gattReady = true
            _busy.value = false
            retryCount = 0
            val devName = try { lastDevice?.name } catch (_: SecurityException) { null } ?: "NS200-CDI-R8"
            _connectedDeviceName.value = devName
            send("PING")
            listener.onState("Connected • $devName • PHY 1M", true)
            return
        }

        descriptorActive = true
        val ok = try {
            if (Build.VERSION.SDK_INT >= 33) {
                owner.writeDescriptor(
                    d,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                owner.writeDescriptor(d)
            }
        } catch (_: Exception) {
            false
        }

        if (!ok) {
            descriptorActive = false
            fail("GATT sibuk saat penulisan CCCD")
            return
        }

        phaseTimer = Runnable {
            if (descriptorActive) fail("Timeout penulisan CCCD")
        }.also { main.postDelayed(it, 3_000) }
    }

    private fun consume(uuid: UUID, bytes: ByteArray) = main.post {
        if (uuid == telemetryUuid) {
            listener.onRawPacket(bytes)
            val value = CdiProtocol.telemetry(bytes, lastTelemetry)
            if (value == null) {
                listener.onResponse("ERR,TELEMETRY_V3_CRC_OR_LENGTH_" + bytes.size)
            } else {
                lastTelemetry = value
                listener.onTelemetry(value)
            }
        } else if (uuid == otaStatusUuid) {
            val statusStr = bytes.toString(Charsets.US_ASCII).trim()
            listener.onResponse("OTA_STATUS,$statusStr")
            handleOtaStatus(statusStr)
        } else if (uuid == responseUuid) {
            responseBuffer.append(bytes.toString(Charsets.US_ASCII))
            while (true) {
                while (responseBuffer.isNotEmpty() &&
                    (responseBuffer[0] == '\n' || responseBuffer[0] == '\r')) {
                    responseBuffer.deleteCharAt(0)
                }

                val newline = responseBuffer.indexOf("\n")
                val star = responseBuffer.indexOf("*")
                val completeWithoutNewline = star >= 0 && responseBuffer.length >= star + 5
                val end = when {
                    newline >= 0 -> newline + 1
                    completeWithoutNewline -> star + 5
                    else -> break
                }

                val frame = responseBuffer.substring(0, end)
                responseBuffer.delete(0, end)
                val parsed = CdiProtocol.response(frame)

                if (parsed == null) {
                    listener.onResponse("ERR,RESPONSE_CRC")
                } else {
                    val abortTransaction = parsed.body.startsWith("ERR,")
                    if (activeCommand?.sequence == parsed.sequence) {
                        commandTimer?.let(main::removeCallbacks)
                        commandTimer = null
                        activeCommand = null
                        if (abortTransaction) commands.clear()
                        updatePending()
                    }
                    listener.onResponse(parsed.body)
                    if (parsed.body.startsWith("OTA,")) {
                        handleOtaStatus(parsed.body)
                    }
                    if (!abortTransaction) writeNextCommand()
                }
            }

            if (responseBuffer.length > 512) {
                responseBuffer.clear()
                listener.onResponse("ERR,RESPONSE_BUFFER_OVERFLOW")
            }
        }
    }

    fun connect() = startScan()

    @SuppressLint("MissingPermission")
    fun startScan() {
        manualStop = false
        stopScanInternal()
        closeCurrent()
        cancelReconnect()
        commands.clear()
        activeCommand = null
        updatePending()
        retryCount = 0
        lastDevice = null

        val scanner = adapter?.bluetoothLeScanner
        if (adapter?.isEnabled != true || scanner == null) {
            _busy.value = false
            listener.onState("Bluetooth tidak aktif / tidak tersedia", false)
            return
        }

        _devices.value = emptyList()
        _scanning.value = true
        _busy.value = true
        listener.onState("Memindai NS200-CDI-R7...", false)

        try {
            scanner.startScan(scannerCallback)
            scanTimer = Runnable {
                stopScanInternal()
                if (gatt == null) {
                    _busy.value = false
                    listener.onState("CDI tidak ditemukan • coba SCAN ulang", false)
                }
            }.also { main.postDelayed(it, 15_000) }
        } catch (e: Exception) {
            _scanning.value = false
            _busy.value = false
            listener.onState("Scan gagal: " + e.message, false)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanInternal() {
        scanTimer?.let(main::removeCallbacks)
        scanTimer = null
        _scanning.value = false
        try {
            adapter?.bluetoothLeScanner?.stopScan(scannerCallback)
        } catch (_: Exception) {}
    }

    fun connectDeviceExplicit(device: BluetoothDevice) {
        stopScanInternal()
        manualStop = false
        lastDevice = device
        retryCount = 0
        open(device)
    }

    /**
     * Unpair device if bonded or bonding from previous PIN failure so Android OS doesn't prompt for PIN.
     */
    @SuppressLint("MissingPermission")
    private fun removeBondIfPresent(device: BluetoothDevice) {
        try {
            if (device.bondState != BluetoothDevice.BOND_NONE) {
                device.javaClass.getMethod("removeBond").invoke(device)
            }
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun open(device: BluetoothDevice) {
        cancelReconnect()
        closeCurrent()
        _busy.value = true

        // Ensure stale bonds with wrong PIN are cleared so GATT connects cleanly without PIN/bonding
        removeBondIfPresent(device)

        val displayName = try { device.name } catch (_: Exception) { null } ?: device.address
        listener.onState("Menghubungkan $displayName • tanpa PIN/bonding...", false)

        try {
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            phaseTimer = Runnable {
                if (!gattReady && gatt != null) fail("Timeout connect")
            }.also { main.postDelayed(it, 10_000) }
        } catch (e: Exception) {
            reconnect("connectGatt: " + e.message)
        }
    }

    private fun fail(reason: String) {
        closeCurrent()
        if (!manualStop && autoReconnect && lastDevice != null) {
            reconnect(reason)
        } else {
            _busy.value = false
            listener.onState("Disconnected ($reason)", false)
        }
    }

    private fun reconnect(reason: String) {
        val device = lastDevice ?: return
        if (manualStop || !autoReconnect) return
        cancelReconnect()
        retryCount++

        // Exponential backoff: 1s, 2s, 4s, 8s, 16s, max 30s
        val delayMs = min(30_000L, 1_000L * (1L shl min(retryCount - 1, 5)))
        _busy.value = true
        listener.onState("$reason • reconnect ${delayMs / 1000}s (percobaan $retryCount)", false)

        reconnectTimer = Runnable {
            reconnectTimer = null
            open(device)
        }.also { main.postDelayed(it, delayMs) }
    }

    private fun cancelReconnect() {
        reconnectTimer?.let(main::removeCallbacks)
        reconnectTimer = null
    }

    private fun cancelPhase() {
        phaseTimer?.let(main::removeCallbacks)
        phaseTimer = null
    }

    @SuppressLint("MissingPermission")
    private fun close(owner: BluetoothGatt) {
        try { owner.disconnect() } catch (_: Exception) {}
        try { owner.close() } catch (_: Exception) {}
    }

    private fun closeCurrent() {
        cancelPhase()
        commandTimer?.let(main::removeCallbacks)
        commandTimer = null
        otaJob?.let(main::removeCallbacks)
        otaJob = null
        otaCancelled = true
        otaDataBuffer = null
        val owner = gatt
        gatt = null
        if (owner != null) close(owner)
        commandChar = null
        otaDataChar = null
        otaStatusChar = null
        descriptors.clear()
        descriptorActive = false
        subscriptionsStarted = false
        gattReady = false
        responseBuffer.clear()
        activeCommand = null
        commands.clear()
        updatePending()
        _otaState.value = OtaState.Idle
        _connectedDeviceName.value = null
    }

    fun disconnect() {
        manualStop = true
        stopScanInternal()
        cancelReconnect()
        closeCurrent()
        commands.clear()
        updatePending()
        _busy.value = false
        listener.onState("Disconnected (Manual)", false)
    }

    fun release() = disconnect()

    /**
     * Memulai pengunggahan firmware APP.bin melalui BLE OTA R8.
     * Karakteristik Data: ...1004 (chunk sequential maks 208 byte)
     * Karakteristik Status: ...1005 (notifikasi / status flash)
     */
    fun startOta(data: ByteArray): Boolean {
        if (data.isEmpty()) {
            _otaState.value = OtaState.Error("File APP.bin kosong!")
            return false
        }
        if (!gattReady) {
            _otaState.value = OtaState.Error("BLE belum terhubung!")
            return false
        }
        otaCancelled = false
        otaDataBuffer = data
        val crc32Val = CdiProtocol.crc32(data)
        otaTotalChunks = (data.size + CdiProtocol.OTA_CHUNK_MAX_SIZE - 1) / CdiProtocol.OTA_CHUNK_MAX_SIZE
        otaChunkIndex = 0

        _otaState.value = OtaState.Preparing(
            "Inisialisasi OTA R8 (%d B, %d chunk, CRC32: %08X)...".format(data.size, otaTotalChunks, crc32Val)
        )
        send("OTA,START,%d,%08X".format(data.size, crc32Val))

        main.postDelayed({
            if (!otaCancelled && gattReady) {
                sendNextOtaChunk()
            }
        }, 500)
        return true
    }

    fun cancelOta() {
        otaCancelled = true
        otaJob?.let(main::removeCallbacks)
        otaJob = null
        otaDataBuffer = null
        _otaState.value = OtaState.Idle
        send("OTA,CANCEL")
    }

    @SuppressLint("MissingPermission")
    private fun sendNextOtaChunk() {
        if (otaCancelled || !gattReady) return
        val buf = otaDataBuffer ?: return
        if (otaChunkIndex >= otaTotalChunks) {
            val crc = CdiProtocol.crc32(buf)
            _otaState.value = OtaState.Verifying(crc)
            send("OTA,VERIFY,%08X".format(crc))
            return
        }

        val start = otaChunkIndex * CdiProtocol.OTA_CHUNK_MAX_SIZE
        val end = minOf(start + CdiProtocol.OTA_CHUNK_MAX_SIZE, buf.size)
        val chunk = buf.copyOfRange(start, end)
        val owner = gatt
        val char = otaDataChar

        if (owner != null && char != null) {
            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    owner.writeCharacteristic(
                        char,
                        chunk,
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    )
                } else {
                    @Suppress("DEPRECATION")
                    char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    @Suppress("DEPRECATION")
                    char.value = chunk
                    @Suppress("DEPRECATION")
                    owner.writeCharacteristic(char)
                }
            } catch (_: Exception) {}
        }

        otaChunkIndex++
        val progress = otaChunkIndex.toFloat() / otaTotalChunks
        _otaState.value = OtaState.Transferring(
            bytesTransferred = end,
            totalBytes = buf.size,
            progressPercent = progress,
            chunkIndex = otaChunkIndex,
            totalChunks = otaTotalChunks
        )

        // Pacing ~20ms per 208-byte chunk untuk stabilitas BLE 1M PHY
        otaJob = Runnable {
            sendNextOtaChunk()
        }.also { main.postDelayed(it, 20L) }
    }

    private fun handleOtaStatus(status: String) {
        when {
            status.contains("FLASH_OK", ignoreCase = true) ||
            status.contains("SUCCESS", ignoreCase = true) ||
            status.contains("CRC_OK", ignoreCase = true) ||
            status.contains("DONE", ignoreCase = true) -> {
                _otaState.value = OtaState.Success("Firmware R8 APP.bin berhasil diverifikasi & di-flash ke STM32!")
            }
            status.contains("ERR", ignoreCase = true) || status.contains("FAIL", ignoreCase = true) -> {
                _otaState.value = OtaState.Error("Gagal OTA: $status")
            }
        }
    }

    fun send(body: String): Boolean {
        if (!gattReady || body.isBlank()) return false
        sequence = (sequence + 1) and 0xffff
        commands.addLast(Queued(sequence, body.trim()))
        updatePending()
        writeNextCommand()
        return true
    }

    @SuppressLint("MissingPermission")
    private fun writeNextCommand() {
        if (activeCommand != null || !gattReady) return
        val owner = gatt ?: return
        val c = commandChar ?: return
        val item = commands.removeFirstOrNull() ?: return updatePending()

        activeCommand = item
        updatePending()

        val bytes = CdiProtocol.command(item.sequence, item.body)
        val ok = try {
            if (Build.VERSION.SDK_INT >= 33) {
                owner.writeCharacteristic(
                    c,
                    bytes,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                c.value = bytes
                @Suppress("DEPRECATION")
                owner.writeCharacteristic(c)
            }
        } catch (_: Exception) {
            false
        }

        if (!ok) {
            activeCommand = null
            commands.addFirst(item)
            updatePending()
            fail("GATT command write gagal")
            return
        }

        commandTimer = Runnable {
            val current = activeCommand ?: return@Runnable
            activeCommand = null
            if (current.retries < 2) {
                current.retries++
                commands.addFirst(current)
                listener.onResponse("WARN,COMMAND_RETRY," + current.body + "," + current.retries)
                updatePending()
                writeNextCommand()
            } else {
                listener.onResponse("ERR,COMMAND_TIMEOUT," + current.body)
                updatePending()
                fail("Command response timeout")
            }
        }.also { main.postDelayed(it, 3_000) }
    }

    private fun updatePending() {
        _pending.value = commands.size + if (activeCommand != null) 1 else 0
    }
}
