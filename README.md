# iSuara: Offline ML Sign Language Interpreter

### 🚀 Try it Live
[![Download APK](https://img.shields.io/badge/Download_APK_V3.1.6-iSuara-2ea44f?style=for-the-badge&logo=android)]([YOUR_COPIED_GITHUB_RELEASE_LINK_HERE](https://github.com/HongZhangLim/iSuara/releases/download/v3.1.6/iSuara-v3.1.6.apk))

*(Note: Ensure you allow "Install from unknown sources" on your Android device to install the prototype).*

## 1. Repository Overview & Team Introduction

Welcome to the official repository for **iSuara**, an Edge-AI Android application built to provide real-time, pocket-sized translation of Bahasa Isyarat Malaysia (BIM) into Malay, English, Mandarin and Tamil. This repository contains the complete Android Studio project, including the native Kotlin UI, MediaPipe vision extractors, LiteRT inference pipeline, and the multi-agent cloud translation layer.

**Team Name:** sudo rm -rf /

---

## 2. Project Overview

### Problem Statement

In Malaysia, there are **44,000 Deaf and hard-of-hearing individuals**, but only **60 certified interpreters** nationwide. Hiring a human interpreter costs up to RM150/hour and often requires a 3-day wait. As a result, 78% of Deaf individuals never encounter an interpreter when seeking specialized health services, and 70% fear visiting clinics alone due to the risk of being misunderstood. Writing on paper is not a viable substitute because BIM uses a different grammatical structure (Topic-Comment) than spoken Malay, making written text a challenging second language.

### SDG Alignment

iSuara is built to advance the United Nations Sustainable Development Goals:

* **SDG 10 (Reduced Inequalities) - Target 10.2:** Promotes universal social and economic inclusion by giving the Deaf community an independent, real-time voice.
* **SDG 4 (Quality Education) - Targets 4.5 & 4.a:** Empowers Deaf students to advocate for themselves and participate in inclusive learning environments.
* **SDG 8 (Decent Work & Economic Growth) - Target 8.5:** Breaks workplace communication barriers, allowing seamless idea contribution and productive employment.

### Short Description

iSuara is a real-time, native Android application that bridges the communication gap between the Deaf community and the hearing public. It uses on-device Edge Machine Learning to track 98 BIM signs via the smartphone camera, then routes the detected glosses through several language models that each propose a reading before a judge model picks the best one. The result is rendered in Malay, English, Mandarin and Tamil, and spoken aloud in whichever the user selects.

---

## 3. Key Features

* **Real-Time Vision Tracking:** Uses the standard smartphone camera to extract 3D skeletal data without requiring special gloves, depth cameras, or cloud video processing.
* **Speed-Invariant ML Recognition:** A custom AI model that adapts to any signing speed, successfully recognizing highly compressed, fast motion without dropping frames.
* **Semantic AI Translator:** Overcomes the "Topic-Comment" syntax barrier of BIM by inferring hidden context and restructuring disjointed keywords into grammatical Malay.
* **Multi-Agent Reasoning:** BIM glosses arrive loosely ordered and are genuinely ambiguous, so a single model simply commits to one reading. iSuara asks three different models for a translation, then has a judge model choose the most faithful candidate rather than trusting any one of them.
* **Four-Language Output:** Every translation returns Malay, English, Mandarin and Tamil in a single request, so switching the display language afterwards is instant and costs nothing. Non-Malay selections keep the Malay visible and add the chosen language on a row beneath it.
* **Multilingual Text-To-Speech:** Speaks the selected language, falling back to Malay when a device lacks that voice — Mandarin and Tamil voice data is frequently absent on Malaysian retail devices.

---

## 4. Overview of Technologies Used

### Google Technologies

* **Android Studio & Kotlin Native:** The foundation of our zero-copy architecture, enabling direct access to camera hardware (CameraX) and the device's GPU without the bridge-latency of cross-platform frameworks.
* **Google MediaPipe:** Handles hardware-parallelized skeletal extraction (Pose on GPU, Hands on CPU) of 75 keypoints per frame.
* **Google LiteRT:** Runs our custom int8-quantized BiLSTM model locally, taking only ~1-3ms per inference.
* **Cloud LLM layer:** Acts as our cloud-based semantic brain, transforming raw BIM glosses (e.g., "Market + I + Go") into natural sentences (e.g., "Saya pergi ke pasar"). Three agents answer the same prompt in parallel and a fourth call adjudicates.
* **Expressive cloud TTS:** Speaks the result with an emotion the signer's face actually showed, taking a natural-language delivery instruction that the on-device voice cannot.
* **Google Text-to-Speech (TTS):** The offline native Android TTS engine used to execute the final audio output.
* **Google Colab:** Our primary environment for model training and evaluating quantitative analytics via Matplotlib.

### Other Supporting Tools

* **Jetpack Compose:** For building a modern, reactive, and overlay-driven UI.
* **Matplotlib:** Used extensively during the Colab training phase to plot cross-entropy loss, accuracy curves, and validate EMA smoothing ratios.

---

## 5. Implementation Details & Innovation

### System Architecture

iSuara utilizes a **Decoupled Edge-Cloud Pipeline**. Heavy visual processing (tracking and sign prediction) happens 100% offline on the Edge, ensuring zero-latency and total user privacy. The cloud stage is triggered only for semantic translation of text payloads (<1KB) — no video ever leaves the device.

The cloud stage is a four-call debate: three agents translate the same glosses concurrently, then a judge is shown the candidates and returns the index of the best one. The agents run one API key each, because the fan-out is simultaneous and sharing a key would put every request on one per-minute quota. The judge picks an index rather than writing its own sentence, so the answer is always something an agent actually proposed and the four languages stay mutually consistent. If some agents fail the judge decides among the survivors; if all fail, the app falls back to displaying and speaking the raw glosses.

### Workflow

1. **Capture:** CameraX captures 640x480 video at up to 60 FPS.
2. **Extract:** MediaPipe hardware parallelism tracks body pose (GPU) and dynamically crops hand regions (CPU).
3. **Normalize:** `FrameNormalizer` applies EMA smoothing and engineered features (velocity/acceleration).
4. **Predict:** BiLSTM model evaluates the temporal array to predict one of 98 BIM signs natively on the GPU.
5. **Refine:** Three models each restructure the buffered sign tokens into conversational SVO Malay, plus English, Mandarin and Tamil.
6. **Adjudicate:** A judge model selects the most faithful candidate.
7. **Output:** The app displays the sentence via Compose UI — Malay always, with the selected language beneath it — and speaks the selected language via Android TTS.

---

## 6. Challenges Faced

* **Challenge 1: Model Regression & Overheating**
* *Problem:* We initially used a Transformer model. It was too heavy for mobile, causing overheating and dropping framerates to 10 FPS.
* *Solution:* We pivoted to a **Bidirectional LSTM with Dot Attention**. This achieved the same sequence-understanding but is lightweight enough to run in ~3ms on the GPU, bumping our framerate to a smooth 35+ FPS.


* **Challenge 2: High Tracking Latency**
* *Problem:* Real-world conversations were slow because extracting 258 keypoints caused a bottleneck.
* *Solution:* We implemented **Hardware Parallelism**. We split the MediaPipe workload to run the heavy body PoseLandmarker on the GPU while processing the HandLandmarker simultaneously on the CPU.


* **Challenge 3: Restrictive Usable Range**
* *Problem:* Users had to stand rigidly within 50cm of the camera for hands to be recognized.
* *Solution:* We built a **Dynamic Hand-Crop Strategy** combined with shoulder-width normalization. The app uses body wrist coordinates to artificially "zoom in" on the hands, extending our accurate tracking range to 1.5 meters (a 200% increase).


* **Challenge 4: Ambiguous Gloss Ordering**
* *Problem:* BIM glosses arrive as a loosely ordered bag of words, and a single model just commits to one reading with no signal about how confident that reading is.
* *Solution:* We fan the same glosses out to **three concurrent agents** and have a fourth call adjudicate. On genuinely ambiguous input the agents disagree in useful ways; on clear input they converge, which is itself a signal. Being one model sampled three times, this is a self-consistency ensemble rather than a comparison of models: it catches a shaky reading, but not a misreading all three happen to share. The cost is latency — the pipeline waits for the slowest agent — so this is a quality-over-speed tradeoff, not a free win.

## 7. Installation & Setup

### 1. Prerequisites

* Android Studio Ladybug (2024.2) or newer
* JDK 17+
* Android device with API 26+ (Android 8.0)

### 2. API Keys

Create a `local.properties` file in the project root and add your keys:

```properties
GEMINI_API_KEY=xxxxxx
GEMINI_API_KEY_2=xxxxxx
GEMINI_API_KEY_3=xxxxxx
```

Only the first is required. The others are worth adding for two reasons: the
debate runs **one agent per key**, so three keys means a three-agent debate
while one key means a single unjudged answer; and the speech endpoint
rate-limits hard on the free tier, so it rotates keys to keep talking.
Translation and speech use separate quotas, so they do not compete.

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

---
