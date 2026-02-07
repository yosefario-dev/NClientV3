# NClientV3

A modernized, unofficial NHentai Android client.  
Forked from [NClientV2](https://github.com/Dar9586/NClientV2) by Dar9586.

Releases: <https://github.com/yosefario-dev/NClientV3/releases>

## What's changed from V2

- **Material 3 / Material You** design with dynamic colors
- **Fixed image loading** — nhentai CDN migrated to webp, added fallback chain
- **Fixed for Android 15+** — edge-to-edge status bar, DrawerLayout scrim
- **Gallery detail page** — collapsing toolbar with blurred cover art
- **Fixed comments crash** — ClassCastException on ImageButton
- **Black splash screen** — no more white flash
- **Updated User-Agent** — works with current nhentai
- **Removed ACRA crash reporting** — no data sent anywhere
- **Updated build system** — AGP 8.5.2, Gradle 8.9, SDK 35
- **Package renamed** to `com.yosefario.nclientv3`

## Features

- Browse main page
- Search by query or tags
- Include or exclude tags
- Blur or hide excluded tags
- Download galleries
- Favorite galleries
- PIN lock for privacy
- Share galleries
- Open in browser
- Bookmarks & History

## Libraries

- [PersistentCookieJar](https://github.com/franmontiel/PersistentCookieJar) (Apache 2.0)
- [OkHttp](https://github.com/square/okhttp) (Apache 2.0)
- [PhotoView](https://github.com/chrisbanes/PhotoView) (Apache 2.0)
- [JSoup](https://github.com/jhy/jsoup) (MIT)
- [Glide](https://github.com/bumptech/glide) (BSD/MIT/Apache)
- [Material Components](https://github.com/material-components/material-components-android) (Apache 2.0)

## Original Contributors

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
