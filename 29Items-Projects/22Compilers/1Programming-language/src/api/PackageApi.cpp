#include "plang/api/PackageApi.h"

#include <algorithm>
#include <cctype>
#include <cstdlib>
#include <sstream>
#include <string_view>
#include <unordered_map>
#include <utility>
#include <vector>

namespace plang::api {

namespace {

std::vector<std::string> splitPath(const std::string& path) {
    std::vector<std::string> parts;
    const std::string route = path.substr(0, path.find('?'));
    std::size_t start = 0;
    while (start < route.size()) {
        while (start < route.size() && route[start] == '/') {
            ++start;
        }
        const std::size_t end = route.find('/', start);
        if (start < route.size()) {
            parts.push_back(route.substr(start, end == std::string::npos ? std::string::npos
                                                                         : end - start));
        }
        if (end == std::string::npos) {
            break;
        }
        start = end + 1;
    }
    return parts;
}

std::string routePath(const std::string& path) {
    return path.substr(0, path.find('?'));
}

std::string percentDecode(const std::string& text) {
    std::string out;
    for (std::size_t index = 0; index < text.size(); ++index) {
        if (text[index] == '+') {
            out.push_back(' ');
            continue;
        }
        if (text[index] == '%' && index + 2 < text.size() &&
            std::isxdigit(static_cast<unsigned char>(text[index + 1])) != 0 &&
            std::isxdigit(static_cast<unsigned char>(text[index + 2])) != 0) {
            const std::string hex = text.substr(index + 1, 2);
            out.push_back(static_cast<char>(std::strtol(hex.c_str(), nullptr, 16)));
            index += 2;
            continue;
        }
        out.push_back(text[index]);
    }
    return out;
}

std::string jsonEscape(const std::string& text) {
    std::ostringstream out;
    for (char ch : text) {
        switch (ch) {
        case '\\':
            out << "\\\\";
            break;
        case '"':
            out << "\\\"";
            break;
        case '\n':
            out << "\\n";
            break;
        default:
            out << ch;
            break;
        }
    }
    return out.str();
}

std::unordered_map<std::string, std::string> parseForm(const std::string& body) {
    std::unordered_map<std::string, std::string> fields;
    std::size_t start = 0;
    while (start <= body.size()) {
        const std::size_t end = body.find('&', start);
        const std::string part =
            body.substr(start, end == std::string::npos ? std::string::npos : end - start);
        const std::size_t equal = part.find('=');
        if (equal == std::string::npos) {
            if (!part.empty()) {
                fields.emplace("name", percentDecode(part));
            }
        } else {
            fields[percentDecode(part.substr(0, equal))] = percentDecode(part.substr(equal + 1));
        }
        if (end == std::string::npos) {
            break;
        }
        start = end + 1;
    }
    return fields;
}

std::unordered_map<std::string, std::string> parseQuery(const std::string& path) {
    const std::size_t marker = path.find('?');
    if (marker == std::string::npos) {
        return {};
    }
    return parseForm(path.substr(marker + 1));
}

std::size_t boundedSizeParam(const std::unordered_map<std::string, std::string>& fields,
                             std::string_view key,
                             std::size_t fallback,
                             std::size_t maximum) {
    const auto found = fields.find(std::string(key));
    if (found == fields.end()) {
        return fallback;
    }
    char* end = nullptr;
    const unsigned long value = std::strtoul(found->second.c_str(), &end, 10);
    if (end == found->second.c_str() || *end != '\0') {
        return fallback;
    }
    return std::min<std::size_t>(value, maximum);
}

std::vector<pkg::DependencyConstraint> parseDependencies(const std::string& text) {
    std::vector<pkg::DependencyConstraint> dependencies;
    std::size_t start = 0;
    while (start < text.size()) {
        const std::size_t end = text.find(',', start);
        std::string entry = text.substr(start, end == std::string::npos ? std::string::npos
                                                                        : end - start);
        entry.erase(std::remove_if(entry.begin(), entry.end(), [](unsigned char ch) {
                        return std::isspace(ch) != 0;
                    }),
                    entry.end());
        if (!entry.empty()) {
            const std::size_t at = entry.find('@');
            dependencies.push_back(pkg::DependencyConstraint{
                at == std::string::npos ? entry : entry.substr(0, at),
                at == std::string::npos ? "*" : entry.substr(at + 1),
                false});
        }
        if (end == std::string::npos) {
            break;
        }
        start = end + 1;
    }
    return dependencies;
}

void appendVersionJson(std::ostringstream& body, const pkg::PackageVersion& version) {
    body << R"({"name":")" << jsonEscape(version.name) << R"(","version":")"
         << jsonEscape(version.version) << R"(","archiveSha256":")"
         << jsonEscape(version.archiveSha256) << R"(","yanked":)"
         << (version.yanked ? "true" : "false") << R"(,"dependencies":[)";
    for (std::size_t index = 0; index < version.dependencies.size(); ++index) {
        if (index > 0) {
            body << ',';
        }
        const auto& dependency = version.dependencies[index];
        body << R"({"name":")" << jsonEscape(dependency.name) << R"(","constraint":")"
             << jsonEscape(dependency.constraint) << R"(","optional":)"
             << (dependency.optional ? "true" : "false") << '}';
    }
    body << "]}";
}

} // namespace

PackageApi::PackageApi(pkg::PackageManager& packageManager)
    : packageManager_(packageManager) {}

ApiResponse PackageApi::handle(const ApiRequest& request) {
    const std::vector<std::string> parts = splitPath(request.path);
    const std::string route = routePath(request.path);
    if (request.method == "POST" && route == "/packages") {
        return createPackage(request);
    }
    if (request.method == "GET" && route == "/packages") {
        return listPackages(request);
    }
    if (request.method == "POST" && route == "/resolve") {
        return resolveDependencies(request);
    }
    if (parts.size() == 2 && parts[0] == "packages") {
        const std::string& name = parts[1];
        if (request.method == "GET") {
            return getPackage(name);
        }
        if (request.method == "PUT" || request.method == "PATCH") {
            return updatePackage(name, request);
        }
        if (request.method == "DELETE") {
            return deletePackage(name);
        }
    }
    if (parts.size() == 3 && parts[0] == "packages" && parts[2] == "versions" &&
        request.method == "POST") {
        return publishVersion(parts[1], request);
    }
    if (parts.size() == 4 && parts[0] == "packages" && parts[2] == "versions") {
        if (request.method == "GET") {
            return getVersion(parts[1], parts[3]);
        }
        if (request.method == "DELETE") {
            return deleteVersion(parts[1], parts[3]);
        }
    }
    if (parts.size() == 5 && parts[0] == "packages" && parts[2] == "versions" &&
        parts[4] == "yank" && request.method == "POST") {
        return yankVersion(parts[1], parts[3]);
    }
    return ApiResponse{404, R"({"error":"route not found"})"};
}

ApiResponse PackageApi::createPackage(const ApiRequest& request) {
    const auto fields = parseForm(request.body);
    const auto foundName = fields.find("name");
    const std::string name = foundName == fields.end() ? request.body : foundName->second;
    if (!pkg::isValidPackageName(name)) {
        return ApiResponse{400, R"({"error":"invalid package name"})"};
    }

    const auto result = packageManager_.createPackage(name);
    return ApiResponse{result.ok ? 201 : 409,
                       result.ok ? R"({"status":"created"})" : R"({"error":"package exists"})"};
}

ApiResponse PackageApi::updatePackage(std::string name, const ApiRequest& request) {
    const auto fields = parseForm(request.body);
    const auto foundName = fields.find("name");
    if (foundName == fields.end() || !pkg::isValidPackageName(foundName->second)) {
        return ApiResponse{400, R"({"error":"invalid package name"})"};
    }
    const auto result = packageManager_.updatePackage(std::move(name), foundName->second);
    if (!result.ok) {
        return ApiResponse{result.message == "Package not found." ? 404 : 409,
                           R"({"error":")" + jsonEscape(result.message) + R"("})"};
    }
    return ApiResponse{200, R"({"status":"updated"})"};
}

ApiResponse PackageApi::listPackages(const ApiRequest& request) const {
    const auto query = parseQuery(request.path);
    const std::size_t limit = boundedSizeParam(query, "limit", 50, 100);
    const std::size_t offset = boundedSizeParam(query, "offset", 0, 100000);
    std::ostringstream body;
    const auto packages = packageManager_.listPackages();
    body << R"({"limit":)" << limit << R"(,"offset":)" << offset << R"(,"total":)"
         << packages.size() << R"(,"packages":[)";
    std::size_t emitted = 0;
    for (std::size_t index = offset; index < packages.size() && emitted < limit; ++index) {
        if (emitted++ > 0) {
            body << ',';
        }
        body << R"({"name":")" << jsonEscape(packages[index].name) << R"(","versions":[)";
        std::size_t versionIndex = 0;
        for (const auto& [versionNumber, version] : packages[index].versions) {
            (void)versionNumber;
            if (versionIndex++ > 0) {
                body << ',';
            }
            appendVersionJson(body, version);
        }
        body << "]}";
    }
    body << "]}";
    return ApiResponse{200, body.str()};
}

ApiResponse PackageApi::getPackage(std::string name) const {
    const auto package = packageManager_.findPackage(name);
    if (!package.has_value()) {
        return ApiResponse{404, R"({"error":"package not found"})"};
    }

    std::ostringstream body;
    body << R"({"name":")" << jsonEscape(package->name) << R"(","versions":[)";
    std::size_t index = 0;
    for (const auto& [versionNumber, version] : package->versions) {
        (void)versionNumber;
        if (index++ > 0) {
            body << ',';
        }
        appendVersionJson(body, version);
    }
    body << "]}";
    return ApiResponse{200, body.str()};
}

ApiResponse PackageApi::deletePackage(std::string name) {
    const auto result = packageManager_.deletePackage(name);
    return ApiResponse{result.ok ? 200 : 404,
                       result.ok ? R"({"status":"deleted"})" : R"({"error":"package not found"})"};
}

ApiResponse PackageApi::publishVersion(std::string name, const ApiRequest& request) {
    const auto fields = parseForm(request.body);
    const auto version = fields.find("version");
    const auto checksum = fields.find("sha256");
    if (version == fields.end() || checksum == fields.end()) {
        return ApiResponse{400, R"({"error":"version and sha256 are required"})"};
    }
    if (!pkg::isValidPackageName(name)) {
        return ApiResponse{400, R"({"error":"invalid package name"})"};
    }
    pkg::PackageVersion packageVersion;
    packageVersion.name = std::move(name);
    packageVersion.version = version->second;
    packageVersion.archiveSha256 = checksum->second;
    if (const auto manifest = fields.find("manifest"); manifest != fields.end()) {
        packageVersion.manifestJson = manifest->second;
    }
    if (const auto deps = fields.find("deps"); deps != fields.end()) {
        packageVersion.dependencies = parseDependencies(deps->second);
        for (const auto& dependency : packageVersion.dependencies) {
            if (!pkg::isValidPackageName(dependency.name) ||
                !pkg::isValidConstraint(dependency.constraint)) {
                return ApiResponse{400, R"({"error":"invalid dependency constraint"})"};
            }
        }
    }

    const auto result = packageManager_.publishVersion(std::move(packageVersion));
    if (!result.ok) {
        return ApiResponse{400, R"({"error":")" + jsonEscape(result.message) + R"("})"};
    }
    return ApiResponse{201, R"({"status":"published"})"};
}

ApiResponse PackageApi::getVersion(std::string name, std::string version) const {
    const auto packageVersion = packageManager_.findVersion(name, version);
    if (!packageVersion.has_value()) {
        return ApiResponse{404, R"({"error":"version not found"})"};
    }
    std::ostringstream body;
    appendVersionJson(body, *packageVersion);
    return ApiResponse{200, body.str()};
}

ApiResponse PackageApi::deleteVersion(std::string name, std::string version) {
    const auto result = packageManager_.deleteVersion(name, version);
    return ApiResponse{result.ok ? 200 : 404,
                       result.ok ? R"({"status":"deleted"})" : R"({"error":"version not found"})"};
}

ApiResponse PackageApi::yankVersion(std::string name, std::string version) {
    const auto result = packageManager_.yankVersion(name, version);
    return ApiResponse{result.ok ? 200 : 404,
                       result.ok ? R"({"status":"yanked"})" : R"({"error":"version not found"})"};
}

ApiResponse PackageApi::resolveDependencies(const ApiRequest& request) const {
    const auto fields = parseForm(request.body);
    const auto deps = fields.find("deps");
    if (deps == fields.end() || deps->second.empty()) {
        return ApiResponse{400, R"({"error":"deps is required"})"};
    }
    const auto dependencies = parseDependencies(deps->second);
    if (dependencies.empty()) {
        return ApiResponse{400, R"({"error":"at least one dependency is required"})"};
    }
    for (const auto& dependency : dependencies) {
        if (!pkg::isValidPackageName(dependency.name) ||
            !pkg::isValidConstraint(dependency.constraint)) {
            return ApiResponse{400, R"({"error":"invalid dependency constraint"})"};
        }
    }
    const auto resolved = packageManager_.resolveDependencies(dependencies);
    if (resolved.empty()) {
        return ApiResponse{404, R"({"error":"no matching dependency versions found"})"};
    }
    std::ostringstream body;
    body << R"({"resolved":[)";
    for (std::size_t index = 0; index < resolved.size(); ++index) {
        if (index > 0) {
            body << ',';
        }
        appendVersionJson(body, resolved[index]);
    }
    body << "]}";
    return ApiResponse{200, body.str()};
}

} // namespace plang::api
