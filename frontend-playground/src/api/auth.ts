import { http } from './request'
import type { AdminLoginResponse } from '@/types/api'

export function login(username: string, password: string) {
  return http.post<AdminLoginResponse>('/admin/auth/login', { username, password })
}
