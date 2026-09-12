#include "plang/pkg/PackageManager.h"

#include <algorithm>
#include <cctype>
#include <exception>
#include <regex>
#include <sstream>
#include <utility>

namespace plang::pkg {

namespace {

constexpr std::size_t maxPackageNameLength = 128;
constexpr std::size_t maxManifestBytes = 64 * 1024;
constexpr std::size_t maxDependencyCount = 256;

struct VersionParts {
    int major{0};
    int minor{0};
    int patch{0};
};

std::optional<VersionParts> parseVersion(const std::string& version) {
    static const std::regex semver(R"(^(\d+)\.(\d+)\.(\d+)(-[0-9A-Za-z.-]+)?$)");
    std::smatch match;
    if (!std::regex_match(version, match, semver)) {
        return std::nullopt;
    }
    try {
        return VersionParts{std::stoi(match[1].str()),
                            std::stoi(match[2].str()),
                            std::stoi(match[3].str())};
    } catch (const std::exception&) {
        return std::nullopt;
    }
}

int compareVersion(const std::string& lhs, const std::string& rhs) {
    const auto left = parseVersion(lhs);
    const auto right = parseVersion(rhs);
    if (!left.has_value() || !right.has_value()) {
        return lhs.compare(rhs);
    }
    if (left->major != right->major) {
        return left->major < right->major ? -1 : 1;
    }
    if (left->minor != right->minor) {
        return left->minor < right->minor ? -1 : 1;
    }
    if (left->patch != right->patch) {
        return left->patch < right->patch ? -1 : 1;
    }
    return 0;
}

std::string trim(std::string text) {
    const auto begin = std::find_if_not(text.begin(), text.end(), [](unsigned char ch) {
        return std::isspace(ch) != 0;
    });
    const auto end = std::find_if_not(text.rbegin(), text.rend(), [](unsigned char ch) {
                         return std::isspace(ch) != 0;
                     }).base();
    if (begin >= end) {
        return {};
    }
    return std::string(begin, end);
}

} // namespace

PackageMutationResult PackageManager::createPackage(std::string name) {
    if (!isValidPackageName(name)) {
        return PackageMutationResult{false, "Invalid package name."};
    }
    if (packages_.contains(name)) {
        return PackageMutationResult{false, "Package already exists."};
    }
    packages_.emplace(name, PackageRecord{name, {}});
    return PackageMutationResult{true, "Package created."};
}

PackageMutationResult PackageManager::updatePackage(std::string oldName, std::string newName) {
    if (!isValidPackageName(oldName) || !isValidPackageName(newName)) {
        return PackageMutationResult{false, "Invalid package name."};
    }
    auto found = packages_.find(oldName);
    if (found == packages_.end()) {
        return PackageMutationResult{false, "Package not found."};
    }
    if (oldName != newName && packages_.contains(newName)) {
        return PackageMutationResult{false, "Package already exists."};
    }

    PackageRecord record = std::move(found->second);
    packages_.erase(found);
    record.name = newName;
    for (auto& [versionNumber, version] : record.versions) {
        (void)versionNumber;
        version.name = newName;
    }
    packages_.emplace(newName, std::move(record));
    return PackageMutationResult{true, "Package updated."};
}

PackageMutationResult PackageManager::publishVersion(PackageVersion version) {
    if (!isValidPackageName(version.name)) {
        return PackageMutationResult{false, "Invalid package name."};
    }
    if (!isValidVersion(version.version)) {
        return PackageMutationResult{false, "Invalid semantic version."};
    }
    if (!isValidArchiveSha256(version.archiveSha256)) {
        return PackageMutationResult{false, "Archive checksum must be a 64-character SHA-256 hex digest."};
    }
    if (!isValidManifestJson(version.manifestJson)) {
        return PackageMutationResult{false, "Manifest JSON must be an object or array under 64 KiB."};
    }
    if (version.dependencies.size() > maxDependencyCount) {
        return PackageMutationResult{false, "Too many dependencies."};
    }
    for (const auto& dependency : version.dependencies) {
        if (!isValidPackageName(dependency.name) || !isValidConstraint(dependency.constraint)) {
            return PackageMutationResult{false, "Invalid dependency constraint."};
        }
    }

    auto [it, inserted] =
        packages_.try_emplace(version.name, PackageRecord{version.name, {}});
    (void)inserted;
    if (it->second.versions.contains(version.version)) {
        return PackageMutationResult{false, "Version already exists."};
    }
    it->second.versions.emplace(version.version, std::move(version));
    return PackageMutationResult{true, "Version published."};
}

PackageMutationResult PackageManager::yankVersion(const std::string& name,
                                                  const std::string& version) {
    auto package = packages_.find(name);
    if (package == packages_.end()) {
        return PackageMutationResult{false, "Package not found."};
    }
    auto foundVersion = package->second.versions.find(version);
    if (foundVersion == package->second.versions.end()) {
        return PackageMutationResult{false, "Version not found."};
    }
    foundVersion->second.yanked = true;
    return PackageMutationResult{true, "Version yanked."};
}

PackageMutationResult PackageManager::deleteVersion(const std::string& name,
                                                    const std::string& version) {
    auto package = packages_.find(name);
    if (package == packages_.end()) {
        return PackageMutationResult{false, "Package not found."};
    }
    if (package->second.versions.erase(version) == 0) {
        return PackageMutationResult{false, "Version not found."};
    }
    return PackageMutationResult{true, "Version deleted."};
}

std::optional<PackageRecord> PackageManager::findPackage(const std::string& name) const {
    const auto found = packages_.find(name);
    if (found == packages_.end()) {
        return std::nullopt;
    }
    return found->second;
}

std::optional<PackageVersion> PackageManager::findVersion(const std::string& name,
                                                          const std::string& version) const {
    const auto package = packages_.find(name);
    if (package == packages_.end()) {
        return std::nullopt;
    }
    const auto foundVersion = package->second.versions.find(version);
    if (foundVersion == package->second.versions.end()) {
        return std::nullopt;
    }
    return foundVersion->second;
}

std::vector<PackageRecord> PackageManager::listPackages() const {
    std::vector<PackageRecord> records;
    records.reserve(packages_.size());
    for (const auto& [name, record] : packages_) {
        (void)name;
        records.push_back(record);
    }
    std::sort(records.begin(), records.end(), [](const auto& lhs, const auto& rhs) {
        return lhs.name < rhs.name;
    });
    return records;
}

PackageMutationResult PackageManager::deletePackage(const std::string& name) {
    if (packages_.erase(name) == 0) {
        return PackageMutationResult{false, "Package not found."};
    }
    return PackageMutationResult{true, "Package deleted."};
}

std::vector<PackageVersion> PackageManager::resolveDependencies(
    const std::vector<DependencyConstraint>& dependencies) const {
    std::vector<PackageVersion> resolved;
    std::unordered_set<std::string> visiting;
    std::unordered_set<std::string> resolvedKeys;
    for (const auto& dependency : dependencies) {
        resolveInto(dependency, visiting, resolvedKeys, resolved);
    }
    return resolved;
}

void PackageManager::resolveInto(const DependencyConstraint& dependency,
                                 std::unordered_set<std::string>& visiting,
                                 std::unordered_set<std::string>& resolvedKeys,
                                 std::vector<PackageVersion>& resolved) const {
    auto version = resolveOne(dependency, visiting);
    if (!version.has_value()) {
        return;
    }
    const std::string key = version->name + "@" + version->version;
    if (resolvedKeys.contains(key)) {
        return;
    }
    resolvedKeys.insert(key);
    resolved.push_back(*version);
    if (visiting.contains(version->name)) {
        return;
    }
    visiting.insert(version->name);
    for (const auto& child : version->dependencies) {
        if (!child.optional) {
            resolveInto(child, visiting, resolvedKeys, resolved);
        }
    }
    visiting.erase(version->name);
}

std::optional<PackageVersion> PackageManager::resolveOne(
    const DependencyConstraint& dependency,
    std::unordered_set<std::string>& visiting) const {
    const auto package = packages_.find(dependency.name);
    if (package == packages_.end()) {
        return std::nullopt;
    }

    std::vector<PackageVersion> candidates;
    for (const auto& [versionNumber, version] : package->second.versions) {
        (void)versionNumber;
        if (!version.yanked && versionSatisfies(version.version, dependency.constraint)) {
            candidates.push_back(version);
        }
    }
    if (candidates.empty()) {
        return std::nullopt;
    }

    std::sort(candidates.begin(), candidates.end(), [](const auto& lhs, const auto& rhs) {
        return compareVersion(lhs.version, rhs.version) > 0;
    });
    PackageVersion selected = candidates.front();
    if (visiting.contains(selected.name)) {
        return selected;
    }

    return selected;
}

bool isValidPackageName(const std::string& name) {
    if (name.empty() || name.size() > maxPackageNameLength || name.front() == '.' ||
        name.back() == '.') {
        return false;
    }
    char previous = '\0';
    for (char ch : name) {
        const auto c = static_cast<unsigned char>(ch);
        if (!std::isalnum(c) && ch != '-' && ch != '_' && ch != '.') {
            return false;
        }
        if (ch == '.' && previous == '.') {
            return false;
        }
        previous = ch;
    }
    return true;
}

bool isValidVersion(const std::string& version) {
    return parseVersion(version).has_value();
}

bool isValidConstraint(const std::string& constraint) {
    const std::string cleaned = trim(constraint);
    if (cleaned == "*" || isValidVersion(cleaned)) {
        return true;
    }
    static const std::regex comparator(R"(^(>=|<=|>|<|=|\^|~)\s*\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$)");
    return std::regex_match(cleaned, comparator);
}

bool isValidArchiveSha256(const std::string& checksum) {
    if (checksum.size() != 64) {
        return false;
    }
    return std::all_of(checksum.begin(), checksum.end(), [](unsigned char ch) {
        return std::isxdigit(ch) != 0;
    });
}

bool isValidManifestJson(const std::string& manifestJson) {
    const std::string cleaned = trim(manifestJson);
    if (cleaned.empty() || cleaned.size() > maxManifestBytes) {
        return false;
    }
    return (cleaned.front() == '{' && cleaned.back() == '}') ||
           (cleaned.front() == '[' && cleaned.back() == ']');
}

bool versionSatisfies(const std::string& version, const std::string& constraint) {
    const std::string cleaned = trim(constraint);
    if (cleaned.empty() || cleaned == "*") {
        return true;
    }
    if (isValidVersion(cleaned)) {
        return compareVersion(version, cleaned) == 0;
    }

    std::string op;
    std::string target;
    for (const std::string candidate : {">=", "<=", ">", "<", "=", "^", "~"}) {
        if (cleaned.rfind(candidate, 0) == 0) {
            op = candidate;
            target = trim(cleaned.substr(candidate.size()));
            break;
        }
    }
    if (op.empty() || !isValidVersion(version) || !isValidVersion(target)) {
        return false;
    }

    const int comparison = compareVersion(version, target);
    if (op == ">=") {
        return comparison >= 0;
    }
    if (op == "<=") {
        return comparison <= 0;
    }
    if (op == ">") {
        return comparison > 0;
    }
    if (op == "<") {
        return comparison < 0;
    }
    if (op == "=") {
        return comparison == 0;
    }
    const auto current = parseVersion(version);
    const auto base = parseVersion(target);
    if (!current.has_value() || !base.has_value() || comparison < 0) {
        return false;
    }
    if (op == "^") {
        return current->major == base->major;
    }
    return current->major == base->major && current->minor == base->minor;
}

} // namespace plang::pkg
