#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")/.." && pwd)"
. "$DIR/tests/_assert.sh"
. "$DIR/lib_cards.sh"
. "$DIR/lib_help.sh"
echo "test_help"

h="$(render_help_card)"
assert_eq "$(printf '%s' "$h" | grep -c '<!-- cashchat-ai-review:help -->')" "1" "help 마커"
assert_eq "$(printf '%s' "$h" | grep -c '`/gemini-review`')" "1" "gemini-review 안내"
assert_eq "$(printf '%s' "$h" | grep -c '`/openai-review`')" "1" "openai-review 안내"
assert_eq "$(printf '%s' "$h" | grep -c '`/ask')" "1" "ask 안내"
assert_eq "$(printf '%s' "$h" | grep -c '`/resolve`')" "1" "resolve 안내"
assert_eq "$(printf '%s' "$h" | grep -c '공통 키')" "1" "자동/키 안내 문구"
t_summary
