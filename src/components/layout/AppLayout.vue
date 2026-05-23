<template>
  <el-container class="app-layout">
    <el-aside :width="sidebarWidth" class="app-aside">
      <AppSidebar />
    </el-aside>
    <el-container>
      <el-header class="app-header">
        <AppHeader />
      </el-header>
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useAppStore } from '@/stores/app'
import AppSidebar from './AppSidebar.vue'
import AppHeader from './AppHeader.vue'

const appStore = useAppStore()
const sidebarWidth = computed(() =>
  appStore.sidebarCollapsed ? '64px' : '220px'
)
</script>

<style scoped>
.app-layout {
  height: 100vh;
}
.app-aside {
  background-color: #304156;
  transition: width 0.3s;
  overflow: hidden;
}
.app-header {
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  padding: 0 20px;
  height: 56px;
}
.app-main {
  background: #f5f7fa;
  padding: 20px;
}
</style>
