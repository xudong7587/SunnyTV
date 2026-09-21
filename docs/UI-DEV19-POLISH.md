# SunnyTV dev19 UI / performance polish

Baseline: user-provided complete `0.1.0-dev18` source tree. No earlier patch is re-applied.

## Changes

- Library screen pinned header/tool controls are one compact transparent row. Long library names ellipsize; compact/mobile layouts reserve width for the right-side tools.
- Home hero and the first library shelf are separate lazy items. Crossing the boundary preloads the first library artwork, coalesces focus/scroll work, temporarily suppresses automatic bring-into-view correction, and uses the global motion speed.
- Display preference now offers Auto / 1080p / Native highest. It requests a window display mode only; TV firmware may keep the Android UI surface at 1080p. Diagnostics separately show app-window pixels, active display mode, and the device's highest advertised mode.
- Player controls were redrawn as light-weight vector/glass controls: stronger transport hierarchy, accent focus ring, seek-step badges, consistent utility controls, no blur-heavy effects.
- Version advanced to `0.1.0-dev19` / `versionCode 19`.
- R8/resource shrinking stay disabled to preserve the playback compatibility recovered in dev18.

## Validation performed in this workspace

- Pure core contract suite: 114 tests passed, 0 failed.
- Display mode policy probe covers Auto, 1080p, native-highest and invalid-value fallback.
- Static/adversarial review checked focus hand-off, duplicate scroll correction, compact toolbar width, display-mode fallback, and signer preservation.

Full Android Gradle compilation is not performed in this environment because no Android SDK or the user's private signing keystore is available. Use `scripts/build-dev19-local.ps1` on the existing Windows/Android build machine. The script refuses to create a replacement key and blocks delivery unless the APK signer SHA-256 is:

`5e8dcd5e1eee828e064682ba6f8dc7d54dfcf59d3182dd6b427a23d1214d6b00`
