import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue')
  },
  {
    path: '/',
    component: () => import('@/layout/Layout.vue'),
    redirect: '/project',
    children: [
      {
        path: 'project',
        name: 'Project',
        component: () => import('@/views/ProjectList.vue'),
        meta: { title: '项目配置' }
      },
      {
        path: 'api-schema',
        name: 'ApiSchema',
        component: () => import('@/views/ApiSchemaList.vue'),
        meta: { title: '网关服务配置' }
      },
      {
        path: 'cli',
        name: 'Cli',
        component: () => import('@/views/CliList.vue'),
        meta: { title: 'CLI 能力管理' }
      },
      {
        path: 'mcp',
        name: 'Mcp',
        component: () => import('@/views/McpServerList.vue'),
        meta: { title: 'MCP 服务器' }
      },
      {
        path: 'skill',
        name: 'Skill',
        component: () => import('@/views/SkillList.vue'),
        meta: { title: '技能管理' }
      },
      {
        path: 'model',
        name: 'Model',
        component: () => import('@/views/ModelConfigList.vue'),
        meta: { title: '模型配置' }
      },
      {
        path: 'conversation',
        name: 'Conversation',
        component: () => import('@/views/ConversationList.vue'),
        meta: { title: '会话管理' }
      },
      {
        path: 'playground',
        name: 'Playground',
        component: () => import('@/views/Playground.vue'),
        meta: { title: 'Playground' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const token = localStorage.getItem('x-admin-token')
  if (!token && to.path !== '/login') {
    return '/login'
  }
  return true
})

export default router
