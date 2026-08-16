<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Check } from '@element-plus/icons-vue'
import { listSkillBindOptions, bindSkills } from '@/api/skill'
import type { SkillBindOptionDTO } from '@/types/api'

const props = defineProps<{
  visible: boolean
  projectId: number
  projectName?: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}>()

const loading = ref(false)
const submitLoading = ref(false)
const searchKeyword = ref('')
const typeFilter = ref('all')
const allOptions = ref<SkillBindOptionDTO[]>([])
const selectedIds = ref<number[]>([])

const filteredOptions = computed(() => {
  return allOptions.value.filter((opt) => {
    const matchType = typeFilter.value === 'all' || opt.skillType === typeFilter.value
    const matchSearch =
      !searchKeyword.value ||
      opt.skillName.toLowerCase().includes(searchKeyword.value.toLowerCase()) ||
      (opt.description && opt.description.toLowerCase().includes(searchKeyword.value.toLowerCase()))
    return matchType && matchSearch
  })
})

const isAllSelected = computed(() => {
  return (
    filteredOptions.value.length > 0 &&
    filteredOptions.value.every((opt) => selectedIds.value.includes(opt.id))
  )
})

const isIndeterminate = computed(() => {
  const selectedInFiltered = filteredOptions.value.filter((opt) =>
    selectedIds.value.includes(opt.id)
  ).length
  return selectedInFiltered > 0 && selectedInFiltered < filteredOptions.value.length
})

const selectedCount = computed(() => selectedIds.value.length)
const totalCount = computed(() => allOptions.value.length)

async function loadOptions() {
  if (!props.projectId) return
  loading.value = true
  try {
    const res = await listSkillBindOptions(props.projectId)
    allOptions.value = res.data || []
    selectedIds.value = allOptions.value.filter((opt) => opt.bound).map((opt) => opt.id)
  } finally {
    loading.value = false
  }
}

watch(
  () => props.visible,
  (val) => {
    if (val && props.projectId) {
      loadOptions()
    }
  }
)

function handleClose() {
  emit('update:visible', false)
}

function toggleSelect(id: number) {
  const idx = selectedIds.value.indexOf(id)
  if (idx > -1) {
    selectedIds.value.splice(idx, 1)
  } else {
    selectedIds.value.push(id)
  }
}

function toggleSelectAll() {
  if (isAllSelected.value) {
    const filterIds = filteredOptions.value.map((o) => o.id)
    selectedIds.value = selectedIds.value.filter((id) => !filterIds.includes(id))
  } else {
    for (const opt of filteredOptions.value) {
      if (!selectedIds.value.includes(opt.id)) {
        selectedIds.value.push(opt.id)
      }
    }
  }
}

async function handleConfirm() {
  submitLoading.value = true
  try {
    await bindSkills({ id: props.projectId, skillIds: selectedIds.value })
    ElMessage.success('挂载成功')
    emit('success')
    emit('update:visible', false)
  } finally {
    submitLoading.value = false
  }
}

function skillTypeText(type: string) {
  const map: Record<string, string> = {
    PROMPT: '提示词模板',
    WORKFLOW: '工作流编排'
  }
  return map[type] || type
}

function skillTypeTag(type: string) {
  const map: Record<string, string> = {
    PROMPT: 'primary',
    WORKFLOW: 'success'
  }
  return map[type] || 'info'
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="`为项目「${projectName || ''}」挂载技能`"
    width="640px"
    destroy-on-close
    @close="handleClose"
  >
    <div class="dialog-toolbar">
      <el-input
        v-model="searchKeyword"
        :placeholder="'搜索技能名称或描述'"
        clearable
        :prefix-icon="Search"
        style="width: 260px"
      />
      <el-radio-group v-model="typeFilter" size="default">
        <el-radio-button value="all">全部</el-radio-button>
        <el-radio-button value="PROMPT">提示词</el-radio-button>
        <el-radio-button value="WORKFLOW">工作流</el-radio-button>
      </el-radio-group>
      <div class="selected-info">
        已选 <span class="num">{{ selectedCount }}</span> / {{ totalCount }}
      </div>
    </div>

    <div class="option-list" v-loading="loading">
      <div v-if="filteredOptions.length === 0 && !loading" class="empty-state">
        暂无可用技能
      </div>
      <div
        v-for="opt in filteredOptions"
        :key="opt.id"
        class="option-item"
        :class="{ selected: selectedIds.includes(opt.id) }"
        @click="toggleSelect(opt.id)"
      >
        <div class="option-check">
          <el-checkbox :model-value="selectedIds.includes(opt.id)" @click.stop />
        </div>
        <div class="option-body">
          <div class="option-header">
            <span class="option-name">{{ opt.skillName }}</span>
            <el-tag size="small" :type="skillTypeTag(opt.skillType)">{{ skillTypeText(opt.skillType) }}</el-tag>
          </div>
          <div class="option-desc">{{ opt.description || '暂无描述' }}</div>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="submitLoading" @click="handleConfirm">
        保存挂载
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.dialog-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.selected-info {
  margin-left: auto;
  font-size: 13px;
  color: var(--c-text-2);
}

.selected-info .num {
  color: var(--c-primary);
  font-weight: 600;
}

.option-list {
  max-height: 420px;
  overflow-y: auto;
  border: 1px solid var(--c-border-2);
  border-radius: 8px;
  padding: 4px;
}

.option-item {
  display: flex;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
}

.option-item:hover {
  background: var(--c-bg-2);
}

.option-item.selected {
  background: var(--c-primary-soft);
}

.option-check {
  flex-shrink: 0;
  padding-top: 2px;
}

.option-body {
  flex: 1;
  min-width: 0;
}

.option-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.option-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text-1);
}

.option-desc {
  font-size: 12px;
  color: var(--c-text-3);
  margin-top: 4px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.empty-state {
  padding: 40px 0;
  text-align: center;
  color: var(--c-text-3);
  font-size: 13px;
}
</style>
