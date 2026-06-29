# GameBox – SEDA Project

This repository contains GameBox, a JavaFX desktop app by team **Zero Runtime Warranty**. It includes the Memory game, a fully-featured two-player Zetris implementation, and Chexsagon, a Glinski hexagonal chess implementation.

## Features

### Memory

- **Configurable K-Tuple** (1–45): Define the exact number of identical cards required to form a valid match.
- **Dynamic Grid Generation**: The system automatically computes three optimally balanced board sizes (Large, Medium, and Small) based on your chosen matching size.
- **Local Hot-Seat Mode**: Two players can share a single device and take turns.
- **Network Multiplayer (LAN)**: Host and client play seamlessly over a local network via TCP sockets, featuring instant state synchronization.
- **Robust Network Lifecycle**: Includes safe hosting cancellation, synchronized "Play Again" transitions, and graceful disconnect handling to prevent UI lockups or thread leaks.
- **Emoji Card Faces**: Features bright, easily distinguishable emoji symbols for a colorful playing experience.

### Zetris

#### Core Gameplay
- **Local Two-Player Mode**: Two players share one keyboard with separate controls.
- **LAN Mode**: Host advertises over UDP, joiner selects the host, and gameplay state is synchronized over TCP.
- **Two Boards**: A bottom board and an inverted top board are visible simultaneously.
- **Gameplay Flow**: Pieces spawn, move, rotate, fall, lock, clear lines, score, gradually speed up during the match, detect loss, continue after one player loses, show winner/draw, and restart.
- **Random Block Colors**: Each spawned block is assigned one random color (from a curated palette of 7) that stays with it after locking.
- **LAN Quit Handling**: If an opponent leaves, the game shows a clear message and automatically returns to the Zetris menu after a short delay.

#### Piece Configuration
- **Standard Pieces**: The classic 7 Tetromino shapes (I, O, T, S, Z, J, L).
- **Custom Pieces**: Before a local or hosted game, players can draw any connected shape on a 5×5 grid and save it for use in the session. Multiple custom pieces can be saved.
- **Dual Pieces**: An optional mode that combines two consecutive shapes into a single wider piece for a different challenge.
- **Mixed Sets**: Standard and Custom piece sets can be enabled independently or together.

#### Horizontal Mode
- **Sideways Gravity**: An alternative layout where pieces fall from left to right (bottom player) or right to left (top player), using a 10×20 rotated board.
- **Column Clears**: In horizontal mode, full columns are cleared instead of full rows, and line-clear rewards/penalties are applied in the horizontal direction.

#### Configurable Speed
- **Slow / Normal / Fast**: Three speed presets (750 ms, 550 ms, 320 ms gravity interval) selectable before each game.
- **Progressive Speed Ramp**: Every 15 seconds, the base gravity interval decreases by 20 ms, down to a minimum of 80 ms, so matches intensify over time.
- **Per-Player Effect Overrides**: Speed-up and slow-down special objects independently alter each player's effective gravity, clamped to a minimum of 80 ms.

#### Line-Clear Interactions
- Clearing lines **rewards** the clearing player with extra board rows (or columns in horizontal mode) on their own side.
- Clearing lines **penalizes** the opponent by removing rows (or columns) from their board, applied from the side closest to their spawn point.

#### Special Objects
Every 4 seconds, each active board may spawn one special object on a free tile that has a solid support beneath it and a clear approach path from the spawn direction. Objects expire after 10 seconds (100 game ticks). When a falling piece lands on an object tile, the object's effect is triggered immediately.

| Symbol | Name | Effect |
|:---:|:---|:---|
| `+` | Speed Up Opponent | Doubles the opponent's gravity speed for 10 seconds |
| `S` | Slow Opponent | Halves the opponent's gravity speed for 10 seconds |
| `-` | Slow Self | Halves your own gravity speed for 10 seconds |
| `R` | Rotation Delay (Opponent) | Adds a 2-second input lag to all rotation inputs on the opponent's board for 10 seconds |
| `r` | Rotation Delay (Self) | Adds a 2-second input lag to all rotation inputs on your own board for 10 seconds |
| `*` | Explosion (Radius) | Destroys all locked cells within a radius-3 circle around the impact point |
| `v` | Explosion (Below) | Destroys all locked cells along the gravity direction from the impact point to the board edge |
| `P` | Portal | Removes your active piece and queues its shape as the opponent's very next piece |
| `T` | Teleport Swap | Swaps both players' entire boards and active pieces without affecting scores |
| `G` | Piece Swap | Swaps only the two players' currently active pieces (shape and color), placing them at the same position on each board |

Objects that require an active opponent (those marked with `+`, `S`, `R`, `P`, `T`, `G`) are ineligible for spawning if the opponent has already lost; only self-affecting objects (`-`, `r`, `*`, `v`) can still appear in that case.

Special objects are drawn from a shuffled bag per player so that each type is seen in a roughly even rotation.

#### LAN Architecture
- The **host** owns the authoritative game state and game loop.
- The **joiner** sends keyboard input commands over TCP; the host applies them and broadcasts the full state snapshot back.
- LAN uses UDP (port `54322`) for host discovery and TCP (port `54321`) for gameplay.
- If a LAN host does not appear in the join list, allow Java through the firewall for both ports, then use **Refresh Games**.

### Chexsagon / Hex Chess

#### Variant and Rules
- **Glinski Variant**: Played on a 91-cell hexagonal board with files `a` through `l`, skipping `j`.
- **Standard Material**: Each side starts with 18 pieces: one king, one queen, two rooks, three bishops, two knights, and nine pawns.
- **Legal Move Engine**: The game validates piece movement, check, checkmate, stalemate, en passant, pawn double moves, and illegal self-check.
- **Promotion**: Pawns promote on starred promotion files, with a choice of queen, rook, bishop, or knight.
- **No Castling**: Castling is not part of the implemented Glinski ruleset.
- **Draw Handling**: Supports accepted draw offers, the 50-move rule, threefold repetition, insufficient material, stalemate scoring, and resignation.

#### Play Modes
- **Local Hot-Seat Mode**: Two players share one device and alternate turns on the same board.
- **Bot Mode**: The human plays White and the bot plays Black. Bot calculations run off the JavaFX UI thread.
- **LAN Multiplayer**: Host controls White, joiner controls Black, and the host broadcasts authoritative state snapshots.
- **Custom Position Builder**: Build a legal starting position by placing standard pieces, choosing the side to move, and passing validation before starting.

#### Board UI and Actions
- **Canvas Board**: The hex board is rendered on a JavaFX canvas with coordinate labels and starred promotion cells.
- **Move Feedback**: Selecting a piece highlights legal destinations, the last move is marked, and checked kings are highlighted.
- **Match Actions**: Players can offer, accept, or decline draws, resign, restart local games, or return to the Chexsagon menu.

#### LAN Architecture
- The **host** owns the authoritative Hex Chess state.
- The **joiner** sends moves and draw/resign commands over TCP; the host applies them and broadcasts snapshots.
- LAN discovery uses UDP port `54324`.
- Gameplay uses TCP port `54321` by default; if that port is occupied, hosting falls back to another available TCP port and displays it in the host status.
- Direct join supports manually entering both host IP and TCP port.

## Prerequisites

- Java 25+
- Maven 3.9+
- A LaTeX distribution with `pdflatex` available on your PATH (e.g., miktex or texlive)
- Optional: `gitlab-ci-local` (Requires docker)

## IntelliJ Setup (Recommended)

This project works with any IDE or platform, but we recommend IntelliJ.
The ultimate version is free for students, but the community edition also works fine.
IntelliJ makes debugging, building and testing straightforward and integrates well with Maven and JavaFX.
IntelliJ can be downloaded from their website or using the JetBrains Toolbox or with package managers like `winget` or `snap`.
Visit the installation guide for additional information.

You can do most setup directly in IntelliJ:
- **Maven** is bundled with IntelliJ, so you do not need to install Maven manually.
- **Java** can be downloaded via IntelliJ (`Ctrl+Alt+Shift+S` > `SDKs` > `+` > `Download JDK`). This avoids changing your global PATH on your machine.

### Recommended IntelliJ Plugins

For this project, we recommend:
- **Maven** (usually enabled by default)
- **JUnit** (usually enabled by default)
- **JavaFX**
- **TeXiFy-IDEA**

Install plugins from `Ctrl+Alt+S` > `Plugins`.

## Build the Project

Run from the repository root:

```sh
mvn test javafx:jlink
```

This compiles the project, runs tests, and creates artifacts in `target/`.

For a quick compile without tests:

```sh
mvn -DskipTests compile
```

## Build the Docs

The project includes a `docs` Maven profile that compiles `docs/requirements_memory.tex` to PDF.

```sh
mvn generate-resources -P docs
```

Generated PDF output is copied to `target/docs/` and `target/docs/latex/`.

## Run the Project

Use the JavaFX Maven plugin:

```sh
mvn javafx:run
```

## How to Play

### Memory Local Game
1. Enter your desired **k** value (1–45) and click **Apply**.
2. Select your preferred board size (**Large**, **Medium**, or **Small Board**).
3. Click **Local 2-Player**. Both players will take turns clicking cards on the same screen.

### Memory Network Game
**For the Host:**
1. Configure your game settings (k value and board size).
2. Click **Host Network Game**.
3. Share the displayed IP address with the second player. The game will automatically begin once they connect.

**For the Client:**
1. Click **Join Network Game**.
2. Enter the Host's IP address and click **Connect**.
3. You will be automatically placed into the game as soon as the connection is established.

### Zetris Local Game
1. From the game choice screen, choose **Zetris**.
2. Choose **Local**.
3. Enter both player names.
4. Configure the match:
   - **Piece Set**: Check **Standard** and/or **Custom**. If Custom is selected, draw pieces in the 5×5 grid editor and click **Save Piece** before starting.
   - **Dual Pieces**: Toggle to combine consecutive pieces into wider shapes.
   - **Horizontal Mode**: Toggle to rotate gravity 90° so pieces fall sideways.
   - **Speed**: Select Slow, Normal, or Fast.
5. Click **Start Local**.

**Controls (Local):**

| Player | Move Left | Move Right | Soft Drop | Rotate |
|:---|:---:|:---:|:---:|:---:|
| Bottom | `←` | `→` | `↓` | `↑` |
| Top | `A` | `D` | `W` | `S` |

### Zetris LAN Game
**For the Host:**
1. Choose **Zetris**.
2. Choose **LAN**, then **Host**.
3. Enter your name and configure the match settings (piece set, dual pieces, horizontal mode, speed).
4. Click **Start Host**. The host is now visible on the LAN.
5. Wait for the joiner to appear in the **Joined** field.
6. Click **Start Game**.

**For the Joiner:**
1. Choose **Zetris**.
2. Choose **LAN**, then **Join**.
3. Enter your name.
4. Select the available host from the list and click **Join Game** (or double-click the entry).
5. Wait for the host to start the match.

Zetris LAN uses UDP only for discovery. The gameplay connection uses TCP, with the host owning the authoritative game state and the joiner sending input commands.
If a LAN host does not appear, allow Java through the firewall for UDP port `54322` and TCP port `54321`, then use **Refresh Games** from the join screen.

### Chexsagon Local Game
1. From the game choice screen, choose **Chexsagon - Glinski Variant**.
2. Enter both player names.
3. Click **Start Local**.
4. Click one of the current player's pieces to highlight legal destinations.
5. Click a highlighted destination to move. If a pawn reaches promotion, choose queen, rook, bishop, or knight in the promotion dialog.
6. Use **Offer Draw**, **Accept**, **Decline**, **Resign**, **Restart**, or **Menu** from the side panel as needed.

### Chexsagon Bot Game
1. Choose **Chexsagon - Glinski Variant**.
2. Enter the White player name.
3. Click **Play Bot**.
4. Play White by selecting pieces and destinations; the bot replies automatically as Black.

### Chexsagon Custom Setup
1. Choose **Chexsagon - Glinski Variant**.
2. Click **Custom Setup**.
3. Choose a piece color and type, then left-click board cells to place pieces.
4. Remove pieces by right-clicking a cell, pressing **Remove Piece**, or using `Delete` / `Backspace` after selecting a cell.
5. Choose which color moves first.
6. Use **Standard** to reload the Glinski starting position or **Clear** to empty the board.
7. Click **Start** once the validator reports a legal position.

The custom setup validator requires exactly one king per side, legal king placement, no pawns on promotion squares, no more than nine pawns or 18 pieces per side, a valid side-to-move state, and at least one legal move for the side to move.

### Chexsagon LAN Game
**For the Host:**
1. Choose **Chexsagon - Glinski Variant**.
2. Enter the White player name.
3. Click **Host LAN**.
4. Wait for the joiner. The game opens automatically once a client connects.
5. If discovery does not work, share the displayed host IP and TCP port with the joiner.

**For the Joiner:**
1. Choose **Chexsagon - Glinski Variant**.
2. Enter the Black player name.
3. Select a discovered host and click **Join Selected**, or enter the host IP and TCP port under **Direct Join**.
4. Click **Join LAN** when using direct join.

Chexsagon LAN uses UDP only for discovery on port `54324`. Gameplay uses TCP, with the host controlling White and the joiner controlling Black.
If a LAN host does not appear, allow Java through the firewall for UDP port `54324` and the displayed TCP gameplay port, then use **Refresh** from the Chexsagon menu.
