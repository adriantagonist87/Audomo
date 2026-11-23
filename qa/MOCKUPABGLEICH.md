# Gestaltungs- und Beschlussabgleich

Grundlage sind die 50 Beschlüsse sowie 10a und 10b. Bei wiederkehrenden Elementen gelten die Aufnahmezustände 04 bis 08, Listenmaster 09, Player 10 und der dokumentierte F2-Arbeitsstand 11. Eine Zuordnung belegt noch keine bestandene Sicht- oder Geräteprüfung. Die Prüfergebnisse stehen im [Abnahmeprotokoll](ABNAHME-0.2.md).

Für Änderungen ab 1.0 gelten die späteren Bedienentscheidungen in der [Entwicklungsübergabe](../docs/ENTWICKLUNG.md). Den tatsächlich gebauten Stand zeigen der [Aufnahmeknopf](api34/aufnahmebereit-0.2.4.webp), der [Listenplayer](api34/listenplayer.png) und die [Warteschlangen-Auswahl](api34/warteschlange-auswahl-0.2.14.webp). Die Nummern unten erlauben den Abgleich mit einem separat vorliegenden Originalpaket.

| Nr. | Bezeichnung im ursprünglichen Übergabepaket | Umsetzung und Nachweis |
|---|---|---|
| 1 | App-Icon | Bildmarke in App und Launcher. Herkunft und Vektorableitung in ADR-001. |
| 2 | Wortmarke B | Wortmarke mit Inter Display Light im Kopfbereich. |
| 3 | Farbwelt A | Dunkle Indigo-Flächen und Lavendel im H1- und Listenbild. |
| 4 | UI-Grundstil A | Aufnahmesteuerung und Listenansicht getrennt. Zustandsbilder in der AVD-Serie. |
| 5 | Aufnahmebereit A1 | Aufnahmebereit mit editierbarem Namen. Seit 0.2.4 zeigt Start auf Nutzerwunsch einen gedeckt grünen Aufnahmepunkt. [Emulatoransicht](api34/aufnahmebereit-0.2.4.webp) |
| 6 | Aufnahme läuft A | Zeit, Livewelle, Stopp und Pause. T01 und T02. |
| 7 | Aufnahme pausiert P2 | Fortsetzen mit Play, Stopp aus Pause. T02. |
| 8 | Direkt speichern S1 | Nach gesicherter Datei kurzer Hinweis. Automatischer Upload an konfiguriertes Ziel. Dauerprüfung des Hinweises offen. |
| 9 | Kompakte Aufnahmeliste L1 | 500 synthetische Einträge im selben Listenlayout. Datum, Uhrzeit, Größe, Dauer und Zustand sichtbar. |
| 10 | Listenplayer W1 | Listenplayer mit Text zur tatsächlichen Sprungweite. Wiedergabe bleibt getrennt. |
| 10a | Playersteuerung Variante 2 | Zentraler Play/Pause-Schalter und gerichtete Sprünge. Grenzen im Bedienprüfungslauf. |
| 10b | Playerfunktionen E1 kompakt | Tempo, Stille und Sprungintervall nebeneinander. Änderungen im Player geprüft. |
| 11 | Direktaktionen F2 | Vier ausklappende F2-Aktionen. Screenshot mit Testdatei. Für den Bildstand gilt der dokumentierte Arbeitsstand. |
| 12 | Mehrfachauswahl M2 | Langes Drücken öffnet die untere Mehrfachleiste. Seit 0.2.5 wechselt das Stapelsymbol zwischen „Alle auswählen“ und „Alle abwählen“. Filterwechsel entfernt ausgeblendete Aufnahmen aus der Auswahl. „Cloud Upload“ öffnet für die Auswahl den Zieldialog. [Emulatoransicht](api34/cloud-upload-mehrfach-0.2.8.webp) |
| 13 | Suche S1 inline | Inline-Suchfeld und Umschaltung auf alle Ordner. Filter mit 500 Einträgen getestet. |
| 14 | Sortiermenü O1 | Fünf Sortierfelder mit zwei direkt wählbaren Richtungen im Popup. Seit 0.2.2 kennzeichnet hervorgehobene Schrift die aktive Auswahl. Alle zehn Kombinationen mit 500 Dateien geprüft. [Geräteansicht](api34/sortiermenue-0.2.2.png) |
| 15 | Ordnerauswahl N2 | Ordnerwahl unten mit Unterordner-Erstellung und Verweis auf „Ordner verwalten“. Eigene Einstellungsseite für Ordnerbäume und Abgleich. |
| 16 | Kalender K1 | Kalenderseite mit Priorität. Zwei lokale Android-Testkalender und verschobene Serienausnahme geprüft. |
| 17 | Termintoleranz T1 kompakt | Unabhängiger Vor-/Nachlauf. Wiederkehrende, überlappende und ganztägige Termine geprüft. |
| 18 | Dateiname D1 direkt bearbeiten | Inline-Bearbeitung mit geschützter Endung. Reguläre Auslösung über die Kachel bleibt ohne Namensdialog. |
| 19 | Dateinamensschema V1 | Seit 0.2.6 eigener Bildschirm mit vier Namensbausteinen, getrennter Ordnerwahl und zwei Pfadvorschauen. [Menü auf API 34](api34/dateiname-und-ordner-0.2.6.webp) |
| 20 | Uploadziele U1 | Zielübersicht, Standardziel, Netzregel und Aufbewahrung unter Einstellungen. „Bestehende Aufnahmen hochladen“ öffnet die ordnerübergreifende Auswahl. |
| 21 | Uploadziel einrichten Z1 | Server, Zugang, Basisordner und Verbindungstest. Dropbox-PKCE benötigt echten OAuth-Zugang. |
| 22 | Upload-Warteschlange Q1 | Persistente SQLite-Warteschlange und Android-Job. Fortschritt und Retry im Dialog. Wartender Auftrag nach Neustart und WLAN-Rückkehr verifiziert. |
| 23 | Quick Settings K2 | Dynamische Systemkachel. Emulator-Nachweis aus 0.1, nicht erneut auf Samsung abgenommen. |
| 24 | Kompakte Benachrichtigung B1 | Android-Benachrichtigung mit Dauer und Sitzungsaktionen. Keine Dateinamen im öffentlichen Text. |
| 25 | Profilauswahl P1 | Profilwahl am unteren Bildschirmrand, Anwenden und Verwalten. |
| 26 | Profileditor R1 | Einseitiger Editor. Je drei Zustände für Echo/Rauschen. Akustische Wirkung am Gerät offen. |
| 27 | Standardprofile G2 | Drei editierbare Standardprofile, eigene Profile und persistente Auswahl. Android-Rundlauf geprüft. |
| 28 | Standardwerte der drei Profile | 48/80/32 kbit/s, restliche Werte aus der Freigabe. Kein zusätzlicher Bildschirm. |
| 29 | Einstellungsübersicht H1 ohne Icons | Vollbild-Einstellungsmenü mit fünf Kategorien, Kurzstatus und Pfeilen. Screenshot aufgenommen. |
| 30 | Aufnahmeverhalten B | Mikrofonentzug löst sichtbaren Fehler und Rettung aus. Echter Telefonanruf offen. |
| 31 | Vibrationsmuster V2 | Einzelne Vorschauen und globaler Schalter. Längen nach Nutzerkorrektur 500 bzw. 300/200 ms. |
| 32 | Dateiimport I1 | Einzel-, Mehrfach- und Ordnerimport, SHA-256, Duplikatprüfung. MP3, Ogg und defekte Quellen getestet. |
| 33 | Papierkorb PK2 | Papierkorb, Wiederherstellung und bestätigtes endgültiges Löschen. Alterung aus Löschzeit getestet. |
| 34 | Nach Upload in Papierkorb L1 | Automatische Aufbewahrungsregel standardmäßig aus. Aufräumen nur nach verifizierter Übertragung. |
| 35 | Aufnahmefehler F1 | Fehler mit Ursache, erneutem Versuch und Beenden. Echte Mikrofonsperre am Test-AVD. |
| 36 | Wiederherstellung W1 | Neustart und 20 Prozessabbrüche. Hinweis und gesicherte Dateien nachgewiesen. |
| 37 | Diagnose D1 | Ereignisliste, Filter und manueller Textexport. Chronologie und Filter am S24 offen. |
| 38 | Einstellungsexport E1 | Versionierter JSON-Import/Export mit geprüftem Schema. Unbekannte Version und fremde Installations-ID getestet. |
| 39 | Ersteinrichtung E1 | Einrichtung mit getrennten Android-Freigaben, Kachel und optionalem Cloudziel. |
| 40 | Quick Settings installieren Q1 | Android-Systemabfrage zum Hinzufügen der Kachel, Samsung-Hinweis bei Ablehnung. |
| 41 | Uploadfehler-Meldung N1 | Uploadfehler mit Retry und Details. Android-Benachrichtigung ohne Namen oder Pfade. |
| 42 | Uploadstatus B mit Kurztext | Hochgeladen, Upload läuft, Wartet und Fehler mit vier unterscheidbaren Symbolen, Kurztext und Statusfarbe. Bestätigte Dienste erscheinen neben dem lokalen Speicherort. Das Detailfenster führt jeden gespeicherten Cloudpfad auf. |
| 43 | Uploaddetails D1 | Statusdetails als unteres Blatt, Retry und Warteschlange im Dateikontext. |
| 44 | Cloud-Umbenennen B | Opt-in beim Umbenennen für Dateien mit bestätigter Cloudkopie. |
| 45 | Namenskonflikt B mit drei Aktionen | Drei Konfliktaktionen in einer Reihe. Zusätzliche Bestätigung bei Überschreiben. |
| 46 | Speicherwarnung S1 | Speicherwarnung ohne Abbruch und generische Notification. Echter voller Datenträger offen. |
| 47 | Von Mediensuche ausschließen M1 | Globaler Indexierungsschalter der gewählten Baumfreigabe. .nomedia am Android-Dokumentprovider geprüft. |
| 48 | Cloud-Ordner spiegeln C2 | Unterordner relativ zum Basisordner. Bei geänderten Ordnerbäumen können verifizierte Kopien mitgeführt werden. Ein expliziter Abgleich erkennt extern verschobene Kopien anhand der Prüfsumme. |
| 49 | Cloud-Mitverschieben B | Getrennter Cloudauftrag beim Verschieben. Lokales Ergebnis bleibt bei Cloudfehler bestehen. |
| 50 | Cloud-Mitlöschen B | Lokales Löschen und Cloudlöschung getrennt bestätigt. Automatisches Aufräumen löst keine Cloudlöschung aus. |
