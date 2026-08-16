<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search, Refresh, Delete, Edit } from '@element-plus/icons-vue'
import { pageSkills, createSkill, updateSkill, deleteSkill } from '@/api/skill'
import { pageCli as pageClis } from '@/api/cli'
import type { AgentSkillDTO } from '@/types/api'

const loading = ref(false)
const tableData = ref<AgentSkillDTO[]>([])
const total = ref(0)

const query = reactive({
  keyword: '',
  skillType: undefined as string | undefined,
  state: undefined as number | undefined,
  current: 1,
  size: 10
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<AgentSkillDTO>({
  skillName: '',
  skillCode: '',
  description: '',
  skillType: 'PROMPT',
  systemPrompt: '',
  configJson: '{}',
  state: 1,
  cliIds: []
})

const rules: FormRules = {
  skillName: [{ required: true, message: '请输入技能名称', trigger: 'blur' }],
  skillCode: [{ required: true, message: '请输入技能编码', trigger: 'blur' }]
}

const cliOptions = ref<any[]>([])
const cliLoading = ref(false)
const cliSearch = ref('')
const filteredCliOptions = computed(() => {
  if (!cliSearch.value) return cliOptions.value
  const kw = cliSearch.value.toLowerCase()
  return cliOptions.value.filter((c: any) =>
    c.commandName?.toLowerCase().includes(kw) ||
    c.description?.toLowerCase().includes(kw)
  )
})

async function loadData() {
  loading.value = true
  try {
    const res = await pageSkills(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function loadCliOptions() {
  if (cliOptions.value.length > 0) return
  cliLoading.value = true
  try {
    const res = await pageClis({ current: 1, size: 500, state: 1 })
    cliOptions.value = res.data.records || []
  } finally {
    cliLoading.value = false
  }
}

function handleSearch() {
  query.current = 1
  loadData()
}

function handleReset() {
  query.keyword = ''
  query.skillType = undefined
  query.state = undefined
  handleSearch()
}

async function openCreate() {
  dialogTitle.value = '新增技能'
  Object.assign(form, {
    id: undefined,
    skillName: '',
    skillCode: '',
    description: '',
    skillType: 'PROMPT',
    systemPrompt: '',
    configJson: '{}',
    state: 1,
    cliIds: []
  })
  await loadCliOptions()
  dialogVisible.value = true
}

async function openEdit(row: AgentSkillDTO) {
  dialogTitle.value = '编辑技能'
  Object.assign(form, { ...row })
  form.cliIds = row.cliIds || []
  await loadCliOptions()
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (form.id) {
        await updateSkill(form)
        ElMessage.success('修改成功')
      } else {
        await createSkill(form)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      loadData()
    } finally {
      submitLoading.value = false
    }
  })
}

function handleDelete(row: AgentSkillDTO) {
  ElMessageBox.confirm(`确认删除技能「${row.skillName}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteSkill(row.id!)
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

function skillTypeText(type: string) {
  const map: Record<string, string> = {
    PROMPT: '提示词模板',
    WORKFLOW: '工作流编排'
  }
  return map[type] || type
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
        <h2>技能管理</h2>
        <p>管理 Agent 技能，包括提示词模板与关联 CLI 命令</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增技能</el-button>
    </div>

    <div class="filter-bar">
      <el-input v-model="query.keyword" placeholder="技能名称/编码" clearable style="width: 200px;" :prefix-icon="Search" @keyup.enter="handleSearch" />
      <el-select v-model="query.skillType" placeholder="技能类型" clearable style="width: 140px;">
        <el-option label="提示词模板" value="PROMPT" />
        <el-option label="工作流编排" value="WORKFLOW" />
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
        <el-table-column label="技能名称" min-width="160">
          <template #default="{ row }">
            <div class="skill-cell">
              <span class="skill-name">{{ row.skillName }}</span>
              <span class="skill-code">{{ row.skillCode }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="primary">{{ skillTypeText(row.skillType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="描述" min-width="200">
          <template #default="{ row }">
            <span class="desc-text">{{ row.description || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="CLI命令" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.cliIds?.length" size="small">{{ row.cliIds.length }} 个</el-tag>
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
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="技能名称" prop="skillName">
          <el-input v-model="form.skillName" placeholder="请输入技能名称" />
        </el-form-item>
        <el-form-item label="技能编码" prop="skillCode">
          <el-input v-model="form.skillCode" placeholder="请输入技能编码（英文标识）" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="技能类型" prop="skillType">
          <el-radio-group v-model="form.skillType">
            <el-radio value="PROMPT">提示词模板</el-radio>
            <el-radio value="WORKFLOW">工作流编排</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="技能描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="可选，描述技能用途" />
        </el-form-item>
        <el-form-item label="系统提示词" prop="systemPrompt">
          <el-input
            v-model="form.systemPrompt"
            type="textarea"
            :rows="6"
            placeholder="技能专属系统提示词，挂载到项目后会追加到项目提示词中"
          />
        </el-form-item>
        <el-form-item label="关联CLI命令">
          <div class="cli-selector">
            <el-input
              v-model="cliSearch"
              placeholder="搜索CLI命令"
              clearable
              size="small"
              style="width: 240px"
            />
            <div class="cli-list" v-loading="cliLoading">
              <el-checkbox-group v-model="form.cliIds">
                <div v-for="cli in filteredCliOptions" :key="cli.id" class="cli-item">
                  <el-checkbox :value="cli.id">
                    <span class="cli-name">{{ cli.commandName }}</span>
                    <span class="cli-desc">{{ cli.description }}</span>
                  </el-checkbox>
                </div>
                <div v-if="filteredCliOptions.length === 0 && !cliLoading" class="cli-empty">
                  暂无可用CLI命令
                </div>
              </el-checkbox-group>
            </div>
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
  </div>
</template>

<style scoped>
.skill-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.skill-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text-1);
}

.skill-code {
  font-size: 11px;
  color: var(--c-text-3);
  font-family: var(--font-mono);
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

.cli-selector {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cli-list {
  width: 100%;
  max-height: 200px;
  overflow-y: auto;
  border: 1px solid var(--c-border-2);
  border-radius: 6px;
  padding: 8px;
}

.cli-item {
  padding: 4px 6px;
  border-radius: 4px;
}

.cli-item:hover {
  background: var(--c-bg-2);
}

.cli-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--c-text-1);
  margin-right: 8px;
}

.cli-desc {
  font-size: 12px;
  color: var(--c-text-3);
}

.cli-empty {
  text-align: center;
  color: var(--c-text-3);
  padding: 20px 0;
  font-size: 13px;
}
</style>
