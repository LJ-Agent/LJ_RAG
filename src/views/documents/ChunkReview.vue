<template>
  <div class="page-container">
    <div class="page-container__header">
      <div style="display: flex; align-items: center; gap: 12px;">
        <el-button @click="$router.back()" text>
          <el-icon><ArrowLeft /></el-icon> 返回
        </el-button>
        <h2>{{ pageTitle }}</h2>
        <el-tag v-if="docStatus" size="small">{{ DOCUMENT_STATUS_MAP[docStatus]?.label || docStatus }}</el-tag>
      </div>
      <div style="display: flex; gap: 8px;">
        <!-- chunk-edit 模式：发起审核 -->
        <el-button
          v-if="mode === 'chunk-edit' && stats"
          type="primary"
          :disabled="stats.activeCount === 0"
          :loading="submittingReview"
          @click="doSubmitForReview"
        >
          发起审核 ({{ stats.activeCount }} 个块)
        </el-button>
        <!-- content-review 模式：审核通过/驳回 -->
        <template v-if="mode === 'content-review'">
          <el-button
            type="danger"
            :loading="reviewing"
            @click="doReject"
          >
            驳回
          </el-button>
          <el-button
            type="success"
            :loading="reviewing"
            :disabled="(stats?.activeCount || 0) === 0"
            @click="doApprove"
          >
            审核通过
          </el-button>
        </template>
      </div>
    </div>

    <!-- 统计栏 -->
    <div class="stats-bar" v-if="stats">
      <span>总块数: <b>{{ stats.totalCount }}</b></span>
      <span>活跃: <b style="color: #67c23a">{{ stats.activeCount }}</b></span>
      <span>已删除: <b style="color: #f56c6c">{{ stats.deletedCount }}</b></span>
      <span>总字符数: <b>{{ stats.totalChars.toLocaleString() }}</b></span>
    </div>

    <div class="chunk-toolbar">
      <div style="display: flex; align-items: center; gap: 8px;">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索块内容..."
          clearable
          @input="onSearch"
          style="width: 260px"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button v-if="mode !== 'read-only'" type="primary" @click="openCreateDialog">新增块</el-button>
        <el-button v-if="mode === 'chunk-edit'" type="warning" @click="openRechunkDialog">重新分块</el-button>
      </div>
    </div>

    <div class="chunk-layout">
      <!-- 左侧块列表 -->
      <div class="chunk-list-panel">
        <el-table
          :data="chunkList"
          v-loading="loading"
          highlight-current-row
          @current-change="selectChunk"
          max-height="calc(100vh - 380px)"
          stripe
        >
          <el-table-column label="#" width="50" align="center">
            <template #default="{ row }">{{ row.chunkIndex }}</template>
          </el-table-column>
          <el-table-column prop="content" label="内容预览" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.content?.substring(0, 100) }}</template>
          </el-table-column>
          <el-table-column label="字符" width="70" align="center">
            <template #default="{ row }">{{ row.charCount }}</template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
                {{ row.status === 'ACTIVE' ? '活跃' : '已删' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="pagination.params.page"
            v-model:page-size="pagination.params.size"
            :total="pagination.total.value"
            :page-sizes="[10, 20, 50]"
            layout="total, prev, pager, next"
            @current-change="pagination.onPageChange"
            @size-change="pagination.onSizeChange"
            small
          />
        </div>
      </div>

      <!-- 右侧编辑区 -->
      <div class="chunk-edit-panel">
        <div v-if="!selectedChunk" class="edit-placeholder">
          选择左侧块以查看和编辑内容
        </div>
        <div v-else class="edit-area">
          <div class="edit-header">
            <span>块 #{{ selectedChunk.chunkIndex }} ({{ selectedChunk.charCount }} 字符)</span>
            <div v-if="mode !== 'read-only'" style="display: flex; gap: 4px;">
              <el-button size="small" type="primary" :loading="saving" @click="saveChunk">保存</el-button>
              <el-button
                v-if="selectedChunk.status === 'ACTIVE'"
                size="small"
                type="danger"
                @click="deleteChunk"
              >删除</el-button>
            </div>
          </div>
          <el-input
            v-model="editContent"
            type="textarea"
            :rows="20"
            resize="vertical"
            placeholder="块内容"
            :disabled="mode === 'read-only'"
          />
        </div>
      </div>
    </div>
  </div>

  <!-- 新增块弹窗 -->
  <el-dialog v-model="createVisible" title="新增块" width="600px" destroy-on-close>
    <el-form>
      <el-form-item label="块内容" required>
        <el-input v-model="newChunkContent" type="textarea" :rows="8" placeholder="请输入块内容" maxlength="5000" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createVisible = false">取消</el-button>
      <el-button type="primary" :loading="creating" @click="doCreateChunk">确认新增</el-button>
    </template>
  </el-dialog>

  <!-- 重新分块弹窗 -->
  <el-dialog v-model="rechunkVisible" title="重新分块" width="560px" destroy-on-close>
    <el-form label-width="90px">
      <el-form-item label="当前文档">
        <span>{{ docName }}</span>
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
</template>

<script setup lang="ts">
import { ref, reactive, watch, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, Search } from '@element-plus/icons-vue'
import { chunkApi, type ChunkVO, type ChunkStats } from '@/api/modules/chunks'
import { fileApi } from '@/api/modules/files'
import { reviewApi } from '@/api/modules/review'
import { usePagination } from '@/composables/usePagination'
import { DOCUMENT_STATUS_MAP } from '@/utils/constants'
import { CHUNK_STRATEGY_CONFIGS } from '@/api/types/file'
import type { StrategyField } from '@/api/types/file'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const docId = Number(route.params.id)

const docName = ref('')
const docStatus = ref('')
const chunkList = ref<ChunkVO[]>([])
const stats = ref<ChunkStats | null>(null)
const loading = ref(false)
const saving = ref(false)
const selectedChunk = ref<ChunkVO | null>(null)
const editContent = ref('')
const searchKeyword = ref('')
const createVisible = ref(false)
const newChunkContent = ref('')
const creating = ref(false)
const submittingReview = ref(false)
const reviewing = ref(false)

// 重新分块
const rechunkVisible = ref(false)
const rechunkForm = reactive({ chunkStrategy: 'semantic' })
const rechunkParams = reactive<Record<string, any>>({})
const rechunking = ref(false)

const rechunkFields = computed<StrategyField[]>(() =>
  CHUNK_STRATEGY_CONFIGS[rechunkForm.chunkStrategy]?.fields || []
)
const currentRechunkStrategyLabel = computed(() =>
  CHUNK_STRATEGY_CONFIGS[rechunkForm.chunkStrategy]?.label || ''
)

const pagination = usePagination()

const mode = computed(() => {
  if (docStatus.value === 'CHUNK_REVIEW') return 'chunk-edit'
  if (docStatus.value === 'PENDING_REVIEW') return 'content-review'
  return 'read-only'
})

const pageTitle = computed(() => {
  if (mode.value === 'chunk-edit') return `分块编辑 — ${docName.value}`
  if (mode.value === 'content-review') return `分块审核 — ${docName.value}`
  return `查看分块 — ${docName.value}`
})

function selectChunk(c: ChunkVO | null) {
  selectedChunk.value = c
  editContent.value = c?.content || ''
}

let searchTimer: ReturnType<typeof setTimeout> | null = null

async function fetchChunks() {
  loading.value = true
  try {
    const keyword = searchKeyword.value?.trim() || undefined
    const res = await chunkApi.list(docId, {
      page: pagination.params.page,
      size: pagination.params.size,
      keyword,
    })
    chunkList.value = res.records
    pagination.setTotal(res.total)
  } finally {
    loading.value = false
  }
}

function onSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    pagination.params.page = 1
    fetchChunks()
  }, 300)
}

function openCreateDialog() {
  newChunkContent.value = ''
  createVisible.value = true
}

async function doCreateChunk() {
  if (!newChunkContent.value.trim()) {
    ElMessage.warning('请输入块内容')
    return
  }
  creating.value = true
  try {
    await chunkApi.create(docId, newChunkContent.value.trim())
    ElMessage.success('块已新增')
    createVisible.value = false
    pagination.params.page = 1
    fetchChunks()
    fetchStats()
  } catch {
    ElMessage.error('新增失败')
  } finally {
    creating.value = false
  }
}

async function fetchStats() {
  try {
    await chunkApi.syncCount(docId)
    stats.value = await chunkApi.getStats(docId)
  } catch { /* ignore */ }
}

async function fetchDocInfo() {
  try {
    const doc = await fileApi.detail(docId)
    docName.value = doc.fileName
    docStatus.value = doc.status
  } catch { /* ignore */ }
}

async function saveChunk() {
  if (!selectedChunk.value) return
  saving.value = true
  try {
    const updated = await chunkApi.update(selectedChunk.value.id, editContent.value)
    ElMessage.success('保存成功')
    const idx = chunkList.value.findIndex(c => c.id === updated.id)
    if (idx >= 0) {
      chunkList.value[idx] = updated
    }
    selectedChunk.value = updated
    fetchStats()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function deleteChunk() {
  if (!selectedChunk.value) return
  try {
    await ElMessageBox.confirm('确定删除该块吗？', '确认', { type: 'warning' })
    await chunkApi.delete(selectedChunk.value.id)
    ElMessage.success('已删除')
    selectedChunk.value.status = 'DELETED'
    fetchChunks()
    fetchStats()
  } catch { /* cancelled */ }
}

async function doSubmitForReview() {
  if (!stats.value || stats.value.activeCount === 0) {
    ElMessage.warning('没有可提交的块')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认将 ${stats.value.activeCount} 个活跃块提交至审核吗？提交后不再支持编辑。`,
      '提交审核',
      { type: 'info', confirmButtonText: '确认提交', cancelButtonText: '取消' }
    )
    submittingReview.value = true
    await chunkApi.submitForReview(docId)
    ElMessage.success('已提交审核')
    fetchDocInfo()
  } catch { /* cancelled */ }
  finally { submittingReview.value = false }
}

async function doStartEmbedding() {
  if (!stats.value || stats.value.activeCount === 0) {
    ElMessage.warning('没有可向量化的块')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认对 ${stats.value.activeCount} 个活跃块发起向量化入库吗？`,
      '发起向量化',
      { type: 'info', confirmButtonText: '确认', cancelButtonText: '取消' }
    )
    await chunkApi.startEmbedding(docId)
    ElMessage.success('向量化任务已发起')
    fetchDocInfo()
  } catch { /* cancelled */ }
}

async function doApprove() {
  try {
    await ElMessageBox.confirm(
      '确认审核通过？通过后将发起向量化入库。',
      '审核通过',
      { type: 'success', confirmButtonText: '确认通过', cancelButtonText: '取消' }
    )
    reviewing.value = true
    await reviewApi.submit({ documentId: docId, result: 'APPROVED' })
    ElMessage.success('审核已通过')
    fetchDocInfo()
  } catch { /* cancelled */ }
  finally { reviewing.value = false }
}

async function doReject() {
  try {
    const { value: comment } = await ElMessageBox.prompt('请输入驳回原因', '驳回', {
      type: 'warning',
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消',
      inputValidator: (v: string) => v?.trim() ? true : '请输入驳回原因',
    })
    reviewing.value = true
    await reviewApi.submit({ documentId: docId, result: 'REJECTED', comment: comment || '' })
    ElMessage.success('已驳回')
    fetchDocInfo()
  } catch { /* cancelled */ }
  finally { reviewing.value = false }
}

// --- 重新分块 ---
function openRechunkDialog() {
  rechunkForm.chunkStrategy = 'semantic'
  initRechunkParams('semantic')
  rechunkVisible.value = true
}

function initRechunkParams(strategy: string) {
  const fields = CHUNK_STRATEGY_CONFIGS[strategy]?.fields || []
  const defaults: Record<string, any> = {}
  for (const f of fields) {
    defaults[f.key] = f.default
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
  rechunking.value = true
  try {
    const configJson = JSON.stringify({ ...rechunkParams })
    await fileApi.rechunk(docId, rechunkForm.chunkStrategy, configJson)
    ElMessage.success('重新分块任务已发起，请等待分块完成后刷新页面')
    rechunkVisible.value = false
    // 清空旧分块列表，刷新文档状态为"分块中"
    chunkList.value = []
    stats.value = null
    selectedChunk.value = null
    fetchDocInfo()
  } catch {
    ElMessage.error('重新分块失败')
  } finally {
    rechunking.value = false
  }
}

watch(pagination.params, () => fetchChunks(), { immediate: true })

onMounted(() => {
  fetchStats()
  fetchDocInfo()
})
</script>

<style scoped>
.stats-bar {
  display: flex;
  gap: 24px;
  padding: 10px 16px;
  margin-bottom: 16px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 13px;
  color: #606266;
}
.chunk-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.chunk-layout {
  display: flex;
  gap: 16px;
  flex: 1;
  min-height: 0;
}
.chunk-list-panel {
  flex: 1;
  min-width: 0;
}
.chunk-edit-panel {
  width: 480px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fff;
  display: flex;
  flex-direction: column;
}
.edit-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
}
.edit-area {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 12px;
}
.edit-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 13px;
  color: #606266;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
</style>
