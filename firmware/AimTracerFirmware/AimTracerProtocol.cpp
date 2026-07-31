#include "AimTracerProtocol.h"

namespace AimTracerProtocol {

uint32_t crc32Update(uint32_t crc, const uint8_t* bytes, size_t length) {
  for (size_t i = 0; i < length; ++i) {
    crc ^= bytes[i];
    for (uint8_t bit = 0; bit < 8; ++bit) {
      const uint32_t mask = -(crc & 1U);
      crc = (crc >> 1U) ^ (0xEDB88320U & mask);
    }
  }
  return crc;
}

}  // namespace AimTracerProtocol
