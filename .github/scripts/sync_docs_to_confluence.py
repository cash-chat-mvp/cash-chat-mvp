from __future__ import annotations

import os
import sys
import json
import re
import markdown
import requests
from collections import Counter
from pathlib import Path
from datetime import datetime, timezone

BASE_URL = os.environ["CONFLUENCE_BASE_URL"]
SPACE_KEY = os.environ["CONFLUENCE_SPACE_KEY"]
ROOT_PAGE_TITLE = os.environ.get("CONFLUENCE_ROOT_PAGE_TITLE", "Cash Chat Docs")
JIRA_AUTH = os.environ["JIRA_AUTH"]
ACTOR = os.environ.get("GITHUB_ACTOR", "unknown")
COMMIT_SHA = os.environ.get("COMMIT_SHA", "")
DOCS_DIR = Path(os.environ.get("DOCS_DIR", "docs"))

# 루트 페이지에 저장하는 "파일경로 → Confluence 페이지 ID" 매핑 속성 키.
# 제목이 아니라 이 ID로 페이지를 찾아 업데이트하므로 H1(제목) 변경/파일 rename에
# 영향받지 않고 동일 페이지를 안정적으로 갱신한다.
SYNC_INDEX_KEY = "syncIndex"
DIR_BODY = "<p><em>📁 디렉토리 컨테이너</em></p>"

HEADERS = {
    "Authorization": f"Basic {JIRA_AUTH}",
    "Content-Type": "application/json",
    "Accept": "application/json",
}

# path(str) -> Confluence 페이지 제목. build_title_map()이 채운다.
_title_map: dict[str, str] = {}
# path(str) -> 페이지 ID. 루트 속성에서 로드/저장.
_index: dict[str, int] = {}
# 루트 syncIndex 속성의 현재 버전. None이면 아직 속성이 없다는 뜻.
_index_version: int | None = None
# 한 실행 내 디렉토리 페이지 ID 캐시.
_dir_cache: dict[str, int] = {}


def build_meta_banner(actor: str, sha: str, date: str) -> str:
    short_sha = sha[:7] if sha else "unknown"
    return (
        f"<p><em>🤖 자동 동기화 | 작성자: @{actor} | {date} | {short_sha}</em></p>"
        "<hr />"
    )


def md_to_html(content: str) -> str:
    html = markdown.markdown(
        content,
        extensions=["tables", "fenced_code", "nl2br"],
    )
    # fenced_code가 생성하는 class="language-*" 속성을 제거하여
    # Confluence와의 호환성을 높이고 <code> 태그를 단순하게 유지
    html = re.sub(r'<code class="language-[^"]*">', "<code>", html)
    return html


def extract_h1_title(raw: str) -> str | None:
    """Markdown 본문에서 첫 H1 헤딩 텍스트를 반환. 없으면 None."""
    for line in raw.splitlines():
        stripped = line.strip()
        if stripped.startswith("# "):
            return stripped[2:].strip()
    return None


def build_title_map(docs_dir: Path) -> dict[str, str]:
    """docs 트리 전체를 스캔해 각 디렉토리/MD 파일의 Confluence 제목을 결정.

    - MD 파일: 첫 H1 헤딩 → 없으면 파일명(stem)
    - 디렉토리: 디렉토리명
    - 같은 base 제목이 둘 이상이면 부모 디렉토리명을 괄호로 덧붙여 충돌 회피
    - 그래도 겹치면 일련번호로 분리

    정렬된 순회로 매 실행 동일한 결과를 보장한다.
    """
    entries: list[tuple[Path, str, str]] = []  # (path, base_title, parent_name)
    for p in sorted(docs_dir.rglob("*")):
        if p.is_dir():
            entries.append((p, p.name, p.parent.name))
        elif p.suffix == ".md":
            base = extract_h1_title(p.read_text(encoding="utf-8")) or p.stem
            entries.append((p, base, p.parent.name))

    freq = Counter(base for _, base, _ in entries)
    used: set[str] = set()
    title_map: dict[str, str] = {}
    for p, base, parent in entries:
        title = base if freq[base] == 1 else f"{base} ({parent})"
        candidate, i = title, 2
        while candidate in used:
            candidate = f"{title} ({i})"
            i += 1
        used.add(candidate)
        title_map[str(p)] = candidate
    return title_map


def _api(method: str, path: str, **kwargs) -> dict:
    url = f"{BASE_URL}/wiki/rest/api{path}"
    resp = requests.request(method, url, headers=HEADERS, **kwargs)
    resp.raise_for_status()
    return resp.json()


def _status_code(exc: requests.HTTPError) -> int | None:
    return exc.response.status_code if exc.response is not None else None


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
    """특정 부모 페이지의 자식 중 제목으로 페이지 조회(페이지네이션 포함)."""
    start = 0
    limit = 200
    while True:
        data = _api(
            "GET",
            f"/content/{parent_id}/child/page",
            params={"expand": "version", "limit": limit, "start": start},
        )
        results = data.get("results", [])
        for page in results:
            if page["title"] == title:
                return page
        if len(results) < limit:
            return None
        start += limit


def _get_page(page_id: int) -> dict | None:
    """ID로 페이지 조회. 삭제됐으면(404) None."""
    try:
        return _api("GET", f"/content/{page_id}", params={"expand": "version"})
    except requests.HTTPError as exc:
        if _status_code(exc) == 404:
            return None
        raise


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


def load_index(root_id: int) -> None:
    """루트 페이지의 syncIndex 속성에서 파일경로→페이지ID 매핑을 로드."""
    global _index, _index_version
    try:
        data = _api("GET", f"/content/{root_id}/property/{SYNC_INDEX_KEY}")
    except requests.HTTPError as exc:
        if _status_code(exc) == 404:
            _index, _index_version = {}, None
            return
        raise
    _index = {str(k): int(v) for k, v in data.get("value", {}).items()}
    _index_version = data["version"]["number"]


def save_index(root_id: int) -> None:
    """변경된 매핑을 루트 페이지 속성에 저장(없으면 생성, 있으면 버전 증가)."""
    if _index_version is None:
        _api(
            "POST",
            f"/content/{root_id}/property",
            json={"key": SYNC_INDEX_KEY, "value": _index},
        )
    else:
        _api(
            "PUT",
            f"/content/{root_id}/property/{SYNC_INDEX_KEY}",
            json={"key": SYNC_INDEX_KEY, "value": _index, "version": {"number": _index_version + 1}},
        )


def ensure_root() -> int:
    """루트 페이지를 보장하고 ID 반환. 제목이 고정이라 인덱스 없이 제목으로 조회."""
    existing = _find_root_page(ROOT_PAGE_TITLE)
    if existing:
        return int(existing["id"])
    result = _create_page(ROOT_PAGE_TITLE, "<p><em>Cash Chat 문서 자동 동기화 루트</em></p>", None)
    return int(result["id"])


def _page_url(result: dict) -> str:
    return f"{BASE_URL}/wiki{result['_links']['webui']}"


def sync_dir_page(dir_path: Path, title: str, parent_id: int) -> int:
    """디렉토리 컨테이너 페이지를 ID 기반으로 보장. 제목이 바뀌었으면 갱신."""
    key = str(dir_path)
    if key in _dir_cache:
        return _dir_cache[key]

    page = None
    page_id = _index.get(key)
    if page_id is not None:
        page = _get_page(page_id)
    if page is None:
        # 인덱스 미스/고아 → 제목으로 자가복구 조회
        page = _find_child_page(title, parent_id)

    if page is not None:
        pid = int(page["id"])
        if page["title"] != title:
            _update_page(pid, title, DIR_BODY, page["version"]["number"])
    else:
        result = _create_page(title, DIR_BODY, parent_id)
        pid = int(result["id"])

    _index[key] = pid
    _dir_cache[key] = pid
    return pid


def ensure_dir_chain(md_path: Path, root_id: int) -> int:
    """MD 파일의 부모 디렉토리 체인을 Confluence에 보장하고 직속 부모 ID 반환."""
    parent_id = root_id
    accum = DOCS_DIR
    for part in md_path.parent.relative_to(DOCS_DIR).parts:
        accum = accum / part
        parent_id = sync_dir_page(accum, _title_map[str(accum)], parent_id)
    return parent_id


def sync_md_file(md_path: Path, parent_id: int) -> dict:
    """단일 MD 파일을 ID 기반으로 동기화. 제목(H1)이 바뀌어도 같은 페이지 갱신."""
    key = str(md_path)
    title = _title_map[key]
    raw = md_path.read_text(encoding="utf-8")
    date = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    body_html = build_meta_banner(ACTOR, COMMIT_SHA, date) + md_to_html(raw)

    page = None
    page_id = _index.get(key)
    if page_id is not None:
        page = _get_page(page_id)  # 삭제됐으면 None
    if page is None:
        # 인덱스 미스/고아 → 제목으로 자가복구 조회
        page = _find_child_page(title, parent_id)

    if page is not None:
        pid = int(page["id"])
        result = _update_page(pid, title, body_html, page["version"]["number"])
        action = "updated"
    else:
        result = _create_page(title, body_html, parent_id)
        pid = int(result["id"])
        action = "created"

    _index[key] = pid
    return {"title": title, "action": action, "url": _page_url(result)}


def select_targets() -> list[Path]:
    """동기화 대상 MD 파일 목록 결정.

    - SYNC_ALL=true (workflow_dispatch): docs 전체
    - 그 외: CHANGED_FILES(공백 구분)의 MD 파일만
    삭제된 파일은 제외한다.
    """
    if os.environ.get("SYNC_ALL", "").lower() == "true":
        candidates = [str(p) for p in DOCS_DIR.rglob("*.md")]
    else:
        candidates = [
            c for c in os.environ.get("CHANGED_FILES", "").split() if c.endswith(".md")
        ]
    targets = []
    for rel in sorted(set(candidates)):
        path = Path(rel)
        if path.exists():
            targets.append(path)
    return targets


def main() -> None:
    global _title_map
    _title_map = build_title_map(DOCS_DIR)

    targets = select_targets()
    results: list[dict] = []
    if targets:
        root_id = ensure_root()
        load_index(root_id)
        for md_path in targets:
            parent_id = ensure_dir_chain(md_path, root_id)
            results.append(sync_md_file(md_path, parent_id))
        save_index(root_id)

    print(json.dumps({"status": "success", "pages": results}))


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(json.dumps({"status": "error", "error": str(exc)}), file=sys.stderr)
        sys.exit(1)
