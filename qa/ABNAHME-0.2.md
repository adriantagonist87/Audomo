# Abnahme Audomo 0.2

Stand 22.11.2026. API 34, ARM64. Die normale Referenzinstanz war Galaxy_S24_Ultra_API_34. Für Prozessabbrüche und Neustart lief ein separates AVD mit demselben Systemimage und derselben Displaykonfiguration. Das persönliche SM-S928B wurde während der Radioaufnahme nicht für Fehlerinjektionen verwendet.

16 der 44 Fälle haben einen PASS für den angegebenen Umfang, 28 enthalten offene Teilprüfungen. Ein ausdrücklich begrenzter PASS bezieht sich auf die genannte Umgebung. Die Liste enthält alle Kernfälle des Übergabepakets. [40 Aufnahmeversuche](api34/aufnahme-serie-0.2.json) · [Neustart und Mikrofonsperre](api34/unterbrechungen-0.2.json) · [Gestaltungsabgleich](MOCKUPABGLEICH.md)

| Fall | Stand | Befund |
|---|---|---|
| T01 Start und Stopp | PASS AVD | 20 reguläre Sitzungen, genau eine Ogg-Datei je Sitzung, Dekoder- und Metadatenframezahl identisch. |
| T02 Pause und Fortsetzen | PASS AVD | Mehrere Pause-/Fortsetzen-Zyklen, stehender Framezähler und Stopp aus Pause. |
| T03 Lange Hintergrundaufnahme | PASS 30-Minuten-Ersttest | Physisches S24 mit 0.1.3. 29:59,68 vollständig dekodiert, 9.001 fortlaufende Ogg-Seiten und alle 1.800 Messfenster mit Signal. Sechsstunden-Zeitmarkerlauf aus der Übergabe offen. |
| T04 Konkurrierende Befehle | TEILPRÜFUNG | Doppelte Start-, Pause-, Fortsetzen- und Stoppaufrufe hinterließen genau eine Datei. Wechsel aller drei Bedienoberflächen am gesperrten S24 offen. |
| T05 Audiounterbrechung | TEILPRÜFUNG | Mikrofon-Privatschalter während echter AVD-Aufnahme. Fehler sichtbar, vorherige Frames gerettet. Anruf und konkurrierende Telefon-App am S24 offen. |
| T06 Prozessabbruch und Recovery | PASS AVD | 20 Prozessabbrüche, jede Sitzung einmal gerettet und dekodiert. |
| T07 Neustart und voller Speicher | TEILPRÜFUNG | Aufnahme nach Neustart mit 278.088 Frames gerettet. Uploadauftrag nach Neustart bei WLAN-Rückkehr verifiziert. Voller Speicher und Abbruch während Finalisierung offen. |
| T08 Mikrofonroute | TEILPRÜFUNG | S24 meldete internes Mikrofon. Konkrete hintere Kapsel und reproduzierbarer Richtungswechsel offen. |
| T09 Verstärkung | TEILPRÜFUNG | Digitale Multiplikation bei +5 dB und Clipping für positive und negative PCM-Werte geprüft. Akustischer Referenzpegel am S24 offen. |
| T10 Opus und Parameter | PASS Codec | 24 Encoderkombinationen mit synthetischem PCM auf S24 und AVD. Unabhängige Dekodierung im Codecbericht, Radiodatei separat mit Core Audio dekodiert. |
| T11 Audioeffekte | TEILPRÜFUNG | Standardprofil meldete Echo ein und Rauschen aus am S24. Akustische Vergleichsreihen und Kontrollverlust offen. |
| T12 Profile | PASS Profilmodell | Standardprofil direkt editiert, eigenes Profil angelegt und Einstellungen neu geladen. Alle drei Ursprungsbitraten geprüft. |
| T13 Benachrichtigungstöne | TEILPRÜFUNG | Ohne Zugriff sichtbarer Hinweis, mit Zugriff Android-Unterbrechungsfilter während der Sitzung aktiv und nach Stopp zurückgesetzt. Fremde Tonquelle und manueller DND-Wechsel auf One UI offen. |
| T14 Kalenderzugriff | TEILPRÜFUNG | Android-Kalenderprovider mit zwei lokalen Testkalendern und geänderter Priorität. Ablehnung bzw. Entzug im laufenden Bedienablauf auf One UI offen. |
| T15 Terminzuordnung | PASS Provider | Zwei Android-Testkalender. Wiederholung, Einzeltermin, Absage, Ganztag und verschobene Serienausnahme am alten und neuen Zeitpunkt geprüft. |
| T16 Überschneidung und Toleranz | PASS Kernlogik | Überlappung, deterministische Priorität und Toleranz im JVM-Test. Nachlauf gegen Android-Provider getestet. |
| T17 Schema und Zeit | TEILPRÜFUNG | Vorlagen, Literalersetzung und Offset im JVM-Test. Interaktive Mitternachts- und DST-Sequenz offen. |
| T18 Namen und Fallback | TEILPRÜFUNG | Fallback, reservierte Zeichen, Unicode-Länge und geschützte Endung getestet. Tastaturfälle auf One UI offen. |
| T19 Quick Settings | TEILPRÜFUNG | Früherer echter Systemkacheltest im AVD. Vollständige Wiederholung am Samsung-Sperrbildschirm offen. |
| T20 Haptik | TEILPRÜFUNG | Fühlbare Vorschauen vom Nutzer bestätigt, konfigurierte 500-ms- und 300/200-ms-Sequenzen im Systemprotokoll. Weitere DND-Kombinationen offen. |
| T21 Notification und Sperrbildschirm | TEILPRÜFUNG | Systemnotification und Steuerung am AVD in früherem Test. Gesperrtes S24 und Uploadfehler bei Sperre offen. |
| T22 Speicherort und Ordner | TEILPRÜFUNG | Echter Android-Ordnerdialog mit persistenter Baumfreigabe. Schreiben, SHA-256-Rücklesen, Rename und Restore über externen Dokumentprovider. Langsamer Provider und Entzug offen. |
| T23 Dateiliste | PASS AVD | 500 Testdateien mit unterschiedlichen Endungen, Größen und Dauern. Für jeden der fünf Sortierschlüssel alle 499 Nachbarpaare auf- und absteigend geprüft. Suchtreffer, leeres Ergebnis und Zurücksetzen geprüft. |
| T24 Umbenennen, Kopieren, Verschieben | TEILPRÜFUNG | Umbenennen, Kopieren, Verschieben, Namenskonflikt und SHA-256-Gleichheit geprüft. Externer Platzmangel bei Kopie offen. |
| T25 Teilen und extern öffnen | PASS S24 und AVD | Externes Test-APK empfängt VIEW, SEND und SEND_MULTIPLE über FileProvider. Beide Formate bytegleich zurückgelesen und mit stummem Android-MediaPlayer bis zum Ende abgespielt. |
| T26 Auswahl und Schnellaktionen | TEILPRÜFUNG | F2-Aktionen sichtbar, zwei ausgewählte IDs und Leeren der Auswahl nach Filterwechsel im Bedienlauf bestätigt. |
| T27 Papierkorb | TEILPRÜFUNG | Papierkorb, Restore nach erneutem Laden, Endgültig-Löschen-Schutz, standardmäßig inaktive 30-Tage-Regel und altersabhängiges Aufräumen geprüft. |
| T28 Mediensuche | TEILPRÜFUNG | Anlegen und Entfernen von .nomedia im freigegebenen Android-Ordner geprüft. Erneute tatsächliche Medienindexierung auf One UI offen. |
| T29 Metadaten | PASS lokal | Bei Import, Rename, Copy, Move, Trash und Restore blieben Audiobytes und gespeicherter Zeitpunkt erhalten. |
| T30 Import | PASS S24 und AVD | MP3/Ogg kopiert, vollständig dekodiert, Bytegleichheit geprüft. Duplikat, nicht dekodierbare Quelle und beschädigte Ogg-Seite abgelehnt. |
| T31 Listenplayer | TEILPRÜFUNG | Reale Listenwiedergabe, Pause, Grenzen bei 15-Sekunden-Sprüngen, Tempowechsel und Stille-Option im UI-Test. Akustischer Stillevergleich offen. |
| T32 Zieladapter | TEILPRÜFUNG | WebDAV/HTTPS, FTP, explizites FTPS und SFTP gegen lokale Server erfolgreich. Dropbox-PKCE und mehrteilige Anfragefolge mit simuliertem Transport auf Android. Echter Dropbox-Zugang bleibt offen. |
| T33 Warteschlange | TEILPRÜFUNG | Manueller Upload über Android-Job. Automatischer Abschluss zweimal aufgerufen, genau ein persistenter Auftrag. Nach Systemneustart und WLAN-Rückkehr Remotehash bestätigt. Abbruch während einer langen Übertragung offen. |
| T34 Netzregeln | TEILPRÜFUNG | QA-AVD mit deaktiviertem WLAN und aktivierter WLAN-Bindung. Auftrag wartete, blieb über Neustart erhalten und übertrug nach WLAN-Rückkehr. Wechsel mitten im Transfer auf One UI offen. |
| T35 Integrität | PASS lokale Protokollserver | Bei allen vier lokalen Protokollen Rücklese-Verifikation, Schreibkonflikt, Ersetzungsprüfung und Schutz vor Löschen einer fremden Prüfsumme. Echte Dropbox separat unter T32. |
| T36 Aufbewahrung | PASS lokaler FTP-Server | Standard behält die Datei. Bei eingeschaltetem Aufräumen blieb die Datei im Konflikt aktiv. Erst nach freigegebenem Ersetzen und SHA-256-Abgleich im Papierkorb. |
| T37 Namenskonflikt | PASS FTP-Warteschlange | Auslassen, separater Zielname und explizites Ersetzen gegen denselben Namenskonflikt geprüft. Unveränderte Fremddatei nach alternativem Namen bestätigt. Weitere Provider und große Schrift offen. |
| T38 Remote-Dateiverwaltung | TEILPRÜFUNG | Verifizierte FTP-Cloudkopie nach lokaler Umbenennung am neuen Ort gelesen, früherer Pfad entfernt. Getrennter Löschauftrag entfernt die bestätigte Remote-Kopie. Parallelzugriffe fremder Clients offen. |
| T39 Geheimnisse | TEILPRÜFUNG | Android-Keystore AES-GCM, Schemavalidierung beim Einstellungsimport, Testgeheimnis weder in Einstellungsdatei noch im Klartextspeicher. Vollständige Kontrolle von systemweiten Logs und Sperrbildschirm offen. |
| T40 Einstellungen | PASS Kernlogik | JSON-Rundlauf für Profil, Kalenderregeln und Schema, Ablehnung unbekannter Version, Kalenderzuordnung bei neuer Installation zurückgesetzt. |
| T41 Diagnose | TEILPRÜFUNG | Inline-Aufnahmefehler bei Mikrofonsperre beobachtet. Uploadfehler-Notification samt manuellen Aktionen auf One UI offen. |
| T42 Werbung und Tracking | TEILPRÜFUNG | Laufzeitabhängigkeiten und Manifest inventarisiert. Internetzugriff für ausdrücklich konfigurierte Uploads. Vollständiger Netzwerk-Mitschnitt bei Alltagsnutzung offen. |
| T43 Visuelle Abnahme | TEILPRÜFUNG | [H1](api34/einstellungen.png), [Liste mit 500 Einträgen und vier Uploadzuständen](api34/liste-500.png), ausgeklappter Player, [F2](api34/dateiaktionen.png) und [Mehrfachauswahl](api34/mehrfachauswahl.png) als reale Screenshots geprüft. Zuordnung aller 52 Beschlüsse liegt vor. |
| T44 Darstellung und Bedienbarkeit | TEILPRÜFUNG | Neun Einstellungswege über die Oberfläche aufgerufen, einschließlich Diagnosefilter und Protokollauswahl. H1 und Einstellungs-Export bei 150 % Systemschrift über Scrollen erreichbar. TalkBack, gesamte Oberfläche und lange Konflikttexte auf One UI offen. |

## Durchgeführte Läufe

| Umgebung | Prüfumfang und Ergebnis |
|---|---|
| SM-S928B, Android 14 | Update über vorhandene Installation. Sieben Instrumentierungstests mit synthetischem Audio bestanden. Import und Dateioperationen, beschädigte Metadaten, Papierkorbfristen, geschützter Einstellungsexport, 24 Codec-Kombinationen, Wiedergabe über URI-Freigaben im zweiten APK und lokaler Dropbox-Anfragetest. Einstellungsübersicht und Speicherunterseite über die reale Oberfläche geöffnet. |
| Galaxy_S24_Ultra_API_34, ARM64, 1440 × 3120 | 15 Instrumentierungstests bestanden. Zusätzliche Providerprüfungen für Kalender und Dokumentordner, Bibliothek mit 500 Dateien, die lokale Uploadwarteschlange und die vier Transferprotokolle. |
| Getrennte QA-AVD, gleiches API-34-Image | 20 normale Abschlüsse und 20 Abbrüche. Weitere Prüfungen umfassten Doppelauslösung, Pause/Fortsetzen, DND-Zugriff, Mikrofonentzug und Neustart während der Aufnahme. Separater Upload-Neustarttest mit WLAN-Bedingung. [Messwerte zum Upload-Neustart](api34/upload-neustart-0.2.json) |
| Gradle | Zehn JVM-Tests bestanden. Android Lint meldete 0 Fehler und 36 Warnungen, darunter zwei Warnungen zu TrustManager-Hilfsklassen der Commons-Net-Abhängigkeit. Der FTPS-Pfad setzt einen TrustManager aus dem Android-Vertrauensspeicher und aktiviert die Hostnamenprüfung. Unbekannte Test-CA ohne expliziten Testtransport abgewiesen. |

### Radioversuch auf dem S24

Der 30-Minuten-Versuch lief vor der Aktualisierung mit Version 0.1.3. Die Aufnahme blieb auf dem Telefon. Zur Messung wurde eine temporäre lokale Kopie vollständig dekodiert. Sie enthielt 86.384.640 Mono-Frames bei 48 kHz, eine gültige Ogg-Endseite und keine durchgehend stillen Einsekundenfenster. Eine unkalibrierte Radiosendung gibt keinen Nachweis für die Identität der Mikrofonkapsel oder für subsekundengenaue Lücken. [Anonymisierte Messwerte](api34/radio-30min-s24.json)

### Cloudprüfung

Alle vier Testserver liefen auf der Loopbackadresse des Mac. Die App auf dem Emulator übertrug synthetische Daten. Beim Wiederholen über den NAT-Alias 10.0.2.2 traten auch Verbindungszeitüberschreitungen auf. Der abschließende Lauf nutzte deshalb ADB-Portweiterleitungen auf die Loopbackadresse des Emulators. Alle 15 Instrumentierungstests bestanden. Die temporären Weiterleitungen wurden anschließend entfernt. WebDAV und FTPS wurden einmal mit unbekannter und einmal mit der nur im Test eingesetzten Zertifizierungsstelle angesprochen. Der SFTP-Test verwendete den festgelegten öffentlichen Hostschlüssel. Zu den Prüfungen gehörten Ordner mit Umlaut, Erstübertragung, Auslesen, Namenskonflikt, überprüftes Ersetzen und Entfernen. Bei FTP und SFTP lassen sich konkurrierende Änderungen durch fremde Clients zwischen der letzten Prüfung und der Serveroperation nicht allgemein atomar ausschließen.

Dropbox blieb ohne Kontoanmeldung. Auf dem S24 und im Emulator prüfte ein vollständig lokaler Transport den PKCE-Challenge, den Token-Refresh-Aufruf sowie Start, Offset und Abschluss einer mehrteiligen Upload-Anfrage über 16 MiB. Eine Zustimmung im Browser, der Austausch eines echten Codes und die Serverantworten von Dropbox sind offen. Die eingebundene API folgt der Anbieterdokumentation. [Dropbox OAuth](https://developers.dropbox.com/oauth-guide) · [Dropbox-Dateizugriff](https://developers.dropbox.com/dbx-file-access-guide)

### Reproduzieren

Die Geräteklassen stehen unter `app/src/androidTest/java/de/audomo/`. Für die vier lokalen Protokolltests `qa/protocol_servers.py` in einer Python-Umgebung mit pyftpdlib, pyOpenSSL, cryptography und Paramiko mit einem neuen temporären Verzeichnis starten. Dessen synthetische Verbindungsdaten aus `client.json` nur ins private Cacheverzeichnis des QA-Emulators als `qa-protocols.json` übertragen. Für die alternative Weiterleitung den Server mit `--adb-reverse` starten. Für jeden Kontrollport aus `ports` und jeden Datenport aus `passivePorts` des Testprofils auf dem Emulator `adb reverse tcp:PORT tcp:PORT` einrichten und nach dem Lauf mit `adb reverse --remove tcp:PORT` entfernen. Die Instrumentierung für `ProtocolDeviceTest` und `UploadQueueDeviceTest` mit `-e localServers true` ausführen, anschließend den Cacheeintrag und das temporäre Serververzeichnis entfernen. `qa/upload_neustart.py` begrenzt den Neustarttest auf die separate Audomo-QA-AVD. Für den SAF-Test ist eine echte persistente Freigabe von `Documents/Audomo-QA-020` Voraussetzung. `RadioDeviceTest` benötigt eine ausdrücklich gestartete Messung mit `-e radioMinutes 30`.

### Auslieferung

`Audomo-0.2-API34-debug.apk`, Versionscode 5, Paket `de.audomo`, minSdk 34, targetSdk 34. Der Android-Signaturprüfer bestätigt Schema v2. Die installierte Basis-APK auf dem S24 und die ausgelieferte Datei sind bytegleich.

```text
SHA-256  503c9ada73bffd675349b2ee8c2fbc8253e3c1441c915c428e28b20976e0f83a
```
