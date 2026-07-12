import { http } from './request'
import type { AgentProjectDTO, PageResult } from '@/types/api'

export function pageProjects(params: { current?: number; size?: number; state?: number }) {
  return http.get<PageResult<AgentProjectDTO>>('/admin/project/page', params)
}
