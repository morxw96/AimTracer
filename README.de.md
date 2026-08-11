# AimTracer MVP

[English](README.md)

Aktueller Stand: App **0.7.2**, Firmware **0.6**.

AimTracer ist ein quelloffener Abzugs- und Haltefehler-Monitor für den **Seeed
Studio XIAO nRF52840 Sense**. Das Board erfasst die Pistolenbewegung mit dem
integrierten LSM6DS3TR-C, erkennt die Auslösung gemeinsam über Bewegung und
PDM-Mikrofon und sendet komplette Schussfenster per Bluetooth LE an native
Apps für iPhone und Android.

Das MVP enthält:

- Firmware mit 416-Hz-IMU-Erfassung, Gyro-Nullung und kompakten
  104-Hz-Schussfenstern,
- PDM-Mikrofon-Hüllkurve bei 16 kHz,
- konfigurierbare Audio-/Bewegungs-Koinzidenzerkennung,
- 500 ms Vorlauf und 250 ms Nachlauf im Ringpuffer,
- zwei Schussslots für Erfassung und parallele BLE-Übertragung,
- fest versioniertes 20-Byte-GATT-Protokoll mit CRC-32,
- bestätigte ATT-Indications für verlustfreie Schussübertragung ab Firmware 0.2,
- CoreBluetooth-Verbindung auf dem iPhone und Android-BLE-Verbindung,
- native Oberflächen in SwiftUI und Kotlin/Jetpack Compose,
- vollständige Schussspur, Halte-/Auslöse-/Nachhaltewerte,
- lokale, atomar geschriebene JSON-Sessionhistorie,
- LP20-, LP40-, LP60-, Trocken- und freie Trainingssessions,
- persönlicher Technikindex von 0 bis 100 und sessionübergreifender Verlauf,
- Vergleich mit bis zu fünf früheren Sessions desselben Programms,
- Excel-kompatibler CSV-, Rohdaten-JSON- und druckfertiger PDF-Export,
- einstellbare Triggerparameter und manuelle Testauslösung,
- Akkustand, Akkuspannung und Ladevorgang in iOS und Android,
- dauerhafte X/Y-Grapheninvertierung für alternative Montagerichtungen,
- automatisches nRF52840-System-OFF nach zwei Stunden Inaktivität im Akkubetrieb,
- automatische deutsche und englische Lokalisierung von Apps,
  Statusmeldungen, CSV-Tabellen und PDF-Berichten anhand der Handysprache,

## Projektstruktur

```text
AimTracerMVP/
├── CHANGELOG.de.md              Versionshinweise
├── firmware/AimTracerFirmware/   Arduino-Firmware
├── ios/AimTracer.xcodeproj/      fertiges Xcode-Projekt
├── ios/AimTracer/                Swift-/SwiftUI-Quellen
├── android/AimTracerAndroid/      fertiges Android-Studio-Projekt
└── docs/
    ├── BLE_GATT.md              verbindliche Protokollspezifikation
    ├── CALIBRATION.md           Feldkalibrierung an der Waffe
    ├── ANALYSIS_AND_EXPORT.md   Kennwerte, Ranking und Export
    └── TROUBLESHOOTING.md       Diagnose und Firmware-0.2-Upgrade
```

## Voraussetzungen

### Hardware

- Seeed Studio XIAO nRF52840 **Sense** (nicht die Variante ohne „Sense“),
- 3,7-V-LiPo am Batterieanschluss,
- starr montiertes Gehäuse,
- USB-C-Datenkabel zum ersten Flashen.

### Firmware

- Arduino IDE 2 oder Arduino CLI,
- **Seeed nRF52 mbed-enabled Boards 2.9.3**,
- **ArduinoBLE 2.1.0**,
- **Seeed Arduino LSM6DS3 2.0.7**.

Das PDM-Modul kommt bereits mit dem Seeed-mbed-Core.

### iPhone-App

- macOS mit Xcode,
- iOS 17 oder neuer,
- ein echtes iPhone für den BLE-Test; der Simulator kompiliert die App, kann
  das XIAO aber nicht sinnvoll als reales BLE-Gerät testen,
- kostenlose oder bezahlte Apple-Developer-Signierung für die Installation
  auf dem eigenen iPhone.

### Android-App

- Android Studio mit Android SDK 35,
- Android 8.0 oder neuer (API 26),
- ein echtes Android-Gerät für den BLE-Test.

## Firmware bauen und flashen

### Arduino IDE

1. In den Einstellungen als zusätzliche Board-URL eintragen:
   `https://files.seeedstudio.com/arduino/package_seeeduino_boards_index.json`
2. Im Boardverwalter `Seeed nRF52 mbed-enabled Boards` in Version 2.9.3
   installieren.
3. Über den Bibliotheksverwalter `ArduinoBLE` 2.1.0 und
   `Seeed Arduino LSM6DS3` 2.0.7 installieren.
4. `firmware/AimTracerFirmware/AimTracerFirmware.ino` öffnen.
5. Board `XIAO nRF52840 Sense (No Updates)` auswählen.
6. Kompilieren und hochladen.

Falls das Board nicht als Port erscheint, Reset zweimal schnell drücken und
erneut hochladen.

### Arduino CLI

```sh
arduino-cli config add board_manager.additional_urls \
  https://files.seeedstudio.com/arduino/package_seeeduino_boards_index.json
arduino-cli core update-index
arduino-cli core install Seeeduino:mbed@2.9.3
arduino-cli lib install ArduinoBLE@2.1.0
arduino-cli lib install "Seeed Arduino LSM6DS3"@2.0.7
arduino-cli compile \
  --fqbn Seeeduino:mbed:xiaonRF52840Sense \
  firmware/AimTracerFirmware
arduino-cli upload \
  --fqbn Seeeduino:mbed:xiaonRF52840Sense \
  --port /dev/cu.usbmodemXXXX \
  firmware/AimTracerFirmware
```

Den Port im letzten Befehl anpassen.

## iPhone-App bauen

1. `ios/AimTracer.xcodeproj` in Xcode öffnen.
2. Target `AimTracer` → `Signing & Capabilities` öffnen.
3. Das eigene Team wählen und bei Bedarf die Bundle-ID
   `de.aimtracer.mvp` ändern.
4. Das angeschlossene iPhone als Ziel wählen.
5. App starten und den Bluetooth-Zugriff erlauben.

Die App verlangt nur `NSBluetoothAlwaysUsageDescription`; ein
Bluetooth-Hintergrundmodus ist im MVP absichtlich nicht aktiviert. Während
einer Session sollte die App im Vordergrund bleiben.

## Android-App bauen

1. `android/AimTracerAndroid` in Android Studio öffnen.
2. Die Gradle-Synchronisierung abwarten.
3. Ein Android-Gerät per USB verbinden und die App starten.
4. Bluetooth-Zugriff erlauben.

Alternativ lässt sich das geprüfte Debug-Paket im Projektordner bauen:

```sh
cd android/AimTracerAndroid
./gradlew testDebugUnitTest assembleDebug lintDebug
```

Die APK liegt danach unter
`app/build/outputs/apk/debug/app-debug.apk`. Eine ausführlichere Anleitung
steht in
[`android/AimTracerAndroid/README.de.md`](android/AimTracerAndroid/README.de.md).

## Erster Test

1. XIAO einschalten und ruhig ablegen.
2. In der App im Live-Tab oben rechts `AimTracer` verbinden.
3. Warten, bis „Sensor bereit“ erscheint.
4. Session starten und beispielsweise `LP20` oder `LP40` auswählen.
5. Unter `Kalibrierung` zunächst einen manuellen Testtrigger setzen.
6. Nach dem Nachlauf erscheint der Schuss mit blauer Vor- und oranger
   Nachlaufspur.
7. Danach die Feldkalibrierung in
   [`docs/CALIBRATION.md`](docs/CALIBRATION.md) durchführen.

Bei einer Meldung über eine unvollständige Schussübertragung die Schritte in
[`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md) verwenden und prüfen,
dass in der Live-Ansicht mindestens Firmware `0.2` angezeigt wird.

## Akku und Laden

Der BQ25101-Ladechip arbeitet unabhängig von der Firmware: Sobald USB-C
angeschlossen ist, lädt er den angeschlossenen Einzellen-LiPo. Firmware 0.6
setzt `P0.13/HICHG` ausdrücklich auf HIGH und wählt damit den konservativen
Ladestrom von **50 mA**. Bei einem 500-mAh-Akku sind rechnerisch etwa zehn
Stunden zuzüglich der langsameren Ladeendphase zu erwarten.

Die Firmware liest die Akkuspannung jede Sekunde über den vorgesehenen
Spannungsteiler ein und veröffentlicht:

- Akkustand in Prozent über den standardisierten BLE Battery Service,
- Akkuspannung, Ladestatus und gewählten Ladestrom über `Power`,
- Warnstatus unter 20 % und kritischen Status bis 5 %.

Die Prozentzahl ist eine grobe LiPo-Spannungsschätzung. Unter Last und während
des Ladens kann sie abweichen. Für einen Abgleich mit dem Multimeter ist
`kBatteryCalibrationFactor` in der Firmware mit `CALIBRATION:` markiert.

Der offene Ladezustandsausgang wird mit einem internen Pull-up gelesen. Die
Anzeige „lädt“ wird zusätzlich mit dem hardwareseitigen VBUS-Detektor des
nRF52840 verriegelt: Ohne tatsächlich erkannte USB-Spannung kann der Status
nicht aktiv werden. Änderungen von USB und Ladestatus werden innerhalb von
ungefähr einer Sekunde übertragen. Ist USB vorhanden, aber der Ladecontroller
nicht aktiv, zeigen die Apps stattdessen „USB angeschlossen“.

Im Akkubetrieb wechselt Firmware 0.6 nach zwei Stunden ohne Schuss,
BLE-Befehl, Einstellungsänderung oder Verbindungswechsel in nRF52840 System
OFF. Mikrofon und IMU werden zuvor abgeschaltet. Mit USB-Versorgung bleibt der
Auto-Schlaf aus. Da System OFF auch Bluetooth deaktiviert, wird das fertige
Gerät durch Aus-/Einschalten am Schiebeschalter oder mit der Reset-Taste des
XIAO wieder aufgeweckt.

## Anzeigezeit nach dem Schuss

Die Triggererkennung läuft weiterhin mit 416 Hz. Für das gespeicherte
Schussfenster übernimmt Firmware 0.4 oder neuer jeden vierten Messwert und überträgt damit
104 Hz. Ein Standardschuss mit 500 ms Vor- und 250 ms Nachlauf benötigt so nur
noch ungefähr 78 statt 312 bestätigte Sample-Pakete. Die Apps zeigen während
Aufnahme und Übertragung zusätzlich einen sichtbaren Fortschrittsstatus.

Die Berechnung der Spur und Kennwerte dauert nur wenige Millisekunden. Die
verbleibende Wartezeit entsteht hauptsächlich durch den 250-ms-Nachlauf und
die bestätigte BLE-Übertragung, deren Dauer vom Telefon und
BLE-Verbindungsintervall abhängt.

## Sessionauswertung und Export

Nach jedem Schuss zeigt die Live-Ansicht den persönlichen Technikindex für
„Ruhig halten“, „Abzugsverhalten“ und „Nachhalten“.
Die Sessionansicht enthält:

- Mittelwert und Bestwert je Kennwert,
- einen Verlauf über alle Schüsse,
- den Trend der ersten gegen die letzten bis zu zehn Schüsse,
- eine nach Reihenfolge oder Rang sortierbare Schussliste,
- den Vergleich mit bis zu fünf früheren Sessions desselben Programms,
- ein Balkendiagramm der letzten zwölf Sessions desselben Programms.

Die ersten drei abgeschlossenen Sessions jedes Programms bilden die persönliche Baseline. Bis
dahin kennzeichnet die App den Index als Einlernphase. `50` entspricht der
persönlichen Referenz; höhere Werte bedeuten weniger Bewegung. Der
Gesamtindex gewichtet Halten mit 30 %, den Abzug mit 50 % und Nachhalten mit
20 %. Er ist ausdrücklich keine Vorhersage der Ringzahl.

Über die Exportaktionen der Sessionansicht lassen sich drei Dateien erzeugen:

- eine UTF-8-CSV mit deutschem Semikolon-/Dezimalkommaformat für Excel,
- ein A4-PDF mit Zusammenfassung, Verlauf und vollständiger Schusstabelle,
- ein JSON mit allen unveränderten IMU-/Mikrofonsamples und Montageprofil.

Beim Beenden fragt die App optional nach dem Meyton-Gesamtergebnis und nimmt
es in Sessionansicht, CSV, JSON und PDF auf. Eine LP40-Schusstabelle passt
vollständig auf eine A4-Tabellenseite. Die
genaue Berechnung ist in
[`docs/ANALYSIS_AND_EXPORT.md`](docs/ANALYSIS_AND_EXPORT.md) dokumentiert.

## Wichtige Kalibrierstellen

Im Code sind die bewussten Platzhalter mit `CALIBRATION:` markiert:

- Firmware: Mikrofon-, Accel- und Gyro-Schwellen,
- App: festes Montageprofil mit Platinenunterseite oben, Sensorseite unten
  und USB-C zum Schützen,
- App: Exponent der persönlichen Baseline und 30/50/20-Gewichtung des
  Technikindex,
- Firmware: zweistündiges Auto-Schlaf-Intervall.

Alle Schwellen lassen sich in der App ändern und werden über die Config-
Characteristic an die laufende Firmware geschickt. Sie werden im MVP nach
einem Neustart noch nicht im Flash des XIAO gespeichert.

## Protokoll und Daten

Die vollständige Spezifikation steht in
[`docs/BLE_GATT.md`](docs/BLE_GATT.md). Schusspakete verwenden eine laufende
ID, Sample-Indizes und CRC-32. Die App verwirft unvollständige oder beschädigte
Transfers mit einer sichtbaren Fehlermeldung.

Sessions liegen ausschließlich lokal im privaten App-Verzeichnis als
`sessions.json`. Es gibt keine Cloud- oder Netzwerkübertragung.

## Grenzen des MVP

- AimTracer zeigt relative Winkelbewegung, nicht den Zielpunkt auf der Scheibe.
- Es ist kein SCATT-Ersatz und berechnet keine tatsächliche Ringzahl.
- Die Halte-/Auslöse-/Nachhaltewerte sind vergleichbare RMS-Messwerte, keine
  sportwissenschaftlich validierten Scores.
- Der persönliche Technikindex ist nur innerhalb desselben Trainingsprogramms
  vergleichbar und keine Treffer- oder Ringprognose.
- Automatisches Wiederverbinden, Hintergrundaufzeichnung, Firmware-Update per
  BLE und Konfigurationsspeicherung im Flash sind Folgeausbaustufen.
- Firmware und Apps sind kompiliert geprüft. Die Trigger-Schwellen benötigen
  weiter praktische Abstimmung; App 0.7.2 nutzt standardmäßig die an der
  Montage geprüften Achsen `Rollen = gx`, `rechts = +gz` und `oben = +gy` mit
  optionaler Grapheninvertierung.

## Verifikation

Der mitgelieferte Stand wurde geprüft mit:

- Firmware-Build: Seeed mbed 2.9.3, ArduinoBLE 2.1.0,
  Seeed Arduino LSM6DS3 2.0.7, FQBN
  `Seeeduino:mbed:xiaonRF52840Sense`,
- iOS-Debug- und Release-Build: Xcode 26.6, generisches iOS-Simulatorziel,
  Deployment Target iOS 17,
- Android: sauberer Debug-Build, Unit-Tests und Android Lint mit Android SDK 35,
  minSdk 26 und targetSdk 35.

## Offizielle Referenzen

- [Seeed: XIAO nRF52840 Sense – Einstieg und Hardware](https://wiki.seeedstudio.com/XIAO_BLE/)
- [Seeed: Hinweise zur Akkuspannungsmessung beim Laden](https://wiki.seeedstudio.com/battery_charging_considerations/)
- [Seeed: IMU-Nutzung](https://wiki.seeedstudio.com/XIAO-BLE-Sense-IMU-Usage/)
- [Seeed: PDM-Mikrofon](https://wiki.seeedstudio.com/XIAO-BLE-Sense-PDM-Usage/)
- [Seeed: offizielle LSM6DS3-Arduino-Bibliothek](https://github.com/Seeed-Studio/Seeed_Arduino_LSM6DS3)
- [Nordic: nRF52840 Product Specification](https://docs.nordicsemi.com/r/bundle/ps_nrf52840/page/keyfeatures_html5.html)
- [Apple: Datenübertragung mit Core Bluetooth](https://developer.apple.com/documentation/corebluetooth/transferring-data-between-bluetooth-low-energy-devices)
- [Apple: Bluetooth-Datenschutzhinweis](https://developer.apple.com/documentation/bundleresources/information-property-list/nsbluetoothalwaysusagedescription)
- [Android: Bluetooth-Berechtigungen](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [Android: Bluetooth Low Energy](https://developer.android.com/develop/connectivity/bluetooth/ble/ble-overview)
- [Android: Jetpack Compose BOM](https://developer.android.com/develop/ui/compose/bom)
- [Android: Dokumente über den Storage Access Framework speichern](https://developer.android.com/training/data-storage/shared/documents-files)

## Lizenz

MIT, siehe `LICENSE`.
