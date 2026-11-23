# Quellcode, Werkzeuge und Assets

Der neu geschriebene Audomo-Quellcode steht unter Apache License 2.0. Der geprüfte AudioRecorder-Checkout diente der technischen Bewertung. Die App enthält dessen Anwendungscode und dessen Signaturschlüssel nicht.

| Bestandteil | Verwendung und Herkunft | Lizenz |
|---|---|---|
| Android SDK | Plattform-APIs für AudioRecord, MediaCodec, Dienste, Kacheln und Android-Berechtigungen. Die SDK-Werkzeuge befinden sich auf dem Buildrechner. | Android SDK License Agreement für die Werkzeuge |
| AndroidX Media3 ExoPlayer 1.11.0, AndroidX Core 1.18.0 | Lokale Wiedergabe mit Tempo und Stille-Skip sowie lesende FileProvider-URIs. Aufgelöste Laufzeitabhängigkeiten sind mit `./gradlew :app:dependencies --configuration debugRuntimeClasspath` einsehbar. | Apache-2.0, jeweilige Hinweise in den AndroidX-Artefakten |
| Apache Commons Net 3.13.0 und Commons IO 2.21.0 | FTP und explizites FTPS. FTPS verwendet den Android-Vertrauensspeicher und prüft den Hostnamen. | Apache-2.0 |
| JSch 2.28.8, gepflegte mwiede-Version | SFTP mit vorgegebenem öffentlichem Hostschlüssel. | BSD und ISC gemäß gebündelter Lizenzdatei |
| Dropbox Android SDK und Core SDK 7.1.1 | Kontofreigabe mit PKCE und Rückkehr zu Audomo. | [MIT-Lizenz im APK](app/src/main/assets/licenses/Dropbox-SDK-LICENSE.txt) |
| OkHttp 4.12.0 und Okio 3.6.0 | HTTPS für WebDAV und Dropbox. | Apache-2.0 |
| Gradle Wrapper 9.6.0, AGP 9.5.2 | Gradle-Standardwrapper, unverändert aus dem lokalen Android-Studio-Projektwerkzeug übernommen. Die Wrapper-Konfiguration pinnt die Distribution samt SHA-256. | Apache-2.0 |
| JUnit 4.13.2 | Lokale JVM-Tests. | EPL-1.0 |
| Inter Display Light | Unveränderte TTF der offiziellen Version 4.1. SHA-256 des Release-ZIP ist `9883fdd4a49d4fb66bd8177ba6625ef9a64aa45899767dde3d36aa425756b11e`. | SIL OFL 1.1, die Lizenz liegt auch als APK-Asset bei |
| Audomo-Bildmarke | Ableitung der freigegebenen Rastervorlage aus dem Übergabepaket. Das unveränderte Original bleibt dort erhalten. | Markenasset des Projekts, Herkunft gemäß Übergabepaket |
| UI-Symbole | Android-Vektorpfade nach den Material-Icons für Aufnahme-, Such-, Sortier- und Wiedergabehandlungen. | Apache-2.0 |

Die benutzte Schriftdatei und ihre Lizenz stammen aus derselben Veröffentlichung. [Inter 4.1](https://github.com/rsms/inter/releases/tag/v4.1) · [Schriftlizenz](app/src/main/assets/licenses/Inter-OFL.txt)

Die [Liste aller aufgelösten Module](licenses/Laufzeitabhaengigkeiten.md) dokumentiert auch die transitiven Laufzeitabhängigkeiten dieser APK.
