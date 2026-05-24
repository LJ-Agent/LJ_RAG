<template>
  <div class="qa-layout">
    <!-- 左侧会话列表 -->
    <div class="qa-sidebar">
      <div class="sidebar-header">
        <el-button type="primary" size="small" @click="createNewSession" style="width: 100%">
          <el-icon><Plus /></el-icon> 新建会话
        </el-button>
      </div>
      <div class="session-list">
        <div
          v-for="s in sessions"
          :key="s.id"
          :class="['session-item', { active: activeSessionId === s.id }]"
          @click="switchSession(s.id)"
        >
          <div class="session-info">
            <span class="session-title">{{ s.title }}</span>
            <span class="session-meta">{{ s.messageCount }} 条 · {{ formatTime(s.updatedAt) }}</span>
          </div>
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
        <el-empty v-if="sessions.length === 0" description="暂无会话" :image-size="60" />
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
        <el-divider direction="vertical" />
        <el-checkbox v-model="streamMode" label="流式输出" border size="small" />
      </div>

      <!-- 消息区域 -->
      <div class="qa-messages" ref="messagesRef">
        <div v-if="messages.length === 0 && !isStreaming" style="text-align: center; margin-top: 20vh; color: #909399;">
          <el-icon :size="48"><ChatDotRound /></el-icon>
          <p style="margin-top: 12px;">选择一个知识库，开始提问吧</p>
        </div>

        <div v-for="(msg, idx) in messages" :key="idx" :class="['qa-message', msg.role]">
          <div class="qa-message__bubble">
            <div v-if="msg.role === 'user'">{{ msg.content }}</div>
            <div v-else v-html="renderMarkdown(msg.content)" class="markdown-body"></div>

            <!-- 检索过程可视化 -->
            <div v-if="msg.sourceDocs && msg.sourceDocs.length > 0" class="source-docs">
              <el-collapse>
                <el-collapse-item>
                  <template #title>
                    <div class="retrieval-title">
                      <span>检索过程</span>
                      <span class="retrieval-summary">
                        命中 {{ msg.sourceDocs.length }} 条 · 耗时 {{ msg.latencyMs || '--' }}ms · {{ msg.tokenCount || '--' }} tokens
                      </span>
                    </div>
                  </template>

                  <!-- 检索步骤可视化 -->
                  <div class="retrieval-steps">
                    <div class="step-item">
                      <div class="step-indicator step-1">
                        <el-icon><Search /></el-icon>
                      </div>
                      <div class="step-content">
                        <div class="step-title">向量检索（Milvus）</div>
                        <div class="step-desc">使用 embedding 向量在 Milvus 中进行 ANN 搜索，召回语义相似的 Top-N 文档片段</div>
                      </div>
                    </div>
                    <div class="step-item">
                      <div class="step-indicator step-2">
                        <el-icon><Document /></el-icon>
                      </div>
                      <div class="step-content">
                        <div class="step-title">BM25 关键词检索</div>
                        <div class="step-desc">基于 jieba 分词的 BM25 算法，匹配精确关键词，补充字面匹配结果</div>
                      </div>
                    </div>
                    <div class="step-item">
                      <div class="step-indicator step-3">
                        <el-icon><Connection /></el-icon>
                      </div>
                      <div class="step-content">
                        <div class="step-title">RRF 融合排序</div>
                        <div class="step-desc">将向量检索和 BM25 检索结果通过 RRF 算法融合，重排序后返回最终 {{ msg.sourceDocs.length }} 条结果</div>
                      </div>
                    </div>
                  </div>

                  <el-divider />

                  <!-- 命中文档列表 -->
                  <div class="retrieval-results-header">
                    <span>命中文档片段 ({{ msg.sourceDocs.length }})</span>
                  </div>
                  <div v-for="(doc, di) in msg.sourceDocs" :key="doc.chunkId" class="source-doc-item">
                    <div class="source-doc-header">
                      <span class="source-doc-name">
                        <el-tag size="small" type="primary">{{ di + 1 }}</el-tag>
                        {{ doc.documentName || '文档#' + doc.documentId }}
                      </span>
                      <el-tag size="small" :type="doc.score > 0.7 ? 'success' : doc.score > 0.4 ? 'warning' : 'info'">
                        RRF: {{ (doc.score * 100).toFixed(2) }}%
                      </el-tag>
                    </div>
                    <p class="source-doc-content">{{ doc.content }}</p>
                  </div>
                </el-collapse-item>
              </el-collapse>
            </div>
          </div>
        </div>

        <div v-if="isStreaming" class="qa-message bot">
          <div class="qa-message__bubble">
            <div v-if="streamContent" v-html="renderMarkdown(streamContent)" class="markdown-body"></div>
            <span v-else class="typing-indicator">思考中...</span>
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
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted } from 'vue'
import { ChatDotRound, Plus, MoreFilled, Promotion, Search, Document, Connection } from '@element-plus/icons-vue'
import { qaApi } from '@/api/modules/qa'
import { knowledgeBaseApi } from '@/api/modules/knowledgeBase'
import { useSSE } from '@/composables/useSSE'
import { sanitizeHtml } from '@/utils/sanitize'
import { marked } from 'marked'
import type { KnowledgeBaseVO } from '@/api/types/knowledgeBase'
import type { SourceDoc, ChatSessionVO } from '@/api/types/qa'
import { ElMessage, ElMessageBox } from 'element-plus'

interface ChatMessage {
  role: 'user' | 'bot'
  content: string
  sourceDocs?: SourceDoc[]
  tokenCount?: number
  latencyMs?: number
}

const kbList = ref<KnowledgeBaseVO[]>([])
const selectedKbIds = ref<number[]>([])
const streamMode = ref(true)
const question = ref('')
const messages = ref<ChatMessage[]>([])
const messagesRef = ref<HTMLElement>()

const sessions = ref<ChatSessionVO[]>([])
const activeSessionId = ref<number | null>(null)

const { isStreaming, streamContent, startStream } = useSSE()

const canSend = computed(() => question.value.trim() && selectedKbIds.value.length > 0 && !isStreaming.value)

function formatTime(dateStr: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  return d.toLocaleDateString()
}

function renderMarkdown(text: string): string {
  const html = marked.parse(text || '') as string
  return sanitizeHtml(html)
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
        const sourceDocs: SourceDoc[] = []
        try {
          if (r.answer) {
            // sourceDocs stored in QaServiceImpl as JSON, available through the expanded history API
            // For now, show answer without separate sourceDocs in history mode
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
      await loadSessions()
    } catch { /* cancelled */ }
  }
}

async function ensureSession(): Promise<number | null> {
  if (activeSessionId.value) return activeSessionId.value
  const kbIds = selectedKbIds.value.join(',')
  const s = await qaApi.createSession({ kbIds, title: '新会话' })
  await loadSessions()
  activeSessionId.value = s.id
  return s.id
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

  const sessionId = await ensureSession()
  const dto = {
    question: q,
    kbIds: selectedKbIds.value,
    sessionId: sessionId || undefined,
    topK: 5,
    scoreThreshold: 0.7,
  }

  if (streamMode.value) {
    const botMsg: ChatMessage = { role: 'bot', content: '' }
    messages.value.push(botMsg)

    await startStream(
      dto,
      (chunk) => {
        botMsg.content += chunk
        scrollToBottom()
      },
      (meta) => {
        botMsg.tokenCount = meta.tokenCount
        botMsg.latencyMs = meta.latencyMs
      }
    )

    if (!botMsg.content) {
      botMsg.content = '[未获取到回答]'
    }
    // 刷新会话列表以更新消息计数
    loadSessions()
  } else {
    try {
      const answer = await qaApi.chat(dto)
      messages.value.push({
        role: 'bot',
        content: answer.answer,
        sourceDocs: answer.sourceDocs,
        tokenCount: answer.tokenCount,
        latencyMs: answer.latencyMs,
      })
      loadSessions()
    } catch {
      messages.value.push({ role: 'bot', content: '[请求失败，请重试]' })
    }
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
  width: 240px;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  background: #fafafa;
}
.sidebar-header {
  padding: 12px;
  border-bottom: 1px solid #e4e7ed;
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

.source-docs {
  margin-top: 12px;
  border-top: 1px solid #e4e7ed;
  padding-top: 8px;
}
.source-doc-item {
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}
.source-doc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.source-doc-name {
  font-weight: 500;
  font-size: 13px;
}
.source-doc-content {
  font-size: 13px;
  color: #606266;
  margin: 4px 0 0;
  white-space: pre-wrap;
  word-break: break-all;
}

.typing-indicator {
  color: #909399;
  font-style: italic;
}

.retrieval-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}
.retrieval-summary {
  color: #909399;
  font-size: 12px;
}
.retrieval-steps {
  margin-bottom: 4px;
}
.step-item {
  display: flex;
  gap: 12px;
  padding: 10px 0;
}
.step-indicator {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 14px;
  flex-shrink: 0;
}
.step-1 { background: #409eff; }
.step-2 { background: #67c23a; }
.step-3 { background: #e6a23c; }
.step-content {
  flex: 1;
}
.step-title {
  font-weight: 500;
  font-size: 13px;
  margin-bottom: 2px;
}
.step-desc {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}
.retrieval-results-header {
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 8px;
  color: #606266;
}
</style>
