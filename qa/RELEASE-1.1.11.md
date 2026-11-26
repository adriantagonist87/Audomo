# Audomo 1.1.11

Die stabile Ausgabe vom 27. November 2026 unterstützt Android ab Version 14 (API 34). Paketname ist `de.audomo`, Versionscode 35.

## Änderungen seit 1.0.3

- Beim Wischen zwischen Aufnehmen, Wiedergeben und Warteschlange folgt der Inhalt dem Finger. Logo, Zahnrad und Reiter bleiben stehen. Android-Zurück führt zum zuletzt besuchten Menü.
- Der Papierkorb hat einen eigenen Eintrag am Zahnrad und lässt sich in der Wiedergabeliste über das Symbol links neben der Lupe öffnen. Vor dem Leeren zeigt Audomo die Anzahl der betroffenen Aufnahmen. [Papierkorb-Einstellungen](api34/papierkorb-einstellungen-1.1.11.webp)
- Wer eine Aufnahme während der Upload-Wartezeit löscht, entfernt damit auch ihre offenen Uploads. Bereits bestätigte Cloudkopien bleiben beim lokalen Löschen erhalten. Die Warteschlange merkt sich den gewählten Filter und zählt die darin sichtbaren Aufträge.
- Die optionale App-Sperre schützt die Bibliothek und die Einstellungen per Android-Biometrie oder Gerätesperre. Die Schnellkachel und die neue Startbildschirm-Verknüpfung können eine Aufnahme weiterhin starten und stoppen.
- Der Player fragt vor der Wiedergabe über den Handylautsprecher nach. „Nur Bluetooth“ erlaubt die Wiedergabe ausschließlich über einen Bluetooth-Medienausgang.
- Eigene Aufnahmeprofile lassen sich am Zahnrad anlegen, umbenennen und löschen. Die Berechtigungen für Mikrofon, Kalender, Benachrichtigungen und „Nicht stören“ sind an einer Stelle zusammengefasst.

## Prüfstand

Die Version bestand 33 instrumentierte Tests auf dem Android-14-Emulator und 14 JVM-Tests. Android Lint meldete für die Release-Variante keine Fehler. Auf dem Galaxy S24 Ultra wurden unter anderem der feste Dashboard-Kopf, die Papierkorbverwaltung und der Rückweg zur Wiedergabeliste geprüft. Für Löschtests kamen synthetische Aufnahmen auf dem Emulator zum Einsatz. [Geräteprotokoll](GERAETETEST.md)

Die dokumentierten Grenzen bei mehrstündigen Aufnahmen mit Zeitmarkern, Anrufen während der Aufnahme, One-UI-Energiesparzuständen und der vollständigen TalkBack-Abnahme gelten weiter. [Abnahmegrenzen](RELEASE-1.0.0.md#vorbefunde-und-grenzen-der-freigabe)

## Installation

Die offizielle APK und ihre SHA-256-Prüfsumme stehen bei der [GitHub-Veröffentlichung](https://github.com/adriantagonist87/Audomo/releases/tag/v1.1.11). Für die stabile Ausgabe gilt weiterhin das bisherige [Signaturzertifikat](../docs/release-certificate.sha256). Wer eine debug-signierte Testfassung nutzt, muss für den Wechsel seine Daten sichern. [Wechsel von der Testversion](../docs/BEDIENUNG.md#wechsel-von-der-testversion)
