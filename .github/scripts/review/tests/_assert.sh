#!/usr/bin/env bash
# 단위 테스트 공용 assert 헬퍼. 각 테스트 스크립트가 source 한다.
set -uo pipefail
T_PASS=0; T_FAIL=0
assert_eq() { # $1=actual $2=expected $3=label
  if [ "$1" = "$2" ]; then T_PASS=$((T_PASS+1)); echo "  ✓ $3";
  else T_FAIL=$((T_FAIL+1)); echo "  ✗ $3"; echo "    expected: [$2]"; echo "    actual:   [$1]"; fi
}
assert_rc() { # $1=actual_rc $2=expected_rc $3=label
  if [ "$1" = "$2" ]; then T_PASS=$((T_PASS+1)); echo "  ✓ $3";
  else T_FAIL=$((T_FAIL+1)); echo "  ✗ $3 (rc expected $2 got $1)"; fi
}
t_summary() { echo "── $T_PASS passed, $T_FAIL failed ──"; [ "$T_FAIL" -eq 0 ]; }
