# Commit Plan

These commands copy from `testing-ground` to `zero-runtime-warranty`. They do not delete files and do not commit anything.

Important: these are whole-file copy batches. A few files, especially `HexChessGameController.java`, contain changes that support more than one step, so those files intentionally appear in more than one step. If a repeated file produces no new diff in a later step, that is expected.

## 1. Commit Title: Fix Hex Chess Rule State And Local UX

PowerShell copy command for step 1:

```powershell
$ErrorActionPreference = "Stop"

$Root = "C:\Users\kevka\Desktop\SEDA"
$Source = Join-Path $Root "testing-ground"
$Target = Join-Path $Root "zero-runtime-warranty"

$Files = @(
    "src/main/java/seda_project/control_alt_defeat/gamebox/controller/hexchess/HexChessCanvasBoard.java",
    "src/main/java/seda_project/control_alt_defeat/gamebox/controller/hexchess/HexChessGameController.java",
    "src/main/java/seda_project/control_alt_defeat/gamebox/controller/hexchess/HexChessSetupController.java",
    "src/main/java/seda_project/control_alt_defeat/gamebox/model/hexchess/HexBoard.java",
    "src/main/java/seda_project/control_alt_defeat/gamebox/model/hexchess/HexChessBot.java",
    "src/main/java/seda_project/control_alt_defeat/gamebox/model/hexchess/HexChessGameSetup.java",
    "src/main/java/seda_project/control_alt_defeat/gamebox/model/hexchess/HexGameEndDetector.java",
    "src/main/java/seda_project/control_alt_defeat/gamebox/model/hexchess/HexGameState.java",
    "src/main/java/seda_project/control_alt_defeat/gamebox/model/hexchess/HexGameStatus.java",
    "src/main/java/seda_project/control_alt_defeat/gamebox/model/hexchess/HexLegalMoveValidator.java",
    "src/main/java/seda_project/control_alt_defeat/gamebox/model/hexchess/HexMoveGenerator.java",
    "src/main/java/seda_project/control_alt_defeat/gamebox/model/hexchess/HexMoveRules.java",
    "src/main/java/seda_project/control_alt_defeat/gamebox/model/hexchess/HexPositionValidator.java",
    "src/main/resources/hexchess/HexChessGame.fxml",
    "src/main/resources/hexchess/HexChessSetup.fxml"
)

if (-not (Test-Path -LiteralPath $Source)) { throw "Source folder not found: $Source" }
if (-not (Test-Path -LiteralPath $Target)) { throw "Target folder not found: $Target" }

$Missing = $Files | Where-Object { -not (Test-Path -LiteralPath (Join-Path $Source $_)) }
if ($Missing) { throw "Missing source files:`n$($Missing -join "`n")" }

foreach ($File in $Files) {
    $From = Join-Path $Source $File
    $To = Join-Path $Target $File
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $To) | Out-Null
    Copy-Item -LiteralPath $From -Destination $To -Force
}

Write-Host "Step 1 copied $($Files.Count) files."
git -C $Target status --short
```

Commit the engine and local gameplay fixes:
- Track pawn double-move eligibility in game state so custom positions cannot incorrectly grant standard-opening double moves.
- Preserve double-move eligibility through legal move generation and endgame detection.
- Revoke pending draw offers after a move, prevent overwriting active draw offers, and surface clearer draw-offer status messages.
- Keep promotion choices official: queen, rook, bishop, or knight.
- Mark promotion squares with stars in the board UI.
- Improve custom setup editing by disabling Start for invalid positions, supporting right-click/Delete removal, and replacing an existing same-color king when placing a king.
- Improve bot responsiveness by avoiding full game-state replay during mate checks.

Commit title:
`Fix Hex Chess rule state and local UX`

## 2. Commit Title: Harden Hex Chess LAN State Sync

PowerShell copy command for step 2:

```powershell
$ErrorActionPreference = "Stop"

$Root = "C:\Users\kevka\Desktop\SEDA"
$Source = Join-Path $Root "testing-ground"
$Target = Join-Path $Root "zero-runtime-warranty"

$Files = @(
    "src/main/java/seda_project/control_alt_defeat/gamebox/controller/hexchess/HexChessGameController.java",
    "src/main/java/seda_project/control_alt_defeat/gamebox/controller/hexchess/HexChessMenuController.java",
    "src/main/java/seda_project/control_alt_defeat/gamebox/model/hexchess/HexGameState.java",
    "src/main/java/seda_project/control_alt_defeat/gamebox/model/hexchess/HexGameStatus.java",
    "src/main/java/seda_project/control_alt_defeat/gamebox/network/GameServer.java",
    "src/main/java/seda_project/control_alt_defeat/gamebox/network/hexchess/HexChessLanDiscoveryService.java",
    "src/main/java/seda_project/control_alt_defeat/gamebox/network/hexchess/HexChessProtocol.java",
    "src/main/java/seda_project/control_alt_defeat/gamebox/network/hexchess/HexChessStateSnapshot.java",
    "src/main/resources/hexchess/HexChessGame.fxml",
    "src/main/resources/hexchess/HexChessMenu.fxml"
)

if (-not (Test-Path -LiteralPath $Source)) { throw "Source folder not found: $Source" }
if (-not (Test-Path -LiteralPath $Target)) { throw "Target folder not found: $Target" }

$Missing = $Files | Where-Object { -not (Test-Path -LiteralPath (Join-Path $Source $_)) }
if ($Missing) { throw "Missing source files:`n$($Missing -join "`n")" }

foreach ($File in $Files) {
    $From = Join-Path $Source $File
    $To = Join-Path $Target $File
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $To) | Out-Null
    Copy-Item -LiteralPath $From -Destination $To -Force
}

Write-Host "Step 2 copied $($Files.Count) files."
git -C $Target status --short
```

Commit the network reliability and LAN UX fixes:
- Serialize and deserialize full rule state, including repetition counts and pawn double-move eligibility.
- Reject malformed snapshots and malformed move payloads instead of silently resetting or accepting partial state.
- Treat disconnects and unrecoverable bot/network errors as terminal game states with visible messages.
- Disable unsupported LAN restart behavior by hiding Restart during network games.
- Add direct LAN port entry, advertise the actual bound host port, fall back to an available port when the default is busy, and filter stale discovery entries.
- Keep client-side "move sent" and "draw offer sent" messages inside game state so render cycles do not erase them.

Commit title:
`Harden Hex Chess LAN state sync`

## 3. Commit Title: Add Hex Chess Regression And E2E Coverage

PowerShell copy command for step 3:

```powershell
$ErrorActionPreference = "Stop"

$Root = "C:\Users\kevka\Desktop\SEDA"
$Source = Join-Path $Root "testing-ground"
$Target = Join-Path $Root "zero-runtime-warranty"

$Files = @(
    "src/test/java/seda_project/control_alt_defeat/gamebox/controller/hexchess/HexChessEndToEndSmokeTest.java",
    "src/test/java/seda_project/control_alt_defeat/gamebox/controller/hexchess/HexChessFxmlSmokeTest.java",
    "src/test/java/seda_project/control_alt_defeat/gamebox/model/hexchess/HexChessRulesTest.java",
    "src/test/java/seda_project/control_alt_defeat/gamebox/network/hexchess/HexChessNetworkSerializationTest.java"
)

if (-not (Test-Path -LiteralPath $Source)) { throw "Source folder not found: $Source" }
if (-not (Test-Path -LiteralPath $Target)) { throw "Target folder not found: $Target" }

$Missing = $Files | Where-Object { -not (Test-Path -LiteralPath (Join-Path $Source $_)) }
if ($Missing) { throw "Missing source files:`n$($Missing -join "`n")" }

foreach ($File in $Files) {
    $From = Join-Path $Source $File
    $To = Join-Path $Target $File
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $To) | Out-Null
    Copy-Item -LiteralPath $From -Destination $To -Force
}

Write-Host "Step 3 copied $($Files.Count) files."
git -C $Target status --short
```

Commit the new verification coverage:
- Add Hex Chess rule tests for board size, cell tones, Glinski material counts, custom pawn double-move blocking, promotion options, and draw-offer behavior.
- Add network tests for snapshot round-trips, malformed snapshots, malformed last moves, malformed move payloads, valid promotion move payloads, and localhost TCP protocol exchange.
- Add JavaFX smoke tests for Hex Chess menu, setup, and game FXML loading.
- Add controller-level E2E smoke coverage for local play, bot play, custom setup editing/validation, and two-instance LAN play over real localhost TCP.

Commit title:
`Add Hex Chess regression and E2E coverage`

## Final Verification

After the three copy steps, verify from `zero-runtime-warranty`:

```powershell
cd "C:\Users\kevka\Desktop\SEDA\zero-runtime-warranty"
git status --short
mvn test
mvn -DskipTests package
git diff --check
```
