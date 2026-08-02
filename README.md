# AimTracer MVP

[Deutsch](README.de.md)

Current release: app **0.6.0**, firmware **0.5**.

AimTracer is an open-source trigger and hold-error monitor for the **Seeed
Studio XIAO nRF52840 Sense**. The board measures pistol movement with its
integrated LSM6DS3TR-C, detects the shot from a combination of motion and the
PDM microphone, and sends complete shot windows over Bluetooth LE to native
iPhone and Android apps.

The MVP includes:

- 416 Hz IMU acquisition, gyro zeroing, and compact 104 Hz shot windows,
- a 16 kHz PDM microphone envelope,
- configurable audio/motion coincidence detection,
- a ring buffer with 500 ms of pre-trigger and 250 ms of post-trigger data,
- two shot slots for acquisition and concurrent BLE transfer,
- a fixed, versioned 20-byte GATT protocol with CRC-32,
- confirmed ATT indications for lossless shot transfer since firmware 0.2,
- Core Bluetooth connectivity on iPhone and Android BLE connectivity,
- native SwiftUI and Kotlin/Jetpack Compose interfaces,
- full shot traces and hold, trigger, and follow-through metrics,
- a local session history written atomically as JSON,
- LP20, LP40, LP60, dry-fire, and free-training sessions,
- relative shot rankings and progress across a complete session,
- comparison with up to five earlier sessions of the same program,
- Excel-compatible CSV, raw JSON, and print-ready PDF exports,
- adjustable trigger parameters and manual test triggering,
- battery level, battery voltage, and charging status on iOS and Android,
- automatic German and English localization of the apps, status messages,
  CSV tables, and PDF reports based on the phone language,
- a `by Moritz Wenzel` credit in the settings screen.

## Project structure

```text
AimTracerMVP/
├── CHANGELOG.md                  Release notes
├── firmware/AimTracerFirmware/  Arduino firmware
├── ios/AimTracer.xcodeproj/     Ready-to-build Xcode project
├── ios/AimTracer/               Swift and SwiftUI sources
├── android/AimTracerAndroid/    Ready-to-build Android Studio project
└── docs/
    ├── BLE_GATT.en.md           Authoritative protocol specification
    ├── CALIBRATION.en.md        On-firearm field calibration
    ├── ANALYSIS_AND_EXPORT.en.md Metrics, ranking, and export
    └── TROUBLESHOOTING.en.md    Diagnostics and firmware upgrade notes
```

## Requirements

### Hardware

- Seeed Studio XIAO nRF52840 **Sense** (not the non-Sense variant),
- a 3.7 V LiPo connected to the battery pads,
- a rigidly mounted enclosure,
- a USB-C data cable for the initial flash.

### Firmware

- Arduino IDE 2 or Arduino CLI,
- **Seeed nRF52 mbed-enabled Boards 2.9.3**,
- **ArduinoBLE 2.1.0**,
- **Seeed Arduino LSM6DS3 2.0.7**.

The PDM module is already included in the Seeed mbed core.

### iPhone app

- macOS with Xcode,
- iOS 17 or later,
- a physical iPhone for BLE testing; the simulator can compile the app but
  cannot meaningfully test the XIAO as a real BLE peripheral,
- a free or paid Apple Developer signing identity for installation on your
  own iPhone.

### Android app

- Android Studio with Android SDK 35,
- Android 8.0 or later (API 26),
- a physical Android device for BLE testing.

## Build and flash the firmware

### Arduino IDE

1. Add this Additional Boards Manager URL:
   `https://files.seeedstudio.com/arduino/package_seeeduino_boards_index.json`
2. Install `Seeed nRF52 mbed-enabled Boards` version 2.9.3 from Boards
   Manager.
3. Install `ArduinoBLE` 2.1.0 and `Seeed Arduino LSM6DS3` 2.0.7 from Library
   Manager.
4. Open `firmware/AimTracerFirmware/AimTracerFirmware.ino`.
5. Select `XIAO nRF52840 Sense (No Updates)`.
6. Compile and upload.

If the board does not appear as a port, press Reset twice in quick succession
and upload again.

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

Replace the port in the final command.

## Build the iPhone app

1. Open `ios/AimTracer.xcodeproj` in Xcode.
2. Open the `AimTracer` target and select `Signing & Capabilities`.
3. Select your development team and, if necessary, change the bundle ID
   `de.aimtracer.mvp`.
4. Select the connected iPhone as the destination.
5. Run the app and grant Bluetooth access.

The app only requests `NSBluetoothAlwaysUsageDescription`. Background
Bluetooth operation is intentionally disabled in the MVP, so keep the app in
the foreground during a session.

## Build the Android app

1. Open `android/AimTracerAndroid` in Android Studio.
2. Wait for Gradle synchronization to finish.
3. Connect an Android device over USB and run the app.
4. Grant Bluetooth access.

Alternatively, build the verified debug package from the project directory:

```sh
cd android/AimTracerAndroid
./gradlew testDebugUnitTest assembleDebug lintDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. More
detailed instructions are available in
[`android/AimTracerAndroid/README.md`](android/AimTracerAndroid/README.md).

## First test

1. Turn on the XIAO and place it on a stable surface.
2. In the app's Live tab, connect to `AimTracer`.
3. Wait until the app reports `Sensor ready`.
4. Start a session and select a program such as `LP20` or `LP40`.
5. Use a manual test trigger from the Calibration screen.
6. After the post-trigger window, the shot appears with a blue pre-trigger
   trace and an orange post-trigger trace.
7. Continue with the field-calibration procedure in
   [`docs/CALIBRATION.en.md`](docs/CALIBRATION.en.md).

If the app reports an incomplete shot transfer, follow
[`docs/TROUBLESHOOTING.en.md`](docs/TROUBLESHOOTING.en.md) and verify that the
Live screen reports firmware 0.2 or later.

## Battery and charging

The BQ25101 charger operates independently of the firmware. Connecting USB-C
charges the attached single-cell LiPo. Firmware 0.5 explicitly drives
`P0.13/HICHG` HIGH, selecting the conservative **50 mA** charge current. A
500 mAh battery therefore takes roughly ten hours plus the slower final
charging phase.

Once per second, the firmware reads the battery voltage through the board's
voltage divider and publishes:

- battery percentage through the standard BLE Battery Service,
- battery voltage, charging state, and selected charge current through the
  custom `Power` characteristic,
- a low warning below 20% and a critical warning at or below 5%.

The percentage is a coarse estimate derived from LiPo voltage and may vary
under load or while charging. `kBatteryCalibrationFactor`, marked
`CALIBRATION:` in the firmware, is the adjustment point for comparison with a
multimeter.

The open-drain charge-status output is read with an internal pull-up. The
`charging` state is additionally gated by the nRF52840 hardware VBUS detector,
so it cannot become active without actual USB voltage. USB and charger-state
changes are reported within about one second. If USB is present but the
charger is not active, the apps display `USB connected` instead.

## Delay after a shot

Trigger detection continues to run at 416 Hz. Firmware 0.4 and later retain
every fourth sample for the stored shot window and transfer it at 104 Hz. A
standard shot with 500 ms of pre-trigger and 250 ms of post-trigger data
therefore requires about 78 confirmed sample packets instead of 312. The apps
show visible acquisition and transfer progress during this process.

Trace and metric calculation takes only a few milliseconds. Most of the
remaining delay comes from the 250 ms post-trigger window and confirmed BLE
transfer, whose duration depends on the phone and BLE connection interval.

## Session analysis and export

After every shot, the Live screen shows its rank for hold stability, trigger
behavior, and follow-through within the active session. The Session screen
contains:

- mean and best value for each metric,
- progress across all shots,
- a comparison between the first and last group of up to ten shots,
- a shot list sortable by sequence or rank,
- a comparison with up to five earlier sessions of the same program.

The Session screen can export:

- a UTF-8 CSV using German Excel-compatible semicolon and decimal-comma
  formatting,
- an A4 PDF with a summary, progress chart, and complete shot table,
- JSON containing every unmodified IMU/microphone sample and mounting metadata.

When a session is stopped, the app optionally asks for its Meyton total and
includes it in the session view, CSV, JSON, and PDF. All 40 LP40 rows fit on
one A4 table page. The exact
calculation is documented in
[`docs/ANALYSIS_AND_EXPORT.en.md`](docs/ANALYSIS_AND_EXPORT.en.md).

## Calibration points

Intentional placeholders are marked with `CALIBRATION:` in the code:

- firmware: microphone, accelerometer, and gyro thresholds,
- apps: fixed mounting profile with the PCB underside up, sensor side down,
  and USB-C facing the shooter,
- apps: equal weighting of the three metrics in the relative comparison
  index.

All trigger thresholds can be changed in the apps and are sent to the running
firmware through the Config characteristic. In the MVP, they are not yet
persisted in the XIAO flash across restarts.

## Protocol and data

The complete specification is in
[`docs/BLE_GATT.en.md`](docs/BLE_GATT.en.md). Shot packets use a monotonically
increasing ID, sample indices, and CRC-32. The apps reject incomplete or
corrupt transfers with a visible error.

Sessions are stored exclusively in the private app directory as
`sessions.json`. AimTracer performs no cloud or network transfer.

## MVP limitations

- AimTracer shows relative angular movement, not the aiming point on the
  target.
- It is not a SCATT replacement and does not calculate an actual score.
- Hold, trigger, and follow-through values are comparable RMS measurements,
  not sports-science-validated scores.
- Rank and comparison index are valid only within a session and do not
  predict hits or scores.
- Automatic reconnection, background recording, BLE firmware updates, and
  persistent configuration are planned extensions.
- Firmware and apps have been compile-tested; trigger thresholds and axis
  mapping still require the intended practical test on a mounted device.

## Verification

The included release was verified with:

- firmware: Seeed mbed 2.9.3, ArduinoBLE 2.1.0,
  Seeed Arduino LSM6DS3 2.0.7, FQBN
  `Seeeduino:mbed:xiaonRF52840Sense`,
- iOS Debug and Release builds: Xcode 26.6, generic iOS Simulator destination,
  deployment target iOS 17,
- Android: clean Debug build, unit tests, and Android Lint with Android SDK 35,
  minSdk 26, and targetSdk 35.

## Official references

- [Seeed: XIAO nRF52840 Sense getting started and hardware](https://wiki.seeedstudio.com/XIAO_BLE/)
- [Seeed: battery-voltage measurement while charging](https://wiki.seeedstudio.com/battery_charging_considerations/)
- [Seeed: IMU usage](https://wiki.seeedstudio.com/XIAO-BLE-Sense-IMU-Usage/)
- [Seeed: PDM microphone](https://wiki.seeedstudio.com/XIAO-BLE-Sense-PDM-Usage/)
- [Seeed: official LSM6DS3 Arduino library](https://github.com/Seeed-Studio/Seeed_Arduino_LSM6DS3)
- [Nordic: nRF52840 Product Specification](https://docs.nordicsemi.com/r/bundle/ps_nrf52840/page/keyfeatures_html5.html)
- [Apple: transferring data with Core Bluetooth](https://developer.apple.com/documentation/corebluetooth/transferring-data-between-bluetooth-low-energy-devices)
- [Apple: Bluetooth privacy description](https://developer.apple.com/documentation/bundleresources/information-property-list/nsbluetoothalwaysusagedescription)
- [Android: Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [Android: Bluetooth Low Energy](https://developer.android.com/develop/connectivity/bluetooth/ble/ble-overview)
- [Android: Jetpack Compose BOM](https://developer.android.com/develop/ui/compose/bom)
- [Android: saving documents with the Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files)

## License

MIT. See `LICENSE`.
