"""
The iSuara web backend.

It does exactly the two things the browser cannot do itself:

  1. Sign inference — the TFLite model needs the Flex delegate (see
     inference.py), which no browser TFLite runtime ships.
  2. Gemini calls — the API keys must not be in a public bundle.

Everything else from the Android app (camera, MediaPipe landmark extraction,
feature normalization, text-to-speech) stays client-side, so raw video never
leaves the browser. Only a normalized 30-frame feature window and, on translate,
a list of Malay glosses cross the wire.
"""

import asyncio
import json
import logging

import numpy as np
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

from . import config, translator
from .inference import SignInterpreter

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
log = logging.getLogger("isuara")

app = FastAPI(title="iSuara Web API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=config.ALLOWED_ORIGINS,
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Serves the MediaPipe .task bundles and label_map.json straight from the
# Android app's assets, so the browser and the phone load byte-identical models.
app.mount("/models", StaticFiles(directory=str(config.ASSETS_DIR)), name="models")

# Loaded once at import, and deliberately NOT fatal on failure.
#
# Without the model there is no sign recognition, but the camera, the landmark
# extraction, the feature pipeline and the translator all still work — and on
# Windows a missing Flex delegate is the expected case until someone drops the
# Keras export in. Serving a working site that says exactly what is missing
# beats refusing to start.
interpreter: SignInterpreter | None
try:
    interpreter = SignInterpreter()
except Exception as e:  # noqa: BLE001 — reported through /api/health instead
    interpreter = None
    MODEL_ERROR = str(e)
    log.error("Sign recognition disabled — %s", e)
else:
    MODEL_ERROR = ""


# ─────────────────────────── inference ───────────────────────────

# Bytes in one 30 x 780 float32 window.
_WINDOW_BYTES = config.SEQUENCE_LENGTH * config.NUM_FEATURES * 4


@app.websocket("/ws/predict")
async def predict_socket(websocket: WebSocket) -> None:
    """
    One long-lived socket per camera session.

    Binary in: a Float32Array of 30 x 780, row-major by frame — the same layout
    FrameNormalizer.buildSequenceFeatures produces. JSON out: the top class.

    A socket rather than POST-per-window because the client fires a prediction
    roughly three times a second for as long as the camera runs, and paying TCP
    plus TLS setup on each would dominate the ~2ms of actual inference.
    """
    await websocket.accept()

    if interpreter is None:
        await websocket.send_text(json.dumps({"error": "model unavailable"}))
        await websocket.close(code=1011)
        return

    log.info("predict socket opened")
    try:
        while True:
            data = await websocket.receive_bytes()

            if len(data) != _WINDOW_BYTES:
                await websocket.send_text(
                    json.dumps({"error": f"expected {_WINDOW_BYTES} bytes, got {len(data)}"})
                )
                continue

            features = np.frombuffer(data, dtype="<f4")
            # Off the event loop: inference takes a lock and blocks, and this
            # socket must stay responsive to the next window.
            index, confidence = await asyncio.to_thread(interpreter.predict_top_class, features)

            await websocket.send_text(
                json.dumps(
                    {
                        "label": interpreter.labels[index],
                        "confidence": confidence,
                        "isConfident": confidence >= config.CONFIDENCE_THRESHOLD,
                    }
                )
            )
    except WebSocketDisconnect:
        log.info("predict socket closed")
    except Exception:
        log.exception("predict socket failed")
        await websocket.close(code=1011)


# ─────────────────────────── translation ───────────────────────────


class EmotionReading(BaseModel):
    """
    The signer's observed facial expression — mirrors emotion/EmotionReading.kt.

    Optional throughout: the expression classifier is a separate model, and a
    translation must still work when it has not run.
    """

    descriptor: str
    confidence: float = 0.0
    isHighArousal: bool = False  # noqa: N815 — matches the Kotlin field name


class TranslateRequest(BaseModel):
    words: list[str] = Field(min_length=1, max_length=16)
    emotion: EmotionReading | None = None


@app.post("/api/translate")
async def translate(request: TranslateRequest) -> StreamingResponse:
    """
    Runs the debate and streams its progress as newline-delimited JSON.

    NDJSON rather than a single response body because the debate takes seconds
    and the Android UI shows which stage it is in throughout; the browser reads
    these lines to render the same thing. Lines are either
    `{"stage": "JUDGING", "label": "..."}` or a final
    `{"translation": {...}}` / `{"error": "..."}`.

    A failure is reported in-band, with HTTP 200, because the stream has already
    begun by the time an agent can fail — the client treats a body with no
    `translation` line as a failure and falls back to the raw glosses, exactly
    as CameraScreen.kt does when translate() throws.
    """

    async def stream():
        try:
            emotion = request.emotion.model_dump() if request.emotion else None
            async for event in translator.debate(request.words, emotion):
                # A stage event carries its display label so the client does not
                # keep a second copy of the wording.
                if "stage" in event:
                    event = {**event, "label": translator.STAGE_LABELS[event["stage"]]}
                yield json.dumps(event) + "\n"
        except Exception as e:
            log.exception("translate failed")
            yield json.dumps({"error": str(e)}) + "\n"

    return StreamingResponse(
        stream(),
        media_type="application/x-ndjson",
        # Buffering proxies would defeat the point of streaming the stages.
        headers={"Cache-Control": "no-store", "X-Accel-Buffering": "no"},
    )


# ─────────────────────── avatar grammar restructuring ───────────────────────


class AvatarRestructureRequest(BaseModel):
    sentence: str = Field(min_length=1, max_length=200)


BIM_GRAMMAR_SYSTEM = """You are an expert in Bahasa Isyarat Malaysia (BIM) sign language grammar.
Convert a natural spoken sentence into the correct sequence of BIM sign glosses for 3D sign avatar synthesis.

Linguistic Rules:
1. Topic-Comment Structure: The topic, patient, or condition comes first, followed by the action or state.
2. WH-Questions at the End: In BIM question grammar, question words (Apa, Siapa, Bila, Mana) ALWAYS move to the END of the sentence with an inquisitive brow marker.
3. Omit Spoken Particles & Glue Words: Drop words that have no independent sign in BIM ("yang", "boleh", "di", "ke", "adalah", "ialah", "sekarang", "kerana", "sangat", "pun", "akan").
4. Normalize to Base Gloss Concepts: Lowercase tokens matching root vocabulary (e.g. "awak", "tolong", "saya", "apa", "anak", "sakit", "suhu", "hospital", "lapar").
5. Return ONLY a single JSON object (no markdown fences, no commentary):
{"reasoning": "<short explanation of the BIM grammar rule applied>", "tokens": ["gloss1", "gloss2", ...], "display": ["Gloss1", "Gloss2", ...]}

Examples:
Input: "Encik, apa yang saya boleh tolong?"
Output: {"reasoning": "In BIM question grammar, the addressee [Awak] leads, followed by the action [Tolong] and agent [Saya], while the question word [Apa] moves to the end. Particles 'yang' and 'boleh' are omitted.", "tokens": ["awak", "tolong", "saya", "apa"], "display": ["Awak", "Tolong", "Saya", "Apa"]}

Input: "Anak awak sakit apa sekarang?"
Output: {"reasoning": "In BIM medical question syntax, the subject [Anak] and possessor [Awak] lead, followed by condition [Sakit], with the question word [Apa] at the end. Temporal filler 'sekarang' is omitted.", "tokens": ["anak", "awak", "sakit", "apa"], "display": ["Anak", "Awak", "Sakit", "Apa"]}"""


@app.post("/api/avatar/restructure")
async def restructure_avatar(request: AvatarRestructureRequest) -> dict:
    """Restructures a spoken sentence into BIM sign grammar tokens using GonkaRouter."""
    if not translator.gonka.is_configured:
        return _fallback_restructure(request.sentence)

    user_turn = f'Input: "{request.sentence}"\nOutput:'
    try:
        raw_text, req_id, _ = await translator.gonka.complete(
            model=config.DEFAULT_MODEL,
            system=BIM_GRAMMAR_SYSTEM,
            user=user_turn,
            max_tokens=300,
            temperature=0.1,
        )
        # Parse JSON from output
        start = raw_text.find("{")
        end = raw_text.rfind("}")
        if start != -1 and end != -1 and start < end:
            obj = json.loads(raw_text[start : end + 1])
            obj["model"] = f"GonkaRouter ({config.DEFAULT_MODEL})"
            obj["requestId"] = req_id
            return obj
    except Exception as e:
        log.warning("Gonka avatar restructure failed: %s", e)

    return _fallback_restructure(request.sentence)


def _fallback_restructure(sentence: str) -> dict:
    clean = sentence.lower().strip()
    drop_words = {"yang", "boleh", "di", "ke", "adalah", "ialah", "sekarang", "kerana", "sangat", "pun", "akan"}
    q_words = {"apa", "siapa", "bila", "mana", "kenapa", "bagaimana"}

    raw_words = [w.strip(".,?!;:") for w in clean.split() if w.strip(".,?!;:")]
    filtered = [w for w in raw_words if w not in drop_words]

    q_word = next((w for w in filtered if w in q_words), None)
    if q_word:
        tokens = [w for w in filtered if w != q_word] + [q_word]
        reasoning = f"BIM Question Syntax: Question marker [{q_word.upper()}] positioned at sentence end, spoken glue particles dropped."
    else:
        tokens = filtered
        reasoning = "BIM Topic-Comment Syntax: Spoken glue particles dropped and topic concepts prioritized."

    return {
        "reasoning": reasoning,
        "tokens": tokens,
        "displayTokens": [t.title() for t in tokens],
        "model": "BIM Linguistic Engine",
    }


# ─────────────────────────── health ───────────────────────────


@app.get("/api/health")
async def health() -> dict:
    """
    What the client needs to know before it starts.

    `translationEnabled` is false when no key is configured; the client then
    falls back to showing and speaking the raw glosses rather than offering a
    Translate button that cannot work. `modelError` carries the load failure
    verbatim so a broken setup is diagnosable from the browser.
    """
    return {
        "status": "ok" if interpreter else "degraded",
        "model": interpreter.backend if interpreter else "unavailable",
        "modelError": MODEL_ERROR,
        "classes": len(interpreter.labels) if interpreter else 0,
        "translationEnabled": translator.gonka.is_configured,
        "provider": "GonkaRouter",
        "models": config.AGENT_MODELS,
        "keySlots": 3,
    }
