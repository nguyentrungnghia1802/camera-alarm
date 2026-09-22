$ErrorActionPreference='Stop'
$repo='D:/_CODE_BANK/Project_/04_Extensions/camera-alarm'
$evidence=Join-Path $repo 'docs/review/evidence/release-fixes-20260922'
Push-Location C:/Windows/Temp/camera-alarm-upgrade-0d76d41
try { & .\gradlew.bat :app:assembleRelease > "$evidence/upgrade-baseline-build.log" 2>&1; if($LASTEXITCODE -ne 0){throw 'Baseline release build failed'} } finally {Pop-Location}
$sign='C:/Users/NTNghia/.camera-alarm-signing/sign-release.ps1'
& $sign -UnsignedApk C:/Windows/Temp/camera-alarm-upgrade-0d76d41/app/build/outputs/apk/release/app-release-unsigned.apk -SignedApk C:/Windows/Temp/camera-alarm-upgrade-0d76d41/baseline-signed.apk
& $sign -UnsignedApk "$repo/app/build/outputs/apk/release/app-release-unsigned.apk" -SignedApk "$repo/app/build/outputs/apk/release/camera-alarm-1.1.0-signed.apk"
& $sign -UnsignedApk "$repo/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" -SignedApk "$repo/app/build/outputs/apk/androidTest/debug/app-release-signed-androidTest.apk"
$adb="$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
& $adb -s emulator-5554 uninstall com.personal.cameraalarm.test
& $adb -s emulator-5554 uninstall com.personal.cameraalarm
& $adb -s emulator-5554 install "$repo/app/build/outputs/apk/release/camera-alarm-1.1.0-signed.apk" > "$evidence/signed-fresh-install.txt"
& $adb -s emulator-5554 install -t "$repo/app/build/outputs/apk/androidTest/debug/app-release-signed-androidTest.apk" > "$evidence/signed-test-install.txt"
& $adb -s emulator-5554 shell cmd appops set --uid com.personal.cameraalarm SCHEDULE_EXACT_ALARM allow
& $adb -s emulator-5554 shell pm grant com.personal.cameraalarm android.permission.POST_NOTIFICATIONS
& $adb -s emulator-5554 shell cmd appops set com.personal.cameraalarm USE_FULL_SCREEN_INTENT allow
& $adb -s emulator-5554 logcat -c
& $adb -s emulator-5554 shell am instrument -w -r -e class com.personal.cameraalarm.AlarmStopInstrumentedTest,com.personal.cameraalarm.SelectedLanguageInstrumentedTest com.personal.cameraalarm.test/androidx.test.runner.AndroidJUnitRunner > "$evidence/signed-fresh-smoke.txt"
& $adb -s emulator-5554 logcat -d -v threadtime -s CameraAlarm StopProbe TestRunner > "$evidence/signed-fresh-smoke-log.txt"
