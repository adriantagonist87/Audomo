# An Audomo weiterarbeiten

Das Repository enthält die Android-App, Testwerkzeuge und die dokumentierten Entscheidungen. Zum Bauen, Prüfen und Weiterentwickeln genügt ein Checkout. Die Oberfläche und die Projektdokumentation sind deutsch.

## Reihenfolge für den Einstieg

1. In der [README](README.md) den vorgesehenen Bedienweg lesen. Das [Handbuch](docs/BEDIENUNG.md) erklärt die Aktionen und ihre Folgen für Nutzer.
2. Den betroffenen Pfad in der [Entwicklungsübergabe](docs/ENTWICKLUNG.md) nachschlagen. Vor einer Änderung die aufrufende Activity, den schreibenden Dienst und die vorhandenen Tests zusammen lesen.
3. Über die [Feature-IDs](qa/ANFORDERUNGSABGLEICH.md), den [Gestaltungsabgleich](qa/MOCKUPABGLEICH.md) und das [aktuelle Releaseprotokoll](qa/RELEASE-1.1.11.md) ermitteln, welche Entscheidung gilt und welches Verhalten tatsächlich geprüft wurde. Die historischen 0.2-Berichte behalten ihren Versionsbezug.
4. Eine Änderung über den gewöhnlichen Einstieg der App und mit bereits gespeicherten Daten prüfen. [Testanleitung](docs/TESTEN.md)

## Produkt und Daten

- Erstgerät ist das Galaxy S24 Ultra unter Android 14 und One UI 6.1. Neue Plattformaufrufe oberhalb API 34 mit Versionsprüfung und einem nutzbaren Alternativpfad versehen. Mikrofonrichtung, Effekte und Vibrationswahrnehmung auf Samsung separat prüfen.
- Die UUID identifiziert eine Aufnahme. Die sichtbare Bezeichnung, Ordner, Dokument-URI, Cloudpfad und Zeilenposition können sich ändern. Auswahl von Aufnahmen und von Uploadaufträgen jeweils an die zugehörige stabile ID binden.
- Der Aufnahmeprozess muss ohne Kalenderzugriff, Cloudkonto oder Netzwerk speichern können. Änderungen an Aufnahme, Container oder Wiederherstellung mit Pause, doppelten Befehlen und Prozessabbruch testen. Die ursprünglichen Audiodaten erhalten.
- Bestätigte Cloudkopien und die sichtbare Warteschlange haben unterschiedliche Lebenszyklen. Vor Änderungen an Löschen, Leeren, Wiederholen oder Priorisieren den laufenden Transfer, den Serverabschluss und einen Prozessneustart abdecken.
- Den Android-Verlauf von der tatsächlich aufgerufenen Ansicht zurück bis „Aufnehmen“ erhalten. Der lange Druck auf die Kachel öffnet die bestehende App-Ansicht. Bedienaktionen zusätzlich ohne Wischgeste zugänglich halten.
- Für Screenshots, Logs und Serverprüfungen synthetische Aufnahmen und Kalender verwenden. Zugangsdaten, produktive Inhalte und Signiermaterial bleiben außerhalb von Git.
- Neue Produktentscheidungen mit Datum oder Version und betroffenem Bedienweg festhalten. Nur tatsächlich ausgeführte Tests mit Ergebnis protokollieren. Quelle, Vermutung und Beobachtung unterscheiden.

Neue konkrete Nutzerentscheidungen haben Vorrang vor alten Mockups. Für den Aufnahmestart gilt beispielsweise der runde gedeckt grüne Aufnahmepunkt. Für den Player gilt weiterhin Play. Die Prioritäten und ausgewählten optionalen Funktionen stehen im [Funktionsabgleich](qa/ANFORDERUNGSABGLEICH.md).
