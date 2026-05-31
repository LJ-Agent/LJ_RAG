export interface SystemConfigVO {
  id: number
  configKey: string
  configValue: string
  configType: 'STRING' | 'NUMBER' | 'BOOLEAN' | 'JSON'
  description: string
  category: string
  label: string
  defaultVal: string
  validationRule: string
  sortOrder: number
  editable: number
  targetServices: string
  reloadStrategy: string
  status: string
}

export interface ConfigHistoryVO {
  id: number
  configId: number
  configKey: string
  oldValue: string
  newValue: string
  changedBy: number
  changedByName: string
  changedAt: string
}

export interface ConfigSaveResult {
  configKey: string
  newValue: string
  reloadStrategy: string
  isUpdate: boolean
  validated: boolean
}

export interface ConfigValidateResult {
  valid: string
  error: string
}
