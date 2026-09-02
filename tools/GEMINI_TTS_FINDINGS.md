# Gemini TTS spike — findings

Measured 2026-09-02 against the keys in `local.properties`, via
`generativelanguage.googleapis.com`. Recorded here because several of these
shaped the design of `GeminiTtsService` and are not obvious from the docs.

## Contract (verified working)

    POST /v1beta/models/{model}:generateContent
    Header: x-goog-api-key: <plain API key>          # no OAuth, no service account
    Body:   contents[0].parts[0].text  = "<style instruction>: <text to speak>"
            generationConfig.responseModalities = ["AUDIO"]
            generationConfig.speechConfig.voiceConfig.prebuiltVoiceConfig.voiceName = "Kore"
    Reply:  candidates[0].content.parts[0].inlineData
            mimeType = "audio/L16;codec=pcm;rate=24000"
            data     = base64 raw PCM, 16-bit signed LE, mono, 24 kHz  (no header)

A plain API key is enough — the Cloud TTS product needs OAuth, the Gemini API
does not. That is why this integration targets the Gemini API.

## The style instruction is obeyed, not spoken

Verified by holding the payload fixed and varying instruction length:

| input                                             | audio |
|---------------------------------------------------|-------|
| a ~35-word instruction + "Tolong."                 | 0.97s |
| "Tolong! Panggil polis, cepat!" bare               | 2.29s |
| same, prefixed "urgently and fearfully, fast"      | 1.85s |
| same, prefixed "softly and heavily, slowly"        | 4.60s |

A spoken instruction would have made the first row ~12s. It is 0.97s, and the
fear/sad pair moves in the right direction. Prompted emotion genuinely works.

## Models

| model                        | notes                                  |
|------------------------------|----------------------------------------|
| gemini-2.5-flash-preview-tts | works                                  |
| gemini-3.1-flash-tts-preview | works, better success rate in sampling |
| gemini-2.5-flash-tts         | 404 — not a valid id on this API       |

## Failure modes that the fallback has to cover

These are why `SpeechRouter` falls back on *any* failure, not just offline:

1. **HTTP 429** — frequent on this tier. Hit 3/5 and 1/5 of calls in one run.
   Mitigated by rotating the three keys in `local.properties`.
2. **`finishReason: OTHER` with no content part** — a 200 response carrying no
   audio at all. Must be treated as failure, not dereferenced.
3. **HTTP 400 "Model tried to generate text, but it should only be used for
   TTS"** — triggered by a bare word with no instruction. Always send a style
   instruction prefix, never raw text.

## Latency

3.0-4.4s end to end. Higher than the 3s the plan assumed, so the router's
timeout is 8s. This sits on top of the existing debate latency and is only
entered once per sentence.
