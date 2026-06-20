#!/usr/bin/env bash
# /help 명령어 레퍼런스 카드(명령어 표 단일 소스). lib_cards.sh 를 source 한 뒤 사용.
# ⚠️ 동기화 유지: 같은 표가 .github/PULL_REQUEST_TEMPLATE.md, .github/workflows/pr-description.yml,
#                docs/review/ai-code-review.md 에도 있다. 바꾸면 네 곳을 함께 맞출 것.
render_help_card() {
  local body
  body=$(cat <<'MD'
PR 코멘트에 아래 명령어를 입력하면 AI 리뷰를 사용할 수 있어요.

| 명령어 | 설명 | 사용 위치 |
|---|---|---|
| `/gemini-review` | Gemini 코드 리뷰 (PR을 열면 자동 1회 실행, 재요청 시 입력) | PR 코멘트 |
| `/openai-review` | OpenAI 심층 리뷰 (수동 · 비용 발생) | PR 코멘트 |
| `/ask 질문내용` | AI 답변/코드에 후속 질문 (저비용 모델) | PR · 라인 코멘트 |
| `/resolve` | AI가 반영 여부 판단 → Jira 서브태스크 생성 + 스레드 해결 | 라인 코멘트 답글 |
| `/help` | 이 도움말 표시 | PR 코멘트 |
| `@coderabbitai review` | CodeRabbit 리뷰 (수동 · 비용 발생) | PR 코멘트 |

ℹ️ 모든 AI 리뷰는 기본적으로 **작성자/요청자 개인 키**로 동작하고, 한도(429) 도달 시에만 **공통 키로 자동 폴백**해요(미등록 사용자는 처음부터 공통 키). PR을 열면 자동 리뷰 1회, 이후 **푸시마다 변경 라인 코드 제안(/improve) + 해결된 코멘트 자동 정리**가 실행됩니다. 오늘 사용량은 `/help` 하단 표에서 확인하세요.
MD
)
  render_card help '🛟 AI 코드 리뷰 명령어 도움말' "$body" ''
}
