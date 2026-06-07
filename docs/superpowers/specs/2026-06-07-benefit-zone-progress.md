# 혜택존(Benefit Zone) Phase F+1 진행 로그

## Task 0: 의존성 추가
- 상태: ✅ 완료
- 변경 파일:
  - `apps/frontend/gradle/libs.versions.toml`
  - `apps/frontend/shared/build.gradle.kts`
- 검증:
  - `./gradlew :shared:dependencies --configuration commonMainImplementation -q | grep -i ktor` → `io.ktor:ktor-client-auth:2.3.12 (n)` 확인됨
  - `./gradlew :shared:dependencies --configuration commonTestImplementation -q | grep -iE "ktor|coroutines-test|kotlin-test"` → `kotlin-test`, `ktor-client-mock:2.3.12`, `kotlinx-coroutines-test:1.9.0` 모두 확인됨 (coroutines-core와 동일 버전 1.9.0)
  - `./gradlew :shared:compileKotlinMetadata -q` → 성공 (출력 없음, 에러 없음)
- 인계 메모:
  - 기존 coroutines 버전 키 이름은 `kotlinxCoroutines` (값 `1.9.0`)이며, 일반적인 `coroutines` 키는 toml에 없음. 플랜 문서의 `coroutines = "1.8.1"` 안내는 이 저장소 상황과 다르므로 **새 키를 만들지 않고 기존 `kotlinxCoroutines` ref를 재사용**함 — `kotlinx-coroutines-test`도 `version.ref = "kotlinxCoroutines"`로 선언됨.
  - ktor 버전 키 이름은 `ktor` (값 `2.3.12`).
  - `commonTest` 블록은 기존 파일 스타일(`commonMain.dependencies {}` 축약형)에 맞춰 `commonTest.dependencies {}`로 추가함.
