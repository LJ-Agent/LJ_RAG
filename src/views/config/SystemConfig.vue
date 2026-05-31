<template>
  <div class="config-page">
    <div class="config-header">
      <h2>系统配置</h2>
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon> 添加配置
      </el-button>
    </div>

    <!-- 顶层分类Tab -->
    <el-tabs v-model="activeCategory" @tab-change="onCategoryChange" type="card">
      <el-tab-pane v-for="c in categories" :key="c.value" :name="c.value">
        <template #label>
          <span>{{ c.label }} <el-tag size="small" type="info" style="margin-left:4px">{{ c.count }}</el-tag></span>
        </template>
      </el-tab-pane>
    </el-tabs>

    <!-- 分块策略二级Tab：每个策略展示其通用参数+专属参数 -->
    <div v-if="activeCategory === 'chunk'" class="strategy-tabs">
      <el-radio-group v-model="chunkSubFilter" size="small" @change="onSubFilterChange">
        <el-radio-button value="fixed">Fixed 固定长度</el-radio-button>
        <el-radio-button value="recursive">Recursive 递归分割</el-radio-button>
        <el-radio-button value="semantic">Semantic 语义段落</el-radio-button>
        <el-radio-button value="topic">Topic 主题检测</el-radio-button>
        <el-radio-button value="hybrid">Hybrid 混合策略</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 配置表格 -->
    <el-table :data="filteredList" v-loading="isLoading" stripe border style="width: 100%">
      <el-table-column label="参数名" min-width="180">
        <template #default="{ row }">
          <div class="param-cell">
            <div class="param-label">
              <el-tooltip v-if="row.required === 1" content="关键配置，不可删除" placement="top">
                <el-icon class="lock-icon"><Lock /></el-icon>
              </el-tooltip>
              {{ row.label || row.configKey }}
            </div>
            <div class="param-key">{{ row.configKey }}</div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="当前值" min-width="140">
        <template #default="{ row }">
          <template v-if="row.configType === 'BOOLEAN'">
            <el-tag :type="row.configValue === 'true' ? 'success' : 'danger'" size="small">{{ row.configValue }}</el-tag>
          </template>
          <span v-else class="config-val">{{ row.configValue }}</span>
          <span v-if="row.defaultVal" class="config-default">默认: {{ row.defaultVal }}</span>
        </template>
      </el-table-column>
      <el-table-column label="校验" width="130" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.validationRule" class="rule-hint">{{ formatRule(row.validationRule) }}</span>
          <span v-else class="rule-none">—</span>
        </template>
      </el-table-column>
      <el-table-column label="生效" width="80" align="center">
        <template #default="{ row }">
          <el-tooltip :content="strategyTooltip(row.reloadStrategy)">
            <el-tag size="small" :type="strategyTagType(row.reloadStrategy)">
              {{ row.reloadStrategy === 'kafka' ? '即时' : row.reloadStrategy === 'api' ? '热载' : '重启' }}
            </el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="170" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link type="warning" size="small" @click="openHistory(row)">历史</el-button>
          <el-tooltip v-if="row.required === 1" content="关键配置不可删除" placement="top">
            <el-icon class="lock-btn"><Lock /></el-icon>
          </el-tooltip>
          <el-popconfirm
            v-else
            title="删除后系统将使用默认值运行，确定删除？"
            @confirm="handleDelete(row.id)"
          >
            <template #reference>
              <el-button link type="danger" size="small">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 空状态 -->
    <el-empty v-if="!isLoading && filteredList.length === 0" description="暂无配置" :image-size="60" />

    <!-- 编辑抽屉 -->
    <el-drawer v-model="drawerVisible" :title="isEdit ? '编辑配置' : '添加配置'" size="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px" label-position="top">
        <el-form-item label="参数名">
          <el-input v-model="form.label" placeholder="中文显示名" />
        </el-form-item>
        <el-form-item label="配置键" prop="configKey">
          <el-input v-model="form.configKey" placeholder="如 chunk.default_size" :disabled="isEdit" />
          <div v-if="isEdit" style="font-size:11px;color:#e6a23c;margin-top:2px">🔒 键名不可修改（代码中硬编码引用）</div>
        </el-form-item>
        <el-form-item label="当前值" prop="configValue">
          <template v-if="form.configType === 'BOOLEAN'">
            <el-switch v-model="boolValue" active-text="true" inactive-text="false" @change="onBoolChange" />
          </template>
          <template v-else-if="form.configType === 'NUMBER'">
            <el-input-number v-model="numValue" :min="minFromRule()" :max="maxFromRule()" style="width:100%"
              @change="onNumChange" />
          </template>
          <template v-else>
            <el-input v-model="form.configValue" placeholder="值" @blur="validateField" />
          </template>
          <div v-if="fieldError" class="field-error">{{ fieldError }}</div>
          <div v-if="form.defaultVal" class="field-hint">默认值: {{ form.defaultVal }}</div>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.configType" style="width:100%" @change="onTypeChange">
            <el-option v-for="t in ['STRING','NUMBER','BOOLEAN','JSON']" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" style="width:100%">
            <el-option v-for="c in categories" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="生效策略">
          <el-select v-model="form.reloadStrategy" style="width:100%">
            <el-option label="Kafka推送 — 即时生效" value="kafka" />
            <el-option label="API重载 — 2~5秒生效" value="api" />
            <el-option label="需重启服务" value="restart" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="参数说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存并发布</el-button>
      </template>
    </el-drawer>

    <!-- 变更历史抽屉 -->
    <el-drawer v-model="historyVisible" title="变更历史" size="420px" direction="rtl">
      <template v-if="historyList.length > 0">
        <el-timeline>
          <el-timeline-item v-for="h in historyList" :key="h.id" :timestamp="h.changedAt" placement="top">
            <p><strong>{{ h.changedByName || '系统' }}</strong> 修改了 <code>{{ historyKey }}</code></p>
            <p>
              <span class="history-old">{{ h.oldValue || '(新建)' }}</span>
              <el-icon><ArrowRight /></el-icon>
              <span class="history-new">{{ h.newValue }}</span>
            </p>
            <el-button link type="primary" size="small" @click="handleRollback(h)">回滚到此版本</el-button>
          </el-timeline-item>
        </el-timeline>
      </template>
      <el-empty v-else description="暂无变更记录" :image-size="60" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { Plus, ArrowRight, Lock } from '@element-plus/icons-vue'
import { configApi } from '@/api/modules/configs'
import type { SystemConfigVO, ConfigHistoryVO } from '@/api/types/config'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'

// ─── 分类 ───
const CATEGORY_LABELS: Record<string, string> = {
  chunk: '分块策略', retrieval: '检索参数', cleaning: '文档清洗',
  qa: '问答设置', rate_limit: '限流控制', review: '审核策略',
  upload: '文件上传', general: '通用'
}
const categories = ref<{ value: string; label: string; count: number }[]>([])
const activeCategory = ref('chunk')
const chunkSubFilter = ref('semantic')  // 默认展示系统默认策略semantic

async function loadCategories() {
  const cats: { value: string; label: string; count: number }[] = []
  for (const [val, label] of Object.entries(CATEGORY_LABELS)) {
    try {
      const res = await configApi.listByCategory(val)
      if (res && res.length > 0) cats.push({ value: val, label, count: res.length })
    } catch { /* skip empty categories */ }
  }
  cats.sort((a, b) => b.count - a.count)
  categories.value = cats
  if (cats.length > 0 && !activeCategory.value) activeCategory.value = cats[0].value
}

function onCategoryChange() { chunkSubFilter.value = 'semantic'; fetchList() }
function onSubFilterChange() { /* computed triggers */ }

// ─── 列表 + 子筛选：分块策略 = 通用参数 + 策略专属参数 ───
const list = ref<SystemConfigVO[]>([])
const isLoading = ref(false)
const COMMON_CHUNK_KEYS = ['chunk.strategy', 'chunk.default_size', 'chunk.overlap', 'chunk.min_chunk_size', 'chunk.max_chunk_size']

const filteredList = computed(() => {
  if (activeCategory.value !== 'chunk') return list.value
  return list.value.filter(r =>
    COMMON_CHUNK_KEYS.includes(r.configKey) ||
    r.configKey.startsWith('chunk.' + chunkSubFilter.value + '.')
  )
})

async function fetchList() {
  isLoading.value = true
  try { const res = await configApi.listByCategory(activeCategory.value); list.value = res || [] }
  finally { isLoading.value = false }
}

// ─── 编辑表单 ───
const drawerVisible = ref(false), isEdit = ref(false), submitting = ref(false)
const fieldError = ref(''), boolValue = ref(false), numValue = ref(0)
const formRef = ref<FormInstance>()
const form = reactive({ id: 0, configKey: '', configValue: '', configType: 'STRING' as any,
  description: '', category: 'chunk', label: '', reloadStrategy: 'kafka', defaultVal: '', validationRule: '' })

const formRules: FormRules = {
  configKey: [{ required: true, message: '请输入配置键', trigger: 'blur' }],
  configValue: [{ required: true, message: '请输入配置值', trigger: 'blur' }],
}

function openCreate() {
  isEdit.value = false; resetForm()
  form.category = activeCategory.value; drawerVisible.value = true
}
function openEdit(row: SystemConfigVO) {
  isEdit.value = true
  form.id = row.id; form.configKey = row.configKey; form.configValue = row.configValue
  form.configType = row.configType; form.description = row.description || ''
  form.category = row.category || 'general'; form.label = row.label || ''
  form.reloadStrategy = row.reloadStrategy || 'kafka'; form.defaultVal = row.defaultVal || ''
  form.validationRule = row.validationRule || ''
  boolValue.value = row.configValue === 'true'
  numValue.value = Number(row.configValue) || 0
  fieldError.value = ''; drawerVisible.value = true
}
function resetForm() {
  form.id = 0; form.configKey = ''; form.configValue = ''; form.configType = 'STRING'
  form.description = ''; form.category = 'chunk'; form.label = ''
  form.reloadStrategy = 'kafka'; form.defaultVal = ''; form.validationRule = ''
  boolValue.value = false; numValue.value = 0; fieldError.value = ''
}
function onBoolChange(v: boolean) { form.configValue = String(v) }
function onNumChange(v: number | undefined) { form.configValue = String(v ?? 0) }
function onTypeChange() { form.configValue = ''; boolValue.value = false; numValue.value = 0; fieldError.value = '' }

function minFromRule(): number | undefined {
  try { const r = JSON.parse(form.validationRule || '{}'); return r.min } catch { return undefined }
}
function maxFromRule(): number | undefined {
  try { const r = JSON.parse(form.validationRule || '{}'); return r.max } catch { return undefined }
}

async function validateField() {
  fieldError.value = ''
  if (!form.configValue) return
  try {
    const res = await configApi.validate({ configKey: form.configKey, configValue: form.configValue, configType: form.configType } as any)
    if (res && res.valid === 'false') fieldError.value = res.error
  } catch { /* ignore */ }
}

// ─── 校验显示 ───
function parseValidationRule(row: SystemConfigVO) { if (!row.validationRule) return null; try { return JSON.parse(row.validationRule) } catch { return null } }
function formatRule(ruleJson: string): string {
  const rule = parseValidationRule({ validationRule: ruleJson } as any)
  if (!rule) return '—'
  if (rule.type === 'int' || rule.type === 'float') return `${rule.type} [${rule.min ?? '-∞'}, ${rule.max ?? '+∞'}]`
  if (rule.type === 'enum') return `枚举: ${(rule.values || []).slice(0, 3).join(', ')}`
  if (rule.type === 'bool') return 'true/false'
  return rule.type
}
function typeTagType(t: string) { return t === 'NUMBER' ? 'warning' : t === 'BOOLEAN' ? 'success' : t === 'JSON' ? 'danger' : 'info' }
function strategyTagType(s: string) { return s === 'kafka' ? 'success' : s === 'api' ? 'warning' : 'danger' }
function strategyTooltip(s: string) {
  if (s === 'kafka') return 'Kafka推送，下次调用即时生效'
  if (s === 'api') return '组件API热重载，2~5秒生效'
  if (s === 'restart') return '需手动重启对应服务'
  return ''
}

// ─── CRUD ───
async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  await validateField()
  if (fieldError.value) { ElMessage.error('校验失败: ' + fieldError.value); return }
  submitting.value = true
  try {
    const res = await configApi.save({
      configKey: form.configKey, configValue: form.configValue, configType: form.configType,
      description: form.description, category: form.category,
      label: form.label, reloadStrategy: form.reloadStrategy,
    } as any)
    const msg = res.reloadStrategy === 'restart' ? '⚠ 需重启服务生效' : 'Kafka推送即时生效'
    ElMessage.success({ message: `已发布: ${form.configKey} = ${form.configValue} | ${msg}`, duration: 4000 })
    drawerVisible.value = false; fetchList()
  } finally { submitting.value = false }
}
async function handleDelete(id: number) { await configApi.delete(id); ElMessage.success('已删除'); fetchList() }

// ─── 历史 ───
const historyVisible = ref(false), historyList = ref<ConfigHistoryVO[]>([]), historyKey = ref('')
async function openHistory(row: SystemConfigVO) {
  historyKey.value = row.configKey; historyVisible.value = true; historyList.value = []
  try { historyList.value = await configApi.getHistory(row.configKey) } catch { historyList.value = [] }
}
async function handleRollback(h: ConfigHistoryVO) {
  try {
    await ElMessageBox.confirm(`回滚 "${h.configKey}" 到旧值: ${h.oldValue || '(空)'}？`, '回滚确认', { type: 'warning' })
    const res = await configApi.rollback(h.configKey, h.id)
    ElMessage.success(`已回滚: ${res.newValue}`); historyVisible.value = false; fetchList()
  } catch { /* cancelled */ }
}

// ─── 初始化 ───
loadCategories().then(() => { if (categories.value.length > 0) { activeCategory.value = categories.value[0].value; fetchList() } })
</script>

<style scoped>
.config-page { height: 100%; display: flex; flex-direction: column; }
.config-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.config-header h2 { margin: 0; font-size: 18px; }

.strategy-tabs { margin-bottom: 12px; padding: 8px 12px; background: #f5f7fa; border-radius: 6px; }

.param-cell { line-height: 1.4; }
.param-label { font-weight: 500; font-size: 14px; display: flex; align-items: center; gap: 2px; }
.param-key { font-size: 12px; color: #909399; font-family: monospace; }
.config-val { font-family: monospace; font-size: 14px; }
.config-default { font-size: 11px; color: #c0c4cc; margin-left: 6px; }
.rule-hint { font-size: 12px; color: #909399; }
.rule-none { color: #c0c4cc; }
.lock-icon { color: #e6a23c; font-size: 14px; flex-shrink: 0; }
.lock-btn { color: #c0c4cc; font-size: 16px; cursor: not-allowed; }

.field-error { color: #f56c6c; font-size: 12px; margin-top: 4px; }
.field-hint { color: #909399; font-size: 12px; margin-top: 2px; }

.history-old { color: #f56c6c; text-decoration: line-through; margin-right: 8px; font-family: monospace; }
.history-new { color: #67c23a; margin-left: 8px; font-weight: 500; font-family: monospace; }
</style>
