# KodaStream

An Android app that pairs a **Material 3 Expressive** interface with the
**CloudStream extension engine**.

- The look (theme, palettes, typography) comes from the Koda interface.
- Content comes from **extensions** (`.cs3` files) that you install from
  repositories, exactly like CloudStream. Existing CloudStream repositories work
  as they are.
- No YouTube, no accounts, no content bundled in the app.

## What works in this first version

| Area | Status |
|---|---|
| Home (rows per provider, pick a provider) | yes |
| Search across every installed provider | yes |
| Details: movies, series (seasons), anime (dub/sub), live streams | yes |
| Player (ExoPlayer: HLS, DASH, progressive, source + subtitle picker, resume) | yes |
| Add repositories (`repo.json`, `cloudstreamrepo://`, short codes) | yes |
| Install / update / uninstall extensions, update at startup | yes |
| Theme: system/light/dark, AMOLED, wallpaper colors, 28 palettes, 5 styles | yes |
| English and Arabic (RTL) | yes |
| Library / favourites / history / downloads | not yet |
| Torrents and magnet links | no (no torrent engine) |
| DRM streams | no |
| Separate audio tracks, casting, sync services (AniList, MAL, ...) | not yet |
| Extension settings screens (`openSettings`) | not yet |

R8/minification is **off on purpose**: extensions are linked against the engine's
class and method names at runtime and would break if they were renamed.

## How it fits together

```
:library   CloudStream's engine, unchanged (MainAPI, extractors, networking)
:app       Compose UI + a small compatibility layer
             com.lagradost.cloudstream3.*   the classes extensions expect to find
                                            (CloudStreamApp, CommonActivity, Plugin,
                                             DataStore, CloudflareKiller, ...)
             com.lagradost.cloudstream3.plugins   PluginManager / RepositoryManager
             com.kodastream.app.*           the screens
```

An extension is a DEX file plus `manifest.json`. `PluginManager` loads it with a
`PathClassLoader` whose parent is the app's class loader, so the extension finds
`MainAPI`, `ExtractorLink` and friends from `:library`, and the few app classes
it calls from the compatibility layer.

## Build

GitHub Actions builds the APK on every push to `main` (workflow
`.github/workflows/build.yml`). Run it by hand and download the result with the
GitHub CLI:

```bash
gh workflow run build.yml
gh run watch
gh run download --name KodaStream-apk
```

If the build fails:

```bash
gh run view --log-failed
```

Local build (JDK 17 and the Android SDK needed):

```bash
./gradlew :app:assembleRelease
```

The release APK is signed with the **debug key** so it installs right away.
Add your own keystore before publishing anywhere.

## License and credits

- The engine in `library/` and the extension-loading design are from
  [CloudStream](https://github.com/recloudstream/cloudstream), **GPL-3.0**.
  This project is therefore GPL-3.0 as well (see `LICENSE`) and must be
  distributed with its source.
- The files in `app/src/main/java/com/kodastream/app/ui/theme/` are adapted from
  the **Koda** interface. The Koda source that was supplied contained **no
  license file**, so permission to reuse those files has to come from its author
  before this project is published. If it does not, replace that folder with your
  own theme (it is a handful of small files).
- This app ships **no content and no providers**. It only runs extensions the
  user chooses to install. What you watch is your responsibility.

---

## بالعربية

تطبيق أندرويد يجمع واجهة **Material 3 Expressive** مع **محرك إضافات كلاودستريم**.

- الشكل (الألوان والخطوط والثيم) مأخوذ من واجهة كودا.
- المحتوى يأتي من **الإضافات** (ملفات cs3) التي تثبّتها من المستودعات كما في
  كلاودستريم، وأي مستودع كلاودستريم موجود يعمل كما هو.
- بدون يوتيوب وبدون حسابات وبدون أي محتوى داخل التطبيق.

البناء يتم تلقائياً عبر GitHub Actions عند كل دفع إلى الفرع الرئيسي، والأوامر في
قسم Build أعلاه.

الترخيص: GPL-3.0 بسبب محرك كلاودستريم. ملفات الثيم المأخوذة من كودا لم يكن معها
ملف ترخيص، فيجب الحصول على إذن صاحبها قبل نشر المشروع أو استبدالها بثيم خاص بك.
