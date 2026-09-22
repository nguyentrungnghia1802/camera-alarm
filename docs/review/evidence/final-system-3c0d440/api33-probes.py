"""Cold/warm recovery reproduction, preserving every result and OS queue snapshot."""
import subprocess,time,pathlib,sys,json,datetime,concurrent.futures
ROOT=pathlib.Path(__file__).resolve().parent
OUT=ROOT/sys.argv[1];OUT.mkdir(exist_ok=False)
SDK=pathlib.Path('C:/Users/NTNghia/AppData/Local/Android/Sdk')
ADB=str(SDK/'platform-tools/adb.exe');EMU=str(SDK/'emulator/emulator.exe')
def run(*args,timeout=30):
    cmd=[ADB,'-s','emulator-5554',*args]
    p=subprocess.run(cmd,capture_output=True,timeout=timeout)
    with (OUT/'commands.jsonl').open('a') as f:f.write(json.dumps({'time':datetime.datetime.now().astimezone().isoformat(),'cmd':cmd,'exit':p.returncode})+'\n')
    return p.stdout.decode(errors='replace')+p.stderr.decode(errors='replace')
def snapshot(folder,n):
    # Parallel read-only services, to sample dispatch independently of the app test.
    def one(service):
        text=run('shell','dumpsys '+service)
        (folder/f'{n}-{service.replace(" ","-")}.txt').write_text(text,encoding='utf-8')
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:list(pool.map(one,['alarm','activity broadcasts','jobscheduler']))
def test(folder):
    folder.mkdir();run('logcat','-c')
    (folder/'device.txt').write_text(run('shell','getprop ro.build.fingerprint')+run('shell','settings get global boot_count')+run('shell','cat /proc/uptime'))
    with (folder/'instrumentation.txt').open('w') as f:
        cmd=[ADB,'-s','emulator-5554','shell','am','instrument','-w','-r','-e','class','com.personal.cameraalarm.PendingDeliveryInstrumentedTest','com.personal.cameraalarm.test/androidx.test.runner.AndroidJUnitRunner']
        (folder/'command.json').write_text(json.dumps(cmd))
        proc=subprocess.Popen(cmd,stdout=f,stderr=subprocess.STDOUT)
        n=0;start=time.monotonic()
        while proc.poll() is None and time.monotonic()-start<70:
            snapshot(folder,n);n+=1;time.sleep(2)
        if proc.poll() is None:proc.terminate();raise RuntimeError('Instrumentation process exceeded external 70s safety bound')
    (folder/'runtime.log').write_text(run('logcat','-d','-v','threadtime','-s','CameraAlarm','TestRunner','ActivityManager','AlarmManager','BroadcastQueue'),encoding='utf-8')
    (folder/'audio.txt').write_text(run('shell','dumpsys audio'),encoding='utf-8')
    (folder/'services.txt').write_text(run('shell','dumpsys activity services com.personal.cameraalarm'),encoding='utf-8')
    output=(folder/'instrumentation.txt').read_text();status='PASS' if 'OK (1 test)' in output else 'FAIL'
    print(folder.name,status,flush=True)
    with (OUT/'results.txt').open('a') as f:f.write(f'{folder.name}: {status}\n')
(OUT/'commit.txt').write_text(subprocess.check_output(['git','rev-parse','HEAD'],text=True))
for cycle in range(1,4):
    run('emu','kill');time.sleep(3)
    with (OUT/f'boot{cycle}.log').open('w') as log:
        subprocess.Popen([EMU,'-avd','CameraAlarm_API_33','-port','5554','-no-window','-no-audio','-no-snapshot','-memory','1536','-cores','2'],stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
    end=time.monotonic()+180
    while time.monotonic()<end:
        if run('shell','getprop sys.boot_completed').strip()=='1':break
        time.sleep(2)
    else:raise RuntimeError('Boot not completed')
    run('shell','input keyevent KEYCODE_WAKEUP');run('shell','input keyevent KEYCODE_MENU')
    for apk in ['app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
        result=run('install','-r','-t',apk,timeout=60)
        if 'Success' not in result:raise RuntimeError(result)
    run('shell','cmd appops set --uid com.personal.cameraalarm SCHEDULE_EXACT_ALARM allow')
    run('shell','pm grant com.personal.cameraalarm android.permission.POST_NOTIFICATIONS')
    test(OUT/f'cold-{cycle}')
    test(OUT/f'warm-{cycle}')
