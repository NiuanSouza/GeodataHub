<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  FiSearch,
  FiMapPin,
  FiShield,
  FiAlertTriangle,
  FiDatabase,
  FiCalendar,
  FiCheckCircle,
  FiRefreshCw,
} from 'vue-icons-plus/fi'
import { dashboardService, type ProcessLog } from '@/services/dashboard'
import { userService } from '@/services/user'

const router = useRouter()

// Filtros rápidos por categoria/status
type QuickCategory =
  'CONCLUIDAS' | 'TODAS' | 'ATTENTION' | 'QUARENTENA' | 'VALIDACAO' | 'TRATAMENTO' | 'EM_CORRECAO'
// Por padrão, selecionado para mostrar somente as cargas concluídas
const selectedCategory = ref<QuickCategory>('CONCLUIDAS')

// Filtros tradicionais padronizados com o Dashboard
const filters = ref({
  dateBeggin: '',
  dateEnd: '',
  conjunto: '',
  etapa: '',
  situacao: '',
})

// Busca textual rápida e filtros adicionais de safra/órgão
const searchQuery = ref('')
const selectedSource = ref('')
const selectedYear = ref('')

// Dados e paginação
const processos = ref<ProcessLog[]>([])
const filterOptions = ref<{
  conjuntos: { id: number | string; label: string }[]
  etapas: { id: number | string; label: string }[]
  situacoes: { id: number | string; label: string }[]
}>({
  conjuntos: [],
  etapas: [],
  situacoes: [],
})
const isLoading = ref(true)
const loadError = ref(false)
const toastErrorMessage = ref<string | null>(null)
const dataSource = ref<'API' | 'DEMONSTRACAO' | ''>('')
const currentPage = ref(0)
const totalPages = ref(1)

// Permissão para abrir auditoria detalhada e visualização de processo
const currentUser = computed(() => userService.getCurrentUser())
const canAuditProcess = computed(() => true)

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
 * Extrai um ano válido a partir do campo year ou da data de publicação.
 */
const resolveYear = (process: ProcessLog): string => {
  if (process.year && String(process.year).trim()) {
    return String(process.year).trim()
  }
  const match = String(process.dateTime || '').match(/\b(20\d{2}|19\d{2})\b/)
  return match?.[1] || '2026'
}

/**
 * Resolve o órgão/fonte da base com fallback amigável.
 */
const resolveSource = (process: ProcessLog): string => {
  if (process.source && String(process.source).trim()) {
    return String(process.source).trim()
  }
  const ds = (process.dataset || '').toLowerCase()
  if (
    ds.includes('car') ||
    ds.includes('imóveis') ||
    ds.includes('imoveis') ||
    ds.includes('reserva')
  ) {
    return 'SICAR / SFB'
  }
  if (ds.includes('app') || ds.includes('hidro') || ds.includes('preserva')) {
    return 'IBAMA / ANA'
  }
  return 'INCRA / GeoRural'
}

/**
 * Verifica se a base está vigente (publicada / concluída / fechada).
 */
const isVigente = (process: ProcessLog): boolean => {
  const norm = (process.status || '').toLowerCase()
  return (
    norm.includes('conclu') ||
    norm.includes('fechad') ||
    norm.includes('publicad') ||
    norm.includes('vigente')
  )
}

/**
 * Opções dinâmicas de Categorias/Conjuntos (servidor + dados carregados).
 */
const availableConjuntos = computed(() => {
  const map = new Map<string, { id: string; label: string }>()
  for (const opt of filterOptions.value.conjuntos || []) {
    map.set(opt.label, { id: String(opt.id), label: opt.label })
  }
  for (const p of processos.value) {
    if (p.dataset && !map.has(p.dataset)) {
      map.set(p.dataset, { id: p.dataset, label: p.dataset })
    }
  }
  return Array.from(map.values())
})

/**
 * Opções dinâmicas de Órgão de Origem.
 */
const availableSources = computed(() => {
  const set = new Set<string>()
  for (const p of processos.value) {
    set.add(resolveSource(p))
  }
  return Array.from(set).sort()
})

/**
 * Opções dinâmicas de Ano da Safra.
 */
const availableYears = computed(() => {
  const set = new Set<string>()
  for (const p of processos.value) {
    set.add(resolveYear(p))
  }
  return Array.from(set).sort((a, b) => b.localeCompare(a))
})

/**
 * Carrega o catálogo do servidor aplicando filtros padrão quando aplicável.
 */
const loadPage = async (pageNumber: number, currentFilters?: any) => {
  isLoading.value = true
  loadError.value = false
  toastErrorMessage.value = null

  try {
    const f = currentFilters || filters.value
    const isNumericConjunto = /^\d+$/.test(f.conjunto)
    const page = await dashboardService.getDashboardProcesses({
      page: pageNumber,
      size: 12,
      dateBegin: f.dateBeggin || undefined,
      dateEnd: f.dateEnd || undefined,
      conjunto: isNumericConjunto ? f.conjunto : undefined,
      etapa: f.etapa || undefined,
      situacao: f.situacao || undefined,
    })
    processos.value = page.content || []
    dataSource.value = page.source ?? 'API'
    currentPage.value = page.number ?? 0
    totalPages.value = Math.max(1, page.totalPages ?? 1)

    // Se nenhuma base for vigente/concluída mas existirem cargas cadastradas, auto-chavear para TODAS
    if (
      processos.value.length > 0 &&
      processos.value.filter(isVigente).length === 0 &&
      selectedCategory.value === 'CONCLUIDAS'
    ) {
      selectedCategory.value = 'TODAS'
    }
  } catch (error: any) {
    console.error('Não foi possível carregar o catálogo de bases:', error)
    loadError.value = true
    const isTimeout =
      error?.code === 'ECONNABORTED' ||
      String(error?.message || '')
        .toLowerCase()
        .includes('timeout')
    toastErrorMessage.value = isTimeout
      ? 'Tempo limite excedido (timeout) ao carregar o catálogo de bases. Tente novamente.'
      : 'Erro ao carregar o catálogo de bases. Verifique a conexão com o servidor.'
  } finally {
    isLoading.value = false
  }
}

onMounted(async () => {
  try {
    const opts = await dashboardService.getFilterOptions()
    if (opts) {
      filterOptions.value.conjuntos = opts.conjuntos || []
      filterOptions.value.etapas = opts.etapas || []
      filterOptions.value.situacoes = opts.situacoes || []
    }
  } catch {
    // Segue com opções derivadas dos itens carregados
  }
  await loadPage(0)
})

const applyFilters = async () => {
  await loadPage(0, filters.value)
}

/**
 * Filtra os itens combinando as abas rápidas, filtros tradicionais, órgão, safra e busca textual.
 */
const filteredProcessos = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()

  // Resolve o label do conjunto caso o filtro selecionado seja um ID numérico
  const selectedConjuntoOption = availableConjuntos.value.find(
    (c) => String(c.id) === String(filters.value.conjunto),
  )
  const targetConjuntoLabel = selectedConjuntoOption
    ? selectedConjuntoOption.label.toLowerCase()
    : filters.value.conjunto.trim().toLowerCase()

  return processos.value.filter((process) => {
    const status = (process.status || '').toLowerCase()
    const stage = (process.stage || '').toLowerCase()
    const itemSource = resolveSource(process)
    const itemYear = resolveYear(process)
    const itemDataset = (process.dataset || '').toLowerCase()

    // 1. Filtros Rápidos por Aba
    if (selectedCategory.value === 'CONCLUIDAS') {
      if (!isVigente(process)) return false
    } else if (selectedCategory.value === 'ATTENTION') {
      if (!isErrorOrQuarantined(process)) return false
    } else if (selectedCategory.value === 'QUARENTENA') {
      if (!status.includes('quarentena') && !process.quarantined) return false
    } else if (selectedCategory.value === 'VALIDACAO') {
      if (!stage.includes('valida')) return false
    } else if (selectedCategory.value === 'TRATAMENTO') {
      if (!stage.includes('trata')) return false
    } else if (selectedCategory.value === 'EM_CORRECAO') {
      if (!process.assignedEditor) return false
    }

    // 2. Filtros Tradicionais
    if (
      targetConjuntoLabel &&
      itemDataset !== targetConjuntoLabel &&
      !itemDataset.includes(targetConjuntoLabel)
    ) {
      return false
    }

    if (filters.value.etapa) {
      const opt = filterOptions.value.etapas.find(
        (e) => String(e.id) === String(filters.value.etapa),
      )
      if (opt && !stage.includes(opt.label.toLowerCase())) return false
    }

    if (filters.value.situacao) {
      const opt = filterOptions.value.situacoes.find(
        (s) => String(s.id) === String(filters.value.situacao),
      )
      if (opt && !status.includes(opt.label.toLowerCase())) return false
    }

    // 3. Filtros específicos do Catálogo (Órgão e Ano da Safra)
    if (selectedSource.value && itemSource.toLowerCase() !== selectedSource.value.toLowerCase()) {
      return false
    }

    if (selectedYear.value && itemYear !== selectedYear.value) {
      return false
    }

    // 4. Filtro de busca rápida por texto
    if (q) {
      const searchable = [
        process.id,
        process.dataset,
        itemSource,
        itemYear,
        process.stage,
        process.status,
        isVigente(process) ? 'vigente' : '',
      ]
        .filter(Boolean)
        .map((v) => String(v).toLowerCase())

      if (!searchable.some((val) => val.includes(q))) {
        return false
      }
    }

    return true
  })
})

const handleConjuntoChange = async () => {
  await loadPage(0, filters.value)
}

const clearFilters = async () => {
  const hadServerFilter =
    /^\d+$/.test(filters.value.conjunto) ||
    filters.value.etapa ||
    filters.value.situacao ||
    filters.value.dateBeggin ||
    filters.value.dateEnd
  searchQuery.value = ''
  selectedCategory.value = 'CONCLUIDAS'
  selectedSource.value = ''
  selectedYear.value = ''
  filters.value = {
    dateBeggin: '',
    dateEnd: '',
    conjunto: '',
    etapa: '',
    situacao: '',
  }
  if (hadServerFilter) {
    await loadPage(0)
  }
}

const dismissToast = () => {
  toastErrorMessage.value = null
}

const navigateToProcess = (processId: string) => {
  router.push({ name: 'process-log', params: { id: processId } })
}

const viewOnMap = (process: ProcessLog) => {
  router.push({
    name: 'mapa',
    query: {
      processoId: process.id,
      base: process.dataset,
      id: process.id,
      source: resolveSource(process),
      year: resolveYear(process),
    },
  })
}

const getStatusBadgeClass = (status: string) => {
  const norm = (status || '').toLowerCase()
  if (norm.includes('conclu') || norm.includes('fechad')) return 'status-badge status-concluida'
  if (norm.includes('andamento')) return 'status-badge status-andamento'
  if (norm.includes('valida')) return 'status-badge status-validacao'
  if (norm.includes('falh') || norm.includes('quarentena')) return 'status-badge status-falhou'
  return 'status-badge'
}
</script>

<template>
  <main class="catalog-view">
    <!-- Toast de Erro / Timeout (Tarefa 4) -->
    <Transition name="fade">
      <div
        v-if="toastErrorMessage"
        class="toast-error"
        role="alert"
        data-testid="catalog-error-toast"
      >
        <div class="toast-error-content">
          <FiAlertTriangle size="18" />
          <span>{{ toastErrorMessage }}</span>
        </div>
        <div class="toast-error-actions">
          <button type="button" class="btn-toast-retry" @click="loadPage(currentPage)">
            <FiRefreshCw size="14" /> Tentar novamente
          </button>
          <button
            type="button"
            class="btn-toast-close"
            aria-label="Fechar alerta"
            @click="dismissToast"
          >
            &times;
          </button>
        </div>
      </div>
    </Transition>

    <!-- Cabeçalho da Página -->
    <header class="page-heading">
      <div>
        <p class="eyebrow">Catálogo Territorial & Auditoria</p>
        <h1 class="page-title">Catálogo de Bases Geoespaciais</h1>
        <p class="page-subtitle">
          Consulte os conjuntos disponíveis (Imóveis Rurais, Reserva Legal, APP), filtre por etapa,
          situação, órgão ou safra, visualize no mapa ou audite a linhagem completa.
        </p>
      </div>
      <span v-if="dataSource === 'DEMONSTRACAO'" class="demo-label">Base demonstrativa</span>
    </header>

    <!-- Filtro Rápido em Abas por Categoria / Status (Padrão: Concluídas / Vigentes) -->
    <div class="category-tabs mt-4">
      <button
        type="button"
        class="category-tab-btn"
        :class="{ active: selectedCategory === 'CONCLUIDAS' }"
        data-testid="tab-concluidas"
        @click="selectedCategory = 'CONCLUIDAS'"
      >
        Concluídas ({{ processos.filter(isVigente).length }})
      </button>
      <button
        type="button"
        class="category-tab-btn"
        :class="{ active: selectedCategory === 'TODAS' }"
        data-testid="tab-todas"
        @click="selectedCategory = 'TODAS'"
      >
        Todas as Cargas ({{ processos.length }})
      </button>
      <button
        type="button"
        class="category-tab-btn"
        :class="{ active: selectedCategory === 'ATTENTION' }"
        data-testid="tab-attention"
        @click="selectedCategory = 'ATTENTION'"
      >
        Requerem Atenção ({{ processos.filter(isErrorOrQuarantined).length }})
      </button>
      <button
        type="button"
        class="category-tab-btn"
        :class="{ active: selectedCategory === 'QUARENTENA' }"
        @click="selectedCategory = 'QUARENTENA'"
      >
        Quarentena ({{
          processos.filter(
            (p) => (p.status || '').toLowerCase().includes('quarentena') || p.quarantined,
          ).length
        }})
      </button>
      <button
        type="button"
        class="category-tab-btn"
        :class="{ active: selectedCategory === 'VALIDACAO' }"
        @click="selectedCategory = 'VALIDACAO'"
      >
        Validação ({{
          processos.filter((p) => (p.stage || '').toLowerCase().includes('valida')).length
        }})
      </button>
      <button
        type="button"
        class="category-tab-btn"
        :class="{ active: selectedCategory === 'TRATAMENTO' }"
        @click="selectedCategory = 'TRATAMENTO'"
      >
        Tratamento ({{
          processos.filter((p) => (p.stage || '').toLowerCase().includes('trata')).length
        }})
      </button>
      <button
        type="button"
        class="category-tab-btn"
        :class="{ active: selectedCategory === 'EM_CORRECAO' }"
        @click="selectedCategory = 'EM_CORRECAO'"
      >
        Com Especialista ({{ processos.filter((p) => Boolean(p.assignedEditor)).length }})
      </button>
    </div>

    <!-- Barra de Busca Rápida e Metadados do Catálogo (Safra / Órgão) + Filtros Tradicionais Padronizada com o Dashboard -->
    <section class="card filter-panel mt-3" aria-label="Busca e refinamento do catálogo">
      <div class="search-row">
        <div class="search-input-wrapper">
          <FiSearch class="search-icon" size="18" />
          <input
            v-model="searchQuery"
            type="search"
            data-testid="search-input"
            placeholder="Pesquisar por nome da base, ID, órgão emissor ou ano..."
            class="search-input"
          />
        </div>
        <button
          v-if="
            searchQuery ||
            filters.conjunto ||
            filters.etapa ||
            filters.situacao ||
            filters.dateBeggin ||
            filters.dateEnd ||
            selectedSource ||
            selectedYear ||
            selectedCategory !== 'CONCLUIDAS'
          "
          type="button"
          class="btn btn_outline btn-clear"
          data-testid="clear-filters"
          @click="clearFilters"
        >
          Limpar filtros
        </button>
      </div>

      <div class="selectors-row">
        <div class="filter-field">
          <label for="filter-source">Órgão de Origem:</label>
          <select
            id="filter-source"
            v-model="selectedSource"
            data-testid="filter-source"
            class="filter-select"
          >
            <option value="">Todos os órgãos</option>
            <option v-for="src in availableSources" :key="src" :value="src">
              {{ src }}
            </option>
          </select>
        </div>

        <div class="filter-field">
          <label for="filter-year">Ano da Safra:</label>
          <select
            id="filter-year"
            v-model="selectedYear"
            data-testid="filter-year"
            class="filter-select"
          >
            <option value="">Todos os anos</option>
            <option v-for="yr in availableYears" :key="yr" :value="yr">
              {{ yr }}
            </option>
          </select>
        </div>
      </div>

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
          <label for="filter-conjunto">Conjunto:</label>
          <select
            id="filter-conjunto"
            class="input-inline"
            v-model="filters.conjunto"
            data-testid="filter-conjunto"
            @change="handleConjuntoChange"
          >
            <option value="">Todos</option>
            <option v-for="opt in availableConjuntos" :key="opt.id" :value="String(opt.id)">
              {{ opt.label }}
            </option>
          </select>
        </div>

        <div class="filter-item">
          <label for="filter-etapa">Etapa:</label>
          <select
            id="filter-etapa"
            class="input-inline"
            v-model="filters.etapa"
            data-testid="filter-etapa"
          >
            <option value="">Todas</option>
            <option v-for="opt in filterOptions.etapas" :key="opt.id" :value="opt.id">
              {{ opt.label }}
            </option>
          </select>
        </div>

        <div class="filter-item">
          <label for="filter-situacao">Situação:</label>
          <select
            id="filter-situacao"
            class="input-inline"
            v-model="filters.situacao"
            data-testid="filter-situacao"
          >
            <option value="">Todas</option>
            <option v-for="opt in filterOptions.situacoes" :key="opt.id" :value="opt.id">
              {{ opt.label }}
            </option>
          </select>
        </div>

        <div class="filter-actions">
          <button class="btn btn-filter" data-testid="apply-filters" @click="applyFilters">
            FILTRAR
          </button>
        </div>
      </div>
    </section>

    <!-- Estado de Carregamento -->
    <div v-if="isLoading" class="state-card mt-4" role="status" data-testid="loading-state">
      <div class="spinner"></div>
      <p>Carregando catálogo de bases...</p>
    </div>

    <!-- Empty State Amigável (Tarefa 4) -->
    <div
      v-else-if="filteredProcessos.length === 0"
      class="state-card empty-state mt-4"
      data-testid="empty-state"
    >
      <FiDatabase size="40" class="empty-icon" />
      <h3>Nenhuma base de dados encontrada com estes filtros</h3>
      <p>
        Tente ajustar os termos pesquisados, selecionar a aba "Todas as Cargas" ou limpar os filtros
        de data, conjunto e órgão.
      </p>
      <button
        v-if="
          searchQuery ||
          filters.conjunto ||
          filters.etapa ||
          filters.situacao ||
          filters.dateBeggin ||
          filters.dateEnd ||
          selectedSource ||
          selectedYear ||
          selectedCategory !== 'CONCLUIDAS'
        "
        type="button"
        class="btn btn_outline mt-3"
        @click="clearFilters"
      >
        Restaurar todos os conjuntos
      </button>
    </div>

    <!-- Grid do Catálogo (Tarefas 1 e 2) -->
    <section v-else class="catalog-section mt-4" aria-label="Lista de bases disponíveis">
      <div class="catalog-grid" data-testid="catalog-grid">
        <article
          v-for="process in filteredProcessos"
          :key="process.id"
          class="catalog-card"
          data-testid="catalog-card"
        >
          <!-- Topo do Card: ID e Tag Verde "Vigente" -->
          <div class="card-top">
            <span class="carga-id">Base #{{ process.id }}</span>
            <div class="card-badges">
              <span v-if="isVigente(process)" class="badge-vigente" data-testid="badge-vigente">
                <FiCheckCircle size="12" /> Vigente
              </span>
              <span :class="getStatusBadgeClass(process.status)">
                {{ process.status }}
              </span>
            </div>
          </div>

          <!-- Nome do Conjunto / Categoria -->
          <h2 class="card-dataset">{{ process.dataset }}</h2>
          <p class="card-stage">
            Etapa atual: <strong>{{ process.stage }}</strong>
          </p>

          <!-- Metadados Resumidos (Tarefa 2) -->
          <dl class="card-metadata">
            <div class="meta-item">
              <dt>Fonte / Órgão</dt>
              <dd data-testid="meta-source">{{ resolveSource(process) }}</dd>
            </div>
            <div class="meta-item">
              <dt>Ano da Safra</dt>
              <dd data-testid="meta-year">{{ resolveYear(process) }}</dd>
            </div>
            <div class="meta-item">
              <dt>Data de Publicação</dt>
              <dd data-testid="meta-date">
                <FiCalendar size="12" style="margin-right: 4px" />
                {{ process.dateTime || '—' }}
              </dd>
            </div>
            <div class="meta-item">
              <dt>Sistema (EPSG)</dt>
              <dd>{{ process.epsg || 'EPSG:4674' }}</dd>
            </div>
          </dl>

          <!-- Ações do Card (Tarefa 3) -->
          <div class="card-actions">
            <button
              type="button"
              class="btn-card-map"
              data-testid="btn-view-map"
              @click="viewOnMap(process)"
            >
              <FiMapPin size="14" />
              Visualizar no Mapa
            </button>

            <button
              v-if="canAuditProcess"
              type="button"
              class="btn-card-audit"
              data-testid="btn-audit-process"
              @click="navigateToProcess(process.id)"
            >
              <FiShield size="14" />
              Auditar Linhagem
            </button>
          </div>
        </article>
      </div>

      <!-- Paginação -->
      <nav
        v-if="!isLoading && !loadError && totalPages > 1"
        class="page-controls"
        aria-label="Paginação do catálogo"
      >
        <button
          class="btn btn_outline"
          type="button"
          :disabled="currentPage === 0"
          @click="loadPage(currentPage - 1)"
        >
          Anterior
        </button>
        <span>Página {{ currentPage + 1 }} de {{ totalPages }}</span>
        <button
          class="btn btn_outline"
          type="button"
          :disabled="currentPage + 1 >= totalPages"
          @click="loadPage(currentPage + 1)"
        >
          Próxima
        </button>
      </nav>
    </section>
  </main>
</template>

<style scoped>
.catalog-view {
  max-width: 1240px;
  margin: 0 auto;
  color: var(--color-text);
}

/* Toast de Erro */
.toast-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  background-color: #fef2f2;
  border: 1px solid #fecaca;
  border-left: 4px solid #dc2626;
  color: #991b1b;
  padding: 0.85rem 1.25rem;
  border-radius: 8px;
  margin-bottom: 1.5rem;
  box-shadow: 0 4px 12px rgba(220, 38, 38, 0.1);
}

.toast-error-content {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  font-weight: 600;
  font-size: 0.9rem;
}

.toast-error-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.btn-toast-retry {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  background: #dc2626;
  color: #fff;
  border: none;
  border-radius: 6px;
  padding: 0.35rem 0.75rem;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
}

.btn-toast-close {
  background: transparent;
  border: none;
  color: #991b1b;
  font-size: 1.35rem;
  line-height: 1;
  cursor: pointer;
  padding: 0 0.25rem;
}

/* Cabeçalho */
.page-heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
  margin-bottom: 1rem;
}

.eyebrow {
  margin: 0 0 6px;
  color: var(--vis-brand-orange, #f26522);
  font-size: 0.74rem;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.page-title {
  font-size: 1.8rem;
  font-weight: 700;
  color: var(--color-heading);
  margin: 0 0 0.4rem;
}

.page-subtitle {
  color: var(--vis-c-text-dark-2);
  margin: 0;
  font-size: 0.95rem;
}

.demo-label {
  padding: 0.3rem 0.75rem;
  border-radius: 999px;
  background: rgba(242, 101, 34, 0.12);
  color: var(--vis-brand-orange, #f26522);
  font-size: 0.75rem;
  font-weight: 700;
  white-space: nowrap;
}

/* Abas de categoria rápida padronizadas */
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
  background: var(--vis-brand-orange, #f26522);
  border-color: var(--vis-brand-orange, #f26522);
  color: #fff;
}

/* Barra de Filtros Tradicionais Padronizada */
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
  border: 1px solid var(--vis-brand-orange, #f26522);
  color: var(--vis-brand-orange, #f26522);
  background: transparent;
  font-weight: 700;
  letter-spacing: 0.04em;
  padding: 0.5rem 1.4rem;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
  height: 100%;
}

.btn-filter:hover {
  background: var(--vis-brand-orange, #f26522);
  color: #fff;
}

/* Painel de Busca e Filtros de Safra/Órgão */
.filter-panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 1rem 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.search-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.search-input-wrapper {
  position: relative;
  flex: 1;
}

.search-icon {
  position: absolute;
  top: 50%;
  left: 0.85rem;
  transform: translateY(-50%);
  color: var(--vis-c-text-dark-2);
  opacity: 0.7;
}

.search-input {
  width: 100%;
  padding: 0.55rem 0.85rem 0.55rem 2.5rem;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-background);
  color: var(--color-text);
  font-size: 0.88rem;
  outline: none;
  transition: border-color 0.2s;
}

.search-input:focus {
  border-color: var(--vis-brand-orange, #f26522);
}

.selectors-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 0.75rem;
}

.filter-field {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 0.35rem 0.75rem;
  background: var(--color-background);
}

.filter-field label {
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--color-heading);
  white-space: nowrap;
}

.filter-select {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  color: var(--color-text);
  font-size: 0.82rem;
  outline: none;
  cursor: pointer;
}

.btn-clear {
  white-space: nowrap;
  padding: 0.45rem 0.85rem;
  font-size: 0.8rem;
}

/* Estados de Carregamento e Vazio */
.state-card {
  text-align: center;
  padding: 3.5rem 2rem;
  background: var(--color-surface);
  border: 1px dashed var(--color-border);
  border-radius: 10px;
  color: var(--vis-c-text-dark-2);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
}

.empty-icon {
  color: var(--vis-brand-orange, #f26522);
  opacity: 0.75;
  margin-bottom: 0.5rem;
}

.empty-state h3 {
  margin: 0;
  font-size: 1.15rem;
  color: var(--color-heading);
}

.empty-state p {
  margin: 0;
  max-width: 460px;
  font-size: 0.9rem;
}

/* Grid de Cards do Catálogo */
.catalog-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 1.25rem;
}

.catalog-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 1.35rem;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    border-color 0.18s ease;
}

.catalog-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.06);
  border-color: rgba(242, 101, 34, 0.45);
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.75rem;
}

.carga-id {
  font-family: monospace;
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--vis-brand-orange, #f26522);
  background: rgba(242, 101, 34, 0.1);
  padding: 0.2rem 0.55rem;
  border-radius: 4px;
}

.card-badges {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  flex-wrap: wrap;
}

.badge-vigente {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  background-color: #dcfce7;
  color: #15803d;
  border: 1px solid #86efac;
  padding: 0.2rem 0.55rem;
  border-radius: 999px;
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
}

.status-badge {
  font-size: 0.7rem;
  font-weight: 700;
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  text-transform: uppercase;
  background: #f1f5f9;
  color: #475569;
}

.status-concluida {
  background: #e0f2fe;
  color: #0369a1;
}

.status-andamento {
  background: #fef3c7;
  color: #b45309;
}

.status-validacao {
  background: #ffedd5;
  color: #c2410c;
}

.status-falhou {
  background: #fee2e2;
  color: #b91c1c;
}

.card-dataset {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--color-heading);
  margin: 0 0 0.25rem;
  line-height: 1.35;
}

.card-stage {
  font-size: 0.82rem;
  color: var(--vis-c-text-dark-2);
  margin: 0 0 1rem;
}

.card-metadata {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
  margin: 0 0 1.25rem;
  padding: 0.85rem;
  background: var(--color-background);
  border: 1px solid var(--color-border);
  border-radius: 8px;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.meta-item dt {
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--vis-c-text-dark-2);
}

.meta-item dd {
  margin: 0;
  font-size: 0.84rem;
  font-weight: 600;
  color: var(--color-heading);
  display: flex;
  align-items: center;
}

.card-actions {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding-top: 0.85rem;
  border-top: 1px solid var(--color-border);
}

.btn-card-map,
.btn-card-audit {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  padding: 0.55rem 0.75rem;
  font-size: 0.78rem;
  font-weight: 700;
  text-transform: uppercase;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-card-map {
  background: var(--vis-brand-orange, #f26522);
  color: #fff;
  border: 1px solid var(--vis-brand-orange, #f26522);
}

.btn-card-map:hover {
  opacity: 0.92;
}

.btn-card-audit {
  background: transparent;
  color: var(--vis-brand-orange, #f26522);
  border: 1px solid var(--vis-brand-orange, #f26522);
}

.btn-card-audit:hover {
  background: var(--vis-brand-orange, #f26522);
  color: #fff;
}

.page-controls {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin: 24px 0 8px;
  color: var(--vis-c-text-dark-2);
  font-size: 0.85rem;
}

.page-controls button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
