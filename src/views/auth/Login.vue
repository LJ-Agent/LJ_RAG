<template>
  <div class="login-page">
    <div class="login-card">
      <h1 class="login-card__title">RAG 知识库系统</h1>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password :prefix-icon="Lock" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" style="width: 100%" @click="handleLogin">登 录</el-button>
        </el-form-item>
      </el-form>
      <div class="login-card__footer">
        <span>还没有账号？</span>
        <router-link to="/register">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { loginApi } from '@/api/modules/auth'
import { useAuthStore } from '@/stores/auth'
import { usePermissionStore } from '@/stores/permission'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const permissionStore = usePermissionStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await loginApi.login(form)
    authStore.setAuth(res.accessToken, res.refreshToken, res.userInfo)
    permissionStore.setPermissions(res.userInfo.permissions || [])
    permissionStore.setRoles(res.userInfo.roles || [])

    const redirect = (route.query.redirect as string) || '/dashboard'
    router.push(redirect)
  } catch {
    // 拦截器已处理错误消息
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.15);
}
.login-card__title {
  text-align: center;
  font-size: 24px;
  color: #303133;
  margin-bottom: 30px;
}
.login-card__footer {
  text-align: center;
  font-size: 14px;
  color: #909399;
}
.login-card__footer a {
  color: #409eff;
  margin-left: 4px;
}
</style>
