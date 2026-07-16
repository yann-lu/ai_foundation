<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ChatDotRound,
  Clock,
  Connection,
  Cpu,
  DocumentCopy,
  Loading,
  Plus,
  Promotion,
  Refresh,
  VideoPause,
  Warning
} from '@element-plus/icons-vue'
import { pageProjects } from '@/api/project'
import { createConversation, createRun, streamRunEvents, cancelRun, getMessages } from '@/api/chat'
import { pageConversations } from '@/api/conversation'
import { renderMarkdown } from '@/utils/markdown'
import type { AgentProjectDTO, ConversationDTO, CreateRunRequest, MessageDTO, RunStreamEnvelope } from '@/types/api'

interface ChatMessage {
  role: string
  content: string
  reasoning?: string
  streaming?: boolean
  createdAt?: number
}

interface RunEventLog {
  eventType: RunStreamEnvelope['eventType']
  taskState: string | null
  dataPreview: string
  timestamp: number
}

interface ParsedAssistantContent {
  answer: string
  reasoning: string
}

interface PromptVariableDefinition {
  name: string
  label?: string
  type?: string
  required?: boolean
  description?: string
  defaultValue?: unknown
}

const projects = ref<AgentProjectDTO[]>([])
const selectedProjectCode = ref('')
const conversationCode = ref('')
const systemPrompt = ref('')
const userInput = ref('')
const sending = ref(false)
const messages = ref<ChatMessage[]>([])
const chatBodyRef = ref<HTMLElement>()
const userInputRef = ref()

const conversations = ref<ConversationDTO[]>([])
const activeConversationId = ref<number>()
const convLoading = ref(false)
const convLoadingMore = ref(false)
const convHasMore = ref(true)
const convPage = ref(1)
const convPageSize = 20
const convListRef = ref<HTMLElement>()
const creating = ref(false)
const variableDialogVisible = ref(false)
const pendingVariables = ref<PromptVariableDefinition[]>([])
const contextVariableForm = ref<Record<string, unknown>>({})

const currentRunCode = ref('')
const runStartedAt = ref<number>()
const runFinishedAt = ref<number>()
const runEvents = ref<RunEventLog[]>([])
let activeEventSource: EventSource | null = null

const selectedProject = computed(() => projects.value.find(p => p.projectCode === selectedProjectCode.value))
const activeConversation = computed(() => conversations.value.find(c => c.id === activeConversationId.value))
const canSend = computed(() => !!conversationCode.value && !!userInput.value.trim() && !sending.value)
const hasReasoning = computed(() => messages.value.some(msg => !!msg.reasoning?.trim()))
const latestReasoning = computed(() => [...messages.value].reverse().find(msg => msg.reasoning?.trim())?.reasoning || '')
const lastAssistant = computed(() => [...messages.value].reverse().find(msg => msg.role === 'assistant'))
const tokenCount = computed(() => lastAssistant.value?.content.length || 0)
const runDuration = computed(() => {
  if (!runStartedAt.value) return '--'
  const end = runFinishedAt.value || Date.now()
  return `${Math.max(0, end - runStartedAt.value)} ms`
})

function parsePromptVariables(project?: AgentProjectDTO): PromptVariableDefinition[] {
  if (!project?.promptVariables?.trim()) return []
  try {
    const parsed = JSON.parse(project.promptVariables)
    return Array.isArray(parsed) ? parsed.filter(item => item?.name) : []
  } catch {
    return []
  }
}

function openVariableDialog(definitions: PromptVariableDefinition[]) {
  pendingVariables.value = definitions
  const values: Record<string, unknown> = {}
  definitions.forEach(item => {
    if (item.defaultValue !== undefined && item.defaultValue !== null) {
      values[item.name] = item.defaultValue
    } else {
      values[item.name] = ''
    }
  })
  contextVariableForm.value = values
  variableDialogVisible.value = true
}

function normalizeContextVariables() {
  const values: Record<string, unknown> = {}
  for (const item of pendingVariables.value) {
    const raw = contextVariableForm.value[item.name]
    if (item.required && (raw === undefined || raw === null || String(raw).trim() === '')) {
      ElMessage.warning(`请填写${item.label || item.name}`)
      return null
    }
    if (raw === undefined || raw === null || String(raw).trim() === '') {
      continue
    }
    if (item.type === 'number') {
      const num = Number(raw)
      if (Number.isNaN(num)) {
        ElMessage.warning(`${item.label || item.name} 必须是数字`)
        return null
      }
      values[item.name] = num
    } else if (item.type === 'boolean') {
      values[item.name] = raw === true || raw === 'true'
    } else {
      values[item.name] = String(raw).trim()
    }
  }
  return values
}
const activeModelName = computed(() => activeConversation.value?.modelName || selectedProject.value?.projectCode || '--')

async function loadProjects() {
  const res = await pageProjects({ current: 1, size: 100, state: 1 })
  projects.value = res.data.records
  if (!selectedProjectCode.value && projects.value.length > 0) {
    selectedProjectCode.value = projects.value[0].projectCode
  }
}

async function loadConversations(reset = false) {
  if (!selectedProjectCode.value) {
    conversations.value = []
    return
  }
  if (reset) {
    convPage.value = 1
    convHasMore.value = true
    conversations.value = []
  } else if (convLoading.value || convLoadingMore.value || !convHasMore.value) {
    return
  }
  if (reset) convLoading.value = true
  else convLoadingMore.value = true
  try {
    const res = await pageConversations({
      productCode: selectedProjectCode.value,
      state: 0,
      current: convPage.value,
      size: convPageSize
    })
    const list = res.data.records
    conversations.value = reset ? list : [...conversations.value, ...list]
    convHasMore.value = conversations.value.length < res.data.total
    if (list.length) convPage.value++
  } finally {
    convLoading.value = false
    convLoadingMore.value = false
  }
}

function handleConvScroll() {
  const el = convListRef.value
  if (!el) return
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 24) {
    loadConversations()
  }
}

async function selectConversation(conv: ConversationDTO) {
  if (sending.value) {
    ElMessage.warning('当前 Run 仍在执行')
    return
  }
  activeConversationId.value = conv.id
  conversationCode.value = conv.conversationCode
  messages.value = []
  resetRunInspector()
  const res = await getMessages(conv.conversationCode)
  messages.value = res.data.map((m: MessageDTO) => {
    const parsed = m.role === 'assistant' ? parseAssistantContent(m.content) : { answer: m.content, reasoning: '' }
    return {
      role: m.role,
      content: parsed.answer,
      reasoning: parsed.reasoning,
      createdAt: m.createTime ? new Date(m.createTime).getTime() : undefined
    }
  })
  await scrollToTop()
}

async function handleNewConversation() {
  if (!selectedProjectCode.value) {
    ElMessage.warning('请先选择项目')
    return
  }
  if (sending.value) {
    ElMessage.warning('当前 Run 仍在执行')
    return
  }
  const definitions = parsePromptVariables(selectedProject.value)
  if (definitions.length) {
    openVariableDialog(definitions)
    return
  }
  await createNewConversation({})
}

async function confirmCreateConversation() {
  const variables = normalizeContextVariables()
  if (variables == null) return
  variableDialogVisible.value = false
  await createNewConversation(variables)
}

async function createNewConversation(contextVariables: Record<string, unknown>) {
  creating.value = true
  try {
    messages.value = []
    conversationCode.value = ''
    resetRunInspector()
    const res = await createConversation({
      productCode: selectedProjectCode.value,
      contextVariables,
      title: `Playground ${new Date().toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}`
    })
    conversationCode.value = res.data.conversationCode
    activeConversationId.value = res.data.id
    await loadConversations(true)
    await nextTick()
    userInputRef.value?.focus?.()
    ElMessage.success('会话已创建')
  } finally {
    creating.value = false
  }
}

async function handleSend() {
  if (!canSend.value) return
  const text = userInput.value.trim()
  userInput.value = ''
  messages.value.push({ role: 'user', content: text, createdAt: Date.now() })
  const assistantMsg: ChatMessage = { role: 'assistant', content: '', reasoning: '', streaming: true, createdAt: Date.now() }
  messages.value.push(assistantMsg)
  sending.value = true
  resetRunInspector()
  await scrollToBottom()

  const req: CreateRunRequest = {
    conversationCode: conversationCode.value,
    userMessage: text,
    systemPrompt: systemPrompt.value.trim() || undefined
  }

  try {
    const createRes = await createRun(req)
    const runCode = createRes.data.runCode
    currentRunCode.value = runCode
    runStartedAt.value = Date.now()
    await consumeRunEvents(runCode, assistantMsg)
    await loadConversations(true)
  } catch (e: any) {
    assistantMsg.streaming = false
    assistantMsg.content = `请求失败: ${e.message || '未知错误'}`
    addRunEvent('run_error', 'FAILED', assistantMsg.content, Date.now())
    ElMessage.error('请求失败')
  } finally {
    sending.value = false
    currentRunCode.value = ''
    closeEventSource()
    runFinishedAt.value = runFinishedAt.value || Date.now()
  }
}

function consumeRunEvents(runCode: string, assistantMsg: ChatMessage) {
  return new Promise<void>((resolve, reject) => {
    const source = streamRunEvents(runCode)
    activeEventSource = source

    source.onmessage = async (event) => {
      let envelope: RunStreamEnvelope
      try {
        envelope = JSON.parse(event.data) as RunStreamEnvelope
      } catch {
        return
      }
      addRunEvent(envelope.eventType, envelope.taskState, envelope.data, envelope.timestamp)

      if (envelope.eventType === 'chat_token') {
        appendAssistantToken(assistantMsg, String(envelope.data || ''))
        await scrollToBottom()
      } else if (envelope.eventType === 'run_complete') {
        const reply = typeof envelope.data === 'string' ? envelope.data : assistantMsg.content
        const parsed = parseAssistantContent(reply)
        assistantMsg.content = parsed.answer
        assistantMsg.reasoning = parsed.reasoning
        assistantMsg.streaming = false
        runFinishedAt.value = Date.now()
        resolve()
        closeEventSource()
        await scrollToBottom()
      } else if (envelope.eventType === 'run_error') {
        assistantMsg.content = String(envelope.data || '执行失败')
        assistantMsg.streaming = false
        runFinishedAt.value = Date.now()
        closeEventSource()
        reject(new Error(assistantMsg.content))
      } else if (envelope.eventType === 'run_cancelled') {
        assistantMsg.streaming = false
        runFinishedAt.value = Date.now()
        resolve()
        closeEventSource()
      }
    }

    source.onerror = () => {
      closeEventSource()
      reject(new Error('SSE 连接中断'))
    }
  })
}

function appendAssistantToken(message: ChatMessage, token: string) {
  message.content += token
}

function parseAssistantContent(content: string): ParsedAssistantContent {
  if (!content) return { answer: '', reasoning: '' }
  let reasoning = ''
  let answer = content
  answer = answer.replace(/<think>([\s\S]*?)<\/think>/gi, (_match, block) => {
    reasoning += `${block.trim()}\n\n`
    return ''
  })
  answer = answer.replace(/<think>([\s\S]*)$/i, (_match, block) => {
    reasoning += block.trim()
    return ''
  })
  return { answer: answer.trimStart(), reasoning: reasoning.trim() }
}

async function handleStop() {
  if (!currentRunCode.value) return
  try {
    await cancelRun(currentRunCode.value)
  } catch {
    /* request layer already reports cancellation errors */
  }
}

function resetRunInspector() {
  runEvents.value = []
  currentRunCode.value = ''
  runStartedAt.value = undefined
  runFinishedAt.value = undefined
  closeEventSource()
}

function closeEventSource() {
  if (activeEventSource) {
    activeEventSource.close()
    activeEventSource = null
  }
}

function addRunEvent(eventType: RunStreamEnvelope['eventType'], taskState: string | null, data: unknown, timestamp?: number) {
  runEvents.value.push({
    eventType,
    taskState,
    dataPreview: previewEventData(data),
    timestamp: timestamp || Date.now()
  })
}

function previewEventData(data: unknown) {
  if (data == null || data === '') return ''
  const text = typeof data === 'string' ? data : JSON.stringify(data)
  return text.length > 120 ? `${text.slice(0, 120)}...` : text
}

async function copyConversationCode() {
  if (!conversationCode.value) return
  await navigator.clipboard.writeText(conversationCode.value)
  ElMessage.success('会话编码已复制')
}

async function scrollToBottom() {
  await nextTick()
  if (chatBodyRef.value) {
    chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
  }
}

async function scrollToTop() {
  await nextTick()
  if (chatBodyRef.value) {
    chatBodyRef.value.scrollTop = 0
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

function formatTime(t?: string | number) {
  if (!t) return ''
  const d = new Date(t)
  const now = new Date()
  if (d.toDateString() === now.toDateString()) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

function eventLabel(eventType: string) {
  const labels: Record<string, string> = {
    run_start: 'Run start',
    chat_start: 'Chat start',
    chat_token: 'Token',
    chat_complete: 'Chat complete',
    run_complete: 'Run complete',
    run_error: 'Run error',
    run_cancelled: 'Cancelled'
  }
  return labels[eventType] || eventType
}

watch(selectedProjectCode, () => {
  if (sending.value) return
  activeConversationId.value = undefined
  conversationCode.value = ''
  messages.value = []
  resetRunInspector()
  loadConversations(true)
})

onMounted(loadProjects)
onBeforeUnmount(closeEventSource)
</script>

<template>
  <div class="playground-shell">
    <aside class="conversation-rail">
      <div class="rail-head">
        <div>
          <p class="eyebrow">Project</p>
          <h2>{{ selectedProject?.projectName || 'Playground' }}</h2>
        </div>
        <el-button :icon="Refresh" circle :disabled="sending" @click="loadConversations(true)" />
      </div>

      <el-select v-model="selectedProjectCode" class="project-select" placeholder="选择项目" filterable :disabled="sending">
        <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.projectCode" />
      </el-select>

      <el-button class="new-conversation" type="primary" :icon="Plus" :loading="creating" :disabled="sending" @click="handleNewConversation">
        新建测试会话
      </el-button>

      <div ref="convListRef" v-loading="convLoading" class="conversation-list" @scroll="handleConvScroll">
        <button
          v-for="conv in conversations"
          :key="conv.id"
          :class="['conversation-item', { active: conv.id === activeConversationId }]"
          type="button"
          @click="selectConversation(conv)"
        >
          <span class="conversation-title">{{ conv.title || conv.conversationCode }}</span>
          <span class="conversation-meta">
            <span>{{ conv.modelName || 'default model' }}</span>
            <span>{{ formatTime(conv.lastMessageTime || conv.createTime) }}</span>
          </span>
        </button>
        <el-empty v-if="!convLoading && !conversations.length" description="暂无会话" :image-size="54" />
        <div v-if="convLoadingMore" class="list-state">
          <el-icon class="loading-icon"><Loading /></el-icon>
          <span>加载中</span>
        </div>
        <div v-if="!convHasMore && conversations.length && !convLoadingMore" class="list-state">没有更多了</div>
      </div>
    </aside>

    <main class="chat-console">
      <header class="console-topbar">
        <div>
          <p class="eyebrow">Conversation</p>
          <h1>{{ activeConversation?.title || '对话后台测试台' }}</h1>
        </div>
        <div class="topbar-actions">
          <el-tag :type="sending ? 'warning' : conversationCode ? 'success' : 'info'" effect="plain">
            {{ sending ? 'Streaming' : conversationCode ? 'Ready' : 'Idle' }}
          </el-tag>
          <el-button :icon="DocumentCopy" :disabled="!conversationCode" @click="copyConversationCode">复制会话</el-button>
        </div>
      </header>

      <section ref="chatBodyRef" class="message-stage">
        <div v-if="!conversationCode" class="empty-state">
          <el-icon><ChatDotRound /></el-icon>
          <h2>选择历史会话或创建新会话</h2>
          <p>Playground 用于验证项目模型配置、Run 生命周期、SSE 流式输出和模型思考标签解析。</p>
        </div>
        <div v-else-if="!messages.length" class="empty-state conversation-ready-state">
          <el-icon><Promotion /></el-icon>
          <h2>会话已创建</h2>
          <p>在下方输入测试消息，开始验证当前项目的模型配置和流式输出。</p>
        </div>

        <article v-for="(msg, i) in messages" :key="i" :class="['message-row', msg.role]">
          <div class="message-avatar">{{ msg.role === 'user' ? 'ME' : 'AI' }}</div>
          <div class="message-card">
            <div class="message-head">
              <strong>{{ msg.role === 'user' ? 'User' : 'Assistant' }}</strong>
              <span>{{ formatTime(msg.createdAt) }}</span>
              <el-icon v-if="msg.streaming" class="loading-icon"><Loading /></el-icon>
            </div>
            <el-collapse v-if="msg.reasoning" class="reasoning-collapse">
              <el-collapse-item title="模型思考链路" name="reasoning">
                <pre>{{ msg.reasoning }}</pre>
              </el-collapse-item>
            </el-collapse>
            <div v-if="msg.content" class="message-content" v-html="renderMarkdown(msg.content, { streaming: msg.streaming })" />
            <span v-else-if="msg.streaming" class="stream-placeholder">等待模型输出</span>
          </div>
        </article>
      </section>

      <footer class="composer-panel">
        <el-input
          v-model="systemPrompt"
          type="textarea"
          :rows="2"
          placeholder="System prompt，可选。用于临时覆盖本次 Run 的行为约束。"
          resize="none"
          :disabled="sending"
        />
        <div class="composer-row">
          <el-input
            ref="userInputRef"
            v-model="userInput"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 6 }"
            :placeholder="conversationCode ? '输入测试消息，Enter 发送，Shift+Enter 换行' : '先新建或选择一个会话，再输入测试消息'"
            :disabled="!conversationCode || sending"
            resize="none"
            @keydown="handleKeydown"
          />
          <el-button v-if="!sending" class="send-button" type="primary" :icon="Promotion" :disabled="!canSend" @click="handleSend">
            发送
          </el-button>
          <el-button v-else class="send-button" type="danger" :icon="VideoPause" @click="handleStop">
            停止
          </el-button>
        </div>
      </footer>
    </main>

    <aside class="inspector-panel">
      <section class="metric-grid">
        <div class="metric-cell">
          <el-icon><Connection /></el-icon>
          <span>Run</span>
          <strong>{{ currentRunCode || '--' }}</strong>
        </div>
        <div class="metric-cell">
          <el-icon><Clock /></el-icon>
          <span>Duration</span>
          <strong>{{ runDuration }}</strong>
        </div>
        <div class="metric-cell">
          <el-icon><Cpu /></el-icon>
          <span>Model</span>
          <strong>{{ activeModelName }}</strong>
        </div>
        <div class="metric-cell">
          <el-icon><Warning /></el-icon>
          <span>Chars</span>
          <strong>{{ tokenCount }}</strong>
        </div>
      </section>

      <section class="inspector-section">
        <div class="section-title">
          <span>思考链路</span>
          <el-tag size="small" :type="hasReasoning ? 'success' : 'info'" effect="plain">{{ hasReasoning ? 'Detected' : 'Waiting' }}</el-tag>
        </div>
        <pre v-if="latestReasoning" class="reasoning-preview">{{ latestReasoning }}</pre>
        <p v-else class="muted-text">当前后端未单独推送 reasoning 事件；这里会解析模型输出中的 &lt;think&gt;...&lt;/think&gt; 片段。</p>
      </section>

      <section class="inspector-section event-section">
        <div class="section-title">
          <span>SSE 事件</span>
          <el-tag size="small" effect="plain">{{ runEvents.length }}</el-tag>
        </div>
        <div class="event-list">
          <div v-for="(event, index) in runEvents" :key="`${event.timestamp}-${index}`" class="event-item">
            <span class="event-dot" />
            <div>
              <div class="event-line">
                <strong>{{ eventLabel(event.eventType) }}</strong>
                <span>{{ formatTime(event.timestamp) }}</span>
              </div>
              <p v-if="event.dataPreview">{{ event.dataPreview }}</p>
              <small v-if="event.taskState">{{ event.taskState }}</small>
            </div>
          </div>
          <p v-if="!runEvents.length" class="muted-text">发送消息后显示 run_start、chat_token、run_complete 等事件。</p>
        </div>
      </section>
    </aside>

    <el-dialog v-model="variableDialogVisible" title="创建会话变量" width="520px">
      <el-form label-width="120px">
        <el-form-item
          v-for="item in pendingVariables"
          :key="item.name"
          :label="item.label || item.name"
          :required="item.required"
        >
          <el-select v-if="item.type === 'boolean'" v-model="contextVariableForm[item.name]" placeholder="请选择" style="width: 100%;">
            <el-option label="true" :value="true" />
            <el-option label="false" :value="false" />
          </el-select>
          <el-input v-else v-model="contextVariableForm[item.name]" :placeholder="item.description || item.name" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="variableDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="confirmCreateConversation">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
