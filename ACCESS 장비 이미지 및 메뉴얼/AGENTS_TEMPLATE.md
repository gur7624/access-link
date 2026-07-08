# SHAL-1000 ACCESS LINK Android App - Project Rules

## 목적
이 프로젝트는 SHAL-1000 ACCESS LINK 장비의 통신, 입력, 출력, 릴레이 제어, 실시간 로그, 자동 테스트를 수행하는 Android 테스트 앱이다.

## 기본 개발 원칙
- Kotlin과 현재 Android Studio 프로젝트 구성을 우선 유지한다.
- 기존 정상 동작 기능을 임의로 삭제하지 않는다.
- UI와 통신/프로토콜 로직을 분리한다.
- 모든 송수신과 이벤트를 공통 로그 구조로 기록한다.
- Packet Encoder, Packet Decoder, Command Router, Connection Layer를 분리한다.
- 가능한 부분에는 Unit Test를 작성한다.
- Protocol Manual에 없는 값, 시간 단위, 상태를 임의로 가정하지 않는다.
- 실제 장비에서 확인할 수 없는 물리 상태를 앱 내부 상태로 단정하지 않는다.

## SHAL-1000 프로토콜 기본 구조
STX | Length | Command | Data | ETX

- STX = 0x02
- ETX = 0x03
- Length = 전체 패킷 길이
- Data는 명령에 따라 가변 길이

## 주요 명령
- 0x00 SET_RELAYCONTROL
- 0x01 SET_SERIALSEND
- 0x02 SET_WIEGANDOUT
- 0x09 GET_WIEGANDINPUTDATA (Command Code List 기준)
- 0x10 GET_RECVDATA0 (RS-232 수신)
- 0x11 GET_RECVDATA1 (RS-485 수신)
- 0x12 GET_INPUTPORT0
- 0x13 GET_INPUTPORT1

## 문서상 주의사항
1. Wiegand Input 명령 코드 충돌
   - Command Code List: 0x09
   - Wiegand Input 상세 페이지의 Response Packet: 0x10
   - 0x10은 RS-232 Receive에도 사용됨
   - 실제 장비 또는 기존 구현으로 확인하기 전 임의 확정 금지

2. Relay Time 단위
   - 매뉴얼에는 2자리 Time 필드와 00=연속 출력 조건이 있으나 시간 단위가 명확하지 않음
   - 초 단위라고 임의 가정 금지

3. Relay 상태
   - 제어 명령 송신 성공과 실제 물리 접점 상태는 다름
   - 별도의 상태 조회 명령이 확인되지 않으므로 Command Sent와 Physical State를 구분한다.

## 검증 규칙
- 각 STEP 종료 후 Build 및 가능한 Test 실행
- 실패 시 다음 STEP으로 진행 금지
- 장비가 필요한 검증 항목은 명확히 분리하여 보고
