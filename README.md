# GameBox – SEDA Project

This repository contains GameBox, a JavaFX desktop app by team **Zero Runtime Warranty**. It currently includes the Memory game and an in-progress two-player Zetris implementation.

## Features

### Memory

- **Configurable K-Tuple** (1–45): Define the exact number of identical cards required to form a valid match.
- **Dynamic Grid Generation**: The system automatically computes three optimally balanced board sizes (Large, Medium, and Small) based on your chosen matching size.
- **Local Hot-Seat Mode**: Two players can share a single device and take turns.
- **Network Multiplayer (LAN)**: Host and client play seamlessly over a local network via TCP sockets, featuring instant state synchronization.
- **Robust Network Lifecycle**: Includes safe hosting cancellation, synchronized "Play Again" transitions, and graceful disconnect handling to prevent UI lockups or thread leaks.
- **Emoji Card Faces**: Features bright, easily distinguishable emoji symbols for a colorful playing experience.

### Zetris

- **Local Two-Player Mode**: Two players share one keyboard with separate controls.
- **LAN Mode**: Host advertises over UDP, joiner selects the host, and gameplay state is synchronized over TCP.
- **Custom Pieces**: Local/host setup can define session-only connected custom blocks.
- **Two Boards**: Bottom board and inverted top board are visible at the same time.
- **Gameplay Flow**: Pieces spawn, move, rotate, fall, lock, clear lines, score, detect loss, continue after one player loses, show winner/draw, and restart.
- **Special Objects**: Every 4 seconds, each active board can spawn a shuffled-bag special object on a supported free tile. Objects expire after 10 seconds, timed effects last 10 seconds, speed effects are 2x, and swaps exchange boards/pieces without changing scores.
- **Random Block Colors**: Each spawned block gets one random color that stays with it after locking.
- **LAN Quit Handling**: If an opponent leaves, the game shows a clear message and returns to the Zetris menu.

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
4. Choose Standard or Custom pieces.
5. Click **Start Local**.

Controls:

- Bottom player: Left/Right to move, Down to move forward, Up to rotate.
- Top player: A/D to move, W to move forward, S to rotate.

### Zetris LAN Game
**For the Host:**
1. Choose **Zetris**.
2. Choose **LAN**, then **Host**.
3. Enter your name and click **Start Host**.
4. Wait for the joiner to appear.
5. Click **Start Game**.

**For the Joiner:**
1. Choose **Zetris**.
2. Choose **LAN**, then **Join**.
3. Enter your name.
4. Select the available host and click **Join Game**.
5. Wait for the host to start the match.

Zetris LAN uses UDP only for discovery. The gameplay connection uses TCP, with the host owning the authoritative game state and the joiner sending input commands.
If a LAN host does not appear, allow Java through the firewall for UDP port `54322` and TCP port `54321`, then use **Refresh Games** from the join screen.
