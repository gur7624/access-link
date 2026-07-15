# 업데이트 이력

ACCESS LINK 앱의 큰 작업 단위 변경 내용을 날짜별로 누적 기록한다.

## 2026-07-15

### 첫 화면 재구성

- 사용자 첫 화면을 안면인식 / 카드 리더기 중심의 대기 화면으로 변경했다.
- 하단 탭을 제거하고 관리자 진입 버튼을 우측 상단의 작은 버튼으로 분리했다.
- 어두운 파랑과 흰색 계열을 섞은 사이버틱한 화면 톤을 적용했다.
- 카드 리더기 영역에는 연결 상태, 최근 인증 기록, 수동 개방 버튼을 표시한다.

### 안면 인증 문 개방 설계 정정

- Android OS 생체 인증 프롬프트는 지문이 함께 노출될 수 있어 안면 전용 출입 인증 요구와 맞지 않는 것으로 정정했다.
- AndroidX Biometric 의존성과 OS 생체 인증 호출은 제거했다.
- 안면 인증 성공 후 문 개방 정책 함수는 전용 얼굴 비교 엔진 연결 지점으로 유지했다.
- 현재 안면인식 버튼은 전용 얼굴 비교 모델이 필요하다는 로그를 남기며, 얼굴 검출만으로 문을 열지 않는다.
- 향후 구현은 전면 카메라 기반 얼굴 등록, 등록 얼굴 템플릿 저장, 실시간 얼굴 비교, 성공/실패 UI 표시, 성공 시 문 개방 순서로 진행한다.

### 변경 파일

- `AccessLinkTester/gradle/libs.versions.toml`
- `AccessLinkTester/app/build.gradle.kts`
- `AccessLinkTester/app/src/main/java/com/shinhwa/accesslinktester/MainActivity.kt`
- `AccessLinkTester/app/src/main/java/com/shinhwa/accesslinktester/AccessLinkAppController.kt`
- `AccessLinkTester/app/src/main/java/com/shinhwa/accesslinktester/model/AppModels.kt`
- `AccessLinkTester/app/src/main/java/com/shinhwa/accesslinktester/ui/AppRoot.kt`
- `AccessLinkTester/app/src/main/java/com/shinhwa/accesslinktester/ui/screens/HomeScreen.kt`
- `AccessLinkTester/app/src/main/java/com/shinhwa/accesslinktester/ui/screens/AccessLogScreen.kt`

### 전면 카메라 얼굴 검출/등록 UI 추가

- CameraX와 ML Kit Face Detection 의존성을 추가했다.
- Android 카메라 권한을 추가했다.
- 사용자 첫 화면의 안면인식 영역에 전면 카메라 프리뷰와 얼굴 검출 상태를 표시한다.
- 관리자 화면에 `얼굴` 탭을 추가했다.
- 관리자 얼굴 등록 화면에서 얼굴이 1명 감지될 때 등록자 이름을 저장할 수 있게 했다.
- 등록 얼굴 목록을 SharedPreferences에 저장/복원한다.
- 현재 단계는 얼굴 검출과 등록 UI까지이며, 동일인 판별과 문 개방은 얼굴 비교 모델 연결 전까지 막는다.

### 온디바이스 얼굴 비교 및 문 개방 연결

- Apache-2.0 라이선스의 FaceNet TFLite 모델(`facenet.tflite`)을 앱 assets에 추가했다.
- TensorFlow Lite 런타임 의존성을 추가했다.
- 카메라 프레임에서 얼굴 영역을 잘라 160x160 RGB 입력으로 변환하고 128차원 얼굴 임베딩을 생성한다.
- 관리자 얼굴 등록 시 이름과 얼굴 임베딩을 함께 저장하도록 변경했다.
- 사용자 첫 화면에서 등록 임베딩과 현재 얼굴 임베딩의 L2 거리를 비교한다.
- 등록 얼굴과 일치하면 초록 상태를 표시하고 문 개방 명령을 전송한다.
- 미등록 얼굴이면 빨간 상태로 `등록되지 않은 사용자입니다`를 표시하고 문을 열지 않는다.
- 반복 개방과 반복 실패 로그를 막기 위해 안면 개방 쿨다운과 실패 로그 간격을 적용했다.

### 모델 출처

- FaceNet Android 예제 및 모델: https://github.com/shubham0204/FaceRecognition_With_FaceNet_Android
- 라이선스: Apache License 2.0
