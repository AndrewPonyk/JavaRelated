#include "engine/audio/AudioEngine.hpp"

#include <gtest/gtest.h>

#include <vector>

using namespace engine::audio;

TEST(Audio, MixIsSilentWithNoVoices) {
    AudioEngine a;
    ASSERT_TRUE(a.initialize(48000, 2));
    std::vector<float> out(8, 123.0f);
    a.mix(out.data(), 4);
    for (const float s : out) {
        EXPECT_FLOAT_EQ(s, 0.0f);
    }
}

TEST(Audio, PlaysMonoSoundIntoStereo) {
    AudioEngine a;
    ASSERT_TRUE(a.initialize(48000, 2));
    const SoundHandle snd = a.createSound({1.0f, 1.0f, 1.0f, 1.0f}, 48000);
    a.play(snd, 0.5f);

    std::vector<float> out(4, 0.0f); // 2 stereo frames
    a.mix(out.data(), 2);
    EXPECT_FLOAT_EQ(out[0], 0.5f);
    EXPECT_FLOAT_EQ(out[1], 0.5f);
    EXPECT_EQ(a.activeVoices(), 1u);
}

TEST(Audio, VoiceDeactivatesWhenFinished) {
    AudioEngine a;
    a.initialize(48000, 2);
    const SoundHandle snd = a.createSound({1.0f, 1.0f}, 48000);
    a.play(snd, 1.0f, /*loop*/ false);

    std::vector<float> out(8, 0.0f);
    a.mix(out.data(), 4); // 2 samples consumed over 4 frames -> finishes
    EXPECT_EQ(a.activeVoices(), 0u);
}

TEST(Audio, MasterVolumeScalesOutput) {
    AudioEngine a;
    a.initialize(48000, 2);
    const SoundHandle snd = a.createSound({1.0f, 1.0f}, 48000);
    a.setMasterVolume(0.5f);
    a.play(snd, 1.0f);

    std::vector<float> out(4, 0.0f);
    a.mix(out.data(), 2);
    EXPECT_FLOAT_EQ(out[0], 0.5f);
}

TEST(Audio, ThreeDSourceOnRightIsLouderOnRight) {
    AudioEngine a;
    a.initialize(48000, 2);
    ListenerState l;
    l.position = {0, 0, 0};
    l.forward  = {0, 0, -1};
    l.up       = {0, 1, 0};
    a.setListener(l);

    const SoundHandle snd = a.createSound(std::vector<float>(16, 1.0f), 48000);
    a.playAt(snd, {10, 0, 0}, 1.0f); // to the listener's right (+x)

    std::vector<float> out(4, 0.0f);
    a.mix(out.data(), 2);
    EXPECT_GT(out[1], out[0]); // right channel louder than left
}

TEST(Audio, GeneratedToneHasSamples) {
    AudioEngine a;
    a.initialize(48000, 2);
    const SoundHandle tone = a.createTone(440.0f, 0.1f);
    EXPECT_TRUE(tone.valid());
}

TEST(Audio, ToneRejectsNonPositiveDuration) {
    AudioEngine a;
    a.initialize(48000, 2);
    const SoundHandle tone = a.createTone(440.0f, -1.0f); // must not over-allocate
    a.play(tone, 1.0f);
    // An empty sound deactivates immediately when mixed.
    std::vector<float> out(4, 0.0f);
    a.mix(out.data(), 2);
    EXPECT_EQ(a.activeVoices(), 0u);
}

TEST(Audio, MixToleratesNullAndZeroFrames) {
    AudioEngine a;
    a.initialize(48000, 2);
    a.mix(nullptr, 4); // must not crash
    std::vector<float> out(2, 7.0f);
    a.mix(out.data(), 0);          // zero frames: no-op, buffer untouched
    EXPECT_FLOAT_EQ(out[0], 7.0f); // confirms early-out before the silence fill
}
