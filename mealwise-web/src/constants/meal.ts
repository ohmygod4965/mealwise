import type { MealType } from '@/types/api'

export interface MealTypeOption {
  value: MealType
  label: string
  emoji: string
  /** 建议用餐时段，仅用于界面提示 */
  timeHint: string
}

export const MEAL_TYPE_OPTIONS: MealTypeOption[] = [
  { value: 'BREAKFAST', label: '早餐', emoji: '🌅', timeHint: '06:00 – 10:00' },
  { value: 'LUNCH', label: '午餐', emoji: '☀️', timeHint: '11:00 – 14:00' },
  { value: 'DINNER', label: '晚餐', emoji: '🌙', timeHint: '17:00 – 21:00' },
  { value: 'SNACK', label: '加餐', emoji: '🍎', timeHint: '随时补充能量' }
]

export function getMealTypeMeta(type: string): MealTypeOption {
  return MEAL_TYPE_OPTIONS.find((item) => item.value === type) ?? MEAL_TYPE_OPTIONS[0]
}

/** 按当前时间猜测餐次，减少用户选择成本 */
export function guessMealType(date = new Date()): MealType {
  const hour = date.getHours()
  if (hour < 10) return 'BREAKFAST'
  if (hour < 15) return 'LUNCH'
  if (hour < 21) return 'DINNER'
  return 'SNACK'
}

/** 食物类别：与后端种子数据及 docs/init.sql 中的中文类别保持一致 */
export const FOOD_CATEGORIES = ['主食', '肉蛋', '蔬菜', '水果', '奶制品', '坚果', '豆制品', '饮料', '其他']

export interface AllergenOption {
  value: string
  label: string
}

/** 过敏原 token 与中文展示名（后端存储英文 token） */
export const ALLERGEN_OPTIONS: AllergenOption[] = [
  { value: 'egg', label: '蛋类' },
  { value: 'gluten', label: '麸质' },
  { value: 'seafood', label: '海鲜' },
  { value: 'soy', label: '大豆' },
  { value: 'peanut', label: '花生' },
  { value: 'tree_nut', label: '坚果' },
  { value: 'milk', label: '奶制品' },
  { value: 'sesame', label: '芝麻' }
]

export function allergenLabel(token: string): string {
  return ALLERGEN_OPTIONS.find((item) => item.value === token)?.label ?? token
}
