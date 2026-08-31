import type { SalaryProfile } from '@salary-flow/core'

/**
 * 桌面小组件桥接（仅原生壳内生效，Web/PWA 自动 no-op）。
 *
 * 原生侧注册了 @CapacitorPlugin(name = "WidgetSync")，这里直接使用 Capacitor
 * 注入的全局桥（window.Capacitor.nativePromise），避免给 Web 端引入
 * @capacitor/core 依赖。小组件的分钟级刷新由原生闹钟承担，App 无需常驻。
 */
interface CapacitorNativeBridge {
  isNativePlatform?: () => boolean
  nativePromise?: (pluginId: string, methodName: string, options?: unknown) => Promise<unknown>
}

function nativeBridge(): CapacitorNativeBridge | null {
  const cap = (window as unknown as { Capacitor?: CapacitorNativeBridge }).Capacitor
  return cap && cap.isNativePlatform?.() ? cap : null
}

/** 把当前薪资配置快照写给原生小组件；失败静默（不影响 App 功能）。 */
export function syncWidgetProfile(profile: SalaryProfile): void {
  const cap = nativeBridge()
  if (!cap?.nativePromise) return
  try {
    void cap
      .nativePromise('WidgetSync', 'sync', { profileJson: JSON.stringify(profile) })
      .catch(() => undefined)
  } catch {
    // 桥不可用时静默跳过
  }
}

/** 原生安装包版本号（gradle versionName）；Web/PWA 或桥不可用时返回 null。 */
export async function fetchNativeVersion(): Promise<string | null> {
  const cap = nativeBridge()
  if (!cap?.nativePromise) return null
  try {
    const result = (await cap.nativePromise('WidgetSync', 'version')) as { version?: string } | undefined
    return result?.version ?? null
  } catch {
    return null
  }
}
