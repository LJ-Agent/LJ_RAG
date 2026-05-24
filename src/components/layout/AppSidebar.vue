<template>
  <div class="sidebar">
    <div class="sidebar__logo">
      <span v-if="!appStore.sidebarCollapsed">RAG 知识库</span>
      <span v-else>R</span>
    </div>
    <el-menu
      :default-active="activeMenu"
      :collapse="appStore.sidebarCollapsed"
      :collapse-transition="false"
      background-color="#304156"
      text-color="#bfcbd9"
      active-text-color="#409eff"
      router
    >
      <el-menu-item index="/dashboard">
        <el-icon><Odometer /></el-icon>
        <span>仪表盘</span>
      </el-menu-item>
      <el-menu-item index="/knowledge-bases">
        <el-icon><Collection /></el-icon>
        <span>知识库管理</span>
      </el-menu-item>
      <el-menu-item index="/documents">
        <el-icon><Document /></el-icon>
        <span>文档列表</span>
      </el-menu-item>
      <el-menu-item index="/documents/upload">
        <el-icon><Upload /></el-icon>
        <span>上传文档</span>
      </el-menu-item>
      <el-menu-item index="/review">
        <el-icon><Checked /></el-icon>
        <span>审核管理</span>
      </el-menu-item>
      <el-menu-item index="/qa">
        <el-icon><ChatDotRound /></el-icon>
        <span>知识问答</span>
      </el-menu-item>
      <el-menu-item index="/users">
        <el-icon><User /></el-icon>
        <span>用户管理</span>
      </el-menu-item>
      <el-menu-item index="/configs">
        <el-icon><Setting /></el-icon>
        <span>系统配置</span>
      </el-menu-item>
      <el-menu-item index="/feedback">
        <el-icon><ChatLineSquare /></el-icon>
        <span>反馈管理</span>
      </el-menu-item>
    </el-menu>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'

const route = useRoute()
const appStore = useAppStore()

const activeMenu = computed(() => {
  const { path } = route
  if (path.startsWith('/qa')) return '/qa'
  if (path.startsWith('/documents/upload')) return '/documents/upload'
  if (path.startsWith('/documents')) return '/documents'
  return path
})
</script>

<style scoped>
.sidebar {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.sidebar__logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  user-select: none;
}
.el-menu {
  border-right: none;
  flex: 1;
}
</style>
