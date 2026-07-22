# EXHAUSTIVE END-TO-END AUDIT — MEMORY GAME

Audit date: 2026-07-22  
Audit mode: implementation and verification audit; production fixes applied for the four authorized defects.  
Environment: Windows 11 amd64, Eclipse Temurin OpenJDK 25.0.2, Maven 3.9.15.

## Executive summary

**Final determination: REJECTED.** Across the 44 Part A IDs, the updated result is **38 PASS, 1 FAIL, 5 MANUAL-ONLY**. Four previously reported product defects are resolved. The only remaining reportable issue is the intentionally excluded automated-coverage gap: `QR-MAIN-01` still fails because the permanent suite does not contain at least one test covering every functional requirement ID, particularly complete Memory LAN synchronization/out-of-turn/restart/disconnect flows.

Per the clarified mentor requirement, **the current dynamic generation of up to three variants is correct**. There is no requirement for fixed 44/28/14-card variants or for exactly three options at every `k`. The implementation consistently provides three variants for `k=1..15`, two for `k=16..22`, and one for `k=23..45`; every generated board has a card count divisible by `k` and is playable. The former `k=1` multiplicity defect is resolved by the new 45-icon pool.

### Verification performed

- Updated `mvn clean package`: **BUILD SUCCESS**, 328 tests run, 0 failures/errors/skips, 21.351 seconds.
- Permanent regression tests now cover all four repaired behaviors.
- Exhaustive `k = 1..45` sweep: all 82 generated variants passed count, geometry, divisibility, and exact symbol-multiplicity checks.
- 820 completed model rounds: ten rounds on every generated variant, with no crash, incomplete round, or state leakage.
- All 45 supplied Icons8 PNGs loaded successfully through JavaFX at their expected 50×50 dimensions.
- Full 44-card JavaFX UI round followed by ten consecutive Restart actions: passed; each restart reset score to 0:0, turn to Player 1, all cards to `?`, result bar hidden, and generated a new model/board.
- JavaFX render and bounds checks at 1366×768, 1920×1080, and 2560×1440 for both menu and game screens: passed with no clipping or overlap.
- LAN socket audit: ten serialized state updates delivered and validated in 28 ms total on loopback; both host-side and client-side disconnect callbacks fired within 3 seconds.
- Late-join regression: after the first client connected, the listening socket rejected a second connection while the active first connection continued exchanging protocol messages.

# Part A — Full Requirements Coverage

## Constraints

| ID | Authoritative requirement | Result | Evidence |
|---|---|---:|---|
| CON-TECH-01 | **Technology:** The game must be implemented using Java version 21 or newer. | PASS | `pom.xml:11,94-95` targets Java 25; audit runtime was OpenJDK 25.0.2. |
| CON-PLAT-01 | **Platform:** The game must run on Windows, macOS, and Linux desktop systems. | MANUAL-ONLY | JavaFX/Maven code is platform-neutral and Windows passed, but no macOS or Linux desktop was available for an actual launch/game run. |
| CON-NET-01 | **Network Scope:** Multiplayer functionality is restricted to operating within a single LAN subnet using private IPv4 addresses. | PASS | LAN discovery uses IPv4 UDP broadcast addresses (`LanDiscoveryService.java:129-140,265-288`), and the UI joins only discovered hosts (`MemoryMenuController.java:338-377`). |
| CON-PLAY-01 | **Players:** Each round must have exactly two human players participating. | PASS | Local mode exposes exactly Player 1/Player 2 and LAN has one host plus one active client (`MemoryMenu.fxml:214-260`; `GameServer.java:46-57`). The listening socket now closes immediately after the first accept, and a late second-connection regression test passes. |
| CON-CONF-01 | **Config Range:** The configurable match size n must be an integer from 1 to 45, inclusive. | PASS | All 45 values applied and produced at least one playable variant; bounds are enforced at `MemoryMenuController.java:155-165` and `BoardVariant.java:66-73`. |
| CON-INPUT-01 | **Input:** The game must be fully playable using only mouse input. | PASS | Menu, cards, restart, quit, host, refresh, and join are JavaFX buttons/radios/text fields with mouse action handlers (`MemoryMenu.fxml:69-197,264-322`; `GameController.java:378-386`; `GameBoard.fxml:155-164`). |

## Use cases

| ID | Authoritative requirement | Result | Evidence |
|---|---|---:|---|
| UC-01 | **Create or Join Session:** A player must be able to create or join a two-player LAN session. The session is established successfully if one user hosts and the other joins, but joining fails if the host is unreachable. | PASS | Host/join controls and paths are implemented at `MemoryMenuController.java:242-304,338-391`; actual server/client exchange passed. Connection exceptions produce `Connection failed` and return the menu to a usable state. |
| UC-02 | **Configure and Start Game:** The host configures match parameters (match size n and deck size) and starts a synchronized round. Both peers receive the same initial board state, though invalid config values will block the start of the game. | PASS | Applied variant is required before local/host start (`MemoryMenuController.java:214-249`); host sends one serialized initial snapshot (`GameController.java:208-225`) and client applies it (`GameController.java:280-290,313-336`). `0`, `46`, empty, negative, non-numeric, and changed-but-not-applied values are visibly rejected and block start. |
| UC-03 | **Play Synchronized Turn:** The active player executes a synchronized turn (selection and resolution), which updates card states, scores, and turns consistently on both peers. Out-of-turn input must be ignored. | PASS | Host is authoritative, rejects client input unless Player 2 is active (`GameController.java:227-240`), local endpoints reject out-of-turn clicks (`GameController.java:409-420`), and every accepted state transition is broadcast (`GameController.java:449-489,494-497`). Ten actual state messages deserialized successfully in 20 ms. |
| UC-04 | **End Game and Restart:** The round completes when zero face-down cards remain. The system displays the winner or draw, and players can trigger a synchronized restart unless the connection is lost. | PASS | End/winner logic is at `GameModel.java:241-276`; result UI at `GameController.java:666-683`; restart rebuilds and broadcasts a fresh model at `GameController.java:686-745`. Full UI completion plus ten restarts passed. |

## Functional requirements

| ID | Authoritative requirement | Priority | Result | Evidence |
|---|---|---:|---:|---|
| FR-NET-01 | The system shall show Host and Join controls in the main menu, so that players can create or join a session. | High | PASS | `MemoryMenu.fxml:264-298`; rendered menu shows Host LAN Game, Refresh, and disabled Join Selected. |
| FR-GAME-02 | The system shall initialize a new round after Start is activated, with all cards face-down, scores reset to 0:0, and the turn indicator showing Player 1. | High | PASS | `GameController.java:175-179,519-540`; actual 44-card UI showed all `?`, 0:0, and Player 1 turn. Ten restarts repeated the same result. |
| FR-INPUT-01 | The host shall configure n in the range 1 to 45 before game start, so that the match size is defined before any cards are dealt. | High | PASS | All values 1–45 applied and started; validation is `MemoryMenuController.java:140-165,214-249`. |
| FR-INPUT-02 | The host shall configure a deck size that is divisible by n, so that the game can generate complete matching groups. | High | PASS | Every one of the 82 actual generated variants had `totalCards % k == 0`; construction is `totalCards = k * n` (`BoardVariant.java:20-35,71-84`). |
| FR-GAME-03 | The system shall generate a deck where every symbol appears exactly n times, so that every valid match consists of n identical cards. | High | PASS | The pool now contains 45 unique Icons8 face identifiers (`GameModel.java:11-56`), and generation selects one distinct face per group without wrapping (`GameModel.java:89`). All 82 variants passed exact multiplicity checks, including `k=1` Large with 45 unique symbols. |
| FR-NET-02 | The system shall synchronize the initial board and starting turn across peers within one round-trip update, so that both players begin from the same state. | High | PASS | Host sends one START snapshot (`GameController.java:208-225`); client deserializes it atomically (`GameController.java:280-290,313-336`). Snapshot includes dimensions, current player, scores, game-over, and every card (`MemoryStateSnapshot.java:24-35`). |
| FR-GAME-04 | The active player shall open at most n cards in one attempt, so that card n+1 cannot be opened before the attempt is resolved. | High | PASS | `GameModel.java:219-238`; nth selection resolves immediately. Mismatch locks controller input (`GameController.java:478-491`). |
| FR-GAME-05 | The system shall resolve an attempt automatically after the nth card is opened, so that the outcome of the attempt becomes visible. | High | PASS | `GameModel.java:232-260`; model tests at `MemoryGameModelTest.java:278-315,423-440` passed. |
| FR-GAME-06 | The system shall score one point when all n opened cards match, so that matched cards become non-selectable and the current player is rewarded exactly once. | High | PASS | `GameModel.java:241-258`; matched UI disables cards at `GameController.java:563-576`; rapid/already-matched model tests passed. |
| FR-GAME-07 | The system shall keep the turn on a match and switch the turn on a mismatch, so that turn ownership follows the game rules consistently. | High | PASS | Match path does not change player; `closeOpenCards` switches exactly once (`GameModel.java:241-269`); tests `MemoryGameModelTest.java:289-345,603-613` passed. |
| FR-GAME-08 | The system shall close mismatched cards after a mismatch, so that both peers return to the same playable state. | High | PASS | 800 ms pause closes cards and broadcasts the resulting state (`GameController.java:478-497`); model close behavior passed at `MemoryGameModelTest.java:318-332`. |
| FR-NET-03 | The system shall reject player input when it is not that player's turn, so that out-of-turn clicks never alter game state. | High | PASS | UI gates at `GameController.java:409-420`; host independently validates remote FLIP at `GameController.java:227-240`. |
| FR-GAME-10 | The system shall end the game when no face-down cards remain and announce the result, so that the winner or draw is displayed on both peers. | High | PASS | `GameModel.java:251-255`; `GameController.java:666-683`; full UI round displayed post-game state, and snapshots carry `gameOver`. |
| FR-GAME-11 | The system shall determine the outcome from the player scores, so that the player with the higher score wins and equal scores produce a draw. | High | PASS | `GameModel.java:272-278`; player-1 win, player-2 win, and draw tests passed at `MemoryGameModelTest.java:373-419`. |
| FR-GAME-12 | The system shall provide synchronized restart after round end, so that both peers start the next round from the same board. | Medium | PASS | Host creates one fresh model then sends `RESTART_STATE` (`GameController.java:720-745`); client applies the host snapshot (`GameController.java:301-305`). Local UI restart passed ten times. |
| FR-NET-04 | The system shall detect connection loss during a round and inform both players when synchronization is no longer possible. | Medium | PASS | Both host and client install disconnect listeners (`GameController.java:188-196`); handler locks input and shows connection-lost UI (`GameController.java:352-362`). Both socket directions triggered callbacks within 3 seconds. |
| FR-NET-05 | The system shall propagate valid moves from host to client, so that both peers continue the game on the same state. | High | PASS | Every accepted host selection broadcasts state (`GameController.java:449-497`); ten state snapshots propagated and validated in 20 ms. |
| FR-GAME-09 | The system shall ignore input on cards that are already matched or currently open, so that invalid selections never change score, turn, or deck state. | Medium | PASS | `GameModel.java:219-230`; controller pre-checks at `GameController.java:422-445`; repeated/same/matched tests passed at `MemoryGameModelTest.java:245-271`. |

## Quality requirements

| ID | Authoritative requirement | Result | Evidence |
|---|---|---:|---|
| QR-USAB-01 | Valid card selections must be visibly acknowledged on both peers; fit criterion: 10 consecutive valid clicks show the identical card state change on both peers every time. | MANUAL-ONLY | Ten network states propagated correctly, but two simultaneously rendered physical peer windows were not available to prove visible equality click-by-click. |
| QR-USAB-02 | The main game screen must fit on a 1920x1080 display without overlapping controls or clipped text at the default window size. | PASS | JavaFX render/bounds test and visual inspection passed at 1920×1080; also passed 1366×768 and 2560×1440. |
| QR-PERF-01 | The main menu must be visible within 3 seconds of launching the application. | MANUAL-ONLY | FXML rendering was fast, but a cold packaged-application launch with visible-window timestamp was not available. |
| QR-PERF-02 | A new round must be initialized and ready within 1 second after Start is activated. | PASS | Actual 44-card controller initialization measured 0 ms at test-clock resolution and rendered ready. |
| QR-PERF-03 | Valid card selections must be acknowledged on both peers within 1 second. | MANUAL-ONLY | Transport delivered ten full states in 20 ms, but end-to-end visible acknowledgment on two physical windows was not timed. |
| QR-PERF-04 | An attempt must resolve within 250 ms after the final required card is opened. | PASS | `selectCard` resolves synchronously on the nth click (`GameModel.java:219-260`); 820 full rounds completed in 19 ms total model time. |
| QR-PERF-05 | Mismatched cards must turn face-down again within 1000 ms following a mismatch. | PASS | Explicit delay is 800 ms (`GameController.java:61,478-491`). |
| QR-PERF-06 | Restarted rounds must become visible within 1 second of activating Restart. | PASS | Ten consecutive JavaFX restarts rebuilt and refreshed synchronously (`GameController.java:720-736`); no timeout or leak. |
| QR-PERF-07 | Valid moves must propagate from the host to the client within 1 second on a standard LAN. | PASS | Ten actual serialized host-to-client states arrived in 20 ms total on loopback, well inside 1 second. A physical LAN latency recheck remains advisable. |
| QR-PERF-08 | If a connection is lost, a safe state (input disabled and a connection-loss message shown) must be reached within 3 seconds. | PASS | Both disconnect callback directions completed within 3 seconds; `handleDisconnect` immediately sets `inputLocked` and connection-lost text before showing the warning (`GameController.java:352-361`). |
| QR-NET-01 | Both peers must remain in the exact same game state (card states, scores, active player) after every completed turn. | PASS | Host broadcasts complete snapshots after opens, matches, and mismatch closure (`GameController.java:449-497`); snapshot round-trip validation and ten sequential state deliveries passed. |
| QR-NET-02 | Valid moves must successfully propagate from the host to the client. | PASS | Actual host-to-client TCP state propagation test passed ten consecutive messages. |
| QR-RELY-03 | The game must be able to complete 10 consecutive full multiplayer rounds without any crashes, deadlocks, or data loss. | MANUAL-ONLY | 820 local/model rounds and repeated network state transfers passed, but ten full two-window multiplayer rounds were not executed end-to-end. |
| QR-RECV-01 | If the network connection drops, the system must enter a safe error state that disables input and informs both players via a message. | PASS | Disconnect callbacks and safe-state handler passed (`GameController.java:188-196,352-362`). |
| QR-MAIN-01 | The project must build successfully using `mvn clean package` and have automated tests covering the main game rules (at least one test per functional requirement ID). | **FAIL** | Build half passed: 328/328 tests. Coverage half remains intentionally unresolved: permanent tests still do not provide at least one complete test per Memory FR ID, especially end-to-end `FR-NET-02/03/04/05` and synchronized `FR-GAME-12` controller flows. |
| QR-MAIN-02 | The project's README must outline reproducible build and run steps. | PASS | Build and run instructions are present at `README.md:128-159`; Memory play instructions are at `README.md:164-183`. |

## Part A totals

| Category | PASS | FAIL | MANUAL-ONLY | Total |
|---|---:|---:|---:|---:|
| Constraints | 5 | 0 | 1 | 6 |
| Use cases | 4 | 0 | 0 | 4 |
| Functional requirements | 18 | 0 | 0 | 18 |
| Quality requirements | 11 | 1 | 4 | 16 |
| **Total** | **38** | **1** | **5** | **44** |

# Part B — Exhaustive Boundary and Range Sweep

## All `k` values and all generated variants

Notation: `cards@rows×cols / unique symbols / multiplicity`. `OK` means every distinct symbol occurs exactly `k` times. All 82 generated variants had a card count evenly divisible by `k` and a grid capacity large enough for every card.

| k | Large | Medium | Small | Result |
|---:|---|---|---|---|
| 1 | 45@5×9 / 45 / OK | 30@5×6 / 30 / OK | 15@3×5 / 15 / OK | PASS |
| 2 | 44@7×7 / 22 / OK | 28@4×7 / 14 / OK | 14@4×4 / 7 / OK | PASS |
| 3 | 45@5×9 / 15 / OK | 30@5×6 / 10 / OK | 15@3×5 / 5 / OK | PASS |
| 4 | 44@7×7 / 11 / OK | 28@4×7 / 7 / OK | 12@3×4 / 3 / OK | PASS |
| 5 | 45@5×9 / 9 / OK | 30@5×6 / 6 / OK | 15@3×5 / 3 / OK | PASS |
| 6 | 42@6×7 / 7 / OK | 24@4×6 / 4 / OK | 12@3×4 / 2 / OK | PASS |
| 7 | 42@6×7 / 6 / OK | 28@4×7 / 4 / OK | 14@4×4 / 2 / OK | PASS |
| 8 | 40@5×8 / 5 / OK | 24@4×6 / 3 / OK | 8@2×4 / 1 / OK | PASS |
| 9 | 45@5×9 / 5 / OK | 27@5×6 / 3 / OK | 9@3×3 / 1 / OK | PASS |
| 10 | 40@5×8 / 4 / OK | 20@4×5 / 2 / OK | 10@2×5 / 1 / OK | PASS |
| 11 | 44@7×7 / 4 / OK | 22@5×5 / 2 / OK | 11@3×4 / 1 / OK | PASS |
| 12 | 36@6×6 / 3 / OK | 24@4×6 / 2 / OK | 12@3×4 / 1 / OK | PASS |
| 13 | 39@6×7 / 3 / OK | 26@5×6 / 2 / OK | 13@4×4 / 1 / OK | PASS |
| 14 | 42@6×7 / 3 / OK | 28@4×7 / 2 / OK | 14@4×4 / 1 / OK | PASS |
| 15 | 45@5×9 / 3 / OK | 30@5×6 / 2 / OK | 15@3×5 / 1 / OK | PASS |
| 16 | 32@4×8 / 2 / OK | 16@4×4 / 1 / OK | — | PASS — two approved variants |
| 17 | 34@6×6 / 2 / OK | 17@4×5 / 1 / OK | — | PASS — two approved variants |
| 18 | 36@6×6 / 2 / OK | 18@3×6 / 1 / OK | — | PASS — two approved variants |
| 19 | 38@6×7 / 2 / OK | 19@4×5 / 1 / OK | — | PASS — two approved variants |
| 20 | 40@5×8 / 2 / OK | 20@4×5 / 1 / OK | — | PASS — two approved variants |
| 21 | 42@6×7 / 2 / OK | 21@3×7 / 1 / OK | — | PASS — two approved variants |
| 22 | 44@7×7 / 2 / OK | 22@5×5 / 1 / OK | — | PASS — two approved variants |
| 23 | 23@5×5 / 1 / OK | — | — | PASS — one approved variant |
| 24 | 24@4×6 / 1 / OK | — | — | PASS — one approved variant |
| 25 | 25@5×5 / 1 / OK | — | — | PASS — one approved variant |
| 26 | 26@5×6 / 1 / OK | — | — | PASS — one approved variant |
| 27 | 27@5×6 / 1 / OK | — | — | PASS — one approved variant |
| 28 | 28@4×7 / 1 / OK | — | — | PASS — one approved variant |
| 29 | 29@5×6 / 1 / OK | — | — | PASS — one approved variant |
| 30 | 30@5×6 / 1 / OK | — | — | PASS — one approved variant |
| 31 | 31@6×6 / 1 / OK | — | — | PASS — one approved variant |
| 32 | 32@4×8 / 1 / OK | — | — | PASS — one approved variant |
| 33 | 33@6×6 / 1 / OK | — | — | PASS — one approved variant |
| 34 | 34@6×6 / 1 / OK | — | — | PASS — one approved variant |
| 35 | 35@5×7 / 1 / OK | — | — | PASS — one approved variant |
| 36 | 36@6×6 / 1 / OK | — | — | PASS — one approved variant |
| 37 | 37@6×7 / 1 / OK | — | — | PASS — one approved variant |
| 38 | 38@6×7 / 1 / OK | — | — | PASS — one approved variant |
| 39 | 39@6×7 / 1 / OK | — | — | PASS — one approved variant |
| 40 | 40@5×8 / 1 / OK | — | — | PASS — one approved variant |
| 41 | 41@6×7 / 1 / OK | — | — | PASS — one approved variant |
| 42 | 42@6×7 / 1 / OK | — | — | PASS — one approved variant |
| 43 | 43@7×7 / 1 / OK | — | — | PASS — one approved variant |
| 44 | 44@7×7 / 1 / OK | — | — | PASS — one approved variant |
| 45 | 45@5×9 / 1 / OK | — | — | PASS — one approved variant |

### Clarified dynamic variant rule

The mentor permits any board-variant generation logic, including returning up to three variants. The current implementation computes `maxN = floor(45/k)` and derives Large/Medium/Small symbol-group counts from it (`BoardVariant.java:66-84`). Duplicate-sized results are intentionally collapsed, so fewer variants at higher `k` values are correct. The clarified audit independently verified the displayed variant count for all 45 values: three for `k=1..15`, two for `k=16..22`, and one for `k=23..45`.

## Explicit `(k, variant)` pair verification

The audit verified all 82 generated pairs, exceeding the 15-pair minimum. Representative special cases include `(1,L/M/S)`, `(2,L/M/S)`, `(3,L/M/S)`, `(4,L/M/S)`, `(5,L/M/S)`, `(10,L/M/S)`, `(15,L/M/S)`, `(16,L/M)`, `(22,L/M)`, `(23,L)`, `(24,L)`, `(30,L)`, `(44,L)`, and `(45,L)`. Actual board child count always equaled the variant card count, and all actual counts were divisible by `k`.

## Invalid input handling

| Input/action | Observed result | Result |
|---|---|---:|
| `0` then Apply | “k must be between 1 and 45…”; variants cleared; local/host validation blocked. | PASS |
| `46` then Apply | Same range error; variants cleared; local/host validation blocked. | PASS |
| Empty then Apply | “Please enter a whole number…”; variants cleared; local/host validation blocked. | PASS |
| Negative text such as `-1` pasted after applied `2` | Field displays `-1`; Apply shows the 1–45 range error, clears variants, and blocks Start/Host. | PASS |
| Non-numeric text such as `abc` pasted after applied `2` | Field displays `abc`; Apply shows the whole-number error, clears variants, and blocks Start/Host. | PASS |
| Edit applied `2` to `3`, do not Apply, click Start/Host | Start is blocked with “Click Apply before starting after changing k.” | PASS |

# Part C — Exhaustive UI / Screen / Control Coverage

## Resolution and rendering matrix

| Screen | 1366×768 | 1920×1080 | 2560×1440 |
|---|---:|---:|---:|
| Main menu | PASS | PASS | PASS |
| In-game, Large 44-card board | PASS | PASS | PASS |

All required controls and text remained within the viewport with no overlap or clipping. At `k=2`, the rendered menu accurately showed Large 44 cards/7×7, Medium 28 cards/4×7, and Small 14 cards/4×4. Large rendered exactly 44 buttons as six complete rows of seven plus a final row of two. At wider resolutions the layout expands substantially and leaves generous whitespace, but remains clean and usable.

## Main menu coverage

| Control/behavior | Result | Evidence/observation |
|---|---:|---|
| Tuple field + Apply, persistence, edges | PASS | Values 1–45 apply; 0/46/empty/negative/non-numeric values block with an accurate message; unapplied edits also block. |
| Board variant radios | PASS | Under the clarified mentor-approved logic, dynamic labels/counts are accurate and all expected options are selectable: three for `k=1..15`, two for `k=16..22`, and one for `k=23..45`. |
| Player 1/2 names | PASS | Both editable; empty values fall back to defaults; duplicate names and special characters/emoji are accepted; text is capped at 32 UTF-16 characters (`SafeText.java:5,12-25`; `UiInputGuards.java:12-15,34-57`). |
| Host LAN Game | PASS by code/socket evidence | Starts `GameServer`, advertises, disables conflicting menu controls, shows hosting address/status (`MemoryMenuController.java:242-304`). |
| Refresh | PASS by code evidence | Clears list, restarts discovery, updates status (`MemoryMenuController.java:325-335`). |
| Join Selected with no selection | PASS | Rendered disabled; binding is `MemoryMenuController.java:448-451`. |
| Join Selected after selection | PASS by code evidence | Selection listener enables it and selected host is used (`MemoryMenuController.java:130-131,338-377`). Real two-machine discovery remains in Part F. |
| Available Games empty state | PASS | “No LAN games found” rendered cleanly (`MemoryMenu.fxml:301-313`). |
| Start Game and Exit Game rows/chevrons | PASS | Both rendered, mouse-reachable, and wired (`MemoryMenu.fxml:143-197`). |

## In-game coverage

| Area/behavior | Result | Evidence/observation |
|---|---:|---|
| Header/title/status lifecycle | PASS | “Memory Match,” phase, score/turn/result states update correctly. The subtitle now says “unique card” for `k=1`, “matching pairs” for `k=2`, and “matching groups of k” for larger values (`GameController.java:381-388`; `GameBoard.fxml:75-77`). |
| Initial card grid | PASS | Every card starts `?`; exact count and computed row/column geometry matched all tested variants. |
| Card states | PASS by controller/model/CSS evidence | Hidden, face-up, matched-owner, disabled matched, mismatch delay, and reset paths are separate (`GameController.java:563-656`). Physical hover appearance remains manual-only. |
| SCORE | PASS | 0:0 initialization and immediate match scoring verified through full UI round and restarts. |
| TURN | PASS | Matches retain player; mismatch changes player; sidebar reads model state (`GameController.java:519-540`). |
| Restart | PASS | Ten consecutive actions after a completed round fully reset model and UI. |
| Quit to Menu | PASS by code/FXML evidence | Confirmation then network cleanup and routing are implemented (`GameController.java:757-801`). Physical modal interaction remains manual-only. |
| Disconnect/error state | PASS by code/socket evidence | Input locks and warning/message appears; both disconnect directions fired within 3 seconds (`GameController.java:352-362`). |
| Win/draw overlay/bar | PASS | Post-game result bar shows winner/draw plus scores (`GameController.java:666-683`; `GameBoard.fxml:144-151`). |

# Part D — Repeated-Stress and Sequencing Scenarios

| Scenario | Result | Evidence |
|---|---:|---|
| 1. Restart 10 times consecutively after a completed round | PASS | Full 44-card UI round completed; ten Restart clicks each produced 0:0, Player 1, all `?`, no result bar, and a fresh model/board. |
| 2. Host disconnects mid-round; other peer safe within 3 seconds | PASS at transport/code level | Closing host triggered client callback within 3 seconds; handler locks input and displays warning. Full two-window visual confirmation remains in Part F. |
| 3. Rapid same-card and already-matched clicks | PASS | Model/controller ignore non-selectable cards; permanent tests cover same and matched cards (`MemoryGameModelTest.java:245-271`). No double score occurred. |
| 4. Two rounds without app restart (Quit to Menu, Start again) | PASS by construction/stress evidence | Every route/start constructs a new `GameModel` (`GameController.java:175`); 820 completed model rounds and ten controller rebuilds showed no score/turn/deck carryover. Physical confirmation dialog sequence remains in Part F. |
| 5. Join Selected while host round is already in progress | PASS | `GameServer` closes its listening socket immediately after the first accept (`GameServer.java:52-53`). Regression testing confirms a later TCP connection is rejected while the original client remains connected and can exchange messages. |

# Part E — Resolved and Remaining Findings

## RESOLVED-01 — `k=1` symbol multiplicity

- Replaced the 23 old Memory faces with all 45 supplied Icons8 PNGs.
- Removed modulo wrapping from deck generation; every generated group now receives a distinct face.
- All 82 variants passed exact `k`-copy multiplicity checks, and all 45 files loaded through JavaFX.

## RESOLVED-02 — Invalid pasted `k` values

- The field now retains pasted negative/non-numeric text for validation instead of silently restoring the old valid value.
- Apply displays the correct error, clears variants, and prevents local/host start.

## RESOLVED-03 — Late second client connection

- The host's listening socket closes immediately after accepting the first client.
- A permanent socket regression confirms the second connection is rejected and the first connection remains usable.

## RESOLVED-04 — Incorrect “matching pairs” instruction

- The instruction now adapts to `k`: unique card for 1, pairs for 2, and groups of the configured size for 3–45.

## BUG-05 — Permanent automated suite does not cover every functional requirement ID

- **Violation:** `QR-MAIN-01`.
- **Severity:** **blocks acceptance** under the stated acceptance rule.
- **Description:** The build and 328 tests pass, but the permanent suite still does not contain at least one complete durable automated test for every Memory FR ID. Missing end-to-end coverage includes initial synchronized board/turn, out-of-turn network rejection, host move propagation through both controllers, synchronized restart, and connection-loss safe UI.
- **Reproduction:** Run `mvn clean package` and inventory Memory tests. The socket suite now enforces one-client acceptance, but it does not exercise every complete Memory controller/network requirement flow.
- **Citation:** `MemoryGameModelTest.java`; `GameChoiceFxmlSmokeTest.java`; `GameNetworkEndToEndTest.java`.

# Part F — Manual-Only Checklist

- [ ] Launch and complete a Memory game on macOS; reason: no macOS desktop was available, required for CON-PLAT-01.
- [ ] Launch and complete a Memory game on Linux; reason: no Linux desktop was available, required for CON-PLAT-01.
- [ ] Measure cold packaged-app launch until the main menu is visibly painted and confirm ≤3 seconds; reason: FXML render timing is not equivalent to cold process/window launch (QR-PERF-01).
- [ ] Use two physical LAN peers for 10 consecutive valid clicks and visually compare card state after each click; reason: transport/state passed, but two simultaneous visible peer windows were unavailable (QR-USAB-01, QR-PERF-03).
- [ ] Complete 10 consecutive full multiplayer rounds on two physical peers without restarting either app; reason: 820 local/model rounds passed, but this exact reliability fit criterion remains unexecuted (QR-RELY-03).
- [ ] Confirm a hosted game appears in Available Games on a second physical machine and disappears after TTL/cancel; reason: socket behavior and discovery code were audited, but cross-machine UDP broadcast/firewall behavior was not physically observed.
- [ ] Confirm Join Selected visibly enables after selecting a real discovered host; reason: binding/code passed, but no second physical discovery UI was available.
- [ ] Hover every menu action, variant radio, hidden/face-up/matched card, Restart, and Quit at all three resolutions; reason: static/rendered states were verified, but physical pointer-hover styling was not exhaustively captured.
- [ ] Force a mismatch on both a host turn and client turn and time visible flip-back on both windows; reason: code delay is 800 ms and transport passed, but dual-window visible timing was not recorded.
- [ ] Kill the host process mid-round and confirm the client visibly disables every card and shows the warning within 3 seconds; reason: callback and handler passed, but abrupt-process two-window visual behavior remains manual.
- [ ] Kill the client process mid-round and confirm the host visibly disables every card and shows the warning within 3 seconds; reason: callback and handler passed, but abrupt-process two-window visual behavior remains manual.
- [ ] From the disconnect warning, click OK and verify a clean menu return with no stale hosting/discovery state; reason: blocking native Alert interaction was not automated.
- [ ] Exercise Quit to Menu confirmation with mouse, then complete a second full round and compare independence; reason: fresh-model construction/stress passed, but the native confirmation-dialog sequence was not physically clicked.
- [ ] Enter a 32-character mixed emoji/surrogate-pair name on both peers and visually inspect scoreboard/turn text; reason: the 32 UTF-16-unit cap passed, but glyph-boundary rendering across platforms should be checked manually.

# Part G — Final Acceptance Determination

## Determination: REJECTED

All High-priority functional requirements now pass. The determination remains rejected solely because `QR-MAIN-01` fails the explicit per-functional-requirement automated-test coverage criterion that was intentionally excluded from this repair. Five items also remain MANUAL-ONLY, including cross-platform operation and full two-peer visual/reliability criteria.

## High-priority functional checklist

- [x] FR-NET-01 — PASS
- [x] FR-GAME-02 — PASS
- [x] FR-INPUT-01 — PASS
- [x] FR-INPUT-02 — PASS
- [x] FR-GAME-03 — PASS
- [x] FR-NET-02 — PASS
- [x] FR-GAME-04 — PASS
- [x] FR-GAME-05 — PASS
- [x] FR-GAME-06 — PASS
- [x] FR-GAME-07 — PASS
- [x] FR-GAME-08 — PASS
- [x] FR-NET-03 — PASS
- [x] FR-GAME-10 — PASS
- [x] FR-GAME-11 — PASS
- [x] FR-NET-05 — PASS

## Quality acceptance checklist

The authoritative quality list does not assign individual priorities, so every quality item is shown rather than silently treating unlabelled items as optional.

- [ ] QR-USAB-01 — MANUAL-ONLY
- [x] QR-USAB-02 — PASS
- [ ] QR-PERF-01 — MANUAL-ONLY
- [x] QR-PERF-02 — PASS
- [ ] QR-PERF-03 — MANUAL-ONLY
- [x] QR-PERF-04 — PASS
- [x] QR-PERF-05 — PASS
- [x] QR-PERF-06 — PASS
- [x] QR-PERF-07 — PASS
- [x] QR-PERF-08 — PASS
- [x] QR-NET-01 — PASS
- [x] QR-NET-02 — PASS
- [ ] QR-RELY-03 — MANUAL-ONLY
- [x] QR-RECV-01 — PASS
- [ ] QR-MAIN-01 — **FAIL** (build passes; per-FR automated coverage does not)
- [x] QR-MAIN-02 — PASS

BUG-05 is the only remaining reportable bug and was intentionally left unresolved per the user's instruction. The dynamic one-to-three board-variant generation is accepted and is not a defect.
