# Locks Continue Difficulty and Extension System Design Guide

[简体中文](difficulty-system-guide.md) | English · [Documentation](README.md)

## 1. Purpose

This document defines lock difficulty, the four lockpicking minigames, lockpick attributes, lock affixes, loot-table mappings, and server validation requirements for Locks Continue.

The initial phase prioritizes a difficulty system driven by native data packs. KubeJS compatibility is outside this phase, but the data structures should allow it to be added later.

## 2. Core Design Principles

1. Every lockpick quality can attempt every lock. There is no hard quality requirement.
2. Difficulty primarily changes a minigame's structure and required actions, rather than merely increasing durability costs or reducing allowed mistakes.
3. Each of the four lock types uses parameters suited to its own gameplay. A single generic multiplier must not control all minigames.
4. The server generates and saves the puzzle seed. The client handles presentation, input, and animation.
5. The server validates position, lock state, tools, sessions, and critical game results.
6. Reopening the same lock must not reroll an easier puzzle unless the lock instance is regenerated or explicitly reset.
7. Data-pack authors can adjust difficulty, loot rules, tool attributes, and affixes without changing Java code.

## 3. Core Data Model

Each generated lock instance must save at least:

```text
containerPos       Container position
lockType           Lock type
difficultyId       Difficulty definition ID
puzzleSeed         Puzzle seed
affixes            List of lock affixes
generatedFrom      Source loot table, optional
unlocked           Whether the lock has been opened
```

Each lockpicking session saves:

```text
sessionId          Unique session ID
playerId           Player ID
containerPos       Target container position
lockSnapshot       Lock parameters for this session
toolSnapshot       Starting tool and its attributes
puzzleState        Server-side puzzle state
mistakesLeft       Remaining valid mistakes
durabilitySpent    Durability consumed
completed          Whether the session is complete
expiresAt          Session expiration time
```

Resolving difficulty and affixes must produce an immutable `LockChallengeSnapshot`. Once a game starts, reloading data packs must not change its active puzzle.

## 4. Five Default Difficulty Levels

The following values are initial balancing recommendations, not fixed constants that cannot be changed:

| Difficulty | Pin lock | Memory lock | Rotary sweet spot | Pipe lock |
| --- | --- | --- | --- | --- |
| Simple | 4 pins | 4×4 | ±12° | 5×5, 1 target terminal |
| Normal | 5 pins | 4×4, shorter preview | ±9° | 5×5, 2 target terminals |
| Hard | 6 pins | 6×6 | ±7° | 6×6, 3 target terminals |
| Expert | 7 pins | 6×8 | ±5° | 7×7, 4 target terminals |
| Master | 8 pins | 8×8 | ±3° | 8×8, 5 target terminals |

Difficulty IDs are separate from display names. For example:

```text
lockscontinue:simple
lockscontinue:normal
lockscontinue:hard
lockscontinue:expert
lockscontinue:master
```

## 5. Pin Lock Requirements

### 5.1 Gameplay

- A/D moves the lockpick horizontally.
- W lifts the pin currently above the pick.
- The player must set the pin during the correct window near its highest position.
- Setting every pin completes the puzzle.
- On a failed action, difficulty and tool stability determine how many previously set pins drop.

### 5.2 Difficulty Parameters

```json
{
  "pin_count": 7,
  "set_tolerance_ticks": 2,
  "apex_hold_ticks": 1,
  "speed_variance": 1.35,
  "reset_locked_pins": 2
}
```

- `pin_count`: Number of pins; the recommended range is 3–10.
- `set_tolerance_ticks`: Additional timing tolerance before and after the top window.
- `apex_hold_ticks`: How long a pin remains at its highest position. Larger values make the puzzle easier.
- `speed_variance`: Fixed speed differences between pins. With the `variable_speed` affix, each retry rerolls the speed.
- `reset_locked_pins`: Number of set pins that drop after one valid mistake.

### 5.3 Implementation Requirements

- Pin arrays and client slots must not be fixed at five entries.
- The GUI calculates its logical width from `pin_count`.
- With more than five pins, tile or segment the middle of the lock body instead of stretching the entire texture.
- The server stores the deterministic motion profile and judgement window for each lift.
- Invalid or expired packets must not count as gameplay mistakes or consume tool durability.

## 6. Memory Lock Requirements

### 6.1 Gameplay

- Show all cards briefly at the start.
- Turn all cards face down when the preview ends.
- The player reveals two cards at a time.
- A matching pair stays face up. A mismatched pair remains visible briefly before being covered again.
- Matching every pair completes the puzzle.

### 6.2 Difficulty Parameters

```json
{
  "rows": 6,
  "columns": 8,
  "preview_ticks_per_pair": 4,
  "mismatch_limit": 5,
  "hint_count": 1
}
```

- `rows`, `columns`: Board dimensions. Their product must be even.
- `preview_ticks_per_pair`: Adds preview time for each pair, so larger boards are not given disproportionately short previews.
- `mismatch_limit`: Allowed mismatches.
- `hint_count`: Available hints.

Recommended total preview time:

```text
previewTicks = base time + pair count × preview_ticks_per_pair
```

### 6.3 Implementation Requirements

- The server generates a dynamic card array from the board dimensions; it must not be fixed at 16 cards.
- The client lays out cards using the row and column counts. Item icons must remain recognizable and clickable on an 8×8 board.
- Selecting a second card sends both indices to the server for validation.
- Pressing `H` consumes one hint and reveals one unfinished pair for 60 ticks. The status bar shows remaining hints and the hint countdown.
- The server returns `MATCHED`, `MISMATCH`, or `REJECTED`.
- Only `MISMATCH` can consume an allowed mistake. `REJECTED` indicates invalid input or a state mismatch.
- The random item pool must exclude air, technical items, items without normal models, and other unsuitable icons.
- A similar-icon pool may be added later as an affix. It must not be the default behavior for every difficult lock.

## 7. Rotary Lock Requirements

### 7.1 Gameplay

- The pick follows the mouse angle within the permitted arc.
- The screwdriver follows only the lock core's rotation, not the pick angle.
- The closer the pick is to the hidden target angle, the farther the core can turn.
- The core can reach the fully open position only while the pick is in the sweet spot.
- Releasing the action lets the core spring back smoothly.

### 7.2 Difficulty Parameters

```json
{
  "sweet_spot_degrees": 5.0,
  "feedback_falloff": 1.5
}
```

- `sweet_spot_degrees`: Angular tolerance around the correct angle; the rotary lock's main difficulty parameter.
- `feedback_falloff`: How quickly the permitted rotation decreases away from the target angle.

Base difficulty does not need a complex pressure system. Damage from forced turns, jamming, and high friction are better suited to lock affixes.

### 7.3 Implementation Requirements

- The target angle must lie within the client's reachable arc.
- The server validates the final attempted angle and session state.
- Ordinary mouse movement must not send high-frequency packets. Send packets only for actual attempts or necessary synchronization.
- Tool precision may slightly widen the sweet spot, but cannot bypass the puzzle.

## 8. Pipe Lock Requirements

### 8.1 Why the Previous Approach Needs Replacing

A short path from one start to one endpoint requires little planning. Random pipes unrelated to the target add visual noise without meaningful complexity. A cross pipe does not change shape when rotated and therefore cannot count toward required solution rotations.

### 8.2 Gameplay Goals

The pipe lock is a route puzzle from a start to target endpoints:

1. The start is fixed and cannot rotate.
2. Generate one or more fixed target endpoints according to difficulty.
3. The start must connect to every target endpoint simultaneously.
4. Ordinary branches and unrelated pipes do not affect the success condition.
5. Replace single-ended leaves that are neither starts nor targets with rotatable straight, corner, or T-shaped decoy pipes instead of leaving many empty cells.
6. Connecting the start to all target endpoints completes the puzzle.

`require_all_connected` and `forbid_leaks` remain available for a strict network mode in data packs. Built-in difficulties disable them so ordinary branches do not affect the route puzzle.

### 8.3 Generation Algorithm

1. Determine board size, filled-cell count, and target-terminal count from difficulty.
2. Generate a connected tree using randomized depth-first search or randomized Prim's algorithm.
3. Choose a boundary leaf as the start. Select targets using actual path distance along the tree, not Manhattan distance.
4. Choose endpoints, straight pipes, corners, T-junctions, and cross pipes from node degrees.
5. Replace non-start, non-target single-ended leaves with rotatable decoys. Higher difficulties use more T-shaped decoys.
6. Scramble the required routes toward the configured rotation range, then randomly rotate decoys outside those routes.
7. For one target, use exact minimum-cost search. For multiple targets, estimate rotations for the shared generated-tree solution and use each target's exact shortest route as a lower bound. Regenerate candidates outside the required range.
8. Verify that the initial board is not already solved.

Add a single-ended pipe type:

```text
END_CAP
```

### 8.4 Difficulty Parameters

```json
{
  "width": 7,
  "height": 7,
  "terminal_count": 4,
  "minimum_solution_rotations": 18,
  "maximum_solution_rotations": 28,
  "minimum_route_cells": 24,
  "minimum_filled_cells": 47,
  "minimum_junctions": 10,
  "minimum_dead_ends": 0,
  "require_all_connected": false,
  "forbid_leaks": false,
  "hint_count": 1,
  "time_limit_ticks": 1200
}
```

`time_limit_ticks` is the pipe game's independent time limit. It defaults to `1200` ticks (60 seconds), accepts `20–7200`, and accepts `-1` to disable the difficulty's limit. Timing starts with the first valid rotation. Closing the GUI or disconnecting does not reset the start time. The server checks every tick; reaching the limit fails the attempt and applies the configured failure durability cost only once.

If the reward rule uses `time_ticks` and a positive `failure_at`, the shorter enabled limit applies. Reward `failure_at: -1` disables only the reward rule's own time limit; the difficulty limit still applies. Both must be `-1` for unlimited time. The GUI uses the server's actual remaining time, synchronized separately from the reward grading metric.

Complexity primarily comes from:

- Board dimensions.
- Number of targets that must connect simultaneously.
- Minimum filled cells and density of rotatable decoys.
- Route length from the start to targets.
- Number of T-junctions.
- Distracting arrangements of empty cells and ordinary branches.
- Actual generated-tree distance from the start to targets.
- Exact minimum rotation range for one target, or estimated rotation range for a shared solution with multiple targets.
- Strict network mode, if enabled by a data-pack override.
- Hints activated with `H`: each consumes one hint and lasts 60 ticks. The highlighted error comes from a real correction toward a valid solution for the current board, not a guess based on the first leak.

Minimum rotation calculations must respect rotational symmetry:

- End caps, corners, and T-junctions have four orientations.
- Straight pipes have only two distinct orientations.
- Cross pipes have one state and add no rotation complexity.

Default pipe ranges for the five built-in difficulties:

| Difficulty | Board | Targets | Minimum filled cells | Minimum route cells | Estimated rotations | Minimum junctions |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Simple | 5×5 | 1 | 20 | 7 | 2–5 | 1 |
| Normal | 5×5 | 2 | 23 | 11 | 5–9 | 3 |
| Hard | 6×6 | 3 | 34 | 17 | 11–17 | 6 |
| Expert | 7×7 | 4 | 47 | 24 | 18–28 | 10 |
| Master | 8×8 | 5 | 62 | 32 | 28–42 | 14 |

Older data packs that omit `maximum_solution_rotations` still have no explicit upper bound. The generator targets a range near the lower bound instead of scrambling the board to an unnecessarily extreme difficulty. Affixes can adjust both rotation bounds, `minimum_route_cells`, and `minimum_filled_cells`.

Single-target boards use exact minimum rotations. Multiple-target boards use estimated rotations for the shared generated-tree solution, with exact shortest-route calculations for individual targets as a lower bound. The hardest individual target route must require at least roughly one third of the estimated lower bound. This avoids exponential whole-board enumeration on dense boards while preventing decoy shortcuts that bypass the main route in only a few rotations.

## 9. Lockpick Quality

Any lockpick quality can attempt any lock difficulty. Quality supplies attributes rather than restricting lock types.

Suggested attributes:

```text
durability    Total durability
precision     Timing and angular precision
stability     Stability
insight       Puzzle assistance
speed         Client animation and response speed
```

Attribute effects:

| Attribute | Pin lock | Memory lock | Rotary lock | Pipe lock |
| --- | --- | --- | --- | --- |
| precision | Widen the top timing window | No direct effect | Slightly widen the sweet spot | No direct effect |
| stability | Reduce pins reset after failure | No direct effect | Reduce jamming pressure | No direct effect |
| insight | Show a speed hint | Temporarily reveal one pair | Give directional heat feedback | Mark a leak |
| speed | Improve movement response | Speed up card flips | Speed up spring-back animation | Speed up rotation animation |

Tool attributes provide assistance only. The client cannot use them to declare success.

## 10. Lock Affixes

Apply affixes after resolving base difficulty parameters, and save them in the lock instance. Suggested initial affixes:

| Affix | Effect |
| --- | --- |
| Multi-pin | Add one or two pins |
| Tight Spring | Shorten the top timing window |
| Fogged | Shorten the memory preview |
| Extra Lines | Add pipe target terminals and junctions |
| Rusted | Increase durability costs for mistakes |
| Jammed | Make rotary feedback fall off faster |
| Reinforced | Increase tool durability consumed by valid mistakes |
| Unstable | Reset more progress after failure |

An affix must not change only client presentation without entering the server snapshot. Otherwise, client and server puzzle states will diverge.

## 11. Loot-Table and Difficulty Mappings

When a generated container receives its loot table, select lock difficulty, type, and affixes using that loot-table ID.

Recommended rule fields:

```json
{
  "priority": 100,
  "loot_tables": [
    "minecraft:chests/ancient_city"
  ],
  "difficulty_weights": {
    "lockscontinue:expert": 70,
    "lockscontinue:master": 30
  },
  "lock_type_weights": {
    "lockscontinue:pin_lock": 25,
    "lockscontinue:pipe_lock": 25,
    "lockscontinue:rotary_lock": 25,
    "lockscontinue:memory_lock": 25
  },
  "affix_chance": 0.65,
  "maximum_affixes": 2
}
```

Rule matching order:

1. Explicit lock data on the container.
2. The highest-priority exact loot-table rule.
3. Tag, namespace, or wildcard rules.
4. The default rule.

Generate lock data only once per container. Reloading a chunk must not reroll its difficulty or affixes.

## 12. Networking and Server Validation

The server must at least verify that:

- The player remains near the target container.
- The target chunk is loaded.
- The container still exists and is locked.
- The player still holds the lockpick allowed when the session began.
- The session has not expired or completed.
- The action type applies to the current lock.
- The action sequence and puzzle state are current.
- Success is derived from the server's puzzle state.

Recommended unified action results:

```text
UPDATED     Valid action; state changed
PROGRESS    Valid action with progress
MISTAKE     Valid action that failed a gameplay check; may consume durability
SOLVED      Puzzle completed
REJECTED    Invalid, expired, or out of sync; no durability cost
```

Only `MISTAKE` can consume attempts or durability. Network errors, duplicate packets, and expired packets must return `REJECTED`.

## 13. Recommended Data-Pack Directories

```text
data/lockscontinue/lockscontinue/difficulties/*.json
data/lockscontinue/lockscontinue/lockpicks/*.json
data/lockscontinue/lockscontinue/affixes/*.json
data/lockscontinue/lockscontinue/loot_lock_rules/*.json
```

Loading definitions requires:

- Range validation.
- Defaults for missing fields.
- Reporting invalid resource IDs.
- Defined override behavior for duplicate IDs.
- Data-pack reload logging.
- Sending the complete session snapshot from server to client.

## 14. Implementation Order

1. Separate `MISTAKE` from `REJECTED`.
2. Extend difficulty definitions, snapshots, and network serialization.
3. Make pin counts and pin-lock GUI layouts dynamic.
4. Make memory boards dynamic and validate each pair on the server.
5. Rewrite pipe generation and completion checks using complete network rules.
6. Simplify base rotary difficulty to sweet spots and feedback curves.
7. Connect tool attributes.
8. Connect lock affixes.
9. Rebalance the five default levels.
10. Finish data-pack documentation before considering a KubeJS interface.

## 15. Acceptance Criteria

### General

- The same seed and snapshot generate identical puzzles on client and server.
- Invalid network actions do not consume tools.
- Reopening a lock does not refresh its puzzle.
- Invalid data-pack definitions do not crash the server.

### Pin Locks

- All counts from 3 to 10 display and operate correctly.
- The server can validate every pin independently.
- Failure resets the number of pins specified by the snapshot.

### Memory Locks

- 4×4, 6×6, 6×8, and 8×8 boards can all be completed.
- The server validates each match.
- Players cannot reveal three cards at once or reuse matched cards.

### Rotary Locks

- Every target angle is reachable by the client.
- The five sweet-spot widths produce distinct but fair difficulty levels.
- Pick and screwdriver transforms do not affect each other.

### Pipe Locks

- Every seed generates a solvable puzzle.
- Initial boards are always unsolved.
- Single-target boards meet the configured exact minimum-rotation range; multiple-target boards meet the estimated shared-solution range.
- Generated routes from the start to targets contain at least `minimum_route_cells`.
- Boards contain at least `minimum_filled_cells` nonempty cells.
- All targets can connect simultaneously using one set of pipe orientations.
- Every target terminal must connect.
- With `forbid_leaks` enabled, any leak prevents completion.
- Cross pipes do not count toward rotation complexity.

## 16. Outside the Initial Scope

- A KubeJS API.
- Client-defined or client-validated success conditions.
- Requiring a particular tool quality for a particular lock.
- Replacing puzzle difficulty with purely numerical durability increases.
- Generating unsolvable or guess-dependent puzzles to increase difficulty.
