# Cash Chat 프론트엔드 작업 정리 (2026-03-08)

## 1) 오늘 작업 목표
- iOS/Android 실행 이슈 해결
- iOS UI를 Android와 전반적으로 유사하게 맞추기
- iOS 안전영역(Safe Area), 사이드바, 전환 애니메이션 디테일 개선
- 탭 전환/화면 진입 애니메이션을 더 자연스럽게 조정

---

## 2) 빌드/실행 이슈 정리

### A. Xcode Script Phase 에러
#### 증상
- `./gradlew: Permission denied`
- `Unable to locate a Java Runtime`
- `JAVA_HOME is set to an invalid directory`

#### 원인
- `gradlew` 실행 권한/실행 방식 문제
- Java 런타임 미설치 또는 Xcode Script Phase에서 잘못된 `JAVA_HOME` 경로 사용
- 특히 `/Applications/AndroidStudio.app/...` vs `/Applications/Android Studio.app/...` 경로 혼선

#### 조치
- Script Phase에서 Java 경로 확인 및 환경 변수 재설정
- `bash ./gradlew ...` 형태로 실행
- 실제 존재하는 JBR 경로 확인 후 반영

#### 결과
- Java 관련 에러는 해소 방향으로 진행됨
- 이후 경고는 남았지만, 핵심 에러는 `JAVA_HOME`/권한 이슈 중심으로 정리됨

---

### B. Xcode 경고
#### 증상
- `Run script build phase 'Embed Shared Framework' will be run during every build ...`

#### 의미
- 에러가 아니라 경고
- Output 파일 미지정으로 빌드 때마다 스크립트가 재실행된다는 뜻

#### 권장 처리
1. `Based on dependency analysis` 체크 해제 (간단)
2. 또는 Script Phase `Output Files`에 framework 출력 경로 지정

---

### C. Android Studio 실행 이슈
#### 증상
- `Error Module not specified` (Edit Configuration)

#### 원인
- Run Configuration에서 모듈 미지정

#### 조치
- Android App 실행 구성에서 올바른 module 지정
- Gradle/JDK 경로 확인(Wrapper/JVM 21 등)

---

## 3) iOS UI/UX 개선 내용

주요 수정 파일:
- `CashChatIOS/CashChatIOS/ContentView.swift`

### A. 구조 안정화
- `MainTabContainer` 스코프 오류 수정
  - `cannot find MainTabContainer in scope` 해결
- 중첩 타입 참조 및 접근성 정리

### B. 채팅 화면 Safe Area 개선
- 과한 Safe Area 가산을 줄이고 레이아웃 단순화
- 메인 화면은 과도한 inset 보정을 줄여 자연스럽게
- 사이드바 콘텐츠 영역은 top/bottom safe area를 별도로 고려

### C. 사이드바 UX 개선
- 오버레이/딤 레이어 강도 낮춤
- 패널 폭/그림자/경계선을 과하지 않게 조정
- 콘텐츠 시작 위치 상향 조정
- 요청 반영: 사이드바 콘텐츠 시작점을 상단으로 더 올림

### D. 네비게이션 바(탭바)
- 커스텀 플로팅 형태 실험 후
- 사용자 피드백 반영하여 iOS 기본 `TabView` 스타일로 복귀

### E. 마이페이지 애니메이션
- 진입 시 도미노(stagger) 형태 애니메이션 적용
  - 헤더, 메뉴 아이템, 통계 카드 순차 등장
- `opacity + offset` 기반으로 부드러운 진입

### F. 채팅/리워드/상점 탭 전환 체감 개선
- 각 탭 화면 진입 시 페이드+슬라이드 인 적용
- 초기값(빠름)에서 사용자 피드백 반영해 속도 완화
  - duration 증가
  - 시작 지연 소폭 추가
  - offset 값 조정으로 더 자연스러운 체감

---

## 4) 현재 상태 요약

- iOS 화면 전환/진입 애니메이션은 기본 동작 완료
- 사이드바 시작 위치/강도/자연스러움 개선 반영
- 네비게이션 바는 기존 iOS 네이티브 형태로 복귀
- Safe Area는 메인 과보정 제거 + 사이드바 콘텐츠 기준 보장 방향으로 정리

---

## 5) 다음 권장 점검 항목 (빠른 QA 체크리스트)

1. **iOS 실기기/노치 기기 확인**
   - iPhone 15/16 계열에서 상단 헤더/사이드바 시작선 확인
2. **탭 전환 체감 확인**
   - Chat ↔ Rewards ↔ Shop에서 속도/부드러움 최종 튜닝
3. **사이드바 스크롤 하단 여백 확인**
   - 홈 인디케이터 영역과 마지막 셀 겹침 여부
4. **광고 모달/첫 채팅 화면 비교 확인**
   - Android 대비 텍스트/간격/강조 요소 동등성 체크

---

## 6) 참고
- 오늘 변경은 대부분 `ContentView.swift` 중심으로 진행됨
- Xcode 경고(`Embed Shared Framework`)는 치명적 에러가 아님
- Java/Gradle 환경은 경로 오타와 런타임 인식 문제가 핵심이었음
