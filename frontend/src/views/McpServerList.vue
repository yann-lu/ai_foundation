<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search, Refresh, Delete, Edit } from '@element-plus/icons-vue'
import {
  pageMcpServers, createMcpServer, updateMcpServer, deleteMcpServer
} from '@/api/mcp'
import type { AgentMcpServerDTO } from '@/types/api'

const loading = ref(false)
const tableData = ref<AgentMcpServerDTO[]>([])
const total = ref(0)

const query = reactive({
  keyword: '',
  transportType: undefined as string | undefined,
  state: undefined as number | undefined,
  current: 1,
  size: 10
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<AgentMcpServerDTO>({
  serverCode: '',
  serverName: '',
  description: '',
  transportType: 'stdio',
  command: '',
  workingDir: '',
  envVars: '',
  baseUrl: '',
  authType: 'NONE',
  authConfig: '',
  state: 1
})

const rules: FormRules = {
  serverCode: [{ required: true, message: '请输入服务器编码', trigger: 'blur' }],
  serverName: [{ required: true, message: '请输入服务器名称', trigger: 'blur' }],
  transportType: [{ required: true, message: '请选择传输方式', trigger: 'change' }],
  command: [{
    validator: (_r, v, cb) => {
      if (form.transportType === 'stdio' && !v) return cb(new Error('stdio 方式下启动命令必填'))
      cb()
    },
    trigger: 'blur'
  }],
  baseUrl: [{
    validator: (_r, v, cb) => {
      if ((form.transportType === 'sse' || form.transportType === 'http') && !v) {
        return cb(new Error('sse/http 方式下 Base URL 必填'))
      }
      cb()
    },
    trigger: 'blur'
  }]
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageMcpServers(query)
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
  query.keyword = ''
  query.transportType = undefined
  query.state = undefined
  handleSearch()
}

function openCreate() {
  dialogTitle.value = '新增 MCP 服务器'
  Object.assign(form, {
    id: undefined,
    serverCode: '',
    serverName: '',
    description: '',
    transportType: 'stdio',
    command: '',
    workingDir: '',
    envVars: '',
    baseUrl: '',
    authType: 'NONE',
    authConfig: '',
    state: 1
  })
  dialogVisible.value = true
}

function openEdit(row: AgentMcpServerDTO) {
  dialogTitle.value = '编辑 MCP 服务器'
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (form.id) {
        await updateMcpServer(form)
        ElMessage.success('修改成功')
      } else {
        await createMcpServer(form)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      loadData()
    } finally {
      submitLoading.value = false
    }
  })
}

function handleDelete(row: AgentMcpServerDTO) {
  ElMessageBox.confirm(
    `确认删除 MCP 服务器「${row.serverName}」吗？被 CLI 工具引用的服务器无法删除。`,
    '提示',
    { type: 'warning' }
  )
    .then(async () => {
      await deleteMcpServer(row.id!)
      ElMessage.success('删除成功')
      loadData()
    })
    .catch(() => {})
}

function stateText(state: number) { return state === 1 ? '启用' : '停用' }
function stateType(state: number) { return state === 1 ? 'success' : 'info' }

function transportText(t: string) {
  const map: Record<string, string> = { stdio: 'STDIO', sse: 'SSE', http: 'HTTP' }
  return map[t] || t
}
function transportTagType(t: string) {
  const map: Record<string, string> = { stdio: 'primary', sse: 'warning', http: 'success' }
  return map[t] || 'info'
}

function formatTime(time?: string) {
  if (!time) return '-'
  return time.replace('T', ' ')
}

onMounted(loadData)
</script>

<template>
  <div>
    <div class="page-header">
      <div class="page-header-left">
        <h2>MCP 服务器</h2>
        <p>管理 MCP Server 实例（stdio / SSE / HTTP），供 CLI 工具引用</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增 MCP 服务器</el-button>
    </div>

    <div class="filter-bar">
      <el-input v-model="query.keyword" placeholder="名称/编码" clearable style="width: 220px;" :prefix-icon="Search" @keyup.enter="handleSearch" />
      <el-select v-model="query.transportType" placeholder="传输方式" clearable style="width: 140px;">
        <el-option label="STDIO" value="stdio" />
        <el-option label="SSE" value="sse" />
        <el-option label="HTTP" value="http" />
      </el-select>
      <el-select v-model="query.state" placeholder="状态" clearable style="width: 110px;">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button :icon="Refresh" @click="handleReset">重置</el-button>
    </div>

    <div class="table-card" v-loading="loading">
      <el-table :data="tableData" stripe>
        <el-table-column label="服务器" min-width="200">
          <template #default="{ row }">
            <div class="server-cell">
              <span class="server-name">{{ row.serverName }}</span>
              <span class="server-code">{{ row.serverCode }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="传输方式" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="transportTagType(row.transportType)">{{ transportText(row.transportType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启动命令 / Base URL" min-width="280">
          <template #default="{ row }">
            <span class="command-text">{{ row.transportType === 'stdio' ? row.command : row.baseUrl }}</span>
          </template>
        </el-table-column>
        <el-table-column label="描述" min-width="200">
          <template #default="{ row }">
            <span class="desc-text">{{ row.description || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="stateType(row.state)" effect="light">{{ stateText(row.state) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="170" align="center">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.updateTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="720px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="服务器编码" prop="serverCode">
          <el-input v-model="form.serverCode" placeholder="英文唯一标识，如 bing_cn_enhanced" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="服务器名称" prop="serverName">
          <el-input v-model="form.serverName" placeholder="如：必应中文搜索增强版" />
        </el-form-item>
        <el-form-item label="传输方式" prop="transportType">
          <el-radio-group v-model="form.transportType">
            <el-radio value="stdio">STDIO（本地子进程）</el-radio>
            <el-radio value="sse">SSE</el-radio>
            <el-radio value="http">HTTP</el-radio>
          </el-radio-group>
        </el-form-item>

        <template v-if="form.transportType === 'stdio'">
          <el-form-item label="启动命令" prop="command">
            <el-input v-model="form.command" placeholder="如：npx -y bing-cn-mcp-enhanced" />
          </el-form-item>
          <el-form-item label="工作目录" prop="workingDir">
            <el-input v-model="form.workingDir" placeholder="可选，子进程的工作目录" />
          </el-form-item>
          <el-form-item label="环境变量" prop="envVars">
            <el-input
              v-model="form.envVars"
              type="textarea"
              :rows="3"
              placeholder='JSON 格式，如 {"API_KEY":"xxx"}，无环境变量可留空'
            />
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="Base URL" prop="baseUrl">
            <el-input v-model="form.baseUrl" placeholder="如：https://example.com/mcp/sse" />
          </el-form-item>
          <el-form-item label="鉴权类型" prop="authType">
            <el-radio-group v-model="form.authType">
              <el-radio value="NONE">无</el-radio>
              <el-radio value="BEARER">Bearer Token</el-radio>
              <el-radio value="BASIC">Basic Auth</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="form.authType !== 'NONE'" label="鉴权配置" prop="authConfig">
            <el-input
              v-model="form.authConfig"
              type="textarea"
              :rows="3"
              placeholder='JSON 格式，如 {"token":"xxx"} 或 {"username":"u","password":"p"}'
            />
          </el-form-item>
        </template>

        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="可选，描述服务器用途" />
        </el-form-item>
        <el-form-item label="状态" prop="state">
          <el-radio-group v-model="form.state">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.server-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.server-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text-1);
}

.server-code {
  font-size: 11px;
  color: var(--c-text-3);
  font-family: var(--font-mono);
}

.command-text {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--c-text-2);
}

.desc-text {
  color: var(--c-text-2);
  font-size: 13px;
}

.time-text {
  color: var(--c-text-3);
  font-size: 12px;
  font-family: var(--font-mono);
}
</style>
