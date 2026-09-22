$ErrorActionPreference='Stop'
$ev=Join-Path $PWD 'docs/review/evidence/final-release-fd06c2c'
$adb="$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
& $adb -s emulator-5554 uninstall com.personal.cameraalarm.test > "$ev/signed-remove-debug-test.txt"
& $adb -s emulator-5554 uninstall com.personal.cameraalarm > "$ev/signed-remove-debug.txt"
& $adb -s emulator-5554 install app/build/outputs/apk/release/camera-alarm-fd06c2c-signed.apk > "$ev/signed-fresh-install.txt"
if($LASTEXITCODE -ne 0){throw 'Fresh install failed'}
& $adb -s emulator-5554 install -t app/build/outputs/apk/androidTest/debug/app-release-signed-androidTest.apk > "$ev/signed-test-install.txt"
& $adb -s emulator-5554 shell cmd appops set --uid com.personal.cameraalarm SCHEDULE_EXACT_ALARM allow
& $adb -s emulator-5554 shell pm grant com.personal.cameraalarm android.permission.POST_NOTIFICATIONS
& $adb -s emulator-5554 shell cmd appops set com.personal.cameraalarm USE_FULL_SCREEN_INTENT allow
& $adb -s emulator-5554 logcat -c
& $adb -s emulator-5554 shell am instrument -w -r -e class com.personal.cameraalarm.AlarmStopInstrumentedTest,com.personal.cameraalarm.SelectedLanguageInstrumentedTest com.personal.cameraalarm.test/androidx.test.runner.AndroidJUnitRunner > "$ev/signed-fresh-smoke.txt"
& $adb -s emulator-5554 logcat -d -v threadtime -s CameraAlarm StopProbe TestRunner > "$ev/signed-fresh-smoke-log.txt"
