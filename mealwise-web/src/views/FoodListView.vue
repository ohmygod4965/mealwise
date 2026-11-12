<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { createFood, deleteFood, getFoods, updateFood } from '@/api/food'
import EmptyState from '@/components/EmptyState.vue'
import { ALLERGEN_OPTIONS, FOOD_CATEGORIES, allergenLabel } from '@/constants/meal'
import { useAuthStore } from '@/stores/auth'
import type { FoodPayload, FoodVO } from '@/types/api'
import { formatNumber } from '@/utils/format'

interface FoodFormState {
  name: string
  category: string
  caloriesPer100g: number | undefined
  proteinPer100g: number | undefined
  fatPer100g: number | undefined
  carbPer100g: number | undefined
  unitName: string
  unitWeightG: number | undefined
  allergens: string[]
  enabled: boolean
}

const authStore = useAuthStore()

/* ------------------------------ 列表查询 ------------------------------ */

const foods = ref<FoodVO[]>([])
const loading = ref(false)
const loadError = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(10)

const keywordInput = ref('')
const categoryInput = ref('')
const applied = reactive({ keyword: '', category: '' })

const hasFilters = computed(() => Boolean(applied.keyword || applied.category))
const myAllergens = computed(() => new Set(authStore.allergens))

/** 类别选项：内置类别 + 当前数据里出现过的自定义类别 */
const categoryOptions = computed(() => {
  const fromData = foods.value.map((food) => food.category).filter(Boolean)
  return Array.from(new Set([...FOOD_CATEGORIES, ...fromData]))
})

async function loadFoods(): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    const result = await getFoods({
      keyword: applied.keyword || undefined,
      category: applied.category || undefined,
      page: page.value,
      size: size.value
    })
    foods.value = result.records ?? []
    total.value = result.total ?? 0
    // 以后端返回的分页信息为准，避免本地页码与数据不一致
    if (result.page) page.value = result.page
    if (result.size) size.value = result.size
  } catch {
    // 错误提示由 axios 拦截器统一展示，这里只维护页面兜底状态
    loadError.value = true
    foods.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  applied.keyword = keywordInput.value.trim()
  applied.category = categoryInput.value
  page.value = 1
  void loadFoods()
}

function handleReset(): void {
  keywordInput.value = ''
  categoryInput.value = ''
  applied.keyword = ''
  applied.category = ''
  page.value = 1
  void loadFoods()
}

function handlePageChange(value: number): void {
  page.value = value
  void loadFoods()
}

function handleSizeChange(value: number): void {
  size.value = value
  page.value = 1
  void loadFoods()
}

/** 过敏原匹配：用于在食物列表中提醒当前用户 */
function isMyAllergen(token: string): boolean {
  return myAllergens.value.has(token)
}

function categoryTone(category: string): 'success' | 'warning' | 'danger' | 'info' {
  const tones: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
    主食: 'warning',
    肉蛋: 'danger',
    蔬菜: 'success',
    水果: 'success',
    奶制品: 'info',
    坚果: 'warning',
    豆制品: 'success',
    饮料: 'info'
  }
  return tones[category] ?? 'info'
}

/* ------------------------------ 新增 / 编辑 ------------------------------ */

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()

function createEmptyForm(): FoodFormState {
  return {
    name: '',
    category: '',
    caloriesPer100g: undefined,
    proteinPer100g: undefined,
    fatPer100g: undefined,
    carbPer100g: undefined,
    unitName: '',
    unitWeightG: undefined,
    allergens: [],
    enabled: true
  }
}

const form = reactive<FoodFormState>(createEmptyForm())

function validateUnitWeight(
  _rule: unknown,
  value: number | undefined,
  callback: (error?: Error) => void
): void {
  if (value === undefined || value === null) {
    callback()
    return
  }
  if (Number(value) < 0.1) {
    callback(new Error('单位重量需大于 0'))
  } else {
    callback()
  }
}

const rules: FormRules<FoodFormState> = {
  name: [
    { required: true, message: '请输入食物名称', trigger: 'blur' },
    { max: 64, message: '食物名称最多 64 字', trigger: 'blur' }
  ],
  category: [
    { required: true, message: '请选择或输入类别', trigger: 'change' },
    { max: 32, message: '类别最多 32 字', trigger: 'blur' }
  ],
  caloriesPer100g: [
    { required: true, message: '请输入每 100g 热量', trigger: 'blur' },
    { type: 'number', min: 0, max: 999999, message: '热量需在 0-999999 之间', trigger: 'blur' }
  ],
  proteinPer100g: [
    { required: true, message: '请输入蛋白质', trigger: 'blur' },
    { type: 'number', min: 0, max: 99999, message: '蛋白质需在 0-99999 之间', trigger: 'blur' }
  ],
  fatPer100g: [
    { required: true, message: '请输入脂肪', trigger: 'blur' },
    { type: 'number', min: 0, max: 99999, message: '脂肪需在 0-99999 之间', trigger: 'blur' }
  ],
  carbPer100g: [
    { required: true, message: '请输入碳水', trigger: 'blur' },
    { type: 'number', min: 0, max: 99999, message: '碳水需在 0-99999 之间', trigger: 'blur' }
  ],
  unitName: [{ max: 16, message: '单位名最多 16 字', trigger: 'blur' }],
  unitWeightG: [{ validator: validateUnitWeight, trigger: 'blur' }]
}

const dialogTitle = computed(() => (editingId.value ? '编辑食物' : '新增食物'))

function resetForm(): void {
  Object.assign(form, createEmptyForm())
}

function openCreate(): void {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

function openEdit(row: FoodVO): void {
  editingId.value = row.id
  Object.assign(form, {
    name: row.name,
    category: row.category,
    caloriesPer100g: row.caloriesPer100g,
    proteinPer100g: row.proteinPer100g,
    fatPer100g: row.fatPer100g,
    carbPer100g: row.carbPer100g,
    unitName: row.unitName ?? '',
    unitWeightG: row.unitWeightG ?? undefined,
    allergens: [...(row.allergens ?? [])],
    enabled: row.enabled
  })
  dialogVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

function handleDialogClosed(): void {
  editingId.value = null
  resetForm()
}

function buildPayload(): FoodPayload {
  return {
    name: form.name.trim(),
    category: form.category.trim(),
    caloriesPer100g: Number(form.caloriesPer100g ?? 0),
    proteinPer100g: Number(form.proteinPer100g ?? 0),
    fatPer100g: Number(form.fatPer100g ?? 0),
    carbPer100g: Number(form.carbPer100g ?? 0),
    unitName: form.unitName.trim() || null,
    unitWeightG: form.unitWeightG ?? null,
    allergens: form.allergens,
    enabled: form.enabled
  }
}

async function handleSave(): Promise<void> {
  if (!formRef.value || saving.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const payload = buildPayload()
    if (editingId.value !== null) {
      await updateFood(editingId.value, payload)
      ElMessage.success('食物信息已更新')
    } else {
      await createFood(payload)
      ElMessage.success('已添加到食物库')
      page.value = 1
    }
    dialogVisible.value = false
    await loadFoods()
  } catch {
    // 参数校验 / 冲突等错误由拦截器提示
  } finally {
    saving.value = false
  }
}

/* ------------------------------ 删除（软删除） ------------------------------ */

const deletingId = ref<number | null>(null)

async function handleDelete(row: FoodVO): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `删除后「${row.name}」会从食物库中下架，已记录的餐次不受影响。`,
      '删除食物',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch {
    // 用户取消
    return
  }

  deletingId.value = row.id
  try {
    await deleteFood(row.id)
    ElMessage.success('食物已删除')
    // 删除当前页最后一条时回退一页，避免停留在空页
    if (foods.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    await loadFoods()
  } catch {
    // 错误提示由拦截器处理
  } finally {
    deletingId.value = null
  }
}

onMounted(() => {
  void loadFoods()
})
</script>

<template>
  <div class="food-page">
    <header class="page-head">
      <div>
        <p class="eyebrow">FOOD LIBRARY</p>
        <h1 class="page-title">食物库</h1>
        <p class="page-subtitle">
          每 100g 的热量与三大营养素基准。搜索、维护食物，再用它们拼出每一餐。
        </p>
      </div>
      <div class="page-actions">
        <el-button type="primary" :icon="Plus" size="large" @click="openCreate">新增食物</el-button>
      </div>
    </header>

    <!-- 筛选条件 -->
    <section class="filter-card surface-card">
      <div class="filter-card__field filter-card__field--grow">
        <el-input
          v-model="keywordInput"
          :prefix-icon="Search"
          placeholder="搜索食物名称，如：米饭"
          clearable
          size="large"
          @keyup.enter="handleSearch"
        />
      </div>
      <div class="filter-card__field">
        <el-select
          v-model="categoryInput"
          placeholder="全部类别"
          clearable
          size="large"
          class="category-select"
        >
          <el-option v-for="item in categoryOptions" :key="item" :label="item" :value="item" />
        </el-select>
      </div>
      <div class="filter-card__actions">
        <el-button type="primary" :icon="Search" size="large" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" size="large" @click="handleReset">重置</el-button>
      </div>
    </section>

    <!-- 食物表格 -->
    <section class="table-card surface-card">
      <div class="section-heading table-card__heading">
        <div class="section-heading__title">
          食物清单
          <el-tag v-if="total > 0" size="small" effect="plain" round>{{ total }} 种</el-tag>
        </div>
        <span class="section-heading__hint">营养数据均为每 100g 含量</span>
      </div>

      <div v-loading="loading" class="table-body">
        <EmptyState
          v-if="loadError && !loading"
          description="食物库加载失败，请检查网络后重试"
          :image-size="88"
        >
          <el-button type="primary" plain :icon="Refresh" @click="loadFoods">重新加载</el-button>
        </EmptyState>

        <EmptyState
          v-else-if="!loading && foods.length === 0"
          :description="hasFilters ? '没有找到符合条件的食物，换个关键词试试' : '食物库还是空的，先添加第一种食物吧'"
          :image-size="88"
        >
          <el-button v-if="hasFilters" plain :icon="Refresh" @click="handleReset">清空筛选</el-button>
          <el-button v-else type="primary" :icon="Plus" @click="openCreate">添加食物</el-button>
        </EmptyState>

        <template v-else>
          <el-table :data="foods" row-key="id" class="food-table">
            <el-table-column label="食物" min-width="190">
              <template #default="{ row }">
                <div class="food-name">
                  <span class="food-name__text">{{ row.name }}</span>
                  <span v-if="row.unitName" class="food-name__unit">
                    1 {{ row.unitName }} ≈ {{ formatNumber(row.unitWeightG) }} g
                  </span>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="类别" width="110">
              <template #default="{ row }">
                <el-tag :type="categoryTone(row.category)" effect="light" round size="small">
                  {{ row.category }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column label="热量" width="105" align="right">
              <template #default="{ row }">
                <span class="nutrient nutrient--calories mono-number">
                  {{ formatNumber(row.caloriesPer100g) }}
                </span>
                <span class="nutrient__unit">kcal</span>
              </template>
            </el-table-column>

            <el-table-column label="蛋白质" width="95" align="right">
              <template #default="{ row }">
                <span class="mono-number">{{ formatNumber(row.proteinPer100g) }}</span>
                <span class="nutrient__unit">g</span>
              </template>
            </el-table-column>

            <el-table-column label="脂肪" width="90" align="right">
              <template #default="{ row }">
                <span class="mono-number">{{ formatNumber(row.fatPer100g) }}</span>
                <span class="nutrient__unit">g</span>
              </template>
            </el-table-column>

            <el-table-column label="碳水" width="90" align="right">
              <template #default="{ row }">
                <span class="mono-number">{{ formatNumber(row.carbPer100g) }}</span>
                <span class="nutrient__unit">g</span>
              </template>
            </el-table-column>

            <el-table-column label="过敏原" min-width="150">
              <template #default="{ row }">
                <div class="allergen-list">
                  <template v-if="(row.allergens ?? []).length">
                    <el-tag
                      v-for="token in row.allergens"
                      :key="token"
                      size="small"
                      effect="plain"
                      :type="isMyAllergen(token) ? 'danger' : 'info'"
                      :title="isMyAllergen(token) ? '与你设置的过敏原匹配' : undefined"
                    >
                      {{ allergenLabel(token) }}
                    </el-tag>
                  </template>
                  <span v-else class="text-faint">—</span>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small" effect="light">
                  {{ row.enabled ? '已上架' : '已下架' }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column label="操作" width="136" align="right" fixed="right">
              <template #default="{ row }">
                <div class="row-actions">
                  <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
                  <el-button
                    link
                    type="danger"
                    :icon="Delete"
                    :loading="deletingId === row.id"
                    @click="handleDelete(row)"
                  >
                    删除
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-row">
            <el-pagination
              v-model:current-page="page"
              v-model:page-size="size"
              :total="total"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @current-change="handlePageChange"
              @size-change="handleSizeChange"
            />
          </div>
        </template>
      </div>
    </section>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="660px"
      :close-on-click-modal="false"
      :close-on-press-escape="!saving"
      @closed="handleDialogClosed"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="handleSave"
      >
        <p class="form-section-title">基本信息</p>
        <div class="form-grid">
          <el-form-item label="食物名称" prop="name" class="form-grid__wide">
            <el-input v-model="form.name" placeholder="如：鸡胸肉" maxlength="64" show-word-limit />
          </el-form-item>

          <el-form-item label="类别" prop="category">
            <el-select
              v-model="form.category"
              placeholder="选择或输入类别"
              filterable
              allow-create
              default-first-option
            >
              <el-option v-for="item in categoryOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>

          <el-form-item label="计量单位（可选）" prop="unitName">
            <el-input v-model="form.unitName" placeholder="如：碗 / 个 / 片" maxlength="16" />
          </el-form-item>

          <el-form-item label="1 单位约多少克（可选）" prop="unitWeightG">
            <el-input-number
              v-model="form.unitWeightG"
              :min="0.1"
              :max="999999"
              :precision="1"
              :step="10"
              controls-position="right"
              placeholder="如：200"
            />
          </el-form-item>
        </div>

        <p class="form-section-title">每 100g 营养</p>
        <div class="form-grid form-grid--nutrition">
          <el-form-item label="热量（kcal）" prop="caloriesPer100g">
            <el-input-number
              v-model="form.caloriesPer100g"
              :min="0"
              :max="999999"
              :precision="1"
              :step="10"
              controls-position="right"
            />
          </el-form-item>

          <el-form-item label="蛋白质（g）" prop="proteinPer100g">
            <el-input-number
              v-model="form.proteinPer100g"
              :min="0"
              :max="99999"
              :precision="1"
              :step="1"
              controls-position="right"
            />
          </el-form-item>

          <el-form-item label="脂肪（g）" prop="fatPer100g">
            <el-input-number
              v-model="form.fatPer100g"
              :min="0"
              :max="99999"
              :precision="1"
              :step="1"
              controls-position="right"
            />
          </el-form-item>

          <el-form-item label="碳水（g）" prop="carbPer100g">
            <el-input-number
              v-model="form.carbPer100g"
              :min="0"
              :max="99999"
              :precision="1"
              :step="1"
              controls-position="right"
            />
          </el-form-item>
        </div>

        <p class="form-section-title">更多信息</p>
        <div class="form-grid">
          <el-form-item label="过敏原" prop="allergens" class="form-grid__wide">
            <el-select
              v-model="form.allergens"
              multiple
              filterable
              allow-create
              default-first-option
              :reserve-keyword="false"
              placeholder="选择或输入过敏原"
            >
              <el-option
                v-for="item in ALLERGEN_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="上架状态" prop="enabled">
            <el-switch
              v-model="form.enabled"
              active-text="上架"
              inactive-text="下架"
              inline-prompt
            />
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          {{ saving ? '保存中…' : editingId ? '保存修改' : '添加食物' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.food-page {
  display: flex;
  flex-direction: column;
}

/* ---------------------------- 筛选 ---------------------------- */

.filter-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 18px;
  margin-bottom: 18px;
}

.filter-card__field {
  flex-shrink: 0;
}

.filter-card__field--grow {
  flex: 1;
  min-width: 180px;
}

.category-select {
  width: 170px;
}

.filter-card__actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

/* ---------------------------- 表格 ---------------------------- */

.table-card {
  padding: 22px 22px 14px;
}

.table-card__heading {
  padding: 0 2px;
}

.table-body {
  min-height: 320px;
}

.food-table {
  width: 100%;
}

.food-name {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.food-name__text {
  font-weight: 700;
  color: var(--mw-ink);
}

.food-name__unit {
  font-size: 11.5px;
  color: var(--mw-faint);
}

.nutrient {
  font-weight: 700;
}

.nutrient--calories {
  color: var(--mw-ink);
}

.nutrient__unit {
  margin-left: 2px;
  font-size: 10.5px;
  color: var(--mw-faint);
  font-weight: 500;
}

.allergen-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.row-actions {
  display: flex;
  justify-content: flex-end;
}

.row-actions :deep(.el-button) {
  margin-left: 0;
  padding: 2px 6px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  padding: 18px 2px 4px;
}

/* ---------------------------- 弹窗表单 ---------------------------- */

.form-section-title {
  margin: 2px 0 12px;
  font-size: 11.5px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--mw-primary-dark);
}

.form-section-title:not(:first-child) {
  margin-top: 12px;
  padding-top: 18px;
  border-top: 1px dashed var(--mw-line);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.form-grid--nutrition {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.form-grid__wide {
  grid-column: 1 / -1;
}

.form-grid :deep(.el-input-number) {
  width: 100%;
}

.form-grid :deep(.el-select) {
  width: 100%;
}

/* ---------------------------- 响应式 ---------------------------- */

@media (max-width: 900px) {
  .filter-card {
    flex-wrap: wrap;
  }

  .filter-card__field--grow {
    flex-basis: 100%;
  }

  .filter-card__field {
    flex: 1;
  }

  .category-select {
    width: 100%;
  }

  .filter-card__actions {
    width: 100%;
  }

  .filter-card__actions .el-button {
    flex: 1;
  }

  .form-grid--nutrition {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .table-card {
    padding: 18px 14px 10px;
  }

  .pagination-row {
    justify-content: center;
    overflow-x: auto;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .form-grid--nutrition {
    grid-template-columns: 1fr;
  }
}
</style>
