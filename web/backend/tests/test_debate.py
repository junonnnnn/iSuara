import pytest
from app import translator


@pytest.mark.asyncio
async def test_gonka_debate():
    events = []
    async for event in translator.debate(["SAYA", "MAKAN"]):
        events.append(event)

    stages = [e["stage"] for e in events if "stage" in e]
    assert "CONSULTING" in stages
    assert "JUDGING" in stages or "IDLE" in stages

    candidates = [e["candidate"] for e in events if "candidate" in e]
    assert len(candidates) >= 1
    # Check that at least one candidate has a valid Gonka request ID
    successful = [c for c in candidates if not c.get("failed") and c.get("sentence")]
    assert len(successful) >= 1
    assert any(c.get("requestId") for c in successful)

    translations = [e["translation"] for e in events if "translation" in e]
    assert len(translations) == 1
    t = translations[0]
    assert "ms" in t and len(t["ms"]) > 0
    assert "requestIds" in t
