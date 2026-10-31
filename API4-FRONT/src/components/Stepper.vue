<script setup lang="ts">
import { computed } from 'vue'
import { FiCheck, FiAlertCircle } from 'vue-icons-plus/fi'

const props = defineProps<{
  currentStep: number
  progressStep?: number
  warningStep?: number
  steps: string[]
}>()

const emit = defineEmits<{
  (e: 'step-click', index: number): void
}>()

const actualProgressStep = computed(() => props.progressStep ?? props.currentStep)

const progressPercentage = computed(() => {
  if (props.steps.length <= 1) return '0%'
  const percentage = ((actualProgressStep.value - 1) / (props.steps.length - 1)) * 100
  return `${Math.min(percentage, 100)}%`
})

const onStepClick = (index: number) => {
  if (index + 1 <= actualProgressStep.value) {
    emit('step-click', index + 1)
  }
}
</script>

<template>
  <div class="stepper" :style="{ '--step-count': steps.length }">
    <div
      v-for="(step, index) in steps"
      :key="index"
      class="step"
      :class="{
        'step--active': currentStep === index + 1,
        'step--completed': actualProgressStep > index + 1,
        'step--clickable': index + 1 <= actualProgressStep,
        'step--warning': warningStep === index + 1 && actualProgressStep <= index + 1
      }"
      @click="onStepClick(index)"
    >
      <div class="step__indicator">
        <FiCheck v-if="actualProgressStep > index + 1" size="16" />
        <span v-else-if="warningStep === index + 1 && actualProgressStep <= index + 1" class="warning-icon">!</span>
        <span v-else>{{ index + 1 }}</span>
      </div>
      <span class="step__label">
        {{ step }}
      </span>
    </div>
    <div class="stepper__line" :style="{ '--progress': progressPercentage }"></div>
  </div>
</template>

<style scoped>
.stepper {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 2rem;
  position: relative;
  padding: 0;
}

.stepper__line {
  position: absolute;
  top: 1.25rem;
  left: calc(100% / (var(--step-count) * 2));
  right: calc(100% / (var(--step-count) * 2));
  height: 3px;
  background-color: var(--color-border);
  transform: translateY(-50%);
  z-index: 0;
  border-radius: 2px;
}

.stepper__line::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  width: var(--progress);
  background-color: var(--vis-brand-orange);
  border-radius: 2px;
  transition: width 0.5s cubic-bezier(0.4, 0, 0.2, 1);
}

.step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  z-index: 1;
  cursor: default;
  flex: 1;
  min-width: 0;
}

.step__indicator {
  width: 2.5rem;
  height: 2.5rem;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 0.9rem;
  border: 3px solid var(--color-border);
  background-color: var(--color-surface);
  color: var(--color-text);
  transition: all 0.3s ease;
}

.step--active .step__indicator {
  border-color: var(--vis-brand-orange);
  background-color: var(--vis-brand-orange);
  color: #ffffff;
  box-shadow: 0 0 0 4px rgba(242, 101, 34, 0.2);
}

.step--completed .step__indicator {
  border-color: var(--vis-c-success);
  background-color: var(--vis-c-success);
  color: #ffffff;
}

.step--warning .step__indicator {
  border-color: #eab308;
  background-color: #fef08a;
  color: #854d0e;
  box-shadow: 0 0 0 4px rgba(234, 179, 8, 0.2);
}

.step--active.step--warning .step__indicator {
  background-color: #eab308;
  color: #ffffff;
}

.warning-icon {
  font-weight: 900;
  font-size: 1.2rem;
}

.step__label {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-text);
  opacity: 0.5;
  text-align: center;
  transition: opacity 0.3s ease;
  white-space: normal;
  word-wrap: break-word;
  max-width: 100%;
}

.step--active .step__label {
  opacity: 1;
  color: var(--vis-brand-orange);
}

.step--completed .step__label {
  opacity: 0.8;
  color: var(--vis-c-success);
}

.step--warning .step__label {
  opacity: 1;
  color: #eab308;
}

.step--clickable {
  cursor: pointer;
}

.step--clickable:hover .step__indicator {
  transform: scale(1.05);
}
</style>
