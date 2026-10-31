<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  FiFileText,
  FiCopy,
  FiCheck,
  FiClock,
  FiShield,
  FiDownload,
  FiAlertTriangle,
  FiBell,
  FiRefreshCw,
  FiXCircle,
  FiCheckCircle,
} from 'vue-icons-plus/fi'
import type { ProcessVersion, LinhagemCompletaCarga } from '@/services/auditoria'
import { uploadService } from '@/services/upload'

interface Props {
  versions: ProcessVersion[]
  selectedVersionId?: string
  cargaId?: string
  cargaDataset?: string
  layerName?: string
  canClose?: boolean
  linhagemCompleta?: LinhagemCompletaCarga | null
  userRole?: string
}

const props = withDefaults(defineProps<Props>(), {
  selectedVersionId: '',
  cargaId: '',
  cargaDataset: '',
  layerName: '',
  canClose: true,
  linhagemCompleta: null,
  userRole: 'Auditor',
})

const emit = defineEmits<{
  (e: 'select-version', version: ProcessVersion): void
  (e: 'close'): void
  (e: 'notificar-operador', id: string): void
  (e: 'reprocessar', id: string): void
  (e: 'cancelar', id: string): void
}>()

const activeTab = ref<'arquivos' | 'timeline' | 'quarentena' | 'decisoes'>('arquivos')
const copiedHash = ref<string | null>(null)
const isExportingCsv = ref(false)
const toastMessage = ref<string | null>(null)

const showToast = (msg: string) => {
  toastMessage.value = msg
  setTimeout(() => {
    if (toastMessage.value === msg) {
      toastMessage.value = null
    }
  }, 3000)
}

const formatShortHash = (hash?: string): string => {
  if (!hash) return 'Não calculado'
  const clean = hash.trim().replace(/^sha256:/i, '')
  if (clean.length <= 16) return clean
  return `${clean.slice(0, 8)}...${clean.slice(-6)}`
}

const selectedVersion = computed(() => {
  return props.versions.find((v) => v.id === props.selectedVersionId) || props.versions[0] || null
})

const copyHash = async (hash?: string) => {
  if (!hash) return
  try {
    await navigator.clipboard.writeText(hash)
    copiedHash.value = hash
    showToast('Hash SHA-256 copiado para a área de transferência!')
    setTimeout(() => {
      if (copiedHash.value === hash) copiedHash.value = null
    }, 2500)
  } catch {
    showToast('Não foi possível copiar o hash.')
  }
}

const exportarRejeicoes = async () => {
  const numericId = Number(String(props.cargaId).replace(/\D/g, ''))
  if (!numericId) return
  isExportingCsv.value = true
  try {
    const blob = await uploadService.exportarRejeicoesCsv(numericId)
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `rejeicoes_carga_${numericId}.csv`
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
    showToast('Relatório de rejeições (CSV) exportado com sucesso!')
  } catch (err: any) {
    showToast('Sem rejeições de quarentena para exportar nesta carga.')
  } finally {
    isExportingCsv.value = false
  }
}

const getStatusBadgeClass = (status?: string) => {
  const s = (status || '').toLowerCase()
  if (s.includes('conclu')) return 'badge-success'
  if (s.includes('andamento')) return 'badge-warning'
  if (s.includes('falh') || s.includes('cancel')) return 'badge-danger'
  if (s.includes('quarentena') || s.includes('valida')) return 'badge-info'
  return 'badge-neutral'
}

const formatDateTime = (dt?: string) => {
  if (!dt) return '—'
  const parsed = new Date(dt)
  return Number.isNaN(parsed.getTime()) ? dt : parsed.toLocaleString('pt-BR')
}
</script>

<template>
  <aside class="audit-drawer-panel" aria-label="Painel de Auditoria e Linhagem">
    <!-- Cabeçalho do Painel -->
    <div class="panel-header">
      <div class="header-titles">
        <span class="eyebrow"><FiShield class="icon-inline" /> US05 &bull; Auditoria e Linhagem</span>
        <h2>Rastreabilidade da Carga</h2>
      </div>
      <button
        v-if="canClose"
        type="button"
        class="btn-close"
        title="Fechar painel de auditoria"
        aria-label="Fechar painel"
        @click="emit('close')"
      >
        &times;
      </button>
    </div>

    <!-- Toast de Feedback -->
    <div v-if="toastMessage" class="audit-toast" role="status">
      <span>{{ toastMessage }}</span>
    </div>

    <!-- Caixa de Metadados Principais da Carga -->
    <div class="carga-summary-card">
      <div class="summary-line">
        <span class="label">ID da Carga:</span>
        <strong class="value">#{{ cargaId || selectedVersion?.id }}</strong>
      </div>
      <div v-if="cargaDataset" class="summary-line">
        <span class="label">Conjunto:</span>
        <span class="value text-truncate" :title="cargaDataset">{{ cargaDataset }}</span>
      </div>
      <div class="summary-line">
        <span class="label">Situação Atual:</span>
        <span class="badge" :class="getStatusBadgeClass(selectedVersion?.status)">
          {{ selectedVersion?.status || 'Em andamento' }}
        </span>
      </div>
    </div>

    <!-- Navegação de Abas do Painel -->
    <nav class="audit-tabs" aria-label="Seções de Auditoria">
      <button
        type="button"
        class="tab-btn"
        :class="{ active: activeTab === 'arquivos' }"
        @click="activeTab = 'arquivos'"
      >
        <FiFileText size="14" />
        Arquivos ({{ versions.length }})
      </button>
      <button
        type="button"
        class="tab-btn"
        :class="{ active: activeTab === 'timeline' }"
        @click="activeTab = 'timeline'"
      >
        <FiClock size="14" />
        Linhagem
      </button>
      <button
        type="button"
        class="tab-btn"
        :class="{ active: activeTab === 'quarentena' }"
        @click="activeTab = 'quarentena'"
      >
        <FiAlertTriangle size="14" />
        Quarentena
      </button>
      <button
        type="button"
        class="tab-btn"
        :class="{ active: activeTab === 'decisoes' }"
        @click="activeTab = 'decisoes'"
      >
        <FiCheckCircle size="14" />
        Governança
      </button>
    </nav>

    <!-- ABA 1: ARQUIVOS & HASHES (REPROCESSAMENTOS) -->
    <div v-if="activeTab === 'arquivos'" class="tab-content">
      <p class="tab-intro">
        Cada tentativa gera um novo Hash SHA-256 e preserva permanentemente o arquivo anterior no histórico:
      </p>

      <div class="version-cards-list">
        <div
          v-for="(ver, idx) in versions"
          :key="ver.id + '-' + idx"
          class="version-card location-item"
          :class="{ 'version-card-active': selectedVersion?.id === ver.id && selectedVersion?.versionNumber === ver.versionNumber }"
          @click="emit('select-version', ver)"
        >
          <div class="card-top">
            <span class="attempt-badge" :class="idx === 0 ? 'badge-primary' : 'badge-secondary'">
              {{ ver.versionTag }}
            </span>
            <span class="badge" :class="getStatusBadgeClass(ver.status)">{{ ver.status }}</span>
          </div>

          <div class="card-body">
            <div class="file-name-row">
              <FiFileText class="file-icon" />
              <strong class="file-name" :title="ver.layerName">{{ ver.layerName }}</strong>
            </div>

            <div class="hash-box">
              <span class="hash-label">Hash SHA-256:</span>
              <div class="hash-row">
                <code class="hash-code" :title="ver.integrityHash">
                  {{ formatShortHash(ver.integrityHash) }}
                </code>
                <button
                  type="button"
                  class="btn-icon"
                  :title="copiedHash === ver.integrityHash ? 'Hash copiado!' : 'Copiar hash SHA-256 completo'"
                  @click.stop="copyHash(ver.integrityHash)"
                >
                  <FiCheck v-if="copiedHash === ver.integrityHash" class="icon-success" />
                  <FiCopy v-else />
                </button>
              </div>
            </div>

            <div class="card-footer-info">
              <span class="date-text">{{ ver.dateTime }}</span>
              <a
                v-if="ver.originalFileUrl && ver.originalFileUrl !== '#'"
                :href="ver.originalFileUrl"
                class="btn-download"
                download
                title="Download do arquivo desta versão"
                @click.stop
              >
                <FiDownload size="12" /> Baixar
              </a>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ABA 2: LINHAGEM TEMPORAL DA ESTEIRA -->
    <div v-else-if="activeTab === 'timeline'" class="tab-content">
      <p class="tab-intro">Histórico completo de passagens da carga pelas etapas da esteira:</p>

      <div v-if="linhagemCompleta?.historicoEtapas && linhagemCompleta.historicoEtapas.length > 0" class="timeline-list">
        <div
          v-for="(h, idx) in linhagemCompleta.historicoEtapas"
          :key="idx"
          class="timeline-item"
        >
          <div class="timeline-bullet" :class="getStatusBadgeClass(h.situacao)"></div>
          <div class="timeline-content">
            <div class="timeline-header">
              <strong>Etapa {{ h.etapa }}</strong>
              <span class="badge" :class="getStatusBadgeClass(h.situacao)">{{ h.situacao }}</span>
            </div>
            <div class="timeline-dates">
              <span>Início: {{ formatDateTime(h.dataInicio) }}</span>
              <span v-if="h.dataFim">&bull; Fim: {{ formatDateTime(h.dataFim) }}</span>
            </div>
            <p v-if="h.mensagem" class="timeline-msg">{{ h.mensagem }}</p>
          </div>
        </div>
      </div>
      <div v-else class="empty-state">
        <p>Aguardando registro de etapas para esta carga.</p>
      </div>
    </div>

    <!-- ABA 3: QUARENTENA & AÇÕES TÉCNICAS (US05 - TAREFAS 16 A 19) -->
    <div v-else-if="activeTab === 'quarentena'" class="tab-content">
      <div class="actions-group">
        <span class="actions-title">Ações Operacionais e Técnicas:</span>

        <button
          type="button"
          class="btn-action btn-outline"
          :disabled="isExportingCsv"
          @click="exportarRejeicoes"
        >
          <FiDownload />
          <span>{{ isExportingCsv ? 'Gerando CSV...' : 'Exportar Rejeições (CSV)' }}</span>
        </button>

        <button
          type="button"
          class="btn-action btn-notify"
          @click="emit('notificar-operador', String(cargaId))"
        >
          <FiBell />
          <span>Notificar Operador para Reprocessar</span>
        </button>

        <button
          type="button"
          class="btn-action btn-reprocess"
          @click="emit('reprocessar', String(cargaId))"
        >
          <FiRefreshCw />
          <span>Reprocessar com Arquivo Corrigido</span>
        </button>

        <button
          type="button"
          class="btn-action btn-cancel"
          @click="emit('cancelar', String(cargaId))"
        >
          <FiXCircle />
          <span>Cancelar / Invalidar Carga</span>
        </button>
      </div>

      <div class="quarantine-logs-box">
        <h4>Logs de Inconsistência e Quarentena</h4>
        <div v-if="linhagemCompleta?.logsQuarentena && linhagemCompleta.logsQuarentena.length > 0" class="quarantine-logs-list">
          <div
            v-for="(log, idx) in linhagemCompleta.logsQuarentena"
            :key="idx"
            class="quarantine-log-entry"
          >
            <div class="log-meta">
              <span class="badge-severidade">{{ log.severidade || 'CRÍTICO' }}</span>
              <span class="log-stage">Origem: {{ log.etapaFalha }}</span>
              <span class="log-time">{{ formatDateTime(log.dataDeteccao) }}</span>
            </div>
            <p class="log-error-text">{{ log.mensagemErro }}</p>
          </div>
        </div>
        <p v-else class="text-muted">Nenhuma ocorrência crítica de quarentena pendente para esta carga.</p>
      </div>
    </div>

    <!-- ABA 4: GOVERNANÇA & DECISÕES DO GESTOR -->
    <div v-else-if="activeTab === 'decisoes'" class="tab-content">
      <div v-if="linhagemCompleta?.decisoesGestor && linhagemCompleta.decisoesGestor.length > 0" class="decisoes-list">
        <div
          v-for="d in linhagemCompleta.decisoesGestor"
          :key="d.id"
          class="decisao-card"
        >
          <div class="decisao-header">
            <strong>Portão: {{ d.etapa }}</strong>
            <span class="badge" :class="d.decisao === 'APROVADO' ? 'badge-success' : 'badge-danger'">
              {{ d.decisao }}
            </span>
          </div>
          <div class="decisao-meta">
            <span>Avaliador: {{ d.gestorNome || 'Gestor' }}</span> &bull;
            <span>Data: {{ formatDateTime(d.dataDecisao) }}</span>
          </div>
          <p v-if="d.parecer" class="decisao-parecer">"{{ d.parecer }}"</p>
        </div>
      </div>
      <div v-else class="empty-state">
        <p>Nenhuma decisão de portão registrada até o momento.</p>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.audit-drawer-panel {
  display: flex;
  flex-direction: column;
  padding: 20px 16px;
  background: var(--color-surface);
  height: 100%;
  min-height: 100%;
  overflow-y: auto;
  overflow-x: hidden;
  box-sizing: border-box;
  width: 100%;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.header-titles .eyebrow {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.74rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--vis-brand-orange, #ea580c);
  margin-bottom: 4px;
}

.header-titles h2 {
  font-size: 1.1rem;
  font-weight: 800;
  color: var(--color-heading);
  margin: 0;
}

.btn-close {
  background: transparent;
  border: none;
  font-size: 1.35rem;
  line-height: 1;
  color: var(--color-text-muted, #64748b);
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 4px;
  transition: all 0.15s;
}

.btn-close:hover {
  color: var(--color-heading);
  background: var(--color-surface-soft, #f8fafc);
}

.audit-toast {
  margin-top: 10px;
  padding: 8px 12px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  color: #166534;
  font-size: 0.8rem;
  font-weight: 600;
  border-radius: 6px;
}

.carga-summary-card {
  margin-top: 14px;
  padding: 12px 14px;
  background: var(--color-surface-soft, #f8fafc);
  border: 1px solid var(--color-border, #e2e8f0);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.summary-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.82rem;
  gap: 8px;
}

.summary-line .label {
  color: var(--color-text-muted, #64748b);
  flex-shrink: 0;
}

.summary-line .value {
  color: var(--color-heading);
  font-weight: 600;
}

.text-truncate {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 220px;
}

.audit-tabs {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 4px;
  margin-top: 14px;
  margin-bottom: 12px;
  background: var(--color-surface-soft, #f1f5f9);
  padding: 4px;
  border-radius: 8px;
}

.tab-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 8px 2px;
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--color-text-muted, #64748b);
  background: transparent;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tab-btn:hover {
  color: var(--color-heading);
}

.tab-btn.active {
  background: #ffffff;
  color: var(--color-heading);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.tab-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tab-intro {
  font-size: 0.78rem;
  color: var(--color-text-muted, #64748b);
  margin: 0;
  line-height: 1.4;
}

.version-cards-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
  min-width: 0;
}

.version-card {
  padding: 12px 14px;
  background: var(--color-surface-soft, #f8fafc);
  border: 1px solid var(--color-border, #e2e8f0);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
  box-sizing: border-box;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  overflow: hidden;
}

.version-card:hover {
  border-color: #cbd5e1;
}

.version-card-active {
  border-color: var(--vis-brand-orange, #ea580c);
  background: #fffaf0;
  box-shadow: 0 0 0 1px var(--vis-brand-orange, #ea580c);
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  min-width: 0;
  width: 100%;
}

.attempt-badge {
  font-size: 0.72rem;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 6px;
  white-space: nowrap;
  flex-shrink: 0;
}

.card-top .badge {
  flex-shrink: 0;
}

.badge-primary {
  background: #e0e7ff;
  color: #3730a3;
}

.badge-secondary {
  background: #fef3c7;
  color: #92400e;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  min-width: 0;
}

.file-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  width: 100%;
}

.file-icon {
  color: var(--vis-brand-orange, #ea580c);
  flex-shrink: 0;
}

.file-name {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--color-heading);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.hash-box {
  background: var(--color-surface, #ffffff);
  border: 1px solid var(--color-border, #e2e8f0);
  border-radius: 6px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  width: 100%;
  box-sizing: border-box;
}

.hash-label {
  font-size: 0.68rem;
  text-transform: uppercase;
  font-weight: 700;
  color: var(--color-text-muted, #64748b);
  letter-spacing: 0.05em;
}

.hash-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
  width: 100%;
}

.hash-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.75rem;
  color: var(--color-text, #0f172a);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.btn-icon {
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--color-text-muted, #64748b);
  padding: 4px;
  border-radius: 4px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.btn-icon:hover {
  color: var(--color-heading);
  background: var(--color-surface-soft, #f1f5f9);
}

.icon-success {
  color: #16a34a;
}

.card-footer-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  font-size: 0.74rem;
  color: var(--color-text-muted, #64748b);
  padding-top: 6px;
  border-top: 1px dashed var(--color-border, #e2e8f0);
  min-width: 0;
  width: 100%;
}

.date-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.btn-download {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #0284c7;
  font-weight: 600;
  text-decoration: none;
  flex-shrink: 0;
}

.btn-download:hover {
  text-decoration: underline;
}

/* Timeline */
.timeline-list {
  display: grid;
  gap: 14px;
  position: relative;
  padding-left: 16px;
  border-left: 2px solid var(--color-border, #e2e8f0);
  margin-left: 6px;
}

.timeline-item {
  position: relative;
}

.timeline-bullet {
  position: absolute;
  left: -23px;
  top: 4px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #cbd5e1;
}

.timeline-bullet.badge-success {
  background: #16a34a;
}
.timeline-bullet.badge-danger {
  background: #dc2626;
}
.timeline-bullet.badge-warning {
  background: #f59e0b;
}

.timeline-content {
  display: grid;
  gap: 3px;
}

.timeline-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.85rem;
}

.timeline-dates {
  font-size: 0.7rem;
  color: var(--color-text-muted, #64748b);
}

.timeline-msg {
  font-size: 0.74rem;
  color: var(--color-heading);
  margin: 2px 0 0 0;
  line-height: 1.35;
}

/* Quarentena e Ações */
.actions-group {
  display: grid;
  gap: 8px;
}

.actions-title {
  font-size: 0.74rem;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--color-text-muted, #64748b);
}

.btn-action {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 8px 12px;
  font-size: 0.8rem;
  font-weight: 700;
  border-radius: 6px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.15s;
}

.btn-outline {
  background: #ffffff;
  border-color: #cbd5e1;
  color: #334155;
}
.btn-outline:hover {
  background: #f8fafc;
}

.btn-notify {
  background: #fef3c7;
  border-color: #fde68a;
  color: #92400e;
}
.btn-notify:hover {
  background: #fde68a;
}

.btn-reprocess {
  background: #e0f2fe;
  border-color: #bae6fd;
  color: #0369a1;
}
.btn-reprocess:hover {
  background: #bae6fd;
}

.btn-cancel {
  background: #fee2e2;
  border-color: #fecaca;
  color: #b91c1c;
}
.btn-cancel:hover {
  background: #fecaca;
}

.quarantine-logs-box {
  margin-top: 10px;
  display: grid;
  gap: 8px;
}

.quarantine-logs-box h4 {
  font-size: 0.82rem;
  font-weight: 700;
  margin: 0;
  color: var(--color-heading);
}

.quarantine-logs-list {
  display: grid;
  gap: 8px;
}

.quarantine-log-entry {
  padding: 8px 10px;
  background: #fff1f2;
  border: 1px solid #fecdd3;
  border-radius: 6px;
  display: grid;
  gap: 4px;
}

.log-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.7rem;
}

.badge-severidade {
  background: #be123c;
  color: #ffffff;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 0.65rem;
}

.log-stage,
.log-time {
  color: #881337;
}

.log-error-text {
  font-size: 0.75rem;
  color: #4c0519;
  margin: 0;
  line-height: 1.35;
}

/* Governança */
.decisoes-list {
  display: grid;
  gap: 10px;
}

.decisao-card {
  padding: 10px;
  background: var(--color-surface-soft, #f8fafc);
  border: 1px solid var(--color-border, #e2e8f0);
  border-radius: 6px;
  display: grid;
  gap: 4px;
}

.decisao-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.82rem;
}

.decisao-meta {
  font-size: 0.7rem;
  color: var(--color-text-muted, #64748b);
}

.decisao-parecer {
  font-size: 0.75rem;
  color: var(--color-heading);
  font-style: italic;
  margin: 2px 0 0 0;
  line-height: 1.35;
}

/* Badges de Status */
.badge {
  font-size: 0.7rem;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
  white-space: nowrap;
}

.badge-success {
  background: #dcfce7;
  color: #166534;
}
.badge-warning {
  background: #fef3c7;
  color: #92400e;
}
.badge-danger {
  background: #fee2e2;
  color: #991b1b;
}
.badge-info {
  background: #e0f2fe;
  color: #075985;
}
.badge-neutral {
  background: #f1f5f9;
  color: #475569;
}

.empty-state {
  padding: 24px 12px;
  text-align: center;
  color: var(--color-text-muted, #64748b);
  font-size: 0.8rem;
}
</style>
