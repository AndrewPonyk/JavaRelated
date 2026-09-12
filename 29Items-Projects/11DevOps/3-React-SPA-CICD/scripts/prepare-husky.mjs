#!/usr/bin/env node
/**
 * Installs husky hooks ONLY when this project is its own git repository root.
 *
 * Why the guard: `husky` sets `core.hooksPath` on whatever repository contains the
 * current directory. If this project is vendored inside a larger repo (or built in a
 * context without .git, e.g. Docker/CI), running husky would either fail the install
 * or — worse — hijack the parent repository's hooks.
 */
import { existsSync } from 'node:fs';
import { spawnSync } from 'node:child_process';

if (process.env.HUSKY === '0') {
  process.exit(0); // explicit opt-out (CI)
}

if (!existsSync('.git')) {
  console.log('[prepare] .git not found at project root — skipping husky hook install.');
  process.exit(0);
}

const result = spawnSync('npx', ['--no-install', 'husky'], {
  stdio: 'inherit',
  shell: process.platform === 'win32',
});
process.exit(result.status ?? 0);
