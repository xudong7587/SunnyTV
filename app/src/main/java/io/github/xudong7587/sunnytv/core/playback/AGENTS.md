# Module rules

SessionEvents must preserve start/stop and coalesce only redundant progress. Never report Playing during metadata prefetch. Keep reporter I/O off the UI thread; close bounded work on lifecycle exit. Add tests before changing ordering or retry behavior.

Read root AGENTS.md and docs/STATUS.md before changes.
