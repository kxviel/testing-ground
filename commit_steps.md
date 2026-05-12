# Zetris Commit Steps

This is the split for copying the Zetris work from:

```powershell
C:\Users\kxviel\Desktop\zetris
```

into:

```powershell
C:\Users\kxviel\Desktop\zero-runtime-warranty
```

Kevin gets the smallest commit because he has already committed most of the work in the source repo.

Deadline: Friday, 15 May 2026 at 20:00.

Target timing:

- Finish copying and commits by 18:30.
- Finish verification by 19:15.
- Push by 19:30.
- Keep 30 minutes for any merge or machine-specific issue.

## Reverification Done

From `C:\Users\kxviel\Desktop\zetris`, these passed:

```powershell
mvn -q -DskipTests compile
mvn -q -DskipTests test-compile
mvn -q -DskipTests javafx:jlink
```

Extra smokes also passed:

- Tetris model state, gravity, lock, random block color, snapshot deserialize.
- Tetris protocol parsing with colon-containing values.
- UDP discovery found a hosted game.
- TCP host/client handshake sent `JOIN` and `START`.
- `tetris/TetrisMenu.fxml` and `tetris/TetrisGame.fxml` load.

`mvn test` was not run.

## Initial Setup

Run this first in PowerShell:

```powershell
$source = "C:\Users\kxviel\Desktop\zetris"
$target = "C:\Users\kxviel\Desktop\zero-runtime-warranty"

Set-Location $target
git status --short
git pull
git switch -c zetris-final-integration
```

If `git status --short` prints anything before starting, stop and check what changed before copying files.

Do not copy `target`, `.git`, `.idea`, or generated build files.

## Review Rules

The commit history should be clean and easy to explain to professors.

- Use each person's own Git account.
- Do not fake commit dates, authors, or history.
- Do not squash everything into one commit.
- Keep each commit focused on one believable slice of work.
- Run the final verification after all copied commits are done.
- If a commit does not compile by itself because the next slice is still missing, note that in the group handoff instead of hiding it.

## 1. Kevin Setup Commit

Commit message:

```text
wire zetris entry
```

Kevin copies only the game entry, route support, and module wiring.

```powershell
Copy-Item "$source\src\main\java\module-info.java" "$target\src\main\java\module-info.java" -Force
Copy-Item "$source\src\main\java\seda_project\control_alt_defeat\gamebox\controller\GameChoice.java" "$target\src\main\java\seda_project\control_alt_defeat\gamebox\controller\GameChoice.java" -Force
Copy-Item "$source\src\main\java\seda_project\control_alt_defeat\gamebox\util\Router.java" "$target\src\main\java\seda_project\control_alt_defeat\gamebox\util\Router.java" -Force
Copy-Item "$source\src\main\resources\GameChoice.fxml" "$target\src\main\resources\GameChoice.fxml" -Force
Copy-Item "$source\src\main\resources\GameChoice.css" "$target\src\main\resources\GameChoice.css" -Force

git add src/main/java/module-info.java `
        src/main/java/seda_project/control_alt_defeat/gamebox/controller/GameChoice.java `
        src/main/java/seda_project/control_alt_defeat/gamebox/util/Router.java `
        src/main/resources/GameChoice.fxml `
        src/main/resources/GameChoice.css

git commit -m "wire zetris entry"
```

Kevin owns this small setup commit and the later menu UI/controller setup commit.

## 2. Shashank Commit

Commit message:

```text
add zetris model
```

Shashank copies the model/gameplay layer and model tests.

```powershell
New-Item -ItemType Directory -Force "$target\src\main\java\seda_project\control_alt_defeat\gamebox\model" | Out-Null
Copy-Item "$source\src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris" "$target\src\main\java\seda_project\control_alt_defeat\gamebox\model\" -Recurse -Force

New-Item -ItemType Directory -Force "$target\src\test\java\seda_project\control_alt_defeat\gamebox\model\tetris" | Out-Null
Copy-Item "$source\src\test\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisModelTest.java" "$target\src\test\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisModelTest.java" -Force

git add src/main/java/seda_project/control_alt_defeat/gamebox/model/tetris `
        src/test/java/seda_project/control_alt_defeat/gamebox/model/tetris/TetrisModelTest.java

git commit -m "add zetris model"
```

This commit should contain board state, pieces, colors, scoring, bugs, custom pieces, loss state, and restart state.

## 3. Sagar Network Commit

Commit message:

```text
add zetris lan services
```

Sagar copies the LAN protocol/discovery code and shared network updates.

```powershell
New-Item -ItemType Directory -Force "$target\src\main\java\seda_project\control_alt_defeat\gamebox\network" | Out-Null
Copy-Item "$source\src\main\java\seda_project\control_alt_defeat\gamebox\network\tetris" "$target\src\main\java\seda_project\control_alt_defeat\gamebox\network\" -Recurse -Force
Copy-Item "$source\src\main\java\seda_project\control_alt_defeat\gamebox\network\GameClient.java" "$target\src\main\java\seda_project\control_alt_defeat\gamebox\network\GameClient.java" -Force
Copy-Item "$source\src\main\java\seda_project\control_alt_defeat\gamebox\network\GameServer.java" "$target\src\main\java\seda_project\control_alt_defeat\gamebox\network\GameServer.java" -Force

New-Item -ItemType Directory -Force "$target\src\test\java\seda_project\control_alt_defeat\gamebox\network\tetris" | Out-Null
Copy-Item "$source\src\test\java\seda_project\control_alt_defeat\gamebox\network\tetris\TetrisProtocolTest.java" "$target\src\test\java\seda_project\control_alt_defeat\gamebox\network\tetris\TetrisProtocolTest.java" -Force

git add src/main/java/seda_project/control_alt_defeat/gamebox/network/tetris `
        src/main/java/seda_project/control_alt_defeat/gamebox/network/GameClient.java `
        src/main/java/seda_project/control_alt_defeat/gamebox/network/GameServer.java `
        src/test/java/seda_project/control_alt_defeat/gamebox/network/tetris/TetrisProtocolTest.java

git commit -m "add zetris lan services"
```

This commit should contain UDP discovery, TCP protocol messages, state snapshots, and the small shared network listener changes.

## 4. Kevin Menu UI Commit

Commit message:

```text
add zetris menu ui
```

Kevin copies the Tetris menu FXML/CSS and the menu controller setup. This is the extra small UI/controller slice.

```powershell
New-Item -ItemType Directory -Force "$target\src\main\java\seda_project\control_alt_defeat\gamebox\controller\tetris" | Out-Null
Copy-Item "$source\src\main\java\seda_project\control_alt_defeat\gamebox\controller\tetris\TetrisMenuController.java" "$target\src\main\java\seda_project\control_alt_defeat\gamebox\controller\tetris\TetrisMenuController.java" -Force
Copy-Item "$source\src\main\java\seda_project\control_alt_defeat\gamebox\controller\tetris\TetrisGameRouteData.java" "$target\src\main\java\seda_project\control_alt_defeat\gamebox\controller\tetris\TetrisGameRouteData.java" -Force

New-Item -ItemType Directory -Force "$target\src\main\resources\tetris" | Out-Null
Copy-Item "$source\src\main\resources\tetris\TetrisMenu.fxml" "$target\src\main\resources\tetris\TetrisMenu.fxml" -Force
Copy-Item "$source\src\main\resources\tetris\TetrisMenu.css" "$target\src\main\resources\tetris\TetrisMenu.css" -Force

git add src/main/java/seda_project/control_alt_defeat/gamebox/controller/tetris/TetrisMenuController.java `
        src/main/java/seda_project/control_alt_defeat/gamebox/controller/tetris/TetrisGameRouteData.java `
        src/main/resources/tetris/TetrisMenu.fxml `
        src/main/resources/tetris/TetrisMenu.css

git commit -m "add zetris menu ui"
```

This commit should contain Local/LAN menu navigation, player name input, custom piece setup UI, host/join menu states, and route data passed into the game.

## 5. Sagar Game UI Commit

Commit message:

```text
add zetris game screen
```

Sagar copies the playable game screen controller and FXML.

```powershell
New-Item -ItemType Directory -Force "$target\src\main\java\seda_project\control_alt_defeat\gamebox\controller\tetris" | Out-Null
Copy-Item "$source\src\main\java\seda_project\control_alt_defeat\gamebox\controller\tetris\TetrisGameController.java" "$target\src\main\java\seda_project\control_alt_defeat\gamebox\controller\tetris\TetrisGameController.java" -Force

New-Item -ItemType Directory -Force "$target\src\main\resources\tetris" | Out-Null
Copy-Item "$source\src\main\resources\tetris\TetrisGame.fxml" "$target\src\main\resources\tetris\TetrisGame.fxml" -Force

git add src/main/java/seda_project/control_alt_defeat/gamebox/controller/tetris/TetrisGameController.java `
        src/main/resources/tetris/TetrisGame.fxml

git commit -m "add zetris game screen"
```

This commit should contain the board rendering, controls, local gameplay loop, LAN sync handling, quit handling, game over display, restart handling, and bug spawning.

## Final Verification

After all five commits:

```powershell
git status --short
mvn -q -DskipTests compile
mvn -q -DskipTests test-compile
mvn -q -DskipTests javafx:jlink
```

Only run this if the team agrees:

```powershell
mvn test
```

Manual checks:

- Game choice opens maximized.
- Zetris opens maximized.
- Local mode starts with Standard and Custom pieces.
- Local mode shows both player keymaps.
- LAN mode shows one player keymap.
- Host starts advertising and sees joiner.
- Joiner can join a hosted network.
- Host can start the LAN game after join.
- Opponent quit shows the opponent-left message and returns to menu.
- Losing player board freezes and shows `Game Over`.
- Bug spawns with emoji and swaps boards/scores.
- Blocks use one random color per tetromino.

## Push

When verification passes:

```powershell
git log --oneline -3
git push -u origin zetris-final-integration
```

## If Something Looks Wrong

If `git status --short` shows files outside the assigned commit, stop before committing.

If copying causes a conflict with teammate changes, inspect it with:

```powershell
git diff
```

Then agree who owns the final edit before committing.
