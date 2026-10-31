<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  processService,
  type IndicatorExportFormat,
  type ProcessLogDetails,
  type ProcessTraceability,
} from '../services/process'
import { userService } from '../services/user'
import { dashboardService } from '../services/dashboard'
import { uploadService } from '../services/upload'
import Stepper from '../components/Stepper.vue'
import Auditoria from '../components/Auditoria.vue'
import ProcessStageIngestao from '../components/process-stages/ProcessStageIngestao.vue'
import ProcessStageValidacao from '../components/process-stages/ProcessStageValidacao.vue'
import ProcessStageTratamento from '../components/process-stages/ProcessStageTratamento.vue'
import ProcessStagePublicacao from '../components/process-stages/ProcessStagePublicacao.vue'
import ProcessStageQuarentena from '../components/process-stages/ProcessStageQuarentena.vue'
import {
  auditoriaService,
  type ProcessVersion,
  type AuditProcessCarga,
} from '@/services/auditoria'

interface Props {
  processId?: string
  detailsData?: ProcessLogDetails | null
}

const props = defineProps<Props>()

const route = useRoute()
const router = useRouter()

const AUDIT_STORAGE_KEY = 'audit_panel_open'

const getInitialAuditOpenState = (): boolean => {
  try {
    const saved = localStorage.getItem(AUDIT_STORAGE_KEY)
    return saved !== null ? saved !== 'false' : true
  } catch {
    return true
  }
}

const isAuditOpen = ref<boolean>(getInitialAuditOpenState())

const saveAuditOpenState = (open: boolean) => {
  isAuditOpen.value = open
  try {
    localStorage.setItem(AUDIT_STORAGE_KEY, String(open))
  } catch {
    // Ignora erro caso localStorage esteja indisponível
  }
}

const toggleAuditPanel = () => {
  saveAuditOpenState(!isAuditOpen.value)
}

const closeAuditPanel = () => {
  saveAuditOpenState(false)
}

const processId = computed(() => props.processId || (route?.params?.id as string) || '')
const isLoading = ref(!props.detailsData)
const details = ref<ProcessLogDetails | null>(props.detailsData || null)

// Controle do componente Auditoria
const auditVersions = ref<ProcessVersion[]>([])
const currentCarga = ref<AuditProcessCarga | null>(null)
const currentCargaLinhagem = computed(() => currentCarga.value?.linhagemCompleta || null)
const quarentenaStageRef = ref<any>(null)
const currentUser = computed(() => userService.getCurrentUser())

// Rastreabilidade e Exportação (US05 e US07)
const traceability = ref<ProcessTraceability | null>(null)
const isTraceabilityLoading = ref(false)
const traceabilityError = ref('')
const exportError = ref('')
const exportingFormat = ref<IndicatorExportFormat | null>(null)
let traceabilityRequestId = 0

const currentCargaId = computed(() => {
  return details.value?.id || currentCarga.value?.id || processId.value
})

const isBackendProcess = computed(
  () =>
    details.value?.dataSource === 'API' &&
    /^\d+$/.test(String(details.value.id)),
)

const isClosed = computed(() => details.value?.status?.toLowerCase().includes('fechad') ?? false)
const isAuditor = computed(() => userService.getCurrentUser().role === 'Auditor')
const isReadOnly = computed(() => isClosed.value || isAuditor.value)

const loadTraceability = async (id: string, backendProcess: boolean) => {
  const requestId = ++traceabilityRequestId
  traceability.value = null
  traceabilityError.value = ''
  if (!backendProcess) {
    isTraceabilityLoading.value = false
    return
  }

  isTraceabilityLoading.value = true
  try {
    const result = await processService.getTraceability(id)
    if (requestId === traceabilityRequestId) traceability.value = result
  } catch (error: any) {
    if (requestId === traceabilityRequestId) {
      traceabilityError.value =
        error?.response?.status === 404
          ? 'A linhagem deste processo ainda não está disponível.'
          : 'Não foi possível carregar a linhagem do arquivo.'
    }
  } finally {
    if (requestId === traceabilityRequestId) isTraceabilityLoading.value = false
  }
}

const exportIndicator = async (format: IndicatorExportFormat) => {
  if (!details.value) return
  exportingFormat.value = format
  exportError.value = ''
  try {
    const file = await dashboardService.exportarVersao(details.value as any, format)
    const objectUrl = URL.createObjectURL(file)
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = `indicador_${details.value.id}.${format}`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(objectUrl)
  } catch {
    exportError.value = 'Não foi possível exportar esta versão. Tente novamente mais tarde.'
  } finally {
    exportingFormat.value = null
  }
}

const processSteps = ['Ingestão', 'Validação', 'Tratamento', 'Publicação']

const progressStepIndex = computed(() => {
  if (!details.value) return 1
  if (details.value.status === 'Concluída') return 5 // Todas concluídas
  const stage = details.value.stage === 'Cálculo analítico' ? 'Publicação' : details.value.stage
  const index = processSteps.indexOf(stage)
  return index >= 0 ? index + 1 : 1
})

const warningStepIndex = computed(() => {
  if (!details.value) return undefined
  const isErrorStage = details.value.stage === 'Validação' || details.value.stage === 'Tratamento'
  const isErrorStatus =
    details.value.status === 'Falhou' ||
    details.value.status === 'Em quarentena' ||
    (details.value.quarantineRecords?.length ?? 0) > 0 ||
    (details.value.validationChecks?.some((c) => c.status === 'error') ?? false)
  if (isErrorStage && isErrorStatus && !details.value.assignedEditor) {
    return processSteps.indexOf(details.value.stage) + 1
  }
  return undefined
})

const activeStepIndex = ref(1)
const isQuarantineMode = ref(false)
const selectedQuarantineStage = ref('')

const loadData = async (idToLoad: string) => {
  isLoading.value = true
  try {
    // Carrega detalhes do processo via processService e sintetiza versões pela linhagem/dataset
    const loadedDetails = await processService.getProcessDetails(idToLoad)
    details.value = loadedDetails
    activeStepIndex.value = progressStepIndex.value > 4 ? 4 : progressStepIndex.value
    if (loadedDetails) {
      selectedQuarantineStage.value = loadedDetails.stage
    }

    const { carga, versions } = await auditoriaService.getVersionsForProcess(
      idToLoad,
      loadedDetails,
    )
    currentCarga.value = carga
    auditVersions.value = versions

    await loadTraceability(idToLoad, details.value?.dataSource === 'API')
    startPollingIfNeeded()
  } catch (e) {
    console.error('Erro ao carregar detalhes do processo:', e)
  } finally {
    isLoading.value = false
  }
}

let pollingTimer: any = null

const startPollingIfNeeded = () => {
  stopPolling()
  // Faz polling enquanto o processamento estiver em segundo plano (não atingiu 100%)
  const isProcessando = details.value?.processamentoConcluido === false || details.value?.stage === 'Ingestão'
  if (isProcessando || details.value?.status === 'Em andamento') {
    pollingTimer = setInterval(async () => {
      if (processId.value) {
        try {
          const updated = await processService.getProcessDetails(processId.value)
          if (updated) {
            const previousStep = progressStepIndex.value
            details.value = updated
            // Só avança a aba ativa se o progresso da etapa tiver de fato avançado
            if (progressStepIndex.value > previousStep) {
              activeStepIndex.value = progressStepIndex.value > 4 ? 4 : progressStepIndex.value
            }
            // Para o polling quando o processamento em segundo plano terminar 100%
            if (updated.processamentoConcluido !== false && updated.status !== 'Em andamento') {
              stopPolling()
              loadData(processId.value)
            }
          }
        } catch {
          // Ignore transient poll errors
        }
      }
    }, 2500)
  }
}

const stopPolling = () => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

onBeforeUnmount(() => {
  stopPolling()
})

watch(
  () => props.detailsData,
  async (newVal) => {
    if (newVal) {
      details.value = newVal
      isLoading.value = false
      activeStepIndex.value = progressStepIndex.value > 4 ? 4 : progressStepIndex.value
      selectedQuarantineStage.value = newVal.stage
      isQuarantineMode.value = false

      const { carga, versions } = await auditoriaService.getVersionsForProcess(newVal.id, newVal)
      currentCarga.value = carga
      auditVersions.value = versions

      await loadTraceability(newVal.id, newVal.dataSource === 'API')
    }
  },
  { immediate: true },
)

watch(
  () => processId.value,
  (newId) => {
    if (
      newId &&
      !props.detailsData &&
      String(details.value?.id).toLowerCase() !== String(newId).toLowerCase()
    ) {
      loadData(newId)
    }
  },
)

onMounted(async () => {
  if (props.detailsData) {
    details.value = props.detailsData
    isLoading.value = false
    activeStepIndex.value = progressStepIndex.value > 4 ? 4 : progressStepIndex.value
    selectedQuarantineStage.value = props.detailsData.stage
    await loadTraceability(props.detailsData.id, props.detailsData.dataSource === 'API')
    return
  }

  if (processId.value) {
    await loadData(processId.value)
  } else {
    isLoading.value = false
  }
})

const handleStepClick = (index: number) => {
  activeStepIndex.value = index
  isQuarantineMode.value = false
}

const openQuarantine = () => {
  isQuarantineMode.value = true
}

const handleVersionChange = async (newVersion: ProcessVersion) => {
  try {
    const fullDetails = await processService.getProcessDetails(newVersion.id)
    details.value = {
      ...fullDetails,
      id: newVersion.id,
    }
    if (!props.processId && route?.params?.id !== newVersion.id) {
      router.replace(`/processo/${encodeURIComponent(newVersion.id)}`)
    }
  } catch {
    details.value = newVersion
  }

  activeStepIndex.value = progressStepIndex.value > 4 ? 4 : progressStepIndex.value
  selectedQuarantineStage.value = details.value.stage
  isQuarantineMode.value = false

  await loadTraceability(newVersion.id, details.value?.dataSource === 'API')
}

const handleNotificarOperadorAuditoria = async (cargaIdStr: string) => {
  const numericId = Number(String(cargaIdStr || details.value?.id).replace(/\D/g, ''))
  if (numericId) {
    try {
      await uploadService.notificarOperadorQuarentena(numericId)
      await loadData(processId.value)
    } catch (e) {
      console.error('Erro ao notificar operador:', e)
    }
  }
}

const handleAbrirReprocessamentoAuditoria = () => {
  isQuarantineMode.value = true
  nextTick(() => {
    quarentenaStageRef.value?.openReprocessModal?.()
  })
}

const handleAbrirCancelamentoAuditoria = () => {
  isQuarantineMode.value = true
  nextTick(() => {
    quarentenaStageRef.value?.openCancelModal?.()
  })
}

const handleQuarantineReload = () => {
  isQuarantineMode.value = false
  loadData(processId.value)
}

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/')
  }
}

const currentComponent = computed(() => {
  switch (activeStepIndex.value) {
    case 1:
      return ProcessStageIngestao
    case 2:
      return ProcessStageValidacao
    case 3:
      return ProcessStageTratamento
    case 4:
      return ProcessStagePublicacao
    default:
      return ProcessStageIngestao
  }
})
</script>

<template>
  <div class="process-log-page" :class="{ 'has-audit-open': isAuditOpen && auditVersions.length > 0 }">
    <!-- Coluna Principal com a Visualização do Processo -->
    <div class="process-main-column">
      <div class="process-header">
        <div class="process-nav-row">
          <div class="process-nav-left">
            <button class="btn btn_outline btn-sm btn-back" @click="goBack" title="Voltar para tela anterior">
              &larr; Voltar
            </button>
            <h2 class="process-title">
              Detalhes da Carga {{ currentCargaId }}
              <span v-if="details" class="status-badge" :class="details.status.toLowerCase().replace(/\s+/g, '-')">
                {{ details.status }}
              </span>
            </h2>
          </div>

          <!-- Botão para alternar a exibição da Auditoria -->
          <div v-if="auditVersions.length > 0" class="process-nav-right">
            <button
              class="btn btn_outline btn-sm audit-toggle-btn"
              :class="{ 'btn-active': isAuditOpen }"
              @click="toggleAuditPanel"
            >
              <svg class="audit-icon" viewBox="0 0 24 24" width="16" height="16" fill="none"
                stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M3 12a9 9 0 1 0 3-6.7L3 8" />
                <path d="M3 3v5h5" />
                <path d="M12 7v5l4 2" />
              </svg>
              <span class="audit-label">{{ isAuditOpen ? 'Ocultar Auditoria' : 'Auditoria de Versões' }}</span>
              <span class="audit-count-badge">{{ auditVersions.length }}</span>
            </button>
          </div>
        </div>

        <Stepper
          :currentStep="activeStepIndex"
          :progressStep="progressStepIndex"
          :warningStep="warningStepIndex"
          :steps="processSteps"
          @step-click="handleStepClick"
        />
      </div>

      <main class="page-content">
        <div
          v-if="exportError"
          class="traceability-error"
          role="alert"
          data-testid="export-error-toast"
        >
          {{ exportError }}
        </div>
        <div
          v-if="traceabilityError"
          class="traceability-error"
          role="alert"
          data-testid="traceability-error-toast"
        >
          {{ traceabilityError }}
        </div>
        <div v-if="isClosed" class="closed-version-banner" role="status">
          Versão histórica fechada. O conteúdo está disponível somente para consulta.
        </div>
        <div v-if="isLoading" class="loading-state">
          <div class="spinner"></div>
          <p>Carregando informações da carga...</p>
        </div>

        <div v-else-if="details" class="details-container">
          <!-- Visão de Quarentena (Desvio Lateral) -->
          <div v-if="isQuarantineMode" class="quarantine-view tab-content">
            <div class="card card-content">
              <div class="quarantine-header">
                <h2>Zona de Quarentena</h2>
                <button class="btn btn_outline" @click="isQuarantineMode = false">
                  Voltar ao Fluxo
                </button>
              </div>

              <div class="quarantine-body">
                <ProcessStageQuarentena
                  v-if="details"
                  ref="quarentenaStageRef"
                  :details="details"
                  :read-only="isReadOnly"
                  @reload="handleQuarantineReload"
                />
              </div>
            </div>
          </div>

          <!-- Visão Normal -->
          <div v-else class="tab-content">
            <component
              :is="currentComponent"
              :details="details"
              :read-only="isReadOnly"
              :traceability="traceability"
              :exporting-format="exportingFormat"
              :can-export="isBackendProcess"
              :versions="auditVersions"
              @open-quarantine="openQuarantine"
              @export="exportIndicator"
              @reload="loadData(processId)"
            />
          </div>
        </div>

        <div v-else class="loading-state">
          <p>Não foi possível carregar os detalhes da carga.</p>
        </div>
      </main>
    </div>

    <!-- Componente Auditoria (Painel lateral integrado) -->
    <div v-if="isAuditOpen && auditVersions.length > 0" class="audit-side-wrapper">
      <Auditoria
        :versions="auditVersions"
        :selectedVersionId="details?.id"
        :cargaId="currentCarga?.id"
        :cargaDataset="details?.dataset"
        :layerName="details?.layerName"
        :canClose="true"
        :linhagemCompleta="currentCargaLinhagem"
        :userRole="currentUser.role"
        @select-version="handleVersionChange"
        @close="closeAuditPanel"
        @notificar-operador="handleNotificarOperadorAuditoria"
        @reprocessar="handleAbrirReprocessamentoAuditoria"
        @cancelar="handleAbrirCancelamentoAuditoria"
      />
    </div>
  </div>
</template>

<style scoped>
.process-log-page {
  display: flex;
  margin: -32px;
  height: calc(100% + 64px);
  background-color: var(--color-background);
  position: relative;
  overflow: hidden;
}

.process-main-column {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  overflow-x: hidden;
}

.audit-side-wrapper {
  width: clamp(380px, 32vw, 480px);
  min-width: 350px;
  flex-shrink: 0;
  background: var(--color-surface);
  height: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: -4px 0 16px rgba(0, 0, 0, 0.05);
  border-left: 1px solid var(--color-border);
  animation: slideIn 0.25s ease;
  z-index: 10;
}

.audit-side-wrapper > :deep(.audit-drawer-panel) {
  flex: 1;
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateX(16px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.process-header {
  padding: 2rem 2rem 1rem 2rem;
  border-bottom: 1px solid var(--color-border);
}

.process-nav-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
  gap: 1rem;
}

.process-nav-left {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex: 1 1 320px;
  min-width: 0;
}

.process-nav-right {
  flex-shrink: 0;
  margin-left: auto;
}

.process-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.btn-back {
  white-space: nowrap;
  flex-shrink: 0;
}

.process-title {
  margin: 0;
  color: var(--color-heading);
  font-size: 1.4rem;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem 0.75rem;
  min-width: 0;
  word-break: break-word;
}

.has-audit-open .process-title {
  font-size: 1.2rem;
}

.has-audit-open .process-header,
.has-audit-open .page-content {
  padding-left: 1.5rem;
  padding-right: 1.5rem;
}

.status-badge {
  font-size: 0.85rem;
  padding: 0.2rem 0.6rem;
  border-radius: 4px;
  background: var(--color-surface-soft);
  border: 1px solid var(--color-border);
  font-weight: 600;
}

.status-badge.concluída,
.status-badge.concluida {
  color: #16a34a;
  border-color: rgba(22, 163, 74, 0.3);
}

.status-badge.em-andamento {
  color: #d97706;
  border-color: rgba(217, 119, 6, 0.3);
}

.status-badge.aguardando-validação,
.status-badge.aguardando-validacao {
  color: #0284c7;
  border-color: rgba(2, 132, 199, 0.3);
}

.status-badge.falhou {
  color: #dc2626;
  border-color: rgba(220, 38, 38, 0.3);
}

.status-badge.fechado,
.status-badge.fechada {
  color: #64748b;
  border-color: rgba(100, 116, 139, 0.3);
  background: rgba(100, 116, 139, 0.08);
}

.audit-toggle-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  white-space: nowrap;
  line-height: 1;
  transition: background 0.2s, color 0.2s, border-color 0.2s;
}

.audit-toggle-btn.btn-active {
  background: var(--vis-brand-orange);
  border-color: var(--vis-brand-orange);
  color: #fff;
}

.audit-icon {
  flex-shrink: 0;
  display: block;
}

.audit-count-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  font-size: 0.72rem;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--vis-brand-orange);
  color: #fff;
}

.audit-toggle-btn.btn-active .audit-count-badge {
  background: #fff;
  color: var(--vis-brand-orange);
}

.closed-version-banner {
  margin-bottom: 1.5rem;
  padding: 0.85rem 1.25rem;
  border-radius: 6px;
  background: #f1f5f9;
  border: 1px solid #cbd5e1;
  color: #475569;
  font-size: 0.9rem;
  font-weight: 500;
}

.traceability-card {
  margin-top: 2rem;
  padding: 1.25rem 1.5rem;
  border-radius: 8px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
}

.traceability-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 1rem;
  margin-bottom: 1rem;
}

.traceability-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.traceability-eyebrow {
  margin: 0;
  font-size: 0.72rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--vis-brand-orange);
  font-weight: 700;
}

.traceability-heading h3 {
  margin: 0.2rem 0 0;
  font-size: 1.05rem;
  color: var(--color-heading);
}

.traceability-id {
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--color-text-muted, #64748b);
}

.traceability-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 1rem 1.5rem;
  margin: 0;
}

.traceability-grid dt {
  font-size: 0.75rem;
  color: var(--color-text-muted, #64748b);
  margin-bottom: 0.25rem;
}

.traceability-grid dd {
  margin: 0;
  font-size: 0.88rem;
  color: var(--color-text);
  font-weight: 500;
  word-break: break-all;
}

.traceability-hash {
  grid-column: 1 / -1;
}

.traceability-hash dd {
  font-family: monospace;
  font-size: 0.8rem;
}

.traceability-state {
  margin: 0;
  font-size: 0.85rem;
  color: var(--color-text-muted, #64748b);
}

.traceability-error {
  color: #dc2626;
}

.page-content {
  padding: 2rem;
  flex-grow: 1;
}

.details-container {
  max-width: 1200px;
  margin: 0 auto;
}

.tab-content {
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.card {
  background: var(--color-surface);
  border-radius: 8px;
  border: 1px solid var(--color-border);
}

.card-content {
  padding: 1.5rem;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem;
  gap: 1rem;
}

.quarantine-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}

.quarantine-header h2 {
  color: var(--color-heading);
  margin: 0;
}

@media (max-width: 1024px) {
  .process-log-page.has-audit-open {
    flex-direction: column;
    height: auto;
    overflow: visible;
  }
  .has-audit-open .process-main-column {
    overflow: visible;
  }
  .audit-side-wrapper {
    width: 100%;
    height: auto;
    border-top: 1px solid var(--color-border);
  }
}

@media (max-width: 760px) {
  .process-log-page {
    margin: -20px -16px;
    height: calc(100% + 40px);
  }
}
</style>
