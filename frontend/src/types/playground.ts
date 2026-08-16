import type { RunStreamEnvelope } from './api'

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  reasoning?: string
  status: 'complete' | 'running' | 'error' | 'cancelled'
}

export interface RunEventLog {
  eventType: RunStreamEnvelope['eventType']
  taskState: string | null
  data: unknown
  timestamp: number
}

export interface PromptVariableDefinition {
  name: string
  label?: string
  type?: string
  required?: boolean
  description?: string
  defaultValue?: unknown
}

export interface FlowEvent {
  id: string
  type: 'user' | 'system' | 'reasoning' | 'assistant' | 'tool_call' | 'tool_result' | 'error'
  label: string
  content: string
  timestamp: number
  toolName?: string
  toolPayload?: Record<string, unknown>
  toolResult?: string
}

export type TraceEntryType =
  | 'system' | 'context' | 'user' | 'reasoning'
  | 'assistant' | 'tool_call' | 'tool_result'
  | 'summary' | 'complete' | 'error' | 'cancelled'

export interface TraceEntry {
  id: string
  type: TraceEntryType
  label: string
  content: string
  timestamp: number
  endTimestamp?: number
  toolName?: string
  toolArgs?: string
  toolResult?: string
  toolResultTimestamp?: number
  durationMs?: number
  expanded?: boolean
}

export interface TimelineStep {
  id: string
  label: string
  icon: unknown
  color: string
  startTime: number
  endTime: number
  content: string | null
  count?: number
}

export const DETAIL_TABS = ['summary', 'payload', 'result', 'timing'] as const
export type DetailTab = typeof DETAIL_TABS[number]
