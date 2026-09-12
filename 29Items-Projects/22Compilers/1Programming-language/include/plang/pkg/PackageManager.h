#pragma once

#include <optional>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

namespace plang::pkg {

struct DependencyConstraint {
    std::string name;
    std::string constraint;
    bool optional{false};
};

struct PackageVersion {
    std::string name;
    std::string version;
    std::string manifestJson{"{}"};
    std::string archiveSha256;
    bool yanked{false};
    std::vector<DependencyConstraint> dependencies;
};

struct PackageRecord {
    std::string name;
    std::unordered_map<std::string, PackageVersion> versions;
};

struct PackageMutationResult {
    bool ok{false};
    std::string message;
};

class PackageManager {
public:
    [[nodiscard]] PackageMutationResult createPackage(std::string name);
    [[nodiscard]] PackageMutationResult updatePackage(std::string oldName, std::string newName);
    [[nodiscard]] PackageMutationResult publishVersion(PackageVersion version);
    [[nodiscard]] PackageMutationResult yankVersion(const std::string& name,
                                                    const std::string& version);
    [[nodiscard]] PackageMutationResult deleteVersion(const std::string& name,
                                                      const std::string& version);
    [[nodiscard]] std::optional<PackageRecord> findPackage(const std::string& name) const;
    [[nodiscard]] std::optional<PackageVersion> findVersion(const std::string& name,
                                                            const std::string& version) const;
    [[nodiscard]] std::vector<PackageRecord> listPackages() const;
    [[nodiscard]] PackageMutationResult deletePackage(const std::string& name);
    [[nodiscard]] std::vector<PackageVersion> resolveDependencies(
        const std::vector<DependencyConstraint>& dependencies) const;

private:
    [[nodiscard]] std::optional<PackageVersion> resolveOne(
        const DependencyConstraint& dependency,
        std::unordered_set<std::string>& visiting) const;
    void resolveInto(const DependencyConstraint& dependency,
                     std::unordered_set<std::string>& visiting,
                     std::unordered_set<std::string>& resolvedKeys,
                     std::vector<PackageVersion>& resolved) const;

    std::unordered_map<std::string, PackageRecord> packages_;
};

bool isValidPackageName(const std::string& name);
bool isValidVersion(const std::string& version);
bool isValidConstraint(const std::string& constraint);
bool isValidArchiveSha256(const std::string& checksum);
bool isValidManifestJson(const std::string& manifestJson);
bool versionSatisfies(const std::string& version, const std::string& constraint);

} // namespace plang::pkg
