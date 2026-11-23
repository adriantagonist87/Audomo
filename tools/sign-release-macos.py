#!/usr/bin/env python3
"""Sign an aligned release APK with the existing Audomo key on the maintainer's Mac."""
import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("output", type=Path, help="Path for a new signed APK")
args = parser.parse_args()
output = args.output.resolve()
unsigned = root / "app/build/outputs/apk/release/app-release-unsigned.apk"
if not unsigned.is_file() or output.exists():
    parser.exit(1, "Unsigned release APK missing or output file already exists.\n")
key = Path.home() / "Library/Application Support/Audomo/signing/audomo-release.p12"
sdk = Path(os.environ["ANDROID_HOME"])
keytool = Path(os.environ["JAVA_HOME"]) / "bin/keytool"
tools = sdk / "build-tools/36.0.0"
expected = (root / "docs/release-certificate.sha256").read_text().split()[0].lower()
password = subprocess.check_output(
    ["/usr/bin/security", "find-generic-password", "-a", "audomo", "-s",
     "de.audomo.release-signing", "-w"]
).rstrip(b"\r\n")
cert = subprocess.check_output(
    [keytool, "-exportcert", "-alias", "audomo", "-keystore", key, "-storepass:env",
     "AUDOMO_KEYTOOL_PASSWORD"], env={**os.environ, "AUDOMO_KEYTOOL_PASSWORD": password.decode()}
)
if hashlib.sha256(cert).hexdigest() != expected:
    parser.exit(1, "The signing certificate does not match the Audomo release certificate.\n")
with tempfile.TemporaryDirectory(dir=output.parent) as folder:
    signed = Path(folder) / "signed.apk"
    subprocess.run([tools / "zipalign", "-c", "-P", "16", "4", unsigned], check=True)
    subprocess.run([tools / "apksigner", "sign", "--ks", key, "--ks-key-alias", "audomo",
                    "--ks-pass", "stdin", "--v4-signing-enabled", "false", "--out", signed,
                    unsigned], input=password + b"\n", check=True)
    subprocess.run([tools / "apksigner", "verify", "--verbose", "--print-certs", signed], check=True)
    os.link(signed, output)
