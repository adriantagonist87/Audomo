# Audomo 1.4.0

Unter „Einstellungen“ → „Updates“ lässt sich das neueste stabile GitHub-Release manuell oder beim Öffnen der App prüfen. APK-Download und Installation beginnen mit einer eigenen Bedienaktion. Android zeigt die Systembestätigung an. [Bedienung](../docs/BEDIENUNG.md#updates-von-github)

## Abnahme auf Android 14

Auf einem separaten ARM64-Emulator enthielt eine temporäre, niedriger versionierte Prüffassung den neuen Updater. Der Test rief das öffentliche GitHub-Release 1.3.1 ab, lud die APK und öffnete den Quellenfreigabe-Dialog von Android. Nach Ablehnung blieb die Installation erneut wählbar. Nach Freigabe, Abbruch im Installer und erneuter Bestätigung meldete Android Version 1.3.1. Der SHA-256 der installierten APK entsprach dem veröffentlichten GitHub-Artefakt. Die gewählte wöchentliche Prüfung blieb beim Update erhalten.

Im Flugmodus zeigte eine manuelle Prüfung den Verbindungsfehler und den Zeitpunkt des letzten Erfolgs. Nach Wiederherstellung der Verbindung meldete die App den aktuellen Versionsstand. Änderungen des Intervalls blieben nach einem Neustart erhalten.

Die 17 JVM-Tests, 35 Kernprüfungen auf dem Android-14-Emulator, sechs Migrationsprüfungen und zwei neue Geräteprüfungen für den Updater bestanden. Letztere decken unter anderem Vorabversionen, fremde Downloadpfade, fehlende Prüfsummen, falsche Dateigröße, abweichende Daten und eine unlesbare APK ab. Release-Lint meldete 0 Fehler. [Regression](release_suite.py)
