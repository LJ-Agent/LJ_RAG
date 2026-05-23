<template>
  <div class="page-container">
    <div class="page-container__header">
      <h2>反馈管理</h2>
    </div>

    <el-tabs v-model="activeStatus" @tab-change="onTabChange">
      <el-tab-pane label="全部" value="" />
      <el-tab-pane v-for="(item, key) in FEEDBACK_STATUS_MAP" :key="key" :value="key">
        <template #label>
          <el-badge :value="item.label" :type="item.type" class="item" />
          <span style="margin-left: 4px;">{{ item.label }}</span>
        </template>
      </el-tab-pane>
    </el-tabs>

    <el-table :data="list" v-loading="isLoading" stripe border style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column label="类型" width="110" align="center">
        <template #default="{ row }">
          <el-tag size="small">{{ FEEDBACK_TYPE_MAP[row.feedbackType] || row.feedbackType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="content" label="反馈内容" min-width="240" show-overflow-tooltip />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="FEEDBACK_STATUS_MAP[row.status]?.type || 'info'" size="small">
            {{ FEEDBACK_STATUS_MAP[row.status]?.label || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="contact" label="联系方式" width="160" show-overflow-tooltip />
      <el-table-column label="提交时间" width="170" align="center">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="showDetail(row)">查看</el-button>
          <el-button v-if="row.status === 'PENDING'" link type="success" size="small" @click="openHandle(row)" v-permission="'FEEDBACK:HANDLE'">处理</el-button>
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
    <el-dialog v-model="detailVisible" title="反馈详情" width="560px">
      <el-descriptions v-if="currentFeedback" :column="1" border>
        <el-descriptions-item label="ID">{{ currentFeedback.id }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ FEEDBACK_TYPE_MAP[currentFeedback.feedbackType] || currentFeedback.feedbackType }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="FEEDBACK_STATUS_MAP[currentFeedback.status]?.type || 'info'" size="small">
            {{ FEEDBACK_STATUS_MAP[currentFeedback.status]?.label || currentFeedback.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="联系方式">{{ currentFeedback.contact || '-' }}</el-descriptions-item>
        <el-descriptions-item label="反馈内容">{{ currentFeedback.content }}</el-descriptions-item>
        <el-descriptions-item label="处理备注">{{ currentFeedback.handlerNote || '-' }}</el-descriptions-item>
        <el-descriptions-item label="处理时间">{{ formatDate(currentFeedback.handledAt) }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 处理弹窗 -->
    <el-dialog v-model="handleVisible" title="处理反馈" width="480px" destroy-on-close @closed="handleNote = ''">
      <el-form>
        <el-form-item label="处理备注" required>
          <el-input v-model="handleNote" type="textarea" :rows="3" placeholder="请输入处理备注" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="handling" @click="submitHandle">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { feedbackApi } from '@/api/modules/feedback'
import { usePagination } from '@/composables/usePagination'
import { formatDate } from '@/utils/format'
import { FEEDBACK_TYPE_MAP, FEEDBACK_STATUS_MAP } from '@/utils/constants'
import type { FeedbackVO } from '@/api/types/feedback'
import { ElMessage } from 'element-plus'

const pagination = usePagination()
const activeStatus = ref('')
const list = ref<FeedbackVO[]>([])
const isLoading = ref(false)

const detailVisible = ref(false)
const currentFeedback = ref<FeedbackVO | null>(null)

const handleVisible = ref(false)
const handleNote = ref('')
const handling = ref(false)
const handlingId = ref(0)

async function fetchList() {
  isLoading.value = true
  try {
    const res = await feedbackApi.list({
      page: pagination.params.page,
      size: pagination.params.size,
      status: activeStatus.value || undefined,
    })
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

function showDetail(row: FeedbackVO) {
  currentFeedback.value = row
  detailVisible.value = true
}

function openHandle(row: FeedbackVO) {
  handlingId.value = row.id
  handleNote.value = ''
  handleVisible.value = true
}

async function submitHandle() {
  if (!handleNote.value.trim()) {
    ElMessage.warning('请输入处理备注')
    return
  }
  handling.value = true
  try {
    await feedbackApi.handle(handlingId.value, handleNote.value)
    ElMessage.success('处理完成')
    handleVisible.value = false
    fetchList()
  } finally {
    handling.value = false
  }
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
