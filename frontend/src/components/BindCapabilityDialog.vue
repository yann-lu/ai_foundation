<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Check } from '@element-plus/icons-vue'
import { listBindOptions, bindCapabilities } from '@/api/cli'
import type { BindCapabilityOptionDTO } from '@/types/api'

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
const allOptions = ref<BindCapabilityOptionDTO[]>([])
const selectedIds = ref<number[]>([])

const filteredOptions = computed(() => {
  return allOptions.value.filter((opt) => {
    const matchType = typeFilter.value === 'all' || opt.commandType === typeFilter.value
    const matchSearch =
      !searchKeyword.value ||
      opt.commandName.toLowerCase().includes(searchKeyword.value.toLowerCase()) ||
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
    const res = await listBindOptions(props.projectId)
    allOptions.value = res.data.cliOptions || []
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
    const newIds = filteredOptions.value.map((o) => o.id)
    const set = new Set(selectedIds.value)
    newIds.forEach((id) => set.add(id))
    selectedIds.value = Array.from(set)
  }
}

async function handleConfirm() {
  submitLoading.value = true
  try {
    await bindCapabilities({
      id: props.projectId,
      cliIds: selectedIds.value
    })
    ElMessage.success('挂载成功')
    emit('success')
    handleClose()
  } finally {
    submitLoading.value = false
  }
}

function typeTagType(type: string) {
  return type === 'API' ? 'primary' : 'warning'
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="挂载能力"
    width="680px"
    destroy-on-close
    class="bind-dialog"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
    @close="handleClose"
  >
    <div class="dialog-subtitle">
      项目：<span class="project-name">{{ projectName }}</span>
    </div>

    <div class="search-bar">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索命令名 / 描述"
        :prefix-icon="Search"
        clearable
      />
    </div>

    <div class="type-tabs">
      <span
        class="type-tab"
        :class="{ active: typeFilter === 'all' }"
        @click="typeFilter = 'all'"
      >
        全部
      </span>
      <span
        class="type-tab"
        :class="{ active: typeFilter === 'API' }"
        @click="typeFilter = 'API'"
      >
        API 型
      </span>
      <span
        class="type-tab"
        :class="{ active: typeFilter === 'PAGE' }"
        @click="typeFilter = 'PAGE'"
      >
        PAGE 型
      </span>
    </div>

    <div class="option-list-wrapper" v-loading="loading">
      <div class="select-all-bar">
        <el-checkbox
          :model-value="isAllSelected"
          :indeterminate="isIndeterminate"
          @change="toggleSelectAll"
        >
          全选当前筛选结果（{{ filteredOptions.length }} 项）
        </el-checkbox>
      </div>

      <div class="option-list" v-if="filteredOptions.length > 0">
        <div
          v-for="opt in filteredOptions"
          :key="opt.id"
          class="option-item"
          :class="{ selected: selectedIds.includes(opt.id) }"
          @click="toggleSelect(opt.id)"
        >
          <div class="option-check">
            <el-checkbox :model-value="selectedIds.includes(opt.id)" />
          </div>
          <div class="option-info">
            <div class="option-name">
              {{ opt.commandName }}
              <el-tag size="small" :type="typeTagType(opt.commandType)" effect="light" class="type-tag">
                {{ opt.commandType }}
              </el-tag>
            </div>
            <div class="option-desc">{{ opt.description || '—' }}</div>
          </div>
          <div class="option-bound">
            <el-icon v-if="opt.bound" class="bound-icon"><Check /></el-icon>
            <span v-if="opt.bound" class="bound-text">已挂载</span>
          </div>
        </div>
      </div>
      <div v-else-if="!loading" class="empty-tip">没有匹配的命令</div>
    </div>

    <div class="footer-bar">
      <span class="footer-info">
        共 {{ totalCount }} 条命令，已选择 <span class="selected-count">{{ selectedCount }}</span> 条
      </span>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="submitLoading" @click="handleConfirm">
        确定挂载
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.dialog-subtitle {
  font-size: 13px;
  color: var(--c-text-2);
  margin-bottom: 12px;
}

.project-name {
  font-weight: 600;
  color: var(--c-text-1);
}

.search-bar {
  margin-bottom: 12px;
}

.type-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--c-border-light);
  padding-bottom: 8px;
}

.type-tab {
  padding: 4px 14px;
  font-size: 13px;
  color: var(--c-text-2);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
}

.type-tab:hover {
  background: var(--c-surface-hover);
}

.type-tab.active {
  background: var(--c-primary-bg);
  color: var(--c-primary);
  font-weight: 600;
}

.option-list-wrapper {
  max-height: 400px;
  overflow-y: auto;
  border: 1px solid var(--c-border-light);
  border-radius: 8px;
}

.select-all-bar {
  padding: 8px 14px;
  background: var(--c-surface);
  border-bottom: 1px solid var(--c-border-light);
  position: sticky;
  top: 0;
  z-index: 1;
  font-size: 13px;
}

.option-list {
  display: flex;
  flex-direction: column;
}

.option-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--c-border-light);
  cursor: pointer;
  transition: background 0.15s;
}

.option-item:last-child {
  border-bottom: none;
}

.option-item:hover {
  background: var(--c-surface-hover);
}

.option-item.selected {
  background: var(--c-primary-bg);
}

.option-check {
  flex-shrink: 0;
}

.option-info {
  flex: 1;
  min-width: 0;
}

.option-name {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text-1);
  display: flex;
  align-items: center;
  gap: 8px;
}

.type-tag {
  font-weight: 400;
}

.option-desc {
  font-size: 12px;
  color: var(--c-text-3);
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.option-bound {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 4px;
}

.bound-icon {
  color: var(--c-success);
  font-size: 16px;
}

.bound-text {
  font-size: 12px;
  color: var(--c-success);
}

.empty-tip {
  padding: 40px;
  text-align: center;
  color: var(--c-text-3);
  font-size: 13px;
}

.footer-bar {
  margin-top: 12px;
  text-align: right;
}

.footer-info {
  font-size: 12px;
  color: var(--c-text-3);
}

.selected-count {
  color: var(--c-primary);
  font-weight: 600;
}
</style>
