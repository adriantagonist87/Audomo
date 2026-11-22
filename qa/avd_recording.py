"""API-34-Kurztest. Nur mit geöffnetem, aufnahmebereitem Audomo auf dem Test-AVD ausführen.
Mikrofon über die Systemabfrage freigeben. Bestehende Sitzungen bleiben erhalten.
macOS: python3 qa/avd_recording.py SERIAL AUSGABEORDNER [--normal 20] [--crash 20]
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import random
import re
import shlex
import struct
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("serial")
parser.add_argument("output", type=Path)
parser.add_argument("--normal", type=int, default=20)
parser.add_argument("--crash", type=int, default=20)
args = parser.parse_args()
adb = str(Path(os.environ["ANDROID_HOME"]) / "platform-tools" / "adb")
args.output.mkdir(parents=True, exist_ok=True)
prefix = [adb, "-s", args.serial]


def call(*cmd):
    return subprocess.check_output([*prefix, *cmd], timeout=25)


def shell(*argv):
    return call("shell", shlex.join(argv)).decode().strip()


def private(*argv):
    return shell("run-as", "de.audomo", *argv)


def inventory():
    return set(private("find", "files/sessions", "-name", "*.ogg").splitlines()) if private("ls", "files").find("sessions") >= 0 else set()


def ui():
    result = shell("uiautomator", "dump", "/data/local/tmp/audomo-test-ui.xml")
    if "dumped to:" not in result:
        raise RuntimeError("UI-Zustand konnte nicht frisch gelesen werden: " + result)
    return list(ET.fromstring(shell("cat", "/data/local/tmp/audomo-test-ui.xml")).iter("node"))


def point(nodes, label):
    node = next(n for n in nodes if (n.get("content-desc") == label or n.get("text") == label)
                and n.get("package") == "de.audomo" and n.get("enabled") == "true")
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    return str((x1+x2)//2), str((y1+y2)//2)


def shot(label):
    (args.output / (label + ".png")).write_bytes(call("exec-out", "screencap", "-p"))


def launch():
    shell("am", "start", "-W", "-n", "de.audomo/.MainActivity")


def tap(position):
    shell("input", "tap", *position)


def decoded_frames(wav):
    with wav.open("rb") as f:
        assert f.read(4) == b"RIFF"
        f.seek(12)
        while True:
            chunk, size = struct.unpack("<4sI", f.read(8))
            if chunk == b"data":
                assert len(f.read(size)) == size
                return size // 2
            f.seek(size + size % 2, 1)


assert args.serial.startswith("emulator-")
assert call("emu", "avd", "name").decode().splitlines()[0] in {"Galaxy_S24_Ultra_API_34", "Audomo_QA_API34"}
assert shell("getprop", "ro.build.version.sdk") == "34"
assert "granted=true" in next(line for line in shell("dumpsys", "package", "de.audomo").splitlines()
                              if "android.permission.RECORD_AUDIO: granted=" in line)
launch()
time.sleep(.3)
start = point(ui(), "Aufnahme starten")
shot("bereit")
rng = random.Random(3401)
results = []
with tempfile.TemporaryDirectory() as tmp:
    for mode in ["normal"] * args.normal + ["abbruch"] * args.crash:
        start = point(ui(), "Aufnahme starten")
        before = inventory()
        t0 = time.monotonic()
        tap(start)
        time.sleep(.8 + rng.random() * .5)
        if not results and mode == "normal":
            nodes = ui()
            stop = point(nodes, "Aufnahme stoppen")
            pause = point(nodes, "Aufnahme pausieren")
            shot("aufnahme")
            tap(pause)
            paused_ui = ui()
            resume = point(paused_ui, "Aufnahme fortsetzen")
            shot("pause")
            timer = next(n.get("text") for n in paused_ui if re.fullmatch(r"\d+:\d+:\d+", n.get("text")))
            time.sleep(2.2)
            assert any(n.get("text") == timer for n in ui())
            tap(resume)
            time.sleep(.7)
            shell("input", "keyevent", "KEYCODE_HOME")
            time.sleep(1)
            shell("input", "keyevent", "KEYCODE_SLEEP")
            time.sleep(2)
            shell("input", "keyevent", "KEYCODE_WAKEUP")
            shell("wm", "dismiss-keyguard")
            launch()
            assert point(ui(), "Aufnahme stoppen")
            tap(pause)
            end = point(ui(), "Aufnahme stoppen")
            tap(end)
        elif mode == "normal":
            tap(point(ui(), "Aufnahme stoppen"))
        else:
            # Only kill the newly created test session of this app.
            assert len(private("find", "files/sessions", "-name", "capture.part").splitlines()) == 1
            shell("am", "force-stop", "de.audomo")
            interrupted = time.monotonic()
            launch()
        for attempt in range(40):
            new = inventory() - before
            if new:
                break
            time.sleep(.1)
        assert len(new) == 1, (mode, new, [(n.get("text")) for n in ui() if n.get("text")])
        audio = new.pop()
        directory = audio.rsplit("/", 1)[0]
        meta = json.loads(private("cat", directory + "/session.json"))
        assert meta["recovered"] == (mode == "abbruch")
        assert private("find", directory, "-name", "*.part") == ""
        local = Path(tmp) / "test.ogg"
        pcm = Path(tmp) / "test.wav"
        local.write_bytes(call("exec-out", "run-as", "de.audomo", "cat", audio))
        subprocess.run(["afconvert", "-f", "WAVE", "-d", "LEI16", str(local), str(pcm)], check=True)
        frames = decoded_frames(pcm)
        assert frames == meta["frames"] and frames > 0
        row = dict(mode=mode, frames=frames, bytes=local.stat().st_size,
                   sha256=hashlib.sha256(local.read_bytes()).hexdigest())
        if mode == "abbruch":
            row["wall_time_minus_audio_s"] = round(interrupted - t0 - frames / 48000, 3)
        results.append(row)
        (args.output / "laeufe.json").write_text(json.dumps(results, indent=2) + "\n")
        if mode == "abbruch" and sum(r["mode"] == mode for r in results) == 1:
            assert any("Wiederhergestellt" in n.get("text") for n in ui())
            shot("wiederherstellung")
        print(len(results), mode, frames, flush=True)
