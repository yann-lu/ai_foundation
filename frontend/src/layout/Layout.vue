<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import { Folder, Setting, ChatDotRound, Monitor, Fold, Expand, ArrowDown } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const isCollapse = ref(false)

function handleLogout() {
  auth.logout()
  router.replace('/login')
}

const menus = [
  { index: '/project', title: '项目配置', icon: Folder },
  { index: '/model', title: '模型配置', icon: Setting },
  { index: '/conversation', title: '会话管理', icon: ChatDotRound },
  { index: '/playground', title: 'Playground', icon: Monitor }
]
</script>

<template>
  <el-container style="height: 100vh">
    <el-aside :width="isCollapse ? '64px' : '210px'" class="app-aside">
      <div style="height: 60px; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 17px; font-weight: 600; white-space: nowrap; overflow: hidden;">
        <span v-if="!isCollapse">AI Foundation</span>
        <span v-else>AI</span>
      </div>
      <el-menu
        :default-active="route.path"
        :collapse="isCollapse"
        background-color="#1e3a5f"
        text-color="#c0d4e8"
        active-text-color="#fff"
        router
      >
        <el-menu-item v-for="m in menus" :key="m.index" :index="m.index">
          <el-icon><component :is="m.icon" /></el-icon>
          <template #title>{{ m.title }}</template>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="app-header" height="60px">
        <div style="display: flex; align-items: center; gap: 12px;">
          <el-icon style="cursor: pointer; font-size: 20px;" @click="isCollapse = !isCollapse">
            <component :is="isCollapse ? Expand : Fold" />
          </el-icon>
          <span style="font-size: 16px; font-weight: 500;">{{ route.meta.title || 'AI Foundation 管理后台' }}</span>
        </div>
        <el-dropdown>
          <span style="cursor: pointer; display: flex; align-items: center; gap: 8px;">
            <el-avatar :size="30" style="background: #2c5f8a;">{{ auth.nickname.charAt(0) || 'A' }}</el-avatar>
            {{ auth.nickname || '管理员' }}
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main :class="['page-container', { 'playground-page-container': route.path === '/playground' }]">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
