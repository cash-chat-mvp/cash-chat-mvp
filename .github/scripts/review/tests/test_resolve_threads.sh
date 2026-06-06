#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")/.." && pwd)"
. "$DIR/tests/_assert.sh"
. "$DIR/lib_ai.sh"
. "$DIR/resolve_threads.sh"
echo "test_resolve_threads"
assert_eq "$(parse_verdict $'yes\n로직이 수정됨')" "yes" "첫 줄 yes 추출"
assert_eq "$(parse_verdict $'No\n아직 미반영')" "no" "첫 줄 no 추출(소문자화)"
assert_eq "$(parse_verdict $'**YES**\n근거')" "yes" "마크다운/공백 제거"
assert_eq "$(parse_reason $'yes\n변수명이 개선되었습니다')" "변수명이 개선되었습니다" "둘째 줄 사유 추출"
assert_eq "$(parse_reason $'yes')" "" "사유 없으면 빈 문자열"
t_summary
