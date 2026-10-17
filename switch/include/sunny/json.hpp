#pragma once
#include <cmath>
#include <cstdint>
#include <map>
#include <stdexcept>
#include <string>
#include <variant>
#include <vector>

namespace sunny {
// Bounded JSON for API/config responses. No eval, implicit conversions or recursive unbounded input.
class Json {
public:
    using Array = std::vector<Json>;
    using Object = std::map<std::string, Json>;
    using Value = std::variant<std::nullptr_t, bool, double, std::string, Array, Object>;
    Json() : value_(nullptr) {}
    Json(std::nullptr_t) : value_(nullptr) {}
    Json(bool v) : value_(v) {}
    Json(double v) : value_(v) {}
    Json(int v) : value_(double(v)) {}
    Json(int64_t v) : value_(double(v)) {}
    Json(const char* v) : value_(std::string(v)) {}
    Json(std::string v) : value_(std::move(v)) {}
    Json(Array v) : value_(std::move(v)) {}
    Json(Object v) : value_(std::move(v)) {}
    static Json parse(const std::string& text, size_t limit = 8 * 1024 * 1024);
    std::string dump() const;
    bool null() const { return std::holds_alternative<std::nullptr_t>(value_); }
    bool object() const { return std::holds_alternative<Object>(value_); }
    std::string text(const std::string& fallback = "") const;
    bool boolean(bool fallback = false) const;
    int64_t integer(int64_t fallback = 0) const;
    double number(double fallback = 0) const;
    const Array& array() const;
    const Object& fields() const;
    const Json& operator[](const std::string& key) const;
    Json& operator[](const std::string& key);
    bool has(const std::string& key) const;
private:
    Value value_;
};
bool validUtf8(const std::string& text);
}
