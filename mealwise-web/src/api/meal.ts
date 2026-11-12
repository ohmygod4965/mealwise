import type { AxiosRequestConfig } from 'axios'
import { http } from '@/api/request'
import type {
  AddMealItemPayload,
  CreateMealPayload,
  MealCaloriesResult,
  MealItemCreatedVO,
  MealVO,
  ReplacementResult,
  ReplacementStrategy,
  UpdateMealItemPayload
} from '@/types/api'

/** 创建餐次 */
export function createMeal(payload: CreateMealPayload): Promise<MealVO> {
  return http.post<MealVO>('/meals', payload)
}

/** 删除整餐（连带食物项），用于「撤销刚才的入账」 */
export function deleteMeal(mealId: number): Promise<null> {
  return http.delete<null>(`/meals/${mealId}`)
}

/** 向餐次添加食物项 */
export function addMealItem(mealId: number, payload: AddMealItemPayload): Promise<MealItemCreatedVO> {
  return http.post<MealItemCreatedVO>(`/meals/${mealId}/items`, payload)
}

/** 修改食物项克数 */
export function updateMealItem(
  mealId: number,
  itemId: number,
  payload: UpdateMealItemPayload
): Promise<MealItemCreatedVO> {
  return http.put<MealItemCreatedVO>(`/meals/${mealId}/items/${itemId}`, payload)
}

/** 删除食物项 */
export function deleteMealItem(mealId: number, itemId: number): Promise<null> {
  return http.delete<null>(`/meals/${mealId}/items/${itemId}`)
}

/** 查询一餐的明细、总热量与三大营养素 */
export function getMealCalories(mealId: number, config?: AxiosRequestConfig): Promise<MealCaloriesResult> {
  return http.get<MealCaloriesResult>(`/meals/${mealId}/calories`, undefined, config)
}

/** 查询同类食物替换建议；无候选时 suggestions 为空且 message 有值，属于成功响应 */
export function getReplacements(
  mealId: number,
  strategy: ReplacementStrategy,
  config?: AxiosRequestConfig
): Promise<ReplacementResult> {
  return http.get<ReplacementResult>(`/meals/${mealId}/replacements`, { strategy }, config)
}
