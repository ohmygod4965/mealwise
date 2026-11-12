import type { AxiosRequestConfig } from 'axios'
import { http } from '@/api/request'
import type { FoodPayload, FoodQuery, FoodVO, PageResult } from '@/types/api'

/** 分页查询食物库，支持名称关键字与类别筛选 */
export function getFoods(params: FoodQuery, config?: AxiosRequestConfig): Promise<PageResult<FoodVO>> {
  return http.get<PageResult<FoodVO>>(
    '/foods',
    {
      keyword: params.keyword?.trim() || undefined,
      category: params.category || undefined,
      page: params.page ?? 1,
      size: params.size ?? 10
    },
    config
  )
}

/** 新增食物 */
export function createFood(payload: FoodPayload): Promise<FoodVO> {
  return http.post<FoodVO>('/foods', payload)
}

/** 全量更新食物 */
export function updateFood(id: number, payload: FoodPayload): Promise<FoodVO> {
  return http.put<FoodVO>(`/foods/${id}`, payload)
}

/** 删除食物：后端为软删除，返回 data: null */
export function deleteFood(id: number): Promise<null> {
  return http.delete<null>(`/foods/${id}`)
}
