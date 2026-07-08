# STEP 5 - Digital Input 구현

## 목표
두 입력 포트의 Pressed/Released 이벤트를 안정적으로 상태화하고 기록한다.

## Codex 명령문

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
