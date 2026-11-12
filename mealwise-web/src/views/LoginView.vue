<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import AuthShell from '@/components/AuthShell.vue'
import { useAuthStore } from '@/stores/auth'

interface LoginForm {
  username: string
  password: string
}

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive<LoginForm>({
  // 注册成功跳转回来时带上用户名，省得用户再输一遍
  username: typeof route.query.username === 'string' ? route.query.username : '',
  password: ''
})

const rules: FormRules<LoginForm> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_]{4,50}$/,
      message: '用户名需为 4-50 位字母、数字或下划线',
      trigger: 'blur'
    }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    {
      pattern: /^(?=.*[A-Za-z])(?=.*\d)\S{8,32}$/,
      message: '密码需为 8-32 位，且同时包含字母和数字',
      trigger: 'blur'
    }
  ]
}

/** 登录：校验 → 请求 → 按 redirect 回到最初想去的页面 */
async function handleSubmit(): Promise<void> {
  if (!formRef.value || submitting.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await authStore.login({
      username: form.username.trim(),
      password: form.password
    })
    ElMessage.success(`欢迎回来，${authStore.username}`)

    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const target = redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/foods'
    await router.replace(target)
  } catch {
    // 后端 message 已由 axios 响应拦截器统一用 ElMessage 展示
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthShell title="欢迎回来" subtitle="登录后继续管理你的食物库，记录今天这一餐。">
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
          placeholder="请输入用户名"
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
          placeholder="请输入密码"
          autocomplete="current-password"
          maxlength="32"
          show-password
        />
      </el-form-item>

      <el-button
        type="primary"
        size="large"
        native-type="submit"
        class="auth-submit"
        :loading="submitting"
      >
        {{ submitting ? '登录中…' : '登录' }}
      </el-button>
    </el-form>

    <template #footer>
      还没有账号？
      <RouterLink to="/register">创建一个账号</RouterLink>
    </template>
  </AuthShell>
</template>

<style scoped>
.auth-form {
  margin-top: 26px;
}

.auth-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.auth-form :deep(.el-input__wrapper) {
  background: #fbfdfb;
  padding: 4px 14px;
  box-shadow: 0 0 0 1px var(--mw-line) inset;
}

.auth-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #cfded5 inset;
}

.auth-submit {
  width: 100%;
  height: 47px;
  margin-top: 6px;
  border-radius: 12px;
  font-size: 15px;
  letter-spacing: 0.02em;
}
</style>
