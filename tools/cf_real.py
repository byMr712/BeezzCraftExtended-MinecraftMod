#!/usr/bin/env python3
"""Run Chrome with remote debugging so the author console can be scripted.

Chrome 136+ refuses --remote-debugging-port on the default user-data-dir, and
cookies copied from the real profile cannot be decrypted (App-Bound Encryption),
so a dedicated profile is used. Sign in to CurseForge once in that window; the
session stays there.

The profile is shared with the AtlasMapExtended project when it already exists,
so the same CurseForge login works for both. Override it with CURSEFORGE_PROFILE.

Usage:
    py tools\\cf_real.py            # start Chrome and wait for the login
    py tools\\cf_real.py --check    # verify the running session
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

PORT = 9222
CHROME = Path(r"C:\Program Files\Google\Chrome\Application\chrome.exe")


def profile_dir() -> Path:
    override = os.getenv("CURSEFORGE_PROFILE")
    if override:
        return Path(override)
    local = Path(os.environ.get("LOCALAPPDATA", Path.home()))
    shared = local / "AtlasMapExtended" / "cf_real"
    if shared.exists():
        return shared
    return local / "BeezzCraftExtended" / "cf_real"


DST = profile_dir()

PING_JS = """
const done = arguments[arguments.length - 1];
fetch('https://authors.curseforge.com/_api/project-members/project-titles',
      {credentials: 'include'})
  .then(async r => done({status: r.status, ct: r.headers.get('content-type') || '',
                         body: (await r.text()).slice(0, 160)}))
  .catch(e => done({status: 0, ct: '', body: String(e)}));
"""


def chrome_running() -> int:
    out = subprocess.run(["tasklist", "/FI", "IMAGENAME eq chrome.exe", "/NH"],
                         capture_output=True, text=True, timeout=20).stdout
    return out.lower().count("chrome.exe")


def port_alive() -> bool:
    try:
        with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/version", timeout=2) as r:
            return r.status == 200
    except Exception:
        return False


def ensure_profile() -> None:
    (DST / "Default").mkdir(parents=True, exist_ok=True)


def launch() -> bool:
    subprocess.Popen([
        str(CHROME),
        f"--remote-debugging-port={PORT}",
        f"--user-data-dir={DST}",
        "--no-first-run", "--no-default-browser-check",
        "--disable-extensions",
        "https://authors.curseforge.com/",
    ])
    for _ in range(90):
        if port_alive():
            return True
        time.sleep(1)
    return False


def attach_verify() -> int:
    from selenium import webdriver
    from selenium.webdriver.chrome.options import Options

    opts = Options()
    opts.debugger_address = f"127.0.0.1:{PORT}"
    try:
        driver = webdriver.Chrome(options=opts)
    except Exception as e:
        print(f"Could not attach to Chrome on port {PORT}: {e}\n"
              "Run py tools\\cf_real.py and leave the window open.", file=sys.stderr)
        return 1
    driver.set_script_timeout(120)
    result = {"status": 0, "ct": "", "body": ""}
    try:
        for _ in range(40):
            result = driver.execute_async_script(PING_JS)
            if result.get("status") == 200 and "json" in (result.get("ct") or ""):
                print(f"SIGNED IN: {result['body']}", flush=True)
                return 0
            time.sleep(2)
        print(f"NOT SIGNED IN: {result}", file=sys.stderr, flush=True)
        return 1
    finally:
        pass  # keep the window open


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--wait", type=float, default=600)
    args = parser.parse_args()

    if args.check:
        if not port_alive():
            print("Chrome is not running on the author profile.\n"
                  "Run py tools\\cf_real.py first.", file=sys.stderr)
            return 1
    else:
        if chrome_running():
            print("Chrome is already running. The dedicated profile can share "
                  "the machine with it; continuing.", flush=True)
        ensure_profile()
        print(f"profile: {DST}", flush=True)
        print(f"Starting Chrome on {DST} ...", flush=True)
        if not launch():
            print("DevTools port did not open. Close the window on this profile "
                  "and try again.", file=sys.stderr, flush=True)
            return 1
        print("DevTools port is up.", flush=True)

    return attach_verify()


if __name__ == "__main__":
    raise SystemExit(main())
