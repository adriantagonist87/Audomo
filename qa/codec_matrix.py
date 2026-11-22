"""Prüft die vom Android-Instrumentierungstest kodierten Prüftöne mit macOS Core Audio.
Aufruf nach Installation der App und Test-APK: python3 qa/codec_matrix.py SERIAL AUSGABEORDNER
"""
import argparse
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("serial")
parser.add_argument("output", type=Path)
args = parser.parse_args()
adb = [str(Path(os.environ["ANDROID_HOME"]) / "platform-tools/adb"), "-s", args.serial]
assert subprocess.check_output(adb + ["emu", "avd", "name"]).decode().splitlines()[0] == "Galaxy_S24_Ultra_API_34"
test = subprocess.check_output(adb + ["shell", "am", "instrument", "-w", "-e", "keepSamples", "true",
                                      "de.audomo.test/androidx.test.runner.AndroidJUnitRunner"], timeout=120).decode()
if "OK (1 test)" not in test:
    raise RuntimeError(test)
args.output.mkdir(parents=True, exist_ok=True)
results = []
with tempfile.TemporaryDirectory() as temp:
    source = Path(temp) / "probe.ogg"
    wave = Path(temp) / "probe.wav"
    for rate in (16000, 24000, 48000):
        for kbps in (16, 24, 32, 48, 64, 80, 96, 128):
            source.write_bytes(subprocess.check_output(adb + ["exec-out", "run-as", "de.audomo", "cat",
                                                               f"cache/qa-encoder/{rate}-{kbps}.ogg"]))
            subprocess.run(["afconvert", "-f", "WAVE", "-d", "LEI16@48000", str(source), str(wave)], check=True)
            with wave.open("rb") as stream:
                assert stream.read(4) == b"RIFF"
                stream.seek(12)
                while True:
                    chunk, size = struct.unpack("<4sI", stream.read(8))
                    if chunk == b"data":
                        pcm = struct.unpack("<" + "h" * (size // 2), stream.read(size))
                        break
                    stream.seek(size + size % 2, 1)
            assert len(pcm) == (rate + 317) * (48000 // rate), (rate, kbps, len(pcm))
            crossings = [i for i in range(4800, 43200) if pcm[i-1] <= 0 < pcm[i]]
            frequency = (len(crossings)-1) * 48000 / (crossings[-1] - crossings[0])
            assert abs(frequency - 440) < 2, (rate, kbps, frequency)
            results.append(dict(input_hz=rate, kbps=kbps, frames_48k=len(pcm), tone_hz=round(frequency, 3)))
            if rate == 48000 and kbps == 48:
                shutil.copyfile(source, args.output / "testsignal-440hz.ogg")
(args.output / "codec-profile.json").write_text(json.dumps(results, indent=2) + "\n")
print(f"{len(results)} Profile vollständig dekodiert, Framezahl und Tonfrequenz geprüft.")
