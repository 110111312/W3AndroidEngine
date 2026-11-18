# Working in this repository

This is a fork of [nikoltu/W3AndroidEngine](https://github.com/nikoltu/W3AndroidEngine) whose `mobile` branch is
Android-only and reads the game data from a fixed folder. `docs/mobile-fork.md` says why; this file says how work is
done here. Upstream's `README.md` still describes the original Windows + Android engine.

## Branches

| branch | rule |
|---|---|
| `main` | mirror of upstream. Never commit to it; only `git fetch upstream && git merge --ff-only upstream/main`. |
| `mobile` | integration branch; the APK builds from it. Direct commits only for changes with no issue (typos, docs). |
| `issue/<number>-<slug>` | one issue per branch, opened as a PR into `mobile`. |

## One worktree per issue

A checkout has **one HEAD**, and HEAD belongs to the directory, not to the session. Two agents working in
`C:/W/W3AndroidEngine` share that HEAD: whoever runs `git switch` last moves the directory under both of them, and the
other agent's next commit lands on whatever branch that happens to be. This has already happened here — a docs commit
went onto another agent's issue branch, and the follow-up `git push origin mobile` reported success while pushing
nothing, because `mobile` was not where the commit was.

So give each issue its own directory:

```bash
git worktree add ../w3-issue-12 -b issue/12-slug mobile   # new branch from mobile
# or, for a branch that already exists:
git worktree add ../w3-issue-12 issue/12-slug
```

- the main checkout stays on `mobile`, for docs, merges and the APK;
- git **refuses** to check out the same branch in two worktrees, so a collision becomes an error instead of a silent
  commit on the wrong branch;
- `git worktree list` says which directory holds which branch — check it before committing when more than one agent
  is about;
- after the merge: `git worktree remove ../w3-issue-12` and `git branch -d issue/12-slug`.

A fresh worktree is a full copy of the tracked files with no `build/` output, so its first `:core:test` is slow; `wc3/`
is ignored and therefore absent, so pass `-PgameDataDir` pointing at the main checkout's copy when a task needs game
data.

## The loop

1. **Read the issue.** `gh issue view <n>`. The issue is the spec and must stand on its own — do not go looking for
   the reasoning in a doc or in someone's memory of a chat.
2. **Branch.** `git switch mobile && git pull && git switch -c issue/<n>-<slug>`
3. **Work.** One issue, one branch. If the fix turns out to need a second, unrelated change, that is a second issue.
4. **Prove what can be proved without a phone** (commands below). This is the part that is cheap; use it.
5. **Commit and open the PR.** `gh pr create --base mobile`. The body says what changed, how it was verified, and — if
   a phone is needed — exactly what to tap and what should be seen.
6. **Hand over.** Add `needs-device` and stop. Do not claim a visual fix you could not see.
7. **After the merge:** `git switch mobile && git pull`, delete the branch.

## Labels are the state

| label | holder | meaning |
|---|---|---|
| `agent-ready` | nobody yet | the design is settled, an agent can start cold |
| `in-progress` | agent | branch exists, work happening |
| `needs-device` | the player | agent's part is done, only a phone can confirm |
| closed | — | merged into `mobile` |

`agent-ready` means *settled*, not *interesting*. If an issue still needs a decision ("radial menu or a bar?"), it is a
normal issue; an agent handed an unsettled design produces a confident diff that gets thrown away.

An issue is ready when it has: the exact repro (device, data, map, steps), file:line anchors for what is known, the
acceptance criteria, the command that proves it, and what is out of scope.

## Stop and ask instead of guessing

- the design is not settled;
- verification needs the phone — hand over with steps instead;
- the change would alter upstream behaviour beyond the issue, or add a desktop code path.

## Prove it without a phone

This machine needs both variables; Android Studio sets them only for its own builds:

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
export ANDROID_HOME="$HOME/AppData/Local/Android/Sdk"
```

| command | what it proves |
|---|---|
| `./gradlew :core:test` | engine logic; must stay green. Add a test when the change is pure logic — `core/test/.../launcher/MpqDataSourcesTest` is the model. |
| `./gradlew :android:assembleDebug` | the APK builds (about 14 MB) |
| `aapt2 dump badging android/build/outputs/apk/debug/*.apk` | the launcher activity and permissions |

`DataFolderCheck` is the only place that decides which data sources the engine is given, and its `[DataSources]`
output is covered by `MpqDataSourcesTest` — check there before changing how game data is found.

## Prove it on the phone

The device is reached over Tailscale, so latency is high and copies are slow and lossy.

- `adb connect <ip>:<port>` — the port changes every time wireless debugging is re-enabled; ask for the current one
  rather than reusing an old one.
- device: Realme RMX3357, Android 13, arm64. The game data (1.29.2, MPQ archives) is already in
  `/sdcard/Android/data/com.etheller.warsmash.android/files/data` — do not push it again.
- `files/warsmash.ini` is written by `GameData.ensure()` and compared against the folder on every start; delete it to
  force a rewrite.
- screenshot: `adb exec-out screencap -p > shot.png`
- crash: `adb shell logcat -d -b crash`
- engine log only: `adb shell logcat -d --pid=$(adb shell pidof com.etheller.warsmash.android)`
- if data does have to be copied, use `./gradlew :android:pushGameData` (per entry, size-verified, retried). Getting
  the files onto the phone another way — unpacking a zip on the device with `/system/bin/unzip` — beats pushing over
  this link by orders of magnitude.

## Never

- commit `wc3/` — it is the 1.4 GB game data and is ignored;
- add desktop, LWJGL or Windows code back;
- make the player choose a folder for the game data;
- report a fix as verified when it was only built.

## Upstream

`upstream-candidate` issues are the ones worth sending back: cut the branch from `main`, keep the diff minimal and free
of personal-build changes, and open it against `nikoltu/W3AndroidEngine`. Upstream declares 1.29 and earlier data
unsupported, so only report defects that reproduce without it — otherwise the answer is "your data", and the report is
wasted.

## Commits

Imperative subject, no trailing full stop, `(#12)` when there is an issue. The body explains why: what broke, what the
fix relies on, what was ruled out. `git log --oneline mobile` is the style to match.
