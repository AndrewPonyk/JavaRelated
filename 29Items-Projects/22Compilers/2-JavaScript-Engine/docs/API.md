# API Documentation

## Engine API

```cpp
jsengine::Engine engine;
jsengine::EvaluationResult result = engine.evaluate("let x = 1 + 2; x;");
```

`EvaluationResult`:

- `ok`: true when parsing, compilation, and execution succeeded.
- `value`: string representation of the returned runtime value.
- `error`: structured error message when `ok` is false.

## Debug Session API

`DebugSessionApi` is an in-process API facade that returns HTTP-like status codes and JSON-style bodies.

Operations:

- `createSession(DebugSessionRequest{target})`
- `listSessions(ListOptions{limit, offset})`
- `getSession(id)`
- `updateSession(id, DebugSessionUpdateRequest{target, paused})`
- `deleteSession(id)`

Statuses:

- `200`: successful read or update
- `201`: created
- `204`: deleted
- `400`: validation error
- `404`: session not found

`limit` defaults to 100 and must be between 1 and 500. `offset` defaults to 0.

## Metadata Store API

`MetadataStore` persists data to SQLite when opened with a database path.

Key/value operations:

- `put(key, value)`
- `get(key)`
- `remove(key)`

Module-cache operations:

- `createModuleCacheEntry(entry)`
- `listModuleCacheEntries(ListOptions{limit, offset})`
- `getModuleCacheEntry(id)`
- `deleteModuleCacheEntry(id)`

Runtime event operations:

- `recordRuntimeEvent(event)`
- `listRuntimeEvents(ListOptions{limit, offset})`

Storage list operations use parameterized `LIMIT` and `OFFSET` values. Invalid list options return an empty result.
