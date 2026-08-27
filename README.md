# VUEO v0.3.0

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
