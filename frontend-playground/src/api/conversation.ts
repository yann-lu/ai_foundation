import { http } from './request'
import type { ConversationDTO, ConversationCreateRequest, ConversationPageRequest, PageResult } from '@/types/api'

export function pageConversations(params: ConversationPageRequest) {
  return http.get<PageResult<ConversationDTO>>('/admin/conversation/page', params)
}

export function createConversation(data: ConversationCreateRequest) {
  return http.post<ConversationDTO>('/chat/create', data)
}
