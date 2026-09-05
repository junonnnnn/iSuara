/**
 * Vocabulary catalog, clip loading and sentence synthesis — ports
 * avatar/data/MotionRepository.kt.
 *
 * The catalog is transcribed from the Kotlin rather than derived from the
 * filenames, because it carries the display titles, English glosses and
 * categories the UI matches against — none of which the files themselves know.
 *
 * Clips are fetched over HTTP and cached: each is 100-300KB, and a sentence
 * replays the same words repeatedly.
 */

import { lerp, parseMotion, type BimFrame, type BimMotion, type HandJoints } from './motion'

export interface VocabularyItem {
  key: string
  title: string
  translation: string
  category: string
  isSentence?: boolean
  assetFileName: string
}

export const CATALOG: VocabularyItem[] = [
  // Sentences
  { key: 'apa_khabar_suhu', title: 'Apa Khabar → Suhu', translation: 'Sentence: How are you → Temperature', category: 'Continuous Sentences', isSentence: true, assetFileName: 'apa_khabar_suhu_3d.json' },
  { key: 'suhu_apa_khabar', title: 'Suhu → Apa Khabar', translation: 'Sentence: Temperature → How are you', category: 'Continuous Sentences', isSentence: true, assetFileName: 'suhu_apa_khabar_3d.json' },

  // 10 audited pilot vocabulary
  { key: 'terima_kasih', title: 'Terima Kasih', translation: 'Thank you', category: 'Pilot Benchmarks', assetFileName: 'terima_kasih.json' },
  { key: 'makan', title: 'Makan', translation: 'Eat', category: 'Pilot Benchmarks', assetFileName: 'makan.json' },
  { key: 'gembira', title: 'Gembira', translation: 'Happy', category: 'Pilot Benchmarks', assetFileName: 'gembira.json' },
  { key: 'cinta', title: 'Cinta', translation: 'Love', category: 'Pilot Benchmarks', assetFileName: 'cinta.json' },
  { key: 'belajar', title: 'Belajar', translation: 'Study / Learn', category: 'Pilot Benchmarks', assetFileName: 'belajar.json' },
  { key: 'doktor', title: 'Doktor', translation: 'Doctor', category: 'Pilot Benchmarks', assetFileName: 'doktor.json' },
  { key: 'baju', title: 'Baju', translation: 'Clothes', category: 'Pilot Benchmarks', assetFileName: 'baju.json' },
  { key: 'air', title: 'Air', translation: 'Water', category: 'Pilot Benchmarks', assetFileName: 'air.json' },
  { key: 'abang', title: 'Abang', translation: 'Brother', category: 'Pilot Benchmarks', assetFileName: 'abang.json' },
  { key: 'apa_khabar', title: 'Apa Khabar', translation: 'How are you', category: 'Pilot Benchmarks', assetFileName: 'apa_khabar_3d.json' },
  { key: 'suhu', title: 'Suhu', translation: 'Temperature', category: 'Pilot Benchmarks', assetFileName: 'suhu_3d.json' },

  // Common conversation and greetings
  { key: 'hai', title: 'Hai', translation: 'Hi / Hello', category: 'Daily Conversation', assetFileName: 'hai.json' },
  { key: 'hello', title: 'Hello', translation: 'Hello', category: 'Daily Conversation', assetFileName: 'hello.json' },
  { key: 'saya', title: 'Saya', translation: 'I / Me', category: 'Daily Conversation', assetFileName: 'saya.json' },
  { key: 'awak', title: 'Awak', translation: 'You', category: 'Daily Conversation', assetFileName: 'awak.json' },
  { key: 'nama', title: 'Nama', translation: 'Name', category: 'Daily Conversation', assetFileName: 'nama.json' },
  { key: 'tolong', title: 'Tolong', translation: 'Help / Please', category: 'Daily Conversation', assetFileName: 'tolong.json' },
  { key: 'sama_sama', title: 'Sama-sama', translation: "You're welcome", category: 'Daily Conversation', assetFileName: 'sama_sama.json' },
  { key: 'baik', title: 'Baik', translation: 'Good / Fine', category: 'Daily Conversation', assetFileName: 'baik.json' },
  { key: 'cantik', title: 'Cantik', translation: 'Beautiful', category: 'Daily Conversation', assetFileName: 'cantik.json' },
  { key: 'ibu', title: 'Ibu', translation: 'Mother', category: 'Family', assetFileName: 'ibu.json' },
  { key: 'bapa', title: 'Bapa', translation: 'Father', category: 'Family', assetFileName: 'bapa.json' },
  { key: 'sekolah', title: 'Sekolah', translation: 'School', category: 'Places', assetFileName: 'sekolah.json' },
  { key: 'hospital', title: 'Hospital', translation: 'Hospital', category: 'Places', assetFileName: 'hospital.json' },

  // Numbers and letters
  { key: '1', title: '1 (Satu)', translation: 'One', category: 'Numbers', assetFileName: '1.json' },
  { key: '2', title: '2 (Dua)', translation: 'Two', category: 'Numbers', assetFileName: '2.json' },
  { key: '3', title: '3 (Tiga)', translation: 'Three', category: 'Numbers', assetFileName: '3.json' },
  { key: '4', title: '4 (Empat)', translation: 'Four', category: 'Numbers', assetFileName: '4.json' },
  { key: '5', title: '5 (Lima)', translation: 'Five', category: 'Numbers', assetFileName: '5.json' },
  { key: 'a', title: 'Huruf A', translation: 'Letter A', category: 'Alphabet', assetFileName: 'a.json' },
  { key: 'b', title: 'Huruf B', translation: 'Letter B', category: 'Alphabet', assetFileName: 'b.json' },
  { key: 'c', title: 'Huruf C', translation: 'Letter C', category: 'Alphabet', assetFileName: 'c.json' },

  // ── Curated Core Vocabulary for Police & Doctor Interactions (from SignAvatar-Muba) ──
  // Emergency & Police
  { key: 'polis', title: 'Polis', translation: 'Police', category: 'Emergency', assetFileName: 'polis.json' },
  { key: 'curi', title: 'Curi', translation: 'Steal / Theft', category: 'Emergency', assetFileName: 'curi.json' },
  { key: 'hilang', title: 'Hilang', translation: 'Lost / Missing', category: 'Emergency', assetFileName: 'hilang.json' },
  { key: 'tangkap', title: 'Tangkap', translation: 'Catch / Arrest', category: 'Emergency', assetFileName: 'tangkap.json' },
  { key: 'jahat', title: 'Jahat', translation: 'Bad / Evil', category: 'Emergency', assetFileName: 'jahat.json' },
  { key: 'bahaya', title: 'Bahaya', translation: 'Danger', category: 'Emergency', assetFileName: 'bahaya.json' },
  { key: 'selamat', title: 'Selamat', translation: 'Safe', category: 'Emergency', assetFileName: 'selamat.json' },
  { key: 'duit', title: 'Duit', translation: 'Money', category: 'Emergency', assetFileName: 'duit.json' },
  { key: 'kereta', title: 'Kereta', translation: 'Car', category: 'Emergency', assetFileName: 'kereta.json' },
  { key: 'berita', title: 'Berita', translation: 'News / Report', category: 'Emergency', assetFileName: 'berita.json' },
  { key: 'kunci', title: 'Kunci', translation: 'Key', category: 'Emergency', assetFileName: 'kunci.json' },
  { key: 'tengok', title: 'Tengok', translation: 'Look / Watch', category: 'Emergency', assetFileName: 'tengok.json' },
  { key: 'lihat', title: 'Lihat', translation: 'See', category: 'Emergency', assetFileName: 'lihat.json' },
  { key: 'alamat', title: 'Alamat', translation: 'Address', category: 'Emergency', assetFileName: 'alamat.json' },
  { key: 'telefon', title: 'Telefon', translation: 'Telephone', category: 'Emergency', assetFileName: 'telefon.json' },
  { key: 'jangan', title: 'Jangan', translation: "Don't", category: 'Emergency', assetFileName: 'jangan.json' },
  { key: 'takut', title: 'Takut', translation: 'Scared / Fear', category: 'Emergency', assetFileName: 'takut.json' },
  { key: 'berlari', title: 'Berlari', translation: 'Run', category: 'Emergency', assetFileName: 'berlari.json' },

  // Healthcare & Doctor
  { key: 'doktor', title: 'Doktor', translation: 'Doctor', category: 'Healthcare', assetFileName: 'doktor.json' },
  { key: 'hospital', title: 'Hospital', translation: 'Hospital', category: 'Healthcare', assetFileName: 'hospital.json' },
  { key: 'klinik', title: 'Klinik', translation: 'Clinic', category: 'Healthcare', assetFileName: 'klinik.json' },
  { key: 'anak', title: 'Anak', translation: 'Child', category: 'Family', assetFileName: 'anak.json' },
  { key: 'sakit', title: 'Sakit', translation: 'Sick / Pain', category: 'Healthcare', assetFileName: 'sakit.json' },
  { key: 'sakit_perut', title: 'Sakit Perut', translation: 'Stomach Ache', category: 'Healthcare', assetFileName: 'sakit_perut.json' },
  { key: 'sakit_kepala', title: 'Sakit Kepala', translation: 'Headache', category: 'Healthcare', assetFileName: 'sakit_kepala.json' },
  { key: 'demam', title: 'Demam', translation: 'Fever', category: 'Healthcare', assetFileName: 'demam.json' },
  { key: 'batuk', title: 'Batuk', translation: 'Cough', category: 'Healthcare', assetFileName: 'batuk.json' },
  { key: 'sihat', title: 'Sihat', translation: 'Healthy', category: 'Healthcare', assetFileName: 'sihat.json' },
  { key: 'ubat', title: 'Ubat', translation: 'Medicine', category: 'Healthcare', assetFileName: 'ubat.json' },
  { key: 'darah', title: 'Darah', translation: 'Blood', category: 'Healthcare', assetFileName: 'darah.json' },
  { key: 'suhu', title: 'Suhu', translation: 'Temperature', category: 'Healthcare', assetFileName: 'suhu.json' },
  { key: 'panas', title: 'Panas', translation: 'Hot', category: 'Healthcare', assetFileName: 'panas.json' },
  { key: 'sejuk', title: 'Sejuk', translation: 'Cold', category: 'Healthcare', assetFileName: 'sejuk.json' },
  { key: 'rehat', title: 'Rehat', translation: 'Rest', category: 'Healthcare', assetFileName: 'rehat.json' },
  { key: 'tidur', title: 'Tidur', translation: 'Sleep', category: 'Healthcare', assetFileName: 'tidur.json' },
  { key: 'badan', title: 'Badan', translation: 'Body', category: 'Healthcare', assetFileName: 'badan.json' },
  { key: 'dada', title: 'Dada', translation: 'Chest', category: 'Healthcare', assetFileName: 'dada.json' },
  { key: 'kaki', title: 'Kaki', translation: 'Leg / Foot', category: 'Healthcare', assetFileName: 'kaki.json' },
  { key: 'tangan', title: 'Tangan', translation: 'Hand', category: 'Healthcare', assetFileName: 'tangan.json' },
  { key: 'mata', title: 'Mata', translation: 'Eye', category: 'Healthcare', assetFileName: 'mata.json' },
  { key: 'telinga', title: 'Telinga', translation: 'Ear', category: 'Healthcare', assetFileName: 'telinga.json' },
  { key: 'mulut', title: 'Mulut', translation: 'Mouth', category: 'Healthcare', assetFileName: 'mulut.json' },
  { key: 'bayi', title: 'Bayi', translation: 'Baby', category: 'Family', assetFileName: 'bayi.json' },

  // Questions, Time & Core Conversation
  { key: 'apa', title: 'Apa', translation: 'What', category: 'Questions', assetFileName: 'apa.json' },
  { key: 'bila', title: 'Bila', translation: 'When', category: 'Questions', assetFileName: 'bila.json' },
  { key: 'mana', title: 'Mana', translation: 'Where', category: 'Questions', assetFileName: 'mana.json' },
  { key: 'siapa', title: 'Siapa', translation: 'Who', category: 'Questions', assetFileName: 'siapa.json' },
  { key: 'kenapa', title: 'Kenapa', translation: 'Why', category: 'Questions', assetFileName: 'kenapa.json' },
  { key: 'bagaimana', title: 'Bagaimana', translation: 'How', category: 'Questions', assetFileName: 'bagaimana.json' },
  { key: 'pagi', title: 'Pagi', translation: 'Morning', category: 'Time', assetFileName: 'pagi.json' },
  { key: 'petang', title: 'Petang', translation: 'Afternoon', category: 'Time', assetFileName: 'petang.json' },
  { key: 'malam', title: 'Malam', translation: 'Night', category: 'Time', assetFileName: 'malam.json' },
  { key: 'hari_ini', title: 'Hari Ini', translation: 'Today', category: 'Time', assetFileName: 'hari_ini.json' },
  { key: 'esok', title: 'Esok', translation: 'Tomorrow', category: 'Time', assetFileName: 'esok.json' },
  { key: 'sekarang', title: 'Sekarang', translation: 'Now', category: 'Time', assetFileName: 'sekarang.json' },
  { key: 'encik', title: 'Encik', translation: 'Sir / Mister', category: 'Greetings', assetFileName: 'encik.json' },
  { key: 'boleh', title: 'Boleh', translation: 'Can / Able', category: 'Daily Conversation', assetFileName: 'boleh.json' },
  { key: 'lapar', title: 'Lapar', translation: 'Hungry', category: 'Daily Conversation', assetFileName: 'lapar.json' },
  { key: 'dahaga', title: 'Dahaga', translation: 'Thirsty', category: 'Daily Conversation', assetFileName: 'dahaga.json' },
  { key: 'tahu', title: 'Tahu', translation: 'Know', category: 'Daily Conversation', assetFileName: 'tahu.json' },
  { key: 'tidak', title: 'Tidak / Tak', translation: 'No / Not', category: 'Daily Conversation', assetFileName: 'tidak.json' },
  { key: 'ada', title: 'Ada', translation: 'Have / Exist', category: 'Daily Conversation', assetFileName: 'ada.json' },
  { key: 'dan', title: 'Dan', translation: 'And', category: 'Daily Conversation', assetFileName: 'dan.json' },
  { key: 'dia', title: 'Dia', translation: 'He / She', category: 'Daily Conversation', assetFileName: 'dia.json' },
  { key: 'kami', title: 'Kami', translation: 'We / Us', category: 'Daily Conversation', assetFileName: 'kami.json' },
  { key: 'mereka', title: 'Mereka', translation: 'They / Them', category: 'Daily Conversation', assetFileName: 'mereka.json' },
  { key: 'rumah', title: 'Rumah', translation: 'Home / House', category: 'Places', assetFileName: 'rumah.json' },
  { key: 'benda', title: 'Benda', translation: 'Things / Items', category: 'Daily Conversation', assetFileName: 'benda.json' },
  { key: 'saya', title: 'Saya', translation: 'I / Me', category: 'Daily Conversation', assetFileName: 'saya.json' },
  { key: 'awak', title: 'Awak', translation: 'You', category: 'Daily Conversation', assetFileName: 'awak.json' },
  { key: 'datang', title: 'Datang', translation: 'Come / Arrive', category: 'Daily Conversation', assetFileName: 'datang.json' },
  { key: 'yang', title: 'Yang', translation: 'Which / That', category: 'Daily Conversation', assetFileName: 'yang.json' },

  // Synthesized Dual-Version Continuous Sentences
  { key: 'sentence_1_bim_encik_saya_boleh_tolong_apa', title: 'BIM: Encik, Saya Boleh Tolong Apa?', translation: 'Sir, what can I help you with? (BIM)', category: 'Sentences', assetFileName: 'sentence_1_bim_encik_saya_boleh_tolong_apa.json' },
  { key: 'sentence_2_bim_apa_khabar_hari_ini_awak_datang_hospital_kenapa', title: 'BIM: Apa-Khabar, Hari-Ini Awak Datang Hospital Kenapa?', translation: 'Hello, why did you come to the hospital today? (BIM)', category: 'Sentences', assetFileName: 'sentence_2_bim_apa_khabar_hari_ini_awak_datang_hospital_kenapa.json' },
  { key: 'sentence_1_ktbm_encik_apa_yang_saya_boleh_tolong', title: 'KTBM: Encik Apa Yang Saya Boleh Tolong', translation: 'Sir, what can I help you with? (KTBM)', category: 'Sentences', assetFileName: 'sentence_1_ktbm_encik_apa_yang_saya_boleh_tolong.json' },
  { key: 'sentence_2_ktbm_apa_khabar_kenapa_datang_hospital_hari_ini', title: 'KTBM: Apa Khabar Kenapa Datang Hospital Hari Ini', translation: 'Hello, why did you come to the hospital today? (KTBM)', category: 'Sentences', assetFileName: 'sentence_2_ktbm_apa_khabar_kenapa_datang_hospital_hari_ini.json' },
]

/**
 * Semantic and synonym aliases mapping common BIM tokens to available 3D motion assets.
 */
export const ALIASES: Record<string, string> = {
  tak: 'tidak',
  tak_tahu: 'tahu',
  apa_apa: 'apa',
  dr: 'doktor',
  panas: 'demam',
  budak: 'anak',
  saudara: 'keluarga',
  bantu: 'tolong',
  bantuan: 'tolong',
  lari: 'berlari',
  'apa-khabar': 'apa_khabar',
  'hari-ini': 'hari_ini',
  'sakit-perut': 'sakit_perut',
  'sakit-kepala': 'sakit_kepala',
}

/** Frames inserted between two words so the hands travel rather than jump (8 frames = 0.14s fast, natural transition). */
const BRIDGE_FRAMES = 8
const SYNTHESIS_FPS = 55

const cache = new Map<string, BimMotion>()

function assetUrl(fileName: string): string {
  return `${import.meta.env.BASE_URL}motions/${fileName}`
}

/** Preload essential demo clips into memory for instant playback with 0ms latency. */
export async function preloadCommonMotions(): Promise<void> {
  const common = [
    'awak',
    'tolong',
    'saya',
    'apa',
    'anak',
    'sakit',
    'demam',
    'sihat',
    'polis',
    'rumah',
    'curi',
    'hilang',
    'benda',
    'tahu',
    'tidak',
    'doktor',
    'datang',
    'hospital',
    'hari_ini',
    'apa_khabar',
    'yang',
  ]
  await Promise.allSettled(common.map((k) => loadMotion(k)))
}

// Immediately trigger preloading
void preloadCommonMotions()

/** Loads a clip by vocabulary key, resolving aliases if necessary. */
export async function loadMotion(key: string): Promise<BimMotion | null> {
  const cleanKey = key.trim().toLowerCase().replace(/[.,?!;]/g, '').replace(/[-\s]+/g, '_')
  const resolvedKey = ALIASES[cleanKey] ?? cleanKey
  const cached = cache.get(resolvedKey)
  if (cached) return cached

  const item = CATALOG.find((v) => v.key.toLowerCase() === resolvedKey)
  const fileName = item?.assetFileName ?? (resolvedKey.endsWith('.json') ? resolvedKey : `${resolvedKey}.json`)

  try {
    const response = await fetch(assetUrl(fileName))
    if (!response.ok) return null
    const motion = parseMotion(await response.json())
    cache.set(resolvedKey, motion)
    return motion
  } catch (e) {
    console.warn(`[avatar] could not load motion "${key}" -> "${resolvedKey}" (${fileName})`, e)
    return null
  }
}

/** Vocabulary matching a query in title, English gloss or key. */
export function searchVocabulary(query: string): VocabularyItem[] {
  if (!query.trim()) return CATALOG
  const q = query.trim().toLowerCase()
  return CATALOG.filter(
    (v) =>
      v.title.toLowerCase().includes(q) ||
      v.translation.toLowerCase().includes(q) ||
      v.key.toLowerCase().includes(q),
  )
}

/**
 * Joins several words into one continuous clip.
 *
 * Signs are not concatenated end to end: a 16-frame co-articulation bridge is
 * interpolated between each pair, because real signing moves continuously
 * between shapes and a hard cut reads as two separate signs rather than a
 * sentence.
 */
export async function synthesizeSentence(words: string[]): Promise<BimMotion | null> {
  const normalizedWords = words.map((w) =>
    w.trim().toLowerCase().replace(/[.,?!;]/g, '').replace(/[-\s]+/g, '_'),
  )

  // 1. Direct precompiled whole sentence match check
  const joinedKey = normalizedWords.join('_')
  const sentenceMap: Record<string, string> = {
    encik_saya_boleh_tolong_apa: 'sentence_1_bim_encik_saya_boleh_tolong_apa.json',
    apa_khabar_hari_ini_awak_datang_hospital_kenapa:
      'sentence_2_bim_apa_khabar_hari_ini_awak_datang_hospital_kenapa.json',
    encik_apa_yang_saya_boleh_tolong: 'sentence_1_ktbm_encik_apa_yang_saya_boleh_tolong.json',
    apa_khabar_kenapa_datang_hospital_hari_ini:
      'sentence_2_ktbm_apa_khabar_kenapa_datang_hospital_hari_ini.json',
  }
  const precompiled = sentenceMap[joinedKey]
  if (precompiled) {
    const motion = await loadMotion(precompiled)
    if (motion) return motion
  }

  // 2. Synthesize individual motions
  const loaded = await Promise.all(normalizedWords.map((w) => loadMotion(w)))
  const motions = loaded.filter((m): m is BimMotion => m !== null)

  if (motions.length === 0) return null
  if (motions.length === 1) return motions[0]

  const frames: BimFrame[] = []
  let index = 0

  motions.forEach((motion, i) => {
    for (const f of motion.frames) {
      frames.push({ ...f, frame: index, time: index / SYNTHESIS_FPS })
      index++
    }

    const next = motions[i + 1]
    if (!next) return

    const last = motion.frames[motion.frames.length - 1]
    const first = next.frames[0]
    if (!last || !first) return

    for (let b = 1; b <= BRIDGE_FRAMES; b++) {
      const alpha = b / (BRIDGE_FRAMES + 1)
      frames.push({
        frame: index,
        time: index / SYNTHESIS_FPS,
        pose: {
          ...last.pose,
          nose:
            last.pose.nose && first.pose.nose
              ? lerp(last.pose.nose, first.pose.nose, alpha)
              : last.pose.nose,
        },
        leftHand: bridgeHand(last.leftHand, first.leftHand, alpha),
        rightHand: bridgeHand(last.rightHand, first.rightHand, alpha),
      })
      index++
    }
  })

  const title = words
    .map((w) => w.replace(/_/g, ' '))
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ')

  return {
    word: title,
    fps: SYNTHESIS_FPS,
    numFrames: frames.length,
    duration: index / SYNTHESIS_FPS,
    frames,
  }
}

function bridgeHand(
  h0: HandJoints | null,
  h1: HandJoints | null,
  alpha: number,
): HandJoints | null {
  if (!h0 || !h1) return h0 ?? h1
  if (h0.points.length !== 21 || h1.points.length !== 21) return h0
  return {
    active: h0.active || h1.active,
    points: h0.points.map((p, i) => lerp(p, h1.points[i], alpha)),
  }
}

/**
 * Resolves free text to a clip: exact catalog hit, else multi-word synthesis,
 * else the best partial match. Mirrors triggerPlay in AvatarPlayerScreen.kt.
 */
export async function resolveQuery(
  query: string,
): Promise<{ key: string; motion: BimMotion } | null> {
  const raw = query.trim()
  if (!raw) return null
  const clean = raw.toLowerCase().replace(/\s+/g, '_')

  const direct = CATALOG.find(
    (v) => v.key.toLowerCase() === clean || v.title.toLowerCase() === raw.toLowerCase(),
  )
  if (direct) {
    const motion = await loadMotion(direct.key)
    if (motion) return { key: direct.key, motion }
  }

  const parts = raw.toLowerCase().split(/\s+/)
  if (parts.length > 1) {
    const synthesized = await synthesizeSentence(parts)
    if (synthesized) return { key: clean, motion: synthesized }
  }

  const fallback = searchVocabulary(raw)[0]
  if (fallback) {
    const motion = await loadMotion(fallback.key)
    if (motion) return { key: fallback.key, motion }
  }

  return null
}
