# What & why

<!-- One or two sentences: what changes, and what problem it solves. Link the ticket. -->

Closes #

## How to review

<!-- Where to start reading; anything unusual a reviewer should know. -->

## Checklist

- [ ] Unit tests cover the new behavior (including error branches)
- [ ] E2E updated if a user journey changed
- [ ] No new `VITE_*` variable carries a secret (they are public — TECH-NOTES §3.4)
- [ ] `contracts/openapi.yaml` + `mocks/` updated if the API contract changed
- [ ] Analytics events added to `src/lib/analytics/events.ts` (typed map), not ad-hoc
- [ ] Checked the PR preview URL (posted below by the bot) on desktop + mobile width

## Screenshots / preview notes

<!-- Optional: before/after or notable states. The preview bot comments the live URL. -->
