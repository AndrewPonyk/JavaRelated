#include "engine/resources/Serialization.hpp"

#include <array>
#include <cstdio>

/// @file Serialization.cpp
/// @brief Content hashing + math/Json conversions.

namespace engine::resources {

std::string contentHash(std::string_view bytes) {
    // 64-bit FNV-1a.
    constexpr u64 kOffsetBasis = 1469598103934665603ull;
    constexpr u64 kPrime       = 1099511628211ull;
    u64           hash         = kOffsetBasis;
    for (const char c : bytes) {
        hash ^= static_cast<u64>(static_cast<unsigned char>(c));
        hash *= kPrime;
    }
    std::array<char, 17> buf{};
    std::snprintf(buf.data(), buf.size(), "%016llx", static_cast<unsigned long long>(hash));
    return std::string(buf.data());
}

Json toJson(const math::Vec3& v) {
    Json a = Json::array();
    a.push_back(v.x);
    a.push_back(v.y);
    a.push_back(v.z);
    return a;
}

math::Vec3 vec3FromJson(const Json& j, math::Vec3 fallback) {
    if (!j.isArray() || j.size() < 3) {
        return fallback;
    }
    return math::Vec3{j[0].asFloat(fallback.x), j[1].asFloat(fallback.y), j[2].asFloat(fallback.z)};
}

} // namespace engine::resources
