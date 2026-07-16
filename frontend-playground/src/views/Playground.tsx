import { useCallback, useEffect, useState } from 'react'
import { useAiRuntime } from '@/runtime/aui-runtime'
import { Thread } from '@/components/assistant-ui/thread'
import { Sidebar } from '@/components/playground/Sidebar'
import { Inspector } from '@/components/playground/Inspector'
import { pageProjects } from '@/api/project'
import { pageConversations, createConversation } from '@/api/conversation'
import { getMessages } from '@/api/chat'
import type { AgentProjectDTO, ConversationDTO } from '@/types/api'
import { Plus, Sun, Moon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useTheme } from '@/lib/useTheme'

interface Props {
  nickname: string
  onLogout: () => void
}

interface PromptVariableDefinition {
  name: string
  label?: string
  type?: string
  required?: boolean
  description?: string
  defaultValue?: unknown
}

function parsePromptVariables(project?: AgentProjectDTO): PromptVariableDefinition[] {
  if (!project?.promptVariables?.trim()) return []
  try {
    const parsed = JSON.parse(project.promptVariables)
    return Array.isArray(parsed) ? parsed.filter((item) => item?.name) : []
  } catch {
    return []
  }
}

export default function Playground({ nickname, onLogout }: Props) {
  const ai = useAiRuntime()
  const { theme, toggle } = useTheme()
  const [projects, setProjects] = useState<AgentProjectDTO[]>([])
  const [projectCode, setProjectCode] = useState('')
  const [conversations, setConversations] = useState<ConversationDTO[]>([])
  const [activeConvCode, setActiveConvCode] = useState('')
  const [loadingConv, setLoadingConv] = useState(false)
  const [variableDialogOpen, setVariableDialogOpen] = useState(false)
  const [pendingVariables, setPendingVariables] = useState<PromptVariableDefinition[]>([])
  const [contextVariableForm, setContextVariableForm] = useState<Record<string, unknown>>({})

  const loadProjects = useCallback(async () => {
    try {
      const res = await pageProjects({ current: 1, size: 100, state: 1 })
      setProjects(res.records)
      if (!projectCode && res.records.length > 0) {
        setProjectCode(res.records[0].projectCode)
      }
    } catch {
      /* ignore */
    }
  }, [projectCode])

  const loadConversations = useCallback(async () => {
    if (!projectCode) {
      setConversations([])
      return
    }
    setLoadingConv(true)
    try {
      const res = await pageConversations({
        productCode: projectCode,
        state: 0,
        current: 1,
        size: 50,
      })
      setConversations(res.records)
    } catch {
      setConversations([])
    } finally {
      setLoadingConv(false)
    }
  }, [projectCode])

  useEffect(() => {
    void loadProjects()
  }, [loadProjects])

  useEffect(() => {
    void loadConversations()
  }, [loadConversations])

  const createWithVariables = useCallback(async (contextVariables: Record<string, unknown>) => {
    if (!projectCode) return
    try {
      const res = await createConversation({
        productCode: projectCode,
        contextVariables,
        title: `Playground ${new Date().toLocaleString('zh-CN', {
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit',
        })}`,
      })
      ai.resetConversation()
      ai.setConversationCode(res.conversationCode)
      setActiveConvCode(res.conversationCode)
      void loadConversations()
    } catch {
      /* request layer reports */
    }
  }, [projectCode, ai])

  const handleNewConversation = useCallback(async () => {
    const definitions = parsePromptVariables(projects.find((p) => p.projectCode === projectCode))
    if (definitions.length > 0) {
      const values: Record<string, unknown> = {}
      definitions.forEach((item) => {
        values[item.name] = item.defaultValue ?? ''
      })
      setPendingVariables(definitions)
      setContextVariableForm(values)
      setVariableDialogOpen(true)
      return
    }
    await createWithVariables({})
  }, [projectCode, projects, createWithVariables])

  const handleConfirmVariables = useCallback(async () => {
    const values: Record<string, unknown> = {}
    for (const item of pendingVariables) {
      const raw = contextVariableForm[item.name]
      if (item.required && (raw == null || String(raw).trim() === '')) return
      if (raw == null || String(raw).trim() === '') continue
      if (item.type === 'number') {
        values[item.name] = Number(raw)
      } else if (item.type === 'boolean') {
        values[item.name] = raw === true || raw === 'true'
      } else {
        values[item.name] = String(raw).trim()
      }
    }
    setVariableDialogOpen(false)
    await createWithVariables(values)
  }, [pendingVariables, contextVariableForm, createWithVariables])

  const handleSelectConversation = useCallback(
    async (conv: ConversationDTO) => {
      if (ai.isRunning) return
      setActiveConvCode(conv.conversationCode)
      ai.setConversationCode(conv.conversationCode)
      ai.clearEvents()
      try {
        const msgs = await getMessages(conv.conversationCode)
        ai.loadHistory(msgs)
      } catch {
        /* ignore */
      }
    },
    [ai],
  )

  const activeProject = projects.find((p) => p.projectCode === projectCode)

  return (
    <div className="bg-background flex h-dvh overflow-hidden">
      <Sidebar
        nickname={nickname}
        projects={projects}
        projectCode={projectCode}
        onProjectChange={(c) => {
          setProjectCode(c)
          setActiveConvCode('')
          ai.resetConversation()
          ai.setConversationCode('')
        }}
        conversations={conversations}
        activeConvCode={activeConvCode}
        loadingConv={loadingConv}
        onNewConversation={handleNewConversation}
        onSelectConversation={handleSelectConversation}
        onRefresh={() => void loadConversations()}
        onLogout={onLogout}
      />

      <main className="flex min-w-0 flex-1 flex-col">
        <header className="border-border bg-card/60 flex items-center gap-3 border-b px-4 py-2.5 backdrop-blur-sm">
          <div className="flex min-w-0 items-center gap-2">
            <span className="text-foreground truncate text-sm font-semibold">
              {activeProject?.projectName || projectCode || '未选择项目'}
            </span>
            {activeConvCode && (
              <>
                <span className="text-border">/</span>
                <span className="text-muted-foreground truncate font-mono text-xs">
                  {activeConvCode}
                </span>
              </>
            )}
          </div>
          <div className="ml-auto flex items-center gap-2">
            <button
              onClick={toggle}
              className="text-muted-foreground hover:text-foreground hover:bg-accent rounded-lg p-2 transition-colors"
              title="切换主题"
            >
              {theme === 'dark' ? <Sun className="size-4" /> : <Moon className="size-4" />}
            </button>
            {!activeConvCode && (
              <Button
                size="sm"
                onClick={handleNewConversation}
                disabled={!projectCode}
              >
                <Plus className="size-4" /> 新建会话
              </Button>
            )}
          </div>
        </header>

        <div className="border-border bg-card/30 flex items-center gap-2 border-b px-4 py-2">
          <span className="text-primary shrink-0 text-xs font-semibold">
            SYSTEM PROMPT
          </span>
          <input
            value={ai.systemPrompt}
            onChange={(e) => ai.setSystemPrompt(e.target.value)}
            placeholder="可选。临时覆盖本次 Run 的行为约束。留空使用默认。"
            className="text-foreground placeholder:text-muted-foreground/60 h-7 min-w-0 flex-1 rounded-md bg-transparent px-2 text-sm outline-none"
          />
        </div>

        <div className="min-h-0 flex-1">
          {activeConvCode ? (
            <ai.AssistantRuntimeProvider runtime={ai.runtime}>
              <Thread />
            </ai.AssistantRuntimeProvider>
          ) : (
            <div className="text-muted-foreground flex h-full flex-col items-center justify-center gap-4">
              <div className="bg-primary/10 flex size-16 items-center justify-center rounded-2xl">
                <Plus className="text-primary size-7" />
              </div>
              <p className="text-sm">选择历史会话或点击「新建会话」开始对话</p>
            </div>
          )}
        </div>
      </main>

      <Inspector runEvents={ai.runEvents} runMeta={ai.runMeta} />

      {variableDialogOpen && (
        <div className="bg-background/70 fixed inset-0 z-50 flex items-center justify-center backdrop-blur-sm">
          <div className="bg-card border-border w-[520px] max-w-[calc(100vw-32px)] rounded-lg border shadow-xl">
            <div className="border-border border-b px-4 py-3">
              <h2 className="text-foreground text-sm font-semibold">创建会话变量</h2>
            </div>
            <div className="space-y-3 px-4 py-4">
              {pendingVariables.map((item) => (
                <label key={item.name} className="block space-y-1.5">
                  <span className="text-muted-foreground text-xs">
                    {item.label || item.name}{item.required ? ' *' : ''}
                  </span>
                  {item.type === 'boolean' ? (
                    <select
                      value={String(contextVariableForm[item.name] ?? '')}
                      onChange={(e) => setContextVariableForm((prev) => ({ ...prev, [item.name]: e.target.value }))}
                      className="border-input bg-background text-foreground h-9 w-full rounded-md border px-2 text-sm outline-none"
                    >
                      <option value="">请选择</option>
                      <option value="true">true</option>
                      <option value="false">false</option>
                    </select>
                  ) : (
                    <input
                      value={String(contextVariableForm[item.name] ?? '')}
                      onChange={(e) => setContextVariableForm((prev) => ({ ...prev, [item.name]: e.target.value }))}
                      placeholder={item.description || item.name}
                      className="border-input bg-background text-foreground h-9 w-full rounded-md border px-2 text-sm outline-none"
                    />
                  )}
                </label>
              ))}
            </div>
            <div className="border-border flex justify-end gap-2 border-t px-4 py-3">
              <Button variant="ghost" onClick={() => setVariableDialogOpen(false)}>取消</Button>
              <Button onClick={handleConfirmVariables}>创建</Button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
