package com.shinhwa.accesslinktester

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import java.util.concurrent.atomic.AtomicReference

class AccessLinkConnectionManager(
    private val usbManager: UsbManager,
    private val onStatusChanged: (AccessLinkConnectionStatus) -> Unit,
    private val onEvent: (AccessLinkConnectionEvent) -> Unit,
    private val onLog: (String) -> Unit
) {
    private val decoder = ShalPacketDecoder()
    private val statusRef = AtomicReference(AccessLinkConnectionStatus())
    private var controller: AccessLinkSerialController? = null
    private var connectedDeviceName: String? = null

    val status: AccessLinkConnectionStatus
        get() = statusRef.get()

    @Synchronized
    fun connect(device: UsbDevice, baudRate: Int) {
        val current = status
        if (
            current.state == AccessLinkConnectionState.CONNECTED &&
            current.baudRate == baudRate &&
            connectedDeviceName == device.deviceName
        ) {
            return
        }

        if (!usbManager.hasPermission(device)) {
            updateStatus(
                AccessLinkConnectionStatus(
                    state = AccessLinkConnectionState.DISCONNECTED,
                    baudRate = baudRate,
                    message = "권한 필요"
                )
            )
            return
        }

        disconnectInternal(logMessage = null)
        decoder.reset()
        connectedDeviceName = device.deviceName
        updateStatus(
            AccessLinkConnectionStatus(
                state = AccessLinkConnectionState.CONNECTING,
                baudRate = baudRate,
                message = "연결 중"
            )
        )

        try {
            val nextController = AccessLinkSerialController(
                usbManager = usbManager,
                device = device,
                baudRate = baudRate,
                onLog = onLog,
                onReceive = ::handleReceive,
                onDisconnected = ::handleUnexpectedDisconnect
            )
            nextController.open()
            controller = nextController
            updateStatus(
                AccessLinkConnectionStatus(
                    state = AccessLinkConnectionState.CONNECTED,
                    baudRate = baudRate,
                    message = "연결됨"
                )
            )
        } catch (exception: Exception) {
            controller = null
            connectedDeviceName = null
            updateStatus(
                AccessLinkConnectionStatus(
                    state = AccessLinkConnectionState.ERROR,
                    baudRate = baudRate,
                    message = "연결 실패: ${exception.message ?: "알 수 없는 오류"}",
                    errorMessage = exception.message
                )
            )
        }
    }

    @Synchronized
    fun disconnect() {
        disconnectInternal(logMessage = "연결 해제")
    }

    @Synchronized
    fun handleUsbDeviceDetached(deviceName: String?) {
        if (deviceName == null || deviceName == connectedDeviceName) {
            disconnectInternal(logMessage = "USB 분리됨")
        }
    }

    @Synchronized
    fun send(packet: ByteArray) {
        val serialController = controller ?: error("연결 필요")
        try {
            serialController.write(packet)
            onEvent(AccessLinkConnectionEvent.PacketSent(packet))
        } catch (exception: Exception) {
            updateStatus(
                status.copy(
                    state = AccessLinkConnectionState.ERROR,
                    message = "송신 실패: ${exception.message ?: "알 수 없는 오류"}",
                    errorMessage = exception.message
                )
            )
            throw exception
        }
    }

    private fun handleReceive(data: ByteArray) {
        onEvent(AccessLinkConnectionEvent.RawDataReceived(data))

        decoder.accept(data).forEach { result ->
            when (result) {
                is ProtocolDecodeResult.Packet -> {
                    onEvent(
                        AccessLinkConnectionEvent.PacketReceived(
                            packet = result.packet,
                            deviceEvent = AccessLinkCommandRouter.route(result.packet)
                        )
                    )
                }

                is ProtocolDecodeResult.Error -> {
                    if (result.error !is ProtocolError.GarbageBeforeStx) {
                        onEvent(AccessLinkConnectionEvent.ProtocolErrorReceived(result.error))
                    }
                }
            }
        }
    }

    private fun handleUnexpectedDisconnect(message: String?) {
        synchronized(this) {
            controller = null
            connectedDeviceName = null
            decoder.reset()
            updateStatus(
                AccessLinkConnectionStatus(
                    state = AccessLinkConnectionState.ERROR,
                    baudRate = status.baudRate,
                    message = "연결 끊김: ${message ?: "수신 오류"}",
                    errorMessage = message
                )
            )
        }
    }

    private fun disconnectInternal(logMessage: String?) {
        controller?.close()
        controller = null
        connectedDeviceName = null
        decoder.reset()
        if (logMessage != null && status.state == AccessLinkConnectionState.CONNECTED) {
            onLog(logMessage)
        }
        updateStatus(
            status.copy(
                state = AccessLinkConnectionState.DISCONNECTED,
                message = "연결 안 됨",
                errorMessage = null
            )
        )
    }

    private fun updateStatus(status: AccessLinkConnectionStatus) {
        statusRef.set(status)
        onStatusChanged(status)
    }
}

enum class AccessLinkConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class AccessLinkConnectionStatus(
    val state: AccessLinkConnectionState = AccessLinkConnectionState.DISCONNECTED,
    val baudRate: Int = 9600,
    val message: String = "연결 안 됨",
    val errorMessage: String? = null
) {
    val connected: Boolean
        get() = state == AccessLinkConnectionState.CONNECTED
}

sealed interface AccessLinkConnectionEvent {
    data class RawDataReceived(val data: ByteArray) : AccessLinkConnectionEvent
    data class PacketReceived(
        val packet: ShalPacket,
        val deviceEvent: DeviceEvent
    ) : AccessLinkConnectionEvent

    data class PacketSent(val packet: ByteArray) : AccessLinkConnectionEvent
    data class ProtocolErrorReceived(val error: ProtocolError) : AccessLinkConnectionEvent
}
