<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search, Refresh, Delete, Edit } from '@element-plus/icons-vue'
import { pageModels, createModel, updateModel, deleteModel } from '@/api/model'
import { pageProjects } from '@/api/project'
import type { AgentModelConfigDTO, AgentProjectDTO } from '@/types/api'

const loading = ref(false)
const tableData = ref<AgentModelConfigDTO[]>([])
const total = ref(0)
const projects = ref<AgentProjectDTO[]>([])

const query = reactive({
  projectId: undefined as number | undefined,
  modelName: '',
  modelType: undefined as string | undefined,
  state: undefined as number | undefined,
  current: 1,
  size: 10
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<AgentModelConfigDTO>({ projectId: undefined as any, modelName: '', modelType: 'CHAT', state: 1 })

const rules: FormRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  modelType: [{ required: true, message: '请选择模型类型', trigger: 'change' }]
}

async function loadProjects() {
  const res = await pageProjects({ current: 1, size: 100 })
  projects.value = res.data.records
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageModels(query)
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
  query.projectId = undefined
  query.modelName = ''
  query.modelType = undefined
  query.state = undefined
  handleSearch()
}

function openCreate() {
  dialogTitle.value = '新增模型配置'
  Object.assign(form, { id: undefined, projectId: query.projectId, modelName: '', modelType: 'CHAT', state: 1 })
  dialogVisible.value = true
}

function openEdit(row: AgentModelConfigDTO) {
  dialogTitle.value = '编辑模型配置'
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
        await updateModel(form)
        ElMessage.success('修改成功')
      } else {
        await createModel(form)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      loadData()
    } finally {
      submitLoading.value = false
    }
  })
}

function handleDelete(row: AgentModelConfigDTO) {
  ElMessageBox.confirm(`确认删除模型「${row.modelName}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteModel(row.id!)
      ElMessage.success('删除成功')
      loadData()
    })
    .catch(() => {})
}

function projectName(id: number) {
  return projects.value.find((p) => p.id === id)?.projectName || id
}

function stateText(state: number) {
  return state === 1 ? '启用' : '停用'
}

function stateType(state: number) {
  return state === 1 ? 'success' : 'info'
}

function typeTag(t: string) {
  return t === 'CHAT' ? '' : 'warning'
}

function formatTime(time?: string) {
  if (!time) return '-'
  return time.replace('T', ' ')
}

onMounted(async () => {
  await loadProjects()
  await loadData()
})
</script>

<template>
  <div>
    <div class="filter-bar">
      <el-select v-model="query.projectId" placeholder="所属项目" clearable filterable style="width: 200px;" @change="handleSearch">
        <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
      </el-select>
      <el-input v-model="query.modelName" placeholder="模型名称" clearable style="width: 180px;" @keyup.enter="handleSearch" />
      <el-select v-model="query.modelType" placeholder="模型类型" clearable style="width: 140px;">
        <el-option label="对话模型 (CHAT)" value="CHAT" />
        <el-option label="向量模型 (EMBEDDING)" value="EMBEDDING" />
      </el-select>
      <el-select v-model="query.state" placeholder="状态" clearable style="width: 120px;">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
      <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      <el-button type="primary" :icon="Plus" @click="openCreate" style="margin-left: auto;">新增模型</el-button>
    </div>
    <div class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column label="ID" prop="id" width="70" />
        <el-table-column label="所属项目" width="160">
          <template #default="{ row }">{{ projectName(row.projectId) }}</template>
        </el-table-column>
        <el-table-column label="模型名称" prop="modelName" min-width="160" />
        <el-table-column label="模型类型" width="120">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.modelType)">{{ row.modelType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="stateType(row.state)">{{ stateText(row.state) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建人" prop="createUser" width="100" />
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="所属项目" prop="projectId">
          <el-select v-model="form.projectId" placeholder="请选择项目" filterable style="width: 100%;" :disabled="!!form.id">
            <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="form.modelName" placeholder="如 deepseek-v4-pro、bge-m3" />
        </el-form-item>
        <el-form-item label="模型类型" prop="modelType">
          <el-radio-group v-model="form.modelType" :disabled="!!form.id">
            <el-radio value="CHAT">对话模型</el-radio>
            <el-radio value="EMBEDDING">向量模型</el-radio>
          </el-radio-group>
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
.action-btns {
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}
</style>
