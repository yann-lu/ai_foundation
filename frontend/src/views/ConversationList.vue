<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Delete, View, Brush, ChatDotRound } from '@element-plus/icons-vue'
import { pageConversations, getConversationDetail, deleteConversation, clearConversationMessages } from '@/api/conversation'
import type { ConversationDTO, ConversationPageRequest, ConversationDetailDTO, MessageDTO } from '@/types/api'

const loading = ref(false)
const tableData = ref<ConversationDTO[]>([])
const total = ref(0)

const query = reactive<ConversationPageRequest>({
  productCode: '',
  title: '',
  state: undefined,
  current: 1,
  size: 10
})

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref<ConversationDetailDTO | null>(null)

async function loadData() {
  loading.value = true
  try {
    const res = await pageConversations(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.current = 1
  loadData()
}

function handleReset() {
  query.productCode = ''
  query.title = ''
  query.state = undefined
  handleSearch()
}

async function openDetail(row: ConversationDTO) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await getConversationDetail(row.id)
    detailData.value = res.data
  } finally {
    detailLoading.value = false
  }
}

function handleDelete(row: ConversationDTO) {
  ElMessageBox.confirm(`确认删除会话「${row.title}」吗？关联消息也将一并删除。`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteConversation(row.id)
      ElMessage.success('删除成功')
      loadData()
    })
    .catch(() => {})
}

function handleClearMessages(row: ConversationDTO) {
  ElMessageBox.confirm(`确认清空会话「${row.title}」的所有消息吗？会话本身保留。`, '提示', { type: 'warning' })
    .then(async () => {
      await clearConversationMessages(row.id)
      ElMessage.success('已清空消息')
      loadData()
    })
    .catch(() => {})
}

function stateText(state: number) {
  return state === 0 ? '活跃' : '归档'
}

function stateType(state: number) {
  return state === 0 ? 'success' : 'info'
}

function roleTag(role: string) {
  return role === 'user' ? '' : 'success'
}

function roleText(role: string) {
  return role === 'user' ? '用户' : role === 'assistant' ? 'AI' : role
}

function formatTime(time?: string) {
  if (!time) return '-'
  return time.replace('T', ' ')
}

function formatContextVariables(value?: string) {
  if (!value) return '-'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

onMounted(loadData)
</script>

<template>
  <div>
    <div class="page-header">
      <div class="page-header-left">
        <h2>会话管理</h2>
        <p>查看所有会话记录，支持详情浏览与消息管理</p>
      </div>
    </div>

    <div class="filter-bar">
      <el-input v-model="query.productCode" placeholder="产品编码" clearable style="width: 160px;" @keyup.enter="handleSearch" />
      <el-input v-model="query.title" placeholder="会话标题" clearable style="width: 180px;" @keyup.enter="handleSearch" />
      <el-select v-model="query.state" placeholder="状态" clearable style="width: 120px;">
        <el-option label="活跃" :value="0" />
        <el-option label="归档" :value="1" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
      <el-button :icon="Refresh" @click="handleReset">重置</el-button>
    </div>

    <div class="table-card">
      <el-table v-loading="loading" :data="tableData" stripe style="width: 100%">
        <el-table-column label="ID" prop="id" width="70" align="center" />
        <el-table-column label="会话" min-width="200">
          <template #default="{ row }">
            <div class="conv-cell">
              <div class="conv-icon">
                <el-icon><ChatDotRound /></el-icon>
              </div>
              <div class="conv-info">
                <div class="conv-title">{{ row.title || '(无标题)' }}</div>
                <div class="conv-code mono">{{ row.conversationCode }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="产品编码" prop="productCode" width="130">
          <template #default="{ row }">
            <span class="mono code-text">{{ row.productCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="模型" prop="modelName" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.modelName">{{ row.modelName }}</span>
            <span class="empty-text">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="stateType(row.state)" effect="light">{{ stateText(row.state) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最后消息" width="160" align="center">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.lastMessageTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160" align="center">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <div class="action-btns">
              <el-button type="primary" link :icon="View" @click="openDetail(row)">详情</el-button>
              <el-button type="warning" link :icon="Brush" @click="handleClearMessages(row)">清空</el-button>
              <el-button type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </div>

    <el-dialog v-model="detailVisible" title="会话详情" width="800px" top="5vh" destroy-on-close>
      <div v-loading="detailLoading">
        <template v-if="detailData">
          <el-descriptions :column="2" border size="small" style="margin-bottom: 16px;">
            <el-descriptions-item label="会话编码">
              <span class="mono">{{ detailData.conversation.conversationCode }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="标题">{{ detailData.conversation.title }}</el-descriptions-item>
            <el-descriptions-item label="产品编码">
              <span class="mono">{{ detailData.conversation.productCode }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="模型">{{ detailData.conversation.modelName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="上下文变量" :span="2">
              <pre class="context-json">{{ formatContextVariables(detailData.conversation.contextVariables) }}</pre>
            </el-descriptions-item>
            <el-descriptions-item label="最后消息">{{ formatTime(detailData.conversation.lastMessageTime) }}</el-descriptions-item>
          </el-descriptions>
          <div class="messages-container">
            <div v-for="msg in detailData.messages" :key="msg.id" class="msg-item">
              <div class="msg-header">
                <el-tag :type="roleTag(msg.role)" size="small" effect="light">{{ roleText(msg.role) }}</el-tag>
                <span class="msg-time">{{ formatTime(msg.createTime) }}</span>
                <span v-if="msg.role === 'assistant'" class="msg-meta">
                  {{ msg.tokenCount }} tokens · {{ msg.durationMs }}ms
                </span>
              </div>
              <div class="msg-content">{{ msg.content }}</div>
            </div>
            <el-empty v-if="!detailData.messages?.length" description="暂无消息" :image-size="80" />
          </div>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.conv-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.conv-icon {
  width: 36px;
  height: 36px;
  background: var(--c-accent-soft);
  color: var(--c-accent);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.conv-icon .el-icon {
  font-size: 18px;
}

.conv-info {
  min-width: 0;
}

.conv-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-code {
  font-size: 11px;
  color: var(--c-text-3);
  margin-top: 2px;
}

.code-text {
  font-size: 12px;
  color: var(--c-text-2);
}

.empty-text {
  color: var(--c-text-3);
  font-size: 13px;
}

.time-text {
  color: var(--c-text-3);
  font-size: 12px;
  font-family: var(--font-mono);
}

.mono {
  font-family: var(--font-mono);
}

.messages-container {
  max-height: 450px;
  overflow-y: auto;
  padding-right: 4px;
}

.msg-item {
  padding: 12px 0;
  border-bottom: 1px solid var(--c-border-subtle);
}

.msg-item:last-child {
  border-bottom: none;
}

.msg-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.msg-time {
  font-size: 12px;
  color: var(--c-text-3);
  font-family: var(--font-mono);
}

.msg-meta {
  font-size: 11px;
  color: var(--c-text-3);
  margin-left: auto;
  font-family: var(--font-mono);
}

.msg-content {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.65;
  font-size: 13px;
  color: var(--c-text-2);
}

.context-json {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--c-text-2);
  background: var(--c-bg-soft);
  padding: 10px;
  border-radius: 6px;
}
</style>
