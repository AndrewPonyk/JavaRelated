#include "static_analyzer/AstExtractor.h"

#include <iostream>
#include <string>

int main(int argc, const char** argv) {
  if (argc < 2 || std::string(argv[1]) == "--help") {
    std::cout << "Usage: static-analyzer <source-path> [compile-commands-path]\n";
    return 0;
  }

  static_analyzer::ExtractorOptions options{
      .source_path = argv[1],
      .compile_commands_path = argc > 2 ? argv[2] : "compile_commands.json",
  };

  static_analyzer::AstExtractor extractor;
  std::cout << extractor.ExtractJson(options) << "\n";
  return 0;
}
