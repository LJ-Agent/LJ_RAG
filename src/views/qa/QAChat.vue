<template>
  <div class="page-container" style="display: flex; flex-direction: column; padding: 0; height: calc(100vh - 96px);">
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
      <div v-if="messages.length === 0" style="text-align: center; margin-top: 20vh; color: #909399;">
        <el-icon :size="48"><ChatDotRound /></el-icon>
        <p style="margin-top: 12px;">选择一个知识库，开始提问吧</p>
      </div>

      <div
        v-for="(msg, idx) in messages"
        :key="idx"
        :class="['qa-message', msg.role]"
      >
        <div class="qa-message__bubble">
          <div v-if="msg.role === 'user'">{{ msg.content }}</div>
          <div v-else v-html="renderMarkdown(msg.content)" class="markdown-body"></div>

          <!-- 来源文档 -->
          <div v-if="msg.sourceDocs && msg.sourceDocs.length > 0" class="source-docs">
            <el-collapse>
              <el-collapse-item>
                <template #title>
                  <span style="font-size: 13px; color: #909399;">
                    参考来源 ({{ msg.sourceDocs.length }}) — {{ msg.tokenCount || '--' }} tokens, {{ msg.latencyMs || '--' }}ms
                  </span>
                </template>
                <div v-for="doc in msg.sourceDocs" :key="doc.chunkId" class="source-doc-item">
                  <div class="source-doc-header">
                    <span class="source-doc-name">{{ doc.documentName }}</span>
                    <el-tag size="small" type="info">相似度: {{ (doc.score * 100).toFixed(1) }}%</el-tag>
                  </div>
                  <p class="source-doc-content">{{ doc.content }}</p>
                </div>
              </el-collapse-item>
            </el-collapse>
          </div>
        </div>
      </div>

      <!-- 流式打字中 -->
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
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick, onMounted } from 'vue'
import { ChatDotRound, Promotion } from '@element-plus/icons-vue'
import { qaApi } from '@/api/modules/qa'
import { knowledgeBaseApi } from '@/api/modules/knowledgeBase'
import { useSSE } from '@/composables/useSSE'
import { sanitizeHtml } from '@/utils/sanitize'
import { marked } from 'marked'
import type { KnowledgeBaseVO } from '@/api/types/knowledgeBase'
import type { SourceDoc } from '@/api/types/qa'
import { ElMessage } from 'element-plus'

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

const { isStreaming, streamContent, startStream, stopStream } = useSSE()

const canSend = computed(() => question.value.trim() && selectedKbIds.value.length > 0 && !isStreaming.value)

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

async function handleSend() {
  if (!canSend.value) {
    if (selectedKbIds.value.length === 0) ElMessage.warning('请先选择知识库')
    return
  }

  const q = question.value.trim()
  question.value = ''

  // 添加用户消息
  messages.value.push({ role: 'user', content: q })
  scrollToBottom()

  const dto = {
    question: q,
    kbIds: selectedKbIds.value,
    topK: 5,
    scoreThreshold: 0.7,
  }

  if (streamMode.value) {
    // SSE流式
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
  } else {
    // 非流式
    try {
      const answer = await qaApi.chat(dto)
      messages.value.push({
        role: 'bot',
        content: answer.answer,
        sourceDocs: answer.sourceDocs,
        tokenCount: answer.tokenCount,
        latencyMs: answer.latencyMs,
      })
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
  } catch { /* ignore */ }
})
</script>

<style scoped>
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
</style>
