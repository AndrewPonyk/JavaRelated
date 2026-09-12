# Technical Notes

## 3.1 CI/CD Pipeline Design

The CI pipeline stays small and fast:

1. Checkout repository.
2. Set up Java 11.
3. Run `mvn verify` with JaCoCo coverage checks.
4. Run `mvn exec:java`.
5. Set up Python 3.12.
6. Install Python dev dependencies.
7. Run `coverage run -m pytest` and `coverage report` with the configured 80% coverage gate.
8. Run `python -m string_algorithms`.

There is no deploy stage because this project is a local console application.

## 3.2 Testing Strategy

Java testing:

- Framework: JUnit 5.
- Coverage gate: 80%+ line coverage enforced by JaCoCo.
- Covered categories:
  - Basic matches.
  - Missing pattern.
  - Overlapping matches.
  - Empty text and invalid pattern handling.
  - Repeated characters.
  - Unicode strings.
  - Console runner integration.

Python testing:

- Framework: pytest.
- Coverage gate: 80%+ line coverage enforced by Coverage.py.
- Tests mirror the Java categories where practical.

Integration testing:

- Run Java and Python console entry points and assert they print expected output.
- Keep sample output stable enough for smoke checks.

End-to-end testing:

- Full browser-style E2E tests are not applicable.
- CLI-level tests exercise representative input flows.

## 3.3 Deployment Strategy

No cloud or infrastructure deployment is required.

Local execution strategy:

- Java: run with Maven or package as a jar.
- Python: run the package module with `PYTHONPATH=python`.
- Scripts in `tools/` and `scripts/` provide Windows PowerShell shortcuts.

Containerization is intentionally out of scope because this is an algorithms-only console learning workspace.

## 3.4 Environment Management

Use environment variables only for local demo behavior, not secrets.

```dotenv
STRING_ALGORITHMS_SAMPLE_TEXT=abracadabra pattern matching abracadabra
STRING_ALGORITHMS_PATTERN=abra
STRING_ALGORITHMS_PATTERNS=abra,cad,match
STRING_ALGORITHMS_PALINDROME_TEXT=forgeeksskeegfor
STRING_ALGORITHMS_COMPARISON_TEXT=cadabra matching
STRING_ALGORITHMS_BENCHMARK=false
```

Guidelines:

- Keep default examples in source code.
- Use environment variables for local overrides.
- Do not commit real secrets.
- Document all variables in `.env.example`.

## 3.5 Version Control Workflow

Use GitHub Flow:

- Keep `main` releasable.
- Create short-lived feature branches.
- Open pull requests for review.
- Run CI before merging.

Gitflow is heavier than needed because there are no production release trains or environment-specific deploy branches.

## 3.6 Common Pitfalls

- Mixing console formatting into algorithm classes makes testing harder.
- Failing to handle overlapping matches causes incorrect KMP and Rabin-Karp results.
- Rabin-Karp hash collisions must be verified with substring equality.
- Aho-Corasick failure links are easy to implement incorrectly for shared prefixes.
- Suffix tree implementations are complex; this project uses compact suffix insertion rather than Ukkonen's linear-time construction.
- Unicode handling can differ by language because Java `char` and Python `str` have different internal models.
- Benchmarks can mislead if they include console printing or one-time setup costs.
