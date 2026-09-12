#include "static_analyzer/AstExtractor.h"

#include <cassert>
#include <string>

int main() {
  static_analyzer::AstExtractor extractor;
  const std::string result = extractor.ExtractJson({"sample.cpp", "compile_commands.json"});

  assert(result.find("\"sourcePath\":\"sample.cpp\"") != std::string::npos);
  assert(result.find("\"schemaVersion\":\"1.0.0\"") != std::string::npos);
  assert(result.find("\"calls\"") != std::string::npos);
  return 0;
}
