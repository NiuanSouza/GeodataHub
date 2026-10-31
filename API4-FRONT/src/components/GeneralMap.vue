<script setup lang="ts">
const props = defineProps<{
  coordinates?: [number, number][]
}>()
</script>

<template>
  <div class="map-placeholder">
    <!-- Componente de Mapa em branco conforme solicitado -->
    <p>Mapa</p>
  </div>
</template>

<style scoped>
.map-placeholder {
  height: 300px;
  width: 100%;
  border-radius: 8px;
  background: var(--color-surface-soft, #f4f4f4);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text);
  opacity: 0.6;
}
</style>
