import { useMemo, useState } from 'react'
import type { RunEventLog, RunMeta } from '@/runtime/aui-runtime'
import { cn } from '@/lib/utils'
import { parseThink } from '@/lib/think-parser'
import {
  Play, User, Brain, MessageSquare, FileText, Wrench, CheckCircle2,
  XCircle, AlertCircle, ChevronDown, Bot, ScrollText,
} from 'lucide-react'
import {
  Collapsible, CollapsibleTrigger, CollapsibleContent,
} from '@/components/ui/collapsible'

interface Props {
  runEvents: RunEventLog[]
  runMeta: RunMeta | null
}

interface TimelineStep {
  id: string
  icon: typeof Play
  label: string
  color: string
  bgColor: string
  borderColor: string
  startTime: number
  endTime: number
  content: string | null
  subSteps?: TimelineStep[]
  count?: number
}

interface RequestMessage {
  role: string
  content: string
}

function formatTime(t: number): string {
  return new Date(t).toLocaleTimeString('zh-CN', {
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  })
}

function durationLabel(start: number, end: number): string {
  const ms = Math.max(0, end - start)
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

export function Inspector({ runEvents, runMeta }: Props) {
  const [view, setView] = useState<'messages' | 'trace'>('messages')
  const tokenCount = runEvents.filter((e) => e.eventType === 'chat_token').length
  const reasoningCount = runEvents.filter((e) => e.eventType === 'chat_reasoning').length
  const duration = runMeta
    ? durationLabel(runMeta.startedAt, runMeta.finishedAt ?? Date.now())
    : '--'

  const steps = useMemo(() => buildTimeline(runEvents), [runEvents])
  const requestMessages = useMemo(() => extractRequestMessages(runEvents), [runEvents])
  const rawResponseText = useMemo(() => {
    const complete = [...runEvents].reverse().find((e) => e.eventType === 'run_complete')
    if (typeof complete?.data === 'string' && complete.data) return complete.data
    return collectText(runEvents, 'chat_token')
  }, [runEvents])
  const parsedResponse = useMemo(() => parseThink(rawResponseText), [rawResponseText])
  const reasoningText = useMemo(() => {
    const reasoning = collectText(runEvents, 'chat_reasoning')
    return reasoning || parsedResponse.reasoning
  }, [runEvents, parsedResponse.reasoning])
  const responseText = parsedResponse.answer

  return (
    <aside className="bg-card border-border flex w-[28rem] shrink-0 flex-col border-l">
      {/* Header */}
      <div className="border-border border-b px-4 py-3">
        <h2 className="text-foreground text-sm font-semibold">Run Inspector</h2>
        <p className="text-muted-foreground mt-0.5 truncate font-mono text-xs">
          {runMeta?.runCode || '等待 Run 启动…'}
        </p>
      </div>

      {/* Stats */}
      <div className="border-border grid grid-cols-3 gap-px border-b bg-border/40">
        <div className="bg-card px-3 py-2.5">
          <p className="text-muted-foreground text-xs">耗时</p>
          <p className="text-foreground text-sm font-mono">{duration}</p>
        </div>
        <div className="bg-card px-3 py-2.5">
          <p className="text-muted-foreground text-xs">回复 Token</p>
          <p className="text-foreground text-sm font-mono">{tokenCount}</p>
        </div>
        <div className="bg-card px-3 py-2.5">
          <p className="text-muted-foreground text-xs">思考片段</p>
          <p className="text-foreground text-sm font-mono">{reasoningCount}</p>
        </div>
      </div>

      <div className="border-border flex gap-1 border-b px-3 py-2">
        <button
          type="button"
          onClick={() => setView('messages')}
          className={cn(
            'flex flex-1 items-center justify-center gap-1.5 rounded-md px-2 py-1.5 text-xs font-medium transition-colors',
            view === 'messages'
              ? 'bg-primary text-primary-foreground'
              : 'text-muted-foreground hover:bg-accent hover:text-foreground',
          )}
        >
          <ScrollText className="size-3.5" /> Messages
        </button>
        <button
          type="button"
          onClick={() => setView('trace')}
          className={cn(
            'flex flex-1 items-center justify-center gap-1.5 rounded-md px-2 py-1.5 text-xs font-medium transition-colors',
            view === 'trace'
              ? 'bg-primary text-primary-foreground'
              : 'text-muted-foreground hover:bg-accent hover:text-foreground',
          )}
        >
          <Play className="size-3.5" /> Trace
        </button>
      </div>

      {view === 'messages' ? (
        <MessageStack
          requestMessages={requestMessages}
          reasoningText={reasoningText}
          responseText={responseText}
          hasEvents={runEvents.length > 0}
        />
      ) : (
        <div className="min-h-0 flex-1 overflow-y-auto px-3 py-3">
          {steps.length === 0 ? (
            <div className="text-muted-foreground flex h-full flex-col items-center justify-center gap-3">
              <Brain className="size-8 opacity-30" />
              <p className="text-xs">发送消息后，执行过程将显示在这里</p>
            </div>
          ) : (
            <div className="space-y-2">
              {steps.map((step) => (
                <TimelineCard key={step.id} step={step} />
              ))}
            </div>
          )}
        </div>
      )}
    </aside>
  )
}

function MessageStack({
  requestMessages,
  reasoningText,
  responseText,
  hasEvents,
}: {
  requestMessages: RequestMessage[]
  reasoningText: string
  responseText: string
  hasEvents: boolean
}) {
  if (!hasEvents) {
    return (
      <div className="text-muted-foreground flex min-h-0 flex-1 flex-col items-center justify-center gap-3 px-6 text-center">
        <ScrollText className="size-8 opacity-30" />
        <p className="text-xs">发送消息后，这里会展示真实请求消息栈、模型思考与最终回复</p>
      </div>
    )
  }

  return (
    <div className="min-h-0 flex-1 overflow-y-auto px-3 py-3">
      <section className="space-y-2">
        <div className="flex items-center justify-between">
          <h3 className="text-foreground text-xs font-semibold">真实发送给模型</h3>
          <span className="text-muted-foreground font-mono text-[11px]">{requestMessages.length} messages</span>
        </div>
        {requestMessages.length > 0 ? (
          <div className="space-y-2">
            {requestMessages.map((message, index) => (
              <MessageBlock key={`${message.role}-${index}`} message={message} index={index} />
            ))}
          </div>
        ) : (
          <div className="border-border bg-muted/30 text-muted-foreground rounded-lg border px-3 py-3 text-xs">
            当前后端未返回请求消息栈。请确认 SSE 中是否包含 request_messages 事件。
          </div>
        )}
      </section>

      <section className="mt-4 space-y-2">
        <h3 className="text-foreground text-xs font-semibold">模型输出</h3>
        <OutputBlock
          icon={Brain}
          label="AI 思考"
          content={reasoningText}
          empty="未捕获到 reasoning_content 或 <think> 片段"
          className="border-purple-500/20 bg-purple-500/5"
          iconClassName="bg-purple-500/10 text-purple-500"
        />
        <OutputBlock
          icon={Bot}
          label="AI 回复"
          content={responseText}
          empty="回复内容等待中"
          className="border-emerald-500/20 bg-emerald-500/5"
          iconClassName="bg-emerald-500/10 text-emerald-500"
        />
      </section>
    </div>
  )
}

function MessageBlock({ message, index }: { message: RequestMessage; index: number }) {
  const roleClass = getRoleClass(message.role)
  const Icon = message.role === 'assistant' ? Bot : message.role === 'system' ? FileText : User

  return (
    <div className={cn('rounded-lg border px-3 py-2.5', roleClass.card)}>
      <div className="mb-2 flex items-center gap-2">
        <span className={cn('flex size-6 items-center justify-center rounded-md', roleClass.icon)}>
          <Icon className="size-3.5" />
        </span>
        <span className="text-foreground text-xs font-semibold uppercase">{message.role}</span>
        <span className="text-muted-foreground ml-auto font-mono text-[11px]">#{index + 1}</span>
      </div>
      <pre className="text-foreground/85 max-h-80 overflow-y-auto whitespace-pre-wrap break-words font-mono text-xs leading-relaxed">
        {message.content || '(empty)'}
      </pre>
    </div>
  )
}

function OutputBlock({
  icon: Icon,
  label,
  content,
  empty,
  className,
  iconClassName,
}: {
  icon: typeof Play
  label: string
  content: string
  empty: string
  className: string
  iconClassName: string
}) {
  return (
    <div className={cn('rounded-lg border px-3 py-2.5', className)}>
      <div className="mb-2 flex items-center gap-2">
        <span className={cn('flex size-6 items-center justify-center rounded-md', iconClassName)}>
          <Icon className="size-3.5" />
        </span>
        <span className="text-foreground text-xs font-semibold">{label}</span>
      </div>
      <pre className={cn(
        'max-h-80 overflow-y-auto whitespace-pre-wrap break-words font-mono text-xs leading-relaxed',
        content ? 'text-foreground/85' : 'text-muted-foreground',
      )}>
        {content || empty}
      </pre>
    </div>
  )
}

function getRoleClass(role: string): { card: string; icon: string } {
  if (role === 'system') {
    return { card: 'border-amber-500/20 bg-amber-500/5', icon: 'bg-amber-500/10 text-amber-500' }
  }
  if (role === 'assistant') {
    return { card: 'border-emerald-500/20 bg-emerald-500/5', icon: 'bg-emerald-500/10 text-emerald-500' }
  }
  return { card: 'border-blue-500/20 bg-blue-500/5', icon: 'bg-blue-500/10 text-blue-500' }
}

function TimelineCard({ step }: { step: TimelineStep }) {
  const Icon = step.icon
  const hasContent = step.content != null && step.content !== ''
  const isError = step.id === 'error'
  const isCancelled = step.id === 'cancelled'
  const isLifecycle = step.id === 'lifecycle'
  const isTool = step.id.startsWith('tool_')

  return (
    <Collapsible defaultOpen={!isLifecycle}>
      <div
        className={cn(
          'border-border rounded-lg border',
          step.bgColor,
        )}
      >
        <CollapsibleTrigger className="flex w-full items-center gap-2.5 px-3 py-2.5 text-left">
          <span className={cn('flex size-7 shrink-0 items-center justify-center rounded-md', step.color)}>
            <Icon className="size-3.5" />
          </span>
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2">
              <span className="text-foreground text-xs font-semibold truncate">
                {step.label}
              </span>
              {step.count != null && step.count > 1 && (
                <span className="text-muted-foreground text-xs">×{step.count}</span>
              )}
            </div>
            <div className="text-muted-foreground text-xs">
              {formatTime(step.startTime)}
              {step.endTime > step.startTime && (
                <span className="ml-1.5">· {durationLabel(step.startTime, step.endTime)}</span>
              )}
            </div>
          </div>
          {hasContent && (
            <ChevronDown className="text-muted-foreground size-3.5 shrink-0 data-[closed]:rotate-180 transition-transform" />
          )}
        </CollapsibleTrigger>

        {hasContent && (
          <CollapsibleContent>
            <div className="border-border border-t px-3 py-2.5">
              <pre className={cn(
                'text-muted-foreground whitespace-pre-wrap break-words font-mono text-xs leading-relaxed',
                isError && 'text-destructive',
                isCancelled && 'text-amber-500',
                isTool && 'text-emerald-500',
              )}>
                {step.content}
              </pre>
            </div>
          </CollapsibleContent>
        )}
      </div>
    </Collapsible>
  )
}

function buildTimeline(events: RunEventLog[]): TimelineStep[] {
  const steps: TimelineStep[] = []
  if (events.length === 0) return steps

  // 1. Lifecycle start
  const startEvents = events.filter(
    (e) => e.eventType === 'run_start' || e.eventType === 'chat_start',
  )
  if (startEvents.length > 0) {
    steps.push({
      id: 'lifecycle_start',
      icon: Play,
      label: 'Run 启动',
      color: 'bg-primary/10 text-primary',
      bgColor: 'bg-primary/5',
      borderColor: 'border-primary/20',
      startTime: startEvents[0].timestamp,
      endTime: startEvents[startEvents.length - 1].timestamp,
      content: null,
    })
  }

  const requestEvent = events.find((e) => e.eventType === 'request_messages')
  if (requestEvent) {
    const requestMessages = extractRequestMessages(events)
    steps.push({
      id: 'request_messages',
      icon: ScrollText,
      label: '模型请求消息栈',
      color: 'bg-amber-500/10 text-amber-500',
      bgColor: 'bg-amber-500/5',
      borderColor: 'border-amber-500/20',
      startTime: requestEvent.timestamp,
      endTime: requestEvent.timestamp,
      content: requestMessages.map((m) => `[${m.role}]\n${m.content}`).join('\n\n---\n\n'),
      count: requestMessages.length,
    })
  }

  // 2. User message
  const userEvents = events.filter((e) => e.eventType === 'user_message')
  if (userEvents.length > 0) {
    const userContent = userEvents.map((e) => String(e.data ?? '')).join('\n')
    steps.push({
      id: 'user_message',
      icon: User,
      label: '用户消息',
      color: 'bg-blue-500/10 text-blue-500',
      bgColor: 'bg-blue-500/5',
      borderColor: 'border-blue-500/20',
      startTime: userEvents[0].timestamp,
      endTime: userEvents[userEvents.length - 1].timestamp,
      content: userContent,
    })
  }

  // 3. AI Reasoning
  const reasoningEvents = events.filter((e) => e.eventType === 'chat_reasoning')
  if (reasoningEvents.length > 0) {
    const reasoningContent = reasoningEvents.map((e) => String(e.data ?? '')).join('')
    steps.push({
      id: 'reasoning',
      icon: Brain,
      label: 'AI 思考',
      color: 'bg-purple-500/10 text-purple-500',
      bgColor: 'bg-purple-500/5',
      borderColor: 'border-purple-500/20',
      startTime: reasoningEvents[0].timestamp,
      endTime: reasoningEvents[reasoningEvents.length - 1].timestamp,
      content: reasoningContent,
      count: reasoningEvents.length,
    })
  }

  // 4. AI Response (tokens)
  const tokenEvents = events.filter((e) => e.eventType === 'chat_token')
  if (tokenEvents.length > 0) {
    const tokenContent = tokenEvents.map((e) => String(e.data ?? '')).join('')
    steps.push({
      id: 'response',
      icon: MessageSquare,
      label: 'AI 回复',
      color: 'bg-emerald-500/10 text-emerald-500',
      bgColor: 'bg-emerald-500/5',
      borderColor: 'border-emerald-500/20',
      startTime: tokenEvents[0].timestamp,
      endTime: tokenEvents[tokenEvents.length - 1].timestamp,
      content: tokenContent,
      count: tokenEvents.length,
    })
  }

  // 5. Tool calls (tool_call / tool_result)
  const toolEvents = events.filter(
    (e) => e.eventType === 'tool_call' || e.eventType === 'tool_result',
  )
  for (const te of toolEvents) {
    const isCall = te.eventType === 'tool_call'
    steps.push({
      id: `tool_${te.timestamp}`,
      icon: Wrench,
      label: isCall ? '工具调用' : '工具结果',
      color: 'bg-amber-500/10 text-amber-500',
      bgColor: 'bg-amber-500/5',
      borderColor: 'border-amber-500/20',
      startTime: te.timestamp,
      endTime: te.timestamp,
      content: te.data != null ? JSON.stringify(te.data, null, 2) : null,
    })
  }

  // 6. Summary update
  const summaryEvents = events.filter((e) => e.eventType === 'summary_update')
  if (summaryEvents.length > 0) {
    const summaryContent = summaryEvents.map((e) => String(e.data ?? '')).join('\n')
    steps.push({
      id: 'summary',
      icon: FileText,
      label: '对话摘要更新',
      color: 'bg-cyan-500/10 text-cyan-500',
      bgColor: 'bg-cyan-500/5',
      borderColor: 'border-cyan-500/20',
      startTime: summaryEvents[0].timestamp,
      endTime: summaryEvents[summaryEvents.length - 1].timestamp,
      content: summaryContent,
    })
  }

  // 7. Chat complete
  const chatComplete = events.find((e) => e.eventType === 'chat_complete')
  if (chatComplete) {
    steps.push({
      id: 'chat_complete',
      icon: CheckCircle2,
      label: '对话完成',
      color: 'bg-emerald-500/10 text-emerald-500',
      bgColor: 'bg-emerald-500/5',
      borderColor: 'border-emerald-500/20',
      startTime: chatComplete.timestamp,
      endTime: chatComplete.timestamp,
      content: null,
    })
  }

  // 8. Run complete
  const runComplete = events.find((e) => e.eventType === 'run_complete')
  if (runComplete) {
    const reply = typeof runComplete.data === 'string' ? runComplete.data : null
    steps.push({
      id: 'run_complete',
      icon: CheckCircle2,
      label: 'Run 完成',
      color: 'bg-emerald-500/10 text-emerald-500',
      bgColor: 'bg-emerald-500/5',
      borderColor: 'border-emerald-500/20',
      startTime: runComplete.timestamp,
      endTime: runComplete.timestamp,
      content: reply,
    })
  }

  // 9. Error
  const errorEvent = events.find((e) => e.eventType === 'run_error')
  if (errorEvent) {
    steps.push({
      id: 'error',
      icon: XCircle,
      label: 'Run 错误',
      color: 'bg-destructive/10 text-destructive',
      bgColor: 'bg-destructive/5',
      borderColor: 'border-destructive/20',
      startTime: errorEvent.timestamp,
      endTime: errorEvent.timestamp,
      content: String(errorEvent.data ?? '执行失败'),
    })
  }

  // 10. Cancelled
  const cancelEvent = events.find((e) => e.eventType === 'run_cancelled')
  if (cancelEvent) {
    steps.push({
      id: 'cancelled',
      icon: AlertCircle,
      label: 'Run 已取消',
      color: 'bg-amber-500/10 text-amber-500',
      bgColor: 'bg-amber-500/5',
      borderColor: 'border-amber-500/20',
      startTime: cancelEvent.timestamp,
      endTime: cancelEvent.timestamp,
      content: null,
    })
  }

  return steps
}

function extractRequestMessages(events: RunEventLog[]): RequestMessage[] {
  const event = [...events].reverse().find((item) => item.eventType === 'request_messages')
  if (!Array.isArray(event?.data)) return []

  return event.data
    .map((item) => {
      if (!item || typeof item !== 'object') return null
      const record = item as Record<string, unknown>
      const role = typeof record.role === 'string' ? record.role : 'unknown'
      const content = typeof record.content === 'string' ? record.content : ''
      return { role, content }
    })
    .filter((item): item is RequestMessage => item != null)
}

function collectText(events: RunEventLog[], eventType: RunEventLog['eventType']): string {
  return events
    .filter((e) => e.eventType === eventType)
    .map((e) => String(e.data ?? ''))
    .join('')
}
