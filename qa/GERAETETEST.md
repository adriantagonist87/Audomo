# Audomo · Teststand vom 26.11.2026

## Aufnahmeprofile und Berechtigungsfarben mit Version 1.1.4

Auf dem Galaxy S24 Ultra wurde die Profilverwaltung direkt aus den Einstellungen geöffnet. Geprüft wurden das Dropdown, der Editor und der Rückweg. Das geöffnete Dropdown zeigte die Qualitätsangaben. Im Editor war für jeden der beiden Effekte genau eine der drei Tasten ausgewählt. Die gespeicherten Profile und die aktive Auswahl stimmten nach dem Update mit dem vorherigen Stand überein. Für alle vier Berechtigungen wurde die tatsächliche Textfarbe mit dem angezeigten Status verglichen.

Im API-34-Emulator wurde aus „Hohe Qualität“ ein eigenes Profil mit 96 kbit/s und 7 dB erstellt. Der eingegebene Name und die Effektwahl überstanden eine Bildschirmdrehung. Nach dem Speichern und erneuten Bearbeiten waren „Standard“ für Echo und „Ein“ für Rauschunterdrückung weiterhin ausgewählt. Ein belegter Name ließ sich korrigieren, ein eigener Name ändern und eine Bearbeitung verwerfen. Das aktive Profil blieb beim Wechsel zum Dashboard und nach einem Prozessneustart erhalten. Abbruch der Löschbestätigung behielt das Profil, bestätigtes Löschen aktivierte „Besprechung“. Alle vier fehlenden Freigaben waren rot. Nach Freigabe des Kalenders über Android wechselte der Status beim Zurückkehren zu grün. [Profilverwaltung](api34/aufnahmeprofile-1.1.4.webp) · [Profil-Dropdown](api34/profilmenue-1.1.4.webp) · [Effektauswahl](api34/profileffekte-1.1.4.webp) · [Berechtigungen](api34/berechtigungen-1.1.4.webp)

## Zentrale Berechtigungen mit Version 1.1.2

Auf dem Galaxy S24 Ultra öffnete „Einstellungen“ → „Berechtigungen“ → „Nicht-stören-Zugriff“ die Samsung-Systemseite. Android-Zurück führte mit dem vorhandenen Freigabestatus in dieselbe Übersicht. Im Android-14-Emulator wurden der Kalenderzugriff direkt aus der Ersteinrichtung erteilt und der Nicht-stören-Zugriff auf der Systemseite erteilt und wieder entzogen. Nach der Rückkehr zeigte die Zeile jeweils „Erlaubt“ beziehungsweise „Zugriff einrichten“. [Ansicht der Berechtigungen](api34/berechtigungen-1.1.2.webp)

## Audioausgang im Player mit Version 1.1.1

Auf dem Galaxy S24 Ultra meldete Android 14 das verbundene Bluetooth-Gerät als aktiven A2DP-Medienausgang. Der freigegebene Hörtest verwendete eine zwei Sekunden lange synthetische MP3 aus dem Testpaket. Im Modus „Nur Bluetooth“ blieb der Player bis zur Bestätigung des Ausgangs am eigenen AudioTrack stumm, gab den Ton frei und erreichte das Dateiende. Der Test stellte die vorherige Einstellung wieder her. Eine physische Trennung während der Wiedergabe wurde in diesem Lauf nicht gemessen.

Im separaten API-34-Emulator mit Lautsprecherausgabe ließ sich die Rückfrage durch Außentipp abbrechen. „Trotzdem abspielen“ startete das Testaudio. Nach Pause und erneutem Play erschien die Rückfrage wieder. „Nur Bluetooth“ war über das Wiedergabemenü erreichbar und ließ auf dem Lautsprecher keine Ausnahme zu. Die Bibliotheks-, Backup- und Ausgangstests bestanden. [Rückfrage im Player](api34/kopfhoerer-rueckfrage-1.1.1.webp)

## Dashboard und Einstellungen mit Version 1.1.0

Auf dem Galaxy S24 Ultra mit Android 14 und One UI 6.1 wurde die Test-APK als Update installiert. Ihre Prüfsumme stimmte mit dem Build überein. Alle lokalen Sitzungseinträge waren nach der Installation vorhanden. Wischen wechselte vorwärts und rückwärts zwischen den Reitern. Das Zahnrad öffnete die Einstellungsübersicht, „Kalender und Dateinamen“ die passende Unterseite. Android-Zurück führte zur Übersicht. Ein Tipp bei geöffnetem Player auf den dahinterliegenden Reiter schloss den Player und ließ die Dateiliste sichtbar.

Auf One UI trat beim Wechsel auf einen Filter ohne Treffer zunächst ein Absturz auf. Die Listenansicht fragte die ID der ersten Zeile ab, obwohl die Ergebnisliste leer war. Im korrigierten Build wurde „Fehlerhafte Uploads“ auf demselben Telefon mit leerem Ergebnis gewählt, der Reiter verlassen und wieder geöffnet. Der Filter blieb ausgewählt. Nach der Prüfung wurde die ursprüngliche Filterauswahl wiederhergestellt.

Im separaten API-34-Emulator wurden alle vier Filter mit wartenden, fehlgeschlagenen, kollidierenden, abgeschlossenen, abgebrochenen und ausgelassenen Testaufträgen geprüft. Die Filterwahl überstand das Beenden des Prozesses. Wischen auf einem Auftrag priorisierte oder entfernte ihn. Ziehen an der Player-Zeitleiste wechselte den Reiter nicht. Ein Außentipp verwarf einen eingegebenen Dateinamen. Alle acht Einstellungskategorien, die Untermenüs zur Wiedergabe und zur Vibration sowie die Rückkehr aus dem Papierkorb wurden bedient. [Einstellungsübersicht](api34/einstellungen-1.1.0.webp) · [Filterauswahl](api34/warteschlangenfilter-1.1.0.webp)

Der abschließende Build bestand 14 JVM-Tests und alle 25 instrumentierten Tests der Android-14-Kernprüfung.

## Umbenennen im Auswahlmodus mit Version 1.0.3

Auf dem Galaxy S24 Ultra wurde 1.0.3 als Update installiert und die installierte APK per SHA-256 geprüft. Die bisherigen Aufnahme-Einträge blieben erhalten. Nach langem Drücken einer Aufnahme erschien „Umbenennen“ in der unteren Aktionsleiste und als erster Eintrag unter „Mehr“. Über das Menü öffnete sich die Namenseingabe mit den Speicherorten „Auf diesem Gerät“ und „Dropbox“. „Abbrechen“ führte zur selben Auswahl zurück.

Im Android-14-Emulator bestand die Regression mit 24 instrumentierten Tests. Sie prüft jetzt ausdrücklich „Mehr“ bei einer und bei zwei markierten Aufnahmen. Eine reine Cloudaufnahme ließ sich über die Aktionsleiste zur Namenseingabe öffnen. [Auswahlmenü mit Testaufnahme](api34/umbenennen-mehr-1.0.3.webp)

## Aufnahmen nachträglich umbenennen mit Version 1.0.2

Am 25.11. erhielt das Galaxy S24 Ultra mit Android 14 die Version 1.0.2 (Build 22) als Update von 1.0.1. Die installierte APK stimmte per SHA-256 mit der getesteten APK überein. Alle zuvor vorhandenen lokalen Aufnahme-Einträge waren nach dem Update vorhanden. In „Wiedergeben“ ließ sich bei einer lokal und in Dropbox gespeicherten Aufnahme über „Speicherorte“ der Dialog „Umbenennen“ öffnen. Beide Speicherorte waren einzeln wählbar. „Abbrechen“ führte zurück zur Liste.

Im Android-14-Emulator wurde eine synthetische Aufnahme in der Dateiliste gewählt, die Umbenennung abgebrochen und anschließend mit einem neuen Namen gespeichert. Die Liste und die gespeicherten Metadaten zeigten den neuen Namen. Mit einem freigegebenen Android-Dokumentordner wurde die umbenannte Datei über den Dokumentprovider zurückgelesen und ihr SHA-256 verglichen.

Die lokalen Testserver führten die serverseitige Umbenennung über WebDAV, FTP, FTPS und SFTP aus. Nach Erfolg lieferte nur der neue Pfad den erwarteten Dateiinhalt. Der Katalogtest umfasste eine rein entfernte Aufnahme in einem außerhalb der App verschobenen Ordner, einen bereits belegten Namen, eine verlorene Serverantwort und zwei getrennte Cloudziele. Beim selektiven Umbenennen behielt das zweite Ziel seinen bisherigen Namen. Ein geplanter Erstupload behielt seine Fälligkeit. Für Dropbox prüfte ein simulierter Transport die Pfade und das Verhalten bei Namenskonflikten. Für einen schreibenden Test im persönlichen Dropbox-Konto liegt noch kein Ergebnis vor.

Der finale Build bestand 14 JVM-Tests und alle 24 instrumentierten Tests der Kernprüfung auf API 34. Dazu kam die separate Prüfung mit dem Android-Dokumentprovider. Nach erneuter Eingabe der Zugangsdaten wiederholte Audomo den fehlgeschlagenen Testupload ohne einen manuellen Start. Die Android-Fehlerbenachrichtigung verschwand nach der Bestätigung.

## Dropbox auf dem Galaxy S24 Ultra mit Version 1.0.1

Mit 0.2.14 brach auf dem physischen S24 unter Android 14 der Verbindungsaufbau zur ersten IPv6-Adresse vor dem TLS-Handshake ab. DNS lieferte außerdem IPv4. Die automatische Verarbeitung und der Android-Fehlerkanal waren eingerichtet. Android hatte weitere Auftragsversuche mit wachsendem Abstand vorgesehen.

Der korrigierte Client erreichte über IPv4 nach dem IPv6-Timeout sowohl die OAuth-Erneuerung als auch den eingerichteten Dropbox-Ordner mit HTTP 200. Die Prüfung gelang auf der Standardroute und mit expliziter Bindung an dasselbe Android-Netz. Beim Test aus den Uploadzielen zeigte das Telefon erst den laufenden Verbindungsversuch und danach die bis zum Schließen sichtbare Bestätigung.

Auf Wunsch des Nutzers wurde ein bestehender, zuvor fehlgeschlagener Auftrag im bereits eingerichteten Konto wiederholt. Das Telefon übertrug die Datei über eine mehrteilige Dropbox-Sitzung, las sie vollständig zurück und speicherte den Auftrag als „Hochgeladen“. Der bestätigte SHA-256 stimmte mit dem separat auf dem Telefon berechneten Wert des lokalen Originals überein.

Im separaten API-34-Emulator führte eine neue Aufnahme mit zeitverzögerter Automatik selbständig zu einem Uploadauftrag. Ein absichtlich falscher Zugang beim lokalen FTP-Dienst erzeugte einen Fehler samt Android-Benachrichtigung. Nach Korrektur des Testzugangs wiederholte Android die Übertragung selbständig, bestätigte den Dateiinhalt und entfernte die Fehlermeldung. Die Prüfung verwendete weder eine erzwungene Jobausführung noch die manuelle Wiederholtaste. [Automatikregression](../app/src/androidTest/java/de/audomo/AutomaticUploadDeviceTest.java)

Der abschließende lokale Stand bestand 14 JVM-Tests und 23 Instrumentierungstests auf API 34. Zusätzlich blieb ein fehlgeschlagener Verbindungstest im separat bedienten Emulator bis zum Schließen lesbar. Der auf dem S24 bestätigte Build enthält den IPv4-Ausweichpfad und den sichtbaren Verbindungsdialog. Nach Wiederherstellung der WLAN-Verbindung wurde der finale 1.0.1-Build mit den ergänzten Fehlertexten als Update installiert. Mit diesem Build las das S24 die vorhandene 28,4-MiB-Cloudkopie erneut und bestätigte ihre Prüfsumme. Die Zahl der Schreibversuche blieb unverändert.

## Warteschlange auswählen, priorisieren und leeren mit Version 0.2.14

Im S24-Ultra-Emulator mit Android 14 wurde eine 0.2.13-Installation mit einem bestätigten FTP-Upload und drei wartenden Aufträgen aktualisiert. Ein Rechtswisch zog einen Auftrag vor. Seine Position blieb nach Beenden des App-Prozesses erhalten. Nach Rückkehr des WLANs bestätigten die Eingangszeiten des lokalen FTP-Dienstes die geänderte Reihenfolge. Der per Linkswisch entfernte Auftrag wurde übersprungen.

Langes Drücken und „Auswählen“ öffneten den Auswahlmodus. Mehrfachauswahl, alle auswählen, alle abwählen, gemeinsam vorziehen, Android-Zurück und Bildschirmdrehung wurden über die Oberfläche geprüft. Ein kurzer horizontaler Wisch und vertikales Scrollen lösten keine Aktion aus. „Beendete Aufträge entfernen“ ließ alle offenen Einträge bestehen. Anschließend wurden 15 offene Aufträge über die Bildschirmgrenze hinweg gemeinsam markiert und entfernt. [Mehrfachauswahl](api34/warteschlange-auswahl-0.2.14.webp)

„Warteschlange leeren“ zeigte auch bei aktivem Filter die Gesamtzahl. Abbrechen behielt die Aufträge. Nach Bestätigung und Prozessneustart blieb die Warteschlange leer. In „Wiedergeben“ blieb der FTP-Speicherort erhalten. Nach Entnahme der lokalen Testkopie ließ sich die Aufnahme aus dem Cloudkatalog bytegleich wiederherstellen. Beim Linkswisch während eines laufenden FTP-Transfers blieben das lokale Original erhalten und das Ziel frei von Teil- und Zieldateien. [Bestätigung beim Leeren](api34/warteschlange-leeren-0.2.14.webp)

14 Unit-Tests und 15 instrumentierte Tests bestanden. Geprüft wurden auch stabile Auftrags-IDs, unveränderte Fälligkeiten und das reine Nachprüfen eines bereits vom Server bestätigten oder fehlenden Zielinhalts. Das physische Galaxy S24 Ultra erhielt 0.2.14 als Update. Die Prüfsumme der installierten APK stimmt mit der Auslieferung überein.

## Text der Aufnahmebenachrichtigung mit Version 0.2.13

Im Android-14-Emulator war „die App ist aktiv und verrichtet ihren Dienst“ bei laufender Aufnahme im geöffneten Benachrichtigungsfeld vollständig lesbar. Der Gerätetest prüfte den Titel und das Audomo-Symbol für Aufnahme und Pause. Das Update wurde auf dem Galaxy S24 Ultra installiert und per SHA-256 bestätigt. [Benachrichtigung im Emulator](api34/aufnahmebenachrichtigung-0.2.13.webp)
## Warteschlange als Hauptreiter mit Version 0.2.12

Im S24-Ultra-Emulator mit Android 14 blieb ein bereits mit 0.2.11 erfolgreich an FTP gesendeter Auftrag nach dem Update sichtbar. Der Weg „Aufnehmen“ → „Wiedergeben“ → „Warteschlange“ führte mit zweimal Android-Zurück zur laufenden Aufnahme. Stopp speicherte diese Aufnahme und reihte sie mit fünf Minuten Wartezeit ein. „Jetzt freigeben“ startete den Transfer vorzeitig. Aus den Auftragsdetails ließ sich danach SFTP als weiteres Ziel beauftragen. Beide Kopien waren bytegleich. Zurück aus dem Warteschlangen-Einstieg in den Upload-Einstellungen führte wieder in diesen Dialog. [Warteschlange](api34/warteschlange-0.2.12.webp)

Zwei gemeinsam ausgewählte Ziele blieben über die Bildschirmdrehung markiert. Ohne WLAN zeigte ein fälliger Auftrag den Wartegrund. Nach „Überspringen“ für FTP wurde die zweite Kopie an SFTP übertragen. „Erneut versuchen“ lieferte anschließend auch die FTP-Kopie. Bei einem laufenden FTP-Datentransfer wurde der lokale Testserver vor der Veröffentlichung kurz angehalten und der Auftrag über die Oberfläche abgebrochen. Nach der Freigabe des Servers war weder eine finale Datei noch eine Arbeitsdatei auf dem Ziel vorhanden. Der Status „Abgebrochen“ blieb nach einem Prozessneustart erhalten. [Auswahl mehrerer Ziele](api34/mehrere-uploadziele-0.2.12.webp)

„Cloudkopie prüfen“ erkannte eine extern entfernte Datei. „Erneut hochladen“ stellte die Kopie bytegleich wieder her. Eine vorübergehend fehlende lokale Quelldatei erzeugte zwei getrennte Fehler. Der Filter „Klärungsbedarf“ zeigte beide betroffenen Dienste und den Grund. Nach Wiederherstellung der Quelle übertrug „Fehler wiederholen“ beide Dateien in den richtigen Unterordner. Die Instrumentierung prüfte unter anderem den Schutz der Veröffentlichung vor einem verspäteten Abbruch und den erneuten Namenskonflikt nach einem ausgelassenen Auftrag. [Fehlerfilter](api34/warteschlange-fehler-0.2.12.webp)

14 Unit-Tests und 14 instrumentierte Tests bestanden. Android Lint meldete keine Fehler. Das physische Galaxy S24 Ultra erhielt 0.2.12 als Update über 0.2.11. Die SHA-256-Prüfsumme der installierten APK stimmt mit der Auslieferung überein. Die Übertragungstests nutzten lokale Testdienste und für Dropbox einen simulierten Transport.

## Verzögerter Cloud-Upload und Aufnahmesymbol mit Version 0.2.11

Auf dem S24-Ultra-Emulator mit Android 14 wurde die App über eine Installation von 0.2.10 aktualisiert. Zwei über den Aufnahmebutton erzeugte Ogg-Dateien erhielten unterschiedliche Wartezeiten von 0,5 und 5 Minuten. Eine dritte, manuell zum selben FTP-Ziel geschickte Datei traf vor beiden ein. Ein Wechsel der Einstellung ließ die bereits vorgemerkten Zeitpunkte bestehen. Der Dialog akzeptierte Komma und Punkt, wies eine negative Eingabe zurück und behielt bei „Abbrechen“ den bisherigen Wert. Die Warteschlange zeigte die Fälligkeit nach einem Prozessneustart weiterhin an. [Verzögerung in Minuten](api34/uploadverzoegerung-0.2.11.webp)

Mit der ausgelieferten APK trafen die neuen Aufnahmen bei 0,5 Minuten nach rund 38 Sekunden und bei 5 Minuten nach 5 Minuten und 26 Sekunden am lokalen FTP-Dienst ein. Android startete beide Aufträge selbstständig. Die Datei mit 0 Minuten war schon vorher übertragen. Ohne WLAN wartete ein weiterer fälliger Auftrag bis zur Wiederverbindung. Alle verglichenen Serverdateien waren bytegleich mit ihren lokalen Originalen. 14 Unit-Tests und 13 instrumentierte Tests bestanden. Android Lint meldete keine Fehler.

Während einer Aufnahme zeigte die Statusleiste das einfarbige Audomo-A. In der ausgeklappten Benachrichtigung stand das Zeichen auf lila Grund neben „Audomo“. Die Gerätetests prüften Logo und Akzentfarbe bei laufender und pausierter Aufnahme, den Export und Import von Dezimalminuten sowie die manuelle Freigabe eines bereits wartenden Auftrags ohne Duplikat. Das Galaxy S24 Ultra erhielt 0.2.11 als Update. Die Prüfsumme der installierten APK stimmt mit der Auslieferung überein. [Aufnahmebenachrichtigung](api34/aufnahmebenachrichtigung-0.2.11.webp)

## Tagesansicht und manuelle Terminwahl mit Version 0.2.10

Auf dem S24-Ultra-Emulator unter Android 14 öffnete ein Tipp auf den Terminbereich die Tagesansicht. Sie zeigte zwei gleichzeitig beginnende Termine nebeneinander, einen ganztägigen Termin oberhalb des Stundenrasters und die späteren Termine des Tages. Ein dritter, abgewählter Kalender war ausgeblendet. Die Providerprüfung deckte auch Serientermine, abgesagte Termine, lokale Tagesgrenzen und die doppelte Stunde bei der Zeitumstellung ab.

„Abbrechen“ erhielt einen bereits manuell eingegebenen Namen. Android-Zurück erhielt einen vorgemerkten Termin, „Automatisch zuordnen“ hob ihn auf. Nach der Wahl von „Musikprobe“ erzeugte der Aufnahmebutton eine geprüfte Opus-Datei namens „Privat_Musikprobe.ogg“ im Kalenderordner „Privat“. Eine zweite Aufnahme erhielt den automatischen Namen „Folgeaufnahme.ogg“. Ohne Kalenderfreigabe und ohne ausgewählte Kalender waren die jeweils passenden Einstiege zur Einrichtung erreichbar. Zwei instrumentierte Tests und zwölf Unit-Tests bestanden. Das Update auf dem Galaxy S24 Ultra wurde per SHA-256 bestätigt. [Tagesansicht](api34/tagesansicht-0.2.10.webp) · [Dateinamenvorschau](api34/termin-vorgemerkt-0.2.10.webp)

## Ordnerverwaltung, Speicherorte und Abgleich mit Version 0.2.9

Ein Galaxy-S24-Ultra-AVD mit API 34 erhielt das Update über eine echte 0.2.8-Installation. Vorher wurden zwei Testaufnahmen per FTP übertragen und von einer die gesamte lokale Sitzung entfernt. Nach dem Update zeigte die Bibliothek für diese Aufnahme FTP und für die zweite „Lokal · FTP“. Die Wiederherstellung der älteren Aufnahme ergab wieder eine abspielbare lokale Datei. Fehlende historische Zeitangaben wurden als unbekannt angezeigt. Ein zusätzlicher SFTP-Upload derselben Aufnahme führte beide Dienste mit ihren Pfaden auf. [Speicherorte in der Liste](api34/speicherorte-liste-0.2.9.webp) · [Cloudkopie wiederherstellen](api34/cloudkopie-wiederherstellen-0.2.9.webp)

In den Einstellungen wurden ein Ordner und ein Unterordner angelegt, mit einer MP3-Datei befüllt, umbenannt und verschoben. Das Auflösen eines Ordners behielt seine Inhalte, eine Namenskollision ließ beide Originale bestehen. Nach einer externen Unterordnerverschiebung im freigegebenen Android-Dokumentbaum ordnete der Abgleich die gespeicherte Aufnahme dem neuen Ordner zu. Sie war dort abspielbar. Nach Entfernen der privaten Test-MP3 stellte der Android-Abgleich die Datei aus der verifizierten Ordnerkopie bytegenau wieder her. [Ordnerverwaltung](api34/ordner-verwalten-0.2.9.webp)

Am lokalen FTP-Dienst wurden lokale und mitgeführte Cloudverschiebung, Leeren des lokalen Papierkorbs, Verschieben einer reinen Cloudkopie, eindeutige und mehrdeutige Treffer sowie fehlende Dateien geprüft. Nach externer Verschiebung zeigte der Zieldialog den neuen Pfad. FTP, FTPS, SFTP und HTTPS-WebDAV wurden mit echten lokalen Testdiensten geprüft. Die Dropbox-Tests verwenden einen simulierten Transport für Paginierung, Download und mehrteiligen Upload. 13 instrumentierte Tests und elf Unit-Tests bestanden. Lint meldete keine Fehler. Das Galaxy S24 Ultra erhielt Version 0.2.9 als Update. Die Prüfsumme der installierten APK stimmt mit der Auslieferung überein.

## Bestehende Aufnahmen hochladen mit Version 0.2.8

Auf Android 14 wurden in „Wiedergeben“ zwei Testaufnahmen markiert und über „Cloud Upload“ an das ausgewählte SFTP-Ziel übertragen. Bei der ersten Zieleinrichtung blieb die Auswahl erhalten. Unter „Einstellungen“ → „Uploadziele“ → „Bestehende Aufnahmen hochladen“ standen vier gespeicherte Aufnahmen zur Auswahl. Die fünfte lag im Papierkorb. Alle auswählen, alle abwählen, Android-Zurück und Drehen behielten jeweils den erwarteten Auswahlzustand. Eine Aufnahme aus dem Ordner „Privat“ kam über diesen Weg unter demselben Ordnernamen auf dem FTP-Ziel an.

Mit der ausgelieferten APK wurden beide Wege mit vertauschten Zielen wiederholt. Auf beiden lokalen Testservern stimmten Dateipfade und Inhalte bytegenau mit den ausgewählten Testdateien überein. Die unmarkierte Aufnahme und die Aufnahme im Papierkorb fehlten wie erwartet. Vier instrumentierte Tests und elf Unit-Tests bestanden. Das Galaxy S24 Ultra erhielt das Update, die Prüfsumme der installierten APK stimmt mit der Auslieferung überein. [Mehrfachauswahl](api34/cloud-upload-mehrfach-0.2.8.webp) · [Auswahl über alle Ordner](api34/bestehende-aufnahmen-hochladen-0.2.8.webp) · [Dienstauswahl](api34/cloud-upload-ziel-0.2.8.webp)

## Android-Zurück vom Reiter „Wiedergeben“ mit Version 0.2.7

Auf dem Galaxy S24 Ultra (SM-S928B, Android 14, One UI 6.1) war die Samsung-Navigation mit drei Gestenstreifen aktiv. Bei der Vorprüfung von 0.2.6 trat das berichtete vorzeitige Verlassen weder mit dem Android-Tastensignal noch beim Wischen am rechten Streifen auf. Seit 0.2.7 legt Android die besuchten Reiter als eigene Ansichten im selben Task ab.

Nach dem Update führte ein Wisch am rechten Streifen aus „Wiedergeben“ direkt zum ursprünglichen „Aufnehmen“. Die Folge „Dateiname und Ordner“ → „Einstellungen“ → „Wiedergeben“ → „Aufnehmen“ wurde mit drei Zurückgesten durchlaufen. Nach drei abwechselnden Reiterwechseln erreichten drei Zurückgesten in umgekehrter Reihenfolge wieder die Startansicht. Der direkte Weg bestand auf dem S24 auch mit `KEYCODE_BACK`. Die installierte APK stimmte per SHA-256 mit der Auslieferung überein.

Auf dem API-34-Emulator führte die Randgeste von der Bibliothek zum Recorder. Nach Drehung und Beenden des Hintergrundprozesses stellte Android die Bibliothek wieder her. Zurück erreichte den Recorder und ein weiterer Schritt den Startbildschirm. Bei einer laufenden Testaufnahme blieben Sitzung und Aufnahmedienst beim Besuch der Dateiliste erhalten. Ein Stopp speicherte genau eine Aufnahme. Drei instrumentierte Tests und elf Unit-Tests bestanden. Android Lint meldete keine Fehler. [Vor Zurück](api34/wiedergeben-vor-zurueck-0.2.7.webp) · [Nach Zurück](api34/aufnehmen-nach-zurueck-0.2.7.webp)

## Dateiname und Kalenderordner mit Version 0.2.6

Im Android-14-Emulator war ein laufender Termin „Abendessen“ im lokalen Testkalender „Privat“ eingetragen. Die Vorlage „Kalendername“ und die Ablage „Unterordner je Kalender“ ergaben bei zwei Aufnahmestarts über die Oberfläche `Privat/Privat.ogg` und `Privat/Privat (2).ogg`. Die Bibliothek und der freigegebene Android-Speicherordner enthielten jeweils einen Ordner „Privat“. Ein vor dem Start eingetragener eigener Name ergab `Privat/Eigener Name.ogg`. [Namens- und Ordnervorschau](api34/dateiname-und-ordner-0.2.6.webp) · [Aufnahmen im Kalenderordner](api34/kalenderordner-0.2.6.webp)

Ohne ausgewählten Kalender entstand `Aufnahme.ogg` direkt im Basisordner. Mit der Vorlage „Termintitel“ entstanden im festen Ordner `Archiv/Abendessen.ogg` und bei Kalenderablage `Archiv/Privat/Abendessen.ogg`. Nach Entzug des Kalenderzugriffs zeigte Audomo den fehlenden Zugriff an und speicherte `Archiv/Aufnahme.ogg`. Für alle sieben Pfade stimmte der SHA-256-Wert der Android-Ordnerkopie mit der privaten Aufnahme überein.

Die Menüprüfung umfasste Vorlagen, Einfügen eines Bausteins, Basisordnerwahl, einen neuen Basisordner und das Verwerfen eines Entwurfs mit Android-Zurück. Eine Bildschirmdrehung ließ den ungespeicherten Entwurf bestehen. Elf Unit-Tests und sieben instrumentierte Tests bestanden, darunter Kalenderpriorität, ältere Sicherungen und Android-Speicheranbieter. Android Lint meldete keine Fehler. Auf dem Galaxy S24 Ultra (SM-S928B) wurde das Update auf 0.2.6 installiert und gegen die ausgelieferte APK per SHA-256 geprüft.

## Mehrfachauswahl mit Version 0.2.5

Im API-34-Emulator markierte „Alle auswählen“ alle 500 Aufnahmen im aktuellen Ordner, einschließlich der Einträge unterhalb des Bildschirms. Eine Suche mit zehn Treffern markierte genau diese zehn. Bei Suche über alle Ordner kam eine weitere Testaufnahme hinzu. Beim Eingrenzen auf den Ausgangsordner fiel sie wieder aus der Auswahl. „Alle abwählen“ setzte den Zähler auf null. Bei leerer Auswahl waren die Dateiaktionen gesperrt. [Teilweise ausgewählt](api34/alle-auswaehlen-0.2.5.webp) · [Alle ausgewählt](api34/alle-abwaehlen-0.2.5.webp)

In einem separaten Touchtest blieb die leere Auswahl nach einer Bildschirmdrehung geöffnet. Eine einzelne Aufnahme ließ sich direkt wieder markieren. Der Papierkorb-Dialog für zwei Suchtreffer zeigte zwei ausgewählte Aufnahmen. Nach Abbruch des Dialogs waren alle drei Testdateien vorhanden.

Fünf instrumentierte Bibliothekstests und zehn Unit-Tests bestanden. Android Lint meldete keine Fehler. Das Update auf dem Galaxy S24 Ultra (SM-S928B) wurde anhand der installierten Versionsnummer und des SHA-256-Abgleichs mit der ausgelieferten APK bestätigt.

## Android-Zurück und Aufnahmeknopf mit Version 0.2.4

Auf dem Android-14-Emulator führten die Systemtaste und die Zurück-Geste zur vorher besuchten Ansicht. Vom Papierkorb ging es zurück in die Einstellungen, von dort zum zuvor geöffneten Player und vom Player zur gefilterten Dateiliste. Die Auswahl blieb auch nach dem Drehen des Displays erhalten. Beim Wechsel aus einer laufenden Aufnahme in die Dateiliste und zurück lief dieselbe Aufnahme weiter. An der Ausgangsansicht kehrte Android zum Startbildschirm zurück.

Bei einer neuen Aufnahme zeigt der Knopf einen gefüllten salbeigrünen Kreis. Nach einer Pause kennzeichnet ein Dreieck das Fortsetzen. Start, Pause, Fortsetzen und Stopp wurden im Emulator bedient. [Aufnahmebereit](api34/aufnahmebereit-0.2.4.webp) · [Pausierte Aufnahme](api34/aufnahme-pausiert-0.2.4.webp)

Zehn Unit-Tests und vier instrumentierte Tests bestanden, darunter die Bedienprüfung mit 500 Testdateien. Android Lint meldete keine Fehler. Das Galaxy S24 Ultra (SM-S928B) erhielt das Update auf 0.2.4 per ADB. Die SHA-256-Prüfsumme der installierten APK stimmte mit der Auslieferung überein.

## Dropbox-Anmeldung mit Version 0.2.3

Auf API 34 führte „Mit Dropbox verbinden“ aus den Uploadzielen zur echten Dropbox-Anmeldeseite. Dropbox zeigte Audomo als anfragende App und bot die Kontoanmeldung an. Der Auslieferungsbuild enthält den registrierten öffentlichen App-Key. Ein separater Build ohne Key zeigte die fehlende Konfiguration vor dem Browserstart. [Audomo-Dialog](api34/dropbox-anmeldung-0.2.3.webp) · [Dropbox-Anmeldeseite im Emulator](api34/dropbox-login-0.2.3.webp)

Im Testbuild mit synthetischer Kennung wurden PKCE-Browseranforderung, Auflösung des Rücksprungschemas, Abbruch, erneutes Verbinden und verschlüsseltes Speichern einer simulierten SDK-Freigabe geprüft. Der lokale Transporttest prüfte Token-Erneuerung und mehrteiligen Upload. Persönliche Kontofreigabe und Dateiübertragung zu einem produktiven Konto stehen aus. Zehn Unit-Tests bestanden. Android-Lint meldete keine Fehler. Das S24 erhielt das Update per ADB. Die installierte APK stimmte per SHA-256 mit der Auslieferung überein.

## Sortiermenü mit Version 0.2.2

Im Referenzemulator prüfte `LibraryUiDeviceTest` mit 500 Testdateien die Reihenfolge aller 499 benachbarten Paare für fünf Sortierfelder in beiden Richtungen. Beim erneuten Öffnen der Bibliothek blieben Feld und Richtung ausgewählt. Das Menü enthielt keine ankreuzbaren Elemente. Beim Update des SM-S928B blieben die zuvor gespeicherten Sortierwerte erhalten. Auf dem S24 wurden beide aktiven Einträge über die Bedienoberfläche erkannt. Erneutes Antippen der aktiven Richtung behielt die Auswahl bei. [Menü auf API 34](api34/sortiermenue-0.2.2.png)

## Kachellangdruck mit Version 0.2.1

Auf dem entsperrten Samsung SM-S928B mit API 34 holte ein über die Samsung-Systemkachel ausgelöster Langdruck dieselbe laufende Audomo-Activity aus dem Hintergrund zurück. Aufnahmezustand und Zahl der gespeicherten Sitzungen blieben gleich. Der Test `TileLongPressDeviceTest` lässt sich mit dem Instrumentierungsargument `-e tileGesture true` auf einem entsperrten Gerät mit bereits hinzugefügter Kachel wiederholen.

Auf dem Referenzemulator öffnete Version 0.2 bei langem Drücken die Android-App-Informationen. Bei 0.2.1 öffnete derselbe Langdruck nach beendetem App-Prozess die Hauptansicht. Bei bereits geöffneter Bibliothek oder Kalenderunterseite blieben die Activity-Instanz und die Task-ID erhalten. Ein weiterer Emulatorlauf mit `-e tileRecording true` prüfte einen kurzen Tipp zum Starten, einen langen Druck während der Aufnahme und einen kurzen Tipp zum Stoppen. Beim Langdruck blieb die Sitzung dieselbe und der Audioframezähler stieg weiter. Nach Stopp lag genau eine neue Aufnahme vor. Der Kacheleinstieg reagiert auf die von Android vorgesehene Systemaktion. [Android-Dokumentation zu Schnellkacheln](https://developer.android.com/develop/ui/views/quicksettings-tiles)

Die umfassenden Ergebnisse für 0.2 stehen im [Abnahmeprotokoll](ABNAHME-0.2.md).

## AVD-Serie mit Version 0.1

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

## Aufnahme und Vibration am physischen Galaxy S24 Ultra

Das Testgerät war ein Samsung SM-S928B mit Android 14 (API 34) und One UI 6.1. Der Nutzer nahm mit Version 0.1 auf und bestätigte die Wiedergabe. Die zuletzt geprüfte Datei enthält Ogg/Opus mit 5,8 s Dauer. Die Sitzungsdaten nennen 48 kHz und das interne Mikrofon. Die gewünschte Ausrichtung nach hinten blieb unbestätigt.

Bei 0.1 spürte der Nutzer keine Vibration. Audomo übersprang die Rückmeldung, weil die Berührungsvibration im System ausgeschaltet war. Ab 0.1.1 verwendet Audomo für die Aufnahmeereignisse die Benachrichtigungsvibration. Der Vibrationsdienst von One UI verwarf zunächst auch diese Aufrufe, weil Benachrichtigungen für Audomo gesperrt waren. Nach der Freigabe durch den Nutzer protokollierte der Dienst drei abgeschlossene Impulse. Der Nutzer bestätigte, dass sie fühlbar, aber zu kurz waren. Seit 0.1.2 zeigt das Vibrationsmenü bei gesperrten Benachrichtigungen einen Hinweis mit direktem Zugang zur Android-Freigabe.

Version 0.1.3 (4) wurde als Update auf dem S24 und dem API-34-AVD installiert. Die drei Vorschauen verwenden dieselben Vibrationsmuster wie die Aufnahmeereignisse. Die Nutzerentscheidung vom 22.11.2026 legt den langen Impuls auf 500 ms, die kurzen auf je 300 ms und die Pause zwischen ihnen auf 200 ms fest. Das Zweiermuster verwendet entsprechend die Zeitwerte des Dreiermusters.

| Prüfung mit 0.1.3 | Ergebnis |
|---|---|
| Drei Vorschauen am AVD | Android protokollierte bei jedem Aufruf den Status `finished`. Die Segmente dauerten 300/200/300 ms, 500 ms bzw. 300/200/300/200/300 ms. Jeder Zwischenwert von 200 ms ist eine Pause. [Vibrationsmenü](api34/vibration.png) |
| Gespeicherte Startvorschau am S24 | Der Vibrationsdienst protokollierte einen 500-ms-Impuls und `finished`. Die Vorschau löste keine Mikrofonaufnahme aus. Die Dauern sind Sollwerte des Treibers. |
| Build | minSdk und targetSdk 34, APK-Signatur v2 geprüft. Neun JVM-Tests erfolgreich. Android Lint meldete 0 Fehler und 20 Warnungen. |
| APK-SHA-256 | `a080b0badbccd7e085e6a501273cdf0fb1d7449398f47a287c847f0d5e9484a6` |

Für T20 bleiben eine Aufnahme samt gesichertem Stopp mit den verlängerten Mustern, ein provozierter Fehlstart sowie die Fälle mit ausgeschalteter App-Vibration und mit „Nicht stören“ am S24 offen.

## Offener Stand zum Abschluss von 0.1.3

Für den Aufnahmeblock fehlen insbesondere der sechsstündige Zeitmarkerlauf auf One UI 6.1, Verhalten bei Anrufen und Mikrofonkonkurrenz, voller Speicher, unterbrochene Finalisierung beim Geräteneustart, reale Hintermikrofon-/Effektmessungen und DND-Wechsel. Beim Oberflächenabgleich bleibt unter anderem die Ausblendzeit des Speicherhinweises offen. Für die späteren Blöcke sind unter anderem dauerhafte SAF-Zielwahl, Import, Mehrfachaktionen, Papierkorb, Cloudziele und verifizierte Uploads offen. Der [Funktionsabgleich](ANFORDERUNGSABGLEICH.md) führt den Status pro ID.
