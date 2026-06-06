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
t_summary
