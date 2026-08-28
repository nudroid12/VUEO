# VUEO Architecture v0.2

```text
                         VUEO
                          |
          +---------------+----------------+
          |                                |
     Built-in App                     Content Manager
          |                                |
   Home / Search /                    +-----+------+
   Library / Player                   |            |
                                  Addons        Plugins
                                  Stremio       JavaScript
                                      |         providers
                                      |        (next milestone)
                                      |
                                Catalog / Meta /
                                Stream / Subtitle
                                      |
                                      v
                              Unified Media Engine
                                      |
                              Smart Source Ranking
                                      |
                                    Player
```

## Naming

- **Content Manager** is the official management section.
- **Addons** means Stremio-compatible addons.
- **Plugins** means JavaScript provider repositories.
- Generic third-party VUEO extensions are not part of this architecture.

## v0.2 scope

The Stremio side is functional through catalog, meta and stream discovery.
The JavaScript plugin repository side is deliberately not executed yet.


## v0.2.2 playback path

```text
Metadata
   |
   +-- Movie ID
   |
   +-- Series -> Season -> Episode video ID
                      |
                      v
               Stream Discovery
                      |
                Smart Ranking
                      |
                Source Picker
                      |
            Direct HTTPS/HLS/DASH
                      |
                  Media3
```

Torrent `infoHash` results are retained in the unified source model, but direct torrent playback is not claimed in this milestone.


## v0.3.0 plugin repository architecture

```text
Content Manager
      |
    Plugins
      |
Repository URL
      |
manifest.json
      |
 +----+----------------------+
 |                           |
Repository metadata       scrapers[]
                             |
                    Provider descriptors
                             |
                    Enable / disable state
                             |
                    Provider JS URL
                             |
                       Runtime next
```

v0.3.0 deliberately separates repository management from arbitrary JavaScript execution.


## v0.3.1 plugin execution path

```text
Cinemeta / Media Item
        |
     IMDb ID
        |
   TMDB Resolver
        |
   Numeric TMDB ID
        |
Enabled Plugin Repositories
        |
Provider JS Bundles
        |
Sandboxed WebView Runtime
        |
restricted native HTTPS fetch
        |
getStreams(...)
        |
Plugin StreamSource[]
        |
        +---------------- Stremio sources
        |
        v
Unified Source Picker
        |
Media3 with provider request headers
```

Remote provider JavaScript cannot access Android files, content providers, or local/private network addresses through VUEO's native fetch bridge.


## v0.3.2 health loop

```text
Provider execution
      |
      +-- streams + fast  -> Online
      +-- streams + slow  -> Slow
      +-- no streams      -> No Results
      +-- runtime error   -> Failed
      |
      v
PluginHealthStore
      |
      +-- Content Manager health summary
      +-- per-provider status and latency
      +-- failure reason
```


## v0.3.3 provider script transport

```text
Plugin manifest / provider filename
            |
            v
Raw GitHub URL detected
            |
     +------+------+
     |             |
 jsDelivr CDN   raw GitHub
     |             |
     +------+------+
            |
      Provider script
            |
      Disk script cache
            |
      JS runtime / health
```

The provider version is part of the cache key, so a manifest provider-version update
naturally causes a fresh script download.


## v0.3.4 provider core

```text
Install / Refresh Repository
          |
          v
Manifest + provider filenames
          |
          v
Native OkHttp
(system DNS -> DoH fallback)
          |
          v
Private provider code store
filesDir/nuvio_plugin_scrapers
          |
          +-------------------------+
                                    |
                              Find Sources
                                    |
                                    v
                                 QuickJS
                                    |
                         native async fetch()
                                    |
                                    v
                                  OkHttp
                                    |
                                    v
                             StreamSource[]
                                    |
                     +--------------+--------------+
                     |                             |
               Plugin sources               Stremio sources
                     |                             |
                     +--------------+--------------+
                                    |
                             Source Picker
```

`Find Sources` never downloads provider JavaScript. Network access during discovery is only the provider's own `fetch()` traffic.


## v0.3.5 compatibility layer

```text
Provider JS
    |
    +-- require("axios")
    |
    +-- require("cheerio-without-node-native")
    |          |
    |          +--> JS Cheerio facade
    |                    |
    |                    +--> native jsoup DOM/CSS engine
    |
    +-- require("crypto-js")
    |          |
    |          +--> JS WordArray facade
    |                    |
    |                    +--> native Android crypto
    |
    +-- URL / URLSearchParams
    |          |
    |          +--> native OkHttp URL parser
    |
    +-- fetch()
               |
               +--> native OkHttp
```

The compatibility layer is deliberately based on the provider APIs documented by Nuvio rather than exposing Node.js wholesale.


## v0.3.6 plugin phase exit

```text
Provider execution
      |
      +-- Online
      +-- Slow
      +-- No Results
      +-- Needs Setup
      +-- Unavailable
      +-- Blocked
      +-- Timeout
      +-- Failed
      |
      v
Plugin sources + Stremio sources
      |
      v
Exact duplicate removal
      |
      v
Smart ranking
      |
      v
4K / 1080p / 720p / Other groups
      |
      v
Play Best
```

v0.3.6 marks the end of continuous provider-runtime work for the current development phase. Remaining individual provider issues can be revisited after the main application is feature-complete.


## v0.4.0 progressive source architecture

```text
User taps Find Sources
          |
          v
Source Picker opens immediately
          |
          +---- recent 2-minute cache --------> instant sources
          |
          +---- Stremio addons
          |         |
          |         +--> partial results
          |
          +---- Plugin engine
                    |
                    +--> healthy providers first
                    |
                    +--> provider completes
                    |
                    +--> partial results
                              |
                              v
                     SourceCleaner + Ranker
                              |
                              v
                    UI updates progressively
                              |
                         Play Best ready
                              |
                    remaining providers continue
```

Leaving the Source Picker cancels the active discovery job.


## v0.5.0 discovery architecture

```text
Installed Stremio Addons
        |
        +------ Home catalogs ------+
        |                           |
        |                     Catalog cache
        |                           |
        |                +----------+----------+
        |                |                     |
        |              Home               Local search
        |                                      |
        +------ search-capable catalogs -------+
                                               |
                                         Remote search
                                               |
                                               v
                                      Merge + deduplicate
                                               |
                                               v
                                        Search results
                                               |
                                               v
                                          Details
                                               |
                           +-------------------+------------------+
                           |                                      |
                         Watch                              More Like This
                           |                                      |
                    Fast Source Engine                    Cached discovery
```

Home and search caches are intentionally short-lived and memory-only during this development phase.
