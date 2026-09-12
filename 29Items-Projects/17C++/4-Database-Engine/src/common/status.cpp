#include "minidb/common/status.hpp"

namespace minidb {

const char* StatusCodeToString(StatusCode code) {
  switch (code) {
    case StatusCode::kOk:
      return "OK";
    case StatusCode::kInvalidArgument:
      return "INVALID_ARGUMENT";
    case StatusCode::kNotFound:
      return "NOT_FOUND";
    case StatusCode::kAlreadyExists:
      return "ALREADY_EXISTS";
    case StatusCode::kSyntaxError:
      return "SYNTAX_ERROR";
    case StatusCode::kIOError:
      return "IO_ERROR";
    case StatusCode::kOutOfMemory:
      return "OUT_OF_MEMORY";
    case StatusCode::kBufferPoolFull:
      return "BUFFER_POOL_FULL";
    case StatusCode::kCorruption:
      return "CORRUPTION";
    case StatusCode::kUnsupported:
      return "UNSUPPORTED";
    case StatusCode::kInternal:
      return "INTERNAL";
  }
  return "UNKNOWN";
}

std::string Status::ToString() const {
  if (ok()) return "OK";
  std::string out = StatusCodeToString(code_);
  if (!message_.empty()) {
    out += ": ";
    out += message_;
  }
  return out;
}

}  // namespace minidb
