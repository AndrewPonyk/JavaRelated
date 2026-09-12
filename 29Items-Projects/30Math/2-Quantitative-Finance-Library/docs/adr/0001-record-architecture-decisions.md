# ADR 0001: Record architecture decisions

Date: 2026-07-10
Status: Accepted

## Context

Numerical libraries accumulate irreversible decisions (FP-precision policy, ABI strategy,
result-changing algorithm swaps) whose rationale evaporates within months.

## Decision

Keep Architecture Decision Records in `docs/adr/`, numbered, in this format
(Context / Decision / Consequences). A decision that changes numerical output of any
public function REQUIRES an ADR plus a CHANGELOG entry.

## Consequences

- Reviewers can point at "needs an ADR" instead of relitigating in PR comments.
- Next expected ADRs: 0002 in-process job queue vs external broker (revisit at GPU
  workers), 0003 FP-determinism policy across compilers (/fp:precise, -ffp-contract=off).
