<template>
  <div class="config-page">
    <div class="config-header"><h2>个人偏好配置</h2></div>
    <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px" title="个人偏好优先于系统默认值。文件上传/问答时将使用您的偏好参数。" />

    <div class="config-body">
      <div class="config-sidebar">
        <div v-for="c in personalCats" :key="c.value"
          :class="['category-item',{active:activeCat===c.value}]" @click="activeCat=c.value">
          <span class="cat-label">{{ c.label }}</span><el-tag size="small" type="info">{{ c.count }}</el-tag>
        </div>
      </div>
      <div class="config-main">
        <div v-if="activeCat==='chunk'" class="strategy-tabs">
          <el-radio-group v-model="chunkSub" size="small">
            <el-radio-button value="default">默认策略</el-radio-button>
            <el-radio-button value="common">通用参数</el-radio-button>
            <el-radio-button value="fixed">Fixed</el-radio-button>
            <el-radio-button value="hierarchical">Hierarchical</el-radio-button>
            <el-radio-button value="recursive">Recursive</el-radio-button>
            <el-radio-button value="semantic">Semantic</el-radio-button>
            <el-radio-button value="topic">Topic</el-radio-button>
            <el-radio-button value="hybrid">Hybrid</el-radio-button>
          </el-radio-group>
        </div>
        <el-table :data="filteredList" v-loading="loading" stripe border>
          <el-table-column label="参数名" min-width="160">
            <template #default="{row}"><div class="param-label">{{ row.label||row.configKey }}</div><div class="param-key">{{ row.configKey }}</div></template>
          </el-table-column>
          <el-table-column label="当前值" min-width="180">
            <template #default="{row}">
              <span :style="{color:row.fromUser?'#303133':'#c0c4cc'}">{{ row.configValue||'未设置' }}</span>
              <el-tag v-if="row.fromUser" size="small" type="success" style="margin-left:6px">已自定义</el-tag>
              <el-tag v-else size="small" type="info" style="margin-left:6px">系统默认</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="校验" width="120" show-overflow-tooltip>
            <template #default="{row}"><span class="rule-hint">{{ row.validationRule ? fmtRule(row.validationRule) : '—' }}</span></template>
          </el-table-column>
          <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip />
          <el-table-column label="操作" width="100" align="center">
            <template #default="{row}">
              <el-button link type="primary" size="small" @click="editRow(row)">修改</el-button>
              <el-button v-if="row.fromUser" link type="danger" size="small" @click="resetRow(row)">恢复</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="editVisible" title="修改偏好" width="420px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item :label="editRowData.label||editRowData.configKey">
          <el-input-number v-if="editRowData.configType==='NUMBER'" v-model="editVal" style="width:100%" :min="rMin()" :max="rMax()"/>
          <el-switch v-else-if="editRowData.configType==='BOOLEAN'" v-model="editBool" active-text="true" inactive-text="false"/>
          <el-select v-else-if="isEnum(editRowData.validationRule)" v-model="editStr" style="width:100%">
            <el-option v-for="v in enumVals(editRowData.validationRule)" :key="v" :label="v" :value="v"/>
          </el-select>
          <el-input v-else v-model="editStr"/>
        </el-form-item>
        <div style="font-size:12px;color:#909399">{{ editRowData.description }}<br/>系统默认: {{ editRowData.defaultValue }}</div>
      </el-form>
      <template #footer><el-button @click="editVisible=false">取消</el-button><el-button type="primary" @click="saveRow">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const PERSONAL_CATS: Record<string,string> = { chunk:'分块策略', retrieval:'检索参数', cleaning:'文档清洗', review:'审核策略', qa:'问答设置', personal:'个人默认' }
const STRATEGY_KEY = 'chunk.strategy'; const COMMON_KEYS = ['chunk.default_size','chunk.overlap','chunk.min_chunk_size','chunk.max_chunk_size']

const loading = ref(false); const allData = ref<any[]>([]); const activeCat = ref('chunk'); const chunkSub = ref('default')
const personalCats = ref<{value:string;label:string;count:number}[]>([])

const filteredList = computed(() => {
  if (activeCat.value !== 'chunk') return allData.value.filter((r:any) => r.category === activeCat.value)
  if (chunkSub.value === 'default') return allData.value.filter((r:any) => r.configKey === STRATEGY_KEY)
  if (chunkSub.value === 'common') return allData.value.filter((r:any) => COMMON_KEYS.includes(r.configKey))
  return allData.value.filter((r:any) => r.configKey.startsWith('chunk.'+chunkSub.value+'.'))
})

async function load() {
  loading.value = true
  try {
    const res = await request.get('/user/configs/effective')
    allData.value = (res as any[]) || []
    const cats: Record<string,number> = {}
    for (const r of allData.value) { const c = r.category||'general'; cats[c] = (cats[c]||0) + 1 }
    personalCats.value = Object.entries(PERSONAL_CATS).filter(([k]) => cats[k]).map(([v,l]) => ({value:v,label:l,count:cats[v]||0}))
  } finally { loading.value = false }
}

const editVisible = ref(false); const editRowData = ref<any>({})
const editVal = ref(0); const editBool = ref(false); const editStr = ref('')
function rMin() { try { return JSON.parse(editRowData.value.validationRule||'{}').min } catch { return undefined } }
function rMax() { try { return JSON.parse(editRowData.value.validationRule||'{}').max } catch { return undefined } }
function isEnum(r: string) { try { return JSON.parse(r).type==='enum' } catch { return false } }
function enumVals(r: string) { try { return JSON.parse(r).values||[] } catch { return [] } }
function fmtRule(r: string) { const rule=(()=>{try{return JSON.parse(r)}catch{return null}})(); if(!rule) return '—'; if(rule.type==='int'||rule.type==='float') return `${rule.type}[${rule.min??'-∞'},${rule.max??'+∞'}]`; if(rule.type==='enum') return '枚举'; return rule.type }

function editRow(row: any) {
  editRowData.value = row
  if (row.configType==='NUMBER') editVal.value = Number(row.configValue)||0
  else if (row.configType==='BOOLEAN') editBool.value = row.configValue==='true'
  else editStr.value = row.configValue||''
  editVisible.value = true
}
async function saveRow() {
  let v = ''
  if (editRowData.value.configType==='NUMBER') v = String(editVal.value)
  else if (editRowData.value.configType==='BOOLEAN') v = String(editBool.value)
  else v = editStr.value
  try { await request.post('/user/configs',{configKey:editRowData.value.configKey,configValue:v}); ElMessage.success('已保存'); editVisible.value=false; load() } catch { ElMessage.error('保存失败') }
}
async function resetRow(row: any) { if (!row.userConfigId) return; try { await request.delete(`/user/configs/${row.userConfigId}`); ElMessage.success('已恢复'); load() } catch {} }

onMounted(() => load())
</script>

<style scoped>
.config-page{height:100%;display:flex;flex-direction:column}
.config-header{margin-bottom:8px}
.config-header h2{margin:0;font-size:18px}
.config-body{display:flex;gap:16px;flex:1;min-height:0}
.config-sidebar{width:160px;flex-shrink:0;border-right:1px solid #e4e7ed;padding-right:12px}
.category-item{display:flex;justify-content:space-between;align-items:center;padding:10px 12px;cursor:pointer;border-radius:6px;margin-bottom:4px;transition:all .15s}
.category-item:hover{background:#ecf5ff}
.category-item.active{background:#d9ecff;color:#409eff;font-weight:500}
.cat-label{font-size:14px}
.config-main{flex:1;min-width:0}
.strategy-tabs{padding:8px 12px;background:#f5f7fa;border-radius:6px;margin-bottom:12px}
.param-label{font-weight:500;font-size:14px}
.param-key{font-size:12px;color:#909399;font-family:monospace}
.rule-hint{font-size:12px;color:#909399}
</style>
