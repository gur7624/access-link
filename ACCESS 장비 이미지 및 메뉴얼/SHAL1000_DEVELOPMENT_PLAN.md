# SHAL-1000 Codex Development Plan

## 실행 원칙

프로젝트 루트의 다음 문서를 먼저 읽어라.

1. AGENTS.md
2. SHAL1000_DEVELOPMENT_PLAN.md 또는 제공된 STEP 문서들

작업 원칙:
1. STEP은 번호 순서대로 진행한다.
2. 한 번에 하나의 STEP만 수행한다.
3. 각 STEP 시작 전에 현재 코드와 이전 STEP 결과를 확인한다.
4. 구현 후 반드시 빌드와 가능한 테스트를 실행한다.
5. 빌드 또는 테스트 실패 시 다음 STEP으로 넘어가지 않는다.
6. 오류를 수정하고 다시 검증한다.
7. 기존 정상 동작 코드를 이유 없이 삭제하거나 전체 재작성하지 않는다.
8. Protocol Manual에 없는 값을 추측해서 구현하지 않는다.
9. 문서 충돌 또는 불명확 사항은 TODO/확인 필요 항목으로 남긴다.
10. 각 STEP 종료 시 아래 형식으로 보고한다.

- 구현한 기능
- 변경한 파일
- 새로 생성한 파일
- 빌드 결과
- 테스트 결과
- 실제 SHAL-1000 장비 검증이 필요한 항목
- 남은 문제
- 다음 STEP

지금은 STEP 0부터 시작해라.
STEP 0이 끝난 후 결과를 보고하고, 다음 STEP으로 자동 진행하지 마라.

---

## STEP 0 - 프로젝트 전체 분석

코드를 수정하기 전에 현재 프로젝트 구조와 구현 상태를 정확히 파악한다.

```text
현재 Android Studio Kotlin 프로젝트는 SHAL-1000 ACCESS LINK 장비를 테스트하고 제어하기 위한 미완성 앱이다.

먼저 코드를 수정하지 말고 프로젝트 전체를 분석해라.

분석할 내용:
1. 현재 프로젝트 구조
2. 사용 중인 Architecture 패턴
3. USB 통신 구현 여부
4. USB Serial 또는 장비 통신 코드 위치
5. 현재 화면별 구현 상태
6. RS-232, RS-485, Wiegand, Digital Input, Relay 관련 기존 코드 존재 여부
7. 현재 빌드 오류
8. 중복 코드와 잘못된 구조
9. Protocol Manual 적용을 위해 추가해야 할 구조
10. 기존 코드를 최대한 유지하면서 리팩터링할 방법
11. 앱과 SHAL-1000 사이의 실제 주 통신 경로가 코드상 무엇인지 확인
12. 연결 객체가 Activity/Fragment 수명주기에 묶여 중복 생성될 위험이 있는지 확인

최종적으로 아래 형식으로 보고해라.
- 현재 구현 완료 기능
- 미완성 기능
- 문제점
- 수정이 필요한 파일
- 새로 만들어야 하는 파일
- 실제 통신 경로
- 권장 개발 순서
- 각 단계별 예상 변경 범위

중요:
- 아직 코드를 수정하지 마라.
- 추측으로 기존 기능을 삭제하지 마라.
- 프로젝트를 실제로 읽고 분석한 결과만 보고해라.
- STEP 0 완료 후 다음 STEP으로 자동 진행하지 마라.
```

---

## STEP 1 - 프로토콜 계층 구현

패킷 생성, 스트림 디코딩, 명령 라우팅을 UI와 분리하여 구현한다.

```text
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
```

---

## STEP 2 - 장비 연결 계층 구현

SHAL-1000과의 연결, 송수신, 권한, 재연결, 타임아웃을 안정적으로 관리한다.

```text
SHAL-1000 장비 연결 관리 계층을 구현해라.

먼저 현재 프로젝트에 구현된 실제 연결 방식을 확인해라.
확인 대상:
- Android USB Host
- USB Serial
- USB CDC
- 전용 USB 드라이버
- TCP Socket
- 기존 Connection Manager

현재 코드에서 실제 사용 중인 연결 방식을 우선 사용하고, 확인되지 않은 통신 방식을 추측하여 새로 추가하지 마라.

필요 기능:
1. 장비 연결
2. 연결 해제
3. 연결 상태 관리
4. 패킷 송신
5. 비동기 수신
6. Packet Decoder 연결
7. 연결 해제 감지
8. Timeout 처리
9. 송수신 오류 처리
10. 선택적 자동 재연결
11. USB 권한 처리
12. Activity 재생성/화면 전환 시 Connection 객체 중복 생성 방지
13. 앱 백그라운드/포그라운드 전환 시 연결 정책 정리
14. 송신 직렬화가 필요한지 검토하고 race condition 방지

연결 상태 예:
DISCONNECTED
CONNECTING
CONNECTED
ERROR

UI 코드와 실제 통신 객체를 분리해라.
기존 통신 코드를 무조건 교체하지 말고 정상 동작하는 부분은 최대한 재사용해라.

작업 완료 후:
- 실제 사용 중인 통신 경로
- 연결 흐름
- 변경 파일
- 권한 처리 흐름
- 재연결 정책
- 테스트 방법
을 보고해라.

빌드와 가능한 테스트를 수행하고 실패 시 다음 STEP으로 넘어가지 마라.
```

---

## STEP 3 - RS-232 / RS-485 기능 구현

SHAL-1000의 Serial Send 명령과 수신 이벤트를 이용해 두 포트를 테스트한다.

```text
SHAL-1000의 RS-232와 RS-485 테스트 기능을 구현해라.

Protocol Manual 기준:
SET_SERIALSEND Command = 0x01

Data:
- UseSerialPort: ASCII 숫자 1자리
  - 0 = RS-232C
  - 1 = RS-485
- SendData: Hex 1~64 bytes

수신:
- 0x10 = RS-232 Receive
- 0x11 = RS-485 Receive

RS-232 화면 요구사항:
- 연결 상태
- ASCII / HEX 입력 모드 선택
- 데이터 입력창
- SEND 버튼
- TX 로그
- RX 로그
- Clear
- Auto Scroll
- 송신 성공/실패 표시

RS-485 화면 요구사항:
- RS-232와 동일한 구성
- 송신 시 UseSerialPort = 1 사용

구현 조건:
- ASCII 입력을 실제 ByteArray로 변환
- HEX 입력 검증
- 홀수 자리 HEX, 잘못된 문자, 공백 처리 정책 정의
- 64바이트 제한 처리
- RX와 TX 방향 구분
- 실제 SHAL 프로토콜 패킷과 사용자가 입력한 Payload를 로그에서 구분
- 빠른 연속 송신 시 UI 멈춤 또는 패킷 뒤섞임 방지
- 수신 데이터는 HEX와 ASCII 두 표현 모두 제공하되 제어문자는 안전하게 표시

기존 디자인 시스템이 있으면 유지해라.
기능부터 정확히 구현하고 과도한 UI 변경은 하지 마라.

구현 후:
- 생성 패킷 예시
- 64바이트 경계 테스트 결과
- ASCII/HEX 변환 테스트 결과
- 실제 장비 테스트 순서
를 보고해라.

실제 빌드와 테스트를 수행해라.
```

---

## STEP 4 - Wiegand Input / Output 구현

Wiegand 수신 모니터와 Wiegand 출력 송신 기능을 분리 구현한다.

```text
SHAL-1000의 Wiegand 기능을 구현해라.

Wiegand는 INPUT과 OUTPUT을 별도 기능으로 구현한다.

[Wiegand Input]
Protocol Manual 기준:
- Command Code List: 0x09 GET_WIEGANDINPUTDATA
- 수신 데이터 길이:
  - 8 characters = 26-bit
  - 10 characters = 34-bit

화면 표시:
- Wiegand Format: 26 bit 또는 34 bit
- Received Data
- Raw Data
- 수신 시간
- 수신 횟수 Count
- 마지막 수신 데이터
- Clear

D0와 D1을 Android UI에서 실시간 High/Low처럼 표시하지 마라.
현재 Protocol Manual이 제공하는 범위는 해석된 Wiegand Input Data이므로 문서 범위 내에서 구현한다.

[Wiegand Output]
Command:
0x02 SET_WIEGANDOUT

Data:
- UseParity
  - 0 = Output parity
  - 1 = Do not output parity
- WiegandData
  - Hex 1~16 bytes

화면:
- Wiegand 데이터 입력
- HEX validation
- Parity 사용 여부 선택
- SEND 버튼
- TX 결과 로그

중요한 문서 충돌:
- Command Code List에서는 Wiegand Input이 0x09
- Wiegand Input 상세 페이지 Response Packet에는 0x10
- 0x10은 RS-232 Receive로도 정의됨

따라서:
- 문서 충돌을 코드 주석과 개발 문서에 명시
- 실제 수신 Raw Packet Debug Logging 추가
- 임의로 잘못된 매핑을 확정하지 말 것
- 필요하면 Command Mapping을 설정 가능하게 구조화할 것
- 현재 장비 또는 기존 코드에서 근거를 찾으면 근거를 보고할 것

구현 후:
- 입력 파싱 규칙
- 출력 패킷 생성 예시
- 문서 충돌 대응 방식
- 실제 장비 검증 항목
을 보고해라.
```

---

## STEP 5 - Digital Input 구현

두 입력 포트의 Pressed/Released 이벤트를 안정적으로 상태화하고 기록한다.

```text
SHAL-1000 Digital Input 화면과 이벤트 처리를 구현해라.

Protocol Manual:
- 0x12 = Input Port 0
- 0x13 = Input Port 1

Status:
- 0 = Released
- 1 = Pressed

각 Input별 표시:
- 현재 상태
- PRESSED / RELEASED
- 마지막 변경 시간
- Press Count
- Release Count
- 전체 Event Count

화면 예:
Input 0
Status: PRESSED
Last Change: 11:30:24.152
Press Count: 10
Release Count: 9

Input 1
Status: RELEASED
Last Change: 11:29:10.011
Press Count: 4
Release Count: 4

추가 조건:
- 동일 상태 중복 이벤트 처리 정책을 명확하게 구현
- 화면 회전/화면 이동으로 Count가 임의 초기화되지 않게 상태 관리
- 모든 상태 변경을 Global Log에 기록할 연결 지점 준비
- Clear Count 기능 추가
- 장비 재연결 시 기존 상태를 UNKNOWN으로 할지 유지할지 정책을 명시
- 최초 상태 수신 전에는 PRESSED/RELEASED를 추측하지 말고 UNKNOWN 표시

구현 후:
- 상태 머신
- 중복 이벤트 처리 정책
- 재연결 시 상태 정책
- 테스트 결과
을 보고해라.
```

---

## STEP 6 - Relay Control 구현

Relay 1/2/Both 제어, 연속/시간 출력, 안전한 상태 표현을 구현한다.

```text
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
```

---

## STEP 7 - Dashboard 구현

각 모듈의 실제 상태와 최근 활동을 한 화면에서 확인한다.

```text
ACCESS LINK Test App의 Dashboard를 구현해라.

기존에 구현된 각 기능의 실제 State를 사용하고 가짜 데이터나 임의의 상태를 만들지 마라.

Dashboard 표시 항목:
1. Device Connection
- Connected
- Disconnected
- Error

2. RS-232
- 최근 TX 시간
- 최근 RX 시간
- TX Count
- RX Count

3. RS-485
- 최근 TX 시간
- 최근 RX 시간
- TX Count
- RX Count

4. Wiegand Input
- 최근 Format
- 최근 Data
- Receive Count

5. Digital Input
- Input 0 상태
- Input 1 상태

6. Relay
- 마지막으로 보낸 Relay 1 Command
- 마지막으로 보낸 Relay 2 Command
- 실제 접점 상태 확인 불가 시 이를 실제 Relay 상태라고 표시하지 말 것

7. Error
- 최근 Protocol Error
- Timeout Count
- Invalid Packet Count

상호작용:
- 각 카드를 누르면 해당 상세 화면으로 이동
- 연결 해제 상태에서 제어 동작 시 명확한 오류 처리
- Dashboard 업데이트가 과도한 recomposition 또는 UI 정지를 유발하지 않도록 상태 구독 구조 점검

과도한 애니메이션은 넣지 말고 테스트 장비 앱에 맞게 정보 전달 중심으로 구성해라.

구현 후:
- Dashboard State 구조
- 각 카드의 데이터 소스
- Navigation 흐름
- 성능 점검 결과
를 보고해라.
```

---

## STEP 8 - Global Log 시스템 구현

모든 통신과 이벤트를 하나의 구조로 수집, 검색, 필터, CSV 저장한다.

```text
앱 전체에서 공통으로 사용하는 Global Log 시스템을 구현해라.

Log 항목:
- Timestamp
- Interface
  - DEVICE
  - RS232
  - RS485
  - WIEGAND_IN
  - WIEGAND_OUT
  - DIGITAL_INPUT
  - RELAY
  - PROTOCOL
- Direction
  - RX
  - TX
  - EVENT
  - ERROR
- Command Code
- Payload
- Raw Packet HEX
- ASCII Representation
- Data Length
- Result
- Error Message

Log Viewer 기능:
- 실시간 표시
- Pause / Resume
- Clear
- Auto Scroll
- Interface 필터
- RX/TX/Event/Error 필터
- 검색
- 최대 로그 개수 관리
- 오래된 로그 때문에 메모리 문제가 생기지 않도록 제한
- CSV Export

구현 원칙:
- 각 기능에서 개별 로그 형식을 만들지 말고 공통 Logger 또는 LogRepository 사용
- UI가 느려지지 않도록 배치/버퍼/Flow 구조 검토
- CSV 저장 시 Android Storage 정책과 공유 방식 검토
- 제어문자 때문에 CSV가 깨지지 않게 escaping 처리
- 사용자 입력 Payload와 SHAL Protocol Raw Packet을 별도 필드로 구분

구현 후:
- LogEntry 데이터 구조
- Repository 구조
- 최대 로그 보관 정책
- CSV 컬럼 구조와 예시
- 성능 테스트 결과
를 보고해라.
```

---

## STEP 9 - Auto Test 구현

외부 결선 조건을 명확히 한 상태에서 포트별 자동 검증 시나리오를 구현한다.

```text
SHAL-1000 기능 검증을 위한 Auto Test 구조를 설계하고 구현해라.

단, 실제 외부 결선이나 Loopback Jig가 필요한 테스트를 소프트웨어만으로 성공했다고 판정하지 마라.

지원할 테스트 시나리오:

1. RS-232 Loopback Test
- 지정 데이터 TX
- 정해진 Timeout 동안 RX 대기
- TX Payload와 RX Payload 비교
- PASS / FAIL / TIMEOUT

2. RS-485 Loopback Test
- 동일 방식

3. Wiegand OUT -> IN Test
- Wiegand Output 송신
- Input 수신 대기
- 송수신 데이터 비교
- PASS / FAIL / TIMEOUT

4. Relay -> Digital Input Test
- 별도 테스트 결선이 있다는 전제에서만 실행
- Relay Command 실행
- Digital Input 이벤트 대기
- 예상 상태와 실제 이벤트 비교
- Relay OFF 복귀
- 결과 기록

Auto Test 공통 기능:
- 사전 조건 표시
- 필요한 결선 안내
- 테스트 시작
- 진행 상태
- 단계별 결과
- Timeout
- PASS / FAIL
- 실패 이유
- 전체 Test Report
- 테스트 중 앱 종료/연결 해제 시 ABORTED 처리
- 중복 테스트 실행 방지
- 안전하게 Relay를 OFF로 복귀시키는 cleanup 단계

하드웨어 결선이 확인되지 않은 경우 자동으로 PASS 처리하지 마라.

구현 후:
- TestCase 인터페이스/상태 머신 구조
- 각 테스트의 사전 조건
- Timeout 정책
- Cleanup 정책
- 실제 장비 검증 절차
를 보고해라.
```

---

## STEP 10 - 전체 검증 및 안정화

새 기능 추가를 중단하고 빌드, 테스트, 수명주기, 통신 경계조건을 집중 검증한다.

```text
지금까지 구현된 SHAL-1000 ACCESS LINK Android 앱 전체를 검토해라.

새로운 기능을 추가하지 말고 품질 점검과 오류 수정에 집중해라.

점검 항목:
1. 프로젝트 전체 Build
2. Unit Test 실행
3. Compile Error
4. Runtime Crash 가능성
5. USB 권한 문제
6. 연결 객체 중복 생성 문제
7. Coroutine/Thread 문제
8. Flow 수집 중복
9. Lifecycle 문제
10. 메모리 누수 가능성
11. Packet split/merge 처리
12. Garbage byte 이후 resync
13. 잘못된 Length 처리
14. 잘못된 HEX 입력 처리
15. 64-byte Serial 제한 경계값
16. Wiegand 입력 명령 코드 문서 충돌 처리
17. Relay Time 단위 임의 가정 여부
18. Relay Command Sent와 실제 물리 상태 혼동 여부
19. 화면 회전/재생성 시 상태 유지
20. 연결 해제 후 재연결
21. 대량 로그 발생 시 성능
22. Auto Test 중 연결 해제/앱 백그라운드 전환
23. 모든 실패 경로의 사용자 오류 메시지
24. 장비 미연결 상태에서 제어 버튼 동작
25. Release Build 가능 여부

문제를 발견하면 수정하고, 수정 후 다시 빌드와 테스트를 수행해라.

최종 보고서 형식:
- 수정한 문제
- 아직 남은 문제
- 실제 SHAL-1000 장비가 있어야 검증 가능한 항목
- 문서 충돌/미확인 사양 목록
- 현재 완성도
- 실제 장비 테스트 순서
- 배포 전 체크리스트

검증 결과를 사실대로 보고하고 확인하지 못한 항목을 성공으로 표시하지 마라.
```

---
