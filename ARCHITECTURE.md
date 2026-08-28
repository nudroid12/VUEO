# VUEO Architecture v0.9.2

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


## v0.6.0 playback architecture

```text
Source Picker
     |
     v
StreamSource + headers + subtitles
     |
     v
Media3 MediaItem
     |
     v
ExoPlayer
     |
     +---- currentTracks ----> Audio selector
     |
     +---- currentTracks ----> Subtitle selector
     |
     +---- Player.Listener --> Buffering / errors
     |
     +---- position ----------> PlaybackStore
     |                             |
     |                             +--> periodic save
     |                             +--> pause save
     |                             +--> exit save
     |
     +---- error
              |
              +--> Retry
              +--> Other Source
```

The player uses the existing Media3 1.11.0 playback stack. Full Library and Continue Watching surfaces remain a later milestone.


## v0.7.0 local Library architecture

```text
Details
   |
   +---- Add to My List --------------------+
   |                                        |
   |                                  LibraryStore
   |                                        |
Watch                                       +--> My List
   |                                        |
   v                                        +--> History
Player                                      |
   |                                        +--> Continue Watching
   +---- selected episode / video ID -------+
   |
   +---- position / duration ---------------+
   |
   +---- PlaybackStore --> Resume prompt

Library
   |
   +--> Continue Watching --> Details at saved episode
   +--> My List ----------> Details
   +--> History ----------> Details
```

LibraryStore uses app-private SharedPreferences with compact JSON records. This milestone intentionally stays local-only.


## v0.8.0 presentation layer

```text
                 VUEO visual system
                       |
        +--------------+--------------+
        |              |              |
   Background       Surfaces        Accent
   #080B0F         layered dark     restrained
        |              |              |
        +--------------+--------------+
                       |
   +---------+---------+---------+---------+
   |         |         |         |         |
  Home     Search    Details   Library   Content
   |         |         |         |       Manager
   +---------+---------+---------+---------+
                       |
                 Source Picker
                       |
                    Player
```

The UI overhaul is deliberately presentation-only. Core discovery, provider, player and Library logic remain separate from the visual layer.


## v0.9.0 runtime hardening

```text
APP START
   |
   +--> Catalog disk snapshot
   |       |
   |       +--> Home renders cached rows
   |
   +--> Addon bootstrap
           |
           +--> pooled OkHttp + resilient DNS
           |
           +--> background fresh catalogs
                       |
                       +--> memory cache
                       +--> bounded disk snapshot


WATCH
   |
   +--> recent SourceDiscoveryCache
   |
   +--> Stremio addons
   |       +--> per-addon timeout
   |
   +--> QuickJS providers
           +--> provider timeout
           +--> Provider Health v2 per-record writes
           +--> progressive source updates


PLAYER
   |
   +--> PlaybackStore every 10s
   |
   +--> LibraryStore every 30s
           + pause / close / complete force updates
```

Source stream URLs remain memory-only because provider links may expire. Public catalog metadata can safely use a bounded local disk snapshot for fast cold startup.


## v0.9.1 settings architecture

```text
Bottom Navigation
   |
   +-- Home
   +-- Search
   +-- Library
   +-- Settings
           |
           +-- Content Manager
           |      +-- Addons
           |      +-- Plugins
           |
           +-- Playback Preferences
           +-- Source Preferences
           +-- Local Data Controls
           +-- About
```

`SettingsStore` uses app-private SharedPreferences and currently owns:

- resume playback
- preferred source quality
- source-card technical detail visibility

Preferred quality feeds the Smart Source ranking layer. It is intentionally implemented as a scoring boost rather than a source filter.


## v0.9.1 Settings and optional enhancement architecture

```text
Settings
   |
   +-- Content Manager
   |      +-- Stremio Addons
   |      +-- Plugin Repositories
   |      +-- Providers
   |      +-- Provider Health / Diagnostics
   |
   +-- Enhancements
   |      +-- TMDB        optional
   |      +-- MDBList     optional
   |
   +-- Playback
   +-- Subtitles
   +-- Sources
   +-- Appearance
   +-- Data & Storage
   +-- Updates
   +-- About VUEO
```

TMDB and MDBList are enrichment modules, not VUEO core dependencies. No enhancement key is required for normal catalog browsing, source discovery, playback, Library, or Content Manager operation.

TMDB's existing provider ID bridge continues to use the locally stored key while the user-facing configuration lives under Enhancements. MDBList is isolated in Settings storage until the v0.9.2 rating enrichment layer consumes it.

Subtitle source acquisition remains part of Stremio Addons in Content Manager. Subtitle preference and presentation behavior live under Settings > Subtitles.

## v0.9.2 optional discovery and enrichment layer

```text
                         Details
                            |
              +-------------+-------------+
              |                           |
          VUEO Core                 Enhancements
              |                           |
      Stremio metadata          +---------+---------+
      cached catalog            |                   |
              |                TMDB               MDBList
              |                 |                   |
              |         metadata / artwork          |
              |         recommendations             |
              |         similar titles              |
              |                 |                   |
              +----------> More Like This      rating bundle
                                 |                   |
                          VUEO fallback       enabled ratings
                                 |                   |
                                 +---------+---------+
                                           |
                                      Details UI
```

### Design rule

Enhancements never become a dependency of VUEO Core. Missing keys, disabled feature toggles, network failures, or unavailable enhancement services must fall back to the existing VUEO experience instead of blocking Details, source discovery, or playback.

### TMDB path

- Resolve an IMDb or numeric media ID to TMDB when possible.
- Fetch richer Details only when metadata or artwork enrichment is enabled.
- Fetch Recommendations first, then Similar when enabled.
- Merge enhancement discovery ahead of cached local recommendations without removing the VUEO fallback.
- TMDB recommendation cards use a `tmdb:` ID internally and attempt to restore an IMDb ID before the existing core metadata and source path is used.

### MDBList path

- Resolve a lookup through IMDb when the media already has an IMDb ID, otherwise use a TMDB ID when available.
- Fetch one rating bundle per title.
- Cache repeated rating lookups in memory.
- Filter the returned bundle according to the user's IMDb, Rotten Tomatoes, Metacritic, TMDB, and Trakt toggles.

### Attribution

The About VUEO page contains the required textual TMDB attribution notice. Approved logo treatment can be finalized with release branding before v1.0.
