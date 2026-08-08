#include <ArduinoBLE.h>
#include <LSM6DS3.h>
#include <PDM.h>
#include <Wire.h>
#include <nrf.h>

#include "AimTracerProtocol.h"

using AimTracerProtocol::Command;
using AimTracerProtocol::PacketType;
using AimTracerProtocol::TriggerMode;

namespace {

constexpr uint16_t kSampleRateHz = 416;
// Detection stays at 416 Hz. Stored shot windows are decimated to 104 Hz,
// reducing the reliable BLE indication count by 75%.
constexpr uint8_t kCaptureDecimation = 4;
constexpr uint16_t kCaptureSampleRateHz =
    kSampleRateHz / kCaptureDecimation;
static_assert(
    kSampleRateHz % kCaptureDecimation == 0,
    "Capture rate must divide the IMU sample rate");
constexpr uint16_t kRingCapacity = 512;
constexpr uint16_t kMaxShotSamples = 480;
constexpr uint8_t kShotSlotCount = 2;
constexpr uint16_t kCalibrationSamples = 256;
constexpr uint16_t kPdmBufferSamples = 256;
// Shot packets use confirmed ATT indications. Their confirmation is the flow
// control, so only a small cooperative pause is needed between packets.
constexpr uint16_t kTxIntervalMs = 1;
constexpr uint16_t kStatusIntervalMs = 250;
constexpr uint32_t kPowerIntervalMs = 1000;
// CALIBRATION: inactivity means no shot, BLE command/config write, or
// connection-state change. System OFF is entered only while running on LiPo.
constexpr uint32_t kAutoSleepAfterMs = 2UL * 60UL * 60UL * 1000UL;

// XIAO nRF52840 Sense pins in the Seeed mbed 2.9.3 variant:
// D21 = P0.13/HICHG, D22 = P0.17/~CHG.
constexpr uint8_t kChargeCurrentPin = 21;
constexpr uint8_t kChargeStatusPin = 22;
constexpr uint8_t kChargeCurrentMa = 50;
constexpr uint8_t kBatteryAdcSamples = 16;
constexpr float kBatteryAdcFullScaleMv = 3300.0F;
constexpr float kBatteryDividerScale = 1510.0F / 510.0F;
// CALIBRATION: compare the reported millivolts with a multimeter and adjust.
constexpr float kBatteryCalibrationFactor = 1.0F;

// Fixed sensor ranges documented in BLE_GATT.md.
constexpr uint16_t kGyroRangeDps = 500;
constexpr uint8_t kGyroRangeCode = 2;
constexpr uint8_t kAccelRangeG = 8;
constexpr uint8_t kAccelRangeCode = 2;

enum ErrorCode : uint8_t {
  ErrorNone = 0,
  ErrorImuInit = 1,
  ErrorPdmInit = 2,
  ErrorBleInit = 3,
  ErrorInvalidConfig = 4,
  ErrorImuRead = 5,
};

enum class SlotState : uint8_t {
  Free,
  Capturing,
  Ready,
  Sending,
};

struct __attribute__((packed)) MotionSample {
  int16_t gx;
  int16_t gy;
  int16_t gz;
  int16_t ax;
  int16_t ay;
  int16_t az;
  uint16_t micPeak;
};

static_assert(sizeof(MotionSample) == 14, "MotionSample layout is part of protocol v1");

struct DeviceConfig {
  TriggerMode triggerMode = TriggerMode::AudioAndMotion;
  uint8_t liveRateHz = 25;
  uint16_t preTriggerMs = 500;
  uint16_t postTriggerMs = 250;

  // CALIBRATION: starting points only. Tune on the mounted LP300XT.
  uint16_t micThreshold = 7000;
  uint16_t accelDeltaThreshold = 1500;
  uint16_t gyroThreshold = 2000;
  uint8_t coincidenceMs = 40;
  uint16_t refractoryMs = 1200;
};

struct ShotSlot {
  SlotState state = SlotState::Free;
  uint16_t shotId = 0;
  uint32_t triggerUptimeMs = 0;
  uint16_t sampleCount = 0;
  uint16_t triggerIndex = 0;
  uint16_t postRemaining = 0;
  uint8_t postDecimationCounter = 0;
  uint16_t audioPeak = 0;
  uint16_t accelPeak = 0;
  uint16_t gyroPeak = 0;
  uint16_t txIndex = 0;
  uint8_t txStage = 0;
  uint32_t crc = 0xFFFFFFFFU;
  MotionSample samples[kMaxShotSamples];
};

LSM6DS3 imu(I2C_MODE, 0x6A);

BLEService aimTracerService(AimTracerProtocol::kServiceUuid);
BLEService batteryService("180F");
BLECharacteristic statusCharacteristic(
    AimTracerProtocol::kStatusUuid, BLERead | BLENotify,
    AimTracerProtocol::kPacketSize, true);
BLECharacteristic controlCharacteristic(
    AimTracerProtocol::kControlUuid, BLEWrite,
    AimTracerProtocol::kPacketSize, false);
BLECharacteristic liveCharacteristic(
    AimTracerProtocol::kLiveUuid, BLENotify,
    AimTracerProtocol::kPacketSize, true);
BLECharacteristic shotCharacteristic(
    AimTracerProtocol::kShotUuid, BLEIndicate,
    AimTracerProtocol::kPacketSize, true);
BLECharacteristic configCharacteristic(
    AimTracerProtocol::kConfigUuid, BLERead | BLEWrite,
    AimTracerProtocol::kPacketSize, true);
BLECharacteristic powerCharacteristic(
    AimTracerProtocol::kPowerUuid, BLERead | BLENotify, 6, true);
BLEUnsignedCharCharacteristic batteryLevelCharacteristic(
    "2A19", BLERead | BLENotify);

DeviceConfig config;
MotionSample ringBuffer[kRingCapacity];
uint16_t ringWriteIndex = 0;
uint16_t ringCount = 0;
MotionSample latestSample{};
MotionSample previousSample{};
bool havePreviousSample = false;

ShotSlot shotSlots[kShotSlotCount];
uint16_t nextShotId = 1;
uint16_t totalShots = 0;
uint16_t droppedTriggers = 0;

bool sessionActive = false;
bool armed = false;
bool microphoneReady = false;
bool calibrated = false;
bool calibrating = true;
bool wasConnected = false;
uint8_t lastError = ErrorNone;

int64_t gyroCalibrationSum[3] = {0, 0, 0};
uint16_t gyroCalibrationCount = 0;
int32_t gyroBias[3] = {0, 0, 0};

volatile uint16_t micPeakFromIsr = 0;
int16_t pdmBuffer[kPdmBufferSamples];
uint16_t latestMicPeak = 0;

uint32_t lastAudioEventMs = 0;
uint32_t lastMotionEventMs = 0;
bool haveAudioEvent = false;
bool haveMotionEvent = false;
uint32_t lastTriggerMs = 0;
uint32_t lastStatusMs = 0;
uint32_t lastPowerMs = 0;
uint32_t lastLiveMs = 0;
uint32_t lastTxMs = 0;
uint32_t lastActivityMs = 0;
uint16_t liveSequence = 0;
uint16_t batteryMillivolts = 0;
uint8_t batteryPercent = 0;
bool batteryCharging = false;
bool externalPowerPresent = false;

void markActivity() {
  lastActivityMs = millis();
}

uint8_t interpolatePercent(
    uint16_t millivolts,
    uint16_t lowerMv,
    uint8_t lowerPercent,
    uint16_t upperMv,
    uint8_t upperPercent) {
  const uint32_t numerator =
      static_cast<uint32_t>(millivolts - lowerMv) *
      static_cast<uint32_t>(upperPercent - lowerPercent);
  return lowerPercent +
      static_cast<uint8_t>(numerator / (upperMv - lowerMv));
}

uint8_t batteryPercentFromMillivolts(uint16_t millivolts) {
  // CALIBRATION: intentionally coarse LiPo open-circuit approximation.
  // Voltage while charging or under load makes the percentage less exact.
  struct Point {
    uint16_t millivolts;
    uint8_t percent;
  };
  constexpr Point curve[] = {
      {3300, 0},
      {3600, 10},
      {3700, 25},
      {3800, 45},
      {3900, 65},
      {4000, 80},
      {4100, 90},
      {4200, 100},
  };

  if (millivolts <= curve[0].millivolts) {
    return curve[0].percent;
  }
  const size_t last = sizeof(curve) / sizeof(curve[0]) - 1;
  if (millivolts >= curve[last].millivolts) {
    return curve[last].percent;
  }
  for (size_t i = 1; i <= last; ++i) {
    if (millivolts <= curve[i].millivolts) {
      return interpolatePercent(
          millivolts,
          curve[i - 1].millivolts,
          curve[i - 1].percent,
          curve[i].millivolts,
          curve[i].percent);
    }
  }
  return 0;
}

uint16_t readBatteryMillivolts() {
  // Seeed explicitly recommends keeping READ_BAT_ENABLE low while reading
  // and not driving it high during USB charging.
  digitalWrite(PIN_VBAT_ENABLE, LOW);
  (void)analogRead(PIN_VBAT);  // discard the first conversion

  uint32_t sum = 0;
  for (uint8_t i = 0; i < kBatteryAdcSamples; ++i) {
    sum += static_cast<uint16_t>(analogRead(PIN_VBAT));
  }
  const float average = static_cast<float>(sum) / kBatteryAdcSamples;
  const float pinMillivolts =
      average * kBatteryAdcFullScaleMv / 4095.0F;
  const float cellMillivolts =
      pinMillivolts * kBatteryDividerScale * kBatteryCalibrationFactor;
  return static_cast<uint16_t>(
      constrain(cellMillivolts + 0.5F, 0.0F, 65535.0F));
}

void initializePowerManagement() {
  // The BQ25101 charger is autonomous. P0.13 only selects its current:
  // HIGH = 50 mA (conservative default), LOW = 100 mA.
  pinMode(kChargeCurrentPin, OUTPUT);
  digitalWrite(
      kChargeCurrentPin,
      kChargeCurrentMa == 100 ? LOW : HIGH);

  // ~CHG is open-drain. Without a pull-up it can float LOW while USB is
  // disconnected and falsely report "charging".
  pinMode(kChargeStatusPin, INPUT_PULLUP);
  pinMode(PIN_VBAT_ENABLE, OUTPUT);
  digitalWrite(PIN_VBAT_ENABLE, LOW);
  analogReadResolution(12);
}

bool readExternalPowerPresent() {
  // Unlike the charger's open-drain ~CHG pin, the nRF52840 VBUS detector
  // unambiguously tells us whether USB power is physically present.
  return (NRF_POWER->USBREGSTATUS &
          POWER_USBREGSTATUS_VBUSDETECT_Msk) != 0;
}

void publishPowerStatus(bool force = false) {
  const uint32_t now = millis();
  if (!force && now - lastPowerMs < kPowerIntervalMs) {
    return;
  }

  const uint8_t previousPercent = batteryPercent;
  const bool previousCharging = batteryCharging;
  batteryMillivolts = readBatteryMillivolts();
  batteryPercent = batteryPercentFromMillivolts(batteryMillivolts);
  externalPowerPresent = readExternalPowerPresent();
  const bool rawChargeSignalActive =
      digitalRead(kChargeStatusPin) == LOW;
  // ~CHG alone proved unreliable on some boards when VBUS was absent.
  // Never report charging unless the SoC independently sees USB power.
  batteryCharging =
      externalPowerPresent && rawChargeSignalActive;

  uint8_t flags = 0;
  flags |= batteryCharging ? (1U << 0) : 0;
  flags |= batteryPercent <= 20 ? (1U << 1) : 0;
  flags |= batteryPercent <= 5 ? (1U << 2) : 0;
  flags |= externalPowerPresent ? (1U << 3) : 0;
  flags |= rawChargeSignalActive ? (1U << 4) : 0;

  uint8_t packet[6] = {};
  packet[0] = AimTracerProtocol::kProtocolVersion;
  packet[1] = batteryPercent;
  packet[2] = flags;
  AimTracerProtocol::putU16(packet, 3, batteryMillivolts);
  packet[5] = kChargeCurrentMa;

  powerCharacteristic.writeValue(packet, sizeof(packet));
  if (force ||
      previousPercent != batteryPercent ||
      previousCharging != batteryCharging) {
    batteryLevelCharacteristic.writeValue(batteryPercent);
  }
  lastPowerMs = now;
}

int16_t readI16(const uint8_t* data, uint8_t offset) {
  return static_cast<int16_t>(
      static_cast<uint16_t>(data[offset]) |
      (static_cast<uint16_t>(data[offset + 1]) << 8));
}

uint16_t abs16(int16_t value) {
  const int32_t wide = value;
  return static_cast<uint16_t>(wide < 0 ? -wide : wide);
}

uint16_t max3(uint16_t a, uint16_t b, uint16_t c) {
  return max(a, max(b, c));
}

int16_t subtractBias(int16_t value, int32_t bias) {
  const int32_t corrected = static_cast<int32_t>(value) - bias;
  return static_cast<int16_t>(constrain(corrected, -32768L, 32767L));
}

void onPdmData() {
  const int available = PDM.available();
  if (available <= 0) {
    return;
  }

  const int bytesToRead = min(
      available, static_cast<int>(sizeof(pdmBuffer)));
  const int bytesRead = PDM.read(pdmBuffer, bytesToRead);
  const int sampleCount = bytesRead / static_cast<int>(sizeof(int16_t));

  uint16_t peak = 0;
  for (int i = 0; i < sampleCount; ++i) {
    peak = max(peak, abs16(pdmBuffer[i]));
  }
  if (peak > micPeakFromIsr) {
    micPeakFromIsr = peak;
  }
}

void beginGyroCalibration() {
  calibrating = true;
  calibrated = false;
  gyroCalibrationCount = 0;
  gyroCalibrationSum[0] = 0;
  gyroCalibrationSum[1] = 0;
  gyroCalibrationSum[2] = 0;
  gyroBias[0] = 0;
  gyroBias[1] = 0;
  gyroBias[2] = 0;
  ringCount = 0;
  ringWriteIndex = 0;
}

void serviceGyroCalibration(int16_t gx, int16_t gy, int16_t gz) {
  if (!calibrating) {
    return;
  }

  gyroCalibrationSum[0] += gx;
  gyroCalibrationSum[1] += gy;
  gyroCalibrationSum[2] += gz;
  ++gyroCalibrationCount;

  if (gyroCalibrationCount >= kCalibrationSamples) {
    gyroBias[0] = gyroCalibrationSum[0] / gyroCalibrationCount;
    gyroBias[1] = gyroCalibrationSum[1] / gyroCalibrationCount;
    gyroBias[2] = gyroCalibrationSum[2] / gyroCalibrationCount;
    calibrating = false;
    calibrated = true;
    ringCount = 0;
    ringWriteIndex = 0;
    if (sessionActive) {
      armed = true;
    }
  }
}

bool readImuSample(MotionSample& output) {
  uint8_t status = 0;
  if (imu.readRegister(&status, LSM6DS3_ACC_GYRO_STATUS_REG) != IMU_SUCCESS) {
    lastError = ErrorImuRead;
    return false;
  }

  if ((status & 0x03U) != 0x03U) {
    return false;
  }

  uint8_t raw[12];
  if (imu.readRegisterRegion(
          raw, LSM6DS3_ACC_GYRO_OUTX_L_G, sizeof(raw)) != IMU_SUCCESS) {
    lastError = ErrorImuRead;
    return false;
  }

  const int16_t rawGx = readI16(raw, 0);
  const int16_t rawGy = readI16(raw, 2);
  const int16_t rawGz = readI16(raw, 4);
  serviceGyroCalibration(rawGx, rawGy, rawGz);

  output.gx = subtractBias(rawGx, gyroBias[0]);
  output.gy = subtractBias(rawGy, gyroBias[1]);
  output.gz = subtractBias(rawGz, gyroBias[2]);
  output.ax = readI16(raw, 6);
  output.ay = readI16(raw, 8);
  output.az = readI16(raw, 10);

  noInterrupts();
  output.micPeak = micPeakFromIsr;
  micPeakFromIsr = 0;
  interrupts();
  latestMicPeak = output.micPeak;
  return true;
}

void pushRing(const MotionSample& sample) {
  ringBuffer[ringWriteIndex] = sample;
  ringWriteIndex = (ringWriteIndex + 1U) % kRingCapacity;
  if (ringCount < kRingCapacity) {
    ++ringCount;
  }
}

uint16_t imuSamplesForMs(uint16_t durationMs) {
  return static_cast<uint16_t>(
      (static_cast<uint32_t>(durationMs) * kSampleRateHz + 999U) / 1000U);
}

uint16_t captureSamplesForMs(uint16_t durationMs) {
  return static_cast<uint16_t>(
      (static_cast<uint32_t>(durationMs) * kCaptureSampleRateHz + 999U) /
      1000U);
}

ShotSlot* freeShotSlot() {
  for (uint8_t i = 0; i < kShotSlotCount; ++i) {
    if (shotSlots[i].state == SlotState::Free) {
      return &shotSlots[i];
    }
  }
  return nullptr;
}

void clearShotSlots() {
  for (uint8_t i = 0; i < kShotSlotCount; ++i) {
    shotSlots[i].state = SlotState::Free;
  }
}

void beginShotCapture(
    uint16_t audioPeak, uint16_t accelPeak, uint16_t gyroPeak) {
  markActivity();
  ShotSlot* slot = freeShotSlot();
  if (slot == nullptr) {
    ++droppedTriggers;
    return;
  }

  const uint16_t requestedPreImu = imuSamplesForMs(config.preTriggerMs);
  const uint16_t availablePreImu = min(ringCount, requestedPreImu);
  const uint16_t preCount = min<uint16_t>(
      (availablePreImu + kCaptureDecimation - 1U) /
          kCaptureDecimation,
      kMaxShotSamples);
  const uint16_t maxPost = kMaxShotSamples - preCount;
  const uint16_t postCount = min(
      captureSamplesForMs(config.postTriggerMs), maxPost);

  slot->state = SlotState::Capturing;
  slot->shotId = nextShotId++;
  if (nextShotId == 0) {
    nextShotId = 1;
  }
  slot->triggerUptimeMs = millis();
  slot->sampleCount = preCount;
  slot->triggerIndex = preCount == 0 ? 0 : preCount - 1;
  slot->postRemaining = postCount * kCaptureDecimation;
  slot->postDecimationCounter = 0;
  slot->audioPeak = audioPeak;
  slot->accelPeak = accelPeak;
  slot->gyroPeak = gyroPeak;
  slot->txIndex = 0;
  slot->txStage = 0;
  slot->crc = 0xFFFFFFFFU;

  for (uint16_t i = 0; i < preCount; ++i) {
    const uint16_t distanceFromNewest =
        (preCount - 1U - i) * kCaptureDecimation;
    const uint16_t index =
        (ringWriteIndex + kRingCapacity - 1U - distanceFromNewest) %
        kRingCapacity;
    slot->samples[i] = ringBuffer[index];
  }

  ++totalShots;
  lastTriggerMs = millis();
  haveAudioEvent = false;
  haveMotionEvent = false;

  if (postCount == 0) {
    slot->state = SlotState::Ready;
  }
}

void appendCapturingSlots(const MotionSample& sample) {
  for (uint8_t i = 0; i < kShotSlotCount; ++i) {
    ShotSlot& slot = shotSlots[i];
    if (slot.state != SlotState::Capturing || slot.postRemaining == 0) {
      continue;
    }
    ++slot.postDecimationCounter;
    if (slot.postDecimationCounter >= kCaptureDecimation &&
        slot.sampleCount < kMaxShotSamples) {
      slot.samples[slot.sampleCount++] = sample;
      slot.postDecimationCounter = 0;
    }
    slot.audioPeak = max(slot.audioPeak, sample.micPeak);
    --slot.postRemaining;
    if (slot.postRemaining == 0 || slot.sampleCount >= kMaxShotSamples) {
      slot.state = SlotState::Ready;
    }
  }
}

void detectTrigger(const MotionSample& sample) {
  if (!sessionActive || !armed || !calibrated) {
    previousSample = sample;
    havePreviousSample = true;
    return;
  }

  const uint32_t now = millis();
  if (now - lastTriggerMs < config.refractoryMs) {
    previousSample = sample;
    havePreviousSample = true;
    return;
  }

  const uint16_t gyroPeak = max3(
      abs16(sample.gx), abs16(sample.gy), abs16(sample.gz));
  uint16_t accelPeak = 0;
  if (havePreviousSample) {
    accelPeak = max3(
        abs16(static_cast<int16_t>(sample.ax - previousSample.ax)),
        abs16(static_cast<int16_t>(sample.ay - previousSample.ay)),
        abs16(static_cast<int16_t>(sample.az - previousSample.az)));
  }

  if (sample.micPeak >= config.micThreshold) {
    lastAudioEventMs = now;
    haveAudioEvent = true;
  }
  if (accelPeak >= config.accelDeltaThreshold ||
      gyroPeak >= config.gyroThreshold) {
    lastMotionEventMs = now;
    haveMotionEvent = true;
  }

  bool shouldTrigger = false;
  switch (config.triggerMode) {
    case TriggerMode::Audio:
      shouldTrigger = haveAudioEvent &&
                      (now - lastAudioEventMs <= config.coincidenceMs);
      break;
    case TriggerMode::Motion:
      shouldTrigger = haveMotionEvent &&
                      (now - lastMotionEventMs <= config.coincidenceMs);
      break;
    case TriggerMode::AudioAndMotion:
      if (haveAudioEvent && haveMotionEvent) {
        const uint32_t difference =
            lastAudioEventMs > lastMotionEventMs
                ? lastAudioEventMs - lastMotionEventMs
                : lastMotionEventMs - lastAudioEventMs;
        shouldTrigger = difference <= config.coincidenceMs;
      }
      break;
  }

  if (shouldTrigger) {
    beginShotCapture(sample.micPeak, accelPeak, gyroPeak);
  }

  previousSample = sample;
  havePreviousSample = true;
}

void encodeConfig(uint8_t packet[AimTracerProtocol::kPacketSize]) {
  memset(packet, 0, AimTracerProtocol::kPacketSize);
  packet[0] = AimTracerProtocol::kProtocolVersion;
  packet[1] = static_cast<uint8_t>(config.triggerMode);
  AimTracerProtocol::putU16(packet, 2, kSampleRateHz);
  packet[4] = config.liveRateHz;
  AimTracerProtocol::putU16(packet, 6, config.preTriggerMs);
  AimTracerProtocol::putU16(packet, 8, config.postTriggerMs);
  AimTracerProtocol::putU16(packet, 10, config.micThreshold);
  AimTracerProtocol::putU16(packet, 12, config.accelDeltaThreshold);
  AimTracerProtocol::putU16(packet, 14, config.gyroThreshold);
  packet[16] = config.coincidenceMs;
  packet[17] = static_cast<uint8_t>(config.refractoryMs / 10U);
  packet[18] = kGyroRangeCode;
  packet[19] = kAccelRangeCode;
}

void publishConfig() {
  uint8_t packet[AimTracerProtocol::kPacketSize];
  encodeConfig(packet);
  configCharacteristic.writeValue(packet, sizeof(packet));
}

bool applyConfig(const uint8_t* packet, size_t length) {
  if (length != AimTracerProtocol::kPacketSize ||
      packet[0] != AimTracerProtocol::kProtocolVersion) {
    return false;
  }

  const uint8_t mode = packet[1];
  const uint16_t sampleRate = AimTracerProtocol::getU16(packet, 2);
  const uint8_t liveRate = packet[4];
  const uint16_t preMs = AimTracerProtocol::getU16(packet, 6);
  const uint16_t postMs = AimTracerProtocol::getU16(packet, 8);
  const uint16_t micThreshold = AimTracerProtocol::getU16(packet, 10);
  const uint16_t accelThreshold = AimTracerProtocol::getU16(packet, 12);
  const uint16_t gyroThreshold = AimTracerProtocol::getU16(packet, 14);
  const uint8_t coincidence = packet[16];
  const uint16_t refractory = static_cast<uint16_t>(packet[17]) * 10U;

  const uint32_t requestedSamples =
      static_cast<uint32_t>(captureSamplesForMs(preMs)) +
      captureSamplesForMs(postMs);

  if (mode < static_cast<uint8_t>(TriggerMode::Audio) ||
      mode > static_cast<uint8_t>(TriggerMode::AudioAndMotion) ||
      sampleRate != kSampleRateHz ||
      liveRate < 5 || liveRate > 50 ||
      preMs < 100 || preMs > 600 ||
      postMs < 100 || postMs > 500 ||
      requestedSamples > kMaxShotSamples ||
      micThreshold == 0 || accelThreshold == 0 || gyroThreshold == 0 ||
      coincidence < 10 || coincidence > 100 ||
      refractory < 300 || refractory > 2500 ||
      packet[18] != kGyroRangeCode ||
      packet[19] != kAccelRangeCode) {
    return false;
  }

  config.triggerMode = static_cast<TriggerMode>(mode);
  config.liveRateHz = liveRate;
  config.preTriggerMs = preMs;
  config.postTriggerMs = postMs;
  config.micThreshold = micThreshold;
  config.accelDeltaThreshold = accelThreshold;
  config.gyroThreshold = gyroThreshold;
  config.coincidenceMs = coincidence;
  config.refractoryMs = refractory;
  return true;
}

bool anySlotInState(SlotState state) {
  for (uint8_t i = 0; i < kShotSlotCount; ++i) {
    if (shotSlots[i].state == state) {
      return true;
    }
  }
  return false;
}

ShotSlot* slotForTransmission() {
  for (uint8_t i = 0; i < kShotSlotCount; ++i) {
    if (shotSlots[i].state == SlotState::Sending) {
      return &shotSlots[i];
    }
  }
  for (uint8_t i = 0; i < kShotSlotCount; ++i) {
    if (shotSlots[i].state == SlotState::Ready) {
      shotSlots[i].state = SlotState::Sending;
      shotSlots[i].txStage = 0;
      shotSlots[i].txIndex = 0;
      shotSlots[i].crc = 0xFFFFFFFFU;
      return &shotSlots[i];
    }
  }
  return nullptr;
}

void encodeMotionBytes(
    const MotionSample& sample, uint8_t* packet, size_t offset) {
  AimTracerProtocol::putI16(packet, offset, sample.gx);
  AimTracerProtocol::putI16(packet, offset + 2, sample.gy);
  AimTracerProtocol::putI16(packet, offset + 4, sample.gz);
  AimTracerProtocol::putI16(packet, offset + 6, sample.ax);
  AimTracerProtocol::putI16(packet, offset + 8, sample.ay);
  AimTracerProtocol::putI16(packet, offset + 10, sample.az);
  AimTracerProtocol::putU16(packet, offset + 12, sample.micPeak);
}

void serviceShotTransmission() {
  if (!BLE.connected() || !shotCharacteristic.subscribed()) {
    return;
  }
  const uint32_t now = millis();
  if (now - lastTxMs < kTxIntervalMs) {
    return;
  }

  ShotSlot* slot = slotForTransmission();
  if (slot == nullptr) {
    return;
  }

  uint8_t packet[AimTracerProtocol::kPacketSize] = {};
  bool sent = false;

  if (slot->txStage == 0) {
    packet[0] = static_cast<uint8_t>(PacketType::ShotMeta);
    packet[1] = AimTracerProtocol::kProtocolVersion;
    AimTracerProtocol::putU16(packet, 2, slot->shotId);
    AimTracerProtocol::putU32(packet, 4, slot->triggerUptimeMs);
    AimTracerProtocol::putU16(packet, 8, kCaptureSampleRateHz);
    AimTracerProtocol::putU16(packet, 10, slot->sampleCount);
    AimTracerProtocol::putU16(packet, 12, slot->triggerIndex);
    AimTracerProtocol::putU16(packet, 14, slot->audioPeak);
    AimTracerProtocol::putU16(packet, 16, slot->accelPeak);
    AimTracerProtocol::putU16(packet, 18, slot->gyroPeak);
    sent = shotCharacteristic.writeValue(packet, sizeof(packet));
    if (sent) {
      slot->txStage = 1;
    }
  } else if (slot->txStage == 1 && slot->txIndex < slot->sampleCount) {
    const MotionSample& sample = slot->samples[slot->txIndex];
    packet[0] = static_cast<uint8_t>(PacketType::ShotSample);
    AimTracerProtocol::putU16(packet, 1, slot->shotId);
    AimTracerProtocol::putU16(packet, 3, slot->txIndex);
    encodeMotionBytes(sample, packet, 5);
    packet[19] = slot->txIndex == slot->triggerIndex ? 0x01 : 0x00;
    sent = shotCharacteristic.writeValue(packet, sizeof(packet));
    if (sent) {
      slot->crc = AimTracerProtocol::crc32Update(
          slot->crc, packet + 5, sizeof(MotionSample));
      ++slot->txIndex;
      if (slot->txIndex >= slot->sampleCount) {
        slot->txStage = 2;
      }
    }
  } else {
    packet[0] = static_cast<uint8_t>(PacketType::ShotEnd);
    AimTracerProtocol::putU16(packet, 1, slot->shotId);
    AimTracerProtocol::putU16(packet, 3, slot->sampleCount);
    AimTracerProtocol::putU32(packet, 5, slot->crc ^ 0xFFFFFFFFU);
    sent = shotCharacteristic.writeValue(packet, sizeof(packet));
    if (sent) {
      slot->state = SlotState::Free;
    }
  }

  if (sent) {
    lastTxMs = now;
  }
}

void publishLive() {
  if (!BLE.connected() || !liveCharacteristic.subscribed() ||
      anySlotInState(SlotState::Sending)) {
    return;
  }

  const uint32_t now = millis();
  const uint16_t interval = 1000U / config.liveRateHz;
  if (now - lastLiveMs < interval) {
    return;
  }

  uint8_t packet[AimTracerProtocol::kPacketSize] = {};
  packet[0] = static_cast<uint8_t>(PacketType::Live);
  AimTracerProtocol::putU16(packet, 1, liveSequence++);
  AimTracerProtocol::putU32(packet, 3, now);
  AimTracerProtocol::putI16(packet, 7, latestSample.gx);
  AimTracerProtocol::putI16(packet, 9, latestSample.gy);
  AimTracerProtocol::putI16(packet, 11, latestSample.gz);
  AimTracerProtocol::putI16(packet, 13, latestSample.ax);
  AimTracerProtocol::putI16(packet, 15, latestSample.ay);
  AimTracerProtocol::putI16(packet, 17, latestSample.az);
  packet[19] = static_cast<uint8_t>(min<uint16_t>(
      latestSample.micPeak >> 8U, 255));
  if (liveCharacteristic.writeValue(packet, sizeof(packet))) {
    lastLiveMs = now;
  }
}

void publishStatus(bool force = false) {
  const uint32_t now = millis();
  if (!force && now - lastStatusMs < kStatusIntervalMs) {
    return;
  }

  uint8_t state = 0;
  state |= BLE.connected() ? (1U << 0) : 0;
  state |= sessionActive ? (1U << 1) : 0;
  state |= armed ? (1U << 2) : 0;
  state |= anySlotInState(SlotState::Capturing) ? (1U << 3) : 0;
  state |= anySlotInState(SlotState::Sending) ? (1U << 4) : 0;
  state |= calibrated ? (1U << 5) : 0;
  state |= microphoneReady ? (1U << 6) : 0;

  uint16_t txShotId = 0;
  uint16_t txIndex = 0;
  for (uint8_t i = 0; i < kShotSlotCount; ++i) {
    if (shotSlots[i].state == SlotState::Sending) {
      txShotId = shotSlots[i].shotId;
      txIndex = shotSlots[i].txIndex;
      break;
    }
  }

  uint8_t packet[AimTracerProtocol::kPacketSize] = {};
  packet[0] = static_cast<uint8_t>(PacketType::Status);
  packet[1] = AimTracerProtocol::kProtocolVersion;
  packet[2] = state;
  packet[3] = lastError;
  AimTracerProtocol::putU16(packet, 4, kSampleRateHz);
  AimTracerProtocol::putU16(packet, 6, ringCount);
  AimTracerProtocol::putU16(packet, 8, totalShots);
  AimTracerProtocol::putU16(packet, 10, droppedTriggers);
  AimTracerProtocol::putU16(packet, 12, latestMicPeak);
  AimTracerProtocol::putU16(packet, 14, txShotId);
  AimTracerProtocol::putU16(packet, 16, txIndex);
  packet[18] = AimTracerProtocol::kFirmwareMajor;
  packet[19] = AimTracerProtocol::kFirmwareMinor;

  statusCharacteristic.writeValue(packet, sizeof(packet));
  lastStatusMs = now;
}

void handleControl() {
  if (!controlCharacteristic.written()) {
    return;
  }
  uint8_t bytes[AimTracerProtocol::kPacketSize] = {};
  const int length = controlCharacteristic.readValue(bytes, sizeof(bytes));
  if (length < 1) {
    return;
  }
  markActivity();

  switch (static_cast<Command>(bytes[0])) {
    case Command::StartSession:
      clearShotSlots();
      sessionActive = true;
      armed = true;
      haveAudioEvent = false;
      haveMotionEvent = false;
      break;
    case Command::StopSession:
      sessionActive = false;
      armed = false;
      break;
    case Command::Arm:
      armed = true;
      break;
    case Command::Disarm:
      armed = false;
      break;
    case Command::ManualTrigger:
      if (sessionActive && armed) {
        beginShotCapture(
            latestSample.micPeak, config.accelDeltaThreshold,
            config.gyroThreshold);
      }
      break;
    case Command::Calibrate:
      armed = false;
      beginGyroCalibration();
      break;
  }
  publishStatus(true);
}

void handleConfigWrite() {
  if (!configCharacteristic.written()) {
    return;
  }
  markActivity();
  uint8_t bytes[AimTracerProtocol::kPacketSize] = {};
  const int length = configCharacteristic.readValue(bytes, sizeof(bytes));
  if (!applyConfig(bytes, length)) {
    lastError = ErrorInvalidConfig;
  } else {
    lastError = ErrorNone;
  }
  publishConfig();
  publishStatus(true);
}

void serviceImu() {
  MotionSample sample;
  if (!readImuSample(sample)) {
    return;
  }
  latestSample = sample;
  pushRing(sample);
  appendCapturingSlots(sample);
  detectTrigger(sample);
}

bool initializeImu() {
  imu.settings.gyroRange = kGyroRangeDps;
  imu.settings.gyroSampleRate = kSampleRateHz;
  imu.settings.gyroBandWidth = 200;
  imu.settings.gyroFifoEnabled = 0;
  imu.settings.accelRange = kAccelRangeG;
  imu.settings.accelSampleRate = kSampleRateHz;
  imu.settings.accelBandWidth = 200;
  imu.settings.accelFifoEnabled = 0;
  imu.settings.tempEnabled = 0;
  if (imu.begin() != IMU_SUCCESS) {
    return false;
  }
  Wire1.setClock(400000);
  beginGyroCalibration();
  return true;
}

bool initializeMicrophone() {
  PDM.onReceive(onPdmData);
  PDM.setBufferSize(sizeof(pdmBuffer));
  PDM.setGain(20);
  return PDM.begin(1, 16000) == 1;
}

bool initializeBle() {
  if (!BLE.begin()) {
    return false;
  }

  BLE.setLocalName("AimTracer");
  BLE.setDeviceName("AimTracer XIAO");
  BLE.setAdvertisedService(aimTracerService);
  aimTracerService.addCharacteristic(statusCharacteristic);
  aimTracerService.addCharacteristic(controlCharacteristic);
  aimTracerService.addCharacteristic(liveCharacteristic);
  aimTracerService.addCharacteristic(shotCharacteristic);
  aimTracerService.addCharacteristic(configCharacteristic);
  aimTracerService.addCharacteristic(powerCharacteristic);
  batteryService.addCharacteristic(batteryLevelCharacteristic);
  BLE.addService(aimTracerService);
  BLE.addService(batteryService);
  publishConfig();
  publishStatus(true);
  publishPowerStatus(true);
  BLE.advertise();
  return true;
}

[[noreturn]] void enterSystemOff() {
  sessionActive = false;
  armed = false;
  PDM.end();

  // ST power-down mode: all ODR bits in CTRL1_XL and CTRL2_G are zero.
  imu.writeRegister(LSM6DS3_ACC_GYRO_CTRL1_XL, 0x00);
  imu.writeRegister(LSM6DS3_ACC_GYRO_CTRL2_G, 0x00);

  digitalWrite(LEDR, HIGH);
  // Disconnect the battery-divider gate while sleeping. Auto-sleep is never
  // entered with USB attached, so this cannot interfere with charging.
  digitalWrite(PIN_VBAT_ENABLE, HIGH);
  delay(5);

  // nRF52840 System OFF disables CPU and radio. Waking performs a reset; on
  // the finished device use the slide switch (off/on) or the XIAO reset key.
  NRF_POWER->SYSTEMOFF = 1;
  __DSB();
  while (true) {
    __WFE();
  }
}

void serviceAutoSleep() {
  const uint32_t now = millis();
  // Read VBUS again at the decision point instead of relying on the last
  // one-second power packet. This avoids a race when USB has just been
  // connected at the two-hour boundary.
  externalPowerPresent = readExternalPowerPresent();
  if (externalPowerPresent ||
      anySlotInState(SlotState::Capturing) ||
      anySlotInState(SlotState::Sending)) {
    return;
  }
  if (now - lastActivityMs >= kAutoSleepAfterMs) {
    enterSystemOff();
  }
}

void fatalBlink(uint8_t error) {
  lastError = error;
  pinMode(LEDR, OUTPUT);
  while (true) {
    digitalWrite(LEDR, LOW);
    delay(150);
    digitalWrite(LEDR, HIGH);
    delay(350);
  }
}

}  // namespace

void setup() {
  pinMode(LEDR, OUTPUT);
  digitalWrite(LEDR, HIGH);
  initializePowerManagement();
  markActivity();

  if (!initializeImu()) {
    fatalBlink(ErrorImuInit);
  }
  if (!initializeMicrophone()) {
    fatalBlink(ErrorPdmInit);
  }
  microphoneReady = true;
  if (!initializeBle()) {
    fatalBlink(ErrorBleInit);
  }
}

void loop() {
  BLE.poll();
  serviceImu();
  handleControl();
  handleConfigWrite();

  const bool connected = BLE.connected();
  if (connected != wasConnected) {
    wasConnected = connected;
    markActivity();
    digitalWrite(LEDR, connected ? LOW : HIGH);
    if (!connected) {
      sessionActive = false;
      armed = false;
    }
    publishStatus(true);
  }

  serviceShotTransmission();
  publishLive();
  publishStatus();
  publishPowerStatus();
  serviceAutoSleep();
  delay(1);
}
