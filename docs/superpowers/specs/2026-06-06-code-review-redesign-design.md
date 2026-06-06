# 코드리뷰 시스템 재설계 — 설계 문서

- 작성일: 2026-06-06
- 대상: `.github/workflows/pr-review.yml`, `pr-description.yml`, `android-build-check.yml`, `ios-build-check.yml`
- 관련 PR: https://github.com/cash-chat-mvp/cash-chat-mvp/pull/173

## 1. 배경 / 문제 정의

현행 AI 코드리뷰 파이프라인은 [pr-review.yml](../../../.github/workflows/pr-review.yml) **1055줄 모놀리식**에
동일 bash 함수(`cleanup_notices`, `notify_failure`, `localize_comments`, `recommend_deep_review`,
스레드 리졸브 로직)가 **5개 잡에 그대로 복붙**되어 있다. 이것이 버그·불안정성·유지보수난의 근본 원인이다.

PR #173 job 로그 분석으로 확인한 구체적 문제:

| # | 증상 | 근본 원인 |
|---|------|-----------|
| 1 | AI 기능이 MR당 한 번꼴로 실패, 원인 추적 어려움 | 무료 Gemini 등급 RPM/RPD 한도 + **워크플로 레벨 재시도/백오프 없음** + open 시 description·auto-review가 **같은 키를 연사** |
| 2 | "중점 리뷰"만 나오고 변경 라인별 리뷰가 안 됨 | 자동 리뷰가 `AUTO_REVIEW=true, AUTO_IMPROVE=false` — `/review`만 돌고 CodeRabbit식 라인별 커밋제안(`/improve`)이 꺼져 있음. effort 낮으면 key_issue 1개만 나옴 |
| 3 | Android/iOS 빌드가 무관 변경에도 계속 돎 | 잡이 항상 생성되어 체크로 표시됨(빠른 스킵 후 green). 빌드 트리거 기준이 라벨 로직과 불일치 |

`dev` 브랜치의 **필수 상태 체크는 `CodeRabbit` 하나뿐** — Android/iOS 빌드와 Gemini/OpenAI 리뷰는
필수 체크가 아니므로, 빌드 잡을 **Skipped**로 만들어도 머지에 영향이 없다.

## 2. 목표

- 안정적이고 추적 가능한 AI 리뷰 (무료 Gemini 등급 유지).
- 변경 라인에 밀착한 라인별 리뷰 복원.
- 빌드 영향 파일이 바뀌었을 때만 Android/iOS 빌드 실행.
- 5중 복붙 제거 → 공유 스크립트화.

비목표(YAGNI): 유료 키 도입, pr-agent 외 리뷰 엔진 교체, CodeRabbit 대체.

## 3. 전체 플로우 (재정의)

| # | 트리거 | 동작 | 키 | 모델 | 도구 |
|---|--------|------|----|------|------|
| 0 | PR `opened`만 | PR 본문 description 채움 | **공통 키** | **model1** | pr-description.yml |
| 1 | PR `opened`만 | **전체 라인별 리뷰** (리뷰 가이드 + key_issues 인라인 + `/improve` 커밋제안) | **공통 키** | model0 | pr-agent |
| 2 | `synchronize` (push) | **자동 리뷰 안 함.** 변경 라인 확인 → 미해결 스레드 판단: 해결됐으면 코멘트+resolve, 미해결/파생이슈면 코멘트만 | **작성자 키** | model1 | 순수 스크립트 (Gemini API) |
| 3 | `/gemini-review` · `/openai-review` 코멘트 | #1과 동일한 전체 리뷰 | **작성자 키** | model0 | pr-agent |
| 4 | `/resolve "사유"` 코멘트 | AI가 사유+원본 코멘트+diff를 읽고 resolve 타당성 판단 → 코멘트, 타당하면 resolve **+ Jira 서브태스크 생성** | **작성자 키** | model1 | 스크립트 |

### 키/모델 규약
- **공통 키** = `secrets.GOOGLE_GEMINI_API_KEY`. **작성자 키** = `secrets.GEMINI_KEY_{SUFFIX}` (미매핑 시 공통 키 폴백).
- **model0** = 리뷰/개선용 1순위(`resolve-gemini-model` 출력). **model1** = 대화/판단/describe용 2순위.
- OpenAI(#3 `/openai-review`)는 `resolve-openai-model` + 작성자 OpenAI 키 경로 유지.

### 트리거 변경 핵심
- 자동 전체 리뷰(#1) 트리거: 현행 `[opened, reopened, synchronize]` → **`[opened]`만**.
- `synchronize`(push)는 **#2 경로로 분리** — pr-agent docker를 **쓰지 않고** 순수 스크립트로 스레드 판단만 수행.
- `reopened`는 전체 리뷰에서 제외(spec "첫 open 시에만"). 필요 시 `/gemini-review`로 수동 재요청.

## 4. 코드 구조 — 공유 스크립트 추출

```
.github/scripts/review/
  lib_comments.sh    # cleanup_notices, notify_failure, post/clear progress, localize_comments
  lib_keys.sh        # PR 작성자/코멘터 login → 키 SUFFIX 매핑 (단일 소스)
  lib_ai.sh          # Gemini/OpenAI HTTP 호출 래퍼 (지수 백오프 재시도 내장)
  run_pr_agent.sh    # docker pr-agent 실행 + 하드 실패 마커 판정 + 재시도(멱등)
  resolve_threads.sh # 변경 라인 기반 미해결 스레드 판단 (auto-resolve, #2에서 사용)
  resolve_command.sh # /resolve 사유 AI 판단 + Jira 서브태스크 (#4)
```

- 워크플로 잡은 스텝에서 `source` 후 함수 호출만 한다. 한 곳을 고치면 전 잡에 반영.
- 각 스크립트는 환경변수 입력(`GITHUB_TOKEN`, `PR_NUMBER`, `GEMINI_KEY`, `MODEL0/1` 등)으로 동작하여
  잡 간 결합도를 낮춘다.
- 한국어 라벨 치환 sed 맵(`localize_comments`)은 `lib_comments.sh` 한 곳에만 둔다.

## 5. 안정성 설계 (무료 유지 + 큐잉/백오프)

1. **키 분리로 경합 제거**: open 시 description(#0, 공통 키 model1)과 auto-review(#1, 공통 키 model0)는
   같은 공통 키를 쓰되 **모델이 다르고**(model1 vs model0) **호출 시점을 분리**한다.
   → description 잡과 review 잡 사이에 `needs:` 의존 또는 짧은 지연을 두어 같은 키 동시 연사를 피한다.
   *(현행은 동일 키·동일 시점 연사가 RPM 충돌의 주범이었다.)*
2. **concurrency group**: 리뷰 계열 잡에 `group: review-${{ github.event.*.number }}` +
   `cancel-in-progress: true` — 같은 PR 중복 실행 직렬화.
3. **지수 백오프 재시도**(`lib_ai.sh`): 모든 Gemini/OpenAI HTTP 호출을 래퍼로 감싸
   429/503/`RESOURCE_EXHAUSTED` 시 20s → 40s, 최대 2회 재시도. 그래도 실패면 한국어 안내 코멘트.
4. **docker pr-agent 재시도**(`run_pr_agent.sh`): **하드 실패 마커**(리뷰 코멘트 미게시 = `PR_AGENT_FAIL_MARKERS`)
   감지 시에만 1회 재실행. 코멘트가 이미 게시된 경우 재시도하지 않아 **중복 코멘트 방지**(멱등).
5. **실패 가시성**: 전 모델 실패 시 영문 실패 코멘트 삭제 → 한국어 안내(사용량 소진/일시 오류 구분) +
   job 빨간 X 유지(현행 동작 보존).
6. **호출량 절감**: push(#2)에서 자동 전체 리뷰 제거 → docker AI 연속 호출 자체가 사라져
   누적 호출이 감소(enable `/improve`로 늘어난 open당 호출을 상쇄).

## 6. Flow #2 상세 (push 시 스레드 판단)

- 트리거: `pull_request_target` `synchronize`.
- pr-agent를 쓰지 않고 `resolve_threads.sh`만 실행:
  1. 이번 push 변경 파일(`before...head`)과 patch 조회.
  2. GraphQL로 **미해결** 리뷰 스레드 조회.
  3. 변경 파일에 걸린 스레드만 대상으로, model1(작성자 키)에게
     "이 코멘트가 이 diff로 해결됐는가?(yes/no + 사유)" 질의(백오프 재시도 포함).
  4. yes → 스레드에 판단 근거 답글 + `resolveReviewThread`.
     no/파생이슈 → 해당 내용 코멘트만, resolve하지 않음.
- 이번 run에서 새로 생성된 스레드는 대상 제외(현행 `PRE_RUN_THREAD_IDS` 스냅샷 개념 유지).

## 7. Flow #4 상세 (`/resolve "사유"`)

- 트리거: `pull_request_review_comment` `created`, 본문이 `/resolve`로 시작, 작성자 권한 OWNER/MEMBER/COLLABORATOR.
- `resolve_command.sh`:
  1. 사유 파싱 + 루트(원본) 리뷰 코멘트 본문/파일 경로 조회.
  2. **AI 판단(model1, 작성자 키)**: 사유 + 원본 코멘트 + 해당 파일 현재 diff를 주고
     "이 사유로 resolve가 타당한가?(yes/no + 근거)" 질의.
  3. **타당(yes)**: 판단 근거 답글 → Jira 상위 티켓(CC-###) 하위 **서브태스크 생성**(원본+사유+PR 링크) →
     스레드 resolve. *(추적 누락 방지 위해 서브태스크 생성 성공 시에만 resolve.)*
  4. **부당(no)**: "이런 이유로 아직 resolve하기 이르다"는 코멘트만, resolve하지 않음.

## 8. 빌드 체크 재설계 (문제 3)

"빌드 영향 파일"을 기준으로 통일한다. 라벨 글로브(`app/**`, `CashChatIOS/**`)보다 넓게,
**KMM 공통(`shared/`)과 Gradle 설정 파일까지 포함**한다 — 이들만 바뀌어도 실제 빌드 결과가 달라지기 때문.

- **빌드 영향 글로브**:
  - Android: `apps/frontend/app/**`, `apps/frontend/shared/**`,
    `apps/frontend/**/*.gradle.kts`, `apps/frontend/gradle/**`
  - iOS: `apps/frontend/CashChatIOS/**`, `apps/frontend/shared/**`,
    `apps/frontend/**/*.gradle.kts`, `apps/frontend/gradle/**`
    *(iOS는 `shared` KMM 프레임워크를 Gradle로 빌드·임베드하므로 shared·gradle 변경에 영향받음.)*
- **구조**: 경량 `detect` 잡(ubuntu, ≈5s)이 변경 파일을 판정해 `android`/`ios` boolean 출력 →
  무거운 빌드 잡은 `needs: detect` + `if: needs.detect.outputs.android == 'true'`.
- **판정 범위**:
  - `opened`/`reopened`: **full diff(base...head)** 를 위 글로브로 판정.
  - `synchronize`: **이번 push diff(before...head)** 만 위 글로브로 판정 → 빌드 영향 파일이 이번에 바뀌었을 때만 실행.
- 무관한 PR/푸시는 빌드 잡이 **Skipped**(러너 안 뜸). 필수 체크가 아니므로 머지 영향 없음.

> 참고: 빌드 영향 글로브는 라벨 글로브(`.github/labeler.yml`의 `app/**` / `CashChatIOS/**`)보다 넓다.
> 라벨은 "변경 영역 표시" 목적, 빌드 판정은 "빌드 결과에 영향" 목적으로 의도적으로 분리한다.

## 9. 비용 영향 (명시)

- **#1/#3에 `/improve` 추가**: MR open(또는 수동 리뷰)당 Gemini 호출이 **리뷰 1회 + 개선제안 1회 ≈ 2배**.
  무료 RPD 소진이 빨라진다. 단 push(#2)에서 자동 전체 리뷰를 제거하므로 **PR 생애 누적 호출은 감소** 예상.
- **#4 /resolve AI 판단**: `/resolve`당 model1 호출 1회 추가(경량).
- **#2 push 스레드 판단**: 미해결 스레드 수만큼 model1 호출(스레드당 1회, 백오프 포함). 변경 파일에 걸린 스레드로 한정.

## 10. 예상 이슈 / 리스크

- **pr-agent 엔트리포인트**: 이미지가 CLI 인자를 무시(고정 러너)하므로 #1 전체 리뷰는
  `GITHUB_ACTION_CONFIG.AUTO_REVIEW/AUTO_IMPROVE` env로 제어(합성 이벤트 불필요).
- **재시도 멱등성**: 리뷰 코멘트 게시 후 재시도하면 중복 → "하드 실패(미게시) 마커"에서만 재시도.
- **공통 키 동시성**: #0(model1)·#1(model0)이 같은 공통 키 → 모델 분리 + 시점 분리로 RPM 충돌 완화.
  필요 시 #1을 `needs: [#0 잡]`으로 직렬화.
- **라벨/빌드 글로브 차이**: 빌드 판정 글로브는 라벨보다 넓다(`shared/`·gradle 포함). 의도된 분리(8절).
- **권한 가드 유지**: 모든 명령(#3·#4·/ask)은 `author_association ∈ {OWNER,MEMBER,COLLABORATOR}` +
  `user.type != Bot` 가드 유지(봇 루프·외부인 트리거 방지).

## 11. 변경 대상 파일

- `.github/workflows/pr-review.yml` — 모놀리식 분해, 트리거/잡 재구성, 스크립트 호출로 전환.
- `.github/workflows/pr-description.yml` — #0 공통 키/model1로 조정, 트리거 `opened`만 확인.
- `.github/workflows/android-build-check.yml` — detect+gate 구조, 라벨 글로브 판정.
- `.github/workflows/ios-build-check.yml` — 동일.
- `.github/scripts/review/*.sh` — 신규 공유 스크립트.
- `.pr_agent.toml` — `pr_code_suggestions`(`commitable_code_suggestions`) 유지 확인, `num_max_findings=7` 유지.

## 12. 검증 계획

- 테스트 PR을 `dev` 대상으로 열어 시나리오별 확인:
  - open: #0 본문 채움 + #1 라인별 리뷰(가이드+인라인+커밋제안) 게시.
  - push(빌드 영향 파일 X, 예: 문서만): 자동 리뷰 안 돎, 빌드 Skipped, 미해결 스레드 판단만.
  - push(`app/**` 변경): Android 빌드만 실행.
  - push(`CashChatIOS/**` 변경): iOS 빌드만 실행.
  - push(`shared/**` 또는 `*.gradle.kts` 변경): Android·iOS **둘 다** 실행.
  - `/gemini-review`·`/openai-review`: 전체 리뷰 재게시.
  - `/resolve "사유"`: AI 판단 → resolve+서브태스크 or 코멘트만.
- 각 AI 호출 실패 시 한국어 안내 코멘트 + job 실패 표시 확인.
- 동시/연속 실행 시 중복 코멘트 없음(멱등) 확인.
