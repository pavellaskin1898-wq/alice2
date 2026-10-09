<script setup lang="ts">
import HyperOsPanel from '../../../components/hyperos/hyperos-panel.vue'
import PermissionsPanel from '../../../components/permissions/permissions-panel.vue'
</script>

<template>
  <div :class="['flex flex-col gap-4 pb-12']">
    <p :class="['text-sm text-neutral-600', 'md:text-base dark:text-neutral-300']">
      {{ $t('settings.pages.system.permissions.description') }}
    </p>

    <PermissionsPanel />

    <!-- Xiaomi HyperOS 2.0.209 adaptation: shown only on Android builds running on HyperOS/MIUI -->
    <HyperOsPanel />
  </div>
</template>

<route lang="yaml">
meta:
  layout: settings
  titleKey: settings.pages.system.permissions.title
  subtitleKey: settings.title
  stageTransition:
    name: slide
</route>
