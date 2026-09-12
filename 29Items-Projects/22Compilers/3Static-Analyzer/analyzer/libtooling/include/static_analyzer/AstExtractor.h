#pragma once

#include <string>

namespace static_analyzer {

struct ExtractorOptions {
  std::string source_path;
  std::string compile_commands_path;
};

class AstExtractor {
 public:
  std::string ExtractJson(const ExtractorOptions& options) const;
};

}  // namespace static_analyzer
