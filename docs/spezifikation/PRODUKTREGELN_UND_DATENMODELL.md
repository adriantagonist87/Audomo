# Produktregeln und Datenmodell

Die beschlossenen Funktionen stehen in den Einzelentscheidungen. Dieses Dokument schließt technische Übergänge und Datenregeln. Wo die vorherige Auswahl kein konkretes Implementierungsdetail bestimmt, ist die hier genannte Lösung ein Umsetzungsvorschlag. Gleichwertige Lösungen sind mit Begründung zulässig, ohne die Produktvorgaben erneut auszuwählen.

## 1. Bereiche und Navigation

| Bereich | Zugang | Hauptinhalt |
|---|---|---|
| Aufnehmen | Hauptreiter, Startansicht | Aktueller Termin, editierbarer Name, Zeit, Zustand, Steuerung |
| Wiedergeben | Hauptreiter oder Dateien | Aktueller Ordner, Liste, Suche, Sortierung, Auswahl und Listenplayer |
| Einstellungen | Kopfmenü | Kompakte H1-Bereiche ohne dekorative Icons |
| Kalender | Einstellungen | Kalenderauswahl, Priorität und zwei kompakte Toleranzfelder |
| Profile | Profilaktion oder Einstellungen | P1-Auswahlblatt, Liste und einseitiger R1-Editor |
| Uploads | Einstellungen und Statusdetails | Zielliste, Verbindung, Warteschlange |
| Papierkorb | Dateiverwaltung | Wiederherstellen, endgültiges Löschen, optionale Bereinigung |
| Diagnose | Einstellungen | Ereignisse, Filter, Details und Export |

Die Auswahl einer Datei wird gemeinsam von F2-Aktionen und Listenplayer genutzt. Ein bloßer Auswahltipp startet nicht eigenständig Audio. Die Play-Aktion startet Wiedergabe. Langes Drücken aktiviert Mehrfachauswahl. Der Player bleibt im Bereich Wiedergeben und übernimmt nicht die Aufnahmesteuerung. Dies ist eine konsistente technische Auslegung der beiden gewählten Listeninteraktionen.

Vorgeschlagenes Verhalten bei aktiver eigener Wiedergabe: Ein Aufnahmestart pausiert sie vor dem Öffnen des Mikrofons. Während der Aufnahme wird keine eigene Lautsprecherwiedergabe automatisch gestartet. Ein geplanter gleichzeitiger Aufnahme-/Abspielmodus ist nicht Teil der Auswahl.

## 2. Aufnahmeabläufe

| Auslöser | Ablauf | Bestätigung |
|---|---|---|
| Start in App | Eventuellen manuellen Namen sichern, Sitzung anlegen, Eingang und Writer öffnen | Startmuster erst nach tatsächlichen Frames und schreibbarem Ziel |
| Start über Kachel | Android-konformer Startkontext, gleiche Engine, kein Namensdialog | Gleiches Startmuster, danach vorherige App |
| Pause | Aufnahmezeit und Datenzufluss anhalten, Sitzung erhalten | Sichtbarer Zustand Pausiert |
| Fortsetzen | Gleiche Sitzung fortführen | Zeit läuft mit tatsächlich aufgezeichnetem Material weiter |
| Stoppen | Eingabe beenden, Container abschließen, Datei prüfen und sichern | Stoppmuster nach erfolgreicher lokaler Sicherung |
| Fehler | Kein Erfolgssignal, gesicherte Daten erhalten, Ursache melden | Fehlermuster, nicht blockierender Fehlerbereich und passende Notification |
| Neustart nach Abbruch | Journal prüfen, lesbare Datei retten, Diagnoseeintrag | Hinweis W1; keine selbstständige neue Aufnahme |

Die angezeigte Aufnahmedauer misst aufgezeichnetes Audio ohne Nutzerpausen. Der ursprüngliche Startzeitpunkt bleibt für Dateinamen und Metadaten unverändert. Eine Aufnahme hat keine künstliche App-Zeitbegrenzung, verfügbare Hardware- und Speicherressourcen bleiben tatsächliche Grenzen. Eine lange Nutzerpause ist keine beschädigte Sitzung.

Die Quick-Settings-Kachel stoppt auch eine gerade pausierte Sitzung. Doppelte Befehle aus App, Kachel oder Benachrichtigung werden über dieselbe Sitzung dedupliziert. Während Finalisierung wird kein zusätzlicher Start angenommen. Fehlende Berechtigungen gehören in einen klaren Fehlerpfad.

## 3. Kalender und Namen

Die Kalenderabfrage bestimmt den Titel, nicht den Beginn der Aufnahme. Ausgewählte Kalender und deren Reihenfolge werden gespeichert. Ganztägige Einträge werden ignoriert. Vor- und Nachlauf sind unabhängig frei editierbare nichtnegative Minutenwerte, 0 deaktiviert die jeweilige Erweiterung. Beispielwerte aus dem Bild sind keine zusätzlichen Defaults.

Die technische Reihenfolge bei Überschneidung ist in TECHNIK_ERGAENZUNG dokumentiert: tatsächlich laufend vor nur toleriert, anschließend Kalenderpriorität und deterministischer Gleichstand. Die Regeln werden im Kalenderbereich knapp erläutert und getestet. Ein manuell eingegebener Name bleibt auch bei verspäteter Kalenderantwort erhalten.

Vorgeschlagene Startvorlage: `{datum}_{uhrzeit}_{termin}`. Datum als YYYY-MM-DD, Uhrzeit als HH-mm-ss. Das Mockup zeigt teilweise HHmm als verkürztes Beispiel, das ist kein Verbot einer Sekundenauflösung. Vollständiger ISO-8601-Zeitpunkt mit Offset wird unabhängig vom Dateinamensschema gespeichert. Ohne Termin oder nutzbaren Titel lautet der Titelteil „Aufnahme“. Dateiendung wird aus dem Format abgeleitet und separat geschützt.

Die endgültige Datei erhält vor Upload genau einen konfliktfreien Namen. Pfadtrenner, Steuerzeichen, reservierte Namen und unzulässige Zeichen werden bereinigt. Ein lokaler Konflikt erhält einen eindeutigen Zusatz. Den Namen nicht allein als Datenbankidentität verwenden. Name und Kalenderdaten sind nicht Teil einer öffentlichen Sperrbildschirmmeldung.

## 4. Dateien und Import

Original bedeutet unveränderte Audioinhalte. Umbenennen und Verschieben verändern keine Audioframes; optionale spätere Konvertierung würde eine separate abgeleitete Datei erzeugen. Schneiden und Stille-Entfernung während der Aufnahme sind nicht enthalten.

Import kopiert Bestandsdateien. Die Quelle wird weder verändert noch gelöscht. Bei gleichen Bytes können bereits importierte Dateien erkannt werden. Gleicher Name allein ist kein Duplikatbeweis. Ein belegter Zielname wird ohne Überschreiben aufgelöst; der ursprüngliche Name bleibt als Importmetadatum erhalten. Bei nicht dekodierbarem Inhalt wird ein Importfehler angezeigt, keine erfundene Dauer.

Lokal gelöschte Dateien gelangen zunächst in den Papierkorb. Wiederherstellung berücksichtigt den ursprünglichen Ordner und Namenskonflikte. Automatische 30-Tage-Bereinigung ist standardmäßig aus. Aktive Aufnahmedateien und noch ungesicherte Recovery-Dateien sind für destructive Dateiaktionen gesperrt. Ausgewählte Mehrfachaktionen arbeiten pro Datei und zeigen Teilergebnisse, statt bei einem Fehler alles als erfolgreich zu melden.

## 5. Upload und Aufbewahrung

Pflichtziele sind Dropbox, WebDAV, FTP/FTPS und SFTP. FTP und FTPS sind getrennte Testpfade. Ein Standardziel genügt für den Pflichtumfang; parallele Mehrfachziele bleiben optional.

Autoupload beginnt nach gesicherter Finalisierung der Datei, sofern eingerichtet und aktiviert. Reihenfolge: lokalen Abschluss prüfen, Queueauftrag speichern, übertragen, Remote-Inhalt verifizieren, Erfolg speichern, gegebenenfalls lokal in den Papierkorb. Netzregeln gelten auch für Wiederholungen und gegebenenfalls Rücklesen zur Verifikation. „Nur WLAN“ und mobile Daten sind auswählbare Verhaltensweisen, kein widersprüchlicher Doppelzwang.

Lokales Behalten ist Voreinstellung. Automatisches lokales Aufräumen nach Upload muss gesondert aktiviert sein. Es löscht keine Cloudkopie. Es darf nicht ausgelöst werden bei Fehler, unklarem Inhalt, Namenskonflikt, ausgelassenem Upload oder einem nicht abgeschlossenen erforderlichen Ziel. Während noch ein anderer Job dieselbe lokale Datei benötigt, wird sie nicht entfernt.

## 6. Namenskonflikte und Remote-Mutationen

| Aktion | Wirkung |
|---|---|
| Neuer Name | Für diesen Upload einen kollisionsfreien Remote-Namen bestimmen; lokalen Namen nicht heimlich ändern |
| Direkt überschreiben | Zusätzliche Bestätigung nach der dokumentierten Sicherheitsregel, dann genau das geprüfte Zielobjekt ersetzen |
| Upload auslassen | Nur diesen Uploadauftrag als ausgelassen markieren; lokale Datei behalten |

Die drei Buttons bleiben nebeneinander, ohne eigene Beschreibung oder Icon. Andere unabhängige Uploads dürfen weiterlaufen, während ein Konflikt auf Eingabe wartet.

Späteres lokales Umbenennen oder Verschieben kann die bekannte Cloudkopie mitändern, wenn der Nutzer die jeweilige Option aktiviert. Ohne die Option bleibt die Kopie unverändert. Als technische Voreinstellung werden diese Optionen nicht vorausgewählt. Ein Remote-Fehler darf nicht als lokale Fehlermeldung ohne Erklärung verschwinden; beide Ergebnisse werden getrennt gespeichert. Ein Timeout nach serverseitigem Erfolg wird durch Abfrage des tatsächlichen Remote-Zustands geklärt, nicht blind durch Löschen oder erneuten Upload.

Manuelles lokales Löschen kann zusätzlich die Cloudkopie löschen. Dieser Schalter ist standardmäßig aus, Remote-Löschen benötigt die zusätzliche Bestätigung. Wiederherstellen aus dem lokalen Papierkorb stellt keine gelöschte Cloudkopie automatisch wieder her.

## 7. Vorgeschlagene stabile Datenobjekte

| Objekt | Wesentliche Daten |
|---|---|
| Recording | UUID, ursprünglicher Startzeitpunkt UTC/Zone/Offset, Dauer, Name, Inhaltsversion, Hash, Größe, Format, Profil-Snapshot, lokaler Speicherort, Zustand |
| CaptureSession | Session-ID, Recording-ID, Encoderkonfiguration, gewünschte/wirksame Route, tatsächliche Effekte, Writer-/Journalpfad, letzte persistente Framegrenze |
| RecordingProfile | ID, Name, Format, Bitrate, Abtastrate, Kanäle, Mikrofonwunsch, Verstärkung, Echo-/Rauschzustand |
| CalendarRule | Ausgewählte Kalenderidentitäten, Reihenfolge, Vorlauf, Nachlauf, Benennungsvorlage |
| TransferTarget | ID, Protokoll, Basisordner, Netzregel, Referenz auf geschützte Geheimnisse, erkannte Serverfähigkeiten |
| UploadJob | UUID, Recording-ID, Inhaltsversion, Ziel-ID, Zielpfad, Versuch, Fortschritt, Resume-Token, Zustand, Fehler, Verifikationsnachweis |
| RemoteCopy | Ziel-ID, Objekt-ID/Pfad, Revision falls verfügbar, geprüfter Hash, Größe, Zeitpunkt und bekannte lokale Zuordnung |
| TrashEntry | Recording-ID, ursprünglicher Ort, Papierkorbordner, Löschzeit, automatische oder manuelle Ursache |
| DiagnosticEvent | ID, Zeit, Kategorie, technische Ursache, betroffene interne ID, datensparsame Details |

Dateisystem und Datenbank sind nicht gemeinsam atomar. Ein kleines Operationsjournal sowie ein Wiederabgleich nach Absturz verhindern verwaiste oder fälschlich als fertig markierte Dateien. Audioframes werden nicht als Datenbank-BLOBs gespeichert. Eine spätere Datenbankmigration darf Originaldateien nicht verlieren.

## 8. Konfiguration und Diagnose

Import und Export haben eine Schema-Version. Unbekannte oder ungültige Werte werden mit verständlicher Fehlermeldung abgelehnt. Profile und harmlose Einstellungen sind exportierbar, Geheimnisse und Audiodateien nicht. Neue Geräte benötigen neue Kalenderzuordnung, Speicherrechte und Kontoverbindung.

Diagnose bleibt lokal und wird nur auf Nutzeraktion exportiert. Keine Werbung, kein Tracking und kein ungefragter Diagnoseserver. Kalenderinhalte, Dateinamen und Zugangsdaten gehören nicht in Standardexporte. Die tatsächliche Audioqualität wird mit Testaufnahmen beurteilt, eine animierte Welle allein ist kein Qualitätsnachweis.
