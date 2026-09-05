# iSuara: Offline ML Sign Language Interpreter

### 🚀 Try it Live

[![Download APK](https://img.shields.io/badge/Download_APK_V3.1.6-iSuara-2ea44f?style=for-the-badge&logo=android)](https://github.com/HongZhangLim/iSuara/releases/download/v3.1.6/iSuara-v3.1.6.apk)
[![Live Demo](https://img.shields.io/badge/Live_Demo-sudo--muba.my-000000?style=for-the-badge&logo=vercel)](https://www.sudo-muba.my)

*(Note: Ensure you allow "Install from unknown sources" on your Android device to install the prototype).*

## 1. Repository Overview & Team Introduction

Welcome to the official repository for **iSuara**, an Edge-AI application built to provide real-time, pocket-sized translation of Bahasa Isyarat Malaysia (BIM) into Malay, English, Mandarin and Tamil. This repository contains the complete Android Studio project — native Kotlin UI, MediaPipe vision extractors, LiteRT inference pipeline, and the multi-agent GonkaRouter translation layer — alongside a full browser port of the same pipeline in `web/`.

**Team Name:** sudo rm -rf /

---

## 2. Project Overview

### Problem Statement

In Malaysia, there are **44,000 Deaf and hard-of-hearing individuals**, but only **60 certified interpreters** nationwide. Hiring a human interpreter costs up to RM150/hour and often requires a 3-day wait. As a result, 78% of Deaf individuals never encounter an interpreter when seeking specialized health services, and 70% fear visiting clinics alone due to the risk of being misunderstood. Writing on paper is not a viable substitute because BIM uses a different grammatical structure (Topic-Comment) than spoken Malay, making written text a challenging second language.

A mistranslation in a clinic is not a bad user experience — it is a harm. That constraint drove most of the engineering decisions below.

### SDG Alignment

iSuara is built to advance the United Nations Sustainable Development Goals:

* **SDG 10 (Reduced Inequalities) - Target 10.2:** Promotes universal social and economic inclusion by giving the Deaf community an independent, real-time voice.
* **SDG 4 (Quality Education) - Targets 4.5 & 4.a:** Empowers Deaf students to advocate for themselves and participate in inclusive learning environments.
* **SDG 8 (Decent Work & Economic Growth) - Target 8.5:** Breaks workplace communication barriers, allowing seamless idea contribution and productive employment.

### Short Description

iSuara is a real-time application that bridges the communication gap between the Deaf community and the hearing public. It uses on-device Edge Machine Learning to track 98 BIM signs via the smartphone camera, then routes the detected glosses through several language models that each propose a reading before a judge model picks the best one. The result is rendered in Malay, English, Mandarin and Tamil, and spoken aloud in whichever the user selects. A 3D avatar signs replies back, making the exchange a conversation rather than a one-way readout.

---

## 3. Key Features

* **Real-Time Vision Tracking:** Uses the standard smartphone camera to extract 3D skeletal data without requiring special gloves, depth cameras, or cloud video processing.
* **Speed-Invariant ML Recognition:** A custom AI model that adapts to any signing speed, successfully recognizing highly compressed, fast motion without dropping frames.
* **Two-Way Communication:** A 3D avatar signs spoken or typed replies back to the user from a library of 110 motion clips, so the hearing party can answer rather than only read.
* **Semantic AI Translator:** Overcomes the "Topic-Comment" syntax barrier of BIM by inferring hidden context and restructuring disjointed keywords into grammatical Malay.
* **Multi-Agent Reasoning:** BIM glosses arrive loosely ordered and are genuinely ambiguous, so a single model simply commits to one reading. iSuara asks three different models for a translation, then has a judge model choose the most faithful candidate rather than trusting any one of them.
* **Traceable Inference:** The GonkaRouter request ID for every candidate and for the judge is recorded and shown in the interface, so a disputed translation can be traced back to the exact inference that produced it.
* **Expression-Aware Register:** An on-device expression model (8 classes) shifts the translation's tone and the voice's pitch and rate — "sakit" said calmly and "sakit" said in distress should not sound identical.
* **Four-Language Output:** Every translation returns Malay, English, Mandarin and Tamil in a single request, so switching the display language afterwards is instant and costs nothing. Non-Malay selections keep the Malay visible and add the chosen language on a row beneath it.
* **Multilingual Text-To-Speech:** Speaks the selected language, falling back to Malay when a device lacks that voice — Mandarin and Tamil voice data is frequently absent on Malaysian retail devices.

---

## 4. Overview of Technologies Used

### Core Technologies

* **GonkaRouter (DeepSeek V4-Flash, MiniMax M2.7, Kimi K2.6):** Acts as our cloud-based semantic brain, transforming raw BIM glosses (e.g., "Market + I + Go") into natural sentences (e.g., "Saya pergi ke pasar"). All three models answer the same prompt in parallel and DeepSeek adjudicates.
* **Android Studio & Kotlin Native:** The foundation of our zero-copy architecture, enabling direct access to camera hardware (CameraX) and the device's GPU without the bridge-latency of cross-platform frameworks.
* **MediaPipe:** Handles hardware-parallelized skeletal extraction (Pose on GPU, Hands on CPU) of 75 keypoints per frame.
* **LiteRT:** Runs our custom quantized BiLSTM model locally, taking only ~1-3ms per inference.
* **Android & Web Speech TTS:** The offline native engines used to execute the final audio output.
* **Google Colab:** Our primary environment for model training and evaluating quantitative analytics via Matplotlib.

### Other Supporting Tools

* **Jetpack Compose:** For building a modern, reactive, and overlay-driven UI.
* **React, TypeScript & Vite:** The browser port of the same pipeline, deployed at [sudo-muba.my](https://www.sudo-muba.my).
* **FastAPI:** Serves inference over a WebSocket and streams the debate stages as NDJSON, so the web UI shows progress instead of appearing frozen.
* **three.js & ONNX Runtime Web:** The 3D signing avatar and the in-browser expression classifier.
* **Matplotlib:** Used extensively during the Colab training phase to plot cross-entropy loss, accuracy curves, and validate EMA smoothing ratios.

---

## 5. Implementation Details & Innovation

### System Architecture

iSuara utilizes a **Decoupled Edge-Cloud Pipeline**. Heavy visual processing (tracking and sign prediction) happens 100% offline on the Edge, ensuring zero-latency and total user privacy. The Cloud (GonkaRouter) is triggered only for semantic translation of text payloads (<1KB) — no video ever leaves the device.

The cloud stage is a four-call debate: three models translate the same glosses concurrently, then a judge model is shown the candidates and returns the index of the best one. The judge picks an index rather than writing its own sentence, so the answer is always something an agent actually proposed and the four languages stay mutually consistent. If some agents fail the judge decides among the survivors; if all fail, the app falls back to displaying and speaking the raw glosses.

### Workflow

1. **Capture:** CameraX captures 640x480 video at up to 60 FPS.
2. **Extract:** MediaPipe hardware parallelism tracks body pose (GPU) and dynamically crops hand regions (CPU).
3. **Normalize:** `FrameNormalizer` applies EMA smoothing and engineered features, expanding 258 raw landmarks into 780 features per frame.
4. **Predict:** BiLSTM model evaluates the temporal array to predict one of 98 BIM signs natively on the GPU.
5. **Refine:** Three models each restructure the buffered sign tokens into conversational SVO Malay, plus English, Mandarin and Tamil.
6. **Adjudicate:** A judge model selects the most faithful candidate.
7. **Output:** The app displays the sentence — Malay always, with the selected language beneath it — and speaks it aloud.

### Model Performance

The V3.1.5 model reaches **91.2% held-out test accuracy** at 1x speed with 1,374,883 parameters. Speed robustness comes from multi-speed temporal downsampling: 1.5x and 2x variants are generated by frame-skipping and hold-padding, so the same sign performed at different tempos maps to the same class.

---

## 6. Challenges Faced

* **Challenge 1: Model Regression & Overheating**
* *Problem:* We initially used a Transformer model. It was too heavy for mobile, causing overheating and dropping framerates to 10 FPS.
* *Solution:* We pivoted to a **Bidirectional LSTM with Dot Attention**. Reading the window in both directions also matters linguistically — several BIM signs share an identical opening handshape and diverge only in the final frames, so a forward-only model commits too early. It runs in ~3ms on the GPU, bumping our framerate to a smooth 35+ FPS.


* **Challenge 2: High Tracking Latency**
* *Problem:* Real-world conversations were slow because extracting 258 keypoints caused a bottleneck.
* *Solution:* We implemented **Hardware Parallelism**. We split the MediaPipe workload to run the heavy body PoseLandmarker on the GPU while processing the HandLandmarker simultaneously on the CPU.


* **Challenge 3: Restrictive Usable Range**
* *Problem:* Users had to stand rigidly within 50cm of the camera for hands to be recognized.
* *Solution:* We built a **Dynamic Hand-Crop Strategy** combined with shoulder-width normalization. The app uses body wrist coordinates to artificially "zoom in" on the hands, extending our accurate tracking range to 1.5 meters (a 200% increase).


* **Challenge 4: Speed Skewing the Training Distribution**
* *Problem:* After generating speed variants, 67% of training gradients came from those variants while validation and test only ever saw 1x speed — the model was optimizing for a distribution it was not judged on.
* *Solution:* **Per-sample weighting** (1x speed at 2.0, variants at 1.0) rebalanced the effective gradient contribution to roughly 50/50, buying speed robustness without sacrificing 1x accuracy.

---

## 7. Installation & Setup

### 1. Prerequisites

* Android Studio Ladybug (2024.2) or newer
* JDK 17+
* Android device with API 26+ (Android 8.0)

### 2. GonkaRouter API Key

Create a `local.properties` file in the project root and add your key:

```properties
GONKA_API_KEY=sk-xxxxxx

```

*Translation works without this — the app falls back to showing and speaking the raw detected glosses.*

### 3. Build & Run

```bash
./gradlew assembleDebug
# Or simply open the project in Android Studio and click Run
```

### 4. First Run Instructions

1. Grant camera permissions when prompted.
2. Point the camera at a person signing BIM.
3. Detected words will appear and build in the bottom buffer.
4. Translation fires automatically after two seconds of stillness, or tap **Translate** to force it.
5. Use the language chip (**MS / EN / 中 / த**) to switch display and speech language. The choice persists across restarts.
6. Tap **Speak** to hear the sentence in the selected language.
7. Tap the bin icon to clear the current buffer.

### 5. Web Version

The browser port lives in `web/`. Setup, deployment notes and the Android-to-web port decisions are documented in [web/README.md](web/README.md).

