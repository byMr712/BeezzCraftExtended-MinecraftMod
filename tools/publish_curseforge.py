#!/usr/bin/env python3
"""CurseForge publishing helpers for BeezzCraft (Extended).

Only the project id lives in tools/curseforge.json; the upload itself uses the
signed-in Chrome session (see cf_real.py / cf_console.py), not an API key.
"""

from __future__ import annotations

import json
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

API = "https://api.curseforge.com/v1"
GAME_ID = 432  # Minecraft
PROJECT_ID = "1817202"
PROJECT_SLUG = "beezz-craft-extended"
DISPLAY_NAME = "BeezzCraft"
JAR_RE = re.compile(
    r"^(?:BeezzCraft|BeezzCraftExtended)-(?P<loader>Fabric|NeoForge)-(?P<mc>\d+\.\d+(?:\.\d+)?)-v(?P<version>\d+(?:\.\d+)*)\.jar$"
)


def load_config() -> dict:
    cfg_path = Path(__file__).with_name("curseforge.json")
    if cfg_path.exists():
        return json.loads(cfg_path.read_text(encoding="utf-8"))
    return {}


def request_json(url: str, token: str, method: str = "GET", data: bytes | None = None,
                 content_type: str | None = None,
                 extra_headers: dict | None = None) -> dict:
    headers = {
        "x-api-key": token,
        "Accept": "application/json",
        "User-Agent": "BeezzCraft/1.1.1 (byMr712)",
    }
    if content_type:
        headers["Content-Type"] = content_type
    if extra_headers:
        headers.update(extra_headers)
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {e.code} for {url}\n{body}") from e


def fetch_project_files(token: str, project_id: str) -> list[dict]:
    files: list[dict] = []
    index = 0
    while True:
        page = request_json(
            f"{API}/mods/{project_id}/files?pageSize=50&index={index}", token
        )
        files.extend(page.get("data", []))
        pagination = page.get("pagination", {})
        index += int(pagination.get("pageSize", 50))
        if index >= int(pagination.get("totalCount", 0)):
            break
    return files


def main() -> int:
    cfg = load_config()
    project = os.getenv("CURSEFORGE_PROJECT_ID") or cfg.get("projectId") or PROJECT_ID
    token = os.getenv("CURSEFORGE_API_KEY") or cfg.get("apiKey")
    if not token:
        print("Set CURSEFORGE_API_KEY to list files through the public API.\n"
              "For uploading use py tools\\cf_console.py (browser session).",
              file=sys.stderr)
        return 2
    for f in fetch_project_files(token, str(project)):
        print(f'{f.get("id")} | {f.get("fileName")} | {f.get("displayName")} | '
              f'{f.get("releaseType")} | {f.get("fileStatus")} | {f.get("gameVersions")}')
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
