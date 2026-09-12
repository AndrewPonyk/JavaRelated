#pragma once

#include <filesystem>
#include <string>
#include <variant>

namespace plang::frontend {

struct SourceLoading {
    std::string message{"Loading source"};
};

struct SourceReady {
    std::filesystem::path path;
    std::string contents;
};

struct SourceError {
    std::string message;
};

using SourcePreviewState = std::variant<SourceLoading, SourceReady, SourceError>;

class SourcePreview {
public:
    [[nodiscard]] SourcePreviewState fetchFromFile(const std::filesystem::path& path) const;
    [[nodiscard]] std::string render(const SourcePreviewState& state) const;
};

} // namespace plang::frontend
