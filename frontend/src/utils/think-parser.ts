export interface ParsedContent {
  reasoning: string
  answer: string
}

export function parseThink(raw: string): ParsedContent {
  if (!raw) return { reasoning: '', answer: '' }

  const openTag = '<think>'
  const closeTag = '</think>'

  const openIdx = raw.indexOf(openTag)
  if (openIdx === -1) {
    return { reasoning: '', answer: raw }
  }

  const closeIdx = raw.indexOf(closeTag, openIdx + openTag.length)
  if (closeIdx === -1) {
    const reasoning = raw.slice(openIdx + openTag.length)
    return { reasoning, answer: '' }
  }

  const reasoning = raw.slice(openIdx + openTag.length, closeIdx)
  const answer = raw.slice(closeIdx + closeTag.length)

  return { reasoning: reasoning.trim(), answer: answer.trim() }
}
