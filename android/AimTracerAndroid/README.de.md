# AimTracer für Android

[English](README.md)

Native Android-Version des AimTracer-MVP in Kotlin und Jetpack Compose. Sie
spricht dasselbe versionierte BLE-GATT-Protokoll wie die iPhone-App. Für die
zuverlässige Schussübertragung wird Firmware 0.2 oder neuer empfohlen.

## Enthalten

- BLE-Suche nach dem AimTracer-Service und Verbindungsaufbau,
- sequenzielle Aktivierung aller GATT-Benachrichtigungen,
- bestätigte BLE-Indications sowie Rekonstruktion kompletter
  20-Byte-Schusstransfers mit Anzahl- und CRC-32-Prüfung,
- Live-Winkelspur sowie Vor-/Nachlaufspur je Schuss,
- LP20-, LP40-, LP60-, Trocken- und freie Sessions,
- Kennwerte für Ruhig halten, Abzugsverhalten und Nachhalten,
- Rang je Kennwert und Gesamtrang innerhalb einer Session,
- Verlauf, Anfang-/Ende-Trend und Vergleich mit bis zu fünf früheren Sessions,
- atomare lokale JSON-Speicherung,
- automatische deutsche/englische Lokalisierung anhand der Handysprache,
- Excel-kompatibler CSV-, Rohdaten-JSON- und A4-PDF-Export,
- Anzeige und Änderung der Firmware-Triggerparameter,
- Anzeige von Akkustand, Akkuspannung und Ladevorgang,
- sichtbarer Aufnahme- und Übertragungsstatus nach einer Auslösung,
- manuelle Testauslösung und Gyro-Neukalibrierung.

## Voraussetzungen

- Android Studio mit Android SDK 35,
- Java 17 oder neuer; Android Studio bringt eine passende Laufzeit mit,
- Android 8.0 oder neuer (API 26),
- echtes Android-Gerät mit Bluetooth LE für den Sensortest.

## In Android Studio starten

1. Diesen Ordner `AimTracerAndroid` in Android Studio öffnen.
2. Die Gradle-Synchronisierung abwarten.
3. Ein Android-Gerät auswählen.
4. `Run` drücken.
5. Beim ersten Start Bluetooth erlauben.

Auf Android 12 und neuer fragt AimTracer nach `BLUETOOTH_SCAN` und
`BLUETOOTH_CONNECT`. Bis Android 11 benötigt der BLE-Scan die damalige
Standortberechtigung. AimTracer fordert keine allgemeine Dateiberechtigung an.

## Kommandozeilen-Build

Im Projektordner:

```sh
./gradlew clean testDebugUnitTest assembleDebug lintDebug
```

Die installierbare Debug-APK liegt anschließend hier:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Installation per Android Debug Bridge:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Für eine veröffentlichbare Release-APK oder ein App Bundle muss in Android
Studio eine eigene Signierung eingerichtet werden. Das mitgelieferte
Debug-Paket ist nur zum Testen auf eigenen Geräten gedacht.

## Bedienung

1. XIAO einschalten und ruhig liegen lassen.
2. Im Tab `Live` auf `Verbinden` tippen und `AimTracer` wählen.
3. Eine LP20-, LP40-, LP60-, Trocken- oder freie Session starten.
4. Nach jeder erkannten Auslösung erscheinen Spur, drei Kennwerte und der
   momentane Rang innerhalb der Session.
5. Die Session beenden, optional das Meyton-Gesamtergebnis eintragen und im
   Tab `Sessions` öffnen.
6. Dort Verlauf, Rangliste und Vergleich zu früheren Sessions ansehen.
7. Mit `CSV für Excel`, `PDF-Bericht` oder `Rohdaten als JSON` einen Zielort im Android-
   Dokumentdialog auswählen.

Das gespeicherte Meyton-Gesamtergebnis erscheint in allen Exporten. Eine
LP40-Tabelle passt vollständig auf eine A4-Seite. Die
AimTracer-Werte sind relative Bewegungskennwerte, keine Treffer- oder
Ringprognose.

## Quellstruktur

```text
app/src/main/java/de/aimtracer/android/
├── analysis/   Winkelspur, Kennwerte, Ranking und Sessionsvergleich
├── ble/        BLE-Scan, GATT-Verbindung und Datenempfang
├── data/       atomare Sessionpersistenz
├── export/     CSV-, JSON- und PDF-Erzeugung
├── model/      Datenmodelle
├── protocol/   Binärprotokoll, CRC und Schussassembler
└── ui/         Jetpack-Compose-Oberfläche
```

Die verbindlichen UUIDs und Paketlayouts stehen in
[`../../docs/BLE_GATT.md`](../../docs/BLE_GATT.md). Berechnung und
Interpretation der Kennwerte sind in
[`../../docs/ANALYSIS_AND_EXPORT.md`](../../docs/ANALYSIS_AND_EXPORT.md)
beschrieben.

## Kalibrierung

Alle bewusst noch praktisch zu bestimmenden Werte sind im Code mit
`CALIBRATION:` markiert. Besonders wichtig sind:

- festes Montageprofil: Platinenunterseite oben, Sensorseite unten und USB-C
  zum Schützen,
- Audio-, Accel- und Gyro-Schwellen in der Firmware beziehungsweise im
  Kalibrierungs-Tab,
- Exponent der persönlichen Baseline und 30/50/20-Gewichtung des Technikindex.

Die Schritte am montierten Gerät stehen in
[`../../docs/CALIBRATION.md`](../../docs/CALIBRATION.md).

## Datenschutz

Sessions werden in `filesDir/sessions.json` im privaten App-Speicher gesichert.
CSV, JSON und PDF entstehen erst beim Export und werden nur an den im
Android-Dokumentdialog gewählten Ort geschrieben. AimTracer besitzt keine
Cloud- oder sonstige Netzwerkfunktion.

## Geprüfter Stand

- Gradle 8.12,
- Android Gradle Plugin 8.5.1,
- Kotlin 2.0.21,
- App 0.7.2,
- compileSdk/targetSdk 35, minSdk 26,
- sauberer `testDebugUnitTest`, `assembleDebug` und `lintDebug`-Lauf.
