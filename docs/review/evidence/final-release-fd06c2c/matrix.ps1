$ErrorActionPreference='Continue'
$adb="$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
$emulator="$env:LOCALAPPDATA/Android/Sdk/emulator/emulator.exe"
$evidence=Join-Path $PWD 'docs/review/evidence/final-release-fd06c2c'
$targets=@(@{api=31;name='CameraAlarm_API_31'},@{api=33;name='CameraAlarm_API_33'},@{api=34;name='CameraAlarm_API_34'},@{api=36;name='Medium_Phone_API_36.1'})
& $adb -s emulator-5554 emu kill
Start-Sleep -Seconds 3
foreach($target in $targets){
 $api=$target.api
 "$(Get-Date -Format o) START API $api commit=$(git rev-parse HEAD)" | Tee-Object "$evidence/api$api-protocol.txt"
 Start-Process $emulator -ArgumentList '-avd',$target.name,'-port','5554','-no-window','-no-audio','-no-snapshot','-memory','1536','-cores','2' -WindowStyle Hidden -RedirectStandardOutput "$evidence/emulator-$api.log" -RedirectStandardError "$evidence/emulator-$api.err"
 $ready=$false
 for($i=0;$i -lt 90;$i++){ $boot=& $adb -s emulator-5554 shell getprop sys.boot_completed 2>$null; if($boot -eq '1'){$ready=$true;break}; Start-Sleep -Seconds 2 }
 if(-not $ready){"BOOT FAILED" | Add-Content "$evidence/api$api-protocol.txt";continue}
 & $adb -s emulator-5554 shell getprop ro.build.fingerprint >> "$evidence/api$api-protocol.txt"
 & $adb -s emulator-5554 shell settings get global boot_count >> "$evidence/api$api-protocol.txt"
 & $adb -s emulator-5554 shell input keyevent KEYCODE_WAKEUP
 & $adb -s emulator-5554 shell input keyevent KEYCODE_MENU
 & $adb -s emulator-5554 uninstall com.personal.cameraalarm.test >> "$evidence/api$api-protocol.txt" 2>&1
 & $adb -s emulator-5554 uninstall com.personal.cameraalarm >> "$evidence/api$api-protocol.txt" 2>&1
 & $adb -s emulator-5554 install app/build/outputs/apk/debug/app-debug.apk >> "$evidence/api$api-protocol.txt" 2>&1
 & $adb -s emulator-5554 install -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >> "$evidence/api$api-protocol.txt" 2>&1
 & $adb -s emulator-5554 shell cmd appops set --uid com.personal.cameraalarm SCHEDULE_EXACT_ALARM allow
 if($api -ge 33){& $adb -s emulator-5554 shell pm grant com.personal.cameraalarm android.permission.POST_NOTIFICATIONS}
 if($api -ge 34){& $adb -s emulator-5554 shell cmd appops set com.personal.cameraalarm USE_FULL_SCREEN_INTENT allow}
 & $adb -s emulator-5554 logcat -c
 if($api -eq 31){
  & $adb -s emulator-5554 shell am instrument -w -r -e class com.personal.cameraalarm.AlarmStopInstrumentedTest com.personal.cameraalarm.test/androidx.test.runner.AndroidJUnitRunner > "$evidence/api31-cold-stop.txt"
  & $adb -s emulator-5554 logcat -d -v threadtime -s CameraAlarm StopProbe TestRunner > "$evidence/api31-cold-stop-logcat.txt"
 }
 $env:ANDROID_SERIAL='emulator-5554'
 & .\gradlew.bat :app:connectedDebugAndroidTest > "$evidence/instrumentation-api$api.log" 2>&1
 $code=$LASTEXITCODE
 Get-ChildItem app/build/outputs/androidTest-results/connected/debug -Recurse -Filter '*.xml' | Copy-Item -Destination {Join-Path $evidence "api$api-$($_.Name)"} -Force
 & $adb -s emulator-5554 logcat -d -v threadtime -s CameraAlarm StopProbe TestRunner > "$evidence/api$api-runtime.log"
 "$(Get-Date -Format o) API $api exit=$code" | Tee-Object "$evidence/api$api-result.txt"
 if($api -ne 36){& $adb -s emulator-5554 emu kill;Start-Sleep -Seconds 3}
}
