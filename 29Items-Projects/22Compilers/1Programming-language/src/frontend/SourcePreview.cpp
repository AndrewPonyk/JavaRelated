#include "plang/frontend/SourcePreview.h"

#include <fstream>
#include <sstream>

namespace plang::frontend {

SourcePreviewState SourcePreview::fetchFromFile(const std::filesystem::path& path) const {
    std::ifstream input(path);
    if (!input) {
        return SourceError{"Unable to open source file: " + path.string()};
    }

    std::ostringstream buffer;
    buffer << input.rdbuf();
    return SourceReady{path, buffer.str()};
}

std::string SourcePreview::render(const SourcePreviewState& state) const {
    if (std::holds_alternative<SourceLoading>(state)) {
        return std::get<SourceLoading>(state).message;
    }
    if (std::holds_alternative<SourceError>(state)) {
        return "error: " + std::get<SourceError>(state).message;
    }

    const auto& ready = std::get<SourceReady>(state);
    std::ostringstream output;
    output << "Loaded " << ready.path.string() << " (" << ready.contents.size() << " bytes)";
    return output.str();
}

} // namespace plang::frontend
