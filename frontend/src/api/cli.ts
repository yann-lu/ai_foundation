import request from './request'
import type {
  CliCommandDTO,
  CliCommandDetailDTO,
  CliCommandPageRequest,
  PageResult,
  BindOptionsResponse,
  BindCapabilitiesRequest
} from '@/types/api'

export function pageCli(params: CliCommandPageRequest) {
  return request.post<PageResult<CliCommandDTO>, { data: PageResult<CliCommandDTO> }>(
    '/admin/cli/page',
    params
  )
}

export function getCli(id: number) {
  return request.get<CliCommandDetailDTO, { data: CliCommandDetailDTO }>(`/admin/cli/${id}`)
}

export function createCli(data: CliCommandDetailDTO) {
  return request.post<number, { data: number }>('/admin/cli', data)
}

export function updateCli(data: CliCommandDetailDTO) {
  return request.put<boolean, { data: boolean }>('/admin/cli', data)
}

export function deleteCli(id: number) {
  return request.delete<boolean, { data: boolean }>(`/admin/cli/${id}`)
}

export function listBindOptions(projectId: number) {
  return request.get<BindOptionsResponse, { data: BindOptionsResponse }>(
    `/admin/project/${projectId}/bindOptions`
  )
}

export function bindCapabilities(data: BindCapabilitiesRequest) {
  return request.post<boolean, { data: boolean }>('/admin/project/bindCapabilities', data)
}
