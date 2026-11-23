# Audomo, Abnahmeplan für die Implementierung

Stand: 22.11.2026. Sämtliche Tests in diesem Dokument sind geplant, nicht bereits ausgeführt.

Die IDs a01 bis h13 beziehen sich auf die unveränderte ursprüngliche Featurematrix. Entscheidungen 01 bis 50 und spätere Korrekturen konkretisieren den Funktionsumfang. Ein grüner Dokumentencheck ist kein bestandener Geräte- oder App-Test.

## Testumgebung und Nachweise

Vor dem ersten Lauf dokumentieren:

- Genaue Galaxy-S24-Modellkennung, Android, One UI, Sicherheitspatch, Energiesparzustand, App-Version und Commit.
- Aufnahmeprofil, tatsächliche Mikrofonroute, Berechtigungen, Dateisystem beziehungsweise Dokumentprovider.
- Servertyp, Serverversion, Protokoll, Verifikationsmethode, Netzregel und verwendetes Testkonto.
- Je Test: Datum, Tester, Schritte, erwartetes und beobachtetes Ergebnis, PASS/FAIL/BLOCKED, Log- oder Datei-Nachweis.
- Testdateien mit synthetischem Sprache-/Zeitmarkersignal verwenden. Reale vertrauliche Besprechungen sind keine notwendige Testvoraussetzung.

Fehlende Geräte oder Server ergeben BLOCKED, kein PASS. Abweichungen an Muss-Funktionen bleiben offen, auch wenn die Oberfläche bereits korrekt aussieht.

## Kernabnahme

| Test | Anforderungen | Durchführung und Erfolgskriterium |
|---|---|---|
| T01 Start und Stopp | a01, h05 | 20 Sitzungen aus der App starten und beenden. Genau eine Datei je erfolgreicher Sitzung; nach Stopp vollständig abspielbar; kein hängender Recorder. Wiedergabe und Aufnahme verwenden getrennte Zustände. |
| T02 Pause und Fortsetzen | a02 | Mehrfach pausieren und fortsetzen. Timer und Audiolänge zählen nur aufgenommene Zeit, Pausen werden nicht als zusätzliche Stille eingefügt. Stopp aus PAUSED speichert alle aufgenommenen Teile. |
| T03 Lange Hintergrundaufnahme | a03, a04, a05, a08 | Mindestens sechs Stunden mit Zeitmarkersignal, längere Bildschirm-Aus-Phase, andere Apps und Energiesparwechsel. Kein künstliches Zeitlimit; Markerreihenfolge, Gesamtdauer und erkannte Lücken prüfen. Nicht erkannte Lücken führen zu FAIL. |
| T04 Konkurrierende Befehle | a01, a02, d03, d04 | App, Kachel und Notification schnell abwechselnd betätigen. Keine doppelte Aufnahme, keine verwaiste Datei, keine widersprüchlichen Zustände oder falsche Erfolgsbestätigung. |
| T05 Audiounterbrechung | a08, h09 | Systemmikrofon-Schalter, konkurrierende Mikrofon-App und Telefonanruf testen. Kein freiwilliges Anruf-Autopause-Verhalten. Tatsächlichen Signalverlust, Callback und Fehlerzustand dokumentieren; gesicherter Audioteil bleibt nutzbar. Akustische Stille allein löst keinen bewiesenen Verlustalarm aus. |
| T06 Prozessabbruch und Recovery | a09, a10 | Zu zufälligen Zeitpunkten im Schreibzyklus Prozess beenden, mindestens 20 Wiederholungen. Nach nächstem Appstart wiederherstellbare Datei; Verlust seit letztem bestätigten Schreibpunkt messen. Prototypziel zwei Sekunden separat bewerten. Keine Neuaufnahme ohne Nutzeraktion. |
| T07 Neustart und voller Speicher | a09, a10, g13 | Aufnahme kurz vor und während Finalisierung durch Neustart beziehungsweise kontrollierten Speichermangel unterbrechen. Wiederherstellung bleibt möglich, soweit Daten gesichert waren; kein falsches „Gespeichert“. Uploadjobs bleiben erhalten. |
| T08 Mikrofonroute | b01 | Verfügbare interne Eingänge wählen, aktive Route messen und diagnostizieren. „Hinten“ nur als tatsächlich wirksam bestätigen, wenn nachgewiesen. Unverfügbare Auswahl klar benennen. Keine Freigabe b01 allein anhand eines Dropdowns. |
| T09 Verstärkung | b04 | Bekanntes Signal bei 0 dB und +5 dB aufnehmen. Erwartetes Pegelverhältnis ohne verdeckte zusätzliche AGC prüfen. Übersteuerung mit starkem Eingangssignal erkennen; gespeicherte Einstellung wird reproduzierbar angewandt. |
| T10 Opus und Parameter | b07, b11, b12 | Alle freigegebenen Profile und jede angebotene Abtastrate/Bitrate prüfen. Ogg mit Opus, korrekte Dauer, kein fehlerhaftes Ende, Dekodierung in zwei unabhängigen Playern. Effektive Raten und gegebenenfalls Resampling nachweisen. |
| T11 Audioeffekte | b14, b15 | Ein, Aus, Gerätestandard jeweils für Echo und Rauschen testen. Aktiven Effekt und Kontrollverlust protokollieren; unverfügbare Einstellung nicht als aktiv anzeigen. Wirkung an geeignetem Testszenario prüfen. |
| T12 Profile | b17 | Drei Startprofile mit 48/80/32 kbit/s und den weiteren beschlossenen Werten prüfen. Eigenes Profil anlegen, ändern, speichern und nach Neustart wieder wählen. Änderungen wirken erst im erlaubten Aufnahmestadium. |
| T13 Benachrichtigungstöne | a12 | Mit und ohne DND-Zugriff aufnehmen. Mit Zugriff fremde Testbenachrichtigungen stumm, ohne Zugriff sichtbarer Hinweis. Vorhandene DND-Regeln und manuelle Nutzeränderungen nach Stopp erhalten. Prozessabbruch und verwaiste eigene Regel prüfen. |
| T14 Kalenderzugriff | c01, c02, c03 | Berechtigung erlauben, verweigern und entziehen. Mehrere Kalender auswählen und Priorität ändern. Ohne Zugriff startet Aufnahme mit Fallback. Importierte alte Kalender-IDs erzeugen keine falsche Zuordnung. |
| T15 Terminzuordnung | c04, c05, c09 | Aktuellen Einzeltermin, Serientermin, Serienausnahme, Ganztagstermin und abgesagten Termin testen. Titel aus dem richtigen Vorkommen; Ganztag und abgesagte Termine ignorieren; Startzeitpunkt bleibt maßgebend. |
| T16 Überschneidung und Toleranz | c10, c11 | Je zwei überlappende Termine, gleiche Kalenderpriorität, Vorlauf/Nachlauf 0 und frei eingetragene Minuten. Grenzwerte direkt vor Beginn, am Beginn, am Ende und nach Ende prüfen. Wiederholte Auswertung liefert identischen Kandidaten. |
| T17 Schema und Zeit | c06, c07 | Variablen einsetzen, Vorschau und Ergebnis vergleichen. Zeitzonenwechsel, Mitternacht und doppelte Stunde bei Sommerzeitende prüfen. ISO-Datum stimmt; vollständiger Zeitpunkt samt Offset in Metadaten erhalten. |
| T18 Namen und Fallback | c08, c12, c13 | Ohne Termin, leeren Titel, lange Namen, Pfadzeichen und Unicode testen. Name vor Aufnahme direkt ändern. Endung bleibt geschützt; kein Überschreiben vorhandener Dateien. Quick Settings verlangt keine Namenseingabe. |
| T19 Quick Settings | d01, d02, d03, d04, d08 | Kachel hinzufügen, bei vorher entsperrtem Gerät aus einer anderen App starten, nach Start zur vorherigen App zurückkehren und über dieselbe Kachel stoppen. Kaltstart, entladene Activity und erneutes Binden der Kachel prüfen. |
| T20 Haptik | d05, d06, d07 | Start-, Stopp- und Fehlermuster getrennt konfigurieren und testen. Erfolgsimpuls erst nach bestätigten Frames beziehungsweise gesicherter Datei. Fehlstart erzeugt ausschließlich Fehlerrückmeldung. DND und globales Haptik-Aus prüfen. |
| T21 Notification und Sperrbildschirm | d09, d10 | Aufnahme über Notification pausieren, fortsetzen und stoppen. Auf gesperrtem Gerät weder Termin- noch Dateiname oder Serverpfad. Gleicher Datenschutz für Uploadfehler. Ablehnung der Notificationberechtigung korrekt behandeln. |
| T22 Speicherort und Ordner | e01, e02 | Freigegebenes Verzeichnis wählen, Unterordner anlegen und wechseln. Neustart, entzogenes URI-Recht, verschobenen Ordner und langsamen Provider testen. Kein stiller Verlust oder irreführender Zielpfad. |
| T23 Dateiliste | e03, e04, e05 | Liste mit mindestens 500 Dateien. Name, Datum/Uhrzeit, Dauer und Größe korrekt; Suche ohne falsche Trefferverluste; Sortieren nach Name, Datum, Dauer, Größe und Endung in beide Richtungen. |
| T24 Umbenennen, Kopieren, Verschieben | e06, e07, h07 | Operationen mit Namenskonflikt, fehlendem Platz und Abbruch ausführen. Audiobytes unverändert. Quelle erst nach gültiger Kopie entfernen; keine dubiosen halbfertigen Einträge nach Neustart. |
| T25 Teilen und extern öffnen | e08, f13 | Einzelne und mehrere Aufnahmen im Android-Teilen-Menü, eine Datei in anderer Audio-App öffnen. Gültiger MIME-Typ und zeitlich nötiger URI-Zugriff; empfangene Datei vollständig abspielbar. |
| T26 Auswahl und Schnellaktionen | e09, h04 | Einzelauswahl F2 und Mehrfachauswahl M2 prüfen. Aktionen wirken nur auf sichtbare ausgewählte IDs, auch nach Sortierung/Filterwechsel. Löschen, Verschieben und Mehr-Menü konsistent. |
| T27 Papierkorb | e10 | Manuell löschen und wiederherstellen; Originalordner fehlt oder Dateiname inzwischen belegt. Nach Neustart Inhalt erhalten. Optionales Aufräumen ist standardmäßig aus; eingeschaltete 30-Tage-Regel arbeitet auf Löschzeit, nicht Aufnahmedatum. |
| T28 Mediensuche | e12 | Verbergen im gewählten Ordner samt Unterordnern aktivieren und mit Android-Mediensuche prüfen. Ausgeschaltete Regel rückgängig machen. Nicht unterstützter Provider zeigt Einschränkung. |
| T29 Metadaten | e14 | Aufnahme, Import, Rename, Move, Upload und Restore durchlaufen. Aufnahmezeit bleibt fachlich korrekt, Dauer aus Audiostream, Größe aus tatsächlichen Bytes. Beschränkte Dateisystemzeit nicht als garantierte Originalzeit ausgeben. |
| T30 Import | e15, h07 | Bestands-MP3 und Ogg-Dateien einschließlich Duplikaten und beschädigtem Exemplar importieren. Originalquelle bleibt unverändert, erfolgreiche Kopien hashgleich; Fehler beziehen sich auf einzelne Dateien. |
| T31 Listenplayer | f01, f02, f03, f04, f05, f07, h05, h07 | Zeitleiste, Pause, 5-Sekunden-Sprünge, anderes Sprungintervall, Tempo und Stille überspringen prüfen. Dateiende und Anfang begrenzen. Originaldatei bleibt hashgleich; Pausentaste ist vom Recorderzustand getrennt. |
| T32 Zieladapter | g01, g02, g03, g04, g09 | Dropbox, WebDAV, FTP, FTPS und SFTP jeweils mit echtem Testziel verbinden, Zielordner wählen, hochladen und verifizieren. FTP und FTPS sind zwei getrennte Prüfpfade innerhalb g03. |
| T33 Warteschlange | g05, g06, g11, g12, g13, g14 | Automatischen und manuellen Auftrag erzeugen; Prozessabbruch, Netzabbruch und Neustart während Upload/Finalisierung testen. Keine verlorenen Aufträge, keine mehrfachen finalen Dateien; verständlicher Fortschritt und korrekter Wiederholungsstatus. |
| T34 Netzregeln | g07, g08 | Nur WLAN, mobile Daten erlaubt, WLAN zu Mobilfunk, Mobilfunk zu WLAN und komplett offline testen. Keine unzulässige neue Übertragung. Verifikationsdownloads beachten dieselbe Regel. |
| T35 Integrität | g15 | Datei nach Upload vollständig prüfen. Falsche Größe, falscher Hash, fremde Datei mit gleichem Namen und unklaren Serverabschluss injizieren. Nur tatsächlich identischer abgeschlossener Inhalt erhält SUCCEEDED/verifiziert. |
| T36 Aufbewahrung | g16, g17 | Standardbehalten und aktiviertes Aufräumen separat testen. Nach verifiziertem Erfolg ausschließlich lokal in Papierkorb; bei Fehler, unklarem Hash oder ausgelassenem Upload unverändert lokal behalten. Keine automatische Cloud-Löschung. |
| T37 Namenskonflikt | g06, g11, g14, g16 | Alle drei Aktionen „Neuer Name“, „Direkt überschreiben“, „Upload auslassen“ prüfen. Buttons nebeneinander, ohne zusätzliche Beschreibung. Nur betroffener Job wartet; andere Jobs dürfen weiterlaufen. Kein unbeabsichtigtes Überschreiben. |
| T38 Remote-Dateiverwaltung | e06, e07, e10, g15 | Optionales Cloud-Rename/Move/Delete, abgelaufene Remote-ID, fremde neue Version und Verbindungsabbruch prüfen. Zustände getrennt nach lokal/remote. Kein Verlust durch veraltete Pfadannahmen. |
| T39 Geheimnisse | g18, h09, h11 | Appdaten, Diagnoseexport, Konfigurationsexport und Fehlertexte auf Testpasswort/-token durchsuchen. Keine Klartextgeheimnisse. Falsches TLS-Zertifikat, geänderter SFTP-Hostschlüssel und widerrufener OAuth-Zugriff führen zum sicheren Fehlerzustand. |
| T40 Einstellungen | h11, b17, c07 | Export und Import auf derselben und einer neuen Installation. Profile, Schema und harmlose Optionen erhalten; Konten, Kalender und Speicherrechte neu zuordnen; unbekannte Schema-Version verständlich ablehnen. |
| T41 Diagnose | h09, a08, a09 | Aufnahmefehler, Wiederherstellung und Uploadfehler erzeugen. Zeitfolge, Kategorie und Fehlerursache nachvollziehbar, Filter und Export funktionieren. Keine Audiodaten oder vertraulichen Titel im Standardexport. |
| T42 Werbung und Tracking | h10 | Abhängigkeiten und Netzwerkverkehr im lokalen Aufnahme-/Playerbetrieb prüfen. Keine Werbe- oder Trackingbibliothek, keine unerwarteten Telemetrieanfragen. Netzwerkzugriffe nur für aktiv konfigurierte Uploads, Anmeldung und dokumentierte Nutzeraktionen. |
| T43 Visuelle Abnahme | h04, h05, d04, e03, f01 | Gültige Bildzuordnung pro Entscheidung mit Implementierung vergleichen. Grüne Play-Aktion, graue Stop-/Pause-Tasten, keine rote Aufnahmefläche, kompakte Liste mit lesbarem Datum und Uhrzeit, korrigierte Funktionsicons. |
| T44 Darstellung und Bedienbarkeit | e03, d09, f01 | Portrait auf dem tatsächlichen S24, kleine Displaybreite, vergrößerte Schrift, Tastatur und Systemleisten prüfen. Keine abgeschnittenen Hauptaktionen oder überlappenden Texte. Kompakte Darstellung darf keine unbedienbaren Touchflächen erzwingen. |

## Ergänzende Entscheidungstests

- D44/D49: Remote-Umbenennen und -Verschieben nur, wenn die entsprechende Option aktiv ist. Ausschalten lässt die Remote-Datei unverändert.
- D45: Ein Überschreibkonflikt wird nach der dokumentierten Bestätigungsregel behandelt. Kein automatischer Fallback auf Überschreiben.
- D48: Lokale Unterordner relativ zum Aufnahmeroot werden unter dem jeweiligen Cloudroot abgebildet. Kein Rückwärtssynchronisieren fremder Cloudordner.
- D50: Lokales Löschen und optionales Remote-Löschen getrennt bestätigen und protokollieren; automatische Aufräumregeln lösen keine Remote-Löschung aus.
- D38/D39/D40: Einrichtung bleibt kompakt, optionale Cloud-Anmeldung überspringbar, Kachel-Systemabfrage mit manuellem Fallback.

## Optional und nicht benötigt

Optionale Funktionen bleiben als solche in der ursprünglichen Matrix erhalten. Wo spätere Entscheidungen eine konkrete sichtbare Funktion festlegen, wird sie entsprechend der Entscheidung implementiert. Dazu gehören etwa die gewählte Timer-/Wellenansicht, Haptikkonfiguration und Dark-UI. Eine nicht ausgewählte MP3-Aufnahmefunktion wird nicht allein aus MP3-Import abgeleitet.

Nicht benötigte Funktionen werden nicht durch Tests oder Architekturvorschläge nachträglich eingeführt. Insbesondere keine Aufnahmeplanung durch Kalender, kein eigener Transkriptionsdienst, kein Aufnahme-Stilleschnitt und keine sichtbare VoxDrop-Integration.

## Abschlusskriterien

- Alle 83 ursprünglichen Muss-Anforderungen sind mindestens einem Kernabnahmetest zugeordnet.
- Jede Änderung an einer späteren Entscheidung ist im Entscheidungsmanifest sichtbar.
- Jede Muss-Anforderung hat zum Release einen tatsächlichen PASS-Nachweis oder einen ausdrücklich offenen Defekt.
- Mindestens ein vollständiger sechs Stunden langer Gerätelauf, Recovery-Fehlerinjektion und die fünf Upload-Prüfpfade wurden durchgeführt.
- Ein prüfbarer Build, Installationsanleitung, Lizenz-/Abhängigkeitsnachweise und ein reproduzierbarer Testbericht liegen vor.
- Das Übergabepaket selbst enthält keine Behauptung, diese zukünftigen App-Tests seien bereits bestanden.
