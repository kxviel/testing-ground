# Tetris Implementation Roadmap

Source requirements reviewed: `C:\Users\kxviel\Downloads\requirements_tetris_masters.pdf`, extracted on 2026-05-09.

This roadmap is intentionally requirement-driven. When the PDF does not specify a detail, this document records the chosen implementation decision instead of pretending the PDF defined it.

## Implementation Checklist

### Done

- [x] Tetris entry button routes to the Tetris menu.
- [x] Tetris menu supports Local and LAN mode selection.
- [x] Local mode collects two player names and starts the Tetris screen.
- [x] LAN host advertises through Tetris-only UDP discovery.
- [x] LAN join lists discovered hosts and connects over TCP.
- [x] LAN host shows the joined player and enables Start Game after a join.
- [x] Tetris setup data is passed through router data.
- [x] Minimal custom config pipe exists for selected piece sets.
- [x] Tetris game screen shows two 10x20 boards, names, scores, config, and top-board inversion.
- [x] Pure Tetris model foundation exists for board, cells, pieces, rotation, player state, and game state.
- [x] Basic model piece behavior exists for spawn, left/right movement, soft drop, rotation, and invalid move rejection.
- [x] Gravity and lock behavior exists for bottom and top boards.
- [x] Dummy board cells were replaced with rendering from `TetrisGameState`.
- [x] Local keyboard controls update the two model-backed boards.
- [x] Top board is visually inverted while the model keeps one downward gravity rule.
- [x] Line clear and one-point-per-line score update exists in the model and UI.
- [x] Local game loop spawns pieces, advances gravity, detects loss, shows winner/draw, and supports restart.
- [x] Custom connected-block editor validates, normalizes, saves, and routes session custom pieces.
- [x] Tetris-specific LAN protocol messages exist separately from Memory protocol.
- [x] LAN gameplay uses host-authoritative snapshots with joiner input commands.
- [x] LAN restart, quit, and disconnect paths stop safely and update the game view.
- [x] Special objects spawn during gameplay and apply timed effects or swap player boards/pieces when hit.
- [x] Each spawned block keeps one random color after locking and through LAN snapshots.
- [x] Bug cells render with an emoji marker.
- [x] LAN disconnects show "Your opponent has left the game." and return to the Zetris menu.
- [x] README documents current GameBox/Zetris build, run, and play instructions.
- [x] Focused Tetris model and protocol tests were added.

### Next To Do In Order

1. [x] Build the pure Tetris model foundation.
   - Add board, cell, piece, rotation, player state, and game state types.
   - Keep this package JavaFX-free and network-free.
2. [x] Implement basic piece behavior in the model.
   - Spawn standard pieces.
   - Move left/right.
   - Soft drop.
   - Rotate.
   - Reject invalid moves at board edges and collisions.
3. [x] Implement gravity and lock behavior.
   - Bottom board pieces fall downward.
   - Top board pieces move upward.
   - Locked cells become part of the settled board.
4. [x] Replace dummy board cells with real model rendering.
   - Render both `10x20` boards from model state.
   - Keep top-board inversion visual and consistent with model coordinates.
5. [x] Add local keyboard controls.
   - Bottom player: arrow keys.
   - Top player: `WASD`.
   - Keep focus on the game scene after menu/start actions.
6. [x] Implement line clear and score update.
   - One point per cleared line.
   - Update board and score labels immediately.
7. [x] Implement loss, continuation, winner/draw, and restart.
   - A lost player stops generating blocks.
   - The opponent continues.
   - Game ends after both players lose.
   - Restart resets both boards and scores.
8. [x] Add the real custom connected-block editor.
   - Validate non-empty orthogonally connected shapes.
   - Normalize saved shapes.
   - Add valid custom parts to the session config.
9. [x] Add Tetris-specific LAN protocol messages.
   - Define JOIN, START, INPUT, STATE, restart, quit, and error message builders.
   - Use the Tetris protocol for the current LAN setup handshake.
   - Keep this separate from Memory `Protocol`.
10. [x] Synchronize LAN gameplay after game start.
    - Host owns game state.
    - Joiner renders snapshots.
    - Handle quit/disconnect cleanly.
11. [x] Add automated tests.
    - Model tests first.
    - Protocol serialization tests next.
    - Controller/manual checks only where automation is not practical.
12. [ ] Run final manual acceptance.
    - Local two-player game.
    - Two-instance LAN game.
    - Custom piece validation.
    - Restart and disconnect behavior.

## Current Repository State

- The project is a JavaFX/Maven desktop app using Java 25 in `pom.xml`, which satisfies the PDF constraint `CON-TECH-01` requiring Java 21 or newer.
- The app currently contains a completed Memory game and a working first-pass Tetris menu/game shell:
  - `src/main/resources/tetris/TetrisMenu.fxml` contains Local/LAN menu panes and minimal config controls.
  - `src/main/java/seda_project/control_alt_defeat/gamebox/controller/tetris/TetrisMenuController.java` handles Local setup, UDP LAN discovery, TCP join/start handshake, and config routing.
  - `src/main/resources/tetris/TetrisGame.fxml` contains a two-board Tetris view.
  - `src/main/java/seda_project/control_alt_defeat/gamebox/controller/tetris/TetrisGameController.java` renders `TetrisGameState` into two 10x20 boards.
  - `src/main/java/seda_project/control_alt_defeat/gamebox/model/tetris/` contains the first pure model foundation types and custom piece validation.
  - `src/main/java/seda_project/control_alt_defeat/gamebox/network/tetris/TetrisProtocol.java` contains Tetris-specific LAN message builders/parsing.
  - `src/main/java/seda_project/control_alt_defeat/gamebox/controller/GameChoice.java` routes `playTetris` to `/tetris/TetrisMenu.fxml`.
- Existing reusable infrastructure:
  - `Router` can switch scenes.
  - `GameServer`, `GameClient`, and `MessageRouter` provide TCP LAN communication.
  - Existing `Protocol` constants are Memory-specific, so Tetris should use a separate Tetris protocol class and should not modify Memory protocol behavior.
- `module-info.java` exports/opens the current Tetris controller, model, and network packages.

## Requirement Summary From The PDF

### In Scope

- Local two-player Tetris on one system.
- LAN two-player Tetris over the same local network.
- Mode selection and game start flow.
- Player names for local and LAN gameplay.
- Block generation, movement, rotation, and falling.
- Independent board for each player.
- Top player board displayed inverted, with top board blocks moving upward.
- Line detection, clearing, board update, and score update.
- Loss detection, stored losing score, opponent continuation, winner/draw declaration, and restart.
- Low-latency LAN synchronization of board, score, player name, active block, speed, and player status.
- Custom connected-block design option.
- In-game objects that swap the players when hit.

### Out Of Scope

- Internet multiplayer outside LAN.
- Mobile app.
- Web app.
- AI opponent.
- Persistent accounts, online leaderboard, or ranking.

### Constraints

- Java 21 or newer.
- Desktop/laptop platform.
- LAN mode only over local network.
- Exactly two players per session.
- Mouse and keyboard input support.

## Implementation Decisions

These decisions are now locked for the first implementation pass.

1. Memory is out of scope.
   - Do not refactor the Memory game.
   - Do not change Memory's LAN host/join behavior.
   - Do not make TCP cleanup a standalone first milestone.
   - Tetris may reuse existing TCP classes where practical, but changes must not alter Memory behavior.
   - Keep Tetris protocol parsing separate so Tetris does not depend on Memory message constants.

2. Board size.
   - Use a standard Tetris board: 10 columns by 20 visible rows.
   - If hidden spawn rows are needed, keep them internal to the model and render only the visible 20 rows.

3. Standard block set.
   - Use the standard seven tetrominoes: `I`, `O`, `T`, `S`, `Z`, `J`, `L`.

4. Scoring formula.
   - Score exactly one point per cleared line.
   - Clearing multiple lines in one lock gives one point per cleared line, so two cleared lines gives two points.

5. Local key bindings.
   - Bottom player:
     - left/right arrows: move left/right,
     - down arrow: soft drop,
     - up arrow: rotate clockwise,
     - right shift or Enter: hard drop if hard drop is implemented.
   - Top player:
     - `A`/`D`: move left/right,
     - `W`: soft drop,
     - `S`: rotate clockwise,
     - Space: hard drop if hard drop is implemented.
   - Invalid key actions are ignored by the model validation rules.

6. LAN opponent connection.
   - Tetris LAN opponent discovery uses UDP broadcast/listen.
   - UDP discovery is only for Tetris in the first implementation pass.
   - Memory stays untouched and does not get UDP discovery.
   - Actual gameplay still uses TCP after a discovered opponent is selected.
   - Discovery entries must include at least player name, game type, host IP/address, TCP port, session id, and timestamp.

7. Win condition after one player loses.
   - Do not end the game immediately after the first player loses.
   - Store the losing player's score, stop only that player's block generation, and let the opponent continue.
   - End the game when both players have lost, then compare final scores.
   - Equal final scores produce a draw.

8. Custom connected-block design rules.
   - The editor validates non-empty connected shapes.
   - Cells must be orthogonally connected; diagonal-only contact does not count.
   - Use a small editor grid, normalize saved shapes, allow rotations, and add valid custom parts to the spawn bag.
   - Custom parts are session-only unless persistence is explicitly added later.

9. Special object behavior.
   - PDF requirement `FR-OUTPUT-12` is implemented as red special-object cells.
   - Every 4 seconds, each active board can spawn one special object in an empty visible cell.
   - Special objects spawn only on supported free tiles, not floating in open space or inside locked blocks.
   - Unused special objects expire after 10 seconds.
   - Hitting a teleport swap object swaps the players' boards and active pieces while keeping names, sides, and scores stable.

## Deferred Decisions

This remains on hold and should not block the first local/LAN core implementation.

1. Speed increase.
   - PDF requirement: `FR-FLOW-11` says speed gradually increases.
   - Current decision: keep speed increase on hold.
   - Acceptance risk: this requirement remains incomplete until a speed curve is implemented.

## Target Package Layout

Add Tetris code without mixing it into Memory packages:

- `src/main/java/seda_project/control_alt_defeat/gamebox/model/tetris/`
  - Pure game logic only.
  - No JavaFX and no network classes.
- `src/main/java/seda_project/control_alt_defeat/gamebox/controller/tetris/`
  - JavaFX menu and game controllers.
- `src/main/java/seda_project/control_alt_defeat/gamebox/network/tetris/`
  - Tetris-specific protocol, UDP discovery, TCP setup, and synchronization adapters.
- `src/main/resources/tetris/`
  - Tetris FXML and CSS.
- `src/test/java/seda_project/control_alt_defeat/gamebox/model/tetris/`
  - Unit tests for deterministic model behavior.
- `src/test/java/seda_project/control_alt_defeat/gamebox/network/tetris/`
  - Protocol serialization and synchronization tests.

Update `module-info.java` with Tetris exports and FXML opens after packages exist.

## Step-By-Step Roadmap

### Step 1: Add Requirement Traceability Skeleton

Goal: Create a visible map from requirements to code and tests before implementing behavior.

Actions:

1. Create a Tetris requirements checklist under `docs/` or keep this section updated in `implementation.md`.
2. Add requirement IDs to test class names or test method names where practical.
3. Keep ambiguous details in the clarification section rather than silently inventing behavior.

Files:

- `implementation.md`
- Optional: `docs/tetris_requirements_traceability.md`

Done when:

- Every `FR-*`, `QR-*`, and acceptance criterion has a planned implementation and test location.

### Step 2: Wire The Tetris Entry Point

Requirements covered:

- `FR-INPUT-01`
- `QR-USAB-01`

Actions:

1. Change `GameChoice.playTetris` to route to `/tetris/TetrisMenu.fxml`.
2. Replace the empty `TetrisMenu.fxml` with a menu that supports:
   - local mode selection,
   - LAN mode selection,
   - player name fields,
   - Tetris UDP opponent discovery/list area for LAN,
   - host mode that advertises the player over UDP and displays the host IP/port for debugging,
   - custom part design entry point,
   - start/cancel/back actions,
   - visible error/status label.
3. Implement `TetrisMenuController` menu validation:
   - local mode uses default names if fields are blank,
   - LAN mode requires a local player name before advertising/joining,
   - LAN mode cannot start before an opponent is connected,
   - errors are shown without crashing or switching scenes.
4. Add `tetris/TetrisMenu.css` or extend existing global CSS consistently.
5. Update `module-info.java` to open/export the Tetris controller package.

Edge cases:

- Blank local names: assign `Player 1` and `Player 2`.
- Blank LAN name: reject with a clear status message.
- Duplicate LAN names discovered: show both safely, using endpoint info internally.
- Tetris FXML load failure: fail with a visible error and log details.
- User presses Back while hosting/discovering: stop sockets/timers cleanly.

Tests:

- Controller-level tests if feasible, otherwise model-free manual test checklist:
  - Tetris button opens Tetris menu.
  - Memory button still opens Memory menu.
  - Local mode can start with blank names.
  - LAN mode rejects blank own name.

### Step 3: Build Pure Tetris Domain Types

Requirements covered:

- `FR-FLOW-03`
- `FR-FLOW-05`
- `FR-FLOW-06`
- `FR-FLOW-07`
- `FR-FLOW-08`
- `FR-FLOW-09`
- `FR-FLOW-10`
- `QR-MAIN-02`

Actions:

1. Add immutable/basic model types:
   - `TetrisCell`
   - `GravityDirection`
   - `PlayerSide`
   - `PieceType`
   - `PieceShape`
   - `TetrisPiece`
   - `TetrisBoard`
   - `TetrisPlayerState`
   - `TetrisGameState`
2. Make `TetrisBoard` own only settled cells and dimensions.
3. Make `TetrisPiece` own shape, rotation, and anchor position.
4. Represent top board orientation explicitly:
   - bottom and top board model gravity move downward,
   - the top board is rotated in the UI so the top player's blocks move upward visually.
5. Keep all validation in model methods:
   - `canPlace`
   - `moveLeft`
   - `moveRight`
   - `rotateClockwise`
   - `hardDrop` if implemented
   - `applyGravity`
   - `lockPiece`
6. Keep current piece generation deterministic in the controller until random generation is added.

Edge cases:

- Moving into left/right wall is rejected.
- Moving into settled cells is rejected.
- Rotation into wall or settled cells is rejected.
- Rotation that would leave the board is rejected unless a deliberate wall-kick rule is added.
- Top board visual movement appears upward because the rendered board is rotated.
- Top board display inversion must not corrupt model coordinates.
- A piece that cannot spawn triggers loss for that player.
- Model methods must not depend on JavaFX controls or threads.

Tests:

- Movement validation at all four board edges.
- Rotation validation near wall and near settled cells.
- Bottom and top model gravity increase row index.
- Top board visual gravity appears upward in the rotated UI.
- Locking transfers active piece cells to settled board.
- Spawn collision sets player lost state.

### Step 4: Implement Piece Generation And Custom Part Design

Requirements covered:

- `FR-FLOW-08`
- `FR-OUTPUT-11`

Actions:

1. Implement built-in piece definitions in a pure model class.
2. Add a `PieceGenerator` abstraction:
   - default generator uses built-in pieces,
   - custom generator can include user-defined connected pieces.
3. Build a custom part editor view:
   - small grid of toggleable cells,
   - name field,
   - preview,
   - validate/save/cancel controls.
4. Implement custom part validation:
   - at least one occupied cell,
   - all occupied cells are orthogonally connected,
   - shape fits the configured max editor size,
   - normalized shape cannot duplicate an existing saved custom shape unless duplicates are explicitly allowed.
5. Decide whether custom parts are session-only or persisted. The PDF does not require persistence, so default to session-only.

Edge cases:

- Empty design is rejected.
- Diagonal-only contact is not connected.
- Disconnected islands are rejected.
- Shape larger than board spawn area is rejected.
- Rotation of a custom part must be validated like built-in parts.
- Custom part that cannot spawn because of current board state triggers the same loss logic as built-in pieces.

Tests:

- Connected shape accepted.
- Empty shape rejected.
- Diagonal-only shape rejected.
- Multi-island shape rejected.
- Custom part appears in generator when saved.

### Step 5: Implement Line Detection, Clearing, And Scoring

Requirements covered:

- `FR-FLOW-12`
- `FR-FLOW-13`
- `FR-FLOW-14`
- `FR-OUTPUT-06`
- `FR-OUTPUT-07`
- `QR-PERF-03`
- `QR-RELY-03`
- `QR-RELY-04`

Actions:

1. Add `Board.fullRows()`.
2. Add `Board.clearRows(List<Integer> rows)`.
3. For bottom board:
   - remove completed rows,
   - shift rows above downward,
   - fill new empty rows at top.
4. For top inverted board:
   - use the same model clear rule as the bottom board,
   - keep the top board rotation in the UI so cleared rows still look correct for the inverted player.
5. Update score after clearing lines.
6. Keep scoring formula in the player/model state until scoring gets more complex.
7. Implement scoring as exactly one point per cleared line.

Edge cases:

- No completed rows leaves board unchanged.
- One completed row clears correctly.
- Multiple non-adjacent rows clear correctly.
- Multiple adjacent rows clear correctly.
- Clearing near spawn side does not shift blocks outside board.
- Score update occurs exactly once per lock.
- Clearing is completed within 1 second in UI.

Tests:

- Single line clear.
- Double/multiple line clear.
- No line clear.
- Score increases by one point per cleared line.
- Top board clear behavior matches the documented rotated-view rule.

### Step 6: Implement Loss, Continuation, Winner, Draw, And Restart Logic

Requirements covered:

- `FR-FLOW-15`
- `FR-FLOW-16`
- `FR-FLOW-17`
- `FR-FLOW-18`
- `FR-FLOW-19`
- `FR-FLOW-20`
- `FR-INPUT-06`
- `FR-OUTPUT-08`
- `FR-OUTPUT-10`

Actions:

1. Add per-player status:
   - active,
   - lost,
   - final score stored,
   - current speed level,
   - current active piece or none.
2. On spawn failure or threshold breach:
   - mark that player lost,
   - stop that player's gravity ticks,
   - store final score,
   - keep their board visible.
3. Continue ticking only the active opponent after one player loses.
4. End the game when both players have lost.
5. Compare scores and produce:
   - player 1 winner,
   - player 2 winner,
   - draw.
6. Display restart after game end.
7. Restart resets:
   - both boards,
   - scores,
   - active blocks,
   - speed,
   - player states,
   - custom pieces if session rules say they persist for the session.

Edge cases:

- Both players lose on the same tick.
- One player loses while the other is already resolving a line clear.
- Restart clicked twice quickly.
- Restart in LAN mode when remote player disconnected.
- Draw when final scores are equal.
- Winner display must match stored final scores, not a partially reset state.

Tests:

- First player loss stops only that player's generation.
- Opponent continues after first loss.
- Both lost then winner by higher score.
- Equal score draw.
- Restart fully resets state.

### Step 7: Implement Game Loop

Requirements covered:

- `FR-FLOW-08`
- `FR-FLOW-09`
- `FR-FLOW-10`
- `FR-OUTPUT-05`
- `QR-PERF-02`

Actions:

1. Use a JavaFX `AnimationTimer`, `Timeline`, or scheduled tick adapter that calls pure model tick methods.
2. Keep model tick deterministic:
   - the controller supplies elapsed time,
   - the model decides when gravity steps occur.
3. Maintain per-player timers so a lost player stops while the opponent continues.
4. Keep the speed constant in the first implementation pass.
5. Put speed-related values behind a `SpeedProfile` or constants class so `FR-FLOW-11` can be added later without rewriting the game loop.
6. Render only after model updates.
7. Pause input briefly only when needed for visual clarity; do not block the JavaFX application thread.

Edge cases:

- Long frame delay should not cause unbounded multi-step catch-up.
- Paused/lost player must not receive gravity ticks.
- Restart must reset accumulated tick time.
- LAN client should render synchronized state instead of independently deciding authoritative ticks unless it is explicitly the host-authoritative side.

Tests:

- Gravity tick moves active piece at the expected interval.
- Lost player no longer ticks.
- Restart resets speed and accumulated time.

Deferred speed note:

- `FR-FLOW-11` remains incomplete until speed increase is taken off hold.

### Step 8: Build The Tetris Game UI

Requirements covered:

- `FR-OUTPUT-01`
- `FR-OUTPUT-02`
- `FR-OUTPUT-03`
- `FR-OUTPUT-05`
- `FR-OUTPUT-06`
- `FR-OUTPUT-07`
- `FR-OUTPUT-08`
- `FR-OUTPUT-10`
- `QR-USAB-02`
- `QR-USAB-03`

Actions:

1. Add `tetris/TetrisGame.fxml`.
2. Add `TetrisGameController`.
3. Display both boards simultaneously.
4. Display the top player's board inverted.
5. Display player names and scores beside their boards.
6. Display current status:
   - running,
   - player lost,
   - connection lost,
   - game over,
   - winner/draw.
7. Add restart button visible only after game ends.
8. Add quit/back action.
9. Render active pieces and settled cells with distinct colors.
10. Leave swap-object rendering out of the first implementation pass.

Edge cases:

- Boards must remain visible at small window sizes.
- Top board inversion must be visual only unless intentionally mapped in model.
- Long player names should truncate or wrap without breaking layout.
- Restart button should not appear before game over.
- Input focus should stay on the game scene after buttons/menu interactions.

Manual verification:

- Open local game.
- Confirm both boards are visible at launch.
- Confirm top board appears inverted.
- Confirm names and scores appear with the correct board.
- Confirm movement updates visible active piece.
- Confirm line clear updates board and score.
- Confirm winner/draw screen and restart button appear after end.

### Step 9: Implement Local Two-Player Controls

Requirements covered:

- `FR-INPUT-02`
- `FR-INPUT-05`
- `FR-FLOW-01`
- `QR-USAB-02`

Actions:

1. Bind key pressed events at scene level in `TetrisGameController`.
2. Bottom player uses arrow keys.
3. Top player uses `WASD`.
4. Route input only to active/non-lost players.
5. Reject invalid moves via model validation and leave the active piece unchanged.
6. Leave counterclockwise rotation and hard drop out until explicitly added.

Edge cases:

- Both players pressing keys simultaneously.
- Key auto-repeat should not break movement validation.
- Input after game over ignored except restart/menu.
- Input for lost player ignored.
- Input focus on a button should not disable gameplay keys during active game.

Tests:

- Controller smoke/manual tests for key mapping.
- Model tests remain the source of truth for validity.

### Completed Step: Implement Swap Bugs

Requirements covered:

- `FR-OUTPUT-12`

Actions:

1. Add a bug position to each `TetrisPlayerState`.
2. Spawn a bug every 4 seconds on each active board when that board does not already have one.
3. Reject bug spawn positions inside settled cells or the active piece.
4. Detect collision when the active piece moves onto the bug.
5. Swap player boards and active pieces while preserving player names, sides, and scores.
6. Synchronize bug position and swap results through LAN snapshots.
7. Render the bug as a distinct red cell.

Edge cases:

- Bug spawn inside settled block is rejected.
- Bug spawn where the active piece already exists is rejected.
- At most one bug exists per board.
- Bugs are cleared when a piece locks or the player loses.
- LAN clients render host-authoritative snapshots and do not spawn bugs locally.

Tests:

- Valid spawn location.
- Collision triggers exactly one swap.
- Swap state is included in serialized game state.
- Swap after restart is reset.

### Step 10: Implement Tetris-Only UDP Discovery

Requirements covered:

- `FR-INPUT-03`
- `FR-INPUT-04`
- `FR-OUTPUT-04`
- `QR-USAB-04`

Scope boundary:

- UDP discovery is implemented only for Tetris.
- Memory is out of scope and keeps its existing host/join behavior.
- Discovery only finds candidates; connecting and gameplay synchronization happen later over TCP.

Actions:

1. Add `TetrisLanDiscoveryService` under the Tetris network package.
2. Add host advertising:
   - player enters their name,
   - app broadcasts a UDP availability message,
   - message includes player name, game type `TETRIS`, host address, intended TCP port, session id, and timestamp,
   - app keeps advertising until the user cancels, leaves the menu, or starts a LAN session.
3. Add join discovery:
   - player enters their name,
   - app listens for Tetris UDP availability messages,
   - app ignores non-Tetris messages,
   - app shows available opponents by player name,
   - app removes stale entries after a timeout.
4. Add opponent selection:
   - player selects a discovered opponent,
   - app stores the selected opponent's host, TCP port, player name, and session id for the later TCP connection step.
5. Show discovery errors in the menu without crashing.
6. Stop UDP discovery and UDP advertising cleanly if the user cancels or leaves the menu.

Edge cases:

- Empty player name.
- No opponents discovered.
- Duplicate player names.
- Stale advertisement from a closed app.
- Self advertisement must not appear as an opponent.
- Firewall blocks UDP broadcast/listen.
- Malformed discovery message.
- Multiple app instances on the same machine.
- Duplicate player names are allowed but still shown as distinct sessions/endpoints.

Tests:

- Discovery message encode/decode.
- Non-Tetris discovery messages ignored.
- Stale discovery entries expire.
- Duplicate names retain unique endpoints/session ids.
- Self advertisement ignored.
- Cancel/leave stops broadcaster and listener threads.
- Manual integration test on two machines or two app instances if networking allows.

### Step 11: Implement Tetris LAN Gameplay Synchronization

Requirements covered:

- `FR-FLOW-02`
- `FR-FLOW-04`
- `FR-FLOW-21`
- `FR-FLOW-22`
- `FR-OUTPUT-09`
- `QR-PERF-01`
- `QR-RELY-02`

Actions:

1. Connect to the selected UDP discovery result using TCP.
2. Add a simple TCP handshake:
   - host sends host player name,
   - client sends client player name,
   - both sides confirm names and assigned player ids.
3. Add `TetrisProtocol` separate from Memory protocol.
4. Define message types for:
   - `HELLO` or `JOIN` with player name,
   - `START` with initial game config and seed,
   - `INPUT` with player id, action, and sequence number,
   - `STATE` snapshot with board, active piece, scores, speed, statuses, names, game result,
   - `LOSS`,
   - `GAME_OVER`,
   - `RESTART_REQUEST`,
   - `RESTART_STATE`,
   - `QUIT`,
   - `ERROR`.
5. Choose one authority model:
   - recommended: host is authoritative for game state.
6. Client sends input commands, not arbitrary board state.
7. Host validates input, advances ticks, and sends snapshots/deltas.
8. Client applies snapshots and renders.
9. Use sequence numbers to ignore stale input/snapshots.
10. On disconnect:
   - stop the LAN session safely,
   - show a connection error,
   - do not restart automatically.

Edge cases:

- Malformed protocol line.
- Unknown message type.
- Out-of-order or duplicate messages.
- Selected opponent becomes unavailable before TCP connect.
- Client tries to connect before host is listening.
- Host cancels while client is connecting.
- Two clients try to connect to the same host.
- Duplicate player names are allowed but still assigned distinct player ids.
- Input from wrong player id.
- Client input after game over.
- Restart request while disconnected.
- Host closes window mid-game.
- Client closes window mid-game.
- Snapshot contains incompatible dimensions or invalid state.

Tests:

- Handshake message serialization.
- Duplicate names do not break player-id assignment.
- Protocol encode/decode round trip.
- Malformed message rejected without crash.
- Host rejects invalid client input.
- Snapshot apply keeps client board equal to host board.
- Disconnect callback stops session and exposes error state.

### Step 12: Implement Restart For Local And LAN

Requirements covered:

- `UC-08`
- `FR-INPUT-06`
- `FR-FLOW-20`
- `FR-OUTPUT-08`
- `FR-FLOW-22`

Actions:

1. Local restart:
   - reset both boards, scores, blocks, speed, and player states.
   - preserve player names.
   - preserve custom parts if session-only custom parts are intended to remain available.
2. LAN restart:
   - restart only when connected.
   - host creates new authoritative state.
   - client receives restart state.
   - show connection error instead of restarting if disconnected.
3. Disable restart button after first click until new state is visible.

Edge cases:

- Restart clicked by both LAN players at almost the same time.
- Restart clicked while disconnect is being handled.
- Restart after draw.
- Restart after first player loss but before final game over should not be available unless explicitly added.

Tests:

- Local restart resets model.
- LAN restart sends and applies new state.
- LAN restart rejected after disconnect.

### Step 13: Add Automated Test Coverage

Requirements covered:

- `QR-MAIN-03`
- All non-deferred high-priority functional requirements need testable behavior.

Actions:

1. Add model tests first:
   - mode-independent game setup,
   - movement validation,
   - rotation validation,
   - gravity direction,
   - line clear,
   - score update,
   - loss detection,
   - continuation after one loss,
   - winner/draw,
   - restart,
   - custom part validation.
2. Add protocol tests:
   - encode/decode,
   - invalid message handling,
   - snapshot consistency.
3. Add limited controller tests only where practical.
4. Keep manual LAN acceptance tests documented because full LAN UI automation may be expensive.

Edge cases to explicitly test:

- Invalid move at each wall.
- Invalid rotation at wall and settled-cell collision.
- Multiple line clears in one lock.
- Spawn collision at losing threshold.
- Both players lose in the same tick.
- LAN malformed message.
- LAN disconnect before restart.
- Custom disconnected shape rejected.
- Tetris UDP discovery with duplicate player names.
- Tetris TCP handshake after opponent selection.

Commands:

```sh
mvn test
mvn javafx:run
```

Done when:

- `mvn test` passes.
- Manual local full-game checklist passes.
- Manual LAN full-game checklist passes on two app instances or two machines.

### Step 14: Update Documentation And Build Artifacts

Requirements covered:

- `QR-MAIN-01`
- Acceptance criteria.

Actions:

1. Update `README.md` with:
   - Tetris run instructions,
   - local controls,
   - Tetris UDP discovery and TCP host/join flow,
   - custom part editor behavior,
   - restart behavior,
   - known LAN firewall note if needed.
2. Update `ARCHITECTURE.md` with:
   - Tetris model/controller/network packages,
   - LAN authority model,
   - protocol summary.
3. Add or update requirements docs if the team maintains generated PDFs.
4. Run:

```sh
mvn test
mvn javafx:jlink
```

Done when:

- Build and tests pass.
- Run instructions are reproducible from a clean checkout.
- Acceptance criteria are demonstrably covered by tests or manual checklist.

## Requirement Traceability Matrix

| Requirement | Implementation Area | Test/Verification |
| --- | --- | --- |
| `FR-INPUT-01` | `TetrisMenuController`, `TetrisMenu.fxml` | Menu smoke/manual test |
| `FR-INPUT-02` | Local start validation | Unit/controller test for default names |
| `FR-INPUT-03` | LAN menu player name field | Controller/manual test |
| `FR-INPUT-04` | Tetris-only UDP opponent discovery and selection | Discovery tests/manual LAN |
| `FR-INPUT-05` | `TetrisGameController` key handlers and model movement methods | Model movement tests/manual input |
| `FR-INPUT-06` | Game-over restart button | Restart tests/manual |
| `FR-FLOW-01` | Local session creation | Local start test |
| `FR-FLOW-02` | UDP discovery followed by TCP host/join connection | LAN integration/manual |
| `FR-FLOW-03` | `TetrisGameState`, `TetrisPlayerState` | Game setup tests |
| `FR-FLOW-04` | Tetris discovery, TCP handshake, and player identity mapping | Discovery/protocol/handshake tests |
| `FR-FLOW-05` | `TetrisBoard.canPlace`, `TetrisPlayerState.moveLeft`, `TetrisPlayerState.moveRight`, `TetrisPlayerState.softDrop` | Movement validation tests |
| `FR-FLOW-06` | `TetrisBoard.canPlace`, `TetrisPlayerState.rotateClockwise` | Rotation validation tests |
| `FR-FLOW-07` | Validation rejection paths | Invalid action tests |
| `FR-FLOW-08` | `TetrisGameController` game loop and deterministic piece sequence | Spawn/tick tests |
| `FR-FLOW-09` | `PlayerSide`, `GravityDirection`, rotated top board view | Top/bottom gravity tests |
| `FR-FLOW-10` | `lockPiece` | Lock tests |
| `FR-FLOW-11` | Deferred: speed increase on hold; keep constants ready | Not complete in first pass |
| `FR-FLOW-12` | `TetrisBoard.fullRows` | Line detection tests |
| `FR-FLOW-13` | `TetrisBoard.clearRows` | Line clear/shift tests |
| `FR-FLOW-14` | `TetrisPlayerState.lockActivePiece`: one point per cleared line | Score tests |
| `FR-FLOW-15` | `TetrisPlayerState.spawnPiece`, `TetrisPlayerState.lost` | Loss tests |
| `FR-FLOW-16` | `PlayerStatus.LOST` | Lost player tick tests |
| `FR-FLOW-17` | `TetrisPlayerState.finalScore` | Loss score tests |
| `FR-FLOW-18` | Continuation after loss | Continuation tests |
| `FR-FLOW-19` | `TetrisGameController.resultText` | Winner/draw tests |
| `FR-FLOW-20` | `TetrisGameController.startNewGame` | Restart tests |
| `FR-FLOW-21` | `TetrisProtocol`, host snapshots | Protocol/sync tests |
| `FR-FLOW-22` | Disconnect handlers | Disconnect tests/manual |
| `FR-OUTPUT-01` | `TetrisGame.fxml` board layout | Manual UI verification |
| `FR-OUTPUT-02` | Top board rendering transform | Manual UI plus orientation test |
| `FR-OUTPUT-03` | Name/score labels | Manual UI verification |
| `FR-OUTPUT-04` | Tetris LAN opponent list populated from UDP discovery | Discovery/manual LAN |
| `FR-OUTPUT-05` | Board renderer refresh | Manual UI/model state test |
| `FR-OUTPUT-06` | Renderer after line clear | Manual UI/model test |
| `FR-OUTPUT-07` | Score labels | Score/manual UI |
| `FR-OUTPUT-08` | Restart button after game end | UI/manual |
| `FR-OUTPUT-09` | LAN synchronized render | Manual LAN/protocol |
| `FR-OUTPUT-10` | Winner/draw result view | Winner/manual UI |
| `FR-OUTPUT-11` | Custom part editor | Validation tests/manual UI |
| `FR-OUTPUT-12` | 4-second special objects in `TetrisGameState` and `TetrisGameController` | Model/snapshot tests/manual UI |
| `QR-USAB-01` | Clear menu mode choices | Manual UI |
| `QR-USAB-02` | Simple keyboard controls | Manual input |
| `QR-USAB-03` | Boards/names/scores visible | Manual UI |
| `QR-USAB-04` | Opponent list by player name for Tetris LAN | Discovery/manual LAN |
| `QR-PERF-01` | Host authoritative snapshots | Manual LAN latency check |
| `QR-PERF-02` | JavaFX game loop/rendering | Manual smoothness check |
| `QR-PERF-03` | Efficient line clear | Unit/performance check |
| `QR-RELY-01` | Full local game | Manual acceptance |
| `QR-RELY-02` | LAN state consistency | LAN sync tests/manual |
| `QR-RELY-03` | Score model | Score tests |
| `QR-RELY-04` | Board clear model | Line clear tests |
| `QR-MAIN-01` | README/build docs | `mvn test`, `mvn javafx:jlink` |
| `QR-MAIN-02` | Package separation | Code review |
| `QR-MAIN-03` | Automated tests | `mvn test` |

## Core First-Pass Acceptance Checklist

Do not call the first-pass implementation complete until all of these pass:

1. A local two-player game starts from the Tetris menu.
2. Both local players can control independent boards with separate keyboard controls.
3. Both boards are visible at the same time.
4. The top board is visibly inverted and its blocks move upward.
5. Player names and scores are visible and attached to the correct boards.
6. Invalid movement and rotation are rejected without changing the piece state.
7. Blocks spawn, fall, lock, and continue for each active player.
8. Completed lines clear and blocks shift to fill the cleared space.
9. Scores update consistently after line clears.
10. A player's loss stops only that player's block generation.
11. The opponent continues after the first player loses.
12. Winner or draw is shown after both players have lost.
13. Restart resets game state and starts a new game.
14. LAN menu allows player name entry.
15. Tetris LAN host mode advertises the player through UDP discovery.
16. Tetris LAN join mode displays available opponents by player name.
17. LAN game starts only after opponent selection, TCP connection, and name handshake succeed.
18. LAN board, score, active status, names, and result stay synchronized.
19. LAN disconnect shows an error and stops the session safely.
20. Custom connected blocks can be designed and invalid disconnected blocks are rejected.
21. `mvn test` passes.
22. `mvn javafx:jlink` passes.

## Deferred Acceptance Items

These are required by the PDF but intentionally not part of the first-pass plan:

1. `FR-FLOW-11`: speed gradually increases during gameplay.

## Suggested Implementation Order

Completed setup work:

1. Tetris menu route and module exports.
2. Local/LAN menu flow.
3. UDP discovery and TCP join/start handshake.
4. Tetris setup/config routing.
5. Two-board game screen.
6. Pure model foundation, movement, rotation, gravity, lock, line clear, score, loss, restart.
7. Local game loop and keyboard-controlled model rendering.
8. Custom connected-block editor and validation.
9. Tetris-specific LAN protocol messages.
10. Host-authoritative LAN gameplay synchronization.
11. LAN restart/disconnect handling.
12. 4-second special object spawning, collision, rendering, and LAN snapshot sync.
13. README/build docs.
14. Focused model/protocol tests.

Next implementation order:

1. Run final automated and manual acceptance checks.

Current verification notes:

- `mvn -q -DskipTests compile` passes.
- `mvn -q -DskipTests test-compile` passes.
- `mvn -q -DskipTests javafx:jlink` passes.
- Same-machine UDP discovery smoke through `TetrisLanDiscoveryService` passes.
- Bug swap and snapshot smoke passes.
- Full `mvn test` was not run because the user explicitly asked not to run it.
- Interactive local/LAN manual acceptance still needs a human two-instance check.
