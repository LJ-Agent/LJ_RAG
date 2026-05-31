<template>
  <el-container class="app-layout">
    <el-aside :width="sidebarWidth" class="app-aside">
      <AppSidebar />
    </el-aside>
    <el-container>
      <el-header class="app-header">
        <AppHeader />
      </el-header>
      <TabBar />
      <el-main class="app-main">
        <router-view v-slot="{ Component, route: r }">
          <keep-alive :max="10">
            <component :is="Component" :key="r.path" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useTabStore } from '@/stores/tabs'
import AppSidebar from './AppSidebar.vue'
import AppHeader from './AppHeader.vue'
import TabBar from './TabBar.vue'

const appStore = useAppStore()
const tabStore = useTabStore()
const route = useRoute()

const sidebarWidth = computed(() =>
  appStore.sidebarCollapsed ? '64px' : '220px'
)

// 监听路由变化自动添加标签
watch(
  () => route.fullPath,
  () => tabStore.addTab(route),
  { immediate: true }
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
