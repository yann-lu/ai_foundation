import { http } from './request'
import type { CreateRunRequest, CreateRunResponse, MessageDTO, RunDetailResponse } from '@/types/api'

export function createRun(data: CreateRunRequest) {
  return http.post<CreateRunResponse>('/chat/runs/create', data)
}

export function streamRunEvents(runCode: string): EventSource {
  return new EventSource(`/chat/runs/events?runCode=${encodeURIComponent(runCode)}`)
}

export function cancelRun(runCode: string, operator?: string) {
  return http.post<boolean>('/chat/runs/cancel', { runCode, operator })
}

export function getRunDetail(runCode: string) {
  return http.post<RunDetailResponse>('/chat/runs/detail', { runCode })
}

export function getLatestRun(conversationCode: string) {
  return http.get<RunDetailResponse | null>(
    `/chat/runs/latest?conversationCode=${encodeURIComponent(conversationCode)}`,
  )
}

export function getMessages(conversationCode: string, beforeId?: number, limit = 50) {
  return http.get<MessageDTO[]>(`/chat/messages/${conversationCode}`, { beforeId, limit })
}
