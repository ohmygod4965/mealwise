<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Star } from '@element-plus/icons-vue'
import { generateMealPlan } from '@/api/ai'
import type { AiGoal, AiPlanVO, MealType } from '@/types/api'
import { formatNumber } from '@/utils/format'

/**
 * AI 饮食推荐页。
 *
 * 交互刻意做成「一问一答」：左边选目标，右边出方案。
 * 生成期间展示进度提示（大模型 + 联网核验通常 10-30 秒），避免用户以为卡死。
 */

const GOAL_OPTIONS: { value: AiGoal; label: string; hint: string }[] = [
  { value: 'FAT_LOSS', label: '减脂', hint: '热量缺口，蛋白质拉高' },
  { value: 'MAINTAIN', label: '维持', hint: '按你的 TDEE 持平' },
  { value: 'MUSCLE_GAIN', label: '增肌', hint: '热量略高，蛋白质优先' }
]

const MEAL_OPTIONS: { value: MealType; label: string }[] = [
  { value: 'BREAKFAST', label: '早餐' },
  { value: 'LUNCH', label: '午餐' },
  { value: 'DINNER', label: '晚餐' },
  { value: 'SNACK', label: '加餐' }
]

const MEAL_LABEL: Record<string, string> = {
  BREAKFAST: '早餐',
  LUNCH: '午餐',
  DINNER: '晚餐',
  SNACK: '加餐'
}

const goal = ref<AiGoal>('MAINTAIN')
const targetCalories = ref<number | undefined>(undefined)
const mealTypes = ref<MealType[]>(['BREAKFAST', 'LUNCH', 'DINNER'])
const preferences = ref('')
const excludeText = ref('')

const loading = ref(false)
const plan = ref<AiPlanVO | null>(null)
const errorMessage = ref('')

const canSubmit = computed(() => !loading.value && mealTypes.value.length > 0)

const goalLabel = computed(
  () => GOAL_OPTIONS.find((item) => item.value === plan.value?.goal)?.label ?? '维持'
)

const totalDelta = computed(() => {
  if (!plan.value) return 0
  return Math.round((plan.value.totals.calories - plan.value.targetCalories) * 10) / 10
})

const deltaText = computed(() => {
  const value = totalDelta.value
  if (value === 0) return '与目标一致'
  return value > 0 ? `超出 ${formatNumber(Math.abs(value))} kcal` : `还差 ${formatNumber(Math.abs(value))} kcal`
})

function stripAllergens(): string[] {
  return excludeText.value
    .split(/[,，、\s]+/)
    .map((item) => item.trim().toLowerCase())
    .filter(Boolean)
}

async function handleGenerate(): Promise<void> {
  if (!canSubmit.value) {
    ElMessage.warning('至少选择一餐')
    return
  }

  loading.value = true
  errorMessage.value = ''
  try {
    plan.value = await generateMealPlan({
      goal: goal.value,
      targetCalories: targetCalories.value ?? undefined,
      mealTypes: mealTypes.value,
      preferences: preferences.value.trim() || undefined,
      excludeAllergens: stripAllergens()
    })
    ElMessage.success('方案已生成')
  } catch (error) {
    const message =
      error instanceof Error && error.message ? error.message : '生成失败，请稍后重试'
    errorMessage.value = message
    plan.value = null
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="ai-page">
    <section class="ai-hero">
      <div class="ai-hero__text">
        <span class="ai-hero__badge">
          <el-icon><Star /></el-icon>
          AI 饮食推荐
        </span>
        <h1>让大模型帮你算好这一天的三餐</h1>
        <p>
          模型先给出搭配方案，再逐样联网核验食物热量（Open Food Facts 公开营养库），
          最后按项目统一的换算口径汇总——模型负责「想吃什么」，数据负责「多少热量」。
        </p>
      </div>
    </section>

    <div class="ai-layout">
      <!-- 左：参数 -->
      <aside class="ai-panel">
        <h2 class="panel-title">告诉我你的目标</h2>

        <div class="field">
          <span class="field__label">饮食目标</span>
          <div class="goal-list">
            <button
              v-for="option in GOAL_OPTIONS"
              :key="option.value"
              type="button"
              class="goal-item"
              :class="{ 'is-active': goal === option.value }"
              @click="goal = option.value"
            >
              <strong>{{ option.label }}</strong>
              <span>{{ option.hint }}</span>
            </button>
          </div>
        </div>

        <div class="field">
          <span class="field__label">每日目标热量（可选）</span>
          <el-input-number
            v-model="targetCalories"
            :min="800"
            :max="5000"
            :step="50"
            placeholder="留空则按你的身体资料自动计算"
            controls-position="right"
            class="w-full"
          />
          <small class="field__hint">留空时按性别/身高/体重/年龄算出的 TDEE</small>
        </div>

        <div class="field">
          <span class="field__label">需要安排</span>
          <el-checkbox-group v-model="mealTypes" class="meal-checks">
            <el-checkbox v-for="item in MEAL_OPTIONS" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-checkbox>
          </el-checkbox-group>
        </div>

        <div class="field">
          <span class="field__label">口味偏好（可选）</span>
          <el-input
            v-model="preferences"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="例如：想吃中餐、少油、不吃香菜"
          />
        </div>

        <div class="field">
          <span class="field__label">额外忌口（可选）</span>
          <el-input v-model="excludeText" placeholder="用逗号分隔，如 egg,dairy" />
          <small class="field__hint">会与你账号里的过敏原合并</small>
        </div>

        <el-button type="primary" size="large" :loading="loading" class="w-full" @click="handleGenerate">
          {{ loading ? '正在生成…' : '生成 AI 方案' }}
        </el-button>
        <p v-if="loading" class="loading-hint">
          大模型正在搭配并逐样联网核验热量，通常需要 10–30 秒
        </p>
      </aside>

      <!-- 右：结果 -->
      <section class="ai-result">
        <div v-if="!plan && !loading && !errorMessage" class="result-empty">
          <div class="result-empty__icon">🍱</div>
          <h3>还没有方案</h3>
          <p>选好目标后点「生成 AI 方案」，右侧会出现三餐搭配、每样食物的克数与热量。</p>
        </div>

        <el-alert v-if="errorMessage" type="error" :closable="false" show-icon :title="errorMessage">
          <template #default>
            <p class="error-tip">可以调整偏好后重试；若持续失败，后端会自动降级为本地规则搭配。</p>
          </template>
        </el-alert>

        <template v-if="plan">
          <div class="summary-card">
            <div class="summary-card__head">
              <span class="tag tag--goal">{{ goalLabel }}</span>
              <span class="tag" :class="plan.source === 'llm' ? 'tag--llm' : 'tag--local'">
                {{ plan.source === 'llm' ? `大模型 · ${plan.model ?? ''}` : '本地规则兜底' }}
              </span>
              <button type="button" class="regenerate" :disabled="loading" @click="handleGenerate">
                <el-icon><Refresh /></el-icon>
                重新生成
              </button>
            </div>

            <p class="summary-card__text">{{ plan.summary }}</p>

            <div class="summary-metrics">
              <div class="metric">
                <span class="metric__label">总热量</span>
                <strong>{{ formatNumber(plan.totals.calories) }}</strong>
                <small>/ {{ formatNumber(plan.targetCalories) }} kcal</small>
              </div>
              <div class="metric">
                <span class="metric__label">蛋白质</span>
                <strong>{{ formatNumber(plan.totals.protein) }}</strong>
                <small>g</small>
              </div>
              <div class="metric">
                <span class="metric__label">脂肪</span>
                <strong>{{ formatNumber(plan.totals.fat) }}</strong>
                <small>g</small>
              </div>
              <div class="metric">
                <span class="metric__label">碳水</span>
                <strong>{{ formatNumber(plan.totals.carb) }}</strong>
                <small>g</small>
              </div>
            </div>

            <p class="summary-card__delta" :class="{ 'is-over': totalDelta > 0 }">{{ deltaText }}</p>

            <ul v-if="plan.notes.length" class="notes">
              <li v-for="note in plan.notes" :key="note">{{ note }}</li>
            </ul>
          </div>

          <div v-for="meal in plan.meals" :key="meal.mealType" class="meal-card">
            <header class="meal-card__head">
              <h3>{{ MEAL_LABEL[meal.mealType] ?? meal.mealType }}</h3>
              <span class="meal-card__total">{{ formatNumber(meal.total.calories) }} kcal</span>
            </header>

            <div class="item-row item-row--head">
              <span>食物</span>
              <span>克数</span>
              <span>热量</span>
              <span>蛋白</span>
              <span>脂肪</span>
              <span>碳水</span>
              <span>来源</span>
            </div>

            <div v-for="(item, index) in meal.items" :key="`${item.name}-${index}`" class="item-row">
              <span class="item-name">
                {{ item.name }}
                <small v-if="item.grams">{{ formatNumber(item.kcalPer100g) }} kcal/100g</small>
              </span>
              <span>{{ formatNumber(item.grams) }}g</span>
              <span class="item-cal">{{ formatNumber(item.calories) }}</span>
              <span>{{ formatNumber(item.protein) }}</span>
              <span>{{ formatNumber(item.fat) }}</span>
              <span>{{ formatNumber(item.carb) }}</span>
              <span>
                <a
                  v-if="item.verifySource === 'web' && item.sourceUrl"
                  class="verify verify--ok"
                  :href="item.sourceUrl"
                  target="_blank"
                  rel="noreferrer"
                  >联网核验</a
                >
                <span v-else-if="item.verifySource === 'food-db'" class="verify verify--db">本地食库</span>
                <span v-else class="verify verify--estimate">模型估算</span>
              </span>
            </div>
          </div>

          <p class="disclaimer">
            以上为模型生成的参考方案，不构成医疗或营养处方；特殊健康状况请咨询专业营养师。
          </p>
        </template>
      </section>
    </div>
  </div>
</template>

<style scoped>
.ai-page {
  max-width: 1220px;
  margin: 0 auto;
  padding: 28px 28px 64px;
}

.ai-hero {
  margin-bottom: 22px;
}

.ai-hero__badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 11px;
  border-radius: 999px;
  background: var(--mw-primary-soft, #e8f5ee);
  color: var(--mw-primary-dark, #237b53);
  font-size: 12px;
  font-weight: 700;
}

.ai-hero h1 {
  margin: 12px 0 8px;
  font-size: 26px;
  letter-spacing: -0.02em;
  color: var(--mw-ink, #17251f);
}

.ai-hero p {
  max-width: 720px;
  color: var(--mw-muted, #5b6b62);
  font-size: 14px;
  line-height: 1.7;
}

.ai-layout {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.ai-panel {
  position: sticky;
  top: 88px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px;
  border-radius: 18px;
  border: 1px solid var(--mw-line, #e3ebe6);
  background: #ffffff;
  box-shadow: 0 18px 40px -32px rgba(23, 37, 31, 0.5);
}

.panel-title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--mw-muted, #5b6b62);
}

.field__hint {
  font-size: 11.5px;
  color: var(--mw-faint, #8a9a91);
}

.goal-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.goal-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid var(--mw-line, #e3ebe6);
  background: #fbfdfb;
  text-align: left;
  cursor: pointer;
  font-family: inherit;
  transition: border-color 0.16s ease, background 0.16s ease;
}

.goal-item strong {
  font-size: 14px;
  color: var(--mw-ink, #17251f);
}

.goal-item span {
  font-size: 12px;
  color: var(--mw-faint, #8a9a91);
}

.goal-item.is-active {
  border-color: var(--mw-primary, #2f9d6b);
  background: var(--mw-primary-soft, #e8f5ee);
}

.meal-checks {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
}

.w-full {
  width: 100%;
}

.loading-hint {
  margin: 0;
  font-size: 12px;
  color: var(--mw-faint, #8a9a91);
  text-align: center;
}

.ai-result {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 320px;
}

.result-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 64px 24px;
  border-radius: 18px;
  border: 1px dashed var(--mw-line, #e3ebe6);
  background: #ffffff;
  text-align: center;
}

.result-empty__icon {
  font-size: 34px;
}

.result-empty h3 {
  margin: 4px 0 0;
  font-size: 16px;
}

.result-empty p {
  margin: 0;
  max-width: 380px;
  font-size: 13px;
  color: var(--mw-muted, #5b6b62);
  line-height: 1.7;
}

.error-tip {
  margin: 4px 0 0;
  font-size: 12.5px;
}

.summary-card {
  padding: 20px;
  border-radius: 18px;
  border: 1px solid var(--mw-line, #e3ebe6);
  background: linear-gradient(160deg, #ffffff, #f6fbf8);
  box-shadow: 0 20px 44px -36px rgba(23, 37, 31, 0.55);
}

.summary-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.tag {
  padding: 3px 9px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 700;
}

.tag--goal {
  background: #eef4ff;
  color: #2a5bb8;
}

.tag--llm {
  background: var(--mw-primary-soft, #e8f5ee);
  color: var(--mw-primary-dark, #237b53);
}

.tag--local {
  background: #fff4e5;
  color: #a8690f;
}

.regenerate {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 11px;
  border-radius: 9px;
  border: 1px solid var(--mw-line, #e3ebe6);
  background: #ffffff;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--mw-muted, #5b6b62);
  cursor: pointer;
  font-family: inherit;
}

.regenerate:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.summary-card__text {
  margin: 12px 0 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--mw-ink, #17251f);
}

.summary-metrics {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.metric {
  padding: 10px 12px;
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid var(--mw-line, #e3ebe6);
}

.metric__label {
  display: block;
  font-size: 11.5px;
  color: var(--mw-faint, #8a9a91);
}

.metric strong {
  font-size: 19px;
  letter-spacing: -0.02em;
  color: var(--mw-ink, #17251f);
}

.metric small {
  margin-left: 3px;
  font-size: 11.5px;
  color: var(--mw-faint, #8a9a91);
}

.summary-card__delta {
  margin: 12px 0 0;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--mw-primary-dark, #237b53);
}

.summary-card__delta.is-over {
  color: #c0483f;
}

.notes {
  margin: 10px 0 0;
  padding-left: 18px;
  font-size: 12px;
  color: var(--mw-muted, #5b6b62);
  line-height: 1.8;
}

.meal-card {
  padding: 16px 18px;
  border-radius: 16px;
  border: 1px solid var(--mw-line, #e3ebe6);
  background: #ffffff;
}

.meal-card__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 10px;
}

.meal-card__head h3 {
  margin: 0;
  font-size: 15px;
}

.meal-card__total {
  font-size: 13px;
  font-weight: 700;
  color: var(--mw-primary-dark, #237b53);
}

.item-row {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) 64px 72px 60px 60px 60px 90px;
  gap: 8px;
  padding: 9px 0;
  border-top: 1px solid rgba(227, 235, 230, 0.7);
  font-size: 13px;
  color: var(--mw-ink, #17251f);
  align-items: center;
}

.item-row--head {
  border-top: none;
  padding-top: 0;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--mw-faint, #8a9a91);
}

.item-name {
  display: flex;
  flex-direction: column;
  gap: 1px;
  font-weight: 600;
}

.item-name small {
  font-size: 11px;
  font-weight: 400;
  color: var(--mw-faint, #8a9a91);
}

.item-cal {
  font-weight: 700;
  color: var(--mw-primary-dark, #237b53);
}

.verify {
  font-size: 11px;
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
}

.verify--ok {
  color: #2a7d4f;
  border-bottom: 1px dashed currentColor;
}

.verify--db {
  color: #2a5bb8;
}

.verify--estimate {
  color: #a8690f;
}

.disclaimer {
  margin: 0;
  font-size: 11.5px;
  color: var(--mw-faint, #8a9a91);
  text-align: center;
}

@media (max-width: 1024px) {
  .ai-layout {
    grid-template-columns: 1fr;
  }

  .ai-panel {
    position: static;
  }
}

@media (max-width: 760px) {
  .ai-page {
    padding: 18px 16px 48px;
  }

  .ai-hero h1 {
    font-size: 21px;
  }

  .summary-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .item-row {
    grid-template-columns: minmax(0, 1.4fr) 52px 62px 78px;
    font-size: 12.5px;
  }

  /* 移动端只保留 食物 / 克数 / 热量 / 来源，营养明细隐藏避免挤压 */
  .item-row > span:nth-child(4),
  .item-row > span:nth-child(5),
  .item-row > span:nth-child(6) {
    display: none;
  }
}
</style>
