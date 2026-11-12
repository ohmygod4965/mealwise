/**
 * AI 辅助能力的前端类型（与后端 /api/ai/* 的 VO 一一对应）。
 */

/** 解析出的一样食物（后端已匹配食物库、已换算克数）。 */
export interface ParsedMealItem {
  foodId: number
  spokenName: string
  name: string
  category: string
  quantityText?: string | null
  grams: number
  calories: number
  protein: number
  fat: number
  carb: number
  confidence: number
}

/** 需要用户补充的信息。 */
export interface ClarifyQuestion {
  type: 'UNKNOWN_FOOD' | 'AMBIGUOUS_QUANTITY' | 'NOT_UNDERSTOOD' | 'EMPTY_INPUT' | string
  field: string
  message: string
  candidates: string[]
}

export type ParsedMealStatus = 'PARSED' | 'NEED_CLARIFICATION'

export interface ParsedMealResult {
  status: ParsedMealStatus
  source: 'llm' | 'rule' | string
  items: ParsedMealItem[]
  questions: ClarifyQuestion[]
  note?: string | null
}

export interface AgentChatResult {
  sessionId: string
  reply: string
  source: 'llm' | 'disabled' | string
}

export interface AskCitation {
  id: string
  title: string
  kind: 'food' | 'knowledge' | string
  score: number
  text: string
}

export interface AskResult {
  answer: string
  source: 'llm' | 'retrieval' | string
  citations: AskCitation[]
}

export interface ExplainSuggestion {
  foodId: number
  name: string
  deltaCalories: number
  ruleReason: string
  explanation: string
  fromLlm: boolean
}

export interface ExplainItem {
  itemId: number
  originalName: string
  quantityG: number
  suggestions: ExplainSuggestion[]
}

export interface ExplainResult {
  mealId: number
  strategy: string
  source: 'llm' | 'rule' | string
  items: ExplainItem[]
}
