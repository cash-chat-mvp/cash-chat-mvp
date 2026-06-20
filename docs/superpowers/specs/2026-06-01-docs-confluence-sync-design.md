# Docs → Confluence 자동 동기화 설계

**날짜**: 2026-06-01  
**상태**: 확정

---

## 목표

`dev` 브랜치에 `docs/` 하위 MD 파일이 변경될 때마다 GitHub Actions가 자동으로 Confluence FCTC Space에 동기화하고, 결과를 Discord로 알린다.

---

## 아키텍처

```
dev 브랜치 push (docs/**/*.md 변경 감지)
        ↓
GitHub Actions: sync-docs-to-confluence.yml
        ↓
.github/scripts/sync_docs_to_confluence.py
  1. git diff로 변경된 MD 파일 목록 추출
  2. docs/ 전체 트리 순회
  3. 디렉토리 → Confluence 부모 페이지 생성/조회 (캐싱)
  4. MD → HTML 변환 후 페이지 upsert
  5. 결과 수집 (생성/업데이트/실패 목록)
        ↓
Discord 알림 (DISCORD_WIKI_WEBHOOK)
  - 성공: 변경 페이지 목록 + Confluence 링크 + 커밋 정보 + 작성자
  - 실패: 에러 상세 내용 + 작성자
```

---

## Confluence 페이지 계층 구조

`docs/` 디렉토리 경로를 Confluence 부모-자식 관계로 1:1 매핑한다.

```
FCTC Space
  └── Cash Chat Docs  (루트 부모 페이지)
        ├── adr
        │     └── 000-adopting-adr
        ├── planning
        │     ├── 00-overview
        │     └── 01-ai-chat
        ├── features
        │     └── reward
        │           ├── spec
        │           ├── rfc
        │           └── tasks
        ├── specs
        │     └── auth
        │           ├── spec
        │           └── tasks
        └── superpowers
              ├── plans
              │     └── ...
              └── specs
                    └── ...
```

- **디렉토리** → Confluence 부모 페이지 (컨테이너 역할)
- **MD 파일** → Confluence 자식 페이지 (MD → HTML 변환)

### 페이지 제목 규칙

Confluence는 Space 내 제목이 유일해야 하므로(`spec.md`가 여러 디렉토리에 존재) 다음 규칙으로 제목을 결정한다:

- MD 파일: 본문 첫 **H1 헤딩** → 없으면 파일명(stem)
- 디렉토리: 디렉토리명
- 같은 base 제목이 둘 이상이면 부모 디렉토리명을 괄호로 덧붙여 충돌 회피 (예: `specs (superpowers)`)
- docs 전체를 스캔해 결정적으로 계산하므로, 변경분만 동기화해도 제목이 안정적

### 페이지 식별 (제목과 분리)

제목(H1)은 사람이 수정할 수 있어 식별자로 부적합하다. 따라서 **루트 페이지의 content property `syncIndex`** 에 `{파일경로: 페이지ID}` JSON 매핑을 저장하고, **제목이 아니라 페이지 ID로 찾아 업데이트**한다.

- H1을 수정해도 같은 페이지가 갱신되고 제목만 새 H1으로 반영됨 (고아 페이지 방지)
- 인덱스가 비어있거나 페이지가 삭제된 경우 제목으로 자가복구 조회 후, 없으면 생성
- 동작: ID로 페이지 조회 → 있으면 update, 없으면 create

---

## 페이지 본문 메타 배너

각 페이지 상단에 자동 삽입:

```
┌─────────────────────────────────────────────────────────────┐
│ 🤖 자동 동기화 | 작성자: @github-actor | 2026-06-01 | abc1234 │
└─────────────────────────────────────────────────────────────┘
```

---

## 파일 구성

```
.github/
  workflows/
    sync-docs-to-confluence.yml   # 트리거, 환경변수 주입, Discord 알림
  scripts/
    sync_docs_to_confluence.py    # 동기화 핵심 로직
```

### sync-docs-to-confluence.yml

**트리거:**
```yaml
on:
  push:
    branches: [dev]
    paths: ['docs/**/*.md']
```

**환경변수:**
- `CONFLUENCE_BASE_URL`: `https://moneyfactoryslave.atlassian.net`
- `CONFLUENCE_SPACE_KEY`: `FCTC`
- `CONFLUENCE_ROOT_PAGE_TITLE`: `Cash Chat Docs`
- `JIRA_AUTH`: `${{ secrets.JIRA_AUTH }}` (Basic Auth 재사용)
- `DISCORD_WIKI_WEBHOOK`: `${{ secrets.DISCORD_WIKI_WEBHOOK }}`
- `GITHUB_ACTOR`: `${{ github.actor }}`
- `COMMIT_SHA`: `${{ github.sha }}`
- `COMMIT_MESSAGE`: `${{ github.event.head_commit.message }}`

### sync_docs_to_confluence.py

**주요 함수:**

| 함수 | 역할 |
|------|------|
| `get_or_create_page(title, parent_id, space_key)` | 페이지 조회 또는 생성 |
| `upsert_page(title, body_html, parent_id, space_key)` | 존재하면 업데이트, 없으면 생성 |
| `md_to_html(md_content)` | Markdown → HTML 변환 |
| `sync_directory(dir_path, parent_id)` | 재귀적 디렉토리 순회 |
| `build_meta_banner(actor, sha, date)` | 상단 메타 배너 HTML 생성 |

**의존 라이브러리:**
- `markdown` — MD → HTML 변환
- `requests` — Confluence REST API 호출 (표준 라이브러리 대체)

---

## Discord 알림 스펙

PR 번호는 머지 커밋 메시지(`Merge pull request #N from ...`)에서 파싱해 PR URL 자동 구성.

### 성공 시 (embed)

```
색상: 초록 (5763719)
제목: ✅ Confluence 업데이트 완료
필드:
  - 작성자: @github-actor
  - MR: [PR 제목](https://github.com/{repo}/pull/{N})
  - 업데이트 문서:
      000-adopting-adr — [바로가기](confluence link)
      00-overview — [바로가기](confluence link)
      ...
푸터: Wild Nomad Coder Workspace | <timestamp>
```

### 실패 시 (embed)

```
색상: 빨강 (15548997)
제목: 🚨 Confluence 업데이트 실패
필드:
  - 작성자: @github-actor
  - MR: [PR 제목](https://github.com/{repo}/pull/{N})
  - 안내: "문서 자동 동기화에 실패했습니다.
           워크플로우를 재실행하거나 Confluence에 직접 작성해주세요. 🙏"
  - Actions 로그: [바로가기](actions run url)
푸터: Wild Nomad Coder Workspace | <timestamp>
```

---

## 인증

| 시크릿 | 용도 | 상태 |
|--------|------|------|
| `JIRA_AUTH` | Confluence Basic Auth (`base64(email:token)`) | 기존 재사용 |
| `DISCORD_WIKI_WEBHOOK` | Discord 알림 | 신규 추가 필요 |

---

## 제약 및 결정 사항

- Confluence 페이지 소유자는 `JIRA_AUTH` 토큰 소유자(서비스 계정)로 고정됨. 작성자 정보는 페이지 본문 배너와 Discord 알림으로 표시.
- `docs/design-preview/` 하위 HTML 파일은 MD가 아니므로 동기화 대상에서 자동 제외됨.
- 평소엔 변경된 MD만 동기화(`github.event.before..sha` diff). `workflow_dispatch`로 전체 재동기화 가능.
- 파일을 rename하면 새 경로 키로 인식되어 새 페이지가 생성되고 기존 페이지는 남는다(수동 정리 필요).
