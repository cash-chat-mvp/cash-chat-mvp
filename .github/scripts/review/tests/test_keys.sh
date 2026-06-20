#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")/.." && pwd)"
. "$DIR/tests/_assert.sh"
. "$DIR/lib_keys.sh"
echo "test_keys"
assert_eq "$(key_suffix_for gudals-kim)" "GUDALS" "gudals-kim → GUDALS"
assert_eq "$(key_suffix_for GUDALS-KIM)" "GUDALS" "대문자도 매핑(대소문자 무시)"
assert_eq "$(key_suffix_for seedplan005)" "CHOI" "seedplan005 → CHOI"
assert_eq "$(key_suffix_for jwchoi42)" "CHOI" "jwchoi42 → CHOI"
assert_eq "$(key_suffix_for jeonj95)" "JEON" "jeonj95 → JEON"
assert_eq "$(key_suffix_for unistuj)" "JEON" "unistuj → JEON"
assert_eq "$(key_suffix_for someone-else)" "" "미매핑 → 빈 문자열"
t_summary
