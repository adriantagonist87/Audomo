# Audomo prüfen

Für jeden Befund App-Version, API-Level, Gerätemodell, Einstieg, erwartetes und beobachtetes Ergebnis festhalten. Den physischen Samsung-Test und den Android-Emulator getrennt ausweisen. Der Galaxy-AVD hat die Displaygröße und das API-Level des Zielgeräts. Seine System-UI, Mikrofonroute und Haptik unterscheiden sich von One UI 6.1.

## Auf dem Entwicklungsrechner

Mit dem JDK und SDK aus der [Buildanleitung](../README.md#selbst-bauen) im Repository ausführen.

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:assembleRelease :app:lintRelease
```

Die XML-Berichte stehen unter `app/build/test-results/testDebugUnitTest/` und `app/build/reports/lint-results-release.xml`. Die JVM-Tests decken Ogg/Opus, Sitzungshelfer, digitale Verstärkung, Kalenderbenennung und Uploadverzögerung ab. Eine Änderung an gebundenen Android-Komponenten braucht zusätzlich einen Gerätestest.

## Wiederholbare Kernprüfung auf Android 14

Einen neuen, entsperrten API-34-Emulator ohne installierte Audomo-Pakete verwenden. Der dokumentierte Lauf nutzte ein ARM64-Image, 1440 × 3120 Pixel und die Konfiguration des Galaxy S24 Ultra. Die Suite installiert Debug-App und Test-APK, gibt Testberechtigungen frei und verwendet ausschließlich ihre eigenen Server auf dem Rechner. Der Serienparameter bestimmt eindeutig das Gerät.

```sh
"$ANDROID_HOME/platform-tools/adb" devices -l
python3 -m venv /tmp/audomo-qa-venv
/tmp/audomo-qa-venv/bin/python -m pip install pyftpdlib==2.2.0 pyOpenSSL==26.4.0 cryptography==50.0.2 paramiko==5.1.0
/tmp/audomo-qa-venv/bin/python qa/release_suite.py emulator-5554
```

Die [Suite](../qa/release_suite.py) führt zwölf Instrumentierungsklassen aus. Die Protokoll-Fixtures vergeben eigene Ports, Testzugänge und Zertifikate. ADB leitet nur diese Ports weiter. Nach Ende werden Weiterleitungen, Cacheprofil und lokale Testserver entfernt. Die Testinstallation verbleibt zur Inspektion auf dem Emulator. Ein weiterer Lauf beginnt mit einem frischen Testgerät. Übersprungene Tests führen in dieser Kernprüfung zu einem Fehler.

| Bereich | Beispiele aus der Suite |
|---|---|
| Aufnahme | Wiederholte Start-/Stoppbefehle, Pause/Fortsetzen, Profile, tatsächlich dekodierbares Audio |
| Bibliothek | 500 synthetische Einträge, Suche und Auswahl, Player, Dokumentaktionen und Freigaben |
| Kalender | Testkalender, Tagesansicht, Terminabbruch, einmalige Auswahl für die nächste Aufnahme |
| Warteschlange | Priorität, stabile IDs, Konflikt, Abbruch, Löschen, Katalog und mehrere Ziele |
| Transport | WebDAV, FTP, FTPS, SFTP mit lokalen Diensten; Dropbox-Kopplung und API-Anfragen mit simuliertem Transport |

## Gezielte Zusatzprüfungen

- Für Regressionen im Container und bei Wiederherstellung die Messserien in `qa/avd_recording.py`, `qa/system_interruptions.py` und `qa/codec_matrix.py` lesen. Die Aufrufparameter sind über `--help` zugänglich. Für die Auswertung gebrauchter Audioformate verwenden die Werkzeuge teilweise Core Audio auf macOS. Abbruch- und Neustarttests ausschließlich auf dem dafür vorgesehenen Emulator ausführen.
- Für Netzwechsel und Geräte-Neustart während eines Auftrags dient `qa/upload_neustart.py`. Für den SAF-Test dem QA-Emulator vorher den im Test erwarteten Android-Dokumentordner freigeben. Für den echten Kachellangdruck die Kachel zunächst in der Systemoberfläche hinzufügen. Voraussetzungen und Aufrufvarianten stehen bei den jeweiligen Tests und im [Abnahmeprotokoll](../qa/ABNAHME-0.2.md).
- Bei UI-Änderungen die tatsächliche Zurücktaste, große Schrift, Mehrfachauswahl über Bildschirmgrenzen und Gesten auf den betroffenen Zeilen erproben. Screenshots im [Geräteprotokoll](../qa/GERAETETEST.md) sind mit Version und Bedienweg verknüpft.
- Für die signierte APK einen separaten leeren Emulator benutzen. Zuerst die SHA-256-Summe der Datei und das öffentliche Zertifikat prüfen. APK installieren, Laufzeitrechte über die Einrichtung erteilen, aufnehmen, pausieren, fortsetzen, speichern, aus der Bibliothek abspielen und über „Warteschlange“ mit Android-Zurück zum Start zurückgehen. Die installierte APK über `adb shell pm path de.audomo` lokalisieren und ihren Hash vergleichen. So wird das tatsächliche Auslieferungsartefakt geprüft.
- Für einen echten Dropbox-Lauf ein freigegebenes Testkonto über den normalen OAuth-Dialog verbinden, eine synthetische Aufnahme hochladen, am Dienst prüfen und zurücklesen. Ein lokaler PKCE- oder HTTP-Test beantwortet diesen Punkt nur teilweise.
- Für den physischen Langzeittest eine entbehrliche Testaufnahme und definiertes Zeitmarkersignal verwenden. Begleitend Modell, Android-Version, effektive Mikrofonroute, Unterbrechungen, Dauer und Ogg-Endseite erfassen. Der vereinbarte 30-Minuten-Erstversuch mit Radio ist im [Messbericht](../qa/api34/radio-30min-s24.json) dokumentiert.

Die [44 Abnahmefälle](spezifikation/ABNAHMEPLAN.md) enthalten auch manuelle Hardware-, System- und Barrierefreiheitstests. Der [Freigabestand](../qa/RELEASE-1.0.0.md) benennt die für 1.0 nachgewiesenen Fälle und die offenen Grenzen.
