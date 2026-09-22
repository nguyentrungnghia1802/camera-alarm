import subprocess,pathlib,time,sys
root=pathlib.Path('docs/review/evidence/final-system-3c0d440');out=root/sys.argv[1];out.mkdir(exist_ok=False)
adb='C:/Users/NTNghia/AppData/Local/Android/Sdk/platform-tools/adb.exe'
def cmd(*args):return subprocess.check_output([adb,'-s','emulator-5554',*args],stderr=subprocess.STDOUT).decode(errors='replace')
for apk in ['app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:print(cmd('install','-r','-t',apk),flush=True)
cmd('logcat','-c')
command=[adb,'-s','emulator-5554','shell','am','instrument','-w','-r','-e','class','com.personal.cameraalarm.AlarmDeliveryPriorityInstrumentedTest','com.personal.cameraalarm.test/androidx.test.runner.AndroidJUnitRunner'];(out/'command.txt').write_text(' '.join(command))
with (out/'instrumentation.txt').open('w') as f:
 p=subprocess.Popen(command,stdout=f,stderr=subprocess.STDOUT);start=time.monotonic()
 for i in range(15):
  if p.poll() is not None:break
  time.sleep(2)
  if i in [6,8,10]:
   for s in ['alarm','activity broadcasts','jobscheduler']:(out/(str(i)+'-'+s.replace(' ','-')+'.txt')).write_text(cmd('shell','dumpsys '+s),encoding='utf-8')
 p.wait(timeout=40)
(out/'runtime.log').write_text(cmd('logcat','-d','-v','threadtime','-s','CameraAlarm','DeliveryProbe','TestRunner'),encoding='utf-8')
print((out/'instrumentation.txt').read_text()[-700:])
