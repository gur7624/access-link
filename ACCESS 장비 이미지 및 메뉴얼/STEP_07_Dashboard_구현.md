# STEP 7 - Dashboard 구현

## 목표
각 모듈의 실제 상태와 최근 활동을 한 화면에서 확인한다.

## Codex 명령문

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
