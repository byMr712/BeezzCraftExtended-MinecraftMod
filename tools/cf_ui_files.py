import sys
import time

import cf_console

project = sys.argv[1] if len(sys.argv) > 1 else "1817202"
url = f"https://authors.curseforge.com/#/projects/{project}/files"

driver = cf_console.connect()
target = driver.execute_cdp_cmd("Target.createTarget", {"url": url})
driver.switch_to.window(target["targetId"])
text = ""
for _ in range(30):
    time.sleep(2)
    text = driver.execute_script("return document.body ? document.body.innerText : ''")
    if "Beezz" in text:
        break
print(text[:8000])
