import { useCallback, useRef, useState } from 'react'
import {
  AssistantRuntimeProvider,
  useExternalStoreRuntime,
  type AppendMessage,
  type ThreadMessageLike,
  generateId,
} from '@assistant-ui/react'
import { createRun, streamRunEvents, cancelRun, getMessages } from '@/api/chat'
import { parseThink } from '@/lib/think-parser'
import type { MessageDTO, RunStreamEnvelope } from '@/types/api'

export interface RunEventLog {
  eventType: RunStreamEnvelope['eventType']
  taskState: string | null
  dataPreview: string
  data: unknown
  timestamp: number
}

export interface RunMeta {
  runCode: string
  startedAt: number
  finishedAt?: number
}

function previewData(data: unknown): string {
  if (data == null || data === '') return ''
  const text = typeof data === 'string' ? data : JSON.stringify(data)
  return text.length > 140 ? `${text.slice(0, 140)}…` : text
}

type ContentParts = Exclude<ThreadMessageLike['content'], string>

function toContentParts(reasoning: string, answer: string): ContentParts {
  return [
    ...(reasoning ? [{ type: 'reasoning' as const, text: reasoning }] : []),
    { type: 'text' as const, text: answer },
  ]
}

function buildHistoryMessage(m: MessageDTO): ThreadMessageLike {
  if (m.role === 'assistant') {
    const { reasoning, answer } = parseThink(m.content)
    return {
      id: String(m.id),
      role: 'assistant',
      status: { type: 'complete' as const, reason: 'stop' as const },
      content: toContentParts(reasoning, answer),
    }
  }
  return {
    id: String(m.id),
    role: 'user',
    content: [{ type: 'text' as const, text: m.content }],
  }
}

function updateAssistant(
  messages: readonly ThreadMessageLike[],
  id: string,
  next: ThreadMessageLike,
): readonly ThreadMessageLike[] {
  return messages.map((m) => (m.id === id ? next : m))
}

export interface UseAiRuntimeResult {
  AssistantRuntimeProvider: typeof AssistantRuntimeProvider
  runtime: ReturnType<typeof useExternalStoreRuntime<ThreadMessageLike>>
  messages: readonly ThreadMessageLike[]
  isRunning: boolean
  runEvents: RunEventLog[]
  runMeta: RunMeta | null
  systemPrompt: string
  conversationCode: string
  setSystemPrompt: (v: string) => void
  setConversationCode: (v: string) => void
  resetConversation: () => void
  loadHistory: (messages: MessageDTO[]) => void
  clearEvents: () => void
}

export function useAiRuntime(): UseAiRuntimeResult {
  const [messages, setMessages] = useState<readonly ThreadMessageLike[]>([])
  const [isRunning, setIsRunning] = useState(false)
  const [runEvents, setRunEvents] = useState<RunEventLog[]>([])
  const [runMeta, setRunMeta] = useState<RunMeta | null>(null)
  const [systemPrompt, setSystemPrompt] = useState('')
  const [conversationCode, setConversationCode] = useState('')
  const eventSourceRef = useRef<EventSource | null>(null)
  const rawRef = useRef('')
  const reasoningRef = useRef('')
  const assistantIdRef = useRef('')
  const runCodeRef = useRef('')

  const appendEvent = useCallback((env: RunStreamEnvelope) => {
    setRunEvents((prev) => [
      ...prev,
      {
        eventType: env.eventType,
        taskState: env.taskState,
        dataPreview: previewData(env.data),
        data: env.data,
        timestamp: env.timestamp || Date.now(),
      },
    ])
  }, [])

  const onNew = useCallback(
    async (message: AppendMessage) => {
      if (message.content.length !== 1 || message.content[0]?.type !== 'text') {
        throw new Error('仅支持文本消息')
      }
      const text = message.content[0].text
      if (!conversationCode) throw new Error('请先创建或选择会话')

      const userMessage: ThreadMessageLike = {
        id: generateId(),
        role: 'user',
        content: [{ type: 'text', text }],
      }
      const assistantId = generateId()
      assistantIdRef.current = assistantId
      const assistantPlaceholder: ThreadMessageLike = {
        id: assistantId,
        role: 'assistant',
        status: { type: 'running' },
        content: [{ type: 'text', text: '' }],
      }

      setMessages((prev) => [...prev, userMessage, assistantPlaceholder])
      setIsRunning(true)
      setRunEvents([])
      rawRef.current = ''
      reasoningRef.current = ''

      const startedAt = Date.now()
      setRunMeta({ runCode: '', startedAt })

      try {
        const createRes = await createRun({
          conversationCode,
          userMessage: text,
          systemPrompt: systemPrompt.trim() || undefined,
        })
        const runCode = createRes.runCode
        runCodeRef.current = runCode
        setRunMeta({ runCode, startedAt })

        await new Promise<void>((resolve, reject) => {
          const source = streamRunEvents(runCode)
          eventSourceRef.current = source

          source.onmessage = (event) => {
            let env: RunStreamEnvelope
            try {
              env = JSON.parse(event.data) as RunStreamEnvelope
            } catch {
              return
            }
           appendEvent(env)

            if (env.eventType === 'chat_reasoning') {
              reasoningRef.current += String(env.data ?? '')
              setMessages((prev) =>
                updateAssistant(prev, assistantId, {
                  id: assistantId,
                  role: 'assistant',
                  status: { type: 'running' },
                  content: toContentParts(reasoningRef.current, rawRef.current),
                }),
              )
           } else if (env.eventType === 'chat_token') {
             rawRef.current += String(env.data ?? '')
              const parsed = parseThink(rawRef.current)
              setMessages((prev) =>
                updateAssistant(prev, assistantId, {
                  id: assistantId,
                  role: 'assistant',
                  status: { type: 'running' },
                  content: toContentParts(reasoningRef.current, parsed.answer),
                }),
              )
            } else if (env.eventType === 'run_complete') {
              const reply = typeof env.data === 'string' ? env.data : rawRef.current
              const parsed = parseThink(reply)
              const reasoning = reasoningRef.current || parsed.reasoning
              setMessages((prev) =>
                updateAssistant(prev, assistantId, {
                  id: assistantId,
                  role: 'assistant',
                  status: { type: 'complete', reason: 'stop' },
                  content: toContentParts(reasoning, parsed.answer),
                }),
              )
              setRunMeta((m) => (m ? { ...m, finishedAt: Date.now() } : m))
              source.close()
              resolve()
            } else if (env.eventType === 'run_error') {
              setMessages((prev) =>
                updateAssistant(prev, assistantId, {
                  id: assistantId,
                  role: 'assistant',
                  status: { type: 'incomplete', reason: 'error' },
                  content: [{ type: 'text', text: String(env.data ?? '执行失败') }],
                }),
              )
              setRunMeta((m) => (m ? { ...m, finishedAt: Date.now() } : m))
              source.close()
              reject(new Error(String(env.data ?? '执行失败')))
            } else if (env.eventType === 'run_cancelled') {
              const parsed = parseThink(rawRef.current || (typeof env.data === 'string' ? env.data : ''))
              setMessages((prev) =>
                updateAssistant(prev, assistantId, {
                  id: assistantId,
                  role: 'assistant',
                  status: { type: 'incomplete', reason: 'cancelled' },
                  content: toContentParts(reasoningRef.current || parsed.reasoning, parsed.answer),
                }),
              )
              setRunMeta((m) => (m ? { ...m, finishedAt: Date.now() } : m))
              source.close()
              resolve()
            }
          }

          source.onerror = () => {
            source.close()
            setMessages((prev) =>
              updateAssistant(prev, assistantId, {
                id: assistantId,
                role: 'assistant',
                status: { type: 'incomplete', reason: 'error' },
                content: toContentParts('', 'SSE 连接中断'),
              }),
            )
            setRunMeta((m) => (m ? { ...m, finishedAt: Date.now() } : m))
            reject(new Error('SSE 连接中断'))
          }
        })
      } catch (e) {
        const errMsg = e instanceof Error ? e.message : '未知错误'
        const id = assistantIdRef.current
        setMessages((prev) =>
          updateAssistant(prev, id, {
            id,
            role: 'assistant',
            status: { type: 'incomplete', reason: 'error' },
            content: [{ type: 'text', text: `请求失败: ${errMsg}` }],
          }),
        )
        setRunMeta((m) => (m ? { ...m, finishedAt: Date.now() } : m))
      } finally {
        setIsRunning(false)
        eventSourceRef.current = null
      }
    },
    [conversationCode, systemPrompt, appendEvent],
  )

  const onCancel = useCallback(async () => {
    const code = runCodeRef.current
    if (!code) return
    try {
      await cancelRun(code)
    } catch {
      /* request layer reports */
    }
  }, [])

  const loadHistory = useCallback((history: MessageDTO[]) => {
    rawRef.current = ''
    reasoningRef.current = ''
    setRunEvents([])
    setRunMeta(null)
    setMessages(history.map(buildHistoryMessage))
  }, [])

  const resetConversation = useCallback(() => {
    setMessages([])
    setRunEvents([])
    setRunMeta(null)
    rawRef.current = ''
    reasoningRef.current = ''
    runCodeRef.current = ''
  }, [])

  const clearEvents = useCallback(() => {
    setRunEvents([])
    setRunMeta(null)
  }, [])

  const runtime = useExternalStoreRuntime<ThreadMessageLike>({
    messages,
    isRunning,
    onNew,
    onCancel,
    convertMessage: (m) => m,
  })

  return {
    AssistantRuntimeProvider,
    runtime,
    messages,
    isRunning,
    runEvents,
    runMeta,
    systemPrompt,
    conversationCode,
    setSystemPrompt,
    setConversationCode,
    resetConversation,
    loadHistory,
    clearEvents,
  }
}

export { getMessages }
