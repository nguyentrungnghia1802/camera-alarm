$ErrorActionPreference='Continue'
$ev=Join-Path (Get-Location) 'docs/review/evidence/final-release-fd06c2c'
$sha=git rev-parse HEAD
"Commit=$sha`nStarted=$(Get-Date -Format o)`nCommands run sequentially; failures retained; no implementation changes." | Set-Content "$ev/provenance.txt"
git status --short --branch > "$ev/initial-git-status.txt"
& "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" devices -l > "$ev/initial-devices.txt"
$gates=@(@('clean'),@('test','--rerun-tasks'),@('lint'),@('assembleDebug'),@(':app:assembleRelease'),@(':app:assembleAndroidTest'))
$i=0
foreach($g in $gates){$i++; $start=Get-Date -Format o; & .\gradlew.bat @g *> "$ev/gate-$i.log"; $code=$LASTEXITCODE; "$(Get-Date -Format o) | $start | .\gradlew.bat $($g -join ' ') | exit=$code" | Add-Content "$ev/gates.txt"; Write-Output "Gate $i exit=$code"}

