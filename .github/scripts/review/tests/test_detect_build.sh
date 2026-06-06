#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")/../../.." && pwd)"
. "$DIR/scripts/review/tests/_assert.sh"
. "$DIR/scripts/build/detect_build_changes.sh"
echo "test_detect_build"
FILES=$'apps/frontend/app/src/Main.kt\ndocs/readme.md'
assert_eq "$(printf '%s' "$FILES" | affects_android && echo y || echo n)" "y" "app/ 변경 → android"
assert_eq "$(printf '%s' "$FILES" | affects_ios && echo y || echo n)" "n" "app/만 → ios 아님"
FILES=$'apps/frontend/shared/src/Common.kt'
assert_eq "$(printf '%s' "$FILES" | affects_android && echo y || echo n)" "y" "shared/ → android"
assert_eq "$(printf '%s' "$FILES" | affects_ios && echo y || echo n)" "y" "shared/ → ios"
FILES=$'apps/frontend/app/build.gradle.kts'
assert_eq "$(printf '%s' "$FILES" | affects_android && echo y || echo n)" "y" "*.gradle.kts → android"
assert_eq "$(printf '%s' "$FILES" | affects_ios && echo y || echo n)" "y" "*.gradle.kts → ios"
FILES=$'apps/frontend/CashChatIOS/App.swift'
assert_eq "$(printf '%s' "$FILES" | affects_android && echo y || echo n)" "n" "CashChatIOS/만 → android 아님"
assert_eq "$(printf '%s' "$FILES" | affects_ios && echo y || echo n)" "y" "CashChatIOS/ → ios"
FILES=$'docs/x.md\napps/backend/Main.kt'
assert_eq "$(printf '%s' "$FILES" | affects_android && echo y || echo n)" "n" "무관 변경 → android 아님"
assert_eq "$(printf '%s' "$FILES" | affects_ios && echo y || echo n)" "n" "무관 변경 → ios 아님"
t_summary
