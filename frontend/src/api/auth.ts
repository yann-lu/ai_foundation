import request from './request'
import type { AdminLoginResponse } from '@/types/api'

export function login(username: string, password: string) {
  return request.post<AdminLoginResponse, { data: AdminLoginResponse }>('/admin/auth/login', { username, password })
}
