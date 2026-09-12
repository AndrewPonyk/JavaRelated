#pragma once

#include "engine/core/Result.hpp"
#include "engine/core/Types.hpp"

#include <filesystem>
#include <string>
#include <vector>

/// @file Filesystem.hpp
/// @brief Path resolution + safe file I/O helpers.
///
/// Centralizes asset-root resolution and bounds-aware reads. Asset/save parsers
/// build on these and must treat all file contents as untrusted (see SECURITY).

namespace engine::platform::fs {

/// Resolve a path relative to the configured asset root (ENGINE_ASSET_ROOT).
[[nodiscard]] std::filesystem::path resolveAsset(std::string_view relative);

/// Read an entire file as bytes. Errors instead of throwing.
[[nodiscard]] Result<std::vector<u8>> readBytes(const std::filesystem::path& path);

/// Read an entire file as text (UTF-8).
[[nodiscard]] Result<std::string> readText(const std::filesystem::path& path);

/// Atomically write bytes (write temp + rename) to avoid partial files on crash.
[[nodiscard]] Result<bool> writeBytesAtomic(const std::filesystem::path& path,
                                            const std::vector<u8>&       data);

[[nodiscard]] bool exists(const std::filesystem::path& path);

/// Per-user writable directory for saves/config (OS-specific).
[[nodiscard]] std::filesystem::path userDataDir(std::string_view appName);

} // namespace engine::platform::fs
