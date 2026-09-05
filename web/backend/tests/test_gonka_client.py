import asyncio
import pytest
from app.gonka_client import strip_thinking, GonkaClient, GonkaResult


def test_strip_thinking():
    raw = "<think>Let me evaluate this BIM sign.</think>{\"ms\": \"Saya lapar\"}"
    assert strip_thinking(raw) == '{"ms": "Saya lapar"}'

    no_think = '{"ms": "Saya lapar"}'
    assert strip_thinking(no_think) == '{"ms": "Saya lapar"}'

    unclosed = "<think>Still reasoning"
    assert strip_thinking(unclosed) == ""


@pytest.mark.asyncio
async def test_gonka_client_complete():
    client = GonkaClient()
    if not client.is_configured:
        pytest.skip("Gonka API key not set")

    # Fast test with DeepSeek V4 Flash
    res = await client.complete(
        model_id="deepseek-ai/DeepSeek-V4-Flash-0731",
        system="Reply with single word 'OK'",
        user="Test",
        max_tokens=10,
    )
    assert isinstance(res, GonkaResult)
    assert res.request_id.startswith("req-") or len(res.request_id) > 5
    assert len(res.text) > 0
    await client.close()
