# Known issues

The tracker is the source of truth — these live as issues, not here:

| issue | what |
|---|---|
| [#1](https://github.com/110111312/W3AndroidEngine/issues/1) | Fan of Knives deals its damage but draws nothing |
| [#2](https://github.com/110111312/W3AndroidEngine/issues/2) | the loading bar fills to 100 %, drops to about half, and fills again |

`AGENTS.md` says how work moves from an issue to `mobile`. Both of the above were found on a Realme RMX3357
(Android 13, arm64) with the 1.29.2 data, in the Night Elf campaign's first mission, and both are engine-side rather
than a consequence of this branch's changes.

The one defect that *was* ours is recorded in `docs/mobile-fork.md` §3, because it explains a design decision that
outlives it: the `[DataSources]` section is now written after the engine resources are unpacked, and compared against
the folder on every start.
