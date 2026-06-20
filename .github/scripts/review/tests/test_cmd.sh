#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")/.." && pwd)"
. "$DIR/tests/_assert.sh"
. "$DIR/lib_cmd.sh"
echo "test_cmd"

# 양성: 명령어가 맨 앞(선행 공백 허용), 인자 유무 무관
detect_command "$(printf '/gemini-review')" gemini-review; assert_rc $? 0 "정확히 명령어만"
detect_command "$(printf '  /gemini-review')" gemini-review; assert_rc $? 0 "선행 공백 허용"
detect_command "$(printf '/ask 이거 왜 이렇게 했나요?')" ask; assert_rc $? 0 "ask + 인자"
detect_command "$(printf '맥락 설명\n/resolve 추후 처리')" resolve; assert_rc $? 0 "둘째 줄 맨 앞 명령"

# 음성: 산문 중간 언급 / 유사어 / 코드블록 속
detect_command "$(printf '/ask 가 왜 안되죠? 라고 묻고 싶을 때')" ask; assert_rc $? 0 "맨 앞이면 인자 취급(양성)"
detect_command "$(printf '왜 /ask 가 안되죠?')" ask; assert_rc $? 1 "문장 중간 언급 → 무시"
detect_command "$(printf '/asking about something')" ask; assert_rc $? 1 "유사어(/asking) → 무시"
detect_command "$(printf '예시: \`/review\` 를 칩니다')" review; assert_rc $? 1 "백틱/문장 속 → 무시"

t_summary
