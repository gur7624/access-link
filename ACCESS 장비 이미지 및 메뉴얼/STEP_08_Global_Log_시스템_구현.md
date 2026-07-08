# STEP 8 - Global Log 시스템 구현

## 목표
모든 통신과 이벤트를 하나의 구조로 수집, 검색, 필터, CSV 저장한다.

## Codex 명령문

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
