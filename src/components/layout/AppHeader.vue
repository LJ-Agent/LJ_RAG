<template>
  <div class="header">
    <div class="header__left">
      <el-icon class="header__toggle" @click="appStore.toggleSidebar" :size="20">
        <Fold v-if="!appStore.sidebarCollapsed" />
        <Expand v-else />
      </el-icon>
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item v-if="route.meta.title">{{ route.meta.title }}</el-breadcrumb-item>
      </el-breadcrumb>
    </div>
    <div class="header__right">
      <el-dropdown trigger="click" @command="handleCommand">
        <span class="header__user">
          <el-icon><UserFilled /></el-icon>
          <span>{{ authStore.user?.realName || authStore.user?.username || '用户' }}</span>
          <el-icon><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">个人中心</el-dropdown-item>
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { usePermissionStore } from '@/stores/permission'
import { loginApi } from '@/api/modules/auth'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const authStore = useAuthStore()
const permissionStore = usePermissionStore()

async function handleCommand(cmd: string) {
  if (cmd === 'profile') {
    router.push('/profile')
  } else if (cmd === 'logout') {
    try { await loginApi.logout() } catch { /* ignore */ }
    authStore.clearAuth()
    permissionStore.clearAll()
    router.push('/login')
  }
}
</script>

<style scoped>
.header {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header__left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.header__toggle {
  cursor: pointer;
}
.header__right {
  display: flex;
  align-items: center;
}
.header__user {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #606266;
}
</style>
