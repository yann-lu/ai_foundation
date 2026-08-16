<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Plus, Refresh, ChatLineRound, MagicStick, Promotion, VideoPause,
  Cpu, Document, Tools, CircleCheck, Close, CircleClose,
  Warning, ArrowDown, User, List, VideoPlay, Avatar,
  Timer, Search, Download, CaretBottom, CaretRight,
  Connection, Histogram, Operation, Clock,
} from '@element-plus/icons-vue'
import { pageProjects } from '@/api/project'
import { pageConversations } from "@/api/conversation"
import { createConversation, createRun, streamRunEvents, cancelRun, getMessages, getRunDetail, listRunEvents, pageRuns } from "@/api/chat"
import { parseThink } from '@/utils/think-parser'
import { renderMarkdown } from '@/utils/markdown'
import type { AgentProjectDTO, ConversationDTO, MessageDTO, RunStreamEnvelope } from '@/types/api'

// ===== Types =====
interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  reasoning?: string
  status: 'complete' | 'running' | 'error' | 'cancelled'
}

interface RunEventLog {
  eventType: RunStreamEnvelope['eventType']
  taskState: string | null
  data: unknown
  timestamp: number
}

interface PromptVariableDefinition {
  name: string
  label?: string
  type?: string
  required?: boolean
  description?: string
  defaultValue?: unknown
}

interface FlowEvent {
  id: string
  type: 'user' | 'system' | 'reasoning' | 'assistant' | 'tool_call' | 'tool_result' | 'error'
  label: string
  content: string
  timestamp: number
  toolName?: string
  toolPayload?: Record<string, unknown>
  toolResult?: string
}

type TraceEntryType =
  | 'system' | 'context' | 'user' | 'reasoning'
  | 'assistant' | 'tool_call' | 'tool_result'
  | 'summary' | 'complete' | 'error' | 'cancelled'

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
  durationMs?: number
  expanded?: boolean
}

interface TimelineStep {
  id: string
  label: string
  icon: any
  color: string
  startTime: number
  endTime: number
  content: string | null
  count?: number
}

// ===== State =====
const projects = ref<AgentProjectDTO[]>([])
const projectCode = ref('')
const conversations = ref<ConversationDTO[]>([])
const activeConvCode = ref('')
const activeConvId = ref<number | null>(null)
const loadingConv = ref(false)
const convListRef = ref<HTMLElement>()

const systemPrompt = ref('')
const userInput = ref('')
const isRunning = ref(false)
const messages = ref<ChatMessage[]>([])
const chatBodyRef = ref<HTMLElement>()

const runEventsMap = ref<Map<string, RunEventLog[]>>(new Map())
const activeTraceRunCode = ref('')
const runCodeList = computed(() => {
  return Array.from(runEventsMap.value.keys()).reverse()
})
const runList = ref<any[]>([])
const runListPage = ref(1)
const runListTotal = ref(0)
const runListLoading = ref(false)

async function loadRunList() {
  if (!activeConvCode.value) return
  runListLoading.value = true
  try {
    const res = await pageRuns(activeConvCode.value, runListPage.value, 50)
    runListTotal.value = res.data?.total || 0
    runList.value = res.data?.records || []
    // 如果还没选中任何 run，默认选第一个
    if (!activeTraceRunCode.value && runList.value.length > 0) {
      const firstRun = runList.value[0]
      await loadRunEvents(firstRun.runCode)
    }
  } finally {
    runListLoading.value = false
  }
}

async function loadRunEvents(runCode: string) {
  if (runEventsMap.value.has(runCode)) {
    activeTraceRunCode.value = runCode
    return
  }
  try {
    const res = await listRunEvents(runCode)
    const eventList: RunEventLog[] = (res.data || []).map((e: any) => ({
      eventType: e.eventType,
      data: e.eventData,
      taskState: e.taskState,
      timestamp: e.timestamp,
    }))
    runEventsMap.value.set(runCode, eventList)
    activeTraceRunCode.value = runCode
  } catch {
    // ignore
  }
}
const runEvents = computed(() => {
  if (!activeTraceRunCode.value) return []
  return runEventsMap.value.get(activeTraceRunCode.value) || []
})
const runCode = ref('')
const runStartedAt = ref<number | null>(null)
const runFinishedAt = ref<number | null>(null)
const inspectorTab = ref<'messages' | 'trace'>('messages')

// Trace view
const mainView = ref<'chat' | 'trace'>('chat')
const traceSearch = ref('')
const selectedToolId = ref<string | null>(null)
const detailTab = ref<'summary' | 'payload' | 'result' | 'timing'>('summary')

const variableDialogOpen = ref(false)
const pendingVariables = ref<PromptVariableDefinition[]>([])
const contextVariableForm = ref<Record<string, unknown>>({})

let eventSource: EventSource | null = null
let assistantMsgId = ''

// 运行状态指示（对话气泡中显示）
const currentAction = ref<'thinking' | 'tool' | 'waiting' | 'timeout'>('waiting')
const currentToolName = ref('')
const thinkingPreview = ref('')
let runTimeoutTimer: ReturnType<typeof setTimeout> | null = null
let toolRetryCount = 0
const MAX_TOOL_RETRIES = 3
const THINKING_TIMEOUT_MS = 60000
const TOOL_TIMEOUT_MS = 30000

// ===== Computed =====
const selectedProject = computed(() =>
  projects.value.find(p => p.projectCode === projectCode.value)
)

const activeConversation = computed(() =>
  conversations.value.find(c => c.conversationCode === activeConvCode.value)
)

const tokenCount = computed(() => {
  const last = [...messages.value].reverse().find(m => m.role === 'assistant')
  return last?.content.length || 0
})

const reasoningCount = computed(() => {
  return runEvents.value.filter(e => e.eventType === 'chat_reasoning').length
})

const runDuration = computed(() => {
  if (!runStartedAt.value) return '--'
  const end = runFinishedAt.value || Date.now()
  const ms = Math.max(0, end - runStartedAt.value)
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
})

const requestMessages = computed(() => {
  const ev = [...runEvents.value].reverse()
    .find(e => e.eventType === 'request_messages')
  if (!ev || !Array.isArray(ev.data)) return []
  return (ev.data as any[]).map(m => ({
    role: m.role || 'unknown',
    content: typeof m.content === 'string' ? m.content : ''
  }))
})

const rawResponseText = computed(() => {
  const complete = [...runEvents.value].reverse().find(e => e.eventType === 'run_complete')
  if (typeof complete?.data === 'string' && complete.data) return complete.data
  return runEvents.value
    .filter(e => e.eventType === 'chat_token')
    .map(e => String(e.data ?? ''))
    .join('')
})

const parsedResponse = computed(() => parseThink(rawResponseText.value))

const responseReasoning = computed(() => {
  const reasoning = runEvents.value
    .filter(e => e.eventType === 'chat_reasoning')
    .map(e => String(e.data ?? ''))
    .join('')
  return reasoning || parsedResponse.value.reasoning
})

const responseText = computed(() => parsedResponse.value.answer)

const timelineSteps = computed(() => buildTimeline(runEvents.value))

const flowEvents = computed(() => buildFlowEvents(runEvents.value))

// ===== Trace View =====
const traceEntries = computed(() => buildTraceEntries(runEvents.value))
const expandedEntryIds = ref<Set<string>>(new Set())

function isEntryExpanded(id: string): boolean {
  return expandedEntryIds.value.has(id)
}

function toggleEntryExpand(id: string) {
  if (expandedEntryIds.value.has(id)) {
    expandedEntryIds.value.delete(id)
  } else {
    expandedEntryIds.value.add(id)
  }
}

const traceStats = computed(() => {
  const entries = traceEntries.value
  const turns = entries.filter(e => e.type === 'user').length
  const toolCalls = entries.filter(e => e.type === "tool_call").length || entries.filter(e => e.type === "tool_result").length
  return { turns, toolCalls }
})

const filteredTraceEntries = computed(() => {
  const q = traceSearch.value.trim().toLowerCase()
  if (!q) return traceEntries.value
  return traceEntries.value.filter(e =>
    e.content.toLowerCase().includes(q) ||
    e.label.toLowerCase().includes(q) ||
    e.type.toLowerCase().includes(q)
  )
})

const selectedTool = computed(() => {
  if (!selectedToolId.value) return null
  return traceEntries.value.find(e => e.id === selectedToolId.value) || null
})

const toolTurnStep = computed(() => {
  if (!selectedToolId.value) return { turn: 0, step: 0 }
  let turn = 0
  let step = 0
  for (const e of traceEntries.value) {
    if (e.type === 'user') { turn++; step = 0 }
    if (e.type === 'tool_call') step++
    if (e.id === selectedToolId.value) return { turn, step }
  }
  return { turn: 0, step: 0 }
})

const timelineBars = computed(() => {
  const entries = traceEntries.value
  if (!runStartedAt.value || entries.length === 0) return { input: [], model: [], tools: [] }
  const start = runStartedAt.value
  const end = runFinishedAt.value || Date.now()
  const total = Math.max(1, end - start)
  const bars = { input: [] as any[], model: [] as any[], tools: [] as any[] }
  for (const entry of entries) {
    const s = Math.max(0, entry.timestamp - start)
    const e = entry.endTimestamp ? Math.max(s, entry.endTimestamp - start) : s + Math.min(total * 0.02, 500)
    const bar = { left: `${(s / total) * 100}%`, width: `${Math.max(0.5, ((e - s) / total) * 100)}%` }
    if (entry.type === 'user' || entry.type === 'system' || entry.type === 'context') {
      bars.input.push(bar)
    } else if (entry.type === 'reasoning' || entry.type === 'assistant' || entry.type === 'error') {
      bars.model.push(bar)
    } else if (entry.type === 'tool_call' || entry.type === 'tool_result') {
      bars.tools.push(bar)
    }
  }
  return bars
})

// ===== Functions =====
function parsePromptVariables(project?: AgentProjectDTO): PromptVariableDefinition[] {
  if (!project?.promptVariables?.trim()) return []
  try {
    const parsed = JSON.parse(project.promptVariables)
    return Array.isArray(parsed) ? parsed.filter((item: any) => item?.name) : []
  } catch {
    return []
  }
}

async function loadProjects() {
  try {
    const res = await pageProjects({ current: 1, size: 100, state: 1 })
    projects.value = res.data.records
    if (!projectCode.value && res.data.records.length > 0) {
      projectCode.value = res.data.records[0].projectCode
    }
  } catch {
    // ignore
  }
}

async function loadConversations() {
  if (!projectCode.value) {
    conversations.value = []
    return
  }
  loadingConv.value = true
  try {
    const res = await pageConversations({
      productCode: projectCode.value,
      state: 0,
      current: 1,
      size: 50,
    } as any)
    // 按置顶 + 最后消息时间倒序排序，确保最新的在最上面
    conversations.value = [...res.data.records].sort((a, b) => {
      // 置顶优先
      if ((b.isPin || 0) !== (a.isPin || 0)) {
        return (b.isPin || 0) - (a.isPin || 0)
      }
      // 按 lastMessageTime 倒序，没有时间的用 createTime
      const aTime = a.lastMessageTime ? new Date(a.lastMessageTime).getTime() : (a.createTime ? new Date(a.createTime).getTime() : 0)
      const bTime = b.lastMessageTime ? new Date(b.lastMessageTime).getTime() : (b.createTime ? new Date(b.createTime).getTime() : 0)
      return bTime - aTime
    })
    // 如果当前没有选中会话，自动选中最新的一个并加载消息
    if (!activeConvCode.value && conversations.value.length > 0) {
      handleSelectConversation(conversations.value[0])
    }
  } catch {
    conversations.value = []
  } finally {
    loadingConv.value = false
  }
}

async function handleSelectConversation(conv: ConversationDTO) {
  if (isRunning.value) return
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  activeConvCode.value = conv.conversationCode
  activeConvId.value = conv.id
  resetRunState()
  await loadConversationMessages(conv.conversationCode)
  // 加载最新 run 的事件日志（用于轨迹回放）
  await loadLatestRunEvents(conv.conversationCode)
}

async function loadLatestRunEvents(conversationCode: string) {
  try {
    // 获取会话最新一条 run
    const latestUrl = '/chat/runs/latest?conversationCode=' + encodeURIComponent(conversationCode)
    const latestRes = await fetch(latestUrl)
    if (!latestRes.ok) return
    const latestJson = await latestRes.json()
    const latestRun = latestJson?.data
    if (!latestRun || !latestRun.runCode) return
    runCode.value = latestRun.runCode
    // 加载事件日志到 Map
    const eventsRes = await listRunEvents(latestRun.runCode)
    const eventList: RunEventLog[] = (eventsRes.data || []).map((e: any) => ({
      eventType: e.eventType,
      data: e.eventData,
      taskState: e.taskState,
      timestamp: e.timestamp,
    }))
    runEventsMap.value.set(latestRun.runCode, eventList)
    activeTraceRunCode.value = latestRun.runCode
  } catch {
    // ignore
  }
}

async function loadConversationMessages(code: string) {
  try {
    const res = await getMessages(code)
    const history: ChatMessage[] = (res.data || []).map((m: MessageDTO) => {
      if (m.role === 'assistant') {
        const { reasoning, answer } = parseThink(m.content)
        return {
          id: String(m.id),
          role: 'assistant',
          content: answer,
          reasoning: reasoning || undefined,
          status: 'complete',
        }
      }
      return {
        id: String(m.id),
        role: 'user',
        content: m.content,
        status: 'complete',
      }
    })
    messages.value = history
    scrollToBottom()
  } catch {
    messages.value = []
  }
}

function resetRunState() {
  runCode.value = ''
  runStartedAt.value = null
  runFinishedAt.value = null
  currentAction.value = 'waiting'
  currentToolName.value = ''
  thinkingPreview.value = ''
  toolRetryCount = 0
  if (runTimeoutTimer) {
    clearTimeout(runTimeoutTimer)
    runTimeoutTimer = null
  }
}

async function handleNewConversation() {
  const definitions = parsePromptVariables(selectedProject.value)
  if (definitions.length > 0) {
    const values: Record<string, unknown> = {}
    definitions.forEach(item => {
      values[item.name] = item.defaultValue ?? ''
    })
    pendingVariables.value = definitions
    contextVariableForm.value = values
    variableDialogOpen.value = true
    return
  }
  await createWithVariables({})
}

async function createWithVariables(contextVariables: Record<string, unknown>) {
  if (!projectCode.value) return
  try {
    const res = await createConversation({
      productCode: projectCode.value,
      contextVariables,
      title: `Playground ${new Date().toLocaleString('zh-CN', {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      })}`,
    })
    activeConvCode.value = res.data.conversationCode
    activeConvId.value = res.data.id
    messages.value = []
    resetRunState()
    loadConversations()
    scrollToBottom()
  } catch (e) {
    // request layer reports error
  }
}

async function handleConfirmVariables() {
  const values: Record<string, unknown> = {}
  for (const item of pendingVariables.value) {
    const raw = contextVariableForm.value[item.name]
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
  variableDialogOpen.value = false
  await createWithVariables(values)
}

async function handlePromotion() {
  const text = userInput.value.trim()
  if (!text || !activeConvCode.value || isRunning.value) return

  userInput.value = ''

  const userMsg: ChatMessage = {
    id: `u_${Date.now()}`,
    role: 'user',
    content: text,
    status: 'complete',
  }
  assistantMsgId = `a_${Date.now()}`
  const assistantMsg: ChatMessage = {
    id: assistantMsgId,
    role: 'assistant',
    content: '',
    reasoning: '',
    status: 'running',
  }
  messages.value.push(userMsg, assistantMsg)
  scrollToBottom()

  isRunning.value = true
  resetRunState()
  currentAction.value = 'waiting'
  currentToolName.value = ''
  thinkingPreview.value = ''
  runStartedAt.value = Date.now()

  let rawBuffer = ''
  let reasoningBuffer = ''
  // 运行状态变量已在顶层声明

  try {
    const createRes = await createRun({
      conversationCode: activeConvCode.value,
      userMessage: text,
      systemPrompt: systemPrompt.value.trim() || undefined,
    })
    runCode.value = createRes.data.runCode

    await new Promise<void>((resolve, reject) => {
      const source = streamRunEvents(createRes.data.runCode)
      eventSource = source

      source.onmessage = (event) => {
        let env: RunStreamEnvelope
        try {
          env = JSON.parse(event.data) as RunStreamEnvelope
        } catch {
          return
        }

        const currentRunCode = runCode.value
        if (currentRunCode) {
          if (!runEventsMap.value.has(currentRunCode)) {
            runEventsMap.value.set(currentRunCode, [])
          }
          runEventsMap.value.get(currentRunCode)!.push({
            eventType: env.eventType,
            taskState: env.taskState,
            data: env.data,
            timestamp: env.timestamp || Date.now(),
          })
          // 新对话进行中时，默认看当前 run 的轨迹
          if (activeTraceRunCode.value !== currentRunCode) {
            activeTraceRunCode.value = currentRunCode
          }
        }

        if (env.eventType === 'chat_token') {
          rawBuffer += String(env.data ?? '')
          const parsed = parseThink(rawBuffer)
          updateAssistantMessage(assistantMsgId, {
            content: parsed.answer,
            reasoning: parsed.reasoning || undefined,
          })
          scrollToBottom()
        } else if (env.eventType === 'chat_reasoning') {
          reasoningBuffer += String(env.data ?? '')
          // 更新思考预览
          if (reasoningBuffer.length > thinkingPreview.value.length) {
            thinkingPreview.value = reasoningBuffer.slice(0, 150)
          }
          currentAction.value = 'thinking'
          // 收到思考内容，重置思考超时
          scheduleThinkingTimeout()
        } else if (env.eventType === 'tool_call') {
          const tc = env.data as Record<string, unknown> | null
          currentToolName.value = tc && typeof tc.name === 'string' ? tc.name : ''
          currentAction.value = 'tool'
          toolRetryCount = 0
          // 工具调用超时保护
          scheduleToolTimeout()
        } else if (env.eventType === 'tool_result') {
          currentAction.value = 'thinking'
          currentToolName.value = ''
          toolRetryCount = 0
          scheduleThinkingTimeout()
        } else if (env.eventType === 'run_complete') {
          runFinishedAt.value = Date.now()
          // Try multiple sources for the final reply
          let finalRaw = ''
          if (typeof env.data === 'string' && env.data.trim()) {
            finalRaw = env.data
          } else if (env.data && typeof env.data === 'object') {
            const d = env.data as Record<string, unknown>
            finalRaw = String(((d.reply ?? d.content ?? d.text) as string) || rawBuffer || '')
          } else {
            finalRaw = rawBuffer
          }
          // If still empty, try to extract answer from reasoning buffer (some models put answer in thinking)
          if (!finalRaw.trim() && reasoningBuffer.trim()) {
            finalRaw = reasoningBuffer
          }
          const finalParsed = parseThink(finalRaw)
          // If answer is empty but reasoning has content, use the last portion as answer
          let answerContent = finalParsed.answer
          if (!answerContent.trim() && finalParsed.reasoning.trim()) {
            // Try to find non-thinking content after the last </think> or use all
            const lastClose = finalRaw.lastIndexOf('</think>')
            if (lastClose !== -1) {
              answerContent = finalRaw.slice(lastClose + '</think>'.length).trim()
            }
          }
          const combinedReasoning = finalParsed.reasoning || reasoningBuffer || ''
          // ReAct 模式下，有些模型会把最终回复也放在 reasoning 里
          // 如果 answer 为空但 reasoning 有内容，直接用 reasoning 作为回答
          if (!answerContent.trim() && combinedReasoning.trim()) {
            answerContent = combinedReasoning
          }
          updateAssistantMessage(assistantMsgId, {
            content: answerContent,
            reasoning: finalParsed.reasoning ? combinedReasoning : undefined,
            status: 'complete',
          })
          source.close()
          isRunning.value = false
          eventSource = null
          currentAction.value = 'waiting'
          currentToolName.value = ''
          thinkingPreview.value = ''
          clearRunTimeout()
          scrollToBottom()
          resolve()
        } else if (env.eventType === 'run_error') {
          runFinishedAt.value = Date.now()
          updateAssistantMessage(assistantMsgId, {
            content: `错误: ${String(env.data ?? '执行失败')}`,
            status: 'error',
          })
          source.close()
          isRunning.value = false
          eventSource = null
          currentAction.value = 'waiting'
          currentToolName.value = ''
          thinkingPreview.value = ''
          clearRunTimeout()
          ElMessage.error(String(env.data ?? '执行失败'))
          reject(new Error(String(env.data ?? '执行失败')))
        } else if (env.eventType === 'run_cancelled') {
          runFinishedAt.value = Date.now()
          const finalRaw = typeof env.data === 'string' ? env.data : rawBuffer
          const finalParsed = parseThink(finalRaw)
          updateAssistantMessage(assistantMsgId, {
            content: finalParsed.answer,
            reasoning: finalParsed.reasoning || reasoningBuffer || undefined,
            status: 'cancelled',
          })
          source.close()
          isRunning.value = false
          eventSource = null
          resolve()
        }
      }

      source.onerror = async () => {
        clearRunTimeout()
        runFinishedAt.value = Date.now()
        // 连接断开时，尝试从详情接口拉取最终结果
        try {
          const res = await getRunDetail(runCode.value)
          const detail = res?.data || res
          if (detail?.reply) {
            const finalParsed = parseThink(detail.reply)
            const reasoning = detail.reasoning || finalParsed.reasoning || reasoningBuffer || ''
            const answer = finalParsed.answer || detail.reply
            updateAssistantMessage(assistantMsgId, {
              content: answer,
              reasoning: reasoning || undefined,
              status: 'complete',
            })
            source.close()
            isRunning.value = false
            currentAction.value = 'waiting'
            eventSource = null
            resolve()
            return
          }
        } catch {
          // fallback to error handling below
        }
        updateAssistantMessage(assistantMsgId, {
          content: 'SSE 连接中断',
          status: 'error',
        })
        source.close()
        isRunning.value = false
        currentAction.value = 'waiting'
        eventSource = null
        reject(new Error('SSE 连接中断'))
      }
    })
  } catch (e) {
    isRunning.value = false
    eventSource = null
    const errMsg = e instanceof Error ? e.message : '未知错误'
    updateAssistantMessage(assistantMsgId, {
      content: `请求失败: ${errMsg}`,
      status: 'error',
    })
  }

  loadConversations()
}

function updateAssistantMessage(id: string, updates: Partial<ChatMessage>) {
  const idx = messages.value.findIndex(m => m.id === id)
  if (idx !== -1) {
    messages.value[idx] = { ...messages.value[idx], ...updates }
  }
}

// ========== 超时与重试 ==========

function clearRunTimeout() {
  if (runTimeoutTimer) {
    clearTimeout(runTimeoutTimer)
    runTimeoutTimer = null
  }
}

// 思考阶段超时：60 秒没收到新内容则尝试从详情接口拉取
function scheduleThinkingTimeout() {
  clearRunTimeout()
  runTimeoutTimer = setTimeout(() => {
    if (isRunning.value && currentAction.value === 'thinking') {
      tryFetchRunDetail()
    }
  }, THINKING_TIMEOUT_MS)
}

// 工具调用超时：30 秒没返回，最多重试 3 次，全部失败则放弃该工具
function scheduleToolTimeout() {
  clearRunTimeout()
  runTimeoutTimer = setTimeout(() => {
    if (!isRunning.value || currentAction.value !== 'tool') return

    toolRetryCount++
    if (toolRetryCount < MAX_TOOL_RETRIES) {
      // 显示重试提示
      thinkingPreview.value = `工具调用超时，正在重试 (${toolRetryCount}/${MAX_TOOL_RETRIES})...`
      currentAction.value = 'thinking'
      // 短暂显示后切回 tool 状态，继续等待
      setTimeout(() => {
        if (isRunning.value) {
          currentAction.value = 'tool'
          scheduleToolTimeout()
        }
      }, 1500)
    } else {
      // 重试 3 次全部失败，放弃该工具，尝试从详情接口拉取
      thinkingPreview.value = '工具调用失败（已重试 3 次），继续处理...'
      currentAction.value = 'timeout'
      clearRunTimeout()
      // 尝试从详情接口兜底
      setTimeout(() => {
        if (isRunning.value) {
          tryFetchRunDetail()
        }
      }, 2000)
    }
  }, TOOL_TIMEOUT_MS)
}

// 超时兜底：从详情接口拉取最终结果
async function tryFetchRunDetail() {
  if (!runCode.value || !isRunning.value) return
  try {
    const res = await getRunDetail(runCode.value)
    const detail = res?.data || res
    const replyText = detail?.reply || ''
    const reasoningText = detail?.reasoning || ''
    
    if (replyText || reasoningText) {
      const finalParsed = parseThink(replyText || reasoningText)
      const answer = finalParsed.answer || replyText || ''
      const reasoning = reasoningText || finalParsed.reasoning || ''
      updateAssistantMessage(assistantMsgId, {
        content: answer || reasoning,
        reasoning: reasoning || undefined,
        status: answer ? 'complete' : 'running',
      })
      if (answer) {
        isRunning.value = false
        currentAction.value = 'waiting'
        runFinishedAt.value = Date.now()
        clearRunTimeout()
        if (eventSource) {
          eventSource.close()
          eventSource = null
        }
      } else {
        // 有思考但无最终答案，继续等
        scheduleThinkingTimeout()
      }
    } else {
      // 详情也没结果，继续轮询一次
      scheduleThinkingTimeout()
    }
  } catch {
    // 详情接口失败，继续等
    scheduleThinkingTimeout()
  }
}

async function handleCancel() {
  if (!runCode.value) return
  try {
    await cancelRun(runCode.value)
  } catch {
    // ignore
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (chatBodyRef.value) {
      chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
    }
  })
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

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

// ===== Flow Events (完整流水) =====
function buildFlowEvents(events: RunEventLog[]): FlowEvent[] {
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
        // 如果之前有 token 输出，先结束那段回复
        if (tokenBuf) {
          flushAssistant(ev.timestamp)
        }
        reasoningBuf += String(ev.data ?? '')
        break

      case 'chat_token':
        // 如果之前有思考，先结束那段思考
        if (reasoningBuf) {
          flushReasoning(ev.timestamp)
        }
        tokenBuf += String(ev.data ?? '')
        break

      case 'tool_call':
        flushReasoning(ev.timestamp)
        flushAssistant(ev.timestamp)
        const callData = ev.data as any
        flow.push({
          id: `tool_call_${ev.timestamp}`,
          type: 'tool_call',
          label: '调用工具',
          content: callData?.name || 'unknown',
          timestamp: ev.timestamp,
          toolName: callData?.name,
          toolPayload: callData?.arguments || {},
        })
        break

      case 'tool_result':
        const resultData = ev.data as any
        flow.push({
          id: `tool_result_${ev.timestamp}`,
          type: 'tool_result',
          label: '工具结果',
          content: typeof resultData?.result === 'string' ? resultData.result : JSON.stringify(ev.data),
          timestamp: ev.timestamp,
          toolName: resultData?.toolName,
          toolResult: typeof resultData?.result === 'string' ? resultData.result : JSON.stringify(ev.data),
        })
        break

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

  // 收尾
  const lastTs = events.length > 0 ? events[events.length - 1].timestamp : Date.now()
  flushReasoning(lastTs)
  flushAssistant(lastTs)

  return flow
}

// ===== Trace Entries (full chronological flow) =====
function buildTraceEntries(events: RunEventLog[]): TraceEntry[] {
  const entries: TraceEntry[] = []
  if (events.length === 0) return entries

  // System & context from request_messages
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
        // Context messages (runtime context, skills, etc.)
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
        let result = ""
        if (tr && tr.result != null && String(tr.result).trim() !== "") {
          result = String(tr.result)
        } else if (tr && typeof tr === "object") {
          result = JSON.stringify(tr, null, 2)
        } else {
          result = String(ev.data ?? "")
        }
        // Find last unmatched tool call
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

function getTraceIcon(type: TraceEntryType) {
  const map: Record<TraceEntryType, any> = {
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

function getTraceBadgeLabel(type: TraceEntryType): string {
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

function truncateArgs(args: string): string {
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

function isLongContent(entry: TraceEntry): boolean {
  return entry.content.length > 200
}

const detailTabs = ['summary', 'payload', 'result', 'timing'] as const

// ===== Timeline =====
function buildTimeline(events: RunEventLog[]): TimelineStep[] {
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

// ===== Watch =====
watch(projectCode, () => {
  activeConvCode.value = ''
  activeConvId.value = null
  messages.value = []
  resetRunState()
  loadConversations()
})

// ===== Lifecycle =====
onMounted(async () => {
  await loadProjects()
  await loadConversations()
})

onBeforeUnmount(() => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
})
</script>

<template>
  <div class="pg-shell">
    <!-- Sidebar -->
    <aside class="pg-sidebar">
      <div class="pg-sidebar-head">
        <div class="pg-sidebar-logo">
          <el-icon class="logo-icon"><MagicStick /></el-icon>
          <span>Playground</span>
        </div>
      </div>

      <div class="pg-sidebar-section">
        <label class="pg-label">项目</label>
        <el-select
          v-model="projectCode"
          placeholder="选择项目"
          size="default"
          style="width: 100%;"
        >
          <el-option v-if="projects.length === 0" value="" label="无可用项目" disabled />
          <el-option
            v-for="p in projects"
            :key="p.projectCode"
            :label="p.projectName"
            :value="p.projectCode"
          />
        </el-select>
      </div>

      <div class="pg-sidebar-section conv-section">
        <div class="conv-header">
          <span class="pg-label">会话 ({{ conversations.length }})</span>
          <div class="conv-actions">
            <el-icon
              class="icon-btn"
              :class="{ 'is-spinning': loadingConv }"
              title="刷新"
              @click="loadConversations"
            >
              <Refresh />
            </el-icon>
            <el-button
              size="small"
              type="primary"
              :icon="Plus"
              :disabled="!projectCode"
              @click="handleNewConversation"
            >
              新建
            </el-button>
          </div>
        </div>
      </div>

      <div class="pg-conv-list" ref="convListRef">
        <div v-if="conversations.length === 0" class="pg-empty">
          {{ projectCode ? '暂无会话，点击「新建」创建' : '请先选择项目' }}
        </div>
        <button
          v-for="conv in conversations"
          :key="conv.id"
          :class="['pg-conv-item', { active: activeConvCode === conv.conversationCode }]"
          @click="handleSelectConversation(conv)"
        >
          <span class="conv-title">{{ conv.title || conv.conversationCode }}</span>
          <span class="conv-meta">
            <el-icon class="conv-meta-icon"><ChatLineRound /></el-icon>
            {{ conv.modelName || '--' }}
            <span v-if="conv.lastMessageTime"> · {{ conv.lastMessageTime.replace('T', ' ') }}</span>
          </span>
        </button>
      </div>
    </aside>

    <!-- Chat -->
    <main class="pg-chat">
      <header class="pg-chat-header">
        <div class="pg-chat-title">
          <span class="project-name">{{ selectedProject?.projectName || projectCode || '未选择项目' }}</span>
          <template v-if="activeConvCode">
            <span class="divider">/</span>
            <span class="conv-code mono">{{ activeConvCode }}</span>
          </template>
        </div>
        <div class="pg-chat-actions">
          <el-button
            v-if="!activeConvCode"
            size="small"
            type="primary"
            :icon="Plus"
            :disabled="!projectCode"
            @click="handleNewConversation"
          >
            新建会话
          </el-button>
        </div>
      </header>

      <!-- View Tabs -->
      <div class="pg-view-tabs-bar">
        <button :class="['pg-view-tab-btn', { active: mainView === 'chat' }]" @click="mainView = 'chat'">
          <el-icon><ChatLineRound /></el-icon>
          对话
        </button>
        <button :class="['pg-view-tab-btn', { active: mainView === 'trace' }]" @click="mainView = 'trace'">
          <el-icon><Histogram /></el-icon>
          轨迹
        </button>
      </div>

      <template v-if="mainView === 'chat'">
        <div class="pg-system-prompt">
          <span class="sp-label">SYSTEM PROMPT</span>
          <input
            v-model="systemPrompt"
            placeholder="可选。临时覆盖本次 Run 的系统提示词。留空使用项目默认。"
            class="sp-input"
          />
        </div>

        <div class="pg-chat-body" ref="chatBodyRef">
        <template v-if="activeConvCode">
          <div v-if="messages.length === 0" class="pg-empty-state">
            <div class="empty-icon">
              <el-icon><MagicStick /></el-icon>
            </div>
            <p>输入消息开始对话</p>
          </div>

          <div v-for="msg in messages" :key="msg.id" :class="['pg-msg', msg.role]">
            <div class="msg-avatar">
              <el-icon v-if="msg.role === 'user'"><User /></el-icon>
              <el-icon v-else><Avatar /></el-icon>
            </div>
            <div class="msg-bubble-wrap">
              <div v-if="msg.reasoning" class="msg-reasoning">
                <div class="reasoning-label">
                  <el-icon><Cpu /></el-icon>
                  思考过程
                </div>
                <div class="reasoning-content">{{ msg.reasoning }}</div>
              </div>
              <div :class="['msg-bubble', { 'is-error': msg.status === 'error', 'is-running': msg.status === 'running' }]">
                <div v-if="msg.role === 'assistant' && msg.content" v-html="renderMarkdown(msg.content)"></div>
                <template v-else-if="msg.role === 'user'">{{ msg.content }}</template>
                <!-- Running status indicator -->
                <div v-if="msg.status === 'running' && msg.role === 'assistant'" class="msg-status">
                  <template v-if="currentAction === 'thinking'">
                    <div class="status-icon thinking"><el-icon><Cpu /></el-icon></div>
                    <div class="status-text">
                      <span class="status-label">正在思考</span>
                      <div v-if="thinkingPreview" class="status-preview">{{ thinkingPreview }}...</div>
                    </div>
                  </template>
                  <template v-else-if="currentAction === 'tool'">
                    <div class="status-icon tool"><el-icon><Tools /></el-icon></div>
                    <div class="status-text">
                      <span class="status-label">正在调用工具</span>
                      <div class="status-preview tool-name">{{ currentToolName }}</div>
                    </div>
                  </template>
                  <template v-else-if="currentAction === 'timeout'">
                    <div class="status-icon timeout"><el-icon><Warning /></el-icon></div>
                    <div class="status-text">
                      <span class="status-label">处理中</span>
                      <div v-if="thinkingPreview" class="status-preview">{{ thinkingPreview }}</div>
                    </div>
                  </template>
                  <template v-else>
                    <div class="status-icon waiting"><el-icon><MagicStick /></el-icon></div>
                    <div class="status-text">
                      <span class="status-label">准备中</span>
                    </div>
                  </template>
                </div>
                <span v-if="msg.status === 'running' && currentAction === 'waiting'" class="typing-dot"></span>
              </div>
            </div>
          </div>
        </template>

        <div v-else class="pg-empty-state">
          <div class="empty-icon primary">
            <el-icon><Plus /></el-icon>
          </div>
          <p>选择历史会话或点击「新建会话」开始对话</p>
        </div>
      </div>

      <div class="pg-composer">
        <div class="composer-input-wrap">
          <textarea
            v-model="userInput"
            :placeholder="activeConvCode ? '输入消息，Enter 发送，Shift+Enter 换行' : '请先选择或创建会话'"
            :disabled="!activeConvCode || isRunning"
            class="composer-textarea"
            @keydown.enter.exact.prevent="handlePromotion"
          ></textarea>
          <div class="composer-actions">
            <el-button
              v-if="isRunning"
              type="danger"
              :icon="VideoPause"
              size="small"
              @click="handleCancel"
            >
              停止
            </el-button>
            <el-button
              v-else
              type="primary"
              :icon="Promotion"
              size="small"
              :disabled="!activeConvCode || !userInput.trim()"
              @click="handlePromotion"
            >
              发送
            </el-button>
          </div>
        </div>
      </div>
      </template>

      <!-- Trace View -->
      <template v-else>
        <div class="pg-trace-view">
          <div class="trace-split">
            <!-- Run 列表 -->
            <div class="trace-run-list">
              <div class="trace-run-list-header">运行记录</div>
              <div v-loading="runListLoading" class="trace-run-list-body">
                <div
                  v-for="run in runList"
                  :key="run.runCode"
                  :class="['trace-run-item', { active: activeTraceRunCode === run.runCode }]"
                  @click="loadRunEvents(run.runCode)"
                >
                  <div class="trace-run-item-title">
                    <span :class="['trace-run-state', `state-${run.taskState}`]"></span>
                    <span class="trace-run-code">{{ run.runCode.slice(0, 16) }}</span>
                  </div>
                  <div class="trace-run-item-meta">
                    <span>{{ run.taskState }}</span>
                    <span>{{ formatTime(run.createTime) }}</span>
                  </div>
                </div>
                <div v-if="!runListLoading && runList.length === 0" class="trace-run-empty">
                  暂无运行记录
                </div>
              </div>
            </div>
            <!-- 轨迹详情 -->
            <div class="trace-detail">
          <!-- Trace Stats Bar -->
          <div class="trace-stats-bar">
            <div class="trace-stat">
              <el-icon class="trace-stat-icon"><Timer /></el-icon>
              <span class="trace-stat-label">Duration</span>
              <span class="trace-stat-value mono">{{ runDuration }}</span>
            </div>
            <div class="trace-stat-divider"></div>
            <div class="trace-stat">
              <el-icon class="trace-stat-icon"><ChatLineRound /></el-icon>
              <span class="trace-stat-label">Turns</span>
              <span class="trace-stat-value mono">{{ traceStats.turns }}</span>
            </div>
            <div class="trace-stat-divider"></div>
            <div class="trace-stat">
              <el-icon class="trace-stat-icon"><Tools /></el-icon>
              <span class="trace-stat-label">Calls</span>
              <span class="trace-stat-value mono">{{ traceStats.toolCalls }}</span>
            </div>
            <div class="trace-stat-actions">
              <div class="trace-search">
                <el-icon class="trace-search-icon"><Search /></el-icon>
                <input
                  v-model="traceSearch"
                  placeholder="搜索..."
                  class="trace-search-input"
                />
              </div>
              <button class="trace-export-btn" title="导出">
                <el-icon><Download /></el-icon>
                Export
              </button>
            </div>
          </div>

          <!-- Timeline Bar Chart -->
          <div class="trace-timeline-bar">
            <div class="tl-row">
              <span class="tl-label">Input</span>
              <div class="tl-track">
                <div v-for="(b, i) in timelineBars.input" :key="i" class="tl-bar tl-input" :style="{ left: b.left, width: b.width }"></div>
              </div>
            </div>
            <div class="tl-row">
              <span class="tl-label">Model</span>
              <div class="tl-track">
                <div v-for="(b, i) in timelineBars.model" :key="i" class="tl-bar tl-model" :style="{ left: b.left, width: b.width }"></div>
              </div>
            </div>
            <div class="tl-row">
              <span class="tl-label">Tools</span>
              <div class="tl-track">
                <div v-for="(b, i) in timelineBars.tools" :key="i" class="tl-bar tl-tools" :style="{ left: b.left, width: b.width }"></div>
              </div>
            </div>
          </div>

          <!-- Trace Content: list + detail -->
          <div class="trace-content">
            <div class="trace-list">
              <div v-if="filteredTraceEntries.length === 0" class="trace-empty">
                <el-icon class="trace-empty-icon"><Connection /></el-icon>
                <p>发送消息后，完整执行轨迹将显示在这里</p>
              </div>
              <div v-else class="trace-list-inner">
                <div
                  v-for="entry in filteredTraceEntries"
                  :key="entry.id"
                  :class="['trace-entry', 'trace-' + entry.type, { 'is-selected': selectedToolId === entry.id }]"
                  @click="entry.type === 'tool_call' && (selectedToolId = entry.id)"
                >
                  <div class="trace-entry-dot">
                    <el-icon :class="['trace-entry-icon', 'icon-' + entry.type]">
                      <component :is="getTraceIcon(entry.type)" />
                    </el-icon>
                  </div>
                  <div class="trace-entry-body">
                    <div class="trace-entry-head">
                      <span :class="['trace-entry-badge', 'badge-' + entry.type]">
                        {{ getTraceBadgeLabel(entry.type) }}
                      </span>
                      <template v-if="entry.type === 'tool_call'">
                        <span class="trace-entry-tool-name">{{ entry.toolName }}</span>
                      </template>
                      <span class="trace-entry-time mono">{{ formatTime(entry.timestamp) }}</span>
                    </div>
                    <div class="trace-entry-content">
                      <!-- Tool call with result -->
                      <template v-if="entry.type === 'tool_call' && entry.toolResult">
                        <div v-if="!isEntryExpanded(entry.id)" @click.stop="toggleEntryExpand(entry.id)" class="trace-entry-preview">
                          <div class="trace-args-preview">
                            <span class="trace-preview-label">Payload</span>
                            <code>{{ truncateArgs(entry.toolArgs || '') }}</code>
                          </div>
                          <div class="trace-result-preview">
                            <span class="trace-preview-label result">→ Result</span>
                            <span>{{ (entry.toolResult || '').slice(0, 80) }}…</span>
                          </div>
                          <div class="trace-expand-hint">
                            <el-icon><CaretBottom /></el-icon>
                            展开查看完整内容
                          </div>
                        </div>
                        <div v-else @click.stop="toggleEntryExpand(entry.id)" class="trace-entry-expanded">
                          <div class="trace-expand-section">
                            <div class="trace-expand-title">Payload</div>
                            <pre class="trace-expand-pre">{{ entry.toolArgs || '{}' }}</pre>
                          </div>
                          <div class="trace-expand-section">
                            <div class="trace-expand-title result">Result</div>
                            <pre class="trace-expand-pre result-pre">{{ entry.toolResult }}</pre>
                          </div>
                          <div class="trace-collapse-hint">
                            <el-icon><CaretRight /></el-icon>
                            收起
                          </div>
                        </div>
                      </template>
                      <!-- Tool call without result -->
                      <template v-else-if="entry.type === 'tool_call'">
                        <div v-if="!isEntryExpanded(entry.id)" @click.stop="toggleEntryExpand(entry.id)" class="trace-entry-preview">
                          <code>{{ truncateArgs(entry.toolArgs || '') }}</code>
                          <div class="trace-expand-hint">
                            <el-icon><CaretBottom /></el-icon>
                            展开查看完整内容
                          </div>
                        </div>
                        <div v-else @click.stop="toggleEntryExpand(entry.id)" class="trace-entry-expanded">
                          <pre class="trace-expand-pre">{{ entry.toolArgs || '{}' }}</pre>
                          <div class="trace-collapse-hint">
                            <el-icon><CaretRight /></el-icon>
                            收起
                          </div>
                        </div>
                      </template>
                      <!-- Other entries -->
                      <template v-else>
                        <div v-if="isLongContent(entry) && !isEntryExpanded(entry.id)" @click.stop="toggleEntryExpand(entry.id)" class="trace-entry-preview">
                          <span>{{ entry.content.slice(0, 150) }}…</span>
                          <div class="trace-expand-hint">
                            <el-icon><CaretBottom /></el-icon>
                            展开查看完整内容
                          </div>
                        </div>
                        <div v-else-if="isEntryExpanded(entry.id)" @click.stop="toggleEntryExpand(entry.id)">
                          <pre class="trace-entry-pre">{{ entry.content }}</pre>
                          <div class="trace-collapse-hint">
                            <el-icon><CaretRight /></el-icon>
                            收起
                          </div>
                        </div>
                        <pre v-else class="trace-entry-pre">{{ entry.content }}</pre>
                      </template>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Tool Detail Panel -->
            <div v-if="selectedTool && selectedTool.type === 'tool_call'" class="trace-detail">
              <div class="trace-detail-head">
                <span class="trace-detail-badge">TOOL</span>
                <span class="trace-detail-title">Turn {{ toolTurnStep.turn }} · Step {{ toolTurnStep.step }}</span>
                <button class="trace-detail-close" @click="selectedToolId = null">
                  <el-icon><Close /></el-icon>
                </button>
              </div>
              <div class="trace-detail-tabs">
                <button
                  v-for="tab in detailTabs"
                  :key="tab"
                  :class="['detail-tab', { active: detailTab === tab }]"
                  @click="detailTab = tab"
                >
                  {{ tab === 'summary' ? 'Summary' : tab === 'payload' ? 'Payload' : tab === 'result' ? 'Result' : 'Timing' }}
                </button>
              </div>
              <div class="trace-detail-body">
                <template v-if="detailTab === 'summary'">
                  <div class="detail-row">
                    <span class="detail-label">Hierarchy</span>
                    <span class="detail-value">Assistant Message → Tool Call</span>
                  </div>
                  <div class="detail-row">
                    <span class="detail-label">Status</span>
                    <span class="detail-value status-completed">Completed</span>
                  </div>
                  <div class="detail-row">
                    <span class="detail-label">Tool Name</span>
                    <span class="detail-value mono">{{ selectedTool.toolName }}</span>
                  </div>
                </template>
                <template v-else-if="detailTab === 'payload'">
                  <div class="detail-section-title">Payload</div>
                  <pre class="detail-pre">{{ selectedTool.toolArgs || '{}' }}</pre>
                </template>
                <template v-else-if="detailTab === 'result'">
                  <div class="detail-section-title">Result</div>
                  <pre class="detail-pre">{{ selectedTool.toolResult || 'No result' }}</pre>
                </template>
                <template v-else-if="detailTab === 'timing'">
                  <div class="detail-row">
                    <span class="detail-label">Started</span>
                    <span class="detail-value mono">{{ formatTime(selectedTool.timestamp) }}</span>
                  </div>
                  <div v-if="selectedTool.toolResultTimestamp" class="detail-row">
                    <span class="detail-label">Ended</span>
                    <span class="detail-value mono">{{ formatTime(selectedTool.toolResultTimestamp) }}</span>
                  </div>
                  <div class="detail-row">
                    <span class="detail-label">Duration</span>
                    <span class="detail-value mono duration-value">
                      {{ selectedTool.durationMs ? formatDuration(selectedTool.durationMs) : 'N/A' }}
                    </span>
                  </div>
                  <div class="detail-row">
                    <span class="detail-label">Timing Source</span>
                    <span class="detail-value muted">Session timestamps</span>
                  </div>
                </template>
              </div>
            </div>
          </div>
        </div>
      </template>
    </main>



    <!-- Variable Dialog -->
    <div v-if="variableDialogOpen" class="pg-dialog-mask" @click.self="variableDialogOpen = false">
      <div class="pg-dialog">
        <div class="pg-dialog-head">
          <h2>创建会话变量</h2>
        </div>
        <div class="pg-dialog-body">
          <div v-for="item in pendingVariables" :key="item.name" class="form-item">
            <label class="form-label">
              {{ item.label || item.name }}
              <span v-if="item.required" class="required">*</span>
            </label>
            <select
              v-if="item.type === 'boolean'"
              :value="String(contextVariableForm[item.name] ?? '')"
              @change="(e: any) => contextVariableForm[item.name] = e.target.value"
              class="form-select"
            >
              <option value="">请选择</option>
              <option value="true">true</option>
              <option value="false">false</option>
            </select>
            <input
              v-else
              :value="String(contextVariableForm[item.name] ?? '')"
              @input="(e: any) => contextVariableForm[item.name] = e.target.value"
              :placeholder="item.description || item.name"
              class="form-input"
            />
          </div>
        </div>
        <div class="pg-dialog-foot">
          <el-button @click="variableDialogOpen = false">取消</el-button>
          <el-button type="primary" @click="handleConfirmVariables">创建</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pg-shell {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  height: calc(100vh - 56px - 40px);
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-lg);
  overflow: hidden;
}

/* ===== Sidebar ===== */
.pg-sidebar {
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--c-border);
  background: var(--c-surface);
  min-height: 0;
}

.pg-sidebar-head {
  padding: 14px 16px;
  border-bottom: 1px solid var(--c-border);
}

.pg-sidebar-logo {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 14px;
  color: var(--c-text-1);
}

.logo-icon {
  width: 28px;
  height: 28px;
  background: var(--c-primary-soft);
  color: var(--c-primary);
  border-radius: 7px;
  padding: 5px;
  font-size: 18px;
}

.pg-sidebar-section {
  padding: 12px 16px;
  border-bottom: 1px solid var(--c-border);
}

.pg-label {
  display: block;
  font-size: 11px;
  font-weight: 600;
  color: var(--c-text-3);
  text-transform: uppercase;
  letter-spacing: 0.02em;
  margin-bottom: 8px;
}

.conv-section {
  padding-bottom: 8px;
}

.conv-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.conv-header .pg-label {
  margin-bottom: 0;
}

.conv-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.icon-btn {
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  color: var(--c-text-3);
  font-size: 14px;
  transition: all 0.15s ease;
}

.icon-btn:hover {
  color: var(--c-text-1);
  background: var(--c-surface-hover);
}

.icon-btn.is-spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.pg-conv-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 4px 0;
}

.pg-empty {
  padding: 24px 16px;
  text-align: center;
  font-size: 12px;
  color: var(--c-text-3);
}

.pg-conv-item {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 16px;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
  border-left: 2px solid transparent;
  transition: all 0.15s ease;
}

.pg-conv-item:hover {
  background: var(--c-surface-hover);
}

.pg-conv-item.active {
  background: var(--c-primary-bg);
  border-left-color: var(--c-primary);
}

.conv-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--c-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pg-conv-item.active .conv-title {
  color: var(--c-primary);
  font-weight: 600;
}

.conv-meta {
  font-size: 11px;
  color: var(--c-text-3);
  display: flex;
  align-items: center;
  gap: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-meta-icon {
  font-size: 11px;
}

/* ===== Chat ===== */
.pg-chat {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: var(--c-bg);
}

.pg-chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-bottom: 1px solid var(--c-border);
  background: var(--c-surface);
  flex-shrink: 0;
}

.pg-chat-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.project-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text-1);
}

.divider {
  color: var(--c-border-strong);
}

.conv-code {
  font-size: 12px;
  color: var(--c-text-3);
}

.mono {
  font-family: var(--font-mono);
}

.pg-system-prompt {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 20px;
  border-bottom: 1px solid var(--c-border);
  background: var(--c-surface);
  flex-shrink: 0;
}

.sp-label {
  font-size: 10px;
  font-weight: 700;
  color: var(--c-primary);
  letter-spacing: 0.05em;
  flex-shrink: 0;
}

.sp-input {
  flex: 1;
  height: 30px;
  border: none;
  background: transparent;
  font-size: 12px;
  color: var(--c-text-1);
  outline: none;
  padding: 0 8px;
  border-radius: 4px;
  transition: background 0.15s ease;
  font-family: var(--font-sans);
}

.sp-input::placeholder {
  color: var(--c-text-3);
  opacity: 0.6;
}

.sp-input:focus {
  background: var(--c-surface-hover);
}

.pg-chat-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 24px 28px;
}

.pg-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 12px;
  color: var(--c-text-3);
  font-size: 13px;
}

.empty-icon {
  width: 56px;
  height: 56px;
  background: var(--c-surface-hover);
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: var(--c-text-3);
}

.empty-icon.primary {
  background: var(--c-primary-soft);
  color: var(--c-primary);
}

.pg-msg {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  max-width: 85%;
}

.pg-msg.user {
  flex-direction: row-reverse;
  margin-left: auto;
}

.msg-avatar {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
}

.pg-msg.user .msg-avatar {
  background: var(--c-primary);
  color: white;
}

.pg-msg.assistant .msg-avatar {
  background: var(--c-surface);
  color: var(--c-primary);
  border: 1px solid var(--c-border);
}

.msg-bubble-wrap {
  min-width: 0;
}

.msg-reasoning {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 10px 10px 2px 10px;
  padding: 10px 12px;
  margin-bottom: 6px;
  max-width: 100%;
}

.reasoning-label {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  font-weight: 600;
  color: #8b5cf6;
  margin-bottom: 6px;
}

.reasoning-content {
  font-size: 12px;
  line-height: 1.6;
  color: var(--c-text-2);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
  font-family: var(--font-mono);
}

.msg-bubble {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 10px;
  padding: 10px 14px;
  font-size: 13px;
  line-height: 1.65;
  color: var(--c-text-1);
  word-break: break-word;
  position: relative;
}

.pg-msg.user .msg-bubble {
  background: var(--c-primary);
  color: white;
  border-color: var(--c-primary);
  border-radius: 10px 2px 10px 10px;
}

.msg-bubble.is-error {
  background: var(--c-error-soft);
  border-color: var(--c-error);
  color: var(--c-error);
}

.msg-bubble :deep(p) {
  margin: 0 0 8px;
}

.msg-bubble :deep(p:last-child) {
  margin-bottom: 0;
}

.msg-bubble :deep(code) {
  background: var(--c-bg-soft);
  padding: 2px 5px;
  border-radius: 4px;
  font-family: var(--font-mono);
  font-size: 12px;
}

.pg-msg.user .msg-bubble :deep(code) {
  background: rgba(255, 255, 255, 0.2);
}

.msg-bubble :deep(pre) {
  background: var(--c-text-1);
  color: var(--c-text-inverse);
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 8px 0;
}

.msg-bubble :deep(pre code) {
  background: transparent;
  color: inherit;
  padding: 0;
}


/* ===== Message Running Status ===== */
.msg-bubble.is-running {
  min-width: 200px;
}

.msg-status {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 4px 2px;
}

.status-icon {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
}

.status-icon.thinking {
  background: rgba(139, 92, 246, 0.12);
  color: #8b5cf6;
  animation: status-pulse 2s ease-in-out infinite;
}

.status-icon.tool {
  background: rgba(245, 158, 11, 0.12);
  color: #f59e0b;
}

.status-icon.timeout {
  background: rgba(239, 68, 68, 0.12);
  color: #ef4444;
  animation: status-pulse 1.5s ease-in-out infinite;
}

.status-icon.waiting {
  background: rgba(107, 114, 128, 0.12);
  color: #6b7280;
}

@keyframes status-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.status-text {
  flex: 1;
  min-width: 0;
}

.status-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--c-text-2);
  display: block;
  margin-bottom: 4px;
}

.status-label.tool-label {
  color: #f59e0b;
}

.status-preview {
  font-size: 12px;
  color: var(--c-text-3);
  line-height: 1.5;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.status-preview.tool-name {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--c-text-1);
  background: var(--c-bg-soft);
  padding: 2px 8px;
  border-radius: 4px;
  display: inline-block;
  -webkit-line-clamp: 1;
}


.typing-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  background: var(--c-text-3);
  border-radius: 50%;
  margin-left: 4px;
  animation: typing 1.2s infinite;
  vertical-align: middle;
}

@keyframes typing {
  0%, 60%, 100% { opacity: 0.3; }
  30% { opacity: 1; }
}

/* ===== Composer ===== */
.pg-composer {
  padding: 12px 20px 16px;
  border-top: 1px solid var(--c-border);
  background: var(--c-surface);
  flex-shrink: 0;
}

.composer-input-wrap {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 12px;
  overflow: hidden;
  transition: all 0.15s ease;
}

.composer-input-wrap:focus-within {
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px var(--c-primary-soft);
}

.composer-textarea {
  width: 100%;
  min-height: 60px;
  max-height: 150px;
  padding: 12px 14px;
  border: none;
  resize: none;
  font-size: 13px;
  font-family: var(--font-sans);
  line-height: 1.5;
  color: var(--c-text-1);
  background: transparent;
  outline: none;
}

.composer-textarea::placeholder {
  color: var(--c-text-3);
}

.composer-actions {
  display: flex;
  justify-content: flex-end;
  padding: 8px 12px 10px;
  border-top: 1px solid var(--c-border-subtle);
}

/* ===== Inspector ===== */
.pg-inspector {
  display: flex;
  flex-direction: column;
  border-left: 1px solid var(--c-border);
  background: var(--c-surface);
  min-height: 0;
}

.pg-inspector-head {
  padding: 14px 16px;
  border-bottom: 1px solid var(--c-border);
}

.pg-inspector-head h3 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text-1);
}

.run-code {
  margin: 3px 0 0;
  font-size: 11px;
  color: var(--c-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pg-inspector-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  background: var(--c-border);
  border-bottom: 1px solid var(--c-border);
}

.stat-item {
  background: var(--c-surface);
  padding: 10px 8px;
  text-align: center;
}

.stat-label {
  display: block;
  font-size: 10px;
  color: var(--c-text-3);
  margin-bottom: 3px;
}

.stat-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text-1);
}

.pg-inspector-tabs {
  display: flex;
  gap: 4px;
  padding: 8px;
  border-bottom: 1px solid var(--c-border);
}

.tab-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 6px 8px;
  background: transparent;
  border: none;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 500;
  color: var(--c-text-3);
  cursor: pointer;
  transition: all 0.15s ease;
}

.tab-btn:hover {
  background: var(--c-surface-hover);
  color: var(--c-text-1);
}

.tab-btn.active {
  background: var(--c-primary);
  color: white;
}

.tab-btn .el-icon {
  font-size: 13px;
}

.pg-inspector-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px;
}

.inspector-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--c-text-3);
  font-size: 12px;
}

.message-stack {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.stack-section {
  border: 1px solid var(--c-border);
  border-radius: 8px;
  overflow: hidden;
}

.stack-section-title {
  padding: 7px 10px;
  font-size: 11px;
  font-weight: 600;
  color: var(--c-text-2);
  background: var(--c-bg-soft);
  border-bottom: 1px solid var(--c-border);
}

.stack-empty {
  padding: 10px;
  font-size: 12px;
  color: var(--c-text-3);
  text-align: center;
}

.stack-msg {
  padding: 8px 10px;
  border-bottom: 1px solid var(--c-border-subtle);
}

.stack-msg:last-child {
  border-bottom: none;
}

.stack-role {
  display: inline-block;
  font-size: 10px;
  font-weight: 600;
  color: var(--c-primary);
  text-transform: uppercase;
  margin-bottom: 4px;
}

.stack-content {
  font-size: 12px;
  color: var(--c-text-2);
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.stack-pre {
  margin: 0;
  padding: 8px 10px;
  font-size: 11px;
  font-family: var(--font-mono);
  color: var(--c-text-2);
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.5;
  max-height: 180px;
  overflow: auto;
}

.stack-text {
  padding: 8px 10px;
  font-size: 12px;
  color: var(--c-text-2);
  line-height: 1.5;
  word-break: break-word;
}

.stack-text :deep(p) {
  margin: 0 0 6px;
}

.stack-text :deep(p:last-child) {
  margin-bottom: 0;
}

.stack-text :deep(code) {
  background: var(--c-bg-soft);
  padding: 1px 4px;
  border-radius: 3px;
  font-family: var(--font-mono);
  font-size: 11px;
}

/* ===== Trace ===== */
.trace-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.trace-step {
  border: 1px solid var(--c-border);
  border-radius: 8px;
  overflow: hidden;
}

.trace-step-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  background: var(--c-surface-hover);
  font-size: 12px;
  font-weight: 500;
}

.trace-step-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
}

.trace-step-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-step-count {
  font-size: 10px;
  font-weight: 600;
  background: currentColor;
  color: var(--c-surface);
  padding: 1px 5px;
  border-radius: 10px;
  opacity: 0.2;
}

.trace-step-duration {
  font-size: 10px;
  font-family: var(--font-mono);
  font-weight: 600;
  opacity: 0.8;
}

.trace-step-content {
  max-height: 200px;
  overflow: auto;
}

.trace-step-content pre {
  margin: 0;
  padding: 8px 10px;
  font-size: 11px;
  font-family: var(--font-mono);
  color: var(--c-text-2);
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.5;
  background: var(--c-bg-soft);
}

/* ===== Dialog ===== */
.pg-dialog-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(2px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

/* ===== Flow (完整流水) ===== */
.flow-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 4px 0;
}

.flow-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.flow-badge {
  display: flex;
  align-items: center;
  padding-left: 2px;
}

.flow-badge-text {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.flow-user .flow-badge-text {
  background: rgba(59, 130, 246, 0.15);
  color: #3b82f6;
}

.flow-reasoning .flow-badge-text {
  background: rgba(139, 92, 246, 0.15);
  color: #8b5cf6;
}

.flow-assistant .flow-badge-text {
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
}

.flow-tool_call .flow-badge-text {
  background: rgba(245, 158, 11, 0.15);
  color: #f59e0b;
}

.flow-tool_result .flow-badge-text {
  background: rgba(20, 184, 166, 0.15);
  color: #14b8a6;
}

.flow-error .flow-badge-text {
  background: rgba(239, 68, 68, 0.15);
  color: #ef4444;
}

.flow-content {
  border: 1px solid var(--c-border);
  border-radius: 8px;
  background: var(--c-surface);
  overflow: hidden;
}

.flow-text {
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--c-text);
}

.flow-text :deep(p) {
  margin: 0 0 8px 0;
}
.flow-text :deep(p:last-child) {
  margin-bottom: 0;
}

.flow-pre {
  margin: 0;
  padding: 10px 12px;
  font-size: 12px;
  font-family: var(--font-mono);
  color: var(--c-text-2);
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.5;
  background: var(--c-bg-soft);
  max-height: 300px;
  overflow: auto;
}

.flow-tool {
  padding: 0;
}

.flow-tool-name {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: rgba(245, 158, 11, 0.08);
  border-bottom: 1px solid var(--c-border);
  font-size: 13px;
  font-weight: 600;
  color: #f59e0b;
  font-family: var(--font-mono);
}

.flow-tool-args {
  padding: 8px 12px;
}

.flow-tool-args-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--c-text-3);
  margin-bottom: 4px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.flow-tool-args pre {
  margin: 0;
  font-size: 11px;
  font-family: var(--font-mono);
  color: var(--c-text-2);
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.5;
  background: var(--c-bg-soft);
  padding: 8px;
  border-radius: 4px;
  max-height: 200px;
  overflow: auto;
}

.flow-tool-result {
  padding: 0;
}

.flow-tool-result-title {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: rgba(20, 184, 166, 0.08);
  border-bottom: 1px solid var(--c-border);
  font-size: 12px;
  font-weight: 600;
  color: #14b8a6;
}

.flow-tool-result-content {
  margin: 0;
  padding: 10px 12px;
  font-size: 11px;
  font-family: var(--font-mono);
  color: var(--c-text-2);
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.5;
  max-height: 250px;
  overflow: auto;
}

.flow-error {
  padding: 10px 12px;
  font-size: 13px;
  color: var(--c-error);
  background: rgba(239, 68, 68, 0.05);
}


.pg-dialog {
  width: 480px;
  max-width: calc(100vw - 32px);
  background: var(--c-surface);
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
  overflow: hidden;
}

.pg-dialog-head {
  padding: 14px 18px;
  border-bottom: 1px solid var(--c-border);
}

.pg-dialog-head h2 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text-1);
}

.pg-dialog-body {
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 60vh;
  overflow-y: auto;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.form-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--c-text-2);
}

.required {
  color: var(--c-error);
  margin-left: 2px;
}

.form-input,
.form-select {
  height: 36px;
  padding: 0 10px;
  border: 1px solid var(--c-border);
  border-radius: 7px;
  font-size: 13px;
  color: var(--c-text-1);
  background: var(--c-surface);
  outline: none;
  transition: all 0.15s ease;
  font-family: var(--font-sans);
}

.form-input:focus,
.form-select:focus {
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px var(--c-primary-soft);
}

.pg-dialog-foot {
  padding: 12px 18px;
  border-top: 1px solid var(--c-border);
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
/* ===== View Tabs ===== */
/* ===== View Tabs (对话/轨迹) ===== */
.pg-view-tabs-bar {
  display: flex;
  align-items: center;
  gap: 0;
  padding: 0 20px;
  border-bottom: 1px solid var(--c-border);
  background: var(--c-surface);
}

.pg-view-tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 4px;
  margin-right: 24px;
  border: none;
  background: transparent;
  color: var(--c-text-3);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}

.pg-view-tab-btn:hover {
  color: var(--c-text-1);
}

.pg-view-tab-btn.active {
  color: var(--c-primary);
  border-bottom-color: var(--c-primary);
  font-weight: 600;
}

.pg-view-tab-btn .el-icon {
  font-size: 14px;
}

/* ===== Trace View ===== */
.trace-run-selector {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-bottom: 1px solid var(--c-border-subtle);
  background: var(--c-bg-soft);
}
.trace-run-label {
  font-size: 12px;
  color: var(--c-text-secondary);
}
.trace-run-select {
  flex: 1;
  max-width: 240px;
}
.pg-trace-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.trace-stats-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 20px;
  border-bottom: 1px solid var(--c-border);
  background: var(--c-surface);
}

.trace-stat {
  display: flex;
  align-items: center;
  gap: 6px;
}

.trace-stat-icon {
  font-size: 14px;
  color: var(--c-text-3);
}

.trace-stat-label {
  font-size: 11px;
  color: var(--c-text-3);
  font-weight: 500;
}

.trace-stat-value {
  font-size: 12px;
  font-weight: 600;
  color: var(--c-text-1);
}

.trace-stat-divider {
  width: 1px;
  height: 16px;
  background: var(--c-border);
}

.trace-stat-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

.trace-search {
  position: relative;
  display: flex;
  align-items: center;
}

.trace-search-icon {
  position: absolute;
  left: 8px;
  font-size: 13px;
  color: var(--c-text-3);
}

.trace-search-input {
  height: 28px;
  padding: 0 10px 0 26px;
  border: 1px solid var(--c-border);
  border-radius: 6px;
  font-size: 12px;
  color: var(--c-text-1);
  background: var(--c-bg-soft);
  outline: none;
  width: 160px;
  transition: all 0.15s ease;
  font-family: inherit;
}

.trace-search-input:focus {
  border-color: var(--c-primary);
  background: var(--c-surface);
}

.trace-export-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  border: 1px solid var(--c-border);
  border-radius: 6px;
  background: var(--c-surface);
  color: var(--c-text-2);
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}

.trace-export-btn:hover {
  border-color: var(--c-primary);
  color: var(--c-primary);
}

.trace-export-btn .el-icon {
  font-size: 13px;
}

/* Timeline Bar */
.trace-timeline-bar {
  padding: 12px 20px;
  border-bottom: 1px solid var(--c-border);
  background: var(--c-bg-soft);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tl-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.tl-label {
  font-size: 10px;
  font-weight: 600;
  color: var(--c-text-3);
  text-transform: uppercase;
  width: 50px;
  letter-spacing: 0.5px;
}

.tl-track {
  position: relative;
  flex: 1;
  height: 8px;
  background: var(--c-border);
  border-radius: 2px;
  overflow: hidden;
}

.tl-bar {
  position: absolute;
  top: 0;
  height: 100%;
  border-radius: 2px;
  opacity: 0.8;
}

.tl-input { background: var(--c-primary); }
.tl-model { background: #8b5cf6; }
.tl-tools { background: #f59e0b; }

/* Trace Content */
.trace-content {
  flex: 1;
  min-height: 0;
  display: flex;
  overflow: hidden;
}

.trace-list {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  padding: 12px 16px;
}

.trace-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  height: 100%;
  color: var(--c-text-3);
}

.trace-empty-icon {
  font-size: 40px;
  opacity: 0.3;
}

.trace-empty p {
  margin: 0;
  font-size: 13px;
}

.trace-list-inner {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.trace-entry {
  display: flex;
  gap: 12px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: default;
  transition: background 0.15s ease;
  position: relative;
}

.trace-entry:hover {
  background: var(--c-bg-soft);
}

.trace-entry.is-selected {
  background: rgba(245, 158, 11, 0.08);
  box-shadow: inset 2px 0 0 #f59e0b;
}

.trace-entry.trace-tool_call {
  cursor: pointer;
  padding-left: 32px;
}

.trace-entry.trace-tool_call::before {
  content: '';
  position: absolute;
  left: 20px;
  top: 0;
  bottom: 0;
  width: 1px;
  background: var(--c-border);
}

.trace-entry.trace-tool_result {
  padding-left: 32px;
}

.trace-entry.trace-tool_result::before {
  content: '';
  position: absolute;
  left: 20px;
  top: 0;
  bottom: 0;
  width: 1px;
  background: var(--c-border);
}

.trace-entry-dot {
  flex-shrink: 0;
  padding-top: 2px;
}

.trace-entry-icon {
  width: 20px;
  height: 20px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

.icon-system { background: rgba(107, 114, 128, 0.15); color: #6b7280; }
.icon-context { background: rgba(16, 185, 129, 0.15); color: #10b981; }
.icon-user { background: rgba(59, 130, 246, 0.15); color: #3b82f6; }
.icon-reasoning { background: rgba(139, 92, 246, 0.15); color: #8b5cf6; }
.icon-assistant { background: rgba(139, 92, 246, 0.15); color: #8b5cf6; }
.icon-tool_call { background: rgba(245, 158, 11, 0.15); color: #f59e0b; }
.icon-tool_result { background: rgba(20, 184, 166, 0.15); color: #14b8a6; }
.icon-summary { background: rgba(6, 182, 212, 0.15); color: #06b6d4; }
.icon-complete { background: rgba(16, 185, 129, 0.15); color: #10b981; }
.icon-error { background: rgba(239, 68, 68, 0.15); color: #ef4444; }
.icon-cancelled { background: rgba(245, 158, 11, 0.15); color: #f59e0b; }

.trace-entry-body {
  flex: 1;
  min-width: 0;
}

.trace-entry-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.trace-entry-badge {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 3px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  border: 1px solid;
}

.badge-system { background: rgba(107, 114, 128, 0.1); color: #6b7280; border-color: rgba(107, 114, 128, 0.2); }
.badge-context { background: rgba(16, 185, 129, 0.1); color: #10b981; border-color: rgba(16, 185, 129, 0.2); }
.badge-user { background: rgba(59, 130, 246, 0.1); color: #3b82f6; border-color: rgba(59, 130, 246, 0.2); }
.badge-reasoning { background: rgba(139, 92, 246, 0.1); color: #8b5cf6; border-color: rgba(139, 92, 246, 0.2); }
.badge-assistant { background: rgba(139, 92, 246, 0.1); color: #8b5cf6; border-color: rgba(139, 92, 246, 0.2); }
.badge-tool_call { background: rgba(245, 158, 11, 0.1); color: #f59e0b; border-color: rgba(245, 158, 11, 0.2); }
.badge-tool_result { background: rgba(20, 184, 166, 0.1); color: #14b8a6; border-color: rgba(20, 184, 166, 0.2); }
.badge-summary { background: rgba(6, 182, 212, 0.1); color: #06b6d4; border-color: rgba(6, 182, 212, 0.2); }
.badge-complete { background: rgba(16, 185, 129, 0.1); color: #10b981; border-color: rgba(16, 185, 129, 0.2); }
.badge-error { background: rgba(239, 68, 68, 0.1); color: #ef4444; border-color: rgba(239, 68, 68, 0.2); }
.badge-cancelled { background: rgba(245, 158, 11, 0.1); color: #f59e0b; border-color: rgba(245, 158, 11, 0.2); }

.trace-entry-tool-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--c-text-1);
  font-family: var(--font-mono);
}

.trace-entry-time {
  margin-left: auto;
  font-size: 10px;
  color: var(--c-text-3);
}

.trace-entry-content {
  font-size: 12px;
  line-height: 1.6;
  color: var(--c-text);
}

.trace-entry-preview {
  cursor: pointer;
}

.trace-entry-preview span {
  color: var(--c-text-2);
  display: block;
}

.trace-args-preview,
.trace-result-preview {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.trace-preview-label {
  font-size: 10px;
  font-weight: 600;
  color: var(--c-text-3);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  flex-shrink: 0;
}

.trace-preview-label.result {
  color: #14b8a6;
}

.trace-args-preview code {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--c-text-2);
  background: var(--c-bg-soft);
  padding: 1px 6px;
  border-radius: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-result-preview span {
  font-size: 11px;
  color: var(--c-text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-expand-hint,
.trace-collapse-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 10px;
  color: var(--c-text-3);
  margin-top: 6px;
}

.trace-expand-hint .el-icon,
.trace-collapse-hint .el-icon {
  font-size: 12px;
}

.trace-entry-expanded {
  cursor: pointer;
}

.trace-expand-section {
  margin-bottom: 8px;
}

.trace-expand-section:last-child {
  margin-bottom: 0;
}

.trace-expand-title {
  font-size: 10px;
  font-weight: 600;
  color: var(--c-text-3);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 4px;
}

.trace-expand-title.result {
  color: #14b8a6;
}

.trace-expand-pre {
  margin: 0;
  padding: 8px 10px;
  font-size: 11px;
  font-family: var(--font-mono);
  color: var(--c-text-2);
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.5;
  background: var(--c-bg-soft);
  border-radius: 4px;
  max-height: 240px;
  overflow: auto;
}

.result-pre {
  color: rgba(16, 185, 129, 0.85);
  background: rgba(16, 185, 129, 0.05);
}

.trace-entry-pre {
  margin: 0;
  font-size: 12px;
  font-family: var(--font-mono);
  color: var(--c-text-2);
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
}

/* Trace Detail Panel */
.trace-detail {
  width: 300px;
  flex-shrink: 0;
  border-left: 1px solid var(--c-border);
  background: var(--c-surface);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.trace-detail-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--c-border);
}

.trace-detail-badge {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 3px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  background: rgba(245, 158, 11, 0.1);
  color: #f59e0b;
  border: 1px solid rgba(245, 158, 11, 0.2);
}

.trace-detail-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--c-text-1);
}

.trace-detail-close {
  margin-left: auto;
  background: transparent;
  border: none;
  color: var(--c-text-3);
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s ease;
}

.trace-detail-close:hover {
  color: var(--c-text-1);
  background: var(--c-bg-soft);
}

.trace-detail-tabs {
  display: flex;
  gap: 2px;
  padding: 6px 8px;
  border-bottom: 1px solid var(--c-border);
  background: var(--c-bg-soft);
}

.detail-tab {
  flex: 1;
  padding: 5px 0;
  border: none;
  background: transparent;
  color: var(--c-text-3);
  font-size: 11px;
  font-weight: 500;
  text-transform: capitalize;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.detail-tab:hover {
  color: var(--c-text-1);
}

.detail-tab.active {
  background: var(--c-primary);
  color: #fff;
}

.trace-detail-body {
  flex: 1;
  overflow-y: auto;
  padding: 14px;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.detail-row:last-child {
  margin-bottom: 0;
}

.detail-label {
  font-size: 11px;
  color: var(--c-text-3);
  flex-shrink: 0;
}

.detail-value {
  font-size: 12px;
  color: var(--c-text-1);
  text-align: right;
  word-break: break-word;
}

.detail-value.muted {
  color: var(--c-text-3);
}

.detail-value.status-completed {
  color: #10b981;
  font-weight: 600;
}

.detail-value.duration-value {
  color: #10b981;
  font-weight: 600;
}

.detail-section-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--c-text-3);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}

.detail-pre {
  margin: 0;
  padding: 10px;
  font-size: 11px;
  font-family: var(--font-mono);
  color: var(--c-text-2);
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.5;
  background: var(--c-bg-soft);
  border-radius: 6px;
  max-height: 300px;
  overflow: auto;
}
</style>

