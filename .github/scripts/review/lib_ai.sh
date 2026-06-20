#!/usr/bin/env bash
# AI 호출 공용: rate-limit 감지 / 실패 분류 / 지수 백오프 재시도.
# AI_RETRY_SLEEP(초, 기본 20), AI_RETRY_MAX(기본 2)로 조정 가능(테스트에서 0으로 단축).

ai_is_rate_limited() { # $1=응답 본문. rate-limit이면 rc0.
  printf '%s' "${1:-}" | grep -qiE 'RESOURCE_EXHAUSTED|insufficient_quota|"code": ?429|rate.?limit|\b429\b|\b503\b|overloaded'
}

ai_classify() { # $1=로그/응답 → "quota" | "transient"
  if printf '%s' "${1:-}" | grep -qiE 'RESOURCE_EXHAUSTED|insufficient_quota|rate.?limit|\b429\b'; then
    echo "quota"
  else
    echo "transient"
  fi
}

# ai_retry CMD [ARGS...] — CMD가 rc0 낼 때까지 지수 백오프로 최대 AI_RETRY_MAX회 재시도.
# CMD는 일시 실패 시 비0을 반환해야 한다(호출자가 응답 검사 후 false를 반환하도록 래핑).
ai_retry() {
  local max="${AI_RETRY_MAX:-2}" base="${AI_RETRY_SLEEP:-20}" n=0 delay
  while :; do
    n=$((n+1))
    if "$@"; then return 0; fi
    if [ "$n" -ge "$max" ]; then return 1; fi
    delay=$(( base * n ))   # 20s, 40s ...
    [ "$delay" -gt 0 ] && sleep "$delay"
  done
}

# gemini_generate KEY MODEL PROMPT_JSON_FILE OUT_FILE — generateContent 1회 호출.
# rate-limit/HTTP 오류면 rc1(ai_retry가 재시도). 성공 시 OUT_FILE에 본문 저장 후 rc0.
gemini_generate() {
  local key="$1" model="$2" payload="$3" out="$4"
  local code
  code=$(curl -s -o "$out" -w '%{http_code}' --max-time 30 -X POST \
    "https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${key}" \
    -H "Content-Type: application/json" --data-binary "@${payload}") || code="000"
  if [ "$code" != "200" ] || ai_is_rate_limited "$(cat "$out" 2>/dev/null)"; then return 1; fi
  return 0
}

# ai_generate PRIMARY_KEY FALLBACK_KEY MODEL PAYLOAD OUT
#   기본은 개인키(PRIMARY)로 호출하고, 실패 시에만 공용키(FALLBACK)로 폴백한다.
#   - 개인키 rate-limit(RPD/RPM 소진): 같은 키 재시도는 무의미 → 즉시 공용키로 폴백
#   - 개인키 일시 오류: 개인키로 백오프 재시도 후 그래도 실패하면 공용키 폴백
#   FALLBACK이 비었거나 PRIMARY와 같으면 폴백하지 않는다. rc0=성공 / rc1=최종 실패.
ai_generate() {
  local kp="$1" kf="$2" model="$3" payload="$4" out="$5"
  # 1차: 개인키 1회
  if gemini_generate "$kp" "$model" "$payload" "$out"; then return 0; fi
  local can_fallback=0
  [ -n "$kf" ] && [ "$kf" != "$kp" ] && can_fallback=1
  # 개인키가 rate-limit이면 재시도 없이 곧장 공용키로
  if [ "$can_fallback" -eq 1 ] && ai_is_rate_limited "$(cat "$out" 2>/dev/null)"; then
    echo "::notice::개인키 rate-limit → 공용키로 폴백"
    ai_retry gemini_generate "$kf" "$model" "$payload" "$out" && return 0
    return 1
  fi
  # 일시 오류: 개인키로 백오프 재시도
  if ai_retry gemini_generate "$kp" "$model" "$payload" "$out"; then return 0; fi
  # 그래도 실패 → 공용키 폴백
  if [ "$can_fallback" -eq 1 ]; then
    echo "::notice::개인키 실패 → 공용키로 폴백"
    ai_retry gemini_generate "$kf" "$model" "$payload" "$out" && return 0
  fi
  return 1
}
