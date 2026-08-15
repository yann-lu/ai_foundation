<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Plus, Refresh, ChatLineRound, MagicStick, Promotion, VideoPause,
  Cpu, Document, Tools, CircleCheck, Close, CircleClose,
  Warning, ArrowDown, User, List, VideoPlay, Avatar
} from '@element-plus/icons-vue'
import { pageProjects } from '@/api/project'
import { pageConversations } from "@/api/conversation"
import { createConversation, createRun, streamRunEvents, cancelRun, getMessages } from "@/api/chat"
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

const runEvents = ref<RunEventLog[]>([])
const runCode = ref('')
const runStartedAt = ref<number | null>(null)
const runFinishedAt = ref<number | null>(null)
const inspectorTab = ref<'messages' | 'trace'>('messages')

const variableDialogOpen = ref(false)
const pendingVariables = ref<PromptVariableDefinition[]>([])
const contextVariableForm = ref<Record<string, unknown>>({})

let eventSource: EventSource | null = null
let assistantMsgId = ''

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
    conversations.value = res.data.records
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
  runEvents.value = []
  runCode.value = ''
  runStartedAt.value = null
  runFinishedAt.value = null
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
  runStartedAt.value = Date.now()

  let rawBuffer = ''
  let reasoningBuffer = ''

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

        runEvents.value.push({
          eventType: env.eventType,
          taskState: env.taskState,
          data: env.data,
          timestamp: env.timestamp || Date.now(),
        })

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
        } else if (env.eventType === 'run_complete') {
          runFinishedAt.value = Date.now()
          const finalRaw = typeof env.data === 'string' ? env.data : rawBuffer
          const finalParsed = parseThink(finalRaw)
          updateAssistantMessage(assistantMsgId, {
            content: finalParsed.answer,
            reasoning: finalParsed.reasoning || reasoningBuffer || undefined,
            status: 'complete',
          })
          source.close()
          isRunning.value = false
          eventSource = null
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

      source.onerror = () => {
        runFinishedAt.value = Date.now()
        updateAssistantMessage(assistantMsgId, {
          content: 'SSE 连接中断',
          status: 'error',
        })
        source.close()
        isRunning.value = false
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
              <div :class="['msg-bubble', { 'is-error': msg.status === 'error' }]">
                <div v-if="msg.role === 'assistant' && msg.content" v-html="renderMarkdown(msg.content)"></div>
                <template v-else-if="msg.role === 'user'">{{ msg.content }}</template>
                <span v-if="msg.status === 'running'" class="typing-dot"></span>
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
    </main>

    <!-- Inspector -->
    <aside class="pg-inspector">
      <div class="pg-inspector-head">
        <h3>Run Inspector</h3>
        <p class="run-code mono">{{ runCode || '等待 Run 启动…' }}</p>
      </div>

      <div class="pg-inspector-stats">
        <div class="stat-item">
          <span class="stat-label">耗时</span>
          <span class="stat-value mono">{{ runDuration }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">回复 Token</span>
          <span class="stat-value mono">{{ tokenCount }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">思考片段</span>
          <span class="stat-value mono">{{ reasoningCount }}</span>
        </div>
      </div>

      <div class="pg-inspector-tabs">
        <button
          :class="['tab-btn', { active: inspectorTab === 'messages' }]"
          @click="inspectorTab = 'messages'"
        >
          <el-icon><List /></el-icon> Messages
        </button>
        <button
          :class="['tab-btn', { active: inspectorTab === 'trace' }]"
          @click="inspectorTab = 'trace'"
        >
          <el-icon><VideoPlay /></el-icon> Trace
        </button>
      </div>

      <div class="pg-inspector-body">
        <template v-if="inspectorTab === 'messages'">
          <div v-if="runEvents.length === 0" class="inspector-empty">
            暂无数据，发起对话后显示
          </div>
          <div v-else class="message-stack">
            <div class="stack-section">
              <div class="stack-section-title">请求消息</div>
              <div v-if="requestMessages.length === 0" class="stack-empty">无</div>
              <div v-for="(m, i) in requestMessages" :key="i" class="stack-msg">
                <span class="stack-role">{{ m.role }}</span>
                <div class="stack-content">{{ m.content }}</div>
              </div>
            </div>
            <div class="stack-section">
              <div class="stack-section-title">AI 思考</div>
              <div v-if="!responseReasoning" class="stack-empty">无</div>
              <pre v-else class="stack-pre">{{ responseReasoning }}</pre>
            </div>
            <div class="stack-section">
              <div class="stack-section-title">AI 回复</div>
              <div v-if="!responseText" class="stack-empty">无</div>
              <div v-else class="stack-text" v-html="renderMarkdown(responseText)"></div>
            </div>
          </div>
        </template>

        <template v-else>
          <div v-if="timelineSteps.length === 0" class="inspector-empty">
            暂无 Trace，发起对话后显示
          </div>
          <div v-else class="trace-list">
            <div v-for="step in timelineSteps" :key="step.id" class="trace-step">
              <div class="trace-step-head" :style="{ color: step.color }">
                <div class="trace-step-icon">
                  <el-icon><component :is="step.icon" /></el-icon>
                </div>
                <span class="trace-step-label">{{ step.label }}</span>
                <span v-if="step.count" class="trace-step-count">{{ step.count }}</span>
                <span v-if="step.startTime !== step.endTime" class="trace-step-duration">
                  {{ durationLabel(step.startTime, step.endTime) }}
                </span>
              </div>
              <div v-if="step.content" class="trace-step-content">
                <pre>{{ step.content }}</pre>
              </div>
            </div>
          </div>
        </template>
      </div>
    </aside>

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
  grid-template-columns: 260px minmax(0, 1fr) 340px;
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
</style>
