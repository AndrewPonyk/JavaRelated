# Shared contracts

This directory contains language-neutral contracts exchanged between independently deployed components. It must not become a shared runtime-library dumping ground.

Rules:

- Event names include a major version in the topic name, for example `inventory.stock-changed.v1`.
- Producers may add optional fields in a compatible release; they must not remove or change required fields within a major version.
- Consumers ignore unknown fields, validate required fields, and deduplicate by `eventId`.
- CI validates examples and performs backward-compatibility checks against the default branch.
- Breaking changes use a new schema/topic major version and a measured dual-publish migration window.

