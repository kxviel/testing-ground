# Full Exhaustive Audit — Tetris (Zetris)

Audit date: 2026-07-22  
Scope: read-only verification of the current workspace against the supplied specification. No production code, resource, or existing test was modified.

> **Post-audit remediation (2026-07-22):** BUG-01, BUG-02, and BUG-04 were subsequently fixed and covered by permanent regression tests. BUG-03 (Restart remaining available during a running game) was intentionally left unchanged at the user's request. The post-fix full suite passes 331/331 tests.

## 1. Executive summary

The audit checked all 86 discrete Section 0 items (5 constraints, 8 use cases, 53 functional requirements, 14 quality requirements, and 6 acceptance criteria), plus 47 UI/visual checklist items. Section 0 totals are **72 PASS, 0 FAIL, 9 PARTIAL, and 5 CANNOT VERIFY STATICALLY**. UI/visual totals are **33 PASS, 3 FAIL, 5 PARTIAL, and 6 CANNOT VERIFY STATICALLY**.

The clean build succeeded and the permanent suite passed **328/328 tests** (0 failures, 0 errors, 0 skipped). The Tetris-only rerun passed **128/128** (13 UI contract, 94 comprehensive model, 21 model tests).

Four production defects were found:

1. **High — horizontal player cards obscure playable grid cells** at both required resolutions.
2. **Medium — LAN client speed labels diverge from the host after the 15-second speed ramp** because elapsed game time is not synchronized.
3. **Medium — Restart is visible and active during a running game**, rather than becoming available after game end.
4. **Low — radius-bomb preview under-encloses the outer affected cells by 3 px** because cell gaps are omitted from its radius calculation.

The underlying gameplay model is strong: movement/rotation validation, locking, scoring, loss handling, line transfer, bombs, timed effects, teleport/swap behavior, dual pieces, horizontal gravity, snapshots, and hostile-input bounds are covered and pass. A real two-machine LAN match and pixel-level human visual review were not possible in this environment and are explicitly marked rather than inferred.

## 2. Section 1 — every Section 0 requirement

### Constraints

- **CON-TECH-01 — PASS** — Java 25 or newer. **Evidence:** `pom.xml:11,94-95` targets Java 25; `mvn clean package` succeeded.
- **CON-PLAT-01 — PASS** — Standard desktop/laptop platform. **Evidence:** JavaFX desktop entry point and resizable stage in `GameBox.java:24-38` and `WindowManager.java:44-55`; launch smoke test reached “Startup completed.”
- **CON-NET-01 — PASS** — LAN gameplay only over a local network. **Evidence:** UDP LAN broadcast discovery (`LanDiscoveryService.java:60,129-187,265-285`) and discovered-host TCP connection (`TetrisMenuController.java:449-485`); no internet matchmaking service exists.
- **CON-PLAY-01 — PASS** — Exactly two players. **Evidence:** two fixed player records in `TetrisGameState.java:17-20,39-49`; one host and one joining client in `TetrisGameSetup.java:28-37`.
- **CON-INPUT-01 — PASS** — Mouse and keyboard input. **Evidence:** FXML buttons/checkboxes and mouse selection; key handling in `TetrisGameController.java:1051-1143`.

### Use cases

- **UC-01 — PARTIAL** — Start a same-system two-player game. **Evidence:** local route at `TetrisMenuController.java:336-344`; both boards are created and JavaFX-tested. **Notes:** no human-completed full match was played.
- **UC-02 — CANNOT VERIFY STATICALLY** — Start a two-system LAN game. **Evidence:** host/join implementation at `TetrisMenuController.java:346-510,582-632`; generic loopback socket tests pass. **Notes:** a real second machine on the same LAN was unavailable.
- **UC-03 — PASS** — Move and rotate the active block. **Evidence:** controller mapping at `TetrisGameController.java:1061-1154`; model tests cover valid and blocked moves/rotations.
- **UC-04 — PASS** — Generate and fall blocks. **Evidence:** 100 ms game loop and spawn path at `TetrisGameController.java:402-447,1294-1313`.
- **UC-05 — PASS** — Detect and clear completed lines. **Evidence:** `TetrisBoard.java:148-230`; passing row/column clear tests.
- **UC-06 — PASS** — Handle one player losing. **Evidence:** blocked spawn calls `lost()` in `TetrisPlayerState.java:87-108`; status rendering at `TetrisGameController.java:741-746`.
- **UC-07 — PASS** — End game and declare winner. **Evidence:** `TetrisGameState.java:487-493` and score result logic at `TetrisGameController.java:731-769`.
- **UC-08 — PASS** — Restart after game end. **Evidence:** `TetrisGameController.java:373-400`; state is recreated and timers/bags reset. **Notes:** the same action is incorrectly available before game end (BUG-03).

### Functional — Input

- **FR-INPUT-01 — PASS** — Choose local or LAN before start. **Evidence:** local selection/start and separate host/join paths in `TetrisMenuController.java:126-139,188-245`.
- **FR-INPUT-02 — PASS** — Assign local default names. **Evidence:** `SafeText.java:5-13`; defaults applied by `TetrisGameSetup.java:18-25`.
- **FR-INPUT-03 — PASS** — Each LAN player can enter a name. **Evidence:** editable fields in `TetrisMenu.fxml:227-265`; host/join names consumed at `TetrisMenuController.java:356,469,582-600`.
- **FR-INPUT-04 — PASS** — Select an available local-network opponent. **Evidence:** discovered-game list and join selection at `TetrisMenuController.java:449-485,513-545`.
- **FR-INPUT-05 — PASS** — Keyboard movement and rotation. **Evidence:** `TetrisGameController.java:1061-1154`.
- **FR-INPUT-06 — PASS** — Restart button can be pressed after game end. **Evidence:** button in `TetrisGame.fxml:145-150`; handler at `TetrisGameController.java:373-381`. **Notes:** also enabled too early; see BUG-03.

### Functional — Flow

- **FR-FLOW-01 — PASS** — Initialize local two-player session after local selection. **Evidence:** `TetrisMenuController.java:223-230,336-344` and `TetrisGameState.java:39-49`.
- **FR-FLOW-02 — PARTIAL** — Establish same-LAN communication before LAN start. **Evidence:** server waits for client/name before enabling Start (`TetrisMenuController.java:397-446`); loopback socket exchange passes. **Notes:** not exercised on two physical systems.
- **FR-FLOW-03 — PASS** — Independent boards, scores, active blocks, speed, names, and states on new game. **Evidence:** separate immutable player states at `TetrisGameState.java:39-49`; reset/spawn at `TetrisGameController.java:385-399`.
- **FR-FLOW-04 — PASS** — Associate LAN board/score/state with the matching name. **Evidence:** fixed BOTTOM host/TOP join assignment in `TetrisGameSetup.java:32-37` and snapshot player ordering in `TetrisStateSnapshot.java:33-61`.
- **FR-FLOW-05 — PASS** — Validate movement before update. **Evidence:** `TetrisPlayerState.java:446-457`; blocked movement tests pass.
- **FR-FLOW-06 — PASS** — Validate rotation before update. **Evidence:** `TetrisPlayerState.java:502-509`; rotation tests pass.
- **FR-FLOW-07 — PASS** — Reject invalid movement/rotation and retain last valid state. **Evidence:** same methods return the unchanged record; wall/blocked tests pass.
- **FR-FLOW-08 — PASS** — Continuously generate pieces for active players. **Evidence:** gravity loop and `spawnMissingPieces` at `TetrisGameController.java:425-447,1294-1313`.
- **FR-FLOW-09 — PASS** — Bottom falls down and inverted top appears to fall up. **Evidence:** vertical model gravity is DOWN (`TetrisGameConfig.java:93-99`), while top rendering reverses rows/columns (`TetrisGameController.java:133-145,467-469`), producing upward visual movement.
- **FR-FLOW-10 — PASS** — Lock at final valid position. **Evidence:** `TetrisPlayerState.java:139-150,251-300`; gravity-lock tests pass.
- **FR-FLOW-11 — PASS** — Gradually increase speed. **Evidence:** 20 ms reduction every 15 seconds to an 80 ms floor in `TetrisGameConfig.java:22-25,101-105`. **Notes:** client label defect in BUG-02 does not change authoritative host gameplay speed.
- **FR-FLOW-12 — PASS** — Detect one or more completed horizontal lines. **Evidence:** `TetrisBoard.java:148-153`; model tests cover full rows.
- **FR-FLOW-13 — PASS** — Clear rows and shift blocks to fill empty space. **Evidence:** `TetrisBoard.java:162-190`; multirow shift behavior is tested. Horizontal mode analogously compacts columns by gravity direction.
- **FR-FLOW-14 — PASS** — Score from cleared lines. **Evidence:** `TetrisPlayerState.java:279-300`; score-clear test passes.
- **FR-FLOW-15 — PASS** — Detect losing threshold. **Evidence:** blocked spawn causes loss at `TetrisPlayerState.java:97-105`; full-board loss test passes.
- **FR-FLOW-16 — PASS** — Stop generation for a lost player. **Evidence:** `spawnMissingPieces` checks `isPlaying()` at `TetrisGameController.java:1297-1309`.
- **FR-FLOW-17 — PASS** — Store lost player’s score. **Evidence:** `lost()` retains score as `finalScore`; result uses stored score at `TetrisGameController.java:753-768`.
- **FR-FLOW-18 — PASS** — Opponent continues after one loss. **Evidence:** game finishes only when both are not playing (`TetrisGameState.java:487-493`); status says the opponent continues.
- **FR-FLOW-19 — PASS** — Determine winner by final scores or draw. **Evidence:** `TetrisGameController.java:753-765`.
- **FR-FLOW-20 — PASS** — Reset all game states on restart. **Evidence:** timers, elapsed time, item bags, state, active pieces, loop and network state are reset/restarted at `TetrisGameController.java:385-400`.
- **FR-FLOW-21 — PASS** — LAN-sync board, score, playing status, names, and result. **Evidence:** complete player/config/status snapshot at `TetrisStateSnapshot.java:33-107`; host sends after each state change and client coalesces latest state at `TetrisGameController.java:1187-1218,1234-1243,1393-1400`. **Notes:** elapsed speed-ramp time is outside the listed fields and causes BUG-02.
- **FR-FLOW-22 — PASS** — Show error or safely stop after LAN loss. **Evidence:** disconnect stops loops/closes sockets, shows a clear message, then returns to menu (`TetrisGameController.java:1221-1231,1450-1457`); connection-disconnect tests pass.

### Functional — Output

- **FR-OUTPUT-01 — PASS** — Display both boards simultaneously. **Evidence:** both grids are rendered in stacked/side-by-side containers at `TetrisGameController.java:459-568`. **Notes:** horizontal cells are partly obscured by BUG-01.
- **FR-OUTPUT-02 — PASS** — Top board is inverted. **Evidence:** `BoardView.ROTATED_180` at `TetrisGameController.java:133-145,467-469`.
- **FR-OUTPUT-03 — PASS** — Show names with boards and scores. **Evidence:** per-board `PlayerCard` at `TetrisGameController.java:156-215,559-567`.
- **FR-OUTPUT-04 — PASS** — LAN list uses player names. **Evidence:** `DiscoveredGame.toString()` begins with player name (`LanDiscoveryService.java:46-56`); FXML list at `TetrisMenu.fxml:310-321`.
- **FR-OUTPUT-05 — PASS** — Visually update position/orientation/board after valid updates. **Evidence:** `finishStateChange()` always renders at `TetrisGameController.java:1393-1400`.
- **FR-OUTPUT-06 — PASS** — Display post-clear shifted board. **Evidence:** immediate render after gravity/lock/clear through the same state-change path.
- **FR-OUTPUT-07 — PASS** — Display both scores during play. **Evidence:** each `PlayerCard.update` sets live score (`TetrisGameController.java:191-214`).
- **FR-OUTPUT-08 — PARTIAL** — Display Restart after the game ends. **Evidence:** it is present after end, but `renderResult()` forces it visible for every state (`TetrisGameController.java:731-735`). **Notes:** violates intended timing; BUG-03.
- **FR-OUTPUT-09 — PASS** — Display synchronized LAN board, score, and player status. **Evidence:** authoritative snapshots and render path noted under FR-FLOW-21.
- **FR-OUTPUT-10 — PASS** — Display winner/draw at end. **Evidence:** `TetrisGameController.java:737-765`.
- **FR-OUTPUT-11 — PASS** — Custom connected-block designer. **Evidence:** 5×5 editor in `TetrisMenuController.java:259-321`; connectivity validation in `CustomPieceBuilder`; connected/invalid tests pass.
- **FR-OUTPUT-12 — PASS** — Object swaps players. **Evidence:** BOARD_SWAP exchanges boards and falling pieces while preserving identities/scores at `TetrisGameState.java:209,272-308`; tests pass.
- **FR-OUTPUT-13 — PASS** — Object makes opponent’s piece speed faster for fixed time. **Evidence:** 50% gravity for 100 ticks at `TetrisGameState.java:23-25,168-174`.
- **FR-OUTPUT-14 — PASS** — Object slows self for fixed time. **Evidence:** 200% gravity for 100 ticks at `TetrisGameState.java:175-180`.
- **FR-OUTPUT-15 — PASS** — Object delays opponent rotation for fixed time. **Evidence:** `TetrisGameState.java:181-185`; delayed rotation test passes.
- **FR-OUTPUT-16 — PASS** — Object delays own rotation for fixed time. **Evidence:** `TetrisGameState.java:186-190`; same effect engine.
- **FR-OUTPUT-17 — PASS** — Object slows opponent for fixed time. **Evidence:** `TetrisGameState.java:191-196`.
- **FR-OUTPUT-18 — PASS** — Menu-adjustable speed. **Evidence:** Slow/Normal/Fast map to 750/550/320 ms at `TetrisMenuController.java:105-112,705-715`; UI test verifies mapping.
- **FR-OUTPUT-19 — PASS** — A clear expands actor by one line and shrinks opponent by one. **Evidence:** `TetrisGameState.java:214-270`; vertical and horizontal transfer tests pass.
- **FR-OUTPUT-20 — PASS** — Radius-3 explosion. **Evidence:** Euclidean squared distance at `TetrisBoard.java:232-246`; model test proves radius behavior. **Notes:** visual preview has BUG-04.
- **FR-OUTPUT-21 — PASS** — Explosion clears blocks ahead/below. **Evidence:** direction-specific clear at `TetrisBoard.java:249-270`; vertical and horizontal tests pass.
- **FR-OUTPUT-22 — PASS** — Portal removes active piece and queues it for opponent next. **Evidence:** `TetrisGameState.java:205-208`; queue-priority tests pass.
- **FR-OUTPUT-23 — PASS** — Teleporter swaps falling blocks. **Evidence:** FALLING_PIECE_SWAP at `TetrisGameState.java:210,393-467`; tests pass.
- **FR-OUTPUT-24 — PASS** — Dual-block advanced option. **Evidence:** dual shapes built at `TetrisGameConfig.java:78-90,178-197`; first-draw and horizontal tests pass.
- **FR-OUTPUT-25 — PASS** — Horizontal-play option. **Evidence:** 10×20 boards, LEFT/RIGHT gravity, and side-by-side layout at `TetrisGameConfig.java:93-99` and `TetrisGameController.java:485-557`; tests pass.

### Quality — Usability

- **QR-USAB-01 — PASS** — Clear local/LAN choice. **Evidence:** distinct Local, Host, Refresh and Join controls in `TetrisMenu.fxml`.
- **QR-USAB-02 — PASS** — Simple movement/rotation controls. **Evidence:** per-player control cards and fixed key maps at `TetrisGameController.java:703-710,1061-1143`.
- **QR-USAB-03 — PARTIAL** — Clearly show both boards, names, and scores. **Evidence:** all are present, but horizontal player cards cover grid cells (BUG-01).
- **QR-USAB-04 — PASS** — Understandable LAN selection by player name. **Evidence:** named discovery entries and selection status.

### Quality — Performance

- **QR-PERF-01 — CANNOT VERIFY STATICALLY** — Low-latency LAN board/score synchronization. **Evidence:** host sends each 100 ms tick and client coalesces latest snapshot. **Notes:** no two-system latency measurement was possible.
- **QR-PERF-02 — CANNOT VERIFY STATICALLY** — Smooth movement. **Evidence:** 100 ms JavaFX timeline and immutable small-board updates. **Notes:** requires human frame/jank observation under load.
- **QR-PERF-03 — PASS** — Update after line clear within one second. **Evidence:** clear and render occur synchronously in one 100 ms tick (`finishStateChange`); unit tests execute in milliseconds.

### Quality — Reliability

- **QR-RELY-01 — PARTIAL** — Complete a full local match without crash. **Evidence:** exhaustive model transitions, UI construction, startup and full suite pass. **Notes:** no human end-to-end full match was completed.
- **QR-RELY-02 — PARTIAL** — Keep both LAN game states consistent. **Evidence:** complete authoritative state snapshots and socket tests pass. **Notes:** real two-system session unavailable; displayed speed diverges after ramp (BUG-02).
- **QR-RELY-03 — PASS** — Consistent scoring. **Evidence:** score is saturated, only line clears increment it, final score is retained; scoring tests pass.
- **QR-RELY-04 — PASS** — Correct post-clear board. **Evidence:** row/column compaction tests pass for DOWN, LEFT and RIGHT gravity.

### Quality — Maintainability

- **QR-MAIN-01 — PASS** — Documented reproducible build/run commands. **Evidence:** `README.md:101-159` documents Java 25+, `mvn test javafx:jlink`, compile and `mvn javafx:run`; clean package succeeded.
- **QR-MAIN-02 — PASS** — Clear separation of input, gameplay, output and LAN. **Evidence:** controller/model/network/resource packages and dedicated protocol/snapshot classes.
- **QR-MAIN-03 — PARTIAL** — Automated tests cover all named areas. **Evidence:** movement, clear, scoring, loss, setup, snapshots and generic sockets are covered. **Notes:** no permanent Tetris-specific controller test exercises actual menu mode/name interaction, LAN host→input→state gameplay, disconnect screen flow, or restart logic.

### Acceptance criteria

- **AC-01 — PARTIAL** — Two players complete a full local game. **Evidence:** model/UI paths pass; no full human match completed.
- **AC-02 — CANNOT VERIFY STATICALLY** — Two players complete a full game on separate LAN systems. **Notes:** only loopback transport and static flow were available.
- **AC-03 — PASS** — Names correctly associated with boards and scores. **Evidence:** setup-side mapping, snapshot order and per-board cards are consistent.
- **AC-04 — PARTIAL** — Board and score remain synchronized in LAN play. **Evidence:** those exact fields round-trip and host pushes updates; no real two-system duration run.
- **AC-05 — PASS** — Completed lines clear and blocks shift downward. **Evidence:** direct model tests.
- **AC-06 — CANNOT VERIFY STATICALLY** — No listed constraint is violated. **Evidence:** code/build supports all constraints. **Notes:** physical platform/LAN-only runtime boundaries cannot be conclusively established without deployment testing.

## 3. Section 2 — UI and visual checklist

Actual JavaFX scene layout was exercised by `TetrisUiContractTest` at 1366×768 and 1920×1080. The app was also launched with `mvn javafx:run` and reached “Startup completed.” This tool session did not provide reliable pixel-level desktop screenshot inspection or a second LAN machine; visual-only judgments are therefore marked explicitly.

### Window, layout and theme

- **UI-WIN-01 — PASS** — Maximized, not forced fullscreen. **Evidence:** `WindowManager.java:71-81` sets fullscreen false and maximized true when the saved/default mode is MAXIMIZED; native stage decorations remain.
- **UI-WIN-02 — PARTIAL** — Nothing clipped/hidden/overflowing on setup, game, LAN at both sizes. **Evidence:** responsive scroll containers and tested board bounds pass. **Notes:** horizontal cards obscure board content (BUG-01), and complete setup/LAN pixel inspection was unavailable.
- **UI-WIN-03 — CANNOT VERIFY STATICALLY** — No text/icon displays ellipsis. **Evidence:** wrap text is used in major labels; vector icons are tested. **Notes:** only rendered-pixel inspection can rule out every ellipsis.
- **UI-THEME-01 — PASS** — Consistent light theme/no leftover dark page. **Evidence:** Theme variables are white/light (`Theme.css:1-29`), Tetris roots use `-sh-background`, and board-zone exterior is transparent. Dark shades occur only as intentional board/block colors.

### Setup controls

- **UI-SETUP-01 — PASS** — Required left/right controls and sections exist. **Evidence:** `TetrisMenu.fxml:58-335`.
- **UI-SETUP-02 — PASS** — Standard and Custom semantics verified. **Evidence:** they are intentionally additive, not mutually exclusive; `buildConfig` includes both and `availableShapes()` consumes both (`TetrisGameConfig.java:64-82`).
- **UI-SETUP-03 — PASS** — Selected speed is applied. **Evidence:** menu maps choice into config; in-game card uses config-derived effective speed; UI mapping tests pass.
- **UI-SETUP-04 — PASS** — Dual and Horizontal are independent and affect model/layout. **Evidence:** separate booleans/check boxes; all four combinations construct independently; dual-horizontal test passes.
- **UI-SETUP-05 — PASS** — Start guards required setup. **Evidence:** disabled until Local selection and blocked for no piece set/unsaved Custom. Blank names are valid by specification because they receive safe defaults, not treated as errors.
- **UI-SETUP-06 — PASS** — Back returns cleanly. **Evidence:** first exits Local configuration, then closes LAN resources and routes to GameBox (`TetrisMenuController.java:233-245`).
- **UI-SETUP-07 — PARTIAL** — Name edge cases. **Evidence:** fields are editable; blanks default, controls/newlines sanitize, and names clamp to 32 chars (`SafeText.java:5-34`); duplicate names are allowed. **Notes:** emoji rendering and every long-name layout require pixel review.
- **UI-SETUP-08 — PASS** — Host starts and updates UI state. **Evidence:** bind/advertise plus “Host is visible on LAN,” Cancel Hosting and waiting-state updates (`TetrisMenuController.java:346-423`).
- **UI-SETUP-09 — PASS** — Refresh re-queries list. **Evidence:** clears/restarts listener and stale timer at `TetrisMenuController.java:513-534`.
- **UI-SETUP-10 — PASS** — Join Selected disabled/inert without selection and enabled with selection. **Evidence:** `TetrisMenuController.java:454-457,540-545`.
- **UI-SETUP-11 — PARTIAL** — Empty placeholder and real host discovery. **Evidence:** “No LAN games found” FXML placeholder and tested UDP parser/list controller. **Notes:** second-instance broadcast appearance was not visually observed.

### Sidebar, boards and player cards

- **UI-SCALE-01 — PASS** — Responsive sidebar roughly 20–25%. **Evidence:** 22% clamped to 320–420 px (`ResponsiveLayout.java:60-68,85-116`).
- **UI-SCALE-02 — PASS** — Board area scales with square cells/aspect preserved. **Evidence:** fit calculation and runtime assertions pass at both resolutions (`TetrisGameController.java:574-665`).
- **UI-SCALE-03 — PASS** — No exterior board void/background block. **Evidence:** transparent board zone CSS and JavaFX background assertion.
- **UI-BOARD-01 — PASS** — Vertical boards stacked, square, unclipped. **Measured:** 169×329 px each at 1366×768; 249×485 px each at 1920×1080. Tests confirm centered/in viewport.
- **UI-BOARD-02 — PASS** — Horizontal boards side by side, square-cell grid, unclipped by viewport. **Measured:** 466×239 px each at 1366×768; 689×349 px each at 1920×1080.
- **UI-BOARD-03 — PASS** — LAN uses same equal-sized arrangement as local. **Evidence:** one arrangement function for both modes and JavaFX network-layout tests.
- **UI-CARD-01 — PASS** — Cards are intentionally unmanaged. **Evidence:** `BoardSlot` sets card unmanaged at `TetrisGameController.java:218-260`; no defect assigned merely for this design.
- **UI-CARD-02 — FAIL** — Cards must never obscure cells. **Evidence:** in horizontal mode each slot is board-sized; the board spans approximately x=0..466 at 1366 or x=2..691 at 1920, while the 340 px card spans x=4..344 and is stacked above the board (`BoardSlot` child order at line 236). Therefore both cards overlap playable cells at both target sizes. See BUG-01.
- **UI-CARD-03 — PARTIAL** — Pixel dimensions are reported above. Cells remain square, but scale is not equivalent: vertical cell sizes are 14.60/22.40 px and horizontal sizes 21.45/32.80 px at 1366/1920 respectively. The major regression is overlap, not clipping.
- **UI-INFO-01 — PASS** — Name, score, speed and correct controls per board. **Evidence:** `PlayerCard.update` and key binding agree for TOP/WASD and BOTTOM/arrows. **Notes:** horizontal left/right labels describe keys, although movement is perpendicular to horizontal gravity.
- **UI-INFO-02 — PASS** — Active effects show live type/duration. **Evidence:** effect chips at `TetrisGameController.java:948-1000`, updated each render; effect metadata survives snapshots.

### Special objects and horizontal mirroring

- **UI-BOMB-01 — PASS** — Falling bombs use opaque red squares with black vector icons. **Evidence:** `paintBombCell` at `TetrisGameController.java:908-917`; glyph tests at 12/14 px pass.
- **UI-BOMB-02 — PARTIAL** — Bombs use normal spawn/move/lock model in both orientations and snapshots preserve them; model tests pass. **Notes:** no human LAN playthrough visually observed.
- **UI-RADIUS-01 — PASS** — True circle and Euclidean effect. **Evidence:** JavaFX `Circle` plus `dx²+dy² ≤ r²` model implementation.
- **UI-RADIUS-02 — PASS** — Filled translucent preview and solid outline. **Evidence:** `TetrisGameController.java:887-904`.
- **UI-RADIUS-03 — FAIL** — Circle must fully enclose outer affected cells. **Evidence:** radius is `(3 + 0.5) × cellSize`, but the center-to-outer-edge requirement is `3 × (cellSize + 1px gap) + 0.5 × cellSize`; it is always 3 px short. See BUG-04.
- **UI-RADIUS-04 — PASS** — Preview updates on movement. **Evidence:** every accepted input calls render.
- **UI-RADIUS-05 — PASS** — No opponent preview in LAN. **Evidence:** preview requires `ownBoard` at `TetrisGameController.java:784-786`.
- **UI-COLUMN-01 — PASS** — Preview/clear only from impact onward. **Evidence:** direction checks at `TetrisGameController.java:847-869` exactly match model `TetrisBoard.java:249-270`; opposite-side tests pass.
- **UI-COLUMN-02 — PASS** — Fill matches radius fill. **Evidence:** both use RGB #F5C4B3 at opacity 0.38.
- **UI-COLUMN-03 — PASS** — Preview updates live. **Evidence:** render-after-input path.
- **UI-COLUMN-04 — PASS** — Hidden on live-viewed opponent board in LAN. **Evidence:** same `ownBoard` guard.
- **UI-HORIZ-01 — PASS** — Mirroring is data-index mapping, not node transform; overlay stays upright. **Evidence:** `BoardView.MIRRORED_HORIZONTALLY`, identity-transform and overlay tests.
- **UI-HORIZ-02 — PASS** — Controls remain perpendicular to mirrored gravity. **Evidence:** horizontal movement changes rows, while RIGHT/LEFT gravity changes columns; no visual node inversion is used.

### Buttons, LAN and regression

- **UI-BUTTON-01 — CANNOT VERIFY STATICALLY** — Primary actions are most visually prominent. **Evidence:** Start/host classes exist. **Notes:** prominence is a human visual judgment.
- **UI-BUTTON-02 — CANNOT VERIFY STATICALLY** — Secondary/destructive styling consistency. **Evidence:** shared style classes exist. **Notes:** requires whole-flow visual comparison.
- **UI-RESTART-01 — FAIL** — Restart should appear after end. **Evidence:** `renderResult()` forces it visible and enabled during RUNNING local games (`TetrisGameController.java:731-735`), and the handler has no finished-state guard. See BUG-03.
- **UI-LAN-01 — PASS** — Entries begin with player name. **Evidence:** `LanDiscoveryService.DiscoveredGame.toString()`.
- **UI-LAN-02 — PASS** — Port-bound hosting error is clear and recoverable. **Evidence:** catch path closes temporary server, restarts discovery, and shows “Could not start host: …” (`TetrisMenuController.java:366-385`).
- **UI-LAN-03 — CANNOT VERIFY STATICALLY** — Real host+join completes. **Evidence:** generic loopback transport passes and static Tetris handshake is complete. **Notes:** no second physical/interactive peer.
- **UI-LAN-04 — CANNOT VERIFY STATICALLY** — Low-latency visual synchronization on both systems. **Notes:** requires two running peers and measurement; BUG-02 predicts speed-label divergence after 15 s.
- **UI-REG-01 — CANNOT VERIFY STATICALLY** — No Memory Match/Chexsagon visual regression. **Evidence:** full tests pass. **Notes:** user-owned Memory changes are already present and no cross-game pixel walkthrough was possible; this audit made no shared-code change.

## 4. Automated test suite

### Commands and results

- `mvn clean package` — **BUILD SUCCESS**; **328 tests**, 0 failures, 0 errors, 0 skipped; 14 Surefire suites; approximately 20.9 s.
- `mvn '-Dtest=TetrisModelTest,TetrisComprehensiveTest,TetrisUiContractTest' test` — **BUILD SUCCESS**; **128 tests**, 0 failures, 0 errors, 0 skipped; 5.8 s.
- `mvn javafx:run` — application reached `GameBox -- Startup completed`; process was then intentionally terminated after startup verification.

Tetris-specific permanent tests:

- `TetrisUiContractTest`: 13/13 pass.
- `TetrisComprehensiveTest`: 94/94 pass.
- `TetrisModelTest`: 21/21 pass.

The permanent suite is broad but does not detect the four findings: it checks that cards do not overlap one another, not that cards do not intersect their boards; snapshots omit host elapsed time; restart timing is not asserted; and radius preview tests do not compare circle bounds with outer affected-cell bounds.

## 5. Bugs found — not fixed

### BUG-01 — Horizontal player cards obscure playable cells

**Severity:** High. **Modes:** local and LAN, Horizontal enabled, both players, both required resolutions.  
`BoardSlot` overlays the unmanaged card above the board (`TetrisGameController.java:218-260`). In horizontal layout, the slot is essentially board-sized. At 1366×768 the board is 466×239 px and the card occupies x=4..344; at 1920×1080 the board is 689×349 px and the card still occupies x=4..344. Their rectangles intersect, so the card masks cells and locked pieces. The current UI test only checks card-vs-card overlap.  
**Suggested fix direction:** retain the card as unmanaged, but compute its position outside the board’s actual bounds (for example in unused slot space above/beside it), and add a bounds-intersection assertion for card versus grid in every mode/resolution.

### BUG-02 — LAN client speed labels desynchronize after the gravity ramp

**Severity:** Medium. **Modes:** LAN host/join after 15 seconds.  
The host increments `elapsedGameMs` and derives ramped gravity (`TetrisGameController.java:432-443,1436-1438`). The client does not run the loop, its `elapsedGameMs` remains zero, and `TetrisStateSnapshot` serializes game/player state but not elapsed time (`TetrisStateSnapshot.java:33-38`). Client cards therefore continue to calculate speed from elapsed zero while the host shows 20 ms faster every 15 seconds. Board movement remains host-authoritative, but the displayed “ms/step” is wrong on the joining system.  
**Suggested fix direction:** include authoritative elapsed/ramped base gravity in the LAN snapshot and use it when rendering client speed; add a >15 s host/client synchronization test.

### BUG-03 — Restart is visible and active before game end

**Severity:** Medium. **Modes:** local and LAN.  
`renderResult()` unconditionally calls `UiVisibility.setVisibleManaged(restartButton, true)` (`TetrisGameController.java:731-735`), and `onRestart()` has no finished-state guard (`:373-381`). A running match can be reset accidentally; a LAN client can request and force a host restart mid-game. This conflicts with the specified after-game restart flow.  
**Suggested fix direction:** show/enable Restart only for `FINISHED` (and define any mutual LAN restart policy), reject restart messages while running, and test running/finished/disconnected states.

### BUG-04 — Radius preview cuts into the outer affected cells

**Severity:** Low. **Modes:** all modes/orientations when a radius bomb is active.  
The effect correctly clears cell centers within Euclidean radius 3, but the preview radius is `3.5 × cellSize` (`TetrisGameController.java:881-905`). Grid centers are separated by `cellSize + 1 px`; fully enclosing the axis cell at distance 3 requires `3 × (cellSize + 1) + cellSize/2 = 3.5 × cellSize + 3 px`. The drawn circle is therefore 3 px short and visually cuts the outermost affected cells.  
**Suggested fix direction:** derive preview geometry from the actual grid cell step and add boundary assertions at minimum and both measured cell sizes.

## 6. Items requiring manual human verification

- Complete a full local match with two humans in vertical and horizontal mode: verifies sustained controls, pacing, readability, loss continuation and end/restart experience.
- Complete a full host/join match on two physical machines on the same LAN: verifies broadcast discovery, firewall/network conditions, real latency, disconnection and restart.
- Pixel-inspect setup, gameplay and LAN lobby at maximized 1366×768 and 1920×1080: rules out clipping, ellipses, icon washout and styling inconsistencies not observable through node bounds.
- Observe motion smoothness and measure LAN state latency under simultaneous rapid input: timing quality cannot be proved from source alone.
- Confirm emoji/font fallback and 32-character names on the target OS: font rendering is platform-dependent.
- Visually compare Memory Match and Chexsagon after the user’s existing workspace changes: this Tetris audit made no shared changes and cannot attribute pre-existing visual state.

## 7. Overall recommendation

Do not treat Horizontal mode as release-ready until BUG-01 is fixed; it directly obstructs gameplay. Fix BUG-02 and BUG-03 before a LAN acceptance demonstration, then correct BUG-04 for visual accuracy. Add focused permanent tests for card/grid nonintersection, authoritative LAN elapsed speed, restart-state gating, and radius-preview bounds. After those fixes, perform the two-machine/manual checks above. The core gameplay model and current automated baseline are otherwise in good condition.
