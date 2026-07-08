# STEP 1 - 프로토콜 계층 구현

## 목표
패킷 생성, 스트림 디코딩, 명령 라우팅을 UI와 분리하여 구현한다.

## Codex 명령문

SHAL-1000 Protocol Layer를 구현해라.

UI는 수정하지 말고 프로토콜 처리 계층부터 구현해라.

프로토콜 기본 구조:
STX | Length | Command | Data | ETX
- STX = 0x02
- ETX = 0x03
- Length는 Protocol Manual 정의에 맞춰 전체 패킷 길이로 처리한다.

필요 기능:
1. Packet Encoder
2. Streaming Packet Decoder
3. Command Router
4. Protocol Error 모델
5. Unit Test

Packet Decoder는 아래 경우를 처리해야 한다.
- Partial packet: 한 패킷이 여러 번에 나뉘어 수신
- Multiple packets: 여러 패킷이 한 버퍼에 붙어서 수신
- Garbage bytes before STX
- Invalid STX
- Invalid ETX
- Invalid Length
- Unknown Command
- 다음 정상 패킷으로 복구 가능한 resynchronization

Command Router 대상:
- 0x00 Relay 관련 응답/결과
- 0x01 Serial Send 관련 응답/결과
- 0x02 Wiegand Output 관련 응답/결과
- 0x09 Wiegand Input 후보
- 0x10 RS-232 Receive 및 문서 충돌 검토
- 0x11 RS-485 Receive
- 0x12 Digital Input 0
- 0x13 Digital Input 1

Kotlin 이벤트 모델 예시:
sealed interface DeviceEvent
- SerialReceived
- WiegandReceived
- DigitalInputChanged
- RelayCommandResult
- ProtocolError

프로젝트의 현재 Architecture에 더 적합한 타입이 있으면 그 구조를 사용해라.

최소 Unit Test:
- 정상 패킷 인코딩
- 정상 패킷 디코딩
- 분할 수신
- 연속 패킷 수신
- Garbage + 정상 패킷 복구
- 잘못된 STX/ETX
- 잘못된 Length
- Unknown Command

작업 완료 후:
1. 변경 파일 목록
2. 구현 구조
3. 테스트 결과
4. 아직 확인이 필요한 프로토콜 문제
5. 다음 STEP에서 Connection Layer와 연결할 지점
을 보고해라.

프로젝트를 실제 빌드하고 가능한 테스트를 실행해라.
