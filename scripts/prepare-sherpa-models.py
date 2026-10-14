import pathlib,urllib.request,time,tarfile,zipfile,hashlib,json
root=pathlib.Path(__file__).resolve().parent.parent / 'dist' / 'sherpa-models';root.mkdir(exist_ok=True)
items=[('sense.tar.bz2','https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17.tar.bz2'),('seg.tar.bz2','https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-segmentation-models/sherpa-onnx-pyannote-segmentation-3-0.tar.bz2'),('embedding.onnx','https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx'),('four-speakers.wav','https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-segmentation-models/0-four-speakers-zh.wav')]
for name,url in items:
 p=root/name
 if not p.exists():
  for i in range(5):
   try:
    with urllib.request.urlopen(url,timeout=300) as inp,p.open('wb') as out:
     while True:
      b=inp.read(1024*1024)
      if not b:break
      out.write(b)
    break
   except Exception:
    p.unlink(missing_ok=True)
    if i==4:raise
    time.sleep(2)
 print('Downloaded '+name+': '+str(p.stat().st_size),flush=True)
with tarfile.open(root/'sense.tar.bz2') as t,zipfile.ZipFile(root/'LifeMemory-SenseVoice.zip','w',zipfile.ZIP_STORED) as z:
 for m in t:
  name=pathlib.PurePosixPath(m.name).name
  if m.isfile() and name in ['model.int8.onnx','tokens.txt','LICENSE','README.md','zh.wav','en.wav']:
   data=t.extractfile(m).read()
   if name.endswith('.wav'):(root/name).write_bytes(data)
   else:z.writestr(name,data)
with tarfile.open(root/'seg.tar.bz2') as t,zipfile.ZipFile(root/'LifeMemory-SpeakerModels.zip','w',zipfile.ZIP_STORED) as z:
 for m in t:
  if m.isfile() and pathlib.PurePosixPath(m.name).name=='model.onnx':z.writestr('segmentation.onnx',t.extractfile(m).read())
 z.write(root/'embedding.onnx','embedding.onnx')
print('Model ZIP bundles ready',flush=True)
