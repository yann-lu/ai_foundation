<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search, Refresh, Delete, Edit } from '@element-plus/icons-vue'
import { pageProjects, createProject, updateProject, deleteProject } from '@/api/project'
import type { AgentProjectDTO } from '@/types/api'

const loading = ref(false)
const tableData = ref<AgentProjectDTO[]>([])
const total = ref(0)

const query = reactive({ projectName: '', projectCode: '', state: undefined as number | undefined, current: 1, size: 10 })

const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<AgentProjectDTO>({ projectName: '', projectCode: '', description: '', state: 1 })

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
  Object.assign(form, { id: undefined, projectName: '', projectCode: '', description: '', state: 1 })
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

onMounted(loadData)
</script>

<template>
  <div>
    <div class="filter-bar">
      <el-input v-model="query.projectName" placeholder="项目名称" clearable style="width: 200px;" @keyup.enter="handleSearch" />
      <el-input v-model="query.projectCode" placeholder="项目编码" clearable style="width: 180px;" @keyup.enter="handleSearch" />
      <el-select v-model="query.state" placeholder="状态" clearable style="width: 120px;">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
      <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      <el-button type="primary" :icon="Plus" @click="openCreate" style="margin-left: auto;">新增项目</el-button>
    </div>
    <div class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column label="ID" prop="id" width="70" />
        <el-table-column label="项目名称" prop="projectName" min-width="140" />
        <el-table-column label="项目编码" prop="projectCode" width="140" />
        <el-table-column label="描述" prop="description" min-width="180" show-overflow-tooltip />
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
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="项目名称" prop="projectName">
          <el-input v-model="form.projectName" placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="项目编码" prop="projectCode">
          <el-input v-model="form.projectCode" placeholder="请输入项目编码" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="项目描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="可选" />
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
