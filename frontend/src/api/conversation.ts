import request from './request'
import type { ConversationDTO, ConversationPageRequest, ConversationDetailDTO, PageResult } from '@/types/api'

export function pageConversations(params: ConversationPageRequest) {
  return request.get<PageResult<ConversationDTO>, { data: PageResult<ConversationDTO> }>('/admin/conversation/page', { params })
}

export function getConversationDetail(id: number) {
  return request.get<ConversationDetailDTO, { data: ConversationDetailDTO }>(`/admin/conversation/${id}`)
}

export function deleteConversation(id: number) {
  return request.delete(`/admin/conversation/${id}`)
}

export function clearConversationMessages(id: number) {
  return request.post(`/admin/conversation/${id}/clear-messages`)
}
