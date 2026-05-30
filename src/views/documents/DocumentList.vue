<template>
  <div class="page-container">
    <div class="page-container__header">
      <h2>文档列表</h2>
      <div class="header-actions">
        <el-button type="danger" :disabled="selectedIds.length === 0" @click="handleBatchDelete">
          <el-icon><Delete /></el-icon> 批量删除 ({{ selectedIds.length }})
        </el-button>
        <el-button type="primary" @click="$router.push('/documents/upload')" v-permission="'DOCUMENT:UPLOAD'">
          <el-icon><Upload /></el-icon> 上传文档
        </el-button>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="所属知识库">
          <el-select v-model="query.kbId" placeholder="全部" clearable style="width: 180px" @change="doSearch">
            <el-option v-for="kb in kbList" :key="kb.id" :label="kb.kbName" :value="kb.id" />
          </el-select>
        </el-form-item>
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
    <el-table :data="list" v-loading="isLoading" stripe border style="width: 100%" @selection-change="onSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="文件名称" min-width="200">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openRawFile(row)">
            {{ row.fileName }}
          </el-button>
        </template>
      </el-table-column>
      <el-table-column prop="kbName" label="所属知识库" width="150" align="center" show-overflow-tooltip>
        <template #default="{ row }">{{ row.kbName || '-' }}</template>
      </el-table-column>
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
      <el-table-column label="操作" width="380" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="showDetail(row)">详情</el-button>
          <el-button link type="primary" size="small" @click="$router.push(`/documents/${row.id}/content`)">浏览</el-button>
          <el-button
            v-if="row.status === 'CHUNK_REVIEW'"
            link type="primary" size="small"
            @click="$router.push(`/documents/${row.id}/chunks`)"
          >分块详情</el-button>
          <el-button
            v-if="row.status === 'PENDING_REVIEW' || row.status === 'COMPLETED'"
            link type="primary" size="small"
            @click="$router.push(`/documents/${row.id}/chunks`)"
          >查看分块</el-button>
          <el-button
            v-if="row.status === 'REJECTED' || row.status === 'CHUNKING_FAILED' || row.status === 'PARSING_FAILED' || row.status === 'CLEANING_FAILED' || row.status === 'COMPLETED'"
            link type="warning" size="small"
            @click="openRechunk(row)"
          >重新分块</el-button>
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
        <el-descriptions-item label="文件名称">{{ currentDoc.fileName }}</el-descriptions-item>
        <el-descriptions-item label="所属知识库">{{ currentDoc.kbName || '-' }}</el-descriptions-item>
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

    <!-- 重新分块弹窗 -->
    <el-dialog v-model="rechunkVisible" title="重新分块" width="560px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="当前文件">
          <span>{{ rechunkDoc?.fileName }}</span>
        </el-form-item>
        <el-form-item label="分块策略" required>
          <el-select v-model="rechunkForm.chunkStrategy" style="width: 100%" @change="onRechunkStrategyChange">
            <el-option v-for="(cfg, key) in CHUNK_STRATEGY_CONFIGS" :key="key" :label="cfg.label" :value="key" />
          </el-select>
        </el-form-item>
        <template v-if="rechunkFields.length > 0">
          <el-divider content-position="left">{{ currentRechunkStrategyLabel }} — 参数配置</el-divider>
          <el-form-item
            v-for="field in rechunkFields"
            :key="field.key"
            :label="field.label"
          >
            <el-input-number
              v-if="field.type === 'number'"
              v-model="rechunkParams[field.key]"
              :min="field.min"
              :max="field.max"
              :step="field.step || 1"
              controls-position="right"
              style="width: 280px"
            />
            <el-input
              v-else
              v-model="rechunkParams[field.key]"
              style="width: 360px"
              :placeholder="String(field.default)"
            />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="rechunkVisible = false">取消</el-button>
        <el-button @click="resetRechunkParams" text type="primary" style="float: left">恢复默认值</el-button>
        <el-button type="primary" :loading="rechunking" @click="handleRechunk">确认重新分块</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Upload, Delete } from '@element-plus/icons-vue'
import { fileApi } from '@/api/modules/files'
import { knowledgeBaseApi } from '@/api/modules/knowledgeBase'
import { usePagination } from '@/composables/usePagination'
import { formatDate, formatFileSize } from '@/utils/format'
import { DOCUMENT_STATUS_MAP } from '@/utils/constants'
import { CHUNK_STRATEGY_CONFIGS } from '@/api/types/file'
import type { FileVO, StrategyField } from '@/api/types/file'
import type { KnowledgeBaseVO } from '@/api/types/knowledgeBase'
import { getAccessToken } from '@/utils/token'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const pagination = usePagination()
const query = reactive({ kbId: undefined as number | undefined, fileName: '', status: undefined as string | undefined })
const list = ref<FileVO[]>([])
const isLoading = ref(false)
const kbList = ref<KnowledgeBaseVO[]>([])
const selectedIds = ref<number[]>([])

const detailVisible = ref(false)
const currentDoc = ref<FileVO | null>(null)

const rechunkVisible = ref(false)
const rechunkDoc = ref<FileVO | null>(null)
const rechunkForm = reactive({ chunkStrategy: 'semantic' })
const rechunkParams = reactive<Record<string, any>>({})
const rechunking = ref(false)

const rechunkFields = computed<StrategyField[]>(() =>
  CHUNK_STRATEGY_CONFIGS[rechunkForm.chunkStrategy]?.fields || []
)
const currentRechunkStrategyLabel = computed(() =>
  CHUNK_STRATEGY_CONFIGS[rechunkForm.chunkStrategy]?.label || ''
)

onMounted(async () => {
  // 加载知识库列表供筛选下拉
  try {
    const res = await knowledgeBaseApi.list({ page: 1, size: 100 })
    kbList.value = res.records
  } catch { /* ignore */ }

  // 从路由参数获取 kbId（支持从知识库下钻）
  const kbIdFromRoute = route.query.kbId
  if (kbIdFromRoute) {
    query.kbId = Number(kbIdFromRoute)
    pagination.reset()
    pagination.params.page = 1
    fetchList()
  }
})

async function fetchList() {
  isLoading.value = true
  selectedIds.value = []
  try {
    const params: Record<string, any> = {
      page: pagination.params.page,
      size: pagination.params.size,
    }
    if (query.kbId) params.kbId = query.kbId
    if (query.fileName) params.fileName = query.fileName
    if (query.status) params.status = query.status
    const res = await fileApi.list(params)
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
  query.kbId = undefined
  query.fileName = ''
  query.status = undefined
  doSearch()
}

function onSelectionChange(rows: FileVO[]) {
  selectedIds.value = rows.map((r) => r.id)
}

function showDetail(row: FileVO) {
  currentDoc.value = row
  detailVisible.value = true
}

function openRawFile(row: FileVO) {
  const ext = row.fileName?.split('.').pop()?.toLowerCase() || ''
  // Word/Excel 浏览器无法原生渲染，跳转到 RawFileView 用组件渲染
  if (['docx', 'doc', 'xlsx', 'xls'].includes(ext)) {
    router.push(`/documents/${row.id}/raw-view`)
    return
  }
  const token = getAccessToken()
  const url = `${import.meta.env.VITE_API_BASE_URL}/files/${row.id}/raw?token=${encodeURIComponent(token || '')}`
  window.open(url, '_blank')
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

async function handleBatchDelete() {
  if (selectedIds.value.length === 0) return
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedIds.value.length} 个文档吗？此操作不可恢复。`,
      '批量删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await fileApi.batchDelete(selectedIds.value)
    ElMessage.success(`已删除 ${selectedIds.value.length} 个文档`)
    selectedIds.value = []
    fetchList()
  } catch { /* cancelled or error */ }
}

function openRechunk(row: FileVO) {
  rechunkDoc.value = row
  rechunkForm.chunkStrategy = row.chunkStrategy || 'semantic'
  initRechunkParams(rechunkForm.chunkStrategy, row.chunkConfig)
  rechunkVisible.value = true
}

function initRechunkParams(strategy: string, existingConfig?: string) {
  const fields = CHUNK_STRATEGY_CONFIGS[strategy]?.fields || []
  const defaults: Record<string, any> = {}
  for (const f of fields) {
    defaults[f.key] = f.default
  }
  // Try to parse existing config to restore previous values
  if (existingConfig) {
    try {
      const parsed = JSON.parse(existingConfig)
      for (const f of fields) {
        if (parsed[f.key] !== undefined) {
          defaults[f.key] = parsed[f.key]
        }
      }
    } catch { /* ignore parse error, use defaults */ }
  }
  Object.keys(rechunkParams).forEach(k => delete rechunkParams[k])
  Object.assign(rechunkParams, defaults)
}

function onRechunkStrategyChange(strategy: string) {
  initRechunkParams(strategy)
}

function resetRechunkParams() {
  initRechunkParams(rechunkForm.chunkStrategy)
}

async function handleRechunk() {
  if (!rechunkDoc.value) return
  rechunking.value = true
  try {
    const configJson = JSON.stringify({ ...rechunkParams })
    await fileApi.rechunk(rechunkDoc.value.id, rechunkForm.chunkStrategy, configJson)
    ElMessage.success('重新分块任务已发起')
    rechunkVisible.value = false
    fetchList()
  } catch {
    ElMessage.error('重新分块失败')
  } finally {
    rechunking.value = false
  }
}

watch(pagination.params, () => fetchList(), { immediate: true })
</script>

<style scoped>
.header-actions {
  display: flex;
  gap: 12px;
}
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
