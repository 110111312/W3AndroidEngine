# The mobile branch: an Android-only fork

This branch is a personal fork of [nikoltu/W3AndroidEngine](https://github.com/nikoltu/W3AndroidEngine) with two
changes of scope, made so the engine can be built and played on a phone from one local copy of the game files:

1. **No PC build.** The `desktop` module, the LWJGL launcher and the Windows helpers are gone.
2. **No "point at your game folder" step.** The game data lies at a fixed place in the app's own storage, and the
   engine configuration is written from what is found there.

Nothing in `core` or `shared` was forked: the engine itself is upstream's.

## 1. The desktop module is gone

Removed in `0c1a2176`:

- `desktop/` — `DesktopLauncher`, `HeadlessLauncher`, the LWJGL `LwjglApplication`/`LwjglCanvas` overrides,
  JavaSound/OpenAL audio, the MDX editor (`Ysera*`), the Windows launcher pages, the ffmpeg movie decoders and the
  bundled FLAC decoder (47 tracked files)
- the `project(":desktop")` block in `build.gradle` and `desktop` in `settings.gradle`
- `welaunch.bat`, and the `/desktop/*` lines in `.gitignore`

Nothing in `core`, `shared`, `server` or `android` imported `com.etheller.warsmash.desktop.*` or lwjgl, so this was a
leaf module and the APK build is unchanged by its removal. `core/test/.../DesktopTextureSizeTest` only tests core
logic and stays.

Still PC-shaped and left alone on purpose: the `server` module (a standalone BattleNet/uberserver; nothing depends on
it) and the desktop branches inside `core` (`GpuMemory.desktopDefaultTextureSize`, the desktop variant of
`LauncherBoard`, `MouseTouchBridge`). They are runtime branches, not a separate build.

## 2. The game data folder is fixed

The game files are read from

```
Android/data/com.etheller.warsmash.android/files/data
```

`android/src/.../GameData.java` writes `warsmash.ini` beside it (in `files/`) from what the folder holds, so there is
nothing to choose and no storage permission to grant:

| What is in `files/data` | `[DataSources]` written |
|---|---|
| `War3.mpq` + `War3x.mpq` + `War3Local.mpq` + `War3xLocal.mpq` (1.29 and earlier) | `MPQ` per archive in load order, then `Maps`, then the engine's folders |
| `Data/` + `.build.info` (1.31 and newer) | `CASC` with the prefixes `DataFolderCheck` finds |
| extracted files (`units/`, `ui/`) | `Folder`, plus the locale layer for a `_locales` install |

The later data source wins in `CompoundDataSource`, so the order is deliberate: base archives first, the locale
archives after them, the engine's own `resources/` and `engine/` folders last. The engine's files therefore still
override the game's.

A folder that holds nothing the engine can read leaves the configuration unwritten: the app then says where the data
goes and closes, instead of failing deep inside the engine start.

### Filling the folder

The game files are not in the repository (`/wc3/` is ignored). `android/build.gradle` copies a local copy to a
connected device:

```bash
./gradlew :android:installWithGameData   # installDebug, then copy the game files
./gradlew :android:pushGameData          # copy only
```

The source is `wc3/Files_1.29.2/Warcraft III`; `-PgameDataDir=<folder>` points at another copy. The shell needs
`JAVA_HOME` and `ANDROID_HOME`; Android Studio sets them for its own builds only.

The copy goes over whatever `adb` is connected to, which can be a device on another network (`adb connect
<host>:<port>` for wireless debugging). Each top-level entry is pushed on its own and compared against its local size,
and a failed entry is retried, because a long copy over a remote link tends to drop near the end — a single bulk
`adb push` of the whole folder does not survive that. `Movies/` is skipped unless `-PwithMovies` is given: it is
193 MB of cinematic archives, and 1.29's are not what the engine plays.

### What this removed

- `DataFolderActivity` (the folder screen: SAF folder picker, ZIP import, all-files-access prompt, readability walk),
  its 54 strings, the "Game data" long-press shortcut and `res/xml/shortcuts.xml`
- the update prompt that screen hosted (`UpdatePrompt`). A local fork has no releases to be told about; the class is
  still in the tree if it is wanted back, in `LauncherActivity` instead.
- the first-run `SharedPreferences` key `launcher`/`dataFolder`: the configuration file is now the only state

`MANAGE_EXTERNAL_STORAGE` stays in the manifest: the player's own content folder (`[CustomContent]`) can still be
picked outside the app's folder.

## 3. Game data version: 1.29.2 works

Upstream's `README.md` says: *"Older releases that ship MPQ archives (1.29 and earlier) are not supported."* The engine is
built for 1.31 and newer (CASC, `_locales`, the HD/DE graphics sets, the Reforged web UI files), and the 1.29.2 copy
this branch was written against is four MPQ archives plus loose `Maps/`, `Campaigns/` and `Movies/` folders.

In practice, on a Realme RMX3357 (Android 13, arm64) with the 1.29.2 archives:

- the archives open with the engine's own reader (`core/src/mpq/`) and supply the classic data it asks for
  (`units\UnitData.slk`, `Units\UnitUI.slk`, `Doodads\Doodads.slk`, `Units\UnitAbilities.slk`, `scripts\common.j`,
  `Scripts\blizzard.j`, `UI\FrameDef\UI\ConsoleUI.fdf`, …)
- the menu builds, the campaign list opens, and the Night Elf opening mission runs at ~57 FPS with terrain, units,
  the briefing panel and Maiev's dialogue
- nothing is logged as missing by the engine process

**The one crash seen on the way there was ours, not the data's.** `GameData.ensure` wrote the `[DataSources]` section
before unpacking the engine's own resources, and `DataFolderCheck.mpqDataSourcesSection` only lists a folder that
already exists — so the first run's ini named the four archives and `Maps` but not `files/resources` or `files/engine`.
`SmashFrameDef.toc` lives in `resources/UI/FrameDef/`, so `GameUI.loadTOCFile` got a null stream and
`MenuUI.mainSkins` threw an NPE on the GL thread. Two fixes: the resources are unpacked before the section is written,
and the ini is now **compared** against what the folder implies and rewritten when it differs, instead of merely being
tested for existence. That second one matters for every future build: without it an ini left by an earlier build keeps
the game on the wrong sources for good.

What 1.29 data cannot give: the Reforged/HD art and the web UI files, so anything that asks for those will show the
classic fallback or fail to find the file. `Movies/` is the 1.29 cinematic archives and is not part of the default copy.
If a screen ever does need 1.31+ files, put an installed CASC or an extracted 1.31 tree in the same `files/data` folder:
`GameData` writes that configuration too, so nothing else has to change.

## 4. Open work

- **Hero mode** — playing as one hero with the army following, instead of the RTS console. This is the gameplay the
  fork exists for; its design, its open decisions and where it lands in the code are in `docs/hero-mode.md`.
- **A build that carries its own game data.** `GameData` is the only place that knows where the data is, so the
  remaining step is to ship the game folder as APK assets behind a Gradle flag and copy it into `files/data` on first
  start. `installWithGameData`/`pushGameData` are then only needed for iteration.
- Upstream's README still describes Windows builds and the folder screen; it is left untouched until this branch has
  a README of its own.
