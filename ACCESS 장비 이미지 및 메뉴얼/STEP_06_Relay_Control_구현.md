# STEP 6 - Relay Control 구현

## 목표
Relay 1/2/Both 제어, 연속/시간 출력, 안전한 상태 표현을 구현한다.

## Codex 명령문

SHAL-1000 Relay Control 기능을 구현해라.

Protocol:
Command = 0x00 SET_RELAYCONTROL
Control Data Size = 4 ASCII characters

구성:
UseRelay: 1 character
- 0 = Relay 0 + Relay 1
- 1 = Relay 0
- 2 = Relay 1

OutputType: 1 character
- 0 = OFF
- 1 = ON

Time: 2 characters
- 00이면 연속 출력
- 정확한 시간 단위는 Protocol Manual에서 명확하지 않으므로 seconds라고 임의 확정하지 마라.

화면 요구사항:
Relay 1
- ON
- OFF

Relay 2
- ON
- OFF

Both
- ON
- OFF

Output Mode:
- Continuous
- Timed

Time 입력:
- 프로토콜 규격에 맞는 2자리 입력 범위 제한
- 시간 단위 미확인 시 UI에 잘못된 단위 표시 금지
- Raw Time 또는 '장비 사양 확인 필요'로 처리

추가 버튼:
- ALL OFF

안전 조건:
- 앱 시작 시 현재 상태를 모르는 상태에서 ON/OFF라고 단정하지 말 것
- Command Sent와 실제 동작 확인을 구분할 것
- 별도 Relay 상태 조회 Command가 확인되지 않으므로 송신 성공을 실제 접점 상태와 동일하게 취급하지 말 것
- UI 상태 예: UNKNOWN, COMMAND_SENT_ON, COMMAND_SENT_OFF, ERROR
- 실제 확인 수단이 추가되기 전에는 'Relay ON 상태' 대신 'ON 명령 송신됨'처럼 표현할 것

모든 Relay 명령은 Global Log에 연결 가능한 공통 이벤트로 기록해라.

구현 후:
- 생성한 Packet 예시
- UI State 구조
- 안전 처리 방식
- 실제 장비 테스트 순서
를 설명해라.
