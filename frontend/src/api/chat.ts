import request from './request'
import type { ConversationDTO, ConversationCreateRequest, MessageDTO, ChatSyncRequest, ChatSyncResponse, ChatStreamRequest } from '@/types/api'

export function createConversation(data: ConversationCreateRequest) {
  return request.post<ConversationDTO, { data: ConversationDTO }>('/chat/create', data)
}

export function getMessages(conversationCode: string, beforeId?: number, limit = 50) {
  return request.get<MessageDTO[], { data: MessageDTO[] }>(`/chat/messages/${conversationCode}`, {
    params: { beforeId, limit }
  })
}

export function syncChat(data: ChatSyncRequest) {
  return request.post<ChatSyncResponse, { data: ChatSyncResponse }>('/chat/sync', data)
}

export function streamChat(data: ChatStreamRequest): Promise<ReadableStream<Uint8Array>> {
  return fetch('/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  }).then((res) => {
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return res.body!
  })
}
