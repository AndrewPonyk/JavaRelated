#pragma once

#include "engine/core/Result.hpp"
#include "engine/core/Types.hpp"
#include "engine/core/math/Vec.hpp"

#include <string>
#include <vector>

/// @file AudioEngine.hpp
/// @brief Streaming software mixer with 3D spatialization.
///
/// Real PCM mixing: voices reference decoded sounds; mix() sums active voices into an
/// interleaved stereo buffer with per-voice volume, distance attenuation, and
/// constant-power panning relative to the listener. The OS audio device (SDL_audio)
/// is an optional output sink that simply calls mix() on its callback thread.

namespace engine::audio {

struct SoundHandle {
    u32 id = 0xFFFF'FFFFu;
    [[nodiscard]] bool valid() const noexcept { return id != 0xFFFF'FFFFu; }
};

struct VoiceHandle {
    u32 id = 0xFFFF'FFFFu;
    [[nodiscard]] bool valid() const noexcept { return id != 0xFFFF'FFFFu; }
};

struct ListenerState {
    math::Vec3 position{};
    math::Vec3 forward{0, 0, -1};
    math::Vec3 up{0, 1, 0};
};

class AudioEngine {
public:
    [[nodiscard]] Result<bool> initialize(u32 sampleRate = 48000, u32 channels = 2);
    void                       shutdown();

    /// Register decoded mono PCM as a playable sound (procedural/tests/imported).
    [[nodiscard]] SoundHandle createSound(std::vector<f32> monoSamples, u32 sampleRate);

    /// Load a sound. Without a decoder backend, reads raw little-endian f32 mono PCM.
    [[nodiscard]] Result<SoundHandle> loadSound(const std::string& path, bool streaming = false);

    /// Generate a sine tone (handy for tests / placeholder SFX).
    [[nodiscard]] SoundHandle createTone(f32 frequencyHz, f32 seconds, f32 amplitude = 0.5f);

    VoiceHandle play(SoundHandle sound, f32 volume = 1.0f, bool loop = false);
    VoiceHandle playAt(SoundHandle sound, math::Vec3 worldPos, f32 volume = 1.0f, bool loop = false);
    void        stop(VoiceHandle voice);
    void        stopAll();

    void setListener(const ListenerState& listener) noexcept { listener_ = listener; }
    void setMasterVolume(f32 volume) noexcept { masterVolume_ = volume; }

    /// Mix all active voices into `output` (interleaved, `channels` per frame).
    /// `output` must hold frames * channels samples; it is overwritten (not added to).
    void mix(f32* output, u32 frames);

    /// Per-frame housekeeping (currently voice culling is done inside mix()).
    void update(f32 dt);

    [[nodiscard]] usize activeVoices() const;
    [[nodiscard]] bool  isInitialized() const noexcept { return initialized_; }

private:
    struct Sound {
        std::vector<f32> samples;
        u32              sampleRate = 48000;
    };
    struct Voice {
        u32        soundId = 0;
        usize      cursor  = 0; // read position in samples
        f32        volume  = 1.0f;
        bool       loop    = false;
        bool       active  = false;
        bool       is3D    = false;
        math::Vec3 worldPos{};
    };

    void computeGains(const Voice& v, f32& outLeft, f32& outRight) const;

    std::vector<Sound> sounds_;
    std::vector<Voice> voices_;
    ListenerState      listener_{};
    u32                sampleRate_  = 48000;
    u32                channels_    = 2;
    f32                masterVolume_ = 1.0f;
    bool               initialized_ = false;
};

} // namespace engine::audio
