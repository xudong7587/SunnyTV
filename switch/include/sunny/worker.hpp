#pragma once
#include <atomic>
#include <condition_variable>
#include <deque>
#include <functional>
#include <future>
#include <mutex>
#include <thread>

namespace sunny {
// Serial queue preserves Playing -> Progress -> Stopped ordering. Network tasks receive cancel.
class Worker {
    std::mutex mutex_;
    std::condition_variable ready_;
    std::deque<std::function<void()>> queue_;
    std::atomic_bool stopping_{false};
    std::thread thread_;
public:
    std::atomic_bool cancel{false};
    Worker() : thread_([this] { for (;;) { std::function<void()> job; { std::unique_lock<std::mutex> lock(mutex_); ready_.wait(lock, [this] { return stopping_ || !queue_.empty(); }); if (stopping_) return; job = std::move(queue_.front()); queue_.pop_front(); } job(); } }) {}
    ~Worker() { shutdown(); }
    void shutdown() { cancel = true; { std::lock_guard<std::mutex> lock(mutex_); stopping_ = true; queue_.clear(); } ready_.notify_all(); if (thread_.joinable()) thread_.join(); }
    template <typename F> auto submit(F f) -> std::future<decltype(f())> {
        using Result = decltype(f()); auto task = std::make_shared<std::packaged_task<Result()>>(std::move(f)); auto future = task->get_future();
        { std::lock_guard<std::mutex> lock(mutex_); if (queue_.size() >= 128 || stopping_) throw std::runtime_error("后台任务过多"); queue_.push_back([task] { (*task)(); }); }
        ready_.notify_one(); return future;
    }
};
}
