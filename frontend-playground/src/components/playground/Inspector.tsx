import type { RunEventLog, RunMeta } from '@/runtime/aui-runtime'
import { cn } from '@/lib/utils'

interface Props {
  runEvents: RunEventLog[]
  runMeta: RunMeta | null
}

const LIFECYCLE_EVENTS = new Set([
  'run_start',
  'chat_start',
  'chat_complete',
  'run_complete',
  'run_error',
  'run_cancelled',
])

const EVENT_LABELS: Record<string, string> = {
  run_start: 'Run 启动',
  chat_start: '对话开始',
  chat_token: 'Token 输出',
  chat_complete: '对话完成',
  run_complete: 'Run 完成',
  run_error: 'Run 错误',
  run_cancelled: 'Run 取消',
}

function eventColor(eventType: string): string {
  if (eventType === 'run_error') return 'bg-destructive'
  if (eventType === 'run_cancelled') return 'bg-amber-500'
  if (eventType === 'run_complete' || eventType === 'chat_complete')
    return 'bg-emerald-500'
  if (eventType === 'chat_token') return 'bg-muted-foreground/40'
  return 'bg-primary'
}

function formatTime(t: number): string {
  return new Date(t).toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

export function Inspector({ runEvents, runMeta }: Props) {
  const tokenCount = runEvents.filter((e) => e.eventType === 'chat_token').length
  const duration = runMeta
    ? `${Math.max(0, (runMeta.finishedAt ?? Date.now()) - runMeta.startedAt)} ms`
    : '--'

  const lifecycleEvents = runEvents.filter((e) => LIFECYCLE_EVENTS.has(e.eventType))

  return (
    <aside className="bg-card border-border flex w-80 shrink-0 flex-col border-l">
      <div className="border-border border-b px-4 py-3">
        <h2 className="text-foreground text-sm font-semibold">Run Inspector</h2>
      </div>

      <div className="border-border grid grid-cols-2 gap-px border-b bg-border/40">
        <div className="bg-card px-4 py-3">
          <p className="text-muted-foreground text-xs">Run Code</p>
          <p className="text-foreground truncate text-sm font-mono">
            {runMeta?.runCode || '--'}
          </p>
        </div>
        <div className="bg-card px-4 py-3">
          <p className="text-muted-foreground text-xs">耗时</p>
          <p className="text-foreground text-sm font-mono">{duration}</p>
        </div>
        <div className="bg-card px-4 py-3">
          <p className="text-muted-foreground text-xs">Token 事件</p>
          <p className="text-foreground text-sm font-mono">{tokenCount}</p>
        </div>
        <div className="bg-card px-4 py-3">
          <p className="text-muted-foreground text-xs">总事件数</p>
          <p className="text-foreground text-sm font-mono">{runEvents.length}</p>
        </div>
      </div>

      <div className="border-border border-b px-4 py-3">
        <h3 className="text-muted-foreground mb-2 text-xs font-medium">
          生命周期
        </h3>
        <div className="space-y-1.5">
          {lifecycleEvents.length === 0 && (
            <p className="text-muted-foreground text-xs">发送消息后显示事件</p>
          )}
          {lifecycleEvents.map((event, i) => (
            <div key={i} className="flex items-center gap-2">
              <span className={cn('size-2 shrink-0 rounded-full', eventColor(event.eventType))} />
              <span className="text-foreground text-xs font-medium">
                {EVENT_LABELS[event.eventType] || event.eventType}
              </span>
              <span className="text-muted-foreground ml-auto text-xs">
                {formatTime(event.timestamp)}
              </span>
            </div>
          ))}
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-4 py-3">
        <h3 className="text-muted-foreground mb-2 text-xs font-medium">
          全部事件 ({runEvents.length})
        </h3>
        <div className="space-y-2">
          {runEvents.map((event, i) => (
            <div key={i} className="border-border rounded-md border px-2.5 py-2">
              <div className="flex items-center gap-2">
                <span className={cn('size-1.5 shrink-0 rounded-full', eventColor(event.eventType))} />
                <span className="text-foreground text-xs font-mono font-medium">
                  {event.eventType}
                </span>
                <span className="text-muted-foreground ml-auto text-xs">
                  {formatTime(event.timestamp)}
                </span>
              </div>
              {event.taskState && (
                <p className="text-muted-foreground mt-1 text-xs">
                  state: {event.taskState}
                </p>
              )}
              {event.dataPreview && (
                <p className="text-muted-foreground mt-0.5 line-clamp-2 break-all font-mono text-xs">
                  {event.dataPreview}
                </p>
              )}
            </div>
          ))}
        </div>
      </div>
    </aside>
  )
}
