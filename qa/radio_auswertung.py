"""Analyze only the named S24 test session. Retain numeric results; temporary audio stays local."""
import argparse
import audioop
import json
import os
from pathlib import Path
import re
import shlex
import statistics
import struct
import subprocess
import tempfile

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument("session");parser.add_argument("serial");parser.add_argument("output",type=Path)
parser.add_argument("--port",default="5037")
args=parser.parse_args()
assert re.fullmatch("[0-9a-f-]{36}",args.session)
adb=[str(Path(os.environ["ANDROID_HOME"])/"platform-tools/adb"),"-P",args.port,"-s",args.serial]
def private(path):
    return [*adb,"exec-out",shlex.join(("run-as","de.audomo","cat",path))]
directory="files/sessions/"+args.session
meta=json.loads(subprocess.check_output(private(directory+"/session.json")))
assert Path(meta["name"]).name==meta["name"] and not meta["recovered"]
with tempfile.TemporaryDirectory() as tmp:
    ogg,wav=Path(tmp)/"radio.ogg",Path(tmp)/"decoded.wav"
    with ogg.open("wb") as dest:
        subprocess.run(private(directory+"/"+meta["name"]),stdout=dest,check=True)
    pages=0;last=0;skip=None;serial=None;end=False
    with ogg.open("rb") as f:
        while head:=f.read(27):
            assert len(head)==27 and head[:5]==b'OggS\x00' and not end
            pos,sid,seq=struct.unpack_from("<QII",head,6)
            assert seq==pages and (serial is None or serial==sid) and pos>=last
            lace=f.read(head[26]);payload=f.read(sum(lace));assert len(payload)==sum(lace)
            if not pages:
                assert payload.startswith(b"OpusHead");skip=struct.unpack_from("<H",payload,10)[0];serial=sid
            pages+=1;last=pos;end=bool(head[5]&4)
    assert end and last-skip==meta["frames"]
    subprocess.run(["afconvert","-f","WAVE","-d","LEI16",str(ogg),str(wav)],check=True)
    levels=[];frames=0
    with wav.open("rb") as f:
        assert f.read(4)==b"RIFF";f.read(4);assert f.read(4)==b"WAVE"
        while header:=f.read(8):
            tag,n=struct.unpack("<4sI",header)
            if tag==b"fmt ":
                fmt=f.read(n);channels,hz=struct.unpack_from("<HI",fmt,2);assert channels==1 and hz==48000
                if n%2:f.read(1)
            elif tag==b"data":
                remaining=n
                while remaining:
                    chunk=f.read(min(hz*2,remaining));assert chunk;remaining-=len(chunk);frames+=len(chunk)//2
                    levels.append(audioop.rms(chunk,2))
                break
            else:f.seek(n+n%2,1)
    assert frames==meta["frames"]
    report={"app":"0.1.3","device":"SM-S928B","androidApi":34,"source":"Radio im Raum, kein kalibrierter Zeitmarker",
            "durationSeconds":round(frames/48000,3),"frames":frames,"bytes":ogg.stat().st_size,"oggPagesInSequence":pages,
            "confirmedEOS":end,"sampleRate":hz,"channels":channels,"bitrateSettingKbps":meta.get("bitrate"),
            "route":meta.get("route"),"effects":meta.get("effects"),"oneSecondWindowsIncludingLastPartial":len(levels),
            "fullySilentWindows":levels.count(0),"medianRmsPcm16":statistics.median(levels),"peakWindowRmsPcm16":max(levels)}
    args.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n");print(json.dumps(report,ensure_ascii=False))
