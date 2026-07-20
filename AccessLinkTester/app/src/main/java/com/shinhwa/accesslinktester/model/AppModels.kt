package com.shinhwa.accesslinktester.model

import android.hardware.usb.UsbConstants
import com.shinhwa.accesslinktester.WiegandInput
import java.util.Locale

// ---------------------------------------------------------------------------
// ACCESS LINK 장비 식별자 (USB VID/PID)
// ---------------------------------------------------------------------------

const val ACCESS_LINK_SERIAL_VENDOR_ID = 0x1A86   // CH340 USB Serial
const val ACCESS_LINK_SERIAL_PRODUCT_ID = 0x7523
const val ACCESS_LINK_LAN_VENDOR_ID = 0x0BDA      // USB LAN
const val ACCESS_LINK_LAN_PRODUCT_ID = 0x8152

// ---------------------------------------------------------------------------
// 서비스 데이터 모델 (DEV_SPEC v2 · 4장)
// ---------------------------------------------------------------------------

/** 문(Relay) 하나의 설정. index 0 = Relay0, 1 = Relay1. */
data class DoorConfig(
    val index: Int,
    val name: String = "",        // 관리자 지정. 비어 있으면 사용자 화면에 노출 안 함
    val openSeconds: Int = 3,     // 0~99, 0=지속
    val enabled: Boolean = false  // 사용 여부
)

/** 사용자 홈에 OPEN 버튼으로 노출할지 판정. 이름 없거나 사용 여부 off면 숨김. */
fun DoorConfig.visibleToUser(): Boolean = enabled && name.isNotBlank()

/** 프로토콜 SET_RELAYCONTROL 의 UseRelay 매핑: Relay0=1, Relay1=2. */
fun DoorConfig.useRelayValue(): Int = index + 1

/** 등록 사용자(카드). */
data class RegisteredCard(
    val key: String,   // Wiegand 디코드 코드(decimalCode ?: raw32Decimal) 문자열, 없으면 dataHex
    val name: String,  // 카드 소유자 이름 (관리자 입력)
    val addedAt: Long
)

/** 등록 얼굴. 얼굴 특징값은 전용 얼굴 비교 모델 연결 시 추가한다. */
data class RegisteredFace(
    val id: String,
    val name: String,
    val addedAt: Long,
    val embedding: List<Float> = emptyList()
)

/** 카드 등록 대기 모드에서 감지된 카드 원본. */
data class CapturedCard(
    val key: String,
    val cardNumber: String
)

/** 출입 이벤트 = "누가·언제·카드번호"의 핵심 기록. */
enum class AccessEventType { OPEN, GRANTED, DENIED, TAG, FACE, INPUT, SYSTEM, ERROR }

data class AccessEvent(
    val time: String,          // HH:mm:ss (필요시 날짜 포함)
    val type: AccessEventType,
    val personName: String?,   // 등록자 이름, 미등록이면 null
    val cardNumber: String?,   // 카드번호(표시용)
    val message: String,
    val detail: String? = null
)

data class ServiceSettings(
    val autoOpenEnabled: Boolean = false,
    val autoOpenDoorIndexes: Set<Int> = emptySet()  // 자동개방 대상 문
)

// ---------------------------------------------------------------------------
// 카드 인증 판정 (순수 로직 · 유닛테스트 대상)
//  - Compose/Android 의존 없이 등록/미등록 × 자동개방 on/off 를 결정한다.
// ---------------------------------------------------------------------------

sealed interface CardOutcome {
    /** 자동개방 off: 태그만 기록. 등록자면 이름, 미등록이면 null. */
    data class Tag(val personName: String?, val cardNumber: String) : CardOutcome

    /** 자동개방 on + 등록 카드: 대상 문을 연다. */
    data class Granted(
        val personName: String,
        val cardNumber: String,
        val doorIndexes: Set<Int>
    ) : CardOutcome

    /** 등록 카드지만 자동 개방 대상 문이 지정되지 않은 상태. */
    data class NoTarget(
        val personName: String,
        val cardNumber: String
    ) : CardOutcome

    /** 자동개방 on + 미등록 카드: 거부. */
    data class Denied(val cardNumber: String) : CardOutcome
}

/**
 * 카드 인증 판정. [cards] 에서 [key] 로 등록 여부를 조회하고 [settings] 에 따라 결과를 낸다.
 * 개방 대상 문은 사용 가능(visibleToUser)한 문으로 한정한다.
 */
fun evaluateCard(
    key: String,
    cardNumber: String,
    cards: List<RegisteredCard>,
    settings: ServiceSettings,
    doors: List<DoorConfig>
): CardOutcome {
    val registered = cards.firstOrNull { it.key == key }
    if (!settings.autoOpenEnabled) {
        return CardOutcome.Tag(personName = registered?.name, cardNumber = cardNumber)
    }
    return if (registered != null) {
        val targets = doors
            .filter { it.index in settings.autoOpenDoorIndexes && it.visibleToUser() }
            .map { it.index }
            .toSet()
        if (targets.isEmpty()) {
            CardOutcome.NoTarget(registered.name, cardNumber)
        } else {
            CardOutcome.Granted(registered.name, cardNumber, targets)
        }
    } else {
        CardOutcome.Denied(cardNumber)
    }
}

/** 카드 고유 키: decimalCode ?: raw32Decimal, 둘 다 없으면 dataHex. */
fun cardKeyOf(input: WiegandInput): String {
    return (input.decimalCode ?: input.raw32Decimal)?.toString() ?: input.dataHex
}

/** 표시용 카드번호 문자열. */
fun cardNumberOf(input: WiegandInput): String {
    return (input.decimalCode ?: input.raw32Decimal)?.toString() ?: input.dataHex
}

// ---------------------------------------------------------------------------
// 통신/연결 UI 상태
// ---------------------------------------------------------------------------

data class SerialUiState(
    val connected: Boolean = false,
    val baudRate: Int = 9600,
    val status: String = "연결 안 됨"
)

data class EthernetUiState(
    val connected: Boolean = false,
    val interfaceName: String? = null,
    val detail: String = "랜선 링크 미감지"
)

// ---------------------------------------------------------------------------
// USB 스냅샷 (장비 진단용)
// ---------------------------------------------------------------------------

data class UsbDeviceSnapshot(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val deviceClass: Int,
    val deviceSubclass: Int,
    val deviceProtocol: Int,
    val manufacturerName: String,
    val productName: String,
    val hasPermission: Boolean,
    val interfaces: List<UsbInterfaceSnapshot>
) {
    val displayName: String
        get() = when {
            isAccessLinkLan -> "ACCESS LINK USB LAN"
            isAccessLinkSerial -> "ACCESS LINK USB Serial"
            productName != "미확인" && productName != "권한 필요" -> productName
            manufacturerName != "미확인" && manufacturerName != "권한 필요" -> manufacturerName
            else -> "USB 장치"
        }

    val vendorIdHex: String get() = vendorId.toUsbHex()
    val productIdHex: String get() = productId.toUsbHex()
    val deviceClassName: String get() = usbClassName(deviceClass)
    val isAccessLinkSerial: Boolean
        get() = vendorId == ACCESS_LINK_SERIAL_VENDOR_ID && productId == ACCESS_LINK_SERIAL_PRODUCT_ID
    val isAccessLinkLan: Boolean
        get() = vendorId == ACCESS_LINK_LAN_VENDOR_ID && productId == ACCESS_LINK_LAN_PRODUCT_ID

    val typeGuess: String
        get() {
            val classes = interfaces.map { it.interfaceClass }.toSet() + deviceClass
            return when {
                isAccessLinkSerial -> "CH340 USB Serial"
                isAccessLinkLan -> "USB Ethernet/LAN"
                UsbConstants.USB_CLASS_COMM in classes || UsbConstants.USB_CLASS_CDC_DATA in classes -> "Serial/CDC 추정"
                UsbConstants.USB_CLASS_HID in classes -> "HID 추정"
                UsbConstants.USB_CLASS_MISC in classes || interfaces.size > 1 -> "Composite 추정"
                UsbConstants.USB_CLASS_VENDOR_SPEC in classes -> "Vendor 전용"
                else -> "타입 확인 필요"
            }
        }
}

data class UsbInterfaceSnapshot(
    val id: Int,
    val interfaceClass: Int,
    val interfaceSubclass: Int,
    val interfaceProtocol: Int,
    val endpoints: List<UsbEndpointSnapshot>
) {
    val className: String get() = usbClassName(interfaceClass)
}

data class UsbEndpointSnapshot(
    val address: Int,
    val attributes: Int,
    val maxPacketSize: Int,
    val direction: Int,
    val type: Int
) {
    val addressHex: String get() = "0x${address.toString(16).uppercase(Locale.US).padStart(2, '0')}"
    val directionName: String get() = if (direction == UsbConstants.USB_DIR_IN) "IN" else "OUT"
    val typeName: String
        get() = when (type) {
            UsbConstants.USB_ENDPOINT_XFER_CONTROL -> "Control"
            UsbConstants.USB_ENDPOINT_XFER_ISOC -> "Isochronous"
            UsbConstants.USB_ENDPOINT_XFER_BULK -> "Bulk"
            UsbConstants.USB_ENDPOINT_XFER_INT -> "Interrupt"
            else -> "Unknown"
        }
}

fun Int.toUsbHex(): String {
    return "0x${toString(16).uppercase(Locale.US).padStart(4, '0')}"
}

fun usbClassName(value: Int): String {
    return when (value) {
        UsbConstants.USB_CLASS_APP_SPEC -> "Application Specific"
        UsbConstants.USB_CLASS_AUDIO -> "Audio"
        UsbConstants.USB_CLASS_CDC_DATA -> "CDC Data"
        UsbConstants.USB_CLASS_COMM -> "Communication"
        UsbConstants.USB_CLASS_CONTENT_SEC -> "Content Security"
        UsbConstants.USB_CLASS_CSCID -> "Smart Card"
        UsbConstants.USB_CLASS_HID -> "HID"
        UsbConstants.USB_CLASS_HUB -> "Hub"
        UsbConstants.USB_CLASS_MASS_STORAGE -> "Mass Storage"
        UsbConstants.USB_CLASS_MISC -> "Miscellaneous"
        UsbConstants.USB_CLASS_PER_INTERFACE -> "Per Interface"
        UsbConstants.USB_CLASS_PHYSICA -> "Physical"
        UsbConstants.USB_CLASS_PRINTER -> "Printer"
        UsbConstants.USB_CLASS_STILL_IMAGE -> "Still Image"
        UsbConstants.USB_CLASS_VENDOR_SPEC -> "Vendor Specific"
        UsbConstants.USB_CLASS_VIDEO -> "Video"
        UsbConstants.USB_CLASS_WIRELESS_CONTROLLER -> "Wireless Controller"
        else -> "Unknown ($value)"
    }
}
