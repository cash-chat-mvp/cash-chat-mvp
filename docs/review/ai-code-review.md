# AI 코드 리뷰 사용 가이드

<!-- 동기화 유지: 명령어 표는 .github/PULL_REQUEST_TEMPLATE.md / pr-description.yml / lib_help.sh 와 동일 -->

이 저장소는 pr-agent(Qodo) 기반 AI 코드 리뷰를 GitHub Actions 로 제공합니다. 리뷰는
Gemini(자동·무료 등급)와 OpenAI(수동·유료), 그리고 CodeRabbit 을 사용할 수 있습니다.

## 1. 한눈에 보기

| 명령어 | 설명 | 사용 위치 |
|---|---|---|
| `/gemini-review` | Gemini 코드 리뷰 (PR을 열면 자동 1회 실행, 재요청 시 입력) | PR 코멘트 |
| `/openai-review` | OpenAI 심층 리뷰 (수동 · 비용 발생) | PR 코멘트 |
| `/ask 질문내용` | AI 답변/코드에 후속 질문 (저비용 모델) | PR · 라인 코멘트 |
| `/resolve` | AI가 반영 여부 판단 → Jira 서브태스크 생성 + 스레드 해결 | 라인 코멘트 답글 |
| `/help` | 명령어 도움말 표시 (오늘 사용량 표 포함) | PR 코멘트 |
| `@coderabbitai review` | CodeRabbit 리뷰 (수동 · 비용 발생) | PR 코멘트 |

> 명령어는 코멘트(또는 줄) **맨 앞**에 입력해야 인식됩니다. 문장 중간의 `/ask` 같은 언급은
> 트리거되지 않습니다. 권한: OWNER/MEMBER/COLLABORATOR 만 명령어를 사용할 수 있습니다.

## 2. 동작 흐름 (라이프사이클)

`dev` 대상 PR에서 이벤트별로 다음이 자동 실행됩니다.

### PR을 열 때 (opened)
1. **자동 리뷰 1회** — 라인별 리뷰(`/review`, 강한 모델 `model0`) + 코드 개선 제안(`/improve`, 약한 모델 `model1`)
2. **PR 설명 자동 채움** — 제목 정규화([CC-###]) + 실제 diff 기반 **AI 요약**(한 줄 요약 / 주요 변경 / 왜 / 리뷰 포커스)

### 푸시할 때 (synchronize) — 매 푸시마다
1. **스레드 자동 정리** — 변경된 라인에 걸린 미해결 리뷰 스레드를 AI(`model1`)가 판단해,
   해결됐으면 근거 답글 + **리졸브**(실패 시 "수동 리졸브 필요" 안내), 아니면 보류.
2. **라인별 코드 제안(`/improve`, `model1`)** — 새 변경에 대한 개선 제안을 추가.

> 즉, 예전과 달리 **푸시할 때도 코드 라인 리뷰(improve)가 자동으로 돕니다.** 전체 리뷰(`/review`)는
> PR을 열 때만 자동 1회이며, 다시 받고 싶으면 `/gemini-review` 로 요청하세요.

### 명령어를 입력할 때
`/gemini-review`, `/openai-review`, `/ask`, `/resolve`, `/help` 를 코멘트로 트리거. 상세는 3장 참고.

## 3. 명령어 상세

### `/gemini-review`
다시 전체 리뷰가 필요할 때 PR 코멘트로 입력합니다. 호출: `/review` + `/improve`.

### `/openai-review`
OpenAI 모델로 더 깊은 리뷰가 필요할 때 사용합니다. **항상 비용이 발생**합니다.
호출: `/review` + `/improve`.

### `/ask 질문내용`
AI 리뷰 코멘트(라인/PR)에 답글로 후속 질문을 합니다. 따옴표는 필요 없습니다.
저비용 모델(`model1`)을 사용합니다. 예: `/ask 이 함수가 null 일 때 어떻게 되나요?`

### `/resolve`
리뷰 코멘트 **답글**로 입력하면, AI가 해당 지적이 반영/처리됐는지 판단합니다.
타당하면 추후 처리 항목을 **Jira 서브태스크**로 등록하고 스레드 **리졸브를 시도**합니다.
사유를 덧붙일 수 있습니다: `/resolve 다음 PR에서 일괄 처리`.

> 리졸브는 실제 성공 여부를 **검증**합니다. 봇 권한 등으로 리졸브에 실패하면 거짓 성공 대신
> "수동으로 Resolve conversation 을 눌러 주세요" 안내를 남기고, 자세한 원인은 워크플로 로그에 남깁니다.

### `/help`
사용 가능한 명령어 도움말 카드 + **오늘(KST) 사용량 표**를 PR 코멘트로 게시합니다. (AI 호출 없음)

### `@coderabbitai review`
CodeRabbit 앱으로 리뷰를 요청합니다. (수동 · 비용 발생)

## 4. 키 전략 & 429 폴백

**기본은 모두 "개인 키"** 로 동작하고, 한도(429/RPD 소진) 도달 시에만 **공통 키로 자동 폴백**합니다.

- **자동 리뷰·푸시 리뷰·describe**: PR **작성자** 개인 키 → 실패 시 공통 키
- **명령어(`/gemini-review`·`/ask`·`/resolve`)**: **요청자** 개인 키 → 실패 시 공통 키
- **미등록 사용자**: 처음부터 공통 키 사용
- 개인 키 등록 사용자: `gudals-kim`, `seedplan005`/`jwchoi42`, `jeonj95`/`unistuj`
  (매핑은 [`lib_keys.sh`](../../.github/scripts/review/lib_keys.sh))

폴백 동작:
- **직접 호출 경로(resolve/ask 판단)**: rate-limit이면 즉시 공통 키로, 일시 오류면 개인 키
  백오프 재시도 후 공통 키. (`ai_generate` in `lib_ai.sh`)
- **pr-agent 경로(review/improve)**: 컨테이너 내부 호출이라 중간에 키 교체가 불가 →
  quota 감지 시 **공통 키로 전체 1회 재실행**. (`run_pr_agent_fallback` in `run_pr_agent.sh`)

이 전략 덕분에 서로 다른 PR은 각자 개인 키로 **병렬** 처리되어 공통 키 한도를 아끼고,
개인 키가 소진된 경우에만 공통 키가 쓰입니다.

## 5. 사용량(RPD) 확인

무료 등급은 "남은 RPD 조회 API"가 없어, 시스템이 **트리거한 리뷰 연산 횟수를 직접 집계**합니다
(KST 일자별, 자정 초기화). `/help` 하단에 모델별 **공통 키 / 내 키 오늘 사용 횟수**가 표시됩니다.

| 모델 | 용도 | 일일 한도(RPD, 키당) |
|---|---|---|
| `model0` | 전체 리뷰(`/review`) | 20 |
| `model1` | 개선·대화(`/improve`·`/ask`·resolve·요약) | 500 |

> ⚠️ pr-agent 내부의 다중 Gemini 호출은 측정 불가하므로 표시값은 **추정치**입니다.
> 실제 소비된 호출 수와 다를 수 있습니다.

## 6. 안정성: 동시성

같은 Gemini 키를 동시에 호출하면 RPM 초과가 발생할 수 있어, **PR별로 직렬화**합니다.

- 같은 PR의 자동 리뷰·요약·명령은 한 그룹(`gemini-pr-{번호}`)으로 묶여 **순차** 실행
  (pr-review / pr-description 워크플로가 그룹 공유) → 같은 개인 키 동시 호출 방지
- 서로 다른 PR은 각자 개인 키로 **병렬** 실행 → 빠르고, 실행 유실 없음

## 7. 리뷰 결과 읽는 법

- **PR 리뷰 가이드**: 난이도, 중점 리뷰 영역, 보안/테스트 관찰 사항 요약.
- **PR 코드 개선 제안**: 라인에 바로 커밋 가능한 AS-IS/TO-BE 제안.
- **인라인 코멘트**: 변경 라인에 붙는 구체 지적. Conventional Comments 접두사로 분류됩니다.
  - `[Suggestion]` 개선 제안 / `[Nitpick]` 사소(머지 무방) / `[Question]` 의도 질문 /
    `[Compliment]` 좋은 코드 칭찬.

## 8. 비용

- **호출 총량은 이전과 비슷하나 부하가 개인 키로 분산** — 공통 키 RPD를 아낍니다.
- **푸시마다 `/improve` 가 추가로 실행** — push가 잦으면 작성자 키 `model1` 소모가 늘 수 있습니다.
- **429 폴백 시 pr-agent 는 전체 재실행** — 그 경우에만 호출이 약 2배(드묾).
- **OpenAI 는 항상 비용 발생** — 꼭 필요한 PR 에만 `/openai-review` 를 사용하세요.
- 사용량이 소진되면 봇이 "오늘의 AI 리뷰 사용량을 모두 사용했어요" 카드를 띄웁니다.

## 9. FAQ / 트러블슈팅

- **푸시하면 리뷰가 도나요?** — 네. 푸시마다 해결된 스레드 자동 정리 + 라인별 개선 제안(`/improve`)이
  돕니다. 전체 리뷰가 다시 필요하면 `/gemini-review` 로 요청하세요.
- **자동 리졸브 코멘트는 달렸는데 스레드가 안 닫혀요** — 봇 권한/일시 오류로 리졸브가 실패한
  경우입니다. "수동 리졸브 필요" 안내가 함께 달리며, 원인은 해당 실행 로그에서 확인할 수 있습니다.
- **명령어가 안 먹어요** — 코멘트 맨 앞에 입력했는지, 권한(OWNER/MEMBER/COLLABORATOR)이
  있는지 확인하세요.
- **리뷰가 실패했어요** — 봇이 실패/사용량 카드를 띄웁니다. 개인 키가 소진된 경우 공통 키로
  자동 폴백하며, 그래도 실패하면 잠시 후 같은 명령어로 재시도하세요.
- **내 사용량이 궁금해요** — `/help` 하단 표에서 오늘(KST) 공통 키/내 키 사용량을 확인하세요.

## 10. 참고 (구현 파일)

- 워크플로: `.github/workflows/pr-review.yml`, `.github/workflows/pr-description.yml`
- 스크립트 테스트 CI: `.github/workflows/review-scripts-test.yml`
- 공유 스크립트: `.github/scripts/review/` — `lib_cards.sh`, `lib_cmd.sh`, `lib_help.sh`,
  `lib_comments.sh`, `lib_ai.sh`(`ai_generate`), `lib_keys.sh`, `lib_gh.sh`(`gh_resolve_thread`),
  `lib_rpd.sh`(사용량 카운터), `resolve_*.sh`, `run_pr_agent.sh`(`run_pr_agent_fallback`)
- RPD 카운터 액션: `.github/actions/rpd/`
- 단위 테스트: `.github/scripts/review/tests/`
- 공통 설정: `.pr_agent.toml`
