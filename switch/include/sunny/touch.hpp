#pragma once
#include <cmath>
#include <cstdint>

namespace sunny {
struct Point { float x = 0, y = 0; };
struct Bounds {
    float x = 0, y = 0, w = 0, h = 0;
    bool contains(Point p) const { return p.x >= x && p.x < x + w && p.y >= y && p.y < y + h; }
};
enum class Gesture { None, Tap, Scroll, SwipeLeft, SwipeRight };
struct TouchResult { Gesture gesture = Gesture::None; Point point; float dy = 0; };
class TouchTracker {
    bool active_ = false, moved_ = false;
    int64_t finger_ = 0;
    Point start_, last_;
public:
    bool active() const { return active_; }
    void begin(int64_t finger, Point point) {
        if (active_) return;
        active_ = true; moved_ = false; finger_ = finger; start_ = last_ = point;
    }
    TouchResult move(int64_t finger, Point point) {
        if (!active_ || finger != finger_) return {};
        float dx = point.x - start_.x, dy = point.y - start_.y;
        if (std::hypot(dx, dy) > 12) moved_ = true;
        TouchResult result; result.point = point;
        if (moved_ && std::abs(dy) > std::abs(dx)) { result.gesture = Gesture::Scroll; result.dy = point.y - last_.y; }
        last_ = point;
        return result;
    }
    TouchResult end(int64_t finger, Point point) {
        if (!active_ || finger != finger_) return {};
        active_ = false;
        float dx = point.x - start_.x, dy = point.y - start_.y;
        TouchResult result; result.point = point;
        if (!moved_ && std::hypot(dx, dy) <= 12) result.gesture = Gesture::Tap;
        else if (std::abs(dx) > 65 && std::abs(dx) > std::abs(dy) * 1.25f)
            result.gesture = dx < 0 ? Gesture::SwipeLeft : Gesture::SwipeRight;
        return result;
    }
    void cancel() { active_ = false; }
};
}
