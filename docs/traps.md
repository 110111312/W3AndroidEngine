# Traps

The mistakes that have cost a build, a device round, or a merge in this repository. Each one is written as the symptom
that shows up, then the rule that keeps it from happening again. This file is for anyone — human or agent — touching
the touch layer, the melee UI or hero mode.

## Coordinates

**A ray cast from a touch goes to the wrong place, a mirrored aim, "the circle is off-screen".** The engine's ray
helpers (`getClickLocation`, `getClickLocationOnZPlane`, `rayPickUnit`, `pickNearestTree`) take **y counted up from the
bottom of the canvas**, while pointer events count y **down from the top of the window**. Every caller flips it:
`worldScreenY = canvas.getHeight() - screenY` (`MeleeUIInput:1445`). A ray cast without the flip is not a small error —
a thumb at the bottom of the screen becomes a finger at the top of the world, so the aim lands on the far side of the
map and the cast goes with it.

**UI world units are not screen pixels.** The melee UI lives in an `ExtendViewport` holding the largest 4:3 box that
fits the screen (`WarsmashGdxMapScreen:216`), so the world is wider than the box and the interface's bottom-right
corner is **not** `(screenWidth, screenHeight)`. Convert screen points with `uiViewport.unproject`, and take the corner
from `MeleeUIMinimapPanel.heroInterfaceLeft()/heroInterfaceTop()` (`MeleeUI` computes the same pair). Hard-coding an
edge is how issue 6 and issue 7 each needed a rebuild.

**dp is not px.** Every size a thumb has to hit is in dp and scaled by `Gdx.graphics.getDensity()` (3 on a 480 dpi
phone). A layout test that only checks the maths against itself proves nothing: the original hero tray test passed while
the whole cluster was mirrored past the right edge. Assert the property the player can see — for the tray,
`HeroTrayLogicTest.noButtonIsEverCutByTheScreenEdge` asserts every circle is inside the interface at densities 1,
2.625, 3 and 3.5.

**`-cos` where `+cos` belongs mirrors a whole cluster.** Angles for a bottom-right cluster are between 90 and 180
degrees, so `cos` is negative: a point on a ring is `right + cos(deg) * ring`, never `right - cos(...)`.

## The touch layer

**A new `GestureListener` method silently does nothing.** The recognizer's listener is not the screen: it is
`DoubleTapMapper`, which forwards what it understands to the screen. A method added to `GestureListener` with a default
body (that is how the second-pointer channel was added) is answered by the **default**, not by your screen, until the
mapper forwards it. The symptom is a feature that never runs and a gesture that quietly turns into a pan instead. Add
the pass-through **and** a mapper test (`DoubleTapMapperTest.aSecondFingerReachesTheScreenInsteadOfStartingAPan`).

**A second finger is a camera pan unless something claims it.** `GestureRecognizer` only gives a finger to the listener
if `secondaryDown` returns true, otherwise it falls into the two-finger path and cancels what was running. A feature
that wants a second thumb has to claim it in `HeroTouchHost.beginSkill` and be honest about returning false.

**A tap is a press and a lift with nothing between them.** Reading "the finger lifted while over the button it started
on" as *drag back to cancel* cancels every tap made by a second thumb, because a tap has no drag to distinguish it.
Record the press point and compare with `TouchSettings.tapSlopPx`.

**Two methods that look like one.** `TouchTarget.heroTouch()` (what `RtsTouchAdapter` asks) and
`MeleeUI.getHeroTouch()` (what the console asks) are different methods; the screen implements the first by calling the
second. A stub on the wrong one gives a hero mode whose taps work and whose drags do not.

## Abilities and orders

**A move order interrupts a spell, and a held stick re-issues one every few frames.** Anything that starts a cast while
the player is walking has to keep the stick off for the cast's moment (`HeroCastHold` in hero mode), or the cast is
cancelled before it fires — including instant skills, because a cast with an animation is still a cast. The length is a
table: nothing in the ability data describes it (a unit's cast point is only a multiplier on a model's spell sequence).

**`CUnit.getCurrentOrder()` is the last order *started*, not the one running.** It cannot tell you a cast has
finished; it is `null` only after an explicit reset or death. Do not build "wait until the cast ends" on it — measure
the hold instead.

**`99999` is the data saying "as far as it likes"**, not a distance. Blink's cast range is 99999; used as a scale it
aims ten thousand units away, and used as a clamp it never bites. Treat a declared range at or above 5000, or none,
as "no reach of its own" and substitute a sane default (`HeroAim.scaleRange`).

**Many rawcodes are not implemented.** `AHfs` Flame Strike, `AOsh` Shockwave, `ACcw` Crushing Wave and `AUim` Impale
fall through to `CAbilityGenericDoNothing`; `AHab`, `AOcl`, `AHca` and `Acoi` are commented out in
`CAbilityData.registerCodes`. Check the registry before promising a spell.

**Shadow Strike is not a class.** `AEsh` is ability-builder JSON (`core/assets/abilityBehaviors/nightElfHeroUnitActives.json`)
turned into `CAbilityAbilityBuilderActiveUnitTarget`. Grepping for `CAbilityShadowStrike` finds nothing, and a cast-type
table that only knows the four spell base classes misclassifies every ability-builder skill.

**Item order ids are arithmetic.** A usable item's order is `OrderIds.itemUseFirst(nine) + slot`, where `nine` comes
from `GameData.isUseNineItemInventory()` — the ini key is normally absent, so the six-slot layout is what runs.

## The build

**A new static table fails `StaticStateTest`.** Any static mutable field, collection or array has to be listed in its
allow-lists; that test exists to stop shared state creeping in, so name the table rather than removing the check.

**`Log.info` is invisible on the phone.** It writes to `System.out`, which the Android launcher does not route to
logcat, and the phone's own logcat is drowned by system chatter — a whole play session is evicted from the buffer in
seconds. For diagnostics, write a line to a file in the app's own folder (`Gdx.files.local("heroaim.txt")`) and read it
with `adb shell run-as com.etheller.warsmash.android cat files/heroaim.txt`. Remove the scaffolding before the merge.

## The device loop

- The wireless-debugging **port changes** whenever it is toggled; ask for the current one instead of reusing it, and
  expect the device to drop during a long command (`adb install` and `adb pull` included).
- **Installing kills the game**: it comes back at the menu, not in the mission.
- **Taps need focus.** If another window holds focus the taps go to it, and the screenshots show a frame the game drew
  earlier. `adb shell dumpsys window | grep mCurrentFocus` before believing anything.
- **Taps cannot test multitouch.** `input tap`/`input swipe` inject one pointer, so "walk and cast at once" has to be
  tested by the player. Design the check so one gesture answers one question.
- **Screenshots are slow** (about ten seconds each over Tailscale, and each one is a large file). Read a diagnostics
  file instead and let the log answer the question.
- The engine pauses in the background, so a tap sent to a backgrounded app does nothing at all — and the screenshot
  afterwards still looks plausible.
