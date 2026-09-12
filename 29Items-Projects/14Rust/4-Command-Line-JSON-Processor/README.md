# Command-Line JSON Processor

A high-performance, asynchronous streaming JSON processor written in Rust. It provides fast data filtering and schema inference capabilities for massive newline-delimited JSON (NDJSON) files without consuming excessive memory.

## Detailed Features & Capabilities

**Data Ingestion & Output:**
1. **Stream from File:** Read a `.jsonl` file from the local filesystem chunk-by-chunk.
2. **Stream from Pipeline (`stdin`):** Receive streaming JSON data piped from another command (e.g., `cat data.jsonl | json-processor`).
3. **Compact Output Mode:** Output processed JSON as a dense, single-line string.
4. **Pretty Output Mode:** Output processed JSON formatted with spaces, newlines, and indentation (`--pretty`).

**Data Selection (Extraction):**
5. **Full Object Pass-Through:** Pass the entire JSON object through unchanged using the identity filter (`.`).
6. **Extract String Attribute:** Select and extract a single string attribute from the object (e.g., `--filter ".name"`).
7. **Extract Integer Attribute:** Select and extract a numeric attribute (e.g., `--filter ".age"`).
8. **Extract Nested Object as Root:** Extract an entire nested object to become the new root of the output (e.g., `--filter ".address"`).
9. **Extract Array:** Extract an array attribute (e.g., `--filter ".roles"`).

**Data Filtering (Conditional Matching):**
10. **Filter by String Match:** Retain only objects where a specific attribute matches a string exactly (e.g., `--filter ".status == \"active\""`).
11. **Filter by Integer Match:** Retain only objects where an attribute matches an integer exactly (e.g., `--filter ".age == 35"`).
12. **Filter by Boolean Match:** Retain only objects where an attribute is true/false (e.g., `--filter ".verified == true"`).
13. **Filter by Null Match:** Retain objects where a specific field is explicitly null (e.g., `--filter ".metadata == null"`).

**Schema Inference Engine (`--schema`):**
14. **Root Type Detection:** Determine if the base records are Objects, Arrays, etc.
15. **Nested Object Traversal:** Automatically crawl down into nested objects and map their internal fields.
16. **Array Traversal:** Look inside arrays to determine the data types of the elements contained within.
17. **Dynamic Type Resolution:** Identify standard JSON types correctly (`String`, `Integer`, `Float`, `Boolean`, `Null`).
18. **Mixed-Type Detection:** Identify and record if a single field has multiple conflicting types across different records (e.g., flagging that `age` was seen as both an `Integer` and `Null`).
19. **Deterministic Report Generation:** Aggregate all findings into a single, alphabetically sorted JSON schema summary.

**Resilience & Error Handling:**
20. **Invalid Line Skipping/Handling:** Safely catch `serde_json` parsing errors on malformed lines without crashing the entire stream.
21. **Missing Key Handling:** Gracefully handles missing keys by dropping the record without panicking.

## Prerequisites
- [Rust Toolchain (Cargo)](https://rustup.rs/)

## Setup & Installation

1. Clone the repository:
   ```bash
   git clone <repo-url>
   cd 4-Command-Line-JSON-Processor
   ```
2. Build the optimized release binary:
   ```bash
   cargo build --release
   ```
3. The executable will be available at `target/release/json-processor`.

*(Alternatively, you can just use `cargo run --release -- [args...]` to build and run simultaneously).*

## Usage

### 1. Filtering JSON
Extract or filter JSON records from a file. If the file is not provided, it reads from `stdin`.

```bash
# Filter records where the status is "active"
json-processor --input sample.jsonl --filter ".status == \"active\""

# Pretty-print the filtered output
json-processor --input sample.jsonl --filter ".status == \"active\"" --pretty
```

### 2. Schema Inference
Analyze a stream of JSON and output the structure of the data.

```bash
# Infer the schema of a file
json-processor --input sample.jsonl --schema --pretty

# Or pipe data in via stdin
cat sample.jsonl | json-processor --schema --pretty
```

## Testing
To run the integration and unit tests:
```bash
cargo test
```

## Troubleshooting
- **Input File Not Found**: Ensure you provide the absolute or relative path to the `.jsonl` file correctly.
- **Filter Syntax Errors**: The basic filtering engine only supports exact equality matching on top-level keys (`.key == "value"`) or extraction (`.key`). Ensure you wrap strings in escaped quotes (`\"`).
