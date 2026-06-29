# AI Usage

AI tools were used as a development aid throughout the project. They helped with architecture planning, debugging, edge-case discovery, test generation, and documentation, while the team remained responsible for integrating the results into the JavaFX/Maven codebase.

## General Development Support

- Discussed JavaFX structure, MVC boundaries, controller event flow, FXML routing, and UI state handling.
- Helped identify likely causes of compiler errors, Maven/JavaFX configuration issues, runtime bugs, and networking problems.
- Suggested edge cases for game logic, UI behavior, LAN setup, disconnect handling, and restart flows.
- Helped plan and refine the networking flow for host/client synchronization.

## Game Research

AI assistance was also used to research existing public implementations and turn that research into practical roadmaps for the individual games.

For Chexsagon / Hex Chess, we looked into:

- [scottbedard/hexchess](https://github.com/scottbedard/hexchess), as a mature Glinski hexagonal chess rules engine and architecture reference.
- [theonlytechnohead/Hexchess](https://github.com/theonlytechnohead/Hexchess), as a working web-based Hex Chess implementation and interaction reference.

Those references helped us build a Hex Chess milestone map for:

- board geometry and coordinate notation
- Glinski starting position and material counts
- piece movement and pawn-specific rules
- legal move validation and self-check prevention
- promotion, en passant, check, checkmate, stalemate, and draw conditions
- separation between model rules, JavaFX controllers, canvas rendering, and LAN synchronization

For Zetris / Tetris, we looked into:

- [BenJeau/JavaFX-Tetris](https://github.com/BenJeau/JavaFX-Tetris), as a JavaFX falling-block game reference.
- [HanSolo/tetris](https://github.com/HanSolo/tetris), as another JavaFX Tetris implementation reference for game loop, levels, rendering, and controls.

Those references helped us reason about:

- piece spawning, movement, rotation, locking, and line clearing
- gravity timing, speed changes, and game-loop structure
- JavaFX rendering approaches for grid-based games
- keyboard input handling and pause/restart behavior
- how to extend a classic Tetris loop into the project's two-player Zetris variant

For Memory, we looked into:

- [lturpinat/Memory](https://github.com/lturpinat/Memory), as a JavaFX memory game reference.
- [hellocodeclub/memorygame](https://github.com/hellocodeclub/memorygame), as a small Java memory-card implementation reference.
- [hemtri1984/MatchingCardMemoryGame](https://github.com/hemtri1984/MatchingCardMemoryGame), as another public matching-card memory game reference.

Those references helped us reason about:

- card state, face-up/face-down transitions, and match detection
- board generation and symbol pairing
- turn flow, score handling, and end-of-game detection
- UI feedback for selected, matched, and hidden cards
- how to adapt normal pair matching into the project's configurable K-tuple matching mode

## Documentation and QA

- The README updates were generated using AI, then reviewed and kept consistent across both project copies.
- AI was used as a strict QA reviewer to challenge assumptions, check implementation claims against the code, find missing edge cases, and verify documentation consistency.

## Tests

The test classes were straight-up AI-generated. We used them to validate expected behavior around board geometry, Glinski material setup, pawn movement, en passant, promotion, draw offers, network serialization, and JavaFX smoke coverage.
