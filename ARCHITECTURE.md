# VUEO Architecture v0.9.3

```text
VUEO
|
+-- Core Experience
|   +-- Home / Search / Details
|   +-- Library
|   +-- Source Picker
|   +-- Media3 Player
|
+-- Content Manager
|   +-- Stremio Addons
|   +-- Plugin Repositories
|   +-- Providers
|   +-- Provider Health
|
+-- Enhancements (optional)
|   +-- TMDB
|   +-- MDBList
|
+-- Data Layer
|   +-- SettingsStore
|   +-- LibraryStore
|   +-- PlaybackStore
|   +-- VueoBackupManager
|   +-- VueoDataMigration
|   +-- Catalog / Source Cache
|
+-- Distribution Layer
    +-- VueoUpdateManager
    +-- VueoUpdateStore
    +-- HTTPS release manifest
        +-- APK download URL, optional
        +-- Telegram URL, optional
```

## Backup Boundary

Portable backup data contains durable user configuration and Library state only.

```text
BACKUP
  Content Manager preferences
  Settings
  Optional enhancement preferences
  Library
  Playback progress
  API keys only with explicit opt-in

REBUILT
  Catalog/search cache
  Recent source cache
  Provider health
  Downloaded provider JavaScript
```

This keeps backup files small and avoids moving stale runtime state between devices.

## Restore Flow

```text
Select VUEO JSON backup
        |
Validate format + schema
        |
Restore known preference groups
        |
Preserve local API keys when backup excluded credentials
        |
Clear stale caches + provider health
        |
Run data migration
        |
Reload Stremio addons
        |
Sync missing provider scripts
        |
Refresh Home + Library state
```

## Update Flow

```text
App boot or Settings > Check for Updates
        |
VueoUpdateManager
        |
HTTPS update.json
        |
versionCode comparison
        |
+------------------------+
| newer release?         |
+------------------------+
        |
        +-- no  -> Up to date
        |
        +-- yes -> What's New
                  +-- Download Update if downloadUrl exists
                  +-- Open Telegram if telegramUrl exists
```

Automatic checks are cached and throttled. Update delivery stays provider-neutral so a future release can use Telegram, GitHub Releases or another HTTPS endpoint without changing the app architecture.

## Migration

`VueoDataMigration` owns the local durable-data schema version. Migrations run before normal boot and after restore, allowing future preference/schema changes without forcing users to clear app data.
