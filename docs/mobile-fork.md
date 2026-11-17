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

### What this removed

- `DataFolderActivity` (the folder screen: SAF folder picker, ZIP import, all-files-access prompt, readability walk),
  its 54 strings, the "Game data" long-press shortcut and `res/xml/shortcuts.xml`
- the update prompt that screen hosted (`UpdatePrompt`). A local fork has no releases to be told about; the class is
  still in the tree if it is wanted back, in `LauncherActivity` instead.
- the first-run `SharedPreferences` key `launcher`/`dataFolder`: the configuration file is now the only state

`MANAGE_EXTERNAL_STORAGE` stays in the manifest: the player's own content folder (`[CustomContent]`) can still be
picked outside the app's folder.

## 3. Game data version: 1.29 is not supported upstream

Upstream's `README.md` says: *"Older releases that ship MPQ archives (1.29 and earlier) are not supported."* The
engine is built for 1.31 and newer (CASC, `_locales`, the HD/DE graphics sets, the Reforged web UI files).

The local copy in `wc3/` is 1.29.2, i.e. four MPQ archives and loose `Maps/`, `Campaigns/`, `Movies/` folders. The
archives do open with the engine's own reader (`core/src/mpq/`) and hold the classic data the engine reads
(`units\UnitData.slk`, `Units\UnitUI.slk`, `Doodads\Doodads.slk`, `Units\UnitAbilities.slk`, `scripts\common.j`,
`Scripts\blizzard.j`, `UI\FrameDef\UI\ConsoleUI.fdf`, …), but files the launcher and the campaign screens ask for were
only added later (`UI\CampaignInfoClassic.txt`, `UI\CampaignInfoReforged.txt`).

So with 1.29.2: **melee and custom maps are expected to work; the campaign list and parts of the launcher art are
expected not to.** This has not been run on a device yet.

If that turns out to be a problem, the fix is to supply 1.31+ data (installed CASC or files extracted with the
`W3-Android-prep-tool` the upstream README links) into the same `files/data` folder. `GameData` writes the CASC
configuration already, so nothing else has to change.

## 4. Open work

- **A build that carries its own game data.** `GameData` is the only place that knows where the data is, so the
  remaining step is to ship the game folder as APK assets behind a Gradle flag and copy it into `files/data` on first
  start. `installWithGameData`/`pushGameData` are then only needed for iteration.
- `PrepPage`'s "Choose another source" button and the options' *Game Data* button now show where the data is read
  from; the first one is a leftover of the folder screen and could be dropped.
- Upstream's README still describes Windows builds and the folder screen; it is left untouched until this branch has
  a README of its own.
