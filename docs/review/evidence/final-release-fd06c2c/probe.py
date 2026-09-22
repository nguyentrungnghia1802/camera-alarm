"""External verification recorder; never imported by the application or tests."""
import subprocess, pathlib, datetime, xml.etree.ElementTree as E, re, sys, json, sqlite3, io, tarfile
ROOT=pathlib.Path(__file__).resolve().parent
ADB='C:/Users/NTNghia/AppData/Local/Android/Sdk/platform-tools/adb.exe'
SERIAL='emulator-5554'
def adb(*args, binary=False):
    cmd=[ADB,'-s',SERIAL,*args]
    p=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    with (ROOT/'commands.jsonl').open('a',encoding='utf-8') as f:
        f.write(json.dumps({'time':datetime.datetime.now().astimezone().isoformat(),'command':cmd,'exit':p.returncode,'stderr':p.stderr.decode(errors='replace')},ensure_ascii=False)+'\n')
    if p.returncode:raise RuntimeError(p.stderr.decode(errors='replace'))
    return p.stdout if binary else p.stdout.decode('utf-8',errors='replace')
def shell(command):return adb('shell',command)
def dump(name=None):
    result=shell('uiautomator dump /sdcard/verify-ui.xml')
    if 'dumped to:' not in result:raise RuntimeError(result)
    xml=shell('cat /sdcard/verify-ui.xml')
    if name:(ROOT/(name+'.xml')).write_text(xml,encoding='utf-8')
    return E.fromstring(xml)
def show(name=None):
    tree=dump(name)
    for n in tree.iter('node'):
        if n.get('text') or n.get('content-desc'): print(n.get('text'),'|',n.get('content-desc'),'|',n.get('bounds'),'|',n.get('class'))
def tap(label,index=None):
    ns=[n for n in dump().iter('node') if n.get('text')==label or n.get('content-desc')==label]
    if index is None and len(ns)!=1:raise RuntimeError(f'Expected one {label!r}, found {len(ns)}')
    b=list(map(int,re.findall(r'\d+',ns[int(index or 0)].get('bounds'))));shell(f'input tap {(b[0]+b[2])//2} {(b[1]+b[3])//2}')
def edit(index,text):
    ns=[n for n in dump().iter('node') if n.get('class')=='android.widget.EditText'];n=ns[int(index)]
    b=list(map(int,re.findall(r'\d+',n.get('bounds'))));shell(f'input tap {(b[0]+b[2])//2} {(b[1]+b[3])//2}')
    shell('input keycombination 113 29');shell('input text '+text.replace(' ','%s'))
def scroll(direction='down',index=0):
    ns=[n for n in dump().iter('node') if n.get('scrollable')=='true'];n=ns[int(index)]
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')));x=(x1+x2)//2;lo=y1+int((y2-y1)*.2);hi=y1+int((y2-y1)*.8)
    start,end=(hi,lo) if direction=='down' else (lo,hi);shell(f'input swipe {x} {start} {x} {end} 350')
def shot(name): (ROOT/(name+'.png')).write_bytes(adb('exec-out','screencap','-p',binary=True))
def save(name,command):
    text=shell(command);(ROOT/name).write_text(text,encoding='utf-8');return text
def db(name):
    blob=adb('exec-out','run-as','com.personal.cameraalarm','tar','cf','-','databases',binary=True)
    folder=ROOT/name;folder.mkdir(exist_ok=True)
    with tarfile.open(fileobj=io.BytesIO(blob)) as tar:
        for m in tar.getmembers():
            if m.isfile():(folder/pathlib.PurePosixPath(m.name).name).write_bytes(tar.extractfile(m).read())
    con=sqlite3.connect(folder/'camera_alarm_database');con.row_factory=sqlite3.Row
    data={t:[dict(r) for r in con.execute('SELECT * FROM '+t)] for t in ['trigger_rules','alert_events']};con.close()
    (ROOT/(name+'.json')).write_text(json.dumps(data,indent=2,ensure_ascii=False),encoding='utf-8');return data
def wire(data):
    pos=0;out=[]
    def varint():
        nonlocal pos
        val=0;shift=0
        while True:
            b=data[pos];pos+=1;val|=(b&127)<<shift
            if b<128:return val
            shift+=7
    while pos<len(data):
        tag=varint();field=tag>>3;kind=tag&7
        if kind==0:value=varint()
        elif kind==2:
            size=varint();value=data[pos:pos+size];pos+=size
        elif kind in (1,5):
            size=8 if kind==1 else 4;value=data[pos:pos+size];pos+=size
        else:raise ValueError(kind)
        out.append((field,kind,value))
    return out
def prefs(name,filename='camera_alarm_settings'):
    data=adb('exec-out','run-as','com.personal.cameraalarm','cat','files/datastore/'+filename+'.preferences_pb',binary=True)
    (ROOT/(name+'.pb')).write_bytes(data);out={}
    for _,_,entry in wire(data):
        fields={f:v for f,k,v in wire(entry)};key=fields[1].decode();f,k,v=wire(fields[2])[0]
        out[key]=v.decode() if f==5 else (bool(v) if f==1 else v if isinstance(v,int) else v.hex())
    (ROOT/(name+'.json')).write_text(json.dumps(out,indent=2,ensure_ascii=False),encoding='utf-8');return out
if __name__=='__main__':
    mode=sys.argv[1];args=sys.argv[2:]
    if mode=='dump':show(*args)
    elif mode=='tap':tap(*args)
    elif mode=='shot':shot(*args)
    elif mode=='shell':print(shell(*args))
    elif mode=='db':print(json.dumps(db(*args),ensure_ascii=False,indent=2))
    elif mode=='prefs':print(json.dumps(prefs(*args),ensure_ascii=False,indent=2))
    elif mode=='edit':edit(*args)
    elif mode=='scroll':scroll(*args)
