import type { Plugin } from '@capacitor/core'

import { Capacitor, registerPlugin } from '@capacitor/core'

/**
 * JS facade over the native `HyperOs` Capacitor plugin.
 *
 * All entry points are no-ops on non-Android platforms so the same bundle can
 * run in the browser / iOS without conditional imports at every call site.
 */

export interface HyperOsEnvironment {
  isXiaomiDevice: boolean
  isMiuiOrHyperOs: boolean
  isHyperOs: boolean
  /** True on the targeted firmware family: Xiaomi HyperOS 2.x (e.g. OS 2.0.209). */
  isHyperOs2: boolean
  /**
   * Exact build version string of the ROM, e.g. "OS2.0.209.0.WCCCNXM" —
   * read from `ro.mi.os.version.incremental` / `ro.build.version.incremental`.
   */
  hyperOsVersion: string
  /** True when running on the exact target firmware build 2.0.209. */
  isHyperOsTargetBuild: boolean
  firmware: string
  androidSdkInt: number
}

export interface HyperOsBatteryState {
  ignoring: boolean
  isHyperOs: boolean
}

export interface HyperOsSurvivalSteps {
  batteryExemptionDialog: boolean
  autostartSettings: boolean
  batteryDetails: boolean
  isHyperOs: boolean
}

export interface HyperOsRefreshRateDiagnostics {
  isHyperOs: boolean
  isHyperOs2: boolean
  hyperOsVersion?: string
  firmware: string
  reportedRefreshMode?: string
  recommendedSetting?: string
}

interface HyperOsPluginPlugin extends Plugin {
  getEnvironment: () => Promise<HyperOsEnvironment>
  isIgnoringBatteryOptimizations: () => Promise<HyperOsBatteryState>
  requestHyperOsSurvivalSettings: () => Promise<HyperOsSurvivalSteps>
  getRefreshRateDiagnostics: () => Promise<HyperOsRefreshRateDiagnostics>
  openRefreshRateSettings: () => Promise<{ opened: boolean }>
}

const noopEnvironment: HyperOsEnvironment = {
  isXiaomiDevice: false,
  isMiuiOrHyperOs: false,
  isHyperOs: false,
  isHyperOs2: false,
  hyperOsVersion: 'N/A',
  isHyperOsTargetBuild: false,
  firmware: 'non-native',
  androidSdkInt: 0,
}

const native = Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android'
  ? registerPlugin<HyperOsPluginPlugin>('HyperOs')
  : undefined

export const HyperOs = {
  enabled: !!native,

  async getEnvironment(): Promise<HyperOsEnvironment> {
    return await native?.getEnvironment() ?? noopEnvironment
  },

  async isIgnoringBatteryOptimizations(): Promise<HyperOsBatteryState> {
    return await native?.isIgnoringBatteryOptimizations() ?? { ignoring: true, isHyperOs: false }
  },

  async requestHyperOsSurvivalSettings(): Promise<HyperOsSurvivalSteps | undefined> {
    return await native?.requestHyperOsSurvivalSettings()
  },

  async getRefreshRateDiagnostics(): Promise<HyperOsRefreshRateDiagnostics | undefined> {
    return await native?.getRefreshRateDiagnostics()
  },

  async openRefreshRateSettings(): Promise<boolean> {
    const result = await native?.openRefreshRateSettings()
    return result?.opened ?? false
  },
}
