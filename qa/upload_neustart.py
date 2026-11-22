"""Prüft den Upload über Neustart ausschließlich auf der wegwerfbaren Audomo-QA-AVD."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument("serial")
parser.add_argument("local_fixture", type=Path)
parser.add_argument("result", type=Path)
args = parser.parse_args()
adb = str(Path(os.environ["ANDROID_HOME"]) / "platform-tools/adb")
base = [adb, "-s", args.serial]

def run(*cmd, **kw):
    return subprocess.check_output([*base, *cmd], timeout=70, **kw).decode().strip()

if not args.serial.startswith("emulator-") or run("emu", "avd", "name").splitlines()[0] != "Audomo_QA_API34":
    raise SystemExit("Eigene separate QA-AVD erforderlich")
assert run("shell", "settings", "get", "global", "wifi_on") == "1"
run("shell", "run-as", "de.audomo", "sh", "-c", "'cat > cache/qa-protocols.json'", input=args.local_fixture.read_bytes())

def stage(name):
    result = run("shell", "am", "instrument", "-w", "-e", "restartStage", name, "-e", "class",
                 "de.audomo.UploadRestartDeviceTest", "de.audomo.test/androidx.test.runner.AndroidJUnitRunner")
    if "OK (1 test)" not in result:
        raise RuntimeError(result)

try:
    run("shell", "svc", "wifi", "disable")
    time.sleep(3)
    stage("prepare")
    old = run("shell", "cat", "/proc/sys/kernel/random/boot_id")
    run("reboot")
    run("wait-for-device")
    for attempt in range(60):
        if run("shell", "getprop", "sys.boot_completed") == "1":
            break
        time.sleep(1)
    else:
        raise RuntimeError("Neustart nicht abgeschlossen")
    assert run("shell", "cat", "/proc/sys/kernel/random/boot_id") != old
    run("shell", "svc", "wifi", "enable")
    time.sleep(5)
    stage("verify")
    args.result.write_text(json.dumps({
        "avd": "Audomo_QA_API34", "api": 34, "source": "Synthetische Ogg-Datei",
        "duplicateFinalization": "ein Auftrag", "wifiDisabled": "wartet",
        "persistedJobRequiresWifi": True, "separateBootId": True,
        "verifiedAfterWifiReturned": True, "localAndRemoteSha256Equal": True
    }, ensure_ascii=False, indent=2) + "\n")
    print("WLAN-Bedingung, einzelner Auftrag und Hashgleichheit nach Neustart bestätigt.")
finally:
    run("shell", "svc", "wifi", "enable")
    run("shell", "run-as", "de.audomo", "rm", "-f", "cache/qa-protocols.json")
