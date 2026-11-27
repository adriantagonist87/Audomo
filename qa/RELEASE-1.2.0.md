# Audomo 1.2.0

Die Ausgabe für Android 14 und neuer hat den Versionscode 36. Sie aktualisiert die offizielle 1.1.11 mit dem bisherigen Audomo-Signierschlüssel.

## Was sich für einen Gerätewechsel ändert

Unter „Sicherung und Diagnose“ entscheidet der Nutzer, was mitkommt. Die erste Stufe überträgt die Einstellungen. Die zweite schließt gespeicherte Cloudverbindungen ein. Die dritte überträgt zusätzlich die gesamte Bibliothek, den Papierkorb, die Warteschlange, bestätigte Cloudpfade und das Diagnoseprotokoll. Alle drei Archive haben ein eigenes Passwort. Frühere JSON-Exporte lassen sich weiterhin lesen. Ein abgelaufener oder widerrufener Cloudzugang kann eine erneute Anmeldung erfordern. Android vergibt Geräteberechtigungen und Dokumentordnerfreigaben auf dem jeweiligen Telefon. [Anleitung zum Gerätewechsel](../docs/BEDIENUNG.md#sicherung-und-gerätewechsel)

Die Bibliothek verwendet bei einer Aufnahme den Singular. Die Reiterbeschriftung passt sich bei vergrößerter Systemschrift an die verfügbare Breite an.

## Prüfung am 27. November 2026

| Bereich | Ergebnis |
|---|---|
| Android 14 ARM64 | 33 instrumentierte Kernprüfungen bestanden, darunter automatische und verzögerte Uploads, Papierkorb vor Upload, verifizierte Cloudkopien, 500 Bibliothekseinträge, Kalender und Navigationsverlauf. WebDAV, FTP, FTPS und SFTP liefen gegen eigene Server. Dropbox-Anfragen liefen mit simulierten Dienstantworten. |
| Gerätewechsel | Sechs weitere Instrumentierungsprüfungen bestanden. Darunter ein neuer Android-Keystore, unveränderte Aufnahme-IDs und Audiobytes, Papierkorb und Auftragsversuche, ein Archiv über mehrere Verschlüsselungssegmente, falsches Passwort sowie Rücknahme eines erzwungenen Datenbankfehlers nach Beginn der Übernahme. Der Cloudkatalogtest lud eine ausschließlich auf dem FTP-Testserver verfügbare Aufnahme für das Archiv zurück und stellte sie lokal hashgleich wieder her. |
| Build | 14 JVM-Tests bestanden. Android Lint für die Release-Variante meldete keine Fehler. |
| Ausgangsversion 1.1.11 | 43 Menüpfade einschließlich aller zehn Einstellungskategorien im Release-Emulator geöffnet. 20 reguläre Aufnahmen und 20 Prozessabbrüche ausgewertet. Nach 30 Minuten Bildschirm-Aus-Betrieb ließ sich die Aufnahme exportieren und vollständig dekodieren. 9.022 Ogg-Seiten bestanden die CRC-Prüfung, 86.586.705 Sampleframes entsprachen 30:03,89 Minuten einschließlich der Bedienzeit zum Stoppen. |
| Gerät und Hardware | Der frühere 30-Minuten-Radiotest und die reale Bluetooth-Ausgabe sind im Geräteprotokoll dokumentiert. Für diesen Lauf wurde am Emulator der virtuelle Mikrofoneingang verwendet. Ein neuer Dropbox-OAuth-Durchlauf mit einem persönlichen Konto, ein echter Telefonanruf, die akustische Wirkung der Samsung-Mikrofonmodi und eine vollständige TalkBack-Prüfung sind gesonderte Hardwareprüfungen. |

[Testreihe](release_suite.py) · [Geräteprotokoll](GERAETETEST.md) · [Release mit APK und Prüfsumme](https://github.com/adriantagonist87/Audomo/releases/tag/v1.2.0)
