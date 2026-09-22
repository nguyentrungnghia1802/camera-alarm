$ErrorActionPreference='Stop'
$adb="$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
$emulator="$env:LOCALAPPDATA/Android/Sdk/emulator/emulator.exe"
$evidence=Join-Path $PWD 'docs/review/evidence/release-fixes-20260922'
git rev-parse HEAD | Set-Content (Join-Path $evidence 'matrix-source.txt')
$targets=@(@{api=31;name='CameraAlarm_API_31'},@{api=33;name='CameraAlarm_API_33'},@{api=34;name='CameraAlarm_API_34'},@{api=36;name='Medium_Phone_API_36.1'})
& $adb -s emulator-5554 emu kill
Start-Sleep -Seconds 3
foreach($target in $targets){
 Write-Output "START API $($target.api)"
 Start-Process $emulator -ArgumentList '-avd',$target.name,'-port','5554','-no-window','-no-audio','-no-snapshot','-memory','1536','-cores','2' -WindowStyle Hidden -RedirectStandardOutput "$env:TEMP/release-emulator-$($target.api).log" -RedirectStandardError "$env:TEMP/release-emulator-$($target.api).err"
 $ready=$false
 for($i=0;$i -lt 90;$i++){
  $boot=& $adb -s emulator-5554 shell getprop sys.boot_completed 2>$null
  if($boot -eq '1'){$ready=$true;break}
  Start-Sleep -Seconds 2
 }
 if(-not $ready){throw "API $($target.api) failed to boot"}
 & $adb -s emulator-5554 shell input keyevent KEYCODE_WAKEUP
 & $adb -s emulator-5554 shell input keyevent KEYCODE_MENU
 $install=& $adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk 2>&1
 if($install -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE'){
  & $adb -s emulator-5554 uninstall com.personal.cameraalarm
  & $adb -s emulator-5554 install app/build/outputs/apk/debug/app-debug.apk
 }
 & $adb -s emulator-5554 shell cmd appops set --uid com.personal.cameraalarm SCHEDULE_EXACT_ALARM allow
 if($target.api -ge 33){& $adb -s emulator-5554 shell pm grant com.personal.cameraalarm android.permission.POST_NOTIFICATIONS}
 if($target.api -ge 34){& $adb -s emulator-5554 shell cmd appops set com.personal.cameraalarm USE_FULL_SCREEN_INTENT allow}
 $env:ANDROID_SERIAL='emulator-5554'
 & .\gradlew.bat :app:connectedDebugAndroidTest > "$evidence/instrumentation-api$($target.api).log" 2>&1
 $code=$LASTEXITCODE
 Get-ChildItem app/build/outputs/androidTest-results/connected/debug -Recurse -Filter '*.xml' | Copy-Item -Destination {Join-Path $evidence "api$($target.api)-$($_.Name)"} -Force
 & $adb -s emulator-5554 logcat -d -v threadtime -s StopProbe CameraAlarm TestRunner > "$evidence/api$($target.api)-runtime.log"
 "API $($target.api) exit=$code" | Tee-Object "$evidence/api$($target.api)-result.txt"
 if($code -ne 0){throw "Instrumentation failed API $($target.api)"}
 if($target.api -ne 36){& $adb -s emulator-5554 emu kill;Start-Sleep -Seconds 3}
}
