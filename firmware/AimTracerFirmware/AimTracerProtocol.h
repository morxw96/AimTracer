#pragma once

#include <Arduino.h>

namespace AimTracerProtocol {

constexpr uint8_t kProtocolVersion = 1;
constexpr uint8_t kFirmwareMajor = 0;
constexpr uint8_t kFirmwareMinor = 5;
constexpr size_t kPacketSize = 20;

constexpr char kServiceUuid[] = "7B8A0001-6D5B-4F0D-9C6A-3E0D6C0A1000";
constexpr char kStatusUuid[] = "7B8A0002-6D5B-4F0D-9C6A-3E0D6C0A1000";
constexpr char kControlUuid[] = "7B8A0003-6D5B-4F0D-9C6A-3E0D6C0A1000";
constexpr char kLiveUuid[] = "7B8A0004-6D5B-4F0D-9C6A-3E0D6C0A1000";
constexpr char kShotUuid[] = "7B8A0005-6D5B-4F0D-9C6A-3E0D6C0A1000";
constexpr char kConfigUuid[] = "7B8A0006-6D5B-4F0D-9C6A-3E0D6C0A1000";
constexpr char kPowerUuid[] = "7B8A0007-6D5B-4F0D-9C6A-3E0D6C0A1000";

enum class PacketType : uint8_t {
  Status = 0x01,
  Live = 0x10,
  ShotMeta = 0x20,
  ShotSample = 0x21,
  ShotEnd = 0x22,
};

enum class Command : uint8_t {
  StartSession = 0x01,
  StopSession = 0x02,
  Arm = 0x03,
  Disarm = 0x04,
  ManualTrigger = 0x05,
  Calibrate = 0x06,
};

enum class TriggerMode : uint8_t {
  Audio = 1,
  Motion = 2,
  AudioAndMotion = 3,
};

inline void putU16(uint8_t* buffer, size_t offset, uint16_t value) {
  buffer[offset] = static_cast<uint8_t>(value & 0xFF);
  buffer[offset + 1] = static_cast<uint8_t>((value >> 8) & 0xFF);
}

inline void putI16(uint8_t* buffer, size_t offset, int16_t value) {
  putU16(buffer, offset, static_cast<uint16_t>(value));
}

inline void putU32(uint8_t* buffer, size_t offset, uint32_t value) {
  buffer[offset] = static_cast<uint8_t>(value & 0xFF);
  buffer[offset + 1] = static_cast<uint8_t>((value >> 8) & 0xFF);
  buffer[offset + 2] = static_cast<uint8_t>((value >> 16) & 0xFF);
  buffer[offset + 3] = static_cast<uint8_t>((value >> 24) & 0xFF);
}

inline uint16_t getU16(const uint8_t* buffer, size_t offset) {
  return static_cast<uint16_t>(buffer[offset]) |
         (static_cast<uint16_t>(buffer[offset + 1]) << 8);
}

uint32_t crc32Update(uint32_t crc, const uint8_t* bytes, size_t length);

}  // namespace AimTracerProtocol
