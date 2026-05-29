<template>
  <div class="qa-layout">
    <!-- 左侧会话列表 -->
    <div class="qa-sidebar">
      <div class="sidebar-header">
        <el-button type="primary" size="small" @click="createNewSession" style="width: 100%">
          <el-icon><Plus /></el-icon> 新建会话
        </el-button>
        <div v-if="sortedSessions.length > 0" class="session-select-all">
          <el-checkbox
            :model-value="isAllSelected"
            :indeterminate="isIndeterminate"
            @change="toggleSelectAll"
          >全选</el-checkbox>
          <el-button
            v-if="selectedSessionIds.length > 0"
            type="danger" size="small"
            @click="handleBatchDeleteSessions"
          >
            <el-icon><Delete /></el-icon> 批量删除 ({{ selectedSessionIds.length }})
          </el-button>
        </div>
      </div>
      <div class="session-list">
        <div
          v-for="s in sortedSessions"
          :key="s.id"
          :class="['session-item', { active: activeSessionId === s.id }]"
        >
          <el-checkbox
            :model-value="selectedSessionIds.includes(s.id)"
            class="session-checkbox"
            @change="(val: any) => toggleSessionSelect(s.id, val)"
            @click.stop
          />
          <div class="session-info" @click="switchSession(s.id)">
            <span class="session-title">{{ s.title }}</span>
            <span class="session-meta">{{ s.messageCount }} 条 · {{ formatTime(s.createdAt) }}</span>
          </div>
          <span
            :class="['session-star', { starred: pinnedId === s.id }]"
            @click.stop="togglePin(s.id)"
            title="置顶会话"
          >
            <el-icon><StarFilled v-if="pinnedId === s.id" /><Star v-else /></el-icon>
          </span>
          <el-dropdown trigger="click" @command="(cmd: string) => handleSessionAction(cmd, s)">
            <span class="session-more" @click.stop><el-icon><MoreFilled /></el-icon></span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="rename">重命名</el-dropdown-item>
                <el-dropdown-item command="delete" divided style="color: #f56c6c">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <el-empty v-if="sortedSessions.length === 0" description="暂无会话" :image-size="60" />
      </div>
    </div>

    <!-- 右侧聊天区域 -->
    <div class="qa-main">
      <!-- 知识库选择 -->
      <div class="qa-kb-selector">
        <span style="font-weight: 500; white-space: nowrap">选择知识库：</span>
        <el-checkbox-group v-model="selectedKbIds" size="small">
          <el-checkbox v-for="kb in kbList" :key="kb.id" :value="kb.id" :label="kb.kbName" border />
        </el-checkbox-group>
        <span v-if="selectedKbIds.length === 0" style="font-size: 12px; color: #e6a23c;">请选择一个知识库</span>
      </div>

      <!-- 消息区域 -->
      <div class="qa-messages" ref="messagesRef">
        <div v-if="messages.length === 0 && !isStreaming" style="text-align: center; margin-top: 20vh; color: #909399;">
          <el-icon :size="48"><ChatDotRound /></el-icon>
          <p style="margin-top: 12px;">选择一个知识库，开始提问吧</p>
        </div>

        <template v-for="(msg, idx) in messages" :key="idx">
          <!-- 流式输出期间跳过最后一条bot消息，由streaming区域独立展示避免重复 -->
          <div v-if="!(isStreaming && msg.role === 'bot' && idx === messages.length - 1)" :class="['qa-message', msg.role]">
            <div class="qa-message__bubble">
            <div v-if="msg.role === 'user'">{{ msg.content }}</div>
            <div v-else>
              <!-- 思考过程（浅灰色） -->
              <div v-if="msg.thinking" class="thinking-display">
                {{ msg.thinking }}
              </div>
              <!-- 回答内容 -->
              <div v-html="renderMarkdown(msg.content)" class="markdown-body"></div>
            </div>

            <!-- 回答依据 -->
            <div v-if="msg.sourceDocs && msg.sourceDocs.length > 0" class="source-docs">
              <div class="source-docs-header">
                <el-icon><Document /></el-icon>
                <span>回答依据</span>
                <span class="retrieval-summary">
                  共 {{ msg.sourceDocs.length }} 个片段 · 耗时 {{ msg.latencyMs || '--' }}ms · {{ msg.tokenCount || '--' }} tokens
                </span>
              </div>
              <div
                v-for="(doc, di) in msg.sourceDocs"
                :key="doc.chunkId"
                class="source-doc-item"
                @click="openChunkDetail(doc.chunkId, doc.documentId, doc.content)"
                title="点击查看块详情及原文对照"
              >
                <div class="source-doc-header">
                  <span class="source-doc-name">
                    <el-tag size="small" type="primary">{{ di + 1 }}</el-tag>
                    <a class="source-doc-link" @click.stop="openRawFileById(doc.documentId)" :title="'打开原文件: ' + (doc.documentName || '')">{{ doc.documentName || '文档#' + doc.documentId }}</a>
                    <span class="source-doc-chunk">Chunk #{{ doc.chunkIndex }}</span>
                  </span>
                  <el-tag size="small" :type="doc.score > 0.7 ? 'success' : doc.score > 0.4 ? 'warning' : 'info'">
                    相似度: {{ (doc.score * 100).toFixed(1) }}%
                  </el-tag>
                </div>
                <p class="source-doc-content">{{ doc.content }}</p>
              </div>
            </div>
          </div>
        </div>
        </template>

        <!-- 流式输出 -->
        <div v-if="isStreaming" class="qa-message bot">
          <div class="qa-message__bubble">
            <div class="streaming-block">
              <div class="streaming-header">
                <span class="streaming-dot"></span>
                <span>{{ phaseLabel }}</span>
              </div>
              <!-- 错误提示 -->
              <div v-if="error" class="streaming-error">
                {{ error }}
              </div>
              <!-- 思考过程（浅灰色） -->
              <div v-if="streamThinking" class="streaming-thinking">
                {{ streamThinking }}
              </div>
              <!-- 回答内容 — 流式阶段用纯文本避免 Markdown 部分渲染乱码 -->
              <div v-if="streamContent" class="streaming-content">{{ streamContent }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="qa-input-area">
        <el-input
          v-model="question"
          type="textarea"
          :rows="2"
          placeholder="输入问题，Enter发送，Shift+Enter换行"
          :disabled="isStreaming"
          resize="none"
          @keydown.enter.exact.prevent="handleSend"
        />
        <el-button type="primary" :loading="isStreaming" :disabled="!canSend" @click="handleSend">
          <el-icon><Promotion /></el-icon>
        </el-button>
      </div>
    </div>
  </div>

  <!-- 块详情弹窗（含原文对照） -->
  <el-dialog v-model="chunkDetailVisible" :title="'块详情 — ' + chunkDocName" width="960px" destroy-on-close top="3vh" @opened="scrollToHighlight">
    <template v-if="chunkDetailLoading">
      <el-skeleton :rows="10" animated />
    </template>
    <template v-else-if="chunkDetail">
      <!-- 块元数据 -->
      <el-descriptions :column="4" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="文档">{{ chunkDocName }}</el-descriptions-item>
        <el-descriptions-item label="块序号">#{{ chunkDetail.chunkIndex }}</el-descriptions-item>
        <el-descriptions-item label="字符数">{{ chunkDetail.charCount }}</el-descriptions-item>
        <el-descriptions-item label="层级">{{ chunkDetail.level }}</el-descriptions-item>
        <el-descriptions-item label="数据库ID">{{ chunkDetail.id }}</el-descriptions-item>
        <el-descriptions-item label="业务ID">{{ chunkDetail.chunkId }}</el-descriptions-item>
        <el-descriptions-item label="父块ID">{{ chunkDetail.parentId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="chunkDetail.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
            {{ chunkDetail.status === 'ACTIVE' ? '活跃' : '已删除' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <!-- 块内容 -->
      <div class="chunk-detail-section">
        <div class="chunk-detail-section-title">
          <el-icon><Collection /></el-icon> 块内容（Markdown）
        </div>
        <div class="chunk-detail-content markdown-body" v-html="renderMarkdown(chunkDetail.content || '')"></div>
      </div>

      <!-- 原文对照 -->
      <div class="chunk-detail-section" v-if="rawContent">
        <div class="chunk-detail-section-title">
          <el-icon><Document /></el-icon> 原文对照 · 标黄处为块对应内容
        </div>
        <div ref="docContentRef" class="chunk-doc-content">
          <pre class="doc-text" v-html="highlightedDocContent"></pre>
        </div>
      </div>
      <div v-else-if="!chunkDetailLoading && rawContentError" class="chunk-doc-empty">
        {{ rawContentError }}
      </div>
    </template>
    <template #footer>
      <el-button @click="chunkDetailVisible = false">关闭</el-button>
      <el-button type="primary" @click="openRawFileById(currentDocId)">
        <el-icon><Document /></el-icon> 查看原文件
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ChatDotRound, Plus, MoreFilled, Promotion, Document, Collection, Star, StarFilled, Delete } from '@element-plus/icons-vue'
import { qaApi } from '@/api/modules/qa'
import { chunkApi, type ChunkVO } from '@/api/modules/chunks'
import { fileApi } from '@/api/modules/files'
import { knowledgeBaseApi } from '@/api/modules/knowledgeBase'
import { useSSE } from '@/composables/useSSE'
import type { StreamMetadata } from '@/composables/useSSE'
import { sanitizeHtml } from '@/utils/sanitize'
import { getAccessToken } from '@/utils/token'
import { marked } from 'marked'
import type { KnowledgeBaseVO } from '@/api/types/knowledgeBase'
import type { SourceDoc, ChatSessionVO } from '@/api/types/qa'
import { ElMessage, ElMessageBox } from 'element-plus'

interface ChatMessage {
  role: 'user' | 'bot'
  content: string
  thinking?: string
  sourceDocs?: SourceDoc[]
  tokenCount?: number
  latencyMs?: number
}

const STORAGE_KEY = 'rag-pinned-session'

const kbList = ref<KnowledgeBaseVO[]>([])
const selectedKbIds = ref<number[]>([])
const question = ref('')
const messages = ref<ChatMessage[]>([])
const messagesRef = ref<HTMLElement>()

const sessions = ref<ChatSessionVO[]>([])
const activeSessionId = ref<number | null>(null)
const pinnedId = ref<number | null>(loadPinnedId())
const selectedSessionIds = ref<number[]>([])

// 块详情弹窗
const chunkDetailVisible = ref(false)
const chunkDetailLoading = ref(false)
const chunkDetail = ref<ChunkVO | null>(null)
const chunkDocName = ref('')
const currentDocId = ref(0)
const rawContent = ref('')
const rawContentError = ref('')
const docContentRef = ref<HTMLElement>()
const highlightedDocContent = ref('')

const { isStreaming, streamPhase, streamContent, streamThinking, streamSourceDocs, error, startStream } = useSSE()
const router = useRouter()

const canSend = computed(() => question.value.trim() && selectedKbIds.value.length > 0 && !isStreaming.value)

const phaseLabel = computed(() => {
  const labels: Record<string, string> = {
    retrieving: '正在检索相关知识...',
    thinking: 'AI 正在思考...',
    reasoning: 'AI 正在深度推理...',
    generating: '正在生成回答...',
  }
  return labels[streamPhase.value] || '处理中...'
})

const sortedSessions = computed(() => {
  if (!pinnedId.value) return sessions.value
  return [...sessions.value].sort((a, b) => {
    if (a.id === pinnedId.value) return -1
    if (b.id === pinnedId.value) return 1
    return 0
  })
})

function loadPinnedId(): number | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? Number(raw) : null
  } catch {
    return null
  }
}

function savePinnedId(id: number | null) {
  if (id) {
    localStorage.setItem(STORAGE_KEY, String(id))
  } else {
    localStorage.removeItem(STORAGE_KEY)
  }
}

function togglePin(id: number) {
  if (pinnedId.value === id) {
    pinnedId.value = null
    savePinnedId(null)
  } else {
    pinnedId.value = id
    savePinnedId(id)
  }
}

const isAllSelected = computed(() =>
  sortedSessions.value.length > 0 && selectedSessionIds.value.length === sortedSessions.value.length
)
const isIndeterminate = computed(() =>
  selectedSessionIds.value.length > 0 && selectedSessionIds.value.length < sortedSessions.value.length
)

function toggleSelectAll() {
  if (isAllSelected.value) {
    selectedSessionIds.value = []
  } else {
    selectedSessionIds.value = sortedSessions.value.map(s => s.id)
  }
}

function toggleSessionSelect(id: number, val: boolean) {
  if (val) {
    selectedSessionIds.value.push(id)
  } else {
    selectedSessionIds.value = selectedSessionIds.value.filter(v => v !== id)
  }
}

function formatTime(dateStr: string): string {
  if (!dateStr) return ''
  // 尝试兼容多种日期格式，确保按本地时间解析
  let d = new Date(dateStr)
  if (isNaN(d.getTime())) {
    // 尝试替换空格为T（兼容 "2026-05-29 14:30:00" 格式）
    d = new Date(dateStr.replace(' ', 'T'))
  }
  if (isNaN(d.getTime())) return dateStr
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  const pad = (n: number) => String(n).padStart(2, '0')
  const timeStr = `${pad(d.getHours())}:${pad(d.getMinutes())}`

  if (diff < 60000) return '刚刚'
  if (diff < 1800000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 21600000) return Math.floor(diff / 3600000) + '小时前'
  // 今天之内：显示具体时间
  if (d.getFullYear() === now.getFullYear() &&
      d.getMonth() === now.getMonth() &&
      d.getDate() === now.getDate()) {
    return '今天 ' + timeStr
  }
  // 昨天
  const yesterday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1)
  if (d.getFullYear() === yesterday.getFullYear() &&
      d.getMonth() === yesterday.getMonth() &&
      d.getDate() === yesterday.getDate()) {
    return '昨天 ' + timeStr
  }
  if (diff < 604800000) return Math.floor(diff / 86400000) + '天前'
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function renderMarkdown(text: string): string {
  const html = marked.parse(text || '') as string
  return sanitizeHtml(html)
}

function openRawFileById(documentId: number) {
  const token = getAccessToken()
  const url = `${import.meta.env.VITE_API_BASE_URL}/files/${documentId}/raw?token=${encodeURIComponent(token || '')}`
  window.open(url, '_blank')
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function goToChunkDetail(chunkId: string) {
  router.push(`/chunks/${encodeURIComponent(chunkId)}/detail`)
}

async function openChunkDetail(chunkId: string, documentId: number, chunkContent: string) {
  chunkDetailVisible.value = true
  chunkDetailLoading.value = true
  chunkDetail.value = null
  chunkDocName.value = ''
  currentDocId.value = documentId
  rawContent.value = ''
  rawContentError.value = ''
  highlightedDocContent.value = ''

  try {
    const [chunk, docInfo] = await Promise.all([
      chunkApi.getByChunkId(chunkId),
      fileApi.detail(documentId).catch(() => null),
    ])
    chunkDetail.value = chunk
    chunkDocName.value = docInfo?.fileName || `文档#${documentId}`

    // 加载原文件内容
    try {
      const text = await fileApi.getRawContent(documentId)
      // 检测是否为可读文本（排除明显的二进制内容）
      const printable = text.replace(/[\x20-\x7E一-鿿　-〿＀-￯\n\r\t]/g, '')
      if (printable.length > text.length * 0.3) {
        // 二进制文件，无法直接展示文本
        rawContentError.value = '原文件为二进制格式（PDF/Word/PPT等），无法在此直接展示文本对照。请点击"查看原文件"按钮打开原文件，或前往分块详情页查看。'
      } else {
        rawContent.value = text
        highlightedDocContent.value = buildHighlightedDoc(text, chunkContent)
      }
    } catch {
      rawContentError.value = '无法加载原文件内容'
    }
  } catch {
    rawContentError.value = '加载块详情失败'
  } finally {
    chunkDetailLoading.value = false
  }
}

function buildHighlightedDoc(docText: string, chunkText: string): string {
  const trimmed = chunkText.trim()
  if (!trimmed) return escapeHtml(docText)

  // 多级匹配策略：完整匹配 → 首200字符 → 首100字符 → 首句
  const strategies = [
    trimmed,
    trimmed.substring(0, Math.min(200, trimmed.length)),
    trimmed.substring(0, Math.min(100, trimmed.length)),
    trimmed.split(/[。！？\n]/)[0]?.trim(),
  ].filter(s => s && s.length >= 10)

  for (const search of strategies) {
    const idx = docText.indexOf(search!)
    if (idx !== -1) {
      const endIdx = idx + trimmed.length
      const before = escapeHtml(docText.substring(0, idx))
      const match = escapeHtml(docText.substring(idx, Math.min(endIdx, docText.length)))
      const after = escapeHtml(docText.substring(Math.min(endIdx, docText.length)))
      return before + '<mark class="chunk-highlight" id="chunk-highlight-anchor">' + match + '</mark>' + after
    }
  }

  return '<p style="color:#909399;margin-bottom:8px;">（原文件与清洗后文本差异较大，无法精确定位块内容）</p>' + escapeHtml(docText)
}

function scrollToHighlight() {
  // 等待 DOM 更新后滚动到高亮位置
  setTimeout(() => {
    const el = document.getElementById('chunk-highlight-anchor')
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }
  }, 100)
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

async function loadSessions() {
  try {
    const res = await qaApi.getSessions({ page: 1, size: 50 })
    sessions.value = res.records
  } catch { /* ignore */ }
}

async function createNewSession() {
  try {
    const kbIds = selectedKbIds.value.join(',')
    const s = await qaApi.createSession({ kbIds, title: '新会话' })
    await loadSessions()
    switchSession(s.id)
  } catch {
    ElMessage.error('创建会话失败')
  }
}

async function switchSession(sessionId: number) {
  activeSessionId.value = sessionId
  messages.value = []
  try {
    const res = await qaApi.getSessionRecords(sessionId, { page: 1, size: 100 })
    if (res.records.length > 0) {
      const msgs: ChatMessage[] = []
      for (const r of res.records) {
        msgs.push({ role: 'user', content: r.question })
        let sourceDocs: SourceDoc[] = []
        try {
          if (r.sourceDocs) {
            sourceDocs = typeof r.sourceDocs === 'string'
              ? JSON.parse(r.sourceDocs)
              : r.sourceDocs
          }
        } catch { /* ignore */ }
        msgs.push({
          role: 'bot',
          content: r.answer,
          sourceDocs,
          latencyMs: r.latencyMs,
        })
      }
      messages.value = msgs
    }
    scrollToBottom()
  } catch {
    ElMessage.error('加载会话记录失败')
  }
}

async function handleSessionAction(cmd: string, session: ChatSessionVO) {
  if (cmd === 'rename') {
    try {
      const { value } = await ElMessageBox.prompt('请输入新标题', '重命名', {
        inputValue: session.title,
        confirmButtonText: '确定',
        cancelButtonText: '取消',
      })
      if (value) {
        await qaApi.updateSession(session.id, { title: value.trim() })
        await loadSessions()
      }
    } catch { /* cancelled */ }
  } else if (cmd === 'delete') {
    try {
      await ElMessageBox.confirm('确定删除该会话及其所有问答记录吗？', '删除确认', {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      })
      await qaApi.deleteSession(session.id)
      ElMessage.success('会话已删除')
      if (activeSessionId.value === session.id) {
        activeSessionId.value = null
        messages.value = []
      }
      if (pinnedId.value === session.id) {
        pinnedId.value = null
        savePinnedId(null)
      }
      selectedSessionIds.value = selectedSessionIds.value.filter(v => v !== session.id)
      await loadSessions()
    } catch { /* cancelled */ }
  }
}

async function handleBatchDeleteSessions() {
  if (selectedSessionIds.value.length === 0) return
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedSessionIds.value.length} 个会话吗？`,
      '批量删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await qaApi.batchDeleteSessions(selectedSessionIds.value)
    ElMessage.success(`已删除 ${selectedSessionIds.value.length} 个会话`)
    if (activeSessionId.value && selectedSessionIds.value.includes(activeSessionId.value)) {
      activeSessionId.value = null
      messages.value = []
    }
    if (pinnedId.value && selectedSessionIds.value.includes(pinnedId.value)) {
      pinnedId.value = null
      savePinnedId(null)
    }
    selectedSessionIds.value = []
    await loadSessions()
  } catch { /* cancelled */ }
}

async function ensureSession(): Promise<number | null> {
  if (activeSessionId.value) return activeSessionId.value
  const kbIds = selectedKbIds.value.join(',')
  const s = await qaApi.createSession({ kbIds, title: '新会话' })
  await loadSessions()
  activeSessionId.value = s.id
  return s.id
}

function handleStreamDone(botMsg: ChatMessage, meta: StreamMetadata) {
  botMsg.tokenCount = meta.tokenCount
  botMsg.latencyMs = meta.latencyMs
  botMsg.thinking = meta.thinking || undefined
  botMsg.sourceDocs = streamSourceDocs.value.length > 0 ? streamSourceDocs.value : (meta.sourceDocs || [])
  if (!botMsg.content && !botMsg.thinking) {
    botMsg.content = '[未获取到回答]'
  }
  scrollToBottom()
  loadSessions()
}

async function handleSend() {
  if (!canSend.value) {
    if (selectedKbIds.value.length === 0) ElMessage.warning('请先选择知识库')
    return
  }

  const q = question.value.trim()
  question.value = ''

  messages.value.push({ role: 'user', content: q })
  scrollToBottom()

  try {
    const sessionId = await ensureSession()
    // 新会话首次提问时，自动将标题设为问题前15字+省略号
    const session = sessions.value.find(s => s.id === sessionId)
    if (session && session.title === '新会话' && session.messageCount === 0) {
      const shortTitle = q.length > 7 ? q.substring(0, 7) + '...' : q
      try {
        await qaApi.updateSession(sessionId!, { title: shortTitle })
        session.title = shortTitle
      } catch { /* ignore */ }
    }
    const dto = {
      question: q,
      kbIds: selectedKbIds.value,
      sessionId: sessionId || undefined,
      topK: 5,
      scoreThreshold: 0.3,
    }

    const botMsg: ChatMessage = { role: 'bot', content: '' }
    messages.value.push(botMsg)

    await startStream(
      dto,
      (thinking) => {
        botMsg.thinking = (botMsg.thinking || '') + thinking
        scrollToBottom()
      },
      (chunk) => {
        botMsg.content += chunk
        scrollToBottom()
      },
      (meta) => {
        handleStreamDone(botMsg, meta)
      }
    )
  } catch {
    messages.value.push({ role: 'bot', content: '[请求失败，请重试]' })
    scrollToBottom()
  }
}

onMounted(async () => {
  try {
    const res = await knowledgeBaseApi.list({ page: 1, size: 100 })
    kbList.value = res.records.filter((kb) => kb.status === 1)
    await loadSessions()
  } catch { /* ignore */ }
})
</script>

<style scoped>
.qa-layout {
  display: flex;
  height: calc(100vh - 96px);
  background: #fff;
}

/* 侧边栏 */
.qa-sidebar {
  width: 260px;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  background: #fafafa;
}
.sidebar-header {
  padding: 12px;
  border-bottom: 1px solid #e4e7ed;
}
.session-select-all {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
  gap: 6px;
}
.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
}
.session-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  cursor: pointer;
  transition: background 0.2s;
}
.session-item:hover { background: #ecf5ff; }
.session-item.active { background: #d9ecff; }
.session-checkbox {
  margin-right: 6px;
}
.session-info {
  flex: 1;
  min-width: 0;
}
.session-title {
  display: block;
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.session-meta {
  font-size: 12px;
  color: #909399;
}
.session-star {
  cursor: pointer;
  padding: 2px;
  color: #c0c4cc;
  font-size: 16px;
  transition: color 0.2s;
  margin-right: 2px;
}
.session-star:hover { color: #e6a23c; }
.session-star.starred { color: #e6a23c; }
.session-more {
  opacity: 0;
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 4px;
}
.session-item:hover .session-more { opacity: 1; }
.session-more:hover { background: #c6e2ff; }

/* 主区域 */
.qa-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.qa-kb-selector {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
  flex-wrap: wrap;
}
.qa-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}
.qa-message {
  display: flex;
  margin-bottom: 16px;
}
.qa-message.bot { justify-content: flex-start; }
.qa-message.user { justify-content: flex-end; }
.qa-message__bubble {
  max-width: 80%;
  padding: 10px 16px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.7;
}
.qa-message.user .qa-message__bubble {
  background: #409eff;
  color: #fff;
}
.qa-message.bot .qa-message__bubble {
  background: #f4f4f5;
}
.qa-input-area {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid #e4e7ed;
  align-items: flex-end;
}

.markdown-body {
  line-height: 1.7;
}
.markdown-body :deep(p) { margin: 0.5em 0; }
.markdown-body :deep(pre) { background: #282c34; color: #abb2bf; padding: 12px; border-radius: 4px; overflow-x: auto; font-size: 13px; }
.markdown-body :deep(code) { background: rgba(0,0,0,0.06); padding: 2px 6px; border-radius: 3px; font-size: 13px; }
.markdown-body :deep(pre code) { background: transparent; padding: 0; }
.markdown-body :deep(table) { border-collapse: collapse; margin: 8px 0; }
.markdown-body :deep(th), .markdown-body :deep(td) { border: 1px solid #ddd; padding: 8px 12px; }
.markdown-body :deep(th) { background: #f5f7fa; }

/* 思考过程（浅灰色） */
.thinking-display {
  font-size: 13px;
  color: #b0b3bb;
  line-height: 1.7;
  white-space: pre-wrap;
  margin-bottom: 12px;
  padding: 8px 12px;
  background: #f9f9fb;
  border-radius: 6px;
  border-left: 2px solid #dcdfe6;
}

/* 回答依据 */
.source-docs {
  margin-top: 16px;
  border-top: 2px solid #409eff;
  padding-top: 12px;
}
.source-docs-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 14px;
  color: #303133;
  margin-bottom: 10px;
}
.retrieval-summary {
  font-weight: 400;
  font-size: 12px;
  color: #909399;
  margin-left: auto;
}
.source-doc-item {
  padding: 10px 12px;
  margin-bottom: 8px;
  background: #fafafa;
  border-radius: 6px;
  border-left: 3px solid #409eff;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s;
}
.source-doc-item:hover {
  background: #ecf5ff;
  border-left-color: #337ecc;
}
.source-doc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.source-doc-name {
  font-weight: 500;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.source-doc-chunk {
  font-size: 12px;
  color: #909399;
  font-weight: 400;
}
.source-doc-link {
  color: #409eff;
  cursor: pointer;
  text-decoration: none;
}
.source-doc-link:hover {
  text-decoration: underline;
}
.source-doc-content {
  font-size: 13px;
  color: #606266;
  margin: 4px 0 0;
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.6;
}

/* 流式输出 */
.streaming-block {
  min-width: 200px;
}
.streaming-header {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #909399;
  font-size: 13px;
  margin-bottom: 8px;
}
.streaming-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #409eff;
  animation: blink 1.4s infinite;
}
@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.2; }
}

/* 流式思考过程 — 浅灰色 */
.streaming-thinking {
  font-size: 13px;
  color: #b0b3bb;
  line-height: 1.7;
  white-space: pre-wrap;
  margin-bottom: 10px;
  padding: 8px 12px;
  background: #f9f9fb;
  border-radius: 6px;
  border-left: 2px solid #dcdfe6;
}

/* 流式回答内容 — 纯文本，避免 Markdown 部分渲染乱码 */
.streaming-content {
  font-size: 14px;
  color: #303133;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 流式错误提示 */
.streaming-error {
  font-size: 13px;
  color: #f56c6c;
  margin-bottom: 10px;
  padding: 8px 12px;
  background: #fef0f0;
  border-radius: 6px;
  border-left: 2px solid #f56c6c;
}

/* 块详情弹窗 */
.chunk-detail-section {
  margin-bottom: 20px;
}
.chunk-detail-section-title {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.chunk-detail-content {
  background: #fafafa;
  padding: 14px 16px;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  max-height: 240px;
  overflow-y: auto;
  line-height: 1.8;
  font-size: 14px;
}
.chunk-doc-content {
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  max-height: 360px;
  overflow-y: auto;
}
.doc-text {
  padding: 14px 16px;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.8;
  font-size: 14px;
  color: #606266;
  font-family: inherit;
}
.chunk-doc-empty {
  color: #909399;
  font-size: 13px;
  text-align: center;
  padding: 24px;
}
</style>

<!-- 全局样式：v-html 渲染的 mark 标签标黄，scoped 无法穿透 v-html -->
<style>
.chunk-highlight {
  background: #fef08a;
  color: #92400e;
  padding: 2px 4px;
  border-radius: 2px;
  scroll-margin-top: 120px;
}
</style>
