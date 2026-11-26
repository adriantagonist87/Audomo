# Audomo bedienen

## Erste Aufnahme

Im Reiter „Aufnehmen“ startet der runde, gedeckt grüne Aufnahmepunkt die Aufnahme. Beim ersten Mal fragt Android nach dem Mikrofonzugriff. Während der Aufnahme steuern Quadrat und Pause die Sitzung. Aus dem pausierten Zustand setzt Play dieselbe Datei fort. Nach einem bestätigten Stopp steht die Datei unter „Wiedergeben“ bereit. Eine unterbrochene Sitzung wird beim nächsten App-Prozessstart geprüft. Die App startet dabei das Mikrofon ausschließlich auf Nutzeraktion.

Während der Aufnahme und in der Pause zeigt die Android-Statusleiste das Audomo-A mit Schallwelle und Punkt. Die aufgeklappte Aufnahmesteuerung verwendet den lila Farbakzent. Den Mikrofonindikator für den Datenschutz zeigt Android separat an.

Die drei Standardprofile verwenden Opus/Ogg, Mono, 48 kHz, +5 dB und 48, 80 oder 32 kbit/s. Über „Einstellungen“ → „Aufnahmeprofile“ oder „Profil“ im Aufnahmedashboard öffnet sich die Profilverwaltung. Das Dropdown „Aktives Profil“ wählt die Einstellungen für die nächste Aufnahme. Jeder Eintrag zeigt Abtastrate und Bitrate. Das Häkchen kennzeichnet die aktive Wahl. Im Editor stehen bei Echo- und Rauschunterdrückung „Standard“, „Ein“ und „Aus“ direkt nebeneinander. „Standard“ übernimmt die Vorgabe des Geräts. „Neues Profil“ übernimmt die Klangwerte des aktiven Profils als Ausgangspunkt. „Speichern“ aktiviert das neue oder bearbeitete Profil. Eigene Profile lassen sich umbenennen und löschen. Beim Löschen des aktiven Profils wechselt Audomo zu „Besprechung“. Angaben zur physischen Kapsel sind geräteabhängig. Der Aufnahmebildschirm zeigt die gemeldete Route und kennzeichnet die gewünschte Richtung. Die Diagnose zeigt den über Android ermittelten Effektzustand. [Profilverwaltung](../qa/api34/aufnahmeprofile-1.1.4.webp) · [Effekte einstellen](../qa/api34/profileffekte-1.1.4.webp)

Das Zahnrad oben rechts öffnet die Einstellungen. Unter „Berechtigungen“ stehen Mikrofon, Kalender, Benachrichtigungen und der optionale Nicht-stören-Zugriff zusammen. Ein Tipp auf „Nicht-stören-Zugriff“ öffnet die Android-Freigabe. Dort Audomo auswählen und den Zugriff erlauben. Beim Zurückkehren zeigt Audomo erteilte Rechte in Grün als „Erlaubt“ und fehlende Rechte in Rot als „Nicht erlaubt“. Der optionale Nicht-stören-Zugriff erlaubt Audomo die Steuerung einer eigenen Aufnahmeregel. [Berechtigungen in Audomo](../qa/api34/berechtigungen-1.1.4.webp)

Unter „Einstellungen“ → „Kalender und Dateinamen“ → „Dateiname und Ordner“ lassen sich Dateiname und Ablage für neue Aufnahmen unabhängig wählen. „Vorlagen“ → „Kalendername“ verwendet den Anzeigenamen des Kalenders. „Baustein einfügen“ kombiniert `{datum}`, `{uhrzeit}`, `{termin}` und `{kalender}` mit eigenem Text. Die Vorschau zeigt den Pfad mit und ohne passenden Termin. Über „Kalender auswählen“ lassen sich Kalender freigeben, aktivieren und priorisieren.

„Unterordner je Kalender“ legt den Kalenderordner unter dem gewählten Basisordner an und verwendet ihn bei weiteren Aufnahmen erneut. Beim Kalender „Privat“ und der Vorlage „Kalendername“ entstehen etwa `Privat/Privat.ogg` und `Privat/Privat (2).ogg`. Ohne zugeordneten Termin landet die Datei direkt im Basisordner. Fehlende Kalender- oder Terminbausteine erhalten den Ersatztext „Aufnahme“. Ein direkt vor der Aufnahme eingegebener Name gilt auch bei automatischer Ordnerwahl. Als weitere Ablageoptionen stehen ein fester Ordner und der jeweils aktuelle Bibliotheksordner zur Verfügung. Bestehende Installationen behalten zunächst ihre bisherige Vorlage und die Ablage im aktuellen Bibliotheksordner.

Unter „Einstellungen“ → „Schnellzugriff und Vibration“ richtet „Kachel hinzufügen“ die Schnelleinstellungskachel ein. Audomo zeigt an, ob Android die Kachel hinzugefügt hat, ob sie bereits vorhanden ist oder ob das Hinzufügen ausblieb. Die Statuszeile im Menü merkt sich die letzte Systemmeldung. Ein Tipp auf die Kachel startet oder stoppt die Aufnahme. Langes Drücken bringt Audomo in den Vordergrund. [Kachelbestätigung](../qa/api34/kachel-bestaetigt-1.1.5.webp)

„Verknüpfung zum Startbildschirm hinzufügen“ fordert das Symbol „Aufnahme“ beim Launcher an. Nach dem Anheften startet ein Tipp die Aufnahme, der nächste beendet und speichert sie. Das funktioniert auch bei pausierter Aufnahme. Kachel und Verknüpfung steuern dieselbe Sitzung. Beide benötigen die Mikrofonfreigabe. Das Dashboard bleibt geschlossen. Die Aufnahmebenachrichtigung und der Android-Mikrofonindikator begleiten die Aufnahme. [Schnellzugriff einrichten](../qa/api34/schnellzugriff-1.1.5.webp)

Unter „Vibration“ hat jedes Ereignis eine Taste „Testen“. Ein langer Impuls dauert 500 ms. Zwei oder drei kurze Impulse dauern jeweils 300 ms mit 200 ms Pause dazwischen. Auf dem Galaxy S24 müssen Benachrichtigungen für Audomo erlaubt sein, damit diese Muster fühlbar sind. Bei gesperrter Freigabe führt „Freigabe öffnen“ zur Android-Einstellung.

„Wiedergeben“ öffnet die lokale Liste. Über das Sortiersymbol lassen sich Feld und Reihenfolge direkt wählen, etwa „Datum“ und „Neueste zuerst“. Die beiden aktiven Einträge sind hervorgehoben. Einen Eintrag auswählen und anschließend Play drücken. Die Dateiaktionen ermöglichen Teilen, externes Öffnen und Exportieren über den Android-Dateidialog. Originale liegen im privaten dauerhaften App-Speicher. Eine Deinstallation entfernt diesen Speicher. Zum Aufbewahren außerhalb der App „Datei exportieren“ verwenden.

### Besprechungen mit Kopfhörern anhören

Bei „Play“ prüft Audomo den Medienausgang. Über Bluetooth-Audio, kabelgebundene Kopfhörer und USB-Headsets beginnt die Wiedergabe direkt. Für den Handylautsprecher oder einen unbekannten Ausgang fragt Audomo „Ohne Kopfhörer abspielen?“. „Trotzdem abspielen“ gilt für diesen Start. „Abbrechen“, Android-Zurück und ein Tipp außerhalb lassen die Aufnahme stumm. Auch nach einer Pause wird der Ausgang erneut geprüft. [Rückfrage im Player](../qa/api34/kopfhoerer-rueckfrage-1.1.1.webp)

Unter „Einstellungen“ → „Wiedergabe“ → „Audioausgang“ sperrt „Nur Bluetooth“ die Lautsprecher-Ausnahme. In der Android-Medienausgabe muss ein Bluetooth-Audiogerät für Medien ausgewählt sein. Auch ein Bluetooth-Lautsprecher oder eine Freisprechanlage kann diesen Ton wiedergeben. Bricht die geschützte Verbindung ab, pausiert der Player. Nach dem erneuten Verbinden entscheidet ein Tipp auf Play über das Fortsetzen.

## Einstellungen und Dateien

### App-Sperre

Öffne am Zahnrad **App-Sperre → App beim Öffnen sperren**. Bestätige die Änderung im Android-Systemdialog. Zur Auswahl stehen die starke Biometrie deines Geräts, etwa der registrierte Fingerabdruck, und deine eingerichtete Android-Displaysperre (PIN, Muster oder Passwort). Ohne sichere Displaysperre führt „Gerätesicherheit öffnen“ zur Einrichtung. Zum Ausschalten ist dieselbe Bestätigung erforderlich. [Ansicht der App-Sperre](../qa/api34/app-sperre-1.1.8.webp)

Die App fragt beim Öffnen, nach dem Wechsel zu einer anderen App und nach dem Ausschalten des Displays erneut. Innerhalb von Audomo kannst du zwischen Reitern und Einstellungen wechseln. Auch der Weg aus einer Benachrichtigung zu den Auftragsdetails führt über die Sperre.

Ein Tipp auf die angeheftete Verknüpfung „Aufnahme“ oder die Schnellkachel startet oder beendet weiterhin die Aufnahme. Für eine Schnellaufnahme direkt in Audomo brich den Android-Entsperrdialog ab und tippe auf der Sperrseite auf „Aufnahme starten / stoppen“. Ein langer Druck auf die Kachel öffnet die geschützte App. Die Sperre gilt für die Audomo-Oberfläche. Bereits exportierte Dateien und Cloudkopien unterliegen den Zugriffsregeln ihres Speicherorts.

Ist auch das Telefon gesperrt, führt Android vor dem Start über die Schnellkachel durch die Geräteentsperrung. Eine laufende Aufnahme lässt sich direkt mit der Kachel stoppen, sofern die Schnelleinstellungen auf dem Sperrbildschirm erreichbar sind.

### Dateiverwaltung

Das Zahnrad ist in allen drei Reitern an derselben Stelle. Die Kategorie „Wiedergabe“ enthält den Audioausgang, Tempo, Sprungintervall und das Überspringen stiller Passagen. Diagnose und Einstellungsdateien stehen unter „Sicherung und Diagnose“. Android-Zurück oder der Pfeil oben führt in die vorherige Menüebene. [Einstellungsübersicht](../qa/api34/einstellungen-1.1.4.webp)

Unter „Dateien und Ordner“ → „Speicher und Import“ lässt sich ein Android-Dokumentordner freigeben. Audomo sichert eine Aufnahme zuerst im privaten Speicher, kopiert sie in den ausgewählten Ordner und liest die Kopie zur SHA-256-Prüfung zurück. Bei einem Zugriffsfehler bleibt die private Sicherung erhalten.

„Aufnehmen“ ist die Startansicht. Beim seitlichen Wischen folgt der Inhalt deinem Finger. Logo, Zahnrad und Reiternamen behalten ihren Platz. Der lavendelfarbene Unterstrich wandert zwischen den Reitern mit. Ziehst du ein Stück zur Seite und wieder zurück, bleibt die bisherige Seite geöffnet. Ein Tipp auf den Reiternamen springt direkt dorthin. In der Warteschlange die Wischgeste auf der Reiterleiste, der Überschrift oder auf freier Fläche beginnen. Auf einem Auftrag gelten dessen Wischaktionen.

Android-Zurück folgt den tatsächlich besuchten Ansichten. Wer von „Aufnehmen“ direkt auf „Warteschlange“ tippt und dann zu „Wiedergeben“ wischt, kommt mit Zurück erst zur Warteschlange und danach zu „Aufnehmen“. Ein offener Player, eine Mehrfachauswahl oder eine Suche wird zuerst geschlossen. Aus dem Papierkorb geht es zurück zur Bibliothek oder zu den Einstellungen, je nach Einstieg.

Unter „Aufnehmen“ öffnet ein Tipp auf „Aktueller Kalendertermin“ die Tagesansicht der ausgewählten Kalender. Gleichzeitige Termine stehen nebeneinander, ganztägige Termine oben. Ein Tipp auf einen Termin übernimmt ihn für die nächste Aufnahme. Der Dateiname folgt der eingestellten Vorlage. Fehlt darin der Baustein für den Termintitel, hängt Audomo den ausdrücklich gewählten Titel an. Bei „Unterordner je Kalender“ bestimmt der gewählte Termin auch den Ordner. „Abbrechen“ und Android-Zurück behalten den bisherigen Entwurf bei. „Automatisch zuordnen“ hebt eine Auswahl vor dem Aufnahmestart auf.

Ein Tipp in der Bibliothek öffnet die Schnellaktionen und den Player. Ein kurzer Tipp außerhalb seiner Bedienelemente schließt den Player. Der erste Tipp wird dabei zum Schließen verwendet. Auch Auswahl- und Bestätigungsdialoge lassen sich durch Antippen des abgedunkelten Bereichs abbrechen. Langes Drücken aktiviert die Mehrfachauswahl. Das Stapelsymbol neben der Anzahl markiert alle Aufnahmen des aktuellen Ordners oder alle Suchtreffer, auch außerhalb des Bildschirms. Sind alle markiert, hebt ein weiterer Tipp die Auswahl auf. „Abbrechen“ beendet den Auswahlmodus.

Dateien werden zuerst in den Papierkorb gelegt. Die 30-Tage-Bereinigung wird separat aktiviert. Beim Import werden Quelldateien unverändert kopiert. Der Einstellungs-Export enthält keine Audioinhalte, Zugangscodes oder Passwörter.

Unter „Einstellungen“ → „Dateien und Ordner“ → „Ordner verwalten“ lassen sich Ordner und Unterordner anlegen, umbenennen, verschieben oder löschen. Beim Löschen rückt der Inhalt in den übergeordneten Ordner. Die Auswahl für neue Aufnahmen und der Kalender-Basisordner folgen der Änderung. Vorhandene Cloudkopien werden auf Wunsch ebenfalls verschoben. Namenskollisionen werden vorab angezeigt.

In „Wiedergeben“ zeigt die rechte Spalte die Speicherorte jeder Aufnahme. Antippen öffnet die zugehörigen Dienste und Pfade. Eine bestätigte Cloudkopie bleibt nach der lokalen Papierkorbleerung im Katalog und lässt sich über „Lokal wiederherstellen“ zurückholen. Beim Update können ältere bestätigte Aufträge auch ohne lokale Sitzungsdaten übernommen werden. Dann bleibt der ursprüngliche Aufnahmezeitpunkt unbekannt.

### Aufnahme umbenennen

In „Wiedergeben“ eine Aufnahme lange drücken. Sobald „1 ausgewählt“ erscheint, unten auf „Umbenennen“ tippen. Bei einer lokalen Aufnahme steht „Umbenennen“ auch an erster Stelle im Menü „Mehr“. Ein kurzer Tipp auf die Aufnahme öffnet die Zeilenaktionen mit dem Stiftsymbol oder bei einer reinen Cloudaufnahme die „Speicherorte“. [Ansicht im Menü „Mehr“](../qa/api34/umbenennen-mehr-1.0.3.webp)

Den neuen Dateinamen eingeben und die gewünschten Speicherorte anhaken. „Auf diesem Gerät“ gilt auch für die von Audomo verwaltete Kopie im freigegebenen Android-Ordner. Jede Cloudkopie lässt sich einzeln auswählen. Ordner und Dateiendung bleiben erhalten. Bei einem geplanten Erstupload kann der neue Name schon vor der Übertragung festgelegt werden.

Die lokale Änderung ist sofort sichtbar. Eine Cloud-Umbenennung erscheint in der Warteschlange. Audomo prüft den Dateiinhalt am neuen Pfad und übernimmt den Namen nach der Bestätigung. Ist der Name in der Cloud belegt, bleiben beide Dateien erhalten. In den Auftragsdetails kann man den bisherigen Cloudnamen behalten oder einen freien Namensvorschlag verwenden.

Nach Änderungen außerhalb von Audomo in „Ordner verwalten“ den „Android-Ablageort abgleichen“ oder „Cloudpfade abgleichen“ wählen. Der Android-Abgleich durchsucht den freigegebenen Dokumentordner. Der Cloudabgleich prüft den gewählten Dienst unter dessen Basisordner. Für verschobene Kopien übernimmt Audomo einen neuen Pfad erst nach eindeutiger Zuordnung anhand der Prüfsumme. Fehlende, veränderte oder mehrdeutige Cloudkopien bleiben mit ihrem zuletzt bekannten Pfad sichtbar.

Unter „Wiedergeben“ eine Aufnahme lange drücken, weitere Dateien markieren und „Cloud Upload“ antippen. In „Einstellungen“ → „Cloud und Uploads“ → „Bestehende Aufnahmen hochladen“ lassen sich Aufnahmen aus mehreren Ordnern gemeinsam auswählen. Bei mehreren verbundenen Diensten lassen sich mehrere Ziele markieren und mit „Einreihen“ gemeinsam beauftragen. Den Fortschritt zeigt die Upload-Warteschlange. Aufnahmen im Papierkorb sind von der Auswahl ausgenommen.

## Warteschlange

„Offene Aufträge“ zeigt, welche Uploads noch laufen, warten oder eine Entscheidung brauchen. Sind nur erledigte Einträge gespeichert, steht hier „Keine offenen Aufträge“. „Upload hinzufügen“ öffnet die Dateiauswahl. Pro Aufnahme und Uploadziel entsteht ein eigener Auftrag.

Über den Filter oben lässt sich der Ausschnitt wechseln. Die Zahl daneben zählt die angezeigten Aufträge. In einer leeren Ansicht erscheint nur die zum Filter passende Meldung.

| Ansicht | Welche Aufträge erscheinen? |
|---|---|
| Offene Aufträge | Wartende und laufende Übertragungen, Fehler, Konflikte und Cloudkopien mit Prüfbedarf |
| Wartend | Auf den Start wartende Aufträge einschließlich zeitverzögerter Uploads |
| Zu prüfen | Fehler, Namenskonflikte und gemeldete Probleme mit einer Cloudkopie |
| Alle Aufträge | Der ganze Verlauf, einschließlich erfolgreich beendeter, abgebrochener und übersprungener Aufträge |

Die App merkt sich die Filterwahl. Der Hinweis „Uploads prüfen“ auf der Aufnahmeseite öffnet direkt „Zu prüfen“. Beim nächsten gewöhnlichen Aufruf des Reiters gilt wieder der selbst gewählte Filter. Laufende Übertragungen zeigen ihren Fortschritt. Wartet ein Auftrag auf eine Startzeit oder auf WLAN, steht das in seiner Zeile. [Leere Arbeitsliste](../qa/api34/warteschlange-offen-1.1.7.webp) · [Aufträge mit Prüfbedarf](../qa/api34/warteschlange-pruefen-1.1.7.webp)

Ein Wisch nach rechts setzt einen wartenden oder fehlgeschlagenen Auftrag an die Spitze. Die Reihenfolge gilt auch nach einem Neustart. Die eingestellte Verzögerung und die WLAN-Regel gelten weiter. Ein Wisch nach links entfernt den einzelnen Auftrag direkt. Läuft die Übertragung bereits, bricht Audomo sie bis zur Bestätigung am Ziel ab. Hat die Veröffentlichung bereits begonnen, prüft Audomo das Ergebnis und behält eine bestätigte Cloudkopie im Katalog.

„Auswählen“ oder langes Drücken startet die Mehrfachauswahl. Tippen markiert einzelne Aufträge. Das Stapelsymbol markiert alle Aufträge des aktuellen Filters oder hebt die Auswahl auf. „An die Spitze“ zieht die geeigneten Aufträge in ihrer bisherigen Reihenfolge vor. „Entfernen“ fragt bei der Auswahl nach Bestätigung. Im Menü oben rechts sind die Uploadziele erreichbar. In „Alle Aufträge“ lässt sich die Warteschlange nach Bestätigung leeren. Enthält sie zugleich beendete und offene Aufträge, lassen sich gezielt die beendeten entfernen. Cloudkopien mit Prüfbedarf zählen als offen. Aufnahmen und bestätigte Cloud-Speicherorte bleiben erhalten.

Antippen öffnet die Auftragsdetails mit Zielpfad, Fehlerursache und den für diesen Auftrag verfügbaren Aktionen. „Jetzt freigeben“ hebt die Aufnahmeverzögerung auf. „Überspringen“ nimmt den Auftrag aus der automatischen Verarbeitung. „Auftrag abbrechen“ beendet auch eine laufende Übertragung, solange der Dienst den endgültigen Zielinhalt noch nicht bestätigt. In der Abschlussphase prüft Audomo das Ergebnis. „Erneut versuchen“ gibt abgebrochene, ausgelassene und fehlgeschlagene Aufträge wieder frei. Bei sichtbaren Fehlern bietet das Menü „Fehlgeschlagene erneut versuchen“. Ein Konflikt lässt sich in den Auftragsdetails lösen.

Nach dem Speichern zeigt Audomo das automatische Uploadziel und bei einer Verzögerung die früheste Startzeit. Scheitert die Übertragung, steht der Grund in der Android-Benachrichtigung und in den Auftragsdetails. Unter „Aufnehmen“ öffnet „Uploads prüfen“ die Warteschlange. Vorübergehende Verbindungsfehler versucht Android mit wachsendem Abstand erneut. Bei Namenskonflikten ist eine Entscheidung in den Details nötig.

„Cloudkopie prüfen“ liest die gespeicherte Datei beim Dienst und vergleicht ihren SHA-256-Wert mit der bestätigten Aufnahme. Bei Abweichungen zeigt Audomo, ob die Datei fehlt oder sich geändert hat. Von dort sind der Cloudpfadabgleich und bei fehlender Kopie das erneute Hochladen erreichbar. „Weitere Uploadziele wählen“ ergänzt Ziele für dieselbe Aufnahme. Zwei Ziele erhalten zwei unabhängig steuerbare Aufträge. Ab Version 0.2.12 zeigt die Detailansicht auch die gezählten Versuche und die Zeitpunkte von Versuch und Prüfung.

Automatische Uploads sind anfangs ausgeschaltet. Ein Ziel wird in „Uploadziele“ eingerichtet. Dropbox verwendet einen öffentlichen App-Key und OAuth mit PKCE. Für SFTP wird der öffentliche Hostschlüssel des vorgesehenen Servers benötigt. FTPS verwendet explizites TLS, WebDAV HTTPS. Zugangsdaten liegen AES-GCM-verschlüsselt mit einem Schlüssel aus dem Android-Keystore. Erst nach vollständigem Rücklesen und SHA-256-Vergleich gilt eine Übertragung als bestätigt.

Unter „Einstellungen“ → „Cloud und Uploads“ → „Uploadziele“ → „Upload-Verzögerung“ gilt die Wartezeit in Minuten ab dem lokalen Speichern einer Aufnahme. Mit „0“ (Standard) ist der Auftrag sofort fällig, mit „0,5“ nach 30 Sekunden und mit „5“ nach fünf Minuten. Die Warteschlange zeigt den frühesten Uploadzeitpunkt. Die gewählte Netzregel und Androids Hintergrundplanung können den Start weiter verschieben. Bereits wartende Aufnahmen behalten ihren Zeitpunkt. Wer in „Wiedergeben“ für dieselbe Aufnahme und dasselbe Ziel „Cloud Upload“ wählt, gibt den Auftrag sofort frei.

## Dropbox verbinden

In „Einstellungen“ → „Cloud und Uploads“ → „Uploadziele“ → „Ziel hinzufügen“ → „Dropbox“ den Zielordner wählen und „Mit Dropbox verbinden“ antippen. Nach der Freigabe im Browser oder in der installierten Dropbox-App kehrt Android zu Audomo zurück. Audomo speichert den Zugang verschlüsselt und zeigt das Ziel an. Beim Abbruch bleibt die Einrichtung zur erneuten Anmeldung geöffnet.

Um die Verbindung zu prüfen, unter „Uploadziele“ das gespeicherte Dropbox-Ziel antippen und „Verbindung testen“ wählen. Der Dialog zeigt erst den laufenden Test und anschließend das Ergebnis, bis du ihn schließt. Er prüft die Anmeldung und den lesenden Zugriff auf den Zielordner. Ob eine bestimmte Audiodatei vollständig übertragen wurde, zeigt „Hochgeladen“ im jeweiligen Auftrag.

Der öffentliche App-Key für Audomo ist im Standard-Build hinterlegt. Wer eine eigene Ausgabe veröffentlichen möchte, registriert diese einmalig bei Dropbox. In der App Console „Create app“, „Scoped access“ und „App folder“ wählen. Unter „Permissions“ die Rechte `files.metadata.read`, `files.content.read` und `files.content.write` aktivieren und speichern. Unter „Settings“ den öffentlichen „App key“ kopieren. [Dropbox App Console](https://www.dropbox.com/developers/apps) · [OAuth bei Dropbox](https://developers.dropbox.com/oauth-guide)

Für eine eigene Ausgabe den öffentlichen Schlüssel als Gradle-Eigenschaft `audomoDropboxAppKey` oder Umgebungsvariable `AUDOMO_DROPBOX_APP_KEY` setzen. Derselbe Wert bestimmt die App-Kennung und das vom offiziellen SDK verwendete Android-Rücksprungschema. Ein Build ohne Schlüssel erklärt die fehlende Registrierung in der App. [Android-Integration des Dropbox-SDK](https://github.com/dropbox/dropbox-sdk-java/blob/v7.1.1/README.md)

## Wechsel von der Testversion

1. Eine laufende Aufnahme beenden. In „Wiedergeben“ jede aufzubewahrende lokale Aufnahme in einen Android-Dokumentordner außerhalb der App exportieren. Eine repräsentative Datei mit einem anderen Player öffnen und für alle wichtigen Dateien Namen, Anzahl und Größen vergleichen.
2. Bei Aufnahmen, die ausschließlich in der Cloud liegen, Dienst, Konto und Pfad festhalten. Die Datei beim Dienst prüfen oder über die Bibliothek eine lokale Kopie wiederherstellen und exportieren. Der Cloudkatalog gehört zum privaten App-Speicher.
3. Den Einstellungsexport außerhalb der App ablegen. Er enthält ausgewählte Einstellungen. Aufnahmeindex, Warteschlange, Cloudzugänge und Android-Ordnerberechtigungen sind separat zu behandeln.
4. Erst nach eigener Prüfung der Sicherungen über den Wechsel entscheiden. Nach einer Neuinstallation die Audio-Dateien importieren, Einstellungen einlesen und Kalender, Dokumentordner und Cloudziele erneut verbinden. Der Import exportierter Audiodateien erzeugt neue Bibliothekseinträge. Eine vollständige Übernahme der alten Zuordnungen bietet 1.0.0 nicht.

Ab der offiziellen 1.0.0 ermöglichen dieselbe Paketkennung, derselbe Release-Schlüssel und ein höherer Versionscode gewöhnliche Updates mit Erhalt der App-Daten.
