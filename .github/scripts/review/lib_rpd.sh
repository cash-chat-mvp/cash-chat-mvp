#!/usr/bin/env bash
# 리뷰 호출 카운터(추정치). 상태는 JSON 파일에 "{keylabel}|{model}": count 형태로 저장.
# ⚠️ pr-agent 내부의 다중 Gemini 호출은 측정 불가 → 이 값은 "오늘 트리거한 리뷰 연산 횟수"
#    추정치이며, 실제 소비된 API 호출 수와 다를 수 있다.
# 일일 초기화는 호출자가 KST 날짜를 캐시 키에 포함해 처리한다(rpd_kst_date).

RPD_MODEL0_LIMIT="${RPD_MODEL0_LIMIT:-20}"    # 리뷰(model0) 일일 한도(RPD)
RPD_MODEL1_LIMIT="${RPD_MODEL1_LIMIT:-500}"   # 개선/대화(model1) 일일 한도(RPD)

# 한국시간(Asia/Seoul) 기준 날짜 — 자정 KST에 카운터가 새로 시작되도록.
rpd_kst_date() { TZ='Asia/Seoul' date +%Y-%m-%d; }

# rpd_add FILE KEYLABEL MODEL [N]  — 카운터에 N(기본 1) 누적
rpd_add() {
  local file="$1" key="$2" model="$3" n="${4:-1}" tmp
  [ -f "$file" ] || echo '{}' > "$file"
  tmp=$(mktemp)
  if jq --arg k "${key}|${model}" --argjson n "$n" '.[$k] = ((.[$k] // 0) + $n)' "$file" > "$tmp" 2>/dev/null; then
    mv "$tmp" "$file"
  else
    rm -f "$tmp"
  fi
}

# rpd_get FILE KEYLABEL MODEL  — 현재 카운트(없으면 0)
rpd_get() {
  local file="$1" key="$2" model="$3"
  [ -f "$file" ] || { echo 0; return; }
  jq -r --arg k "${key}|${model}" '.[$k] // 0' "$file" 2>/dev/null || echo 0
}

# rpd_render FILE M0_NAME M1_NAME VIEWER_KEYLABEL  — /help 표시용 마크다운 표.
# 카운터 차원은 고정 티어("model0"/"model1")이고, 표에는 보기 좋은 모델명을 표시한다.
rpd_render() {
  local file="$1" m0name="${2:-model0}" m1name="${3:-model1}" viewer="${4:-shared}"
  local s0 s1 v0 v1
  s0=$(rpd_get "$file" shared model0); s1=$(rpd_get "$file" shared model1)
  v0=$(rpd_get "$file" "$viewer" model0); v1=$(rpd_get "$file" "$viewer" model1)
  cat <<MD
| 모델 | 일일 한도(RPD) | 공통키 오늘 사용 | 내 키 오늘 사용 |
|---|---|---|---|
| \`${m0name}\` (리뷰) | ${RPD_MODEL0_LIMIT} | ${s0} | ${v0} |
| \`${m1name}\` (개선·대화) | ${RPD_MODEL1_LIMIT} | ${s1} | ${v1} |
MD
}
