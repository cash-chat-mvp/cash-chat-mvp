# Docs → Confluence 자동 동기화 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `dev` 브랜치에 `docs/**/*.md` 파일이 변경될 때마다 GitHub Actions가 Confluence FCTC Space에 디렉토리 계층 그대로 페이지를 동기화하고, Discord로 결과를 알린다.

**Architecture:** Python 스크립트(`.github/scripts/sync_docs_to_confluence.py`)가 Confluence REST API v1을 직접 호출해 `docs/` 트리를 재귀 순회하며 디렉토리는 부모 페이지로, MD 파일은 자식 페이지로 upsert한다. GitHub Actions 워크플로우가 트리거·환경변수 주입·Discord 알림을 담당한다.

**Tech Stack:** Python 3.11, `markdown`, `requests`, GitHub Actions, Confluence REST API v1, Discord Webhook (curl + jq)

---

## 파일 구성

| 경로 | 역할 |
|------|------|
| `.github/scripts/sync_docs_to_confluence.py` | 핵심 동기화 로직 (Confluence API 헬퍼, 재귀 순회, 결과 JSON 출력) |
| `.github/workflows/sync-docs-to-confluence.yml` | 트리거, Python 실행, Discord 알림 |
| `tests/test_sync_docs.py` | 순수 함수 단위 테스트 |

---

## Task 1: Python 스크립트 — 순수 함수 단위 + Confluence API 헬퍼

**Files:**
- Create: `.github/scripts/sync_docs_to_confluence.py`
- Create: `tests/test_sync_docs.py`

- [ ] **Step 1: 테스트 파일 작성**

`tests/test_sync_docs.py`:
```python
import os
import sys

os.environ.update({
    "CONFLUENCE_BASE_URL": "https://example.atlassian.net",
    "CONFLUENCE_SPACE_KEY": "TEST",
    "JIRA_AUTH": "dGVzdA==",
    "GITHUB_ACTOR": "test-user",
    "COMMIT_SHA": "abc1234567890",
    "DOCS_DIR": "docs",
})

sys.path.insert(0, ".github/scripts")
from sync_docs_to_confluence import build_meta_banner, md_to_html


def test_build_meta_banner_contains_actor():
    result = build_meta_banner("test-user", "abc1234567890", "2026-06-01")
    assert "@test-user" in result
    assert "abc1234" in result
    assert "2026-06-01" in result


def test_build_meta_banner_contains_hr():
    result = build_meta_banner("user", "sha123", "2026-01-01")
    assert "<hr" in result


def test_md_to_html_heading():
    result = md_to_html("# Hello World")
    assert "<h1>" in result
    assert "Hello World" in result


def test_md_to_html_table():
    md = "| A | B |\n|---|---|\n| 1 | 2 |"
    result = md_to_html(md)
    assert "<table>" in result


def test_md_to_html_fenced_code():
    md = "```python\nprint('hi')\n```"
    result = md_to_html(md)
    assert "<code>" in result
```

- [ ] **Step 2: 테스트 실행 — FAIL 확인**

```bash
cd /Users/gudals-mac/Documents/nomade/cash-chat-mvp
pip install markdown requests pytest 2>/dev/null
python -m pytest tests/test_sync_docs.py -v 2>&1 | head -30
```

Expected: `ModuleNotFoundError: No module named 'sync_docs_to_confluence'`

- [ ] **Step 3: 스크립트 뼈대 + 순수 함수 구현**

`.github/scripts/sync_docs_to_confluence.py`:
```python
import os
import sys
import json
import re
import markdown
import requests
from pathlib import Path
from datetime import datetime, timezone

BASE_URL = os.environ["CONFLUENCE_BASE_URL"]
SPACE_KEY = os.environ["CONFLUENCE_SPACE_KEY"]
ROOT_PAGE_TITLE = os.environ.get("CONFLUENCE_ROOT_PAGE_TITLE", "Cash Chat Docs")
JIRA_AUTH = os.environ["JIRA_AUTH"]
ACTOR = os.environ.get("GITHUB_ACTOR", "unknown")
COMMIT_SHA = os.environ.get("COMMIT_SHA", "")
DOCS_DIR = Path(os.environ.get("DOCS_DIR", "docs"))

HEADERS = {
    "Authorization": f"Basic {JIRA_AUTH}",
    "Content-Type": "application/json",
    "Accept": "application/json",
}

_page_cache: dict[str, int] = {}


def build_meta_banner(actor: str, sha: str, date: str) -> str:
    short_sha = sha[:7] if sha else "unknown"
    return (
        f"<p><em>🤖 자동 동기화 | 작성자: @{actor} | {date} | {short_sha}</em></p>"
        "<hr />"
    )


def md_to_html(content: str) -> str:
    return markdown.markdown(
        content,
        extensions=["tables", "fenced_code", "nl2br"],
    )


def parse_pr_info(commit_message: str, repo: str) -> tuple[str, str, str]:
    """Returns (pr_number, pr_title, pr_url). Empty strings if not a merge commit."""
    lines = commit_message.strip().splitlines()
    match = re.search(r"#(\d+)", lines[0]) if lines else None
    if not match:
        return "", commit_message.strip(), ""
    number = match.group(1)
    title = lines[2].strip() if len(lines) >= 3 else f"PR #{number}"
    url = f"https://github.com/{repo}/pull/{number}"
    return number, title, url


def _api(method: str, path: str, **kwargs) -> dict:
    url = f"{BASE_URL}/wiki/rest/api{path}"
    resp = requests.request(method, url, headers=HEADERS, **kwargs)
    resp.raise_for_status()
    return resp.json()
```

- [ ] **Step 4: 테스트 재실행 — PASS 확인**

```bash
python -m pytest tests/test_sync_docs.py -v
```

Expected: 모든 테스트 PASS

- [ ] **Step 5: 커밋**

```bash
git add .github/scripts/sync_docs_to_confluence.py tests/test_sync_docs.py
git commit -m "feat(ci): Confluence 동기화 스크립트 뼈대 및 순수 함수 구현"
```

---

## Task 2: Python 스크립트 — Confluence API 연동 (find / create / update / upsert)

**Files:**
- Modify: `.github/scripts/sync_docs_to_confluence.py`

- [ ] **Step 1: `find_child_page`, `find_root_page` 함수 추가**

`_api` 함수 아래에 이어서 추가:
```python
def _find_root_page(title: str) -> dict | None:
    """Space 루트 레벨에서 제목으로 페이지 조회."""
    params = {
        "title": title,
        "spaceKey": SPACE_KEY,
        "type": "page",
        "expand": "version,ancestors",
    }
    data = _api("GET", "/content", params=params)
    for r in data.get("results", []):
        ancestors = r.get("ancestors", [])
        if len(ancestors) <= 1:
            return r
    return None


def _find_child_page(title: str, parent_id: int) -> dict | None:
    """특정 부모 페이지의 자식 중 제목으로 페이지 조회."""
    data = _api(
        "GET",
        f"/content/{parent_id}/child/page",
        params={"expand": "version", "limit": 200},
    )
    for page in data.get("results", []):
        if page["title"] == title:
            return page
    return None
```

- [ ] **Step 2: `_create_page`, `_update_page` 함수 추가**

```python
def _create_page(title: str, body_html: str, parent_id: int | None) -> dict:
    payload: dict = {
        "type": "page",
        "title": title,
        "space": {"key": SPACE_KEY},
        "body": {"storage": {"value": body_html, "representation": "storage"}},
    }
    if parent_id is not None:
        payload["ancestors"] = [{"id": parent_id}]
    return _api("POST", "/content", json=payload)


def _update_page(page_id: int, title: str, body_html: str, current_version: int) -> dict:
    payload = {
        "type": "page",
        "title": title,
        "version": {"number": current_version + 1},
        "body": {"storage": {"value": body_html, "representation": "storage"}},
    }
    return _api("PUT", f"/content/{page_id}", json=payload)
```

- [ ] **Step 3: `get_or_create_dir_page`, `upsert_md_page` 추가**

```python
def get_or_create_dir_page(title: str, parent_id: int | None) -> int:
    """디렉토리용 컨테이너 페이지 ID 반환. 없으면 생성."""
    cache_key = f"{title}::{parent_id}"
    if cache_key in _page_cache:
        return _page_cache[cache_key]
    existing = _find_root_page(title) if parent_id is None else _find_child_page(title, parent_id)
    if existing:
        pid = int(existing["id"])
    else:
        result = _create_page(title, "", parent_id)
        pid = int(result["id"])
    _page_cache[cache_key] = pid
    return pid


def upsert_md_page(title: str, body_html: str, parent_id: int) -> tuple[str, str]:
    """MD 파일 페이지 upsert. Returns (action, page_url)."""
    existing = _find_child_page(title, parent_id)
    if existing:
        version = existing["version"]["number"]
        result = _update_page(int(existing["id"]), title, body_html, version)
        action = "updated"
    else:
        result = _create_page(title, body_html, parent_id)
        action = "created"
    url = f"{BASE_URL}/wiki{result['_links']['webui']}"
    return action, url
```

- [ ] **Step 4: `sync_md_file`, `sync_directory`, `main` 추가**

```python
def sync_md_file(md_path: Path, parent_id: int) -> dict:
    """단일 MD 파일을 Confluence에 동기화. Returns result dict."""
    title = md_path.stem
    raw = md_path.read_text(encoding="utf-8")
    date = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    banner = build_meta_banner(ACTOR, COMMIT_SHA, date)
    body_html = banner + md_to_html(raw)
    action, url = upsert_md_page(title, body_html, parent_id)
    return {"title": title, "action": action, "url": url}


def sync_directory(dir_path: Path, parent_id: int, results: list) -> None:
    """docs/ 트리를 재귀 순회하며 동기화."""
    for item in sorted(dir_path.iterdir()):
        if item.is_dir():
            dir_page_id = get_or_create_dir_page(item.name, parent_id)
            sync_directory(item, dir_page_id, results)
        elif item.suffix == ".md":
            page_result = sync_md_file(item, parent_id)
            results.append(page_result)


def main() -> None:
    root_id = get_or_create_dir_page(ROOT_PAGE_TITLE, None)
    results: list[dict] = []
    sync_directory(DOCS_DIR, root_id, results)
    print(json.dumps({"status": "success", "pages": results}))


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(json.dumps({"status": "error", "error": str(exc)}), file=sys.stderr)
        sys.exit(1)
```

- [ ] **Step 5: 기존 테스트 다시 실행 — 여전히 PASS 확인**

```bash
python -m pytest tests/test_sync_docs.py -v
```

Expected: 모든 테스트 PASS (새 함수는 Confluence 연동이므로 단위 테스트 외 영역)

- [ ] **Step 6: 커밋**

```bash
git add .github/scripts/sync_docs_to_confluence.py
git commit -m "feat(ci): Confluence API 헬퍼 및 재귀 동기화 로직 구현"
```

---

## Task 3: GitHub Actions 워크플로우 작성

**Files:**
- Create: `.github/workflows/sync-docs-to-confluence.yml`

- [ ] **Step 1: 워크플로우 파일 생성**

`.github/workflows/sync-docs-to-confluence.yml`:
```yaml
name: Confluence 문서 동기화

on:
  push:
    branches: [dev]
    paths:
      - 'docs/**/*.md'

jobs:
  sync:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4
        with:
          fetch-depth: 2

      - name: Set up Python
        uses: actions/setup-python@v5
        with:
          python-version: '3.11'

      - name: 의존 라이브러리 설치
        run: pip install markdown requests

      - name: PR 정보 파싱
        id: pr_info
        env:
          COMMIT_MSG: ${{ github.event.head_commit.message }}
          REPO: ${{ github.repository }}
        run: |
          PR_NUMBER=$(echo "$COMMIT_MSG" | grep -oE '#[0-9]+' | head -1 | tr -d '#')
          if [ -n "$PR_NUMBER" ]; then
            PR_TITLE=$(echo "$COMMIT_MSG" | sed -n '3p')
            PR_URL="https://github.com/${REPO}/pull/${PR_NUMBER}"
            PR_DISPLAY="${PR_TITLE:-PR #${PR_NUMBER}}"
          else
            PR_URL=""
            PR_DISPLAY=$(echo "$COMMIT_MSG" | head -1)
          fi
          echo "pr_url=${PR_URL}" >> $GITHUB_OUTPUT
          echo "pr_display=${PR_DISPLAY}" >> $GITHUB_OUTPUT

      - name: Confluence 동기화 실행
        id: sync
        env:
          CONFLUENCE_BASE_URL: https://moneyfactoryslave.atlassian.net
          CONFLUENCE_SPACE_KEY: FCTC
          CONFLUENCE_ROOT_PAGE_TITLE: Cash Chat Docs
          JIRA_AUTH: ${{ secrets.JIRA_AUTH }}
          GITHUB_ACTOR: ${{ github.actor }}
          COMMIT_SHA: ${{ github.sha }}
          DOCS_DIR: docs
        run: |
          RESULT=$(python .github/scripts/sync_docs_to_confluence.py)
          echo "result=${RESULT}" >> $GITHUB_OUTPUT

      - name: Discord 성공 알림
        if: success()
        env:
          DISCORD_WIKI_WEBHOOK: ${{ secrets.DISCORD_WIKI_WEBHOOK }}
          RESULT: ${{ steps.sync.outputs.result }}
          ACTOR: ${{ github.actor }}
          PR_URL: ${{ steps.pr_info.outputs.pr_url }}
          PR_DISPLAY: ${{ steps.pr_info.outputs.pr_display }}
        run: |
          if [ -z "$DISCORD_WIKI_WEBHOOK" ]; then
            echo "ℹ️ DISCORD_WIKI_WEBHOOK 없음 — 알림 건너뜀"
            exit 0
          fi

          PAGE_LIST=$(echo "$RESULT" | jq -r '
            .pages[] |
            if .action == "created" then
              "**" + .title + "** (신규) — [바로가기](" + .url + ")"
            else
              "**" + .title + "** — [바로가기](" + .url + ")"
            end
          ' | head -20 | awk '{printf "%s\n", $0}')

          if [ -z "$PAGE_LIST" ]; then
            PAGE_LIST="변경된 문서가 없습니다."
          fi

          PAYLOAD=$(jq -n \
            --arg actor "$ACTOR" \
            --arg pr_display "$PR_DISPLAY" \
            --arg pr_url "$PR_URL" \
            --arg pages "$PAGE_LIST" \
            --arg ts "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
            '{embeds: [{
              color: 5763719,
              title: "✅ Confluence 업데이트 완료",
              fields: [
                {name: "작성자", value: ("`@" + $actor + "`"), inline: true},
                {name: "MR", value: (if $pr_url != "" then ("[" + $pr_display + "](" + $pr_url + ")") else $pr_display end), inline: true},
                {name: "업데이트 문서", value: $pages, inline: false}
              ],
              footer: {text: "Wild Nomad Coder Workspace"},
              timestamp: $ts
            }]}')

          curl -sS -X POST "$DISCORD_WIKI_WEBHOOK" \
            -H "Content-Type: application/json" \
            -d "$PAYLOAD"

      - name: Discord 실패 알림
        if: failure()
        env:
          DISCORD_WIKI_WEBHOOK: ${{ secrets.DISCORD_WIKI_WEBHOOK }}
          ACTOR: ${{ github.actor }}
          PR_URL: ${{ steps.pr_info.outputs.pr_url }}
          PR_DISPLAY: ${{ steps.pr_info.outputs.pr_display }}
          RUN_URL: ${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}
        run: |
          if [ -z "$DISCORD_WIKI_WEBHOOK" ]; then
            echo "ℹ️ DISCORD_WIKI_WEBHOOK 없음 — 알림 건너뜀"
            exit 0
          fi

          PAYLOAD=$(jq -n \
            --arg actor "$ACTOR" \
            --arg pr_display "$PR_DISPLAY" \
            --arg pr_url "$PR_URL" \
            --arg run_url "$RUN_URL" \
            --arg ts "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
            '{embeds: [{
              color: 15548997,
              title: "🚨 Confluence 업데이트 실패",
              fields: [
                {name: "작성자", value: ("`@" + $actor + "`"), inline: true},
                {name: "MR", value: (if $pr_url != "" then ("[" + $pr_display + "](" + $pr_url + ")") else $pr_display end), inline: true},
                {name: "안내", value: "문서 자동 동기화에 실패했습니다.\n워크플로우를 재실행하거나 Confluence에 직접 작성해주세요. 🙏", inline: false},
                {name: "Actions 로그", value: ("[바로가기](" + $run_url + ")"), inline: false}
              ],
              footer: {text: "Wild Nomad Coder Workspace"},
              timestamp: $ts
            }]}')

          curl -sS -X POST "$DISCORD_WIKI_WEBHOOK" \
            -H "Content-Type: application/json" \
            -d "$PAYLOAD"
```

- [ ] **Step 2: YAML 문법 검증**

```bash
python -c "import yaml; yaml.safe_load(open('.github/workflows/sync-docs-to-confluence.yml'))" && echo "YAML OK"
```

Expected: `YAML OK`

- [ ] **Step 3: 커밋**

```bash
git add .github/workflows/sync-docs-to-confluence.yml
git commit -m "feat(ci): Confluence 동기화 GitHub Actions 워크플로우 추가"
```

---

## Task 4: GitHub Secret 등록 및 첫 실행 검증

**Files:** 없음 (GitHub 레포 설정)

- [ ] **Step 1: GitHub Secret `DISCORD_WIKI_WEBHOOK` 등록**

GitHub 레포 → Settings → Secrets and variables → Actions → New repository secret:
- Name: `DISCORD_WIKI_WEBHOOK`
- Value: Discord 채널 Webhook URL

> `JIRA_AUTH`는 이미 존재하므로 추가 불필요.

- [ ] **Step 2: `dev` 브랜치에 docs 파일 변경 커밋 push**

```bash
# 테스트용 — 기존 MD 파일에 공백 한 줄 추가
echo "" >> docs/adr/000-adopting-adr.md
git add docs/adr/000-adopting-adr.md
git commit -m "docs: Confluence 동기화 첫 실행 테스트"
git push origin dev
```

- [ ] **Step 3: Actions 탭에서 워크플로우 실행 확인**

GitHub → Actions → "Confluence 문서 동기화" 워크플로우가 트리거됐는지 확인.

- [ ] **Step 4: Confluence 페이지 생성 확인**

`https://moneyfactoryslave.atlassian.net/wiki/spaces/FCTC` 에서:
- "Cash Chat Docs" 루트 페이지 존재 확인
- `adr` → `000-adopting-adr` 계층 확인
- 페이지 상단 메타 배너(🤖 자동 동기화 | 작성자: @...) 확인

- [ ] **Step 5: Discord 알림 확인**

Discord 채널에서:
- Embed 색상 초록, 제목 "✅ Confluence 업데이트 완료"
- 작성자, MR, 업데이트 문서 목록, 바로가기 링크 확인
- 푸터 "Wild Nomad Coder Workspace" + 타임스탬프 확인

- [ ] **Step 6: 실패 케이스 검증 (선택)**

`JIRA_AUTH`를 임시로 잘못된 값으로 교체해 실패 알림이 Discord에 오는지 확인 후 원복.

- [ ] **Step 7: 테스트용 커밋 되돌리기 (필요 시)**

```bash
git revert HEAD --no-edit
git push origin dev
```
