## What & why

<!-- One paragraph: what changes and the reason. Link the issue. -->

## Checklist

- [ ] Tests cover the change (tolerance-based assertions for numerics — docs/TECH-NOTES.md §3.2)
- [ ] No `sympy.sympify` / `eval` on user input (parser boundary respected)
- [ ] Migrations are backward-compatible one release back (expand → migrate → contract)
- [ ] Docs updated if behavior/contracts changed (`docs/`, `.env.example`)
- [ ] Notebooks (if touched) run top-to-bottom and are output-stripped

## How to verify

<!-- Commands or steps a reviewer can run. -->
