"""Reboot and microphone privacy tests. Only runs on the disposable API-34 QA emulator."""
import argparse
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument("serial");parser.add_argument("report",type=Path)
args=parser.parse_args()
adb=[str(Path(os.environ["ANDROID_HOME"])/"platform-tools/adb"),"-s",args.serial]
def call(*arguments,timeout=25):
    return subprocess.check_output([*adb,*arguments],timeout=timeout).decode().strip()
def shell(*arguments):return call("shell",shlex.join(arguments))
def inventory():return set(shell("run-as","de.audomo","find","files/sessions","-name","*.ogg").splitlines())
def start():shell("am","start","-W","-n","de.audomo/.MainActivity")
def tree():
    raw=call("exec-out","uiautomator","dump","--compressed","/dev/tty")
    return [n for n in ET.fromstring(raw[raw.index("<?xml"):raw.rindex("</hierarchy>")+12]).iter("node") if n.get("package")=="de.audomo"]
def tap(label):
    node=next(n for n in tree() if n.get("content-desc")==label or n.get("text")==label)
    x,y,r,b=map(int,re.findall(r"\d+",node.get("bounds")));shell("input","tap",str((x+r)//2),str((y+b)//2))
def result():
    for _ in range(50):
        new=inventory()-before
        if new:
            assert len(new)==1
            meta=json.loads(shell("run-as","de.audomo","cat",next(iter(new)).rsplit("/",1)[0]+"/session.json"))
            assert meta["recovered"] and meta["frames"]>0
            return {"recovered":True,"frames":meta["frames"]}
        time.sleep(.15)
    raise AssertionError("Keine gerettete Aufnahme")
assert args.serial.startswith("emulator-") and call("emu","avd","name").splitlines()[0]=="Audomo_QA_API34"
assert shell("getprop","ro.build.version.sdk")=="34"
report={}
start();before=inventory();tap("Aufnahme starten");time.sleep(2.5)
assert any(n.get("content-desc")=="Aufnahme stoppen" for n in tree())
call("reboot");call("wait-for-device",timeout=90)
for _ in range(90):
    if shell("getprop","sys.boot_completed")=="1":break
    time.sleep(1)
shell("input","keyevent","KEYCODE_WAKEUP");shell("wm","dismiss-keyguard");start()
report["reboot"]=result()
args.report.write_text(json.dumps(report,indent=2))
before=inventory();tap("Aufnahme starten");time.sleep(2)
try:
    shell("cmd","sensor_privacy","enable","0","microphone")
    time.sleep(3)
    observed=[n.get("text","") for n in tree()]
    report["microphonePrivacy"]={"visibleError":any("unterbrochen" in t.lower() or "stummgeschaltet" in t.lower() for t in observed)}
    if report["microphonePrivacy"]["visibleError"]:report["microphonePrivacy"].update(result())
finally:
    shell("cmd","sensor_privacy","disable","0","microphone")
    if any(n.get("content-desc")=="Aufnahme stoppen" for n in tree()):tap("Aufnahme stoppen")
args.report.write_text(json.dumps(report,indent=2)+"\n")
print(json.dumps(report))
