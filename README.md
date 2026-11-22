# Audomo für Android

Der Entwicklungsstand 0.2 richtet sich an das Galaxy S24 Ultra mit Android 14 und One UI 6.1. Aufnehmen, Pausieren, Fortsetzen, Speichern und Wiederherstellen laufen auf dem eingerichteten ARM64-AVD. Der vollständige Lieferumfang und die offenen Geräteprüfungen stehen im [Funktionsabgleich](qa/ANFORDERUNGSABGLEICH.md).

## Bauen und installieren

Dieses Verzeichnis als Projekt in Android Studio öffnen. Das zugehörige Übergabepaket liegt daneben unter `../Audomo/`. Die App verwendet die Paketkennung `de.audomo`, minSdk 34, targetSdk 34 und compileSdk 37. Der Build wurde mit Android Studio JBR 25.0.4, Android SDK Platform 37.0, Build Tools 36.0.0, Gradle 9.6.0 und Android Gradle Plugin 9.5.2 ausgeführt. Der erste Build benötigt Zugriff auf die konfigurierten Gradle-, Google- und Maven-Repositories.

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="/Users/remote-ai-user/Library/Android/sdk"
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
"$ANDROID_HOME/platform-tools/adb" devices -l
```

Unter `app/build/outputs/apk/debug/app-debug.apk` liegt die mit dem lokalen Android-Debugschlüssel signierte APK. Den Ziel-AVD bei Bedarf in Android Studio starten. Die Seriennummer in den folgenden Beispielen mit `adb devices -l` und `emu avd name` abgleichen.

```sh
"$ANDROID_HOME/platform-tools/adb" -s emulator-5556 emu avd name
"$ANDROID_HOME/platform-tools/adb" -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/platform-tools/adb" -s emulator-5556 shell am start -n de.audomo/.MainActivity
```

## Erste Aufnahme

Im Reiter „Aufnehmen“ startet die grüne Play-Taste die Mikrofonfreigabe. Während der Aufnahme steuern Quadrat und Pause die Sitzung. Aus dem pausierten Zustand setzt Play dieselbe Datei fort. Nach einem bestätigten Stopp steht die Datei unter „Wiedergeben“ bereit. Eine unterbrochene Sitzung wird beim nächsten App-Prozessstart geprüft. Die App startet dabei das Mikrofon ausschließlich auf Nutzeraktion.

Die drei Standardprofile verwenden Opus/Ogg, Mono, 48 kHz, +5 dB und 48, 80 oder 32 kbit/s. „Profil“ öffnet die Auswahl und den Editor für eigene Profile. Angaben zur physischen Kapsel sind geräteabhängig. Der Aufnahmebildschirm zeigt die gemeldete Route und kennzeichnet die gewünschte Richtung. Die Diagnose zeigt den über Android ermittelten Effektzustand.

Im Kopfmenü lassen sich Benachrichtigungen erlauben, die Systemabfrage für die Kachel öffnen, die Haptik einstellen und Kalender für die Benennung auswählen. Bei der ersten Verwendung der Kachel muss der Mikrofonzugriff bereits erteilt sein. Der optionale Nicht-stören-Zugriff erlaubt Audomo die Steuerung einer eigenen Aufnahmeregel.

Unter „Vibration“ hat jedes Ereignis eine Taste „Testen“. Ein langer Impuls dauert 500 ms. Zwei oder drei kurze Impulse dauern jeweils 300 ms mit 200 ms Pause dazwischen. Auf dem Galaxy S24 müssen Benachrichtigungen für Audomo erlaubt sein, damit diese Muster fühlbar sind. Bei gesperrter Freigabe führt „Freigabe öffnen“ zur Android-Einstellung.

„Wiedergeben“ öffnet die lokale Liste. Einen Eintrag auswählen und anschließend Play drücken. Die Dateiaktionen ermöglichen Teilen, externes Öffnen und Exportieren über den Android-Dateidialog. Originale liegen im privaten dauerhaften App-Speicher. Eine Deinstallation entfernt diesen Speicher. Zum Aufbewahren außerhalb der App „Datei exportieren“ verwenden.

## Einstellungen und Dateien

„Weitere Optionen“ → „Einstellungen“ öffnet die H1-Ansicht mit Aufnahme, Kalender und Dateinamen, Dateien, Cloud und Uploads sowie App und Diagnose. Unter „Speicher und Import“ lässt sich ein Android-Dokumentordner freigeben. Audomo sichert eine Aufnahme zuerst im privaten Speicher, kopiert sie in den ausgewählten Ordner und liest die Kopie zur SHA-256-Prüfung zurück. Bei einem Zugriffsfehler bleibt die private Sicherung erhalten.

Ein Tipp in der Bibliothek öffnet die Schnellaktionen und den Player. Langes Drücken aktiviert Mehrfachauswahl. Dateien werden zuerst in den Papierkorb gelegt. Die 30-Tage-Bereinigung wird separat aktiviert. Beim Import werden Quelldateien unverändert kopiert. Der Einstellungs-Export enthält keine Audioinhalte, Zugangscodes oder Passwörter.

Uploads sind anfangs ausgeschaltet. Ein Ziel wird in „Uploadziele“ eingerichtet. Dropbox verwendet einen öffentlichen App-Key und OAuth mit PKCE. Für SFTP wird der öffentliche Hostschlüssel des vorgesehenen Servers benötigt. FTPS verwendet explizites TLS, WebDAV HTTPS. Zugangsdaten liegen AES-GCM-verschlüsselt mit einem Schlüssel aus dem Android-Keystore. Erst nach vollständigem Rücklesen und SHA-256-Vergleich gilt eine Übertragung als bestätigt.

## Entwicklung und Nachweise

- [Entscheidung zur Quellbasis, Architektur und Abhängigkeiten](ADR-001.md)
- [Abnahme 0.2 nach allen 44 Kernfällen](qa/ABNAHME-0.2.md) und [alle Gestaltungsbeschlüsse](qa/MOCKUPABGLEICH.md)
- [Geräte- und Testprotokoll](qa/GERAETETEST.md)
- [Abgleich der 122 Feature-IDs](qa/ANFORDERUNGSABGLEICH.md)
- [Quellcodelizenz](LICENSE) und [Hinweise zu Bibliotheken und Markenassets](DRITTANBIETER.md)

Für die wiederholbare Kurzserie muss der Test-AVD entsperrt sein. Audomo einmal über den Android-Dialog Mikrofonzugriff gewähren und eine laufende Aufnahme beenden. Das Skript legt eigene Testsitzungen an, beendet für Abbruchtests ausschließlich den Audomo-Prozess und nutzt Core Audio auf macOS als unabhängigen Decoder.

```sh
python3 qa/avd_recording.py emulator-5556 /tmp/audomo-api34-messung
```

Der Instrumentierungstest verwendet ausschließlich synthetische PCM-Prüftöne. Für die Codec-Matrix auf demselben AVD gelten die folgenden Befehle.

```sh
./gradlew :app:assembleDebugAndroidTest
"$ANDROID_HOME/platform-tools/adb" -s emulator-5556 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
python3 qa/codec_matrix.py emulator-5556 /tmp/audomo-api34-codecs
```
