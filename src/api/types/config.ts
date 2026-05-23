export interface SystemConfigVO {
  id: number
  configKey: string
  configValue: string
  configType: 'STRING' | 'NUMBER' | 'BOOLEAN' | 'JSON'
  description: string
}
