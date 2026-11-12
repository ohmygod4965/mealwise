import { http } from '@/api/request'
import type { MealType } from '@/types/api'
import type {
  AgentChatResult,
  AskResult,
  ExplainResult,
  ParsedMealResult
} from '@/types/ai-assist'

/**
 * AI 辅助接口：自然语言录餐 / Agent 对话 / 营养问答 / 替换解释。
 *
 * 这四类都可能在服务端降级（source=rule/retrieval/disabled），
 * 所以前端一律当作正常响应处理，不按错误弹窗。
 */

/** 自然语言 → 结构化食物项；可能返回追问。 */
export function parseMealText(text: string): Promise<ParsedMealResult> {
  return http.post<ParsedMealResult>('/ai/parse-meal', { text }, { timeout: 30000 })
}

/** Agent 多轮对话：同一 sessionId 共享记忆。 */
export function chatWithAgent(sessionId: string | null, message: string): Promise<AgentChatResult> {
  return http.post<AgentChatResult>('/ai/agent', { sessionId, message }, { timeout: 120000 })
}

/** 清空某个会话的 Agent 记忆。 */
export function resetAgentSession(sessionId: string): Promise<null> {
  return http.delete<null>(`/ai/agent/${encodeURIComponent(sessionId)}`)
}

/** 营养知识问答（RAG）。 */
export function askNutrition(question: string): Promise<AskResult> {
  return http.post<AskResult>('/ai/ask', { question }, { timeout: 60000 })
}

/** 规则 Top3 + LLM 润色解释（模型失败时 explanation 等于规则原文）。 */
export function explainReplacements(
  mealId: number,
  strategy: 'higher' | 'lower'
): Promise<ExplainResult> {
  return http.get<ExplainResult>(`/meals/${mealId}/replacements/explain`, { strategy }, { timeout: 60000 })
}

/** 供录餐确认时调用：建餐 + 逐项加入。 */
export async function commitParsedMeal(
  mealType: MealType,
  items: Array<{ foodId: number; grams: number }>
): Promise<number> {
  const meal = await http.post<{ id: number }>('/meals', { mealType, eatenAt: null })
  for (const item of items) {
    await http.post(`/meals/${meal.id}/items`, { foodId: item.foodId, quantityG: item.grams })
  }
  return meal.id
}
