# Commit Plan

## 1. Fix Hex Chess rule state and local UX

Commit the engine and local gameplay fixes:
- Track pawn double-move eligibility in game state so custom positions cannot incorrectly grant standard-opening double moves.
- Preserve double-move eligibility through legal move generation and endgame detection.
- Revoke pending draw offers after a move, prevent overwriting active draw offers, and surface clearer draw-offer status messages.
- Keep promotion choices official: queen, rook, bishop, or knight.
- Mark promotion squares with stars in the board UI.
- Improve custom setup editing by disabling Start for invalid positions, supporting right-click/Delete removal, and replacing an existing same-color king when placing a king.

Suggested commit message:
`Fix Hex Chess rules and local game UX`

## 2. Harden Hex Chess LAN and network state handling

Commit the network reliability and LAN UX fixes:
- Serialize and deserialize full rule state, including repetition counts and pawn double-move eligibility.
- Reject malformed snapshots and malformed move payloads instead of silently resetting or accepting partial state.
- Treat disconnects and unrecoverable bot/network errors as terminal game states with visible messages.
- Disable unsupported LAN restart behavior by hiding Restart during network games.
- Add direct LAN port entry, advertise the actual bound host port, fall back to an available port when the default is busy, and filter stale discovery entries.
- Keep client-side "move sent" and "draw offer sent" messages inside game state so render cycles do not erase them.

Suggested commit message:
`Harden Hex Chess LAN state sync`

## 3. Add focused Hex Chess regression coverage

Commit the new verification coverage:
- Add Hex Chess rule tests for board size, cell tones, Glinski material counts, custom pawn double-move blocking, promotion options, and draw-offer behavior.
- Add network tests for snapshot round-trips, malformed snapshots, malformed last moves, malformed move payloads, valid promotion move payloads, and localhost TCP protocol exchange.
- Add JavaFX smoke tests for Hex Chess menu, setup, and game FXML loading.
- Verify with:
  - `mvn test`
  - `mvn -DskipTests package`
  - `git diff --check`

Suggested commit message:
`Add Hex Chess regression tests`
