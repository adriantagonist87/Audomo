# Audomo 1.3.0

Für Android 14 und neuer. Die APK hat den Versionscode 37 und verwendet den bisherigen Release-Schlüssel.

## Bedienwege

| Wo | Was sich geändert hat |
|---|---|
| Einstellungen → Cloud und Uploads → Aus Dropbox wiederherstellen | Audomo liest den gewählten App-Ordner samt Unterordnern ein, prüft die Audiobytes und ergänzt die lokale Bibliothek. Das funktioniert auch auf einem neuen Gerät. Die Abschlussmeldung nennt hinzugefügte, vorhandene und übersprungene Aufnahmen. |
| Uploadziel auswählen | Der App-Ordner heißt `Dropbox/`. Bei eingerichtetem Unterordner erscheint etwa `Dropbox/Projekte`. |
| Einstellungen → Berechtigungen → Akkuoptimierung | Android kann eine Ausnahme für Audomo erteilen. Beim Zurückkehren zeigt die App den aktuellen Status. |
| Wiedergeben → Aufnahme antippen | Drei gleich hohe Tasten steuern die Wiedergabe. Am Zahnrad rechts lassen sich Tempo, Sprungintervall und „Stille überspringen“ ändern. Dieselben Werte gelten unter Einstellungen → Wiedergabe. Im breiten Querformat stehen Liste und Player nebeneinander. |
| Aufnehmen, Wiedergeben und Warteschlange | Lange Dateinamen laufen über mehrere Zeilen. Auch der Player zeigt den ganzen Namen. |

[Player hochkant](api34/player-1.3.0.webp) · [Player im Querformat](api34/player-querformat-1.3.0.webp) · [Player-Einstellungen](api34/player-optionen-1.3.0.webp) · [Berechtigungen](api34/berechtigungen-1.3.0.webp)

## Prüfung am 27. November 2026

Die Prüfreihe auf einem isolierten Android-14-ARM64-Emulator umfasst 35 Kernprüfungen und sechs Migrationsprüfungen. Der Dropbox-Test verwendet synthetische Antworten und eine echte Ogg-Testdatei. Er prüft die rekursive Auflistung mit Folgeseite, einen leeren Unterordner, die bytegleiche Wiederherstellung, eine bereits lokal importierte Datei ohne Cloudhistorie, die erneute Wiederherstellung unter derselben UUID sowie abweichende lokale Bytes und eine falsche Dropbox-Prüfsumme. Ein unabhängiger Referenzwert deckt die 4-MiB-Grenze des Blockhashs ab. Ein Produktionskonto wurde für diesen Abruf nicht verwendet.

Auf einem zweiten API-34-Emulator wurde eine Aufnahme mit langem Dateinamen über den Android-Dateidialog in die offizielle 1.2.0 importiert. Nach dem Update blieb die Aufnahme im Player zugänglich. Geprüft wurden die Ausrichtung der drei Tasten, der Zugang zu den Player-Einstellungen in beiden Ausrichtungen, die Wirkung ihrer Werte im allgemeinen Einstellungsmenü, die Lautsprecherrückfrage im Querformat und der mehrzeilige Dateiname im Aufnahme-Editor. Android zeigte den Dialog zur Akku-Ausnahme an. Nach Bestätigung wechselte die Anzeige in Audomo auf „Für Audomo ausgeschaltet“.

Die 14 JVM-Tests bestanden. Release-Lint meldete 0 Fehler und 54 Warnungen, darunter den Hinweis zur direkten Anforderung einer Akku-Ausnahme. Die GitHub-Fassung fordert diese Ausnahme auf ausdrücklichen Tipp unter „Berechtigungen“ an. [Testwerkzeug](release_suite.py) · [Handbuch](../docs/BEDIENUNG.md)
