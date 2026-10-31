<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { BsLightbulbFill } from 'vue-icons-plus/bs'

const theme = ref<'light' | 'dark'>('dark')

const toggleTheme = () => {
  theme.value = theme.value === 'dark' ? 'light' : 'dark'
  document.documentElement.setAttribute('data-theme', theme.value)
  localStorage.setItem('theme', theme.value)
}

onMounted(() => {
  const savedTheme = localStorage.getItem('theme') as 'light' | 'dark' | null

  if (savedTheme) {
    theme.value = savedTheme
  } else {
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
    theme.value = prefersDark ? 'dark' : 'light'
  }

  document.documentElement.setAttribute('data-theme', theme.value)
})
</script>

<template>
  <button
    class="theme-toggle"
    type="button"
    :aria-label="theme === 'dark' ? 'Ativar modo claro' : 'Ativar modo escuro'"
    :title="theme === 'dark' ? 'Ativar modo claro' : 'Ativar modo escuro'"
    @click="toggleTheme"
  >
    <span class="theme-toggle-icon" aria-hidden="true">
      <BsLightbulbFill />
    </span>
    <span class="theme-toggle-label">{{ theme === 'dark' ? 'Claro' : 'Escuro' }}</span>
  </button>
</template>

<style scoped>
.theme-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 38px;
  padding: 5px 12px 5px 7px;
  color: var(--color-text);
  background: var(--color-surface-soft);
  border: 1px solid var(--color-border);
  border-radius: 999px;
  cursor: pointer;
  font: inherit;
  font-size: 0.78rem;
  font-weight: 700;
  transition:
    color 0.25s ease,
    background-color 0.25s ease,
    border-color 0.25s ease,
    transform 0.25s ease;
}

.theme-toggle:hover {
  color: var(--color-brand);
  border-color: var(--color-brand);
  transform: translateY(-1px);
}

.theme-toggle:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: 3px;
}

.theme-toggle-icon {
  display: grid;
  width: 26px;
  height: 26px;
  place-items: center;
  color: var(--color-brand);
  background: color-mix(in srgb, var(--color-brand) 16%, transparent);
  border-radius: 50%;
  transition:
    color 0.25s ease,
    background-color 0.25s ease,
    transform 0.35s ease;
}

.theme-toggle-icon :deep(svg) {
  width: 15px;
  height: 15px;
}

.theme-toggle:hover .theme-toggle-icon {
  background: var(--color-brand);
  color: var(--color-surface);
  transform: rotate(-12deg) scale(1.08);
}

@media (max-width: 760px) {
  .theme-toggle {
    padding-right: 7px;
  }

  .theme-toggle-label {
    display: none;
  }
}
</style>
