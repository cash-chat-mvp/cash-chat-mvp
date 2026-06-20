#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")/.." && pwd)"
. "$DIR/tests/_assert.sh"
. "$DIR/lib_rpd.sh"
echo "test_rpd"

F="$(mktemp)"; rm -f "$F"   # 존재하지 않는 상태에서 시작

# 없는 카운터는 0
assert_eq "$(rpd_get "$F" shared m0)" "0" "초기값 0"

# 증가(기본 +1) — 카운터 차원은 고정 티어 model0/model1
rpd_add "$F" shared model0
assert_eq "$(rpd_get "$F" shared model0)" "1" "기본 +1"

# 명시적 +N 누적
rpd_add "$F" shared model0 3
assert_eq "$(rpd_get "$F" shared model0)" "4" "누적 +3 → 4"

# 키/모델 분리
rpd_add "$F" GUDALS model1 2
assert_eq "$(rpd_get "$F" GUDALS model1)" "2" "다른 키·모델 분리 카운트"
assert_eq "$(rpd_get "$F" shared model0)" "4" "다른 키 증가가 기존 값 보존"

# KST 날짜 형식 (YYYY-MM-DD)
rpd_kst_date | grep -qE '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'; assert_rc $? 0 "KST 날짜 형식"

# 렌더: 한도(20/500)와 공통키/내키 사용량 표 (표시명은 모델명, 카운트는 티어 기준)
OUT="$(RPD_MODEL0_LIMIT=20 RPD_MODEL1_LIMIT=500 rpd_render "$F" "gemini-3.5-flash" "gemini-3.1-flash-lite" GUDALS)"
printf '%s' "$OUT" | grep -q '| 20 |'; assert_rc $? 0 "model0 한도 20 표시"
printf '%s' "$OUT" | grep -q '| 500 |'; assert_rc $? 0 "model1 한도 500 표시"
printf '%s' "$OUT" | grep -qF '`gemini-3.5-flash`'; assert_rc $? 0 "model0 이름 표시"
# 공통키 model0=4, 내키(GUDALS) model1=2 가 표 셀에 반영
printf '%s' "$OUT" | grep -qF '| 4 |'; assert_rc $? 0 "공통키 사용량(4) 반영"
printf '%s' "$OUT" | grep -qF '| 2 |'; assert_rc $? 0 "내키 사용량(2) 반영"

rm -f "$F"
t_summary
