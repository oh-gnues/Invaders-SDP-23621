# Endless Mode integration review — updated 2026-10-25

## Current version

The owner's `d251a9c` implementation is integrated with course main `4a1f6b5` (including the re-applied A1 #192, VFX and music changes). KFC balance PR #28 remains separate. This review does not change campaign, shared item, shop, engine or enemy sources; TitleScreen contains the optional entry hook.

## Validation and evidence

- Production/test compilation and all 263 checks passed against the integrated tree: 63 connected-system, 48 run, 110 reward/item, 19 UI/input and 23 packaged-entry/password checks.
- The class-based entry works from a JAR without a source checkout. Empty/wrong QA passwords are rejected; `000` enters an isolated test session.
- The team leader relayed that the Endless owner playtested `d251a9c` successfully. No detailed checkpoint-by-checkpoint report was provided; this is not a sign-off on the new integrated tree.
- The integration is text-conflict-free, but real-window checking with the latest A1 boss patterns and VFX is still required. Passing tests do not establish complete compatibility with every future shared-system feature.

## Remaining release checks

1. Confirm the implemented wave-based rules and password QA policy described in `teams/KFC.md`; they are explicitly distinguished from the initial requirements.
2. Play the integrated commit: reward selection/reroll at waves 3 and 6, bosses at waves 5 and 10 (including warning visibility), pause/resume, connected screens, death/retry and returning to the menu.
3. Check QA does not alter normal saves. Separately verify normal-run coin saving, cash-out and death forfeiture with a backed-up disposable profile; QA cannot validate real payouts.
4. Check audio using the actual assets available in the tested build; wiring an audio call does not guarantee an audible sound.

## Running

Use JDK 17 or later. On Windows, `launch-game.cmd` recompiles and starts the game. On other systems:

```sh
mkdir -p bin/endless-game
javac -encoding UTF-8 -d bin/endless-game $(find src -name '*.java')
java -cp bin/endless-game:res engine.Core
```

Main menu: E -> small lock -> 000 for QA. Move with A/D or arrows, shoot with Space, pause with P or Escape. While paused, Q cashes out/ends the run (QA awards no shared currency); H opens Endless records. Follow the on-screen controls for other connected screens.

Automated checks on Windows: `powershell -ExecutionPolicy Bypass -File tests/endless/verify-connected.ps1`. The script isolates saves and exercises packaged entry.

## Final upstream sync (October 25)

Integrated course main 4a1f6b5 after A1 #192. Endless now preserves aimed-bullet horizontal velocity, passes the player target, draws native warnings below its HUD, shifts rain spawn positions to its play area and preserves warning timers across pause. Purchased A1 ship looks are read for both normal and isolated QA entry. No shared campaign sources are overridden.
