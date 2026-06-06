#!/usr/bin/env bash
# 리뷰 코멘트 공용: 안내 코멘트 정리 / 실패 알림 / 진행 코멘트 / 한국어 라벨 치환.
# 필요 env: GITHUB_TOKEN, GITHUB_API_URL, GITHUB_REPOSITORY, PR_NUMBER

# 카드 렌더 의존(같은 디렉터리)
. "$(dirname "${BASH_SOURCE[0]}")/lib_cards.sh"

# ── 정리 대상(notice) 판별: 리뷰 흐름 카드(progress/quota/error) 또는 pr-agent 영문 notice → rc0 ──
# help/clean 은 제외(리뷰 실행이 도움말/정상완료 안내를 지우지 않도록).
is_notice_body() {
  local b="${1:-}"
  printf '%s' "$b" | grep -qE '<!-- cashchat-ai-review:(progress|quota|error) -->' && return 0
  printf '%s' "$b" | grep -qE '^(Failed to generate code suggestions for PR|Preparing review\.\.\.|Preparing suggestions\.\.\.)$' && return 0
  return 1
}

# ── 리뷰 가이드 본문 영문 라벨 → 한국어 (stdin → stdout) ──
localize_review_body() {
  sed \
    -e 's/## Incremental PR Reviewer Guide/## 증분 PR 리뷰 가이드/g' \
    -e 's/## PR Reviewer Guide/## PR 리뷰 가이드/g' \
    -e 's/Here are some key observations to aid the review process:/리뷰에 참고할 주요 관찰 사항입니다:/g' \
    -e 's/Estimated effort to review/리뷰 예상 난이도/g' \
    -e 's/No relevant tests/관련 테스트 없음/g' \
    -e 's/PR contains tests/테스트 포함됨/g' \
    -e 's/No security concerns identified/보안 이슈 없음/g' \
    -e 's/Security concerns/보안 이슈/g' \
    -e 's/No major issues detected/주요 이슈 없음/g' \
    -e 's/Recommended focus areas for review/중점 리뷰 영역/g' \
    -e 's/No TODO sections/TODO 섹션 없음/g' \
    -e 's/TODO sections/TODO 섹션/g' \
    -e 's/Contribution time estimate/기여 소요 시간 추정/g' \
    -e 's/Ticket compliance check/티켓 준수 확인/g' \
    -e 's/Relevant ticket/관련 티켓/g'
}

# ── 코드 제안 본문 영문 라벨 → 한국어 (stdin → stdout) ──
localize_suggestions_body() {
  sed \
    -e 's/## PR Code Suggestions/## PR 코드 개선 제안/g' \
    -e 's/No code suggestions found for the PR./✅ 개선할 점을 찾지 못했어요 — 코드가 깔끔합니다! 👍 (사용량 소진이 아닌 정상 완료입니다)/g' \
    -e 's#<strong>Category</strong>#<strong>분류</strong>#g' \
    -e 's/<strong>Suggestion/<strong>제안/g' \
    -e 's#<strong>Impact</strong>#<strong>영향도</strong>#g' \
    -e 's/Suggestion importance\[1-10\]:/제안 중요도[1-10]:/g' \
    -e 's/Why: /이유: /g'
}

_api() { echo "${GITHUB_API_URL}/repos/${GITHUB_REPOSITORY}"; }

# ── 안내성 코멘트만 삭제(리뷰 결과물 보존) ──
cleanup_notices() {
  local api; api="$(_api)"
  curl -s -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
    "$api/issues/${PR_NUMBER}/comments?per_page=100" \
    | jq -r '.[] | select(.user!=null and .user.type=="Bot" and .body!=null and (
        (.body|test("<!-- cashchat-ai-review:(progress|quota|error) -->")) or
        (.body=="Failed to generate code suggestions for PR") or
        (.body=="Preparing review...") or (.body=="Preparing suggestions...")
      )) | .id' \
    | while read -r cid; do
        [ -n "$cid" ] && curl -s -X DELETE -H "Authorization: Bearer $GITHUB_TOKEN" "$api/issues/comments/${cid}" >/dev/null || true
      done || true
}

# 진행 카드 본문(순수) — 네트워크 없음
progress_card() {
  render_card progress '🔍 AI 코드 리뷰 진행 중' \
$'변경된 코드를 살펴보는 중입니다 — 보통 1~2분 정도 걸려요.\n끝나면 이 안내는 결과로 바뀌거나 사라집니다.' \
    ''
}
# 실패 카드 본문(순수). $1=classify(quota|transient) $2=재시도 명령
failure_card() {
  local cls="${1:-transient}" retry="${2:-/gemini-review}"
  if [ "$cls" = "quota" ]; then
    render_card quota '오늘의 AI 리뷰 사용량을 모두 사용했어요' \
$'무료 등급의 일일 호출 한도(RPD)에 도달해 이번 리뷰를 완료하지 못했어요.\n한도가 회복되면 다시 시도해 주세요. 🙏' \
      "\`${retry}\` 재요청"
  else
    render_card error 'AI 리뷰를 완료하지 못했어요' \
$'일시적인 오류로 리뷰 생성에 실패했어요. 잠시 후 다시 시도해 주세요.' \
      "\`${retry}\` 재요청"
  fi
}

# ── 진행 코멘트 게시(전역 PROGRESS_ID 설정); clear_progress를 trap에 등록해 사용 ──
post_progress() {
  local api; api="$(_api)" body; body="$(progress_card)"
  PROGRESS_ID=$(curl -s -X POST -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
    "$api/issues/${PR_NUMBER}/comments" -d "$(jq -n --arg b "$body" '{body:$b}')" | jq -r '.id // empty' || true)
}
clear_progress() {
  [ -z "${PROGRESS_ID:-}" ] && return 0
  curl -s -X DELETE -H "Authorization: Bearer $GITHUB_TOKEN" "$(_api)/issues/comments/${PROGRESS_ID}" >/dev/null || true
  PROGRESS_ID=""
}

# 전 모델 실패 시: 진행 카드를 실패 카드로 제자리 업데이트(없으면 신규 게시). $1=로그 $2=재시도명령
notify_failure() {
  local log="$1" retry="${2:-/gemini-review}" api; api="$(_api)"
  local cls body; cls="$(ai_classify "$(cat "$log" 2>/dev/null)")"; body="$(failure_card "$cls" "$retry")"
  if [ -n "${PROGRESS_ID:-}" ]; then
    curl -s -X PATCH -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
      "$api/issues/comments/${PROGRESS_ID}" -d "$(jq -n --arg b "$body" '{body:$b}')" >/dev/null
    PROGRESS_ID=""   # EXIT 트랩(clear_progress)이 실패 카드를 지우지 않도록 해제
  else
    cleanup_notices
    curl -s -X POST -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
      "$api/issues/${PR_NUMBER}/comments" -d "$(jq -n --arg b "$body" '{body:$b}')" >/dev/null
  fi
}

# ── 게시된 리뷰 가이드/코드 제안 코멘트를 한국어화(최신 1건씩) ──
localize_comments() {
  local api; api="$(_api)"
  local comments rid sid body
  comments=$(curl -s -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" "$api/issues/${PR_NUMBER}/comments?per_page=100")
  rid=$(printf '%s' "$comments" | jq -r '[.[]|select(.body!=null and (.body|test("PR Reviewer Guide")))]|last|.id // empty')
  if [ -n "$rid" ]; then
    body=$(printf '%s' "$comments" | jq -r --argjson id "$rid" '.[]|select(.id==$id)|.body' | localize_review_body)
    curl -s -X PATCH -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" "$api/issues/comments/${rid}" -d "$(jq -n --arg b "$body" '{body:$b}')" >/dev/null
  fi
  sid=$(printf '%s' "$comments" | jq -r '[.[]|select(.body!=null and (.body|test("PR Code Suggestions")))]|last|.id // empty')
  if [ -n "$sid" ]; then
    body=$(printf '%s' "$comments" | jq -r --argjson id "$sid" '.[]|select(.id==$id)|.body' | localize_suggestions_body)
    curl -s -X PATCH -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" "$api/issues/comments/${sid}" -d "$(jq -n --arg b "$body" '{body:$b}')" >/dev/null
  fi
}
