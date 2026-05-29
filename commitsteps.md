# Copy `zetris` changes into `zero-runtime-warranty`

## Step 1: copy the verified files

```powershell
$source = 'C:\Users\kevka\Desktop\zetris'
$dest = 'C:\Users\kevka\Desktop\zero-runtime-warranty'

$files = @(
  'README.md',
  'implementation.md',
  'RequirementsTetrisMasters_SecondHandIn.pdf',
  'src\main\java\seda_project\control_alt_defeat\gamebox\controller\tetris\TetrisGameController.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\controller\tetris\TetrisMenuController.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\PieceShape.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisBoard.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisEffectState.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisGameConfig.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisGameState.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisPlayerState.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\network\tetris\TetrisStateSnapshot.java',
  'src\main\resources\tetris\TetrisGame.fxml',
  'src\main\resources\tetris\TetrisMenu.css',
  'src\main\resources\tetris\TetrisMenu.fxml',
  'src\test\java\seda_project\control_alt_defeat\gamebox\controller\tetris\TetrisFxmlSmokeTest.java',
  'src\test\java\seda_project\control_alt_defeat\gamebox\controller\tetris\TetrisGameControllerTest.java',
  'src\test\java\seda_project\control_alt_defeat\gamebox\controller\tetris\TetrisMenuControllerTest.java',
  'src\test\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisModelTest.java',
  'src\test\java\seda_project\control_alt_defeat\gamebox\network\tetris\TetrisLanDiscoveryServiceTest.java',
  'src\test\java\seda_project\control_alt_defeat\gamebox\network\tetris\TetrisProtocolTest.java'
)

foreach ($relative in $files) {
  $target = Join-Path $dest $relative
  New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
  Copy-Item (Join-Path $source $relative) $target -Force
}

Remove-Item (Join-Path $dest 'src\main\java\seda_project\control_alt_defeat\gamebox\controller\tetris\RotationLagQueue.java') -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path $dest 'src\test\java\seda_project\control_alt_defeat\gamebox\controller\tetris\RotationLagQueueTest.java') -Force -ErrorAction SilentlyContinue
```

Do not copy `C:\Users\kevka\Desktop\zetris\RequirementsTetrisMasters_SecondHandIn.txt`; it is only a local extracted helper file.

## Step 2: verify and commit in `zero-runtime-warranty`

```powershell
Set-Location 'C:\Users\kevka\Desktop\zero-runtime-warranty'

$env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.2\jbr'
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.2\plugins\maven\lib\maven3\bin\mvn.cmd' test

git add README.md implementation.md RequirementsTetrisMasters_SecondHandIn.pdf `
  src\main\java\seda_project\control_alt_defeat\gamebox\controller\tetris\TetrisGameController.java `
  src\main\java\seda_project\control_alt_defeat\gamebox\controller\tetris\TetrisMenuController.java `
  src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\PieceShape.java `
  src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisBoard.java `
  src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisEffectState.java `
  src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisGameConfig.java `
  src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisGameState.java `
  src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisPlayerState.java `
  src\main\java\seda_project\control_alt_defeat\gamebox\network\tetris\TetrisStateSnapshot.java `
  src\main\resources\tetris\TetrisGame.fxml `
  src\main\resources\tetris\TetrisMenu.css `
  src\main\resources\tetris\TetrisMenu.fxml `
  src\test\java\seda_project\control_alt_defeat\gamebox\controller\tetris\TetrisFxmlSmokeTest.java `
  src\test\java\seda_project\control_alt_defeat\gamebox\controller\tetris\TetrisGameControllerTest.java `
  src\test\java\seda_project\control_alt_defeat\gamebox\controller\tetris\TetrisMenuControllerTest.java `
  src\test\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisModelTest.java `
  src\test\java\seda_project\control_alt_defeat\gamebox\network\tetris\TetrisLanDiscoveryServiceTest.java `
  src\test\java\seda_project\control_alt_defeat\gamebox\network\tetris\TetrisProtocolTest.java

git rm --ignore-unmatch `
  src\main\java\seda_project\control_alt_defeat\gamebox\controller\tetris\RotationLagQueue.java `
  src\test\java\seda_project\control_alt_defeat\gamebox\controller\tetris\RotationLagQueueTest.java

git commit -m "Align Zetris with second hand-in requirements"
```

Current verified baseline in `zetris`: `mvn test` passes with 65 tests.

If you also want to run `mvn javafx:jlink`, use a full Java 25 JDK that includes `jlink`. A Java 26 JDK does not work with the current JavaFX 25 module set.
