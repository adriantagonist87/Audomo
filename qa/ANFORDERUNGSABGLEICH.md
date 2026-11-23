# Funktionsabgleich · Audomo 0.2

Der Abgleich enthält alle 122 IDs der ursprünglichen Matrix. „Vorhanden“ bezeichnet einen erreichbaren Codepfad. Die Spalte Nachweis nennt den tatsächlich geprüften Umfang. Der Abnahmeplan für das physische Samsung Galaxy S24 Ultra bleibt maßgeblich.

Beim Kandidaten bedeutet „ohne Nachweis“, dass die Prüfung der Builddateien, des Manifests und der betroffenen v2-Aufnahme-, Daten- und UI-Module keinen passenden Beleg ergab. Es gab keinen Gerätetest des Kandidaten. Der konkrete Commit und der Buildnachweis stehen in der [Quellbasisentscheidung](../ADR-001.md). Die Feature- und Test-IDs beziehen sich auf die unveränderte Übergabe.

Die 44 Testfälle, die verwendeten Geräte und die verbleibenden Abnahmegrenzen sind im [Abnahmeprotokoll](ABNAHME-0.2.md) dokumentiert. Der [Gestaltungsabgleich](MOCKUPABGLEICH.md) berücksichtigt alle 52 Entscheidungseinträge einschließlich 10a und 10b.

## Aufnahme

| ID | Priorität, Anforderung | Beschlüsse / Tests | Kandidat | Audomo und Modul | Nachweis | Einschränkung |
|---|---|---|---|---|---|---|
| a01 | MUSS · Aufnahme starten und stoppen | 5, 6, 8, 23, 24 / T01, T04 | vorhanden · AudioRecordingService / RecorderV2 | vorhanden · CaptureEngine, CaptureService | T01, T02, T04 · 20 normale Sitzungen, doppelte Befehle, Pause und Stopp aus Pause am AVD | Zusammenspiel von App, Kachel und Benachrichtigung am gesperrten S24 offen |
| a02 | MUSS · Aufnahme pausieren und fortsetzen | 6, 7, 24 / T02, T04 | vorhanden · AudioRecordingService / RecorderV2 | vorhanden · CaptureEngine, CaptureService | T01, T02, T04 · 20 normale Sitzungen, doppelte Befehle, Pause und Stopp aus Pause am AVD | Zusammenspiel von App, Kachel und Benachrichtigung am gesperrten S24 offen |
| a03 | MUSS · Mehrstündige Aufnahmen ohne Zeitlimit | 6, 35, 36, 46 / T03 | teilweise · MediaRecorderBase / NotEnoughSpaceException | vorhanden · CaptureEngine, CaptureService | T03 · Radio auf physischem S24 unter 0.1.3 im Hintergrund, 29:59,68 vollständig dekodiert | Unkalibrierte Quelle. Sechsstundenlauf, Display- und Energiesparwechsel auf Samsung offen |
| a04 | MUSS · Hintergrundaufnahme bei ausgeschaltetem Bildschirm | 6, 23, 24 / T03 | vorhanden · AudioRecordingService / RecorderV2 | vorhanden · CaptureEngine, CaptureService | T03 · Radio auf physischem S24 unter 0.1.3 im Hintergrund, 29:59,68 vollständig dekodiert | Unkalibrierte Quelle. Sechsstundenlauf, Display- und Energiesparwechsel auf Samsung offen |
| a05 | MUSS · Aufnahme während der Nutzung anderer Apps | 6, 23, 24 / T03 | vorhanden · AudioRecordingService / RecorderV2 | vorhanden · CaptureEngine, CaptureService | T03 · Radio auf physischem S24 unter 0.1.3 im Hintergrund, 29:59,68 vollständig dekodiert | Unkalibrierte Quelle. Sechsstundenlauf, Display- und Energiesparwechsel auf Samsung offen |
| a06 | OPTIONAL · Live-Anzeige von Aufnahmezeit und Pegel | 5, 6, 7 / ∅ | vorhanden · AudioRecordingService / RecorderV2 | vorhanden · MainActivity, CaptureEngine | AVD · Aufnahme- und Pausebild | Pegel mit definiertem Signal am S24 messen · Ursprünglich optional; Zeit-/Status-/Pegeloberfläche durch Entscheidungen 05 bis 07 gewählt. |
| a07 | OPTIONAL · Verbleibende Speicherkapazität anzeigen | 46 / ∅ | teilweise · MediaRecorderBase / NotEnoughSpaceException | teilweise · CaptureEngine, CaptureService | Code | Speicherwarnung ab 32 MiB; keine numerische Restkapazität · Kapazitätsanzeige bleibt optional; Warnverhalten 46 ist ausgewählt. |
| a08 | MUSS · Audioverluste erkennen und melden | 30, 35, 37 / T03, T05, T41 | teilweise · MediaRecorderBase / NotEnoughSpaceException | teilweise · AudioInput, CaptureEngine | T05 · aktiver Mikrofon-Privatschalter im AVD löst Fehler aus, intakte Frames gerettet | Anruf und sehr kurze akustische Aussetzer am S24 offen |
| a09 | MUSS · Audiodaten nach Absturz möglichst retten | 36, 37 / T06, T07, T41 | teilweise · BrokenRecordRestorer / AacFrameIndex (AAC) | vorhanden · SessionStore, OggOpus | T06 und T07 · 20 Prozessabbrüche und separater Neustart mit Wiederherstellung | Voller Datenträger und unterbrochene Finalisierung offen |
| a10 | MUSS · Aufnahmedateien regelmäßig zwischensichern | 35, 36 / T06, T07 | teilweise · BrokenRecordRestorer / AacFrameIndex (AAC) | vorhanden · SessionStore, OggOpus | T06 und T07 · 20 Prozessabbrüche und separater Neustart mit Wiederherstellung | Voller Datenträger und unterbrochene Finalisierung offen |
| a11 | OPTIONAL · Aufnahme bei Telefonanruf automatisch pausieren | 30 / ∅ | ohne Nachweis | abgewählt | Entscheidung 30 | Kein Implementierungsauftrag · Durch spätere Entscheidung 30 B für den jetzigen Umfang abgewählt. |
| a12 | MUSS · Benachrichtigungstöne während Aufnahme stummschalten | 30, 39 / T13 | ohne Nachweis | vorhanden · RecordingQuietMode | T13 · Android-AVD mit und ohne Zugriff auf die Unterbrechungsregeln | Akustischer Fremdton und manuelle Änderung auf One UI offen |
| a13 | NICHT BENÖTIGT · Aufnahme bei Tönen anderer Apps pausieren | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| a14 | NICHT BENÖTIGT · Stille bei Aufnahme automatisch überspringen | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |

## Audio und Profile

| ID | Priorität, Anforderung | Beschlüsse / Tests | Kandidat | Audomo und Modul | Nachweis | Einschränkung |
|---|---|---|---|---|---|---|
| b01 | MUSS · Internes Mikrofon auswählen, z. B. hinten | 26, 28 / T08 | teilweise · AudioInput / AudioSource | teilweise · AudioInput, ProfileDialog | T08 · S24 meldet interne Route. 0.2 liest aktive Mikrofonbeschreibungen, soweit Android sie liefert | Konkrete hintere Kapsel am S24 unbestätigt |
| b02 | NICHT BENÖTIGT · Externes USB-Mikrofon | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| b03 | OPTIONAL · Bluetooth-Mikrofon | 26 / ∅ | ohne Nachweis | fehlt · Audio und Profile | Offen | Optional |
| b04 | MUSS · Mikrofonverstärkung in dB einstellen | 26, 28 / T09 | ohne Nachweis | vorhanden · CaptureEngine, ProfileDialog | T09 · +5 dB und beidseitiges Clipping mit bekanntem PCM-Signal | Kalibrierter akustischer Vergleich offen |
| b05 | OPTIONAL · MP3-Aufnahmeformat | 26 / ∅ | ohne Nachweis | fehlt · Audio und Profile | Offen | Optional |
| b06 | OPTIONAL · AAC/M4A-Aufnahmeformat | 26 / ∅ | vorhanden · RecordingFormat / FormatConfig | fehlt · Audio und Profile | Offen | Optional |
| b07 | MUSS · Opus/Ogg-Aufnahmeformat | 26, 28 / T10 | fehlt · RecordingFormat kennt M4A, WAV, 3GP | vorhanden · OpusEncoder, OggOpus, Profile | T10 · 24 Codec-Kombinationen auf S24 und AVD mit synthetischem PCM | Nicht jede Kombination wurde mit der physischen Mikrofonroute aufgenommen |
| b08 | NICHT BENÖTIGT · WAV/PCM-Aufnahmeformat | ∅ / ∅ | vorhanden · RecordingFormat / FormatConfig | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| b09 | NICHT BENÖTIGT · FLAC-Aufnahmeformat | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| b10 | OPTIONAL · Mono und Stereo auswählen | 26, 28 / ∅ | vorhanden · RecordingFormat / FormatConfig | fehlt · Audio und Profile | Offen | Optional |
| b11 | MUSS · Abtastrate einstellen | 26, 28 / T10 | vorhanden · RecordingFormat / FormatConfig | vorhanden · OpusEncoder, OggOpus, Profile | T10 · 24 Codec-Kombinationen auf S24 und AVD mit synthetischem PCM | Nicht jede Kombination wurde mit der physischen Mikrofonroute aufgenommen |
| b12 | MUSS · Bitrate einstellen | 26, 28 / T10 | vorhanden · RecordingFormat / FormatConfig | vorhanden · OpusEncoder, OggOpus, Profile | T10 · 24 Codec-Kombinationen auf S24 und AVD mit synthetischem PCM | Nicht jede Kombination wurde mit der physischen Mikrofonroute aufgenommen |
| b13 | OPTIONAL · Variable Bitrate (VBR) | 26 / ∅ | ohne Nachweis | fehlt · Audio und Profile | Offen | Optional |
| b14 | MUSS · Echounterdrückung ein/aus/Gerätestandard | 26, 28 / T11 | ohne Nachweis | vorhanden · AudioInput, ProfileDialog | T11 · S24 meldete Echo ein und Rauschen aus im Radioprofil | Akustische Wirkung und Kontrollverlust bei Konkurrenz offen |
| b15 | MUSS · Rauschunterdrückung ein/aus/Gerätestandard | 26, 28 / T11 | ohne Nachweis | vorhanden · AudioInput, ProfileDialog | T11 · S24 meldete Echo ein und Rauschen aus im Radioprofil | Akustische Wirkung und Kontrollverlust bei Konkurrenz offen |
| b16 | OPTIONAL · Automatische Pegelanpassung (AGC) | 26 / ∅ | ohne Nachweis | fehlt · Audio und Profile | Offen | Optional |
| b17 | MUSS · Speicherbare Aufnahmeprofile | 25, 26, 27, 28 / T12, T40 | ohne Nachweis | vorhanden · Profile, ProfileDialog | T12, T40 · Standardprofil bearbeiten, eigenes Profil und Einstellungen erneut laden | Gerätewechsel und alle Editor-Eingabefelder gesondert prüfen |
| b18 | NICHT BENÖTIGT · Vor dem Teilen automatisch komprimieren | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |

## Kalender und Namen

| ID | Priorität, Anforderung | Beschlüsse / Tests | Kandidat | Audomo und Modul | Nachweis | Einschränkung |
|---|---|---|---|---|---|---|
| c01 | MUSS · Android-Kalender auslesen | 16, 39 / T14 | ohne Nachweis | vorhanden · CalendarNames, CalendarSettings | T14–T16 · zwei Android-Testkalender, Terminserie, Einzeltermin, Ganztag, Absage, Priorität und Toleranz | Verschobene Serienausnahme am AVD geprüft. Kalenderfreigabe auf One UI offen |
| c02 | MUSS · Bestimmten Kalender auswählen | 16 / T14 | ohne Nachweis | vorhanden · CalendarNames, CalendarSettings | T14–T16 · zwei Android-Testkalender, Terminserie, Einzeltermin, Ganztag, Absage, Priorität und Toleranz | Verschobene Serienausnahme am AVD geprüft. Kalenderfreigabe auf One UI offen |
| c03 | MUSS · Mehrere Kalender auswählen | 16 / T14 | ohne Nachweis | vorhanden · CalendarNames, CalendarSettings | T14–T16 · zwei Android-Testkalender, Terminserie, Einzeltermin, Ganztag, Absage, Priorität und Toleranz | Verschobene Serienausnahme am AVD geprüft. Kalenderfreigabe auf One UI offen |
| c04 | MUSS · Beim Start laufenden Termin suchen | 5, 16, 17 / T15 | ohne Nachweis | vorhanden · CalendarNames, CalendarSettings | T14–T16 · zwei Android-Testkalender, Terminserie, Einzeltermin, Ganztag, Absage, Priorität und Toleranz | Verschobene Serienausnahme am AVD geprüft. Kalenderfreigabe auf One UI offen |
| c05 | MUSS · Titel des Kalendertermins übernehmen | 5, 18, 19 / T15 | ohne Nachweis | vorhanden · CalendarNames, CalendarSettings | T14–T16 · zwei Android-Testkalender, Terminserie, Einzeltermin, Ganztag, Absage, Priorität und Toleranz | Verschobene Serienausnahme am AVD geprüft. Kalenderfreigabe auf One UI offen |
| c06 | MUSS · ISO-Datum und Uhrzeit im Dateinamen | 5, 18, 19 / T17 | teilweise · NameFormatToken ohne Termintitel | vorhanden · CalendarNames, NamingSettings, CaptureEngine | T17, T18, T40 · Kalendername, Termintitel, Ordnerwahl, Ersatzname und Einstellungsimport auf API 34 geprüft | Interaktive Zeitzonen- und DST-Grenze offen |
| c07 | MUSS · Dateinamensschema frei konfigurieren | 19 / T17, T40 | teilweise · NameFormatToken ohne Termintitel | vorhanden · CalendarNames, NamingSettings, CaptureEngine | T17, T18, T40 · Kalendername, Termintitel, Ordnerwahl, Ersatzname und Einstellungsimport auf API 34 geprüft | Interaktive Zeitzonen- und DST-Grenze offen |
| c08 | MUSS · Standardname ohne Kalendertermin | 19 / T18 | teilweise · NameFormatToken ohne Termintitel | vorhanden · CalendarNames, NamingSettings, CaptureEngine | T17, T18, T40 · Kalendername, Termintitel, Ordnerwahl, Ersatzname und Einstellungsimport auf API 34 geprüft | Interaktive Zeitzonen- und DST-Grenze offen |
| c09 | MUSS · Ganztägige Kalendereinträge ignorieren | 16 / T15 | ohne Nachweis | vorhanden · CalendarNames, CalendarSettings | T14–T16 · zwei Android-Testkalender, Terminserie, Einzeltermin, Ganztag, Absage, Priorität und Toleranz | Verschobene Serienausnahme am AVD geprüft. Kalenderfreigabe auf One UI offen |
| c10 | MUSS · Regeln für überschneidende Termine | 16, 17 / T16 | ohne Nachweis | vorhanden · CalendarNames, CalendarSettings | T14–T16 · zwei Android-Testkalender, Terminserie, Einzeltermin, Ganztag, Absage, Priorität und Toleranz | Verschobene Serienausnahme am AVD geprüft. Kalenderfreigabe auf One UI offen |
| c11 | MUSS · Zeitliche Toleranz vor/nach Termin | 17 / T16 | ohne Nachweis | vorhanden · CalendarNames, CalendarSettings | T14–T16 · zwei Android-Testkalender, Terminserie, Einzeltermin, Ganztag, Absage, Priorität und Toleranz | Verschobene Serienausnahme am AVD geprüft. Kalenderfreigabe auf One UI offen |
| c12 | MUSS · Dateinamen vor Aufnahme bearbeiten | 5, 18 / T18 | ohne Nachweis | vorhanden · MainActivity, SessionStore | SessionStoreTest | Inline-Bearbeitung und geschützte Endung; Tastatur-/Großschrifttest offen |
| c13 | MUSS · Ungültige Sonderzeichen automatisch ersetzen | 19 / T18 | ohne Nachweis | vorhanden · MainActivity, SessionStore | SessionStoreTest | Inline-Bearbeitung und geschützte Endung; Tastatur-/Großschrifttest offen |
| c14 | NICHT BENÖTIGT · Automatischer Aufnahmestart bei Kalendertermin | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |

## Android-Systemintegration

| ID | Priorität, Anforderung | Beschlüsse / Tests | Kandidat | Audomo und Modul | Nachweis | Einschränkung |
|---|---|---|---|---|---|---|
| d01 | MUSS · Eigene Quick-Settings-Kachel | 23, 40 / T19 | ohne Nachweis | vorhanden · CaptureTile, TileStartActivity | T19 · sichtbare Systemkachel auf AVD | Android-Systemkachel und sichtbare Start-Activity; Samsung-Systemoberfläche offen |
| d02 | MUSS · Aufnahme mit einem Tipp starten | 23 / T19 | ohne Nachweis | vorhanden · CaptureTile, TileStartActivity | T19 · sichtbare Systemkachel auf AVD | Android-Systemkachel und sichtbare Start-Activity; Samsung-Systemoberfläche offen |
| d03 | MUSS · Aufnahme über dieselbe Kachel stoppen | 23 / T04, T19 | ohne Nachweis | vorhanden · CaptureTile, TileStartActivity | T19 · sichtbare Systemkachel auf AVD | Android-Systemkachel und sichtbare Start-Activity; Samsung-Systemoberfläche offen |
| d04 | MUSS · Aktuellen Aufnahmezustand in Kachel anzeigen | 23 / T04, T19, T43 | ohne Nachweis | vorhanden · CaptureTile, TileStartActivity | T19 · sichtbare Systemkachel auf AVD | Android-Systemkachel und sichtbare Start-Activity; Samsung-Systemoberfläche offen |
| d05 | MUSS · Vibration nach erfolgreichem Start | 23, 31 / T20 | ohne Nachweis | vorhanden · Audomo, CaptureService, MainActivity | S24 · Vorschau nach Freigabe fühlbar. Mit 0.1.3 Startvorschau 500 ms im Systemprotokoll | Start nach bestätigten Frames sowie DND am S24 mit den neuen Dauern prüfen |
| d06 | MUSS · Vibration nach erfolgreichem Stopp | 23, 31 / T20 | ohne Nachweis | vorhanden · Audomo, CaptureService, MainActivity | AVD · Stoppvorschau 500 ms im Systemprotokoll | Impuls nach gesichertem Stopp und globales Aus am S24 prüfen |
| d07 | MUSS · Anderes Vibrationsmuster bei Fehler | 31, 35 / T20 | ohne Nachweis | vorhanden · Audomo, CaptureService, MainActivity | AVD · Fehlervorschau dreimal 300 ms mit 200 ms Pause | Fehlstart mit getrennt gewähltem Muster am S24 prüfen |
| d08 | MUSS · Nach Start zur vorherigen App zurückkehren | 23 / T19 | ohne Nachweis | vorhanden · CaptureTile, TileStartActivity | T19 · sichtbare Systemkachel auf AVD | Android-Systemkachel und sichtbare Start-Activity; Samsung-Systemoberfläche offen |
| d09 | MUSS · Aufnahmesteuerung über Benachrichtigung | 24 / T21, T44 | teilweise · AudioRecordingService | vorhanden · CaptureService, UploadWorker | T21 · AVD-Steuerung, generische öffentliche Texte, Chronometer und Uploadfehlerkanal. Aufnahmelogo und lila Akzent bei Aufnahme und Pause mit 0.2.11 geprüft | Sperrbildschirm am S24 gesondert prüfen |
| d10 | MUSS · Vertrauliche Sperrbildschirminformationen verbergen | 24, 41 / T21 | teilweise · AudioRecordingService | vorhanden · CaptureService, UploadWorker | T21 · AVD-Steuerung, generische öffentliche Texte, Chronometer und Uploadfehlerkanal | Sperrbildschirm am S24 gesondert prüfen |
| d11 | NICHT BENÖTIGT · Start direkt vom gesperrten Bildschirm | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| d12 | NICHT BENÖTIGT · Homescreen-Widget | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| d13 | OPTIONAL · Erinnerungsbenachrichtigung | ∅ / ∅ | ohne Nachweis | fehlt · Android-Systemintegration | Offen | Optional |
| d14 | NICHT BENÖTIGT · Näherungssensor zur Displaysteuerung | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |

## Dateiverwaltung

| ID | Priorität, Anforderung | Beschlüsse / Tests | Kandidat | Audomo und Modul | Nachweis | Einschränkung |
|---|---|---|---|---|---|---|
| e01 | MUSS · Aufnahmeordner frei wählen | 15, 29, 39 / T22 | fehlt · FileDataSourceImpl, privates Aufnahmeziel | vorhanden · DocumentStorage, LibraryFiles, LibraryActions | T22 · echter SAF-Baum, Unterordner, geschriebene Datei über Provider zurückgelesen | Entzug der Freigabe und langsamer Dokumentprovider offen |
| e02 | MUSS · Unterordner verwalten | 15 / T22 | ohne Nachweis | vorhanden · FolderManager, FolderSettings, DocumentReconcile | T22 · Ordnerbaum angelegt, umbenannt, verschoben, aufgelöst. Namenskollision und externer Android-Ordnerwechsel geprüft | Entzug der Freigabe und langsamer Dokumentprovider offen |
| e03 | MUSS · Aufnahmeliste mit Datum, Dauer und Größe | 9, 42 / T23, T43, T44 | teilweise · RecordEntity / SortOrder / RecordsViewModel | vorhanden · LibraryView, SessionStore | T23 · 500 echte Testdateien. Suche und fünf Sortierschlüssel in beiden Richtungen im Bedienlauf geprüft | 500-Dateien-Lauf am AVD |
| e04 | MUSS · Aufnahmen durchsuchen | 13 / T23 | ohne Nachweis | vorhanden · LibraryView, SessionStore | T23 · 500 echte Testdateien. Suche und fünf Sortierschlüssel in beiden Richtungen im Bedienlauf geprüft | 500-Dateien-Lauf am AVD |
| e05 | MUSS · Sortieren nach Name, Datum, Dauer, Größe, Endung | 14 / T23 | teilweise · RecordEntity / SortOrder / RecordsViewModel | vorhanden · LibraryView, SessionStore | T23 · 500 echte Testdateien. Suche und fünf Sortierschlüssel in beiden Richtungen im Bedienlauf geprüft | 500-Dateien-Lauf am AVD |
| e06 | MUSS · Dateien manuell umbenennen | 11, 44 / T24, T38 | vorhanden · RecordsViewModel / HomeViewModel | vorhanden · LibraryFiles, DocumentStorage | T24, T29 · Rename, Copy, Move und Hashvergleich auf Android | Externer Platzmangel beim Kopieren offen |
| e07 | MUSS · Dateien kopieren und verschieben | 11, 12, 15, 49 / T24, T38 | teilweise · MoveRecordsViewModel / DeletedRecordsViewModel | vorhanden · LibraryFiles, DocumentStorage | T24, T29 · Rename, Copy, Move und Hashvergleich auf Android | Externer Platzmangel beim Kopieren offen |
| e08 | MUSS · Android-Teilen-Menü | 11, 12 / T25 | vorhanden · RecordsViewModel / HomeViewModel | vorhanden · LibraryActions, FileProvider | T25 · separates APK liest MP3/Ogg über VIEW, SEND und SEND_MULTIPLE am S24 und AVD, Hashvergleich und Wiedergabe bis zum Ende | Separates Android-Empfänger-APK mit MediaPlayer |
| e09 | MUSS · Mehrfachauswahl von Aufnahmen | 12 / T26 | ohne Nachweis | vorhanden · LibraryView, LibraryActions | T26 · Einzel- und Sammelauswahl, Abwahl, Ordnergrenze und Suchfilter auf API 34 geprüft. [Gerätetest](GERAETETEST.md) | TalkBack und alle Mehrfachaktionen auf One UI offen |
| e10 | MUSS · Papierkorb mit Wiederherstellung | 33, 34, 50 / T27, T38 | teilweise · MoveRecordsViewModel / DeletedRecordsViewModel | vorhanden · LibraryFiles, LibraryActions | T27 · Papierkorb, Wiederherstellung, Wiederladen und 30-Tage-Regel geprüft | Systemzeitwechsel und alle Bestätigungsdialoge auf One UI offen |
| e11 | NICHT BENÖTIGT · Aufnahmen anpinnen | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| e12 | MUSS · Ordner vor Android-Mediensuche verbergen | 47 / T28 | ohne Nachweis | vorhanden · DocumentStorage, AppSettings | T28 · .nomedia im freigegebenen Android-Ordner angelegt und wieder entfernt | Tatsächliche Neuindexierung auf One UI offen |
| e13 | OPTIONAL · Ausgeblendete Aufnahmen anzeigen | ∅ / ∅ | ohne Nachweis | fehlt · Dateiverwaltung | Offen | Optional |
| e14 | MUSS · Korrekte Dateizeitstempel und Metadaten | 9, 32 / T29 | teilweise · RecordEntity / SortOrder / RecordsViewModel | vorhanden · SessionStore, LibraryFiles | T29 · ursprünglicher Zeitpunkt, Framezahl und Bytes bei lokalen Dateiaktionen geprüft | Zeitauflösung fremder Provider kann variieren |
| e15 | MUSS · Bestehende Aufnahmen importieren | 32 / T30 | vorhanden · RecordsViewModel / HomeViewModel | vorhanden · LibraryFiles, DocumentStorage, SessionStore | T24, T29, T30 · reale MP3- und Ogg-Quelldateien, Originalhash nach Aktionen, beschädigte Ogg abgelehnt | Weitere Fremdformate und Provider gesondert prüfen |

## Wiedergabe

| ID | Priorität, Anforderung | Beschlüsse / Tests | Kandidat | Audomo und Modul | Nachweis | Einschränkung |
|---|---|---|---|---|---|---|
| f01 | MUSS · Audioplayer mit Zeitleiste | 10, 10a, 10b / T31, T43, T44 | vorhanden · ExoAudioPlayer / AudioPlaybackService / PlaybackSpeed | vorhanden · LibraryView, Media3 | T31 · Listenplayer, Pause, Sprunggrenzen, 1,5-faches Tempo und Stille-Option über Bedienoberfläche | Akustische Referenz zur Stilleüberspringung offen |
| f02 | MUSS · Wiedergabe pausieren/fortsetzen | 10, 10a / T31 | vorhanden · ExoAudioPlayer / AudioPlaybackService / PlaybackSpeed | vorhanden · LibraryView, Media3 | T31 · Listenplayer, Pause, Sprunggrenzen, 1,5-faches Tempo und Stille-Option über Bedienoberfläche | Akustische Referenz zur Stilleüberspringung offen |
| f03 | MUSS · Vor- und Zurückspringen um 5 Sekunden | 10, 10a / T31 | teilweise · ExoAudioPlayer (Seek) | vorhanden · LibraryView, Media3 | T31 · Listenplayer, Pause, Sprunggrenzen, 1,5-faches Tempo und Stille-Option über Bedienoberfläche | Akustische Referenz zur Stilleüberspringung offen |
| f04 | MUSS · Sprungintervalle konfigurieren | 10b / T31 | teilweise · ExoAudioPlayer (Seek) | vorhanden · LibraryView, Media3 | T31 · Listenplayer, Pause, Sprunggrenzen, 1,5-faches Tempo und Stille-Option über Bedienoberfläche | Akustische Referenz zur Stilleüberspringung offen |
| f05 | MUSS · Wiedergabegeschwindigkeit ändern | 10b / T31 | vorhanden · ExoAudioPlayer / AudioPlaybackService / PlaybackSpeed | vorhanden · LibraryView, Media3 | T31 · Listenplayer, Pause, Sprunggrenzen, 1,5-faches Tempo und Stille-Option über Bedienoberfläche | Akustische Referenz zur Stilleüberspringung offen |
| f06 | NICHT BENÖTIGT · Wiedergabeschleife | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| f07 | MUSS · Stille bei Wiedergabe überspringen | 10b / T31 | ohne Nachweis | vorhanden · LibraryView, Media3 | T31 · Listenplayer, Pause, Sprunggrenzen, 1,5-faches Tempo und Stille-Option über Bedienoberfläche | Akustische Referenz zur Stilleüberspringung offen |
| f08 | OPTIONAL · Hintergrundwiedergabe | ∅ / ∅ | vorhanden · ExoAudioPlayer / AudioPlaybackService / PlaybackSpeed | fehlt · Wiedergabe | Offen | Optional |
| f09 | OPTIONAL · Steuerung über Bluetooth-Headset oder Auto | ∅ / ∅ | ohne Nachweis | fehlt · Wiedergabe | Offen | Optional |
| f10 | NICHT BENÖTIGT · Audio schneiden und bearbeiten | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| f11 | OPTIONAL · Alte Aufnahme nachträglich fortsetzen | ∅ / ∅ | ohne Nachweis | fehlt · Wiedergabe | Offen | Optional |
| f12 | OPTIONAL · Audioformat konvertieren | ∅ / ∅ | ohne Nachweis | fehlt · Wiedergabe | Offen | Optional |
| f13 | MUSS · Datei mit anderer Audio-App öffnen | 11 / T25 | ohne Nachweis | vorhanden · LibraryActions, FileProvider | T25 · separates APK liest MP3/Ogg über VIEW, SEND und SEND_MULTIPLE am S24 und AVD, Hashvergleich und Wiedergabe bis zum Ende | Separates Android-Empfänger-APK mit MediaPlayer |
| f14 | NICHT BENÖTIGT · Audio als Video exportieren | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| f15 | NICHT BENÖTIGT · Aufnahme als Klingelton | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |

## Uploads

| ID | Priorität, Anforderung | Beschlüsse / Tests | Kandidat | Audomo und Modul | Nachweis | Einschränkung |
|---|---|---|---|---|---|---|
| g01 | MUSS · Dropbox-Anbindung | 20, 21, 22 / T32 | ohne Nachweis | vorhanden · DropboxLinkDialog, DropboxFiles, CloudConfig | API 34 mit SDK 7.1.1. Browseranforderung mit PKCE, Rücksprungzuordnung, Abbruch und simulierte Freigabe geprüft. Refresh und blockweises Hochladen mit lokalem Transporttest | Echte OAuth-Autorisierung und produktiver Dropbox-Transfer ausstehend |
| g02 | MUSS · WebDAV-Anbindung | 20, 21, 22 / T32 | ohne Nachweis | vorhanden · RemoteFiles, CloudScreen | T32 · vier lokale Protokollserver, Zielordner, Schreiben, Rücklesen, Konflikt und Löschen | Unabhängige Serverprodukte und externe Konten offen |
| g03 | MUSS · FTP/FTPS-Anbindung | 20, 21, 22 / T32 | ohne Nachweis | vorhanden · RemoteFiles, CloudScreen | T32 · vier lokale Protokollserver, Zielordner, Schreiben, Rücklesen, Konflikt und Löschen | Unabhängige Serverprodukte und externe Konten offen |
| g04 | MUSS · SFTP-Anbindung | 20, 21, 22 / T32 | ohne Nachweis | vorhanden · RemoteFiles, CloudScreen | T32 · vier lokale Protokollserver, Zielordner, Schreiben, Rücklesen, Konflikt und Löschen | Unabhängige Serverprodukte und externe Konten offen |
| g05 | MUSS · Automatischer Upload nach Aufnahmeende | 8, 20, 22 / T33 | ohne Nachweis | vorhanden · CaptureService, UploadQueue | T33 · Abschluss zweimal ausgelöst, ein persistenter Auftrag, Serverinhalt nach Geräteneustart verifiziert. Einstellbare Wartezeit und manuelle Sofortfreigabe am API-34-Emulator geprüft | Automatischer Transfer direkt nach neuer S24-Aufnahme ausstehend |
| g06 | MUSS · Einzelne Aufnahmen manuell hochladen | 11, 20, 22 / T33, T37 | ohne Nachweis | vorhanden · ManualUploadDialog, UploadQueue, UploadWorker | T33, T37 · Einzel- und Mehrfachauswahl aus der Liste und den Einstellungen, Dienstauswahl und Dateiinhalt an lokalen FTP- und SFTP-Zielen auf API 34 geprüft | Lang laufender Hintergrundauftrag auf One UI offen |
| g07 | MUSS · Upload auf WLAN beschränken können | 20, 22 / T34 | ohne Nachweis | vorhanden · UploadQueue, UploadWorker | T34 · WLAN aus, Android-Job wartet. Transfer und Hashprüfung nach Neustart und WLAN-Rückkehr | Realer Wechsel WLAN/Mobilfunk am S24 ausstehend |
| g08 | MUSS · Upload über mobile Daten | 20, 22 / T34 | ohne Nachweis | vorhanden · UploadQueue, UploadWorker | T34 · WLAN aus, Android-Job wartet. Transfer und Hashprüfung nach Neustart und WLAN-Rückkehr | Realer Wechsel WLAN/Mobilfunk am S24 ausstehend |
| g09 | MUSS · Zielordner auf Server wählen | 21, 48 / T32 | ohne Nachweis | vorhanden · RemoteFiles, CloudScreen | T32 · vier lokale Protokollserver, Zielordner, Schreiben, Rücklesen, Konflikt und Löschen | Unabhängige Serverprodukte und externe Konten offen |
| g10 | OPTIONAL · Gleichzeitig an mehrere Ziele hochladen | ∅ / ∅ | ohne Nachweis | fehlt · Uploads | Offen | Optional |
| g11 | MUSS · Persistente Upload-Warteschlange | 22 / T33, T37 | ohne Nachweis | vorhanden · QueueDashboard, UploadDetailsDialog, UploadQueue, UploadWorker | T33, T37 · Dritter Hauptreiter, mehrere Ziele, Einzelprüfung, Abbruch eines laufenden FTP-Transfers und Wiederholung im API-34-Emulator. Auswahl, echte Uploadreihenfolge und Leeren mit Erhalt des Cloudkatalogs in 0.2.14 geprüft | Lang laufender Hintergrundauftrag auf One UI offen |
| g12 | MUSS · Automatische Wiederholungsversuche | 22, 41, 43 / T33 | ohne Nachweis | vorhanden · UploadWorker, UploadQueue | T33 · persistenter WLAN-Job über echten Emulatorneustart, nach Wiederanlauf erfolgreich gelesen | Abbruch mitten im laufenden Datentransfer und Zeitabstände unter One UI offen |
| g13 | MUSS · Upload nach Neustart fortsetzen | 22 / T07, T33 | ohne Nachweis | vorhanden · UploadWorker, UploadQueue | T33 · persistenter WLAN-Job über echten Emulatorneustart, nach Wiederanlauf erfolgreich gelesen | Abbruch mitten im laufenden Datentransfer und Zeitabstände unter One UI offen |
| g14 | MUSS · Upload-Fortschritt und Fehler anzeigen | 22, 41, 42, 43 / T33, T37 | ohne Nachweis | vorhanden · QueueDashboard, UploadDetailsDialog, UploadQueue, UploadWorker | T33, T37 · Dritter Hauptreiter, mehrere Ziele, Einzelprüfung, Abbruch eines laufenden FTP-Transfers und Wiederholung im API-34-Emulator | Lang laufender Hintergrundauftrag auf One UI offen |
| g15 | MUSS · Erfolgreichen Upload überprüfen | 22, 34 / T35, T38 | ohne Nachweis | vorhanden · UploadWorker, LibraryFiles | T35–T37 · geprüfter SHA-256, Behalten, drei Konfliktwege und optionaler Papierkorb nach erfolgreicher Verifikation am lokalen FTP-Server | Fehler- und Parallelzugriffe gegen jeden externen Anbieter offen |
| g16 | MUSS · Originaldatei lokal behalten | 34 / T36, T37 | ohne Nachweis | vorhanden · UploadWorker, LibraryFiles | T35–T37 · geprüfter SHA-256, Behalten, drei Konfliktwege und optionaler Papierkorb nach erfolgreicher Verifikation am lokalen FTP-Server | Fehler- und Parallelzugriffe gegen jeden externen Anbieter offen |
| g17 | MUSS · Automatische Löschung nach Upload | 34 / T36 | ohne Nachweis | vorhanden · UploadWorker, LibraryFiles | T35–T37 · geprüfter SHA-256, Behalten, drei Konfliktwege und optionaler Papierkorb nach erfolgreicher Verifikation am lokalen FTP-Server | Fehler- und Parallelzugriffe gegen jeden externen Anbieter offen |
| g18 | MUSS · Zugangsdaten geschützt speichern | 21, 38 / T39 | ohne Nachweis | vorhanden · CloudConfig, SettingsBackup | T39 · Android-Keystore AES-GCM, gesicherte Werte verschlüsselt, Geheimnis vom Export ausgeschlossen | Systemweite Protokolle und Sperrbildschirm prüfen |
| g19 | NICHT BENÖTIGT · Upload nur während Smartphone geladen wird | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |

## Weitere Funktionen

| ID | Priorität, Anforderung | Beschlüsse / Tests | Kandidat | Audomo und Modul | Nachweis | Einschränkung |
|---|---|---|---|---|---|---|
| h01 | OPTIONAL · Dark Mode und Android-Systemdesign | 3, 4, 29 / ∅ | teilweise · v2/theme, v2/app, Audioplayer | vorhanden · MainActivity, theme.xml | AVD · Screenshots | Gewählte dunkle Variante; zusätzliche Systemfarbschemata außerhalb der Entscheidung · Dunkles Design ausgewählt; vollständiger zusätzlicher Hellmodus ist nicht zugesagt. |
| h02 | NICHT BENÖTIGT · Verschiedene Farbschemata | ∅ / ∅ | teilweise · v2/theme, v2/app, Audioplayer | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| h03 | NICHT BENÖTIGT · Automatische Bildschirmrotation | 4 / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| h04 | MUSS · Schnellaktionen in Aufnahmeliste | 11 / T26, T43 | teilweise · v2/theme, v2/app, Audioplayer | vorhanden · LibraryView, LibraryActions | T26 · zwei stabile IDs, Kontextleiste, F2 und M2 im Android-UI-Test | TalkBack und alle Mehrfachaktionen auf One UI offen |
| h05 | MUSS · Aufnahme und Wiedergabe getrennt | 5, 10 / T01, T31, T43 | teilweise · v2/theme, v2/app, Audioplayer | vorhanden · MainActivity, LibraryView, CaptureService | AVD · Aufnahmeansicht | Getrennte Zustände; Aufnahmepriorität und Wiedergabeübergänge umfassend prüfen |
| h06 | NICHT BENÖTIGT · Direkte Übergabe an VoxDrop über Teilen | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| h07 | MUSS · Originalaufnahme unverändert erhalten | 32, 34 / T24, T30, T31 | teilweise · v2/theme, v2/app, Audioplayer | vorhanden · LibraryFiles, DocumentStorage, SessionStore | T24, T29, T30 · reale MP3- und Ogg-Quelldateien, Originalhash nach Aktionen, beschädigte Ogg abgelehnt | Weitere Fremdformate und Provider gesondert prüfen |
| h08 | NICHT BENÖTIGT · Integrierte Transkription in der App | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| h09 | MUSS · Diagnoseprotokoll für Aufnahmefehler | 35, 36, 37 / T05, T39, T41 | teilweise · Timber und BrokenRecordRestorer | vorhanden · Audomo, AppSettings | T05, T41 · generisches Ereignisprotokoll, Filter, Export, Fehler bei Mikrofonentzug sichtbar | Vollständige interaktive Filterprüfung auf One UI offen |
| h10 | MUSS · Werbefrei und ohne Tracking | 1, 29 / T42 | ohne Nachweis | vorhanden · AndroidManifest.xml, app/build.gradle.kts | T42 · Abhängigkeiten und Manifest geprüft, Internetberechtigung für konfigurierte Zieladapter | Paketmitschnitt bei Alltagsnutzung offen |
| h11 | MUSS · Einstellungen importieren/exportieren | 38 / T39, T40 | ohne Nachweis | vorhanden · AppSettings, SettingsBackup | T39, T40 · Import/Export im Android-Rundlauf, ungültige Version abgelehnt | Systemdialog auf zweitem Gerät und weitere alte Formate offen |
| h12 | NICHT BENÖTIGT · Aufnahmequalitätsvergleich mit VoxDrop | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| h13 | NICHT BENÖTIGT · Aufnahme über Sprachbefehl starten | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |
