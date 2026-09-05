"""
The multi-model consensus translator running on the Gonka Network (https://api.gonkarouter.io).

BIM glosses arrive loosely ordered and the mapping to a sentence is genuinely
ambiguous, so a single model just commits to one reading. Three distinct
Gonka-hosted models (DeepSeek V4-Flash, MiniMax M2.7, Kimi K2.6) answer in parallel
and DeepSeek adjudicates the consensus.

Every inference step records and propagates its verifiable Gonka Request ID
(from the HTTP x-request-id response header) directly to the client for the
Transparency UI.

The judge returns an INDEX, not a sentence. That way the result is always
something an agent actually proposed — the judge cannot invent a fourth answer —
and the four languages stay mutually consistent because they come from one
agent's single response.
"""

import asyncio
import logging
from typing import AsyncIterator

from . import config, parsing, prompts
from .gonka_client import GonkaResult, gonka

log = logging.getLogger(__name__)

# The three models that debate on the Gonka Network.
DEFAULT_MODEL = config.DEFAULT_MODEL
AGENT_MODELS = config.AGENT_MODELS
JUDGE_MODEL = config.JUDGE_MODEL

# Compatibility alias for main.py health check
clients = gonka

# Where the pipeline currently is, for display while the user waits.
STAGE_LABELS = {
    "IDLE": "",
    "CONSULTING": "Consulting Gonka interpreters…",
    "COLLECTED": "Interpretations received",
    "JUDGING": "Weighing consensus on Gonka Network…",
    "DECIDING": "Adjudicating final translation…",
}

# Hold long enough for each stage transition to be read (mirrors STAGE_HOLD_MS = 400L in DebateTranslator.kt).
_STAGE_HOLD_S = 0.4


async def _agent(
    words: list[str],
    model_id: str,
    emotion: dict | None = None,
) -> tuple[dict[str, str | None], str]:
    """
    One Gonka interpreter: gloss list in, translation dict + request_id out.

    Every agent receives the identical system prompt. One retry on parse errors,
    as models occasionally prepend formatting or markdown commentary on initial attempt.
    """
    user = prompts.user_turn(words, emotion)
    last_error: Exception | None = None

    for attempt in range(2):
        try:
            res: GonkaResult = await gonka.complete(
                model_id=model_id,
                system=prompts.SYSTEM,
                user=user,
                max_tokens=2048,
            )
            translation = parsing.extract_translation(res.text)
            log.info("[%s] %s -> %s (req_id: %s)", model_id, words, translation["ms"], res.request_id)
            return translation, res.request_id
        except ValueError as e:
            last_error = e
            log.warning("[%s] unparseable reply, attempt %d: %s", model_id, attempt + 1, e)
        except Exception as e:
            # Network or timeout errors: do not retry to avoid long stalls
            log.warning("[%s] network or timeout error: %s", model_id, e)
            raise

    raise last_error or RuntimeError("translation failed")


async def _judge(
    words: list[str], candidates: list[dict[str, str | None]]
) -> tuple[int, str, str]:
    """
    Returns (winning_candidate_index, reason, judge_request_id).
    """
    log.info("judging consensus on GonkaRouter with %s", JUDGE_MODEL)
    res: GonkaResult = await gonka.complete(
        model_id=JUDGE_MODEL,
        system=prompts.JUDGE,
        user=prompts.judge_turn(words, candidates),
        max_tokens=1024,
    )
    choice, reason = parsing.extract_choice(res.text, len(candidates))
    log.info('judge chose [%d] "%s" — %s (req_id: %s)', choice, candidates[choice]["ms"], reason, res.request_id)
    return choice, reason, res.request_id


async def debate(words: list[str], emotion: dict | None = None) -> AsyncIterator[dict]:
    """
    Runs the multi-model debate on Gonka Network, streaming events as they happen.

    Events:
      {"stage": ...}                                     pipeline stage transition
      {"candidates": [...]}                              initial slots with model names
      {"candidate": {index, model, sentence, failed, requestId}}  one model resolved
      {"verdict": {choice, reason, requestId}}           judge decision with Gonka request ID
      {"translation": {...}}                             final output with request ID traceability
    """
    if not words:
        raise ValueError("no glosses to translate")

    yield {"stage": "CONSULTING"}

    # Announce slots up front so the UI renders all three models as pending
    yield {
        "candidates": [
            {"index": i, "model": model, "sentence": None, "failed": False, "requestId": None}
            for i, model in enumerate(AGENT_MODELS)
        ]
    }

    async def run(index: int) -> tuple[int, dict[str, str | None] | None, str | None, Exception | None]:
        try:
            translation, req_id = await _agent(words, AGENT_MODELS[index], emotion)
            return index, translation, req_id, None
        except Exception as e:
            return index, None, None, e

    tasks = [asyncio.create_task(run(i)) for i in range(len(AGENT_MODELS))]

    resolved: dict[int, dict[str, str | None]] = {}
    request_ids: dict[int, str] = {}

    for finished in asyncio.as_completed(tasks):
        index, translation, req_id, error = await finished
        model = AGENT_MODELS[index]

        if error is not None or translation is None:
            log.warning("interpreter[%d] (%s) failed: %s", index, model, error)
            yield {
                "candidate": {
                    "index": index,
                    "model": model,
                    "sentence": None,
                    "failed": True,
                    "requestId": req_id,
                }
            }
            continue

        resolved[index] = translation
        if req_id:
            request_ids[index] = req_id

        log.info("candidate[%d] (%s): %s", index, model, translation["ms"])
        yield {
            "candidate": {
                "index": index,
                "model": model,
                "sentence": translation["ms"],
                "failed": False,
                "requestId": req_id,
            }
        }

    candidates = [resolved[i] for i in sorted(resolved)]

    if not candidates:
        raise RuntimeError(f"all {len(AGENT_MODELS)} Gonka interpreters failed")

    # If only one candidate succeeded, return it unjudged
    if len(candidates) == 1:
        log.info("only one interpreter succeeded, returning it unjudged")
        yield {"stage": "IDLE"}
        yield {"translation": candidates[0]}
        return

    yield {"stage": "COLLECTED"}
    await asyncio.sleep(_STAGE_HOLD_S)
    yield {"stage": "JUDGING"}

    reason = ""
    judge_req_id = ""
    try:
        chosen, reason, judge_req_id = await _judge(words, candidates)
    except Exception as e:
        log.warning("judge failed, falling back to first candidate: %s", e)
        chosen = 0

    yield {
        "verdict": {
            "choice": chosen,
            "reason": reason,
            "requestId": judge_req_id,
        }
    }
    yield {"stage": "DECIDING"}
    await asyncio.sleep(_STAGE_HOLD_S)
    yield {"stage": "IDLE"}

    final_translation = dict(candidates[chosen])
    final_translation["requestIds"] = [
        request_ids.get(i) for i in sorted(resolved) if i in request_ids
    ]
    if judge_req_id:
        final_translation["judgeRequestId"] = judge_req_id

    yield {"translation": final_translation}
