// imgproc/util/sha256.hpp
// Self-contained SHA-256 (FIPS 180-4). Used to verify model-artifact integrity
// before loading (supply-chain hardening — see ARCHITECTURE.md §2.5).
#pragma once

#include <array>
#include <cstddef>
#include <cstdint>
#include <string>

namespace imgproc::util {

/// Streaming SHA-256 digest.
class Sha256 {
public:
    Sha256() { reset(); }

    void reset();
    void update(const void* data, std::size_t len);

    /// Finalize and return the lowercase hex digest (64 chars). Consumes state.
    [[nodiscard]] std::string hexDigest();

    /// One-shot helpers.
    [[nodiscard]] static std::string hashBytes(const void* data, std::size_t len);
    [[nodiscard]] static std::string hashString(const std::string& s);
    /// Hash a file's contents; returns empty string if the file can't be read.
    [[nodiscard]] static std::string hashFile(const std::string& path);

private:
    void processBlock(const std::uint8_t* block);

    std::array<std::uint32_t, 8> state_{};
    std::uint64_t bit_len_{0};
    std::uint8_t buffer_[64]{};
    std::size_t buffer_len_{0};
};

}  // namespace imgproc::util
