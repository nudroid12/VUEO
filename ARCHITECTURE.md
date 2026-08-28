# VUEO Architecture v0.9.6

```text
VUEO
|
+-- Brand Layer
|   +-- Fixed Lime Green VUEO mark
|   +-- V + play launcher identity
|
+-- Appearance Layer
|   +-- VUEO Dark surfaces
|   +-- White default accent
|   +-- Lime Green / Ocean / Violet / Amber / Coral
|   +-- Dynamic Material3 primary color
|
+-- Profiles
|   +-- Who's Watching
|   +-- My List / Continue Watching / History
|   +-- Playback progress
|   +-- Per-profile playback + subtitle preferences
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
    +-- SettingsStore
    +-- ProfileStore
    +-- LibraryStore
    +-- PlaybackStore
    +-- Backup / Restore
    +-- Migration
```

## Accent ownership

The selected interface accent is a global app preference in `vueo_settings`. It changes interactive UI color only. The VUEO brand mark remains Lime Green and does not follow the selected accent.

## Profile ownership

Viewing state and personal playback/subtitle preferences are profile-scoped. Content sources, provider configuration, optional enhancement keys and update configuration remain global.


## v0.9.6 UI system

Brand identity and interface accent are intentionally separate. The VUEO mark and wordmark use Brand Lime Green. Interactive accent color is controlled by Appearance and defaults to White. Core screens share the same dark layered surfaces, rounded cards, restrained status colors and cinematic poster/backdrop treatment.
