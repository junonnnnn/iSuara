import numpy as np, onnxruntime as ort, json

MEAN=[0.485,0.456,0.406]; STD=[0.229,0.224,0.225]
LABELS=["Anger","Contempt","Disgust","Fear","Happiness","Neutral","Sadness","Surprise"]
W=H=224

# Channel-distinct on purpose: each channel occupies its own value band, so any
# channel swap moves the per-channel means by ~0.5+ and the test fails loudly.
# Closed-form so the Kotlin test regenerates it exactly, with no PNG asset.
def synth():
    img=np.zeros((H,W,3),dtype=np.uint8)
    for y in range(H):
        for x in range(W):
            img[y,x,0]=(x*7+y*13)%60            # R: 0..59
            img[y,x,1]=90+((x*3+y*29)%60)       # G: 90..149
            img[y,x,2]=190+((x*17+y*5)%60)      # B: 190..249
    return img

def pre(img):
    x=img.astype(np.float64)/255.0
    for i in range(3): x[...,i]=(x[...,i]-MEAN[i])/STD[i]
    return x.transpose(2,0,1).astype("float32")[np.newaxis,...]

def softmax(v):
    e=np.exp(v-v.max()); return e/e.sum()

rgb=synth()
t_rgb=pre(rgb); t_bgr=pre(rgb[...,::-1].copy())
print("RGB channel means:", [round(float(t_rgb[0,c].mean()),6) for c in range(3)])
print("BGR channel means:", [round(float(t_bgr[0,c].mean()),6) for c in range(3)])
print("=> separation:", round(float(np.abs(t_rgb.mean(axis=(0,2,3))-t_bgr.mean(axis=(0,2,3))).max()),4))

sess=ort.InferenceSession("enet_b0_8_best_vgaf.onnx",providers=["CPUExecutionProvider"])
lo_rgb=sess.run(None,{"input":t_rgb})[0][0]; lo_bgr=sess.run(None,{"input":t_bgr})[0][0]
print("logit delta RGB vs BGR:", round(float(np.abs(lo_rgb-lo_bgr).max()),4))
print("RGB argmax:",LABELS[int(lo_rgb.argmax())]," BGR argmax:",LABELS[int(lo_bgr.argmax())])

flat=t_rgb.reshape(-1)
spot=[0,1,223,224,50175,50176,50177,100351,100352,100353,150526,150527]
fx={
 "note":"Golden fixture for EmotionPreprocessor.toTensor. Image is closed-form; see synthetic() in EmotionPreprocessorTest.",
 "generated_by":"scratchpad/spike2.py against enet_b0_8_best_vgaf.onnx",
 "layout":"NCHW float32, batch 1, 3x224x224","channel_order":"RGB",
 "mean":MEAN,"std":STD,"tensor_length":int(flat.size),
 "channel_mean":[float(t_rgb[0,c].mean()) for c in range(3)],
 "channel_min":[float(t_rgb[0,c].min()) for c in range(3)],
 "channel_max":[float(t_rgb[0,c].max()) for c in range(3)],
 "spot_values":{str(i):float(flat[i]) for i in spot},
 "reference_logits_rgb":[float(v) for v in lo_rgb],
 "reference_probs_rgb":[float(v) for v in softmax(lo_rgb)],
}
open("emotion_preprocess_golden.json","w").write(json.dumps(fx,indent=2))
print(json.dumps({k:fx[k] for k in ("channel_mean","channel_min","channel_max","spot_values")},indent=2))
