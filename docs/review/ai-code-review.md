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
| `/help` | 명령어 도움말 표시 | PR 코멘트 |
| `@coderabbitai review` | CodeRabbit 리뷰 (수동 · 비용 발생) | PR 코멘트 |

> 명령어는 코멘트(또는 줄) **맨 앞**에 입력해야 인식됩니다. 문장 중간의 `/ask` 같은 언급은
> 트리거되지 않습니다. 권한: OWNER/MEMBER/COLLABORATOR 만 명령어를 사용할 수 있습니다.

## 2. 명령어 상세

### 자동 리뷰 (PR을 열 때 1회)
PR을 `dev` 로 열면 Gemini 가 자동으로 라인별 리뷰 + 코드 개선 제안을 1회 게시합니다.
이때는 **공통 키**를 사용합니다. 이후 커밋을 푸시해도 **전체 재리뷰는 자동으로 돌지 않고**,
이미 해결된 리뷰 코멘트 스레드만 자동으로 정리(리졸브)합니다.

### `/gemini-review`
다시 리뷰가 필요할 때 PR 코멘트로 입력합니다. 호출: `/review` + `/improve` 2회분.
요청자 **개인 키**로 동작하며, 미등록 사용자는 공통 키로 폴백합니다.

### `/openai-review`
OpenAI 모델로 더 깊은 리뷰가 필요할 때 사용합니다. **항상 비용이 발생**합니다.
호출: `/review` + `/improve`. 요청자 개인 키(미등록 시 공통 키).

### `/ask 질문내용`
AI 리뷰 코멘트(라인/PR)에 답글로 후속 질문을 합니다. 따옴표는 필요 없습니다.
저비용 모델(model_weak)을 사용합니다. 예: `/ask 이 함수가 null 일 때 어떻게 되나요?`

### `/resolve`
리뷰 코멘트 **답글**로 입력하면, AI가 해당 지적이 반영/처리됐는지 판단합니다.
타당하면 추후 처리 항목을 **Jira 서브태스크**로 등록하고 스레드를 리졸브합니다.
사유를 덧붙일 수 있습니다: `/resolve 다음 PR에서 일괄 처리`.

### `/help`
사용 가능한 명령어 도움말 카드를 PR 코멘트로 게시합니다. (AI 호출 없음)

### `@coderabbitai review`
CodeRabbit 앱으로 리뷰를 요청합니다. (수동 · 비용 발생)

## 3. 리뷰 결과 읽는 법

- **PR 리뷰 가이드**: 난이도, 중점 리뷰 영역, 보안/테스트 관찰 사항 요약.
- **PR 코드 개선 제안**: 라인에 바로 커밋 가능한 AS-IS/TO-BE 제안.
- **인라인 코멘트**: 변경 라인에 붙는 구체 지적. Conventional Comments 접두사로 분류됩니다.
  - `[Suggestion]` 개선 제안 / `[Nitpick]` 사소(머지 무방) / `[Question]` 의도 질문 /
    `[Compliment]` 좋은 코드 칭찬.

## 4. 비용 & 키 전략

- **자동 리뷰는 공통 키로 1회만** — 무료 등급 일일 호출 한도(RPD)를 아끼기 위함입니다.
- **명령어 리뷰는 요청자 개인 키** — 본인 할당량을 쓰고, 미등록자는 공통 키로 폴백합니다.
  개인 키가 등록된 사용자: `gudals-kim`, `seedplan005`/`jwchoi42`, `jeonj95`/`unistuj`.
- **OpenAI 는 항상 비용 발생** — 꼭 필요한 PR 에만 `/openai-review` 를 사용하세요.
- 사용량이 소진되면 봇이 "오늘의 AI 리뷰 사용량을 모두 사용했어요" 카드를 띄웁니다.
  한도 회복 후 다시 명령어로 요청하세요.

## 5. FAQ / 트러블슈팅

- **푸시했는데 리뷰가 다시 안 돌아요** — 정상입니다. 자동 재리뷰는 없고, 필요하면
  `/gemini-review` 로 요청하세요.
- **명령어가 안 먹어요** — 코멘트 맨 앞에 입력했는지, 권한(OWNER/MEMBER/COLLABORATOR)이
  있는지 확인하세요.
- **리뷰가 실패했어요** — 봇이 실패/사용량 카드를 띄웁니다. 잠시 후 같은 명령어로 재시도하세요.

## 6. 참고 (구현 파일)

- 워크플로: `.github/workflows/pr-review.yml`, `.github/workflows/pr-description.yml`
- 공유 스크립트: `.github/scripts/review/` (`lib_cards.sh`, `lib_cmd.sh`, `lib_help.sh`,
  `lib_comments.sh`, `lib_ai.sh`, `lib_keys.sh`, `resolve_*.sh`, `run_pr_agent.sh`)
- 공통 설정: `.pr_agent.toml`
