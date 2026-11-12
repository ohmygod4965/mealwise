/// <reference types="vite/client" />

// .vue 单文件组件的类型声明，交给 vue-tsc 做模板类型检查
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}

interface ImportMetaEnv {
  /** 可选：覆盖接口基础路径，默认 /api */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
