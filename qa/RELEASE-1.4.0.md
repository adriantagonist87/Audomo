# Audomo 1.4.0

Unter „Einstellungen“ → „Updates“ lässt sich das neueste stabile GitHub-Release manuell oder beim Öffnen der App prüfen. APK-Download und Installation beginnen mit einer eigenen Bedienaktion. Android zeigt die Systembestätigung an. [Bedienung](../docs/BEDIENUNG.md#updates-von-github)

## Abnahme auf Android 14

Auf einem separaten ARM64-Emulator enthielt eine temporäre, niedriger versionierte Prüffassung den neuen Updater. Der Test rief das öffentliche GitHub-Release 1.3.1 ab, lud die APK und öffnete den Quellenfreigabe-Dialog von Android. Nach Ablehnung blieb die Installation erneut wählbar. Nach Freigabe, Abbruch im Installer und erneuter Bestätigung meldete Android Version 1.3.1. Der SHA-256 der installierten APK entsprach dem veröffentlichten GitHub-Artefakt. Die gewählte wöchentliche Prüfung blieb beim Update erhalten.

Im Flugmodus zeigte eine manuelle Prüfung den Verbindungsfehler und den Zeitpunkt des letzten Erfolgs. Nach Wiederherstellung der Verbindung meldete die App den aktuellen Versionsstand. Änderungen des Intervalls blieben nach einem Neustart erhalten.

Die 17 JVM-Tests, 35 Kernprüfungen auf dem Android-14-Emulator, sechs Migrationsprüfungen und zwei neue Geräteprüfungen für den Updater bestanden. Letztere decken unter anderem Vorabversionen, fremde Downloadpfade, fehlende Prüfsummen, falsche Dateigröße, abweichende Daten und eine unlesbare APK ab. Release-Lint meldete 0 Fehler. [Regression](release_suite.py)

## Veröffentlichung und Geräteupdate

Vor der Veröffentlichung wurde eine Testaufnahme in die unveränderte GitHub-Version 1.3.1 importiert. Beim Update auf die signierte 1.4.0 blieben Installationsidentität und Testaufnahme erhalten. Der Player konnte die Datei weiterhin öffnen.

Nach der Veröffentlichung meldete die mit altem Versionscode gebaute Prüffassung beim Öffnen von Audomo die neue 1.4.0. Unmittelbar nach der Freigabe hatte der öffentliche Endpunkt zunächst noch die Vorversion geliefert. Über den Dashboard-Hinweis ließ sich die neue APK von GitHub laden und im Android-Installer bestätigen. Die installierte 1.4.0 meldete „Audomo ist aktuell“. Das eingestellte Wochenintervall blieb erhalten. [Update-Seite während der Prüfung](api34/update-verfuegbar-1.4.0.webp)

Auf dem Galaxy S24 Ultra wurde die von GitHub zurückgeladene APK per WLAN-ADB über die offizielle 1.3.1 installiert. Versionscode 39, fortbestehende Installationsidentität und der Hash der installierten APK wurden geprüft. GitHub, Emulator und S24 lieferten denselben SHA-256-Wert `e7abc6260a1783a878044c1b74e8dc91f4a650cfefff92e5b67dc42c6c9ef40f`. [Stabiles Release mit APK](https://github.com/adriantagonist87/Audomo/releases/tag/v1.4.0)
