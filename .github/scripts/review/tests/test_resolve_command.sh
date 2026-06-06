#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")/.." && pwd)"
. "$DIR/tests/_assert.sh"
. "$DIR/lib_ai.sh"
. "$DIR/resolve_command.sh"
echo "test_resolve_command"
assert_eq "$(parse_resolve_reason '/resolve "추후 일괄 수정"')" "추후 일괄 수정" "따옴표 사유 추출"
assert_eq "$(parse_resolve_reason '/resolve 이미 반영함')" "이미 반영함" "무따옴표 사유"
assert_eq "$(parse_resolve_reason '/resolve')" "추후 일괄 수정 예정" "사유 없으면 기본값"
assert_eq "$(extract_jira_parent '[CC-342] Apple oauth' 'feature/CC-999')" "CC-342" "제목 우선 추출"
assert_eq "$(extract_jira_parent 'no ticket' 'feature/CC-777')" "CC-777" "제목 없으면 브랜치에서"
assert_eq "$(extract_jira_parent 'nope' 'feature/x')" "" "둘 다 없으면 빈값"
assert_eq "$(parse_resolve_reason '/resolve “스마트 따옴표”')" "스마트 따옴표" "곡선(스마트) 따옴표 제거"
t_summary
