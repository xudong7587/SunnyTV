# SunnyTV development rules

## Read first

Read `docs/STATUS.md`, `docs/CODEX_HANDOFF.md`, `docs/DEVELOPMENT_PLAN.md`, `docs/DESIGN_SYSTEM.md`, and `docs/BUILD.md`. This is an existing development snapshot, not a blank project. The app is named SunnyTV.

## Non-negotiable constraints

- Keep Android native Kotlin / Compose TV / Media3. Do not replace native pages with a WebView.
- The user screenshot describes the top of a single library, not the whole app. Preserve global home, library index, library landing, grid, detail, cloud, search and settings.
- Prefer Emby library Primary artwork and existing posters / Backdrops / Logos. Do not replace them with generic icons or re-scrape via TMDB by default.
- Never edit the user's MediaIndex repository, signing/STRM contract, NAS files or provider credentials.
- No cloud file delete/move/rename operations. User-authorized local account removal and Emby favorite/progress writes are separate, explicit actions.
- Keep origin/path-scoped credentials, bounded redirects, strict TLS, a consistent playback UA and cancellable operations. No signed URL logging.
- Never commit tokens, passwords, cookies, keystores, SDKs or build caches. Do not copy private example media into releases.
- Avoid unrelated refactors and broad dependency upgrades. Explain shared model / network changes and test Emby, CD2 and STRM regressions.
- Audit every upstream file before copying. This snapshot contains no Moonfin code; do not claim its features are inherited.

## Verification discipline

The original handoff has 102 executed pure Kotlin tests and 12 browser preview tests. Neither proves Android compilation or TV behavior. Run current tests after changes; numbers can change. Report code-written, compiled, unit-tested, integrated and TV-validated separately.

Start with BUILD-01 in CODEX_HANDOFF. Do not disable tests/lint or remove broken features to create a misleading green build. Keep a concrete known-issues list.

## Module boundaries

Network policies live in core/network; metadata/URLs in source adapters; UI cannot build API URLs; player cannot request cloud cookies. Root rules apply everywhere; local AGENTS.md files add module-specific requirements.

## dev2 continuation

优先在当前工程增量开发；GitHub 初始化器只用于新仓库，不能用内嵌老源码覆盖现有 app。每次改核心时跑纯 Kotlin 契约测试（dev2 为114项）和相关 HTTP/Android 测试；缺少工具链明确记录。工作流打包不允许包含图片评审素材、签名密钥或真实服务凭据。任何自动提交只作用于用户明确指定的 SunnyTV，不修改 MediaIndex。已具备标准 Wrapper 后保留它及校验值。
