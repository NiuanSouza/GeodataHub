<script setup lang="ts">
import { computed } from 'vue'
import { FiArrowDown, FiCheck, FiAlertTriangle, FiX } from 'vue-icons-plus/fi'

const props = defineProps<{
  title: string
  value: number | string
  variant: 'success' | 'warning' | 'danger' | 'info' | 'primary' | 'orange'
  iconType?: 'down' | 'check' | 'warning' | 'error'
}>()

const variantClass = computed(() => {
  switch (props.variant) {
    case 'success':
      return 'border-success text-success'
    case 'warning':
      return 'border-warning text-warning'
    case 'danger':
      return 'border-danger text-danger'
    case 'info':
      return 'border-info text-info'
    case 'primary':
      return 'border-primary text-primary'
    case 'orange':
      return 'border-orange text-orange'
    default:
      return 'border-primary text-primary'
  }
})

const variableName = computed(() => {
  if (props.variant === 'orange') return '--vis-brand-orange'
  return `--vis-c-${props.variant}`
})
</script>

<template>
  <div class="stat-card" :class="variantClass">
    <div class="stat-header">
      <span class="stat-title">{{ title }}</span>
    </div>
    <div class="stat-body">
      <div class="stat-icon-container">
        <!-- SVG Icons based on iconType using vue-icons-plus -->
        <FiArrowDown v-if="iconType === 'down'" class="stat-icon icon-down" size="32" />
        <FiCheck v-else-if="iconType === 'check'" class="stat-icon icon-check" size="32" />
        <FiAlertTriangle v-else-if="iconType === 'warning'" class="stat-icon icon-warning" size="32" />
        <FiX v-else-if="iconType === 'error'" class="stat-icon icon-error" size="32" />
      </div>
      <span class="stat-value">{{ value }}</span>
    </div>
  </div>
</template>

<style scoped>
.stat-card {
  display: flex;
  flex-direction: column;
  padding: 1.5rem;
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  width: 100%;
  min-width: 200px;
  box-shadow:
    0 4px 6px -1px rgba(0, 0, 0, 0.05),
    0 2px 4px -1px rgba(0, 0, 0, 0.03);
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
  border-top-width: 4px;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow:
    0 10px 15px -3px rgba(0, 0, 0, 0.1),
    0 4px 6px -2px rgba(0, 0, 0, 0.05);
}

.stat-header {
  margin-bottom: 0.75rem;
}

.stat-title {
  font-size: 0.85rem;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--color-text);
  letter-spacing: 0.05em;
}

.stat-body {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1rem;
}

.stat-value {
  font-size: 2.5rem;
  font-weight: 800;
  color: var(--color-heading);
  line-height: 1;
}

.stat-footer {
  margin-top: auto;
}

.stat-variable-badge {
  display: inline-block;
  padding: 0.25rem 0.5rem;
  background-color: var(--color-background-mute);
  color: var(--color-text);
  border-radius: 4px;
  font-family: monospace;
  font-size: 0.75rem;
  opacity: 0.7;
}

/* Specific Variant Styles - Top Border and Icon Colors */
.border-success {
  border-top-color: var(--vis-c-success);
}
.border-warning {
  border-top-color: var(--vis-c-warning);
}
.border-danger {
  border-top-color: var(--vis-c-danger);
}
.border-info {
  border-top-color: var(--vis-c-info);
}
.border-primary {
  border-top-color: var(--vis-c-primary, #3b82f6);
}
.border-orange {
  border-top-color: var(--vis-brand-orange);
}

.icon-down {
  stroke: var(--vis-c-info, #8b9bb4);
} /* Muted blue for download/down */
.icon-check {
  stroke: var(--vis-c-success);
}
.icon-warning {
  stroke: var(--vis-c-warning);
}
.icon-error {
  stroke: var(--vis-c-danger);
}
</style>
