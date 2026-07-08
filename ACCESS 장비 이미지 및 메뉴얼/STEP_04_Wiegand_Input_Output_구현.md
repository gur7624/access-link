# STEP 4 - Wiegand Input / Output 구현

## 목표
Wiegand 수신 모니터와 Wiegand 출력 송신 기능을 분리 구현한다.

## Codex 명령문

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
