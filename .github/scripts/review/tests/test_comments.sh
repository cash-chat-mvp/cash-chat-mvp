#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")/.." && pwd)"
. "$DIR/tests/_assert.sh"
. "$DIR/lib_cards.sh"
. "$DIR/lib_comments.sh"
echo "test_comments"
assert_eq "$(printf '## PR Reviewer Guide' | localize_review_body)" "## PR 리뷰 가이드" "리뷰 가이드 제목 한국어화"
assert_eq "$(printf 'Estimated effort to review' | localize_review_body)" "리뷰 예상 난이도" "난이도 라벨"
assert_eq "$(printf 'Recommended focus areas for review' | localize_review_body)" "중점 리뷰 영역" "중점 영역 라벨"
assert_eq "$(printf '## PR Code Suggestions' | localize_suggestions_body)" "## PR 코드 개선 제안" "코드 제안 제목"
assert_eq "$(printf 'Why: ' | localize_suggestions_body)" "이유: " "Why 라벨"
# 마커 기반: 리뷰 흐름 notice(progress/quota/error)는 notice, help/clean·리뷰결과는 아님
is_notice_body "$(render_card progress t b)"; assert_rc $? 0 "progress 카드 → notice"
is_notice_body "$(render_card error t b)"; assert_rc $? 0 "error 카드 → notice"
is_notice_body 'Preparing review...'; assert_rc $? 0 "pr-agent 영문 notice → notice"
is_notice_body "$(render_card help t b)"; assert_rc $? 1 "help 카드 → notice 아님"
is_notice_body '## PR 리뷰 가이드'; assert_rc $? 1 "리뷰 결과 → notice 아님"
# 진행/실패 카드 본문 빌더(네트워크 분리된 순수 함수)
assert_eq "$(progress_card | grep -c '<!-- cashchat-ai-review:progress -->')" "1" "progress_card 마커"
assert_eq "$(progress_card | grep -c '^> \[!NOTE\]')" "1" "progress_card NOTE"
assert_eq "$(failure_card quota '/gemini-review' | grep -c '^> \[!WARNING\]')" "1" "quota → WARNING"
assert_eq "$(failure_card quota '/gemini-review' | grep -c '`/gemini-review`')" "1" "quota 카드에 재시도 명령"
assert_eq "$(failure_card transient '/openai-review' | grep -c '^> \[!CAUTION\]')" "1" "transient → CAUTION"

# post_progress: 운영과 동일한 set -euo pipefail 하에서 진행 카드를 게시(회귀 방지).
# curl 을 스텁해 -d 페이로드를 캡처하고, body 변수가 올바로 설정됐는지(명령으로 오인 안 됨) 검증.
CAP="$(mktemp)"
PP_RC=0
PP_OUT=$(
  set -euo pipefail
  . "$DIR/lib_cards.sh"; . "$DIR/lib_comments.sh"
  export GITHUB_API_URL="https://api.example" GITHUB_REPOSITORY="o/r" PR_NUMBER="1" GITHUB_TOKEN="t"
  curl() { local p=""; while [ $# -gt 0 ]; do [ "$1" = "-d" ] && p="$2"; shift; done; [ -n "$p" ] && printf '%s' "$p" > "$CAP"; printf '{"id":99}'; }
  post_progress
  printf '%s' "${PROGRESS_ID:-EMPTY}"
) || PP_RC=$?
assert_rc "$PP_RC" 0 "post_progress: set -e 하에서 실패 없이 완료"
assert_eq "$PP_OUT" "99" "post_progress: 응답 id를 PROGRESS_ID에 설정"
assert_eq "$(jq -r '.body' "$CAP" 2>/dev/null | grep -c '<!-- cashchat-ai-review:progress -->')" "1" "post_progress: 진행 카드 body를 전송(변수 정상 할당)"
rm -f "$CAP"

t_summary
