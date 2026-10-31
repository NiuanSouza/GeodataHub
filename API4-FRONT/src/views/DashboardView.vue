<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import CardContainer from '../components/CardContainer.vue'
import DataTable from '../components/DataTable.vue'
import type { Column } from '../components/DataTable.vue'
import {
  dashboardService,
  type DashboardSummary,
  type ProcessLog,
} from '../services/dashboard'
import { processService } from '../services/process'
import { userService } from '../services/user'
import Stepper from '../components/Stepper.vue'
import {
  FiPlus,
  FiMinus,
  FiAlertTriangle,
  FiUserPlus,
  FiUserCheck,
  FiCheckCircle,
  FiShield,
} from 'vue-icons-plus/fi'

const route = useRoute()
const router = useRouter()

// Usuário e permissões
const currentUser = computed(() => userService.getCurrentUser())
const isAdmin = computed(() => userService.isAdmin())
const isAuditorRole = computed(() => currentUser.value.role === 'Auditor')
const isOperadorRole = computed(() => currentUser.value.role === 'Operador')

/**
 * Modo de visualização de auditoria:
 * Ativado quando a rota for /auditor ou quando o usuário logado for Auditor.
 */
const isAuditorView = computed(() => route.path === '/auditor' || isAuditorRole.value)

// Permissões de ação
const canUpload = computed(() => (isAdmin.value || isOperadorRole.value) && !isAuditorView.value)
const canAssignSpecialist = computed(() => isAdmin.value && !isAuditorView.value)
const canSignalOperator = computed(() => isAdmin.value && !isAuditorView.value)

const summaryData = ref<DashboardSummary[]>([])
const recentProcesses = ref<ProcessLog[]>([])
const filterOptions = ref<{ conjuntos: any[]; etapas: any[]; situacoes: any[] }>({
  conjuntos: [],
  etapas: [],
  situacoes: [],
})

const isInitialLoading = ref(true)
const isTableLoading = ref(false)
const auditorStatus = ref<Record<string, 'loading' | 'requested' | 'error'>>({})

// Filtro rápido por categoria
type ErrorCategory = 'TODAS' | 'ATTENTION' | 'QUARENTENA' | 'VALIDACAO' | 'TRATAMENTO' | 'EM_CORRECAO'
const selectedCategory = ref<ErrorCategory>('TODAS')

// Modal de alocação de especialista
const showAssignModal = ref(false)
const selectedProcessForEditor = ref<ProcessLog | null>(null)
const selectedEditorId = ref<number | ''>('')
const availableEditors = ref<{ id: number; name: string }[]>([])
const isAssigning = ref(false)
const notificationMessage = ref<string | null>(null)

// Acesso à visualização completa do processo e linhagem para todos os perfis
const canViewFullProcess = computed(() => true)

const processSteps = ['Ingestão', 'Validação', 'Tratamento', 'Publicação']

const getStepIndex = (item: ProcessLog): number => {
  const stage = (item.stage || '').toLowerCase()
  if (stage.includes('publica')) return 4
  if (stage.includes('trata')) return 3
  if (stage.includes('valida')) return 2
  return 1
}

const isProcessDone = (item: ProcessLog) => (item.status || '').toLowerCase().includes('conclu')

const getProgressStep = (item: ProcessLog): number => {
  const idx = getStepIndex(item)
  return isProcessDone(item) && idx === processSteps.length ? idx + 1 : idx
}

const getWarningStep = (item: ProcessLog): number | undefined => {
  const status = (item.status || '').toLowerCase()
  if (status.includes('falh') || status.includes('quarentena') || status.includes('ressalva') || item.quarantined) {
    return getStepIndex(item)
  }
  return undefined
}

const goToLog = (id: string) => {
  router.push({ name: 'process-log', params: { id } })
}

const filters = ref({
  dateBeggin: '',
  dateEnd: '',
  conjunto: '',
  etapa: '',
  situacao: '',
})

const tableColumns: Column[] = [
  { key: 'id', label: 'ID CARGA' },
  { key: 'dateTime', label: 'DATA/HORA' },
  { key: 'dataset', label: 'CONJUNTO' },
  { key: 'stage', label: 'ETAPA' },
  { key: 'status', label: 'SITUAÇÃO' },
  { key: 'actions', label: 'AÇÕES' },
]

/**
 * Identifica se um processo possui erro, inconsistência ou retenção.
 */
const isErrorOrQuarantined = (item: ProcessLog): boolean => {
  const status = (item.status || '').toLowerCase()
  const stage = (item.stage || '').toLowerCase()

  if (
    status.includes('falh') ||
    status.includes('quarentena') ||
    status.includes('valida') ||
    status.includes('ressalva')
  ) {
    return true
  }

  if (stage.includes('valida') || stage.includes('trata')) {
    if (!status.includes('conclu')) {
      return true
    }
  }

  if (item.assignedEditor) {
    return true
  }

  if (item.quarantined || Boolean(item.pauseReason)) {
    return true
  }

  return false
}

/**
 * Processos exibidos aplicando abas de categoria rápida e filtros tradicionais.
 */
const displayedProcesses = computed(() => {
  return recentProcesses.value.filter((item) => {
    const status = (item.status || '').toLowerCase()
    const stage = (item.stage || '').toLowerCase()

    if (selectedCategory.value === 'ATTENTION') {
      if (!isErrorOrQuarantined(item)) return false
    } else if (selectedCategory.value === 'QUARENTENA') {
      if (!status.includes('quarentena') && !item.quarantined) return false
    } else if (selectedCategory.value === 'VALIDACAO') {
      if (!stage.includes('valida')) return false
    } else if (selectedCategory.value === 'TRATAMENTO') {
      if (!stage.includes('trata')) return false
    } else if (selectedCategory.value === 'EM_CORRECAO') {
      if (!item.assignedEditor) return false
    }

    // Filtros por campos do formulário
    if (filters.value.conjunto && String(item.dataset) !== String(filters.value.conjunto)) {
      const opt = filterOptions.value.conjuntos.find((c) => String(c.id) === String(filters.value.conjunto))
      if (opt && item.dataset !== opt.label) return false
    }

    if (filters.value.etapa) {
      const opt = filterOptions.value.etapas.find((e) => String(e.id) === String(filters.value.etapa))
      if (opt && !stage.includes(opt.label.toLowerCase())) return false
    }

    if (filters.value.situacao) {
      const opt = filterOptions.value.situacoes.find((s) => String(s.id) === String(filters.value.situacao))
      if (opt && !status.includes(opt.label.toLowerCase())) return false
    }

    return true
  })
})

/**
 * Resumo dos cards gerado de acordo com a permissão / modo de visualização.
 */
const displayedSummary = computed<DashboardSummary[]>(() => {
  if (isAuditorView.value) {
    const allErrors = recentProcesses.value.filter(isErrorOrQuarantined)
    const quarentenaTotal = recentProcesses.value.filter(
      (p) => (p.status || '').toLowerCase().includes('quarentena') || p.quarantined,
    ).length
    const emCorrecaoTotal = recentProcesses.value.filter((p) => Boolean(p.assignedEditor)).length
    const aguardandoValidaTotal = recentProcesses.value.filter(
      (p) =>
        (p.stage || '').toLowerCase().includes('valida') ||
        (p.status || '').toLowerCase().includes('valida'),
    ).length

    return [
      {
        title: 'Cargas que Requerem Atenção',
        value: allErrors.length,
        type: 'danger',
        iconType: 'total',
      },
      {
        title: 'Cargas em Quarentena',
        value: quarentenaTotal,
        type: 'danger',
        iconType: 'danger',
      },
      {
        title: 'Pendentes de Validação',
        value: aguardandoValidaTotal,
        type: 'warning',
        iconType: 'warning',
      },
      {
        title: 'Especialista Alocado',
        value: emCorrecaoTotal,
        type: 'info',
        iconType: 'info',
      },
    ]
  }

  // Dashboard Operacional Padrão
  if (summaryData.value.length > 0) {
    return summaryData.value
  }

  return [
    { title: 'Total de Cargas', value: recentProcesses.value.length, type: 'info', iconType: 'total' },
    {
      title: 'Cargas Concluídas',
      value: recentProcesses.value.filter((p) => (p.status || '').toLowerCase().includes('conclu')).length,
      type: 'success',
      iconType: 'success',
    },
    {
      title: 'Inconsistências / Falhas',
      value: recentProcesses.value.filter(isErrorOrQuarantined).length,
      type: 'danger',
      iconType: 'danger',
    },
    {
      title: 'Em Andamento',
      value: recentProcesses.value.filter((p) => (p.status || '').toLowerCase().includes('andamento')).length,
      type: 'warning',
      iconType: 'warning',
    },
  ]
})

const loadData = async (currentFilters?: any) => {
  if (!isInitialLoading.value) {
    isTableLoading.value = true
  }
  try {
    const page = await dashboardService.getDashboardProcesses(currentFilters)
    recentProcesses.value = page.content || []
  } catch (error) {
    console.error('Failed to fetch dashboard processes', error)
  } finally {
    isInitialLoading.value = false
    isTableLoading.value = false
  }
}

const loadSummary = async () => {
  try {
    const summary = await dashboardService.getDashboardSummary()
    summaryData.value = summary
  } catch (error) {
    console.error('Failed to fetch dashboard summary', error)
  }
}

onMounted(async () => {
  // Ajusta a aba padrão dependendo do papel
  if (isAuditorView.value) {
    selectedCategory.value = 'ATTENTION'
  } else {
    selectedCategory.value = 'TODAS'
  }

  dashboardService.getFilterOptions().then((options) => {
    filterOptions.value = options
  })
  availableEditors.value = await dashboardService.getAvailableEditors()
  await Promise.all([loadSummary(), loadData()])
})

const applyFilters = async () => {
  await loadData(filters.value)
}

/**
 * Erro ou retenção pendente de especialista.
 */
const hasErrorAwaitingEditor = (item: ProcessLog) => {
  if (item.assignedEditor) return false
  const status = (item.status || '').toLowerCase()
  if (status.includes('conclu')) return false
  const stage = (item.stage || '').toLowerCase()
  return (
    status.includes('falh') ||
    status.includes('quarentena') ||
    status.includes('ressalva') ||
    status.includes('valida') ||
    stage.includes('valida') ||
    stage.includes('trata')
  )
}

const openAssignModal = (item: ProcessLog) => {
  selectedProcessForEditor.value = item
  if (availableEditors.value.length > 0 && selectedEditorId.value === '') {
    const firstEditor = availableEditors.value[0]
    if (firstEditor) selectedEditorId.value = firstEditor.id
  }
  showAssignModal.value = true
}

const confirmAssignEditor = async () => {
  if (!selectedProcessForEditor.value || selectedEditorId.value === '') return
  isAssigning.value = true
  try {
    const editorObj = availableEditors.value.find((e) => e.id === selectedEditorId.value)
    const editorName = editorObj ? editorObj.name : 'Desconhecido'

    const updated = await dashboardService.assignEditor(
      selectedProcessForEditor.value.id,
      Number(selectedEditorId.value),
      editorName,
    )

    if (/^\d+$/.test(String(selectedProcessForEditor.value.id))) {
      try {
        await processService.alocarAuditor(
          selectedProcessForEditor.value.id,
          Number(selectedEditorId.value),
          currentUser.value.id || 1,
        )
      } catch (err) {
        console.warn('Erro ao chamar /carga/alocar-auditor no backend:', err)
      }
    }

    if (updated) {
      selectedProcessForEditor.value.assignedEditor = editorName
      selectedProcessForEditor.value.status = 'Em andamento'
      selectedProcessForEditor.value.pauseReason = `Especialista ${editorName} alocado para correção do erro na etapa de ${selectedProcessForEditor.value.stage}.`

      await loadSummary()

      notificationMessage.value = `Especialista ${editorName} alocado para a carga ${selectedProcessForEditor.value.id}! Processo agora em andamento.`
      setTimeout(() => {
        notificationMessage.value = null
      }, 5000)
    }
  } catch (err) {
    console.error('Falha ao alocar especialista', err)
  } finally {
    isAssigning.value = false
    showAssignModal.value = false
    selectedProcessForEditor.value = null
  }
}

const getBadgeClass = (status: string) => {
  const norm = (status || '').toLowerCase()
  if (norm.includes('conclu')) return 'badge badge_success'
  if (norm.includes('andamento')) return 'badge badge_info'
  if (norm.includes('valida')) return 'badge badge_warning'
  if (norm.includes('falh') || norm.includes('quarentena')) return 'badge badge_danger'
  return 'badge badge_primary'
}

const getProcessLogs = (item: ProcessLog): string[] => {
  if (item.logs?.length) return item.logs
  return item.pauseReason ? [item.pauseReason] : []
}

const requestAuditor = async (item: ProcessLog) => {
  auditorStatus.value[item.id] = 'loading'
  try {
    await dashboardService.requestAuditor(item.id)
    item.auditorRequested = true
    auditorStatus.value[item.id] = 'requested'
  } catch (error) {
    console.error('Failed to request auditor', error)
    auditorStatus.value[item.id] = 'error'
  }
}
</script>

<template>
  <main class="dashboard">
    <!-- Cabeçalho Dinâmico gerado de acordo com a permissão/modo -->
    <header class="page-heading">
      <div>
        <p class="eyebrow">{{ isAuditorView ? 'Auditoria Operacional' : 'Monitoramento do Sistema' }}</p>
        <h1>{{ isAuditorView ? 'Painel do Auditor' : 'Dashboard Operacional' }}</h1>
        <p class="page-description">
          {{
            isAuditorView
              ? 'Supervisão e auditoria de linhagem de cargas do sistema, inconsistências e retenções.'
              : 'Acompanhamento em tempo real da esteira de ingestão, validação, tratamento e publicação.'
          }}
        </p>
      </div>

      <div v-if="isAuditorView" class="auditor-badge-header">
        <FiShield size="16" />
        <span>Perfil Auditor (Modo Consulta & Linhagem)</span>
      </div>
      <div v-else-if="isAdmin" class="admin-badge-header">
        <FiShield size="16" />
        <span>Acesso Geral (Gestor)</span>
      </div>
    </header>

    <div v-if="isInitialLoading" class="loading-state">
      <div class="spinner"></div>
      <p>Carregando dados do sistema...</p>
    </div>

    <template v-else>
      <!-- Notificação de Alocação de Especialista -->
      <Transition name="fade">
        <div v-if="notificationMessage" class="alert alert-success-notification mb-3">
          <FiCheckCircle size="18" />
          <span>{{ notificationMessage }}</span>
        </div>
      </Transition>

      <!-- Cards de Resumo Gerados Dinamicamente -->
      <div class="card-grid">
        <CardContainer
          v-for="(item, index) in displayedSummary"
          :key="index"
          :title="item.title"
          :value="item.value"
          :variant="item.type"
          :iconType="item.iconType"
        />
      </div>

      <!-- Filtro Rápido em Abas por Categoria Operacional -->
      <div class="category-tabs mt-4">
        <button
          type="button"
          class="category-tab-btn"
          :class="{ active: selectedCategory === 'TODAS' }"
          @click="selectedCategory = 'TODAS'"
        >
          Todas as Cargas ({{ recentProcesses.length }})
        </button>
        <button
          type="button"
          class="category-tab-btn"
          :class="{ active: selectedCategory === 'ATTENTION' }"
          @click="selectedCategory = 'ATTENTION'"
        >
          Requerem Atenção ({{ recentProcesses.filter(isErrorOrQuarantined).length }})
        </button>
        <button
          type="button"
          class="category-tab-btn"
          :class="{ active: selectedCategory === 'QUARENTENA' }"
          @click="selectedCategory = 'QUARENTENA'"
        >
          Quarentena ({{ recentProcesses.filter((p) => (p.status || '').toLowerCase().includes('quarentena') || p.quarantined).length }})
        </button>
        <button
          type="button"
          class="category-tab-btn"
          :class="{ active: selectedCategory === 'VALIDACAO' }"
          @click="selectedCategory = 'VALIDACAO'"
        >
          Validação ({{ recentProcesses.filter((p) => (p.stage || '').toLowerCase().includes('valida')).length }})
        </button>
        <button
          type="button"
          class="category-tab-btn"
          :class="{ active: selectedCategory === 'TRATAMENTO' }"
          @click="selectedCategory = 'TRATAMENTO'"
        >
          Tratamento ({{ recentProcesses.filter((p) => (p.stage || '').toLowerCase().includes('trata')).length }})
        </button>
        <button
          type="button"
          class="category-tab-btn"
          :class="{ active: selectedCategory === 'EM_CORRECAO' }"
          @click="selectedCategory = 'EM_CORRECAO'"
        >
          Com Especialista ({{ recentProcesses.filter((p) => Boolean(p.assignedEditor)).length }})
        </button>
      </div>

      <!-- Barra de Filtros Tradicionais -->
      <div class="card card-filters mt-3">
        <div class="card-content filters-container">
          <div class="filter-item filter-dates">
            <div class="date-row">
              <label for="dateBeggin">Início:</label>
              <input
                type="date"
                id="dateBeggin"
                name="dateBeggin"
                class="input-inline"
                v-model="filters.dateBeggin"
              />
            </div>
            <div class="date-row">
              <label for="dateEnd">Fim:</label>
              <input
                type="date"
                id="dateEnd"
                name="dateEnd"
                class="input-inline"
                v-model="filters.dateEnd"
              />
            </div>
          </div>

          <div class="filter-item">
            <label>Conjunto:</label>
            <select class="input-inline" v-model="filters.conjunto">
              <option value="">Todos</option>
              <option v-for="opt in filterOptions.conjuntos" :key="opt.id" :value="opt.id">
                {{ opt.label }}
              </option>
            </select>
          </div>

          <div class="filter-item">
            <label>Etapa:</label>
            <select class="input-inline" v-model="filters.etapa">
              <option value="">Todas</option>
              <option v-for="opt in filterOptions.etapas" :key="opt.id" :value="opt.id">
                {{ opt.label }}
              </option>
            </select>
          </div>

          <div class="filter-item">
            <label>Situação:</label>
            <select class="input-inline" v-model="filters.situacao">
              <option value="">Todas</option>
              <option v-for="opt in filterOptions.situacoes" :key="opt.id" :value="opt.id">
                {{ opt.label }}
              </option>
            </select>
          </div>

          <div class="filter-actions">
            <button class="btn btn-filter" @click="applyFilters">FILTRAR</button>
          </div>
        </div>
      </div>

      <!-- Tabela Unificada com DataTable -->
      <div class="mt-4">
        <div v-if="isTableLoading" class="loading-state" style="padding: 2rem">
          <div class="spinner"></div>
          <p>Atualizando tabela...</p>
        </div>

        <div v-else-if="displayedProcesses.length === 0" class="empty-state card card-content">
          <FiCheckCircle size="42" class="text-success mb-2" />
          <h3>Nenhuma carga encontrada nesta categoria</h3>
          <p class="text-muted">Não há registros atendendo aos filtros selecionados.</p>
        </div>

        <DataTable v-else :columns="tableColumns" :data="displayedProcesses" rowKey="id">
          <template #cell-status="{ item, value }">
            <div style="display: flex; align-items: center; gap: 0.4rem; flex-wrap: wrap">
              <span :class="getBadgeClass(value)">{{ value }}</span>
              <span
                v-if="item.assignedEditor"
                class="badge badge-editor-assigned"
                title="Especialista alocado para resolução"
              >
                <FiUserCheck size="11" /> {{ item.assignedEditor }}
              </span>
            </div>
          </template>

          <template #cell-actions="{ isExpanded }">
            <div class="table-actions">
              <span class="action-icon">
                <FiMinus v-if="isExpanded" size="18" />
                <FiPlus v-else size="18" />
              </span>
            </div>
          </template>

          <template #expanded-row="{ item }">
            <div class="expanded-details">
              <p v-if="item.source">
                <strong>Fonte:</strong> {{ item.source }} | <strong>Ano:</strong> {{ item.year }} |
                <strong>EPSG:</strong> {{ item.epsg }}
              </p>

              <!-- Stepper com o estado atual do processo -->
              <div class="expanded-stepper">
                <Stepper
                  :currentStep="getStepIndex(item)"
                  :progressStep="getProgressStep(item)"
                  :warningStep="getWarningStep(item)"
                  :steps="processSteps"
                />
              </div>

              <!-- Motivo da Inconsistência -->
              <div v-if="item.pauseReason" class="alert alert-warning mt-2">
                <span class="alert-icon">⚠️</span>
                <div>
                  <strong>Motivo da Inconsistência:</strong>
                  <p style="margin: 0.25rem 0 0;">{{ item.pauseReason }}</p>
                </div>
              </div>

              <!-- Especialista alocado -->
              <div v-if="item.assignedEditor" class="alert alert-info mt-2">
                <FiUserCheck size="16" style="flex-shrink: 0" />
                <span>
                  <strong>Especialista Designado:</strong> {{ item.assignedEditor }} (atuando na correção)
                </span>
              </div>

              <!-- Logs do Sistema -->
              <section v-if="getProcessLogs(item).length" class="system-log mt-3" aria-label="Log do sistema">
                <h3>Logs e Trilha de Execução</h3>
                <ul>
                  <li v-for="(entry, index) in getProcessLogs(item)" :key="index">{{ entry }}</li>
                </ul>
              </section>

              <!-- Ações da Linha Expandida Geradas de Acordo com a Permissão -->
              <div v-if="canViewFullProcess || canAssignSpecialist || canSignalOperator" class="expanded-actions mt-3">
                <!-- Sempre disponível para todos os perfis (Auditor, Operador, Administrador) -->
                <button
                  v-if="canViewFullProcess"
                  class="btn btn_outline btn_action_audit"
                  @click="goToLog(item.id)"
                >
                  AUDITAR CARGA / VER LINHAGEM
                </button>

                <!-- ALOCAR ESPECIALISTA: Disponível somente com permissão de Admin -->
                <button
                  v-if="canAssignSpecialist && hasErrorAwaitingEditor(item)"
                  class="btn btn_action_editor"
                  @click="openAssignModal(item)"
                >
                  <FiUserPlus size="14" />
                  ALOCAR ESPECIALISTA
                </button>

                <!-- SINALIZAR AO OPERADOR: Disponível somente com permissão de Admin -->
                <button
                  v-if="canSignalOperator && (item.quarantined || (item.status && item.status.toLowerCase().includes('quarentena')))"
                  class="btn btn_action_notify"
                  type="button"
                  :disabled="auditorStatus[item.id] === 'loading' || item.auditorRequested"
                  @click="requestAuditor(item)"
                >
                  <FiAlertTriangle size="14" />
                  {{
                    auditorStatus[item.id] === 'loading'
                      ? 'SINALIZANDO...'
                      : item.auditorRequested || auditorStatus[item.id] === 'requested'
                        ? 'OPERADOR SINALIZADO'
                        : 'SINALIZAR AO OPERADOR'
                  }}
                </button>
              </div>

              <p v-if="auditorStatus[item.id] === 'error'" class="auditor-error mt-2" role="alert">
                Não foi possível sinalizar ao operador. Tente novamente.
              </p>
            </div>
          </template>
        </DataTable>
      </div>

      <!-- Modal de Alocação de Especialista (Apenas Administrador) -->
      <div
        v-if="canAssignSpecialist && showAssignModal && selectedProcessForEditor"
        class="modal-backdrop"
        @click.self="showAssignModal = false"
      >
        <div class="modal-dialog">
          <div class="modal-header">
            <h3>Alocar Especialista para Correção</h3>
            <button class="modal-close" @click="showAssignModal = false">&times;</button>
          </div>
          <div class="modal-body">
            <p>
              A carga <strong>{{ selectedProcessForEditor.id }}</strong> ({{
                selectedProcessForEditor.dataset
              }}) apresentou inconsistência na etapa de <strong>{{ selectedProcessForEditor.stage }}</strong
              >.
            </p>
            <div v-if="selectedProcessForEditor.pauseReason" class="alert alert-warning mb-3">
              <FiAlertTriangle size="15" style="flex-shrink: 0" />
              <small>{{ selectedProcessForEditor.pauseReason }}</small>
            </div>
            <div class="form-group">
              <label class="label">Selecione o Especialista:</label>
              <select class="input" v-model="selectedEditorId">
                <option v-for="editor in availableEditors" :key="editor.id" :value="editor.id">
                  {{ editor.name }}
                </option>
              </select>
            </div>
            <p class="modal-hint">
              Ao alocar um especialista, o status da carga passará imediatamente para
              <strong>"Em andamento"</strong> para a execução das correções manuais.
            </p>
          </div>
          <div class="modal-footer">
            <button
              class="btn btn_outline"
              @click="showAssignModal = false"
              :disabled="isAssigning"
            >
              Cancelar
            </button>
            <button class="btn btn_primary" @click="confirmAssignEditor" :disabled="isAssigning">
              <span v-if="!isAssigning">Confirmar Alocação</span>
              <span v-else>Alocando...</span>
            </button>
          </div>
        </div>
      </div>

      <!-- Botão de Upload: Apenas para quem tem permissão (Operador e Administrador no modo operacional) -->
      <div v-if="canUpload" class="bottom-action">
        <RouterLink to="/upload" class="btn-upload">
          + NOVA CARGA DE DADOS (Upload)
        </RouterLink>
      </div>
    </template>
  </main>
</template>

<style scoped>
.dashboard {
  max-width: 1240px;
  margin: 0 auto;
  color: var(--color-text);
}

.page-heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 24px;
  margin-bottom: 24px;
}

.eyebrow {
  margin: 0 0 6px;
  color: var(--vis-brand-orange);
  font-size: 0.74rem;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.page-heading h1 {
  margin: 0;
  color: var(--color-heading);
  font-size: 1.8rem;
}

.page-description {
  margin: 8px 0 0;
  color: var(--vis-c-text-dark-2);
}

.auditor-badge-header {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(2, 132, 199, 0.1);
  color: #0284c7;
  font-size: 0.78rem;
  font-weight: 600;
  white-space: nowrap;
}

.admin-badge-header {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(124, 58, 237, 0.1);
  color: #7c3aed;
  font-size: 0.78rem;
  font-weight: 600;
  white-space: nowrap;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

/* Abas de categoria rápida */
.category-tabs {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 4px;
}

.category-tab-btn {
  padding: 8px 14px;
  border-radius: 6px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  color: var(--vis-c-text-dark-2);
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s ease;
}

.category-tab-btn:hover {
  background: var(--color-surface-soft);
  color: var(--color-text);
}

.category-tab-btn.active {
  background: var(--vis-brand-orange);
  border-color: var(--vis-brand-orange);
  color: #fff;
}

/* Filtros */
.card-filters {
  background: var(--color-surface);
  border-radius: 8px;
  border: 1px solid var(--color-border);
}

.filters-container {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0.75rem;
  padding: 0.75rem;
}

@media (min-width: 768px) {
  .filters-container {
    grid-template-columns: 1fr 1fr;
  }
}

@media (min-width: 1200px) {
  .filters-container {
    grid-template-columns: auto 1fr 1fr 1fr auto;
    align-items: stretch;
  }
}

.filter-item {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 0.35rem 0.6rem;
  background: var(--color-background);
  min-width: 0;
}

.filter-item label {
  font-size: 0.75rem;
  font-weight: 600;
  white-space: nowrap;
  color: var(--color-heading);
}

.filter-dates {
  flex-direction: column;
  align-items: stretch;
  gap: 0.25rem;
}

.date-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.4rem;
}

.input-inline {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  outline: none;
  color: var(--color-text);
  font-size: 0.8rem;
  padding: 0;
  cursor: pointer;
}

.filter-actions {
  display: flex;
  align-items: stretch;
  justify-content: flex-end;
}

.btn-filter {
  height: 100%;
  border: 1px solid var(--vis-brand-orange, #f26522);
  color: var(--vis-brand-orange, #f26522);
  background: transparent;
  font-weight: 700;
  letter-spacing: 0.04em;
  padding: 0.5rem 1.4rem;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-filter:hover {
  background: var(--vis-brand-orange, #f26522);
  color: #fff;
}

.bottom-action {
  display: flex;
  justify-content: center;
  margin: 2rem 0 1rem;
}

.btn-upload {
  display: inline-block;
  text-decoration: none;
  letter-spacing: 0.03em;
  background-color: var(--vis-brand-orange, #f26522);
  color: white;
  border: none;
  border-radius: 9999px;
  padding: 0.85rem 2rem;
  font-size: 0.95rem;
  font-weight: 700;
  cursor: pointer;
  transition:
    opacity 0.2s,
    transform 0.1s;
  box-shadow: 0 4px 6px rgba(242, 101, 34, 0.3);
  animation: uploadPulse 2.8s ease-in-out infinite;
}

.btn-upload:hover {
  opacity: 0.9;
  transform: translateY(-1px);
  animation-play-state: paused;
}

@keyframes uploadPulse {
  0%,
  100% {
    box-shadow: 0 4px 6px rgba(242, 101, 34, 0.3);
  }
  50% {
    box-shadow: 0 7px 20px rgba(242, 101, 34, 0.48);
  }
}

/* Badges e Tabela */
.badge {
  padding: 0.25rem 0.6rem;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
}

.badge_success { background: #dcfce7; color: #166534; }
.badge_info { background: #e0f2fe; color: #0369a1; }
.badge_warning { background: #fef3c7; color: #b45309; }
.badge_danger { background: #fee2e2; color: #b91c1c; }
.badge_primary { background: #f1f5f9; color: #475569; }

.badge-editor-assigned {
  background: #ede9fe;
  color: #6d28d9;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 0.72rem;
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
}

.table-actions {
  display: flex;
  align-items: center;
  justify-content: center;
}

.action-icon {
  cursor: pointer;
  color: var(--vis-brand-orange);
}

/* Linha expandida PADRONIZADA */
.expanded-details {
  background: transparent;
  padding: 0;
}

.expanded-stepper {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--vis-brand-orange, #f26522);
  border-radius: 8px;
  padding: 1rem 1rem 0.25rem;
  margin: 0.5rem 0 0.75rem;
}

.expanded-stepper :deep(.stepper) {
  margin-bottom: 0.75rem;
}

.expanded-details p {
  margin: 0 0 0.5rem 0;
  font-size: 0.9rem;
}

.system-log {
  background: var(--color-background);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 0.75rem 1rem;
}

.system-log h3 {
  margin: 0 0 0.5rem;
  font-size: 0.85rem;
  color: var(--color-heading);
}

.system-log ul {
  margin: 0;
  padding-left: 1.25rem;
  font-size: 0.82rem;
  color: var(--vis-c-text-dark-2);
}

.expanded-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

/* Botões das ações da linha expandida */
.btn_action_audit {
  border: 1px solid var(--vis-brand-orange, #f26522);
  color: var(--vis-brand-orange, #f26522);
  background: transparent;
  font-weight: 700;
  letter-spacing: 0.04em;
  padding: 0.6rem 1.25rem;
  font-size: 0.82rem;
  text-transform: uppercase;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s ease-in-out;
}

.btn_action_audit:hover {
  background: var(--vis-brand-orange, #f26522);
  color: #fff;
}

.btn_action_editor {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid #7c3aed;
  color: #7c3aed;
  background: rgba(124, 58, 237, 0.06);
  font-weight: 700;
  letter-spacing: 0.04em;
  padding: 0.6rem 1.25rem;
  font-size: 0.82rem;
  text-transform: uppercase;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s ease-in-out;
}

.btn_action_editor:hover {
  background: #7c3aed;
  color: #fff;
}

.btn_action_notify {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--vis-c-danger, #ef4444);
  color: var(--vis-c-danger, #ef4444);
  background: rgba(239, 68, 68, 0.06);
  font-weight: 700;
  letter-spacing: 0.04em;
  padding: 0.6rem 1.25rem;
  font-size: 0.82rem;
  text-transform: uppercase;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s ease-in-out;
}

.btn_action_notify:hover:not(:disabled) {
  background: var(--vis-c-danger, #ef4444);
  color: #fff;
}

.btn_action_notify:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.auditor-error {
  color: var(--vis-c-danger, #ef4444);
}

.alert-warning {
  background-color: rgba(234, 179, 8, 0.15);
  color: var(--color-text);
  padding: 0.75rem;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.alert-warning strong {
  color: #b45309;
}

:root[data-theme='dark'] .alert-warning strong {
  color: #fef08a;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem;
  gap: 1rem;
  color: var(--color-text);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3rem 1.5rem;
  text-align: center;
}

.text-success { color: #16a34a; }
.text-muted { color: var(--vis-c-text-dark-2); }

.alert-success-notification {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  background-color: #ecfdf5;
  border: 1px solid #a7f3d0;
  color: #065f46;
  padding: 0.75rem 1.25rem;
  border-radius: 8px;
  font-weight: 500;
  font-size: 0.95rem;
}

.alert-info {
  background-color: rgba(59, 130, 246, 0.1);
  color: var(--color-text);
  border-left: 3px solid #3b82f6;
  padding: 0.65rem 0.85rem;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.9rem;
}

/* Modal */
.modal-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 1rem;
  box-sizing: border-box;
}

.modal-dialog {
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  width: 100%;
  max-width: 480px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.2);
  overflow: hidden;
  animation: modalScale 0.25s ease-out;
}

@keyframes modalScale {
  from {
    opacity: 0;
    transform: scale(0.95);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1.25rem 1.5rem;
  border-bottom: 1px solid var(--color-border);
}

.modal-header h3 {
  margin: 0;
  font-size: 1.15rem;
  color: var(--color-heading);
}

.modal-close {
  background: none;
  border: none;
  font-size: 1.5rem;
  color: var(--color-text);
  opacity: 0.5;
  cursor: pointer;
  line-height: 1;
}

.modal-close:hover {
  opacity: 1;
}

.modal-body {
  padding: 1.5rem;
}

.form-group {
  margin-top: 1rem;
}

.modal-hint {
  font-size: 0.8rem;
  color: var(--vis-c-text-dark-2);
  margin-top: 0.75rem;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  padding: 1rem 1.5rem;
  border-top: 1px solid var(--color-border);
  background-color: var(--color-background);
}
</style>
