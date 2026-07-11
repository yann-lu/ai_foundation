export interface ApiResponse<T = any> {
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
  state: number
  createUser?: string
  modifyUser?: string
  createTime?: string
  updateTime?: string
}

export interface AgentModelConfigDTO {
  id?: number
  projectId: number
  modelName: string
  modelType: string
  state: number
  createUser?: string
  modifyUser?: string
  createTime?: string
  updateTime?: string
}

export interface AgentProjectPageRequest {
  projectName?: string
  projectCode?: string
  state?: number
  current?: number
  size?: number
}

export interface AgentModelConfigPageRequest {
  projectId?: number
  modelName?: string
  modelType?: string
  state?: number
  current?: number
  size?: number
}

// ===== Conversation & Message =====

export interface ConversationDTO {
  id: number
  projectId: number
  productCode: string
  blocCode: string
  hotelCode: string
  conversationCode: string
  userId: number
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
  blocCode?: string
  hotelCode?: string
  userId?: number
  title?: string
  modelName?: string
}

export interface ConversationPageRequest {
  projectId?: number
  productCode?: string
  title?: string
  state?: number
  current?: number
  size?: number
}

export interface ConversationDetailDTO {
  conversation: ConversationDTO
  messages: MessageDTO[]
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

// ===== Chat =====

export interface ChatSyncRequest {
  conversationCode: string
  userMessage: string
  modelName?: string
  systemPrompt?: string
  temperature?: number
  maxTokens?: number
}

export interface ChatSyncResponse {
  content: string
  tokenCount: number
  durationMs: number
  assistantMessageId: number
}

export interface ChatStreamRequest {
  conversationCode: string
  userMessage: string
  modelName?: string
  systemPrompt?: string
  temperature?: number
  maxTokens?: number
}

export interface ChatStreamChunkDTO {
  eventType: 'start' | 'token' | 'complete' | 'error'
  content: string | null
  timestamp: number
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

export interface RunStreamEnvelope {
  eventType: 'run_start' | 'chat_start' | 'chat_token' | 'chat_complete' | 'run_complete' | 'run_error' | 'run_cancelled'
  runCode: string
  conversationCode: string
  timestamp: number
  taskState: string | null
  data: unknown
  traceId: string | null
}
