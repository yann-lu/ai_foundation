import request from './request'
import type { AgentProjectDTO, AgentProjectPageRequest, PageResult } from '@/types/api'

export function pageProjects(params: AgentProjectPageRequest) {
  return request.get<PageResult<AgentProjectDTO>, { data: PageResult<AgentProjectDTO> }>('/admin/project/page', { params })
}

export function getProject(id: number) {
  return request.get<AgentProjectDTO, { data: AgentProjectDTO }>(`/admin/project/${id}`)
}

export function createProject(data: AgentProjectDTO) {
  return request.post('/admin/project', data)
}

export function updateProject(data: AgentProjectDTO) {
  return request.put('/admin/project', data)
}

export function deleteProject(id: number) {
  return request.delete(`/admin/project/${id}`)
}
