"""
Where the web backend finds its assets and its keys.

Model files are read straight out of the Android app's assets directory rather
than copied: `app/src/main/assets` stays the single source of truth, so the web
build can never drift onto a stale model or an out-of-date label map.
"""

import os
from pathlib import Path

from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parents[1] / ".env")

# web/backend/app/config.py -> app -> backend -> web -> repo root
REPO_ROOT = Path(__file__).resolve().parents[3]
ASSETS_DIR = REPO_ROOT / "app" / "src" / "main" / "assets"

# The V3.1.2 FP16 export, which ml/SignInterpreter.kt also loads.
#
# This one matters far beyond being newer: it contains NO Flex ops, where the
# older v3 exports carry FlexTensorListReserve/SetItem/Stack. Verified with
# tools/check_model.py — it loads without a "select TF ops" delegate being
# created at all. Consequences:
#
#   * any TensorFlow version runs it; the 2.17-2.19 window no longer binds
#   * it can run IN THE BROWSER via LiteRT.js / tfjs-tflite, which would remove
#     this inference backend entirely and put the pipeline back on the edge
#
# Same contract as before — (1, 30, 780) float32 in, (1, 98) float32 softmax out
# — so nothing upstream changes.
MODEL_FILE = ASSETS_DIR / "bim_lstm_v312_fp16.tflite"

# The older Flex-dependent exports, kept for A/B comparison. Nothing selects
# these by default; loading one re-imposes the TensorFlow version window.
MODEL_FILE_V3_INT8 = ASSETS_DIR / "bim_lstm_v3_int8.tflite"
MODEL_FILE_V3_F32 = ASSETS_DIR / "bim_lstm_v3_f32.tflite"
LABEL_MAP_FILE = ASSETS_DIR / "label_map.json"

# Keras artifacts, gitignored because they are large and not in the Android
# build. Drop whichever the training notebook produced into web/backend/models:
#
#   bim_lstm_v3.keras  — the export model, z-score scaler already baked in.
#                        Preferred: nothing else is needed beside it.
#   v3_best.keras      — the raw trained model. Needs the two scaler arrays,
#                        which the notebook saves next to it.
KERAS_DIR = Path(__file__).resolve().parents[1] / "models"
KERAS_MODEL = KERAS_DIR / "bim_lstm_v3.keras"
KERAS_INNER_MODEL = KERAS_DIR / "v3_best.keras"
SCALER_MEAN = KERAS_DIR / "scaler_mean_v3.npy"
SCALER_SCALE = KERAS_DIR / "scaler_scale_v3.npy"

# Must match the tensor shapes baked into the model — see ml/SignInterpreter.kt.
SEQUENCE_LENGTH = 30
NUM_FEATURES = 780
NUM_CLASSES = 98

# Kept identical to SignPredictor.kt so the web build classifies exactly as the
# phone does.
CONFIDENCE_THRESHOLD = 0.6

# Origins allowed to reach the API. The Vite dev server proxies /api and
# /models, so this matters only when the frontend is served from elsewhere.
ALLOWED_ORIGINS = [
    o.strip()
    for o in os.getenv("ALLOWED_ORIGINS", "http://localhost:5173,http://127.0.0.1:5173").split(",")
    if o.strip()
]


GONKA_BASE_URL = os.getenv("GONKA_BASE_URL", "https://api.gonkarouter.io")

# The three models that debate on GonkaRouter, matching service/GonkaTranslator.kt.
DEFAULT_MODEL = "deepseek-ai/DeepSeek-V4-Flash-0731"
AGENT_MODELS = [
    DEFAULT_MODEL,
    "MiniMaxAI/MiniMax-M2.7",
    "moonshotai/Kimi-K2.6",
]
JUDGE_MODEL = DEFAULT_MODEL


def _read_local_properties_key() -> str:
    """Fallback to GONKA_API_KEY in local.properties in repo root if present."""
    local_props = REPO_ROOT / "local.properties"
    if not local_props.is_file():
        return ""
    try:
        for line in local_props.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line.startswith("GONKA_API_KEY="):
                return line.split("=", 1)[1].strip().strip('"').strip("'")
    except Exception:
        pass
    return ""


def gonka_api_key() -> str:
    """
    The configured GonkaRouter API key.

    Reads GONKA_API_KEY from the environment (.env) and falls back to
    local.properties in the repository root.
    """
    key = os.getenv("GONKA_API_KEY", "").strip()
    if not key:
        key = _read_local_properties_key()
    return key
