import request from './request'
import type {
  ApiSchemaConfigDTO,
  ApiSchemaConfigPageRequest,
  PageResult
} from '@/types/api'

export function pageApiSchema(params: ApiSchemaConfigPageRequest) {
  return request.post<PageResult<ApiSchemaConfigDTO>, { data: PageResult<ApiSchemaConfigDTO> }>(
    '/admin/apiSchema/page',
    params
  )
}

export function getApiSchema(id: number) {
  return request.get<ApiSchemaConfigDTO, { data: ApiSchemaConfigDTO }>(`/admin/apiSchema/${id}`)
}

export function listEnabledApiSchema() {
  return request.get<ApiSchemaConfigDTO[], { data: ApiSchemaConfigDTO[] }>('/admin/apiSchema/listEnabled')
}

export function createApiSchema(data: ApiSchemaConfigDTO) {
  return request.post<number, { data: number }>('/admin/apiSchema', data)
}

export function updateApiSchema(data: ApiSchemaConfigDTO) {
  return request.put<boolean, { data: boolean }>('/admin/apiSchema', data)
}

export function deleteApiSchema(id: number) {
  return request.delete<boolean, { data: boolean }>(`/admin/apiSchema/${id}`)
}
