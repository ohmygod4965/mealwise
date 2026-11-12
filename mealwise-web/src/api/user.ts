import type { AxiosRequestConfig } from 'axios'
import { http } from '@/api/request'
import type { UpdateProfilePayload, UserProfileVO } from '@/types/api'

/** 查询当前登录用户的资料（含身体信息与 profileComplete） */
export function getMyProfile(config?: AxiosRequestConfig): Promise<UserProfileVO> {
  return http.get<UserProfileVO>('/users/me', undefined, config)
}

/** 更新身体资料；成功后 profileComplete 变为 true */
export function updateMyProfile(
  payload: UpdateProfilePayload,
  config?: AxiosRequestConfig
): Promise<UserProfileVO> {
  return http.put<UserProfileVO>('/users/me/profile', payload, config)
}
