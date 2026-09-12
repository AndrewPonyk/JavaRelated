#include "static_analyzer/AstExtractor.h"

#include <fstream>
#include <regex>
#include <sstream>
#include <string>

namespace {

std::string EscapeJson(const std::string& value) {
  std::ostringstream escaped;
  for (const char character : value) {
    switch (character) {
      case '"':
        escaped << "\\\"";
        break;
      case '\\':
        escaped << "\\\\";
        break;
      case '\n':
        escaped << "\\n";
        break;
      case '\r':
        escaped << "\\r";
        break;
      case '\t':
        escaped << "\\t";
        break;
      default:
        escaped << character;
    }
  }
  return escaped.str();
}

void AppendCommaIfNeeded(std::ostringstream& json, bool& first) {
  if (!first) {
    json << ",";
  }
  first = false;
}

}  // namespace

namespace static_analyzer {

std::string AstExtractor::ExtractJson(const ExtractorOptions& options) const {
  std::ifstream input(options.source_path);
  const std::regex function_pattern(
      R"(^\s*(?:static\s+|inline\s+|extern\s+)?[\w:<>~*&\s]+\s+([A-Za-z_]\w*)\s*\([^;]*\)\s*\{?)");
  const std::regex call_pattern(R"(\b([A-Za-z_]\w*)\s*\()");
  const std::regex variable_pattern(
      R"(\b(?:int|char|long|short|bool|float|double|auto|std::string)\s+([A-Za-z_]\w*)\b(?!\s*\())");

  std::ostringstream functions;
  std::ostringstream calls;
  std::ostringstream variables;
  std::ostringstream control_flow;
  bool first_function = true;
  bool first_call = true;
  bool first_variable = true;
  bool first_control = true;

  std::string line;
  int line_number = 0;
  while (std::getline(input, line)) {
    ++line_number;
    std::smatch match;
    if (std::regex_search(line, match, function_pattern)) {
      const std::string name = match[1].str();
      if (name != "if" && name != "for" && name != "while" && name != "switch") {
        AppendCommaIfNeeded(functions, first_function);
        functions << "{\"name\":\"" << EscapeJson(name) << "\",\"line\":" << line_number
                  << ",\"column\":1}";
      }
    }

    auto call_begin = std::sregex_iterator(line.begin(), line.end(), call_pattern);
    auto call_end = std::sregex_iterator();
    for (auto it = call_begin; it != call_end; ++it) {
      const std::string name = (*it)[1].str();
      if (name == "if" || name == "for" || name == "while" || name == "switch" ||
          name == "return" || name == "sizeof") {
        continue;
      }
      AppendCommaIfNeeded(calls, first_call);
      calls << "{\"name\":\"" << EscapeJson(name) << "\",\"line\":" << line_number
            << ",\"column\":" << (it->position(1) + 1) << "}";
    }

    auto var_begin = std::sregex_iterator(line.begin(), line.end(), variable_pattern);
    auto var_end = std::sregex_iterator();
    for (auto it = var_begin; it != var_end; ++it) {
      AppendCommaIfNeeded(variables, first_variable);
      variables << "{\"name\":\"" << EscapeJson((*it)[1].str()) << "\",\"line\":"
                << line_number << ",\"column\":" << (it->position(1) + 1) << "}";
    }

    if (std::regex_search(line, std::regex(R"(\b(if|for|while|switch|return|break|continue)\b)"))) {
      AppendCommaIfNeeded(control_flow, first_control);
      control_flow << "{\"text\":\"" << EscapeJson(line) << "\",\"line\":" << line_number
                   << ",\"column\":1}";
    }
  }

  std::ostringstream json;
  json << "{";
  json << "\"schemaVersion\":\"1.0.0\",";
  json << "\"sourcePath\":\"" << EscapeJson(options.source_path) << "\",";
  json << "\"compileCommandsPath\":\"" << EscapeJson(options.compile_commands_path) << "\",";
  json << "\"functions\":[" << functions.str() << "],";
  json << "\"calls\":[" << calls.str() << "],";
  json << "\"variables\":[" << variables.str() << "],";
  json << "\"controlFlow\":[" << control_flow.str() << "]";
  json << "}";
  return json.str();
}

}  // namespace static_analyzer
