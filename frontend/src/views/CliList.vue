<script setup lang="ts">
import { onMounted, reactive, ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  Plus,
  Search,
  Refresh,
  Delete,
  Edit,
  Setting,
  InfoFilled,
  MagicStick
} from '@element-plus/icons-vue'
import {
  pageCli,
  getCli,
  createCli,
  updateCli,
  deleteCli
} from '@/api/cli'
import { listEnabledApiSchema } from '@/api/apiSchema'
import type { ApiSchemaConfigDTO } from '@/types/api'
import type {
  CliCommandDTO,
  CliCommandDetailDTO,
  CliParamDTO,
  CliRecallTagDTO
} from '@/types/api'

const loading = ref(false)
const tableData = ref<CliCommandDTO[]>([])
const total = ref(0)

const query = reactive({
  keyword: '',
  commandType: '' as string,
  commandPrefix: '' as string,
  state: undefined as number | undefined,
  current: 1,
  size: 20
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const activeTab = ref('basic')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

// 顶部提示 + 服务选择器状态
const showAdvancedEdit = ref(false)
const schemaOptions = ref<ApiSchemaConfigDTO[]>([])
const apiMatchMode = ref<'list' | 'path'>('path')
const isCommandNameManual = ref(false)

const defaultForm = (): CliCommandDetailDTO => ({
  commandName: '',
  commandPrefix: '',
  commandGroup: '',
  commandAction: '',
  cliTemplate: '',
  description: '',
  commandType: 'API',
  state: 1,
  params: [],
  tool: { url: '', method: 'POST' },
  page: { pageRoute: '' },
  recallTags: []
})

const form = reactive<CliCommandDetailDTO>(defaultForm())

const rules: FormRules = {
  commandPrefix: [{ required: true, message: '请输入命令前缀', trigger: 'blur' }],
  commandGroup: [{ required: true, message: '请输入命令分组', trigger: 'blur' }],
  commandAction: [{ required: true, message: '请输入命令动作', trigger: 'blur' }],
  commandName: [
    { required: true, message: '请输入命令名', trigger: 'blur' },
    { pattern: /^[a-z0-9_]+$/, message: '仅支持小写字母、数字和下划线', trigger: 'blur' }
  ],
  description: [{ required: true, message: '请输入功能描述', trigger: 'blur' }],
  commandType: [{ required: true, message: '请选择命令类型', trigger: 'change' }]
}

// ====== 命令预览 ======
const commandPreview = computed(() => {
  const prefix = form.commandPrefix || 'prefix'
  const type = form.commandType === 'API' ? 'cli api' : 'cli page'
  const group = form.commandGroup || 'group'
  const action = form.commandAction || 'action'
  const paramStr = form.params
    .map((p) => {
      const name = p.paramName || 'param'
      return p.isRequired === 1 ? `<${name}>(required)` : `<${name}>`
    })
    .join(' ')
  return `${prefix} ${type} ${group} ${action} ${paramStr}`.trim()
})

// ====== 命令名自动生成 ======
function autoGenerateCommandName() {
  if (!form.commandPrefix || !form.commandGroup || !form.commandAction) return
  const typePart = form.commandType === 'API' ? 'cli_api' : 'cli_page'
  form.commandName = `${form.commandPrefix}_${typePart}_${form.commandGroup}_${form.commandAction}`
  form.cliTemplate = commandPreview.value
}

watch(
  () => [form.commandPrefix, form.commandGroup, form.commandAction, form.commandType],
  () => {
    if (!form.id && !isCommandNameManual.value) {
      autoGenerateCommandName()
    }
  }
)

// 参数变化时同步模板
watch(
  () => form.params.length,
  () => {
    if (!showAdvancedEdit.value) {
      form.cliTemplate = commandPreview.value
    }
  }
)

async function loadData() {
  loading.value = true
  try {
    const res = await pageCli(query)
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
  query.commandType = ''
  query.commandPrefix = ''
  query.state = undefined
  handleSearch()
}

function openCreate() {
  dialogTitle.value = '新增 CLI 命令'
  Object.assign(form, defaultForm())
  form.params = []
  form.recallTags = []
  form.tool = { url: '', method: 'POST' }
  form.page = { pageRoute: '' }
  activeTab.value = 'basic'
  showAdvancedEdit.value = false
  isCommandNameManual.value = false
  apiMatchMode.value = 'path'
  loadSchemaOptions()
  dialogVisible.value = true
}

async function loadSchemaOptions() {
  if (schemaOptions.value.length > 0) return
  try {
    const res = await listEnabledApiSchema()
    schemaOptions.value = res.data
  } catch (e) {
    // ignore
  }
}

async function openEdit(row: CliCommandDTO) {
  dialogTitle.value = '编辑 CLI 命令'
  const res = await getCli(row.id!)
  Object.assign(form, res.data)
  if (!form.params) form.params = []
  if (!form.recallTags) form.recallTags = []
  if (form.commandType === 'API' && !form.tool) {
    form.tool = { url: '', method: 'POST' }
  }
  if (form.commandType === 'PAGE' && !form.page) {
    form.page = { pageRoute: '' }
  }
  activeTab.value = 'basic'
  showAdvancedEdit.value = false
  isCommandNameManual.value = true
  dialogVisible.value = true
}

function addParam() {
  const newParam: CliParamDTO = {
    paramName: '',
    paramType: 'String',
    isRequired: 0,
    description: '',
    sortOrder: form.params.length
  }
  form.params.push(newParam)
}

function removeParam(index: number) {
  form.params.splice(index, 1)
}

function moveParam(index: number, direction: number) {
  const newIndex = index + direction
  if (newIndex < 0 || newIndex >= form.params.length) return
  const temp = form.params[index]
  form.params[index] = form.params[newIndex]
  form.params[newIndex] = temp
  form.params.forEach((p, i) => (p.sortOrder = i))
}

function addRecallTag() {
  const newTag: CliRecallTagDTO = {
    tagType: 'ALIAS',
    tagValue: '',
    weight: 0,
    matchMode: 'exact',
    sortOrder: form.recallTags.length
  }
  form.recallTags.push(newTag)
}

function removeRecallTag(index: number) {
  form.recallTags.splice(index, 1)
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (form.id) {
        await updateCli(form as any)
        ElMessage.success('修改成功')
      } else {
        await createCli(form as any)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      loadData()
    } finally {
      submitLoading.value = false
    }
  })
}

function handleDelete(row: CliCommandDTO) {
  ElMessageBox.confirm(
    `确认删除命令「${row.commandName}」吗？已挂载的项目将自动解绑。`,
    '提示',
    { type: 'warning' }
  )
    .then(async () => {
      await deleteCli(row.id!)
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

function typeTagType(type: string) {
  return type === 'API' ? 'primary' : 'warning'
}

function formatTime(time?: string) {
  if (!time) return '-'
  return time.replace('T', ' ').slice(0, 19)
}

const paramTypes = ['String', 'Number', 'Boolean', 'Array', 'Object']
const httpMethods = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH']

const tagTypeOptions = [
  { value: 'ALIAS', label: 'ALIAS 用户说法' },
  { value: 'OP', label: 'OP 操作码' },
  { value: 'DOMAIN', label: 'DOMAIN 业务域' },
  { value: 'SLOT', label: 'SLOT 分槽' }
]

const authTypeOptions = [
  { value: 'NONE', label: 'NONE - 无需鉴权' },
  { value: 'HEADER', label: 'HEADER - Header 鉴权' }
]

const displayTypeOptions = [
  { value: 'PAGE', label: '页面' },
  { value: 'MODAL', label: '弹窗' }
]

const targetTypeOptions = [
  { value: 'INTERNAL', label: '站内' },
  { value: 'EXTERNAL', label: '站外' }
]

onMounted(loadData)
</script>

<template>
  <div>
    <div class="page-header">
      <div class="page-header-left">
        <h2>CLI 能力管理</h2>
        <p>管理 Agent 可调用的 CLI 命令（API 调用 / 页面跳转）</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增命令</el-button>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="query.keyword"
        placeholder="搜索命令名/描述"
        clearable
        style="width: 220px"
        @keyup.enter="handleSearch"
      />
      <el-select v-model="query.commandType" placeholder="命令类型" clearable style="width: 130px">
        <el-option label="API 型" value="API" />
        <el-option label="PAGE 型" value="PAGE" />
      </el-select>
      <el-input
        v-model="query.commandPrefix"
        placeholder="命令前缀"
        clearable
        style="width: 140px"
        @keyup.enter="handleSearch"
      />
      <el-select v-model="query.state" placeholder="状态" clearable style="width: 110px">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
      <el-button :icon="Refresh" @click="handleReset">重置</el-button>
    </div>

    <div class="table-card">
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column label="命令名" width="220">
          <template #default="{ row }">
            <span class="command-name">{{ row.commandName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTagType(row.commandType)" effect="light" size="small">
              {{ row.commandType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="前缀" prop="commandPrefix" width="100" />
        <el-table-column label="分组" prop="commandGroup" width="110" />
        <el-table-column label="描述" prop="description" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.description" class="desc-text">{{ row.description }}</span>
            <span v-else class="empty-text">—</span>
          </template>
        </el-table-column>
        <el-table-column label="已挂载" width="90" align="center">
          <template #default="{ row }">
            <span class="bound-count">{{ row.boundCount || 0 }}</span>
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
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="820px"
      destroy-on-close
      class="cli-dialog"
    >
      <!-- 顶部提示 -->
      <div class="form-top-hint">
        <el-icon><InfoFilled /></el-icon>
        <span>Tool 参数将自动从下方命令参数同步（paramName / paramType / 必填 / 描述）</span>
      </div>

      <el-tabs v-model="activeTab">
        <!-- ===== Tab 1: 基本信息 ===== -->
        <el-tab-pane label="基本信息" name="basic">
          <div class="section-header">
            <span class="section-title">命令结构</span>
            <span class="section-subtitle">（选择 API 后自动生成）</span>
          </div>

          <div class="cli-preview-row">
            <div class="cli-label">CLI 命令</div>
            <div class="cli-preview-box">
              <code class="cli-preview-text">{{ commandPreview }}</code>
            </div>
            <el-button type="primary" link @click="showAdvancedEdit = !showAdvancedEdit">
              {{ showAdvancedEdit ? '收起编辑' : '高级编辑' }}
            </el-button>
          </div>

          <div v-if="showAdvancedEdit" class="advanced-edit-box">
            <el-input
              v-model="form.cliTemplate"
              type="textarea"
              :rows="2"
              placeholder="手动编辑命令模板"
              class="mono-input"
            />
          </div>

          <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
            <el-row :gutter="16">
              <el-col :span="8">
                <el-form-item label="命令前缀" prop="commandPrefix">
                  <el-input v-model="form.commandPrefix" placeholder="如 epms" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="命令分组" prop="commandGroup">
                  <el-input v-model="form.commandGroup" placeholder="如 order" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="命令动作" prop="commandAction">
                  <el-input v-model="form.commandAction" placeholder="如 query" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="命令标识" prop="commandName">
              <el-input
                v-model="form.commandName"
                :disabled="!isCommandNameManual && !form.id"
                placeholder="自动生成，可点击手动编辑"
                class="mono-input"
              />
              <div v-if="!isCommandNameManual && !form.id" class="form-tip">
                由前缀/分组/动作自动生成，
                <el-button type="primary" link @click="isCommandNameManual = true">手动编辑</el-button>
              </div>
            </el-form-item>
            <el-form-item label="命令模板" prop="cliTemplate">
              <el-input v-model="form.cliTemplate" placeholder="CLI 模板示例，供大模型参考" />
            </el-form-item>
            <el-form-item label="功能描述" prop="description">
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="3"
                placeholder="供大模型理解的功能描述"
              />
            </el-form-item>
            <el-form-item label="命令类型" prop="commandType">
              <el-radio-group v-model="form.commandType">
                <el-radio value="API">API 接口调用</el-radio>
                <el-radio value="PAGE">PAGE 页面跳转</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="状态" prop="state">
              <el-switch v-model="form.state" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- ===== Tab 2: 参数配置 ===== -->
        <el-tab-pane label="参数配置" name="params">
          <div class="section-header">
            <span class="section-title">命令参数</span>
            <span class="section-table">agent_cli_param</span>
          </div>

          <div class="add-link-row">
            <el-button type="primary" link :icon="Plus" @click="addParam" class="add-link-btn">
              添加参数
            </el-button>
            <span class="param-hint">必填参数在命令模板中会自动追加 (required)</span>
          </div>

          <div v-if="form.params.length === 0" class="empty-tip">暂无参数，点击上方按钮添加</div>

          <el-table v-else :data="form.params" border size="default" class="param-table">
            <el-table-column label="排序" width="60" align="center">
              <template #default="{ $index }">{{ $index + 1 }}</template>
            </el-table-column>
            <el-table-column label="参数名" min-width="160">
              <template #default="{ row }">
                <el-input v-model="row.paramName" size="small" placeholder="参数名" />
              </template>
            </el-table-column>
            <el-table-column label="参数类型" width="130">
              <template #default="{ row }">
                <el-select v-model="row.paramType" size="small" style="width: 100%">
                  <el-option v-for="t in paramTypes" :key="t" :label="t" :value="t" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="元素类型" width="120">
              <template #default="{ row }">
                <el-input
                  v-model="row.itemType"
                  size="small"
                  :disabled="row.paramType !== 'Array'"
                  placeholder="仅 Array 时"
                />
              </template>
            </el-table-column>
            <el-table-column label="必填" width="70" align="center">
              <template #default="{ row }">
                <el-switch
                  v-model="row.isRequired"
                  :active-value="1"
                  :inactive-value="0"
                  size="small"
                />
              </template>
            </el-table-column>
            <el-table-column label="默认值" width="120">
              <template #default="{ row }">
                <el-input v-model="row.defaultValue" size="small" placeholder="默认值" />
              </template>
            </el-table-column>
            <el-table-column label="参数描述" min-width="200">
              <template #default="{ row }">
                <el-input v-model="row.description" size="small" placeholder="参数描述" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template #default="{ $index }">
                <el-button type="danger" link @click="removeParam($index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ===== Tab 3: 实现定义 ===== -->
        <el-tab-pane label="实现定义" name="impl">
          <!-- API 型 -->
          <div v-if="form.commandType === 'API'">
            <div class="section-header">
              <span class="section-title">API 配置</span>
              <span class="section-table">agent_tool_definition</span>
            </div>

            <div class="service-selector-row">
              <el-select
                v-model="form.tool!.schemaCode"
                placeholder="选择网关服务"
                style="width: 280px"
                @focus="loadSchemaOptions()"
              >
                <el-option
                  v-for="opt in schemaOptions"
                  :key="opt.id"
                  :label="opt.schemaName + ' (' + opt.schemaCode + ')'"
                  :value="opt.schemaCode"
                />
              </el-select>
              <el-radio-group v-model="apiMatchMode" size="default">
                <el-radio-button value="list">从列表选择</el-radio-button>
                <el-radio-button value="path">按地址匹配</el-radio-button>
              </el-radio-group>
              <span v-if="form.tool?.url" class="matched-info">
                已匹配 {{ form.tool.method }} {{ form.tool.url }}
              </span>
            </div>

            <el-form label-width="100px">
              <el-row :gutter="24">
                <el-col :span="12">
                  <el-form-item label="请求方式">
                    <el-select v-model="form.tool!.method" style="width: 100%">
                      <el-option v-for="m in httpMethods" :key="m" :label="m" :value="m" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="鉴权类型">
                    <el-select v-model="form.tool!.authType" style="width: 100%">
                      <el-option
                        v-for="opt in authTypeOptions"
                        :key="opt.value"
                        :label="opt.label"
                        :value="opt.value"
                      />
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-form-item label="接口地址">
                <el-input
                  v-model="form.tool!.url"
                  placeholder="/schedule/remind/add"
                  class="mono-input"
                />
              </el-form-item>
              <el-form-item label="工具描述">
                <el-input
                  v-model="form.tool!.description"
                  type="textarea"
                  :rows="2"
                  placeholder="简述该工具的功能，用于 Tool 级别的描述"
                />
              </el-form-item>
              <el-form-item label="请求 Schema">
                <el-input
                  v-model="form.tool!.requestSchema"
                  type="textarea"
                  :rows="6"
                  placeholder='JSON Schema，如 {"type":"object","properties":{...}}'
                  class="json-textarea"
                />
              </el-form-item>
              <el-form-item label="响应 Schema">
                <el-input
                  v-model="form.tool!.responseSchema"
                  type="textarea"
                  :rows="6"
                  placeholder="JSON Schema，描述响应数据结构"
                  class="json-textarea"
                />
              </el-form-item>
            </el-form>
          </div>

          <!-- PAGE 型 -->
          <div v-if="form.commandType === 'PAGE'">
            <div class="section-header">
              <span class="section-title">页面配置</span>
              <span class="section-table">agent_page_definition</span>
            </div>

            <el-form label-width="100px">
              <el-form-item label="页面名称">
                <el-input v-model="form.page!.pageName" placeholder="订单详情页" />
              </el-form-item>
              <el-form-item label="页面路由">
                <el-input
                  v-model="form.page!.pageRoute"
                  placeholder="/order/detail?id={{id}}"
                  class="mono-input"
                />
              </el-form-item>
              <el-form-item label="跳转前缀">
                <el-input v-model="form.page!.pagePrefix" placeholder="https:// 或 app://" />
              </el-form-item>
              <el-row :gutter="24">
                <el-col :span="12">
                  <el-form-item label="展示类型">
                    <el-select v-model="form.page!.displayType" style="width: 100%">
                      <el-option
                        v-for="opt in displayTypeOptions"
                        :key="opt.value"
                        :label="opt.label"
                        :value="opt.value"
                      />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="目标类型">
                    <el-select v-model="form.page!.targetType" style="width: 100%">
                      <el-option
                        v-for="opt in targetTypeOptions"
                        :key="opt.value"
                        :label="opt.label"
                        :value="opt.value"
                      />
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-form-item label="资源项目">
                <el-input v-model="form.page!.resourceProject" placeholder="OAuth 资源池代码" />
              </el-form-item>
              <el-form-item label="资源 IDs">
                <el-input v-model="form.page!.resourceIds" placeholder="逗号分隔" />
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>

        <!-- ===== Tab 4: 召回标签 ===== -->
        <el-tab-pane label="召回标签" name="tags">
          <div class="section-header">
            <span class="section-title">召回标签</span>
            <span class="section-table">agent_cli_recall_tag</span>
          </div>

          <div class="add-link-row">
            <el-button type="primary" link :icon="Plus" @click="addRecallTag" class="add-link-btn">
              添加标签
            </el-button>
            <span class="param-hint">
              ALIAS 用户说法别名 / OP 操作码 / DOMAIN 业务域 / SLOT 分槽
            </span>
          </div>

          <div v-if="form.recallTags.length === 0" class="empty-tip">
            暂无召回标签，点击上方按钮添加
          </div>

          <el-table v-else :data="form.recallTags" border size="default">
            <el-table-column label="类型" width="180">
              <template #default="{ row }">
                <el-select v-model="row.tagType" size="small" style="width: 100%">
                  <el-option
                    v-for="opt in tagTypeOptions"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="标签值" min-width="240">
              <template #default="{ row }">
                <el-input v-model="row.tagValue" size="small" placeholder="用户说法或规范码" />
              </template>
            </el-table-column>
            <el-table-column label="匹配模式" width="150">
              <template #default="{ row }">
                <el-select v-model="row.matchMode" size="small" style="width: 100%">
                  <el-option label="exact 精确" value="exact" />
                  <el-option label="contains 包含" value="contains" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="权重" width="100">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.weight"
                  :min="0"
                  :max="100"
                  size="small"
                  style="width: 100%"
                />
              </template>
            </el-table-column>
            <el-table-column label="排序" width="100">
              <template #default="{ row }">
                <el-input-number v-model="row.sortOrder" :min="0" size="small" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center">
              <template #default="{ $index }">
                <el-button type="danger" link @click="removeRecallTag($index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.command-name {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text-1);
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

.bound-count {
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--c-primary);
  font-weight: 600;
}

.action-btns {
  display: flex;
  gap: 4px;
  justify-content: center;
}

/* ===== Dialog ===== */

:deep(.cli-dialog .el-dialog__body) {
  padding-top: 0;
}

.form-top-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  margin-bottom: 16px;
  background: var(--c-surface);
  border-radius: 8px;
  font-size: 13px;
  color: var(--c-text-2);
}

.form-top-hint .el-icon {
  color: var(--c-info);
  font-size: 16px;
}

.section-header {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 2px solid var(--c-primary-bg);
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--c-text-1);
}

.section-table {
  font-size: 12px;
  color: var(--c-text-3);
  font-family: var(--font-mono);
}

.section-subtitle {
  font-size: 12px;
  color: var(--c-text-3);
}

.mono-input :deep(input),
.mono-input :deep(textarea) {
  font-family: var(--font-mono);
}

.cli-preview-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}

.cli-label {
  width: 100px;
  flex-shrink: 0;
  text-align: right;
  color: var(--c-text-2);
  font-size: 14px;
  line-height: 24px;
  padding-top: 4px;
}

.cli-preview-box {
  flex: 1;
  background: var(--c-surface);
  border: 1px solid var(--c-border-light);
  border-radius: 6px;
  padding: 12px 14px;
  min-height: 40px;
}

.cli-preview-text {
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--c-text-1);
  line-height: 1.6;
  word-break: break-all;
}

.advanced-edit-box {
  margin-left: 112px;
  margin-bottom: 16px;
}

.form-tip {
  margin-top: 4px;
  font-size: 12px;
  color: var(--c-text-3);
}

.add-link-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.add-link-btn {
  font-weight: 500;
  padding: 0 !important;
}

.param-hint {
  font-size: 12px;
  color: var(--c-text-3);
}

.empty-tip {
  padding: 24px;
  text-align: center;
  color: var(--c-text-3);
  font-size: 13px;
  background: var(--c-surface);
  border-radius: 8px;
  border: 1px dashed var(--c-border-light);
}

.param-table {
  border-radius: 6px;
  overflow: hidden;
}

.param-table :deep(.el-table__cell) {
  padding: 6px 8px;
}

.service-selector-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.matched-info {
  font-size: 12px;
  color: var(--c-success);
  font-family: var(--font-mono);
}

.json-textarea :deep(textarea) {
  font-family: var(--font-mono);
  font-size: 12px;
}
</style>
