import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types/api'

const service: AxiosInstance = axios.create({
  baseURL: '/',
  timeout: 15000
})

service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('x-admin-token')
    if (token) {
      config.headers['x-admin-token'] = token
    }
    return config
  },
  (error) => Promise.reject(error)
)

service.interceptors.response.use(
  (response): any => {
    const data = response.data as ApiResponse
    if (data.success) {
      return data
    }
    ElMessage.error(data.message || '请求失败')
    return Promise.reject(new Error(data.message))
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      localStorage.removeItem('x-admin-token')
      localStorage.removeItem('admin-nickname')
      ElMessage.error('登录已失效，请重新登录')
      window.location.replace('/login')
    } else {
      const msg = error.response?.data?.message || error.message || '网络异常'
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  }
)

export default service
