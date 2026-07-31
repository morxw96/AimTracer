# AimTracer BLE GATT Specification

[Deutsch](BLE_GATT.md)

Protocol version: **1**

All multibyte values use **little-endian** byte order. Every notification is
exactly 20 bytes long and therefore works with the smallest commonly
available ATT payload. UUIDs and packet layouts are stable from protocol
version 1 onward.

## Services and characteristics

| Element | UUID | Properties |
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

A Control write contains exactly one command byte:

| Value | Command |
|---:|---|
| `0x01` | Start session, clear the queue, and arm |
| `0x02` | End session and disarm |
| `0x03` | Arm |
| `0x04` | Disarm |
| `0x05` | Trigger a manual test shot |
| `0x06` | Recalibrate the gyro zero; keep the device motionless |

## Config (20 bytes)

| Offset | Type | Content |
|---:|---|---|
| 0 | `u8` | Protocol version; must be `1` |
| 1 | `u8` | Trigger: `1` audio, `2` motion, `3` audio **and** motion |
| 2 | `u16` | IMU sample rate in Hz; read-only `416` in the MVP |
| 4 | `u8` | Live rate in Hz, valid range `5...50` |
| 5 | `u8` | Reserved, `0` |
| 6 | `u16` | Pre-trigger duration in ms, valid range `100...600` |
| 8 | `u16` | Post-trigger duration in ms, valid range `100...500` |
| 10 | `u16` | Microphone peak threshold, raw absolute PCM amplitude |
| 12 | `u16` | Acceleration-delta threshold, raw LSB |
| 14 | `u16` | Gyro threshold, raw LSB |
| 16 | `u8` | Audio/motion coincidence window in ms, `10...100` |
| 17 | `u8` | Refractory period in units of 10 ms, `30...250` |
| 18 | `u8` | Gyro range: `2` = ±500°/s; read-only |
| 19 | `u8` | Accelerometer range: `2` = ±8 g; read-only |

The firmware rejects the complete Config packet if any field is invalid.

> **CALIBRATION:** The three thresholds are useful starting values, not a
> finished LP300XT calibration. With the enclosure mounted, first collect
> data using the manual trigger, then compare live shots and dry firing.
> Finalize the thresholds only after those measurements.

## Status (20 bytes, packet type `0x01`)

| Offset | Type | Content |
|---:|---|---|
| 0 | `u8` | `0x01` |
| 1 | `u8` | Protocol version |
| 2 | `u8` | Status bits: connected, session, armed, capture, TX, calibrated, microphone |
| 3 | `u8` | Last error code |
| 4 | `u16` | IMU sample rate |
| 6 | `u16` | Number of samples currently in the ring buffer |
| 8 | `u16` | Shots captured since startup |
| 10 | `u16` | Dropped triggers |
| 12 | `u16` | Latest microphone peak |
| 14 | `u16` | Shot ID currently being transferred, otherwise `0` |
| 16 | `u16` | Current transfer sample index |
| 18 | `u8` | Firmware major version |
| 19 | `u8` | Firmware minor version |

## Live (20 bytes, packet type `0x10`)

| Offset | Type | Content |
|---:|---|---|
| 0 | `u8` | `0x10` |
| 1 | `u16` | Monotonic sequence number |
| 3 | `u32` | Device uptime in ms |
| 7 | `i16 × 3` | Gyro X/Y/Z, raw values after bias correction |
| 13 | `i16 × 3` | Accelerometer X/Y/Z, raw values |
| 19 | `u8` | Microphone peak, `peak >> 8` |

## Power (6 bytes)

The firmware updates this value at startup and once per second. In parallel,
`Battery Level` contains the standardized single percentage value from
`0...100`.

| Offset | Type | Content |
|---:|---|---|
| 0 | `u8` | Protocol version |
| 1 | `u8` | Estimated battery level `0...100%` |
| 2 | `u8` | Flags: bit 0 charging, bit 1 low (≤20%), bit 2 critical (≤5%), bit 3 USB/VBUS present, bit 4 raw `~CHG` signal active |
| 3 | `u16` | Measured battery voltage in mV |
| 5 | `u8` | Selected charge current in mA, currently `50` |

The percentage is approximated from LiPo voltage and is not a precise
capacity measurement while charging or under load.
`kBatteryCalibrationFactor` is the intended adjustment point for comparison
with a multimeter. The firmware keeps `P0.14/READ_BAT_ENABLE` LOW while
measuring.

Bit 0 is set only when both the nRF52840 hardware detector sees VBUS and the
charger reports an active charge cycle through `P0.17/~CHG`. Bit 4 is
diagnostic only.

## Shot transfer

A shot consists of `Meta`, `Sample × n`, and `End`. The app assembles packets
by shot ID. Since firmware 0.2, this channel uses confirmed ATT indications.
The next packet is sent only after confirmation from the phone. Packet count
and CRC additionally detect corruption or an incomplete transfer caused by a
disconnect.

Since firmware 0.4, trigger detection continues at 416 Hz while the
transferred shot window contains every fourth sensor value and therefore
reports a 104 Hz sample rate. With 500 ms of pre-trigger and 250 ms of
post-trigger data, this reduces the number of sample packets from about 312
to 78 without changing the packet layout or protocol version.

### Meta (20 bytes, packet type `0x20`)

| Offset | Type | Content |
|---:|---|---|
| 0 | `u8` | `0x20` |
| 1 | `u8` | Protocol version |
| 2 | `u16` | Shot ID |
| 4 | `u32` | Trigger uptime in ms |
| 8 | `u16` | Sample rate in Hz |
| 10 | `u16` | Sample count |
| 12 | `u16` | Trigger sample index |
| 14 | `u16` | Audio peak |
| 16 | `u16` | Maximum accelerometer-delta peak |
| 18 | `u16` | Maximum gyro peak |

### Sample (20 bytes, packet type `0x21`)

| Offset | Type | Content |
|---:|---|---|
| 0 | `u8` | `0x21` |
| 1 | `u16` | Shot ID |
| 3 | `u16` | Sample index |
| 5 | `i16 × 3` | Gyro X/Y/Z |
| 11 | `i16 × 3` | Accelerometer X/Y/Z |
| 17 | `u16` | Audio peak corresponding to this IMU sample |
| 19 | `u8` | Bit 0 set if this is the trigger sample |

Time relative to the trigger:

`t = (sampleIndex - triggerIndex) / sampleRateHz`

Gyro scaling at ±500°/s: `raw × 0.0175°/s`.

Accelerometer scaling at ±8 g: `raw × 0.000244 g`.

### End (20 bytes, packet type `0x22`)

| Offset | Type | Content |
|---:|---|---|
| 0 | `u8` | `0x22` |
| 1 | `u16` | Shot ID |
| 3 | `u16` | Sample count |
| 5 | `u32` | CRC-32/ISO-HDLC over the 14 raw bytes of every sample (`gyro...mic`) |
| 9 | `u8` | Flags, currently `0` |
| 10...19 |  | Reserved, `0` |
