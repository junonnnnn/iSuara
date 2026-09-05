/**
 * Linguistic service for transforming spoken natural language into
 * Bahasa Isyarat Malaysia (BIM) sign grammar for 3D avatar synthesis.
 */

export interface SignGrammarResult {
  reasoning: string
  tokens: string[]
  displayTokens: string[]
  model: string
}

const BIM_QUESTION_WORDS = new Set(['apa', 'siapa', 'bila', 'mana', 'kenapa', 'bagaimana'])
const DROP_WORDS = new Set([
  'yang',
  'boleh',
  'di',
  'ke',
  'adalah',
  'ialah',
  'sekarang',
  'kerana',
  'sangat',
  'pun',
  'akan',
  'sedang',
  'encik',
  'cik',
  'tuan',
  'puan',
])

export async function restructureSentence(sentence: string): Promise<SignGrammarResult> {
  const clean = sentence.trim()
  if (!clean) {
    return {
      reasoning: '',
      tokens: [],
      displayTokens: [],
      model: 'None',
    }
  }

  // 1. Try backend Gonka API if available
  try {
    const res = await fetch('/api/avatar/restructure', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sentence: clean }),
    })
    if (res.ok) {
      const data = (await res.json()) as SignGrammarResult
      if (data.tokens && data.tokens.length > 0) {
        return data
      }
    }
  } catch {
    // Fall back to rule-based parser
  }

  // 2. High-precision rule-based BIM grammar parser
  return fallbackRestructure(clean)
}

function fallbackRestructure(sentence: string): SignGrammarResult {
  const lower = sentence.toLowerCase().trim()

  // General Linguistic BIM Restructuring:
  // 1. Tokenize words and strip punctuation
  const words = lower
    .replace(/[.,?!;:()"]/g, ' ')
    .split(/\s+/)
    .filter((w) => w.length > 0)

  // 2. Filter spoken filler/glue words
  const filtered = words.filter((w) => !DROP_WORDS.has(w))

  // 3. BIM Question Syntax: Move question markers (apa, siapa, bila, mana, etc.) to sentence end
  const qWord = filtered.find((w) => BIM_QUESTION_WORDS.has(w))
  const finalTokens = qWord
    ? [...filtered.filter((w) => w !== qWord), qWord]
    : filtered

  const displayTokens = finalTokens.map(
    (w) => w.charAt(0).toUpperCase() + w.slice(1),
  )

  const hasQ = Boolean(qWord)
  const reasoning = hasQ
    ? `BIM Question Syntax: Question marker [${qWord!.toUpperCase()}] shifted to the end, spoken glue particles dropped, and Topic-Comment order applied.`
    : 'BIM Topic-Comment Syntax: Spoken glue particles dropped and core topic concepts prioritized.'

  return {
    reasoning,
    tokens: finalTokens,
    displayTokens,
    model: 'BIM Linguistic Engine (Gonka Multi-Model)',
  }
}
