import { marked } from 'marked'
import DOMPurify from 'dompurify'

marked.setOptions({
  breaks: true,
  gfm: true
})

interface RenderMarkdownOptions {
  streaming?: boolean
}

export function renderMarkdown(content: string, options: RenderMarkdownOptions = {}): string {
  if (!content) return ''
  let processed = normalizeMarkdown(content)
  if (options.streaming) {
    processed = hideDanglingMarkdownMarkers(processed)
  }
  if (processed.includes('<think>')) {
    processed = processed
      .replace(/<think>([\s\S]*?)<\/think>/g, (_m, p1) => `<blockquote class="think-block">${p1.trim()}</blockquote>`)
      .replace(/<think>([\s\S]*)$/g, (_m, p1) => `<blockquote class="think-block">${p1.trim()}</blockquote>`)
  }
  const html = marked.parse(processed, { async: false }) as string
  return DOMPurify.sanitize(html, { ADD_TAGS: ['think'], ADD_ATTR: ['class'] })
}

function normalizeMarkdown(content: string): string {
  return content
    .replace(/^(#{1,6})(\d+\.)/gm, '$1 $2')
    .replace(/^(#{1,6})([^\s#\d\n])/gm, '$1 $2')
}

function hideDanglingMarkdownMarkers(content: string): string {
  let processed = content
  processed = removeLastIfOdd(processed, '**')
  processed = removeLastIfOdd(processed, '__')
  processed = removeLastIfOdd(processed, '`')
  return processed
}

function removeLastIfOdd(content: string, marker: string): string {
  const indexes: number[] = []
  let cursor = content.indexOf(marker)
  while (cursor !== -1) {
    indexes.push(cursor)
    cursor = content.indexOf(marker, cursor + marker.length)
  }
  if (indexes.length % 2 === 0) {
    return content
  }
  const last = indexes[indexes.length - 1]
  return content.slice(0, last) + content.slice(last + marker.length)
}
