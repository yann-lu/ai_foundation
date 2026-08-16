import { useMemo, useState } from 'react'
import type { RunEventLog, RunMeta } from '@/runtime/aui-runtime'
import { cn } from '@/lib/utils'
import { parseThink } from '@/lib/think-parser'
import {
  Clock, MessageSquare, Wrench, Download, Search, X,
  Server, User, Brain, Bot, FileText, CheckCircle2,
  XCircle, AlertCircle, ChevronDown, ChevronRight,
  Zap, ListTree,
} from 'lucide-react'

interface Props {
  runEvents: RunEventLog[]
  runMeta: RunMeta | null
}

type TraceEntryType =
  | 'system'
  | 'user'
  | 'context'
  | 'reasoning'
  | 'assistant'
  | 'tool_call'
  | 'tool_result'
  | 'summary'
  | 'complete'
  | 'error'
  | 'cancelled'

interface TraceEntry {
  id: string
  type: TraceEntryType
  label: string
  content: string
  timestamp: number
  endTimestamp?: number
  toolName?: string
  toolArgs?: string
  toolResult?: string
  toolResultTimestamp?: number
  indent?: number
  durationMs?: number
}

interface ToolDetail {
  entry: TraceEntry
  turn: number
  step: number
}

function formatTime(t: number): string {
  return new Date(t).toLocaleTimeString('zh-CN', {
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  })
}

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

const typeConfig: Record<TraceEntryType, {
  badge: string
  badgeBg: string
  icon: typeof Zap
  iconBg: string
  iconColor: string
  label: string
}> = {
  system: {
    badge: 'bg-neutral-500/10 text-neutral-400 border-neutral-500/20',
    badgeBg: 'bg-neutral-500/5',
    icon: Server,
    iconBg: 'bg-neutral-500/10',
    iconColor: 'text-neutral-400',
    label: 'SYSTEM',
  },
  user: {
    badge: 'bg-blue-500/10 text-blue-400 border-blue-500/20',
    badgeBg: 'bg-blue-500/5',
    icon: User,
    iconBg: 'bg-blue-500/10',
    iconColor: 'text-blue-400',
    label: 'USER',
  },
  context: {
    badge: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
    badgeBg: 'bg-emerald-500/5',
    icon: FileText,
    iconBg: 'bg-emerald-500/10',
    iconColor: 'text-emerald-400',
    label: 'CONTEXT',
  },
  reasoning: {
    badge: 'bg-purple-500/10 text-purple-400 border-purple-500/20',
    badgeBg: 'bg-purple-500/5',
    icon: Brain,
    iconBg: 'bg-purple-500/10',
    iconColor: 'text-purple-400',
    label: 'THINKING',
  },
  assistant: {
    badge: 'bg-violet-500/10 text-violet-400 border-violet-500/20',
    badgeBg: 'bg-violet-500/5',
    icon: Bot,
    iconBg: 'bg-violet-500/10',
    iconColor: 'text-violet-400',
    label: 'ASSISTANT',
  },
  tool_call: {
    badge: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
    badgeBg: 'bg-amber-500/5',
    icon: Wrench,
    iconBg: 'bg-amber-500/10',
    iconColor: 'text-amber-400',
    label: 'TOOL',
  },
  tool_result: {
    badge: 'bg-orange-500/10 text-orange-400 border-orange-500/20',
    badgeBg: 'bg-orange-500/5',
    icon: CheckCircle2,
    iconBg: 'bg-orange-500/10',
    iconColor: 'text-orange-400',
    label: 'TOOL_RESULT',
  },
  summary: {
    badge: 'bg-cyan-500/10 text-cyan-400 border-cyan-500/20',
    badgeBg: 'bg-cyan-500/5',
    icon: FileText,
    iconBg: 'bg-cyan-500/10',
    iconColor: 'text-cyan-400',
    label: 'SUMMARY',
  },
  complete: {
    badge: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
    badgeBg: 'bg-emerald-500/5',
    icon: CheckCircle2,
    iconBg: 'bg-emerald-500/10',
    iconColor: 'text-emerald-400',
    label: 'COMPLETE',
  },
  error: {
    badge: 'bg-red-500/10 text-red-400 border-red-500/20',
    badgeBg: 'bg-red-500/5',
    icon: XCircle,
    iconBg: 'bg-red-500/10',
    iconColor: 'text-red-400',
    label: 'ERROR',
  },
  cancelled: {
    badge: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
    badgeBg: 'bg-amber-500/5',
    icon: AlertCircle,
    iconBg: 'bg-amber-500/10',
    iconColor: 'text-amber-400',
    label: 'CANCELLED',
  },
}

function buildTraceEntries(events: RunEventLog[]): TraceEntry[] {
  const entries: TraceEntry[] = []
  if (events.length === 0) return entries

  // 1. System prompt (from request_messages or run-level)
  const requestMsgEvent = events.find((e) => e.eventType === 'request_messages')
  if (requestMsgEvent && Array.isArray(requestMsgEvent.data)) {
    const msgs = requestMsgEvent.data as Array<{ role: string; content: string }>
    let sysIdx = 0
    let ctxIdx = 0
    for (const msg of msgs) {
      if (msg.role === 'system') {
        entries.push({
          id: `system_${sysIdx++}`,
          type: 'system',
          label: 'Initial System Prompt',
          content: msg.content,
          timestamp: requestMsgEvent.timestamp,
        })
      } else if (msg.role === 'user' && entries.some((e) => e.type === 'system')) {
        entries.push({
          id: `context_${ctxIdx++}`,
          type: 'context',
          label: 'Current runtime context',
          content: msg.content,
          timestamp: requestMsgEvent.timestamp,
        })
      }
    }
  }

  // 2. User message
  const userEvents = events.filter((e) => e.eventType === 'user_message')
  for (const ue of userEvents) {
    entries.push({
      id: `user_${ue.timestamp}`,
      type: 'user',
      label: 'User Message',
      content: String(ue.data ?? ''),
      timestamp: ue.timestamp,
    })
  }

  // 3. Walk through reasoning/tool/response in chronological order
  // Group consecutive reasoning events, tool events, and token events
  let reasoningBuffer = ''
  let reasoningStart = 0
  let responseBuffer = ''
  let responseStart = 0
  let toolCallIndex = 0
  let toolResultIndex = 0
  const pendingToolResult = new Map<string, { callIndex: number; callTimestamp: number }>()

  const flushReasoning = (endTime: number) => {
    if (reasoningBuffer.trim()) {
      entries.push({
        id: `reasoning_${reasoningStart}`,
        type: 'reasoning',
        label: 'AI Thinking',
        content: reasoningBuffer.trim(),
        timestamp: reasoningStart,
        endTimestamp: endTime,
        durationMs: endTime - reasoningStart,
      })
      reasoningBuffer = ''
    }
  }

  const flushResponse = (endTime: number) => {
    if (responseBuffer.trim()) {
      const { answer } = parseThink(responseBuffer)
      if (answer.trim()) {
        entries.push({
          id: `assistant_${responseStart}`,
          type: 'assistant',
          label: 'Assistant Response',
          content: answer.trim(),
          timestamp: responseStart,
          endTimestamp: endTime,
          durationMs: endTime - responseStart,
        })
      }
      responseBuffer = ''
    }
  }

  for (const event of events) {
    switch (event.eventType) {
      case 'chat_reasoning':
        if (!reasoningStart) reasoningStart = event.timestamp
        reasoningBuffer += String(event.data ?? '')
        flushResponse(event.timestamp)
        break

      case 'chat_token':
        if (!responseStart) responseStart = event.timestamp
        responseBuffer += String(event.data ?? '')
        flushReasoning(event.timestamp)
        break

      case 'tool_call': {
        flushReasoning(event.timestamp)
        flushResponse(event.timestamp)
        const tc = event.data as Record<string, unknown> | null
        const toolName = tc && typeof tc.name === 'string' ? tc.name : 'unknown_tool'
        const args = tc && tc.arguments != null
          ? (typeof tc.arguments === 'string' ? tc.arguments : JSON.stringify(tc.arguments, null, 2))
          : ''
        const entryId = `tool_call_${toolCallIndex}_${event.timestamp}`
        entries.push({
          id: entryId,
          type: 'tool_call',
          label: toolName,
          content: args,
          toolName,
          toolArgs: args,
          timestamp: event.timestamp,
          indent: 1,
        })
        // Create a paired result entry placeholder
        pendingToolResult.set(entryId, { callIndex: toolCallIndex, callTimestamp: event.timestamp })
        toolCallIndex++
        break
      }

      case 'tool_result': {
        const tr = event.data as Record<string, unknown> | null
        const result = tr && tr.result != null ? String(tr.result) : String(event.data ?? '')
        // Find the most recent unmatched tool call and link to it
        let linkedCallId: string | null = null
        let callTimestamp = event.timestamp
        for (const entry of entries) {
          if (entry.type === 'tool_call' && entry.toolResultTimestamp == null) {
            linkedCallId = entry.id
            callTimestamp = entry.timestamp
          }
        }
        if (linkedCallId) {
          // Update the tool call entry with result info
          const callEntry = entries.find((e) => e.id === linkedCallId)
          if (callEntry) {
            callEntry.toolResult = result
            callEntry.toolResultTimestamp = event.timestamp
            callEntry.endTimestamp = event.timestamp
            callEntry.durationMs = event.timestamp - callTimestamp
          }
        } else {
          // No matching call, add as standalone result
          entries.push({
            id: `tool_result_${toolResultIndex++}_${event.timestamp}`,
            type: 'tool_result',
            label: 'Tool Result',
            content: result,
            toolResult: result,
            timestamp: event.timestamp,
            indent: 1,
          })
        }
        break
      }

      case 'summary_update':
        flushReasoning(event.timestamp)
        flushResponse(event.timestamp)
        entries.push({
          id: `summary_${event.timestamp}`,
          type: 'summary',
          label: 'Summary Update',
          content: String(event.data ?? ''),
          timestamp: event.timestamp,
        })
        break

      case 'run_complete':
        flushReasoning(event.timestamp)
        flushResponse(event.timestamp)
        break

      case 'run_error':
        flushReasoning(event.timestamp)
        flushResponse(event.timestamp)
        entries.push({
          id: `error_${event.timestamp}`,
          type: 'error',
          label: 'Run Error',
          content: String(event.data ?? '执行失败'),
          timestamp: event.timestamp,
        })
        break

      case 'run_cancelled':
        flushReasoning(event.timestamp)
        flushResponse(event.timestamp)
        entries.push({
          id: `cancelled_${event.timestamp}`,
          type: 'cancelled',
          label: 'Run Cancelled',
          content: '',
          timestamp: event.timestamp,
        })
        break
    }
  }

  // Final flush
  const lastEvent = events[events.length - 1]
  if (lastEvent) {
    flushReasoning(lastEvent.timestamp)
    flushResponse(lastEvent.timestamp)
  }

  return entries
}

function computeStats(entries: TraceEntry[], meta: RunMeta | null) {
  const duration = meta
    ? formatDuration(Math.max(0, (meta.finishedAt ?? Date.now()) - meta.startedAt))
    : '--'
  const turns = entries.filter((e) => e.type === 'user').length
  const toolCalls = entries.filter((e) => e.type === 'tool_call').length
  return { duration, turns, toolCalls }
}

interface TimelineBar {
  startPct: number
  widthPct: number
  color: string
  row: number
}

function buildTimelineBars(entries: TraceEntry[], meta: RunMeta | null): TimelineBar[] {
  if (!meta || entries.length === 0) return []
  const start = meta.startedAt
  const end = meta.finishedAt ?? Date.now()
  const total = Math.max(1, end - start)

  const bars: TimelineBar[] = []
  let userRow = 0
  let modelRow = 1
  let toolRow = 2

  for (const entry of entries) {
    const entryStart = Math.max(0, entry.timestamp - start)
    const entryEnd = entry.endTimestamp
      ? Math.max(entryStart, entry.endTimestamp - start)
      : entryStart + Math.min(total * 0.02, 500)
    const startPct = (entryStart / total) * 100
    const widthPct = Math.max(0.5, ((entryEnd - entryStart) / total) * 100)

    let color = ''
    let row = 0
    switch (entry.type) {
      case 'user':
      case 'system':
        color = 'bg-blue-500'
        row = userRow
        break
      case 'reasoning':
      case 'assistant':
        color = 'bg-purple-500'
        row = modelRow
        break
      case 'tool_call':
      case 'tool_result':
        color = 'bg-amber-500'
        row = toolRow
        break
      case 'error':
        color = 'bg-red-500'
        row = modelRow
        break
      default:
        continue
    }
    bars.push({ startPct, widthPct, color, row })
  }

  return bars
}

export function TraceView({ runEvents, runMeta }: Props) {
  const entries = useMemo(() => buildTraceEntries(runEvents), [runEvents])
  const stats = useMemo(() => computeStats(entries, runMeta), [entries, runMeta])
  const bars = useMemo(() => buildTimelineBars(entries, runMeta), [entries, runMeta])
  const [expanded, setExpanded] = useState<Set<string>>(new Set())
  const [selectedTool, setSelectedTool] = useState<ToolDetail | null>(null)
  const [detailTab, setDetailTab] = useState<'summary' | 'payload' | 'result' | 'timing'>('summary')
  const [searchQuery, setSearchQuery] = useState('')

  const toggleExpand = (id: string) => {
    setExpanded((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const filteredEntries = useMemo(() => {
    if (!searchQuery.trim()) return entries
    const q = searchQuery.toLowerCase()
    return entries.filter(
      (e) =>
        e.content.toLowerCase().includes(q) ||
        e.label.toLowerCase().includes(q) ||
        e.type.toLowerCase().includes(q),
    )
  }, [entries, searchQuery])

  // Compute turn/step info for tool calls
  const toolDetails = useMemo(() => {
    const map = new Map<string, { turn: number; step: number }>()
    let turn = 0
    let step = 0
    for (const entry of entries) {
      if (entry.type === 'user') {
        turn++
        step = 0
      }
      if (entry.type === 'tool_call') {
        step++
        map.set(entry.id, { turn, step })
      }
    }
    return map
  }, [entries])

  const handleToolClick = (entry: TraceEntry) => {
    const detail = toolDetails.get(entry.id)
    if (detail) {
      setSelectedTool({ entry, turn: detail.turn, step: detail.step })
      setDetailTab('summary')
    }
  }

  if (entries.length === 0) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-3 text-muted-foreground">
        <ListTree className="size-10 opacity-30" />
        <p className="text-sm">发送消息后，完整执行轨迹将显示在这里</p>
      </div>
    )
  }

  return (
    <div className="flex h-full flex-col">
      {/* Stats Bar */}
      <div className="border-border flex items-center gap-4 border-b px-4 py-3">
        <div className="flex items-center gap-2">
          <Clock className="text-muted-foreground size-3.5" />
          <span className="text-muted-foreground text-xs">Duration</span>
          <span className="text-foreground font-mono text-xs font-semibold">{stats.duration}</span>
        </div>
        <div className="text-border">·</div>
        <div className="flex items-center gap-2">
          <MessageSquare className="text-muted-foreground size-3.5" />
          <span className="text-muted-foreground text-xs">Turns</span>
          <span className="text-foreground font-mono text-xs font-semibold">{stats.turns}</span>
        </div>
        <div className="text-border">·</div>
        <div className="flex items-center gap-2">
          <Wrench className="text-muted-foreground size-3.5" />
          <span className="text-muted-foreground text-xs">Calls</span>
          <span className="text-foreground font-mono text-xs font-semibold">{stats.toolCalls}</span>
        </div>

        <div className="ml-auto flex items-center gap-2">
          <div className="relative">
            <Search className="text-muted-foreground pointer-events-none absolute left-2 top-1/2 size-3.5 -translate-y-1/2" />
            <input
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="搜索"
              className="bg-muted text-foreground placeholder:text-muted-foreground/60 h-7 w-36 rounded-md pl-7 pr-2 text-xs outline-none"
            />
          </div>
          <button
            className="text-muted-foreground hover:text-foreground hover:bg-accent inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs transition-colors"
            title="导出"
          >
            <Download className="size-3.5" /> Export
          </button>
        </div>
      </div>

      {/* Timeline Bar Chart */}
      <div className="border-border bg-card/50 border-b px-4 py-3">
        <div className="space-y-1">
          {[0, 1, 2].map((row) => (
            <div key={row} className="flex items-center gap-2">
              <span className="text-muted-foreground w-10 text-[10px] font-medium uppercase">
                {row === 0 ? 'Input' : row === 1 ? 'Model' : 'Tools'}
              </span>
              <div className="bg-muted/50 relative h-2 flex-1 rounded-sm">
                {bars
                  .filter((b) => b.row === row)
                  .map((bar, i) => (
                    <div
                      key={i}
                      className={cn('absolute top-0 h-full rounded-sm opacity-80', bar.color)}
                      style={{
                        left: `${bar.startPct}%`,
                        width: `${bar.widthPct}%`,
                      }}
                    />
                  ))}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Main Content: Timeline + Detail Panel */}
      <div className="flex min-h-0 flex-1">
        {/* Left: Timeline */}
        <div className="min-w-0 flex-1 overflow-y-auto">
          <div className="space-y-0.5 px-3 py-2">
            {filteredEntries.map((entry) => {
              const config = typeConfig[entry.type]
              const Icon = config.icon
              const isExpanded = expanded.has(entry.id)
              const isTool = entry.type === 'tool_call' || entry.type === 'tool_result'
              const isLongContent = entry.content.length > 120
              const showExpand = isLongContent || isTool

              return (
                <div
                  key={entry.id}
                  className={cn(
                    'group rounded-md transition-colors',
                    isTool && entry.indent === 1 && 'ml-6',
                    selectedTool?.entry.id === entry.id
                      ? 'bg-primary/10 ring-1 ring-primary/30'
                      : 'hover:bg-accent/40',
                  )}
                >
                  <div
                    className="flex items-start gap-2.5 px-2.5 py-2"
                    onClick={() => isTool && entry.type === 'tool_call' && handleToolClick(entry)}
                  >
                    {/* Dot / connector */}
                    <div className="relative flex flex-col items-center pt-0.5">
                      <span className={cn('flex size-5 items-center justify-center rounded-sm', config.iconBg)}>
                        <Icon className={cn('size-3', config.iconColor)} />
                      </span>
                    </div>

                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <span className={cn(
                          'inline-flex items-center rounded px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wide border',
                          config.badge,
                        )}>
                          {config.label}
                        </span>
                        {entry.type === 'tool_call' && (
                          <span className="text-foreground truncate text-xs font-medium">
                            {entry.toolName}
                          </span>
                        )}
                        <span className="text-muted-foreground ml-auto shrink-0 font-mono text-[10px]">
                          {formatTime(entry.timestamp)}
                        </span>
                      </div>

                      <div className="mt-1">
                        {showExpand && !isExpanded ? (
                          <button
                            onClick={(e) => {
                              e.stopPropagation()
                              toggleExpand(entry.id)
                            }}
                            className="w-full text-left"
                          >
                            <p className="text-foreground/80 truncate text-xs leading-relaxed">
                              {entry.type === 'tool_call' && entry.toolArgs
                                ? (() => {
                                    try {
                                      const parsed = JSON.parse(entry.toolArgs)
                                      const keys = Object.keys(parsed)
                                      if (keys.length > 0) {
                                        const firstKey = keys[0]
                                        const firstVal = String(parsed[firstKey])
                                        return `{ "${firstKey}": ${firstVal.length > 40 ? firstVal.slice(0, 40) + '…' : firstVal}${keys.length > 1 ? `, ... (${keys.length} keys)` : ''} }`
                                      }
                                    } catch {
                                      // fall through
                                    }
                                    return entry.content.slice(0, 120) + '…'
                                  })()
                                : entry.content.slice(0, 120) + '…'}
                            </p>
                            {entry.type === 'tool_call' && entry.toolResult && (
                              <p className="text-muted-foreground mt-1 truncate text-[11px] leading-relaxed">
                                <span className="text-emerald-400">→</span> {entry.toolResult.slice(0, 100)}…
                              </p>
                            )}
                            <div className="text-muted-foreground mt-1 flex items-center gap-0.5 text-[10px]">
                              <ChevronDown className="size-3" />
                              展开查看完整内容
                            </div>
                          </button>
                        ) : isExpanded || !showExpand ? (
                          <div>
                            {entry.type === 'tool_call' && entry.toolResult ? (
                              <div className="space-y-2">
                                <div>
                                  <p className="text-muted-foreground mb-1 text-[10px] font-semibold uppercase">Payload</p>
                                  <pre className="text-foreground/85 whitespace-pre-wrap break-words font-mono text-[11px] leading-relaxed">
                                    {entry.toolArgs || '{}'}
                                  </pre>
                                </div>
                                <div>
                                  <p className="text-muted-foreground mb-1 text-[10px] font-semibold uppercase">Result</p>
                                  <pre className="text-emerald-400/85 whitespace-pre-wrap break-words font-mono text-[11px] leading-relaxed">
                                    {entry.toolResult}
                                  </pre>
                                </div>
                              </div>
                            ) : (
                              <pre className="text-foreground/85 whitespace-pre-wrap break-words font-mono text-[11px] leading-relaxed">
                                {entry.content || '(empty)'}
                              </pre>
                            )}
                            {showExpand && (
                              <button
                                onClick={(e) => {
                                  e.stopPropagation()
                                  toggleExpand(entry.id)
                                }}
                                className="text-muted-foreground mt-1 flex items-center gap-0.5 text-[10px] hover:text-foreground"
                              >
                                <ChevronRight className="size-3" />
                                收起
                              </button>
                            )}
                          </div>
                        ) : (
                          <p className="text-foreground/85 text-xs leading-relaxed">
                            {entry.content}
                          </p>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        </div>

        {/* Right: Tool Detail Panel */}
        {selectedTool && (
          <div className="border-border bg-card flex w-80 shrink-0 flex-col border-l">
            <div className="border-border flex items-center gap-2 border-b px-3 py-2.5">
              <span className="bg-amber-500/10 text-amber-400 inline-flex items-center rounded px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wide border border-amber-500/20">
                TOOL
              </span>
              <span className="text-foreground text-xs font-semibold">
                Turn {selectedTool.turn} · Step {selectedTool.step}
              </span>
              <button
                onClick={() => setSelectedTool(null)}
                className="text-muted-foreground hover:text-foreground ml-auto rounded p-1 transition-colors"
              >
                <X className="size-3.5" />
              </button>
            </div>

            {/* Detail Tabs */}
            <div className="border-border flex gap-0 border-b px-2 py-1.5">
              {(['summary', 'payload', 'result', 'timing'] as const).map((tab) => (
                <button
                  key={tab}
                  onClick={() => setDetailTab(tab)}
                  className={cn(
                    'rounded px-2 py-1 text-[11px] font-medium capitalize transition-colors',
                    detailTab === tab
                      ? 'bg-primary text-primary-foreground'
                      : 'text-muted-foreground hover:bg-accent hover:text-foreground',
                  )}
                >
                  {tab === 'summary' ? 'Summary' : tab === 'payload' ? 'Payload' : tab === 'result' ? 'Result' : 'Timing'}
                </button>
              ))}
            </div>

            <div className="min-h-0 flex-1 overflow-y-auto px-3 py-3">
              {detailTab === 'summary' && (
                <div className="space-y-3">
                  <div>
                    <p className="text-muted-foreground text-[11px]">Hierarchy</p>
                    <p className="text-foreground mt-0.5 text-xs">
                      Assistant Message → Tool Call
                    </p>
                  </div>
                  <div>
                    <p className="text-muted-foreground text-[11px]">Status</p>
                    <p className="text-emerald-400 mt-0.5 text-xs font-medium">Completed</p>
                  </div>
                  <div>
                    <p className="text-muted-foreground text-[11px]">Tool Name</p>
                    <p className="text-foreground mt-0.5 font-mono text-xs">
                      {selectedTool.entry.toolName}
                    </p>
                  </div>
                </div>
              )}

              {detailTab === 'payload' && (
                <div className="space-y-2">
                  <p className="text-muted-foreground text-[11px] font-semibold uppercase">Payload</p>
                  <pre className="text-foreground/85 bg-muted/30 whitespace-pre-wrap break-words rounded p-2 font-mono text-[11px] leading-relaxed">
                    {selectedTool.entry.toolArgs || '{}'}
                  </pre>
                </div>
              )}

              {detailTab === 'result' && (
                <div className="space-y-2">
                  <p className="text-muted-foreground text-[11px] font-semibold uppercase">Result</p>
                  <pre className="text-foreground/85 bg-muted/30 whitespace-pre-wrap break-words rounded p-2 font-mono text-[11px] leading-relaxed">
                    {selectedTool.entry.toolResult || 'No result'}
                  </pre>
                </div>
              )}

              {detailTab === 'timing' && (
                <div className="space-y-3">
                  <div>
                    <p className="text-muted-foreground text-[11px]">Started</p>
                    <p className="text-foreground mt-0.5 font-mono text-xs">
                      {formatTime(selectedTool.entry.timestamp)}
                    </p>
                  </div>
                  {selectedTool.entry.toolResultTimestamp && (
                    <div>
                      <p className="text-muted-foreground text-[11px]">Ended</p>
                      <p className="text-foreground mt-0.5 font-mono text-xs">
                        {formatTime(selectedTool.entry.toolResultTimestamp)}
                      </p>
                    </div>
                  )}
                  <div>
                    <p className="text-muted-foreground text-[11px]">Duration</p>
                    <p className="text-emerald-400 mt-0.5 font-mono text-xs font-medium">
                      {selectedTool.entry.durationMs ? formatDuration(selectedTool.entry.durationMs) : 'N/A'}
                    </p>
                  </div>
                  <div>
                    <p className="text-muted-foreground text-[11px]">Timing Source</p>
                    <p className="text-foreground/70 mt-0.5 text-[11px]">
                      Session timestamps
                    </p>
                  </div>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
