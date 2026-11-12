<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotRound, MagicStick, QuestionFilled } from '@element-plus/icons-vue'
import {
  askNutrition,
  chatWithAgent,
  commitParsedMeal,
  parseMealText,
  resetAgentSession
} from '@/api/ai-assist'
import type { AgentChatResult, AskResult, ParsedMealResult } from '@/types/ai-assist'
import { deleteMeal } from '@/api/meal'
import { MEAL_TYPE_OPTIONS, guessMealType } from '@/constants/meal'
import type { MealType } from '@/types/api'

/**
 * AI 助手弹窗：自然语言录餐 / 多轮对话（LangChain4j Agent）/ 营养问答（RAG）。
 *
 * 三段能力都可能被后端降级（source=rule / retrieval / disabled），
 * 这里只做「把 source 标出来」，不把降级当错误。
 */
const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; saved: [number] }>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const activeTab = ref<'parse' | 'chat' | 'ask'>('parse')

/* ------------------------------ 录餐 ------------------------------ */
const text = ref('')
const parsing = ref(false)
const committing = ref(false)
const parsed = ref<ParsedMealResult | null>(null)
const grams = ref<number[]>([])
const mealType = ref<MealType>(guessMealType())

/** 刚入账的餐次：留一个「撤销」入口，避免 AI 记错后只能手动删 */
const lastCommit = ref<{ mealId: number; label: string } | null>(null)
const undoing = ref(false)

async function handleUndo(): Promise<void> {
  if (!lastCommit.value) return
  undoing.value = true
  try {
    await deleteMeal(lastCommit.value.mealId)
    ElMessage.success(`已撤销餐次 #${lastCommit.value.mealId}`)
    lastCommit.value = null
  } finally {
    undoing.value = false
  }
}

async function handleParse(): Promise<void> {
  if (!text.value.trim()) {
    ElMessage.warning('先说一句吃了什么，例如「中午吃了两碗米饭和一份鸡胸肉」')
    return
  }
  parsing.value = true
  try {
    const result = await parseMealText(text.value.trim())
    parsed.value = result
    grams.value = result.items.map((item) => Number(item.grams))
    if (result.status === 'NEED_CLARIFICATION') {
      ElMessage.warning('还缺一些信息，按下面的提示补充后再试')
    }
  } finally {
    parsing.value = false
  }
}

async function handleCommit(): Promise<void> {
  if (!parsed.value || !parsed.value.items.length) return
  const invalid = grams.value.some((value) => !value || value <= 0)
  if (invalid) {
    ElMessage.warning('每样食物的克数都要大于 0')
    return
  }
  committing.value = true
  try {
    const label = MEAL_TYPE_OPTIONS.find((o) => o.value === mealType.value)?.label ?? '餐次'
    const mealId = await commitParsedMeal(
      mealType.value,
      parsed.value.items.map((item, index) => ({ foodId: item.foodId, grams: grams.value[index] }))
    )
    ElMessage.success(`已记录到${label} #${mealId}`)
    emit('saved', mealId)
    lastCommit.value = { mealId, label }
    parsed.value = null
    text.value = ''
  } finally {
    committing.value = false
  }
}

/* ------------------------------ 对话 ------------------------------ */
interface ChatMessage {
  role: 'user' | 'assistant'
  text: string
  source?: string
}

const agentSession = ref<string | null>(null)
const agentInput = ref('')
const sending = ref(false)
const messages = ref<ChatMessage[]>([
  { role: 'assistant', text: '你好，我是饮食助手。可以直接说「午饭吃了米饭 200 克、鸡胸肉 150 克」，我会调用工具帮你记账、算热量、找替换。' }
])

async function handleSend(): Promise<void> {
  const content = agentInput.value.trim()
  if (!content || sending.value) return
  messages.value.push({ role: 'user', text: content })
  agentInput.value = ''
  sending.value = true
  try {
    const result: AgentChatResult = await chatWithAgent(agentSession.value, content)
    agentSession.value = result.sessionId
    messages.value.push({ role: 'assistant', text: result.reply, source: result.source })
  } catch {
    messages.value.push({ role: 'assistant', text: '这次没连上模型，稍后再试一次。', source: 'error' })
  } finally {
    sending.value = false
  }
}

async function handleClearSession(): Promise<void> {
  if (agentSession.value) {
    try {
      await resetAgentSession(agentSession.value)
    } catch {
      // 清空失败不影响本地重置
    }
  }
  agentSession.value = null
  messages.value = [
    { role: 'assistant', text: '新对话开始了。之前聊过的偏好已经清空，重新告诉我即可。' }
  ]
}

/* ------------------------------ 问答 ------------------------------ */
const question = ref('')
const asking = ref(false)
const answer = ref<AskResult | null>(null)

async function handleAsk(): Promise<void> {
  const content = question.value.trim()
  if (!content) {
    ElMessage.warning('想问点什么？例如「减脂期晚餐怎么吃」')
    return
  }
  asking.value = true
  try {
    answer.value = await askNutrition(content)
  } finally {
    asking.value = false
  }
}

function sourceLabel(source?: string): string {
  switch (source) {
    case 'llm':
      return '模型生成'
    case 'rule':
      return '本地规则'
    case 'retrieval':
      return '检索结果'
    case 'disabled':
      return '未配置模型'
    default:
      return ''
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    width="min(780px, 96vw)"
    top="6vh"
    class="ai-dialog"
    :close-on-click-modal="false"
  >
    <template #header>
      <div class="ai-dialog__header">
        <el-icon><MagicStick /></el-icon>
        <span>AI 饮食助手</span>
        <span class="ai-dialog__badge">LangChain4j Agent · 工具编排</span>
      </div>
    </template>

    <el-tabs v-model="activeTab" class="ai-dialog__tabs">
      <!-- 录餐 -->
      <el-tab-pane name="parse">
        <template #label>
          <span class="ai-dialog__tab"><el-icon><MagicStick /></el-icon>说一句录餐</span>
        </template>
        <div class="ai-block">
          <el-input
            v-model="text"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="例如：中午吃了两碗米饭和一份鸡胸肉"
          />
          <div class="ai-block__actions">
            <el-button type="primary" :loading="parsing" @click="handleParse">解析</el-button>
            <span class="ai-block__hint">匹配不到或没说克数时，我会追问。</span>
          </div>
        </div>

        <el-alert
          v-if="lastCommit"
          type="success"
          :closable="false"
          show-icon
          class="ai-alert"
        >
          <template #title>
            已记录到{{ lastCommit.label }} #{{ lastCommit.mealId }}
            <el-button
              size="small"
              text
              type="primary"
              :loading="undoing"
              @click="handleUndo"
            >
              撤销这次入账
            </el-button>
          </template>
        </el-alert>

        <template v-if="parsed">
          <el-alert
            v-if="parsed.note"
            :title="parsed.note"
            type="info"
            :closable="false"
            show-icon
            class="ai-alert"
          />
          <div v-if="parsed.questions.length" class="ai-questions">
            <p v-for="q in parsed.questions" :key="q.type + q.field" class="ai-questions__item">
              <el-icon><QuestionFilled /></el-icon>
              {{ q.message }}
              <em v-if="q.candidates.length">（候选：{{ q.candidates.join(' / ') }}）</em>
            </p>
          </div>

          <el-table v-if="parsed.items.length" :data="parsed.items" size="small" class="ai-table">
            <el-table-column prop="name" label="食物" min-width="110" />
            <el-table-column label="克数" width="150">
              <template #default="{ $index }">
                <el-input-number v-model="grams[$index]" :min="1" :max="2000" :step="10" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="热量" width="100">
              <template #default="{ row }">{{ row.calories }} kcal</template>
            </el-table-column>
            <el-table-column label="匹配" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.confidence >= 0.9 ? 'success' : 'warning'">
                  {{ Math.round(row.confidence * 100) }}%
                </el-tag>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="parsed.items.length" class="ai-block__actions">
            <el-select v-model="mealType" size="small" style="width: 120px">
              <el-option
                v-for="option in MEAL_TYPE_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <el-button type="primary" :loading="committing" @click="handleCommit">确认入账</el-button>
            <span class="ai-block__hint">来源：{{ sourceLabel(parsed.source) }}</span>
          </div>
        </template>
      </el-tab-pane>

      <!-- 对话 -->
      <el-tab-pane name="chat">
        <template #label>
          <span class="ai-dialog__tab"><el-icon><ChatDotRound /></el-icon>对话助手</span>
        </template>
        <div class="ai-chat">
          <div class="ai-chat__messages">
            <div
              v-for="(message, index) in messages"
              :key="index"
              class="ai-chat__row"
              :class="`ai-chat__row--${message.role}`"
            >
              <div class="ai-chat__bubble">
                <p>{{ message.text }}</p>
                <span v-if="message.source" class="ai-chat__source">{{ sourceLabel(message.source) }}</span>
              </div>
            </div>
            <div v-if="sending" class="ai-chat__row ai-chat__row--assistant">
              <div class="ai-chat__bubble ai-chat__bubble--typing">正在调用工具…</div>
            </div>
          </div>
          <div class="ai-chat__input">
            <el-input
              v-model="agentInput"
              placeholder="例如：午饭吃了米饭 400 克、鸡胸肉 150 克，帮我记一下"
              maxlength="300"
              @keyup.enter="handleSend"
            />
            <el-button type="primary" :loading="sending" @click="handleSend">发送</el-button>
            <el-button text @click="handleClearSession">新对话</el-button>
          </div>
        </div>
      </el-tab-pane>

      <!-- 问答 -->
      <el-tab-pane name="ask">
        <template #label>
          <span class="ai-dialog__tab"><el-icon><QuestionFilled /></el-icon>营养问答</span>
        </template>
        <div class="ai-block">
          <el-input
            v-model="question"
            placeholder="例如：减脂期晚餐怎么吃？"
            maxlength="120"
            @keyup.enter="handleAsk"
          />
          <div class="ai-block__actions">
            <el-button type="primary" :loading="asking" @click="handleAsk">提问</el-button>
            <span class="ai-block__hint">答案只依据食物库与营养常识检索结果生成。</span>
          </div>
        </div>

        <template v-if="answer">
          <div class="ai-answer">
            <p class="ai-answer__text">{{ answer.answer }}</p>
            <span class="ai-answer__source">来源：{{ sourceLabel(answer.source) }}</span>
          </div>
          <div v-if="answer.citations.length" class="ai-citations">
            <p class="ai-citations__title">引用资料</p>
            <ul>
              <li v-for="citation in answer.citations" :key="citation.id">
                <strong>{{ citation.title }}</strong>
                <span v-if="citation.kind === 'food'" class="ai-citations__tag">食物库</span>
                <span v-else class="ai-citations__tag">常识</span>
                <p>{{ citation.text }}</p>
              </li>
            </ul>
          </div>
        </template>
      </el-tab-pane>
    </el-tabs>

    <p class="ai-disclaimer">
      AI 生成内容仅供参考，不构成医疗或营养处方；调用时问题文本会发送给第三方模型服务。
    </p>
  </el-dialog>
</template>

<style scoped>
.ai-dialog__header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 700;
  color: var(--mw-ink, #1b2a22);
}

.ai-dialog__badge {
  margin-left: auto;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  color: var(--mw-muted, #5c6b62);
  background: #f0f4f1;
}

.ai-dialog__tab {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.ai-block {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ai-block__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.ai-block__hint {
  font-size: 12px;
  color: var(--mw-faint, #8a978f);
}

.ai-alert {
  margin-top: 12px;
}

.ai-questions {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #fff8e8;
  border: 1px solid #f0dcb4;
}

.ai-questions__item {
  display: flex;
  gap: 6px;
  font-size: 13px;
  color: #8a6416;
  line-height: 1.6;
}

.ai-questions__item + .ai-questions__item {
  margin-top: 6px;
}

.ai-table {
  margin-top: 12px;
}

.ai-chat {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ai-chat__messages {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 46vh;
  overflow-y: auto;
  padding-right: 4px;
}

.ai-chat__row {
  display: flex;
}

.ai-chat__row--user {
  justify-content: flex-end;
}

.ai-chat__bubble {
  max-width: 82%;
  padding: 10px 13px;
  border-radius: 14px;
  font-size: 13.5px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
  background: #f4f7f5;
  color: var(--mw-ink, #1b2a22);
}

.ai-chat__row--user .ai-chat__bubble {
  background: var(--mw-primary, #2f9e6a);
  color: #fff;
}

.ai-chat__bubble--typing {
  color: var(--mw-muted, #5c6b62);
  font-style: italic;
}

.ai-chat__source {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  opacity: 0.65;
}

.ai-chat__input {
  display: flex;
  gap: 8px;
  align-items: center;
}

.ai-answer {
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 12px;
  background: #f6faf7;
  border: 1px solid #dfece4;
}

.ai-answer__text {
  font-size: 14px;
  line-height: 1.75;
  white-space: pre-wrap;
}

.ai-answer__source {
  display: inline-block;
  margin-top: 8px;
  font-size: 11.5px;
  color: var(--mw-faint, #8a978f);
}

.ai-citations {
  margin-top: 14px;
}

.ai-citations__title {
  font-size: 12px;
  font-weight: 700;
  color: var(--mw-muted, #5c6b62);
}

.ai-citations ul {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ai-citations li {
  padding: 10px 12px;
  border-radius: 10px;
  background: #fbfcfb;
  border: 1px solid var(--mw-line, #e3ebe6);
  font-size: 12.5px;
  line-height: 1.6;
}

.ai-citations__tag {
  margin-left: 6px;
  padding: 1px 6px;
  border-radius: 999px;
  font-size: 10.5px;
  color: var(--mw-muted, #5c6b62);
  background: #eef3ef;
}

.ai-citations li p {
  margin-top: 4px;
  color: var(--mw-muted, #5c6b62);
}

.ai-disclaimer {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed var(--mw-line, #e3ebe6);
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--mw-faint, #8a978f);
}
</style>
