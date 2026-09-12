#pragma once

#include "plang/pkg/PackageManager.h"

#include <string>
#include <unordered_map>

namespace plang::api {

struct ApiRequest {
    std::string method;
    std::string path;
    std::string body;
    std::unordered_map<std::string, std::string> headers;
};

struct ApiResponse {
    int status{500};
    std::string body;
};

class PackageApi {
public:
    explicit PackageApi(pkg::PackageManager& packageManager);

    [[nodiscard]] ApiResponse handle(const ApiRequest& request);

private:
    [[nodiscard]] ApiResponse createPackage(const ApiRequest& request);
    [[nodiscard]] ApiResponse updatePackage(std::string name, const ApiRequest& request);
    [[nodiscard]] ApiResponse listPackages(const ApiRequest& request) const;
    [[nodiscard]] ApiResponse getPackage(std::string name) const;
    [[nodiscard]] ApiResponse deletePackage(std::string name);
    [[nodiscard]] ApiResponse publishVersion(std::string name, const ApiRequest& request);
    [[nodiscard]] ApiResponse getVersion(std::string name, std::string version) const;
    [[nodiscard]] ApiResponse deleteVersion(std::string name, std::string version);
    [[nodiscard]] ApiResponse yankVersion(std::string name, std::string version);
    [[nodiscard]] ApiResponse resolveDependencies(const ApiRequest& request) const;

    pkg::PackageManager& packageManager_;
};

} // namespace plang::api
