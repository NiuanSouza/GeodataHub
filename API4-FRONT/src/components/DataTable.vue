<script setup lang="ts">
import { ref } from 'vue'

export interface Column {
  key: string
  label: string
}

const props = defineProps<{
  columns: Column[]
  data: any[]
  rowKey?: string
  rowTestIdKey?: string
}>()

const emit = defineEmits(['row-click'])

const expandedRows = ref<Set<string | number>>(new Set())

const toggleRow = (id: string | number) => {
  const newSet = new Set(expandedRows.value)
  if (newSet.has(id)) {
    newSet.delete(id)
  } else {
    newSet.add(id)
  }
  expandedRows.value = newSet
}

const getKey = (item: any, index: number) => {
  if (props.rowKey && item[props.rowKey] !== undefined) {
    return item[props.rowKey]
  }
  if (item.id !== undefined) return item.id
  return index
}

const getRowTestId = (item: any) => {
  if (props.rowTestIdKey && item[props.rowTestIdKey] !== undefined) {
    return item[props.rowTestIdKey]
  }
  return item.rowTestId || undefined
}

const handleRowClick = (item: any, event: Event) => {
  if ((event.target as HTMLElement).closest('.table-actions')) return
  emit('row-click', item)
  toggleRow(getKey(item, 0))
}
</script>

<template>
  <div class="datatable-container">
    <table class="component-table">
      <thead>
        <tr>
          <th v-for="col in columns" :key="col.key">
            {{ col.label }}
          </th>
        </tr>
      </thead>
      <tbody>
        <template v-for="(item, index) in data" :key="getKey(item, index)">
          <tr
            @click="handleRowClick(item, $event)"
            class="clickable-row"
            :data-testid="getRowTestId(item)"
          >
            <td v-for="col in columns" :key="col.key">
              <slot :name="'cell-' + col.key" :item="item" :value="item[col.key]" :isExpanded="expandedRows.has(getKey(item, index))">
                {{ item[col.key] }}
              </slot>
            </td>
          </tr>

          <tr v-if="expandedRows.has(getKey(item, index))" class="expanded-row">
            <td :colspan="columns.length" class="expanded-cell">
              <div class="expanded-content-wrapper">
                <slot name="expanded-row" :item="item">
                  <div class="p-4 text-center">Nenhum detalhe adicional.</div>
                </slot>
              </div>
            </td>
          </tr>
        </template>

        <tr v-if="data.length === 0">
          <td :colspan="columns.length" class="empty-state">Nenhum registro encontrado.</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.datatable-container {
  width: 100%;
  overflow-x: auto;
  border-radius: 8px;
  border: 1px solid var(--color-border);
}

.component-table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
  background: var(--color-surface);
}

.component-table th {
  padding: 1rem 1.25rem;
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--color-heading);
  border-bottom: 2px solid var(--color-border);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  background-color: var(--color-background-soft);
}

.component-table td {
  padding: 1rem 1.25rem;
  font-size: 0.9rem;
  color: var(--color-text);
  border-bottom: 1px solid var(--color-border);
  vertical-align: middle;
}

.clickable-row {
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.clickable-row:hover {
  background-color: var(--color-background-mute);
}

.expanded-row td.expanded-cell {
  padding: 0;
  border-bottom: 1px solid var(--color-border);
  background-color: #fafafa;
}

@media (prefers-color-scheme: dark) {
  .expanded-row td.expanded-cell {
    background-color: rgba(255, 255, 255, 0.02);
  }
}

.expanded-content-wrapper {
  padding: 1.5rem;
  box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.02);
}

.empty-state {
  text-align: center;
  padding: 3rem;
  color: var(--color-text);
  font-style: italic;
  opacity: 0.7;
}

:deep(.badge) {
  display: inline-flex;
  align-items: center;
  padding: 0.25rem 0.75rem;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 600;
  line-height: 1;
}

:deep(.table-actions) {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}
</style>
