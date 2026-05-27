<template>
  <div class="page-container">
    <div class="page-container__header">
      <h2>审核管理</h2>
      <el-button type="primary" :disabled="selectedIds.length === 0" @click="handleBatchApprove">
        <el-icon><Check /></el-icon> 批量通过 ({{ selectedIds.length }})
      </el-button>
    </div>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane name="CHUNK_REVIEW" label="待块审核" />
      <el-tab-pane name="PENDING" label="待审核" />
      <el-tab-pane name="APPROVED" label="已通过" />
      <el-tab-pane name="REJECTED" label="已驳回" />
    </el-tabs>

    <el-table :data="list" v-loading="isLoading" stripe border style="width: 100%" @selection-change="onSelectionChange">
      <el-table-column type="selection" width="50" v-if="activeTab === 'PENDING' || activeTab === 'CHUNK_REVIEW'" />
      <el-table-column prop="id" label="ID" width="80" align="center" />
      <el-table-column prop="documentName" label="文档名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="chunkCount" label="块数" width="70" align="center" />
      <el-table-column prop="reviewerName" label="审核人" width="120" align="center">
        <template #default="{ row }">{{ row.reviewerName || '-' }}</template>
      </el-table-column>
      <el-table-column label="审核结果" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="REVIEW_RESULT_MAP[row.result]?.type || 'info'" size="small">
            {{ REVIEW_RESULT_MAP[row.result]?.label || row.result || '待块审核' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="comment" label="审核意见" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.comment || '-' }}</template>
      </el-table-column>
      <el-table-column label="时间" width="170" align="center">
        <template #default="{ row }">{{ formatDate(row.reviewedAt || row.createdAt) }}</template>
      </el-table-column>
      <el-table-column v-if="activeTab === 'PENDING' || activeTab === 'CHUNK_REVIEW'" label="操作" width="260" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.chunkCount > 0" link type="primary" size="small" @click="$router.push(`/documents/${row.documentId}/chunks`)">分块详情</el-button>
          <el-button link type="success" size="small" @click="approve(row)">通过</el-button>
          <el-button link type="danger" size="small" @click="openReject(row)">驳回</el-button>
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

    <!-- 驳回弹窗 -->
    <el-dialog v-model="rejectVisible" title="驳回文档" width="480px" destroy-on-close>
      <el-form :model="rejectForm">
        <el-form-item label="驳回原因" required>
          <el-input v-model="rejectForm.comment" type="textarea" :rows="3" placeholder="请输入驳回原因" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="handleReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { Check } from '@element-plus/icons-vue'
import { reviewApi } from '@/api/modules/review'
import { usePagination } from '@/composables/usePagination'
import { formatDate } from '@/utils/format'
import { REVIEW_RESULT_MAP, DOCUMENT_STATUS_MAP } from '@/utils/constants'
import type { ReviewVO } from '@/api/types/review'
import { ElMessage } from 'element-plus'

const pagination = usePagination()
const activeTab = ref('PENDING')
const list = ref<ReviewVO[]>([])
const isLoading = ref(false)
const selectedIds = ref<number[]>([])
const submitting = ref(false)

const rejectVisible = ref(false)
const rejectForm = reactive({ documentId: 0, comment: '' })

async function fetchList() {
  isLoading.value = true
  selectedIds.value = []
  try {
    let res
    if (activeTab.value === 'CHUNK_REVIEW') {
      res = await reviewApi.chunkReview({ page: pagination.params.page, size: pagination.params.size })
    } else {
      const params: Record<string, any> = { page: pagination.params.page, size: pagination.params.size, result: activeTab.value }
      res = await reviewApi.pending(params)
    }
    list.value = res.records
    pagination.setTotal(res.total)
  } finally {
    isLoading.value = false
  }
}

function onTabChange() {
  pagination.reset()
  pagination.params.page = 1
  fetchList()
}

function onSelectionChange(rows: ReviewVO[]) {
  selectedIds.value = rows.map((r) => r.documentId)
}

async function approve(row: ReviewVO) {
  try {
    await reviewApi.submit({ documentId: row.documentId, result: 'APPROVED' })
    ElMessage.success('审核通过')
    fetchList()
  } catch { /* ignore */ }
}

function openReject(row: ReviewVO) {
  rejectForm.documentId = row.documentId
  rejectForm.comment = ''
  rejectVisible.value = true
}

async function handleReject() {
  if (!rejectForm.comment.trim()) {
    ElMessage.warning('请输入驳回原因')
    return
  }
  submitting.value = true
  try {
    await reviewApi.submit({ documentId: rejectForm.documentId, result: 'REJECTED', comment: rejectForm.comment })
    ElMessage.success('已驳回')
    rejectVisible.value = false
    fetchList()
  } catch { /* ignore */ }
  finally { submitting.value = false }
}

async function handleBatchApprove() {
  if (selectedIds.value.length === 0) return
  try {
    await reviewApi.batchApprove(selectedIds.value)
    ElMessage.success(`已批量通过 ${selectedIds.value.length} 个文档`)
    fetchList()
  } catch { /* ignore */ }
}

watch(pagination.params, () => fetchList(), { immediate: true })
</script>

<style scoped>
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
