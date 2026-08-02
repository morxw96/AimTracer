# AimTracer for Android

[Deutsch](README.de.md)

Native Android version of the AimTracer MVP, built with Kotlin and Jetpack
Compose. It uses the same versioned BLE GATT protocol as the iPhone app.
Firmware 0.2 or later is recommended for reliable shot transfer.

## Features

- BLE discovery and connection to the AimTracer service,
- sequential activation of all GATT notifications,
- confirmed BLE indications and reconstruction of complete 20-byte shot
  transfers with packet-count and CRC-32 validation,
- a live angular trace and per-shot pre/post-trigger trace,
- LP20, LP40, LP60, dry-fire, and free-training sessions,
- hold-stability, trigger-behavior, and follow-through metrics,
- per-metric and overall ranking within a session,
- progress, first/last-group trends, and comparison with up to five earlier
  sessions,
- atomic local JSON persistence,
- automatic German/English localization based on the phone language,
- Excel-compatible CSV, raw JSON, and A4 PDF export,
- display and editing of firmware trigger parameters,
- battery level, battery voltage, and charging-state display,
- visible acquisition and transfer progress after a trigger,
- manual test triggering and gyro recalibration.

## Requirements

- Android Studio with Android SDK 35,
- Java 17 or later; Android Studio includes a suitable runtime,
- Android 8.0 or later (API 26),
- a physical Android device with Bluetooth LE for sensor testing.

## Run from Android Studio

1. Open the `AimTracerAndroid` directory in Android Studio.
2. Wait for Gradle synchronization to finish.
3. Select an Android device.
4. Press `Run`.
5. Grant Bluetooth permission on first launch.

On Android 12 and later, AimTracer requests `BLUETOOTH_SCAN` and
`BLUETOOTH_CONNECT`. Up to Android 11, BLE scanning requires the location
permission used by those Android versions. AimTracer does not request general
file-storage access.

## Command-line build

From the Android project directory:

```sh
./gradlew clean testDebugUnitTest assembleDebug lintDebug
```

The installable Debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it with Android Debug Bridge:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

A publishable Release APK or App Bundle requires your own signing
configuration in Android Studio. The included Debug package is intended only
for testing on your own devices.

## Usage

1. Turn on the XIAO and leave it motionless while it initializes.
2. Open the `Live` tab, tap `Connect`, and select `AimTracer`.
3. Start an LP20, LP40, LP60, dry-fire, or free-training session.
4. After each detected trigger, the trace, three metrics, and current session
   rank appear.
5. End the session, optionally enter its Meyton total, and open it from the
   `Sessions` tab.
6. Review progress, ranking, and comparison with earlier sessions.
7. Select CSV, PDF, or raw JSON and choose a destination in the Android
   document picker.

The saved Meyton total appears in every export, and all LP40 rows fit on one
A4 table page. AimTracer
values are relative motion metrics, not predictions of hits or scores.

## Source layout

```text
app/src/main/java/de/aimtracer/android/
├── analysis/   Angular trace, metrics, ranking, and session comparison
├── ble/        BLE scan, GATT connection, and data reception
├── data/       Atomic session persistence
├── export/     CSV, JSON, and PDF generation
├── model/      Data models
├── protocol/   Binary protocol, CRC, and shot assembler
└── ui/         Jetpack Compose interface
```

The authoritative UUIDs and packet layouts are documented in
[`../../docs/BLE_GATT.en.md`](../../docs/BLE_GATT.en.md). Metric calculations
and interpretation are described in
[`../../docs/ANALYSIS_AND_EXPORT.en.md`](../../docs/ANALYSIS_AND_EXPORT.en.md).

## Calibration

All values that intentionally require practical tuning are marked
`CALIBRATION:` in the code. The most important points are:

- fixed mounting profile: PCB underside up, sensor side down, and USB-C
  facing the shooter,
- audio, accelerometer, and gyro thresholds in the firmware or Calibration
  tab,
- equal weighting of the three metrics in the comparison index.

The procedure for a mounted device is documented in
[`../../docs/CALIBRATION.en.md`](../../docs/CALIBRATION.en.md).

## Privacy

Sessions are stored in `filesDir/sessions.json` in private app storage. CSV,
JSON, and PDF files are created only on export and written exclusively to the
location selected in the Android document picker. AimTracer has no cloud or
other network functionality.

## Verified versions

- Gradle 8.12,
- Android Gradle Plugin 8.5.1,
- Kotlin 2.0.21,
- app 0.6.0,
- compileSdk/targetSdk 35 and minSdk 26,
- clean `testDebugUnitTest`, `assembleDebug`, and `lintDebug` runs.
