package com.shinhwa.accesslinktester

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class AccessLinkSerialController(
    private val usbManager: UsbManager,
    private val device: UsbDevice,
    private val baudRate: Int,
    private val onLog: (String) -> Unit,
    private val onReceive: (ByteArray) -> Unit,
    private val onDisconnected: (String?) -> Unit = {}
) {
    private var port: UsbSerialPort? = null
    private var readerThread: Thread? = null
    private val running = AtomicBoolean(false)
    private val writeLock = Any()

    fun open() {
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            ?: error("지원되는 USB Serial 드라이버를 찾지 못했습니다.")
        val serialPort = driver.ports.firstOrNull()
            ?: error("사용 가능한 Serial 포트가 없습니다.")
        val connection = usbManager.openDevice(driver.device)
            ?: error("USB 장치를 열 수 없습니다. 권한을 먼저 승인하세요.")

        serialPort.open(connection)
        serialPort.setParameters(
            baudRate,
            8,
            UsbSerialPort.STOPBITS_1,
            UsbSerialPort.PARITY_NONE
        )
        port = serialPort
        running.set(true)
        startReader(serialPort)
        onLog("CH340 Serial 연결됨: ${baudRate}bps, 8N1")
    }

    fun write(data: ByteArray) {
        synchronized(writeLock) {
            val serialPort = port ?: error("Serial 포트가 열려 있지 않습니다.")
            serialPort.write(data, WRITE_TIMEOUT_MS)
        }
    }

    fun close() {
        running.set(false)
        readerThread?.interrupt()
        readerThread = null
        try {
            port?.close()
        } catch (_: IOException) {
        } finally {
            port = null
        }
    }

    private fun startReader(serialPort: UsbSerialPort) {
        readerThread = Thread {
            val buffer = ByteArray(256)
            while (running.get()) {
                try {
                    val count = serialPort.read(buffer, READ_TIMEOUT_MS)
                    if (count > 0) {
                        onReceive(buffer.copyOf(count))
                    }
                } catch (exception: IOException) {
                    if (running.get()) {
                        onLog("Serial 수신 오류: ${exception.message ?: "알 수 없는 오류"}")
                        onDisconnected(exception.message)
                    }
                    running.set(false)
                }
            }
        }.apply {
            name = "AccessLinkSerialReader"
            start()
        }
    }

    companion object {
        private const val READ_TIMEOUT_MS = 200
        private const val WRITE_TIMEOUT_MS = 1000
    }
}
