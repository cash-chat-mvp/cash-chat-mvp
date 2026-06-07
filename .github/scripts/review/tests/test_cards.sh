#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")/.." && pwd)"
. "$DIR/tests/_assert.sh"
. "$DIR/lib_cards.sh"
echo "test_cards"

card="$(render_card progress '🔍 AI 코드 리뷰 진행 중' '변경된 코드를 살펴보는 중입니다.' '`/gemini-review` 재요청')"

# Alert 토큰 매핑
assert_eq "$(printf '%s' "$card" | grep -c '^> \[!NOTE\]')" "1" "progress → [!NOTE]"
assert_eq "$(render_card quota t b | grep -c '^> \[!WARNING\]')" "1" "quota → [!WARNING]"
assert_eq "$(render_card error t b | grep -c '^> \[!CAUTION\]')" "1" "error → [!CAUTION]"
assert_eq "$(render_card approve t b | grep -c '^> \[!TIP\]')" "1" "approve → [!TIP]"

# 본문 줄은 blockquote 안에
assert_eq "$(printf '%s' "$card" | grep -c '^> 변경된 코드를 살펴보는 중입니다.')" "1" "본문 blockquote 처리"

# 숨김 마커 포함 + card_has_marker 탐지
assert_eq "$(printf '%s' "$card" | grep -c '<!-- cashchat-ai-review:progress -->')" "1" "progress 마커 포함"
card_has_marker "$card"; assert_rc $? 0 "card_has_marker 양성"
card_has_marker "일반 코멘트"; assert_rc $? 1 "card_has_marker 음성"

# 문서 URL: env 있으면 blob, 없으면 상대경로
( unset GITHUB_SERVER_URL GITHUB_REPOSITORY; assert_eq "$(card_docs_url)" "docs/review/ai-code-review.md" "env 없으면 상대경로" )
assert_eq "$(GITHUB_SERVER_URL=https://github.com GITHUB_REPOSITORY=o/r card_docs_url)" "https://github.com/o/r/blob/dev/docs/review/ai-code-review.md" "env 있으면 blob URL"

t_summary
