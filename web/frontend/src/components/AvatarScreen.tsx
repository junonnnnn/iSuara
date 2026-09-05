/**
 * The "Text → Sign" tab — ports avatar/ui/AvatarPlayerScreen.kt.
 *
 * A full-bleed 3D viewport with interactive BIM sign language grammar restructuring.
 * Spoken Malay input is restructured into Topic-Comment syntax and signed continuously.
 *
 * Drag to orbit, wheel or pinch to zoom, matching TouchOrbitController.kt.
 */

import { useCallback, useEffect, useRef, useState } from 'react'

import { AvatarRenderer } from '../lib/avatar/renderer'
import { MotionPlayer } from '../lib/avatar/motionPlayer'
import { CATALOG, loadMotion, resolveQuery, synthesizeSentence } from '../lib/avatar/repository'
import { restructureSentence, type SignGrammarResult } from '../lib/avatar/signGrammar'

export function AvatarScreen() {
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const rendererRef = useRef<AvatarRenderer | null>(null)
  const playerRef = useRef<MotionPlayer | null>(null)
  const rafRef = useRef<number | null>(null)

  const [input, setInput] = useState('')
  const [activeWord, setActiveWord] = useState('')
  const [status, setStatus] = useState('')

  // Multi-model grammar reasoning state
  const [isReasoning, setIsReasoning] = useState(false)
  const [bimTokens, setBimTokens] = useState<string[]>([])
  const [activeModel, setActiveModel] = useState<string | null>(null)
  const [grammarData, setGrammarData] = useState<SignGrammarResult | null>(null)
  const [showReasoningPath, setShowReasoningPath] = useState(false)

  if (!playerRef.current) playerRef.current = new MotionPlayer()
  const player = playerRef.current

  // ── renderer lifecycle ──
  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return

    const renderer = new AvatarRenderer(canvas)
    rendererRef.current = renderer

    let last = performance.now()
    const loop = () => {
      rafRef.current = requestAnimationFrame(loop)
      const now = performance.now()
      const delta = Math.min(Math.max((now - last) / 1000, 0.001), 0.1)
      last = now
      renderer.render(player.advanceTime(delta))
    }
    rafRef.current = requestAnimationFrame(loop)

    const observer = new ResizeObserver(() => renderer.resize())
    observer.observe(canvas)

    return () => {
      if (rafRef.current !== null) cancelAnimationFrame(rafRef.current)
      observer.disconnect()
      renderer.dispose()
      rendererRef.current = null
    }
  }, [player])

  const play = useCallback(
    async (query: string) => {
      const effective = query.trim()
      if (!effective) return
      setStatus('')

      // 1. Direct Catalog Match for single glosses
      const clean = effective.toLowerCase().replace(/\s+/g, '_')
      const direct = CATALOG.find(
        (v) => v.key.toLowerCase() === clean || v.title.toLowerCase() === effective.toLowerCase(),
      )

      if (direct) {
        setGrammarData(null)
        setBimTokens([])
        setActiveModel(null)
        const motion = await loadMotion(direct.key)
        if (motion) {
          player.isLooping = false
          player.setMotion(motion, true)
          setActiveWord(motion.word)
        }
        return
      }

      // 2. Multi-word Sentence Restructuring via AI BIM Grammar
      setIsReasoning(true)
      try {
        const grammarResult = await restructureSentence(effective)
        setGrammarData(grammarResult)
        setBimTokens(grammarResult.displayTokens)
        setActiveModel(grammarResult.model)

        const motion = await synthesizeSentence(grammarResult.tokens)
        if (motion) {
          player.isLooping = false
          player.setMotion(motion, true)
          setActiveWord(motion.word)
        } else {
          // Fallback to resolveQuery
          const resolved = await resolveQuery(effective)
          if (resolved) {
            player.isLooping = false
            player.setMotion(resolved.motion, true)
            setActiveWord(resolved.motion.word)
          } else {
            setStatus(`No sign found for "${effective}"`)
          }
        }
      } catch (err) {
        console.error('BIM grammar reasoning failed', err)
      } finally {
        setIsReasoning(false)
      }
    },
    [player],
  )

  // ── orbit / zoom ──
  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return

    let dragging = false
    let lastX = 0
    let lastY = 0

    const down = (e: PointerEvent) => {
      dragging = true
      lastX = e.clientX
      lastY = e.clientY
      canvas.setPointerCapture(e.pointerId)
    }
    const move = (e: PointerEvent) => {
      if (!dragging) return
      const dx = e.clientX - lastX
      const dy = e.clientY - lastY
      lastX = e.clientX
      lastY = e.clientY
      rendererRef.current?.orbit(-dx * 0.01, dy * 0.005)
    }
    const up = (e: PointerEvent) => {
      dragging = false
      canvas.releasePointerCapture(e.pointerId)
    }
    const wheel = (e: WheelEvent) => {
      e.preventDefault()
      rendererRef.current?.zoom(e.deltaY > 0 ? 1.08 : 0.92)
    }

    canvas.addEventListener('pointerdown', down)
    canvas.addEventListener('pointermove', move)
    canvas.addEventListener('pointerup', up)
    canvas.addEventListener('pointercancel', up)
    canvas.addEventListener('wheel', wheel, { passive: false })
    return () => {
      canvas.removeEventListener('pointerdown', down)
      canvas.removeEventListener('pointermove', move)
      canvas.removeEventListener('pointerup', up)
      canvas.removeEventListener('pointercancel', up)
      canvas.removeEventListener('wheel', wheel)
    }
  }, [])

  return (
    <div className="avatar">
      <canvas ref={canvasRef} className="avatar__canvas" />

      <div className="avatar__hud">
        <span className="avatar__word">{activeWord || '—'}</span>
      </div>

      {status && <p className="avatar__status">{status}</p>}

      <div className="avatar__controls-bottom">

        {/* AI BIM Grammar Reasoning, Final Sign & Collapsible 3-Model Path */}
        {(isReasoning || bimTokens.length > 0) && (
          <div className="avatar__reasoning-card">
            {isReasoning ? (
              <div className="avatar__reasoning-loading">
                <div className="avatar__spinner" />
                <span>Analyzing BIM Grammar across 3 Gonka Models…</span>
              </div>
            ) : (
              <>
                <div className="avatar__reasoning-header">
                  <div className="avatar__reasoning-header-left">
                    <span className="avatar__reasoning-title">BIM Final Sign Sequence</span>
                    <span className="avatar__final-badge">Final Sign</span>
                  </div>
                  {activeModel && <span className="avatar__reasoning-model">{activeModel}</span>}
                </div>

                {/* Final Sign Sequence Chips */}
                <div className="avatar__token-row">
                  {bimTokens.map((token, i) => (
                    <span key={i} className="avatar__token-wrapper">
                      <span className="avatar__token-chip avatar__token-chip--final">{token}</span>
                      {i < bimTokens.length - 1 && <span className="avatar__token-arrow">→</span>}
                    </span>
                  ))}
                </div>

                {/* Collapsible Accordion Toggle */}
                {grammarData?.candidates && grammarData.candidates.length > 0 && (
                  <button
                    type="button"
                    className="avatar__accordion-toggle"
                    onClick={() => setShowReasoningPath((prev) => !prev)}
                    title="Toggle multi-model reasoning breakdown"
                  >
                    <span className="avatar__accordion-icon">{showReasoningPath ? '▴' : '▾'}</span>
                    <span className="avatar__accordion-label">
                      {showReasoningPath ? 'Hide 3-Model Reasoning Path' : 'Show 3-Model Reasoning Path'}
                    </span>
                    <span className="avatar__models-count-tag">3 Models</span>
                  </button>
                )}

                {/* Collapsible 3-Model Reasoning Path Details */}
                {showReasoningPath && grammarData?.candidates && grammarData.candidates.length > 0 && (
                  <div className="avatar__multi-model-panel">
                    <div className="avatar__candidate-grid">
                      {grammarData.candidates.map((cand) => (
                        <div
                          key={cand.model}
                          className={`avatar__candidate-card${cand.isWinner ? ' avatar__candidate-card--winner' : ''}`}
                        >
                          <div className="avatar__candidate-header">
                            <span className="avatar__candidate-model">
                              {cand.model}
                              {cand.isWinner && <span className="avatar__candidate-winner-tag">consensus pick</span>}
                            </span>
                            {cand.requestId && (
                              <span
                                className="avatar__candidate-req"
                                title={`Verifiable Gonka Request ID: ${cand.requestId}`}
                              >
                                req:{cand.requestId.length > 18 ? cand.requestId.slice(0, 16) + '…' : cand.requestId}
                              </span>
                            )}
                          </div>

                          <div className="avatar__candidate-tokens">
                            {cand.displayTokens.map((tok, idx) => (
                              <span key={idx} className="avatar__candidate-token">
                                {tok}
                              </span>
                            ))}
                          </div>
                        </div>
                      ))}
                    </div>

                    {/* Consensus Judge Verdict */}
                    {grammarData.verdict && (
                      <div className="avatar__judge-banner">
                        <div className="avatar__judge-header">
                          <span className="avatar__judge-title">
                            ⚖️ {grammarData.verdict.judgeModel}
                          </span>
                          {grammarData.verdict.requestId && (
                            <span
                              className="avatar__candidate-req"
                              title={`Judge Gonka Request ID: ${grammarData.verdict.requestId}`}
                            >
                              Judge Req ID: {grammarData.verdict.requestId}
                            </span>
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                )}
              </>
            )}
          </div>
        )}

        {/* Input Bar Form */}
        <form
          className="avatar__input-row"
          onSubmit={(e) => {
            e.preventDefault()
            void play(input)
          }}
        >
          <input
            className="avatar__input"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Taip text or sentence"
            list="avatar-vocabulary"
            enterKeyHint="go"
            autoComplete="off"
          />
          <datalist id="avatar-vocabulary">
            {CATALOG.map((item) => (
              <option key={item.key} value={item.title}>
                {item.translation}
              </option>
            ))}
          </datalist>
          <button
            type="submit"
            className="avatar__send"
            title="Play sign"
            disabled={isReasoning || !input.trim()}
          >
            <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor" aria-hidden="true">
              <path d="M4 11h12.17l-5.59-5.59L12 4l8 8-8 8-1.41-1.41L16.17 13H4z" />
            </svg>
            <span className="sr-only">Play sign</span>
          </button>
        </form>
      </div>
    </div>
  )
}
