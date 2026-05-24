<template>
  <div class="page-container">
    <div class="page-container__header">
      <h2>文档列表</h2>
      <el-button type="primary" @click="$router.push('/documents/upload')" v-permission="'DOCUMENT:UPLOAD'">
        <el-icon><Upload /></el-icon> 上传文档
      </el-button>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="文件名称">
          <el-input v-model="query.fileName" placeholder="输入文件名搜索" clearable @clear="doSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px" @change="doSearch">
            <el-option v-for="(item, key) in DOCUMENT_STATUS_MAP" :key="key" :value="key" :label="item.label" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="doSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <el-table :data="list" v-loading="isLoading" stripe border style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" align="center" />
      <el-table-column prop="fileName" label="文件名称" min-width="200" show-overflow-tooltip />
      <el-table-column label="文件类型" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.fileType?.toUpperCase() }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="文件大小" width="100" align="center">
        <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="DOCUMENT_STATUS_MAP[row.status]?.type || 'info'" size="small">
            {{ DOCUMENT_STATUS_MAP[row.status]?.label || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="上传时间" width="170" align="center">
        <template #default="{ row }">{{ formatDate(row.uploadAt || row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="340" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="showDetail(row)">详情</el-button>
          <el-button link type="primary" size="small" @click="$router.push(`/documents/${row.id}/content`)">浏览</el-button>
          <el-button
            v-if="row.status === 'CHUNK_REVIEW'"
            link type="warning" size="small"
            @click="$router.push(`/documents/${row.id}/chunks`)"
          >块管理</el-button>
          <el-button link type="primary" size="small" @click="handleDownload(row)">下载</el-button>
          <el-popconfirm title="确定要删除该文档吗？" @confirm="handleDelete(row.id)">
            <template #reference>
              <el-button link type="danger" size="small">删除</el-button>
            </template>
          </el-popconfirm>
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
    <el-dialog v-model="detailVisible" title="文档详情" width="560px">
      <el-descriptions v-if="currentDoc" :column="2" border>
        <el-descriptions-item label="ID">{{ currentDoc.id }}</el-descriptions-item>
        <el-descriptions-item label="文件名称">{{ currentDoc.fileName }}</el-descriptions-item>
        <el-descriptions-item label="文件类型">{{ currentDoc.fileType?.toUpperCase() }}</el-descriptions-item>
        <el-descriptions-item label="文件大小">{{ formatFileSize(currentDoc.fileSize) }}</el-descriptions-item>
        <el-descriptions-item label="MD5">{{ currentDoc.fileMd5 }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="DOCUMENT_STATUS_MAP[currentDoc.status]?.type || 'info'" size="small">
            {{ DOCUMENT_STATUS_MAP[currentDoc.status]?.label || currentDoc.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="分块数">{{ currentDoc.chunkCount }}</el-descriptions-item>
        <el-descriptions-item label="分块策略">{{ currentDoc.chunkStrategy || 'semantic' }}</el-descriptions-item>
        <el-descriptions-item label="错误信息" :span="2">{{ currentDoc.errorMessage || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { Upload } from '@element-plus/icons-vue'
import { fileApi } from '@/api/modules/files'
import { usePagination } from '@/composables/usePagination'
import { formatDate, formatFileSize } from '@/utils/format'
import { DOCUMENT_STATUS_MAP } from '@/utils/constants'
import type { FileVO } from '@/api/types/file'
import { ElMessage } from 'element-plus'

const pagination = usePagination()
const query = reactive({ fileName: '', status: undefined as string | undefined })
const list = ref<FileVO[]>([])
const isLoading = ref(false)

const detailVisible = ref(false)
const currentDoc = ref<FileVO | null>(null)

async function fetchList() {
  isLoading.value = true
  try {
    const res = await fileApi.list({
      ...query,
      page: pagination.params.page,
      size: pagination.params.size,
    })
    list.value = res.records
    pagination.setTotal(res.total)
  } finally {
    isLoading.value = false
  }
}

function doSearch() {
  pagination.reset()
  pagination.params.page = 1
  fetchList()
}

function resetSearch() {
  query.fileName = ''
  query.status = undefined
  doSearch()
}

function showDetail(row: FileVO) {
  currentDoc.value = row
  detailVisible.value = true
}

async function handleDownload(row: FileVO) {
  try {
    await fileApi.download(row.id, row.fileName)
  } catch {
    ElMessage.error('下载失败')
  }
}

async function handleDelete(id: number) {
  await fileApi.delete(id)
  ElMessage.success('删除成功')
  fetchList()
}

watch(pagination.params, () => fetchList(), { immediate: true })
</script>

<style scoped>
.search-bar {
  margin-bottom: 16px;
  padding: 16px;
  background: #fafafa;
  border-radius: 4px;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
