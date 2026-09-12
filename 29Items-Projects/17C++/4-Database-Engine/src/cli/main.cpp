// MiniDB interactive shell — the user-facing "frontend" for the engine.
//
// Reads one SQL statement per line, executes it through the Database façade,
// and renders the result. Demonstrates the data-fetch + display + error/loading
// pattern asked for in the project spec (Deliverable 4.1), adapted to a CLI.
//
// Usage:
//   minidb [path-to-db-file]          # interactive REPL (default minidb.db)
//   minidb mydb.db < script.sql       # batch mode via stdin redirection

#include <algorithm>
#include <iostream>
#include <string>
#include <vector>

#include "minidb/engine/database.hpp"

namespace {

std::string Trim(const std::string& s) {
  const auto begin = s.find_first_not_of(" \t\r\n");
  if (begin == std::string::npos) return "";
  const auto end = s.find_last_not_of(" \t\r\n");
  return s.substr(begin, end - begin + 1);
}

void PrintResultSet(const minidb::ResultSet& rs) {
  // DDL/DML: just the summary line (a no-op statement has an empty message).
  if (!rs.is_query()) {
    if (!rs.message.empty()) std::cout << rs.message << "\n";
    return;
  }

  const std::size_t ncols = rs.columns.size();
  std::vector<std::size_t> widths(ncols);
  for (std::size_t c = 0; c < ncols; ++c) widths[c] = rs.columns[c].size();
  for (const auto& row : rs.rows) {
    for (std::size_t c = 0; c < ncols && c < row.size(); ++c) {
      widths[c] = std::max(widths[c], row[c].ToString().size());
    }
  }

  auto print_row = [&](const std::vector<std::string>& cells) {
    std::cout << "| ";
    for (std::size_t c = 0; c < ncols; ++c) {
      std::string cell = c < cells.size() ? cells[c] : "";
      cell.resize(widths[c], ' ');
      std::cout << cell << " | ";
    }
    std::cout << "\n";
  };

  auto rule = [&]() {
    std::cout << "+";
    for (std::size_t c = 0; c < ncols; ++c) {
      std::cout << std::string(widths[c] + 2, '-') << "+";
    }
    std::cout << "\n";
  };

  rule();
  print_row(rs.columns);
  rule();
  for (const auto& row : rs.rows) {
    std::vector<std::string> cells;
    cells.reserve(row.size());
    for (const auto& v : row) cells.push_back(v.ToString());
    print_row(cells);
  }
  rule();
  std::cout << rs.rows.size() << " row(s)  [" << rs.message << "]\n";
}

void PrintHelp() {
  std::cout << "Commands:\n"
               "  .help            show this help\n"
               "  .tables          list tables\n"
               "  .exit / .quit    leave the shell\n"
               "SQL (subset, statements end with ';' and may span lines):\n"
               "  CREATE TABLE t (id INT, name VARCHAR(32));\n"
               "  INSERT INTO t VALUES (1, 'Ada');\n"
               "  SELECT id, name FROM t WHERE id = 1;\n"
               "  EXPLAIN SELECT name FROM t WHERE id = 1;\n";
}

/// Index just past the ';' that ends the first complete statement in @p s, or
/// npos if there is no terminator yet. Ignores ';' inside single-quoted strings
/// (where '' is an escaped quote) and inside `--` line comments, so multi-line
/// statements and string literals containing ';' are handled correctly.
std::size_t StatementEnd(const std::string& s) {
  bool in_string = false;
  for (std::size_t i = 0; i < s.size(); ++i) {
    const char c = s[i];
    if (in_string) {
      if (c == '\'') {
        if (i + 1 < s.size() && s[i + 1] == '\'') {
          ++i;  // escaped quote
          continue;
        }
        in_string = false;
      }
      continue;
    }
    if (c == '\'') {
      in_string = true;
    } else if (c == '-' && i + 1 < s.size() && s[i + 1] == '-') {
      const std::size_t nl = s.find('\n', i);
      if (nl == std::string::npos) return std::string::npos;
      i = nl;
    } else if (c == ';') {
      return i + 1;
    }
  }
  return std::string::npos;
}

void RunStatement(minidb::Database& db, const std::string& sql) {
  auto result = db.Execute(sql);
  if (!result.ok()) {
    std::cout << "Error: " << result.status().ToString() << "\n";
    return;
  }
  PrintResultSet(result.value());
}

}  // namespace

int main(int argc, char** argv) {
  const std::string path = argc >= 2 ? argv[1] : "minidb.db";

  auto opened = minidb::Database::Open(path);
  if (!opened.ok()) {
    std::cerr << "Failed to open '" << path
              << "': " << opened.status().ToString() << "\n";
    return 1;
  }
  minidb::Database& db = opened.value();

  std::cout << "MiniDB shell (file: " << path << "). Type .help for help.\n";

  std::string buffer;
  std::string line;
  while (std::getline(std::cin, line)) {
    // Meta commands (".exit", ".tables", ...) are recognized only at a clean
    // statement boundary, i.e. when nothing is buffered.
    if (Trim(buffer).empty()) {
      const std::string meta = Trim(line);
      if (meta.empty()) continue;
      if (meta[0] == '.') {
        buffer.clear();
        if (meta == ".exit" || meta == ".quit") break;
        if (meta == ".help") {
          PrintHelp();
        } else if (meta == ".tables") {
          for (const auto& name : db.catalog().TableNames()) {
            std::cout << "  " << name << "\n";
          }
        } else {
          std::cout << "Unknown command: " << meta << " (try .help)\n";
        }
        continue;
      }
    }

    // Accumulate input and execute each ';'-terminated statement.
    buffer += line;
    buffer += '\n';
    for (std::size_t end; (end = StatementEnd(buffer)) != std::string::npos;) {
      RunStatement(db, buffer.substr(0, end));
      buffer.erase(0, end);
    }
  }
  // Flush a trailing statement that was not terminated by ';'.
  if (!Trim(buffer).empty()) RunStatement(db, buffer);

  if (auto s = db.Flush(); !s.ok()) {
    std::cerr << "Warning: flush failed: " << s.ToString() << "\n";
  }
  return 0;
}
