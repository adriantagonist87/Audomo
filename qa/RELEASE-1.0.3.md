# Audomo 1.0.3

Die stabile Ausgabe vom 25. November 2026 fasst die seit 1.0.0 auf dem Galaxy S24 Ultra erprobten Änderungen zusammen. Das Paket heißt `de.audomo`, trägt den Versionscode 23 und unterstützt Android ab API 34.

## Aufnahmen nachträglich umbenennen

In „Wiedergeben“ eine Aufnahme lange drücken und „Umbenennen“ wählen. Bei einer lokalen Aufnahme steht die Aktion auch an erster Stelle unter „Mehr“. Die Änderung kann für das Gerät und für jede gespeicherte Cloudkopie einzeln gelten. Bei einer Aufnahme, die nur noch in der Cloud liegt, steht sie ebenfalls zur Verfügung. Audomo behält den Ordner und die Dateiendung bei. Einen belegten Namen meldet die Warteschlange als Konflikt. [Bedienung und Bildschirmansicht](../docs/BEDIENUNG.md#aufnahme-umbenennen)

## Dropbox und Uploadfehler

Auf dem S24 scheiterte der Verbindungsaufbau, wenn die zuerst gelieferte IPv6-Adresse unerreichbar war. Audomo kann eine weitere Adresse versuchen. Das Ergebnis des Verbindungstests bleibt bis zum Schließen sichtbar. Fehler bei der Übertragung erscheinen in der Warteschlange und per Android-Benachrichtigung. Nach erneutem Speichern der Zugangsdaten reiht Audomo fehlgeschlagene Aufträge dieses Ziels wieder ein.

## Prüfstand

- Auf dem physischen S24 mit Android 14 wurden ein zuvor fehlgeschlagener Dropbox-Upload und das vollständige Zurücklesen der Datei mit übereinstimmendem SHA-256 bestätigt. Mit dem Testbuild 1.0.3 wurden „Mehr → Umbenennen“, die Wahl zwischen Gerät und Dropbox sowie der Rückweg zur Auswahl geprüft. [Geräteprotokoll](GERAETETEST.md)
- Die Android-14-Kernprüfung umfasst 24 instrumentierte Tests. Die serverseitige Umbenennung wurde gegen lokale WebDAV-, FTP-, FTPS- und SFTP-Server geprüft. Für Dropbox wurden die Umbenennungsanfrage und der Namenskonflikt mit simuliertem Transport getestet. Die Umbenennung in einem echten Dropbox-Konto steht noch aus.
- Die bekannten Grenzen bei mehrstündigen Aufnahmen, Anrufen während der Aufnahme und der vollständigen TalkBack-Abnahme gelten weiter. [Freigabeprotokoll 1.0.0](RELEASE-1.0.0.md)

## Installation

Wer die offizielle 1.0.0 installiert hat, kann die APK als normales Update einspielen. Für ein Gerät mit einer debug-signierten Testfassung gilt die Anleitung zum Wechsel des Signierschlüssels. [Wechsel von der Testversion](../docs/BEDIENUNG.md#wechsel-von-der-testversion)

Die offizielle APK und ihre SHA-256-Prüfsumme stehen bei der [GitHub-Veröffentlichung](https://github.com/adriantagonist87/Audomo/releases/tag/v1.0.3). Das öffentliche Signaturzertifikat ist dasselbe wie bei Version 1.0.0. [Fingerabdruck](../docs/release-certificate.sha256)
