# Hex Chess Implementation Roadmap

## Source Of Truth

- Local slide deck: `C:\Users\kevka\Downloads\Hexagon Chess.pdf`
- Variant: Glinski hexagonal chess
- Verified essentials:
  - 91-cell board, files `a` through `l` without `j`
  - Three bishops and nine pawns per side
  - No castling
  - Pawns move vertically forward
  - Pawns capture one orthogonal-forward cell at 60 degrees to vertical
  - Promotion offers queen, rook, bishop, or knight

## Architecture

- Model:
  - `HexBoard`: immutable board contents
  - `HexStartingPosition`: standard Glinski setup
  - `HexMoveRules`: shared constants and special move helpers
  - `HexMoveGenerator`: pseudo-legal moves
  - `HexLegalMoveValidator`: check and legal-move filtering
  - `HexGameEndDetector`: checkmate, stalemate, and draw outcomes
  - `HexPositionValidator`: custom setup validation
  - `HexGameState`: state transition orchestration
- Controller:
  - JavaFX controllers keep routing, input, rendering, and network UI behavior
- Network:
  - TCP still uses the shared `GameServer` / `GameClient`
  - Hex Chess LAN discovery uses a separate UDP service modeled after Tetris discovery

## Current Status

- Corrected standard Glinski starting position.
- Corrected pawn capture directions.
- Refactored rule-model split out of `HexGameState`.
- Strengthened custom setup validation.
- Added Hex Chess LAN discovery without modifying Tetris.
- Replaced Hex Chess board node rendering with canvas rendering.
- Added promotion choice for queen, rook, bishop, or knight.
- Fixed draw-offer ownership so only the opponent can accept/decline, and opponent moves revoke the offer.
- Moved bot calculation off the JavaFX thread so the board stays responsive while the bot searches.

## Remaining Later Work

- Custom piece behavior plug-in rules once the custom pieces are specified.
- Dedicated Hex Chess tests if test coverage becomes required.
