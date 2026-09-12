# API Documentation

Base URL: `http://localhost:8080`

## Health

`GET /health`

Returns:

```json
{
  "status": "ok",
  "service": "backend-api"
}
```

## Compile Jobs

### List Compile Jobs

`GET /api/compile-jobs?limit=50&offset=0`

Returns the most recent compile jobs. `limit` is clamped to `1..100`; `offset` is clamped to `0+`.

### Create Compile Job

`POST /api/compile-jobs`

Request:

```json
{
  "source": "const answer: number = identity!(40 + 2);",
  "options": {
    "optimize": true,
    "sourceMaps": true,
    "target": "es2022"
  }
}
```

Returns `201 Created` with a persisted compile job. The compiler parses the source, expands built-in macros, runs deterministic transform passes, emits JavaScript, stores source-map and diagnostic artifacts, and reuses cached output when the source/options/compiler-version cache key matches a prior successful job.

### Get Compile Job

`GET /api/compile-jobs/{id}`

Returns `404` when the job does not exist.

### Update Compile Job

`PUT /api/compile-jobs/{id}`

Request:

```json
{
  "source": "let answer: number = 42;",
  "options": {
    "optimize": true,
    "sourceMaps": true,
    "target": "es2022"
  }
}
```

The API recompiles and replaces generated artifacts.

### Delete Compile Job

`DELETE /api/compile-jobs/{id}`

Returns `204 No Content`. Related artifacts are deleted by the database foreign key.

## Compile Artifacts

### List Artifacts

`GET /api/compile-jobs/{id}/artifacts`

Returns generated `javascript`, `sourceMap`, and `diagnostics` artifacts for the job.

### Get Artifact

`GET /api/compile-jobs/{id}/artifacts/{artifactId}`

Returns one artifact or `404`.

### Delete Artifact

`DELETE /api/compile-jobs/{id}/artifacts/{artifactId}`

Returns `204 No Content`. Recompiling the compile job recreates its generated artifacts.

## Validation

The API rejects empty source, source larger than `MAX_SOURCE_BYTES`, and unsupported targets. Supported targets are `es2020`, `es2021`, `es2022`, and `esnext`.
