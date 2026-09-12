#include "engine/core/jobs/JobSystem.hpp"

#include <gtest/gtest.h>

#include <atomic>
#include <cstddef>
#include <vector>

using engine::jobs::JobSystem;

TEST(JobSystem, DispatchRunsEveryJob) {
    JobSystem js;
    js.start(4);
    std::atomic<int> counter{0};
    for (int i = 0; i < 1000; ++i) {
        js.dispatch([&counter] { counter.fetch_add(1); });
    }
    js.waitForIdle();
    EXPECT_EQ(counter.load(), 1000);
    js.stop();
}

TEST(JobSystem, ParallelForCoversEntireRange) {
    JobSystem js;
    js.start();
    std::vector<int> data(1000, -1);
    js.parallelFor(
        data.size(), [&data](std::size_t i) { data[i] = static_cast<int>(i) * 2; }, 64);
    bool allCorrect = true;
    for (std::size_t i = 0; i < data.size(); ++i) {
        if (data[i] != static_cast<int>(i) * 2) {
            allCorrect = false;
        }
    }
    EXPECT_TRUE(allCorrect);
    js.stop();
}

TEST(JobSystem, RunsInlineWhenNotStarted) {
    JobSystem        js; // no workers
    std::atomic<int> counter{0};
    js.dispatch([&counter] { counter.fetch_add(1); }); // executes synchronously
    EXPECT_EQ(counter.load(), 1);
}
