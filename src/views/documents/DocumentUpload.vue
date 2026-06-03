<template>
  <div class="page-container">
    <div class="page-container__header">
      <h2>上传文档</h2>
    </div>

    <el-card>
      <!-- 知识库选择 -->
      <div class="kb-select-section">
        <span class="label">目标知识库：</span>
        <el-select v-model="selectedKbId" placeholder="请选择知识库" style="width: 300px" :loading="kbLoading">
          <el-option v-for="kb in kbList" :key="kb.id" :label="kb.kbName" :value="kb.id" />
        </el-select>
      </div>

      <!-- 分块策略选择 -->
      <div class="strategy-section">
        <span class="label">分块策略：</span>
        <el-select v-model="chunkStrategy" style="width: 220px" @change="onStrategyChange">
          <el-option v-for="[key, cfg] in availableStrategies" :key="key" :label="cfg.label" :value="key" />
        </el-select>
        <span class="strategy-hint">{{ currentStrategyHint }}</span>
      </div>

      <!-- 策略参数配置 -->
      <div class="params-section" v-if="currentFields.length > 0">
        <el-divider content-position="left">{{ currentStrategyLabel }} — 参数配置</el-divider>
        <el-form :model="params" label-width="180px" size="default">
          <el-form-item
            v-for="field in currentFields"
            :key="field.key"
            :label="field.label"
          >
            <el-input-number
              v-if="field.type === 'number'"
              v-model="params[field.key]"
              :min="field.min"
              :max="field.max"
              :step="field.step || 1"
              controls-position="right"
              style="width: 280px"
            />
            <el-input
              v-else
              v-model="params[field.key]"
              style="width: 400px"
              :placeholder="String(field.default)"
            />
            <template v-if="field.key === 'separators'">
              <span class="field-hint">高级选项，一般保持默认即可</span>
            </template>
          </el-form-item>
          <el-form-item>
            <el-button @click="resetParams" text type="primary">恢复默认值</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 上传区域 -->
      <el-upload
        ref="uploadRef"
        class="upload-area"
        drag
        multiple
        :auto-upload="false"
        :before-upload="beforeUpload"
        :on-change="handleFileChange"
        :on-remove="handleRemove"
        :file-list="fileList"
        accept=".pdf,.doc,.docx,.txt,.md,.xls,.xlsx,.ppt,.pptx,.csv"
      >
        <el-icon class="el-icon--upload" :size="48"><UploadFilled /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">
            支持 PDF、Word、Excel、PPT、TXT、Markdown、CSV 格式，单文件最大 100MB
          </div>
        </template>
      </el-upload>

      <!-- 操作按钮 -->
      <div class="upload-actions">
        <el-button type="primary" :loading="uploading" :disabled="!canUpload" @click="startUpload">
          <el-icon><Upload /></el-icon> 开始上传 ({{ fileList.length }} 个文件)
        </el-button>
        <el-button @click="clearFiles">清空列表</el-button>
      </div>

      <!-- 上传结果 -->
      <div v-if="results.length > 0" class="upload-results">
        <h3>上传结果</h3>
        <div v-for="(r, i) in results" :key="i" class="result-item">
          <el-icon v-if="r.success" color="#67c23a"><SuccessFilled /></el-icon>
          <el-icon v-else color="#f56c6c"><CircleCloseFilled /></el-icon>
          <span class="result-name">{{ r.fileName }}</span>
          <span v-if="r.success" class="result-status success">上传成功</span>
          <span v-else class="result-status fail">{{ r.error }}</span>
          <span v-if="r.success" class="result-progress">{{ r.progress }}%</span>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, reactive } from 'vue'
import { Upload, UploadFilled, SuccessFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import { fileApi } from '@/api/modules/files'
import { knowledgeBaseApi } from '@/api/modules/knowledgeBase'
import type { KnowledgeBaseVO } from '@/api/types/knowledgeBase'
import { CHUNK_STRATEGY_CONFIGS } from '@/api/types/file'
import type { StrategyField } from '@/api/types/file'
import request from '@/api/request'
import type { UploadFile, UploadInstance } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'

const uploadRef = ref<UploadInstance>()
const selectedKbId = ref<number | null>(null)
const chunkStrategy = ref('semantic')
const kbList = ref<KnowledgeBaseVO[]>([])
const kbLoading = ref(false)
const fileList = ref<UploadFile[]>([])
const uploading = ref(false)
interface UploadResult { fileName: string; success: boolean; progress: number; error?: string }
const results = ref<UploadResult[]>([])

// Dynamic params
const params = reactive<Record<string, any>>({})

const currentFields = computed<StrategyField[]>(() =>
  CHUNK_STRATEGY_CONFIGS[chunkStrategy.value]?.fields || []
)

const currentStrategyLabel = computed(() =>
  CHUNK_STRATEGY_CONFIGS[chunkStrategy.value]?.label || ''
)

const currentStrategyHint = computed(() => {
  const hints: Record<string, string> = {
    fixed: '按固定字符数分割，适合结构一致的文档',
    hierarchical: '按Markdown标题(# ##)层级分割，适合结构化文档',
    recursive: '递归按分隔符层级分割，适合通用文本',
    semantic: '按段落语义分割，合并短段落，适合文章/论文',
    topic: '按主题相似度自动检测话题切换，适合长文档/书籍',
    hybrid: '先按结构粗分，超阈值再精细分块，适合混合格式文档',
  }
  return hints[chunkStrategy.value] || ''
})

function initParams(strategy: string) {
  const fields = CHUNK_STRATEGY_CONFIGS[strategy]?.fields || []
  const defaults: Record<string, any> = {}
  for (const f of fields) {
    defaults[f.key] = f.default
  }
  // Clear and reassign
  Object.keys(params).forEach(k => delete params[k])
  Object.assign(params, defaults)
}

function onStrategyChange(strategy: string) {
  initParams(strategy)
}

function resetParams() {
  initParams(chunkStrategy.value)
}

// 已启用的分块策略
const enabledStrategies = ref<Set<string>>(new Set(Object.keys(CHUNK_STRATEGY_CONFIGS))) // 默认全部启用
const availableStrategies = computed(() => {
  const entries = Object.entries(CHUNK_STRATEGY_CONFIGS).filter(([k]) => enabledStrategies.value.has(k))
  if (!entries.find(([k]) => k === chunkStrategy.value)) chunkStrategy.value = entries[0]?.[0] || 'semantic'
  return entries
})

const canUpload = computed(() => selectedKbId.value && fileList.value.length > 0)

onMounted(async () => {
  // 读取启用的策略
  try {
    const res: any = await request.post('/user/configs/effective')
    const enabled: string[] = (res || []).filter((r: any) => r.configKey?.startsWith('chunk.') && r.configKey?.endsWith('.enabled') && r.configValue === 'true').map((r: any) => r.configKey.split('.')[1])
    if (enabled.length > 0) enabledStrategies.value = new Set(enabled)
  } catch { /* keep all enabled by default */ }

  kbLoading.value = true
  try {
    const res = await knowledgeBaseApi.list({ page: 1, size: 100 })
    kbList.value = res.records.filter(kb => kb.status === 1)
  } finally {
    kbLoading.value = false
  }
  initParams(chunkStrategy.value)
})

function beforeUpload(file: File) {
  const maxSize = 100 * 1024 * 1024
  if (file.size > maxSize) {
    ElMessage.error(`文件 ${file.name} 超过100MB限制`)
    return false
  }
  return true
}

function handleFileChange(_file: UploadFile, fileList_: UploadFile[]) {
  fileList.value = fileList_
}

function handleRemove() {
  results.value = []
}

function clearFiles() {
  uploadRef.value?.clearFiles()
  fileList.value = []
  results.value = []
}

async function startUpload() {
  if (!selectedKbId.value) {
    ElMessage.warning('请先选择目标知识库')
    return
  }
  if (fileList.value.length === 0) {
    ElMessage.warning('请先选择文件')
    return
  }

  const confirmed = await ElMessageBox.confirm(
    `确定要上传 ${fileList.value.length} 个文件吗？`,
    '确认上传',
    { confirmButtonText: '确定', cancelButtonText: '取消', type: 'info' }
  ).catch(() => false)
  if (!confirmed) return

  uploading.value = true
  results.value = []

  const configJson = JSON.stringify({ ...params })

  for (const file of fileList.value) {
    if (!file.raw) continue
    const result: UploadResult = { fileName: file.name, success: false, progress: 0 }
    results.value.push(result)
    try {
      await fileApi.upload(
        file.raw,
        selectedKbId.value,
        (pct) => { result.progress = pct },
        chunkStrategy.value,
        configJson,
      )
      result.success = true
      result.progress = 100
    } catch (e: any) {
      result.error = e.message || '上传失败'
    }
  }

  uploading.value = false
  ElMessage.success('所有文件上传完成')
}
</script>

<style scoped>
.kb-select-section {
  margin-bottom: 20px;
  display: flex;
  align-items: center;
}
.kb-select-section .label {
  margin-right: 12px;
  font-weight: 500;
}
.strategy-section {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
}
.strategy-section .label {
  margin-right: 12px;
  font-weight: 500;
}
.strategy-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}
.params-section {
  margin-bottom: 20px;
  padding: 8px 16px;
  background: #fafafa;
  border-radius: 4px;
}
.field-hint {
  margin-left: 8px;
  font-size: 12px;
  color: #c0c4cc;
}
.upload-area {
  margin-bottom: 16px;
}
.upload-actions {
  margin-bottom: 20px;
  display: flex;
  gap: 12px;
}
.upload-results h3 {
  font-size: 15px;
  margin-bottom: 12px;
}
.result-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px solid #f0f0f0;
}
.result-name {
  flex: 1;
}
.result-status.success {
  color: #67c23a;
}
.result-status.fail {
  color: #f56c6c;
}
.result-progress {
  color: #909399;
  font-size: 13px;
}
</style>
