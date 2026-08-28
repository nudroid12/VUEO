# VUEO v0.9.4

VUEO is an Android media frontend with open content sources, progressive source discovery, optional enrichment services, local profiles, Library data and Media3 playback.

## v0.9.4 Profiles & Final Polish

This milestone adds local "Who's Watching?" profiles without introducing an account server, email login or cloud dependency.

### Local Profiles

VUEO creates a default local profile automatically. Users can add, edit, switch and delete additional profiles from Settings > Profiles.

When more than one profile exists, VUEO can show a "Who's Watching?" picker on startup. Single-profile users can keep this disabled and launch directly into Home.

Per-profile data:

- My List
- Continue Watching
- Watch History
- playback resume progress
- resume playback preference
- preferred playback quality
- subtitle language and subtitle behavior

Shared app-wide data:

- Content Manager
- Stremio addons
- plugin repositories and providers
- Provider Health
- TMDB configuration
- MDBList configuration
- source technical-detail preference
- update configuration

Kids Profile is a profile label and architecture hook only in v0.9.4. It does not yet filter or block content.

### Backup & Restore

VUEO backups now include local profiles and every profile's isolated Library/playback data. Existing v0.9.3 backup files remain accepted.

API keys remain excluded by default unless Include API Keys is explicitly enabled.

### Data & Storage

Continue Watching and Watch History clear actions affect only the active profile. A full Reset VUEO Data still resets all profiles and application configuration.

### Final Polish

- player user-agent now follows the current VUEO app version
- profile switching invalidates Library state immediately
- Content Manager and enhancement configuration remain global when switching profiles
- the v0.9.3 Settings section-label compile hotfix is carried forward

## Optional Enhancements

TMDB and MDBList remain optional. VUEO core discovery, sources, player, Library and profiles continue to work without either service.

## Version

- versionName: 0.9.4
- versionCode: 21
- compileSdk: 36
- targetSdk: 36
- minSdk: 23
