package com.shinhwa.accesslinktester

object AdminConfig {
    // ═══════════════════════════════════════════════════════════════
    //  관리자 PIN — 여기서만 변경하세요
    //  현재 값: "3919"
    //  운영 배포 전 반드시 변경 / 향후 설정화면 이전 고려
    // ═══════════════════════════════════════════════════════════════
    const val ADMIN_PIN = "3919"

    /** 관리자 PIN 검증. 이 함수는 반드시 위 상수만 참조한다. */
    fun isValid(pin: String): Boolean = pin == ADMIN_PIN
}
