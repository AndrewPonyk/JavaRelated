#include "engine/platform/Filesystem.hpp"

#include "engine/core/Log.hpp"

#include <cstdlib>
#include <fstream>
#include <iterator>

/// @file Filesystem.cpp
/// @brief std::filesystem-backed I/O. All reads are size-bounded and report errors
/// via Result rather than throwing — callers treat file contents as untrusted.

namespace engine::platform::fs {
namespace {
/// Reject absurdly large files early (defense against malformed/hostile inputs).
constexpr u64 kMaxReadBytes = 512ull * 1024 * 1024; // 512 MiB
} // namespace

std::filesystem::path resolveAsset(std::string_view relative) {
    const char* root = std::getenv("ENGINE_ASSET_ROOT");
    std::filesystem::path base = (root != nullptr) ? root : "assets";
    return base / std::filesystem::path(relative);
}

Result<std::vector<u8>> readBytes(const std::filesystem::path& path) {
    std::error_code ec;
    const auto size = std::filesystem::file_size(path, ec);
    if (ec) {
        return err<std::vector<u8>>(ErrorCode::NotFound, "stat failed: " + path.string());
    }
    if (size > kMaxReadBytes) {
        return err<std::vector<u8>>(ErrorCode::InvalidArgument, "file too large: " + path.string());
    }

    std::ifstream file(path, std::ios::binary);
    if (!file) {
        return err<std::vector<u8>>(ErrorCode::IoError, "open failed: " + path.string());
    }
    std::vector<u8> data(static_cast<usize>(size));
    file.read(reinterpret_cast<char*>(data.data()), static_cast<std::streamsize>(size));
    if (!file && !file.eof()) {
        return err<std::vector<u8>>(ErrorCode::IoError, "read failed: " + path.string());
    }
    return ok(std::move(data));
}

Result<std::string> readText(const std::filesystem::path& path) {
    auto bytes = readBytes(path);
    if (!bytes) {
        return bytes.error();
    }
    const auto& v = bytes.value();
    return ok(std::string(v.begin(), v.end()));
}

Result<bool> writeBytesAtomic(const std::filesystem::path& path, const std::vector<u8>& data) {
    const auto tmp = path.string() + ".tmp";
    {
        std::ofstream file(tmp, std::ios::binary | std::ios::trunc);
        if (!file) {
            return err<bool>(ErrorCode::IoError, "open temp failed: " + tmp);
        }
        file.write(reinterpret_cast<const char*>(data.data()),
                   static_cast<std::streamsize>(data.size()));
        if (!file) {
            return err<bool>(ErrorCode::IoError, "write temp failed: " + tmp);
        }
    }
    std::error_code ec;
    std::filesystem::rename(tmp, path, ec);
    if (ec) {
        return err<bool>(ErrorCode::IoError, "rename failed: " + path.string());
    }
    return ok(true);
}

bool exists(const std::filesystem::path& path) {
    std::error_code ec;
    return std::filesystem::exists(path, ec);
}

std::filesystem::path userDataDir(std::string_view appName) {
    // Minimal cross-platform resolution; production uses SDL_GetPrefPath / SHGetKnownFolderPath.
#if defined(_WIN32)
    const char* base = std::getenv("APPDATA");
#else
    const char* base = std::getenv("XDG_DATA_HOME");
    if (base == nullptr) {
        base = std::getenv("HOME");
    }
#endif
    std::filesystem::path dir = (base != nullptr) ? base : ".";
    dir /= appName;
    std::error_code ec;
    std::filesystem::create_directories(dir, ec);
    return dir;
}

} // namespace engine::platform::fs
