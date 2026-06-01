# 코드 리뷰 시스템 개편 설계 (CodeRabbit 스타일 + 폴백 제거 + 모델 분리)

- 작성일: 2026-05-31
- 대상 파일: `.github/workflows/pr-review.yml`, `.pr_agent.toml`
- 작업 브랜치: `feature/CC-326`
- 목적: 기존 Qodo(pr-agent) 기반 리뷰 시스템을 (1) CodeRabbit 유사 경험으로 개편, (2) 모델 폴백 제거 및 사용량 소진 플로우 정비, (3) 도구별 모델 분리로 비용 최적화.

## 배경 / 공유 정보 검증 결과

사용자가 공유한 "CodeRabbit 스타일 재현 가이드"를 pr-agent 소스(`qodo-ai/pr-agent`)로 대조 검증함.

**존재하지 않는 키 (적용 불가 → 제외):**
- `fallback_model` (단수) — 실제는 `fallback_models`(리스트). 본 작업에서는 폴백 자체를 제거하므로 불필요.
- `max_inline_code_comments` — 존재하지 않음.
- `ask_and_reflect` — 존재하지 않음.
- `[github_action_config] handle_mentions` — 존재하지 않음. `@봇` 멘션 자동응답은 Qodo 호스팅 전용 기능이며, 셀프호스팅 GitHub Action은 코멘트의 명시적 `/명령어`만 처리.

**유효 (채택):**
- `model`, `pr_reviewer.inline_code_comments`, `extra_instructions`(페르소나 프롬프트), `[ignore]` 글롭, 네거티브 프롬프트.
- `pr_code_suggestions.commitable_code_suggestions = true` — 코드 줄에 직접 인라인 제안(코드레빗 라인 코멘트 재현의 진짜 키).
- `config.model`(REGULAR) vs `config.model_weak`(WEAK) 분리 — 도구별 모델 배정의 근거.

**도구별 ModelType (검증됨):**
- REGULAR(`model`): `pr_reviewer`(/review), `pr_code_suggestions`(/improve)
- WEAK(`model_weak`): `pr_questions`(/ask), `pr_line_questions`(라인 /ask), `pr_description`(describe), `pr_update_changelog`

## 목표 / 비목표

**목표**
1. CodeRabbit 유사: 친절한 한국어 페르소나 + 코드 줄 인라인 제안 + Conventional Comments 접두사 + AS-IS/TO-BE.
2. 모델 폴백 제거: 리뷰는 항상 `models[0]`. 사용량 소진 시 안내 코멘트만 남기고 종료.
3. 사용량 코멘트 "교체" 플로우(코드레빗 동일): 새 푸시/수동 호출 시 이전 안내성 코멘트 삭제 후 재시도. 성공이면 리뷰 게시(안내 사라짐), 실패면 안내 1개(중복 누적 없음).
4. 도구별 모델 분리: `/review`·`/improve` = `models[0]`, `/ask` 대화·describe = `models[1]`(model_weak).
5. 티키타카: PR 전체 및 라인 댓글에서 `/ask` 대화 지원.
6. 코드 개선 제안의 "개선 없음" vs "사용량 소진/실패" 구분: 제목만 달리고 내용 없는 모호한 코멘트 문제 해결.

**비목표**
- 중복 bash를 composite action/스크립트로 추출하는 리팩터링(접근법 B) — 별도 티켓.
- OpenAI/Gemini 유료 등급 전환(과금) — 운영 결정 사항.
- PR 본문/제목 Jira 룰 연동 — 보류.

## 모델 배정

- resolve 잡이 `GEMINI_MODELS`(CSV)에서 `model0 = models[0]`, `model1 = models[1]`을 산출(Gemini는 `gemini/` 접두어). 존재 확인(GET /models)만 하고 **첫 모델 고정**(폴백 선택 로직 제거).
- 각 리뷰/대화 잡 docker env:
  - `CONFIG.MODEL = model0`
  - `CONFIG.MODEL_WEAK = model1`
  - `CONFIG.FALLBACK_MODELS = []` (항상 — 폴백 차단)
- 결과: `/review`·`/improve`는 model0, `/ask`·describe는 model1을 자동 사용(pr-agent의 ModelType 분기).
- 엣지 케이스: `GEMINI_MODELS`에 모델이 1개뿐이면 `model1 = model0`으로 설정(대화도 동일 모델 사용).
- OpenAI 경로도 동일 패턴(접두어 없음). OpenAI는 수동 전용 유지.

## `.pr_agent.toml`

```toml
[config]
output_language = "ko-KR"
custom_model_max_tokens = 1048576
# model / model_weak / fallback_models 는 워크플로 env에서 주입

[pr_reviewer]
inline_code_comments = true
require_security_review = true
enable_review_labels_effort = false
extra_instructions = """
당신은 친절하고 유능한 시니어 동료 개발자입니다. 한국어로, 협업하듯 부드럽게,
이모지를 적절히 섞어 리뷰하세요.
- 변경 라인(diff)에 밀착해 가독성·성능·잠재적 edge case·보안(OWASP Top10, SQLi, XSS,
  인증/인가, 민감정보 노출)을 짚고, 보안 이슈는 심각도(높음/중간/낮음)를 명시합니다.
- 각 코멘트에 Conventional Comments 접두사를 붙입니다:
  [Suggestion] 개선 제안 / [Nitpick] 사소한 제안(머지 무방) / [Question] 의도 질문 /
  [Compliment] 좋은 코드 칭찬.
- 개선점은 AS-IS / TO-BE 코드블록으로 바로 적용 가능하게 제시합니다.
- 네거티브: 너무 당연한 스타일 지적, 장황한 일반론, 변경과 무관한 훈수는 하지 마세요.
"""

[pr_code_suggestions]
commitable_code_suggestions = true
extra_instructions = "위 리뷰 페르소나와 동일한 친절한 한국어 톤으로, AS-IS/TO-BE를 포함해 작성하세요."

[ignore]
glob = [
  "**/*.lock", "**/package-lock.json", "**/pnpm-lock.yaml", "**/yarn.lock",
  "**/build/**", "**/.gradle/**", "**/dist/**", "**/*.min.*",
  "**/generated/**",
]

[github_action_config]
pr_actions = ["opened", "reopened", "synchronize"]
```

> 참고: `commitable_code_suggestions=true`이면 `/improve`가 요약 표 대신 인라인 제안을 단다.
> 따라서 기존 "코드 제안 표 라벨 한글화" 후처리는 이 모드에서 사실상 미적용(본문은 프롬프트로 한국어 처리). 리뷰 가이드(`## PR Reviewer Guide`) 라벨 한글화 후처리는 유지.

## 트리거 & 잡 구성

```yaml
on:
  pull_request_target: { types: [opened, reopened, synchronize], branches: [dev] }
  issue_comment: { types: [created] }
  pull_request_review_comment: { types: [created] }   # 라인 댓글 /ask (신규)
```

잡:
- `resolve-models`(Gemini): model0/model1 산출. 자동 또는 `/gemini-review`·`/ask` 시.
- `review-gemini-auto`: `pull_request_target` 시 자동 리뷰+개선(model0).
- `review-gemini-manual`: `/gemini-review` 코멘트(model0).
- `ask-gemini`: `issue_comment`/`pull_request_review_comment`의 `/ask`(model_weak=model1). 코멘트 본문 그대로 전달.
- `resolve-openai-model` + `review-openai-manual`: `/openai-review`(수동, model0).

**무한 루프 차단:** 모든 코멘트 트리거 잡 조건에 `github.event.comment.user.type != 'Bot'` 가드 적용.

## 사용량(quota) 교체 플로우

리뷰/개선 docker 실행 **직전에** "안내성 코멘트 정리" 단계를 수행한다.

- **삭제 대상(안내성만):**
  - `Preparing review...`, `Preparing suggestions...`
  - `## ⏳ 오늘의 AI 리뷰 사용량을 모두 사용했어요…` (사용량 소진)
  - `## ⚠️ AI 리뷰를 완료하지 못했어요…` (일반 실패)
  - `Failed to generate code suggestions for PR` (pr-agent 영문 실패)
- **보존(리뷰 결과물):** `## PR 리뷰 가이드`/`## PR Reviewer Guide`, `## PR 코드 개선 제안`/`## PR Code Suggestions`, 인라인 커밋가능 제안, `**[Persistent review]...` 포인터.

흐름:
1. 잡 시작 → 봇 토큰 발급 → **안내성 코멘트 삭제**.
2. `models[0]`로 리뷰 실행(폴백 없음).
   - 성공 → 리뷰 가이드/인라인 제안 게시. 안내성은 1에서 삭제됨 → 자연히 사라짐.
   - 실패(429/RESOURCE_EXHAUSTED) → "사용량 소진" 안내 1개 게시 + job 실패(exit 1).
   - 기타 실패 → "일반 실패" 안내 1개 + exit 1.
3. 다음 푸시/수동 호출 시 1로 복귀 → 이전 안내 제거 후 재시도. **중복 누적 없음, 코드레빗과 동일.**

## 코드 개선 제안: "개선 없음" vs "사용량 소진/실패" 구분

**문제:** `/improve`에서 개선점이 없으면 `## PR Code Suggestions ✨ / No code suggestions found for the PR.`
만 달려, 사용자가 "정말 깨끗해서"인지 "사용량 소진/실패"인지 알 수 없다.

**원인 (pr-agent `pr_code_suggestions.py`):**
- 하드 실패(모델이 예외, 예: 429): `retry_with_fallback_models`가 raise → `Failed to generate code suggestions for PR` 코멘트(영문). → 우리 실패 마커가 잡음.
- 소프트 빈 결과(line 117 `if not data: data = {"code_suggestions": []}`): 모델이 예외 없이 빈/파싱불가 응답을 주면 `publish_no_suggestions()` → "No code suggestions found" 만 게시. 하드 실패와 달리 마커가 안 잡힘 → "개선 없음"과 동일하게 보임.

**해법:**
1. **명확한 긍정 메시지:** `publish_no_suggestions`가 남기는 `No code suggestions found for the PR.`를
   라벨 한글화 단계에서 다음으로 치환한다:
   `✅ 개선할 점을 찾지 못했어요 — 코드가 깔끔합니다! 👍 (사용량 소진이 아닌 정상 완료입니다)`
   → "깨끗한 코드"임을 명확히 전달.
2. **소프트 사용량 소진 감지:** improve 실행 로그에 할당량 마커
   (`RESOURCE_EXHAUSTED|free_tier|quota|rate.?limit|429`)가 있으면, "제안 없음" 코멘트가 떴더라도
   그것을 삭제하고 "사용량 소진" 안내로 교체 + job 실패(exit 1) 처리.
   → 단일 모델(폴백 없음)에서 429는 곧 실패이므로 오탐 위험이 낮다.

판정 우선순위(리뷰/개선 실행 후):
1. 로그가 `PR_AGENT_FAIL_MARKERS`(하드 실패) → 사용량/일반 실패 안내 + exit 1.
2. else 로그에 할당량 마커 → 소프트 사용량 소진: "제안 없음" 삭제 + 사용량 안내 + exit 1.
3. else → 정상. "제안 없음"이면 위 긍정 메시지로 한글화, job 녹색 유지.

## 에러 처리

- 리뷰 실패: 로그에서 `RESOURCE_EXHAUSTED|free_tier|quota|rate.?limit|429` 감지 시 사용량 소진 안내, 아니면 일반 실패 안내. 모두 한국어. job은 빨간 X.
- `/ask` 실패: "답변 생성에 실패했어요. 잠시 후 다시 시도해 주세요." 짧은 코멘트.
- 실패 마커(`PR_AGENT_FAIL_MARKERS`)로 pr-agent의 항상-0-종료를 보정해 job 실패 노출(기존 유지).

## 리뷰 가이드 라벨 한글화

기존 후처리 유지: 리뷰 가이드 코멘트의 하드코딩 영문 라벨(`PR Reviewer Guide`,
`Estimated effort to review`, `No security concerns identified` 등)을 게시 후 한국어로 PATCH.
`commitable_code_suggestions=true`로 코드 제안 "표"는 사라지므로 표 헤더(Category/Suggestion/Impact)
치환은 실질적으로 비활성(코드는 유지하되 영향 없음).
단, "개선 없음" 메시지(`No code suggestions found for the PR.`) 치환은 **유지하되 긍정 메시지로 변경**:
→ `✅ 개선할 점을 찾지 못했어요 — 코드가 깔끔합니다! 👍 (사용량 소진이 아닌 정상 완료입니다)`
(`## PR Code Suggestions ✨` → `## PR 코드 개선 제안 ✨` 치환도 유지)

## 검증(테스트) 방법

- YAML 문법 검증(`ruby -ryaml`).
- bash 구문 검증(`bash -n`), sed/jq 치환 샘플 검증.
- dev 머지 후 실제 PR에서:
  - 자동 리뷰가 한국어 페르소나 + 인라인 제안으로 게시되는지.
  - 사용량 소진 시 안내 1개만, 재푸시 시 교체되는지(중복 없음).
  - `/ask`(PR/라인)가 동작하고 model_weak를 쓰는지(로그의 `Generating prediction with <model1>`).
  - 봇 코멘트가 재트리거하지 않는지(루프 없음).
  - 개선점 없는 PR → 긍정 "제안 없음" 메시지(녹색). 사용량 소진 시 → "사용량 소진" 안내(빨간 X). 둘이 구분되는지.

## 적용 조건

- 본 변경은 `feature/CC-326`에 커밋. `issue_comment`/`pull_request_review_comment` 및
  `pull_request_target` 모두 base/default 브랜치의 워크플로로 실행되므로, **dev에 머지돼야 실제 적용**.
