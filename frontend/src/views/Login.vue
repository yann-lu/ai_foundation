<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({ username: 'admin', password: 'admin123' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await auth.login(form.username, form.password)
      ElMessage.success('登录成功')
      router.replace('/')
    } catch {
      // 错误已在拦截器提示
    } finally {
      loading.value = false
    }
  })
}
</script>

<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-brand">
        <div class="login-brand-icon">AI</div>
        <h1 class="login-title">AI Foundation</h1>
        <p class="login-subtitle">Agent 编排平台 · 管理后台</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" @keyup.enter="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" :prefix-icon="Lock" size="large" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" style="width: 100%; height: 44px;" :loading="loading" @click="handleLogin">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
      <p class="login-hint">默认账号 <code>admin</code> / <code>admin123</code></p>
    </div>
  </div>
</template>

<style scoped>
.login-brand {
  text-align: center;
  margin-bottom: 28px;
}

.login-brand-icon {
  width: 52px;
  height: 52px;
  margin: 0 auto 16px;
  background: linear-gradient(135deg, var(--c-primary), var(--c-accent));
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.02em;
  box-shadow: 0 4px 12px rgba(13, 148, 136, 0.3);
}

.login-title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: var(--c-text-1);
  letter-spacing: -0.01em;
}

.login-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--c-text-3);
}

.login-hint {
  text-align: center;
  color: var(--c-text-3);
  font-size: 12px;
  margin: 8px 0 0;
}

.login-hint code {
  background: var(--c-bg-soft);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--c-text-2);
}

.login-card :deep(.el-input__wrapper) {
  height: 44px;
  padding: 0 12px;
  box-shadow: 0 0 0 1px var(--c-border) inset;
  border-radius: 10px;
  transition: box-shadow 0.15s ease;
}

.login-card :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--c-border-strong) inset;
}

.login-card :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px var(--c-primary-soft), 0 0 0 1px var(--c-primary) inset;
}

.login-card :deep(.el-button--primary) {
  font-weight: 600;
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(13, 148, 136, 0.25);
  transition: all 0.15s ease;
}

.login-card :deep(.el-button--primary:hover) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(13, 148, 136, 0.3);
}
</style>
