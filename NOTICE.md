# Third-party code

## CloudStream (GPL-3.0)
- `library/` is CloudStream's `library` module, copied without changes.
- `gradle/libs.versions.toml`, `gradle.properties`, the Gradle wrapper and the workflow
  conventions follow CloudStream's.
- In `app/src/main/java/com/lagradost/cloudstream3/`, the files `network/CloudflareKiller.kt`
  and `network/DdosGuardKiller.kt` are verbatim copies; `CloudStreamApp.kt`, `CommonActivity.kt`,
  `plugins/Plugin.kt`, `plugins/PluginManager.kt`, `plugins/RepositoryManager.kt`,
  `utils/DataStore.kt` and `network/RequestsHelper.kt` are reduced re-implementations of the
  CloudStream classes of the same names, kept under the same package and member names because
  extensions are compiled against them.
- Source: https://github.com/recloudstream/cloudstream
- License: GNU General Public License v3.0 (`LICENSE`).

## Koda (GPL-3.0)
- `app/src/main/java/com/kodastream/app/ui/theme/` (`Color.kt`, `Type.kt`, `PaletteStyle.kt`,
  `ColorPalettes.kt`, `Theme.kt`) is adapted from the Koda app's theme package.
- `app/src/main/java/com/kodastream/app/ui/components/` reuses Koda's shared UI components
  (`Skeleton.kt`, `ExpressivePullToRefresh.kt`, `SearchField.kt`, the card and badge patterns
  in `Common.kt`, the motion presets in `Motion.kt`), adapted to KodaStream's data classes.
- Source: https://github.com/Ivorisnoob/Koda
- License: GNU General Public License v3.0 (the copy supplied carries the full text in `LICENSE`).
- Koda's logo and name are not used.
