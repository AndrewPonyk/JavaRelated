// Micro-benchmark for the page-backed B+ Tree.
//
// Not wired into the CMake build by default; compile standalone, e.g.:
//   cl /std:c++20 /O2 /EHsc /I include benchmarks\bench_bplus_tree.cpp ^
//      src\common\*.cpp src\storage\*.cpp src\index\bplus_tree.cpp
//
// It builds a real tree over a buffer pool + temp file and reports throughput
// for insert and point-lookup. Phase 3 will replace this hand-rolled timing
// with Google Benchmark and add range-scan + mixed workloads with CI gates.

#include <chrono>
#include <cstdint>
#include <cstdio>
#include <filesystem>
#include <random>
#include <string>
#include <vector>

#include "minidb/index/bplus_tree.hpp"
#include "minidb/storage/buffer_pool_manager.hpp"
#include "minidb/storage/disk_manager.hpp"

namespace {
template <typename F>
double TimeMs(F&& fn) {
  const auto start = std::chrono::steady_clock::now();
  fn();
  const auto end = std::chrono::steady_clock::now();
  return std::chrono::duration<double, std::milli>(end - start).count();
}
}  // namespace

int main() {
  using namespace minidb;
  constexpr int kN = 200'000;

  const std::string path =
      (std::filesystem::temp_directory_path() / "minidb_bench.db").string();
  std::error_code ec;
  std::filesystem::remove(path, ec);

  DiskManager disk;
  if (!disk.Open(path).ok()) {
    std::fprintf(stderr, "failed to open %s\n", path.c_str());
    return 1;
  }
  BufferPoolManager bpm(4096, &disk);  // 16 MiB pool
  BPlusTree tree(&bpm);

  std::vector<std::int64_t> keys(kN);
  std::mt19937_64 rng(42);
  for (int i = 0; i < kN; ++i) keys[i] = static_cast<std::int64_t>(rng());

  const double insert_ms = TimeMs([&] {
    RID rid;
    for (int i = 0; i < kN; ++i) {
      rid.slot = static_cast<slot_id_t>(i & 0xFFFF);
      (void)tree.Insert(keys[i], rid);
    }
  });

  std::uint64_t found = 0;
  const double lookup_ms = TimeMs([&] {
    for (int i = 0; i < kN; ++i) found += tree.GetValue(keys[i]).has_value();
  });

  std::printf("insert: %.1f ms (%.2f M ops/s)\n", insert_ms,
              kN / insert_ms / 1000.0);
  std::printf("lookup: %.1f ms (%.2f M ops/s), found=%llu\n", lookup_ms,
              kN / lookup_ms / 1000.0, static_cast<unsigned long long>(found));

  std::filesystem::remove(path, ec);
  return 0;
}
