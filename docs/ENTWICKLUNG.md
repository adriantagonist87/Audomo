# Entwicklungsübergabe zu Audomo 1.0

## Wo eine Änderung beginnt

Audomo ist eine native Android-Anwendung mit einem Gradle-Modul. Der Java-Code liegt in [de.audomo](../app/src/main/java/de/audomo), die Plattform-Einstiegspunkte und Berechtigungen im [Manifest](../app/src/main/AndroidManifest.xml). Die Activities bauen ihre Android-Views weitgehend im Code auf. Farben, Schrift, Vektorsymbole und die Benachrichtigungsmarke liegen unter `app/src/main/res`. Versionscode, Paketname, SDK-Stufen, Abhängigkeiten und Dropbox-Rücksprungschema stehen in [app/build.gradle.kts](../app/build.gradle.kts).

| Änderungsbereich | Einstieg und beteiligte Klassen | Prüfung mit dem größten Bezug |
|---|---|---|
| Aufnahmetaste, Benachrichtigung oder Kachel | `MainActivity`, `CaptureService`, `CaptureTile`, `TileStartActivity`, `TileOpenActivity`; die Sitzung verwaltet `Audomo` | Aufnahme aus der App, doppelte Befehle, Pause, alte Notification-Aktion, Kachellangdruck |
| Audioformat, Mikrofon, Profil oder Rettung | `Profile`, `AudioInput`, `CaptureEngine`, `OpusEncoder`, `OggOpus`, `SessionStore` | PCM-Gain, Opus-Paketdauer und Ogg-Endseite, Stoppen aus Pause, Prozessabbruch und Decode |
| Kalender und nächste Aufnahme | `CalendarNames`, `CalendarSettings`, `NamingSettings`, `CalendarDayActivity`, `CalendarTimeline`, `RecordingDraft` | Tageswechsel, ganztägige und überlappende Termine, geänderte Berechtigung, Abbrechen, einmalige Übernahme |
| Bibliothek und Player | `LibraryActivity`, `LibraryView`, `LibraryActions`, `LibraryFiles` und Media3 | Auswahl bei Suche/Sortierung, Zurück, Pause und Seek, Import, Teilen und Papierkorb |
| Ordner und externe Dokumente | `FolderManager`, `FolderSettings`, `DocumentStorage`, `DocumentReconcile` | Verschachtelte Pfade, ordnergebundene Einstellungen, entzogenes SAF-Recht, unterbrochener Umzug |
| Zugang und Transport | `CloudScreen`, `CloudConfig`, `DropboxLinkDialog`, `DropboxFiles`, `RemoteFiles`, `UploadWorker` | OAuth-Rücksprung und Abbruch, Serveridentität, Hashkonflikt, Abbruch vor und während der Veröffentlichung |
| Uploadplanung, Bedienung und Katalog | `ManualUploadDialog`, `UploadQueue`, `UploadDelay`, `UploadRetry`, `QueueActivity`, `QueueDashboard`, `QueueSwipeList`, `UploadDetailsDialog`, `CloudLibrary`, `CloudReconcile` | Doppelte Aufträge, zwei Dienste, Fälligkeit, Reihenfolge nach Neustart, Leeren mit erhaltener Cloudkopie |
| Einstellungen und Backup | `AppSettings`, `PlaybackOptions`, `SettingsBackup` und die jeweilige Fachklasse | Alte Optionen, ungültiges Importformat, entkoppelte Konten und Android-Freigaben |

## Vom Start bis zur gesicherten Aufnahme

1. Android erzeugt `Audomo` als Application. Beim Prozessstart prüft der serielle Worker unterbrochene Sitzungen, setzt einen ausstehenden Ordnerwechsel fort und gleicht Aufträge ab. Die UI zeigt während der Wiederherstellungsprüfung einen eigenen Zustand.
2. Der Nutzer startet über Dashboard oder Kachel. Mikrofonzugriff und Androids Foreground-Service-Regeln gelten auch für den Kachelpfad. `CaptureService` bindet Aktionen an die aktuelle Sitzung. Das Mikrofon öffnet erst als Folge eines Aufnahmekommandos.
3. Eingang und Profil liefern PCM an `CaptureEngine`. Digitale Verstärkung begrenzt die Werte auf den zulässigen Bereich. `OpusEncoder` verwendet Androids MediaCodec. `OggOpus` schreibt sequenzierte Seiten mit CRC und Granule-Positionen in Einheiten von 48 kHz. Der Zähler der Aufnahme beruht auf verarbeiteten Frames. Eine Pause unterbricht die Eingabe und lässt die Sitzung offen.
4. Unter `files/sessions/<UUID>/` liegen `session.json`, die laufende `capture.part` und eine kleine Speicherreserve. Metadaten werden über temporäre Datei, Synchronisieren und Umbenennen ersetzt. Der Container sichert vollständige Seiten regelmäßig. Beim Wiederanlauf kann Audomo intakte Seiten bis zum letzten gültigen Punkt retten. Vor einer Änderung an diesem Verfahren den Codepfad für beschädigte Metadaten und den unabhängigen Decode-Test lesen. [Entwurf und Recovery](../ADR-001.md)
5. Ein erfolgreicher Stopp finalisiert die Datei. Erst danach folgen optionale SAF-Auslieferung und Uploadplanung. Ein späterer Übertragungsfehler muss getrennt von einem Fehler beim lokalen Speichern angezeigt werden. Der Prozess verwendet getrennte Executor für Aufnahme/Startprüfung, Dateiverwaltung und Uploadbedienung. Netzwerk-I/O gehört zum Uploadpfad.

## Identität und dauerhafte Daten

| Ablage | Inhalt | Bei Änderungen beachten |
|---|---|---|
| `files/sessions/<UUID>/session.json` und Audio | Startzeit, Namen, Ordner, Profil, Frames, Papierkorb- und Ablageinformationen je nach Lebenszyklus | Anzeigename und Dateiname im Sitzungsverzeichnis können differieren. Alte und unvollständige Metadaten lesen können. |
| App-Einstellungen und `next-recording` | Profile, Namen, Kalenderauswahl, einmaliger Entwurf | Der bestätigte Entwurf gilt für die nächste Aufnahme. Abbrechen erhält ihn. Bei Wechsel von Kalenderrechten die Auswahl prüfen. |
| Persistente SAF-Baumfreigabe und Dokument-URIs | Android verwaltet die Freigabe. Audomo speichert das Ziel einer geprüften Kopie. | Verschieben und Freigabeentzug können URIs ungültig machen. Den privaten Bestand und die bisherigen Zuordnungen bis zur eindeutigen Bestätigung erhalten. |
| `uploads.db`, SQLite-Version 6 | `jobs` bildet Aufnahme/Ziel, Status und Übertragung ab. `recordings` hält bibliotheksfähige Metadaten für die Cloudkopie. | Eindeutiges Paar `(rec,target)`, monotone Job-ID, getrennte Felder für Priorität, Fälligkeit und Sichtbarkeit. |
| `credentials` und Android Keystore | Cloudzugänge liegen AES/GCM-verschlüsselt in privaten Preferences. Der Schlüssel kommt aus dem Android Keystore. | Zugriff über `CloudConfig`. Einstellungsexporte und Diagnoseeinträge enthalten keine Zugangsdaten. |
| Einstellungs-JSON, Schema 1 | Explizit ausgewählte Werte, Profile und Benennungsregeln | Beim Erweitern Export, Validierung, Import und Rückwärtskompatibilität zusammen anpassen. Gerätebezogene Rechte und Verknüpfungen gesondert behandeln. |

`FolderManager` hinterlegt einen ausstehenden Ordnerwechsel, bevor er Sitzungen, Katalog und Referenzen anpasst. Ein Neustart setzt ihn fort. SAF-Ziele liefern und prüfen Dateien über den ContentResolver. Für externe Umzüge suchen `DocumentReconcile` und `CloudReconcile` nach identifizierbaren Kopien. Bei uneindeutigem Inhalt muss der Nutzer die Abweichung sehen können. Eine Cloudaufnahme kann allein durch die bestätigte Zuordnung in der Bibliothek weiterleben.

## Einen Upload vom Auftrag bis zum Katalog verfolgen

`ManualUploadDialog` bildet die gewählten Aufnahmen und Ziele auf Aufträge ab. Nach einer Aufnahme ermittelt `UploadQueue.afterCapture` die automatische Planung. Bei verzögertem Upload liegt der früheste Zeitpunkt als `notBefore` in der Datenbank. Manuelle Freigabe und Priorität sind verschiedene Aktionen. Die Netzregel sowie Androids JobScheduler entscheiden zusätzlich über die tatsächliche Ausführung.

`UploadWorker` beansprucht einen fälligen Auftrag und prüft Quellzustand, Netz und Ziel. Ein vorhandener abweichender Zielinhalt führt zum Konflikt. Beim Übertragen und beim Übernehmen der Zieldatei gelten gesonderte Abbruchpunkte. Nach erfolgreicher Übertragung wird der Remote-Inhalt zurückgelesen und mit SHA-256 verglichen. FTP, explizites FTPS, SFTP und WebDAV sind in `RemoteFiles` implementiert. Dropbox benutzt zusätzlich `DropboxFiles`. Bei einer Cloudverschiebung prüft der Worker auch den früheren Pfad.

Beim nachträglichen Umbenennen übernimmt `LibraryFiles.rename` die lokale Änderung und übergibt die gewählten Aufträge an `UploadQueue.renameTargets`. Für bestätigte Kopien bleibt der bisherige Pfad in `origin`, der neue Name im selben Verzeichnis steht in `path`. Der Worker benennt die Datei beim Dienst um. Bei einer verlorenen Antwort prüft er beide Pfade, bevor er einen weiteren Schreibversuch zulässt. Erst bei identischem SHA-256 am neuen Pfad und verschwundener Quelle wird der Auftrag bestätigt. Ein geplanter Erstupload behält seine Auftrags-ID und Fälligkeit. Der sichtbare Name einer lokalen Aufnahme und die Namen ihrer Cloudkopien können voneinander abweichen.

`priority` ändert die Reihenfolge geeigneter Aufträge, `notBefore` bleibt erhalten. `inQueue` steuert die Anzeige. Entfernt der Nutzer einen bestätigten Auftrag aus der Warteschlange, muss der bestätigte Speicherort weiter abrufbar sein. In der kritischen Abschlussphase kann eine Entfernung eine spätere reine Nachprüfung (`verifyRemoval`) benötigen. Dort darf eine Wiederholung den Serverinhalt nicht erneut verändern. Die Migration in `UploadQueue.onUpgrade` erhält vorhandene IDs und Felder und setzt die neuen Standardwerte. Bei neuen Schemata immer eine bestehende Datenbank mit laufenden und abgeschlossenen Aufträgen prüfen.

## Bedienentscheidungen, die ältere Referenzen präzisieren

- Drei Hauptreiter mit „Aufnehmen“ als Wurzel. Eine Unteransicht merkt sich den tatsächlichen Einstieg. Die System-Zurücktaste schließt zuerst Auswahl, Suche, Player oder Dialog. Ein langer Kacheldruck bringt Audomo in den Vordergrund.
- Der Start der Aufnahme ist ein runder gedeckt grüner Punkt. Fortsetzen und Wiedergabe verwenden Play. Die Aufnahmenotification trägt die Audomo-Bildmarke und den Wortlaut „die App ist aktiv und verrichtet ihren Dienst“. Der lange Vibrationsimpuls dauert 500 ms, kurze dauern je 300 ms mit 200 ms Pause.
- Dateiliste mit Datum, Uhrzeit, Größe und bestätigten Speicherorten. Sortierfeld und Richtung lassen sich direkt wählen. Bibliothek und Warteschlange haben jeweils einen Auswahlmodus mit Alles-auswählen/Alles-abwählen.
- Kalendername und Termintitel sind eigene Bausteine. Tagesauswahl, manueller Name und Ordnerbildung sind getrennt zu prüfen. Kalender- und Cloudauswahl bleiben freiwillig.
- Die Warteschlange bildet Aufträge pro Dienst ab. Rechtswisch priorisiert, Linkswisch entfernt. Für dieselben Aktionen gibt es Bedienelemente. „Warteschlange leeren“ bezieht sich auch bei aktivem Filter auf alle sichtbaren Auftragsarten und verlangt eine Bestätigung.
- Für die dicht gesetzte Bibliothek gelten Listenmaster 09, korrigierter Player 10 und die F2-Aktionen. Größere Systemschrift darf die Zahl sichtbarer Zeilen reduzieren. Die versionierten Bildbelege stammen von der laufenden App. [Beschlussabgleich](../qa/MOCKUPABGLEICH.md) · [Geräteprotokoll](../qa/GERAETETEST.md)

## Anforderungen zurückverfolgen

Die [ursprüngliche Matrix](spezifikation/featurematrix_original.json), [Standardprofile](spezifikation/standardprofile_freigegeben.json), [Produktregeln](spezifikation/PRODUKTREGELN_UND_DATENMODELL.md) und der [ursprüngliche Abnahmeplan](spezifikation/ABNAHMEPLAN.md) sind historische Grundlagen. Der [Funktionsabgleich](../qa/ANFORDERUNGSABGLEICH.md) ordnet Module und Prüfungen je ID zu. Für den heutigen Stand zusätzlich die späteren Einträge im [Geräteprotokoll](../qa/GERAETETEST.md) und die [Grenzen von 1.0.0](../qa/RELEASE-1.0.0.md) lesen.

### Dashboard-Bedienung ab 1.1.0

`DashboardGestures` erkennt seitliche Gesten im Fenster der drei Dashboard-Activities und verwendet dieselbe Navigation wie die Reitertasten. Eine Wischgeste auf einer Auftragszeile geht an `QueueSwipeList`, auf der Zeitleiste an den Player. Bei einem Außentipp auf den geöffneten Player erhält das berührte Element ein Abbruchereignis. Native Auswahldialoge verwenden das gemeinsame Dialogthema. Die Einstellungsübersicht und ihre Untermenüs sind ganze Seiten mit eigener Zurück-Navigation.

`QueueDashboard` speichert den Filter unter `queueFilter` in den App-Einstellungen. Die Werte 0 bis 3 stehen in der Reihenfolge des Auswahlmenüs und sind Teil des Einstellungsbackups. Beim Rücksprung auf eine ältere Activity liest die Warteschlange den aktuellen Wert erneut. Leere Filter dürfen keine Listenposition auswählen. Das ist auf One UI auch bei Adaptern mit stabilen IDs erforderlich.

### Audioausgang im Listenplayer ab 1.1.1

Vor jedem Play prüft `LibraryView` die mit den Media3-Audioattributen erwarteten Ausgänge über `AudioManager.getAudioDevicesForAttributes`. Ein leerer oder gemischter Befund braucht eine Rückfrage. `bluetoothOnly` sperrt die Ausnahme und wird mit den Wiedergabeeinstellungen gesichert. Eine Einmalfreigabe bleibt im laufenden Startvorgang.

`PlaybackOutput` startet die geschützte Wiedergabe stumm, bevorzugt einen eindeutig zugeordneten Medienausgang und prüft am tatsächlich verwendeten Media3-`AudioTrack` die Route. Android 14 liefert am Track einen Ausgang, ab API 36 lassen sich alle Ausgänge abfragen. Zusätzlich prüft Audomo die von Android erwartete Routengruppe. Der Routing-Listener, die erneute Prüfung während der Wiedergabe und Media3s Behandlung von `ACTION_AUDIO_BECOMING_NOISY` decken Wechsel und Trennungen ab. Android steuert den Zeitpunkt der Umschaltung und Benachrichtigung. [Android-Medienausgänge](https://developer.android.com/reference/android/media/AudioRouting) · [Änderungen der Audioausgabe](https://developer.android.com/media/platform/output)
