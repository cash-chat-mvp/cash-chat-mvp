# 코드 리뷰 시스템 개편 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Qodo(pr-agent) 기반 PR 리뷰를 CodeRabbit 유사 경험으로 개편하고, 모델 폴백 제거·도구별 모델 분리·사용량 소진/개선없음 구분 버그를 해결한다.

**Architecture:** `.github/workflows/pr-review.yml`(단일 워크플로, 잡별 인라인 bash)와 `.pr_agent.toml`(공통 설정)만 수정한다. 폴백을 없애 항상 `models[0]`로 리뷰하고, `model_weak=models[1]`로 대화/describe를 처리한다. 리뷰 실행 전 "안내성 코멘트"만 삭제해 CodeRabbit식 교체 UX를 만든다.

**Tech Stack:** GitHub Actions, Qodo pr-agent(Docker, digest 핀닝), GitHub REST(curl), jq, sed, bash. 테스트는 단위테스트가 아니라 정적 검증(`ruby -ryaml`, `bash -n`, sed/jq 샘플) + dev 머지 후 실 PR 검증.

**전제/규칙:**
- 작업 브랜치: `feature/CC-326`. **커밋·푸시·머지는 실행 전 사용자 승인**(사용자 규칙). 커밋 메시지는 한국어, commitlint subject는 한글로 시작.
- 설계 근거: `docs/superpowers/specs/2026-05-31-code-review-system-overhaul-design.md`.
- 실제 적용은 dev 머지 후 발효(트리거가 base/default 브랜치 워크플로로 실행됨).

---

## File Structure

- `.pr_agent.toml` — 공통 설정(페르소나, commitable 인라인, ignore 글롭). model/model_weak/fallback은 워크플로 env에서 주입.
- `.github/workflows/pr-review.yml` — 트리거, 모델 산출(resolve), 리뷰/개선/대화 잡. 잡별 인라인 bash 함수(`cleanup_notices`, `notify_failure`, `localize_comments`).
- `.github/workflows/pr-description.yml` 및 `.github/PULL_REQUEST_TEMPLATE.md` — AI 명령어 안내 표에 `/ask` 추가.

> 중복 bash(3개 잡)는 기존 패턴 유지(접근법 A). composite action 추출은 비목표(후속 티켓).

---

## Task 1: `.pr_agent.toml` — 페르소나 + 인라인 제안 + ignore

**Files:**
- Modify: `/Users/gudals-mac/Documents/nomade/cash-chat-mvp/.pr_agent.toml`

- [ ] **Step 1: 파일 전체를 아래 내용으로 교체**

```toml
# pr-agent 공통 설정
# 시크릿(API 키)·프로바이더·모델·봇 토큰은 워크플로우 env에서 주입합니다.
# 정적 공통 설정만 이 파일에서 관리합니다.

[config]
output_language = "ko-KR"
# pr-agent에 토큰 한도가 등록되지 않은 최신 모델(gemini-3.5-flash 등) 허용
custom_model_max_tokens = 1048576
# 주의: model / model_weak / fallback_models 는 여기에 두지 않는다.
# pr-agent의 apply_repo_settings가 이 파일을 런타임에 다시 적용하면서
# 워크플로 env(CONFIG.MODEL 등)를 덮어쓰기 때문. 각 잡의 docker env에서 주입한다.

[pr_reviewer]
inline_code_comments = true
require_security_review = true
# 자동으로 붙는 "Review effort [1-5]" 라벨 비활성화
enable_review_labels_effort = false
extra_instructions = """
당신은 친절하고 유능한 시니어 동료 개발자입니다. 한국어로, 협업하듯 부드럽게,
이모지를 적절히 섞어 리뷰하세요.
- 변경 라인(diff)에 밀착해 가독성·성능·잠재적 edge case·보안(OWASP Top10, SQL Injection,
  XSS, 인증/인가, 민감정보 노출)을 짚고, 보안 이슈는 심각도(높음/중간/낮음)를 명시합니다.
- 각 코멘트에 Conventional Comments 접두사를 붙입니다:
  [Suggestion] 개선 제안 / [Nitpick] 사소한 제안(머지 무방) / [Question] 의도 질문 /
  [Compliment] 좋은 코드 칭찬.
- 개선점은 AS-IS / TO-BE 코드블록으로 바로 적용 가능하게 제시합니다.
- 너무 당연한 스타일 지적, 장황한 일반론, 변경과 무관한 훈수는 하지 마세요.
"""

[pr_code_suggestions]
# 코드 줄에 직접 커밋가능 인라인 제안 (CodeRabbit 라인 코멘트 재현)
commitable_code_suggestions = true
extra_instructions = "위 리뷰 페르소나와 동일한 친절한 한국어 톤으로, AS-IS/TO-BE를 포함해 작성하세요."

[ignore]
glob = [
  "**/*.lock", "**/package-lock.json", "**/pnpm-lock.yaml", "**/yarn.lock",
  "**/build/**", "**/.gradle/**", "**/dist/**", "**/*.min.*", "**/generated/**",
]

# 자동 리뷰(pull_request_target) 시 처리할 액션 목록
# 기본값에는 synchronize(커밋 푸시)가 없어 명시적으로 추가
[github_action_config]
pr_actions = ["opened", "reopened", "synchronize"]
```

- [ ] **Step 2: TOML 파싱 검증**

Run: `python3 -c "import tomllib,sys; tomllib.load(open('.pr_agent.toml','rb')); print('TOML OK')"`
Expected: `TOML OK`

- [ ] **Step 3: 커밋 (사용자 승인 후)**

```bash
git add .pr_agent.toml
git commit -m "feat(ci): pr-agent 코드레빗 스타일 페르소나·인라인 제안·ignore 적용"
```

---

## Task 2: resolve 잡 — model0 + model1 산출, 폴백 목록 제거

리뷰는 항상 `models[0]`, 대화/describe는 `models[1]`(없으면 `models[0]`). 폴백 선택 로직 삭제.

**Files:**
- Modify: `.github/workflows/pr-review.yml` (resolve-gemini-model: 현재 46–92행, resolve-openai-model: 현재 349–392행)

- [ ] **Step 1: Gemini resolve 의 outputs 교체**

`resolve-gemini-model`의 `outputs:` 블록(현재 46–48행)을 아래로 교체:

```yaml
    outputs:
      model0: ${{ steps.pick.outputs.model0 }}
      model1: ${{ steps.pick.outputs.model1 }}
```

- [ ] **Step 2: Gemini resolve 의 SELECTED/FALLBACKS 산출부 교체**

현재 83–92행(`SELECTED="${VALID[0]}"` ~ `echo "::notice::선택된 Gemini 모델...`)을 아래로 교체:

```bash
          # 리뷰=1순위(model0), 대화/describe=2순위(model1). 폴백 없음.
          MODEL0="gemini/${VALID[0]}"
          if [ ${#VALID[@]} -ge 2 ]; then MODEL1="gemini/${VALID[1]}"; else MODEL1="$MODEL0"; fi
          echo "model0=${MODEL0}" >> "$GITHUB_OUTPUT"
          echo "model1=${MODEL1}" >> "$GITHUB_OUTPUT"
          echo "::notice::Gemini 모델 — 리뷰: $MODEL0 / 대화: $MODEL1"
```

- [ ] **Step 3: OpenAI resolve 의 outputs 교체**

`resolve-openai-model`의 `outputs:` 블록(현재 349–351행)을 아래로 교체:

```yaml
    outputs:
      model0: ${{ steps.pick.outputs.model0 }}
      model1: ${{ steps.pick.outputs.model1 }}
```

- [ ] **Step 4: OpenAI resolve 의 SELECTED/FALLBACKS 산출부 교체**

현재 383–392행을 아래로 교체(OpenAI는 접두어 없음):

```bash
          MODEL0="${VALID[0]}"
          if [ ${#VALID[@]} -ge 2 ]; then MODEL1="${VALID[1]}"; else MODEL1="$MODEL0"; fi
          echo "model0=${MODEL0}" >> "$GITHUB_OUTPUT"
          echo "model1=${MODEL1}" >> "$GITHUB_OUTPUT"
          echo "::notice::OpenAI 모델 — 리뷰: $MODEL0 / 대화: $MODEL1"
```

- [ ] **Step 5: 두 resolve 의 주석 정리**

Gemini resolve 의 62–64행 주석 중 "실제 rate-limit(429) 회피는 ... fallback_models가 담당한다." 줄을 삭제(폴백 제거됨).

- [ ] **Step 6: YAML 검증**

Run: `ruby -ryaml -e 'YAML.load_file(".github/workflows/pr-review.yml"); puts "YAML OK"'`
Expected: `YAML OK`

- [ ] **Step 7: 커밋 (승인 후)**

```bash
git add .github/workflows/pr-review.yml
git commit -m "feat(ci): resolve 잡이 리뷰용/대화용 모델 두 개를 산출하도록 변경"
```

---

## Task 3: 리뷰 잡 env — 모델 분리 주입 + 폴백 완전 제거

3개 잡(`review-gemini-auto`, `review-gemini-manual`, `review-openai-manual`) 공통.

**Files:**
- Modify: `.github/workflows/pr-review.yml`

- [ ] **Step 1: 각 잡의 env 블록에서 MODEL/FALLBACKS → MODEL0/MODEL1 교체**

`review-gemini-auto`(현재 114–115행):
```yaml
          MODEL0: ${{ needs.resolve-gemini-model.outputs.model0 }}
          MODEL1: ${{ needs.resolve-gemini-model.outputs.model1 }}
```
`review-gemini-manual`(현재 233–234행): 위와 동일.
`review-openai-manual`(현재 413–414행):
```yaml
          MODEL0: ${{ needs.resolve-openai-model.outputs.model0 }}
          MODEL1: ${{ needs.resolve-openai-model.outputs.model1 }}
```

- [ ] **Step 2: 각 잡의 FB/EXTRA_ENV 블록 교체**

3개 잡 모두 아래 블록(현재 gemini-auto 142–148행, gemini-manual 261–267행, openai-manual 441–447행)을:
```bash
          FB="${FALLBACKS:-}"
          [ -z "$FB" ] && FB="[]"
          EXTRA_ENV=(-e "CONFIG.FALLBACK_MODELS=$FB")
```
다음으로 교체:
```bash
          # 모델 분리: 리뷰/개선=model0(REGULAR), 대화/describe=model1(WEAK).
          # 폴백 완전 비활성화([])로 이미지 기본값(gpt-5.4-mini) 차단.
          MODEL_ENV=(-e "CONFIG.MODEL=$MODEL0" -e "CONFIG.MODEL_WEAK=$MODEL1" -e "CONFIG.FALLBACK_MODELS=[]")
```

- [ ] **Step 3: docker run 의 모델 env 라인 교체**

각 docker run에서 `-e "CONFIG.MODEL=gemini/$MODEL" \` + `"${EXTRA_ENV[@]}" \` (gemini-auto 158–159행, gemini-manual 282–283행) 두 줄을:
```bash
            "${MODEL_ENV[@]}" \
```
한 줄로 교체. OpenAI(462–463행)의 `-e "CONFIG.MODEL=$MODEL" \` + `"${EXTRA_ENV[@]}" \` 도 동일하게 `"${MODEL_ENV[@]}" \` 로 교체.
> 주의: Gemini는 model0/model1에 이미 `gemini/` 접두어가 포함되어 있으므로 docker에서 추가 접두어를 붙이지 않는다(Task 2에서 prefix 포함).

- [ ] **Step 4: YAML 검증**

Run: `ruby -ryaml -e 'YAML.load_file(".github/workflows/pr-review.yml"); puts "YAML OK"'`
Expected: `YAML OK`

- [ ] **Step 5: bash 구문 검증(대표 1개 잡 run 블록 추출)**

Run:
```bash
grep -cF 'MODEL_ENV=(-e "CONFIG.MODEL=$MODEL0"' .github/workflows/pr-review.yml   # 정의: 3
grep -cF '"${MODEL_ENV[@]}"' .github/workflows/pr-review.yml                       # 사용: 3
grep -c 'EXTRA_ENV' .github/workflows/pr-review.yml                               # 잔재: 0
```
Expected: `3`, `3`, `0`

- [ ] **Step 6: 커밋 (승인 후)**

```bash
git add .github/workflows/pr-review.yml
git commit -m "feat(ci): 리뷰는 model0, 대화/describe는 model1 사용하도록 분리하고 폴백 제거"
```

---

## Task 4: 안내성 코멘트 정리(시작 시) + 소프트 사용량 소진 감지 + 긍정 "개선 없음" 메시지

핵심 동작: 리뷰 실행 **전** 안내성 코멘트만 삭제 → 성공 시 사용량 코멘트 자동 소멸, 실패 시 중복 없이 1개. 그리고 개선 제안이 "없음"으로 보일 때 실제로는 사용량 소진인 경우를 구분.

**Files:**
- Modify: `.github/workflows/pr-review.yml` (3개 잡)

- [ ] **Step 1: 공통 `cleanup_notices` 함수 추가 (3개 잡 run 블록 상단, `notify_failure` 정의 직전)**

각 잡의 `notify_failure() {` 정의 바로 위에 아래 함수를 추가:
```bash
          # 안내성(임시/사용량/실패) 코멘트만 삭제. 리뷰 결과물은 보존.
          cleanup_notices() {
            local api="${GITHUB_API_URL}/repos/${GITHUB_REPOSITORY}"
            curl -s -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
              "$api/issues/${PR_NUMBER}/comments?per_page=100" \
              | jq -r '.[] | select(.body == "Failed to generate code suggestions for PR" or .body == "Preparing review..." or .body == "Preparing suggestions..." or (.body | test("^## ⏳ 오늘의 AI 리뷰")) or (.body | test("^## ⚠️ AI 리뷰를 완료하지"))) | .id' \
              | while read -r cid; do
                  [ -n "$cid" ] && curl -s -X DELETE -H "Authorization: Bearer $GITHUB_TOKEN" \
                    "$api/issues/comments/${cid}" >/dev/null
                done
          }
          # 코드 제안이 "제안 없음"인데 실제로는 사용량 소진인지 판별 후, 그 코멘트 삭제
          delete_no_suggestions_comment() {
            local api="${GITHUB_API_URL}/repos/${GITHUB_REPOSITORY}"
            curl -s -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
              "$api/issues/${PR_NUMBER}/comments?per_page=100" \
              | jq -r '.[] | select((.body | test("PR Code Suggestions|PR 코드 개선 제안")) and (.body | test("No code suggestions found|개선할 점을 찾지 못했|개선 제안이 없습니다"))) | .id' \
              | while read -r cid; do
                  [ -n "$cid" ] && curl -s -X DELETE -H "Authorization: Bearer $GITHUB_TOKEN" \
                    "$api/issues/comments/${cid}" >/dev/null
                done
          }
```

- [ ] **Step 2: 리뷰 실행 직전에 `cleanup_notices` 호출**

- `review-gemini-auto`: `LOG=$(mktemp)` 바로 위 줄에 `cleanup_notices` 추가.
- `review-gemini-manual` / `review-openai-manual`: `run_command "/review"` 호출 바로 위 줄에 `cleanup_notices` 추가.

- [ ] **Step 3: 소프트 사용량 소진 감지 추가 — gemini-auto**

`review-gemini-auto`에서 하드 실패 블록(현재 167–171행) 바로 아래에 추가:
```bash
          # 하드 실패는 아니지만 로그에 할당량 마커가 있으면 소프트 소진으로 처리
          if grep -qiE "RESOURCE_EXHAUSTED|free_tier|quota|rate.?limit|429" "$LOG"; then
            delete_no_suggestions_comment
            notify_failure "$LOG"
            echo "::error::Gemini 사용량 소진(소프트) — 로그를 확인하세요"
            exit 1
          fi
```

- [ ] **Step 4: 소프트 사용량 소진 감지 추가 — manual 2개**

`review-gemini-manual`/`review-openai-manual`의 `run_command "/improve"` 호출 **직후, `localize_comments` 호출 직전**에 추가(각 잡의 `$LOG`가 아니라 마지막 improve 로그를 별도 캡처해야 하므로 `run_command`를 아래처럼 보강):

먼저 `run_command()` 내부에서 로그 경로를 잡 스코프 변수에 보존하도록, `local log; log=$(mktemp)` 를 `LAST_LOG=$(mktemp); local log="$LAST_LOG"` 로 교체(3개 잡 중 manual 2개). 그런 다음 `localize_comments` 호출 직전에 추가:
```bash
          if grep -qiE "RESOURCE_EXHAUSTED|free_tier|quota|rate.?limit|429" "$LAST_LOG"; then
            delete_no_suggestions_comment
            notify_failure "$LAST_LOG"
            echo "::error::사용량 소진(소프트) — 로그를 확인하세요"
            exit 1
          fi
```

- [ ] **Step 5: 긍정 "개선 없음" 메시지로 치환 (localize sed 맵 수정)**

3개 잡의 localize sed 맵에서:
```
              -e 's/No code suggestions found for the PR./이 PR에 대한 코드 개선 제안이 없습니다./g' \
```
를 다음으로 교체:
```
              -e 's/No code suggestions found for the PR./✅ 개선할 점을 찾지 못했어요 — 코드가 깔끔합니다! 👍 (사용량 소진이 아닌 정상 완료입니다)/g' \
```
(gemini-auto의 인라인 localize 199행, gemini-manual의 323행, openai-manual의 503행)

- [ ] **Step 6: jq 필터 샘플 검증**

Run:
```bash
echo '[{"id":1,"body":"## PR 코드 개선 제안 ✨\n\nNo code suggestions found for the PR."},{"id":2,"body":"## PR 리뷰 가이드 🔍"},{"id":3,"body":"Preparing review..."}]' \
| jq -r '.[] | select((.body | test("PR Code Suggestions|PR 코드 개선 제안")) and (.body | test("No code suggestions found|개선할 점을 찾지 못했|개선 제안이 없습니다"))) | .id'
```
Expected: `1` (만 출력 — id 2,3 제외)

- [ ] **Step 7: YAML 검증 + 커밋 (승인 후)**

```bash
ruby -ryaml -e 'YAML.load_file(".github/workflows/pr-review.yml"); puts "YAML OK"'
git add .github/workflows/pr-review.yml
git commit -m "fix(ci): 사용량 소진과 개선없음을 구분하고 안내 코멘트를 시작 시 정리"
```

---

## Task 5: 티키타카 `/ask` — 트리거 + 잡 추가

PR 전체(`issue_comment`) 및 라인 댓글(`pull_request_review_comment`)의 `/ask` 를 model_weak(=model1)로 처리.

**Files:**
- Modify: `.github/workflows/pr-review.yml`

- [ ] **Step 1: 트리거에 `pull_request_review_comment` 추가**

`on:` 블록(현재 11–20행)의 `issue_comment` 아래에 추가:
```yaml
  # 라인 댓글의 /ask 대화 (인라인 코멘트에 답글)
  pull_request_review_comment:
    types: [created]
```

- [ ] **Step 2: resolve-gemini-model 의 `if` 에 /ask 조건 추가**

현재 37–42행 `if:` 를 아래로 교체(`/ask` 와 `pull_request_review_comment` 포함):
```yaml
    if: |
      github.event_name == 'pull_request_target' ||
      (github.event_name == 'issue_comment' &&
       github.event.comment.user.type != 'Bot' &&
       github.event.issue.pull_request != null &&
       (contains(github.event.comment.body, '/gemini-review') || contains(github.event.comment.body, '/ask'))) ||
      (github.event_name == 'pull_request_review_comment' &&
       github.event.comment.user.type != 'Bot' &&
       contains(github.event.comment.body, '/ask'))
```

- [ ] **Step 3: `ask-gemini` 잡 추가 (파일 끝, openai-manual 잡 다음)**

파일 맨 끝(514행 이후)에 아래 잡 추가:
```yaml

  # ====================================================
  # 티키타카 대화 (/ask) — PR 전체 및 라인 댓글, model_weak 사용
  # ====================================================
  ask-gemini:
    name: Gemini Ask
    needs: resolve-gemini-model
    if: |
      (github.event_name == 'issue_comment' &&
       github.event.comment.user.type != 'Bot' &&
       github.event.issue.pull_request != null &&
       contains(github.event.comment.body, '/ask')) ||
      (github.event_name == 'pull_request_review_comment' &&
       github.event.comment.user.type != 'Bot' &&
       contains(github.event.comment.body, '/ask'))
    runs-on: ubuntu-latest
    permissions:
      contents: read
    steps:
      - name: Generate Gemini bot token
        id: bot-token
        uses: actions/create-github-app-token@bcd2ba49218906704ab6c1aa796996da409d3eb1
        with:
          app-id: ${{ secrets.GEMINI_BOT_APP_ID }}
          private-key: ${{ secrets.GEMINI_BOT_PRIVATE_KEY }}
      - name: Ask (Gemini, model_weak)
        env:
          GITHUB_TOKEN: ${{ steps.bot-token.outputs.token }}
          MODEL0: ${{ needs.resolve-gemini-model.outputs.model0 }}
          MODEL1: ${{ needs.resolve-gemini-model.outputs.model1 }}
          GEMINI_KEY: ${{ secrets.GOOGLE_GEMINI_API_KEY }}
        run: |
          set -euo pipefail
          # /ask 는 ModelType.WEAK → CONFIG.MODEL_WEAK(=model1) 사용.
          # 코멘트 본문은 rewrite 하지 않고 그대로 pr-agent 러너가 파싱한다.
          LOG=$(mktemp)
          docker run --rm \
            -e GITHUB_TOKEN \
            -e GITHUB_EVENT_NAME="${{ github.event_name }}" \
            -e GITHUB_REPOSITORY="${{ github.repository }}" \
            -e GITHUB_API_URL="${{ github.api_url }}" \
            -e GITHUB_SERVER_URL="${{ github.server_url }}" \
            -e GITHUB_EVENT_PATH=/github/workflow/event.json \
            -e "CONFIG.AI_PROVIDER=google_ai_studio" \
            -e "CONFIG.MODEL=$MODEL0" \
            -e "CONFIG.MODEL_WEAK=$MODEL1" \
            -e "CONFIG.FALLBACK_MODELS=[]" \
            -e "GOOGLE_AI_STUDIO.GEMINI_API_KEY=$GEMINI_KEY" \
            -e "GEMINI_API_KEY=$GEMINI_KEY" \
            -v "$GITHUB_EVENT_PATH:/github/workflow/event.json:ro" \
            "$PR_AGENT_IMAGE" 2>&1 | tee "$LOG"
          if grep -qE "$PR_AGENT_FAIL_MARKERS" "$LOG" || grep -qiE "RESOURCE_EXHAUSTED|free_tier|quota|rate.?limit|429" "$LOG"; then
            PR_NUM="${{ github.event.issue.number || github.event.pull_request.number }}"
            api="${GITHUB_API_URL}/repos/${{ github.repository }}"
            msg='답변 생성에 실패했어요. 사용량 소진일 수 있어요 — 잠시 후 다시 `/ask` 로 질문해 주세요. 🙏'
            curl -s -X POST -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
              "$api/issues/${PR_NUM}/comments" -d "$(jq -n --arg b "$msg" '{body:$b}')" >/dev/null
            echo "::error::/ask 실패"
            exit 1
          fi
```
> 참고: `pull_request_review_comment` 의 `/ask` 는 pr-agent 러너가 `handle_line_comments`로 라인 컨텍스트를 자동 부착한다(러너 소스 확인됨). 라인 답변은 해당 라인 스레드에 달린다.

- [ ] **Step 4: YAML 검증**

Run: `ruby -ryaml -e 'YAML.load_file(".github/workflows/pr-review.yml"); puts "YAML OK"'`
Expected: `YAML OK`

- [ ] **Step 5: 커밋 (승인 후)**

```bash
git add .github/workflows/pr-review.yml
git commit -m "feat(ci): /ask 티키타카 대화 잡 추가 (라인+PR, model_weak 사용)"
```

---

## Task 6: PR 안내 표에 `/ask` 추가

**Files:**
- Modify: `.github/PULL_REQUEST_TEMPLATE.md`
- Modify: `.github/workflows/pr-description.yml` (자동 생성 본문의 AI 명령어 표)

- [ ] **Step 1: PULL_REQUEST_TEMPLATE.md 표에 행 추가**

`| \`@coderabbitai review\` | ...` 줄 위에 추가:
```
| `/ask "질문"` | AI 코멘트에 답글로 후속 질문 (라인/PR, 비용 낮은 모델) |
```

- [ ] **Step 2: pr-description.yml 의 명령어 표에도 동일 행 추가**

`.github/workflows/pr-description.yml`에서 `/openai-review` 행 다음에 `/ask` 행을 동일 형식으로 추가(해당 `body` 배열의 표 문자열).

- [ ] **Step 3: 검증 + 커밋 (승인 후)**

```bash
ruby -ryaml -e 'YAML.load_file(".github/workflows/pr-description.yml"); puts "YAML OK"'
git add .github/PULL_REQUEST_TEMPLATE.md .github/workflows/pr-description.yml
git commit -m "docs(ci): PR 안내 표에 /ask 대화 명령어 추가"
```

---

## Task 7: 통합 검증 + 실 PR 테스트

**Files:** 없음(검증만)

- [ ] **Step 1: 전체 YAML + TOML 최종 검증**

Run:
```bash
ruby -ryaml -e 'YAML.load_file(".github/workflows/pr-review.yml"); YAML.load_file(".github/workflows/pr-description.yml"); puts "YAML OK"'
python3 -c "import tomllib; tomllib.load(open('.pr_agent.toml','rb')); print('TOML OK')"
```
Expected: `YAML OK` / `TOML OK`

- [ ] **Step 2: 잔재 키 없는지 확인**

Run: `grep -nE "FALLBACKS|fallbacks|EXTRA_ENV|outputs.model\b" .github/workflows/pr-review.yml || echo "clean"`
Expected: `clean` (구 폴백/단일 model 출력 잔재 없음)

- [ ] **Step 3: 푸시 (승인 후) 및 dev 머지 후 실 PR 확인 체크리스트**

```bash
git push origin feature/CC-326
```
dev 머지 후 테스트 PR에서 확인:
- [ ] 자동 리뷰가 한국어 페르소나 + 코드 줄 인라인 제안으로 게시된다.
- [ ] 리뷰 로그에 `Generating prediction with <model0>`(리뷰), `/ask` 시 `<model1>`(대화)가 찍힌다.
- [ ] 개선점 없는 PR → "✅ 개선할 점을 찾지 못했어요…" 긍정 메시지(녹색).
- [ ] 사용량 소진 시 → "⏳ 사용량 소진" 안내 1개(빨간 X), 재푸시 시 교체(중복 없음).
- [ ] 봇 코멘트가 리뷰를 재트리거하지 않는다(루프 없음).
- [ ] `/ask "질문"`(PR/라인)에 봇이 한국어로 답한다.

---

## 자기 검토 메모(작성자)

- 스펙 6개 목표 ↔ 태스크 매핑: ①Task1(페르소나/인라인) ②Task2·3(폴백제거/모델) ③Task4(사용량 플로우) ④Task3(모델분리) ⑤Task5(/ask) ⑥Task4(개선없음 구분). 전부 커버.
- 행 번호는 현재 파일(514행) 기준이며 앞 태스크 적용 시 이동하므로, 실행 시 **앵커 문자열(코드 스니펫)** 기준으로 찾을 것.
- 커밋/푸시/머지는 매 단계 사용자 승인 필요(사용자 규칙).
