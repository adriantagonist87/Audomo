# Freigabeprotokoll 1.0.0

Am 23. November 2026 wurde der Funktionsstand nach 0.2.14 als erste stabile Ausgabe für die direkte APK-Verteilung freigegeben. Der Quellstand der App übernimmt die zuletzt am S24 verwendeten Funktionen. Für 1.0.0 ändern sich Versionsname, Versionscode und Signatur. Als Zielgerät gilt weiterhin das Galaxy S24 Ultra mit Android 14 und One UI 6.1.

| Artefakt | Geprüfter Wert |
|---|---|
| Installationsdatei | `Audomo-1.0.0.apk` |
| Manifest | `de.audomo`, Version 1.0.0, Code 20, minSdk 34, targetSdk 34, compileSdk 37 |
| Auslieferungsvariante | Release, Debugging deaktiviert, ein Signierer, APK-Signatur v3 bestätigt |
| SHA-256 der APK | [SHA256SUMS.txt zum GitHub-Release](https://github.com/adriantagonist87/Audomo/releases/download/v1.0.0/SHA256SUMS.txt) |
| SHA-256 des Signaturzertifikats | `02e25fd64a983b5ee87cf6a578f250d5835827673fd20e048b40b7df691fa338` |

## Für diesen Release erneut ausgeführt

| Prüfung | Ergebnis |
|---|---|
| Gradle und Lint | Release-APK, Debug-App und Instrumentierungs-APK gebaut. Android Lint für Release meldete 0 Fehler und 54 Warnungen. Darunter 34 Hinweise auf im Code zusammengesetzte UI-Texte, 6 zu Preference-Schreibvorgängen sowie 2 TrustManager-Hinweise. Die lokalen Protokolltests prüfen Zertifikatsablehnung und explizit vertrauenswürdige Gegenstellen. |
| JVM | 14 Tests in fünf Klassen bestanden. Umfang sind Ogg/Opus, Sitzungshelfer, digitale Verstärkung, Kalendernamen und Uploadverzögerung. |
| Android-Instrumentierung | 21 Tests in zwölf Klassen auf einem temporären Galaxy-S24-Ultra-AVD, Android 14 ARM64, bestanden. Der Runner verwarf übersprungene Fälle. Die Suite verwendete die Debug-Variante desselben Quellstands sowie vier lokale Server für FTP, FTPS, SFTP und WebDAV. Dropbox verwendete einen simulierten Transport. [Ausführbare Testreihe](release_suite.py) |
| Offizielle APK | Auf demselben temporären AVD nach Entfernen der reinen Testinstallation frisch installiert und kalt gestartet. Mikrofon- und Benachrichtigungsrecht über die Einrichtung erteilt. Aufnahme gestartet, pausiert, fortgesetzt und gespeichert. Eine Aufnahme mit 24 Sekunden erschien als „Lokal“. Wiedergabe gestartet und pausiert. Android-Zurück führte aus der Warteschlange über die Bibliothek wieder zum Aufnahmestart. |
| Identität des Installats | Der SHA-256 der vom Android-Paketmanager gemeldeten `base.apk` entspricht der veröffentlichten APK-Prüfsumme. Manifest und Zertifikat kontrolliert. |

## Vorbefunde und Grenzen der Freigabe

- Auf dem physischen S24 sind Aufnahme und Wiedergabe vom Nutzer bestätigt. Der 30-Minuten-Hintergrundlauf mit einer Radiosendung unter 0.1.3 ergab 29:59,68 Minuten dekodierbares Audio, eine gültige Endseite und kein vollständig stilles Einsekundenfenster. Haptik und nachfolgende 0.2-Aktualisierungen sind im Geräteprotokoll dokumentiert. Die öffentliche Release-Signatur ist neu. Die bestehende Debug-Installation auf dem persönlichen S24 blieb erhalten. [Messdaten](api34/radio-30min-s24.json) · [Ergebnisse nach App-Version](GERAETETEST.md)
- Das Aufrufen der Dropbox-Anmeldung und die lokalen Integrationsprüfungen sind dokumentiert. Echte Kontofreigabe, Upload mit produktiven Serverantworten und anschließender Rückvergleich bei Dropbox stehen aus.
- Ein mehrstündiger Lauf mit kalibrierten Zeitmarkern, die Kombination aus Anruf, Mikrofonkonkurrenz und One-UI-Energiesparzuständen sowie die physische Mikrofonkapsel sind nicht vollständig vermessen. Gleiches gilt für TalkBack über alle Bedienwege. Weitere Einschränkungen sind pro Feature bzw. Abnahmefall verzeichnet. [Funktionsmatrix](ANFORDERUNGSABGLEICH.md) · [Abnahmefälle](ABNAHME-0.2.md)
- Der automatische oder manuelle Upload benötigt ein eingerichtetes Ziel. Bei FTP/SFTP können Änderungen eines anderen Clients zwischen der letzten Prüfung und einer Serveroperation auftreten. Dieser Rest bleibt bei gemeinsamen Zielverzeichnissen relevant. [Transferprüfungen](ABNAHME-0.2.md)

Die Freigabe als stabile 1.0.0 schließt diese dokumentierten Grenzen ein.
