<script setup lang="ts">
import { computed } from 'vue'
import type { ReplacementStrategy, ReplacementSuggestion } from '@/types/api'
import { formatDelta, formatNumber } from '@/utils/format'

const props = defineProps<{
  suggestion: ReplacementSuggestion
  strategy: ReplacementStrategy
  /** 可选：LLM 润色后的解释；为空时展示规则原文 */
  explanation?: string
  explanationFromLlm?: boolean
}>()

/**
 * 替换建议卡片：
 * - 用「增加 / 减少」颜色区分热量方向；
 * - higher 策略下热量增加是预期结果，因此用暖色；
 * - lower 策略下热量减少是预期结果，用绿色。
 */
const deltaTone = computed(() => {
  if (props.suggestion.deltaCalories > 0) return 'up'
  if (props.suggestion.deltaCalories < 0) return 'down'
  return 'flat'
})

const deltaLabel = computed(() => `${formatDelta(props.suggestion.deltaCalories)} 千卡`)

const directionText = computed(() => {
  if (props.suggestion.deltaCalories > 0) return '热量增加'
  if (props.suggestion.deltaCalories < 0) return '热量减少'
  return '热量持平'
})
</script>

<template>
  <article class="replacement-card" :class="`replacement-card--${deltaTone}`">
    <div class="replacement-card__top">
      <div class="replacement-card__identity">
        <h4 class="replacement-card__name">{{ suggestion.name }}</h4>
        <p class="replacement-card__meta">
          {{ suggestion.category }} · {{ formatNumber(suggestion.caloriesPer100g) }} 千卡/100g
        </p>
      </div>
      <span class="delta-pill" :title="directionText">{{ deltaLabel }}</span>
    </div>

    <div class="replacement-card__numbers">
      <span class="replacement-card__numbers-label">本项替换后约</span>
      <span class="replacement-card__numbers-value mono-number">
        {{ formatNumber(suggestion.replacedCalories) }}
      </span>
      <span class="replacement-card__numbers-unit">千卡</span>
      <span v-if="strategy === 'higher'" class="replacement-card__strategy">更高热量</span>
      <span v-else class="replacement-card__strategy replacement-card__strategy--lower">更低热量</span>
    </div>

    <p v-if="props.explanation" class="replacement-card__reason">
      <span class="replacement-card__reason-label">{{ props.explanationFromLlm ? 'AI 解释' : '推荐理由' }}</span>
      {{ props.explanation }}
    </p>
    <p v-else-if="suggestion.reason" class="replacement-card__reason">
      <span class="replacement-card__reason-label">推荐理由</span>
      {{ suggestion.reason }}
    </p>
  </article>
</template>

<style scoped>
.replacement-card {
  border: 1px solid var(--mw-line);
  border-left: 3px solid #d8e3dc;
  border-radius: 14px;
  padding: 15px 17px;
  background: #fbfdfb;
  transition: box-shadow 0.18s ease, transform 0.18s ease;
}

.replacement-card:hover {
  box-shadow: 0 14px 32px -26px rgba(27, 63, 46, 0.55);
  transform: translateY(-1px);
}

.replacement-card--up {
  border-left-color: #e9a23b;
}

.replacement-card--down {
  border-left-color: var(--mw-primary);
}

.replacement-card__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.replacement-card__name {
  font-size: 14.5px;
  font-weight: 700;
  color: var(--mw-ink);
}

.replacement-card__meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--mw-faint);
}

.delta-pill {
  flex-shrink: 0;
  padding: 4px 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  color: var(--mw-muted);
  background: #f0f4f1;
}

.replacement-card--up .delta-pill {
  color: #a8640d;
  background: var(--mw-accent-soft);
}

.replacement-card--down .delta-pill {
  color: var(--mw-primary-dark);
  background: var(--mw-primary-soft);
}

.replacement-card__numbers {
  margin-top: 12px;
  display: flex;
  align-items: baseline;
  gap: 5px;
  font-size: 12px;
  color: var(--mw-muted);
}

.replacement-card__numbers-value {
  font-size: 19px;
  font-weight: 800;
  color: var(--mw-ink);
  letter-spacing: -0.02em;
}

.replacement-card__numbers-unit {
  font-size: 11.5px;
  color: var(--mw-faint);
}

.replacement-card__strategy {
  margin-left: auto;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 10.5px;
  font-weight: 700;
  color: #a8640d;
  background: var(--mw-accent-soft);
}

.replacement-card__strategy--lower {
  color: var(--mw-primary-dark);
  background: var(--mw-primary-soft);
}

.replacement-card__reason {
  margin-top: 11px;
  padding-top: 10px;
  border-top: 1px dashed var(--mw-line);
  font-size: 12.5px;
  line-height: 1.65;
  color: var(--mw-muted);
}

.replacement-card__reason-label {
  display: inline-block;
  margin-right: 6px;
  font-size: 10.5px;
  font-weight: 800;
  letter-spacing: 0.08em;
  color: var(--mw-faint);
  text-transform: uppercase;
}
</style>
