# Verification evidence index — 2bd97df / 2026-09-22

Audited SHA: 2bd97df87fc5e1275582fa0d962eb3309bb7eab0. Documentation-only verification; no production/test changes. Verdict and interpretation live in ../../final-release-report.md. All camera payloads/DB rows here are synthetic test fixtures. phone_now.png is pre-existing and excluded.

## Automated commands

01-clean.log: .\gradlew.bat clean
02-test.log: .\gradlew.bat test --rerun-tasks
03-lint.log: .\gradlew.bat lint
04-assembleDebug.log: .\gradlew.bat assembleDebug
05-_app_assembleRelease.log: .\gradlew.bat :app:assembleRelease
06-_app_assembleAndroidTest.log: .\gradlew.bat :app:assembleAndroidTest
07-instrumentation-api34.log / instrumentation-api31/33/36.log:
$env:ANDROID_SERIAL='emulator-5554'; .\gradlew.bat :app:connectedDebugAndroidTest

XML files give authoritative counts. API31 initial FAIL and cold isolated FAIL are retained alongside warm control PASS; nothing was edited to make tests pass.

Isolated API31 command, unchanged both times:
adb shell am instrument -w -e class com.personal.cameraalarm.AlarmStopInstrumentedTest com.personal.cameraalarm.test/androidx.test.runner.AndroidJUnitRunner

API31/33/36 launcher:
emulator -avd <CameraAlarm_API_31|CameraAlarm_API_33|Medium_Phone_API_36.1> -port 5554 -no-window -no-audio -no-snapshot -memory 1536 -cores 2

## Runtime commands / observations

ADB path: %LOCALAPPDATA%/Android/Sdk/platform-tools/adb.exe. Only one emulator was connected. final-adb-devices.txt records absence of physical Samsung/Xiaomi. Emulator Android/API: 12/31, 13/33, 14/34, 16/36; model sdk_gphone64_x86_64; One UI not applicable. No live physical Android/One UI version was obtainable.

Install debug / fixture APKs:
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb install -r fake-camera/build/outputs/apk/debug/fake-camera-debug.apk

Existing explicit fixture (before manual source/keyword UI edit, never after unlock in reboot E2E):
adb shell am instrument -w -e class com.personal.cameraalarm.RebootScenarioPreparation -e prepareReboot true com.personal.cameraalarm.test/androidx.test.runner.AndroidJUnitRunner

Per-rule source changed using picker UI to Front Door Camera / com.personal.fakecamera; keyword Human; fullscreen/vibration enabled. Fake Camera MainActivity posts via Human Detected button; unique-ID switch is used for distinct events. No fake BOOT broadcast was injected.

Reboot commands:
adb shell locksettings set-pin 2468
adb shell input keyevent KEYCODE_HOME
adb reboot
adb shell getprop sys.boot_completed
adb shell settings get global boot_count
adb shell dumpsys user
adb shell pidof com.personal.cameraalarm
adb shell input keyevent KEYCODE_WAKEUP
adb shell input keyevent KEYCODE_MENU
adb shell input text 2468
adb shell input keyevent KEYCODE_ENTER
adb shell dumpsys activity activities
adb logcat -d -v epoch -s CameraAlarm
adb shell am start -n com.personal.fakecamera/.MainActivity

UI dump determined coordinates for Human button and unique-ID switch; input tap used those observed coordinates. Current boot13 logs/audio/state files capture successful no-app-open trigger. Camera Alarm MainActivity was opened AFTER SUPPRESSED_RINGING assertion solely to STOP. POST permission false limits this reboot probe to listener/audio/state, not post-reboot notification/full-screen certification. Earlier same-round core-fakecamera and screen-off probes cover notification actions/FSI separately.

Boot11 exact access was default after reboot; before boot13 persisted with:
adb shell cmd appops set --uid com.personal.cameraalarm SCHEDULE_EXACT_ALARM allow
adb shell cmd appops set com.personal.cameraalarm SCHEDULE_EXACT_ALARM allow
adb shell cmd appops set com.personal.cameraalarm USE_FULL_SCREEN_INTENT allow
adb shell cmd appops write-settings

Audio/vibration/service evidence:
adb shell dumpsys audio
adb shell dumpsys vibrator_manager
adb shell dumpsys activity services com.personal.cameraalarm
adb shell dumpsys alarm

Privacy canary:
adb shell 'cmd notification post -t UnrelatedTitleCanary unrelated-private-tag-996621 "UnrelatedBodyCanary"'
Only enabled rule targets Fake Camera, not com.android.shell. Log contains canary tag in key; title/body not observed in unrelated trace/history. Release dex command:
apkanalyzer dex code --class com.personal.cameraalarm.notification.CameraNotificationListener app/build/outputs/apk/release/app-release-unsigned.apk

Permission probes:
adb shell cmd appops set com.personal.cameraalarm SCHEDULE_EXACT_ALARM deny
adb shell cmd notification disallow_listener com.personal.cameraalarm/com.personal.cameraalarm.notification.CameraNotificationListener
adb shell cmd notification allow_listener com.personal.cameraalarm/com.personal.cameraalarm.notification.CameraNotificationListener
adb shell pm revoke com.personal.cameraalarm android.permission.POST_NOTIFICATIONS
adb shell cmd appops set com.personal.cameraalarm USE_FULL_SCREEN_INTENT deny

Restore at end: exact/FSI allow, appops write-settings, pm grant POST_NOTIFICATIONS (granted=true read back), listener remains allowed. Emulator-only PIN cleared using locksettings clear --old 2468 and locksettings set-disabled true. No real device credentials involved.

Small UI smoke:
adb shell wm size 720x1280
adb shell wm density 320
adb shell settings put system font_scale 1.5
adb shell cmd uimode night yes
adb shell uiautomator dump /sdcard/<probe>.xml
adb shell screencap -p /sdcard/<probe>.png
adb pull /sdcard/<probe> <evidence-path>
UI actions reached editor Save, history page2 (26–29/29), clear confirmation, empty state. All 29 history rows were synthetic fixture/runtime probes; diagnostic rows had been copied before clearing. Dialog remained English despite Vietnamese parent.
Restored with wm size reset, wm density reset, settings delete system font_scale (original null), cmd uimode night no (original no).

Backup probe: adb shell bmgr enabled; adb shell bmgr list transports; adb shell bmgr backupnow com.personal.cameraalarm. Manager disabled / Backup is not allowed. Actual restore NOT VERIFIED.
Unsigned release: apksigner verify --verbose app/build/outputs/apk/release/app-release-unsigned.apk; exit1 expected, not signed certification. Get-FileHash -Algorithm SHA256 stored separately.

Device logcat threadtime is emulator wall clock; epoch_ms fields are authoritative Unix milliseconds. Host timezone is Asia/Bangkok; do not silently treat emulator wall-clock labels as host local time. Temp originals and extra dumps remain at C:/Windows/Temp/camera-alarm-final-2bd97df-20260922/. Selected evidence is retained here so findings do not depend only on temporary files.