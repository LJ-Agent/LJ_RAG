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
        <el-select v-model="chunkStrategy" style="width: 200px">
          <el-option label="语义分块 (semantic)" value="semantic" />
          <el-option label="固定大小 (fixed)" value="fixed" />
          <el-option label="层级分块 (hierarchical)" value="hierarchical" />
        </el-select>
        <span class="strategy-hint">语义分块按段落分割，固定大小按字符数分割，层级分块按Markdown标题分割</span>
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
import { ref, computed, onMounted } from 'vue'
import { Upload, UploadFilled, SuccessFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import { fileApi } from '@/api/modules/files'
import { knowledgeBaseApi } from '@/api/modules/knowledgeBase'
import type { KnowledgeBaseVO } from '@/api/types/knowledgeBase'
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

const canUpload = computed(() => selectedKbId.value && fileList.value.length > 0)

onMounted(async () => {
  kbLoading.value = true
  try {
    const res = await knowledgeBaseApi.list({ page: 1, size: 100 })
    kbList.value = res.records.filter(kb => kb.status === 1)
  } finally {
    kbLoading.value = false
  }
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

  for (const file of fileList.value) {
    if (!file.raw) continue
    const result: UploadResult = { fileName: file.name, success: false, progress: 0 }
    results.value.push(result)
    try {
      await fileApi.upload(file.raw, selectedKbId.value, (pct) => {
        result.progress = pct
      }, chunkStrategy.value)
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
  margin-bottom: 20px;
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
