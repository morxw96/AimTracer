# AimTracer BLE-GATT-Spezifikation

[English](BLE_GATT.en.md)

Protokollversion: **1**

Alle Mehrbyte-Zahlen sind **Little Endian**. Alle Benachrichtigungen sind exakt
20 Byte lang und funktionieren damit auch mit der kleinsten üblichen
ATT-Nutzlast. UUIDs und Paketlayouts sind ab Version 1 stabil.

## Service und Characteristics

| Element | UUID | Eigenschaften |
|---|---|---|
| AimTracer Service | `7B8A0001-6D5B-4F0D-9C6A-3E0D6C0A1000` | Primary service |
| Status | `7B8A0002-6D5B-4F0D-9C6A-3E0D6C0A1000` | Read, Notify |
| Control | `7B8A0003-6D5B-4F0D-9C6A-3E0D6C0A1000` | Write |
| Live | `7B8A0004-6D5B-4F0D-9C6A-3E0D6C0A1000` | Notify |
| Shot | `7B8A0005-6D5B-4F0D-9C6A-3E0D6C0A1000` | Indicate |
| Config | `7B8A0006-6D5B-4F0D-9C6A-3E0D6C0A1000` | Read, Write |
| Power | `7B8A0007-6D5B-4F0D-9C6A-3E0D6C0A1000` | Read, Notify |
| Battery Service | `0000180F-0000-1000-8000-00805F9B34FB` | Primary service |
| Battery Level | `00002A19-0000-1000-8000-00805F9B34FB` | Read, Notify |

## Control

Ein Control-Schreibvorgang enthält genau ein Befehlsbyte:

| Wert | Befehl |
|---:|---|
| `0x01` | Session starten, Warteschlange leeren und scharf schalten |
| `0x02` | Session beenden und unscharf schalten |
| `0x03` | Scharf schalten |
| `0x04` | Unscharf schalten |
| `0x05` | Manuellen Testschuss auslösen |
| `0x06` | Gyro-Nullpunkt neu kalibrieren; Gerät dabei ruhig halten |

## Config (20 Byte)

| Offset | Typ | Inhalt |
|---:|---|---|
| 0 | `u8` | Protokollversion; muss `1` sein |
| 1 | `u8` | Trigger: `1` Audio, `2` Bewegung, `3` Audio **und** Bewegung |
| 2 | `u16` | IMU-Abtastrate in Hz; in MVP schreibgeschützt `416` |
| 4 | `u8` | Live-Rate in Hz, gültig `5...50` |
| 5 | `u8` | reserviert, `0` |
| 6 | `u16` | Vorlauf in ms, gültig `100...600` |
| 8 | `u16` | Nachlauf in ms, gültig `100...500` |
| 10 | `u16` | Mikrofon-Peakschwelle, rohe absolute PCM-Amplitude |
| 12 | `u16` | Beschleunigungs-Deltaschwelle, rohe LSB |
| 14 | `u16` | Gyro-Schwelle, rohe LSB |
| 16 | `u8` | Koinzidenzfenster Audio/Bewegung in ms, `10...100` |
| 17 | `u8` | Sperrzeit in Einheiten von 10 ms, `30...250` |
| 18 | `u8` | Gyro-Bereich: `2` = ±500 °/s; schreibgeschützt |
| 19 | `u8` | Accelerometer-Bereich: `2` = ±8 g; schreibgeschützt |

Die Firmware verwirft das gesamte Config-Paket, wenn ein Feld ungültig ist.

> **KALIBRIERUNG:** Die drei Schwellwerte sind brauchbare Startwerte, keine
> fertige LP300XT-Kalibrierung. Mit montiertem Gehäuse zunächst Daten per
> manuellem Trigger aufnehmen, dann echte Schüsse und Trockentraining
> vergleichen. Erst danach die Schwellen festschreiben.

## Status (20 Byte, Pakettyp `0x01`)

| Offset | Typ | Inhalt |
|---:|---|---|
| 0 | `u8` | `0x01` |
| 1 | `u8` | Protokollversion |
| 2 | `u8` | Statusbits: verbunden, Session, scharf, Capture, TX, kalibriert, Mikrofon |
| 3 | `u8` | letzter Fehlercode |
| 4 | `u16` | IMU-Abtastrate |
| 6 | `u16` | gefüllte Ringpuffer-Samples |
| 8 | `u16` | seit Start erfasste Schüsse |
| 10 | `u16` | verworfene Trigger |
| 12 | `u16` | letzter Mikrofonpeak |
| 14 | `u16` | aktuell übertragene Schuss-ID, sonst `0` |
| 16 | `u16` | aktuell übertragener Sample-Index |
| 18 | `u8` | Firmware-Major |
| 19 | `u8` | Firmware-Minor |

## Live (20 Byte, Pakettyp `0x10`)

| Offset | Typ | Inhalt |
|---:|---|---|
| 0 | `u8` | `0x10` |
| 1 | `u16` | laufende Sequenznummer |
| 3 | `u32` | Geräte-Uptime in ms |
| 7 | `i16 × 3` | Gyro X/Y/Z, Rohwerte nach Bias-Korrektur |
| 13 | `i16 × 3` | Accel X/Y/Z, Rohwerte |
| 19 | `u8` | Mikrofonpeak, `peak >> 8` |

## Power (6 Byte)

Die Firmware aktualisiert den Wert beim Start und danach jede Sekunde.
`Battery Level` enthält parallel dazu den standardisierten einzelnen
Prozentwert von `0...100`.

| Offset | Typ | Inhalt |
|---:|---|---|
| 0 | `u8` | Protokollversion |
| 1 | `u8` | geschätzter Akkustand `0...100 %` |
| 2 | `u8` | Flags: Bit 0 lädt, Bit 1 niedrig (≤20 %), Bit 2 kritisch (≤5 %), Bit 3 USB/VBUS vorhanden, Bit 4 rohes `~CHG`-Signal aktiv |
| 3 | `u16` | gemessene Akkuspannung in mV |
| 5 | `u8` | eingestellter Ladestrom in mA, derzeit `50` |

Die Prozentzahl wird aus der LiPo-Spannung angenähert und ist während des
Ladens sowie unter Last nicht als präzise Kapazitätsmessung zu verstehen.
`kBatteryCalibrationFactor` ist der vorgesehene Abgleichpunkt für ein
Multimeter. Die Firmware hält `P0.14/READ_BAT_ENABLE` beim Messen LOW.
Bit 0 wird nur gesetzt, wenn sowohl der nRF52840-Hardwaredetektor VBUS erkennt
als auch der Ladecontroller über `P0.17/~CHG` einen aktiven Ladevorgang meldet.
Bit 4 ist ausschließlich für die Diagnose vorgesehen.

## Schussübertragung

Ein Schuss besteht aus `Meta`, `Sample × n`, `End`. Die App baut nach
Schuss-ID zusammen. Seit Firmware 0.2 verwendet dieser Kanal bestätigte
ATT-Indications. Das nächste Paket wird erst nach Bestätigung des Telefons
gesendet. Anzahl und CRC decken zusätzlich beschädigte oder durch einen
Verbindungsabbruch unvollständige Transfers auf.

Seit Firmware 0.4 bleibt die Triggererkennung bei 416 Hz, während das
übertragene Schussfenster jeden vierten Sensorwert enthält und deshalb eine
Sample-Rate von 104 Hz meldet. Bei 500 ms Vor- und 250 ms Nachlauf sinkt die
Zahl der Sample-Pakete dadurch von ungefähr 312 auf 78, ohne das Paketlayout
oder die Protokollversion zu ändern.

### Meta (20 Byte, Pakettyp `0x20`)

| Offset | Typ | Inhalt |
|---:|---|---|
| 0 | `u8` | `0x20` |
| 1 | `u8` | Protokollversion |
| 2 | `u16` | Schuss-ID |
| 4 | `u32` | Trigger-Uptime in ms |
| 8 | `u16` | Sample-Rate in Hz |
| 10 | `u16` | Sample-Anzahl |
| 12 | `u16` | Index des Trigger-Samples |
| 14 | `u16` | Audio-Peak |
| 16 | `u16` | maximaler Accel-Deltapeak |
| 18 | `u16` | maximaler Gyro-Peak |

### Sample (20 Byte, Pakettyp `0x21`)

| Offset | Typ | Inhalt |
|---:|---|---|
| 0 | `u8` | `0x21` |
| 1 | `u16` | Schuss-ID |
| 3 | `u16` | Sample-Index |
| 5 | `i16 × 3` | Gyro X/Y/Z |
| 11 | `i16 × 3` | Accel X/Y/Z |
| 17 | `u16` | Audio-Peak zum IMU-Sample |
| 19 | `u8` | Bit 0 gesetzt, wenn dies das Trigger-Sample ist |

Zeit relativ zum Trigger:

`t = (sampleIndex - triggerIndex) / sampleRateHz`

Gyro-Skalierung bei ±500 °/s: `raw × 0,0175 °/s`.

Accel-Skalierung bei ±8 g: `raw × 0,000244 g`.

### End (20 Byte, Pakettyp `0x22`)

| Offset | Typ | Inhalt |
|---:|---|---|
| 0 | `u8` | `0x22` |
| 1 | `u16` | Schuss-ID |
| 3 | `u16` | Sample-Anzahl |
| 5 | `u32` | CRC-32/ISO-HDLC über die 14 Rohbytes jedes Samples (`gyro...mic`) |
| 9 | `u8` | Flags, derzeit `0` |
| 10...19 |  | reserviert, `0` |
