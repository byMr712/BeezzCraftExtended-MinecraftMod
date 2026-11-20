import json
import sys
import time

import cf_console

JS = """
const done = arguments[arguments.length - 1];
fetch(arguments[0], {credentials: 'include'}).then(r => r.text())
  .then(t => done(t)).catch(e => done('ERR ' + e));
"""

driver = cf_console.connect()
project = sys.argv[1] if len(sys.argv) > 1 else "1817202"
path = ("/_api/project-files?filter=%7B%22projectId%22%3A%22" + project + "%22%7D"
        "&range=%5B0%2C30%5D&sort=%5B%22DateCreated%22%2C%22DESC%22%5D")
raw = driver.execute_async_script(JS, path)
try:
    data = json.loads(raw)
except Exception:
    print(raw[:600])
    raise SystemExit(1)
items = data.get("data") if isinstance(data, dict) else data
if not isinstance(items, list):
    print(json.dumps(data)[:800])
    raise SystemExit(1)
print(f"{len(items)} files")
for item in items:
    versions = item.get("gameVersions") or []
    names = [v.get("Label") if isinstance(v, dict) else v for v in versions]
    print("  ", item.get("id"), "|", item.get("fileName"), "|",
          item.get("displayName"), "|", item.get("releaseType"), "|",
          "status", item.get("status"), "|", names)
time.sleep(1)
