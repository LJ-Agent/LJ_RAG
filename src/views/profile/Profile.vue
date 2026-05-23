<template>
  <div class="page-container">
    <div class="page-container__header">
      <h2>个人中心</h2>
    </div>

    <el-row :gutter="20">
      <!-- 个人信息 -->
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>个人信息</span>
          </template>
          <el-descriptions :column="1" border v-if="authStore.user">
            <el-descriptions-item label="用户名">{{ authStore.user.username }}</el-descriptions-item>
            <el-descriptions-item label="真实姓名">{{ authStore.user.realName }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ authStore.user.email || '-' }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ authStore.user.phone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="角色">
              <el-tag v-for="role in authStore.user.roles" :key="role" size="small" style="margin-right: 4px;">{{ role }}</el-tag>
              <span v-if="!authStore.user.roles?.length" style="color:#c0c4cc">-</span>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="authStore.user.status === 1 ? 'success' : 'danger'" size="small">
                {{ authStore.user.status === 1 ? '启用' : '禁用' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="最后登录">{{ formatDate(authStore.user.lastLoginAt) }}</el-descriptions-item>
            <el-descriptions-item label="注册时间">{{ formatDate(authStore.user.createdAt) }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <!-- 修改密码 -->
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>修改密码</span>
          </template>
          <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" style="max-width: 400px;">
            <el-form-item label="原密码" prop="oldPassword">
              <el-input v-model="form.oldPassword" type="password" show-password placeholder="请输入原密码" />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="form.newPassword" type="password" show-password placeholder="6-128个字符" />
            </el-form-item>
            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input v-model="form.confirmPassword" type="password" show-password placeholder="请确认新密码" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="submitting" @click="handleChangePwd">修改密码</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { userApi } from '@/api/modules/users'
import { useAuthStore } from '@/stores/auth'
import { formatDate } from '@/utils/format'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'

const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const validateConfirm = (_rule: any, value: string, callback: any) => {
  if (value !== form.newPassword) {
    callback(new Error('两次密码输入不一致'))
  } else {
    callback()
  }
}

const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 128, message: '密码长度为6-128个字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' },
  ],
}

async function handleChangePwd() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await userApi.changePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
    })
    ElMessage.success('密码修改成功，请重新登录')
    authStore.clearAuth()
    location.href = '/login'
  } finally {
    submitting.value = false
  }
}
</script>
