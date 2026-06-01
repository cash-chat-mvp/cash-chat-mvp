import os
import sys

import requests

os.environ.update({
    "CONFLUENCE_BASE_URL": "https://example.atlassian.net",
    "CONFLUENCE_SPACE_KEY": "TEST",
    "JIRA_AUTH": "dGVzdA==",
    "GITHUB_ACTOR": "test-user",
    "COMMIT_SHA": "abc1234567890",
    "DOCS_DIR": "docs",
})

sys.path.insert(0, ".github/scripts")
import sync_docs_to_confluence as sdc
from sync_docs_to_confluence import (
    build_meta_banner,
    md_to_html,
    extract_h1_title,
    build_title_map,
)


def _http_error(status: int) -> requests.HTTPError:
    resp = requests.Response()
    resp.status_code = status
    err = requests.HTTPError(response=resp)
    return err


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


def test_extract_h1_title():
    assert extract_h1_title("# 제목입니다\n본문") == "제목입니다"


def test_extract_h1_title_skips_non_heading():
    assert extract_h1_title("앞 문장\n\n# 진짜 제목\n") == "진짜 제목"


def test_extract_h1_title_none_when_absent():
    assert extract_h1_title("헤딩 없음\n## H2뿐") is None


def test_build_title_map_uses_h1(tmp_path):
    docs = tmp_path / "docs"
    (docs / "a").mkdir(parents=True)
    (docs / "b").mkdir()
    (docs / "a" / "spec.md").write_text("# Alpha Spec\n", encoding="utf-8")
    (docs / "b" / "spec.md").write_text("# Beta Spec\n", encoding="utf-8")
    tm = build_title_map(docs)
    # 파일명은 둘 다 spec이지만 H1이 달라 충돌하지 않는다
    assert tm[str(docs / "a" / "spec.md")] == "Alpha Spec"
    assert tm[str(docs / "b" / "spec.md")] == "Beta Spec"


def test_build_title_map_fallback_to_stem(tmp_path):
    docs = tmp_path / "docs"
    docs.mkdir()
    (docs / "000-adr.md").write_text("헤딩 없는 문서\n", encoding="utf-8")
    tm = build_title_map(docs)
    assert tm[str(docs / "000-adr.md")] == "000-adr"


def test_build_title_map_dir_collision_disambiguated(tmp_path):
    docs = tmp_path / "docs"
    (docs / "specs").mkdir(parents=True)
    (docs / "super" / "specs").mkdir(parents=True)
    tm = build_title_map(docs)
    t1 = tm[str(docs / "specs")]
    t2 = tm[str(docs / "super" / "specs")]
    # 디렉토리명이 같아도 제목은 고유해야 한다
    assert t1 != t2


def test_build_title_map_deterministic(tmp_path):
    docs = tmp_path / "docs"
    (docs / "x").mkdir(parents=True)
    (docs / "x" / "doc.md").write_text("# Same\n", encoding="utf-8")
    first = build_title_map(docs)
    second = build_title_map(docs)
    assert first == second


def test_load_index_missing_returns_empty(monkeypatch):
    def fake_api(method, path, **kwargs):
        raise _http_error(404)

    monkeypatch.setattr(sdc, "_api", fake_api)
    sdc.load_index(123)
    assert sdc._index == {}
    assert sdc._index_version is None


def test_load_index_parses_value(monkeypatch):
    def fake_api(method, path, **kwargs):
        return {"value": {"docs/a.md": 5, "docs/b": 9}, "version": {"number": 3}}

    monkeypatch.setattr(sdc, "_api", fake_api)
    sdc.load_index(1)
    assert sdc._index == {"docs/a.md": 5, "docs/b": 9}
    assert sdc._index_version == 3


def test_save_index_creates_when_new(monkeypatch):
    calls = []
    monkeypatch.setattr(sdc, "_api", lambda m, p, **k: calls.append((m, p, k)) or {})
    sdc._index = {"docs/a.md": 5}
    sdc._index_version = None
    sdc.save_index(99)
    assert calls[0][0] == "POST"
    assert calls[0][1] == "/content/99/property"


def test_save_index_updates_with_bumped_version(monkeypatch):
    calls = []
    monkeypatch.setattr(sdc, "_api", lambda m, p, **k: calls.append((m, p, k)) or {})
    sdc._index = {"docs/a.md": 5}
    sdc._index_version = 7
    sdc.save_index(99)
    assert calls[0][0] == "PUT"
    assert calls[0][2]["json"]["version"]["number"] == 8


def test_sync_md_file_updates_when_in_index(tmp_path, monkeypatch):
    docs = tmp_path / "docs"
    docs.mkdir()
    f = docs / "spec.md"
    f.write_text("# 제목\n본문", encoding="utf-8")

    monkeypatch.setattr(sdc, "DOCS_DIR", docs)
    sdc._title_map = build_title_map(docs)
    sdc._index = {str(f): 42}
    sdc._dir_cache = {}

    calls = []

    def fake_get_page(page_id):
        return {"id": str(page_id), "title": "옛 제목", "version": {"number": 4}}

    def fake_update(page_id, title, body, version):
        calls.append(("update", page_id, title, version))
        return {"id": str(page_id), "_links": {"webui": "/x"}}

    monkeypatch.setattr(sdc, "_get_page", fake_get_page)
    monkeypatch.setattr(sdc, "_update_page", fake_update)

    result = sdc.sync_md_file(f, parent_id=1)
    assert result["action"] == "updated"
    assert calls[0] == ("update", 42, "제목", 4)


def test_sync_md_file_creates_when_absent(tmp_path, monkeypatch):
    docs = tmp_path / "docs"
    docs.mkdir()
    f = docs / "new.md"
    f.write_text("# 새 문서\n", encoding="utf-8")

    monkeypatch.setattr(sdc, "DOCS_DIR", docs)
    sdc._title_map = build_title_map(docs)
    sdc._index = {}
    sdc._dir_cache = {}

    monkeypatch.setattr(sdc, "_find_child_page", lambda title, parent_id: None)
    monkeypatch.setattr(
        sdc, "_create_page",
        lambda title, body, parent_id: {"id": "100", "_links": {"webui": "/new"}},
    )

    result = sdc.sync_md_file(f, parent_id=1)
    assert result["action"] == "created"
    assert sdc._index[str(f)] == 100
