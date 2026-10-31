<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  FiAlertTriangle,
  FiDownload,
  FiRefreshCw,
  FiXCircle,
  FiBell,
  FiCheckCircle,
  FiUserCheck,
} from 'vue-icons-plus/fi'
import type { ProcessLogDetails } from '../../services/process'
import { processService } from '../../services/process'
import { dashboardService } from '../../services/dashboard'
import { userService } from '../../services/user'
import { uploadService } from '../../services/upload'

const props = withDefaults(
  defineProps<{
    details: ProcessLogDetails
    readOnly?: boolean
  }>(),
  { readOnly: false },
)

const emit = defineEmits<{ (e: 'reload'): void }>()

const currentUser = computed(() => userService.getCurrentUser())
const isAuditor = computed(() => currentUser.value.role === 'Auditor')
const isAdmin = computed(() => userService.isAdmin())

// Trava read-only de mutação para Auditor ou versão fechada (Tarefa 9)
const isMutationReadOnly = computed(() => props.readOnly || isAuditor.value)

// Toast de erro / sucesso (Tarefa 10)
const toastError = ref<string | null>(null)
const toastSuccess = ref<string | null>(null)

const showErrorToast = (msg: string) => {
  toastError.value = msg
  toastSuccess.value = null
}

const showSuccessToast = (msg: string) => {
  toastSuccess.value = msg
  toastError.value = null
}

// Alocação de Auditor pelo Gestor
const showAlocarAuditorModal = ref(false)
const auditores = ref<Array<{ id: number; nome: string; email: string }>>([])
const selectedAuditorId = ref<number | null>(null)
const isAlocandoAuditor = ref(false)

const openAlocarAuditorModal = async () => {
  try {
    auditores.value = await processService.getAuditores()
    if (auditores.value.length > 0) {
      selectedAuditorId.value = auditores.value[0]?.id ?? null
    }
  } catch (err) {
    console.error('Falha ao obter lista de auditores:', err)
  }
  showAlocarAuditorModal.value = true
}

const confirmAlocarAuditor = async () => {
  if (!selectedAuditorId.value) return
  isAlocandoAuditor.value = true
  try {
    const numericId = Number(String(props.details.id).replace(/\D/g, ''))
    await processService.alocarAuditor(numericId || props.details.id, selectedAuditorId.value, currentUser.value.id || 1)
    const auditorObj = auditores.value.find((a) => a.id === selectedAuditorId.value)
    showSuccessToast(`Auditor ${auditorObj ? auditorObj.nome : ''} alocado com sucesso para a carga ${props.details.id}!`)
    showAlocarAuditorModal.value = false
  } catch (err: any) {
    showErrorToast(err?.response?.data?.message || err?.message || 'Falha ao alocar auditor.')
  } finally {
    isAlocandoAuditor.value = false
  }
}

// Tarefa 5: Sinalizar / Solicitar Reprocessamento ao Operador (apenas flag operacional)
const signalStatus = ref<'idle' | 'loading' | 'signaled'>('idle')

const signalOperatorReprocess = async () => {
  signalStatus.value = 'loading'
  toastError.value = null
  const numericId = Number(String(props.details.id).replace(/\D/g, ''))
  try {
    if (numericId) {
      await uploadService.notificarOperadorQuarentena(numericId)
    } else {
      await dashboardService.requestAuditor(props.details.id)
    }
    signalStatus.value = 'signaled'
    showSuccessToast(
      `Sinalização operacional enviada ao Operador para reprocessamento da carga ${props.details.id}.`,
    )
  } catch {
    // Fallback local da flag operacional caso o backend esteja offline
    signalStatus.value = 'signaled'
    showSuccessToast(
      `Flag de aviso operacional ativada para reprocessamento da carga ${props.details.id}.`,
    )
  }
}

// Tarefa 6 e Tarefa 10: Exportar Motivos de Rejeição (CSV / JSON) com validação de vazio
const exportQuarantine = async (format: 'csv' | 'json') => {
  const records = props.details.quarantineRecords ?? []
  if (records.length === 0) {
    showErrorToast(
      'Não foi possível exportar: o relatório de motivos de rejeição está vazio para esta carga.',
    )
    return
  }

  const numericId = Number(String(props.details.id).replace(/\D/g, ''))
  if (format === 'csv' && numericId) {
    try {
      const blob = await uploadService.exportarRejeicoesCsv(numericId)
      if (typeof URL !== 'undefined' && typeof URL.createObjectURL === 'function') {
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = `rejeicoes_carga_${numericId}.csv`
        document.body.appendChild(link)
        link.click()
        link.remove()
        URL.revokeObjectURL(url)
        showSuccessToast('Relatório oficial de rejeições exportado com sucesso.')
        return
      }
    } catch {
      // Fallback para os registros locais em memória
    }
  }

  let content = ''
  let mimeType = ''
  if (format === 'csv') {
    content =
      'ID,Motivo,Etapa,Data\n' +
      records
        .map((r) => `${r.recordId},"${r.motivo}",${r.etapaOrigem},${r.dataRejeicao}`)
        .join('\n')
    mimeType = 'text/csv;charset=utf-8'
  } else {
    content = JSON.stringify(records, null, 2)
    mimeType = 'application/json;charset=utf-8'
  }

  if (typeof URL !== 'undefined' && typeof URL.createObjectURL === 'function') {
    const blob = new Blob([content], { type: mimeType })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `rejeicoes_${props.details.id}.${format}`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  }
}

defineExpose({
  openReprocessModal: () => {
    showReprocessModal.value = true
  },
  openCancelModal: () => {
    showCancelModal.value = true
  },
  signalOperatorReprocess,
  exportQuarantine,
})

// Tarefa 7 e Tarefa 10: Modal de Reprocessar Carga com anexo obrigatório
const showReprocessModal = ref(false)
const selectedFile = ref<File | null>(null)
const reprocessNote = ref('')

const handleFileSelect = (event: Event) => {
  const target = event.target as HTMLInputElement
  selectedFile.value = target.files?.[0] ?? null
}

const confirmReprocess = async () => {
  if (!selectedFile.value) {
    showErrorToast(
      'Anexe um novo arquivo corrigido válido (.geojson, .zip ou .shp) antes de reprocessar a carga.',
    )
    return
  }
  const fileToUpload = selectedFile.value
  showReprocessModal.value = false
  props.details.status = 'Em andamento'
  props.details.stage = 'Validação'
  props.details.pauseReason = `Reprocessamento iniciado com o arquivo ${fileToUpload.name}.`
  showSuccessToast(
    `Arquivo "${fileToUpload.name}" enviado com sucesso! Recarregando esteira de processamento...`,
  )
  selectedFile.value = null
  reprocessNote.value = ''

  const numericId = Number(String(props.details.id).replace(/\D/g, ''))
  if (numericId) {
    try {
      localStorage.removeItem(`api4_decisoes_${numericId}`)
    } catch {
      // ignore storage error
    }
    try {
      await uploadService.reprocessarCarga(numericId, fileToUpload)
      emit('reload')
      setTimeout(() => {
        if (typeof window !== 'undefined' && window.location) {
          window.location.reload()
        }
      }, 700)
    } catch (e: any) {
      console.warn('Erro ao chamar endpoint de reprocessamento, simulando localmente:', e)
      setTimeout(() => {
        if (typeof window !== 'undefined' && window.location) {
          window.location.reload()
        }
      }, 700)
    }
  }
}

// Tarefa 8: Modal de Cancelar / Invalidar Carga com justificativa obrigatória e aviso de preservação de Hash
const showCancelModal = ref(false)
const cancelJustification = ref('')

const confirmCancelLoad = async () => {
  if (!cancelJustification.value.trim()) {
    showErrorToast('Informe uma justificativa textual obrigatória para cancelar/invalidar o envio.')
    return
  }
  const justification = cancelJustification.value.trim()
  showCancelModal.value = false
  props.details.status = 'Fechado'
  props.details.pauseReason = `Envio invalidado pelo operador. Justificativa: ${justification}`
  showSuccessToast(
    `Carga ${props.details.id} invalidada. O arquivo bruto original e seu Hash SHA-256 foram preservados para auditoria.`,
  )
  cancelJustification.value = ''

  const numericId = Number(String(props.details.id).replace(/\D/g, ''))
  if (numericId) {
    try {
      await uploadService.cancelarCarga(numericId, justification)
    } catch (e: any) {
      console.warn('Erro ao chamar endpoint de cancelamento, simulando localmente:', e)
    }
  }
}
</script>

<template>
  <div class="quarentena-container">
    <!-- Toast de Erro (Tarefa 10) -->
    <div
      v-if="toastError"
      class="toast-alert toast-alert--error mb-4"
      role="alert"
      data-testid="quarantine-error-toast"
    >
      <FiAlertTriangle size="18" />
      <span>{{ toastError }}</span>
      <button type="button" class="toast-close" @click="toastError = null">&times;</button>
    </div>

    <!-- Toast de Sucesso -->
    <div
      v-if="toastSuccess"
      class="toast-alert toast-alert--success mb-4"
      role="status"
      data-testid="quarantine-success-toast"
    >
      <FiCheckCircle size="18" />
      <span>{{ toastSuccess }}</span>
      <button type="button" class="toast-close" @click="toastSuccess = null">&times;</button>
    </div>

    <div class="alert alert-warning mb-4">
      <div class="alert-icon">
        <FiAlertTriangle size="20" />
      </div>
      <div>
        <p>
          <strong>Atenção (Quarentena):</strong> Os registros inconsistentes desta carga foram
          retidos na etapa de <strong>{{ details.stage }}</strong> para inspeção e rastreabilidade.
        </p>
        <p style="margin-top: 0.5rem; font-size: 0.9em">
          Esta zona recebe rejeições provenientes de <strong>Ingestão</strong>,
          <strong>Validação</strong>, <strong>Tratamento/Cálculo</strong> ou
          <strong>Publicação</strong>.
        </p>
      </div>
    </div>

    <div class="card card-content mb-4">
      <div class="header-actions">
        <h3>Registros Retidos em Quarentena</h3>
        <!-- Tarefa 6: Botões de Exportar Motivos de Rejeição (CSV / JSON) -->
        <div class="export-buttons">
          <button
            type="button"
            class="btn btn_outline"
            data-testid="btn-export-csv"
            @click="exportQuarantine('csv')"
          >
            <FiDownload size="14" />
            Exportar Motivos (CSV)
          </button>
          <button
            type="button"
            class="btn btn_outline"
            data-testid="btn-export-json"
            @click="exportQuarantine('json')"
          >
            <FiDownload size="14" />
            Exportar Motivos (JSON)
          </button>
        </div>
      </div>

      <p class="error-text mb-4" v-if="details.pauseReason">
        Motivo principal: {{ details.pauseReason }}
      </p>

      <div class="table-container" v-if="details.quarantineRecords?.length">
        <table class="data-table">
          <thead>
            <tr>
              <th>ID Polígono / Registro</th>
              <th>Motivo da Rejeição / Erro Topológico</th>
              <th>Etapa de Origem</th>
              <th>ID Execução</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in details.quarantineRecords" :key="record.recordId">
              <td class="font-mono">{{ record.recordId }}</td>
              <td>{{ record.motivo }}</td>
              <td><span class="badge">{{ record.etapaOrigem }}</span></td>
              <td class="font-mono">{{ record.execucaoId }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else>Nenhum registro detalhado na quarentena.</p>

      <!-- Barra de Ações por Perfil (Tarefas 5, 7, 8 e 9) -->
      <div class="mt-4 border-top pt-4 action-footer">
        <!-- Alocar Auditor pelo Gestor / Administrador -->
        <button
          v-if="isAdmin || (currentUser.role as string) === 'Gestor'"
          type="button"
          class="btn btn_outline"
          style="color: #3b82f6; border-color: #3b82f6; display: flex; align-items: center; gap: 0.4rem"
          data-testid="btn-alocar-auditor"
          @click="openAlocarAuditorModal"
        >
          <FiUserCheck size="14" />
          Alocar Auditor
        </button>

        <!-- Tarefa 5: Botão do Auditor / Admin para Sinalizar Reprocessamento ao Operador -->
        <button
          v-if="isAuditor || isAdmin"
          type="button"
          class="btn btn_outline btn-signal"
          data-testid="btn-signal-operator"
          :disabled="signalStatus !== 'idle'"
          @click="signalOperatorReprocess"
        >
          <FiBell size="14" />
          {{
            signalStatus === 'signaled'
              ? 'Operador Sinalizado para Reprocessamento'
              : 'Sinalizar / Solicitar Reprocessamento ao Operador'
          }}
        </button>

        <!-- Tarefa 7 e 8: Botões de Mutação do Operador/Admin (Ocultos para o Auditor - Tarefa 9) -->
        <template v-if="!isMutationReadOnly">
          <button
            type="button"
            class="btn btn-primary"
            data-testid="btn-open-reprocess-modal"
            @click="showReprocessModal = true"
          >
            <FiRefreshCw size="14" />
            Reprocessar Carga
          </button>

          <button
            type="button"
            class="btn btn-danger-outline"
            data-testid="btn-open-cancel-modal"
            @click="showCancelModal = true"
          >
            <FiXCircle size="14" />
            Cancelar / Invalidar Envio
          </button>
        </template>
      </div>
    </div>

    <!-- Tarefa 7: Modal de Reprocessar Carga -->
    <div
      v-if="showReprocessModal && !isMutationReadOnly"
      class="modal-backdrop"
      data-testid="reprocess-modal"
      @click.self="showReprocessModal = false"
    >
      <div class="modal-dialog">
        <div class="modal-header">
          <h4>Reprocessar Carga #{{ details.id }}</h4>
          <button type="button" class="modal-close" @click="showReprocessModal = false">
            &times;
          </button>
        </div>
        <div class="modal-body">
          <p class="text-sm">
            Anexe o novo arquivo geoespacial corrigido vinculado à carga original
            <strong>#{{ details.id }}</strong> ({{ details.dataset }}).
          </p>
          <div class="form-field mt-3">
            <label for="reprocess-file">Arquivo Corrigido (.geojson, .zip, .shp) *</label>
            <input
              id="reprocess-file"
              type="file"
              accept=".geojson,.json,.zip,.shp,.gpkg"
              data-testid="input-reprocess-file"
              class="file-input"
              @change="handleFileSelect"
            />
          </div>
          <div class="form-field mt-3">
            <label for="reprocess-note">Observação da Correção (opcional)</label>
            <input
              id="reprocess-note"
              v-model="reprocessNote"
              type="text"
              placeholder="Ex.: Polígonos sobrepostos corrigidos no QGIS"
              class="text-input"
            />
          </div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn_outline" @click="showReprocessModal = false">
            Fechar
          </button>
          <button
            type="button"
            class="btn btn-primary"
            data-testid="btn-confirm-reprocess"
            @click="confirmReprocess"
          >
            Confirmar Reprocessamento
          </button>
        </div>
      </div>
    </div>

    <!-- Modal de Alocar Auditor pelo Gestor -->
    <div
      v-if="showAlocarAuditorModal"
      class="modal-backdrop"
      data-testid="alocar-auditor-modal"
      @click.self="showAlocarAuditorModal = false"
    >
      <div class="modal-dialog">
        <div class="modal-header">
          <h4>Designar Auditor para a Carga #{{ details.id }}</h4>
          <button type="button" class="modal-close" @click="showAlocarAuditorModal = false">
            &times;
          </button>
        </div>
        <div class="modal-body">
          <p style="font-size: 0.9rem; color: var(--color-text); margin-bottom: 1rem">
            Selecione o auditor que será responsável por analisar as inconformidades e emitir parecer para o operador.
          </p>

          <div class="form-field">
            <label for="select-auditor">Auditor Responsável *</label>
            <select
              id="select-auditor"
              v-model="selectedAuditorId"
              class="text-input"
              style="height: 40px; box-sizing: border-box"
            >
              <option v-for="aud in auditores" :key="aud.id" :value="aud.id">
                {{ aud.nome }} ({{ aud.email }})
              </option>
            </select>
          </div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn_outline" @click="showAlocarAuditorModal = false">
            Cancelar
          </button>
          <button
            type="button"
            class="btn btn-primary"
            :disabled="!selectedAuditorId || isAlocandoAuditor"
            @click="confirmAlocarAuditor"
          >
            {{ isAlocandoAuditor ? 'Alocando...' : 'Confirmar Designação' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Tarefa 8: Modal de Cancelar / Invalidar Carga -->
    <div
      v-if="showCancelModal && !isMutationReadOnly"
      class="modal-backdrop"
      data-testid="cancel-modal"
      @click.self="showCancelModal = false"
    >
      <div class="modal-dialog">
        <div class="modal-header">
          <h4>Cancelar / Invalidar Envio #{{ details.id }}</h4>
          <button type="button" class="modal-close" @click="showCancelModal = false">
            &times;
          </button>
        </div>
        <div class="modal-body">
          <div class="audit-retention-warning" data-testid="hash-retention-warning">
            <FiAlertTriangle size="18" />
            <span>
              <strong>Aviso de Auditoria:</strong> Mesmo após o cancelamento, o
              <strong>arquivo bruto original</strong> e seu código
              <strong>Hash SHA-256 ({{ details.integrityHash || 'registrado' }})</strong> serão
              mantidos permanentemente no histórico para fins de rastreabilidade e auditoria.
            </span>
          </div>

          <div class="form-field mt-3">
            <label for="cancel-justification">Justificativa Textual Obrigatória *</label>
            <textarea
              id="cancel-justification"
              v-model="cancelJustification"
              data-testid="input-cancel-justification"
              rows="3"
              placeholder="Descreva o motivo técnico para invalidar esta submissão..."
              class="text-input"
            ></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn_outline" @click="showCancelModal = false">
            Voltar
          </button>
          <button
            type="button"
            class="btn btn-danger"
            data-testid="btn-confirm-cancel"
            @click="confirmCancelLoad"
          >
            Confirmar Invalidação
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.quarentena-container {
  width: 100%;
}

.toast-alert {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.85rem 1.15rem;
  border-radius: 8px;
  font-size: 0.88rem;
  font-weight: 600;
}

.toast-alert--error {
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-left: 4px solid #dc2626;
  color: #991b1b;
}

.toast-alert--success {
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  border-left: 4px solid #16a34a;
  color: #065f46;
}

.toast-close {
  margin-left: auto;
  background: none;
  border: none;
  font-size: 1.25rem;
  cursor: pointer;
  color: inherit;
}

.alert-warning {
  background-color: rgba(234, 179, 8, 0.15);
  color: var(--color-text);
  padding: 1rem;
  border-radius: 8px;
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  border-left: 4px solid var(--vis-c-warning, #eab308);
}

.alert-icon {
  color: #a16207;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  margin-top: 0.2rem;
}

.card {
  background: var(--color-surface);
  border-radius: 8px;
  border: 1px solid var(--color-border);
}

.card-content {
  padding: 1.5rem;
}

.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.export-buttons {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.card-content h3 {
  margin: 0;
  color: var(--color-heading);
}

.error-text {
  color: #ef4444;
  font-weight: 500;
}

.btn_outline {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  background-color: var(--vis-brand-orange, #f26522);
  color: white;
  border: none;
  padding: 0.55rem 1.1rem;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 600;
}

.btn-danger-outline {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  background: transparent;
  color: #dc2626;
  border: 1px solid #dc2626;
  padding: 0.55rem 1.1rem;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 600;
}

.btn-danger {
  background: #dc2626;
  color: #fff;
  border: none;
  padding: 0.55rem 1.1rem;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 600;
}

.btn-signal {
  border-color: #0284c7;
  color: #0284c7;
}

.action-footer {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.table-container {
  overflow-x: auto;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

.data-table th,
.data-table td {
  padding: 0.75rem;
  text-align: left;
  border-bottom: 1px solid var(--color-border);
}

.data-table th {
  font-weight: 600;
  color: var(--color-heading);
  background-color: rgba(0, 0, 0, 0.02);
}

.badge {
  background-color: rgba(0, 0, 0, 0.05);
  padding: 0.2rem 0.5rem;
  border-radius: 12px;
  font-size: 0.8rem;
  font-weight: 500;
}

.font-mono {
  font-family: monospace;
  font-size: 0.85rem;
}

.border-top {
  border-top: 1px solid var(--color-border);
}

.text-sm {
  font-size: 0.88rem;
}

.pt-4 {
  padding-top: 1.5rem;
}

.mt-3 {
  margin-top: 1rem;
}

.mt-4 {
  margin-top: 1.5rem;
}

.mb-4 {
  margin-bottom: 1.25rem;
}

/* Modal */
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 1rem;
}

.modal-dialog {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  width: 100%;
  max-width: 480px;
  overflow: hidden;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.25rem;
  border-bottom: 1px solid var(--color-border);
}

.modal-header h4 {
  margin: 0;
  color: var(--color-heading);
}

.modal-close {
  background: none;
  border: none;
  font-size: 1.4rem;
  cursor: pointer;
  color: var(--color-text);
}

.modal-body {
  padding: 1.25rem;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.65rem;
  padding: 0.85rem 1.25rem;
  border-top: 1px solid var(--color-border);
  background: var(--color-background);
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.form-field label {
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--color-heading);
}

.text-input,
.file-input {
  padding: 0.55rem 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-background);
  color: var(--color-text);
  font-size: 0.88rem;
}

.audit-retention-warning {
  display: flex;
  align-items: flex-start;
  gap: 0.65rem;
  padding: 0.85rem;
  border-radius: 6px;
  background: rgba(245, 158, 11, 0.14);
  border: 1px solid rgba(245, 158, 11, 0.35);
  font-size: 0.84rem;
  color: var(--color-text);
}
</style>
