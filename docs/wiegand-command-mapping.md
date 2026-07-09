# Wiegand Command Mapping Note

STEP_04 기준으로 SHAL-1000 프로토콜 문서에 Wiegand Input 명령 충돌이 있다.

- Command Code List: `0x09` = `GET_WIEGANDINPUTDATA`
- Wiegand Input 상세 Response Packet: `0x10` 표기
- 같은 문서에서 `0x10`은 `RS-232 Receive`로도 정의됨

현재 앱 구현은 기존 코드와 Command Code List 근거에 따라 Wiegand Input을 `0x09`로 유지한다.
`0x10`은 RS-232 Receive로 처리한다.

실제 장비에서 `0x10` 패킷이 Wiegand Input으로 확인되기 전까지 임의로 매핑을 바꾸지 않는다.
Raw Packet 로그를 남겨 실제 장비 테스트 중 근거를 확보한다.
