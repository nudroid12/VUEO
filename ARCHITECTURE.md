# VUEO Architecture v0.2

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
