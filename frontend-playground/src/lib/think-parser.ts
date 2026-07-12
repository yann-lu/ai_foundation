export interface ParsedContent {
  reasoning: string
  answer: string
}

export function parseThink(raw: string): ParsedContent {
  if (!raw) return { reasoning: '', answer: '' }
  let reasoning = ''
  let answer = raw
  answer = answer.replace(/<think>([\s\S]*?)<\/think>/gi, (_m, block: string) => {
    reasoning += `${block.trim()}\n\n`
    return ''
  })
  answer = answer.replace(/<think>([\s\S]*)$/i, (_m, block: string) => {
    reasoning += block.trim()
    return ''
  })
  return { reasoning: reasoning.trim(), answer: answer.trimStart() }
}
