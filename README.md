# VUEO v0.9.5

VUEO is an Android media frontend with open content sources, local profiles, progressive source discovery, optional enrichment services, Library state and Media3 playback.

## v0.9.5 Branding & Appearance

VUEO now separates its brand color from the user's interface accent.

- White is the default accent for a calmer dark interface.
- Optional accents: Lime Green, Ocean, Violet, Amber and Coral.
- Accent selection applies immediately without restarting the app.
- Material3 primary controls and VUEO custom interactive states use the selected accent.
- The VUEO logo and wordmark stay Lime Green as a consistent brand identity.
- The launcher mark is refined into the VUEO V + play motif.

## Profiles

Local Profiles and Who's Watching remain available from v0.9.4. My List, Continue Watching, History, playback progress and personal playback/subtitle preferences are isolated by profile. Content Manager, providers, TMDB and MDBList remain app-wide.

## Backup

Appearance is stored in `vueo_settings`, so the selected accent is included automatically in VUEO backup and restore. API keys remain excluded unless Include API Keys is enabled.

## Version

- versionName: 0.9.5
- versionCode: 22
- compileSdk: 36
- targetSdk: 36
- minSdk: 23
