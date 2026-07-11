<script setup lang="ts">
import { onMounted, ref, nextTick, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Promotion, Loading } from '@element-plus/icons-vue'
import { pageProjects } from '@/api/project'
import { createConversation, getMessages, streamChat } from '@/api/chat'
import type { AgentProjectDTO, MessageDTO, ChatStreamRequest, ChatStreamChunkDTO } from '@/types/api'

interface ChatMessage {
  role: string
  content: string
  streaming?: boolean
}

const projects = ref<AgentProjectDTO[]>([])
const selectedProjectCode = ref('')
const conversationCode = ref('')
const systemPrompt = ref('')
const userInput = ref('')
const sending = ref(false)
const messages = ref<ChatMessage[]>([])
const chatBodyRef = ref<HTMLElement>()

const canSend = computed(() => !!conversationCode.value && !!userInput.value.trim() && !sending.value)

async function loadProjects() {
  const res = await pageProjects({ current: 1, size: 100, state: 1 })
  projects.value = res.data.records
  if (projects.value.length > 0) {
    selectedProjectCode.value = projects.value[0].projectCode
  }
}

async function handleNewConversation() {
  if (!selectedProjectCode.value) {
    ElMessage.warning('请先选择项目')
    return
  }
  messages.value = []
  conversationCode.value = ''
  const res = await createConversation({
    productCode: selectedProjectCode.value,
    title: 'Playground 会话'
  })
  conversationCode.value = res.data.conversationCode
  ElMessage.success('会话已创建')
}

async function handleSend() {
  if (!canSend.value) return
  const text = userInput.value.trim()
  userInput.value = ''
  messages.value.push({ role: 'user', content: text })
  const assistantMsg: ChatMessage = { role: 'assistant', content: '', streaming: true }
  messages.value.push(assistantMsg)
  sending.value = true
  await scrollToBottom()

  const req: ChatStreamRequest = {
    conversationCode: conversationCode.value,
    userMessage: text,
    systemPrompt: systemPrompt.value || undefined
  }

  try {
    const stream = await streamChat(req)
    const reader = stream.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const events = buffer.split('\n\n')
      buffer = events.pop() || ''

      for (const evt of events) {
        const dataLine = evt.split('\n').find(l => l.startsWith('data:'))
        if (!dataLine) continue
        const jsonStr = dataLine.slice(5).trim()
        if (!jsonStr) continue
        let chunk: ChatStreamChunkDTO
        try {
          chunk = JSON.parse(jsonStr)
        } catch {
          continue
        }
        if (chunk.eventType === 'token') {
          assistantMsg.content += chunk.content || ''
          await scrollToBottom()
        } else if (chunk.eventType === 'complete') {
          assistantMsg.streaming = false
        } else if (chunk.eventType === 'error') {
          assistantMsg.streaming = false
          assistantMsg.content = '❌ ' + (chunk.content || '未知错误')
          ElMessage.error('模型调用失败')
        }
      }
    }
  } catch (e: any) {
    assistantMsg.streaming = false
    assistantMsg.content = '❌ 请求失败: ' + (e.message || '未知错误')
    ElMessage.error('请求失败')
  } finally {
    sending.value = false
  }
}

async function scrollToBottom() {
  await nextTick()
  if (chatBodyRef.value) {
    chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

onMounted(loadProjects)
</script>

<template>
  <div class="playground">
    <div class="pg-toolbar">
      <el-select v-model="selectedProjectCode" placeholder="选择项目" style="width: 200px;" filterable>
        <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.projectCode" />
      </el-select>
      <el-input v-model="systemPrompt" placeholder="系统提示词（可选）" clearable style="width: 300px;" />
      <el-button type="primary" :icon="Plus" @click="handleNewConversation">新建会话</el-button>
      <span v-if="conversationCode" class="conv-code">{{ conversationCode }}</span>
    </div>

    <div ref="chatBodyRef" class="chat-body">
      <div v-if="!conversationCode" class="empty-hint">
        请选择项目并点击「新建会话」开始对话
      </div>
      <div v-for="(msg, i) in messages" :key="i" :class="['chat-msg', msg.role]">
        <div class="msg-avatar">{{ msg.role === 'user' ? '我' : 'AI' }}</div>
        <div class="msg-bubble">
          <span>{{ msg.content }}</span>
          <el-icon v-if="msg.streaming" class="loading-icon"><Loading /></el-icon>
        </div>
      </div>
    </div>

    <div class="chat-input">
      <el-input
        v-model="userInput"
        type="textarea"
        :rows="2"
        placeholder="输入消息，Enter 发送，Shift+Enter 换行"
        :disabled="!conversationCode || sending"
        @keydown="handleKeydown"
        resize="none"
      />
      <el-button type="primary" :icon="Promotion" :loading="sending" :disabled="!canSend" @click="handleSend">
        发送
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.playground {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 60px);
}
.pg-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}
.conv-code {
  font-size: 12px;
  color: #999;
  margin-left: auto;
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #f0f2f5;
}
.empty-hint {
  text-align: center;
  color: #999;
  padding: 80px 0;
}
.chat-msg {
  display: flex;
  margin-bottom: 16px;
  gap: 10px;
}
.chat-msg.user {
  flex-direction: row-reverse;
}
.msg-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}
.chat-msg.user .msg-avatar {
  background: #2c5f8a;
  color: #fff;
}
.chat-msg.assistant .msg-avatar {
  background: #67c23a;
  color: #fff;
}
.msg-bubble {
  max-width: 70%;
  padding: 10px 14px;
  border-radius: 10px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}
.chat-msg.user .msg-bubble {
  background: #2c5f8a;
  color: #fff;
}
.chat-msg.assistant .msg-bubble {
  background: #fff;
  border: 1px solid #e4e7ed;
}
.loading-icon {
  animation: spin 1s linear infinite;
  margin-left: 4px;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
.chat-input {
  display: flex;
  gap: 10px;
  padding: 12px 20px;
  background: #fff;
  border-top: 1px solid #ebeef5;
  align-items: flex-end;
}
</style>
