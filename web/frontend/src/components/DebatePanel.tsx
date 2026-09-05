/**
 * The debate, shown in full underneath the translation.
 *
 * Every interpreter's answer is on screen from the moment its model replies,
 * with the judge's pick marked. Rows fill in individually rather than all at
 * once, so a slow model does not hold up the two that already answered.
 *
 * This used to be a collapsed accordion, on the reasoning that the finished
 * sentence is the point and the machinery behind it should not compete with it.
 * That was the wrong call for this app: the multi-model debate IS the thing
 * worth showing, and hiding it behind a control meant it was never seen. It is
 * open, always, with no toggle to find.
 */

import { shortModelName, STAGE_LABELS, type DebateProgress } from '../lib/translateClient'

interface Props {
  progress: DebateProgress
  /** True while the request is still running. */
  active: boolean
}

export function DebatePanel({ progress, active }: Props) {
  const { candidates, verdict, stage } = progress
  if (candidates.length === 0 && !active) return null

  const answered = candidates.filter((c) => c.sentence !== null).length
  const failed = candidates.filter((c) => c.failed).length

  const heading = active
    ? STAGE_LABELS[stage] || 'Gonka Network Consensus…'
    : `Gonka Consensus: ${answered} of ${candidates.length} models resolved`

  return (
    <section className="debate">
      <div className="debate__header-row">
        <h3 className="debate__heading">
          {active && <span className="spinner" aria-hidden="true" />}
          {heading}
        </h3>
        <span className="debate__gonka-tag" title="Inference running via api.gonkarouter.io">
          Gonka Network
        </span>
      </div>

      <ol className="debate__agents">
        {candidates.map((c) => {
          const chosen = verdict?.choice === c.index
          return (
            <li
              key={c.index}
              className={`debate__agent${chosen ? ' debate__agent--chosen' : ''}`}
            >
              <div className="debate__model-row">
                <span className="debate__model">
                  {shortModelName(c.model)}
                  {chosen && <span className="debate__chosen-tag">consensus pick</span>}
                </span>
                {c.requestId && (
                  <span
                    className="debate__req-badge"
                    title={`Verifiable Gonka Request ID: ${c.requestId}`}
                  >
                    req:{c.requestId.length > 20 ? c.requestId.slice(0, 18) + '…' : c.requestId}
                  </span>
                )}
              </div>
              {c.failed ? (
                <span className="debate__failed">unavailable / timed out</span>
              ) : c.sentence ? (
                <span className="debate__sentence">{c.sentence}</span>
              ) : (
                <span className="debate__pending">evaluating on Gonka…</span>
              )}
            </li>
          )
        })}
      </ol>

      {verdict && (
        <div className="debate__verdict-block">
          <p className="debate__verdict">
            <strong>Gonka Judge (DeepSeek):</strong> {verdict.reason || 'adjudicated consensus across models'}
          </p>
          {verdict.requestId && (
            <span className="debate__verdict-req" title={`Judge Gonka Request ID: ${verdict.requestId}`}>
              Judge Req ID: {verdict.requestId}
            </span>
          )}
        </div>
      )}

      {failed > 0 && (
        <p className="debate__note">
          {failed} model{failed > 1 ? 's' : ''} did not respond within timeout — consensus
          evaluated with the remaining models.
        </p>
      )}
    </section>
  )
}
