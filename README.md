# VUEO v0.2

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
