import request from './request'
import type { AgentSkillDTO, AgentSkillPageRequest, PageResult, SkillBindOptionDTO, BindSkillsRequest } from '@/types/api'

export function pageSkills(params: AgentSkillPageRequest) {
  return request.get<PageResult<AgentSkillDTO>, { data: PageResult<AgentSkillDTO> }>('/admin/skill/page', { params })
}

export function getSkill(id: number) {
  return request.get<AgentSkillDTO, { data: AgentSkillDTO }>(`/admin/skill/${id}`)
}

export function createSkill(data: AgentSkillDTO) {
  return request.post('/admin/skill', data)
}

export function updateSkill(data: AgentSkillDTO) {
  return request.put('/admin/skill', data)
}

export function deleteSkill(id: number) {
  return request.delete(`/admin/skill/${id}`)
}

export function listSkillBindOptions(projectId: number) {
  return request.get<SkillBindOptionDTO[], { data: SkillBindOptionDTO[] }>(`/admin/project/${projectId}/skillBindOptions`)
}

export function bindSkills(data: BindSkillsRequest) {
  return request.post('/admin/project/bindSkills', data)
}
