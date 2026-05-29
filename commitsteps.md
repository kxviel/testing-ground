# Make `zero-runtime-warranty` match `zetris`

This checklist syncs `C:\Users\kevka\Desktop\zero-runtime-warranty` to the exact committed `zetris` snapshot below:

- source repo: `C:\Users\kevka\Desktop\zetris`
- source commit: `37b54ba2d2d1de11fddac67a021706b57286de43`
- source commit message: `Add Zetris hand-in verification coverage`

Scope:

- copy every tracked file that currently differs between the two repos and affects code, resources, tests, build output, or project docs
- delete the tracked files that still exist only in `zero-runtime-warranty`
- do not copy this helper file itself into `zero-runtime-warranty`

Important:

- do not drag files out of the live `C:\Users\kevka\Desktop\zetris` working tree
- that working tree currently has local uncommitted deletions
- always copy from the detached export worktree created in Step 1
- start only if `zero-runtime-warranty` has a clean working tree

## Step 1: create the pinned export and copy the exact parity set

```powershell
$sourceRepo = 'C:\Users\kevka\Desktop\zetris'
$sourceCommit = '37b54ba2d2d1de11fddac67a021706b57286de43'
$sourceWorktree = 'C:\Users\kevka\Desktop\zetris-37b54ba-export'
$dest = 'C:\Users\kevka\Desktop\zero-runtime-warranty'

git -C $dest status --short

if (Test-Path $sourceWorktree) {
  try {
    git -C $sourceRepo worktree remove $sourceWorktree --force
  } catch {
  }
  if (Test-Path $sourceWorktree) {
    Remove-Item -LiteralPath $sourceWorktree -Recurse -Force
  }
}

git -C $sourceRepo worktree add --detach $sourceWorktree $sourceCommit

$copyFiles = @(
  'README.md',
  'commit_steps.md',
  'implementation.md',
  'overview.md',
  'RequirementsTetrisMasters_SecondHandIn.pdf',
  'src\main\java\seda_project\control_alt_defeat\gamebox\controller\tetris\TetrisGameController.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\controller\tetris\TetrisMenuController.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\enums\TetrisItemType.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\PieceShape.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisBoard.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisBoardObject.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisEffectState.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisGameConfig.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisGameState.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisItemBag.java',
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

$deleteFiles = @(
  'releases\GameBoxBat.zip',
  'src\main\resources\global.css'
)

foreach ($relative in $copyFiles) {
  $sourcePath = Join-Path $sourceWorktree $relative
  $targetPath = Join-Path $dest $relative
  New-Item -ItemType Directory -Force -Path (Split-Path $targetPath) | Out-Null
  Copy-Item -LiteralPath $sourcePath -Destination $targetPath -Force
}

foreach ($relative in $deleteFiles) {
  $targetPath = Join-Path $dest $relative
  if (Test-Path $targetPath) {
    Remove-Item -LiteralPath $targetPath -Force
  }
}
```

## Step 2: verify the copied files before staging

```powershell
$sourceRepo = 'C:\Users\kevka\Desktop\zetris'
$sourceCommit = '37b54ba2d2d1de11fddac67a021706b57286de43'
$sourceWorktree = 'C:\Users\kevka\Desktop\zetris-37b54ba-export'
$dest = 'C:\Users\kevka\Desktop\zero-runtime-warranty'

$copyFiles = @(
  'README.md',
  'commit_steps.md',
  'implementation.md',
  'overview.md',
  'RequirementsTetrisMasters_SecondHandIn.pdf',
  'src\main\java\seda_project\control_alt_defeat\gamebox\controller\tetris\TetrisGameController.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\controller\tetris\TetrisMenuController.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\enums\TetrisItemType.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\PieceShape.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisBoard.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisBoardObject.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisEffectState.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisGameConfig.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisGameState.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisItemBag.java',
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

$deleteFiles = @(
  'releases\GameBoxBat.zip',
  'src\main\resources\global.css'
)

$missing = @()
$mismatched = @()

foreach ($relative in $copyFiles) {
  $sourcePath = Join-Path $sourceWorktree $relative
  $destPath = Join-Path $dest $relative

  if (-not (Test-Path $destPath)) {
    $missing += $relative
    continue
  }

  $sourceHash = (Get-FileHash -Algorithm SHA256 $sourcePath).Hash
  $destHash = (Get-FileHash -Algorithm SHA256 $destPath).Hash
  if ($sourceHash -ne $destHash) {
    $mismatched += $relative
  }
}

$stillPresent = $deleteFiles | Where-Object { Test-Path (Join-Path $dest $_) }

Write-Output 'Missing files:'
$missing
Write-Output 'Hash mismatches:'
$mismatched
Write-Output 'Files that should have been deleted but still exist:'
$stillPresent

if ($missing.Count -or $mismatched.Count -or $stillPresent.Count) {
  throw 'Copy verification failed. Fix the listed files before staging.'
}
```

## Step 3: stage, run the parity audit, run tests, and commit

```powershell
$sourceWorktree = 'C:\Users\kevka\Desktop\zetris-37b54ba-export'
$dest = 'C:\Users\kevka\Desktop\zero-runtime-warranty'

$copyFiles = @(
  'README.md',
  'commit_steps.md',
  'implementation.md',
  'overview.md',
  'RequirementsTetrisMasters_SecondHandIn.pdf',
  'src\main\java\seda_project\control_alt_defeat\gamebox\controller\tetris\TetrisGameController.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\controller\tetris\TetrisMenuController.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\enums\TetrisItemType.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\PieceShape.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisBoard.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisBoardObject.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisEffectState.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisGameConfig.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisGameState.java',
  'src\main\java\seda_project\control_alt_defeat\gamebox\model\tetris\TetrisItemBag.java',
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

$deleteFiles = @(
  'releases\GameBoxBat.zip',
  'src\main\resources\global.css'
)

Set-Location $dest

git add -- $copyFiles
git rm --ignore-unmatch -- $deleteFiles

$exclude = @('commitsteps.md')
$sourceTracked = git -C $sourceWorktree ls-files | Where-Object { $exclude -notcontains $_ }
$destTracked = git -C $dest ls-files | Where-Object { $exclude -notcontains $_ }
$sourceSet = [System.Collections.Generic.HashSet[string]]::new([string[]]$sourceTracked)
$destSet = [System.Collections.Generic.HashSet[string]]::new([string[]]$destTracked)

$onlyInSource = $sourceTracked | Where-Object { -not $destSet.Contains($_) } | Sort-Object
$onlyInDest = $destTracked | Where-Object { -not $sourceSet.Contains($_) } | Sort-Object
$modified = foreach ($relative in ($sourceTracked | Where-Object { $destSet.Contains($_) } | Sort-Object)) {
  $sourcePath = Join-Path $sourceWorktree $relative
  $destPath = Join-Path $dest $relative
  if ((Get-FileHash -Algorithm SHA256 $sourcePath).Hash -ne (Get-FileHash -Algorithm SHA256 $destPath).Hash) {
    $relative
  }
}

Write-Output 'Only in source after staging:'
$onlyInSource
Write-Output 'Only in destination after staging:'
$onlyInDest
Write-Output 'Content mismatches after staging:'
$modified

if ($onlyInSource.Count -or $onlyInDest.Count -or $modified.Count) {
  throw 'Parity audit failed. Do not commit until the lists above are empty.'
}

$env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.2\jbr'
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.2\plugins\maven\lib\maven3\bin\mvn.cmd' test

git commit -m "Sync zero-runtime-warranty to zetris parity snapshot"

git -C 'C:\Users\kevka\Desktop\zetris' worktree remove 'C:\Users\kevka\Desktop\zetris-37b54ba-export' --force
```

If the parity audit and `mvn test` both pass, `zero-runtime-warranty` matches the pinned `zetris` snapshot for the tracked project files in scope.
