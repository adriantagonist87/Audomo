# Offizielle Android-Releases

Audomo wird als signierte APK im öffentlichen GitHub-Repository verteilt. Der stabile Kanal verwendet das Paket `de.audomo`. Der erste stabile Stand ist Tag `v1.0.0` mit Versionscode 20. Die 0.2-Test-APKs verwendeten die lokale Debugsignatur. Ein Wechsel von diesen APKs braucht eine eigene Datensicherung. [Hinweise zum Wechsel](BEDIENUNG.md#wechsel-von-der-testversion)

## Identität, Verwahrung und Wiederherstellung

Der für 1.0.0 angelegte RSA-Schlüssel hat 4096 Bit. Sein öffentlicher Zertifikatsfingerabdruck ist [im Repository festgehalten](release-certificate.sha256). Alle offiziellen Updates mit derselben App-Identität müssen sich mit diesem Schlüssel verifizieren lassen.

Auf dem Release-Mac liegt der verschlüsselte PKCS#12-Speicher unter `~/Library/Application Support/Audomo/signing/audomo-release.p12`. Der Alias heißt `audomo`. Das Passwort steht als generisches Passwort im macOS-Schlüsselbund, Dienst `de.audomo.release-signing`, Account `audomo`. Das Signierwerkzeug liest es direkt aus dem Schlüsselbund und gibt es an den Signierer weiter. Die private Datei und das Passwort gehören in die Verfügungsgewalt des Herausgebers.

Für eine Wiederherstellung auf einem anderen Mac werden sowohl PKCS#12-Datei als auch das dazugehörige Passwort benötigt. Der Herausgeber sollte eine verschlüsselte unabhängige Sicherung der Datei und ein getrennt gesichertes Passwort verwahren. Nach dem Wiederherstellen den Zertifikatsfingerabdruck mit dem im Release vergleichen. Der öffentliche Fingerabdruck allein kann keine Folgeversion signieren. Weder private Datei noch Passwort sind Teil des Git-Repositories oder der Release-Assets.

## Aus geprüftem Quellstand signieren

Gradle schreibt die Git-Revision in die APK. Den endgültigen Quellcode und das Prüfprotokoll vor dem Auslieferungsbuild committen. Die spätere APK-Prüfsumme als Release-Asset ablegen. Ein GitHub-Quellarchiv ohne Git-Metadaten kann im Versionskontrolleintrag vom Build eines Checkouts abweichen.

1. Versionsname und monotonen Versionscode in `app/build.gradle.kts` setzen. Releaseprotokoll mit Gerätebezug, bekannten Grenzen und Unterschieden zur Vorversion vorbereiten.
2. Die [Build- und Gerätetests](TESTEN.md) ausführen. Für die öffentliche APK die Variante `release` bauen. Der Standardbuild erzeugt eine unsignierte Datei. Die reguläre Entwicklung braucht keine Release-Zugänge.
3. Auf dem Release-Mac mit gesetztem `JAVA_HOME` und `ANDROID_HOME` aus dem Repository ausführen. Das Zielverzeichnis vorher anlegen. Das Skript prüft die Ausrichtung, den erwarteten Zertifikatsfingerabdruck und anschließend die Signatur. Als Ziel eine neue Datei angeben.

```sh
./gradlew :app:assembleRelease
mkdir -p /tmp/audomo-release
python3 tools/sign-release-macos.py /tmp/audomo-release/Audomo-1.0.0.apk
(cd /tmp/audomo-release && shasum -a 256 Audomo-1.0.0.apk > SHA256SUMS.txt)
```

4. Mit `apksigner verify --verbose --print-certs` Zertifikat und Signaturschemata erfassen. Mit `aapt dump badging` Paket, Version, SDK und Debug-Flag prüfen. Die signierte APK auf einem leeren Testgerät installieren, über die drei Reiter bedienen und den Hash der installierten Datei mit dem Auslieferungsartefakt vergleichen.
5. Den eingebetteten Git-Commit mit `unzip -p DATEI.apk META-INF/version-control-info.textproto` prüfen. Einen annotierten Tag am getesteten Commit anlegen und mit dem Hauptzweig pushen. Einen GitHub-Release für exakt den vorhandenen Tag mit APK und `SHA256SUMS.txt` veröffentlichen. Bei einer offiziellen stabilen Version die Markierung „Latest“ setzen und die Felder Entwurf und Vorabversion kontrollieren. Bereits veröffentlichte Versionen behalten ihre ursprünglichen Assets. Eine Korrektur bekommt eine neue Versionsnummer.
6. Über die GitHub-API Tag, Commit, Status und Assets prüfen. Die veröffentlichte APK und Prüfsumme aus einem neuen Verzeichnis herunterladen und gegen die lokal geprüften Werte vergleichen. Der APK-SHA-256 ist für jede Version neu. Der Fingerabdruck des Signaturzertifikats verbindet die Updates.

## Aus Nutzersicht prüfen

```sh
shasum -a 256 -c SHA256SUMS.txt
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs Audomo-1.0.0.apk
```

Der erste Befehl läuft im Verzeichnis mit APK und Prüfsummendatei. Beim zweiten den Wert `Signer #1 certificate SHA-256 digest` mit dem veröffentlichten Zertifikatsfingerabdruck vergleichen.
