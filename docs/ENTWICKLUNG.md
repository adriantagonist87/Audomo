# Entwicklungsübergabe zu Audomo 1.0

## Wo eine Änderung beginnt

Audomo ist eine native Android-Anwendung mit einem Gradle-Modul. Der Java-Code liegt in [de.audomo](../app/src/main/java/de/audomo), die Plattform-Einstiegspunkte und Berechtigungen im [Manifest](../app/src/main/AndroidManifest.xml). Die Activities bauen ihre Android-Views weitgehend im Code auf. Farben, Schrift, Vektorsymbole und die Benachrichtigungsmarke liegen unter `app/src/main/res`. Versionscode, Paketname, SDK-Stufen, Abhängigkeiten und Dropbox-Rücksprungschema stehen in [app/build.gradle.kts](../app/build.gradle.kts).

| Änderungsbereich | Einstieg und beteiligte Klassen | Prüfung mit dem größten Bezug |
|---|---|---|
| Aufnahmetaste, Benachrichtigung, Kachel oder Startbildschirm-Verknüpfung | `MainActivity`, `CaptureService`, `CaptureTile`, `TileStartActivity`, `TileOpenActivity`; die Sitzung verwaltet `Audomo` | Aufnahme aus der App, doppelte Befehle, Pause, alte Notification-Aktion, Kachellangdruck |
| Audioformat, Mikrofon, Profil oder Rettung | `Profile`, `AudioInput`, `CaptureEngine`, `OpusEncoder`, `OggOpus`, `SessionStore` | PCM-Gain, Opus-Paketdauer und Ogg-Endseite, Stoppen aus Pause, Prozessabbruch und Decode |
| Kalender und nächste Aufnahme | `CalendarNames`, `CalendarSettings`, `NamingSettings`, `CalendarDayActivity`, `CalendarTimeline`, `RecordingDraft` | Tageswechsel, ganztägige und überlappende Termine, geänderte Berechtigung, Abbrechen, einmalige Übernahme |
| Bibliothek und Player | `LibraryActivity`, `LibraryView`, `LibraryActions`, `LibraryFiles` und Media3 | Auswahl bei Suche/Sortierung, Zurück, Pause und Seek, Import, Teilen und Papierkorb |
| Ordner und externe Dokumente | `FolderManager`, `FolderSettings`, `DocumentStorage`, `DocumentReconcile` | Verschachtelte Pfade, ordnergebundene Einstellungen, entzogenes SAF-Recht, unterbrochener Umzug |
| Zugang und Transport | `CloudScreen`, `CloudConfig`, `DropboxLinkDialog`, `DropboxFiles`, `RemoteFiles`, `UploadWorker` | OAuth-Rücksprung und Abbruch, Serveridentität, Hashkonflikt, Abbruch vor und während der Veröffentlichung |
| Uploadplanung, Bedienung und Katalog | `ManualUploadDialog`, `UploadQueue`, `UploadDelay`, `UploadRetry`, `QueueActivity`, `QueueDashboard`, `QueueSwipeList`, `UploadDetailsDialog`, `CloudLibrary`, `CloudReconcile` | Doppelte Aufträge, zwei Dienste, Fälligkeit, Reihenfolge nach Neustart, Leeren mit erhaltener Cloudkopie |
| Einstellungen und Backup | `AppSettings`, `PlaybackOptions`, `BackupDialogs`, `MigrationBackup` und `SettingsBackup` für alte JSON-Dateien | Alte Optionen, ungültiges Importformat, entkoppelte Konten und Android-Freigaben |

## Vom Start bis zur gesicherten Aufnahme

1. Android erzeugt `Audomo` als Application. Beim Prozessstart prüft der serielle Worker unterbrochene Sitzungen, setzt einen ausstehenden Ordnerwechsel fort und gleicht Aufträge ab. Die UI zeigt während der Wiederherstellungsprüfung einen eigenen Zustand.
2. Der Nutzer startet über Dashboard, Kachel oder Startbildschirm-Verknüpfung. Mikrofonzugriff und Androids Foreground-Service-Regeln gelten auch für den Kachelpfad. `CaptureService` bindet Aktionen an die aktuelle Sitzung. Das Mikrofon öffnet erst als Folge eines Aufnahmekommandos.
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
- Die Warteschlange bildet Aufträge pro Dienst ab. Rechtswisch priorisiert, Linkswisch entfernt. Für dieselben Aktionen gibt es Bedienelemente. „Warteschlange leeren“ ist in „Alle Aufträge“ erreichbar und verlangt eine Bestätigung.
- Für die dicht gesetzte Bibliothek gelten Listenmaster 09, korrigierter Player 10 und die F2-Aktionen. Größere Systemschrift darf die Zahl sichtbarer Zeilen reduzieren. Die versionierten Bildbelege stammen von der laufenden App. [Beschlussabgleich](../qa/MOCKUPABGLEICH.md) · [Geräteprotokoll](../qa/GERAETETEST.md)

## Anforderungen zurückverfolgen

Die [ursprüngliche Matrix](spezifikation/featurematrix_original.json), [Standardprofile](spezifikation/standardprofile_freigegeben.json), [Produktregeln](spezifikation/PRODUKTREGELN_UND_DATENMODELL.md) und der [ursprüngliche Abnahmeplan](spezifikation/ABNAHMEPLAN.md) sind historische Grundlagen. Der [Funktionsabgleich](../qa/ANFORDERUNGSABGLEICH.md) ordnet Module und Prüfungen je ID zu. Für den heutigen Stand zusätzlich die späteren Einträge im [Geräteprotokoll](../qa/GERAETETEST.md) und die [Grenzen von 1.0.0](../qa/RELEASE-1.0.0.md) lesen.

### Papierkorb in Einstellungen und Bibliothek ab 1.1.11

Der Papierkorb hat eine eigene Seite in `AppSettings`. Beim Leeren liest diese Seite die aktuell lokal als gelöscht markierten Sitzungen auf `Audomo.libraryFiles` und zeigt ihre Anzahl. Erst die Bestätigung übergibt diese Auswahl an `LibraryActions.perform` und `LibraryFiles.purge`. Damit gilt beim Leeren dieselbe Prüfung auf zwischenzeitlich geänderte oder wiederhergestellte Aufnahmen wie bei der Einzellöschung. Die Dateiliste im Dashboard kann dabei geschlossen sein.

Das Papierkorb-Symbol in `LibraryView` und der Einstieg aus den Einstellungen verwenden beide `MainActivity.openTrash`. Der Verlauf hält fest, ob beim Zurückgehen zusätzlich die Papierkorb-Einstellungen geöffnet werden sollen.

### Papierkorb und Uploads ab 1.1.10

`LibraryFiles.change` sichert beim Verschieben die Papierkorbmarkierung in der Sitzung und entfernt unter derselben Warteschlangensperre die ausstehenden Uploads für deren UUID. Die Entfernung zieht die Meldungen der einzelnen Zielaufträge zurück und plant die verbliebenen Fälligkeiten neu. Die Metadaten halten die Automatik für diese Aufnahme mit `autoQueued` für abgearbeitet. Eine spätere manuelle Auswahl darf einen neuen Auftrag erzeugen. Beim Wiederherstellen und beim endgültigen Löschen werden auch veraltete Aufträge früherer Versionen bereinigt.

`UploadQueue.beforeCommit` speichert ab Datenbankversion 7 den erwarteten Inhaltshash in `publicationHash`, bevor der Server die Datei endgültig übernehmen kann. Nach einem Verbindungsabbruch oder Prozessneustart kann die Entfernung damit einen möglicherweise abgeschlossenen Transfer in eine unsichtbare, rein lesende Prüfung (`verifyRemoval`) umwandeln. Bei alten, bereits gesendeten Uploads kennzeichnet SQL-NULL den unbekannten Abschlusszustand. Nur in diesem Fall wird beim Bereinigen der Hash aus der lokalen Datei bestimmt. Bestätigte Zielkopien, ausdrücklich beauftragte Cloudlöschungen und Umbenennungen behalten ihren eigenen Lebenszyklus.

Beim App-Start gleicht `reconcileTrash` die betroffenen UUIDs mit dem lokalen Papierkorb ab. Der Worker prüft den Sitzungszustand zusätzlich beim Transferstart. Eine tatsächlich fehlende Quelle ohne Papierkorbmarkierung bleibt als Fehler sichtbar.

### Dashboard-Bedienung ab 1.1.0

Ab Version 1.1.8 leiten sich alle Activities mit privaten Inhalten von `ProtectedActivity` ab. Beim gesperrten Kaltstart wartet der Aufbau der Zielansicht einschließlich gespeicherter Fragmente und externer Ergebnisse auf die Bestätigung. Bei der Rückkehr maskiert die Activity ihre Inhalte und Dialogfenster, bis die Sitzung wieder freigegeben ist. Interne Activity-Wechsel verwenden `FLAG_ACTIVITY_NO_USER_ACTION`, damit Home die Sitzung auch bei einer schnellen Rückkehr beenden kann. Bei eingeschalteter Sperre deaktivieren die Activities ihre Screenshot-Vorschau in der Übersicht der letzten Apps.

`UnlockActivity` verwendet den Android-Systemdialog mit `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`. Nur der Aktivierungszustand liegt dauerhaft in den lokalen `app_lock`-Preferences. Die Freigabe lebt im Prozess. Der Einstellungsexport enthält weder den Sperrzustand noch ein Entsperrmerkmal. `CaptureTile`, `TileStartActivity` und der Mikrofon-Vordergrunddienst arbeiten unabhängig von der Freigabe der Ansichten. Neue Ansichten mit Aufnahmen, Kalenderdaten oder Einstellungen müssen die geschützte Basisklasse verwenden.

`DashboardGestures` erkennt seitliche Gesten im gemeinsamen Dashboard-Fenster und verwendet dieselbe Navigation wie die Reitertasten. Eine Wischgeste auf einer Auftragszeile geht an `QueueSwipeList`, auf der Zeitleiste an den Player. Bei einem Außentipp auf den geöffneten Player erhält das berührte Element ein Abbruchereignis. Native Auswahldialoge verwenden das gemeinsame Dialogthema. Die Einstellungsübersicht und ihre Untermenüs sind ganze Seiten mit eigener Zurück-Navigation.

Ab 1.1.9 enthält das Dashboard einen festen Kopf und einen `DashboardPager` mit drei Seitencontainern. Die Seitenposition in Bildschirmbreiten steuert die horizontale Verschiebung der Inhalte und die Position des Unterstrichs. Eine Geste kann vor dem Loslassen umkehren. Das Einrasten berücksichtigt Bewegungsrichtung, Geschwindigkeit und Androids Animationseinstellung. Nur die aktive Seite erhält den Fokus und ist für Bedienungshilfen erreichbar. Listen und Dateikatalog werden als Ansichten und Metadaten vorgehalten. Erst eine Wiedergabeaktion öffnet den Audioplayer.

`MainActivity` sichert bei einem abgeschlossenen Wechsel den bisherigen Besuch im Verlauf. Die Bibliothek hinterlegt unter anderem Suche, Auswahl und Listenposition, die Warteschlange ihre Auswahl und Position. Zurück stellt denselben Besuch wieder her. Der aktuelle Reiter und der Verlauf liegen auch im gespeicherten Activity-Zustand. Eine zurückgezogene Wischgeste erzeugt keinen Eintrag. `LibraryActivity` und `QueueActivity` bleiben als kompatible Einstiegspunkte für ältere Android-Tasks erhalten.

Seit 1.1.7 leitet `QueueDashboard` Zähler, Leerzustand und Sammelaktionen aus derselben gefilterten Liste ab. Die gespeicherten Werte für `queueFilter` bleiben 0 (alle), 1 (offen), 2 (zu prüfen) und 3 (wartend). Ohne gespeicherte Wahl gilt „Offene Aufträge“. Der Einstieg vom Prüfhinweis setzt „Zu prüfen“ für diesen Besuch der Warteschlange. Eine ausdrückliche Filterwahl gilt danach wieder appweit. `UploadQueue.Job.finished()` berücksichtigt sowohl den Auftragsstatus als auch gemeldete Probleme mit der Cloudkopie. Beim Entfernen beendeter Einträge prüft die Datenbank den aktuellen Zustand jeder bestätigten ID erneut. Leere Filter dürfen auf One UI keine Listenposition auswählen.

### Audioausgang im Listenplayer ab 1.1.1

Vor jedem Play prüft `LibraryView` die mit den Media3-Audioattributen erwarteten Ausgänge über `AudioManager.getAudioDevicesForAttributes`. Ein leerer oder gemischter Befund braucht eine Rückfrage. `bluetoothOnly` sperrt die Ausnahme und wird mit den Wiedergabeeinstellungen gesichert. Eine Einmalfreigabe bleibt im laufenden Startvorgang.

`PlaybackOutput` startet die geschützte Wiedergabe stumm, bevorzugt einen eindeutig zugeordneten Medienausgang und prüft am tatsächlich verwendeten Media3-`AudioTrack` die Route. Android 14 liefert am Track einen Ausgang, ab API 36 lassen sich alle Ausgänge abfragen. Zusätzlich prüft Audomo die von Android erwartete Routengruppe. Der Routing-Listener, die erneute Prüfung während der Wiedergabe und Media3s Behandlung von `ACTION_AUDIO_BECOMING_NOISY` decken Wechsel und Trennungen ab. Android steuert den Zeitpunkt der Umschaltung und Benachrichtigung. [Android-Medienausgänge](https://developer.android.com/reference/android/media/AudioRouting) · [Änderungen der Audioausgabe](https://developer.android.com/media/platform/output)

### Aufnahmeprofile und Berechtigungen ab 1.1.4

`ProfileSettings` öffnet die Profilübersicht aus dem Zahnrad und aus „Profil“ im Aufnahmedashboard. Auswahl im Dropdown und Speichern setzen das aktive Profil. Anlegen, Bearbeiten und Umbenennen öffnen eine eigene Activity-Instanz. Die Formularfelder behalten ihren Entwurf bei einer Bildschirmdrehung. Die Auswahllisten verwenden den gemeinsamen Adapter `ProfileDropdown`. Beim Bearbeiten zeigen native Radiogruppen die drei Effektwerte. Die Bedienoption „Standard“ entspricht dem bisherigen Speicherwert „Gerätestandard“. `Profile` schreibt die eigene Profilliste und die aktive Auswahl in derselben Preference-Änderung. Die Namen der Standardprofile sind reserviert, auch wenn frühere Versionen für sie bereits abweichende Klangwerte gespeichert haben.

Die Berechtigungszeilen in `AppSettings` lesen beim Zurückkehren in das Fenster den Android-Status. „Erlaubt“ und „Nicht erlaubt“ erhalten ihre Farbe aus demselben Prüfergebnis wie ihren Text.

### Schnellzugriff ab 1.1.5

`QuickAccess` zeigt das Ergebnis von `StatusBarManager.requestAddTileService` im Einrichtungsdialog. Die letzte bekannte Platzierung liegt auf diesem Gerät in den privaten Preferences `quickAccess`. `CaptureTile` aktualisiert sie beim Hinzufügen, Entfernen und beim Binden an eine vorhandene Kachel. Bei bislang unbekanntem Zustand prüft ein Tipp die Kachel über die Android-Anfrage. [Android-Kachel-API](https://developer.android.com/reference/android/app/StatusBarManager)

Die Launcher-Verknüpfung hat die feste ID `capture-toggle`. Der Anheftstatus kommt von `ShortcutManager.getPinnedShortcuts`. Ein einmaliger PendingIntent meldet die Annahme der Anheftanfrage, nach einem Abbruch prüft Audomo den Status bei der Rückkehr. Die Anfrage und die Launcher-Abfragen laufen außerhalb des UI-Threads. [Android-Shortcut-API](https://developer.android.com/reference/android/content/pm/ShortcutManager)

`TileStartActivity` ist der interne Einstieg für einen vom Nutzer ausgelösten Mikrofon-Vordergrunddienst. Ihr transparenter Task ist von der Dashboard-Navigation getrennt. Das Launcher-Intent mit der Aktion `TOGGLE_RECORDING` beendet auch eine pausierte Sitzung. Ein Start über die Kachel verwendet weiterhin den Startpfad. Bei einer Wiederherstellung der Activity wird kein neuer Steuerbefehl gesendet.

## Migrationsformat ab 1.2.0

`BackupDialogs` öffnet über den Android-Dokumentanbieter eine Datei mit der Endung `.audomo`. Der Formatkopf enthält die Kennung, Formatversion, Sicherungsstufe und ein zufälliges Salt. `BackupArchive` leitet aus dem Archivpasswort mit PBKDF2-HMAC-SHA256 und 600.000 Durchläufen den Schlüssel ab. Google Tink verschlüsselt und authentifiziert den Datenstrom mit AES-256-GCM-HKDF in Segmenten von 1 MiB. Der Kopf geht als authentifizierte Zusatzinformation ein. Die Eintragslänge umfasst 64 Bit. Auch ein Teilimport liest und authentifiziert das ganze Archiv bis zur Endmarke. [Tink Streaming AEAD](https://developers.google.com/tink/streaming-aead)

`BackupPreferences` überträgt portable Einstellungen und löst Kalender über Konto und Kalenderbezeichnung neu auf. Bei fehlender Freigabe oder mehrdeutigen Kalendern bleibt die Zuordnung offen. Nur Stufe 2 und 3 enthalten Verbindungsdaten. Gespeicherte Zugangsdaten werden innerhalb des verschlüsselten Archivs transportiert und auf dem Zielgerät mit dessen Android-Keystore neu verschlüsselt. Schnellkachel-Status, Android-Berechtigungen und Schlüsselmaterial sind gerätegebunden.

`MigrationBackup` reserviert den Leerlauf und erstellt eine Momentaufnahme. Fehlt Audio lokal, kann eine verifizierte Cloudkopie die Lücke schließen. `BackupDatabase` schreibt Bibliothekskatalog und Uploadaufträge zeilenweise mit Spaltennamen. Beim Import baut die App zuerst eine private SQLite-Datenbank und prüft die Sitzungsdateien. Der abschließende Tausch von Bibliothek, Einstellungen und Diagnose verwendet ein lokales Journal und eine SQLite-Commitmarke. Beim nächsten Prozessstart entscheidet diese Marke über Abschluss oder Rücknahme. Dieselbe Aufnahme behält dabei ihre UUID und die Beziehungen zu ihren Cloudkopien.


## Dropbox-Wiederherstellung ab 1.3.0

`DropboxRestoreDialog` startet den lesenden Abruf für ein eingerichtetes Ziel auf dem Bibliotheksexecutor. `DropboxFiles.list` liefert alle Seiten des Unterbaums. `DropboxRestore` liest die gelisteten Dateirevisionen und vergleicht Größe und Dropbox-Blockhash. Für den Katalog berechnet es außerdem den von der Uploadwarteschlange verwendeten SHA-256. Eine lokal vorhandene Aufnahme mit passendem Ordner, Namen und Inhalt behält ihre UUID. Die Veröffentlichung neuer Sitzungen erfolgt per atomarem Verzeichniswechsel, die Zuordnung von Katalog und Cloudpfad in einer SQLite-Transaktion. Neu übernommene Bestätigungen haben `inQueue=0`. Die lokalen Aufnahmedaten erhalten `autoQueued=true`. Bei einem erneuten Lauf kann Audomo nach Unterbrechung zwischen Dateiveröffentlichung und Datenbankbestätigung die vorhandenen Bytes zuordnen. [Blockhash-Spezifikation von Dropbox](https://www.dropbox.com/developers/reference/content-hash)

Bei der Akku-Ausnahme liest das Berechtigungsmenü beim Zurückkehren `PowerManager.isIgnoringBatteryOptimizations` aus. Den Dialog fordert ausschließlich ein Tipp auf den Menüeintrag an. [Android-Dokumentation](https://developer.android.com/training/monitoring-device-state/doze-standby)
