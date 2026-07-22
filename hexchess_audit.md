# Full Exhaustive Audit — Hexagon Chess (Chexsagon)

Audit date: 2026-07-22  
Scope: read-only verification of Chexsagon model, controllers, FXML/CSS, networking, and existing tests  
Specification: `FULL EXHAUSTIVE AUDIT — HEXAGON CHESS (CHEXSAGON)` supplied with the task

## 1. Executive summary

The audit evaluated all **56 explicit requirement IDs**. Results:

| Result | Count |
|---|---:|
| PASS | 38 |
| FAIL | 0 |
| PARTIAL | 5 |
| CANNOT VERIFY STATICALLY | 13 |
| **Total** | **56** |

The core board geometry, normal piece movement, legal-move filtering, en passant, promotion, checkmate, Glinski stalemate scoring, 50-move rule, threefold triggering, insufficient material, draw workflow, resignation, custom-position rejection, and the bot's three critical tactical behaviors all worked in executable probes.

Five defects or specification gaps were found during the read-only audit and have now been resolved:

1. **Resolved — Custom games now retain valid initial pawn double-step rights**, including an untouched standard board loaded through the custom editor.
2. **Resolved — Pawn double-step rights are consumed**, so a moved pawn cannot inherit a right by capturing onto another start square.
3. **Resolved — Repetition keys include pawn double-step rights.**
4. **Resolved — `HexChessStateSnapshot` round-trips both player names**, while retaining legacy 12-field snapshot compatibility.
5. **Resolved — `HexGameState.create(...)` stores terminal resolution scores** for directly created checkmate/stalemate positions.

The post-fix full Maven build passed **336/336 tests**. The Chexsagon model/rules/layout classes account for **106/106 passing tests**, including five permanent regression tests for the repaired findings. A broader focused regression/hardening run passed **118/118**.

Post-audit fixes changed only the relevant Chexsagon controller/model/snapshot files, the Chexsagon model test, and this report. Unrelated Memory Match and Zetris worktree changes were preserved.

### Audit method and limitations

- Read every Chexsagon model, controller, network, FXML/CSS, and existing Chexsagon test file.
- Ran the full suite with `mvn package`.
- Ran the focused suite with `mvn "-Dtest=HexChessModelTest,HexChessRulesTest,HexChessSetupLayoutTest" test`.
- Ran additional ephemeral JShell probes against compiled production classes. These created no source/test files and changed no application code.
- Existing JavaFX tests instantiated and laid out screens at the required resolutions. This environment did not provide direct desktop screenshot/interaction inspection. Therefore, in accordance with the audit instruction, all visual-only UI IDs are marked **CANNOT VERIFY STATICALLY**, with automated/static supporting evidence noted rather than guessed visual conclusions.

## 2. Section 1 — Board and geometry

| ID | Result | Evidence and notes |
|---|---|---|
| HC-GEO-01 | PASS | `HexBoardGeometry.RADIUS` is 5 and executable enumeration returned exactly 91 coordinates. Evidence: `HexBoardGeometry.java:15,22-26` and runtime `cells=91`. |
| HC-GEO-02 | PASS | Files are exactly `[a,b,c,d,e,f,g,h,i,k,l]`; `j` is skipped. A scoped search found no castling or `j`-coordinate references in Chexsagon source/resources/tests. Snapshot and notation use validated `HexCoordinate`. Evidence: `HexBoardGeometry.java:16`, `HexChessStateSnapshot.java:140`. |
| HC-GEO-03 | PASS | Existing tests reject invalid file, rank 0, and out-of-board coordinates. Geometry checks require known file and rank within variable file length. Boundary probes/tests include `a0`, `z1`, `f12`, and `j5`. Evidence: `HexBoardGeometry.java:42-56`; `HexChessModelTest.java:40-55`. |
| HC-GEO-04 | PASS | Programmatically checked every cell in all six edge directions; adjacent same-tone conflicts = **0**. Tone uses floor-modulo 3 over axial coordinates. Evidence: `HexBoardGeometry.java:59-65`. |
| HC-GEO-05 | PASS | Standard board has 36 pieces: per side K1/Q1/R2/B3/N2/P9. Exact White squares: king `g1`, queen `e1`, rooks `c1,i1`, knights `d1,h1`, bishops `f1,f2,f3`, pawns `b1,c2,d3,e4,f5,g4,h3,i2,k1`. Black: king `g10`, queen `e10`, rooks `c8,i8`, knights `d9,h9`, bishops `f9,f10,f11`, pawns `b7,c7,d7,e7,f7,g7,h7,i7,k7`. Evidence: `HexStartingPosition.java:9-32`; existing tests passed. |
| HC-GEO-06 | PASS | `HexBoard.empty()` returns an immutable empty board; the custom editor's Clear action uses it. Evidence: `HexBoard.java:28-30`; `HexChessSetupController.java:120-124`. |

## 3. Section 2 — Piece movement and special rules

### Piece movement

| ID | Result | Exact executable position and result |
|---|---|---|
| HC-PIECE-01 | PASS | White rook on `f6`; own pawn `f8`; enemy pawn `f4`. On the f-axis the generated destinations were `f7,f5,f4`; it excluded own `f8` and all squares beyond captured `f4` (`f3,f2,f1`). On an empty board, rook `f6` had 30 destinations over six directions/three axes. |
| HC-PIECE-02 | PASS | White bishop `f6`; own pawn `e7`; enemy pawn `h5`. It excluded `e7` and beyond (`d8`), included capture `h5`, and excluded beyond (`k4`). Empty-board bishop `f6` destinations were exactly `b4,d2,d5,d8,e4,e7,g4,g7,h2,h5,h8,k4` (12). |
| HC-PIECE-03 | PASS | Empty board, White queen/rook/bishop tested separately from `f6`. Rook set = 30, bishop set = 12, queen set = 42, and the queen set was the exact disjoint union. Full queen list: `a1,a6,b2,b4,b6,c3,c6,d2,d4,d5,d6,d8,e4,e5,e6,e7,f1,f2,f3,f4,f5,f7,f8,f9,f10,f11,g4,g5,g6,g7,h2,h4,h5,h6,h8,i3,i6,k2,k4,k6,l1,l6`. |
| HC-PIECE-04 | PASS | Empty-board king `f6` generated exactly 12 destinations: `d5,e4,e5,e6,e7,f5,f7,g4,g5,g6,g7,h5`. Safety probe: White king `f4`, Black rook `f6`, Black king `l6`; pseudo-legal `f4-f3` existed before moving because the king itself blocked the ray, but legal filtering rejected it after simulating removal of that blocker. |
| HC-PIECE-05 | PASS | No castling code, state, protocol field, move, button, or text exists in scoped Chexsagon code/resources/tests. King generation is a one-step union of the 12 queen directions. |
| HC-PIECE-06 | PASS | Empty-board knight `f6` produced exactly 12 code-defined jumps: `c4,c5,d3,d7,e3,e8,g3,g8,h3,h7,i4,i5`. The move table is generated as two straight steps plus one neighboring straight direction around each of six directions (`HexMoveRules.java:144-163`). |
| HC-PIECE-07 | PASS | White pawn `d3` generated `d3-d4`; Black pawn moves analogously south. Forward movement requires an empty destination (`HexMoveGenerator.java:180-199`). |
| HC-PIECE-08 | PASS | Normal and custom-loaded standard `b1` both generate `b2,b3`. Rights are initialized only for matching unmoved pawns actually occupying start squares and are consumed when affected by a move/capture. Regression position `WK a1, BK l6, WP d3, BR e4`: after `d3xe4`, `...l6-i7`, moved pawn `e4` has `e5` but not illegal `e6`. |
| HC-PIECE-09 | PASS | White pawn `f6` with enemies on `e6`, `f7`, and `g6` generated captures only to `e6,g6`; it could not capture straight to `f7`. Pawn attack deltas are `(1,0)` and `(-1,1)`, distinct from bishop directions. Evidence: `HexMoveRules.java:18-20`. |

### Special rules

| ID | Result | Exact executable position and result |
|---|---|---|
| HC-SPECIAL-01 | PASS | `WK a1, BK l6, WP e5, BP f7`, Black to move. `f7-f5` set en-passant target `f6`; White had `e5xf6 e.p.`. Playing it removed the Black pawn from `f5`, left `f5` empty, and placed the White pawn on `f6`. |
| HC-SPECIAL-02 | PASS | Same position: after `f7-f5`, White played `a1-a2`, Black replied `l6-i7`; White `e5` then had only `e5-e6`, and the en-passant move was absent. |
| HC-SPECIAL-03 | PASS | White: `WK a1, BK l6, WP a5`, White to move; `a5-a6` generated Q/R/B/N and each chosen move replaced the pawn. Black: `WK f6, BK l6, BP a2`, Black to move; `a2-a1` generated Q/R/B/N and each result contained the selected Black piece on `a1`. Controller dialog builds the four distinct choices at `HexChessGameController.java:355-381`. |
| HC-SPECIAL-04 | PASS | Pin position: `WK f1, WR f2, BR f11, BK l6`, White to move. Rook pseudo-move `f2-e2` existed, but legal filtering removed it because it exposed `f1`; only moves remaining on that pin line were `f3` through capture `f11`. |
| HC-SPECIAL-05 | PASS | Discovered-ray king probe `WK f4, BR f6, BK l6`: pseudo `f4-f3` was generated, but legal `f4-f3` was rejected after simulating removal of the king from `f4`. Normal opponent-attack checking is in `HexLegalMoveValidator.java:57,62-82`. |
| HC-SPECIAL-06 | PASS | Valid pre-position `WK a1, WQ b1, BK a3`, White to move; `b1-b3` produced CHECKMATE with score **1.0-0.0** and message “Checkmate. White wins.” |
| HC-SPECIAL-07 | PASS | Valid pre-position `WK a1, WQ b6, BK a3`, White to move; `b6-c6` produced STALEMATE with score **0.75-0.25** and the correct message. Stalemate is evaluated before ordinary draw conditions (`HexGameEndDetector.java:50-56`). |

## 4. Section 3 — Game state, flow, and scoring

| ID | Result | Evidence and notes |
|---|---|---|
| HC-STATE-01 | PASS | Executed RUNNING, CHECK, CHECKMATE, STALEMATE, and DRAW transitions; existing tests passed for RESIGNED, ERROR, and DISCONNECTED. Active state is strictly RUNNING/CHECK (`HexGameState.java:96-98`). |
| HC-STATE-02 | PASS | `HexGameState` and `HexBoard` are records with defensive `Map.copyOf`/`Set.copyOf`; play, draw, resign, error, and disconnect paths construct new states. Runtime comparisons confirmed prior board/state values remained unchanged. Evidence: `HexGameState.java:31-58,392-417`; `HexBoard.java:12-25,44-57`. |
| HC-STATE-03 | PASS | Board `WK a1, BK l6, WR b1, BR k6`, state injected at clock 99: legal quiet `b1-b2` produced clock 100 and DRAW 0.5-0.5. A `d3-d4` pawn move from clock 99 reset to 0. Capture probe `WK a1, BK l6, WR b1, BN b2`: `b1xb2` reset 99 to 0. Threshold constant is 100 plies (`HexMoveRules.java:12`). |
| HC-STATE-04 | PASS | Real standard-board cycle `h1-e2, c8-d8, e2-h1, d8-c8` remained RUNNING on occurrence 2 and became DRAW 0.5-0.5 exactly on occurrence 3. Position keys now include side-to-move, en-passant, and a canonical sorted encoding of pawn double-step rights; a regression test confirms differing rights produce different keys. |
| HC-STATE-05 | PASS | Insufficient material exists and is evaluated. Existing executable tests passed for K-vs-K and K-vs-K+B as insufficient, and positions with pawn or rook as sufficient. Evidence: `HexGameEndDetector.java:66-68,75-96`. |
| HC-STATE-06 | PASS | Offer/accept produced DRAW 0.5-0.5; decline cleared the offer and continued. Offer then move cleared `drawOfferBy`; a later accept returned the unchanged active state. Runtime status said “Draw offer revoked after move. Black to move.” Evidence: `HexGameState.java:137-210,266-299`. |
| HC-STATE-07 | PASS | Existing tests passed White and Black resignation. Resigning side scores 0 and opponent 1; status is RESIGNED and names/colors are present in the result message. Evidence: `HexGameState.java:212-235`. |
| HC-STATE-08 | PASS | Outcomes remain distinct: checkmate 1-0, resignation 1-0/0-1, ordinary draw 0.5-0.5, stalemate 0.75-0.25. Direct creation regressions now also pass: `WK a1,WQ b3,BK a3`, Black to move stores CHECKMATE 1-0; replacing `WQ b3` with `WQ c6` stores STALEMATE 0.75-0.25. |

## 5. Section 4 — Modes, custom builder, and bot

### Modes and bot

| ID | Result | Evidence and notes |
|---|---|---|
| HC-MODE-01 | **PARTIAL** | Local controller permits control of whichever side is on turn, and a complete custom-position checkmate was played through the model. A full standard-length game was not manually clicked through the desktop UI. |
| HC-MODE-02 | **PARTIAL** | Bot is restricted to Black, runs after a 350 ms pause on a daemon worker, returns only a member of the legal-move list, and transitions to ERROR on runtime failure. Executable position probes did not crash. Real-time UI lifecycle was not manually observed. Evidence: `HexChessGameController.java:384-433`. |
| HC-MODE-03 | PASS | `BK a1, BQ b1, WK a3`, Black to move. Only mating move was `b1-b3`; the bot selected it and the played result was CHECKMATE. |
| HC-MODE-04 | PASS | `BK h1, BQ f2, WK g4, WQ b1`, Black to move. Fourteen moves allowed White mate-in-one (`f2-b2,b6,c2,d1,e2,f1,f7,f8,f9,f10,f11,h2,i5,k6`); 21 moves were safe. Bot chose safe `f2-a2`. |
| HC-MODE-05 | PASS | `BK a1, WK a3, WQ c4`, Black to move. Black's only legal move was `a1-b1`, after which White had `c4-b2#`. Bot still returned `a1-b1`; it did not stall or crash. |
| HC-MODE-06 | PASS | Nontrivial snapshot round-trip preserves board, turn/status, message, last move, en-passant target, draw offer, clocks, scores, double-move rights, repetition history, and both player names. `MatchSnapshot` returns state plus names, controller STATE/START broadcasts use actual setup names, and legacy 12-field snapshots remain accepted with safe default names. |
| HC-MODE-07 | **PARTIAL** | Protocol construction/parsing passed for START, MOVE (including promotion/en-passant), STATE, DRAW_OFFER, RESIGN, and QUIT. Host handles JOIN/MOVE/draw/resign/restart/quit and broadcasts authoritative STATE; client handles START/STATE/ERROR/QUIT. Generic TCP end-to-end tests passed. No two-controller live LAN match was manually exercised. Evidence: `HexChessGameController.java:454-531`; `HexChessProtocol.java:15-103`. |
| HC-MODE-08 | **PARTIAL** | Generic network tests verified both graceful client close and graceful host close, including final QUIT delivery. Controller listeners map joiner loss to “Joiner disconnected.” and host loss to “Host disconnected.” and call `gameState.disconnected(...)`. A live Chexsagon UI disconnect in both directions was not manually observed. Evidence: `HexChessGameController.java:273-286,559-573`; `GameNetworkEndToEndTest.java:83-170`. |

### Custom Position Builder

| ID | Result | Exact position/result |
|---|---|---|
| HC-CUSTOM-01 | PASS | Adjacent kings `WK a1, BK a2` rejected: “Kings cannot stand next to each other.” |
| HC-CUSTOM-02 | PASS | White missing (`BK l6`), Black missing (`WK a1`), and both missing (empty board) were separately rejected with exact king-count messages. |
| HC-CUSTOM-03 | PASS | Black to move while White checked: `WK f1, BK l6, BR f6` rejected. Black to move while White already checkmated: `WK a3, BK a1, BQ b3` rejected. Both report that White cannot already be in check when Black is to move. |
| HC-CUSTOM-04 | PASS | `WK a1, BK l6` plus 10 legal-square White pawns rejected: “White has too many pawns; maximum is 9.” Untouched standard position was accepted with “Position is valid.” The later pawn-right gameplay defect is reported under HC-PIECE-08/BUG-01. |
| HC-CUSTOM-05 | **PARTIAL** | The 36-piece standard position (the legal maximum total using standard material) validated successfully. 1,000 repeated validations completed in about 2.97 seconds (~3 ms each) without crash. Large-position canvas rendering/editor interaction was not visually observed, so rendering slowdown cannot be fully certified. |

## 6. Section 5 — UI and visual verification

Direct desktop visual inspection was unavailable. The existing JavaFX tests did instantiate and lay out Chexsagon menu/game/setup screens, including required `1366x768` and `1920x1080` dimensions; the custom setup test checked header, board panel, canvas frame, side panel, action row, and absence of required scrolling. Responsive tests also exercised board resize cycles from `640x360` through `1920x1080`. These are useful automated signals, but they are not substituted for the requested human visual inspection.

| ID | Result | Static/automated supporting evidence; required manual check |
|---|---|---|
| HC-UI-01 | **CANNOT VERIFY STATICALLY** | `WindowManager` explicitly sets fullscreen false and maximized true (`WindowManager.java:78-80`). Must visually confirm title bar and taskbar. |
| HC-UI-02 | **CANNOT VERIFY STATICALLY** | JavaFX responsive/layout tests passed at both required resolutions and assert key anchors/canvases inside their containers. Must visually inspect every control/text/icon for clipping or overflow. |
| HC-UI-03 | **CANNOT VERIFY STATICALLY** | No automated test checks rendered text overrun/ellipsis. Must inspect every Chexsagon screen with normal and long player/status text. |
| HC-UI-04 | **CANNOT VERIFY STATICALLY** | FXML consistently includes `HexChess.css`; CSS declares light backgrounds and no scoped black background (`HexChess.css:1-31,75-81`). Must visually compare with Memory Match and Zetris. |
| HC-UI-05 | **CANNOT VERIFY STATICALLY** | Resize listeners refit hex size by both width and height; automated resize cycle kept Canvas within board zone (`HexChessGameController.java:109-171`). Distortion/gaps/alignment require visual inspection. |
| HC-UI-06 | **CANNOT VERIFY STATICALLY** | Sidebar is a responsive 30% GridPane column with its own vertical ScrollPane; responsive anchors passed. Full visibility and visual quality require inspection. |
| HC-UI-07 | **CANNOT VERIFY STATICALLY** | Selection uses `legalMovesFrom`; legal destination fill is green `#9fd3b5` (`HexChessGameController.java:327-339,666-674`). Must visually click pieces and inspect every highlight. |
| HC-UI-08 | **CANNOT VERIFY STATICALLY** | Both last-move endpoints receive a 3px purple stroke (`HexChessGameController.java:654-685`). Must visually inspect after local, bot, and remote moves. |
| HC-UI-09 | **CANNOT VERIFY STATICALLY** | Checked king cell becomes red and status label receives “... is in check.” (`HexChessGameController.java:608-690`; `HexGameEndDetector.java:70-72`). Visibility/clarity requires inspection. |
| HC-UI-10 | **CANNOT VERIFY STATICALLY** | `ChoiceDialog` lists Queen/Rook/Bishop/Knight and maps choice to matching legal move. Model replacement passed. Dialog presentation and clicking all four options require manual UI execution. |
| HC-UI-11 | **CANNOT VERIFY STATICALLY** | Offer Draw and Resign buttons exist in shared game FXML and controller branches cover Local/Bot/Network. Actual button behavior in all three UI modes needs manual execution. |
| HC-UI-12 | **CANNOT VERIFY STATICALLY** | Validation label is populated from `HexPositionValidator`, styled good/warn, and Start is disabled when invalid (`HexChessSetupController.java:326-339`). Exact rendered messages need visual inspection for every invalid case. |
| HC-UI-13 | **CANNOT VERIFY STATICALLY** | Full Memory/Zetris tests passed and no Chexsagon file was modified, but a visual regression comparison was not available. |

## 7. Existing automated test suite

### Full suite

Command: `mvn package`

- Tests run: **336**
- Passed: **336**
- Failures: **0**
- Errors: **0**
- Skipped: **0**
- Build result: **BUILD SUCCESS**

Class counts reported by Maven:

- `GameChoiceFxmlSmokeTest`: 15
- `HexChessSetupLayoutTest`: 1
- `ResponsiveFxmlLayoutTest`: 5
- `TetrisUiContractTest`: 15
- `GameLogicTest`: 1
- `HexChessModelTest`: 102
- `HexChessRulesTest`: 3
- `MemoryGameModelTest`: 60
- `TetrisComprehensiveTest`: 94
- `TetrisModelTest`: 22
- `AdversarialInputRobustnessTest`: 6
- `GameNetworkEndToEndTest`: 4
- `NetworkSnapshotHardeningTest`: 6
- `SoundManagerTest`: 2

### Focused Chexsagon and regression suites

Command: `mvn "-Dtest=HexChessModelTest,HexChessRulesTest,HexChessSetupLayoutTest" test`

- Chexsagon model/rules/layout tests represented in the focused run: **106 passed**
- Broader focused run including snapshot/adversarial hardening: **118 passed**
- Failures/errors/skips: **0/0/0**

### Important permanent-coverage gaps

Permanent regression tests now cover moved-pawn right consumption, custom-standard double moves, repetition-key rights, snapshot state/name round-trip, and direct terminal scoring. Remaining permanent-coverage gaps include central full move sets, blocker/capture rays, en-passant success/expiry, both-color four-way promotion replacement, real mate/stalemate delivery, exact 100-ply trigger, a full threefold sequence, draw-offer move revocation, the three mandated bot tactical cases, all custom invalid cases, and two-controller LAN gameplay.

## 8. Resolved bug list

### BUG-01 — RESOLVED — Custom setup disabled every legal initial pawn double-step

**Reproduction:** In Custom Position Builder, load Standard, leave White to move, and start. On the resulting untouched standard board, select pawn `b1`. Only `b2` is legal; `b3` is missing. Normal standard mode offers both `b2` and `b3`.

**Cause:** `HexChessGameController.startGame` calls `HexGameState.create(..., !setup.customPosition())` (`HexChessGameController.java:261-264`). For every custom position this supplies `false`, so `initialDoubleMoveEligibleSquares` returns an empty set (`HexGameState.java:433-438`).

**Impact:** A standard position launched from the editor does not obey the same pawn rules as the ordinary standard game; any legitimately unmoved pawn on a standard start square loses its two-step move.

**Resolution:** Game startup now derives eligibility from matching pawns actually present on their color's standard starting squares. Custom Standard therefore preserves all valid initial double moves without granting rights to absent or wrong-type pieces.

### BUG-02 — RESOLVED — A moved pawn could regain a double-step on another start square

**Reproduction:** State `WK a1, BK l6, WP d3, BR e4`, White to move, standard rights enabled. Play `d3xe4`; Black plays `l6-i7`. The moved White pawn on `e4` is then offered both `e4-e5` and illegal `e4-e6`.

**Cause:** Eligibility is represented by board coordinates. `usesStandardDoubleMoveRules` treats the full standard square set as a permanent sentinel, and `updateDoubleMoveEligibility` returns it unchanged (`HexMoveRules.java:50-69`). A moved pawn that lands on any different listed start square inherits that square's right.

**Impact:** Reachable/custom gameplay can allow an illegal two-square pawn move after that pawn has already moved.

**Resolution:** Eligibility is now always consumable. Every move removes affected source, destination, and captured coordinates from the rights set. The exact `d3xe4, ...l6-i7` regression confirms `e4-e6` is absent.

### BUG-03 — RESOLVED — Threefold position identity omitted double-step rights

**Reproduction:** Compare ordinary `HexGameState.standard()` (18 eligible coordinates) with `HexGameState.create(HexBoard.standard(), WHITE, false)` (0 eligible coordinates). Board, side, and en-passant are equal. `HexPositionKey.from(...)` returns the same key even though legal pawn moves differ (`b1-b3` exists only in the first).

**Cause:** `HexPositionKey.from` serializes only pieces, turn, and en-passant target (`HexPositionKey.java:11-21`).

**Impact:** The key does not meet the requirement that all legal rights distinguish repeated positions. Current normal flows partly mask this because BUG-01/BUG-02 make rights effectively all-or-none/static within a game, but correcting pawn history without correcting the key would permit false repetition draws.

**Resolution:** `HexPositionKey` now appends a canonical sorted rights component. `HexGameState` calculates the next rights before recording the next repetition key. A permanent test proves equal board/turn/en-passant with different rights yields different keys.

### BUG-04 — RESOLVED — State snapshot did not serialize player names

**Reproduction:** Serialize any `HexGameState`; the snapshot has 12 fields and no names. `HexChessStateSnapshot.serialize` cannot receive names because `HexGameState` has no name fields.

**Cause:** Names belong to `HexChessGameSetup`; the protocol sends them as separate fields only in `START` (`HexChessProtocol.java:34-36`).

**Impact:** The explicit HC-MODE-06 snapshot contract is unmet. Normal initial LAN setup still carries both names through START, so this is presently more a state-contract/rejoin gap than a demonstrated first-connect failure.

**Resolution:** The snapshot now contains encoded White and Black names and exposes a `MatchSnapshot` result. Host START/STATE messages serialize actual setup names; clients apply them. Deserialization still accepts legacy 12-field snapshots using safe default names.

### BUG-05 — RESOLVED — Direct terminal-state creation stored 0-0

**Reproduction:** Create `WK a1, WQ b3, BK a3`, Black to move. `HexGameState.create` returns CHECKMATE and “White wins” but scores 0.0-0.0. With `WQ c6` instead, it returns STALEMATE and says White gets 0.75 but still stores 0.0-0.0.

**Cause:** `create` computes a `HexGameResolution` but hardcodes the final constructor scores to `0,0` (`HexGameState.java:73-93`) rather than using `resolution.whiteScore()` and `resolution.blackScore()`.

**Impact:** Public model construction can produce contradictory terminal state. The custom UI currently rejects already-terminal starts, and terminal states reached through `play` score correctly, which limits normal user exposure.

**Resolution:** `HexGameState.create` now uses `resolution.whiteScore()` and `resolution.blackScore()`. Permanent tests verify direct checkmate stores 1-0 and direct stalemate stores 0.75-0.25.

## 9. Manual-only follow-up checklist

1. At native maximized **1366x768** and **1920x1080**, visually inspect menu, live match, and custom builder for clipping, overflow, title/taskbar behavior, icon quality, and ellipsis.
2. Repeat with long allowed player names and long status/validation messages.
3. Click legal moves and verify green highlights, selected outline, last-move purple outline, and red checked-king fill at several window sizes.
4. Trigger promotion for both colors and manually select each Q/R/B/N option.
5. Complete a full standard local hotseat game, then a bot game, checking the 350 ms lifecycle during restart/back navigation.
6. Run two application instances over LAN. Exercise START, legal and illegal MOVE, draw offer/accept/decline/revoke, RESIGN, restart, QUIT, then hard-disconnect client and host separately.
7. Fill the editor near its legal material limit and visually assess redraw/click responsiveness.
8. Compare Memory Match and Zetris screens side-by-side for visual regression.

## 10. Release recommendation

**The five identified code defects are fixed and all 336 automated tests pass.** Before a final release, complete the remaining manual visual checks and a two-instance LAN gameplay/disconnect session because those environment-dependent items remain only partially or non-visually verified.
