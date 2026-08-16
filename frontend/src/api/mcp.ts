import request from './request'
import type { AgentMcpServerDTO, AgentMcpServerPageRequest, PageResult } from '@/types/api'

export function pageMcpServers(params: AgentMcpServerPageRequest) {
  return request.get<PageResult<AgentMcpServerDTO>, { data: PageResult<AgentMcpServerDTO> }>('/admin/mcp/page', { params })
}

export function getMcpServer(id: number) {
  return request.get<AgentMcpServerDTO, { data: AgentMcpServerDTO }>(`/admin/mcp/${id}`)
}

export function createMcpServer(data: AgentMcpServerDTO) {
  return request.post('/admin/mcp', data)
}

export function updateMcpServer(data: AgentMcpServerDTO) {
  return request.put('/admin/mcp', data)
}

export function deleteMcpServer(id: number) {
  return request.delete(`/admin/mcp/${id}`)
}
