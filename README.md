# Multi Match Memory Game – SEDA Project

This repository contains a fully functional, configurable k-tuple Memory card matching game built with Java 25 and JavaFX. This project serves as the SEDA master track submission by team **Zero Runtime Warranty**.

## Features

- **Configurable K-Tuple** (1–45): Define the exact number of identical cards required to form a valid match.
- **Dynamic Grid Generation**: The system automatically computes three optimally balanced board sizes (Large, Medium, and Small) based on your chosen matching size.
- **Local Hot-Seat Mode**: Two players can share a single device and take turns.
- **Network Multiplayer (LAN)**: Host and client play seamlessly over a local network via TCP sockets, featuring instant state synchronization.
- **Robust Network Lifecycle**: Includes safe hosting cancellation, synchronized "Play Again" transitions, and graceful disconnect handling to prevent UI lockups or thread leaks.
- **Emoji Card Faces**: Features bright, easily distinguishable emoji symbols for a colorful playing experience.

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

### Local Game (Hot-Seat)
1. Enter your desired **k** value (1–45) and click **Apply**.
2. Select your preferred board size (**Large**, **Medium**, or **Small Board**).
3. Click **Local 2-Player**. Both players will take turns clicking cards on the same screen.

### Network Game
**For the Host:**
1. Configure your game settings (k value and board size).
2. Click **Host Network Game**.
3. Share the displayed IP address with the second player. The game will automatically begin once they connect.

**For the Client:**
1. Click **Join Network Game**.
2. Enter the Host's IP address and click **Connect**.
3. You will be automatically placed into the game as soon as the connection is established.
