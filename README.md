# VUEO v0.1 Foundation

VUEO is an Android-first, neutral media hub designed around two extension systems:

1. Stremio Addon Protocol compatibility.
2. A sandbox-friendly VUEO Plugin V1 manifest for declarative remote integrations.

## What is already implemented

- Dark Jetpack Compose shell inspired by the approved VUEO UI direction.
- Home, Search, Library and Extensions tabs.
- Extension installer UI.
- Stremio manifest detection and parsing.
- Stremio `catalog`, `meta`, `stream`, and `subtitles` resource adapters.
- VUEO Plugin V1 manifest detection and route mapping.
- Unified `MediaExtension` interface.
- Unified stream resolver.
- First-pass Smart Source ranking.
- Media3 dependencies prepared for the player milestone.
- HTTPS-only extension installation by default.

## Current plugin security model

VUEO Plugin V1 does not execute arbitrary APK, JAR, JavaScript, or native code. A plugin is a declarative manifest that exposes HTTPS JSON routes. This keeps plugins isolated from device files, app internals, other extensions and Android permissions.

A future signed SDK can expand capabilities without throwing away this interface.

## VUEO Plugin V1 manifest

See `vueo-plugin-example.json`.

Required:

- `schema`: `vueo-plugin-v1`
- `id`
- `name`
- `version`
- one or more HTTPS routes

Supported routes in v0.1:

- `catalog`
- `meta`
- `stream`
- `subtitles`

Route templates support `{type}` and `{id}`.

## Build requirements

- Android Studio with Android API 37 SDK installed.
- JDK 17 or newer supported by the selected Android Studio/AGP environment.
- Internet connection for Gradle dependencies.

## Next milestone

1. Persist installed extensions.
2. Add extension permission review before install.
3. Build catalog aggregation for Home.
4. Movie/series details page.
5. Source picker using `UnifiedMediaEngine.resolveStreams()`.
6. Media3 player.
7. Android TV focus/remote layout.

## Legal/product boundary

VUEO is a neutral client. The core project does not bundle third-party unlicensed media sources. Developers and users are responsible for ensuring extensions and media sources they add are lawful to use.

### Gradle launcher note

This source bundle includes a small `gradlew`/`gradlew.bat` bootstrap launcher because the build environment used to generate this package could not bundle the official Gradle wrapper JAR. On first run it downloads the official Gradle 9.3.1 binary distribution from `services.gradle.org`, then invokes Gradle from that local cache.
