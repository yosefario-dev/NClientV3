<p align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" width="100" alt="NClientV3 icon" />
</p>

<h1 align="center">NClientV3</h1>

<p align="center">
  A modernized, unofficial NHentai Android client.<br>
  Forked from <a href="https://github.com/Dar9586/NClientV2">NClientV2</a> by Dar9586.
</p>

<p align="center">
  <a href="https://github.com/yosefario-dev/NClientV3/releases"><img src="https://img.shields.io/github/v/release/yosefario-dev/NClientV3?include_prereleases&style=for-the-badge&color=ec2854" alt="Release"></a>
  <a href="https://github.com/yosefario-dev/NClientV3/actions"><img src="https://img.shields.io/github/actions/workflow/status/yosefario-dev/NClientV3/build.yml?style=for-the-badge" alt="Build"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/yosefario-dev/NClientV3?style=for-the-badge" alt="License"></a>
  <img src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 7.0+">
</p>

---

## Screenshots

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.jpg" width="180" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.jpg" width="180" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.jpg" width="180" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.jpg" width="180" />
</p>

## What's new in V3

| Change | Details |
|--------|---------|
| **Material 3 / Material You** | Dynamic colors, updated theme across all screens |
| **Fixed image loading** | nhentai CDN migrated to webp — added smart fallback chain |
| **Android 15+ support** | Edge-to-edge display, status bar fixes |
| **Gallery detail redesign** | Collapsing toolbar with blurred cover art |
| **Comments crash fix** | ClassCastException on ImageButton resolved |
| **Black splash screen** | No more white flash on launch |
| **Updated User-Agent** | Works with current nhentai |
| **No tracking** | Removed ACRA crash reporting — zero data sent |
| **Modern build** | AGP 8.5.2, Gradle 8.9, SDK 35 |

## Features

- Browse and search galleries
- Search by tags with include/exclude filters
- Blur or hide excluded tags
- Download galleries for offline reading
- Favorite galleries
- PIN lock for privacy
- Random gallery discovery
- Share galleries & open in browser
- Bookmarks & history
- Multi-language support (EN, FR, IT, TR, ZH, DE, ES, JA, RU, UK, AR)

## Download

Get the latest APK from [**Releases**](https://github.com/yosefario-dev/NClientV3/releases).

The app includes a built-in self-updater that checks for new releases automatically.

## Building from source

```bash
git clone https://github.com/yosefario-dev/NClientV3.git
cd NClientV3
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Libraries

| Library | License |
|---------|---------|
| [Material Components](https://github.com/material-components/material-components-android) | Apache 2.0 |
| [OkHttp](https://github.com/square/okhttp) | Apache 2.0 |
| [Glide](https://github.com/bumptech/glide) | BSD/MIT/Apache |
| [JSoup](https://github.com/jhy/jsoup) | MIT |
| [PhotoView](https://github.com/chrisbanes/PhotoView) | Apache 2.0 |
| [PersistentCookieJar](https://github.com/franmontiel/PersistentCookieJar) | Apache 2.0 |

## Credits

NClientV3 is a fork of [NClientV2](https://github.com/Dar9586/NClientV2). Huge thanks to the original contributors:

- [Dar9586](https://github.com/Dar9586) — original author
- [Still34](https://github.com/Still34) — code cleanup & Traditional Chinese
- [TacoTheDank](https://github.com/TacoTheDank) — XML and Gradle cleanup
- [hmaltr](https://github.com/hmaltr) — Turkish translation
- [chayleaf](https://github.com/chayleaf) — Cloudflare bypass
- And [many more](https://github.com/Dar9586/NClientV2#contributors)

## License

```text
Copyright 2021 Dar9586
Copyright 2026 yosefario-dev

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
