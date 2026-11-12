<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Refresh } from '@element-plus/icons-vue'
import { getDayRecord, getTodayRecord } from '@/api/day'
import EmptyState from '@/components/EmptyState.vue'
import ProfileDialog from '@/components/ProfileDialog.vue'
import { MEAL_TYPE_OPTIONS } from '@/constants/meal'
import type {
  ComparisonStatus,
  DayComparisonVO,
  DayRecordVO,
  NutritionTotals
} from '@/types/api'
import { formatDateKey, formatDateTime, formatNumber } from '@/utils/format'

const router = useRouter()

const emptyTotals: NutritionTotals = { calories: 0, protein: 0, fat: 0, carb: 0 }

/** 后端状态 → 中文标签 / el-tag 类型 */
const STATUS_LABEL: Record<ComparisonStatus, string> = {
  UNDER: '未达标',
  ON_TRACK: '达标',
  OVER: '超标'
}
const STATUS_TAG: Record<ComparisonStatus, 'warning' | 'success' | 'danger'> = {
  UNDER: 'warning',
  ON_TRACK: 'success',
  OVER: 'danger'
}

/** 对比区四个指标的展示顺序与单位 */
const METRIC_META = [
  { key: 'calories', label: '热量', unit: 'kcal' },
  { key: 'protein', label: '蛋白质', unit: 'g' },
  { key: 'fat', label: '脂肪', unit: 'g' },
  { key: 'carb', label: '碳水', unit: 'g' }
] as const satisfies ReadonlyArray<{
  key: keyof DayComparisonVO
  label: string
  unit: string
}>

interface MetricView {
  key: keyof DayComparisonVO
  label: string
  unit: string
  intake: number
  target: number
  percentText: string
  barPercent: number
  status: ComparisonStatus
  statusLabel: string
  statusTag: 'warning' | 'success' | 'danger'
  diffText: string
}

/* ------------------------------ 日期与数据 ------------------------------ */

const today = ref(formatDateKey())
const selectedDate = ref(today.value)
const record = ref<DayRecordVO | null>(null)
const loading = ref(true)
const loadError = ref(false)
const profileVisible = ref(false)

const isToday = computed(() => selectedDate.value === today.value)
const totals = computed<NutritionTotals>(() => record.value?.totals ?? emptyTotals)
const targets = computed(() => record.value?.targets ?? null)
const hasAnyMeal = computed(() => (record.value?.meals?.length ?? 0) > 0)

const pageSubtitle = computed(() =>
  isToday.value
    ? '今天的每一餐都会自动汇总到这里，对照每日建议看看完成得怎么样。'
    : `正在查看 ${selectedDate.value} 的记录，可切换日期回看历史。`
)

/** 按早餐 → 午餐 → 晚餐 → 加餐的固定顺序分组，并算好每个分区的小计 */
const mealGroups = computed(() =>
  MEAL_TYPE_OPTIONS.map((option) => {
    const meals = (record.value?.meals ?? []).filter((meal) => meal.mealType === option.value)
    const subtotal = meals.reduce<NutritionTotals>(
      (acc, meal) => ({
        calories: acc.calories + (meal.total?.calories ?? 0),
        protein: acc.protein + (meal.total?.protein ?? 0),
        fat: acc.fat + (meal.total?.fat ?? 0),
        carb: acc.carb + (meal.total?.carb ?? 0)
      }),
      { ...emptyTotals }
    )
    const itemCount = meals.reduce((sum, meal) => sum + (meal.items?.length ?? 0), 0)
    return { ...option, meals, subtotal, itemCount }
  })
)

/** 摄入 vs 建议：进度条上限 100%，但百分比文案保留真实值（超标时能看出超了多少） */
const metrics = computed<MetricView[]>(() => {
  const comparison = record.value?.comparison
  if (!comparison) return []

  return METRIC_META.map((meta) => {
    const metric = comparison[meta.key]
    const intake = Number(metric?.intake ?? 0)
    const target = Number(metric?.target ?? 0)
    const percentFromApi = metric?.percent
    const rawPercent =
      typeof percentFromApi === 'number' && Number.isFinite(percentFromApi)
        ? percentFromApi
        : target > 0
          ? (intake / target) * 100
          : 0
    const gap = target - intake
    const diffText =
      Math.abs(gap) < 0.05
        ? '正好达到建议值'
        : gap > 0
          ? `还差 ${formatNumber(gap)} ${meta.unit}`
          : `已超 ${formatNumber(-gap)} ${meta.unit}`
    const status: ComparisonStatus = metric?.status ?? (gap < 0 ? 'OVER' : 'ON_TRACK')

    return {
      ...meta,
      intake,
      target,
      percentText: `${Math.round(rawPercent)}%`,
      barPercent: Math.max(0, Math.min(100, Math.round(rawPercent))),
      status,
      statusLabel: STATUS_LABEL[status],
      statusTag: STATUS_TAG[status],
      diffText
    }
  })
})

let loadSeq = 0

/** 拉取当天 / 指定日期的记录；静默处理错误，由页面内重试态兜底 */
async function loadDay(): Promise<void> {
  const seq = ++loadSeq
  loading.value = true
  loadError.value = false
  try {
    const date = selectedDate.value
    // 今天用 /days/today 交给后端判定，历史日期按 yyyy-MM-dd 查询
    const data =
      date === today.value
        ? await getTodayRecord({ silent: true })
        : await getDayRecord(date, { silent: true })
    if (seq !== loadSeq) return
    record.value = data
  } catch {
    if (seq !== loadSeq) return
    record.value = null
    loadError.value = true
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

/** 用户切日期后自动重新拉取 */
watch(selectedDate, () => {
  void loadDay()
})

function handleToday(): void {
  if (isToday.value) return
  selectedDate.value = today.value
}

function goWorkbench(): void {
  void router.push('/meals')
}

function openProfile(): void {
  profileVisible.value = true
}

function handleProfileSaved(): void {
  // 资料保存后 targets / comparison 会变化，重新拉取当日记录
  void loadDay()
}

/** 顶部导航里的资料弹窗保存后也会广播事件，这里同步刷新 */
function handleProfileUpdated(): void {
  void loadDay()
}

function barColor(status: ComparisonStatus): string {
  if (status === 'OVER') return '#d9534f'
  if (status === 'UNDER') return '#e9a23b'
  return '#2f9d6b'
}

function statusClass(status: ComparisonStatus): string {
  if (status === 'OVER') return 'is-over'
  if (status === 'UNDER') return 'is-under'
  return 'is-on-track'
}

onMounted(() => {
  window.addEventListener('mealwise:profile-updated', handleProfileUpdated)
  void loadDay()
})

onBeforeUnmount(() => {
  window.removeEventListener('mealwise:profile-updated', handleProfileUpdated)
})
</script>

<template>
  <div class="day-page">
    <header class="page-head day-head">
      <div class="day-head__intro">
        <p class="eyebrow">DAILY RECORD</p>
        <h1 class="page-title">今日记录</h1>
        <p class="page-subtitle">{{ pageSubtitle }}</p>
      </div>

      <div class="day-head__tools">
        <div class="day-date">
          <el-date-picker
            v-model="selectedDate"
            type="date"
            value-format="YYYY-MM-DD"
            format="YYYY-MM-DD"
            :clearable="false"
            placeholder="选择日期"
            class="day-date__picker"
          />
          <el-button v-if="!isToday" class="day-date__today" @click="handleToday">今天</el-button>
          <el-tag v-else class="day-date__badge" type="success" effect="plain" round>今天</el-tag>
        </div>

        <div class="day-total">
          <span class="day-total__label">{{ isToday ? '今日总热量' : '当日总热量' }}</span>
          <p class="day-total__value">
            <span class="mono-number">{{ record ? formatNumber(totals.calories) : '—' }}</span>
            <span class="day-total__unit">千卡</span>
          </p>
        </div>
      </div>
    </header>

    <!-- 首次加载：骨架屏 -->
    <div v-if="loading && !record" class="day-grid">
      <div class="day-main">
        <div v-for="index in 3" :key="index" class="surface-card day-skeleton">
          <el-skeleton :rows="4" animated />
        </div>
      </div>
      <aside class="day-side">
        <div class="surface-card day-skeleton">
          <el-skeleton :rows="9" animated />
        </div>
      </aside>
    </div>

    <!-- 请求失败：可重试 -->
    <section v-else-if="loadError" class="surface-card day-error">
      <EmptyState description="每日记录加载失败，请检查网络后重试" :image-size="96">
        <el-button type="primary" plain :icon="Refresh" @click="loadDay">重新加载</el-button>
      </EmptyState>
    </section>

    <!-- 正常内容 -->
    <div v-else-if="record" v-loading="loading" class="day-body">
      <div v-if="!hasAnyMeal" class="day-empty-banner">
        <span class="day-empty-banner__emoji" aria-hidden="true">🌱</span>
        <p>{{ isToday ? '今天还什么都没有记录' : `${selectedDate} 没有记录` }}，从第一餐开始吧。</p>
        <el-button type="primary" plain size="small" @click="goWorkbench">去记录</el-button>
      </div>

      <div class="day-grid">
        <div class="day-main">
          <section
            v-for="group in mealGroups"
            :key="group.value"
            class="meal-section surface-card"
          >
            <header class="meal-section__head">
              <div class="meal-section__identity">
                <span class="meal-section__emoji" aria-hidden="true">{{ group.emoji }}</span>
                <div>
                  <div class="meal-section__title-row">
                    <h2>{{ group.label }}</h2>
                    <el-tag v-if="group.itemCount" size="small" effect="plain" round>
                      {{ group.itemCount }} 样食物
                    </el-tag>
                  </div>
                  <p class="meal-section__sub">{{ group.timeHint }}</p>
                </div>
              </div>

              <div v-if="group.itemCount" class="meal-section__sum">
                <p class="meal-section__calories">
                  <span class="mono-number">{{ formatNumber(group.subtotal.calories) }}</span>
                  <span class="meal-section__unit">kcal</span>
                </p>
                <p class="meal-section__macros">
                  蛋白 {{ formatNumber(group.subtotal.protein) }} g · 脂肪
                  {{ formatNumber(group.subtotal.fat) }} g · 碳水
                  {{ formatNumber(group.subtotal.carb) }} g
                </p>
              </div>
            </header>

            <!-- 该餐次分区为空 -->
            <div v-if="!group.meals.length" class="meal-section__empty">
              <p>还没有记录{{ group.label }}</p>
              <el-button text type="primary" @click="goWorkbench">
                去记录
                <el-icon class="el-icon--right"><ArrowRight /></el-icon>
              </el-button>
            </div>

            <!-- 该餐次分区下的每一餐 -->
            <div v-else class="meal-blocks">
              <article v-for="meal in group.meals" :key="meal.mealId" class="meal-block">
                <header class="meal-block__head">
                  <span class="meal-block__time">{{ formatDateTime(meal.eatenAt) }}</span>
                  <span class="meal-block__subtotal">
                    小计
                    <strong class="mono-number">{{ formatNumber(meal.total.calories) }}</strong>
                    kcal
                    <span class="meal-block__macros">
                      蛋白 {{ formatNumber(meal.total.protein) }} g · 脂肪
                      {{ formatNumber(meal.total.fat) }} g · 碳水
                      {{ formatNumber(meal.total.carb) }} g
                    </span>
                  </span>
                </header>

                <el-table :data="meal.items" row-key="itemId" size="small" class="meal-table">
                  <el-table-column prop="foodName" label="食物" min-width="140">
                    <template #default="{ row }">
                      <span class="item-name">{{ row.foodName }}</span>
                    </template>
                  </el-table-column>

                  <el-table-column label="克数" width="92" align="right">
                    <template #default="{ row }">
                      <span class="mono-number">{{ formatNumber(row.quantityG) }}</span>
                      <span class="nutrient__unit">g</span>
                    </template>
                  </el-table-column>

                  <el-table-column label="热量" width="100" align="right">
                    <template #default="{ row }">
                      <span class="item-calories mono-number">
                        {{ formatNumber(row.calories) }}
                      </span>
                      <span class="nutrient__unit">kcal</span>
                    </template>
                  </el-table-column>

                  <el-table-column label="蛋白质" width="88" align="right">
                    <template #default="{ row }">
                      <span class="mono-number">{{ formatNumber(row.protein) }}</span>
                      <span class="nutrient__unit">g</span>
                    </template>
                  </el-table-column>

                  <el-table-column label="脂肪" width="82" align="right">
                    <template #default="{ row }">
                      <span class="mono-number">{{ formatNumber(row.fat) }}</span>
                      <span class="nutrient__unit">g</span>
                    </template>
                  </el-table-column>

                  <el-table-column label="碳水" width="82" align="right">
                    <template #default="{ row }">
                      <span class="mono-number">{{ formatNumber(row.carb) }}</span>
                      <span class="nutrient__unit">g</span>
                    </template>
                  </el-table-column>
                </el-table>
              </article>
            </div>
          </section>
        </div>

        <aside class="day-side">
          <section class="compare-card surface-card">
            <header class="compare-card__head">
              <div>
                <p class="eyebrow">DAILY TARGET</p>
                <h2 class="compare-card__title">今日摄入 vs 建议</h2>
              </div>
              <el-button
                text
                circle
                :icon="Refresh"
                :loading="loading"
                aria-label="刷新今日记录"
                @click="loadDay"
              />
            </header>

            <!-- 未填写身体资料：引导去填写 -->
            <div v-if="!targets" class="profile-guide">
              <span class="profile-guide__icon" aria-hidden="true">🧭</span>
              <p class="profile-guide__title">先完善身体信息，才能给出每日建议</p>
              <p class="profile-guide__text">
                填写性别、身高、体重与年龄后，我们会估算你的基础代谢和每日建议摄入，并在这里显示完成度。
              </p>
              <el-button type="primary" @click="openProfile">去填写</el-button>
            </div>

            <template v-else>
              <div class="compare-targets">
                <span>
                  建议热量
                  <strong class="mono-number">{{ formatNumber(targets.calories) }}</strong>
                  kcal
                </span>
                <span>
                  基础代谢 {{ formatNumber(targets.bmr) }} · 每日消耗
                  {{ formatNumber(targets.tdee) }} kcal
                </span>
              </div>

              <div v-if="metrics.length" class="metric-list">
                <div
                  v-for="metric in metrics"
                  :key="metric.key"
                  class="metric"
                  :class="statusClass(metric.status)"
                >
                  <div class="metric__head">
                    <span class="metric__label">{{ metric.label }}</span>
                    <el-tag size="small" :type="metric.statusTag" effect="light" round>
                      {{ metric.statusLabel }}
                    </el-tag>
                  </div>
                  <p class="metric__value">
                    <span class="mono-number">{{ formatNumber(metric.intake) }}</span>
                    <span class="metric__divider">/</span>
                    <span class="metric__target">
                      {{ formatNumber(metric.target) }} {{ metric.unit }}
                    </span>
                    <span class="metric__percent">{{ metric.percentText }}</span>
                  </p>
                  <el-progress
                    :percentage="metric.barPercent"
                    :color="barColor(metric.status)"
                    :show-text="false"
                    :stroke-width="8"
                    class="metric__bar"
                  />
                  <p class="metric__diff">{{ metric.diffText }}</p>
                </div>
              </div>

              <div class="compare-formula">
                <p class="compare-formula__title">计算口径</p>
                <p v-if="targets.formula" class="compare-formula__text">{{ targets.formula }}</p>
                <ul v-if="targets.assumptions?.length" class="compare-formula__list">
                  <li v-for="(assumption, index) in targets.assumptions" :key="index">
                    {{ assumption }}
                  </li>
                </ul>
              </div>

              <el-button text type="primary" class="compare-card__edit" @click="openProfile">
                更新身体资料
              </el-button>
            </template>
          </section>
        </aside>
      </div>
    </div>

    <ProfileDialog v-model="profileVisible" @saved="handleProfileSaved" />
  </div>
</template>

<style scoped>
.day-page {
  display: flex;
  flex-direction: column;
}

/* ============================ 页面头部 ============================ */

.day-head {
  margin-bottom: 22px;
}

.day-head__intro {
  min-width: 0;
}

.day-head__tools {
  display: flex;
  align-items: flex-end;
  gap: 22px;
  flex-shrink: 0;
}

.day-date {
  display: flex;
  align-items: center;
  gap: 8px;
}

.day-date__picker {
  width: 158px;
}

.day-date__badge {
  flex-shrink: 0;
}

.day-total {
  padding-left: 20px;
  border-left: 1px solid var(--mw-line);
  text-align: right;
}

.day-total__label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--mw-faint);
}

.day-total__value {
  margin-top: 4px;
  display: flex;
  align-items: baseline;
  justify-content: flex-end;
  gap: 6px;
}

.day-total__value .mono-number {
  font-size: 30px;
  font-weight: 800;
  letter-spacing: -0.03em;
  line-height: 1;
  color: var(--mw-ink);
}

.day-total__unit {
  font-size: 12px;
  font-weight: 600;
  color: var(--mw-muted);
}

/* ============================ 布局 ============================ */

.day-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 342px;
  gap: 18px;
  align-items: start;
}

.day-body {
  min-height: 320px;
}

.day-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.day-side {
  position: sticky;
  top: 90px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.day-skeleton {
  padding: 22px;
}

.day-error {
  padding: 24px;
}

.day-empty-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  margin-bottom: 16px;
  border-radius: 14px;
  border: 1px dashed #c9e5d7;
  background: #f2fbf5;
  font-size: 13px;
  color: var(--mw-muted);
}

.day-empty-banner p {
  flex: 1;
}

.day-empty-banner__emoji {
  font-size: 18px;
  line-height: 1;
}

/* ============================ 餐次分区 ============================ */

.meal-section {
  padding: 20px 22px 18px;
}

.meal-section__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.meal-section__identity {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.meal-section__emoji {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 21px;
  background: var(--mw-primary-soft);
  flex-shrink: 0;
}

.meal-section__title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.meal-section__title-row h2 {
  font-size: 17px;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.meal-section__sub {
  margin-top: 3px;
  font-size: 11.5px;
  color: var(--mw-faint);
}

.meal-section__sum {
  text-align: right;
  flex-shrink: 0;
}

.meal-section__calories {
  display: flex;
  align-items: baseline;
  justify-content: flex-end;
  gap: 4px;
}

.meal-section__calories .mono-number {
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.meal-section__unit {
  font-size: 10.5px;
  font-weight: 600;
  color: var(--mw-faint);
}

.meal-section__macros {
  margin-top: 3px;
  font-size: 11px;
  color: var(--mw-faint);
}

.meal-section__empty {
  margin-top: 16px;
  padding: 20px 16px;
  border-radius: 14px;
  border: 1px dashed var(--mw-line);
  background: #fbfdfb;
  text-align: center;
}

.meal-section__empty p {
  margin-bottom: 4px;
  font-size: 13px;
  color: var(--mw-faint);
}

/* ============================ 单个餐次 ============================ */

.meal-blocks {
  margin-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.meal-block {
  padding: 12px 14px 4px;
  border: 1px solid var(--mw-line);
  border-radius: 14px;
  background: #fbfdfb;
}

.meal-block__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding-bottom: 8px;
}

.meal-block__time {
  font-size: 12px;
  font-weight: 700;
  color: var(--mw-muted);
}

.meal-block__subtotal {
  font-size: 11.5px;
  color: var(--mw-faint);
}

.meal-block__subtotal strong {
  font-size: 13.5px;
  color: var(--mw-primary-dark);
}

.meal-block__macros {
  margin-left: 8px;
}

.meal-table {
  width: 100%;
  background: transparent;
}

.meal-table :deep(.el-table__inner-wrapper) {
  background: transparent;
}

.meal-table :deep(th.el-table__cell) {
  background: #f4faf6;
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
  font-weight: 500;
  color: var(--mw-faint);
}

/* ============================ 摄入 vs 建议 ============================ */

.compare-card {
  padding: 22px 22px 18px;
}

.compare-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.compare-card__title {
  font-size: 18px;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.profile-guide {
  margin-top: 18px;
  padding: 22px 18px;
  border-radius: 16px;
  border: 1px dashed #c9e5d7;
  background: linear-gradient(165deg, #f2fbf5 0%, #ffffff 78%);
  text-align: center;
}

.profile-guide__icon {
  font-size: 28px;
  line-height: 1;
}

.profile-guide__title {
  margin-top: 10px;
  font-size: 14px;
  font-weight: 800;
  line-height: 1.55;
  color: var(--mw-ink);
}

.profile-guide__text {
  margin: 8px 0 14px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--mw-muted);
}

.compare-targets {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f6faf7;
  font-size: 11.5px;
  color: var(--mw-muted);
}

.compare-targets strong {
  font-size: 13px;
  color: var(--mw-primary-dark);
}

.metric-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 18px;
}

.metric__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.metric__label {
  font-size: 13px;
  font-weight: 700;
  color: var(--mw-ink);
}

.metric__value {
  margin: 6px 0 7px;
  display: flex;
  align-items: baseline;
  gap: 5px;
  font-size: 11.5px;
  color: var(--mw-faint);
}

.metric__value .mono-number {
  font-size: 18px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--mw-ink);
}

.metric__divider {
  color: #c4d2ca;
}

.metric__target {
  color: var(--mw-muted);
}

.metric__percent {
  margin-left: auto;
  font-weight: 700;
  color: var(--mw-faint);
  font-variant-numeric: tabular-nums;
}

.metric__bar {
  margin-bottom: 6px;
}

.metric__bar :deep(.el-progress-bar__outer) {
  background: #eef3ef;
}

.metric__diff {
  font-size: 11.5px;
  color: var(--mw-muted);
}

.metric.is-under .metric__diff {
  color: #b1782a;
}

.metric.is-on-track .metric__diff {
  color: var(--mw-primary-dark);
}

.metric.is-over .metric__diff {
  color: #c9504c;
}

.compare-formula {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px dashed var(--mw-line);
}

.compare-formula__title {
  font-size: 10.5px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--mw-faint);
}

.compare-formula__text {
  margin-top: 6px;
  font-size: 11.5px;
  line-height: 1.65;
  color: var(--mw-muted);
}

.compare-formula__list {
  margin: 6px 0 0;
  padding-left: 16px;
  display: flex;
  flex-direction: column;
  gap: 3px;
  font-size: 11px;
  line-height: 1.6;
  color: var(--mw-faint);
}

.compare-card__edit {
  margin-top: 10px;
  padding: 0;
  font-size: 12px;
}

/* ============================ 响应式 ============================ */

@media (max-width: 1080px) {
  .day-grid {
    grid-template-columns: minmax(0, 1fr) 310px;
  }
}

@media (max-width: 900px) {
  .day-head__tools {
    width: 100%;
    align-items: center;
    justify-content: space-between;
  }

  .day-grid {
    grid-template-columns: 1fr;
  }

  .day-side {
    position: static;
  }
}

@media (max-width: 620px) {
  .day-head__tools {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }

  .day-date {
    justify-content: space-between;
  }

  .day-total {
    padding-left: 0;
    border-left: none;
    text-align: left;
  }

  .day-total__value {
    justify-content: flex-start;
  }

  .meal-section {
    padding: 16px 16px 12px;
  }

  .meal-section__head {
    flex-direction: column;
    gap: 10px;
  }

  .meal-section__sum {
    text-align: left;
  }

  .meal-section__calories {
    justify-content: flex-start;
  }

  .meal-block__macros {
    display: block;
    margin-left: 0;
  }
}
</style>
