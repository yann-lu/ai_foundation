export interface ApiResponse<T = unknown> {
  success: boolean
  code: string
  message: string
  data: T
  traceId?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export interface AdminLoginResponse {
  token: string
  username: string
  nickname: string
}

export interface AgentProjectDTO {
  id?: number
  projectName: string
  projectCode: string
  description?: string
  systemPrompt?: string
  promptVariables?: string
  state: number
  createTime?: string
  updateTime?: string
}

export interface ConversationDTO {
  id: number
  projectId: number
  productCode: string
  conversationCode: string
  contextVariables?: string
  title: string
  summary?: string
  modelProvider: string
  modelName: string
  isPin: number
  lastMessageTime?: string
  state: number
  createTime?: string
  updateTime?: string
}

export interface ConversationCreateRequest {
  productCode: string
  contextVariables?: Record<string, unknown>
  title?: string
  modelName?: string
}

export interface ConversationPageRequest {
  productCode?: string
  title?: string
  state?: number
  current?: number
  size?: number
}

export interface MessageDTO {
  id: number
  conversationId: number
  role: string
  content: string
  tokenCount: number
  durationMs: number
  attachments?: string
  clientIp: string
  state: number
  createTime?: string
}

export interface CreateRunRequest {
  conversationCode: string
  userMessage: string
  systemPrompt?: string
}

export interface CreateRunResponse {
  runCode: string
  traceId: string | null
}

export type RunEventType =
  | 'run_start' | 'chat_start' | 'user_message' | 'chat_reasoning' | 'chat_token' | 'chat_complete'
  | 'summary_update' | 'tool_call' | 'tool_result'
  | 'run_complete' | 'run_error' | 'run_cancelled'

export interface RunStreamEnvelope {
  eventType: RunEventType
  runCode: string
  conversationCode: string
  timestamp: number
  taskState: string | null
  data: unknown
  traceId: string | null
}
