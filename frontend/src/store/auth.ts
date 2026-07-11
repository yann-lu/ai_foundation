import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('x-admin-token') || '')
  const nickname = ref(localStorage.getItem('admin-nickname') || '')

  async function login(username: string, password: string) {
    const res = await loginApi(username, password)
    token.value = res.data.token
    nickname.value = res.data.nickname
    localStorage.setItem('x-admin-token', res.data.token)
    localStorage.setItem('admin-nickname', res.data.nickname)
  }

  function logout() {
    token.value = ''
    nickname.value = ''
    localStorage.removeItem('x-admin-token')
    localStorage.removeItem('admin-nickname')
  }

  return { token, nickname, login, logout }
})
