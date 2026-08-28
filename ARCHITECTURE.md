# VUEO Architecture v0.9.4

```text
VUEO
|
+-- Profile Layer
|   +-- ProfileStore
|   +-- Who's Watching
|   +-- Active Profile
|   +-- Per-profile Library / Playback / Subtitle preferences
|
+-- Core Experience
|   +-- Home / Search / Details
|   +-- Library
|   +-- Source Picker
|   +-- Media3 Player
|
+-- Content Manager (global)
|   +-- Stremio Addons
|   +-- Plugin Repositories
|   +-- Providers
|   +-- Provider Health
|
+-- Enhancements (global, optional)
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

## Profile Boundary

```text
GLOBAL
  Content Manager
  Addons / repositories / providers
  Provider Health
  TMDB
  MDBList
  Source technical-detail preference
  Updates

ACTIVE PROFILE
  My List
  Continue Watching
  Watch History
  Playback progress
  Resume preference
  Preferred quality
  Subtitle preferences
```

The default profile keeps the legacy unscoped Library and Playback keys, preserving existing user data when v0.9.4 is installed. Additional profiles use namespaced preference keys.

## Startup Flow

```text
Launch VUEO
    |
Run data migration
    |
Ensure default profile exists
    |
More than one profile + startup picker enabled?
    |
    +-- no  -> Home
    |
    +-- yes -> Who's Watching?
                  |
              Select profile
                  |
                Home
```

No account server or sign-in is required.

## Backup Boundary

Portable backup data contains durable user configuration, all local profile definitions and profile-scoped Library state.

```text
BACKUP
  Profiles
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

v0.9.4 uses backup schema 2 while remaining able to restore schema 1 backups.

## Restore Flow

```text
Select VUEO JSON backup
        |
Validate format + schema
        |
Restore known preference groups including profiles
        |
Preserve local API keys when backup excluded credentials
        |
Clear stale caches + provider health
        |
Run data migration
        |
Ensure a valid local profile exists
        |
Reload Stremio addons
        |
Sync missing provider scripts
        |
Refresh Home + Library + profile state
```

## Migration

`VueoDataMigration` durable-data schema is version 2. The v2 migration guarantees a default local profile exists while keeping the existing default-profile Library and playback keys intact.
