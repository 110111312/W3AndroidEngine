# Hero mode: playing as one hero on a phone

The gameplay this fork exists for. Everything else in the repo is plumbing that makes it possible.

## The idea

Warcraft III on a phone is an RTS, and an RTS is the wrong shape for a phone: squads, control groups, twelve
command buttons, drag-select, right-click-to-move, all of it designed for a mouse and a keyboard and two hands that
are not holding a screen.

So hero mode does not port the RTS. It keeps the world — the campaign, the maps, the units, the spells, the
economy — and changes who the player *is*:

- **the player is the hero.** One unit is under the player's fingers. Moving, aiming a skill, hitting something: that
  is what the left thumb and the right thumb do, nothing else;
- **the army plays itself.** Units follow the hero, fight what threatens them, retreat when they are losing, and do
  not need to be told. A good soldier here is one the player never has to think about;
- **the player still decides the war.** What gets built, what gets trained, where it goes — that stays with the
  player, but as a handful of macro decisions taken now and then, not as continuous micro.

The reference is a mobile MOBA (Mobile Legends / Wild Rift): one hero, a stick, four buttons, and a minimap. Not
StarCraft with a touchscreen.

The test of whether a piece of this is right: *if the player has to look away from the hero to do it, it is either
automatic or it is a macro decision.*

## The screen

Gone: the console along the bottom — the 4×3 command card, the portrait with its info panel, the inventory grid, the
control-group bar. That is the RTS surface, and it is the thing that makes the game unplayable on a phone.

Kept: the world, the minimap (top-left, as now), the resource bar, and enough of a hero panel to read health, mana,
level and cooldowns at a glance.

Added:

| where | what |
|---|---|
| bottom-left | **movement stick.** Drag anywhere in the left zone; the hero walks that way while the thumb is down |
| bottom-right | **skills**, 2 to 4 depending on the hero, in an arc where the thumb rests — plus the **usable equipment** |
| bottom-right, above | **macro panel**, opened deliberately: train, build, and what the army is doing |

The exact arrangement of the skill cluster, the equipment slots and the macro panel is settled later — the shape
above is what the doc is asking to be true, not the final layout.

## The army

Units belong to the player but are not commanded one by one. Their standing orders:

- **follow** the hero at a distance, without crowding into the screen centre;
- **engage** what comes within reach of the hero or of themselves, and disengage back to the hero rather than chasing
  across the map;
- **return** to the hero when the hero moves on, and reform rather than straggle;
- **not die stupidly** — no walking into towers, no standing in the middle of nothing, no chasing a lone unit into a
  base.

Units that carry useful abilities (a healer, a caster) use them in support of the hero rather than at random.

## The macro layer

The player still builds and trains, because an army that also builds itself is a different game. What changes is the
form: a few decisions, taken at a good moment, with an obvious consequence.

- **train**: a short list of what this player can make, one tap each, queued at the building that makes it;
- **build**: the same for structures, with placement either chosen on the map or left to a sensible default;
- **see the army**: how many units, what they are, whether they are alive — without selecting anything.

Whether this is a panel, a radial menu or a row of icons is open.

## Where it lands in the code

The point of this section is that none of it needs a rewrite: the engine already has the seams.

| piece | anchor | note |
|---|---|---|
| the HUD that gets replaced | `core/src/.../w3x/ui/MeleeUI.java:117-265` (state), `:543-568` (build order), `:1015-1145` (the one draw call) | the whole HUD is one `UIFrame` tree under `rootFrame`; hero mode is a second HUD, not a patch to the console |
| the command card to remove | `MeleeUICommands.java:234-303` (4×3 grid), `:139-233` (inventory grid), `MeleeUI.java:134-140` (fixed sizes) | the inventory grid is the closest working example of a second button grid, so the skill tray has a template |
| a tap becoming an order | `MeleeUIInput.java:965-1010` (`rightClickMoveTo`), `MeleeUICommands.java:317-420` (`onClick`) → `CPlayerUnitOrderListener` | every order must go through this seam or replays and LAN break |
| skill buttons | `RenderUnit.java:180-201` (`populateCommandCard`), `CommandCardPopulatingAbilityVisitor.java:118-197`, `CAbilityHero.getLearnedHeroAbilities()` (`:316`) | the list is already there: `unit.getSimulationUnit().getAbilities()` filtered to icon-showing, active ones |
| equipment buttons | `CAbilityInventory.java:43-290` — each held item's abilities are already `CAbility` | a usable item is an ability button with `OrderIds.itemUseFirst + slot`; no new mechanism |
| follow camera | `GameCameraManager.setTargetController` (`:787-800`), `followTargetController` (`:164-205`) | **already exists**; the only UI caller today is press-and-hold on the portrait (`MeleeUIInfoPanel.java:1607-1618`) |
| stick → movement | `core/src/.../input/touch/` — `RtsTouchAdapter`, `GestureRecognizer`, `GestureListener`, `TouchSettings` | **no joystick code exists**; one-finger drag is currently selection box *or* aim, two-finger drag is camera pan, so the stick needs its own zone and a new gesture |
| swapping the HUD | `MeleeToggleUI` (`ui/toggle/`), `WarsmashGdxMapScreen.java:315` | the seam exists: the HUD is a list of `WarsmashToggleableUI` that Alt+Z cycles, and today the list holds one entry, so hero mode is a **second entry**, not a replacement |
| units following the hero | `CBehaviorFollow.java:41-44` + `CAbilityMove.java:203` | **not reusable as a standing order.** It exists and is issued (a move or smart order onto an ally), but its `update` plays STAND and never looks for targets — an army on it would walk with you and never fight. Hero-mode follow is a leash rule beside `CUnitAcquisition` |
| units fighting for themselves | `CUnitAcquisition` (`autoAcquireIdleTargets` `:95`, `autoAcquireTargets` `:57-93`) | this is the per-unit brain, and it is where hero-mode unit behaviour belongs — **not** in the JASS AI |
| train / build | `CAbilityQueue.java:27-41` (train list), `AbstractCAbilityBuild.java` (build list), `MeleeUICommands.java:422-460` (submenu stack) | the macro panel is a different projection of the same lists the command card shows today |
| computer players | `parsers/jass/ai/AIController.java`, `AIProduction`, `AIAttack` | untouched: the *enemy* keeps playing RTS |

Two consequences worth stating before any code:

1. **The hero-mode HUD is a sibling of the console, not a skin on it.** The console is positioned by literal viewport
   fractions and a 4×3 cap; a touch tray wants its own frames and its own hit areas.
2. **The orders still have to be orders.** The stick and the skill buttons must end in
   `CPlayerUnitOrderListener.issue*Order`, exactly like a right-click does, or a LAN game and a replay stop agreeing
   with the screen.

## Open decisions

Not settled, so none of this is an agent task yet:

1. **Is hero mode a mode, or the game?** A switch on the launch screen, a per-map option, or simply the only way this
   fork plays. Campaign missions are the immediate target; melee and LAN are the question.
2. **Does the player ever touch a unit?** Tapping an ally could do nothing, could set "protect this", could select it
   for one order. Doing nothing is the simplest thing that can be right.
3. **What exactly is "follow"?** Radius, leash length, what happens when the hero blinks or dies, whether units reform
   in formation (`CGroupMove` already has slots) or as a blob.
4. **Engagement rules.** Fight only what attacks the hero or the unit, or also what is merely nearby? How far will a
   unit chase before it gives up and comes back?
5. **Where the macro panel lives** and whether building placement is chosen by hand at all.
6. **Skill targeting on a phone.** Point-target and unit-target skills need an aim gesture; drag-to-aim from the
   button is the usual answer, and it has to coexist with the stick and with the existing selection drag.
7. **Campaign heroes specifically**: items come from the map, skills unlock per mission, and some missions are
   designed around controlling several units at once. Which of those break?

## Slices, in the order they would be worth doing

Written down so the work is visible; **not** filed as issues, because the decisions above change them.

1. **Follow camera and hero-only control** — camera locks to the hero, the hero answers a tap-to-move, the console
   hides. Proves the HUD can be swapped without breaking orders.
2. **Movement stick** — a left-zone drag that issues move orders at a fixed cadence, coexisting with selection drag.
3. **Skill tray** — 2–4 ability buttons from the hero's own list, drag-to-aim, cooldown and mana state visible.
4. **Equipment tray** — the inventory's item abilities as the same kind of button.
5. **Units follow** — a leash rule: an idle unit further than X from the hero walks back to Y. **Not** `CBehaviorFollow`,
   which stands still and never looks for a target.
6. **Units fight** — engagement and leash rules in `CUnitAcquisition`, plus support casting.
7. **Macro panel** — train and build from the lists the command card already reads.

Slices 1–4 are UI and can be checked on a phone with a screenshot; 5–6 are simulation and can be tested in
`core/test`; 7 is both.

## Out of scope

- changing how the *enemy* plays — the AI keeps being an RTS opponent;
- removing the console for good: campaign, melee and LAN keep working while hero mode is being built;
- a desktop or Windows path of any kind.
