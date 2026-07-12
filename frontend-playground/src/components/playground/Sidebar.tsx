import type { AgentProjectDTO, ConversationDTO } from '@/types/api'
import { Button } from '@/components/ui/button'
import { Plus, RefreshCw, LogOut, MessageSquare, Sparkles } from 'lucide-react'
import { cn } from '@/lib/utils'

interface Props {
  nickname: string
  projects: AgentProjectDTO[]
  projectCode: string
  onProjectChange: (code: string) => void
  conversations: ConversationDTO[]
  activeConvCode: string
  loadingConv: boolean
  onNewConversation: () => void
  onSelectConversation: (conv: ConversationDTO) => void
  onRefresh: () => void
  onLogout: () => void
}

export function Sidebar(props: Props) {
  return (
    <aside className="bg-card border-border flex w-72 shrink-0 flex-col border-r">
      <div className="border-border flex items-center gap-2 border-b px-4 py-3">
        <div className="bg-primary/10 flex size-8 items-center justify-center rounded-lg">
          <Sparkles className="text-primary size-4" />
        </div>
        <span className="text-foreground font-semibold">Playground</span>
      </div>

      <div className="border-border space-y-2 border-b px-4 py-3">
        <label className="text-muted-foreground text-xs font-medium">
          项目
        </label>
        <select
          value={props.projectCode}
          onChange={(e) => props.onProjectChange(e.target.value)}
          className="border-input bg-background text-foreground h-9 w-full rounded-lg border px-2.5 text-sm outline-none transition-colors focus:border-primary focus:ring-2 focus:ring-primary/20"
        >
          {props.projects.length === 0 && <option value="">无可用项目</option>}
          {props.projects.map((p) => (
            <option key={p.projectCode} value={p.projectCode}>
              {p.projectName}
            </option>
          ))}
        </select>
      </div>

      <div className="border-border flex items-center justify-between border-b px-4 py-2.5">
        <span className="text-muted-foreground text-xs font-medium">
          会话 ({props.conversations.length})
        </span>
        <div className="flex items-center gap-1">
          <button
            onClick={props.onRefresh}
            className="text-muted-foreground hover:text-foreground rounded p-1.5 transition-colors"
            title="刷新"
          >
            <RefreshCw className={cn('size-3.5', props.loadingConv && 'animate-spin')} />
          </button>
          <Button size="sm" variant="ghost" className="h-7 px-2" onClick={props.onNewConversation} disabled={!props.projectCode}>
            <Plus className="size-3.5" /> 新建
          </Button>
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto">
        {props.conversations.length === 0 && (
          <p className="text-muted-foreground/60 px-4 py-6 text-center text-xs">
            {props.projectCode ? '暂无会话，点击「新建」创建' : '请先选择项目'}
          </p>
        )}
        {props.conversations.map((conv) => {
          const active = props.activeConvCode === conv.conversationCode
          return (
            <button
              key={conv.id}
              onClick={() => props.onSelectConversation(conv)}
              className={cn(
                'border-border flex w-full flex-col gap-1 border-b px-4 py-3 text-left transition-colors',
                active
                  ? 'bg-primary/10 border-l-primary border-l-2'
                  : 'hover:bg-accent/50 border-l-2 border-l-transparent',
              )}
            >
              <span className={cn(
                'truncate text-sm font-medium',
                active ? 'text-primary' : 'text-foreground',
              )}>
                {conv.title || conv.conversationCode}
              </span>
              <span className="text-muted-foreground flex items-center gap-1 truncate text-xs">
                <MessageSquare className="size-3" />
                {conv.modelName || '--'}
                {conv.lastMessageTime && ` · ${conv.lastMessageTime}`}
              </span>
            </button>
          )
        })}
      </div>

      <div className="border-border border-t px-4 py-2.5">
        <button
          onClick={props.onLogout}
          className="text-muted-foreground hover:text-foreground flex w-full items-center gap-2 text-xs transition-colors"
        >
          <LogOut className="size-3.5" />
          退出 ({props.nickname})
        </button>
      </div>
    </aside>
  )
}
