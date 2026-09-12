// ============================================================================
//  common/Config.cpp
//  Loads the engine configuration from the YAML subset used by
//  config/trading_engine.yaml, then overlays environment variables. No external
//  YAML dependency: the parser handles exactly the forms this project uses
//  (nested maps by 2-space indentation, inline lists, and a list of instrument
//  maps). Missing file -> validated defaults (handy for tests/CI).
// ============================================================================
#include "common/Config.hpp"

#include <algorithm>
#include <charconv>
#include <cstdlib>
#include <fstream>
#include <map>
#include <sstream>
#include <utility>
#include <vector>

namespace rts::config {
namespace {

std::string trim(std::string_view s) {
    std::size_t b = 0, e = s.size();
    while (b < e && (s[b] == ' ' || s[b] == '\t')) ++b;
    while (e > b && (s[e - 1] == ' ' || s[e - 1] == '\t' || s[e - 1] == '\r')) --e;
    return std::string(s.substr(b, e - b));
}

std::string stripQuotes(std::string s) {
    if (s.size() >= 2 && (s.front() == '"' || s.front() == '\'') && s.back() == s.front())
        return s.substr(1, s.size() - 2);
    return s;
}

// Cut an inline comment (none of our scalar values contain '#').
std::string stripComment(const std::string& line) {
    auto pos = line.find('#');
    return pos == std::string::npos ? line : line.substr(0, pos);
}

std::size_t indentOf(const std::string& line) {
    std::size_t i = 0;
    while (i < line.size() && line[i] == ' ') ++i;
    return i;
}

struct Parsed {
    std::map<std::string, std::string> scalars;   // dotted path -> value
    std::vector<std::string>           instruments;  // raw "{...}" items
};

Parsed parseYaml(std::istream& in) {
    Parsed out;
    std::vector<std::pair<std::size_t, std::string>> stack;  // (indent, key)
    std::string raw;

    auto joinPath = [&](const std::string& leaf) {
        std::string p;
        for (auto& [ind, k] : stack) { p += k; p += '.'; }
        p += leaf;
        return p;
    };

    while (std::getline(in, raw)) {
        const std::string line = stripComment(raw);
        const std::string t    = trim(line);
        if (t.empty()) continue;
        const std::size_t indent = indentOf(line);

        while (!stack.empty() && stack.back().first >= indent) stack.pop_back();

        if (t.rfind("- ", 0) == 0) {                       // list item
            std::string parent;
            for (auto& [ind, k] : stack) { parent += k; parent += '.'; }
            if (!parent.empty()) parent.pop_back();
            if (parent == "instruments") out.instruments.push_back(trim(t.substr(2)));
            continue;
        }

        const auto colon = t.find(':');
        if (colon == std::string::npos) continue;
        const std::string key = trim(t.substr(0, colon));
        const std::string val = trim(t.substr(colon + 1));
        if (val.empty()) {
            stack.emplace_back(indent, key);              // map header
        } else {
            out.scalars[joinPath(key)] = stripQuotes(val);
        }
    }
    return out;
}

bool toBool(const std::string& v) { return v == "true" || v == "1" || v == "yes"; }

std::int64_t toI64(const std::string& v, std::int64_t def) {
    std::int64_t out = def;
    std::from_chars(v.data(), v.data() + v.size(), out);
    return out;
}

double toDouble(const std::string& v, double def) {
    if (v.empty()) return def;
    try { return std::stod(v); } catch (...) { return def; }  // malformed -> default
}

// Safe unsigned parse: returns def on any non-numeric input (never throws).
std::uint32_t toU32(const std::string& v, std::uint32_t def = 0) {
    std::uint32_t out = def;
    if (std::from_chars(v.data(), v.data() + v.size(), out).ec != std::errc{}) return def;
    return out;
}

// Parse "[2, 3, 4, 5]" -> {2,3,4,5}. Non-numeric tokens are skipped, not fatal.
std::vector<unsigned> toUnsignedList(const std::string& v) {
    std::vector<unsigned> out;
    std::string inner = v;
    if (!inner.empty() && inner.front() == '[') inner = inner.substr(1);
    if (!inner.empty() && inner.back() == ']') inner.pop_back();
    std::stringstream ss(inner);
    std::string tok;
    while (std::getline(ss, tok, ',')) {
        tok = trim(tok);
        unsigned u = 0;
        if (!tok.empty() &&
            std::from_chars(tok.data(), tok.data() + tok.size(), u).ec == std::errc{}) {
            out.push_back(u);
        }
    }
    return out;
}

// Parse "{ symbol_id: 1, ticker: AAPL, venue: XNAS }" into a key->value map.
std::map<std::string, std::string> parseInlineMap(const std::string& item) {
    std::map<std::string, std::string> kv;
    std::string inner = item;
    if (!inner.empty() && inner.front() == '{') inner = inner.substr(1);
    if (!inner.empty() && inner.back() == '}') inner.pop_back();
    std::stringstream ss(inner);
    std::string pair;
    while (std::getline(ss, pair, ',')) {
        auto c = pair.find(':');
        if (c == std::string::npos) continue;
        kv[trim(pair.substr(0, c))] = stripQuotes(trim(pair.substr(c + 1)));
    }
    return kv;
}

const char* env(const char* name) { return std::getenv(name); }

}  // namespace

EngineConfig EngineConfig::load(const std::string& path) {
    EngineConfig cfg{};  // defaults

    std::ifstream file(path);
    if (file) {
        const Parsed p = parseYaml(file);
        auto get = [&](const char* key) -> const std::string* {
            auto it = p.scalars.find(key);
            return it == p.scalars.end() ? nullptr : &it->second;
        };

        if (auto* v = get("environment"))     cfg.environment = *v;
        if (auto* v = get("log_level"))       cfg.logLevel = *v;
        if (auto* v = get("use_huge_pages"))  cfg.useHugePages = toBool(*v);
        if (auto* v = get("trading_cores"))   cfg.tradingCores = toUnsignedList(*v);

        if (auto* v = get("fix.host"))           cfg.fix.host = *v;
        if (auto* v = get("fix.port"))           cfg.fix.port = static_cast<std::uint16_t>(toI64(*v, 0));
        if (auto* v = get("fix.sender_comp_id")) cfg.fix.senderCompId = *v;
        if (auto* v = get("fix.target_comp_id")) cfg.fix.targetCompId = *v;
        if (auto* v = get("fix.heartbeat_secs")) cfg.fix.heartbeatSecs = static_cast<int>(toI64(*v, 30));

        if (auto* v = get("oracle.host"))     cfg.oracle.host = *v;
        if (auto* v = get("oracle.port"))     cfg.oracle.port = static_cast<std::uint16_t>(toI64(*v, 1521));
        if (auto* v = get("oracle.service"))  cfg.oracle.service = *v;
        if (auto* v = get("oracle.user"))     cfg.oracle.user = *v;
        if (auto* v = get("oracle.pool_min")) cfg.oracle.poolMin = static_cast<int>(toI64(*v, 2));
        if (auto* v = get("oracle.pool_max")) cfg.oracle.poolMax = static_cast<int>(toI64(*v, 8));

        if (auto* v = get("ml.enabled"))       cfg.ml.enabled = toBool(*v);
        if (auto* v = get("ml.grpc_endpoint")) cfg.ml.grpcEndpoint = *v;
        if (auto* v = get("ml.model_version")) cfg.ml.modelVersion = *v;
        if (auto* v = get("ml.timeout_ms"))    cfg.ml.timeoutMs = static_cast<int>(toI64(*v, 20));

        if (auto* v = get("risk.max_order_qty"))    cfg.risk.maxOrderQty = toI64(*v, 10000);
        if (auto* v = get("risk.max_position_qty")) cfg.risk.maxPositionQty = toI64(*v, 100000);
        if (auto* v = get("risk.max_notional"))     cfg.risk.maxNotional = toDouble(*v, 5e6);
        if (auto* v = get("risk.price_collar_pct")) cfg.risk.priceCollarPct = toDouble(*v, 0.05);

        if (auto* v = get("disruptor.ring_size"))     cfg.ringSize = static_cast<std::size_t>(toI64(*v, 1 << 20));
        if (auto* v = get("disruptor.wait_strategy")) cfg.waitStrategy = *v;

        for (const auto& item : p.instruments) {
            auto kv = parseInlineMap(item);
            InstrumentCfg ic{};
            if (kv.count("symbol_id")) ic.symbolId = toU32(kv["symbol_id"]);
            ic.ticker = kv.count("ticker") ? kv["ticker"] : "";
            ic.venue  = kv.count("venue")  ? kv["venue"]  : "";
            cfg.instruments.push_back(ic);
        }
    }

    // ---- Environment overrides (take precedence over the file) -------------
    if (auto* v = env("TRADING_ENV"))        cfg.environment = v;
    if (auto* v = env("LOG_LEVEL"))          cfg.logLevel = v;
    if (auto* v = env("FIX_HOST"))           cfg.fix.host = v;
    if (auto* v = env("FIX_PORT"))           cfg.fix.port = static_cast<std::uint16_t>(std::atoi(v));
    if (auto* v = env("FIX_SENDER_COMP_ID")) cfg.fix.senderCompId = v;
    if (auto* v = env("FIX_TARGET_COMP_ID")) cfg.fix.targetCompId = v;
    if (auto* v = env("ORACLE_HOST"))        cfg.oracle.host = v;
    if (auto* v = env("ORACLE_USER"))        cfg.oracle.user = v;
    if (auto* v = env("ORACLE_PASSWORD"))    cfg.oracle.password = v;
    if (auto* v = env("ML_GRPC_ENDPOINT"))   cfg.ml.grpcEndpoint = v;
    if (auto* v = env("ML_MODEL_VERSION"))   cfg.ml.modelVersion = v;

    // Provide sensible defaults so a bare config still validates.
    if (cfg.fix.host.empty())         cfg.fix.host = "127.0.0.1";
    if (cfg.fix.senderCompId.empty()) cfg.fix.senderCompId = "TRADER1";
    if (cfg.fix.targetCompId.empty()) cfg.fix.targetCompId = "EXCHANGE";

    cfg.validate();
    return cfg;
}

}  // namespace rts::config
