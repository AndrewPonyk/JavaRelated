# Migration Guide

## Token Changes

1. Run `npm run tokens:sync` when importing a Figma/Tokens Studio export.
2. Run `npm run tokens:build` to regenerate Sass and Tailwind artifacts.
3. Review `docs/RELEASE-NOTES.md` from `npm run release:notes`.
4. Use `GET /api/token-sets/:id/diff/:compareId` before adopting a new token set.

## Component Changes

Components are distributed as framework-neutral Web Components. Breaking changes must include:

- Removed or renamed attributes.
- Changed keyboard behavior.
- Changed ARIA roles or labels.
- Changed CSS custom property names.

## Release Verification

Before publishing, run:

```bash
npm run format
npm run lint
npm test
npm run build
npm run build:storybook
```
