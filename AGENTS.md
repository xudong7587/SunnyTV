# SunnyTV development rules

## Version numbering from 0.2.0

用户于 2026-10-15 明确要求：从本次 `0.2.0` 起使用纯数字版本号（主版本.次版本.修订），不再使用 dev 后缀。Android versionCode 继续递增；0.2.0 使用 52，后续继续递增，不按 versionName 重置。

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

## Signing policy from dev16 onward

The user explicitly removed the dev14 certificate restriction on 2026-09-18. Read docs/SIGNING.md. Use the existing repository signing Secret; never generate a replacement key as a fallback. Publishing requires a valid APK signature matching the public certificate of that existing key. No dev14 APK/certificate gate applies. Keep the current application ID and never replace an existing release tag or asset silently.

## Local APK distribution (2026-10-21 onward)

用户于 2026-10-21 明确调整：以后经过现有签名及发行检查的 SunnyTV APK 只复制到 `Z:\V3 Download\tv apk`，只放 APK，不写 JSON，不再执行 WebDAV 发布或同步旧目录 `Z:\V1 Tools\SunnyTV-updata`。本条取代此前本地 WebDAV 分发要求。
这是用户明确授权的 APK 目录写入例外；不操作其它 NAS 媒体，不删除历史包，不覆盖同版本不同内容。挂载缺失时如实记录未复制。
Git/GitHub 发布流程照旧；本次绿联内网 HTTPS 修复先留在本地，待用户测试反馈后再按用户指示提交、推送或发布。
此次仅调整 APK 投放方式，不要求修改应用内已有升级功能。
以公开 dev29（`543b25d`）为历史基线，dev28 Carlink 已由用户于 2026-09-30 放弃，源码归档在 `.local-tests/dev28-archive`，不进入构建。

## GitHub stable releases from 0.2.2

官网、README 和应用内 GitHub 网页下载默认指向 `/releases/latest`；原生更新读取官方 latest 下载元数据。纯数字正式版使用 `release-stable.yml` 并设置 latest，不发布为 prerelease，不覆盖已有标签或资产。同版本 APK 一经本地目录或 GitHub 分发即保持不变，客户端变更须递增数字版本及 versionCode。
