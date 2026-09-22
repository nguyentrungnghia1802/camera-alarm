import subprocess,sys,xml.etree.ElementTree as E,re,pathlib,time,shlex
adb="C:/Users/NTNghia/AppData/Local/Android/Sdk/platform-tools/adb.exe"
def shell(s): return subprocess.check_output([adb,"-s","emulator-5554","shell",s],text=True,encoding="utf-8",errors="replace")
def dump():
 result=shell("uiautomator dump /sdcard/release-ui.xml")
 if "dumped to:" not in result:raise RuntimeError(result)
 return shell("cat /sdcard/release-ui.xml")
mode=sys.argv[1]
if mode=="dump":
 xml=dump()
 if len(sys.argv)>2:pathlib.Path(sys.argv[2]).write_text(xml,encoding="utf-8")
 for n in E.fromstring(xml).iter("node"):
  if n.get("text") or n.get("content-desc"):print(n.get("text"),"|",n.get("content-desc"),"|",n.get("bounds"),"|",n.get("class"))
elif mode=="tap":
 xml=dump();label=sys.argv[2]
 found=[n for n in E.fromstring(xml).iter("node") if n.get("text")==label or n.get("content-desc")==label]
 if len(found)!=1:raise RuntimeError(f"Expected one {label!r}, got {len(found)}")
 b=list(map(int,re.findall(r"\d+",found[0].get("bounds"))))
 print(shell(f"input tap {(b[0]+b[2])//2} {(b[1]+b[3])//2}"))
elif mode=="shot":
 pathlib.Path(sys.argv[2]).write_bytes(subprocess.check_output([adb,"exec-out","screencap","-p"]))
