<script setup lang="ts">
import { Button } from '@proj-airi/ui'
import { errorMessageFrom } from '@moeru/std'
import { onMounted, ref } from 'vue'

import type { HyperOsEnvironment, HyperOsRefreshRateDiagnostics } from '../../modules/hyperos'

import { HyperOs } from '../../modules/hyperos'

/**
 * Xiaomi HyperOS adaptation panel (target firmware: HyperOS 2.0.209).
 *
 * Only rendered when the native `HyperOs` Capacitor plugin is available,
 * i.e. on Android builds of stage-pocket.
 */

const environment = ref<HyperOsEnvironment>()
const diagnostics = ref<HyperOsRefreshRateDiagnostics>()
const batteryIgnoring = ref<boolean>()
const error = ref<string>()
const busy = ref(false)

async function refresh() {
  error.value = undefined
  try {
    environment.value = await HyperOs.getEnvironment()
    if (!environment.value.isHyperOs) {
      return
    }
    const battery = await HyperOs.isIgnoringBatteryOptimizations()
    batteryIgnoring.value = battery.ignoring
    diagnostics.value = await HyperOs.getRefreshRateDiagnostics()
  }
  catch (err) {
    error.value = errorMessageFrom(err)
  }
}

async function openSurvivalSettings() {
  busy.value = true
  try {
    await HyperOs.requestHyperOsSurvivalSettings()
  }
  finally {
    busy.value = false
  }
}

async function openRefreshRateSettings() {
  busy.value = true
  try {
    await HyperOs.openRefreshRateSettings()
  }
  finally {
    busy.value = false
  }
}

onMounted(async () => {
  if (!HyperOs.enabled) {
    return
  }
  await refresh()
})
</script>

<template>
  <div v-if="HyperOs.enabled && environment?.isHyperOs" :class="['flex flex-col gap-4']">
    <section
      :class="[
        'rounded-xl p-4',
        'border border-neutral-200 bg-neutral-50',
        'dark:border-neutral-700 dark:bg-neutral-800/50',
      ]"
    >
      <h3 :class="['text-sm font-semibold', 'text-neutral-800 dark:text-neutral-100']">
        Xiaomi {{ environment.firmware }}
      </h3>
      <p :class="['mt-1 text-xs', 'text-neutral-600 dark:text-neutral-300']">
        Обнарушена прошивка HyperOS/MIUI. AIRI Pocket адаптирован под неё: удерживается
        фоновое соединение с сервером, отключён навязанный тёмный режим WebView и
        диагностируется снижение частоты обновления экрана.
      </p>

      <dl :class="['mt-3 grid grid-cols-2 gap-x-4 gap-y-1 text-xs', 'text-neutral-600 dark:text-neutral-300']">
        <dt>Android SDK</dt>
        <dd>{{ environment.androidSdkInt }}</dd>
        <dt>Версия HyperOS</dt>
        <dd>{{ environment.hyperOsVersion }}</dd>
        <dt>Целевая прошивка 2.0.209</dt>
        <dd>{{ environment.isHyperOsTargetBuild ? 'да' : 'нет' }}</dd>
        <dt>HyperOS 2.x (семейство 2.0.209)</dt>
        <dd>{{ environment.isHyperOs2 ? 'да' : 'нет' }}</dd>
        <dt>Оптимизация батареи</dt>
        <dd :class="[batteryIgnoring === false ? 'text-red-600 dark:text-red-400' : 'text-green-700 dark:text-green-400']">
          {{ batteryIgnoring === undefined ? '—' : batteryIgnoring ? 'приложение в исключениях' : 'приложение заморожено' }}
        </dd>
        <dt>Частота обновления</dt>
        <dd>{{ diagnostics?.reportedRefreshMode ?? '—' }}</dd>
      </dl>

      <p v-if="error" :class="['mt-2 text-xs text-red-600 dark:text-red-400']">
        {{ error }}
      </p>

      <div :class="['mt-4 flex flex-wrap gap-2']">
        <Button
          :label="batteryIgnoring === false ? 'Открыть настройки HyperOS (обязательно)' : 'Настройки выживания в HyperOS'"
          :disabled="busy"
          @click="openSurvivalSettings()"
        />
        <Button
          label="Частота обновления экрана"
          :disabled="busy"
          @click="openRefreshRateSettings()"
        />
        <Button
          label="Обновить статус"
          :disabled="busy"
          @click="refresh()"
        />
      </div>

      <ul v-if="diagnostics?.recommendedSetting" :class="['mt-3 list-disc pl-5 text-xs', 'text-neutral-600 dark:text-neutral-300']">
        <li>{{ diagnostics.recommendedSetting }}</li>
      </ul>
    </section>
  </div>
</template>
