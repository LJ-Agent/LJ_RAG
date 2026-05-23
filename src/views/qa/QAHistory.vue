<template>
  <div class="page-container">
    <div class="page-container__header">
      <h2>问答历史</h2>
    </div>

    <el-table :data="list" v-loading="isLoading" stripe border style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" align="center" />
      <el-table-column prop="question" label="问题" min-width="200" show-overflow-tooltip />
      <el-table-column label="回答" min-width="280" show-overflow-tooltip>
        <template #default="{ row }">{{ truncateText(row.answer, 80) }}</template>
      </el-table-column>
      <el-table-column label="评分" width="80" align="center">
        <template #default="{ row }">
          <el-rate :model-value="row.rating || 0" disabled show-score text-color="#ff9900" />
        </template>
      </el-table-column>
      <el-table-column label="延迟" width="100" align="center">
        <template #default="{ row }">{{ row.latencyMs }}ms</template>
      </el-table-column>
      <el-table-column label="模式" width="80" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.isStream === 1 ? 'success' : 'info'">
            {{ row.isStream === 1 ? '流式' : '非流式' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="170" align="center">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="80" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="showDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="pagination.params.page"
        v-model:page-size="pagination.params.size"
        :total="pagination.total.value"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="pagination.onPageChange"
        @size-change="pagination.onSizeChange"
      />
    </div>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="问答详情" width="700px">
      <div v-if="currentRecord">
        <div class="qa-detail-q">
          <strong>Q:</strong> {{ currentRecord.question }}
        </div>
        <div class="qa-detail-a">
          <strong>A:</strong>
          <div v-html="renderMarkdown(currentRecord.answer)" class="markdown-body" style="margin-top: 8px;"></div>
        </div>
        <el-divider />
        <el-descriptions :column="3" size="small" border>
          <el-descriptions-item label="延迟">{{ currentRecord.latencyMs }}ms</el-descriptions-item>
          <el-descriptions-item label="模式">{{ currentRecord.isStream === 1 ? '流式' : '非流式' }}</el-descriptions-item>
          <el-descriptions-item label="时间">{{ formatDate(currentRecord.createdAt) }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { qaApi } from '@/api/modules/qa'
import { usePagination } from '@/composables/usePagination'
import { formatDate, truncateText } from '@/utils/format'
import { sanitizeHtml } from '@/utils/sanitize'
import { marked } from 'marked'
import type { ChatHistoryVO } from '@/api/types/qa'

const pagination = usePagination()
const list = ref<ChatHistoryVO[]>([])
const isLoading = ref(false)

const detailVisible = ref(false)
const currentRecord = ref<ChatHistoryVO | null>(null)

function renderMarkdown(text: string): string {
  return sanitizeHtml(marked.parse(text || '') as string)
}

async function fetchList() {
  isLoading.value = true
  try {
    const res = await qaApi.history({
      page: pagination.params.page,
      size: pagination.params.size,
    })
    list.value = res.records
    pagination.setTotal(res.total)
  } finally {
    isLoading.value = false
  }
}

function showDetail(row: ChatHistoryVO) {
  currentRecord.value = row
  detailVisible.value = true
}

watch(pagination.params, () => fetchList(), { immediate: true })
</script>

<style scoped>
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.qa-detail-q {
  margin-bottom: 16px;
  line-height: 1.6;
}
.qa-detail-a {
  line-height: 1.7;
}
.markdown-body :deep(p) { margin: 0.5em 0; }
.markdown-body :deep(pre) { background: #282c34; color: #abb2bf; padding: 12px; border-radius: 4px; overflow-x: auto; }
.markdown-body :deep(code) { background: rgba(0,0,0,0.06); padding: 2px 6px; border-radius: 3px; }
.markdown-body :deep(pre code) { background: transparent; padding: 0; }
</style>
