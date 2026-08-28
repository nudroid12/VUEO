# VUEO v0.9.3

VUEO is an Android media frontend with open content sources, progressive source discovery, optional enrichment services, local Library data and Media3 playback.

## v0.9.3 Distribution & Data

This milestone makes VUEO safer to keep, move and update as a sideloaded APK.

### Backup & Restore

Settings > Data & Storage now supports a portable JSON backup using Android's system document picker.

Backups include:

- installed Stremio addon manifest URLs
- plugin repositories and provider enable/disable preferences
- playback, subtitle and source preferences
- TMDB and MDBList enhancement preferences
- My List
- Continue Watching
- Watch History
- playback resume progress

Temporary catalog/search cache, source cache, provider health diagnostics and downloaded provider scripts are not copied. They are rebuilt after restore.

API keys are excluded by default. Users can explicitly enable Include API Keys before creating a backup.

Restore validates a versioned VUEO backup schema, replaces the backed-up preference groups, clears stale runtime caches, runs data migration, reloads Stremio addons and syncs any missing provider scripts.

### Reset VUEO Data

Data & Storage can reset VUEO to a fresh local state without uninstalling the APK. Content defaults are seeded again after reset.

### Updates

Settings > Updates now has a functional release checker.

- manual Check for Updates
- optional background checks, throttled to at most once every six hours
- cached latest-release state for offline Settings display
- release changelog display
- HTTPS APK delivery link support
- HTTPS Telegram release/channel link support
- no forced redirect when a release feed has no trusted delivery URL

The default release feed is `update.json` from the VUEO GitHub repository. Delivery is intentionally feed-driven so VUEO is not locked to Telegram or GitHub Releases.

### Data Migration

v0.9.3 adds a versioned local-data migration foundation. Migration runs before normal boot and after backup restore.

## Optional Enhancements

TMDB and MDBList remain optional. VUEO core discovery, sources, player and Library continue to work without either service.

## Version

- versionName: 0.9.3
- versionCode: 20
- compileSdk: 36
- targetSdk: 36
- minSdk: 23
