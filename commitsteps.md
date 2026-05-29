# Apply Verified Zetris Changes

This is a local transfer checklist for Kevin and Sagar. Do not commit this helper file into `zero-runtime-warranty`.

Goal:

- make `C:\Users\kevka\Desktop\zero-runtime-warranty` match the verified Zetris code, resources, tests, and project docs from the pinned local source snapshot
- keep the destination commit message focused on the work done, not on the transfer process
- run hash checks, a full parity audit, and tests before committing

Pinned source snapshot:

- commit: `cdf78454475e9dbe8af8a575c893060ce4757568`

Important:

- start only if `zero-runtime-warranty` has a clean working tree
- do not drag files from the live working tree by hand
- always copy from the detached export worktree created in Step 1
- `commitsteps.md` is intentionally excluded from the destination repo

## Step 1: Sagar copies the exact verified file set

Sagar runs this first copy/paste block in PowerShell.

```powershell
$sourceRepo = 'C:\Users\kevka\Desktop\zetris'
$sourceCommit = 'cdf78454475e9dbe8af8a575c893060ce4757568'
$sourceWorktree = 'C:\Users\kevka\Desktop\zetris-cdf7845-export'
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

## Step 2: Kevin verifies Sagar's copy

Kevin runs this second copy/paste block in PowerShell after Sagar finishes Step 1.

```powershell
$sourceWorktree = 'C:\Users\kevka\Desktop\zetris-cdf7845-export'
$dest = 'C:\Users\kevka\Desktop\zero-runtime-warranty'

$copyFiles = @(
  'README.md',
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

## Step 3: Kevin stages, audits, tests, and commits

Kevin runs this final copy/paste block only after Step 2 prints no missing files, no hash mismatches, and no files still present that should have been deleted.

```powershell
$sourceWorktree = 'C:\Users\kevka\Desktop\zetris-cdf7845-export'
$dest = 'C:\Users\kevka\Desktop\zero-runtime-warranty'

$copyFiles = @(
  'README.md',
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

git commit -m "Implement Zetris second hand-in requirements"

git -C 'C:\Users\kevka\Desktop\zetris' worktree remove 'C:\Users\kevka\Desktop\zetris-cdf7845-export' --force
```

The destination commit message is intentionally feature-focused. It should not mention the transfer process.
