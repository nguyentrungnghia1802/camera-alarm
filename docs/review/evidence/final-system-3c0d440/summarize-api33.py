"""Extract token timelines and retain compressed, byte-verified raw OS dumps."""
from pathlib import Path
import re,json,zipfile,hashlib
root=Path(__file__).resolve().parent
logs=[root/'api33-runtime.log',*root.glob('api33-before/*/runtime.log'),*root.glob('api33-after/*/runtime.log'),*root.glob('priority-*/runtime.log')]
rows=[]
for log in logs:
    events=[]
    for line in log.read_text(encoding='utf-8').splitlines():
        m=re.search(r'I CameraAlarm: (\w+) token=(\S+) epoch_ms=(\d+) elapsed_ms=(\d+).*?deadline_ms=(\S+)',line)
        if m:events.append(dict(stage=m[1],token=m[2],epoch=int(m[3]),elapsed=int(m[4]),deadline=m[5],line=line))
    timeline=[e for e in events if e['stage'] in ['ALARM_PENDING_CREATED','OS_ALARM_REGISTERED','PENDING_RETIRED','PENDING_RECOVERY','ALARM_RECEIVER_ENTRY','RINGING','STALE_ALARM','AUDIO_STARTED']]
    (log.parent/(log.stem+'-timeline.json')).write_text(json.dumps(timeline,indent=2)+'\n')
    for recovery in [e for e in events if e['stage']=='PENDING_RECOVERY']:
        token=recovery['token'];owned=[e for e in events if e['token']==token];stages={e['stage']:e for e in owned}
        reg=stages.get('OS_ALARM_REGISTERED');entry=stages.get('ALARM_RECEIVER_ENTRY');retire=stages.get('PENDING_RETIRED')
        stale=[e for e in events if e['stage']=='STALE_ALARM' and e['line'].endswith('token='+token)]
        rows.append(dict(run=str(log.relative_to(root)),retryToken=token,
            registeredEpoch=reg['epoch'] if reg else None,
            callbackEpoch=entry['epoch'] if entry else None,
            registerToCallbackMs=entry['elapsed']-reg['elapsed'] if reg and entry else None,
            retiredEpoch=retire['epoch'] if retire else None,
            receiverAfterRetireMs=entry['elapsed']-retire['elapsed'] if entry and retire else None,
            ringing='RINGING' in stages,retryMarkedStale=bool(stale),
            audioStarts=sum(e['stage']=='AUDIO_STARTED' for e in owned)))
(root/'api33-measured-timelines.json').write_text(json.dumps(rows,indent=2)+'\n')
print(json.dumps(rows,indent=2))
for folder in [root/'api33-before',root/'api33-after',*root.glob('priority-*')]:
    if not folder.is_dir():continue
    files=[p for p in folder.rglob('*.txt') if re.match(r'\d+-(alarm|activity-broadcasts|jobscheduler)\.txt',p.name)]
    if not files:continue
    archive=folder/'os-dumps.zip'
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for p in files:z.write(p,str(p.relative_to(folder)))
    with zipfile.ZipFile(archive) as z:
        assert all(z.read(str(p.relative_to(folder)).replace('\\','/'))==p.read_bytes() for p in files)
    (folder/'os-dumps-sha256.json').write_text(json.dumps({str(p.relative_to(folder)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files},indent=2)+'\n')
    # Raw originals stay on disk; Git stores the lossless archive, never discards evidence.
    (folder/'.gitignore').write_text('**/[0-9]*-alarm.txt\n**/[0-9]*-activity-broadcasts.txt\n**/[0-9]*-jobscheduler.txt\n')
