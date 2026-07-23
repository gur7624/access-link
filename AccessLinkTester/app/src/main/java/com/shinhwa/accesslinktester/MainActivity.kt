package com.shinhwa.accesslinktester

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.shinhwa.accesslinktester.data.ServiceStore
import com.shinhwa.accesslinktester.model.ACCESS_LINK_SERIAL_PRODUCT_ID
import com.shinhwa.accesslinktester.model.ACCESS_LINK_SERIAL_VENDOR_ID
import com.shinhwa.accesslinktester.model.EthernetUiState
import com.shinhwa.accesslinktester.model.UsbDeviceSnapshot
import com.shinhwa.accesslinktester.model.UsbEndpointSnapshot
import com.shinhwa.accesslinktester.model.UsbInterfaceSnapshot
import com.shinhwa.accesslinktester.ui.AppRoot
import com.shinhwa.accesslinktester.ui.theme.AccessLinkTesterTheme

private const val ACTION_USB_PERMISSION = "com.shinhwa.accesslinktester.USB_PERMISSION"
private const val DEFAULT_SERIAL_BAUD_RATE = 9600
private const val AUTO_CONNECT_SERIAL_ON_USB_ATTACH = false

/**
 * Android 배선 전용 — USB 권한/연결, 이더넷 상태, 시리얼 연결 수명주기만 담당하고
 * 앱 상태·서비스 로직은 [AccessLinkAppController], 화면은 [AppRoot] 로 위임한다.
 */
class MainActivity : ComponentActivity() {
    private lateinit var usbManager: UsbManager
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var connectionManager: AccessLinkConnectionManager
    private lateinit var controller: AccessLinkAppController

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    val device = intent.usbDevice()
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    controller.logSystem(
                        if (granted) "USB 권한 승인: ${device?.deviceName ?: "알 수 없는 장치"}"
                        else "USB 권한 거부: ${device?.deviceName ?: "알 수 없는 장치"}"
                    )
                    refreshUsbDevices()
                }

                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    controller.logSystem("USB 장치 연결 감지")
                    refreshUsbDevices()
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    controller.logSystem("USB 장치 분리 감지")
                    connectionManager.handleUsbDeviceDetached(intent.usbDevice()?.deviceName)
                    refreshUsbDevices()
                }
            }
        }
    }

    private val ethernetCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = runOnUiThread { refreshEthernetState() }
        override fun onLost(network: Network) = runOnUiThread { refreshEthernetState() }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            runOnUiThread { refreshEthernetState() }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) =
            runOnUiThread { refreshEthernetState() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usbManager = getSystemService(UsbManager::class.java)
        connectivityManager = getSystemService(ConnectivityManager::class.java)

        connectionManager = AccessLinkConnectionManager(
            usbManager = usbManager,
            onStatusChanged = { status -> runOnUiThread { controller.onConnectionStatus(status) } },
            onEvent = { event -> runOnUiThread { controller.handleConnectionEvent(event) } },
            onLog = { message -> runOnUiThread { controller.logSystem(message) } }
        )
        controller = AccessLinkAppController(
            store = ServiceStore(this),
            send = { packet -> connectionManager.send(packet) }
        )

        registerUsbReceiver()
        registerEthernetCallback()
        refreshUsbDevices()
        refreshEthernetState()
        enableEdgeToEdge()

        setContent {
            AccessLinkTesterTheme {
                AppRoot(
                    controller = controller,
                    onConnectSerial = ::connectSerial,
                    onDisconnectSerial = ::disconnectSerial,
                    onRequestPermission = ::requestUsbPermission,
                    onRefreshDevices = {
                        refreshUsbDevices()
                        refreshEthernetState()
                    }
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // 백그라운드 진입 시 관리자 세션 종료 → 재진입 시 PIN 재요구
        controller.leaveAdmin()
    }

    override fun onDestroy() {
        disconnectSerial()
        unregisterEthernetCallback()
        runCatching { unregisterReceiver(usbReceiver) }
        super.onDestroy()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(usbReceiver, filter)
        }
    }

    private fun refreshUsbDevices() {
        val snapshots = usbManager.deviceList.values
            .sortedWith(compareBy<UsbDevice> { it.vendorId }.thenBy { it.productId }.thenBy { it.deviceName })
            .map { device -> device.toSnapshot(usbManager.hasPermission(device)) }

        controller.setUsbDevices(snapshots)

        val serialDevice = snapshots.firstOrNull { it.isAccessLinkSerial }
        when {
            AUTO_CONNECT_SERIAL_ON_USB_ATTACH &&
                serialDevice?.hasPermission == true &&
                !connectionManager.status.connected ->
                connectSerial(DEFAULT_SERIAL_BAUD_RATE)

            serialDevice == null && connectionManager.status.connected ->
                connectionManager.handleUsbDeviceDetached(null)
        }
    }

    private fun registerEthernetCallback() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, ethernetCallback)
    }

    private fun unregisterEthernetCallback() {
        runCatching { connectivityManager.unregisterNetworkCallback(ethernetCallback) }
    }

    @Suppress("DEPRECATION")
    private fun refreshEthernetState() {
        val state = connectivityManager.allNetworks.firstNotNullOfOrNull { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) != true) return@firstNotNullOfOrNull null
            val linkProperties = connectivityManager.getLinkProperties(network)
            val interfaceName = linkProperties?.interfaceName
            val addresses = linkProperties?.linkAddresses
                ?.mapNotNull { it.address.hostAddress }
                ?.filter { it.isNotBlank() }
                .orEmpty()
            EthernetUiState(
                connected = true,
                interfaceName = interfaceName,
                detail = listOfNotNull(
                    interfaceName,
                    addresses.takeIf { it.isNotEmpty() }?.joinToString(", ")
                ).joinToString(" / ").ifBlank { "링크 감지" }
            )
        } ?: EthernetUiState(connected = false, interfaceName = null, detail = "랜선 링크 미감지")

        controller.setEthernet(state)
    }

    private fun requestUsbPermission(device: UsbDeviceSnapshot) {
        val usbDevice = usbManager.deviceList.values.firstOrNull { it.deviceName == device.deviceName }
        if (usbDevice == null) {
            controller.logSystem("권한 요청 실패: 장치를 다시 찾을 수 없음")
            refreshUsbDevices()
            return
        }
        if (usbManager.hasPermission(usbDevice)) {
            refreshUsbDevices()
            return
        }
        val permissionIntent = PendingIntent.getBroadcast(
            this,
            usbDevice.deviceName.hashCode(),
            Intent(ACTION_USB_PERMISSION).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        usbManager.requestPermission(usbDevice, permissionIntent)
        controller.logSystem("USB 권한 요청: ${usbDevice.deviceName}")
    }

    private fun connectSerial(baudRate: Int) {
        if (connectionManager.status.connected && connectionManager.status.baudRate == baudRate) return

        val device = usbManager.deviceList.values.firstOrNull { it.isAccessLinkSerialDevice() }
        if (device == null) {
            controller.logSystem("제어 장치 없음")
            return
        }
        if (!usbManager.hasPermission(device)) {
            requestUsbPermission(device.toSnapshot(false))
            return
        }
        connectionManager.connect(device, baudRate)
    }

    private fun disconnectSerial() {
        connectionManager.disconnect()
    }

    private fun Intent.usbDevice(): UsbDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
    }
}

// ---------------------------------------------------------------------------
// UsbDevice → 스냅샷 매핑 (Android 타입 → model)
// ---------------------------------------------------------------------------

private fun UsbDevice.isAccessLinkSerialDevice(): Boolean =
    vendorId == ACCESS_LINK_SERIAL_VENDOR_ID && productId == ACCESS_LINK_SERIAL_PRODUCT_ID

private fun UsbDevice.toSnapshot(hasPermission: Boolean): UsbDeviceSnapshot {
    val interfaceSnapshots = (0 until interfaceCount).map { index -> getInterface(index).toSnapshot() }
    return UsbDeviceSnapshot(
        deviceName = deviceName,
        vendorId = vendorId,
        productId = productId,
        deviceClass = deviceClass,
        deviceSubclass = deviceSubclass,
        deviceProtocol = deviceProtocol,
        manufacturerName = safeValue { manufacturerName },
        productName = safeValue { productName },
        hasPermission = hasPermission,
        interfaces = interfaceSnapshots
    )
}

private fun UsbInterface.toSnapshot(): UsbInterfaceSnapshot {
    val endpoints = (0 until endpointCount).map { index ->
        val endpoint = getEndpoint(index)
        UsbEndpointSnapshot(
            address = endpoint.address,
            attributes = endpoint.attributes,
            maxPacketSize = endpoint.maxPacketSize,
            direction = endpoint.direction,
            type = endpoint.type
        )
    }
    return UsbInterfaceSnapshot(
        id = id,
        interfaceClass = interfaceClass,
        interfaceSubclass = interfaceSubclass,
        interfaceProtocol = interfaceProtocol,
        endpoints = endpoints
    )
}

private inline fun safeValue(block: () -> String?): String {
    return try {
        block().orEmpty().ifBlank { "미확인" }
    } catch (_: SecurityException) {
        "권한 필요"
    }
}
