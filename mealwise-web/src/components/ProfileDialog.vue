<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { getMyProfile, updateMyProfile } from '@/api/user'
import type { Gender, UpdateProfilePayload, UserProfileVO } from '@/types/api'

const props = defineProps<{
  /** 受控显隐，配合 v-model 使用 */
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  /** 保存成功，父页面可据此刷新每日记录 / 建议值 */
  saved: [profile: UserProfileVO]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const formRef = ref<FormInstance>()
const loading = ref(false)
const saving = ref(false)
const loadError = ref(false)
const serverError = ref('')

/** 表单状态：后端返回 null / 未填写时用 undefined 占位，避免输入框显示 0 */
const form = reactive<{
  gender: Gender | undefined
  heightCm: number | undefined
  weightKg: number | undefined
  age: number | undefined
}>({
  gender: undefined,
  heightCm: undefined,
  weightKg: undefined,
  age: 30
})

/** 前端校验规则与后端保持一致：身高 100-250、体重 30-300、年龄 10-100 */
const rules: FormRules = {
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  heightCm: [
    { required: true, message: '请输入身高', trigger: 'blur' },
    {
      type: 'number',
      min: 100,
      max: 250,
      message: '身高需在 100-250 cm 之间',
      trigger: ['blur', 'change']
    }
  ],
  weightKg: [
    { required: true, message: '请输入体重', trigger: 'blur' },
    {
      type: 'number',
      min: 30,
      max: 300,
      message: '体重需在 30-300 kg 之间',
      trigger: ['blur', 'change']
    }
  ],
  age: [
    {
      type: 'number',
      min: 10,
      max: 100,
      message: '年龄需在 10-100 岁之间',
      trigger: ['blur', 'change']
    }
  ]
}

/** 打开弹窗时拉取最新资料回填，保证与后端一致 */
async function loadProfile(): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    // silent：加载失败时用弹窗内提示代替全局 toast
    const profile = await getMyProfile({ silent: true })
    form.gender = profile.gender ?? undefined
    form.heightCm = profile.heightCm ?? undefined
    form.weightKg = profile.weightKg ?? undefined
    form.age = profile.age ?? 30
  } catch {
    // 拉取失败不阻断填写，用户可直接补全后保存
    loadError.value = true
  } finally {
    loading.value = false
  }
}

/** 保存：前端校验通过后再提交，后端校验错误直接展示 message */
async function handleSubmit(): Promise<void> {
  if (saving.value) return

  serverError.value = ''
  const instance = formRef.value
  if (!instance) return

  // 校验失败会 reject，这里统一转成 false
  const valid = await instance.validate().catch(() => false)
  if (!valid) return

  // 校验通过后做一次显式收敛，避免把 undefined / NaN 传给后端
  const gender = form.gender
  const heightCm = Number(form.heightCm)
  const weightKg = Number(form.weightKg)
  if (!gender || !Number.isFinite(heightCm) || !Number.isFinite(weightKg)) {
    serverError.value = '请检查性别、身高和体重是否填写正确'
    return
  }

  const payload: UpdateProfilePayload = { gender, heightCm, weightKg }
  if (typeof form.age === 'number' && Number.isFinite(form.age)) {
    payload.age = form.age
  }

  saving.value = true
  try {
    // silent：错误在弹窗内以更醒目的方式展示，不再弹全局提示
    const profile = await updateMyProfile(payload, { silent: true })
    ElMessage.success('身体资料已保存')
    emit('saved', profile)
    visible.value = false
  } catch (error) {
    serverError.value = error instanceof Error ? error.message : '保存失败，请稍后重试'
  } finally {
    saving.value = false
  }
}

/** 弹窗打开时回填资料并清空上一次的校验 / 错误提示 */
watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    serverError.value = ''
    loadError.value = false
    void loadProfile().then(() => {
      void nextTick(() => formRef.value?.clearValidate())
    })
  }
)
</script>

<template>
  <el-dialog
    v-model="visible"
    title="我的身体资料"
    width="520px"
    append-to-body
    :close-on-click-modal="!saving"
    :close-on-press-escape="!saving"
  >
    <p class="profile-dialog__intro">
      填写性别、身高、体重（年龄可选），我们据此估算你的基础代谢与每日建议摄入。资料仅用于计算，不会公开展示。
    </p>

    <el-alert
      v-if="serverError"
      :title="serverError"
      type="error"
      show-icon
      :closable="false"
      class="profile-dialog__alert"
    />

    <div v-loading="loading" class="profile-dialog__body">
      <el-alert
        v-if="loadError"
        title="资料加载失败，可直接填写后保存"
        type="warning"
        show-icon
        :closable="false"
        class="profile-dialog__alert"
      >
        <template #default>
          <el-button link type="primary" @click="loadProfile">重新加载</el-button>
        </template>
      </el-alert>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="性别" prop="gender">
          <el-radio-group v-model="form.gender">
            <el-radio value="MALE">男</el-radio>
            <el-radio value="FEMALE">女</el-radio>
          </el-radio-group>
        </el-form-item>

        <div class="profile-dialog__grid">
          <el-form-item label="身高（cm）" prop="heightCm">
            <el-input-number
              v-model="form.heightCm"
              :min="100"
              :max="250"
              :step="1"
              :precision="1"
              controls-position="right"
              placeholder="如 170"
              class="profile-dialog__number"
            />
          </el-form-item>

          <el-form-item label="体重（kg）" prop="weightKg">
            <el-input-number
              v-model="form.weightKg"
              :min="30"
              :max="300"
              :step="0.5"
              :precision="1"
              controls-position="right"
              placeholder="如 65"
              class="profile-dialog__number"
            />
          </el-form-item>

          <el-form-item label="年龄（可选）" prop="age">
            <el-input-number
              v-model="form.age"
              :min="10"
              :max="100"
              :step="1"
              :precision="0"
              controls-position="right"
              placeholder="默认 30"
              class="profile-dialog__number"
            />
          </el-form-item>
        </div>
      </el-form>
    </div>

    <template #footer>
      <el-button :disabled="saving" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="loading" @click="handleSubmit">
        {{ saving ? '保存中…' : '保存' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.profile-dialog__intro {
  margin-bottom: 16px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--mw-muted);
}

.profile-dialog__alert {
  margin-bottom: 14px;
  border-radius: 10px;
}

.profile-dialog__body {
  min-height: 180px;
}

.profile-dialog__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(132px, 1fr));
  gap: 0 12px;
}

.profile-dialog__number {
  width: 100%;
}
</style>
