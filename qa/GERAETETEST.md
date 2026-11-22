# Audomo 0.1 · Teststand vom 22.11.2026

## Gerät und Build

| Merkmal | Beobachtung |
|---|---|
| Erstgerät für die Produktabnahme | Samsung Galaxy S24 Ultra, Android 14, One UI 6.1 |
| Tatsächliches Testgerät | Android-Studio-AVD `Galaxy_S24_Ultra_API_34`, `emulator-5556`, Google-APIs-Systemimage |
| System | Android 14, API 34, ARM64-v8a, Modell `sdk_gphone64_arm64`, Patch 05.09.2023, Build `UE1A.230829.050/12077443` |
| Display und Energie | 1440 × 3120 Pixel, 560 dpi. Beim Auslesen 100 % und Energiesparmodus aus. |
| Quellstand der APK | `a9589dfbbee8ad8349e4939701a9f83c2bb94685` |
| Test-APK | `de.audomo`, Version 0.1 (1). minSdk 34, targetSdk 34, compileSdk 37. APK-Signatur v2 geprüft. |
| SHA-256 | `bd989361b6fe27a4889127a711f52b4d40beca10a89600b423573fd8d125d6d8` |
| Reproduzierbarkeit | Zwei saubere Builds, einer direkt aus einem separaten Git-Export, ergaben auf diesem Rechner mit demselben Debugschlüssel dieselbe SHA-256. Auch die per ADB auf dem Gerät gelesene base.apk hat diese Prüfsumme. |
| Werkzeugprüfung | Gradle-Build, neun JVM-Tests und Android Lint erfolgreich. Lint meldet 0 Fehler und 20 Warnungen, darunter übersetzbare UI-Texte und das bewusst auf Android 14 gesetzte Ziel-SDK. |
| Prüfbedingungen | Standardprofil Besprechung, Mikrofon über normalen Android-Systemdialog freigegeben, privater permanenter App-Speicher. Benachrichtigungen später ebenfalls über die Systemabfrage aktiviert. |

## Ausgeführte Prüfungen

„Bestanden“ bezieht sich auf den jeweils beschriebenen Versuch auf diesem Systemimage. Die vollständigen T-Nummern im Übergabepaket enthalten teilweise zusätzliche Hardware-, Dauer- oder Fehlerbedingungen.

| Bezug | Durchführung und beobachtetes Ergebnis | Urteil |
|---|---|---|
| T01 | 20 Sitzungen aus der sichtbaren Aufnahmeoberfläche beendet. Nach jeder Sitzung genau eine neue Ogg-Datei. Core Audio auf macOS dekodierte jede Ausgabe vollständig. Ihre PCM-Framezahl stimmte mit der gespeicherten Framezahl überein. | Bestanden, AVD |
| T02 | Aufnahme gestartet, pausiert, angezeigte Dauer vor und nach einer Wartezeit verglichen, fortgesetzt, erneut pausiert und aus der Pause beendet. Die Anzeige blieb während der Pause stehen. Encoder und endgültige Datei enthielten eine fortlaufende Audioposition. | Bestanden, Kurzablauf |
| T03 | In derselben Sitzung Home gedrückt, Display ausgeschaltet, wieder aktiviert und zur laufenden Aufnahme zurückgekehrt. Die abschließende Datei wurde dekodiert. | Kurzer Hintergrundlauf bestanden. Sechsstundentest mit Zeitmarkern auf dem S24 offen. |
| T06 | 20 weitere kurze Sitzungen mit unterschiedlich verzögertem `am force-stop de.audomo` unterbrochen. Bei erneutem Öffnen entstand jeweils genau eine lesbare Datei mit `recovered=true`. Die wiederhergestellten Dateien wurden separat in PCM dekodiert. Ein weiterer Aufnahmestart erforderte eine neue Bedienhandlung. | Bestanden, AVD-Prozessabbruch |
| T06/T07, beschädigte Seiten | JVM-Tests für abgeschnittene Endseite, falsche CRC, reinen Header, erneut ausgeführte Recovery und bytegleichen Paketinhalt bei intaktem Abschluss. Die beschädigte Testquelle blieb erhalten. | Bestanden. Abrupter Geräteneustart und tatsächlich voller Datenträger offen. |
| T08/T11 | Der AVD meldete einen internen Mikrofoneingang. Die Richtung Hinten ließ sich nicht bestätigen. Die erste Standardaufnahme meldete „Echo nicht steuerbar“ und „Rauschen nicht steuerbar“. | Ersatzpfad nutzbar. Wirksame Kapsel, AEC, NS und akustische Wirkung auf Samsung offen. |
| T10 | Instrumentierungstest mit echtem Android-Opus-Encoder und generiertem Sinussignal. 16, 24 und 48 kHz kombiniert mit 16, 24, 32, 48, 64, 80, 96 und 128 kbit/s. Alle 24 Kombinationen geschrieben, mit Android MediaExtractor geöffnet und in ihrer exakten Endposition sowie Bytegleichheit nach erneuter Recovery geprüft. Unabhängig davon dekodierte Core Audio alle 24 synthetischen Ogg-Dateien mit exakt der erwarteten Framezahl. Die gemessene Tonhöhe lag jeweils innerhalb von 2 Hz um 440 Hz. [Codec-Messwerte](api34/codec-profile.json) | Codec- und Containerprüfung bestanden. Mikrofonpfad und Vergleichsmessungen für jede Kombination offen. |
| T15 bis T18 | Drei Kalenderlogiktests für aktive und tolerierte Termine, halb offene Intervallgrenzen, Priorität, Gleichstand, Zeitzonenoffset und literale Titelersetzung. Zwei Namenstests für Fallback, Zeichenbereinigung und UTF-8-Längengrenze. | Logiktests bestanden. Kalenderprovider mit Testkalendern, Serienterminen und entzogenem Zugriff offen. |
| T19/T21 | Kachel über Audomo und den echten Android-Hinzufügen-Dialog installiert. Aus den Android-Einstellungen über „Aufnehmen“ gestartet. Die Start-Activity schloss sich, die Einstellungen wurden wieder sichtbar. In der Benachrichtigung „Pause“ und „Fortsetzen“ betätigt. Anschließend zeigte die Kachel „Stoppen“. Über diese Kachel beendet, in Audomo erschien „Gespeichert“. Zusätzlich Start und Stopp aus den Schnelleinstellungen mit zuvor beendetem App-Prozess verifiziert. Die Steuerungsmeldungen enthielten ausschließlich generische Aufnahmetexte. | Bestanden, entsperrter AVD. Samsung-Sperrbildschirm offen. |
| T20 | Die Rückmeldungen sind an erste dauerhaft geschriebene Audiopakete bzw. Veröffentlichung der geprüften lokalen Datei gekoppelt. | Fühlbare Muster am physischen Gerät offen. |
| T23/T31/T43 | Dateiliste mit 102 während der Entwicklung entstandenen AVD-Aufnahmen geöffnet. Nach Auswahl der zuletzt gespeicherten Datei Play betätigt. Der Listenplayer zeigte eine fortlaufende Position und das Pausensymbol. | Grundwiedergabe auf API 34 bestanden. 500-Dateien-Test, Vollabnahme von Suche, Player und UI offen. |

## Messdaten und Bildbelege

Das [Laufprotokoll](api34/laeufe.json) enthält pro Sitzung Modus, Größe, dekodierte Framezahl und SHA-256. Für die Abbruchserie erfasst das Skript zusätzlich die monotone Hostzeit vom Startbefehl bis zur Rückkehr des Abbruchbefehls abzüglich der geretteten Audiodauer. In dieser Serie lag die Differenz zwischen 0.254 s und 0.518 s, der Median bei 0.388 s. Diese Größe enthält auch Startlatenz, Mikrofoninitialisierung und ADB-Laufzeit. Eine verlässliche Messung des realen Signalverlusts benötigt ein externes Zeitmarkersignal auf dem Zielgerät.

Das [synthetische Ogg-Testsignal](api34/testsignal-440hz.ogg) aus dem Plattformencoder hat 48 kHz, Mono und die angeforderte Bitrate 48 kbit/s. Der unabhängige Core-Audio-Decoder gab genau 48.317 nutzbare Frames aus, mit 312 Priming-Frames und 331 Frames Endpadding. Diese Datei enthält ausschließlich den vom Instrumentierungstest erzeugten Prüfton.

Screenshots der laufenden App liegen unter [Bereit](api34/bereit.png), [Aufnahme](api34/aufnahme.png), [Pause](api34/pause.png), [Gespeichert](api34/gespeichert.png), [Wiederherstellung](api34/wiederherstellung.png), [Dateiliste](api34/dateiliste.png) und [Listenplayer](api34/listenplayer.png). Das Übergabepaket enthält die unabhängigen Designreferenzen.

## Offene Abnahme

Für den Aufnahmeblock fehlen insbesondere der sechsstündige Zeitmarkerlauf auf One UI 6.1, Verhalten bei Anrufen und Mikrofonkonkurrenz, voller Speicher, unterbrochene Finalisierung beim Geräteneustart, reale Hintermikrofon-/Effektmessungen, Haptik und DND-Wechsel. Ein physisches S24 Ultra war bei der Prüfung nicht mit ADB verbunden. Beim Oberflächenabgleich bleibt unter anderem die Ausblendzeit des Speicherhinweises offen. Für die späteren Blöcke sind unter anderem dauerhafte SAF-Zielwahl, Import, Mehrfachaktionen, Papierkorb, Cloudziele und verifizierte Uploads offen. Der [Funktionsabgleich](ANFORDERUNGSABGLEICH.md) führt den Status pro ID.
