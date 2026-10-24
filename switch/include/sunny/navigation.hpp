#pragma once
#include <algorithm>
#include <cstdint>
#include <cstdlib>

namespace sunny {
enum Navigation : uint8_t { NavLeft = 1, NavRight = 2, NavUp = 4, NavDown = 8 };

// Merge physical D-pad and left-stick input before repeat handling. Axis hysteresis prevents
// stick drift and small fluctuations at the dead-zone boundary from generating extra presses.
class NavigationRepeat {
    int horizontal_ = 0, vertical_ = 0;
    uint8_t held_ = 0;
    uint32_t heldSince_ = 0, repeatedAt_ = 0;
    static int axis(int value, int previous) {
        if (value >= 16000 || (previous == 1 && value > 10000)) return 1;
        if (value <= -16000 || (previous == -1 && value < -10000)) return -1;
        return 0;
    }
public:
    uint8_t update(uint8_t digital, int x, int y, uint32_t now) {
        horizontal_ = axis(x, horizontal_);
        vertical_ = axis(y, vertical_);
        uint8_t stick = 0;
        if (horizontal_ && (!vertical_ || std::abs(x) >= std::abs(y)))
            stick = horizontal_ > 0 ? NavRight : NavLeft;
        else if (vertical_)
            stick = vertical_ > 0 ? NavUp : NavDown;
        uint8_t next = digital | stick;
        uint8_t pressed = next & ~held_;
        if (next != held_) {
            heldSince_ = repeatedAt_ = now;
            held_ = next;
        } else if (next && uint32_t(now - heldSince_) >= 350 && uint32_t(now - repeatedAt_) >= 120) {
            pressed = next;
            repeatedAt_ = now;
        }
        return pressed;
    }
};
}
