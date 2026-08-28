# VUEO v0.3.5

VUEO is an Android media client with a built-in **Content Manager**.

## v0.2 milestone

This build replaces the original generic Extensions screen with the agreed architecture:

- **Built-in VUEO features**
- **Stremio Addons**
- **JavaScript Provider Plugins** (repository engine follows in the next milestone)

### Working in v0.2

- Content Manager screen
- Stremio Addon Manager
- Install addon from HTTPS `manifest.json`
- Persist installed addon manifest URLs
- Restore addons when VUEO restarts
- Refresh or remove an addon
- Parse addon resources, types and catalogs
- Load catalog rows into Home
- Load poster and backdrop images without an extra image library
- Open a title and request richer `meta`
- Discover movie streams from installed Stremio stream addons
- Basic smart source ordering
- Stable Android API 36 build configuration
- Corrected GitHub Actions build summary

### Plugin status

The old experimental generic `VUEO Plugin V1` implementation has been removed.

The **Plugins** area is now reserved for the Nuvio-style JavaScript provider repository system:
repository manifest, multiple providers per repository, enable/disable, sandboxed execution and unified source results.

No repository schema is fabricated in this build.

## Build

```bash
./gradlew :app:assembleDebug
```

The included GitHub Actions workflow builds and uploads the debug APK.

## Content flow

```text
Content Manager
   -> Addons
      -> Install manifest
         -> Parse catalogs
            -> Home
               -> Title details
                  -> Find Sources
```

Series episode selection and playback are later milestones.


## v0.2.1 development defaults

On the first launch after this patch, Content Manager seeds these development addons once:

- YaStream: `https://yastream.tamthai.de/manifest.json`
- Cinemeta: `https://v3-cinemeta.strem.io/manifest.json`
- OpenSubtitles v3: `https://opensubtitles-v3.strem.io/manifest.json`

They remain removable. Removing one does not force it back on each app launch.

Addon Manager groups installed addons automatically by their manifest resources:

- Catalog & Metadata
- Streams
- Subtitles
- Multi-purpose
- Other


## v0.2.2 playback milestone

- Parses Stremio series `videos` into seasons and episodes.
- Season selector.
- Episode selector.
- Unified source picker.
- Smart ranking prioritises directly playable sources.
- `Play Best`.
- Media3 direct HTTPS playback.
- HLS playback.
- DASH playback.
- Remote subtitle tracks are attached to the Media3 item when subtitle addons return them.
- Basic playback resume position.
- Torrent sources remain visible but are labelled as not directly playable until the torrent/debrid layer exists.

The JavaScript Provider Plugin engine remains the next major content-source milestone after this playback path is verified.


## v0.3.0 Nuvio-style Plugin Repository Manager

VUEO now understands the common Nuvio provider repository manifest shape:

- repository `name`
- repository `version`
- `scrapers` array
- provider `id`
- provider `name`
- provider `filename`
- `supportedTypes`
- `enabled`
- `logo`
- `contentLanguage`
- `formats`
- `limited`
- `disabledPlatforms`
- `supportsExternalPlayer`

Implemented:

- Install repository by base URL or direct `manifest.json`
- HTTPS-only repository installation
- Persistent repository storage
- Refresh repository manifest
- Delete repository
- Provider count
- Global plugin-provider toggle
- Enable/disable individual providers
- Persist provider state
- Compute provider JavaScript URL from repository base + filename

Not yet implemented:

- JavaScript execution runtime
- `getStreams(tmdbId, mediaType, season, episode)` invocation
- `fetch`, axios and cheerio compatibility layer
- Plugin stream results in Unified Source Engine

Those are the next runtime milestone.


## v0.3.1 Provider Runtime

Development-default plugin repositories:

- Yoru's Repo
- All-in-One-Nuvio

They are seeded once and remain removable.

Runtime:

- Nuvio-style `getStreams(tmdbId, mediaType, season, episode)`
- Provider JS loaded from repository `filename`
- WebView-based JavaScript sandbox
- WebView network access blocked
- Restricted native HTTPS `fetch` bridge
- Local/private network requests blocked
- Provider timeout
- Up to 3 provider executions concurrently
- Provider URL, quality and request headers mapped into VUEO `StreamSource`
- Stremio and plugin sources merged in the same Source Picker
- Provider headers passed into Media3 playback

TMDB bridge:

- Numeric or `tmdb:` media IDs are used directly
- IMDb IDs can be mapped through TMDB `/find`
- User supplies their own TMDB v3 API key in Content Manager > Plugins

Compatibility note:

This first runtime targets bundled Nuvio providers that export `module.exports.getStreams` and use `fetch`.
Providers requiring unsupported Node modules or special runtime globals can fail individually without stopping the rest of source discovery.


## v0.3.2 Provider Health and Runtime Diagnostics

- Persistent provider health records
- Online, Slow, No Results, Failed and Unknown states
- Per-provider response time
- Last runtime error retained for failed providers
- Recent console logs retained for diagnostics
- Provider Health summary in Content Manager > Plugins
- Health updates after every source discovery
- Source Picker shows online/slow/no-result/failed counts
- Runtime now captures console.log and console.error
- Basic axios compatibility is available through global axios and require("axios")
- Minimal Buffer.from(...).toString("base64") support
- Provider concurrency increased to 5 with a 10 second provider timeout

`No Results` is deliberately separate from `Failed`: a provider can execute correctly and still have no source for a particular title.


## v0.3.3 Provider Script Delivery

This patch addresses provider-wide failures caused before JavaScript execution when
`raw.githubusercontent.com` cannot be resolved on a mobile network.

- GitHub raw manifests and provider scripts get a jsDelivr GitHub CDN candidate.
- The CDN is tried first, with raw GitHub retained as fallback.
- Provider JavaScript is cached in Android `cacheDir` by script URL + provider version.
- Repeated source discovery reuses cached provider scripts instead of downloading dozens of files again.
- Repository refresh uses the same resilient transport.
- Provider Health continues to report the final transport/runtime error when all candidates fail.


## v0.3.4 Nuvio-compatible plugin core

This release replaces the WebView provider runtime.

### Provider code lifecycle

Repository install / refresh now downloads provider JavaScript and stores it under the app's private `nuvio_plugin_scrapers` directory. `Find Sources` reads only local provider code, so source discovery no longer downloads JavaScript from GitHub or jsDelivr.

Existing repositories are migrated in the background and missing provider code is synchronized automatically.

### Runtime

- QuickJS via `quickjs-kt`
- provider evaluation timeout
- native async `fetch()` bridge
- native OkHttp transport
- system DNS first
- Cloudflare DNS-over-HTTPS fallback when system DNS fails
- console diagnostics retained
- basic axios compatibility
- basic Buffer/base64 helpers
- setTimeout compatibility
- URLSearchParams compatibility
- provider health retained
- provider playback headers retained

### Remaining compatibility work

Some Nuvio providers use additional modules such as `crypto-js` or `cheerio-without-node-native`. v0.3.4 reports those as explicit unsupported module errors so later compatibility patches can target the modules actually used by failing providers.


## v0.3.5 Nuvio Compatibility Pack

This release targets the exact runtime failures seen in Provider Health.

### Added require() modules

- `cheerio-without-node-native`
- `cheerio`
- `crypto-js`
- existing `axios`

### HTML compatibility

The Cheerio API is backed by native jsoup 1.23.2. Provider JavaScript gets a Cheerio-style wrapper while parsing and CSS selectors run natively.

Implemented common provider calls include:

- `cheerio.load(html)`
- CSS selection
- `.filter(callback)` and `.filter(selector)`
- `.find()`
- `.text()`
- `.ownText()`
- `.html()`
- `.attr()`
- `.data()`
- `.each()`
- `.map().get()`
- `.eq()`, `.first()`, `.last()`
- `.parent()`, `.parents()`, `.children()`
- `.closest()`, `.next()`, `.prev()`, `.siblings()`
- `.is()`, `.hasClass()`, `.remove()`

### CryptoJS compatibility

Native Android cryptography now backs a CryptoJS-style WordArray API.

Implemented:

- Base64, UTF8, Hex and Latin1 encoders
- WordArray create, concat, clone and random
- MD5
- SHA1 / SHA256 / SHA384 / SHA512
- HmacMD5 / HmacSHA1 / HmacSHA256 / HmacSHA512
- AES CBC PKCS7 encrypt/decrypt
- TripleDES CBC PKCS7 encrypt/decrypt

This directly targets current providers such as Castle, MovieBox and ShowBox.

### Web compatibility

- `URL`
- richer `URLSearchParams`
- `TextEncoder`
- `TextDecoder`
- `AbortSignal.timeout`
- fetch `redirect: "manual"` support

Provider Health remains the source of truth. A provider that executes successfully but returns nothing remains `No Results`, not `Failed`.
