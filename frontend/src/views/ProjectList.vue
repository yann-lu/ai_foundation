<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search, Refresh, Delete, Edit, Document } from '@element-plus/icons-vue'
import { pageProjects, createProject, updateProject, deleteProject } from '@/api/project'
import BindCapabilityDialog from '@/components/BindCapabilityDialog.vue'
import { Setting } from '@element-plus/icons-vue'
import type { AgentProjectDTO } from '@/types/api'

const variableTemplate = '[\n  {\n    "name": "blocCode",\n    "label": "集团编码",\n    "type": "string",\n    "required": true\n  },\n  {\n    "name": "hotelCode",\n    "label": "酒店编码",\n    "type": "string",\n    "required": true\n  }\n]'

const loading = ref(false)
const tableData = ref<AgentProjectDTO[]>([])
const total = ref(0)

const query = reactive({
  projectName: '',
  projectCode: '',
  state: undefined as number | undefined,
  current: 1,
  size: 10
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const bindDialogVisible = ref(false)
const currentProjectId = ref<number>(0)
const currentProjectName = ref('')
const form = reactive<AgentProjectDTO>({
  projectName: '',
  projectCode: '',
  description: '',
  systemPrompt: '',
  promptVariables: '',
  state: 1
})

const rules: FormRules = {
  projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  projectCode: [{ required: true, message: '请输入项目编码', trigger: 'blur' }]
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageProjects(query)
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
  query.projectName = ''
  query.projectCode = ''
  query.state = undefined
  handleSearch()
}

function openCreate() {
  dialogTitle.value = '新增项目'
  Object.assign(form, {
    id: undefined,
    projectName: '',
    projectCode: '',
    description: '',
    systemPrompt: '',
    promptVariables: '',
    state: 1
  })
  dialogVisible.value = true
}

function openEdit(row: AgentProjectDTO) {
  dialogTitle.value = '编辑项目'
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (!validatePromptVariables()) return
    submitLoading.value = true
    try {
      if (form.id) {
        await updateProject(form)
        ElMessage.success('修改成功')
      } else {
        await createProject(form)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      loadData()
    } finally {
      submitLoading.value = false
    }
  })
}

function fillVariableTemplate() {
  form.promptVariables = variableTemplate
}

function validatePromptVariables(): boolean {
  if (!form.promptVariables?.trim()) return true
  try {
    const parsed = JSON.parse(form.promptVariables)
    if (!Array.isArray(parsed)) {
      ElMessage.warning('变量定义必须是 JSON 数组')
      return false
    }
    return true
  } catch {
    ElMessage.warning('变量定义不是合法 JSON')
    return false
  }
}

function openBindDialog(row: AgentProjectDTO) {
  currentProjectId.value = row.id!
  currentProjectName.value = row.projectName
  bindDialogVisible.value = true
}

function handleBindSuccess() {
  loadData()
}

function handleDelete(row: AgentProjectDTO) {
  ElMessageBox.confirm(`确认删除项目「${row.projectName}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteProject(row.id!)
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
  return time.replace('T', ' ')
}

function hasVariables(vars?: string) {
  if (!vars?.trim()) return false
  try {
    const parsed = JSON.parse(vars)
    return Array.isArray(parsed) && parsed.length > 0
  } catch {
    return false
  }
}

onMounted(loadData)
</script>

<template>
  <div>
    <div class="page-header">
      <div class="page-header-left">
        <h2>项目配置</h2>
        <p>管理 Agent 项目，配置系统提示词与上下文变量</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增项目</el-button>
    </div>

    <div class="filter-bar">
      <el-input v-model="query.projectName" placeholder="项目名称" clearable style="width: 200px;" @keyup.enter="handleSearch" />
      <el-input v-model="query.projectCode" placeholder="项目编码" clearable style="width: 180px;" @keyup.enter="handleSearch" />
      <el-select v-model="query.state" placeholder="状态" clearable style="width: 120px;">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
      <el-button :icon="Refresh" @click="handleReset">重置</el-button>
    </div>

    <div class="table-card">
      <el-table v-loading="loading" :data="tableData" stripe style="width: 100%">
        <el-table-column label="ID" prop="id" width="70" align="center" />
        <el-table-column label="项目名称" prop="projectName" min-width="160">
          <template #default="{ row }">
            <div class="project-cell">
              <div class="project-icon">
                <el-icon><Document /></el-icon>
              </div>
              <div>
                <div class="project-name">{{ row.projectName }}</div>
                <div class="project-code mono">{{ row.projectCode }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="描述" prop="description" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.description" class="desc-text">{{ row.description }}</span>
            <span v-else class="empty-text">—</span>
          </template>
        </el-table-column>
        <el-table-column label="变量" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="hasVariables(row.promptVariables)" size="small" type="success">已配置</el-tag>
            <span v-else class="empty-text">—</span>
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
        <el-table-column label="操作" width="260" fixed="right" align="center">
          <template #default="{ row }">
            <div class="action-btns">
              <el-button type="primary" link :icon="Setting" @click="openBindDialog(row)">挂载能力</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="760px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="项目名称" prop="projectName">
          <el-input v-model="form.projectName" placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="项目编码" prop="projectCode">
          <el-input v-model="form.projectCode" placeholder="请输入项目编码" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="项目描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="可选，简短描述项目用途" />
        </el-form-item>
        <el-form-item label="系统提示词" prop="systemPrompt">
          <el-input
            v-model="form.systemPrompt"
            type="textarea"
            :rows="6"
            placeholder="项目固定系统提示词，可使用 {{变量名}} 引用会话变量"
          />
        </el-form-item>
        <el-form-item label="变量定义" prop="promptVariables">
          <div class="json-editor">
            <el-input
              v-model="form.promptVariables"
              type="textarea"
              :rows="8"
              placeholder='JSON 数组，如 [{"name":"tenantId","type":"string","required":true}]'
            />
            <el-button size="small" @click="fillVariableTemplate">填入酒店变量模板</el-button>
          </div>
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

    <BindCapabilityDialog
      v-model:visible="bindDialogVisible"
      :project-id="currentProjectId"
      :project-name="currentProjectName"
      @success="handleBindSuccess"
    />
  </div>
</template>

<style scoped>
.project-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.project-icon {
  width: 36px;
  height: 36px;
  background: var(--c-primary-soft);
  color: var(--c-primary);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.project-icon .el-icon {
  font-size: 18px;
}

.project-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text-1);
}

.project-code {
  font-size: 11px;
  color: var(--c-text-3);
  margin-top: 2px;
}

.desc-text {
  color: var(--c-text-2);
  font-size: 13px;
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

.json-editor {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.json-editor :deep(textarea) {
  font-family: var(--font-mono);
  font-size: 12px;
}

.mono {
  font-family: var(--font-mono);
}
</style>
