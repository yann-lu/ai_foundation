import request from './request'
import type { AgentModelConfigDTO, AgentModelConfigPageRequest, PageResult } from '@/types/api'

export function pageModels(params: AgentModelConfigPageRequest) {
  return request.get<PageResult<AgentModelConfigDTO>, { data: PageResult<AgentModelConfigDTO> }>('/admin/model/page', { params })
}

export function createModel(data: AgentModelConfigDTO) {
  return request.post('/admin/model', data)
}

export function updateModel(data: AgentModelConfigDTO) {
  return request.put('/admin/model', data)
}

export function deleteModel(id: number) {
  return request.delete(`/admin/model/${id}`)
}
