# Audomo 1.3.1

Ohne passenden Termin erzeugt der Baustein `{kalender}` jetzt einen Bindestrich. Der Termintitel `{termin}` erhält bei fehlendem Titel den Ersatztext „Aufnahme“. [Vorschau mit und ohne Termin](api34/dateinamenvorschau-1.3.1.webp)

Am 27. November 2026 wurde die Vorlage `{datum}_{uhrzeit}__{kalender}_{termin}` in der offiziellen 1.3.0 auf einem Android-14-Emulator gespeichert. Nach dem Update auf 1.3.1 zeigte die Vorschau `Privat_Abendessen` für den Beispieltermin und `-_Aufnahme` ohne Termin. Eine Testaufnahme über den virtuellen Audioeingang erschien in der Bibliothek als `2026-11-27_10-34-50__-_Aufnahme.ogg`. Die Aufnahme aus der Vorversion war weiterhin unter ihrem ursprünglichen Namen sichtbar.

15 JVM-Tests bestanden. Release-Lint meldete 0 Fehler. Die GitHub-APK mit Versionscode 38 wurde im Leerlauf über die bestehende Installation auf dem Galaxy S24 Ultra installiert. Der SHA-256-Wert der installierten APK entsprach dem GitHub-Artefakt und lautete `ef5b1edd983454696e23e43dd0dade5bfd8f9bfc47df47730b7b712d715c76ea`. [Release mit APK](https://github.com/adriantagonist87/Audomo/releases/tag/v1.3.1)
