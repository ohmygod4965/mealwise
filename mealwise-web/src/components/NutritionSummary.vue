<script setup lang="ts">
import { computed } from 'vue'
import type { NutritionTotals } from '@/types/api'
import { formatNumber } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    total: NutritionTotals
    itemCount?: number
    totalGrams?: number
    loading?: boolean
  }>(),
  {
    itemCount: 0,
    totalGrams: 0,
    loading: false
  }
)

/** 三大营养素供能（蛋白质 4 kcal/g、脂肪 9 kcal/g、碳水 4 kcal/g） */
const macroCalories = computed(() => ({
  protein: props.total.protein * 4,
  fat: props.total.fat * 9,
  carb: props.total.carb * 4
}))

const macroTotal = computed(() => {
  const sum = macroCalories.value.protein + macroCalories.value.fat + macroCalories.value.carb
  return sum > 0 ? sum : 0
})

/** 供能占比（四舍五入到整数百分比） */
function shareOf(value: number): number {
  if (macroTotal.value <= 0) return 0
  return Math.round((value / macroTotal.value) * 100)
}

const macros = computed(() => [
  {
    key: 'protein',
    label: '蛋白质',
    value: props.total.protein,
    calories: macroCalories.value.protein,
    share: shareOf(macroCalories.value.protein)
  },
  {
    key: 'fat',
    label: '脂肪',
    value: props.total.fat,
    calories: macroCalories.value.fat,
    share: shareOf(macroCalories.value.fat)
  },
  {
    key: 'carb',
    label: '碳水',
    value: props.total.carb,
    calories: macroCalories.value.carb,
    share: shareOf(macroCalories.value.carb)
  }
])
</script>

<template>
  <section v-loading="loading" class="nutrition-summary surface-card">
    <div class="nutrition-summary__head">
      <div>
        <p class="nutrition-summary__label">本餐合计</p>
        <p class="nutrition-summary__calories">
          <span class="mono-number">{{ formatNumber(total.calories) }}</span>
          <span class="nutrition-summary__unit">千卡</span>
        </p>
        <p class="nutrition-summary__meta">
          {{ itemCount }} 样食物 · 共 {{ formatNumber(totalGrams) }} g
        </p>
      </div>
      <div class="nutrition-summary__badge" aria-hidden="true">
        <span>kcal</span>
      </div>
    </div>

    <div class="macro-list">
      <div v-for="macro in macros" :key="macro.key" class="macro" :class="`macro--${macro.key}`">
        <div class="macro__top">
          <span class="macro__dot"></span>
          <span class="macro__label">{{ macro.label }}</span>
          <span class="macro__share">{{ macro.share }}%</span>
        </div>
        <p class="macro__value">
          <span class="mono-number">{{ formatNumber(macro.value) }}</span>
          <span class="macro__unit">g</span>
        </p>
        <div class="macro__bar">
          <span :style="{ width: `${macro.share}%` }"></span>
        </div>
      </div>
    </div>

    <p class="nutrition-summary__note">
      供能比按蛋白质 4 kcal/g、脂肪 9 kcal/g、碳水 4 kcal/g 估算
    </p>
  </section>
</template>

<style scoped>
.nutrition-summary {
  padding: 24px;
}

.nutrition-summary__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 20px;
  border-bottom: 1px dashed var(--mw-line);
}

.nutrition-summary__label {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--mw-faint);
}

.nutrition-summary__calories {
  margin-top: 8px;
  display: flex;
  align-items: baseline;
  gap: 7px;
  color: var(--mw-ink);
}

.nutrition-summary__calories .mono-number {
  font-size: 42px;
  font-weight: 800;
  letter-spacing: -0.03em;
  line-height: 1;
}

.nutrition-summary__unit {
  font-size: 13px;
  font-weight: 600;
  color: var(--mw-muted);
}

.nutrition-summary__meta {
  margin-top: 9px;
  font-size: 12.5px;
  color: var(--mw-faint);
}

.nutrition-summary__badge {
  width: 52px;
  height: 52px;
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: var(--mw-primary-dark);
  background: var(--mw-primary-soft);
  border: 1px solid rgba(47, 157, 107, 0.16);
  flex-shrink: 0;
}

.macro-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 20px;
}

.macro__top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.macro__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.macro__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--mw-muted);
}

.macro__share {
  margin-left: auto;
  font-size: 11.5px;
  font-weight: 700;
  color: var(--mw-faint);
  font-variant-numeric: tabular-nums;
}

.macro__value {
  margin: 6px 0 8px;
  display: flex;
  align-items: baseline;
  gap: 4px;
  color: var(--mw-ink);
}

.macro__value .mono-number {
  font-size: 21px;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.macro__unit {
  font-size: 12px;
  color: var(--mw-faint);
  font-weight: 600;
}

.macro__bar {
  height: 6px;
  border-radius: 999px;
  background: #eef3ef;
  overflow: hidden;
}

.macro__bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
  transition: width 0.35s ease;
}

.macro--protein .macro__dot {
  background: #e2725b;
}

.macro--protein .macro__bar span {
  background: linear-gradient(90deg, #e89986, #e2725b);
}

.macro--fat .macro__dot {
  background: #e9a23b;
}

.macro--fat .macro__bar span {
  background: linear-gradient(90deg, #f3c580, #e9a23b);
}

.macro--carb .macro__dot {
  background: #4f8fd6;
}

.macro--carb .macro__bar span {
  background: linear-gradient(90deg, #8db8e8, #4f8fd6);
}

.nutrition-summary__note {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px dashed var(--mw-line);
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--mw-faint);
}
</style>
