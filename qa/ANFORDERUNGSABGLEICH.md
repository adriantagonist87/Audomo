# Funktionsabgleich · Audomo 0.1

Der Abgleich enthält alle 122 IDs der ursprünglichen Matrix. „Vorhanden“ bezeichnet einen erreichbaren Codepfad. Die Spalte Nachweis nennt den tatsächlich geprüften Umfang. Der Abnahmeplan für das physische Samsung Galaxy S24 Ultra bleibt maßgeblich.

Beim Kandidaten bedeutet „ohne Nachweis“, dass die Prüfung der Builddateien, des Manifests und der betroffenen v2-Aufnahme-, Daten- und UI-Module keinen passenden Beleg ergab. Es gab keinen Gerätetest des Kandidaten. Der konkrete Commit und der Buildnachweis stehen in der [Quellbasisentscheidung](../ADR-001.md). Die Feature- und Test-IDs beziehen sich auf die unveränderte Übergabe.

## Aufnahme

| ID | Priorität, Anforderung | Beschlüsse / Tests | Kandidat | Audomo und Modul | Nachweis | Einschränkung |
|---|---|---|---|---|---|---|
| a01 | MUSS · Aufnahme starten und stoppen | 5, 6, 8, 23, 24 / T01, T04 | vorhanden · AudioRecordingService / RecorderV2 | vorhanden · CaptureService, CaptureEngine, SessionStore | AVD · T01 | Hardwareabnahme steht aus |
| a02 | MUSS · Aufnahme pausieren und fortsetzen | 6, 7, 24 / T02, T04 | vorhanden · AudioRecordingService / RecorderV2 | vorhanden · CaptureEngine | AVD · T02 teilweise | Timerprüfung und Stopp aus Pause; weitere Pausenfolgen am Gerät |
| a03 | MUSS · Mehrstündige Aufnahmen ohne Zeitlimit | 6, 35, 36, 46 / T03 | teilweise · MediaRecorderBase / NotEnoughSpaceException | vorhanden · CaptureService, CaptureEngine | AVD · kurzer Hintergrundlauf | Sechs Stunden, Zeitmarker und Samsung-Energiesparwechsel offen |
| a04 | MUSS · Hintergrundaufnahme bei ausgeschaltetem Bildschirm | 6, 23, 24 / T03 | vorhanden · AudioRecordingService / RecorderV2 | vorhanden · CaptureService, CaptureEngine | AVD · kurzer Hintergrundlauf | Sechs Stunden, Zeitmarker und Samsung-Energiesparwechsel offen |
| a05 | MUSS · Aufnahme während der Nutzung anderer Apps | 6, 23, 24 / T03 | vorhanden · AudioRecordingService / RecorderV2 | vorhanden · CaptureService, CaptureEngine | AVD · kurzer Hintergrundlauf | Sechs Stunden, Zeitmarker und Samsung-Energiesparwechsel offen |
| a06 | OPTIONAL · Live-Anzeige von Aufnahmezeit und Pegel | 5, 6, 7 / ∅ | vorhanden · AudioRecordingService / RecorderV2 | vorhanden · MainActivity, CaptureEngine | AVD · Aufnahme- und Pausebild | Pegel mit definiertem Signal am S24 messen · Ursprünglich optional; Zeit-/Status-/Pegeloberfläche durch Entscheidungen 05 bis 07 gewählt. |
| a07 | OPTIONAL · Verbleibende Speicherkapazität anzeigen | 46 / ∅ | teilweise · MediaRecorderBase / NotEnoughSpaceException | teilweise · CaptureEngine, CaptureService | Code | Speicherwarnung ab 32 MiB; keine numerische Restkapazität · Kapazitätsanzeige bleibt optional; Warnverhalten 46 ist ausgewählt. |
| a08 | MUSS · Audioverluste erkennen und melden | 30, 35, 37 / T03, T05, T41 | teilweise · MediaRecorderBase / NotEnoughSpaceException | teilweise · AudioInput, CaptureEngine | Code | Frameausfall, Zeitstempel, Fehler und Systemstummschaltung behandelt; Telefonie- und Konflikttest offen |
| a09 | MUSS · Audiodaten nach Absturz möglichst retten | 36, 37 / T06, T07, T41 | teilweise · BrokenRecordRestorer / AacFrameIndex (AAC) | vorhanden · OggOpus, SessionStore | AVD · T06; OggOpusTest | Neustart, Stromverlust und voller Speicher gesondert offen |
| a10 | MUSS · Aufnahmedateien regelmäßig zwischensichern | 35, 36 / T06, T07 | teilweise · BrokenRecordRestorer / AacFrameIndex (AAC) | vorhanden · OggOpus, SessionStore | AVD · T06; OggOpusTest | Neustart, Stromverlust und voller Speicher gesondert offen |
| a11 | OPTIONAL · Aufnahme bei Telefonanruf automatisch pausieren | 30 / ∅ | ohne Nachweis | abgewählt | Entscheidung 30 | Kein Implementierungsauftrag · Durch spätere Entscheidung 30 B für den jetzigen Umfang abgewählt. |
| a12 | MUSS · Benachrichtigungstöne während Aufnahme stummschalten | 30, 39 / T13 | ohne Nachweis | vorhanden · RecordingQuietMode | Code | Eigene Zen-Regel; Wirksamkeit, manuelle Android-Änderungen und Haptik prüfen |
| a13 | NICHT BENÖTIGT · Aufnahme bei Tönen anderer Apps pausieren | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| a14 | NICHT BENÖTIGT · Stille bei Aufnahme automatisch überspringen | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |

## Audio und Profile

| ID | Priorität, Anforderung | Beschlüsse / Tests | Kandidat | Audomo und Modul | Nachweis | Einschränkung |
|---|---|---|---|---|---|---|
| b01 | MUSS · Internes Mikrofon auswählen, z. B. hinten | 26, 28 / T08 | teilweise · AudioInput / AudioSource | teilweise · AudioInput, ProfileDialog | AVD · interne Ersatzroute | Richtungswunsch und tatsächliche Route getrennt; konkrete hintere S24-Kapsel unbestätigt |
| b02 | NICHT BENÖTIGT · Externes USB-Mikrofon | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| b03 | OPTIONAL · Bluetooth-Mikrofon | 26 / ∅ | ohne Nachweis | fehlt · Audio und Profile | Offen | Optional |
| b04 | MUSS · Mikrofonverstärkung in dB einstellen | 26, 28 / T09 | ohne Nachweis | vorhanden · CaptureEngine, ProfileDialog | Code | PCM-Verstärkung und Clipping-Erkennung; kalibrierten Pegelvergleich durchführen |
| b05 | OPTIONAL · MP3-Aufnahmeformat | 26 / ∅ | ohne Nachweis | fehlt · Audio und Profile | Offen | Optional |
| b06 | OPTIONAL · AAC/M4A-Aufnahmeformat | 26 / ∅ | vorhanden · RecordingFormat / FormatConfig | fehlt · Audio und Profile | Offen | Optional |
| b07 | MUSS · Opus/Ogg-Aufnahmeformat | 26, 28 / T10 | fehlt · RecordingFormat kennt M4A, WAV, 3GP | vorhanden · OpusEncoder, OggOpus | AVD · vollständige Core-Audio-Dekodierung | T10 mit S24-Mikrofon und allen Einstellungen offen |
| b08 | NICHT BENÖTIGT · WAV/PCM-Aufnahmeformat | ∅ / ∅ | vorhanden · RecordingFormat / FormatConfig | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| b09 | NICHT BENÖTIGT · FLAC-Aufnahmeformat | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| b10 | OPTIONAL · Mono und Stereo auswählen | 26, 28 / ∅ | vorhanden · RecordingFormat / FormatConfig | fehlt · Audio und Profile | Offen | Optional |
| b11 | MUSS · Abtastrate einstellen | 26, 28 / T10 | vorhanden · RecordingFormat / FormatConfig | vorhanden · Profile, OpusEncoder, AudioInput | T10 · 24 Encoderkombinationen auf API 34 | Eingang und Codec konfigurierbar; Hardware-Raten und akustische Wirkung prüfen |
| b12 | MUSS · Bitrate einstellen | 26, 28 / T10 | vorhanden · RecordingFormat / FormatConfig | vorhanden · Profile, OpusEncoder, AudioInput | T10 · 24 Encoderkombinationen auf API 34 | Eingang und Codec konfigurierbar; Hardware-Raten und akustische Wirkung prüfen |
| b13 | OPTIONAL · Variable Bitrate (VBR) | 26 / ∅ | ohne Nachweis | fehlt · Audio und Profile | Offen | Optional |
| b14 | MUSS · Echounterdrückung ein/aus/Gerätestandard | 26, 28 / T11 | ohne Nachweis | vorhanden · AudioInput, ProfileDialog | Code | Alle drei Zustände; Verfügbarkeit und akustische Wirkung auf Samsung messen |
| b15 | MUSS · Rauschunterdrückung ein/aus/Gerätestandard | 26, 28 / T11 | ohne Nachweis | vorhanden · AudioInput, ProfileDialog | Code | Alle drei Zustände; Verfügbarkeit und akustische Wirkung auf Samsung messen |
| b16 | OPTIONAL · Automatische Pegelanpassung (AGC) | 26 / ∅ | ohne Nachweis | fehlt · Audio und Profile | Offen | Optional |
| b17 | MUSS · Speicherbare Aufnahmeprofile | 25, 26, 27, 28 / T12, T40 | ohne Nachweis | vorhanden · Profile, ProfileDialog | Code | Drei Startprofile, eigene Profile, persistente Auswahl; Editorabnahme offen |
| b18 | NICHT BENÖTIGT · Vor dem Teilen automatisch komprimieren | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |

## Kalender und Namen

| ID | Priorität, Anforderung | Beschlüsse / Tests | Kandidat | Audomo und Modul | Nachweis | Einschränkung |
|---|---|---|---|---|---|---|
| c01 | MUSS · Android-Kalender auslesen | 16, 39 / T14 | ohne Nachweis | vorhanden · CalendarNames, CalendarSettings | Code | Provider mit freigegebenen Testkalendern und Serienausnahmen prüfen |
| c02 | MUSS · Bestimmten Kalender auswählen | 16 / T14 | ohne Nachweis | vorhanden · CalendarNames, CalendarSettings | Code | Provider mit freigegebenen Testkalendern und Serienausnahmen prüfen |
| c03 | MUSS · Mehrere Kalender auswählen | 16 / T14 | ohne Nachweis | vorhanden · CalendarNames, CalendarSettings | Code | Provider mit freigegebenen Testkalendern und Serienausnahmen prüfen |
| c04 | MUSS · Beim Start laufenden Termin suchen | 5, 16, 17 / T15 | ohne Nachweis | vorhanden · CalendarNames, CalendarSettings | Code | Provider mit freigegebenen Testkalendern und Serienausnahmen prüfen |
| c05 | MUSS · Titel des Kalendertermins übernehmen | 5, 18, 19 / T15 | ohne Nachweis | vorhanden · CalendarNames, CalendarSettings | Code | Provider mit freigegebenen Testkalendern und Serienausnahmen prüfen |
| c06 | MUSS · ISO-Datum und Uhrzeit im Dateinamen | 5, 18, 19 / T17 | teilweise · NameFormatToken ohne Termintitel | vorhanden · CalendarNames, CalendarSettings | CalendarNamesTest | Grenzwerte und Vorlagen lokal getestet; Kalenderprovider am Gerät offen |
| c07 | MUSS · Dateinamensschema frei konfigurieren | 19 / T17, T40 | teilweise · NameFormatToken ohne Termintitel | vorhanden · CalendarNames, CalendarSettings | CalendarNamesTest | Grenzwerte und Vorlagen lokal getestet; Kalenderprovider am Gerät offen |
| c08 | MUSS · Standardname ohne Kalendertermin | 19 / T18 | teilweise · NameFormatToken ohne Termintitel | vorhanden · CalendarNames, CalendarSettings | CalendarNamesTest | Grenzwerte und Vorlagen lokal getestet; Kalenderprovider am Gerät offen |
| c09 | MUSS · Ganztägige Kalendereinträge ignorieren | 16 / T15 | ohne Nachweis | vorhanden · CalendarNames, CalendarSettings | Code | Provider mit freigegebenen Testkalendern und Serienausnahmen prüfen |
| c10 | MUSS · Regeln für überschneidende Termine | 16, 17 / T16 | ohne Nachweis | vorhanden · CalendarNames, CalendarSettings | CalendarNamesTest | Grenzwerte und Vorlagen lokal getestet; Kalenderprovider am Gerät offen |
| c11 | MUSS · Zeitliche Toleranz vor/nach Termin | 17 / T16 | ohne Nachweis | vorhanden · CalendarNames, CalendarSettings | CalendarNamesTest | Grenzwerte und Vorlagen lokal getestet; Kalenderprovider am Gerät offen |
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
| d05 | MUSS · Vibration nach erfolgreichem Start | 23, 31 / T20 | ohne Nachweis | vorhanden · Audomo, CaptureService, MainActivity | Code | Drei konfigurierbare Muster und globaler Schalter; fühlbare Impulse und DND am Gerät prüfen |
| d06 | MUSS · Vibration nach erfolgreichem Stopp | 23, 31 / T20 | ohne Nachweis | vorhanden · Audomo, CaptureService, MainActivity | Code | Drei konfigurierbare Muster und globaler Schalter; fühlbare Impulse und DND am Gerät prüfen |
| d07 | MUSS · Anderes Vibrationsmuster bei Fehler | 31, 35 / T20 | ohne Nachweis | vorhanden · Audomo, CaptureService, MainActivity | Code | Drei konfigurierbare Muster und globaler Schalter; fühlbare Impulse und DND am Gerät prüfen |
| d08 | MUSS · Nach Start zur vorherigen App zurückkehren | 23 / T19 | ohne Nachweis | vorhanden · CaptureTile, TileStartActivity | T19 · sichtbare Systemkachel auf AVD | Android-Systemkachel und sichtbare Start-Activity; Samsung-Systemoberfläche offen |
| d09 | MUSS · Aufnahmesteuerung über Benachrichtigung | 24 / T21, T44 | teilweise · AudioRecordingService | teilweise · CaptureService | T21 · Pause und Fortsetzen über die Notification | Pause, Fortsetzen und Stopp ohne Dateinamen; Sperrbildschirm und spätere Uploadmeldungen prüfen |
| d10 | MUSS · Vertrauliche Sperrbildschirminformationen verbergen | 24, 41 / T21 | teilweise · AudioRecordingService | teilweise · CaptureService | T21 · Pause und Fortsetzen über die Notification | Pause, Fortsetzen und Stopp ohne Dateinamen; Sperrbildschirm und spätere Uploadmeldungen prüfen |
| d11 | NICHT BENÖTIGT · Start direkt vom gesperrten Bildschirm | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| d12 | NICHT BENÖTIGT · Homescreen-Widget | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| d13 | OPTIONAL · Erinnerungsbenachrichtigung | ∅ / ∅ | ohne Nachweis | fehlt · Android-Systemintegration | Offen | Optional |
| d14 | NICHT BENÖTIGT · Näherungssensor zur Displaysteuerung | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |

## Dateiverwaltung

| ID | Priorität, Anforderung | Beschlüsse / Tests | Kandidat | Audomo und Modul | Nachweis | Einschränkung |
|---|---|---|---|---|---|---|
| e01 | MUSS · Aufnahmeordner frei wählen | 15, 29, 39 / T22 | fehlt · FileDataSourceImpl, privates Aufnahmeziel | teilweise · SessionStore, MainActivity | Code | Einzelexport per Systemdialog; persistentes SAF-Ziel für laufende Aufnahmen fehlt |
| e02 | MUSS · Unterordner verwalten | 15 / T22 | ohne Nachweis | fehlt · Dateiverwaltung | Offen | Für Endabnahme erforderlich |
| e03 | MUSS · Aufnahmeliste mit Datum, Dauer und Größe | 9, 42 / T23, T43, T44 | teilweise · RecordEntity / SortOrder / RecordsViewModel | teilweise · LibraryView, SessionStore | AVD · Dateiliste mit 102 Testaufnahmen | Name, Datum, Zeit, Größe, Dauer, Suche und fünf Sortierschlüssel; Ordner, Uploadstatus und 500-Dateien-Test offen |
| e04 | MUSS · Aufnahmen durchsuchen | 13 / T23 | ohne Nachweis | teilweise · LibraryView, SessionStore | Code | Name, Datum, Zeit, Größe, Dauer, Suche und fünf Sortierschlüssel; Ordner, Uploadstatus und 500-Dateien-Test offen |
| e05 | MUSS · Sortieren nach Name, Datum, Dauer, Größe, Endung | 14 / T23 | teilweise · RecordEntity / SortOrder / RecordsViewModel | teilweise · LibraryView, SessionStore | Code | Name, Datum, Zeit, Größe, Dauer, Suche und fünf Sortierschlüssel; Ordner, Uploadstatus und 500-Dateien-Test offen |
| e06 | MUSS · Dateien manuell umbenennen | 11, 44 / T24, T38 | vorhanden · RecordsViewModel / HomeViewModel | fehlt · Dateiverwaltung | Offen | Für Endabnahme erforderlich |
| e07 | MUSS · Dateien kopieren und verschieben | 11, 12, 15, 49 / T24, T38 | teilweise · MoveRecordsViewModel / DeletedRecordsViewModel | fehlt · Dateiverwaltung | Offen | Für Endabnahme erforderlich |
| e08 | MUSS · Android-Teilen-Menü | 11, 12 / T25 | vorhanden · RecordsViewModel / HomeViewModel | vorhanden · LibraryView, FileProvider | Code | Einzeldatei mit Leserecht freigeben; Empfang und Mehrfachteilen prüfen |
| e09 | MUSS · Mehrfachauswahl von Aufnahmen | 12 / T26 | ohne Nachweis | fehlt · Dateiverwaltung | Offen | Für Endabnahme erforderlich |
| e10 | MUSS · Papierkorb mit Wiederherstellung | 33, 34, 50 / T27, T38 | teilweise · MoveRecordsViewModel / DeletedRecordsViewModel | fehlt · Dateiverwaltung | Offen | Für Endabnahme erforderlich |
| e11 | NICHT BENÖTIGT · Aufnahmen anpinnen | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| e12 | MUSS · Ordner vor Android-Mediensuche verbergen | 47 / T28 | ohne Nachweis | fehlt · Dateiverwaltung | Offen | Für Endabnahme erforderlich |
| e13 | OPTIONAL · Ausgeblendete Aufnahmen anzeigen | ∅ / ∅ | ohne Nachweis | fehlt · Dateiverwaltung | Offen | Optional |
| e14 | MUSS · Korrekte Dateizeitstempel und Metadaten | 9, 32 / T29 | teilweise · RecordEntity / SortOrder / RecordsViewModel | teilweise · SessionStore | AVD · Framezahl gegen Decoder | Start mit Offset, Frames und Bytezahl; Lebenszyklus von Import/Kopie/Verschieben offen |
| e15 | MUSS · Bestehende Aufnahmen importieren | 32 / T30 | vorhanden · RecordsViewModel / HomeViewModel | fehlt · Dateiverwaltung | Offen | Für Endabnahme erforderlich |

## Wiedergabe

| ID | Priorität, Anforderung | Beschlüsse / Tests | Kandidat | Audomo und Modul | Nachweis | Einschränkung |
|---|---|---|---|---|---|---|
| f01 | MUSS · Audioplayer mit Zeitleiste | 10, 10a, 10b / T31, T43, T44 | vorhanden · ExoAudioPlayer / AudioPlaybackService / PlaybackSpeed | vorhanden · LibraryView (Media3) | AVD · laufende Wiedergabe im Listenplayer | Listenplayer mit Seek, einstellbarem Sprung, Tempo und Stille-Skip; vollständige T31-Abnahme offen |
| f02 | MUSS · Wiedergabe pausieren/fortsetzen | 10, 10a / T31 | vorhanden · ExoAudioPlayer / AudioPlaybackService / PlaybackSpeed | vorhanden · LibraryView (Media3) | Code | Listenplayer mit Seek, einstellbarem Sprung, Tempo und Stille-Skip; vollständige T31-Abnahme offen |
| f03 | MUSS · Vor- und Zurückspringen um 5 Sekunden | 10, 10a / T31 | teilweise · ExoAudioPlayer (Seek) | vorhanden · LibraryView (Media3) | Code | Listenplayer mit Seek, einstellbarem Sprung, Tempo und Stille-Skip; vollständige T31-Abnahme offen |
| f04 | MUSS · Sprungintervalle konfigurieren | 10b / T31 | teilweise · ExoAudioPlayer (Seek) | vorhanden · LibraryView (Media3) | Code | Listenplayer mit Seek, einstellbarem Sprung, Tempo und Stille-Skip; vollständige T31-Abnahme offen |
| f05 | MUSS · Wiedergabegeschwindigkeit ändern | 10b / T31 | vorhanden · ExoAudioPlayer / AudioPlaybackService / PlaybackSpeed | vorhanden · LibraryView (Media3) | Code | Listenplayer mit Seek, einstellbarem Sprung, Tempo und Stille-Skip; vollständige T31-Abnahme offen |
| f06 | NICHT BENÖTIGT · Wiedergabeschleife | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| f07 | MUSS · Stille bei Wiedergabe überspringen | 10b / T31 | ohne Nachweis | vorhanden · LibraryView (Media3) | Code | Listenplayer mit Seek, einstellbarem Sprung, Tempo und Stille-Skip; vollständige T31-Abnahme offen |
| f08 | OPTIONAL · Hintergrundwiedergabe | ∅ / ∅ | vorhanden · ExoAudioPlayer / AudioPlaybackService / PlaybackSpeed | fehlt · Wiedergabe | Offen | Optional |
| f09 | OPTIONAL · Steuerung über Bluetooth-Headset oder Auto | ∅ / ∅ | ohne Nachweis | fehlt · Wiedergabe | Offen | Optional |
| f10 | NICHT BENÖTIGT · Audio schneiden und bearbeiten | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| f11 | OPTIONAL · Alte Aufnahme nachträglich fortsetzen | ∅ / ∅ | ohne Nachweis | fehlt · Wiedergabe | Offen | Optional |
| f12 | OPTIONAL · Audioformat konvertieren | ∅ / ∅ | ohne Nachweis | fehlt · Wiedergabe | Offen | Optional |
| f13 | MUSS · Datei mit anderer Audio-App öffnen | 11 / T25 | ohne Nachweis | vorhanden · LibraryView, FileProvider | Code | Einzeldatei mit Leserecht freigeben; Empfang und Mehrfachteilen prüfen |
| f14 | NICHT BENÖTIGT · Audio als Video exportieren | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| f15 | NICHT BENÖTIGT · Aufnahme als Klingelton | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |

## Uploads

| ID | Priorität, Anforderung | Beschlüsse / Tests | Kandidat | Audomo und Modul | Nachweis | Einschränkung |
|---|---|---|---|---|---|---|
| g01 | MUSS · Dropbox-Anbindung | 20, 21, 22 / T32 | ohne Nachweis | fehlt · Uploads | Offen | Für Endabnahme erforderlich |
| g02 | MUSS · WebDAV-Anbindung | 20, 21, 22 / T32 | ohne Nachweis | fehlt · Uploads | Offen | Für Endabnahme erforderlich |
| g03 | MUSS · FTP/FTPS-Anbindung | 20, 21, 22 / T32 | ohne Nachweis | fehlt · Uploads | Offen | Für Endabnahme erforderlich |
| g04 | MUSS · SFTP-Anbindung | 20, 21, 22 / T32 | ohne Nachweis | fehlt · Uploads | Offen | Für Endabnahme erforderlich |
| g05 | MUSS · Automatischer Upload nach Aufnahmeende | 8, 20, 22 / T33 | ohne Nachweis | fehlt · Uploads | Offen | Für Endabnahme erforderlich |
| g06 | MUSS · Einzelne Aufnahmen manuell hochladen | 11, 20, 22 / T33, T37 | ohne Nachweis | fehlt · Uploads | Offen | Für Endabnahme erforderlich |
| g07 | MUSS · Upload auf WLAN beschränken können | 20, 22 / T34 | ohne Nachweis | fehlt · Uploads | Offen | Für Endabnahme erforderlich |
| g08 | MUSS · Upload über mobile Daten | 20, 22 / T34 | ohne Nachweis | fehlt · Uploads | Offen | Für Endabnahme erforderlich |
| g09 | MUSS · Zielordner auf Server wählen | 21, 48 / T32 | ohne Nachweis | fehlt · Uploads | Offen | Für Endabnahme erforderlich |
| g10 | OPTIONAL · Gleichzeitig an mehrere Ziele hochladen | ∅ / ∅ | ohne Nachweis | fehlt · Uploads | Offen | Optional |
| g11 | MUSS · Persistente Upload-Warteschlange | 22 / T33, T37 | ohne Nachweis | fehlt · Uploads | Offen | Für Endabnahme erforderlich |
| g12 | MUSS · Automatische Wiederholungsversuche | 22, 41, 43 / T33 | ohne Nachweis | fehlt · Uploads | Offen | Für Endabnahme erforderlich |
| g13 | MUSS · Upload nach Neustart fortsetzen | 22 / T07, T33 | ohne Nachweis | fehlt · Uploads | Offen | Für Endabnahme erforderlich |
| g14 | MUSS · Upload-Fortschritt und Fehler anzeigen | 22, 41, 42, 43 / T33, T37 | ohne Nachweis | fehlt · Uploads | Offen | Für Endabnahme erforderlich |
| g15 | MUSS · Erfolgreichen Upload überprüfen | 22, 34 / T35, T38 | ohne Nachweis | fehlt · Uploads | Offen | Für Endabnahme erforderlich |
| g16 | MUSS · Originaldatei lokal behalten | 34 / T36, T37 | ohne Nachweis | teilweise · SessionStore | AVD · lokale Originaldateien | Behalten nach verifiziertem Servertransfer mangels Uploadblock offen |
| g17 | MUSS · Automatische Löschung nach Upload | 34 / T36 | ohne Nachweis | fehlt · Uploads | Offen | Für Endabnahme erforderlich |
| g18 | MUSS · Zugangsdaten geschützt speichern | 21, 38 / T39 | ohne Nachweis | fehlt · Uploads | Offen | Für Endabnahme erforderlich |
| g19 | NICHT BENÖTIGT · Upload nur während Smartphone geladen wird | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |

## Weitere Funktionen

| ID | Priorität, Anforderung | Beschlüsse / Tests | Kandidat | Audomo und Modul | Nachweis | Einschränkung |
|---|---|---|---|---|---|---|
| h01 | OPTIONAL · Dark Mode und Android-Systemdesign | 3, 4, 29 / ∅ | teilweise · v2/theme, v2/app, Audioplayer | vorhanden · MainActivity, theme.xml | AVD · Screenshots | Gewählte dunkle Variante; zusätzliche Systemfarbschemata außerhalb der Entscheidung · Dunkles Design ausgewählt; vollständiger zusätzlicher Hellmodus ist nicht zugesagt. |
| h02 | NICHT BENÖTIGT · Verschiedene Farbschemata | ∅ / ∅ | teilweise · v2/theme, v2/app, Audioplayer | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| h03 | NICHT BENÖTIGT · Automatische Bildschirmrotation | 4 / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| h04 | MUSS · Schnellaktionen in Aufnahmeliste | 11 / T26, T43 | teilweise · v2/theme, v2/app, Audioplayer | teilweise · LibraryView | Code | Teilen, Export und Öffnen verfügbar; komplette F2- und M2-Aktionen fehlen |
| h05 | MUSS · Aufnahme und Wiedergabe getrennt | 5, 10 / T01, T31, T43 | teilweise · v2/theme, v2/app, Audioplayer | vorhanden · MainActivity, LibraryView, CaptureService | AVD · Aufnahmeansicht | Getrennte Zustände; Aufnahmepriorität und Wiedergabeübergänge umfassend prüfen |
| h06 | NICHT BENÖTIGT · Direkte Übergabe an VoxDrop über Teilen | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| h07 | MUSS · Originalaufnahme unverändert erhalten | 32, 34 / T24, T30, T31 | teilweise · v2/theme, v2/app, Audioplayer | teilweise · OggOpus, SessionStore, LibraryView | OggOpusTest · Pakete bytegleich | Original bei Recovery und Wiedergabe erhalten; gesamte spätere Dateiverwaltung offen |
| h08 | NICHT BENÖTIGT · Integrierte Transkription in der App | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| h09 | MUSS · Diagnoseprotokoll für Aufnahmefehler | 35, 36, 37 / T05, T39, T41 | teilweise · Timber und BrokenRecordRestorer | teilweise · Audomo, MainActivity | Code | Lokale Ereignisse und technische Angaben; Filter und manueller Diagnoseexport fehlen |
| h10 | MUSS · Werbefrei und ohne Tracking | 1, 29 / T42 | ohne Nachweis | vorhanden · AndroidManifest.xml, app/build.gradle.kts | APK-Manifest | Lokale APK ohne Internetberechtigung; spätere Uploadabhängigkeiten separat prüfen |
| h11 | MUSS · Einstellungen importieren/exportieren | 38 / T39, T40 | ohne Nachweis | fehlt · Weitere Funktionen | Offen | Für Endabnahme erforderlich |
| h12 | NICHT BENÖTIGT · Aufnahmequalitätsvergleich mit VoxDrop | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |
| h13 | NICHT BENÖTIGT · Aufnahme über Sprachbefehl starten | ∅ / ∅ | ohne Nachweis | abgewählt | Featurematrix | Kein Implementierungsauftrag |
