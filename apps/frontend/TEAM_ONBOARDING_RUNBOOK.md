# Cash Chat Frontend 팀 설정/실행 가이드

최종 업데이트: 2026-03-08
대상: Android + iOS 함께 작업하는 팀원

---

## 개요
이 문서는 팀원이 저장소를 `pull` 받은 뒤, 환경 이슈 없이 바로 실행할 수 있도록 필요한 설정과 실행 절차를 정리한 문서입니다.

핵심 포인트:
- Android는 JDK 21 기준
- iOS(Xcode Script Phase)에서 Java 경로 인식 필요
- 로컬/빌드 산출물은 커밋 금지
- Android Studio, Xcode 각각 실행 순서 준수

---

## 설정 1) JDK 21 설치 및 기본 Java 확인

### 왜 필요한가
현재 프로젝트는 Android 빌드 설정이 Java/Kotlin 21 기준입니다.
- `sourceCompatibility = JavaVersion.VERSION_21`
- `targetCompatibility = JavaVersion.VERSION_21`
- `kotlin { jvmToolchain(21) }`

JDK 버전이 다르면 Gradle sync/빌드가 실패할 수 있습니다.

### 확인 명령어
```bash
java -version
/usr/libexec/java_home -V
```

### 정상 예시
- Java 21.x 출력
- `java_home -V` 목록에 21 포함

### 문제 시
- `Unable to locate a Java Runtime`가 뜨면 JDK 설치가 안 된 상태
- Android Studio 내장 JBR만 의존하지 말고 시스템 JDK 21도 설치 권장

---

## 설정 2) Android Studio Gradle/JDK 설정

### 경로
`Android Studio > Settings > Build, Execution, Deployment > Build Tools > Gradle`

### 반드시 확인할 항목
1. `Gradle user home`
- 권장: `/Users/<your-user>/.gradle`

2. `Gradle projects > Distribution`
- `Wrapper` 선택

3. `Gradle JVM`
- Java 21 지정
- 표시가 `Version 21` 또는 설치된 JDK 21 경로여야 함

4. Sync
- 설정 후 `Sync Project with Gradle Files` 실행

### 터미널 검증
프로젝트 루트(`apps/frontend`)에서:
```bash
./gradlew -version
```

출력에서 JVM이 21로 보이면 정상입니다.

---

## 설정 3) iOS(Xcode) Script Phase의 Java 인식

### 왜 필요한가
KMM framework embed 스크립트가 Gradle을 호출할 때 Java를 필요로 합니다.

대표 오류:
- `Unable to locate a Java Runtime`
- `JAVA_HOME is set to an invalid directory`

### Xcode 설정 위치
`Target CashChatIOS > Build Phases > Embed Shared Framework`

### 권장 스크립트 패턴
하드코딩 경로 대신 동적 탐색 방식 권장:
```bash
cd "$SRCROOT/.."

export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH="$JAVA_HOME/bin:$PATH"

bash ./gradlew :shared:embedAndSignAppleFrameworkForXcode
```

### 주의
- 경로 오타 주의: `Android Studio.app` (공백 포함)
- 가능하면 `java_home -v 21` 방식 사용 (머신별 경로 차이 흡수)

---

## 설정 4) 커밋/풀 충돌 방지 규칙 (중요)

### 4-1. 커밋 금지 대상
다음은 로컬/빌드 산출물입니다.
- `.gradle-local/`
- `shared/build/`
- `build/`
- `local.properties`
- IDE 개인 설정(`.idea`, `*.iml`) 등

### 4-2. 중첩 Git 저장소 금지
`apps/frontend/CashChatIOS/.git` 같은 내장 git 폴더는 상위 repo에서 문제를 일으킬 수 있습니다.
- 의도적 submodule이 아니면 제거 후 작업

### 4-3. gradlew 실행권한
`gradlew`는 실행권한(755)이 필요합니다.
문제 시:
```bash
chmod +x gradlew
```

### 4-4. Script Phase 경고
경고 메시지:
- `Run script build phase 'Embed Shared Framework' will be run during every build ...`

이건 에러가 아닌 경고입니다.
해결 옵션:
1. `Based on dependency analysis` 체크 해제
2. Output Files 지정

---

## Android Studio 실행 절차 (Step-by-step)

1. 프로젝트 열기
- `apps/frontend` 폴더를 Android Studio로 Open

2. Gradle 설정 확인
- 위 `설정 2`의 3개 항목 확인(Wrapper/JVM21)

3. Gradle sync
- 상단 Sync 버튼 실행

4. Run Configuration 확인
- 상단 런 구성에서 `app` 모듈 선택
- `Error: Module not specified`가 뜨면 Edit Configuration에서 module=`app` 지정

5. 에뮬레이터/디바이스 선택 후 Run
- 실행 버튼 클릭

6. 실패 시 기본 점검
```bash
./gradlew clean
./gradlew :app:assembleDebug
```

---

## Xcode(iOS) 실행 절차 (Step-by-step)

1. 프로젝트 열기
- `apps/frontend/CashChatIOS/CashChatIOS.xcodeproj` 열기

2. Signing/Target 확인
- Target: `CashChatIOS`
- Team, Bundle Identifier 확인

3. Build Phases 확인
- `Embed Shared Framework` 스크립트가 최신인지 확인
- `JAVA_HOME` 동적 탐색 방식 적용 여부 확인

4. 시뮬레이터 선택
- iPhone 시뮬레이터 선택 (예: 최신 iPhone)

5. Build/Run
- `⌘B` (빌드)
- `⌘R` (실행)

6. 자주 발생하는 오류 대응
- `JAVA_HOME invalid`: Script Phase 경로 재확인
- `Unable to locate Java`: JDK 21 설치/인식 확인
- `gradlew permission denied`: `chmod +x ../gradlew`

---

## 트러블슈팅 빠른 체크리스트

1. `java -version`이 21인가?
2. `./gradlew -version`이 정상인가?
3. Android Studio Gradle JVM이 21인가?
4. Xcode Script Phase에서 `JAVA_HOME=$( /usr/libexec/java_home -v 21 )` 쓰는가?
5. `shared/build` 같은 산출물을 커밋하지 않았는가?
6. `CashChatIOS/.git` 같은 중첩 git이 없는가?

---

## 권장 팀 공지 문구 (복붙용)

```text
이번 프론트엔드 변경으로 Android 빌드는 JDK 21 기준입니다.
각자 Android Studio Gradle JVM을 21로 맞춰주세요.

iOS는 Xcode Build Phases > Embed Shared Framework에서 JAVA_HOME을
/usr/libexec/java_home -v 21 기반으로 설정해야 빌드 에러를 피할 수 있습니다.

로컬/빌드 산출물(.gradle-local, shared/build, local.properties 등)은 커밋하지 말아주세요.
중첩 git(CashChatIOS/.git)도 금지입니다.
```

