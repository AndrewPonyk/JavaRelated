---
name: Bug report
about: Report incorrect behavior in the engine
title: "[bug] "
labels: bug
---

## Summary
A clear, one-sentence description of the bug.

## Reproduction
The smallest SQL script or test that triggers it:

```sql
CREATE TABLE t (id INT, name VARCHAR(16));
INSERT INTO t VALUES (1, 'Ada');
SELECT * FROM t WHERE id = 1;  -- expected ..., got ...
```

## Expected vs. actual
- **Expected:**
- **Actual:**

## Environment
- OS / compiler (e.g. Windows 11 / MSVC 19.4x, Ubuntu 24.04 / GCC 14):
- Build type (Debug/Release):
- Commit SHA:

## Additional context
Stack traces, sanitizer output, or the failing `ctest` case, if any.
