<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Delete, View, Brush } from '@element-plus/icons-vue'
import { pageConversations, getConversationDetail, deleteConversation, clearConversationMessages } from '@/api/conversation'
import type { ConversationDTO, ConversationPageRequest, ConversationDetailDTO, MessageDTO } from '@/types/api'

const loading = ref(false)
const tableData = ref<ConversationDTO[]>([])
const total = ref(0)

const query = reactive<ConversationPageRequest>({
  productCode: '', title: '', state: undefined, current: 1, size: 10
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

onMounted(loadData)
</script>

<template>
  <div>
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
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column label="ID" prop="id" width="70" />
        <el-table-column label="会话标题" prop="title" min-width="140" show-overflow-tooltip />
        <el-table-column label="会话编码" prop="conversationCode" width="220" show-overflow-tooltip />
        <el-table-column label="产品编码" prop="productCode" width="120" />
        <el-table-column label="模型" prop="modelName" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.modelName">{{ row.modelName }}</span>
            <span v-else style="color: #c0c4cc;">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="stateType(row.state)">{{ stateText(row.state) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最后消息" width="170">
          <template #default="{ row }">
            {{ formatTime(row.lastMessageTime) }}
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
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

    <el-dialog v-model="detailVisible" title="会话详情" width="780px" top="5vh">
      <div v-loading="detailLoading">
        <template v-if="detailData">
          <el-descriptions :column="2" border size="small" style="margin-bottom: 16px;">
            <el-descriptions-item label="会话编码">{{ detailData.conversation.conversationCode }}</el-descriptions-item>
            <el-descriptions-item label="标题">{{ detailData.conversation.title }}</el-descriptions-item>
            <el-descriptions-item label="产品编码">{{ detailData.conversation.productCode }}</el-descriptions-item>
            <el-descriptions-item label="模型">{{ detailData.conversation.modelName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="集团/酒店">{{ detailData.conversation.blocCode }} / {{ detailData.conversation.hotelCode }}</el-descriptions-item>
            <el-descriptions-item label="最后消息">{{ formatTime(detailData.conversation.lastMessageTime) }}</el-descriptions-item>
          </el-descriptions>
          <div style="max-height: 400px; overflow-y: auto;">
            <div v-for="msg in detailData.messages" :key="msg.id" class="msg-item">
              <el-tag :type="roleTag(msg.role)" size="small" style="margin-right: 8px;">{{ roleText(msg.role) }}</el-tag>
              <span class="msg-time">{{ formatTime(msg.createTime) }}</span>
              <div class="msg-content">{{ msg.content }}</div>
            </div>
            <el-empty v-if="!detailData.messages?.length" description="暂无消息" />
          </div>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.action-btns {
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}
.msg-item {
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}
.msg-time {
  font-size: 12px;
  color: #999;
}
.msg-content {
  margin-top: 6px;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
}
</style>
