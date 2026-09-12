#include "engine/audio/AudioEngine.hpp"

#include "engine/core/Log.hpp"
#include "engine/platform/Filesystem.hpp"

#include <algorithm>
#include <cmath>
#include <cstring>

/// @file AudioEngine.cpp
/// @brief Real PCM software mixer + 3D spatialization.

namespace engine::audio {
namespace {
constexpr f32 kPiOver2 = 1.57079632679f;
} // namespace

Result<bool> AudioEngine::initialize(u32 sampleRate, u32 channels) {
    sampleRate_  = sampleRate;
    channels_    = channels == 0 ? 2 : channels;
    initialized_ = true;
    log::info("[Audio] mixer initialized ({} Hz, {} ch)", sampleRate_, channels_);
    return ok(true);
}

void AudioEngine::shutdown() {
    voices_.clear();
    sounds_.clear();
    initialized_ = false;
}

SoundHandle AudioEngine::createSound(std::vector<f32> monoSamples, u32 sampleRate) {
    const auto id = static_cast<u32>(sounds_.size());
    sounds_.push_back({std::move(monoSamples), sampleRate == 0 ? sampleRate_ : sampleRate});
    return SoundHandle{id};
}

Result<SoundHandle> AudioEngine::loadSound(const std::string& path, bool streaming) {
    (void) streaming; // streaming vs. fully-decoded is transparent to callers here
    auto bytes = platform::fs::readBytes(platform::fs::resolveAsset(path));
    if (!bytes) {
        return bytes.error();
    }
    const auto& raw = bytes.value();
    if (raw.size() % sizeof(f32) != 0) {
        return err<SoundHandle>(ErrorCode::DeserializeError, "PCM size not f32-aligned: " + path);
    }
    std::vector<f32> samples(raw.size() / sizeof(f32));
    std::memcpy(samples.data(), raw.data(), raw.size());
    return ok(createSound(std::move(samples), sampleRate_));
}

SoundHandle AudioEngine::createTone(f32 frequencyHz, f32 seconds, f32 amplitude) {
    if (seconds <= 0.0f) {
        return createSound({}, sampleRate_); // empty sound rather than a bogus allocation
    }
    const auto count = static_cast<usize>(static_cast<f32>(sampleRate_) * seconds);
    std::vector<f32> samples(count);
    const f32 twoPiF = 2.0f * 3.14159265358979f * frequencyHz;
    for (usize i = 0; i < count; ++i) {
        samples[i] = amplitude * std::sin(twoPiF * static_cast<f32>(i) / static_cast<f32>(sampleRate_));
    }
    return createSound(std::move(samples), sampleRate_);
}

VoiceHandle AudioEngine::play(SoundHandle sound, f32 volume, bool loop) {
    if (!sound.valid() || sound.id >= sounds_.size()) {
        return {};
    }
    Voice v;
    v.soundId = sound.id;
    v.volume  = volume;
    v.loop    = loop;
    v.active  = true;
    v.is3D    = false;
    // Reuse a finished slot if available.
    for (usize i = 0; i < voices_.size(); ++i) {
        if (!voices_[i].active) {
            voices_[i] = v;
            return VoiceHandle{static_cast<u32>(i)};
        }
    }
    voices_.push_back(v);
    return VoiceHandle{static_cast<u32>(voices_.size() - 1)};
}

VoiceHandle AudioEngine::playAt(SoundHandle sound, math::Vec3 worldPos, f32 volume, bool loop) {
    const VoiceHandle h = play(sound, volume, loop);
    if (h.valid()) {
        voices_[h.id].is3D     = true;
        voices_[h.id].worldPos = worldPos;
    }
    return h;
}

void AudioEngine::stop(VoiceHandle voice) {
    if (voice.valid() && voice.id < voices_.size()) {
        voices_[voice.id].active = false;
    }
}

void AudioEngine::stopAll() {
    for (auto& v : voices_) {
        v.active = false;
    }
}

void AudioEngine::computeGains(const Voice& v, f32& outLeft, f32& outRight) const {
    if (!v.is3D) {
        outLeft = outRight = 1.0f;
        return;
    }
    const math::Vec3 toSource = v.worldPos - listener_.position;
    const f32        distance = math::length(toSource);
    const f32        attenuation = 1.0f / (1.0f + distance); // simple inverse rolloff

    // Constant-power pan from the source's position relative to the listener's right.
    const math::Vec3 right = math::normalize(math::cross(listener_.forward, listener_.up));
    f32 pan = 0.0f;
    if (distance > 1e-4f) {
        pan = std::clamp(math::dot(math::normalize(toSource), right), -1.0f, 1.0f);
    }
    const f32 angle = (pan + 1.0f) * 0.5f * kPiOver2; // [-1,1] -> [0, pi/2]
    outLeft  = std::cos(angle) * attenuation;
    outRight = std::sin(angle) * attenuation;
}

void AudioEngine::mix(f32* output, u32 frames) {
    if (output == nullptr || frames == 0) {
        return;
    }
    // Start from silence.
    std::fill(output, output + static_cast<usize>(frames) * channels_, 0.0f);
    if (!initialized_) {
        return;
    }

    for (auto& v : voices_) {
        if (!v.active) {
            continue;
        }
        const Sound& snd = sounds_[v.soundId];
        if (snd.samples.empty()) {
            v.active = false;
            continue;
        }
        f32 gainL = 1.0f;
        f32 gainR = 1.0f;
        computeGains(v, gainL, gainR);
        gainL *= v.volume * masterVolume_;
        gainR *= v.volume * masterVolume_;

        for (u32 f = 0; f < frames; ++f) {
            if (v.cursor >= snd.samples.size()) {
                if (v.loop) {
                    v.cursor = 0;
                } else {
                    v.active = false;
                    break;
                }
            }
            const f32 s = snd.samples[v.cursor++];
            if (channels_ >= 2) {
                output[f * channels_ + 0] += s * gainL;
                output[f * channels_ + 1] += s * gainR;
            } else {
                output[f] += s * (gainL + gainR) * 0.5f;
            }
        }
    }

    // Soft clamp to [-1, 1] to avoid wrap on the device.
    const usize total = static_cast<usize>(frames) * channels_;
    for (usize i = 0; i < total; ++i) {
        output[i] = std::clamp(output[i], -1.0f, 1.0f);
    }
}

void AudioEngine::update(f32 dt) {
    (void) dt; // voice lifetime is managed inside mix(); streaming refill would go here
}

usize AudioEngine::activeVoices() const {
    usize n = 0;
    for (const auto& v : voices_) {
        if (v.active) {
            ++n;
        }
    }
    return n;
}

} // namespace engine::audio
