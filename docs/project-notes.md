# ACCESS LINK Tester 프로젝트 메모

이 문서는 새 채팅이나 새 작업 환경에서도 프로젝트 목적과 현재 결정사항을 빠르게 이어가기 위한 기준 문서입니다.

## 1. 프로젝트 목표

**ACCESS LINK Tester**는 SHINHWA SYSTEM의 ACCESS LINK 장비를 Android 휴대폰에서 테스트하고 진단하기 위한 모바일 앱입니다.

1차 목표는 **USB-C 진단 앱**입니다. Android 휴대폰에 ACCESS LINK를 연결했을 때 장비가 어떤 USB 장치로 인식되는지 확인하고, 이후 실제 제어 기능을 붙이기 위한 정보를 수집합니다.

## 2. 대상 장비

사진과 매뉴얼 기준으로 확인한 장비 정보입니다.

| 항목 | 내용 |
| --- | --- |
| 제품명 | ACCESS LINK |
| 모델명 | SHAL-1000 |
| 제조사 | SHINHWA SYSTEM |
| 전원 | DC 12V |
| 용도 추정 | 출입통제 / 산업용 I/O 통합 게이트웨이 |

### 주요 인터페이스

| 인터페이스 | 수량 | 용도 추정 |
| --- | ---: | --- |
| USB-C | 1 | PC, 스마트폰, 태블릿 연결 |
| USB-A | 2 | USB 주변장치 연결 |
| Ethernet | 1 | 네트워크 통신 |
| RS-232 | 1 | 시리얼 장비 통신 |
| RS-485 | 1 | 산업용 시리얼 통신 |
| Wiegand 입력 | 1 | 카드리더 입력 |
| Wiegand 출력 | 1 | Wiegand 신호 출력 |
| Digital Input | 2 | 접점/센서 입력 |
| Relay Output | 2 | 외부 장치 ON/OFF 제어, NC / COM / NO |

## 3. 현재 가정

- ACCESS LINK는 단순 USB 허브가 아니라 출입통제 장비와 산업용 I/O 장비를 연결하는 게이트웨이에 가깝습니다.
- Android에 연결했을 때 USB Serial, HID, USB Ethernet, Composite Device 중 하나 또는 여러 형태로 잡힐 수 있습니다.
- 실제 릴레이 제어와 입력 읽기는 장비가 Android에서 어떤 방식으로 인식되는지 확인한 뒤 구현합니다.

## 4. 개발 환경

| 항목 | 값 |
| --- | --- |
| IDE | Android Studio Quail 1, 2026.1.1 Patch 2 |
| 언어 | Kotlin |
| UI | Jetpack Compose |
| Git 루트 | `C:\Users\admin\Documents\Access Link` |
| Android 프로젝트 | `C:\Users\admin\Documents\Access Link\AccessLinkTester` |
| 패키지명 | `com.shinhwa.accesslinktester` |

### 설치 및 프로젝트 버전

| 항목 | 버전/값 |
| --- | --- |
| Git | 2.55.0.windows.1 |
| Android SDK 경로 | `C:\Users\admin\AppData\Local\Android\Sdk` |
| Android SDK Platform | android-36.1 |
| Android SDK Build Tools | 36.0.0, 36.1.0, 37.0.0 |
| Gradle Wrapper | 9.4.1 |
| Android Gradle Plugin | 9.2.1 |
| Kotlin | 2.2.10 |
| Jetpack Compose BOM | 2026.02.01 |
| compileSdk | 36.1 |
| targetSdk | 36 |
| minSdk | 26 |

## 5. 1차 앱 범위

첫 버전은 실제 제어보다 **장비 인식과 USB 진단**에 집중합니다.

- ACCESS LINK 연결 상태 표시
- Android에서 감지한 USB 장치 목록 표시
- USB 권한 요청
- Vendor ID / Product ID 표시
- Device Class / Subclass / Protocol 표시
- Interface / Endpoint 정보 표시
- Serial / HID / Ethernet / Composite 여부 추정
- 실시간 로그 표시
- 전체 문구와 로그는 한국어 우선

## 6. 이후 확장 기능

USB 인식 방식과 통신 프로토콜이 확인되면 아래 기능을 순차적으로 추가합니다.

- Relay 1 / Relay 2 ON/OFF 테스트
- Digital Input IN0 / IN1 상태 확인
- Wiegand 입력값 확인
- Wiegand 출력 테스트
- RS-232 송수신 테스트
- RS-485 송수신 테스트
- Ethernet 연결 테스트
- 테스트 결과 저장 및 공유

## 7. UI 방향

앱은 홍보용 화면이 아니라 현장에서 바로 쓰는 **장비 진단 대시보드**처럼 보여야 합니다.

### 화면 원칙

- 첫 화면에서 연결 상태, PASS/FAIL, 포트 상태, 로그가 한눈에 보여야 합니다.
- 메뉴를 깊게 나누기보다 단일 대시보드 중심으로 구성합니다.
- 버튼과 상태값은 현장 작업자가 빠르게 판단할 수 있게 크게 표시합니다.
- 모든 주요 문구는 한국어로 표시합니다.

### 상태 색상

| 상태 | 색상 |
| --- | --- |
| 정상 / PASS | 초록 |
| 실패 / FAIL | 빨강 |
| 진행 중 / 활성 | 파랑 |
| 대기 / 미확인 | 회색 |

## 8. 작업 방식

### Android Studio 역할

- 프로젝트 열기
- Gradle Sync
- 앱 빌드
- 휴대폰 실행
- 컴파일/런타임 오류 확인

### Codex 역할

- 프로젝트 파일 읽기
- 코드 작성 및 수정
- 구조 설계
- 오류 원인 분석
- 문서와 메모 갱신

## 9. 다음 작업

1. 기본 앱이 Android 휴대폰에서 실행되는지 확인합니다.
2. `UsbManager` 기반 USB 장치 목록 화면을 구현합니다.
3. ACCESS LINK를 휴대폰에 연결해 실제 Vendor ID / Product ID / Interface 정보를 확인합니다.
4. 확인된 USB 타입에 맞춰 제어 방식과 통신 프로토콜을 정합니다.

## 10. 실물 연결 확인 결과

2026-07-06 ACCESS LINK SHAL-1000을 Android 휴대폰과 Windows 노트북에 연결해 확인했습니다.

### Android USB 인식

| 장치 | VID | PID | 확인 내용 |
| --- | --- | --- | --- |
| Realtek USB 10/100 LAN | `0x0BDA` | `0x8152` | USB Ethernet/LAN 장치 |
| USB Serial | `0x1A86` | `0x7523` | CH340 계열 USB Serial 장치 |

### Windows 인식

- `Realtek USB FE Family Controller`
- `USB-SERIAL CH340(COM4)`
- 설치가이드의 PC 연결 체크리스트와 일치합니다.

## 11. SHAL-1000 프로토콜 요약

프로토콜 매뉴얼 `SHAL-1000 Protocol Manual Rev0.01` 기준입니다.

### 패킷 구조

| Field | Byte(s) | 값/설명 |
| --- | ---: | --- |
| STX | 1 | `0x02` |
| Length | 1 | 전체 패킷 길이 |
| Command | 1 | 명령 코드 |
| Data | Variable | 명령별 데이터 |
| ETX | 1 | `0x03` |

데이터가 없으면 최소 길이는 4입니다.

### 주요 명령

| Command | 이름 | 설명 |
| --- | --- | --- |
| `0x00` | `SET_RELAYCONTROL` | 릴레이 제어 |
| `0x01` | `SET_SERIALSEND` | RS-232C/RS-485 데이터 출력 |
| `0x02` | `SET_WIEGANDOUT` | Wiegand 출력 |
| `0x09` | `GET_WIEGANDINPUTDATA` | Wiegand 입력 데이터 수신 |
| `0x10` | `GET_RECVDATA0` | RS-232C 수신 데이터 |
| `0x11` | `GET_RECVDATA1` | RS-485 수신 데이터 |
| `0x12` | `GET_INPUTPORT0` | Input Port 0 상태 |
| `0x13` | `GET_INPUTPORT1` | Input Port 1 상태 |

### 릴레이 제어 데이터

`SET_RELAYCONTROL (0x00)` 데이터는 4바이트 ASCII 숫자입니다.

| 필드 | 크기 | 값 |
| --- | ---: | --- |
| UseRelay | 1 | `0`: Relay 0+1, `1`: Relay 0, `2`: Relay 1 |
| OutputType | 1 | `0`: OFF, `1`: ON |
| Time | 2 | `00`: 계속 출력, 그 외 출력 시간 |

예: Relay 0 ON 지속 출력 패킷은 `02 08 00 31 31 30 30 03`입니다.
