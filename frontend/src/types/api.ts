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
  systemPrompt?: string
  promptVariables?: string
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
  conversationCode: string
  userId: number
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

export type RunEventType =
  | 'run_start'
  | 'chat_start'
  | 'user_message'
  | 'request_messages'
  | 'chat_reasoning'
  | 'chat_token'
  | 'tool_call'
  | 'tool_result'
  | 'chat_complete'
  | 'summary_update'
  | 'run_complete'
  | 'run_error'
  | 'run_cancelled'

export interface RunStreamEnvelope {
  eventType: RunEventType
  runCode: string
  conversationCode: string
  timestamp: number
  taskState: string | null
  data: unknown
  traceId: string | null
}

// ===== CLI Capability Management =====

export interface CliCommandDTO {
  id?: number
  commandName: string
  commandPrefix: string
  commandGroup: string
  commandAction: string
  cliTemplate?: string
  description: string
  commandType: 'API' | 'PAGE'
  state: number
  boundCount?: number
  createTime?: string
  updateTime?: string
}

export interface CliParamDTO {
  id?: number
  paramName: string
  paramFlag?: string
  paramType: 'String' | 'Number' | 'Boolean' | 'Array' | 'Object'
  itemType?: string
  isRequired: number
  description?: string
  defaultValue?: string
  sortOrder?: number
  parentParamName?: string
}

export interface ToolDefinitionDTO {
  id?: number
  toolName?: string
  description?: string
  schemaCode?: string
  url: string
  method: string
  authType?: string
  requestSchema?: string
  responseSchema?: string
}

export interface PageDefinitionDTO {
  id?: number
  pageName?: string
  pagePrefix?: string
  pageRoute: string
  displayType?: string
  targetType?: string
  resourceProject?: string
  resourceIds?: string
}

export interface CliRecallTagDTO {
  id?: number
  tagType: string
  tagValue: string
  weight?: number
  matchMode?: string
  sortOrder?: number
}

export interface CliCommandDetailDTO {
  id?: number
  commandName: string
  commandPrefix: string
  commandGroup: string
  commandAction: string
  cliTemplate?: string
  description: string
  commandType: 'API' | 'PAGE'
  state: number
  params: CliParamDTO[]
  tool?: ToolDefinitionDTO
  page?: PageDefinitionDTO
  recallTags: CliRecallTagDTO[]
  createTime?: string
  updateTime?: string
}

export interface CliCommandPageRequest {
  keyword?: string
  commandType?: string
  commandPrefix?: string
  state?: number
  current?: number
  size?: number
}


// ===== API Schema 配置 =====

export interface ApiSchemaConfigDTO {
  id?: number
  schemaCode: string
  schemaName: string
  baseUrl: string
  commandPrefix?: string
  state: number
  createUser?: string
  modifyUser?: string
  createTime?: string
  updateTime?: string
}

export interface ApiSchemaConfigPageRequest {
  keyword?: string
  state?: number
  current?: number
  size?: number
}

export interface BindCapabilityOptionDTO {
  id: number
  commandName: string
  commandType: string
  description: string
  bound: boolean
}

export interface BindOptionsResponse {
  cliOptions: BindCapabilityOptionDTO[]
}

export interface BindCapabilitiesRequest {
  id: number
  cliIds: number[]
}

// ===== Skill =====
export interface AgentSkillDTO {
  id?: number
  skillName: string
  skillCode: string
  description?: string
  skillType: string
  systemPrompt?: string
  configJson?: string
  state: number
  cliIds?: number[]
  createUser?: string
  createTime?: string
  updateTime?: string
}

export interface AgentSkillPageRequest {
  keyword?: string
  skillType?: string
  state?: number
  current?: number
  size?: number
}

export interface SkillBindOptionDTO {
  id: number
  skillName: string
  skillType: string
  description?: string
  bound: boolean
}

export interface BindSkillsRequest {
  id: number
  skillIds: number[]
}

// ===== MCP Server =====
export interface AgentMcpServerDTO {
  id?: number
  serverCode: string
  serverName: string
  description?: string
  transportType: string
  command?: string
  workingDir?: string
  envVars?: string
  baseUrl?: string
  authType?: string
  authConfig?: string
  state: number
  createUser?: string
  createTime?: string
  updateTime?: string
}

export interface AgentMcpServerPageRequest {
  keyword?: string
  transportType?: string
  state?: number
  current?: number
  size?: number
}
