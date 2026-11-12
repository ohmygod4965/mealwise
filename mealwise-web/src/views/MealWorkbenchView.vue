<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowRight,
  Calendar,
  Close,
  Delete,
  MagicStick,
  Plus,
  Refresh
} from '@element-plus/icons-vue'
import { getFoods } from '@/api/food'
import { explainReplacements } from '@/api/ai-assist'
import {
  addMealItem,
  createMeal,
  deleteMealItem,
  getMealCalories,
  getReplacements,
  updateMealItem
} from '@/api/meal'
import EmptyState from '@/components/EmptyState.vue'
import NutritionSummary from '@/components/NutritionSummary.vue'
import ReplacementCard from '@/components/ReplacementCard.vue'
import { MEAL_TYPE_OPTIONS, getMealTypeMeta, guessMealType } from '@/constants/meal'
import type {
  CreateMealPayload,
  FoodVO,
  MealCaloriesResult,
  MealItemVO,
  MealType,
  NutritionTotals,
  ReplacementResult,
  ReplacementStrategy
} from '@/types/api'
import { formatDateTime, formatNumber } from '@/utils/format'

const router = useRouter()

const SESSION_KEY = 'mealwise.active-meal'
const emptyTotals: NutritionTotals = { calories: 0, protein: 0, fat: 0, carb: 0 }

/* ------------------------------ 餐次状态 ------------------------------ */

const mealType = ref<MealType>(guessMealType())
const eatenAt = ref('')
const creating = ref(false)

const activeMealId = ref<number | null>(null)
const mealDetail = ref<MealCaloriesResult | null>(null)
const detailLoading = ref(false)

const hasMeal = computed(() => activeMealId.value !== null && mealDetail.value !== null)
const activeMealMeta = computed(() =>
  mealDetail.value ? getMealTypeMeta(mealDetail.value.mealType) : null
)
const itemCount = computed(() => mealDetail.value?.itemCount ?? 0)
const totalGrams = computed(() => mealDetail.value?.totalGrams ?? 0)
const totalNutrition = computed<NutritionTotals>(() => mealDetail.value?.total ?? emptyTotals)

/** 有食物项才允许跳转「今日记录」，否则按钮禁用并用 tooltip 提示 */
const canSaveToDay = computed(() => hasMeal.value && itemCount.value > 0)

function handleGoDay(): void {
  if (!canSaveToDay.value) return
  void router.push('/day')
}

/** 当前餐次 id 存在 sessionStorage，刷新页面后可凭 id 重新拉取明细 */
function persistActiveMeal(): void {
  if (activeMealId.value === null) {
    sessionStorage.removeItem(SESSION_KEY)
    return
  }
  sessionStorage.setItem(SESSION_KEY, String(activeMealId.value))
}

function resetMealState(): void {
  activeMealId.value = null
  mealDetail.value = null
  selectedFoodId.value = null
  quantityG.value = 100
  sessionStorage.removeItem(SESSION_KEY)
}

async function loadMealDetail(options: { silent?: boolean } = {}): Promise<void> {
  if (activeMealId.value === null) return

  detailLoading.value = true
  try {
    const data = await getMealCalories(
      activeMealId.value,
      options.silent ? { silent: true } : undefined
    )
    mealDetail.value = data
    activeMealId.value = data.mealId
    persistActiveMeal()
  } catch {
    // 静默恢复失败（如餐次已被删除 / 不属于当前用户）时清空工作台
    if (options.silent) resetMealState()
  } finally {
    detailLoading.value = false
  }
}

/** 选择餐次类型 + 可选时间，创建餐次 */
async function handleCreateMeal(): Promise<void> {
  if (creating.value) return

  creating.value = true
  try {
    const payload: CreateMealPayload = { mealType: mealType.value }
    if (eatenAt.value) payload.eatenAt = eatenAt.value

    const meal = await createMeal(payload)
    activeMealId.value = meal.id
    mealDetail.value = {
      mealId: meal.id,
      mealType: meal.mealType,
      eatenAt: meal.eatenAt,
      items: [],
      total: { ...emptyTotals },
      itemCount: 0,
      totalGrams: 0
    }
    persistActiveMeal()
    ElMessage.success(`${getMealTypeMeta(meal.mealType).label}已创建，开始添加食物吧`)
    await loadMealDetail({ silent: true })
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    creating.value = false
  }
}

/** 新建餐次：清空当前工作台（后端没有删除餐次接口，已存在的餐次仍会保留） */
async function handleStartNewMeal(): Promise<void> {
  if (!hasMeal.value) return

  if (itemCount.value > 0) {
    try {
      await ElMessageBox.confirm(
        '当前餐次会从工作台移除，已记录在服务器上的餐次仍会保留。确定新建一个餐次吗？',
        '新建餐次',
        {
          confirmButtonText: '新建餐次',
          cancelButtonText: '取消',
          type: 'warning'
        }
      )
    } catch {
      return
    }
  }

  resetMealState()
  mealType.value = guessMealType()
  eatenAt.value = ''
  ElMessage.info('工作台已清空，请创建新的餐次')
}

/* ------------------------------ 食物搜索与添加 ------------------------------ */

const selectedFoodId = ref<number | null>(null)
const quantityG = ref<number>(100)
const foodOptions = ref<FoodVO[]>([])
const foodSearchLoading = ref(false)
const addingItem = ref(false)
let foodSearchSeq = 0

const selectedFood = computed(
  () => foodOptions.value.find((food) => food.id === selectedFoodId.value) ?? null
)

/** 添加前预览：按克数折算热量与三大营养素，给用户即时反馈 */
const previewNutrition = computed<NutritionTotals>(() => {
  const food = selectedFood.value
  const ratio = food ? (Number(quantityG.value) || 0) / 100 : 0
  return {
    calories: (food?.caloriesPer100g ?? 0) * ratio,
    protein: (food?.proteinPer100g ?? 0) * ratio,
    fat: (food?.fatPer100g ?? 0) * ratio,
    carb: (food?.carbPer100g ?? 0) * ratio
  }
})

async function searchFoodOptions(keyword: string): Promise<void> {
  const seq = ++foodSearchSeq
  foodSearchLoading.value = true
  try {
    const result = await getFoods({ keyword: keyword.trim() || undefined, page: 1, size: 20 })
    if (seq !== foodSearchSeq) return
    // 只允许添加已上架食物
    foodOptions.value = (result.records ?? []).filter((food) => food.enabled)
  } catch {
    if (seq === foodSearchSeq) foodOptions.value = []
  } finally {
    if (seq === foodSearchSeq) foodSearchLoading.value = false
  }
}

function handleFoodSelectVisible(visible: boolean): void {
  if (visible && foodOptions.value.length === 0 && !foodSearchLoading.value) {
    void searchFoodOptions('')
  }
}

async function handleAddItem(): Promise<void> {
  if (activeMealId.value === null) {
    ElMessage.warning('请先创建餐次')
    return
  }
  if (selectedFoodId.value === null) {
    ElMessage.warning('请先选择要添加的食物')
    return
  }

  const qty = Number(quantityG.value)
  if (!Number.isFinite(qty) || qty <= 0) {
    ElMessage.warning('食物克数需大于 0')
    return
  }

  addingItem.value = true
  try {
    await addMealItem(activeMealId.value, {
      foodId: selectedFoodId.value,
      quantityG: qty
    })
    ElMessage.success(`已添加 ${selectedFood.value?.name ?? '食物'} ${formatNumber(qty)} g`)
    selectedFoodId.value = null
    quantityG.value = 100
    await loadMealDetail({ silent: true })
  } catch {
    // 错误提示由拦截器处理
  } finally {
    addingItem.value = false
  }
}

/* ------------------------------ 食物项修改 / 删除 ------------------------------ */

const updatingItemId = ref<number | null>(null)
const deletingItemId = ref<number | null>(null)

async function handleQuantityChange(item: MealItemVO, value: number | undefined): Promise<void> {
  if (activeMealId.value === null) return

  const qty = Number(value)
  if (!Number.isFinite(qty) || qty <= 0) {
    ElMessage.warning('食物克数需大于 0')
    await loadMealDetail({ silent: true })
    return
  }

  updatingItemId.value = item.itemId
  try {
    await updateMealItem(activeMealId.value, item.itemId, { quantityG: qty, version: item.version })
    await loadMealDetail({ silent: true })
  } catch {
    // 更新失败时用服务端数据回滚本地输入
    await loadMealDetail({ silent: true })
  } finally {
    updatingItemId.value = null
  }
}

async function handleDeleteItem(item: MealItemVO): Promise<void> {
  if (activeMealId.value === null) return

  try {
    await ElMessageBox.confirm(`确定把「${item.foodName}」从这一餐移除吗？`, '移除食物', {
      confirmButtonText: '移除',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger'
    })
  } catch {
    return
  }

  deletingItemId.value = item.itemId
  try {
    await deleteMealItem(activeMealId.value, item.itemId)
    ElMessage.success('已从这一餐移除')
    await loadMealDetail({ silent: true })
  } catch {
    // 错误提示由拦截器处理
  } finally {
    deletingItemId.value = null
  }
}

/* ------------------------------ 替换建议 ------------------------------ */

const replacementVisible = ref(false)
const replacementStrategy = ref<ReplacementStrategy>('higher')
const replacementLoading = ref(false)
const replacementError = ref(false)
const replacementData = ref<ReplacementResult | null>(null)

/** AI 解释：foodId → 文本（规则原文或模型润色） */
const aiExplanations = ref<Record<number, { text: string; fromLlm: boolean }>>({})
const aiExplainLoading = ref(false)

/**
 * 把规则引擎的 Top3 交给后端，让模型把 reason 润色成一句话。
 * 模型不可用时后端会回退规则原文，所以这里不区分成功/失败样式。
 */
async function loadAiExplanations(): Promise<void> {
  if (activeMealId.value === null) return
  aiExplainLoading.value = true
  try {
    const result = await explainReplacements(activeMealId.value, replacementStrategy.value)
    const map: Record<number, { text: string; fromLlm: boolean }> = {}
    for (const item of result.items) {
      for (const suggestion of item.suggestions) {
        map[suggestion.foodId] = { text: suggestion.explanation, fromLlm: suggestion.fromLlm }
      }
    }
    aiExplanations.value = map
  } finally {
    aiExplainLoading.value = false
  }
}

const replacementItems = computed(() => replacementData.value?.items ?? [])
const replacementMessage = computed(() => replacementData.value?.message ?? '')
const replacementTotalDelta = computed(() => replacementData.value?.totalDeltaIfAllApplied ?? 0)

const replacementSummaryText = computed(() => {
  if (!replacementData.value) return ''
  if (replacementTotalDelta.value === 0) return '按当前策略，全部采纳后总热量保持不变'
  const direction = replacementTotalDelta.value > 0 ? '增加' : '减少'
  return `全部采纳后，这一餐总热量将${direction} ${formatNumber(
    Math.abs(replacementTotalDelta.value)
  )} 千卡`
})

const replacementSummaryTone = computed(() => {
  if (replacementTotalDelta.value > 0) return 'up'
  if (replacementTotalDelta.value < 0) return 'down'
  return 'flat'
})

const strategyDescription = computed(() =>
  replacementStrategy.value === 'higher'
    ? '在同类食物里，找热量更高、能帮你补充能量的替代。'
    : '在同类食物里，找热量更低、吃起来更轻盈的替代。'
)

async function loadReplacements(): Promise<void> {
  if (activeMealId.value === null) return
  aiExplanations.value = {}

  replacementLoading.value = true
  replacementError.value = false
  try {
    replacementData.value = await getReplacements(activeMealId.value, replacementStrategy.value)
  } catch {
    replacementError.value = true
    replacementData.value = null
  } finally {
    replacementLoading.value = false
  }
}

function openReplacements(): void {
  if (!hasMeal.value) {
    ElMessage.warning('请先创建餐次并添加食物')
    return
  }
  replacementData.value = null
  replacementVisible.value = true
  void loadReplacements()
}

function handleStrategyChange(): void {
  void loadReplacements()
}

/* ------------------------------ 初始化 ------------------------------ */

onMounted(() => {
  const raw = sessionStorage.getItem(SESSION_KEY)
  if (!raw) return

  const mealId = Number(raw)
  if (!Number.isInteger(mealId) || mealId <= 0) {
    sessionStorage.removeItem(SESSION_KEY)
    return
  }
  activeMealId.value = mealId
  void loadMealDetail({ silent: true })
})
</script>

<template>
  <div class="meal-page">
    <header class="page-head">
      <div>
        <p class="eyebrow">MEAL WORKBENCH</p>
        <h1 class="page-title">一餐记录</h1>
        <p class="page-subtitle">
          选餐次、加食物、看营养。每一口都会被换算成热量与三大营养素，再给你同类替换的参考。
        </p>
        <p class="workbench-note">
          <span class="workbench-note__dot" aria-hidden="true"></span>
          这一餐已实时保存，可在
          <RouterLink to="/day" class="workbench-note__link">「今日记录」</RouterLink>
          查看早/午/晚餐汇总。
        </p>
      </div>
      <div class="page-actions">
        <el-button v-if="hasMeal" :icon="Refresh" size="large" @click="handleStartNewMeal">
          新建餐次
        </el-button>
        <el-tooltip
          :disabled="canSaveToDay"
          content="先给这一餐添加食物，才能查看今日记录"
          placement="bottom"
        >
          <span class="page-actions__tooltip">
            <el-button
              type="primary"
              plain
              size="large"
              :icon="Calendar"
              :disabled="!canSaveToDay"
              @click="handleGoDay"
            >
              保存并查看今日记录
            </el-button>
          </span>
        </el-tooltip>
        <el-button
          type="primary"
          size="large"
          :icon="MagicStick"
          :disabled="!hasMeal"
          @click="openReplacements"
        >
          替换建议
        </el-button>
      </div>
    </header>

    <!-- 还没有餐次：先创建 -->
    <section v-if="!hasMeal" class="meal-setup surface-card">
      <div class="meal-setup__intro">
        <p class="eyebrow">STEP 01</p>
        <h2 class="meal-setup__title">先选一个餐次</h2>
        <p class="meal-setup__desc">
          创建餐次后，就可以往里面添加食物。系统会按克数实时计算这一餐的热量与营养。
        </p>

        <ol class="setup-steps">
          <li>
            <span class="setup-steps__num">1</span>
            <span>选择餐次类型与用餐时间</span>
          </li>
          <li>
            <span class="setup-steps__num">2</span>
            <span>搜索食物并输入克数</span>
          </li>
          <li>
            <span class="setup-steps__num">3</span>
            <span>查看营养合计与替换建议</span>
          </li>
        </ol>
      </div>

      <div class="meal-setup__form">
        <p class="setup-label">餐次类型</p>
        <div class="meal-type-grid">
          <button
            v-for="option in MEAL_TYPE_OPTIONS"
            :key="option.value"
            type="button"
            class="meal-type-card"
            :class="{ 'is-active': mealType === option.value }"
            @click="mealType = option.value"
          >
            <span class="meal-type-card__emoji">{{ option.emoji }}</span>
            <span class="meal-type-card__label">{{ option.label }}</span>
            <span class="meal-type-card__hint">{{ option.timeHint }}</span>
          </button>
        </div>

        <div class="setup-time">
          <span class="setup-label">用餐时间（可选）</span>
          <el-date-picker
            v-model="eatenAt"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="默认取当前时间"
            class="setup-date"
          />
        </div>

        <el-button
          type="primary"
          size="large"
          class="setup-submit"
          :icon="Plus"
          :loading="creating"
          @click="handleCreateMeal"
        >
          {{ creating ? '创建中…' : '创建餐次' }}
        </el-button>
      </div>
    </section>

    <!-- 已有餐次：工作台 -->
    <template v-else>
      <section class="meal-banner surface-card">
        <div class="meal-banner__identity">
          <span class="meal-banner__emoji" aria-hidden="true">{{ activeMealMeta?.emoji }}</span>
          <div>
            <div class="meal-banner__title-row">
              <h2>{{ activeMealMeta?.label }}</h2>
              <el-tag size="small" effect="plain" round>{{ itemCount }} 样食物</el-tag>
            </div>
            <p class="meal-banner__meta">
              {{ formatDateTime(mealDetail?.eatenAt) }} · 餐次 #{{ activeMealId }}
            </p>
          </div>
        </div>
        <div class="meal-banner__hint">
          <span class="meal-banner__hint-dot"></span>
          数据实时保存，修改克数后自动重算
        </div>
      </section>

      <div class="workbench-grid">
        <div class="workbench-main">
          <!-- 添加食物 -->
          <section class="add-card surface-card">
            <div class="section-heading">
              <div class="section-heading__title">添加食物</div>
              <span class="section-heading__hint">搜索食物后输入克数</span>
            </div>

            <div class="add-form">
              <el-select
                v-model="selectedFoodId"
                class="add-form__food"
                filterable
                remote
                clearable
                reserve-keyword
                :remote-method="searchFoodOptions"
                :loading="foodSearchLoading"
                placeholder="输入关键词搜索食物，如：米饭"
                @visible-change="handleFoodSelectVisible"
              >
                <el-option
                  v-for="food in foodOptions"
                  :key="food.id"
                  :label="food.name"
                  :value="food.id"
                >
                  <div class="food-option">
                    <span class="food-option__name">{{ food.name }}</span>
                    <span class="food-option__meta">
                      {{ food.category }} · {{ formatNumber(food.caloriesPer100g) }} 千卡/100g
                    </span>
                  </div>
                </el-option>
              </el-select>

              <div class="quantity-input">
                <el-input-number
                  v-model="quantityG"
                  :min="1"
                  :max="5000"
                  :precision="1"
                  :step="10"
                  controls-position="right"
                />
                <span class="quantity-input__unit">g</span>
              </div>

              <el-button
                type="primary"
                :icon="Plus"
                :loading="addingItem"
                class="add-form__submit"
                @click="handleAddItem"
              >
                添加
              </el-button>
            </div>

            <div v-if="selectedFood" class="food-preview">
              <div class="food-preview__identity">
                <span class="food-preview__name">{{ selectedFood.name }}</span>
                <span class="food-preview__category">{{ selectedFood.category }}</span>
              </div>
              <div class="food-preview__stats">
                <span class="food-preview__calories">
                  {{ formatNumber(previewNutrition.calories) }} 千卡
                </span>
                <span>蛋白质 {{ formatNumber(previewNutrition.protein) }} g</span>
                <span>脂肪 {{ formatNumber(previewNutrition.fat) }} g</span>
                <span>碳水 {{ formatNumber(previewNutrition.carb) }} g</span>
              </div>
            </div>
          </section>

          <!-- 食物项列表 -->
          <section class="items-card surface-card">
            <div class="section-heading">
              <div class="section-heading__title">
                本餐食物
                <el-tag size="small" effect="plain" round>{{ itemCount }}</el-tag>
              </div>
              <span class="section-heading__hint">修改克数后自动重新计算</span>
            </div>

            <div v-loading="detailLoading" class="items-body">
              <EmptyState
                v-if="!(mealDetail?.items ?? []).length"
                description="这一餐还没有食物，先添加第一样吧"
                :image-size="88"
              >
                <span class="items-empty-hint">搜索食物 → 输入克数 → 点击添加</span>
              </EmptyState>

              <el-table v-else :data="mealDetail?.items ?? []" row-key="itemId" class="items-table">
                <el-table-column label="食物" min-width="160">
                  <template #default="{ row }">
                    <span class="item-name">{{ row.foodName }}</span>
                  </template>
                </el-table-column>

                <el-table-column label="克数" width="154">
                  <template #default="{ row }">
                    <div class="quantity-cell">
                      <el-input-number
                        v-model="row.quantityG"
                        :min="1"
                        :max="5000"
                        :precision="1"
                        :step="10"
                        size="small"
                        controls-position="right"
                        :disabled="updatingItemId === row.itemId"
                        @change="handleQuantityChange(row, $event)"
                      />
                      <span class="quantity-cell__unit">g</span>
                    </div>
                  </template>
                </el-table-column>

                <el-table-column label="热量" width="100" align="right">
                  <template #default="{ row }">
                    <span class="item-calories mono-number">{{ formatNumber(row.calories) }}</span>
                    <span class="nutrient__unit">kcal</span>
                  </template>
                </el-table-column>

                <el-table-column label="蛋白质" width="90" align="right">
                  <template #default="{ row }">
                    <span class="mono-number">{{ formatNumber(row.protein) }}</span>
                    <span class="nutrient__unit">g</span>
                  </template>
                </el-table-column>

                <el-table-column label="脂肪" width="85" align="right">
                  <template #default="{ row }">
                    <span class="mono-number">{{ formatNumber(row.fat) }}</span>
                    <span class="nutrient__unit">g</span>
                  </template>
                </el-table-column>

                <el-table-column label="碳水" width="85" align="right">
                  <template #default="{ row }">
                    <span class="mono-number">{{ formatNumber(row.carb) }}</span>
                    <span class="nutrient__unit">g</span>
                  </template>
                </el-table-column>

                <el-table-column label="操作" width="76" align="right">
                  <template #default="{ row }">
                    <el-button
                      link
                      type="danger"
                      :icon="Delete"
                      :loading="deletingItemId === row.itemId"
                      @click="handleDeleteItem(row)"
                    >
                      移除
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </section>
        </div>

        <aside class="workbench-side">
          <NutritionSummary
            :total="totalNutrition"
            :item-count="itemCount"
            :total-grams="totalGrams"
            :loading="detailLoading"
          />

          <div class="side-tip surface-card">
            <p class="side-tip__eyebrow">SMART SWAP</p>
            <p class="side-tip__title">不知道换什么？</p>
            <p class="side-tip__text">
              按食材逐个给出同类替换，热量是增是减、为什么推荐，一眼就能看到。
            </p>
            <el-button text type="primary" class="side-tip__action" @click="openReplacements">
              查看替换建议
              <el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
        </aside>
      </div>
    </template>

    <!-- 替换建议抽屉 -->
    <el-drawer
      v-model="replacementVisible"
      size="min(760px, 96vw)"
      :with-header="false"
      class="replacement-drawer"
    >
      <div class="replacement-panel">
        <header class="replacement-panel__head">
          <div>
            <p class="eyebrow">SMART SWAP</p>
            <h2 class="replacement-panel__title">替换建议</h2>
            <p class="replacement-panel__subtitle">{{ strategyDescription }}</p>
          </div>
          <el-button text circle :icon="Close" aria-label="关闭" @click="replacementVisible = false" />
        </header>

        <div class="replacement-toolbar">
          <el-radio-group v-model="replacementStrategy" @change="handleStrategyChange">
            <el-radio-button value="higher">更高热量</el-radio-button>
            <el-radio-button value="lower">更低热量</el-radio-button>
          </el-radio-group>
          <el-button
            text
            circle
            :icon="Refresh"
            :loading="replacementLoading"
            aria-label="刷新替换建议"
            @click="loadReplacements"
          />
          <el-button
            size="small"
            :icon="MagicStick"
            :loading="aiExplainLoading"
            :disabled="!replacementData || !replacementItems.length"
            @click="loadAiExplanations"
          >
            AI 解释
          </el-button>
        </div>

        <div v-loading="replacementLoading" class="replacement-body">
          <!-- 首次加载：骨架屏 -->
          <div v-if="replacementLoading && !replacementData" class="replacement-skeleton">
            <el-skeleton v-for="index in 3" :key="index" :rows="3" animated />
          </div>

          <EmptyState
            v-else-if="replacementError"
            description="替换建议加载失败，请检查网络后重试"
            :image-size="88"
          >
            <el-button type="primary" plain :icon="Refresh" @click="loadReplacements">
              重新加载
            </el-button>
          </EmptyState>

          <EmptyState
            v-else-if="replacementItems.length === 0"
            :description="
              itemCount === 0
                ? '这一餐还没有食物，先添加食物再来看替换建议'
                : replacementMessage || '暂时没有找到同类替换食物'
            "
            :image-size="88"
          >
            <el-button v-if="itemCount === 0" text type="primary" @click="replacementVisible = false">
              回到工作台添加食物
            </el-button>
            <span v-else class="replacement-empty-hint">试试切换更高 / 更低热量策略</span>
          </EmptyState>

          <template v-else>
            <el-alert
              v-if="replacementMessage"
              :title="replacementMessage"
              type="info"
              :closable="false"
              show-icon
              class="replacement-alert"
            />

            <div
              class="replacement-summary"
              :class="`replacement-summary--${replacementSummaryTone}`"
            >
              <span class="replacement-summary__label">全部采纳的预计变化</span>
              <span class="replacement-summary__value">{{ replacementSummaryText }}</span>
            </div>

            <section
              v-for="group in replacementItems"
              :key="group.itemId"
              class="replacement-group"
            >
              <header class="replacement-group__head">
                <div class="replacement-group__identity">
                  <span class="replacement-group__name">{{ group.originalFood.name }}</span>
                  <span class="replacement-group__meta">
                    {{ formatNumber(group.quantityG) }} g · 原热量
                    {{ formatNumber(group.originalCalories) }} 千卡
                  </span>
                </div>
                <div class="replacement-group__base">
                  <span class="mono-number">
                    {{ formatNumber(group.originalFood.caloriesPer100g) }}
                  </span>
                  <span>千卡/100g</span>
                </div>
              </header>

              <div v-if="group.suggestions.length" class="replacement-group__list">
                <ReplacementCard
                  v-for="suggestion in group.suggestions"
                  :key="suggestion.foodId"
                  :suggestion="suggestion"
                  :strategy="replacementStrategy"
                  :explanation="aiExplanations[suggestion.foodId]?.text"
                  :explanation-from-llm="aiExplanations[suggestion.foodId]?.fromLlm"
                />
              </div>

              <div v-else class="replacement-group__empty">
                <span class="replacement-group__empty-icon" aria-hidden="true">🍃</span>
                <p class="replacement-group__empty-title">
                  暂未找到{{ replacementStrategy === 'higher' ? '更高' : '更低' }}热量的同类替代
                </p>
                <p class="replacement-group__empty-text">
                  可以切换策略，或给这一餐补充更多同类食物后再试。
                </p>
              </div>
            </section>
          </template>
        </div>

        <footer class="replacement-panel__footer">
          替换建议按同类食物每 100g 热量计算，结果仅供饮食决策参考。
        </footer>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.meal-page {
  display: flex;
  flex-direction: column;
}

/* 顶部提示：这一餐已实时保存 */
.workbench-note {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--mw-muted);
}

.workbench-note__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--mw-primary);
  box-shadow: 0 0 0 4px rgba(47, 157, 107, 0.12);
  flex-shrink: 0;
}

.workbench-note__link {
  font-weight: 700;
  color: var(--mw-primary-dark);
}

.workbench-note__link:hover {
  text-decoration: underline;
}

/* 顶部操作区：三个按钮在中等屏幕允许换行 */
.page-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.page-actions__tooltip {
  display: inline-flex;
}

/* ============================ 创建餐次 ============================ */

.meal-setup {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  overflow: hidden;
}

.meal-setup__intro {
  padding: 40px 38px;
  background:
    radial-gradient(500px 320px at 100% 0%, rgba(47, 157, 107, 0.14), transparent 65%),
    linear-gradient(160deg, #f2fbf5 0%, #ffffff 72%);
  border-right: 1px solid var(--mw-line);
}

.meal-setup__title {
  font-size: 27px;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.meal-setup__desc {
  margin-top: 12px;
  font-size: 13.5px;
  line-height: 1.75;
  color: var(--mw-muted);
  max-width: 360px;
}

.setup-steps {
  list-style: none;
  margin: 30px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.setup-steps li {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: var(--mw-muted);
}

.setup-steps__num {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11.5px;
  font-weight: 800;
  color: var(--mw-primary-dark);
  background: #ffffff;
  border: 1px solid rgba(47, 157, 107, 0.28);
  box-shadow: 0 4px 10px -8px rgba(35, 123, 83, 0.7);
  flex-shrink: 0;
}

.meal-setup__form {
  padding: 36px 38px 38px;
  display: flex;
  flex-direction: column;
}

.setup-label {
  display: block;
  font-size: 11.5px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--mw-faint);
  margin-bottom: 10px;
}

.meal-type-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.meal-type-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 14px 15px;
  border: 1px solid var(--mw-line);
  border-radius: 14px;
  background: #fbfdfb;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease, box-shadow 0.18s ease,
    transform 0.18s ease;
}

.meal-type-card:hover {
  border-color: #b9d9c8;
  transform: translateY(-1px);
}

.meal-type-card.is-active {
  border-color: var(--mw-primary);
  background: var(--mw-primary-soft);
  box-shadow: 0 0 0 3px rgba(47, 157, 107, 0.1);
}

.meal-type-card__emoji {
  font-size: 19px;
  line-height: 1.3;
}

.meal-type-card__label {
  font-size: 14px;
  font-weight: 700;
  color: var(--mw-ink);
}

.meal-type-card__hint {
  font-size: 10.5px;
  color: var(--mw-faint);
}

.setup-time {
  margin-top: 22px;
}

.setup-date {
  width: 100%;
}

.setup-submit {
  margin-top: 26px;
  height: 46px;
  border-radius: 12px;
  font-size: 15px;
}

/* ============================ 当前餐次横幅 ============================ */

.meal-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 18px 22px;
  margin-bottom: 18px;
}

.meal-banner__identity {
  display: flex;
  align-items: center;
  gap: 14px;
}

.meal-banner__emoji {
  width: 48px;
  height: 48px;
  border-radius: 15px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  background: var(--mw-primary-soft);
  flex-shrink: 0;
}

.meal-banner__title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.meal-banner__title-row h2 {
  font-size: 19px;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.meal-banner__meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--mw-faint);
}

.meal-banner__hint {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  color: var(--mw-muted);
}

.meal-banner__hint-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--mw-primary);
  box-shadow: 0 0 0 4px rgba(47, 157, 107, 0.12);
}

/* ============================ 工作台布局 ============================ */

.workbench-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 330px;
  gap: 18px;
  align-items: start;
}

.workbench-main {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-width: 0;
}

.workbench-side {
  display: flex;
  flex-direction: column;
  gap: 18px;
  position: sticky;
  top: 90px;
}

/* ============================ 添加食物 ============================ */

.add-card {
  padding: 22px;
}

.add-form {
  display: flex;
  align-items: center;
  gap: 10px;
}

.add-form__food {
  flex: 1;
  min-width: 0;
}

.quantity-input {
  position: relative;
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.quantity-input :deep(.el-input-number) {
  width: 132px;
}

.quantity-input__unit {
  margin-left: 6px;
  font-size: 12px;
  color: var(--mw-faint);
}

.add-form__submit {
  flex-shrink: 0;
}

.food-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.food-option__name {
  font-weight: 600;
  color: var(--mw-ink);
}

.food-option__meta {
  font-size: 11.5px;
  color: var(--mw-faint);
}

.food-preview {
  margin-top: 14px;
  padding: 13px 15px;
  border-radius: 12px;
  background: #f7fbf8;
  border: 1px dashed #d6e7dc;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.food-preview__identity {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.food-preview__name {
  font-size: 13.5px;
  font-weight: 700;
}

.food-preview__category {
  font-size: 11px;
  color: var(--mw-faint);
}

.food-preview__stats {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--mw-muted);
}

.food-preview__calories {
  font-weight: 800;
  color: var(--mw-primary-dark);
}

/* ============================ 食物项表格 ============================ */

.items-card {
  padding: 22px 22px 14px;
}

.items-body {
  min-height: 250px;
}

.items-table {
  width: 100%;
}

.item-name {
  font-weight: 700;
  color: var(--mw-ink);
}

.item-calories {
  font-weight: 700;
}

.nutrient__unit {
  margin-left: 2px;
  font-size: 10.5px;
  color: var(--mw-faint);
  font-weight: 500;
}

.quantity-cell {
  display: flex;
  align-items: center;
  gap: 5px;
}

.quantity-cell :deep(.el-input-number) {
  width: 108px;
}

.quantity-cell__unit {
  font-size: 11px;
  color: var(--mw-faint);
}

.items-empty-hint {
  font-size: 12px;
  color: var(--mw-faint);
}

/* ============================ 侧栏提示 ============================ */

.side-tip {
  padding: 20px 21px;
}

.side-tip__eyebrow {
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.16em;
  color: var(--mw-primary);
}

.side-tip__title {
  margin-top: 8px;
  font-size: 15px;
  font-weight: 800;
}

.side-tip__text {
  margin-top: 8px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--mw-muted);
}

.side-tip__action {
  margin-top: 12px;
  padding: 0;
  font-size: 12.5px;
}

/* ============================ 替换建议抽屉 ============================ */

.replacement-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.replacement-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 26px 28px 0;
}

.replacement-panel__title {
  font-size: 25px;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.replacement-panel__subtitle {
  margin-top: 7px;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--mw-muted);
}

.replacement-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 28px;
  border-bottom: 1px solid var(--mw-line);
}

.replacement-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 28px 26px;
  min-height: 240px;
}

.replacement-skeleton {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.replacement-skeleton :deep(.el-skeleton) {
  padding: 16px;
  border: 1px solid var(--mw-line);
  border-radius: 14px;
}

.replacement-alert {
  margin-bottom: 14px;
  border-radius: 10px;
}

.replacement-summary {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 16px;
  border-radius: 12px;
  background: #f3f8f5;
  border: 1px solid #e2eee7;
  margin-bottom: 18px;
}

.replacement-summary--up {
  background: var(--mw-accent-soft);
  border-color: #f3e0c2;
}

.replacement-summary--down {
  background: var(--mw-primary-soft);
  border-color: #d2e9dc;
}

.replacement-summary__label {
  font-size: 10.5px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--mw-faint);
}

.replacement-summary__value {
  font-size: 14px;
  font-weight: 700;
  color: var(--mw-ink);
  line-height: 1.5;
}

.replacement-group {
  border: 1px solid var(--mw-line);
  border-radius: 16px;
  padding: 16px;
  background: #ffffff;
}

.replacement-group + .replacement-group {
  margin-top: 14px;
}

.replacement-group__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding-bottom: 13px;
  border-bottom: 1px dashed var(--mw-line);
}

.replacement-group__identity {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.replacement-group__name {
  font-size: 15px;
  font-weight: 800;
  color: var(--mw-ink);
}

.replacement-group__meta {
  font-size: 11.5px;
  color: var(--mw-faint);
}

.replacement-group__base {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  flex-shrink: 0;
}

.replacement-group__base .mono-number {
  font-size: 19px;
  font-weight: 800;
  color: var(--mw-ink);
  line-height: 1.1;
}

.replacement-group__base span:last-child {
  font-size: 10.5px;
  color: var(--mw-faint);
}

.replacement-group__list {
  display: flex;
  flex-direction: column;
  gap: 9px;
  padding-top: 13px;
}

.replacement-group__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 24px 16px 18px;
}

.replacement-group__empty-icon {
  font-size: 22px;
  line-height: 1;
  opacity: 0.8;
}

.replacement-group__empty-title {
  margin-top: 8px;
  font-size: 13px;
  font-weight: 700;
  color: var(--mw-muted);
}

.replacement-group__empty-text {
  margin-top: 4px;
  font-size: 11.5px;
  color: var(--mw-faint);
}

.replacement-empty-hint {
  font-size: 12px;
  color: var(--mw-faint);
}

.replacement-panel__footer {
  padding: 14px 28px 18px;
  border-top: 1px solid var(--mw-line);
  font-size: 11px;
  line-height: 1.6;
  color: var(--mw-faint);
}

/* 抽屉内部结构需要撑满高度 */
:deep(.replacement-drawer .el-drawer__body) {
  padding: 0;
}

/* ============================ 响应式 ============================ */

@media (max-width: 1080px) {
  .workbench-grid {
    grid-template-columns: minmax(0, 1fr) 300px;
  }
}

@media (max-width: 900px) {
  .meal-setup {
    grid-template-columns: 1fr;
  }

  .meal-setup__intro {
    border-right: none;
    border-bottom: 1px solid var(--mw-line);
    padding: 30px 28px;
  }

  .meal-setup__form {
    padding: 26px 28px 30px;
  }

  .workbench-grid {
    grid-template-columns: 1fr;
  }

  .workbench-side {
    position: static;
  }
}

@media (max-width: 620px) {
  .meal-banner {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }

  .page-actions__tooltip {
    flex: 1;
    min-width: 100%;
  }

  .page-actions__tooltip :deep(.el-button) {
    width: 100%;
  }

  .add-form {
    flex-wrap: wrap;
  }

  .add-form__food {
    flex-basis: 100%;
  }

  .quantity-input {
    flex: 1;
  }

  .quantity-input :deep(.el-input-number) {
    width: 100%;
  }

  .add-form__submit {
    flex: 1;
  }

  .meal-type-grid {
    grid-template-columns: 1fr;
  }

  .replacement-panel__head,
  .replacement-toolbar,
  .replacement-body,
  .replacement-panel__footer {
    padding-left: 18px;
    padding-right: 18px;
  }

  .replacement-panel__head {
    padding-top: 20px;
  }
}
</style>
