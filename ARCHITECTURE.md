# VUEO Architecture v0.1

```text
UI / Compose
    |
    +-- Home
    +-- Search
    +-- Library
    +-- Extensions
            |
            v
    ExtensionInstaller
            |
       +----+----------------+
       |                     |
StremioAddonProvider   VueoPluginProvider
       |                     |
       +----------+----------+
                  |
           MediaExtension
                  |
          UnifiedMediaEngine
                  |
            SourceRanker
                  |
             Media3 Player
```

## Rules

- UI never depends directly on a provider implementation.
- All providers normalize output to VUEO domain models.
- VUEO Plugin V1 is declarative and remote only.
- Extension URLs are HTTPS only.
- Arbitrary APK/JAR/native plugin execution is intentionally not part of v0.1.
- Streams are resolved concurrently and failures are isolated per extension.
