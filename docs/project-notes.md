# ACCESS LINK 초기 조사 메모

이 문서는 ACCESS LINK Tester 프로젝트 초기에 확인한 장비 인식, 개발 환경, 프로토콜 기초 내용을 보관하는 메모입니다.

최신 제품 해석, 앱 방향, Ethernet 테스트 순서, 제조 담당자 확인 질문은 `docs/access-link-knowledge-brief.md`를 기준으로 합니다.

## 1. 현재 문서의 역할

이 문서는 다음 내용을 보관합니다.

- 초기 USB-C 장치 인식 결과
- Android/Windows에서 확인한 USB 장치 정보
- SHAL-1000 프로토콜 기본 패킷 구조
- 초기 개발 환경 정보

이 문서는 최신 기획서가 아닙니다.
앱 구조와 UI/UX 방향은 `access-link-knowledge-brief.md`와 실제 코드 기준을 따릅니다.

## 2. 대상 장비

| 항목 | 내용 |
| --- | --- |
| 제품명 | ACCESS LINK |
| 모델명 | SHAL-1000 |
| 제조사 | SHINHWA SYSTEM |
| 전원 | DC 12V |
| 장비 성격 | 앱·서버·태블릿과 현장 장비를 연결하는 범용 인터페이스 장비 |

### 주요 인터페이스

| 인터페이스 | 수량 | 용도 |
| --- | ---: | --- |
| USB-C | 1 | PC, 스마트폰, 태블릿 연결 및 초기 진단 |
| USB-A | 2 | USB 주변장치 연결 |
| Ethernet | 1 | 운영 네트워크 통신 |
| RS-232 | 1 | 시리얼 장비 통신 |
| RS-485 | 1 | 산업용 시리얼 통신 |
| Wiegand 입력 | 1 | 카드리더 입력 |
| Wiegand 출력 | 1 | Wiegand 신호 출력 |
| Digital Input | 2 | 접점, 버튼, 센서 입력 |
| Relay Output | 2 | 외부 장치 ON/OFF 제어, NC / COM / NO |

## 3. 개발 환경

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

## 4. 초기 USB-C 연결 확인 결과

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

## 5. SHAL-1000 프로토콜 요약

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

## 6. 현재 기준에서 제외한 초기 방향

아래 내용은 초기 단계에서는 유효했지만 현재 기준 문서에서는 사용하지 않습니다.

- 앱을 USB-C 진단 앱으로만 한정하는 방향
- 모든 기능을 단일 대시보드에 모으는 방향
- `문 1`, `문 2`, `Relay 1`, `Relay 2`처럼 현장 용도에 고정된 표현
- Ethernet 설정을 나중 확장으로만 미루는 방향

현재 기준은 다음입니다.

- 기본 용어는 Relay 0/1, Input 0/1, Wiegand, RS-232, RS-485, Ethernet, USB를 사용합니다.
- 운영 연결은 Ethernet 중심으로 확장합니다.
- USB-C는 초기 설정, 현장 진단, 개발 테스트 용도로 유지합니다.
- 관리자 화면은 장비 연결, 출력 설정, 입력 설정, 통신 진단, 인증 동작을 기능별 화면으로 나눕니다.
