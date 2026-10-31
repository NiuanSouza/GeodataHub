<script setup lang="ts">
import { computed } from 'vue'
import { FiCheckCircle, FiXCircle, FiClock, FiAlertTriangle } from 'vue-icons-plus/fi'
import type { ProcessLogDetails } from '../../services/process'
import GateDecisaoGestor from '../GateDecisaoGestor.vue'

const props = defineProps<{
  details: ProcessLogDetails
  readOnly?: boolean
}>()

const emit = defineEmits<{
  (e: 'open-quarantine'): void
  (e: 'reload'): void
}>()

/** Erro ou quarentena na validação ocorre quando o status é de erro/quarentena, há registros de quarentena ou checks falharam */
const isQuarantine = computed(
  () =>
    props.details.status === 'Em quarentena' ||
    (props.details.quarantineRecords?.length ?? 0) > 0 ||
    checks.value.some((c) => c.status === 'error'),
)

const isError = computed(
  () =>
    props.details.status === 'Falhou' ||
    isQuarantine.value,
)

const isUnderCorrection = computed(
  () => props.details.status === 'Em andamento' && !!props.details.assignedEditor,
)

/** Checks vêm da API via details.validationChecks (GET /api/processos/{id}) */
const checks = computed(() => props.details.validationChecks ?? [])

const overallStatus = computed(() => {
  if (isUnderCorrection.value) return { label: 'Em Correção (Editor Alocado)', cls: 'warning' }
  const errorCount = checks.value.filter((c) => c.status === 'error').length
  if (errorCount > 0) return { label: `${errorCount} Erro(s) Detectado(s)`, cls: 'error' }
  if (props.details.status === 'Falhou') return { label: 'Falhou', cls: 'error' }
  if (isQuarantine.value) return { label: 'Em Quarentena', cls: 'error' }
  if (props.details.stage === 'Validação' && props.details.status === 'Em andamento') {
    return { label: 'Em Validação', cls: 'warning' }
  }
  return { label: 'Concluído (Sem Erros)', cls: 'success' }
})
</script>

<template>
  <div class="stage-container">
    <!-- Gate de Decisão do Gestor na Validação -->
    <GateDecisaoGestor
      :processo-id="details.id"
      etapa="VALIDACAO"
      :read-only="readOnly"
      :current-status="details.status"
      :current-stage="details.stage"
      :processamento-concluido="details.processamentoConcluido"
      :progresso-processamento="details.progressoProcessamento"
      @decisao-registrada="emit('reload')"
    />

    <div class="card">
      <div class="card-header">
        <div class="header-left">
          <h3>Validação</h3>
          <p class="header-desc">
            Verificações automáticas executadas pelo backend sobre os dados ingeridos.
          </p>
        </div>
        <div class="header-right">
          <span class="badge" :class="overallStatus.cls">{{ overallStatus.label }}</span>
          <button v-if="isQuarantine" class="btn btn-warning" @click="emit('open-quarantine')">
            <FiAlertTriangle size="14" />
            Ver Quarentena
          </button>
        </div>
      </div>

      <div class="card-body">
        <ul class="checklist">
          <li v-for="check in checks" :key="check.id" class="checklist-item" :class="check.status">
            <div class="check-icon" :class="check.status">
              <FiCheckCircle v-if="check.status === 'success'" size="18" />
              <FiXCircle v-else-if="check.status === 'error'" size="18" />
              <FiClock v-else size="18" />
            </div>
            <div class="check-body">
              <strong>{{ check.label }}</strong>
              <span class="check-desc">{{ check.description }}</span>
              <span class="check-detail" :class="check.status">{{ check.detail }}</span>
            </div>
            <span class="check-badge" :class="check.status">
              {{
                check.status === 'success' ? 'OK' : check.status === 'error' ? 'Erro' : 'Pendente'
              }}
            </span>
          </li>
        </ul>

        <div v-if="isQuarantine && details.pauseReason" class="error-alert">
          <div class="alert-icon">
            <FiAlertTriangle size="22" />
          </div>
          <div class="alert-content">
            <strong>Registros em Quarentena (Desvio Lateral)</strong>
            <p>{{ details.pauseReason }}</p>
            <p style="font-size: 0.82rem; margin-top: 0.35rem; opacity: 0.7">
              O editor deve corrigir o arquivo e subir novamente via upload.
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.stage-container {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.card {
  background: var(--color-surface);
  border-radius: 12px;
  border: 1px solid var(--color-border);
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 1.5rem;
  border-bottom: 1px solid var(--color-border);
  gap: 1rem;
}

.header-left h3 {
  margin: 0 0 0.25rem 0;
  color: var(--color-heading);
  font-size: 1.15rem;
}

.header-desc {
  margin: 0;
  font-size: 0.85rem;
  opacity: 0.6;
  color: var(--color-text);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-shrink: 0;
}

.card-body {
  padding: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

/* CHECKLIST */
.checklist {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.checklist-item {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem 1.25rem;
  border-radius: 8px;
  border: 1px solid var(--color-border);
  background: var(--color-background);
  transition: background 0.2s;
}

.checklist-item.success {
  border-color: rgba(22, 163, 74, 0.25);
  background: rgba(22, 163, 74, 0.04);
}

.checklist-item.error {
  border-color: rgba(220, 53, 69, 0.25);
  background: rgba(220, 53, 69, 0.04);
}

.check-icon {
  flex-shrink: 0;
  width: 2rem;
  height: 2rem;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
}

.check-icon.success {
  color: #16a34a;
  background: rgba(22, 163, 74, 0.1);
}

.check-icon.error {
  color: #dc3545;
  background: rgba(220, 53, 69, 0.1);
}

.check-icon.pending {
  color: #a16207;
  background: rgba(234, 179, 8, 0.1);
}

.check-body {
  flex-grow: 1;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.check-body strong {
  font-size: 0.95rem;
  color: var(--color-heading);
}

.check-desc {
  font-size: 0.82rem;
  color: var(--color-text);
  opacity: 0.6;
}

.check-detail {
  font-size: 0.85rem;
  font-weight: 500;
  margin-top: 0.1rem;
}

.check-detail.success {
  color: #16a34a;
}
.check-detail.error {
  color: #dc3545;
}

/* BADGES */
.badge {
  padding: 0.3rem 0.75rem;
  border-radius: 50px;
  font-size: 0.78rem;
  font-weight: 600;
  white-space: nowrap;
}

.badge.success {
  background: rgba(22, 163, 74, 0.12);
  color: #16a34a;
  border: 1px solid rgba(22, 163, 74, 0.25);
}

.badge.error {
  background: rgba(220, 53, 69, 0.1);
  color: #dc3545;
  border: 1px solid rgba(220, 53, 69, 0.2);
}

.check-badge {
  flex-shrink: 0;
  padding: 0.2rem 0.6rem;
  border-radius: 50px;
  font-size: 0.75rem;
  font-weight: 700;
}

.check-badge.success {
  background: rgba(22, 163, 74, 0.12);
  color: #16a34a;
}
.check-badge.error {
  background: rgba(220, 53, 69, 0.1);
  color: #dc3545;
}

/* ERROR ALERT */
.error-alert {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  padding: 1rem 1.25rem;
  border-radius: 8px;
  background: rgba(220, 53, 69, 0.06);
  border: 1px solid rgba(220, 53, 69, 0.3);
  border-left: 4px solid #dc3545;
}

.alert-icon {
  flex-shrink: 0;
  color: #dc3545;
  padding-top: 0.1rem;
}

.alert-content strong {
  display: block;
  color: #dc3545;
  font-size: 0.95rem;
  margin-bottom: 0.25rem;
}

.alert-content p {
  margin: 0;
  font-size: 0.88rem;
  opacity: 0.8;
  color: var(--color-text);
}

/* BUTTONS */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.5rem 1rem;
  border-radius: 6px;
  font-weight: 600;
  font-size: 0.88rem;
  cursor: pointer;
  border: none;
  transition: all 0.2s;
}

.btn-warning {
  background-color: rgba(234, 179, 8, 0.15);
  color: #92400e;
  border: 1px solid rgba(234, 179, 8, 0.4);
}

.btn-warning:hover {
  background-color: #f59e0b;
  color: #fff;
}
</style>
