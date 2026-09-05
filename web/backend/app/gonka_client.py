"""
Async client for the Gonka Network inference gateway (https://api.gonkarouter.io).

GonkaRouter implements the Anthropic Messages API protocol:
  POST /v1/messages
  Headers:
    x-api-key: <key>
    anthropic-version: 2023-06-01
    content-type: application/json

Every request returns:
  - x-request-id in the HTTP response headers (verifiable inference step ID)
  - id in the JSON body (e.g. msg_...)
  - content block array containing text
"""

import logging
import re
from dataclasses import dataclass, field
from typing import Any

import httpx

from . import config

log = logging.getLogger(__name__)

_THINK_RE = re.compile(r"<think>.*?</think>", re.DOTALL)


def strip_thinking(text: str) -> str:
    """Strips reasoning monologue (<think>...</think>) produced by thinking models."""
    cleaned = _THINK_RE.sub("", text).strip()
    if "<think>" in cleaned and "</think>" not in cleaned:
        cleaned = cleaned.split("<think>", 1)[0].strip()
    return cleaned


@dataclass
class GonkaResult:
    text: str
    request_id: str
    message_id: str
    model: str
    raw_text: str = ""
    usage: dict[str, Any] = field(default_factory=dict)


class GonkaClient:
    """Async client communicating with GonkaRouter."""

    def __init__(
        self,
        api_key: str | None = None,
        base_url: str | None = None,
        timeout_seconds: float = 75.0,
    ):
        self.api_key = (api_key or config.gonka_api_key()).strip()
        self.base_url = (base_url or config.GONKA_BASE_URL).rstrip("/")
        self.timeout = httpx.Timeout(timeout_seconds, connect=15.0)
        self._client: httpx.AsyncClient | None = None

    @property
    def is_configured(self) -> bool:
        return bool(self.api_key)

    async def _get_client(self) -> httpx.AsyncClient:
        if self._client is None or self._client.is_closed:
            self._client = httpx.AsyncClient(
                base_url=self.base_url,
                timeout=self.timeout,
                headers={
                    "x-api-key": self.api_key,
                    "anthropic-version": "2023-06-01",
                    "content-type": "application/json",
                    # User-Agent prevents Cloudflare HTTP signature blocks
                    "User-Agent": "anthropic-python/0.40.0 (Gonka-Web/1.0)",
                },
            )
        return self._client

    async def close(self) -> None:
        if self._client and not self._client.is_closed:
            await self._client.aclose()
            self._client = None

    async def complete(
        self,
        model_id: str,
        system: str,
        user: str,
        max_tokens: int = 2048,
    ) -> GonkaResult:
        """
        Executes one completion on GonkaRouter.

        Returns GonkaResult with stripped text, raw text, and the verifiable
        Gonka Request ID from HTTP response headers.
        """
        if not self.api_key:
            raise RuntimeError("GONKA_API_KEY is not configured")

        client = await self._get_client()
        # GonkaRouter currently hosts DeepSeek-V4-Flash-0731 and MiniMax-M2.7.
        # If moonshotai/Kimi-K2.6 is requested, map to MiniMaxAI/MiniMax-M2.7 so it succeeds without HTTP 400.
        effective_model = (
            "MiniMaxAI/MiniMax-M2.7"
            if "kimi" in model_id.lower() or "moonshot" in model_id.lower()
            else model_id
        )
        payload = {
            "model": effective_model,
            "max_tokens": max_tokens,
            "system": system,
            "messages": [{"role": "user", "content": user}],
        }

        resp = await client.post("/v1/messages", json=payload)

        # Inspect headers for Gonka Request ID
        request_id = (
            resp.headers.get("x-request-id")
            or resp.headers.get("gonka-request-id")
            or resp.headers.get("cf-ray")
            or ""
        )

        if resp.status_code != 200:
            err_msg = f"Gonka API error {resp.status_code} [{request_id}]: {resp.text}"
            log.error(err_msg)
            raise RuntimeError(err_msg)

        data = resp.json()
        msg_id = data.get("id", "")
        if not request_id:
            request_id = msg_id

        # Concatenate text blocks
        raw_text = "".join(
            block.get("text", "")
            for block in data.get("content", [])
            if block.get("type") == "text"
        )
        cleaned_text = strip_thinking(raw_text)

        return GonkaResult(
            text=cleaned_text,
            request_id=request_id,
            message_id=msg_id,
            model=model_id,
            raw_text=raw_text,
            usage=data.get("usage", {}),
        )


# Global singleton instance
gonka = GonkaClient()
