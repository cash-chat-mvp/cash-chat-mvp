#!/usr/bin/env bash
#
# maestro-e2e-test.sh — mock 플레이버 hermetic 인수 테스트 러너 (CC-391)
#
# 에뮬레이터 부팅 → mock APK 설치 → Maestro flow 실행 → (선택) 리포트 생성까지 한 번에 수행.
#
# 사용법:
#   scripts/maestro-e2e-test.sh [OPTIONS] [STORY]
#
#   STORY   실행할 유저 스토리(= flows/ 하위 폴더). 생략하면 전체.
#           send-message | watch-rewarded-ad | complete-offerwall
#           (개발 중 한 기능만 빠르게 돌릴 때 사용 — 전체 대기 방지)
#
# 옵션:
#   --report       HTML-DETAILED 리포트 + debug-output(스크린샷/로그) 생성
#   --no-install   mock APK 재설치 스킵(코드 변경 없을 때 빠르게)
#   --no-boot      에뮬레이터 자동 부팅 스킵(이미 켜져 있다고 가정)
#   -h, --help     도움말
#
# 환경변수(자동 탐지, 필요 시 오버라이드):
#   ANDROID_SDK   Android SDK 경로 (기본: $LOCALAPPDATA/Android/Sdk 또는 $HOME/Android/Sdk)
#   MAESTRO_BIN   maestro 바이너리 디렉터리 (기본: /c/maestro/bin 또는 $HOME/.maestro/bin)
#   AVD           부팅할 AVD 이름 (기본: 첫 번째 AVD)
#
set -euo pipefail

# ── 경로 해석 ───────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND="$(cd "$SCRIPT_DIR/.." && pwd)"        # apps/frontend
STORIES=(send-message watch-rewarded-ad complete-offerwall)

# ── 옵션 파싱 ───────────────────────────────────────────────
REPORT=0; NO_INSTALL=0; NO_BOOT=0; STORY=""
usage() { sed -n '2,30p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit "${1:-0}"; }
for arg in "$@"; do
  case "$arg" in
    --report)     REPORT=1 ;;
    --no-install) NO_INSTALL=1 ;;
    --no-boot)    NO_BOOT=1 ;;
    -h|--help)    usage 0 ;;
    -*)           echo "알 수 없는 옵션: $arg" >&2; usage 1 ;;
    *)            STORY="$arg" ;;
  esac
done

# STORY 유효성 검사
if [ -n "$STORY" ]; then
  ok=0; for s in "${STORIES[@]}"; do [ "$s" = "$STORY" ] && ok=1; done
  if [ "$ok" = 0 ]; then
    echo "알 수 없는 STORY: '$STORY'. 사용 가능: ${STORIES[*]}" >&2; exit 1
  fi
fi

# ── 도구 경로 (PATH 주입) ───────────────────────────────────
: "${ANDROID_SDK:=${LOCALAPPDATA:-$HOME/AppData/Local}/Android/Sdk}"
[ -d "$ANDROID_SDK" ] || ANDROID_SDK="$HOME/Android/Sdk"
: "${MAESTRO_BIN:=/c/maestro/bin}"
[ -x "$MAESTRO_BIN/maestro" ] || MAESTRO_BIN="$HOME/.maestro/bin"
export PATH="$MAESTRO_BIN:$ANDROID_SDK/platform-tools:$ANDROID_SDK/emulator:$PATH"

command -v adb >/dev/null      || { echo "adb 를 찾을 수 없음 (ANDROID_SDK=$ANDROID_SDK)" >&2; exit 1; }
command -v maestro >/dev/null  || { echo "maestro 를 찾을 수 없음 (MAESTRO_BIN=$MAESTRO_BIN)" >&2; exit 1; }

log() { printf '\n\033[1;36m▶ %s\033[0m\n' "$*"; }

# ── 1) 에뮬레이터 부팅 ──────────────────────────────────────
if [ "$NO_BOOT" = 0 ]; then
  if adb devices | grep -qw "device"; then
    log "에뮬레이터 이미 실행 중"
  else
    AVD="${AVD:-$(emulator -list-avds | head -1)}"
    [ -n "$AVD" ] || { echo "AVD 가 없음 — Android Studio 에서 하나 생성하세요." >&2; exit 1; }
    log "에뮬레이터 부팅: $AVD"
    emulator -avd "$AVD" -no-snapshot-save >/dev/null 2>&1 &
    adb wait-for-device
    log "부팅 완료 대기(sys.boot_completed)…"
    until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
  fi
fi

# ── 2) mock APK 빌드·설치 ───────────────────────────────────
if [ "$NO_INSTALL" = 0 ]; then
  log "mock APK 빌드·설치 (installMockDebug)"
  ( cd "$FRONTEND" && ./gradlew :app:installMockDebug -x lint )
fi

# ── 3) 실행 대상 결정 ───────────────────────────────────────
if [ -n "$STORY" ]; then
  TARGET="$FRONTEND/maestro/flows/$STORY"     # 단일 유저 스토리 폴더
  log "실행: 유저 스토리 '$STORY'"
else
  TARGET="$FRONTEND/maestro"                  # 전체 (config.yaml 의 flows/** 포함)
  log "실행: 전체 유저 스토리"
fi

# ── 4) Maestro 실행 (+선택 리포트) ─────────────────────────
if [ "$REPORT" = 1 ]; then
  OUT="$FRONTEND/maestro-out"
  rm -rf "$OUT"; mkdir -p "$OUT"
  log "리포트 생성: $OUT/report.html (+ debug)"
  maestro test "$TARGET" \
    --format HTML-DETAILED --output "$OUT/report.html" \
    --debug-output "$OUT/debug" --test-suite-name "CC-391 FE 인수 테스트"
  echo "리포트: $OUT/report.html"
else
  maestro test "$TARGET"
fi
