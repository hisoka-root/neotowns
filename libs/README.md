# Local API Jars

Jars in this directory are `compileOnly` dependencies not available from any public maven.

## Required for compilation

| Jar | Why it's needed | Where to get it |
|---|---|---|
| `journeymap-neoforge-1.21.1-*.jar` | Contains `journeymap.api.server.ServerAPI` implementation enum | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/journeymap) — download the main mod jar |

## Optional — for Xaero's World Map support

| Jar | Why it's needed | Where to get it |
|---|---|---|
| `xaeroworldmap-neoforge-*.jar` | Contains Xaero's World Map API | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/xaeros-world-map) — if not already resolved via maven |

## Resolved from maven (no manual download)

| Dependency | Maven coordinate | Repo |
|---|---|---|
| JourneyMap API | `info.journeymap:journeymap-api-neoforge:2.0.0-1.21.1` | `maven.blamejared.com` |
| Xaero Minimap | `xaero.minimap:xaerominimap-neoforge-1.21.1:26.1.0` | `chocolateminecraft.com/maven` |
| FTB Chunks | `dev.ftb.mods:ftb-chunks-neoforge:2101.1.19` | `maven.ftb.dev/releases` |
| FTB Teams | `dev.ftb.mods:ftb-teams-neoforge:2101.1.10` | `maven.ftb.dev/releases` |
| FTB Library | `dev.ftb.mods:ftb-library-neoforge:2101.1.32` | `maven.ftb.dev/releases` |
| Architectury (transitive) | `dev.architectury:architectury-neoforge:13.0.8` | `maven.architectury.dev` |

This directory is gitignored (`libs/*.jar`). Only `README.md` is tracked.
