#!/usr/bin/env python3
"""Upload BeezzCraft (Extended) jars through the CurseForge author console.

The console exposes a cookie-authenticated API used by the page itself:
  POST /_api/project-files/{projectId}/upload-file-details
       JSON: fileLength, fileName, displayName, releaseType (1/2/3),
             gameVersionIds (numeric ids from get-game-versions), changelog,
             changelogType (1=text, 5=markdown), ...
       -> { data: { uploadId } }
  POST /_api/project-files/upload-file-chunk   (multipart, 5 MB chunks)
       chunkedUploadId, chunkIndex (1-based), totalChunks, file

Auth is the Chrome session started by tools/cf_real.py. The session is shared
with the AtlasMapExtended project when that profile already exists.

Examples:
    py tools\\cf_console.py --list-versions
    py tools\\cf_console.py --version v1.1 --dry-run
    py tools\\cf_console.py --version v1.1 --only "Fabric-1.21-"
    py tools\\cf_console.py --version v1.1
"""

from __future__ import annotations

import argparse
import base64
import json
import sys
import time
from pathlib import Path

import publish_curseforge as cf

PORT = 9222
CHUNK = 5 * 1024 * 1024
DEFAULT_DIR = Path.home() / "Documents" / "BeezzCraftExtended_all_builds"
DEFAULT_VERSION = "v1.1"

DEFAULT_CHANGELOG = """Beezz Craft (Extended) v1.1

A client-side crafting wheel for the inventory, the crafting table and the creative inventory.

- Opens on Ctrl + right click by default; the functional key and click are configurable.
- Recipes are grouped by family, with variants (wood types, colours) on the mouse wheel.
- Middle click cycles variants; the card shows the result, what is missing and what the clicks do.
- Craft queue: a grey recipe is crafted automatically once the resources are in the inventory.
- "What to get" shows the missing resources under the queue and on the HUD while playing.
- Settings in the gear panel and in Mod Menu: queue, missing recipes, resources, sound,
  key-only, click, key and panel position. Save / Cancel / Reset in Mod Menu.
- Fixed the queue counter hidden behind the item icon.
- Fixed gear-panel rows over the creative tabs or the recipe book not taking clicks.
- Fabric and NeoForge, client-side only, English and Russian.
"""

VERSIONS_JS = """
const done = arguments[arguments.length - 1];
fetch(`/_api/project-files/${arguments[0]}/get-game-versions`, {credentials: 'include'})
  .then(r => r.text()).then(t => done(t)).catch(e => done('ERR ' + e));
"""

PROJECT_JS = """
const done = arguments[arguments.length - 1];
fetch(arguments[0], {credentials: 'include'})
  .then(async r => done({status: r.status, body: await r.text()}))
  .catch(e => done({status: 0, body: String(e)}));
"""

FORM_DATA_JS = """
const done = arguments[arguments.length - 1];
const url = `/_api/project-files/${arguments[0]}/create-project-file-form-data`
          + `?gameId=${arguments[1]}&classId=${arguments[2]}`;
fetch(url, {credentials: 'include'})
  .then(r => r.text()).then(t => done(t)).catch(e => done('ERR ' + e));
"""

UPLOAD_JS = """
const done = arguments[arguments.length - 1];
const projectId = arguments[0];
const meta = arguments[1];
const chunks = arguments[2];
(async () => {
    const details = await fetch(`/_api/project-files/${projectId}/upload-file-details`, {
        method: 'POST',
        credentials: 'include',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(meta),
    });
    const text = await details.text();
    let parsed = {};
    try { parsed = JSON.parse(text); } catch (e) { parsed = {}; }
    if (!details.ok) {
        done({step: 'details', status: details.status, body: text.slice(0, 500)});
        return;
    }
    const data = parsed.data || parsed;
    const uploadId = data.uploadId;
    if (!uploadId) {
        done({step: 'details', status: details.status, body: text.slice(0, 500)});
        return;
    }
    for (let i = 0; i < chunks.length; i++) {
        const bin = atob(chunks[i]);
        const bytes = new Uint8Array(bin.length);
        for (let k = 0; k < bin.length; k++) bytes[k] = bin.charCodeAt(k);
        const fd = new FormData();
        fd.append('chunkedUploadId', uploadId);
        fd.append('chunkIndex', String(i + 1));
        fd.append('totalChunks', String(chunks.length));
        fd.append('file', new Blob([bytes]), meta.fileName);
        const r = await fetch('/_api/project-files/upload-file-chunk', {
            method: 'POST', credentials: 'include', body: fd,
        });
        const body = await r.text();
        if (!r.ok) {
            done({step: 'chunk', index: i + 1, status: r.status, body: body.slice(0, 500)});
            return;
        }
    }
    done({ok: true, uploadId: uploadId, status: details.status});
})();
"""

RELEASE = {"release": 1, "beta": 2, "alpha": 3}


def connect():
    import cf_real

    if not cf_real.port_alive():
        print("Starting Chrome on the author profile...", flush=True)
        if not cf_real.launch():
            raise SystemExit("Chrome did not start; run py tools\\cf_real.py first")
    from selenium import webdriver
    from selenium.webdriver.chrome.options import Options

    opts = Options()
    opts.debugger_address = f"127.0.0.1:{PORT}"
    driver = webdriver.Chrome(options=opts)
    driver.set_script_timeout(1800)
    target = driver.execute_cdp_cmd("Target.createTarget",
                                    {"url": "https://authors.curseforge.com/"})
    driver.switch_to.window(target["targetId"])
    for _ in range(30):
        time.sleep(1)
        if "authors.curseforge.com" in (driver.current_url or ""):
            return driver
    raise SystemExit("could not open the author console")


def game_versions(driver, project_id: str) -> dict[str, int]:
    """All choices offered by the Add File form.

    A new, not-yet-approved project returns [] from get-game-versions, so read
    the same form data the console's own dialog uses. Fall back to the old
    endpoint for projects where the form data is unavailable.
    """
    project = driver.execute_async_script(PROJECT_JS, f"/_api/projects/{project_id}")
    try:
        info = json.loads(project.get("body") or "{}")
    except Exception:
        info = {}

    raw = driver.execute_async_script(
        FORM_DATA_JS, project_id,
        info.get("gameId") or 432,  # Minecraft
        info.get("classId") or 6)   # Mods
    out: dict[str, int] = {}
    try:
        data = json.loads(raw)
    except Exception:
        data = {}
    for group in data.get("versionsData", []) if isinstance(data, dict) else []:
        for field in group:
            for choice in field.get("choices", []):
                name = (choice.get("name") or "").strip().lower()
                if name:
                    out[name] = choice.get("id")
    if out:
        return out

    raw = driver.execute_async_script(VERSIONS_JS, project_id)
    try:
        items = json.loads(raw)
    except Exception:
        raise SystemExit(f"get-game-versions failed: {raw[:400]}")
    return {(i.get("name") or "").strip().lower(): i.get("value") for i in items}


def ids_for(versions: dict[str, int], mc: str, loader: str) -> list[int]:
    """CurseForge expects every category in one list: environment, Java, game, loader."""
    java = "java 25" if mc.startswith("26") else "java 21"
    wanted = [mc.lower(), loader.lower(), "client", java]
    ids = []
    for name in wanted:
        if name not in versions:
            raise SystemExit(f"game version {name!r} not offered; have: {sorted(versions)}")
        ids.append(versions[name])
    return ids


def main() -> int:
    cfg = cf.load_config()
    parser = argparse.ArgumentParser()
    parser.add_argument("--dir", default=str(DEFAULT_DIR))
    parser.add_argument("--version", default=DEFAULT_VERSION)
    parser.add_argument("--project", default=cfg.get("projectId") or cf.PROJECT_ID)
    parser.add_argument("--only", default=None)
    parser.add_argument("--skip", action="append", default=[],
                        help="skip jars whose name contains this text (repeatable)")
    parser.add_argument("--names", default=None,
                        help="comma-separated exact jar file names")
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--release-type", default="release",
                        choices=["release", "beta", "alpha"])
    parser.add_argument("--changelog", default=None)
    parser.add_argument("--changelog-file", default=None,
                        help="read the changelog from a UTF-8 text file")
    parser.add_argument("--changelog-markdown", action="store_true",
                        help="send the changelog as markdown instead of plain text")
    parser.add_argument("--list-versions", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--sleep", type=float, default=1.5)
    args = parser.parse_args()

    if args.changelog_file:
        changelog = Path(args.changelog_file).read_text(encoding="utf-8")
    else:
        changelog = args.changelog or cfg.get("changelog") or DEFAULT_CHANGELOG

    driver = connect()
    versions = game_versions(driver, args.project)
    if args.list_versions:
        for name, value in sorted(versions.items()):
            print(f"  {value:>6}  {name}")
        return 0
    if not versions:
        print("No game versions returned (not signed in?)", file=sys.stderr)
        return 1

    folder = Path(args.dir)
    jars = []
    for jar in sorted(folder.glob("*.jar")):
        m = cf.JAR_RE.match(jar.name)
        if not m or ("v" + m.group("version")) != args.version:
            continue
        if args.only and args.only not in jar.name:
            continue
        if any(text in jar.name for text in args.skip):
            continue
        if args.names:
            wanted = {name.strip() for name in args.names.split(",") if name.strip()}
            if jar.name not in wanted:
                continue
        jars.append((jar, m))
    if args.limit:
        jars = jars[:args.limit]
    if not jars:
        print(f"No {args.version} jars in {folder}", file=sys.stderr)
        return 1

    print(f"Uploading {len(jars)} jars as {args.version} ({args.release_type})")
    failures = 0
    for jar, m in jars:
        mc, loader = m.group("mc"), m.group("loader")
        payload = {
            "fileLength": jar.stat().st_size,
            "fileName": jar.name,
            "displayName": f"{cf.DISPLAY_NAME} {loader} {mc} {args.version}",
            "releaseType": RELEASE[args.release_type],
            "gameVersionIds": ids_for(versions, mc, loader),
            "changelog": changelog,
            "changelogType": 5 if args.changelog_markdown else 1,
            "isMarkedForManualRelease": False,
            "parentFileId": None,
            "addedProjectRelations": [],
            "includeDefaultProjectRelations": True,
        }
        if args.dry_run:
            print(f"  {jar.name} -> {payload['gameVersionIds']} (dry run)", flush=True)
            continue
        data = jar.read_bytes()
        chunks = [base64.b64encode(data[i:i + CHUNK]).decode("ascii")
                  for i in range(0, len(data), CHUNK)]
        result = {}
        for attempt in range(1, 6):
            result = driver.execute_async_script(UPLOAD_JS, str(args.project), payload, chunks)
            if result.get("status") != 429:
                break
            wait = 10 * attempt
            print(f"  {jar.name} throttled (429), retry {attempt}/5 in {wait}s", flush=True)
            time.sleep(wait)
        if result.get("ok"):
            print(f"  {jar.name} -> ok (uploadId {result['uploadId']})", flush=True)
        else:
            failures += 1
            status = result.get("status")
            print(f"  {jar.name} FAILED at {result.get('step')}: "
                  f"{status} {result.get('body','')[:300]}", flush=True)
            if status == 401:
                print("    Session expired. Run: py tools\\cf_real.py  "
                      "and sign in in the Chrome window.", flush=True)
        time.sleep(args.sleep)
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
