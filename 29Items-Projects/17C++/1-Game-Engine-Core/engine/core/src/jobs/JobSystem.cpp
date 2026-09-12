#include "engine/core/jobs/JobSystem.hpp"

#include "engine/core/Log.hpp"

#include <atomic>
#include <condition_variable>
#include <memory>
#include <mutex>
#include <queue>
#include <thread>
#include <vector>

/// @file JobSystem.cpp
/// @brief Shared-queue thread pool. Correct and complete; a single global queue is a
/// known contention point, so per-worker work-stealing deques are a future throughput
/// optimization (the public API would not change).

namespace engine::jobs {

struct JobSystem::Impl {
    std::vector<std::thread> workers;
    std::queue<Job>          queue;
    std::mutex               mutex;
    std::condition_variable  workAvailable;
    std::condition_variable  allDone;
    bool                     running = false;
    usize                    pending = 0; // queued + in-flight, guarded by `mutex`

    void workerLoop() {
        for (;;) {
            Job job;
            {
                std::unique_lock lock(mutex);
                workAvailable.wait(lock, [this] { return !queue.empty() || !running; });
                if (!running && queue.empty()) {
                    return;
                }
                job = std::move(queue.front());
                queue.pop();
            }
            job();
            {
                std::scoped_lock lock(mutex);
                if (--pending == 0) {
                    allDone.notify_all();
                }
            }
        }
    }
};

JobSystem::~JobSystem() {
    stop();
}

void JobSystem::start(u32 workerCount) {
    if (impl_ != nullptr) {
        return; // already started
    }
    if (workerCount == 0) {
        const u32 hw = std::thread::hardware_concurrency();
        workerCount  = hw > 1 ? hw - 1 : 1; // leave one core for the main thread
    }
    workerCount_ = workerCount;
    impl_        = new Impl();
    impl_->running = true;
    impl_->workers.reserve(workerCount);
    for (u32 i = 0; i < workerCount; ++i) {
        impl_->workers.emplace_back([this] { impl_->workerLoop(); });
    }
    log::info("[Jobs] started {} worker thread(s)", workerCount);
}

void JobSystem::stop() {
    if (impl_ == nullptr) {
        return;
    }
    waitForIdle();
    {
        std::scoped_lock lock(impl_->mutex);
        impl_->running = false;
    }
    impl_->workAvailable.notify_all();
    for (auto& t : impl_->workers) {
        if (t.joinable()) {
            t.join();
        }
    }
    delete impl_;
    impl_        = nullptr;
    workerCount_ = 0;
}

void JobSystem::dispatch(Job job) {
    // Without workers, run synchronously so callers always make progress.
    if (impl_ == nullptr) {
        job();
        return;
    }
    {
        std::scoped_lock lock(impl_->mutex);
        impl_->queue.push(std::move(job));
        ++impl_->pending;
    }
    impl_->workAvailable.notify_one();
}

void JobSystem::parallelFor(usize count, const std::function<void(usize)>& fn, usize grain) {
    if (count == 0) {
        return;
    }
    if (impl_ == nullptr) {
        for (usize i = 0; i < count; ++i) {
            fn(i);
        }
        return;
    }

    grain = grain == 0 ? 1 : grain;
    const usize chunks = (count + grain - 1) / grain;
    auto remaining     = std::make_shared<std::atomic<usize>>(chunks);
    auto mtx           = std::make_shared<std::mutex>();
    auto cv            = std::make_shared<std::condition_variable>();

    for (usize c = 0; c < chunks; ++c) {
        const usize begin = c * grain;
        const usize end   = std::min(begin + grain, count);
        dispatch([=, &fn] {
            for (usize i = begin; i < end; ++i) {
                fn(i);
            }
            if (remaining->fetch_sub(1) == 1) {
                std::scoped_lock lock(*mtx);
                cv->notify_one();
            }
        });
    }

    std::unique_lock lock(*mtx);
    cv->wait(lock, [&] { return remaining->load() == 0; });
}

void JobSystem::waitForIdle() {
    if (impl_ == nullptr) {
        return;
    }
    std::unique_lock lock(impl_->mutex);
    impl_->allDone.wait(lock, [this] { return impl_->pending == 0; });
}

} // namespace engine::jobs
