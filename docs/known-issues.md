# Known issues

Found while playing the 1.29.2 data on a Realme RMX3357 (Android 13, arm64): Night Elf campaign, first mission.
Neither of these is touched by what this branch changed — the data folder, the ini and the desktop removal do not go
near ability rendering or the loading screen — so both are most likely upstream. They are written down here until they
can be filed as issues.

## 1. Fan of Knives shows no visual

**Seen:** Maiev casts Fan of Knives, enemies take damage, nothing is drawn — no knife burst, and no spell animation on
the caster.

**The behaviour is implemented, and it does ask for the effect.** `CAbilityFanOfKnives.doEffect` ends with
`simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER)`
(`core/.../abilities/skills/nightelf/warden/CAbilityFanOfKnives.java:72`), so the damage path and the effect request both
run. What is missing is whatever happens after that request.

**Worth checking, in this order:**

1. whether the effect path resolves a model at all — the ability's `Art` field for `Adef`/`Afan` in 1.29 names a path
   under `Abilities\Spells\NightElf\FanOfKnives\`, and a miss there is silent
2. whether the caster's `spell` sequence plays (a missing sequence is also silent, and would rule the model out)
3. whether *any* particle emitter renders on this device — cast something that is known to have a visible effect
   (Blizzard, Storm Bolt, Thunder Clap) and compare; if none of them show, the problem is the effect renderer rather
   than this ability
4. whether 1.29 spells it differently from the 1.31+ layout the engine was written against

## 2. The loading bar reaches 100 %, drops to ~40–50 %, then fills again

**Seen:** on starting the campaign mission, the bar fills to the end, jumps back to roughly half, and fills a second
time before the map appears.

**There are two progress bars, and they share one screen.** `WarsmashGdxMenuScreen.renderStartup()` fills the bar from
`STARTUP_STEP_WEIGHTS` while it builds the menu one step per frame (`WarsmashGdxMenuScreen.java:149-196`), and once the
menu is up the map load runs its own progress through `MenuUI.previewLoadingScreen`/`setPreviewProgress`. The first
pass finishing and the second starting is what reads as a drop.

**Worth checking:**

1. whether the preview bar should be suppressed once the real load starts, or the menu steps folded into the map load's
   total
2. whether the second pass genuinely starts near 50 % (i.e. it is reporting the load it has already done) or restarts
   from zero and the number seen is a repaint of the first pass
3. whether the same thing happens on a melee map, or only on a campaign mission (campaign missions load the
   campaign-specific data on top)
