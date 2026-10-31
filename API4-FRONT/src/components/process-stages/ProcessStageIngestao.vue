<script setup lang="ts">
import { ref, computed } from 'vue'
import { FiAlertTriangle, FiCheckCircle, FiGitCommit } from 'vue-icons-plus/fi'
import type {
  ProcessLogDetails,
  ProcessTraceability,
  IndicatorExportFormat,
} from '../../services/process'
import type { ProcessVersion } from '../../services/auditoria'

const props = withDefaults(
  defineProps<{
    details: ProcessLogDetails
    readOnly?: boolean
    traceability?: ProcessTraceability | null
    exportingFormat?: IndicatorExportFormat | null
    canExport?: boolean
    versions?: ProcessVersion[]
  }>(),
  {
    readOnly: false,
    traceability: null,
    exportingFormat: null,
    canExport: false,
    versions: () => [],
  },
)

const emit = defineEmits<{
  (e: 'export', format: IndicatorExportFormat): void
}>()

const isEditing = ref(false)
const formData = ref({
  layerName: props.details.layerName,
  dataset: props.details.dataset,
  source: props.details.source,
  year: props.details.year,
  epsg: props.details.epsg,
  description: props.details.description,
})

const toggleEdit = () => {
  if (props.readOnly) return
  if (isEditing.value) {
    props.details.layerName = formData.value.layerName
    props.details.dataset = formData.value.dataset
    props.details.source = formData.value.source
    props.details.year = formData.value.year
    props.details.epsg = formData.value.epsg
    props.details.description = formData.value.description
  }
  isEditing.value = !isEditing.value
}

/**
 * Comparação entre EPSG Declarado e EPSG Detectado (Tarefa 4)
 */
const epsgDeclared = computed(() => (props.details.epsg || 'EPSG:4674').trim())
const epsgDetected = computed(() => (props.details.epsgDetected || props.details.epsg || 'EPSG:4674').trim())

const normalizeEpsgCode = (val: string) => {
  const match = val.match(/EPSG:\d+/i)
  return match ? match[0].toUpperCase() : val.toUpperCase()
}

const hasEpsgDivergence = computed(
  () => normalizeEpsgCode(epsgDeclared.value) !== normalizeEpsgCode(epsgDetected.value),
)

const resolvedHash = computed(
  () => props.details.integrityHash || props.traceability?.hashSha256 || '—',
)

const resolvedFileName = computed(
  () => props.traceability?.nomeArquivoOriginal || `carga_${props.details.id}.geojson`,
)

/**
 * Árvore de Linhagem Reversa (Tarefa 3):
 * Status de Publicação -> Resultado do Tratamento/Cálculo -> Resultado da Validação ->
 * Histórico de Reprocessamentos -> Metadados Ricos -> Arquivo Bruto Original e Código Hash
 */
const lineageTree = computed(() => {
  const pubStatus =
    props.details.stage === 'Publicação' && props.details.status === 'Concluída'
      ? 'Publicado e Vigente'
      : props.details.stage === 'Publicação'
        ? props.details.status
        : 'Aguardando etapas anteriores'

  const treatErrors = (props.details.treatmentChecks || []).filter((c) => c.status === 'error').length
  const treatStatus =
    treatErrors > 0
      ? `${treatErrors} inconsistência(s) no tratamento`
      : hasEpsgDivergence.value
        ? `Concluído com ressalva (${epsgDeclared.value} → ${epsgDetected.value})`
        : 'Geometrias e cálculo de áreas validados'

  const valErrors = (props.details.validationChecks || []).filter((c) => c.status === 'error').length
  const valStatus =
    valErrors > 0
      ? `${valErrors} falha(s) de validação detectada(s)`
      : 'Topologia e schema aprovados'

  const reprocessCount = Math.max(0, (props.versions?.length || 1) - 1)
  const reprocessDesc =
    reprocessCount > 0
      ? `${reprocessCount} tentativa(s) anterior(es) registrada(s) no histórico`
      : 'Carga original (sem reprocessamentos anteriores)'

  return [
    {
      step: '1. Status de Publicação',
      detail: pubStatus,
      status: pubStatus.includes('Vigente') ? 'ok' : 'neutral',
    },
    {
      step: '2. Resultado do Tratamento / Cálculo',
      detail: treatStatus,
      status: treatErrors > 0 ? 'error' : hasEpsgDivergence.value ? 'warning' : 'ok',
    },
    {
      step: '3. Resultado da Validação',
      detail: valStatus,
      status: valErrors > 0 ? 'error' : 'ok',
    },
    {
      step: '4. Histórico de Reprocessamentos',
      detail: reprocessDesc,
      status: 'neutral',
    },
    {
      step: '5. Metadados Ricos',
      detail: `${props.details.source} • Safra ${props.details.year} • ${epsgDeclared.value}`,
      status: hasEpsgDivergence.value ? 'warning' : 'ok',
    },
    {
      step: '6. Arquivo Bruto Original e Código Hash',
      detail: `${resolvedFileName.value} • SHA-256: ${resolvedHash.value}`,
      status: 'ok',
    },
  ]
})
</script>

<template>
  <div class="ingestao-stage">
    <!-- Alerta Visual de Divergência de EPSG (Tarefa 4) -->
    <div
      v-if="hasEpsgDivergence"
      class="epsg-divergence-alert mb-4"
      role="alert"
      data-testid="epsg-divergence-banner"
    >
      <FiAlertTriangle size="20" class="epsg-alert-icon" />
      <div>
        <strong>Divergência de Projeção Cartográfica (Com Ressalva)</strong>
        <p>
          O sistema detectou que o <strong>EPSG Declarado ({{ epsgDeclared }})</strong> diverge do
          <strong>EPSG Detectado no arquivo ({{ epsgDetected }})</strong>. A reprojeção automática
          para SIRGAS 2000 foi agendada na etapa de Tratamento.
        </p>
      </div>
    </div>

    <div class="card card-content">
      <div class="header-with-action">
        <div style="display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap">
          <h3>Dados Iniciais e Origem do Indicador (Ingestão)</h3>
          <span class="badge badge_success">Concluída</span>
          <span
            v-if="hasEpsgDivergence"
            class="badge badge_warning"
            data-testid="epsg-warning-badge"
          >
            Com Ressalva (EPSG)
          </span>
        </div>
        <div class="header-actions">
          <button
            v-if="canExport"
            type="button"
            class="btn btn_outline btn-sm"
            :disabled="!!exportingFormat"
            @click="emit('export', 'geojson')"
          >
            {{ exportingFormat === 'geojson' ? 'Exportando…' : 'Exportar GeoJSON' }}
          </button>
          <button
            v-if="canExport"
            type="button"
            class="btn btn_outline btn-sm"
            :disabled="!!exportingFormat"
            @click="emit('export', 'csv')"
          >
            {{ exportingFormat === 'csv' ? 'Exportando…' : 'Exportar CSV' }}
          </button>
          <button
            v-if="!readOnly"
            type="button"
            class="btn btn_outline btn-sm"
            data-testid="btn-edit-metadata"
            @click="toggleEdit"
          >
            {{ isEditing ? 'Salvar' : 'Editar' }}
          </button>
        </div>
      </div>

      <!-- Painel Comparativo de EPSG (Tarefa 4) -->
      <div
        class="epsg-comparison-box mb-4"
        :class="{ 'epsg-comparison-box--warning': hasEpsgDivergence }"
        data-testid="epsg-comparison-panel"
      >
        <div class="epsg-col">
          <span class="epsg-label">EPSG Declarado (Formulário)</span>
          <strong class="epsg-code" data-testid="epsg-declared">{{ epsgDeclared }}</strong>
        </div>
        <div class="epsg-divider">vs.</div>
        <div class="epsg-col">
          <span class="epsg-label">EPSG Detectado (Arquivo Bruto)</span>
          <strong class="epsg-code" data-testid="epsg-detected">{{ epsgDetected }}</strong>
        </div>
        <div class="epsg-status">
          <span v-if="hasEpsgDivergence" class="epsg-tag-warning">
            <FiAlertTriangle size="14" /> Divergente
          </span>
          <span v-else class="epsg-tag-ok">
            <FiCheckCircle size="14" /> Compatível
          </span>
        </div>
      </div>

      <!-- Grid de Metadados Ricos (Tarefa 4) -->
      <div class="form-grid">
        <div class="form-group form-group--full">
          <label>Nome da Fonte de Dados / Camada</label>
          <input
            v-if="isEditing && !readOnly"
            type="text"
            v-model="formData.layerName"
            class="form-control"
          />
          <p v-else class="form-value">{{ details.layerName }}</p>
        </div>

        <div class="form-group">
          <label>Conjunto de Dados</label>
          <input
            v-if="isEditing && !readOnly"
            type="text"
            v-model="formData.dataset"
            class="form-control"
          />
          <p v-else class="form-value">{{ details.dataset }}</p>
        </div>

        <div class="form-group">
          <label>Fonte / Órgão Emissor</label>
          <input
            v-if="isEditing && !readOnly"
            type="text"
            v-model="formData.source"
            class="form-control"
          />
          <p v-else class="form-value">{{ details.source }}</p>
        </div>

        <div class="form-group">
          <label>Ano Base / Competência</label>
          <input
            v-if="isEditing && !readOnly"
            type="text"
            v-model="formData.year"
            class="form-control"
          />
          <p v-else class="form-value">{{ details.year }}</p>
        </div>

        <div class="form-group">
          <label>Data / Hora da Ingestão</label>
          <p class="form-value">{{ traceability?.dataCarga || details.dateTime || '—' }}</p>
        </div>

        <div class="form-group">
          <label>Arquivo Bruto Original</label>
          <p class="form-value">
            {{ resolvedFileName }}
            <span v-if="traceability?.arquivoOriginalId" class="file-id-tag">
              (ID #{{ traceability.arquivoOriginalId }})
            </span>
          </p>
        </div>

        <div class="form-group">
          <label>Competência</label>
          <p class="form-value" data-testid="meta-competencia">{{ details.competencia || '—' }}</p>
        </div>

        <div class="form-group">
          <label>Formato / Classificação de Acesso</label>
          <p class="form-value" data-testid="meta-formato">
            {{ details.formato || '—' }} • {{ details.classificacaoAcesso || '—' }}
          </p>
        </div>

        <div class="form-group form-group--full">
          <label>Cobertura Espacial / Temporal</label>
          <p class="form-value" data-testid="meta-cobertura">
            {{ details.coberturaEspacial || '—' }}
            <template v-if="details.coberturaTemporalInicio || details.coberturaTemporalFim">
              • {{ details.coberturaTemporalInicio || '…' }} a {{ details.coberturaTemporalFim || '…' }}
            </template>
          </p>
        </div>

        <div class="form-group">
          <label>Responsável</label>
          <p class="form-value">{{ details.assignedEditor || 'Operador de Carga' }}</p>
        </div>

        <div v-if="traceability?.parametrosProcessamento" class="form-group form-group--full">
          <label>Parâmetros de Processamento</label>
          <p class="form-value">{{ traceability.parametrosProcessamento }}</p>
        </div>

        <div class="form-group form-group--full">
          <label>Descrição</label>
          <textarea
            v-if="isEditing && !readOnly"
            v-model="formData.description"
            class="form-control textarea"
          ></textarea>
          <p v-else class="form-value">{{ details.description || 'Sem descrição' }}</p>
        </div>

        <div class="form-group form-group--full">
          <label>Hash de Integridade (SHA-256)</label>
          <p class="form-value hash-value" data-testid="integrity-hash">
            {{ resolvedHash }}
          </p>
          <span class="hash-hint">
            Gerado automaticamente pelo backend a partir do arquivo bruto original.
          </span>
        </div>
      </div>

      <!-- Árvore Visual de Linhagem Reversa (Tarefa 3) -->
      <section class="lineage-tree-section mt-4" aria-label="Árvore de Linhagem Reversa" data-testid="lineage-tree">
        <h4 class="lineage-tree-title">
          <FiGitCommit size="16" />
          Árvore de Linhagem e Rastreabilidade (Linha do Tempo Reversa)
        </h4>
        <ol class="lineage-tree-list">
          <li
            v-for="(node, idx) in lineageTree"
            :key="idx"
            class="lineage-node"
            :class="`lineage-node--${node.status}`"
            data-testid="lineage-node"
          >
            <div class="lineage-node-marker"></div>
            <div class="lineage-node-content">
              <strong>{{ node.step }}</strong>
              <span>{{ node.detail }}</span>
            </div>
          </li>
        </ol>
      </section>

      <div class="mt-4">
        <a
          :href="details.originalFileUrl"
          target="_blank"
          class="btn btn_primary full-width-btn"
          download
        >
          Baixar Arquivo Original
        </a>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ingestao-stage {
  width: 100%;
}

.mb-4 {
  margin-bottom: 1rem;
}

.epsg-divergence-alert {
  display: flex;
  align-items: flex-start;
  gap: 0.85rem;
  padding: 1rem 1.25rem;
  border-radius: 8px;
  background: rgba(245, 158, 11, 0.14);
  border: 1px solid rgba(245, 158, 11, 0.4);
  border-left: 4px solid #d97706;
  color: var(--color-text);
}

.epsg-alert-icon {
  color: #d97706;
  flex-shrink: 0;
  margin-top: 2px;
}

.epsg-divergence-alert strong {
  color: #b45309;
  display: block;
  margin-bottom: 0.2rem;
}

.epsg-divergence-alert p {
  margin: 0;
  font-size: 0.88rem;
}

.epsg-comparison-box {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 1rem;
  padding: 0.9rem 1.15rem;
  border-radius: 8px;
  background: var(--color-background);
  border: 1px solid var(--color-border);
}

.epsg-comparison-box--warning {
  background: rgba(245, 158, 11, 0.1);
  border-color: #f59e0b;
}

.epsg-col {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.epsg-label {
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--vis-c-text-dark-2);
}

.epsg-code {
  font-family: monospace;
  font-size: 0.95rem;
  color: var(--color-heading);
}

.epsg-divider {
  font-size: 0.8rem;
  font-weight: 700;
  color: var(--vis-c-text-dark-2);
}

.epsg-tag-warning {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  background: #fef3c7;
  color: #b45309;
  border: 1px solid #f59e0b;
  padding: 0.25rem 0.65rem;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 700;
}

.epsg-tag-ok {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  background: #dcfce7;
  color: #15803d;
  border: 1px solid #86efac;
  padding: 0.25rem 0.65rem;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 700;
}

.badge_warning {
  background: #fef3c7;
  color: #b45309;
  padding: 0.25rem 0.6rem;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 700;
}

.card {
  background: var(--color-surface);
  border-radius: 8px;
  border: 1px solid var(--color-border);
}

.card-content {
  padding: 1.5rem;
}

.header-with-action {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-bottom: 1rem;
  border-bottom: 1px solid var(--color-border);
  padding-bottom: 0.75rem;
}

.header-with-action h3 {
  margin: 0;
  font-size: 1.1rem;
  color: var(--color-heading);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.btn-sm {
  padding: 0.3rem 0.75rem;
  font-size: 0.82rem;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.form-group {
  display: flex;
  flex-direction: column;
}

.form-group--full {
  grid-column: 1 / -1;
}

.form-group label {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--color-text);
  opacity: 0.7;
  margin-bottom: 0.25rem;
}

.form-value {
  margin: 0;
  font-size: 0.96rem;
  font-weight: 500;
  color: var(--color-heading);
}

.file-id-tag {
  font-size: 0.82rem;
  color: var(--vis-c-text-dark-2);
  font-weight: 400;
}

.form-control {
  padding: 0.5rem;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  background-color: var(--color-background);
  color: var(--color-text);
  font-size: 0.9rem;
}

.textarea {
  min-height: 80px;
  resize: vertical;
  font-family: inherit;
}

.form-control:focus {
  outline: none;
  border-color: var(--vis-brand-orange);
}

.mt-4 {
  margin-top: 1.75rem;
}

.full-width-btn {
  width: 100%;
  text-align: center;
  justify-content: center;
}

.hash-value {
  font-family: 'Courier New', Courier, monospace;
  font-size: 0.85rem;
  background: var(--color-background);
  border: 1px solid var(--color-border);
  border-radius: 4px;
  padding: 0.5rem 0.75rem;
  word-break: break-all;
  color: var(--color-text);
  user-select: all;
}

.hash-hint {
  font-size: 0.75rem;
  color: var(--color-text);
  opacity: 0.6;
  margin-top: 0.25rem;
}

/* Árvore de Linhagem Reversa */
.lineage-tree-section {
  padding: 1.15rem;
  border-radius: 8px;
  background: var(--color-background);
  border: 1px solid var(--color-border);
}

.lineage-tree-title {
  margin: 0 0 1rem;
  font-size: 0.92rem;
  font-weight: 700;
  color: var(--color-heading);
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.lineage-tree-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
}

.lineage-node {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 0.55rem 0.75rem;
  border-radius: 6px;
  background: var(--color-surface);
  border-left: 3px solid var(--vis-brand-orange, #f26522);
}

.lineage-node--ok {
  border-left-color: #16a34a;
}

.lineage-node--warning {
  border-left-color: #d97706;
}

.lineage-node--error {
  border-left-color: #dc2626;
}

.lineage-node-marker {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--vis-brand-orange, #f26522);
  margin-top: 6px;
  flex-shrink: 0;
}

.lineage-node--ok .lineage-node-marker {
  background: #16a34a;
}

.lineage-node--warning .lineage-node-marker {
  background: #d97706;
}

.lineage-node--error .lineage-node-marker {
  background: #dc2626;
}

.lineage-node-content {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  font-size: 0.84rem;
}

.lineage-node-content strong {
  color: var(--color-heading);
}

.lineage-node-content span {
  color: var(--vis-c-text-dark-2);
  word-break: break-all;
}
</style>
