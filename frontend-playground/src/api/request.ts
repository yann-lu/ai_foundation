import type { ApiResponse } from '@/types/api'

const BASE = ''

export class ApiError extends Error {
  constructor(public code: string, message: string, public status: number) {
    super(message)
  }
}

async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem('x-admin-token')
  const res = await fetch(BASE + url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { 'x-admin-token': token } : {}),
      ...(options.headers || {}),
    },
  })
  if (res.status === 401) {
    localStorage.removeItem('x-admin-token')
    localStorage.removeItem('admin-nickname')
    window.location.replace('/login')
    throw new ApiError('UNAUTHORIZED', '登录已失效', 401)
  }
  const json: ApiResponse<T> = await res.json()
  if (!json.success) {
    throw new ApiError(json.code, json.message || '请求失败', res.status)
  }
  return json.data
}

export const http = {
  get: <T>(url: string, params?: object) => {
    const qs = params
      ? '?' + Object.entries(params).filter(([, v]) => v != null).map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`).join('&')
      : ''
    return request<T>(url + qs)
  },
  post: <T>(url: string, body?: unknown) =>
    request<T>(url, { method: 'POST', body: body ? JSON.stringify(body) : undefined }),
  put: <T>(url: string, body?: unknown) =>
    request<T>(url, { method: 'PUT', body: body ? JSON.stringify(body) : undefined }),
  delete: <T>(url: string) => request<T>(url, { method: 'DELETE' }),
}
