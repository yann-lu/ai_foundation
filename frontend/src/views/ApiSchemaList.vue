<script setup lang="ts">
import type { FormInstance, FormRules } from "element-plus"
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh, Delete, Edit } from '@element-plus/icons-vue'
import {
  pageApiSchema,
  createApiSchema,
  updateApiSchema,
  deleteApiSchema
} from '@/api/apiSchema'
import type { ApiSchemaConfigDTO } from '@/types/api'

const loading = ref(false)
const tableData = ref<ApiSchemaConfigDTO[]>([])
const total = ref(0)

const query = reactive({
  keyword: '',
  state: undefined as number | undefined,
  current: 1,
  size: 20
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

const defaultForm = (): ApiSchemaConfigDTO => ({
  schemaCode: '',
  schemaName: '',
  baseUrl: '',
  commandPrefix: '',
  state: 1
})

const form = reactive<ApiSchemaConfigDTO>(defaultForm())

const rules: FormRules = {
  schemaCode: [
    { required: true, message: '请输入Schema编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_-]+$/, message: '仅支持字母、数字、下划线和中划线', trigger: 'blur' }
  ],
  schemaName: [{ required: true, message: '请输入Schema名称', trigger: 'blur' }],
  baseUrl: [{ required: true, message: '请输入Base URL', trigger: 'blur' }]
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageApiSchema(query)
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
  query.state = undefined
  handleSearch()
}

function openCreate() {
  dialogTitle.value = '新增网关服务'
  Object.assign(form, defaultForm())
  dialogVisible.value = true
}

function openEdit(row: ApiSchemaConfigDTO) {
  dialogTitle.value = '编辑网关服务'
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
        await updateApiSchema(form)
        ElMessage.success('修改成功')
      } else {
        await createApiSchema(form)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      loadData()
    } finally {
      submitLoading.value = false
    }
  })
}

function handleDelete(row: ApiSchemaConfigDTO) {
  ElMessageBox.confirm(`确认删除网关服务「${row.schemaName}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteApiSchema(row.id!)
      ElMessage.success('删除成功')
      loadData()
    })
    .catch(() => {})
}

function stateText(state: number) {
  return state === 1 ? '启用' : '停用'
}

function stateType(state: number) {
  return state === 1 ? 'success' : 'info'
}

function formatTime(time?: string) {
  if (!time) return '-'
  return time.replace('T', ' ').slice(0, 19)
}

onMounted(loadData)
</script>

<template>
  <div>
    <div class="page-header">
      <div class="page-header-left">
        <h2>网关服务配置</h2>
        <p>管理 API 网关服务配置，配置 baseUrl 供 CLI 接口调用</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增网关服务</el-button>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="query.keyword"
        placeholder="搜索编码/名称"
        clearable
        style="width: 220px"
        @keyup.enter="handleSearch"
      />
      <el-select v-model="query.state" placeholder="状态" clearable style="width: 120px">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
      <el-button :icon="Refresh" @click="handleReset">重置</el-button>
    </div>

    <div class="table-card">
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column label="编码" prop="schemaCode" width="180">
          <template #default="{ row }">
            <span class="code-text">{{ row.schemaCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="服务名称" prop="schemaName" width="200" />
        <el-table-column label="Base URL" prop="baseUrl" min-width="280" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="url-text">{{ row.baseUrl }}</span>
          </template>
        </el-table-column>
        <el-table-column label="命令前缀" prop="commandPrefix" width="120">
          <template #default="{ row }">
            <span v-if="row.commandPrefix" class="prefix-text">{{ row.commandPrefix }}</span>
            <span v-else class="empty-text">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="stateType(row.state)" effect="light" size="small">
              {{ stateText(row.state) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="160" align="center">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.updateTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <div class="action-btns">
              <el-button type="primary" link :icon="Edit" @click="openEdit(row)">编辑</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="Schema编码" prop="schemaCode">
          <el-input v-model="form.schemaCode" :disabled="!!form.id" placeholder="如 PMS-GATEWAY" />
        </el-form-item>
        <el-form-item label="服务名称" prop="schemaName">
          <el-input v-model="form.schemaName" placeholder="如 PMS 网关服务" />
        </el-form-item>
        <el-form-item label="Base URL" prop="baseUrl">
          <el-input v-model="form.baseUrl" placeholder="https://pms-gateway.example.com/api" class="mono-input" />
        </el-form-item>
        <el-form-item label="命令前缀" prop="commandPrefix">
          <el-input v-model="form.commandPrefix" placeholder="可选，关联的 CLI 命令前缀，如 epms" />
        </el-form-item>
        <el-form-item label="状态" prop="state">
          <el-switch v-model="form.state" :active-value="1" :inactive-value="0" />
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
.code-text {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text-1);
}

.url-text {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--c-text-2);
}

.prefix-text {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--c-primary);
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

.action-btns {
  display: flex;
  gap: 4px;
  justify-content: center;
}

.mono-input :deep(input) {
  font-family: var(--font-mono);
}
</style>
