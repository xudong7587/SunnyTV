# Module rules

Retain HeaderScope, HttpPolicy and cancellation. Never forward Authorization, Cookie or Emby tokens to a CDN. Respect Range and signed URL encoding. Redirect tests must cover different hosts/ports, relative Location, loop limits, downgrade and dot-path attacks. Do not introduce trust-all TLS or global auth interceptors.

Read root AGENTS.md and docs/STATUS.md before changes.
