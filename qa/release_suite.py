"""Core regression suite for a fresh, unlocked Android 14 emulator. Run with the fixture venv."""
import argparse
from contextlib import ExitStack
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("serial", help="ADB serial of a new emulator with no Audomo installation")
args = parser.parse_args()
adb = [Path(os.environ["ANDROID_HOME"]) / "platform-tools/adb", "-s", args.serial]


def device(*arguments, data=None):
    return subprocess.check_output([*adb, *arguments], input=data, timeout=360).decode().strip()


if not args.serial.startswith("emulator-") or device("shell", "getprop", "ro.build.version.sdk") != "34":
    parser.error("Für diese Prüfreihe einen Android-14-Emulator wählen.")
if device("shell", "pm", "list", "packages", "de.audomo"):
    parser.error("Für die Prüfreihe einen leeren Emulator verwenden.")
apks = root / "app/build/outputs/apk"
for apk in [apks / "debug/app-debug.apk", apks / "androidTest/debug/app-debug-androidTest.apk"]:
    device("install", "-g", apk)
device("shell", "input", "keyevent", "KEYCODE_WAKEUP")
device("shell", "wm", "dismiss-keyguard")
device("shell", "am", "start", "-W", "-n", "de.audomo/.MainActivity")
classes = [
    "CaptureControlDeviceTest", "CalendarProviderDeviceTest", "CalendarDayDeviceTest",
    "LibraryDeviceTest", "LibraryUiDeviceTest", "OpusProfileDeviceTest", "ShareDeviceTest",
    "DropboxRequestDeviceTest", "DropboxLinkDeviceTest", "ProtocolDeviceTest",
    "UploadQueueDeviceTest", "CloudCatalogDeviceTest",
]
with tempfile.TemporaryDirectory() as temp, ExitStack() as cleanup:
    fixture = Path(temp) / "fixture"
    log = cleanup.enter_context((Path(temp) / "server.log").open("w"))
    server = subprocess.Popen([sys.executable, root / "qa/protocol_servers.py", fixture,
                               "--adb-reverse"], stdout=log, stderr=log)
    cleanup.callback(server.wait)
    cleanup.callback(server.terminate)
    config = fixture / "client.json"
    for _ in range(200):
        if config.is_file():
            break
        if server.poll() is not None:
            sys.exit("Lokaler Testserver konnte nicht starten. Python-Abhängigkeiten prüfen.")
        time.sleep(.1)
    data = config.read_bytes()
    ports = json.loads(data)
    for port in [*ports["ports"].values(), *ports["passivePorts"]]:
        route = "tcp:" + str(port)
        device("reverse", "--no-rebind", route, route)
        cleanup.callback(device, "reverse", "--remove", route)
    device("shell", "run-as", "de.audomo", "sh", "-c", "'cat > cache/qa-protocols.json'", data=data)
    cleanup.callback(device, "shell", "run-as", "de.audomo", "rm", "-f", "cache/qa-protocols.json")
    result = device("shell", "am", "instrument", "-w", "-r", "-e", "class",
                    ",".join("de.audomo." + c for c in classes),
                    "-e", "qaRecorder", "true", "-e", "localServers", "true",
                    "de.audomo.test/androidx.test.runner.AndroidJUnitRunner")
    print(result)
    counts = re.findall(r"^INSTRUMENTATION_STATUS_CODE: (-?\d+)\s*$", result, re.M)
    if not re.search(r"OK \(\d+ tests?\)", result) or not counts or set(counts) != {"0", "1"}:
        sys.exit("Instrumentierung fehlgeschlagen oder ein ausgewählter Test wurde übersprungen.")
