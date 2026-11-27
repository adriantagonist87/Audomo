# <img src="app/src/main/res/drawable-nodpi/brand.png" width="64" height="64" alt="Audomo-App-Symbol"> Audomo

Audomo nimmt eure Projektbesprechungen auf und benennt die Audiodatei nach dem passenden Termin im Android-Kalender. So findet ihr auch Wochen später die Planungsrunde wieder, in der ihr eine Materialwahl oder einen Liefertermin vereinbart habt.

[Audomo für Android herunterladen](https://github.com/adriantagonist87/Audomo/releases/latest) · Ab Android 14

## Besprechungen nur im gemeinsamen Einverständnis aufnehmen

> [!IMPORTANT]
> Audomo wurde ausschließlich für Besprechungen entwickelt, die alle Beteiligten gemeinsam aufzeichnen möchten. Fragt vor dem Start jede Person, ob sie mit der Aufnahme einverstanden ist. Klärt auch, wer sie später anhören darf und ob sie in eure Projektcloud geladen werden soll. Kommt jemand dazu, pausiert die Aufnahme und fragt nach.
>
> In Deutschland macht sich strafbar, wer unbefugt aufnimmt, was eine andere Person in einer nichtöffentlichen Besprechung sagt. Auch wer eine so entstandene Aufnahme unbefugt nutzt oder Dritten zugänglich macht, kann sich strafbar machen. Dafür sieht das Gesetz eine Geldstrafe oder bis zu drei Jahre Freiheitsstrafe vor. [§ 201 Abs. 1 StGB – Verletzung der Vertraulichkeit des Wortes](https://www.gesetze-im-internet.de/stgb/__201.html)

## Vom Kalendertermin zur Aufnahme

Im Kalender „Werkstattumbau“ steht um zehn Uhr die „Planungsrunde“. Ist dieser Kalender in Audomo ausgewählt, erscheint die laufende Besprechung schon beim Öffnen der App. Mit der Dateinamenvorlage „Termintitel“ steht dort bereits `Planungsrunde.ogg`. Ein Tipp auf den grünen Aufnahmeknopf genügt. Soll ein anderer Termin den Namen liefern, tippt auf „Aktueller Kalendertermin“ und wählt ihn in der Tagesansicht aus.

Unter „Einstellungen“ → „Kalender und Dateinamen“ → „Dateiname und Ordner“ könnt ihr Datum, Uhrzeit, Kalender und Termintitel kombinieren. Mit „Unterordner je Kalender“ landet das Gespräch in `Werkstattumbau/Planungsrunde.ogg`. Audomo legt den Ordner bei der ersten Aufnahme an. Die Vorschau zeigt auch, was ohne Termin passiert. Bei der Vorlage `{kalender}_{termin}` heißt die Datei dann `-_Aufnahme.ogg`. Der Bindestrich steht für den fehlenden Kalendernamen.

<p>
  <a href="docs/bilder/tagesansicht.png"><img src="docs/bilder/tagesansicht.png" width="230" alt="Tagesansicht mit der Planungsrunde um zehn Uhr im Kalender Werkstattumbau"></a>
  <a href="qa/api34/dateinamenvorschau-1.3.1.webp"><img src="qa/api34/dateinamenvorschau-1.3.1.webp" width="230" alt="Vorlage für den Dateinamen mit zwei Beispielen, einmal mit Termin und einmal ohne"></a>
</p>

## Nach der Besprechung

Unter „Wiedergeben“ könnt ihr eine Passage noch einmal anhören, ein paar Sekunden zurückspringen oder das Abspieltempo ändern. Die Aufnahme wird zunächst auf dem Gerät gespeichert. Wenn ihr einen gemeinsamen Ablageort vereinbart habt, markiert die gewünschten Aufnahmen und wählt „Cloud Upload“. Für regelmäßige Runden lässt sich der Upload auch automatisch starten, auf Wunsch mit einigen Minuten Verzögerung. [Bedienungsanleitung und unterstützte Cloud-Dienste](docs/BEDIENUNG.md)

Liegen eure Aufnahmen schon in Dropbox, öffnet „Einstellungen“ → „Cloud und Uploads“ → „Aus Dropbox wiederherstellen“. Damit könnt ihr den gewählten Dropbox-Ordner samt Unterordnern aufs Telefon holen. [Aufnahmen aus Dropbox zurückholen](docs/BEDIENUNG.md#aufnahmen-aus-dropbox-zurückholen)

Beim Umzug auf ein neues Telefon könnt ihr nur eure Einstellungen mitnehmen, die Cloudverbindungen einschließen oder die ganze Bibliothek samt Papierkorb und Uploadverlauf übertragen. Die Sicherung hat ein eigenes Passwort. [Sicherung und Gerätewechsel](docs/BEDIENUNG.md#sicherung-und-gerätewechsel)

Ab Version 1.4.0 findet ihr neue stabile Ausgaben unter „Einstellungen“ → „Updates“. Dort könnt ihr die APK direkt laden und die Aktualisierung auf dem Telefon bestätigen. Frühere offizielle Versionen lassen sich mit der APK aus dem GitHub-Release aktualisieren. Wer eine debug-signierte Testfassung nutzt, sollte vor dem Wechsel die Aufnahmen sichern. Die stabile Ausgabe verwendet einen eigenen Signierschlüssel. [Anleitung für den Wechsel](docs/BEDIENUNG.md#wechsel-von-der-testversion)
