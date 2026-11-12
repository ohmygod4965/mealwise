<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import AuthShell from '@/components/AuthShell.vue'
import { ALLERGEN_OPTIONS } from '@/constants/meal'
import { useAuthStore } from '@/stores/auth'

interface RegisterForm {
  username: string
  password: string
  confirmPassword: string
  allergens: string[]
}

const router = useRouter()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive<RegisterForm>({
  username: '',
  password: '',
  confirmPassword: '',
  allergens: []
})

/** 二次确认密码：只在前端校验，不会提交给后端 */
function validateConfirm(_rule: unknown, value: string, callback: (error?: Error) => void): void {
  if (!value) {
    callback(new Error('请再次输入密码'))
  } else if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules: FormRules<RegisterForm> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_]{4,50}$/,
      message: '用户名需为 4-50 位字母、数字或下划线',
      trigger: 'blur'
    }
  ],
  password: [
    { required: true, message: '请设置密码', trigger: 'blur' },
    {
      pattern: /^(?=.*[A-Za-z])(?=.*\d)\S{8,32}$/,
      message: '密码需为 8-32 位，且同时包含字母和数字',
      trigger: 'blur'
    }
  ],
  confirmPassword: [{ validator: validateConfirm, trigger: 'blur' }]
}

/** 注册成功后回到登录页，并预填用户名 */
async function handleSubmit(): Promise<void> {
  if (!formRef.value || submitting.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await authStore.register({
      username: form.username.trim(),
      password: form.password,
      allergens: form.allergens.length ? form.allergens : undefined
    })
    ElMessage.success('注册成功，请使用新账号登录')
    await router.replace({ name: 'login', query: { username: form.username.trim() } })
  } catch {
    // 用户名重复等错误由 axios 拦截器统一提示后端 message
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthShell
    title="创建账号"
    subtitle="建立你的食物库，从今天开始记录每一餐的营养。"
    eyebrow="JOIN MEALWISE"
  >
    <el-form
      ref="formRef"
      class="auth-form"
      :model="form"
      :rules="rules"
      label-position="top"
      size="large"
      @submit.prevent="handleSubmit"
    >
      <el-form-item label="用户名" prop="username">
        <el-input
          v-model="form.username"
          :prefix-icon="User"
          placeholder="4-50 位字母、数字或下划线"
          autocomplete="username"
          maxlength="50"
          clearable
        />
      </el-form-item>

      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          :prefix-icon="Lock"
          type="password"
          placeholder="8-32 位，需同时包含字母和数字"
          autocomplete="new-password"
          maxlength="32"
          show-password
        />
      </el-form-item>

      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          :prefix-icon="Lock"
          type="password"
          placeholder="再次输入密码"
          autocomplete="new-password"
          maxlength="32"
          show-password
        />
      </el-form-item>

      <el-form-item label="过敏原（可选）" prop="allergens">
        <el-select
          v-model="form.allergens"
          multiple
          filterable
          allow-create
          default-first-option
          :reserve-keyword="false"
          placeholder="选择或输入你需要注意的过敏原"
        >
          <el-option
            v-for="item in ALLERGEN_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <p class="field-hint">设置后，食物库会标记含相关过敏原的食物。</p>
      </el-form-item>

      <el-button
        type="primary"
        size="large"
        native-type="submit"
        class="auth-submit"
        :loading="submitting"
      >
        {{ submitting ? '注册中…' : '注册并开始使用' }}
      </el-button>
    </el-form>

    <template #footer>
      已经有账号？
      <RouterLink to="/login">直接登录</RouterLink>
    </template>
  </AuthShell>
</template>

<style scoped>
.auth-form {
  margin-top: 24px;
}

.auth-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.auth-form :deep(.el-input__wrapper) {
  background: #fbfdfb;
  padding: 4px 14px;
  box-shadow: 0 0 0 1px var(--mw-line) inset;
}

.auth-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #cfded5 inset;
}

.auth-form :deep(.el-select__wrapper) {
  background: #fbfdfb;
  min-height: 42px;
  box-shadow: 0 0 0 1px var(--mw-line) inset;
}

.field-hint {
  width: 100%;
  margin-top: 6px;
  font-size: 11.5px;
  line-height: 1.5;
  color: var(--mw-faint);
}

.auth-submit {
  width: 100%;
  height: 47px;
  margin-top: 4px;
  border-radius: 12px;
  font-size: 15px;
  letter-spacing: 0.02em;
}
</style>
