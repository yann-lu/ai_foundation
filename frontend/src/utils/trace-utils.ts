import {
  Operation, Document, User, Cpu, Avatar, Tools,
  CircleCheck, CircleClose, Warning, VideoPlay, List,
  ChatLineRound, Close,
} from '@element-plus/icons-vue'
import type {
  FlowEvent,
  RunEventLog,
  TimelineStep,
  TraceEntry,
  TraceEntryType,
} from '@/types/playground'

export function buildFlowEvents(events: RunEventLog[]): FlowEvent[] {
  const flow: FlowEvent[] = []
  let reasoningBuf = ''
  let tokenBuf = ''
  let reasoningId = 0
  let assistantId = 0

  const flushReasoning = (ts: number) => {
    if (reasoningBuf.trim()) {
      flow.push({
        id: `reasoning_${reasoningId++}`,
        type: 'reasoning',
        label: '思考',
        content: reasoningBuf.trim(),
        timestamp: ts,
      })
    }
    reasoningBuf = ''
  }

  const flushAssistant = (ts: number) => {
    if (tokenBuf.trim()) {
      flow.push({
        id: `assistant_${assistantId++}`,
        type: 'assistant',
        label: '回复',
        content: tokenBuf.trim(),
        timestamp: ts,
      })
    }
    tokenBuf = ''
  }

  for (const ev of events) {
    switch (ev.eventType) {
      case 'user_message':
        flushReasoning(ev.timestamp)
        flushAssistant(ev.timestamp)
        flow.push({
          id: `user_${ev.timestamp}`,
          type: 'user',
          label: '用户',
          content: String(ev.data ?? ''),
          timestamp: ev.timestamp,
        })
        break

      case 'chat_reasoning':
        if (tokenBuf) flushAssistant(ev.timestamp)
        reasoningBuf += String(ev.data ?? '')
        break

      case 'chat_token':
        if (reasoningBuf) flushReasoning(ev.timestamp)
        tokenBuf += String(ev.data ?? '')
        break

      case 'tool_call': {
        flushReasoning(ev.timestamp)
        flushAssistant(ev.timestamp)
        const callData = ev.data as Record<string, unknown> | null
        flow.push({
          id: `tool_call_${ev.timestamp}`,
          type: 'tool_call',
          label: '调用工具',
          content: (callData?.name as string) || 'unknown',
          timestamp: ev.timestamp,
          toolName: callData?.name as string,
          toolPayload: (callData?.arguments as Record<string, unknown>) || {},
        })
        break
      }

      case 'tool_result': {
        const resultData = ev.data as Record<string, unknown> | null
        const result = typeof resultData?.result === 'string'
          ? resultData.result
          : JSON.stringify(ev.data)
        flow.push({
          id: `tool_result_${ev.timestamp}`,
          type: 'tool_result',
          label: '工具结果',
          content: result,
          timestamp: ev.timestamp,
          toolName: resultData?.toolName as string,
          toolResult: result,
        })
        break
      }

      case 'run_error':
        flushReasoning(ev.timestamp)
        flushAssistant(ev.timestamp)
        flow.push({
          id: `error_${ev.timestamp}`,
          type: 'error',
          label: '错误',
          content: String(ev.data ?? '执行失败'),
          timestamp: ev.timestamp,
        })
        break

      case 'run_complete':
        flushReasoning(ev.timestamp)
        flushAssistant(ev.timestamp)
        break
    }
  }

  const lastTs = events.length > 0 ? events[events.length - 1].timestamp : Date.now()
  flushReasoning(lastTs)
  flushAssistant(lastTs)

  return flow
}

export function buildTraceEntries(events: RunEventLog[]): TraceEntry[] {
  const entries: TraceEntry[] = []
  if (events.length === 0) return entries

  const reqEv = events.find(e => e.eventType === 'request_messages')
  if (reqEv && Array.isArray(reqEv.data)) {
    const msgs = reqEv.data as Array<{ role: string; content: string }>
    let sysIdx = 0
    let ctxIdx = 0
    let foundUser = false
    for (const msg of msgs) {
      if (msg.role === 'system') {
        entries.push({
          id: `system_${sysIdx++}`,
          type: 'system',
          label: 'Initial System Prompt',
          content: msg.content,
          timestamp: reqEv.timestamp,
        })
      } else if (msg.role === 'user' && sysIdx > 0 && !foundUser) {
        entries.push({
          id: `context_${ctxIdx++}`,
          type: 'context',
          label: 'Current runtime context',
          content: msg.content,
          timestamp: reqEv.timestamp,
        })
      } else if (msg.role === 'user') {
        foundUser = true
      }
    }
  }

  let reasoningBuf = ''
  let reasoningStart = 0
  let tokenBuf = ''
  let tokenStart = 0
  let toolCallIdx = 0

  const flushReasoning = (ts: number) => {
    if (reasoningBuf.trim()) {
      entries.push({
        id: `reasoning_${reasoningStart}`,
        type: 'reasoning',
        label: 'AI Thinking',
        content: reasoningBuf.trim(),
        timestamp: reasoningStart,
        endTimestamp: ts,
        durationMs: ts - reasoningStart,
      })
    }
    reasoningBuf = ''
  }

  const flushToken = (ts: number) => {
    if (tokenBuf.trim()) {
      entries.push({
        id: `assistant_${tokenStart}`,
        type: 'assistant',
        label: 'Assistant Response',
        content: tokenBuf.trim(),
        timestamp: tokenStart,
        endTimestamp: ts,
        durationMs: ts - tokenStart,
      })
    }
    tokenBuf = ''
  }

  for (const ev of events) {
    switch (ev.eventType) {
      case 'user_message':
        flushReasoning(ev.timestamp)
        flushToken(ev.timestamp)
        entries.push({
          id: `user_${ev.timestamp}`,
          type: 'user',
          label: 'User',
          content: String(ev.data ?? ''),
          timestamp: ev.timestamp,
        })
        break

      case 'chat_reasoning':
        if (tokenBuf) flushToken(ev.timestamp)
        if (!reasoningStart) reasoningStart = ev.timestamp
        reasoningBuf += String(ev.data ?? '')
        break

      case 'chat_token':
        if (reasoningBuf) flushReasoning(ev.timestamp)
        if (!tokenStart) tokenStart = ev.timestamp
        tokenBuf += String(ev.data ?? '')
        break

      case 'tool_call': {
        flushReasoning(ev.timestamp)
        flushToken(ev.timestamp)
        const tc = ev.data as Record<string, unknown> | null
        const toolName = tc && typeof tc.name === 'string' ? tc.name : 'unknown_tool'
        const args = tc && tc.arguments != null
          ? (typeof tc.arguments === 'string' ? tc.arguments : JSON.stringify(tc.arguments, null, 2))
          : ''
        entries.push({
          id: `tool_call_${toolCallIdx}_${ev.timestamp}`,
          type: 'tool_call',
          label: toolName,
          content: args,
          toolName,
          toolArgs: args,
          timestamp: ev.timestamp,
        })
        toolCallIdx++
        break
      }

      case 'tool_result': {
        const tr = ev.data as Record<string, unknown> | null
        let result = ''
        if (tr && tr.result != null && String(tr.result).trim() !== '') {
          result = String(tr.result)
        } else if (tr && typeof tr === 'object') {
          result = JSON.stringify(tr, null, 2)
        } else {
          result = String(ev.data ?? '')
        }
        let matched = false
        for (let i = entries.length - 1; i >= 0; i--) {
          const entry = entries[i]
          if (entry.type === 'tool_call' && !entry.toolResultTimestamp) {
            entry.toolResult = result
            entry.toolResultTimestamp = ev.timestamp
            entry.endTimestamp = ev.timestamp
            entry.durationMs = ev.timestamp - entry.timestamp
            matched = true
            break
          }
        }
        if (!matched) {
          entries.push({
            id: `tool_result_${ev.timestamp}`,
            type: 'tool_result',
            label: 'Tool Result',
            content: result,
            toolResult: result,
            timestamp: ev.timestamp,
          })
        }
        break
      }

      case 'summary_update':
        flushReasoning(ev.timestamp)
        flushToken(ev.timestamp)
        entries.push({
          id: `summary_${ev.timestamp}`,
          type: 'summary',
          label: 'Summary Update',
          content: String(ev.data ?? ''),
          timestamp: ev.timestamp,
        })
        break

      case 'run_error':
        flushReasoning(ev.timestamp)
        flushToken(ev.timestamp)
        entries.push({
          id: `error_${ev.timestamp}`,
          type: 'error',
          label: 'Run Error',
          content: String(ev.data ?? '执行失败'),
          timestamp: ev.timestamp,
        })
        break

      case 'run_cancelled':
        flushReasoning(ev.timestamp)
        flushToken(ev.timestamp)
        entries.push({
          id: `cancelled_${ev.timestamp}`,
          type: 'cancelled',
          label: 'Cancelled',
          content: '',
          timestamp: ev.timestamp,
        })
        break

      case 'run_complete':
        flushReasoning(ev.timestamp)
        flushToken(ev.timestamp)
        break
    }
  }

  const lastTs = events.length > 0 ? events[events.length - 1].timestamp : Date.now()
  flushReasoning(lastTs)
  flushToken(lastTs)

  return entries
}

export function getTraceIcon(type: TraceEntryType) {
  const map: Record<TraceEntryType, unknown> = {
    system: Operation,
    context: Document,
    user: User,
    reasoning: Cpu,
    assistant: Avatar,
    tool_call: Tools,
    tool_result: CircleCheck,
    summary: Document,
    complete: CircleCheck,
    error: CircleClose,
    cancelled: Warning,
  }
  return map[type] || Operation
}

export function getTraceBadgeLabel(type: TraceEntryType): string {
  const map: Record<TraceEntryType, string> = {
    system: 'SYSTEM',
    context: 'CONTEXT',
    user: 'USER',
    reasoning: 'THINKING',
    assistant: 'ASSISTANT',
    tool_call: 'TOOL',
    tool_result: 'RESULT',
    summary: 'SUMMARY',
    complete: 'COMPLETE',
    error: 'ERROR',
    cancelled: 'CANCELLED',
  }
  return map[type] || String(type).toUpperCase()
}

export function truncateArgs(args: string): string {
  if (!args) return '{}'
  try {
    const parsed = JSON.parse(args)
    const keys = Object.keys(parsed)
    if (keys.length === 0) return '{}'
    const firstKey = keys[0]
    const firstVal = String(parsed[firstKey])
    const truncated = firstVal.length > 40 ? firstVal.slice(0, 40) + '…' : firstVal
    if (keys.length > 1) {
      return '{ "' + firstKey + '": ' + truncated + ', ... (' + keys.length + ' keys) }'
    }
    return '{ "' + firstKey + '": ' + truncated + ' }'
  } catch {
    return args.slice(0, 80) + (args.length > 80 ? '…' : '')
  }
}

export function isLongContent(entry: TraceEntry): boolean {
  return entry.content.length > 200
}

export function buildTimeline(events: RunEventLog[]): TimelineStep[] {
  const steps: TimelineStep[] = []

  const startEvent = events.find(e => e.eventType === 'run_start')
  if (startEvent) {
    steps.push({
      id: 'run_start',
      label: 'Run 开始',
      icon: VideoPlay,
      color: 'var(--c-primary)',
      startTime: startEvent.timestamp,
      endTime: startEvent.timestamp,
      content: null,
    })
  }

  const requestMsgEvent = events.find(e => e.eventType === 'request_messages')
  if (requestMsgEvent) {
    const count = Array.isArray(requestMsgEvent.data) ? requestMsgEvent.data.length : 0
    steps.push({
      id: 'request_messages',
      label: `请求消息 (${count})`,
      icon: List,
      color: 'var(--c-accent)',
      startTime: requestMsgEvent.timestamp,
      endTime: requestMsgEvent.timestamp,
      content: null,
      count,
    })
  }

  const chatStart = events.find(e => e.eventType === 'chat_start')
  if (chatStart) {
    steps.push({
      id: 'chat_start',
      label: 'Chat 开始',
      icon: Avatar,
      color: 'var(--c-accent)',
      startTime: chatStart.timestamp,
      endTime: chatStart.timestamp,
      content: null,
    })
  }

  const reasoningEvents = events.filter(e => e.eventType === 'chat_reasoning')
  if (reasoningEvents.length > 0) {
    const content = reasoningEvents.map(e => String(e.data ?? '')).join('')
    steps.push({
      id: 'reasoning',
      label: 'AI 思考',
      icon: Cpu,
      color: '#8b5cf6',
      startTime: reasoningEvents[0].timestamp,
      endTime: reasoningEvents[reasoningEvents.length - 1].timestamp,
      content,
      count: reasoningEvents.length,
    })
  }

  const tokenEvents = events.filter(e => e.eventType === 'chat_token')
  if (tokenEvents.length > 0) {
    const content = tokenEvents.map(e => String(e.data ?? '')).join('')
    steps.push({
      id: 'response',
      label: 'AI 回复',
      icon: ChatLineRound,
      color: 'var(--c-success)',
      startTime: tokenEvents[0].timestamp,
      endTime: tokenEvents[tokenEvents.length - 1].timestamp,
      content,
      count: tokenEvents.length,
    })
  }

  const toolEvents = events.filter(e => e.eventType === 'tool_call' || e.eventType === 'tool_result')
  for (const te of toolEvents) {
    const isCall = te.eventType === 'tool_call'
    steps.push({
      id: `tool_${te.timestamp}`,
      label: isCall ? '工具调用' : '工具结果',
      icon: Tools,
      color: 'var(--c-warning)',
      startTime: te.timestamp,
      endTime: te.timestamp,
      content: te.data != null ? JSON.stringify(te.data, null, 2) : null,
    })
  }

  const summaryEvents = events.filter(e => e.eventType === 'summary_update')
  if (summaryEvents.length > 0) {
    steps.push({
      id: 'summary',
      label: '对话摘要更新',
      icon: Document,
      color: 'var(--c-accent)',
      startTime: summaryEvents[0].timestamp,
      endTime: summaryEvents[summaryEvents.length - 1].timestamp,
      content: summaryEvents.map(e => String(e.data ?? '')).join('\n'),
    })
  }

  const chatComplete = events.find(e => e.eventType === 'chat_complete')
  if (chatComplete) {
    steps.push({
      id: 'chat_complete',
      label: '对话完成',
      icon: CircleCheck,
      color: 'var(--c-success)',
      startTime: chatComplete.timestamp,
      endTime: chatComplete.timestamp,
      content: null,
    })
  }

  const runComplete = events.find(e => e.eventType === 'run_complete')
  if (runComplete) {
    steps.push({
      id: 'run_complete',
      label: 'Run 完成',
      icon: CircleCheck,
      color: 'var(--c-success)',
      startTime: runComplete.timestamp,
      endTime: runComplete.timestamp,
      content: typeof runComplete.data === 'string' ? runComplete.data : null,
    })
  }

  const errorEvent = events.find(e => e.eventType === 'run_error')
  if (errorEvent) {
    steps.push({
      id: 'error',
      label: 'Run 错误',
      icon: Close,
      color: 'var(--c-error)',
      startTime: errorEvent.timestamp,
      endTime: errorEvent.timestamp,
      content: String(errorEvent.data ?? '执行失败'),
    })
  }

  const cancelEvent = events.find(e => e.eventType === 'run_cancelled')
  if (cancelEvent) {
    steps.push({
      id: 'cancelled',
      label: 'Run 已取消',
      icon: CircleClose,
      color: 'var(--c-warning)',
      startTime: cancelEvent.timestamp,
      endTime: cancelEvent.timestamp,
      content: null,
    })
  }

  return steps
}
