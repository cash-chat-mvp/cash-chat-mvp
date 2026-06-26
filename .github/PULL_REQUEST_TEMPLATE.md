> 확인 목록 (Check List)

병합 요청(Merge Request)을 하기 전에, 아래 항목을 확인해 주세요.

- [ ] **Local** 환경에서 **Build** 성공 및 **Test** 검증
- [ ] 제목에 **Jira**에서 **Ticket**의 **ID** 포함

---

> AI 코드 리뷰 명령어 · 자세한 사용법: [docs/review/ai-code-review.md](../docs/review/ai-code-review.md)
<!-- 동기화 유지: 같은 표가 .github/workflows/pr-review.yml(describe job), .github/scripts/review/lib_help.sh, docs/review/ai-code-review.md 에도 있음 -->

| 명령어 | 설명 | 사용 위치 |
|---|---|---|
| `/gemini-review` | Gemini 코드 리뷰 (PR을 열면 자동 1회 실행, 재요청 시 입력) | PR 코멘트 |
| `/openai-review` | OpenAI 심층 리뷰 (수동 · 비용 발생) | PR 코멘트 |
| `/ask 질문내용` | AI 답변/코드에 후속 질문 (저비용 모델) | PR · 라인 코멘트 |
| `/resolve` | AI가 반영 여부 판단 → Jira 서브태스크 생성 + 스레드 해결 | 라인 코멘트 답글 |
| `/auto-review-stop` | 이후 푸시의 자동 코드 제안(/improve) 중단 | PR 코멘트 |
| `/auto-review-start` | 중단한 자동 코드 제안(/improve) 재개 | PR 코멘트 |
| `/help` | 명령어 도움말 표시 | PR 코멘트 |
| `@coderabbitai review` | CodeRabbit 리뷰 (수동 · 비용 발생) | PR 코멘트 |

> ℹ️ 모든 AI 리뷰는 기본적으로 **작성자/요청자 개인 키**로 동작하고, 한도(429) 도달 시에만 **공통 키로 자동 폴백**돼요(미등록 시 공통 키). PR을 열면 자동 리뷰 1회, 이후 **푸시마다 자동 코드 제안(/improve) + 해결된 코멘트 자동 정리**가 실행됩니다. 푸시 자동 제안은 `/auto-review-stop` 으로 끄고 `/auto-review-start` 로 다시 켤 수 있어요(스레드 정리는 계속 동작).
