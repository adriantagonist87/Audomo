# Audomo für Android

Audomo nimmt Sprache als Ogg/Opus auf. Ein Kalendertermin kann den Dateinamen und den Zielordner bestimmen. Die Aufnahme bleibt lokal verfügbar, bis du selbst eine Ablageregel wählst. Über die Warteschlange lassen sich bestehende und neue Aufnahmen an einen oder mehrere Dienste übertragen.

Erstes Zielgerät ist das Samsung Galaxy S24 Ultra mit Android 14 und One UI 6.1. Audomo benötigt mindestens Android 14 (API 34) und ein Mikrofon. Die Bedienoberfläche ist deutsch.

## Installation

Auf dem Android-Gerät die APK aus dem offiziellen Release herunterladen und öffnen. Falls Android danach fragt, dem verwendeten Browser oder Dateimanager die Installation aus dieser Quelle erlauben. Zu jedem Release gehören die APK und ihre SHA-256-Prüfsumme. [Stabile Releases](https://github.com/adriantagonist87/Audomo/releases/latest) · [Prüfbericht zu 1.0.0](qa/RELEASE-1.0.0.md)

Die Testversionen bis 0.2.14 verwenden dieselbe Paketkennung mit einer anderen Signatur. Android kann die stabile Ausgabe deshalb auf dem Galaxy S24 mit installierter Testversion erst nach einer Deinstallation installieren. Eine Deinstallation entfernt private Aufnahmen, Warteschlange, Einstellungen und das Verzeichnis bestätigter Cloudkopien. Vor einem Wechsel die eigenen Daten sichern und die Sicherung prüfen. Wer das vollständige lokale Verzeichnis behalten möchte, kann die Testinstallation weiterverwenden. [Wechsel von der Testversion](docs/BEDIENUNG.md#wechsel-von-der-testversion)

## Die drei Reiter

| Aufnehmen | Wiedergeben | Warteschlange |
|---|---|---|
| Aufnahme starten, pausieren und speichern. Ein Tipp auf den Kalendertermin öffnet den Tag zur Auswahl des nächsten Namens. | Aufnahmen suchen, anhören, umbenennen, verschieben, auswählen oder hochladen. Speicherorte erscheinen am Eintrag. | Status pro Aufnahme und Ziel prüfen. Nach rechts wischen priorisiert, nach links wischen entfernt den Auftrag. Mehrfachauswahl und Leeren sind im selben Reiter erreichbar. |

Der Cloud-Upload unterstützt Dropbox, WebDAV, FTP, FTPS und SFTP. Automatische Übertragung lässt sich einschalten und in Minuten verzögern. Die App vergleicht den Inhalt der übertragenen Datei, bevor sie die Cloudkopie bestätigt. [Bedienungsanleitung mit Einstellungen, Ablage und Dropbox-Einrichtung](docs/BEDIENUNG.md)

## Selbst bauen

Das Repository direkt in Android Studio öffnen. Der Quellcode verwendet Java 17. Der getestete Build nutzt Android Studio JBR 25.0.4, SDK Platform 37, Build Tools 36.0.0, Gradle Wrapper 9.6.0 und Android Gradle Plugin 9.5.2. minSdk und targetSdk sind 34. Ein frischer Checkout benötigt beim ersten Build Zugriff auf Google Maven, Maven Central und die Gradle-Distribution.

```sh
git clone https://github.com/adriantagonist87/Audomo.git
cd Audomo
# macOS mit Android Studio und SDK am Standardort
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Die Test-APK liegt unter `app/build/outputs/apk/debug/app-debug.apk`. Nach dem Start eines Testemulators mit `adb devices -l` dessen Seriennummer ermitteln und gezielt installieren, etwa mit `adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`. ADB liegt in `$ANDROID_HOME/platform-tools`.

## Weiterentwicklung

| Einstieg | Inhalt |
|---|---|
| [AGENTS.md](AGENTS.md) und [Entwicklungsübergabe](docs/ENTWICKLUNG.md) | Einstieg für Menschen und KI, Quellkarte, Produktentscheidungen, Daten und Nebenläufigkeit |
| [Testanleitung](docs/TESTEN.md) und [Freigabeprotokoll](qa/RELEASE-1.0.0.md) | Reproduzierbare Prüfwege, Geräte und bekannte Grenzen |
| [Release-Handbuch](docs/RELEASE.md) | Versionieren, signieren, Prüfsummen, Veröffentlichung und Updates |
| [Featurematrix mit Implementierungsnachweisen](qa/ANFORDERUNGSABGLEICH.md) und [Designentscheidungen](qa/MOCKUPABGLEICH.md) | 122 Feature-IDs und 52 Gestaltungseinträge mit historischem Stand |
| [Geräteprotokoll](qa/GERAETETEST.md) und [Abnahmefälle](qa/ABNAHME-0.2.md) | Messwerte, Bildbelege und verbleibende Geräteprüfungen |
| [Architekturentscheidung](ADR-001.md) | Bewertung der ursprünglichen Quellbasis und Entwurf des Aufnahmepfads |

Der Audomo-Quellcode steht unter Apache License 2.0. Für die Bildmarke, Schrift und eingebundenen Abhängigkeiten gelten die jeweiligen Hinweise. [Lizenz](LICENSE) · [Drittanbieter und Assets](DRITTANBIETER.md)
