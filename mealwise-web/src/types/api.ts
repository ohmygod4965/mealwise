/**
 * Mealwise 后端接口类型定义。
 *
 * 约定：所有接口返回统一响应体 `{ code, message, data, traceId? }`，
 * 其中 `code` 与 HTTP 状态码保持一致；`data` 为空时后端可能直接省略该字段。
 */

/** 统一响应体 */
export interface ApiResponse<T> {
  code: number
  message: string
  data?: T
  traceId?: string
}

/** 分页结果 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

/* ============================ 用户与鉴权 ============================ */

export interface UserVO {
  id: number
  username: string
  allergens: string[]
  /** ADMIN / USER：ADMIN 才显示「用户管理」入口 */
  role?: 'ADMIN' | 'USER'
}

export interface RegisterPayload {
  username: string
  password: string
  allergens?: string[]
}

export interface LoginPayload {
  username: string
  password: string
}

export interface LoginResult {
  token: string
  tokenType: 'Bearer'
  /** 有效期（秒） */
  expiresIn: number
  user: UserVO
}

/** 注册接口返回的用户信息，与 UserVO 结构一致 */
export type RegisterResult = UserVO

/* ============================== 食物库 ============================== */

export interface FoodVO {
  id: number
  name: string
  category: string
  caloriesPer100g: number
  proteinPer100g: number
  fatPer100g: number
  carbPer100g: number
  unitName?: string | null
  unitWeightG?: number | null
  allergens: string[]
  enabled: boolean
}

/** 新增 / 编辑食物的请求体（FoodVO 去掉 id） */
export interface FoodPayload {
  name: string
  category: string
  caloriesPer100g: number
  proteinPer100g: number
  fatPer100g: number
  carbPer100g: number
  unitName?: string | null
  unitWeightG?: number | null
  allergens: string[]
  enabled: boolean
}

export interface FoodQuery {
  keyword?: string
  category?: string
  page?: number
  size?: number
}

/* ============================== 餐次 ============================== */

export type MealType = 'BREAKFAST' | 'LUNCH' | 'DINNER' | 'SNACK'

export type ReplacementStrategy = 'higher' | 'lower'

export interface NutritionTotals {
  calories: number
  protein: number
  fat: number
  carb: number
}

/** 餐次中的食物项（GET /meals/{mealId}/calories 返回） */
export interface MealItemVO {
  itemId: number
  foodId: number
  foodName: string
  quantityG: number
  calories: number
  protein: number
  fat: number
  carb: number
  /** 乐观锁版本号：改克数时回传，服务端据此判断是否有人先改过 */
  version?: number
}

/** 创建餐次返回的基本信息 */
export interface MealVO {
  id: number
  mealType: MealType
  eatenAt: string
  items: MealItemVO[]
}

/** 添加食物项接口的返回体 */
export interface MealItemCreatedVO {
  id: number
  foodId: number
  foodName: string
  quantityG: number
  calories: number
  protein: number
  fat: number
  carb: number
}

export interface CreateMealPayload {
  mealType: MealType
  /** yyyy-MM-dd HH:mm:ss，不传则由后端取当前时间 */
  eatenAt?: string
}

export interface AddMealItemPayload {
  foodId: number
  quantityG: number
}

export interface UpdateMealItemPayload {
  quantityG: number
  /** 读取时的版本号；并发被改过时服务端返回 409 */
  version?: number
}

/** 一餐热量与营养合计 */
export interface MealCaloriesResult {
  mealId: number
  mealType: MealType
  eatenAt: string
  items: MealItemVO[]
  total: NutritionTotals
  itemCount: number
  totalGrams: number
}

/* ============================ 替换建议 ============================ */

export interface ReplacementOriginalFood {
  id: number
  name: string
  caloriesPer100g: number
}

export interface ReplacementSuggestion {
  foodId: number
  name: string
  category: string
  caloriesPer100g: number
  replacedCalories: number
  deltaCalories: number
  reason: string
}

export interface ReplacementItemGroup {
  itemId: number
  originalFood: ReplacementOriginalFood
  quantityG: number
  originalCalories: number
  /** 无候选时为空数组，属于成功响应 */
  suggestions: ReplacementSuggestion[]
}

export interface ReplacementResult {
  mealId: number
  strategy: ReplacementStrategy
  items: ReplacementItemGroup[]
  totalDeltaIfAllApplied: number
  /** 无候选等场景下的友好提示，属于成功响应 */
  message?: string
}

/* ============================ 用户身体资料 ============================ */

export type Gender = 'MALE' | 'FEMALE'

/** GET /users/me 返回的当前用户资料（含身体信息与是否已完善标记） */
export interface UserProfileVO {
  id: number
  username: string
  allergens: string[]
  gender: Gender | null
  heightCm: number | null
  weightKg: number | null
  age: number | null
  /** 性别 / 身高 / 体重是否都已填写 */
  profileComplete: boolean
}

/** 更新身体资料请求体；年龄可选，不传则由后端保留原值 */
export interface UpdateProfilePayload {
  gender: Gender
  heightCm: number
  weightKg: number
  age?: number
}

/* ============================== 每日记录 ============================== */

export type ComparisonStatus = 'UNDER' | 'ON_TRACK' | 'OVER'

/** 每日记录中的一餐 */
export interface DayMealVO {
  mealId: number
  mealType: MealType
  /** yyyy-MM-dd HH:mm:ss */
  eatenAt: string
  items: MealItemVO[]
  total: NutritionTotals
}

/** 后端根据身体资料推算出的每日建议值 */
export interface DayTargetsVO {
  bmr: number
  tdee: number
  calories: number
  protein: number
  fat: number
  carb: number
  /** 计算口径说明，例如使用的公式 */
  formula: string
  /** 计算假设，例如活动系数、蛋白质系数 */
  assumptions: string[]
}

/** 单个营养指标的「摄入 vs 建议」对比 */
export interface NutrientComparison {
  intake: number
  target: number
  diff: number
  percent: number
  status: ComparisonStatus
}

export interface DayComparisonVO {
  calories: NutrientComparison
  protein: NutrientComparison
  fat: NutrientComparison
  carb: NutrientComparison
}

/** GET /days/today 或 GET /days/{date} 返回的每日记录 */
export interface DayRecordVO {
  /** yyyy-MM-dd */
  date: string
  meals: DayMealVO[]
  totals: NutritionTotals
  /** 未填写身体资料时为 null */
  targets: DayTargetsVO | null
  /** 未填写身体资料时为 null */
  comparison: DayComparisonVO | null
}
/* ------------------------------------------------------------------ AI 饮食推荐 */

/** 推荐目标：减脂 / 维持 / 增肌 */
export type AiGoal = 'FAT_LOSS' | 'MAINTAIN' | 'MUSCLE_GAIN'

/** 来源：llm = 大模型生成，local = 本地规则兜底 */
export type AiPlanSource = 'llm' | 'local'

export interface AiPlanItem {
  name: string
  nameEn?: string
  grams: number
  kcalPer100g: number
  calories: number
  protein: number
  fat: number
  carb: number
  /** 是否经过核验（web 或 food-db） */
  verified: boolean
  /** 核验来源：web=联网营养库；food-db=项目食物库；estimate=模型估算 */
  verifySource: 'web' | 'food-db' | 'estimate'
  sourceUrl?: string
}

export interface AiPlanMeal {
  mealType: MealType
  items: AiPlanItem[]
  total: NutritionTotals
}

export interface AiPlanVO {
  goal: AiGoal
  targetCalories: number
  source: AiPlanSource
  model?: string
  summary: string
  meals: AiPlanMeal[]
  totals: NutritionTotals
  verification: { checked: number; matched: number }
  notes: string[]
}

export interface AiMealPlanRequest {
  goal: AiGoal
  targetCalories?: number
  mealTypes?: MealType[]
  preferences?: string
  excludeAllergens?: string[]
}
/* ------------------------------------------------------------------ 账号管理（管理员） */

export type UserRole = 'ADMIN' | 'USER'

export interface AdminUserVO {
  id: number
  username: string
  role: UserRole
  /** 1=启用 0=禁用 */
  status: number
  allergens: string[]
  gender: string | null
  profileComplete: boolean
  createdAt: string
}