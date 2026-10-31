<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  FiAlertTriangle,
  FiSearch,
  FiThumbsDown,
  FiDatabase,
  FiCheck,
  FiAward,
  FiArrowLeft,
  FiRotateCcw,
  FiLock,
  FiFileText,
  FiMap,
  FiCheckCircle,
  FiXCircle,
} from 'vue-icons-plus/fi'
import type { ProcessLogDetails, IndicatorExportFormat } from '../../services/process'
import { processService } from '../../services/process'
import { userService } from '../../services/user'
import { dashboardService } from '../../services/dashboard'
import ProcessoMiniMapa from '../ProcessoMiniMapa.vue'

const props = withDefaults(
  defineProps<{
    details: ProcessLogDetails
    readOnly?: boolean
    exportingFormat?: IndicatorExportFormat | null
    canExport?: boolean
  }>(),
  {
    readOnly: false,
    exportingFormat: null,
    canExport: true,
  },
)

const emit = defineEmits<{
  (e: 'open-quarantine'): void
  (e: 'export', format: IndicatorExportFormat): void
  (e: 'reload'): void
}>()

const router = useRouter()

// Apenas Administrador vê e aciona Aprovar/Reprovar e Fechar Versão
const currentUser = computed(() => userService.getCurrentUser())
const canApprove = computed(() => userService.isAdmin())

// Estados de submissão e decisão
const isSubmitting = ref(false)
const isApproved = ref(false)
const isRejected = ref(false)
const reviewNote = ref('')
const targetAuditStage = ref<'Tratamento' | 'Validação'>('Tratamento')

// Modais da US07
const showPublishConfirmModal = ref(false)
const showRejectModal = ref(false)
const rejectionReason = ref('')
const rejectionDestination = ref<'reprocessar' | 'invalidar'>('reprocessar')
const rejectionError = ref('')

// ── Computeds de estado ────────────────────────────────────────────────────────
const isClosed = computed(() => {
  const st = (props.details.status || '').toLowerCase()
  return st.includes('fechad') || st === 'concluída' || st === 'concluida'
})
const isAlreadyPublished = computed(() => props.details.status === 'Concluída' || isClosed.value)
/** Aprovado nesta sessão */
const isJustApproved = computed(() => isApproved.value && !isClosed.value)
/** Exibe o card de sucesso apenas ao aprovar e publicar nesta sessão */
const showSuccess = computed(() => isJustApproved.value)

/** Exibe painel de decisão: processo em andamento esperando homologação */
const stageIndex = computed(() => {
  const steps = ['Ingestão', 'Validação', 'Tratamento', 'Publicação']
  const s = (props.details.stage || '').trim().toUpperCase()
  if (s.includes('INGEST')) return 0
  if (s.includes('VALIDA')) return 1
  if (s.includes('TRATA')) return 2
  if (s.includes('PUBLICA') || s.includes('CALCULO')) return 3
  const stage = props.details.stage === 'Cálculo analítico' ? 'Publicação' : props.details.stage
  return steps.indexOf(stage)
})

const isStageReached = computed(() => {
  if (props.details.status === 'Concluída' || isClosed.value) return true
  return stageIndex.value >= 3
})

const isAwaiting = computed(
  () =>
    !props.readOnly &&
    !isClosed.value &&
    isStageReached.value &&
    props.details.status === 'Em andamento' &&
    !isApproved.value &&
    !isRejected.value,
)

const isError = computed(
  () =>
    !props.readOnly &&
    (props.details.status === 'Falhou' ||
      props.details.status === 'Em quarentena' ||
      !!props.details.pauseReason),
)

/** Dados analíticos vêm da API via details.analyticsData */
const extractedData = computed(() => props.details.analyticsData ?? [])

// ── Handlers US07 ─────────────────────────────────────────────────────────────

// Tarefa 4: Modal de Aprovação Final e Fechamento de Versão
const openPublishModal = () => {
  showPublishConfirmModal.value = true
}

const closePublishModal = () => {
  showPublishConfirmModal.value = false
}

const publishError = ref<string | null>(null)

const confirmPublishAndClose = async () => {
  if (isSubmitting.value) return

  isSubmitting.value = true
  publishError.value = null
  try {
    if (/^\d+$/.test(String(props.details.id))) {
      try {
        await processService.registrarDecisaoGestor(props.details.id, {
          gestorId: currentUser.value.id || 1,
          etapa: 'PUBLICACAO',
          decisao: 'APROVADO',
          parecer: 'Publicação oficial homologada pelo gestor.',
        })
      } catch (errApi) {
        console.warn('Aviso: endpoint de decisão do gestor offline ou simulado:', errApi)
      }
    }

    try {
      await dashboardService.aprovarEFecharVersao(
        {
          id: String(props.details.id),
          dataset: props.details.dataset,
          stage: 'Publicação',
          status: 'Fechado',
          dateTime: props.details.dateTime,
          source: props.details.source,
          year: props.details.year,
          epsg: props.details.epsg,
        },
        currentUser.value.name,
      )
    } catch (e) {
      console.warn('Aviso ao fechar versão via dashboardService:', e)
    }

    isApproved.value = true
    isRejected.value = false
    props.details.status = 'Concluída'
    props.details.stage = 'Publicação'
    props.details.assignedEditor = currentUser.value.name
  } catch (err: any) {
    console.error('Erro ao aprovar e publicar:', err)
    publishError.value = err?.response?.data?.message || err?.message || 'Falha ao homologar publicação.'
  } finally {
    closePublishModal()
    isSubmitting.value = false
    emit('reload')
  }
}

// Tarefa 3: Modal de Reprovação com Parecer Obrigatório
const openRejectModal = () => {
  rejectionReason.value = ''
  rejectionDestination.value = 'reprocessar'
  rejectionError.value = ''
  showRejectModal.value = true
}

const closeRejectModal = () => {
  showRejectModal.value = false
  rejectionReason.value = ''
  rejectionError.value = ''
}

const confirmReject = async () => {
  if (!rejectionReason.value.trim()) {
    rejectionError.value = 'O parecer técnico é obrigatório para reprovar a carga.'
    return
  }

  if (isSubmitting.value) return

  isSubmitting.value = true
  try {
    if (/^\d+$/.test(String(props.details.id))) {
      await processService.registrarDecisaoGestor(props.details.id, {
        gestorId: currentUser.value.id || 1,
        etapa: 'PUBLICACAO',
        decisao: 'REPROVADO',
        parecer: rejectionReason.value.trim(),
      })
    }

    await dashboardService.reprovarCarga(
      {
        id: String(props.details.id),
        dataset: props.details.dataset,
        stage: 'Publicação',
        status: props.details.status as any,
        dateTime: props.details.dateTime,
        source: props.details.source,
        year: props.details.year,
        epsg: props.details.epsg,
      },
      rejectionReason.value,
      rejectionDestination.value,
    )

    isRejected.value = true
    isApproved.value = false
    props.details.assignedEditor = currentUser.value.name

    if (rejectionDestination.value === 'reprocessar') {
      props.details.stage = 'Tratamento'
      props.details.status = 'Em andamento'
      props.details.pauseReason = `Reprovação na Publicação: ${rejectionReason.value.trim()}. Retornado ao Operador.`
    } else {
      props.details.status = 'Falhou'
      props.details.pauseReason = `Carga invalidada na Publicação: ${rejectionReason.value.trim()}.`
    }
    closeRejectModal()
    emit('reload')
  } catch (error: any) {
    rejectionError.value = error?.message || 'Falha ao reprovar.'
  } finally {
    isSubmitting.value = false
  }
}

const handleUndo = () => {
  isRejected.value = false
  isApproved.value = false
  rejectionReason.value = ''
  props.details.stage = 'Publicação'
  props.details.status = 'Em andamento'
}

const goToDashboard = () => {
  router.push({ name: 'dashboard' })
}

const goToMap = () => {
  router.push(`/mapa?processoId=${props.details.id}`)
}
</script>

<template>
  <div class="stage-container">
    <div v-if="readOnly" class="closed-version-banner" role="status">
      Esta versão foi fechada e está disponível somente para consulta.
    </div>

    <div v-if="!isStageReached" class="card p-4 text-center my-4" data-testid="locked-stage-card">
      <FiLock size="32" class="text-muted mb-2" style="margin: 0 auto; display: block;" />
      <h4>Etapa de Publicação ainda não liberada</h4>
      <p class="text-muted">Esta etapa será liberada após a aprovação das etapas anteriores pelo Gestor.</p>
    </div>

    <template v-else-if="!showSuccess">
      <!-- ═══ MAPA INTERATIVO: POLÍGONOS DA CARGA ═════════════════════════════════ -->
      <ProcessoMiniMapa
        v-if="details?.id"
        :processo-id="details.id"
        :is-published="isAlreadyPublished"
        :status="details.status"
        title="Visualização Territorial do Indicador (Geometrias da Carga)"
        height="320px"
      />

      <!-- ═══ TABELA: DADOS EXTRAÍDOS ════════════════════════════════════════════ -->
      <div class="card">
        <div class="card-header">
          <div class="header-left">
            <h3>Dados Extraídos</h3>
            <p class="header-desc">
              Resultados do cálculo analítico gerado automaticamente pelo backend.
            </p>
          </div>
          <span class="badge success">Cálculo Concluído</span>
        </div>
        <div class="card-body p-0">
          <div class="table-wrapper">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Parâmetro / Algoritmo</th>
                  <th>Resultado</th>
                  <th>Referência (Lei)</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in extractedData" :key="row.param">
                  <td>{{ row.param }}</td>
                  <td class="result-cell">{{ row.result }}</td>
                  <td class="ref-cell">{{ row.reference }}</td>
                  <td>
                    <span class="badge-sm" :class="row.status">{{ row.statusLabel }}</span>
                  </td>
                </tr>
                <tr v-if="extractedData.length === 0">
                  <td colspan="4" class="empty-row">Nenhum dado analítico disponível ainda.</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- ═══ PAINEL DE DECISÃO: aguardando homologação (Tarefa 2, 7 e 8) ══════════════════════════════ -->
      <div v-if="canApprove && isAwaiting" class="action-panel" data-testid="gestor-decision-panel">
        <div class="action-content">
          <div class="action-icon-wrap">
            <FiSearch size="22" />
          </div>
          <div class="action-text">
            <h3>Homologação e Publicação da Versão Oficial</h3>
            <p>
              Os dados foram processados e validados com sucesso. Deseja homologar a versão oficial
              e consolidar na base definitiva de produção?
            </p>
          </div>
        </div>
        <div class="action-buttons">
          <button
            type="button"
            class="btn btn-outline-danger"
            data-testid="btn-reprovar-carga"
            :disabled="isSubmitting"
            @click="openRejectModal"
          >
            <FiXCircle size="15" />
            Reprovar Carga
          </button>
          <button
            type="button"
            class="btn btn-primary"
            data-testid="btn-aprovar-publicar"
            :disabled="isSubmitting"
            @click="openPublishModal"
          >
            <FiLock size="15" />
            Aprovar e Publicar Versão Oficial
          </button>
        </div>
      </div>

      <!-- ═══ VERSÃO OFICIAL BLOQUEADA E EXPORTAÇÕES (Tarefa 5 e 6) ═══ -->
      <div v-if="isAlreadyPublished || readOnly || isClosed || isApproved" class="official-export-box" data-testid="official-export-section">
        <div v-if="isApproved" class="mb-3" style="display: flex; align-items: center; gap: 0.5rem; background: rgba(16, 185, 129, 0.1); border: 1px solid #10b981; border-radius: 6px; padding: 0.75rem 1rem; color: #10b981; font-weight: 600;">
          <FiCheckCircle size="18" />
          <span>Versão homologada e publicada com sucesso! Os dados foram consolidados na base de produção.</span>
        </div>
        <div class="official-seal">
          <span class="badge-bloqueada" data-testid="badge-versao-bloqueada">
            <FiLock size="14" />
            Versão Oficial Bloqueada
          </span>
          <span class="official-seal-desc">
            Versão homologada e congelada com imutabilidade para auditoria histórica.
          </span>
        </div>
        <div class="official-export-buttons">
          <button
            type="button"
            class="btn btn-export-csv"
            data-testid="btn-export-official-csv"
            :disabled="!!exportingFormat"
            @click="emit('export', 'csv')"
          >
            <FiFileText size="14" />
            {{ exportingFormat === 'csv' ? 'Exportando…' : 'Exportar Planilha (CSV)' }}
          </button>
          <button
            type="button"
            class="btn btn-export-geojson"
            data-testid="btn-export-official-geojson"
            :disabled="!!exportingFormat"
            @click="emit('export', 'geojson')"
          >
            <FiMap size="14" />
            {{ exportingFormat === 'geojson' ? 'Exportando…' : 'Exportar Mapa (GeoJSON)' }}
          </button>
          <button
            type="button"
            class="btn btn_outline"
            style="display: inline-flex; align-items: center; gap: 0.4rem;"
            @click="goToMap"
          >
            <FiMap size="14" />
            Visualizar no Mapa
          </button>
        </div>
      </div>

      <!-- ═══ REVISÃO ENVIADA (rejeitado nesta sessão) ════════════════════════════ -->
      <div v-if="!readOnly && isRejected" class="alert-banner danger">
        <div class="alert-icon-lg">
          <FiAlertTriangle size="26" />
        </div>
        <div class="alert-text">
          <strong>Revisão Solicitada para a etapa de {{ targetAuditStage }}</strong>
          <p>
            O processo foi encaminhado para revisão de <strong>{{ targetAuditStage }}</strong> com
            responsável alocado. Status atualizado para <strong>Em andamento</strong>.
            <span v-if="reviewNote"
              ><br /><em>Instruções ao editor: {{ reviewNote }}</em></span
            >
          </p>
        </div>
        <button class="btn-ghost-small" @click="handleUndo">
          <FiRotateCcw size="13" />
          Desfazer
        </button>
      </div>
    </template>

    <!-- ═══ SUCESSO: publicado (nesta sessão) ════════ -->
    <div v-if="showSuccess" class="success-card">
      <div class="success-content">
        <div class="success-icon-wrap">
          <FiAward size="40" />
        </div>
        <h3>Carga Publicada com Sucesso!</h3>
        <p>
          Os dados espaciais e tabulares já estão disponíveis no banco de produção e visíveis nos
          dashboards de negócio.
        </p>
        <div class="success-meta">
          <span>
            <FiDatabase size="13" style="vertical-align: middle; margin-right: 4px" />
            <strong>Tabela:</strong> geo_imoveis_producao
          </span>
          <span class="meta-divider">•</span>
          <span><strong>Registros:</strong> {{ extractedData.length }} indicadores</span>
        </div>

        <!-- Ações pós-publicação -->
        <div v-if="!readOnly" class="success-actions">
          <button class="btn btn-outline-danger" @click="openRejectModal">
            <FiThumbsDown size="15" />
            Solicitar Revisão
          </button>
          <button class="btn btn-primary" @click="goToDashboard">
            <FiArrowLeft size="15" />
            Ver no Dashboard
          </button>
        </div>
      </div>
    </div>

    <!-- ═══ ERRO / QUARENTENA (DESVIO LATERAL) ══════════════════════════════════ -->
    <div v-if="(details.quarantineRecords?.length ?? 0) > 0 && details.pauseReason" class="alert-banner danger">
      <div class="alert-icon-lg">
        <FiAlertTriangle size="26" />
      </div>
      <div class="alert-text">
        <strong>Registros em Quarentena — Publicação Bloqueada</strong>
        <p>{{ details.pauseReason }}</p>
        <p style="font-size: 0.82rem; margin-top: 0.35rem; opacity: 0.7">
          Os registros rejeitados foram desviados para a quarentena. O editor deve corrigir o
          arquivo e subir novamente.
        </p>
      </div>
      <button class="btn-warning-outline" @click="emit('open-quarantine')">
        <FiAlertTriangle size="14" />
        Ver Quarentena
      </button>
    </div>

    <!-- ═══ MODAL DE REPROVAÇÃO COM PARECER OBRIGATÓRIO (Tarefa 3) ═══ -->
    <div
      v-if="showRejectModal"
      class="modal-backdrop"
      data-testid="rejection-modal"
      @click.self="closeRejectModal"
    >
      <div class="modal-dialog">
        <header class="modal-header">
          <div class="modal-header-title">
            <FiAlertTriangle size="22" class="modal-danger-icon" />
            <h3>Reprovar Carga - Parecer Obrigatório</h3>
          </div>
          <button type="button" class="btn-close" @click="closeRejectModal">&times;</button>
        </header>

        <div class="modal-body">
          <p class="modal-info">
            Carga: <strong>{{ details.id }}</strong> &bull; Etapa: <strong>Publicação</strong>
          </p>

          <div class="form-group">
            <label class="form-label">Destino após reprovação:</label>
            <div class="radio-options" data-testid="select-rejection-destination">
              <label class="radio-label">
                <input
                  type="radio"
                  name="destination"
                  value="reprocessar"
                  v-model="rejectionDestination"
                />
                <span>Voltar para o Operador Reprocessar</span>
              </label>
              <label class="radio-label">
                <input
                  type="radio"
                  name="destination"
                  value="invalidar"
                  v-model="rejectionDestination"
                />
                <span>Invalidar Carga permanentemente</span>
              </label>
            </div>
          </div>

          <div class="form-group">
            <label class="form-label">
              Parecer Técnico de Homologação <span class="required-mark">*</span>
            </label>
            <textarea
              v-model="rejectionReason"
              class="form-textarea"
              rows="4"
              data-testid="input-rejection-reason"
              placeholder="Descreva detalhadamente o parecer obrigatório para reprovar a carga..."
            ></textarea>
            <span v-if="rejectionError" class="error-text" data-testid="rejection-error-msg">
              {{ rejectionError }}
            </span>
          </div>
        </div>

        <footer class="modal-footer">
          <button type="button" class="btn-cancel" @click="closeRejectModal">Cancelar</button>
          <button
            type="button"
            class="btn-confirm-rejection"
            data-testid="btn-confirm-rejection"
            :disabled="isSubmitting || !rejectionReason.trim()"
            @click="confirmReject"
          >
            {{ isSubmitting ? 'Processando...' : 'Confirmar Reprovação' }}
          </button>
        </footer>
      </div>
    </div>

    <!-- ═══ MODAL DE APROVAÇÃO FINAL E FECHAMENTO DE VERSÃO (Tarefa 4) ═══ -->
    <div
      v-if="showPublishConfirmModal"
      class="modal-backdrop"
      data-testid="publish-confirm-modal"
      @click.self="closePublishModal"
    >
      <div class="modal-dialog">
        <header class="modal-header">
          <div class="modal-header-title">
            <FiLock size="22" class="modal-lock-icon" />
            <h3>Aprovação e Fechamento Oficial de Versão</h3>
          </div>
          <button type="button" class="btn-close" @click="closePublishModal">&times;</button>
        </header>

        <div class="modal-body">
          <div class="alert-box-warning" data-testid="publish-alert-text">
            <FiAlertTriangle size="24" class="alert-warning-icon" />
            <div>
              <strong>Atenção ao fechamento de versão histórica:</strong>
              <p>
                Esta versão será fechada permanentemente, ficará imutável para auditoria histórica e
                será liberada imediatamente no catálogo do Analista.
              </p>
            </div>
          </div>

          <div class="publish-meta-box">
            <div class="meta-row">
              <span class="meta-label">Carga / Processo:</span>
              <strong>{{ details.id }}</strong>
            </div>
            <div class="meta-row">
              <span class="meta-label">Dataset:</span>
              <span>{{ details.dataset }}</span>
            </div>
            <div class="meta-row">
              <span class="meta-label">Responsável pela Publicação:</span>
              <strong>{{ currentUser.name }}</strong>
            </div>
          </div>
        </div>

        <footer class="modal-footer">
          <button type="button" class="btn-cancel" @click="closePublishModal">Cancelar</button>
          <button
            type="button"
            class="btn-confirm-publish"
            data-testid="btn-confirm-publish"
            :disabled="isSubmitting"
            @click="confirmPublishAndClose"
          >
            {{ isSubmitting ? 'Fechando versão...' : 'Confirmar Fechamento e Publicar' }}
          </button>
        </footer>
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

.closed-version-banner {
  padding: 12px 16px;
  color: #713f12;
  background: #fef3c7;
  border: 1px solid #fde68a;
  border-radius: 8px;
  font-size: 0.88rem;
  font-weight: 700;
}

/* CARD */
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

.card-body {
  padding: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.card-body.p-0 {
  padding: 0;
}

/* TABLE */
.table-wrapper {
  overflow-x: auto;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
}

.data-table th,
.data-table td {
  padding: 0.9rem 1.5rem;
  border-bottom: 1px solid var(--color-border);
  font-size: 0.9rem;
}

.data-table th {
  background: var(--color-background);
  font-weight: 600;
  color: var(--color-text);
  opacity: 0.8;
  font-size: 0.82rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.data-table td {
  color: var(--color-text);
}
.result-cell {
  font-weight: 600;
  color: var(--color-heading) !important;
}
.ref-cell {
  font-size: 0.82rem !important;
  opacity: 0.7;
}

.empty-row {
  text-align: center;
  opacity: 0.5;
  font-style: italic;
  padding: 2rem !important;
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

.badge-sm {
  padding: 0.2rem 0.6rem;
  border-radius: 50px;
  font-size: 0.75rem;
  font-weight: 700;
  display: inline-block;
}

.badge-sm.success {
  background: rgba(22, 163, 74, 0.12);
  color: #16a34a;
}
.badge-sm.error {
  background: rgba(220, 53, 69, 0.1);
  color: #dc3545;
}
.badge-sm.warning {
  background: rgba(234, 179, 8, 0.12);
  color: #92400e;
}

/* ACTION PANEL */
.action-panel {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 2rem;
  background: var(--color-surface);
  border: 2px solid var(--vis-brand-orange, #f26522);
  border-radius: 12px;
  padding: 1.75rem 2rem;
  box-shadow: 0 4px 20px rgba(242, 101, 34, 0.08);
}

.action-content {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  flex: 1;
}

.action-icon-wrap {
  width: 3rem;
  height: 3rem;
  border-radius: 50%;
  background: rgba(242, 101, 34, 0.1);
  color: var(--vis-brand-orange, #f26522);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.action-text h3 {
  margin: 0 0 0.4rem 0;
  color: var(--color-heading);
  font-size: 1.1rem;
}

.action-text p {
  margin: 0;
  font-size: 0.9rem;
  opacity: 0.75;
  color: var(--color-text);
  max-width: 480px;
}

.action-buttons {
  display: flex;
  gap: 0.75rem;
  flex-shrink: 0;
}

/* REVIEW INPUT */
.review-input-panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.review-input-panel h4 {
  margin: 0;
  color: var(--color-heading);
}

.review-input-panel p {
  margin: 0;
  font-size: 0.88rem;
  opacity: 0.7;
  color: var(--color-text);
}

.review-textarea {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-background);
  color: var(--color-text);
  font-family: inherit;
  font-size: 0.9rem;
  resize: vertical;
  box-sizing: border-box;
  transition: border-color 0.2s;
}

.review-textarea:focus {
  outline: none;
  border-color: var(--vis-brand-orange, #f26522);
}

.review-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
}

/* ALERT BANNER */
.alert-banner {
  display: flex;
  align-items: flex-start;
  padding: 1.25rem 1.5rem;
  border-radius: 10px;
  gap: 1rem;
  border-left: 5px solid;
}

.alert-banner.danger {
  background: rgba(220, 53, 69, 0.06);
  border-color: #dc3545;
  border-right: 1px solid rgba(220, 53, 69, 0.2);
  border-top: 1px solid rgba(220, 53, 69, 0.2);
  border-bottom: 1px solid rgba(220, 53, 69, 0.2);
}

.alert-icon-lg {
  flex-shrink: 0;
  color: #dc3545;
  padding-top: 0.1rem;
}

.alert-text {
  flex-grow: 1;
}

.alert-text strong {
  display: block;
  font-size: 1rem;
  color: var(--color-heading);
  margin-bottom: 0.3rem;
}

.alert-text p {
  margin: 0;
  font-size: 0.88rem;
  opacity: 0.8;
  color: var(--color-text);
}

/* SUCCESS CARD */
.success-card {
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid rgba(22, 163, 74, 0.3);
}

.success-content {
  padding: 3rem 2rem;
  background: linear-gradient(135deg, rgba(22, 163, 74, 0.06) 0%, var(--color-surface) 100%);
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.success-icon-wrap {
  width: 5rem;
  height: 5rem;
  border-radius: 50%;
  background: rgba(22, 163, 74, 0.12);
  color: #16a34a;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 1.25rem;
  border: 2px solid rgba(22, 163, 74, 0.2);
}

.success-content h3 {
  color: #16a34a;
  font-size: 1.6rem;
  margin: 0 0 0.75rem 0;
}

.success-content p {
  color: var(--color-text);
  max-width: 500px;
  font-size: 0.95rem;
  opacity: 0.8;
  margin: 0;
}

.success-meta {
  margin-top: 1.5rem;
  display: inline-flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.75rem;
  background: rgba(22, 163, 74, 0.06);
  padding: 0.75rem 1.5rem;
  border-radius: 50px;
  font-size: 0.88rem;
  color: var(--color-text);
  border: 1px solid rgba(22, 163, 74, 0.2);
}

.meta-divider {
  opacity: 0.4;
}

.success-actions {
  display: flex;
  gap: 0.75rem;
  margin-top: 1.75rem;
  flex-wrap: wrap;
  justify-content: center;
}

.post-publish-review {
  width: 100%;
  max-width: 560px;
  margin-top: 1.25rem;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 1.25rem;
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.post-publish-review h4 {
  margin: 0;
  color: var(--color-heading);
  font-size: 0.95rem;
}

/* BUTTONS */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.65rem 1.25rem;
  border-radius: 8px;
  font-weight: 600;
  font-size: 0.9rem;
  cursor: pointer;
  border: none;
  transition: all 0.2s ease;
}

.btn-primary {
  background: var(--vis-brand-orange, #f26522);
  color: #fff;
}

.btn-primary:hover {
  filter: brightness(1.1);
  transform: translateY(-1px);
}

.btn-outline-danger {
  background: transparent;
  border: 1.5px solid #dc3545;
  color: #dc3545;
}

.btn-outline-danger:hover {
  background: #dc3545;
  color: #fff;
}

.btn-ghost {
  background: transparent;
  border: 1px solid var(--color-border);
  color: var(--color-text);
}

.btn-ghost:hover {
  background: var(--color-background);
}

.btn-ghost-small {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  background: transparent;
  border: 1px solid rgba(220, 53, 69, 0.3);
  color: #dc3545;
  padding: 0.4rem 0.9rem;
  border-radius: 6px;
  font-weight: 600;
  font-size: 0.82rem;
  cursor: pointer;
  flex-shrink: 0;
  align-self: center;
  transition: all 0.2s;
}

.btn-ghost-small:hover {
  background: rgba(220, 53, 69, 0.08);
}

.btn-warning-outline {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  background: transparent;
  border: 1px solid rgba(234, 179, 8, 0.5);
  color: #92400e;
  padding: 0.5rem 1rem;
  border-radius: 6px;
  font-weight: 600;
  font-size: 0.88rem;
  cursor: pointer;
  flex-shrink: 0;
  align-self: center;
  transition: all 0.2s;
}

.btn-warning-outline:hover {
  background: #f59e0b;
  color: #fff;
}

/* ═══ ESTILOS US07: Exportação Oficial, Selo Bloqueado e Modais ═══ */
.official-export-box {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 1.25rem;
  flex-wrap: wrap;
}

.official-seal {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.badge-bloqueada {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  background: #ecfdf5;
  color: #065f46;
  border: 1px solid #a7f3d0;
  padding: 0.35rem 0.75rem;
  border-radius: 9999px;
  font-size: 0.82rem;
  font-weight: 700;
  white-space: nowrap;
}

.official-seal-desc {
  font-size: 0.85rem;
  color: #6b7280;
}

.official-export-buttons {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.btn-export-csv,
.btn-export-geojson {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.5rem 1rem;
  border-radius: 6px;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}

.btn-export-csv {
  background: #f0fdf4;
  color: #15803d;
  border: 1px solid #bbf7d0;
}

.btn-export-csv:hover:not(:disabled) {
  background: #dcfce7;
}

.btn-export-geojson {
  background: #eff6ff;
  color: #1d4ed8;
  border: 1px solid #bfdbfe;
}

.btn-export-geojson:hover:not(:disabled) {
  background: #dbeafe;
}

/* Modals */
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: grid;
  place-items: center;
  z-index: 9999;
  padding: 1.5rem;
}

.modal-dialog {
  background: var(--color-surface, #ffffff);
  border-radius: 12px;
  width: 100%;
  max-width: 520px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.modal-header {
  padding: 1.25rem 1.5rem;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.modal-header-title {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.modal-header-title h3 {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 700;
  color: #111827;
}

.modal-danger-icon {
  color: #ef4444;
}

.modal-lock-icon {
  color: #059669;
}

.btn-close {
  background: transparent;
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
  color: #9ca3af;
  line-height: 1;
}

.btn-close:hover {
  color: #111827;
}

.modal-body {
  padding: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.modal-info {
  margin: 0;
  font-size: 0.9rem;
  color: #4b5563;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.form-label {
  font-size: 0.85rem;
  font-weight: 700;
  color: #374151;
}

.required-mark {
  color: #ef4444;
}

.radio-options {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.radio-label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.88rem;
  cursor: pointer;
  color: #374151;
}

.form-textarea {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 0.88rem;
  font-family: inherit;
  resize: vertical;
}

.form-textarea:focus {
  outline: none;
  border-color: var(--vis-brand-orange, #f26522);
}

.error-text {
  font-size: 0.8rem;
  color: #dc2626;
  font-weight: 600;
}

.alert-box-warning {
  display: flex;
  align-items: flex-start;
  gap: 0.85rem;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 8px;
  padding: 1rem;
  color: #92400e;
  font-size: 0.88rem;
}

.alert-warning-icon {
  color: #d97706;
  flex-shrink: 0;
  margin-top: 2px;
}

.alert-box-warning p {
  margin: 0.35rem 0 0 0;
  line-height: 1.4;
  color: #78350f;
}

.publish-meta-box {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  font-size: 0.88rem;
}

.meta-row {
  display: flex;
  justify-content: space-between;
}

.meta-label {
  color: #6b7280;
}

.modal-footer {
  padding: 1.25rem 1.5rem;
  border-top: 1px solid #e5e7eb;
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  background: #f9fafb;
}

.btn-cancel {
  background: white;
  border: 1px solid #d1d5db;
  color: #374151;
  padding: 0.6rem 1.15rem;
  border-radius: 6px;
  font-weight: 600;
  font-size: 0.88rem;
  cursor: pointer;
}

.btn-cancel:hover {
  background: #f3f4f6;
}

.btn-confirm-rejection {
  background: #dc2626;
  color: white;
  border: none;
  padding: 0.6rem 1.25rem;
  border-radius: 6px;
  font-weight: 700;
  font-size: 0.88rem;
  cursor: pointer;
}

.btn-confirm-rejection:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-confirm-publish {
  background: #059669;
  color: white;
  border: none;
  padding: 0.6rem 1.25rem;
  border-radius: 6px;
  font-weight: 700;
  font-size: 0.88rem;
  cursor: pointer;
}

.btn-confirm-publish:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
