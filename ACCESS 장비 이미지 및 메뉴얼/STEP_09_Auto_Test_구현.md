# STEP 9 - Auto Test 구현

## 목표
외부 결선 조건을 명확히 한 상태에서 포트별 자동 검증 시나리오를 구현한다.

## Codex 명령문

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
