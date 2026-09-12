#pragma once

#include "engine/core/Types.hpp"

#include <functional>

/// @file JobSystem.hpp
/// @brief Thread-pool task scheduler for engine parallelism.
///
/// Offloads asset loads, procgen inference, and independent ECS systems onto worker
/// threads, keeping the main thread responsive. A complete, correct shared-queue pool;
/// per-worker work-stealing deques are a future throughput optimization.
/// Implementation in JobSystem.cpp.

namespace engine::jobs {

using Job = std::function<void()>;

class JobSystem {
public:
    JobSystem() = default;
    ~JobSystem();

    JobSystem(const JobSystem&)            = delete;
    JobSystem& operator=(const JobSystem&) = delete;

    /// Spin up workers. `workerCount == 0` => hardware_concurrency() - 1.
    void start(u32 workerCount = 0);

    /// Drain, join, and tear down all workers.
    void stop();

    /// Enqueue a job for asynchronous execution.
    void dispatch(Job job);

    /// Split [0, count) into chunks and run `fn(i)` across workers; blocks until done.
    void parallelFor(usize count, const std::function<void(usize)>& fn, usize grain = 64);

    /// Block until all currently queued/running jobs complete.
    void waitForIdle();

    [[nodiscard]] u32 workerCount() const noexcept { return workerCount_; }

private:
    struct Impl;
    Impl* impl_ = nullptr; // shared-queue pool (work-stealing deques: future optimization)
    u32   workerCount_ = 0;
};

} // namespace engine::jobs
