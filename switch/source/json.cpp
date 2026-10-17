#include "sunny/json.hpp"
#include <cstdio>
#include <cstdlib>
#include <limits>

namespace sunny {
bool validUtf8(const std::string& s) {
    for (size_t i = 0; i < s.size();) {
        unsigned char a = s[i++];
        if (a < 0x80) continue;
        int n; uint32_t cp, min;
        if (a >= 0xc2 && a <= 0xdf) { n = 1; cp = a & 31; min = 0x80; }
        else if (a >= 0xe0 && a <= 0xef) { n = 2; cp = a & 15; min = 0x800; }
        else if (a >= 0xf0 && a <= 0xf4) { n = 3; cp = a & 7; min = 0x10000; }
        else return false;
        while (n--) { if (i == s.size() || (s[i] & 0xc0) != 0x80) return false; cp = (cp << 6) | (s[i++] & 63); }
        if (cp < min || cp > 0x10ffff || (cp >= 0xd800 && cp <= 0xdfff)) return false;
    }
    return true;
}
namespace {
void utf8(std::string& s, uint32_t cp) {
    if (cp < 0x80) s += char(cp);
    else if (cp < 0x800) { s += char(0xc0 | (cp >> 6)); s += char(0x80 | (cp & 63)); }
    else if (cp < 0x10000) { s += char(0xe0 | (cp >> 12)); s += char(0x80 | ((cp >> 6) & 63)); s += char(0x80 | (cp & 63)); }
    else { s += char(0xf0 | (cp >> 18)); s += char(0x80 | ((cp >> 12) & 63)); s += char(0x80 | ((cp >> 6) & 63)); s += char(0x80 | (cp & 63)); }
}
class Parser {
    const std::string& s; size_t p = 0, nodes = 0;
    [[noreturn]] void fail() const { throw std::runtime_error("服务响应不是有效 JSON"); }
    void ws() { while (p < s.size() && (s[p] == ' ' || s[p] == '\t' || s[p] == '\r' || s[p] == '\n')) ++p; }
    bool take(char c) { ws(); if (p < s.size() && s[p] == c) { ++p; return true; } return false; }
    unsigned hex() { unsigned v = 0; for (int i = 0; i < 4; ++i) { if (p == s.size()) fail(); char c = s[p++]; v <<= 4; if (c >= '0' && c <= '9') v += c - '0'; else if (c >= 'a' && c <= 'f') v += c - 'a' + 10; else if (c >= 'A' && c <= 'F') v += c - 'A' + 10; else fail(); } return v; }
    std::string str() {
        if (!take('"')) fail();
        std::string out;
        while (p < s.size()) {
            unsigned char c = s[p++]; if (c == '"') return out;
            if (c < 32) fail();
            if (c != '\\') { out += char(c); continue; }
            if (p == s.size()) fail();
            c = s[p++];
            switch (c) {
            case '"': case '\\': case '/': out += char(c); break;
            case 'b': out += '\b'; break; case 'f': out += '\f'; break;
            case 'n': out += '\n'; break; case 'r': out += '\r'; break; case 't': out += '\t'; break;
            case 'u': { uint32_t cp = hex(); if (cp >= 0xd800 && cp <= 0xdbff) { if (p + 2 > s.size() || s.substr(p, 2) != "\\u") fail(); p += 2; unsigned low = hex(); if (low < 0xdc00 || low > 0xdfff) fail(); cp = 0x10000 + ((cp - 0xd800) << 10) + low - 0xdc00; } else if (cp >= 0xdc00 && cp <= 0xdfff) fail(); utf8(out, cp); break; }
            default: fail();
            }
        } fail();
    }
    Json value(int depth) {
        ws(); if (p == s.size() || depth > 32 || ++nodes > 200000) fail();
        if (s[p] == '"') return str();
        if (take('{')) { Json::Object o; if (take('}')) return o; do { std::string key = str(); if (!take(':')) fail(); if (!o.emplace(key, value(depth + 1)).second) fail(); } while (take(',')); if (!take('}')) fail(); return o; }
        if (take('[')) { Json::Array a; if (take(']')) return a; do { a.push_back(value(depth + 1)); } while (take(',')); if (!take(']')) fail(); return a; }
        for (auto literal : {"true", "false", "null"}) { size_t n = std::char_traits<char>::length(literal); if (s.compare(p, n, literal) == 0) { p += n; if (*literal == 'n') return nullptr; return *literal == 't'; } }
        size_t begin = p; if (s[p] == '-') ++p;
        if (p == s.size()) fail();
        if (s[p] == '0') ++p; else { if (s[p] < '1' || s[p] > '9') fail(); while (p < s.size() && s[p] >= '0' && s[p] <= '9') ++p; }
        if (p < s.size() && s[p] == '.') { ++p; size_t start = p; while (p < s.size() && s[p] >= '0' && s[p] <= '9') ++p; if (start == p) fail(); }
        if (p < s.size() && (s[p] == 'e' || s[p] == 'E')) { ++p; if (p < s.size() && (s[p] == '+' || s[p] == '-')) ++p; size_t start = p; while (p < s.size() && s[p] >= '0' && s[p] <= '9') ++p; if (start == p) fail(); }
        double v = std::strtod(s.substr(begin, p - begin).c_str(), nullptr); if (!std::isfinite(v)) fail(); return v;
    }
public:
    explicit Parser(const std::string& text) : s(text) {}
    Json run() { Json v = value(0); ws(); if (p != s.size()) fail(); return v; }
};
std::string quote(const std::string& s) {
    if (!validUtf8(s)) throw std::runtime_error("文本不是有效 UTF-8");
    std::string out = "\"";
    for (unsigned char c : s) { if (c == '"' || c == '\\') { out += '\\'; out += char(c); } else if (c < 32) { char buf[7]; std::snprintf(buf, sizeof buf, "\\u%04x", c); out += buf; } else out += char(c); }
    return out + "\"";
}
}
Json Json::parse(const std::string& text, size_t limit) { if (text.size() > limit || !validUtf8(text)) throw std::runtime_error("服务响应过大或文本编码无效"); return Parser(text).run(); }
std::string Json::text(const std::string& fallback) const { auto p = std::get_if<std::string>(&value_); return p ? *p : fallback; }
bool Json::boolean(bool fallback) const { auto p = std::get_if<bool>(&value_); return p ? *p : fallback; }
double Json::number(double fallback) const { auto p = std::get_if<double>(&value_); return p ? *p : fallback; }
int64_t Json::integer(int64_t fallback) const { double n = number(double(fallback)); return n >= -9007199254740991.0 && n <= 9007199254740991.0 ? int64_t(n) : fallback; }
const Json::Array& Json::array() const { static const Array empty; auto p = std::get_if<Array>(&value_); return p ? *p : empty; }
const Json::Object& Json::fields() const { static const Object empty; auto p = std::get_if<Object>(&value_); return p ? *p : empty; }
bool Json::has(const std::string& key) const { return fields().count(key) != 0; }
const Json& Json::operator[](const std::string& key) const { static const Json nil; const auto& o = fields(); auto it = o.find(key); return it == o.end() ? nil : it->second; }
Json& Json::operator[](const std::string& key) { if (null()) value_ = Object{}; if (!object()) throw std::runtime_error("JSON 对象类型错误"); return std::get<Object>(value_)[key]; }
std::string Json::dump() const {
    if (null()) return "null";
    if (auto p = std::get_if<bool>(&value_)) return *p ? "true" : "false";
    if (auto p = std::get_if<double>(&value_)) { if (!std::isfinite(*p)) throw std::runtime_error("JSON 数值无效"); char b[64]; std::snprintf(b, sizeof b, "%.17g", *p); return b; }
    if (auto p = std::get_if<std::string>(&value_)) return quote(*p);
    if (auto p = std::get_if<Array>(&value_)) { std::string out = "["; for (const auto& v : *p) { if (out.size() > 1) out += ','; out += v.dump(); } return out + ']'; }
    std::string out = "{"; for (const auto& [k, v] : fields()) { if (out.size() > 1) out += ','; out += quote(k) + ':' + v.dump(); } return out + '}';
}
}
