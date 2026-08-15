<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import {
  Folder, Setting, ChatDotRound, Cpu, Fold, Expand, Tools,
  ArrowDown, Sunny, Moon
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const isCollapse = ref(false)
const isDark = ref(false)

function handleLogout() {
  auth.logout()
  router.replace('/login')
}

const menus = [
  { index: '/project', title: '项目配置', icon: Folder, desc: '管理 Agent 项目' },
  { index: '/api-schema', title: '网关服务', icon: Setting, desc: 'API网关服务配置' },
  { index: '/cli', title: 'CLI 能力', icon: Tools, desc: 'CLI 命令管理' },
  { index: '/model', title: '模型配置', icon: Setting, desc: '配置模型接入' },
  { index: '/conversation', title: '会话管理', icon: ChatDotRound, desc: '查看与会话操作' },
  { index: '/playground', title: 'Playground', icon: Cpu, desc: '实时调试 Agent' }
]

const activeTitle = computed(() => route.meta.title || '')
</script>

<template>
  <el-container style="height: 100vh">
    <el-aside :width="isCollapse ? '60px' : '232px'" class="app-aside">
      <div class="sidebar-brand">
        <div class="sidebar-brand-icon">AI</div>
        <span v-if="!isCollapse" class="sidebar-brand-text">AI Foundation</span>
      </div>
      <el-menu
        :default-active="route.path"
        :collapse="isCollapse"
        :collapse-transition="false"
        background-color="#ffffff"
        text-color="#4b5563"
        active-text-color="#0d9488"
        router
        class="app-menu"
      >
        <el-menu-item v-for="m in menus" :key="m.index" :index="m.index">
          <el-icon><component :is="m.icon" /></el-icon>
          <template #title>
            <span class="menu-title">{{ m.title }}</span>
          </template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="app-header" height="56px">
        <div style="display: flex; align-items: center; gap: 12px;">
          <el-icon
            class="header-icon-btn"
            @click="isCollapse = !isCollapse"
          >
            <component :is="isCollapse ? Expand : Fold" />
          </el-icon>
          <span class="page-title">{{ activeTitle }}</span>
        </div>

        <div style="display: flex; align-items: center; gap: 8px;">
          <el-icon class="header-icon-btn" title="主题">
            <component :is="isDark ? Sunny : Moon" />
          </el-icon>
          <el-dropdown>
            <span class="user-dropdown">
              <el-avatar :size="30" class="user-avatar">
                {{ auth.nickname.charAt(0) || 'A' }}
              </el-avatar>
              <span class="user-name">{{ auth.nickname || '管理员' }}</span>
              <el-icon class="user-arrow"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="page-container">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.app-menu {
  padding: 8px;
  border-right: none !important;
}

.app-menu :deep(.el-menu-item) {
  height: 40px;
  line-height: 40px;
  margin-bottom: 2px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.15s ease;
}

.app-menu :deep(.el-menu-item:hover) {
  background: var(--c-surface-hover);
  color: var(--c-text-1);
}

.app-menu :deep(.el-menu-item.is-active) {
  background: var(--c-primary-bg);
  color: var(--c-primary);
  font-weight: 600;
}

.app-menu :deep(.el-menu-item.is-active::before) {
  content: '';
  position: absolute;
  left: -8px;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 20px;
  background: var(--c-primary);
  border-radius: 0 3px 3px 0;
}

.app-menu :deep(.el-icon) {
  font-size: 18px;
}

.header-icon-btn {
  cursor: pointer;
  font-size: 18px;
  color: var(--c-text-2);
  padding: 6px;
  border-radius: 6px;
  transition: all 0.15s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.header-icon-btn:hover {
  background: var(--c-surface-hover);
  color: var(--c-text-1);
}

.user-dropdown {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background 0.15s ease;
}

.user-dropdown:hover {
  background: var(--c-surface-hover);
}

.user-avatar {
  background: linear-gradient(135deg, var(--c-primary), var(--c-accent));
  font-weight: 600;
}

.user-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--c-text-1);
}

.user-arrow {
  font-size: 12px;
  color: var(--c-text-3);
}

.menu-title {
  font-weight: 500;
}
</style>
