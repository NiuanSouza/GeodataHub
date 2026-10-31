import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import CargaListView from '../CargaListView.vue'
import { dashboardService, type ProcessLog } from '@/services/dashboard'

const pushMock = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: pushMock,
  }),
}))

const mockBases: ProcessLog[] = [
  {
    id: '101',
    dateTime: '19/10/2026 20:05',
    dataset: 'Imóveis Rurais (CAR)',
    stage: 'Publicação',
    status: 'Concluída',
    source: 'INCRA',
    year: '2026',
    epsg: 'EPSG:4674',
  },
  {
    id: '102',
    dateTime: '15/08/2025 14:20',
    dataset: 'Reserva Legal',
    stage: 'Publicação',
    status: 'Concluída',
    source: 'SICAR',
    year: '2025',
    epsg: 'EPSG:4674',
  },
  {
    id: '103',
    dateTime: '10/10/2026 11:40',
    dataset: 'Área de Preservação Permanente (APP)',
    stage: 'Validação',
    status: 'Em andamento',
    source: 'IBAMA',
    year: '2026',
    epsg: 'EPSG:4674',
  },
]

describe('CargaListView (Catálogo de Bases)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.spyOn(dashboardService, 'getFilterOptions').mockResolvedValue({
      conjuntos: [
        { id: 1, label: 'Imóveis Rurais (CAR)' },
        { id: 2, label: 'Reserva Legal' },
        { id: 3, label: 'Área de Preservação Permanente (APP)' },
      ],
      etapas: [
        { id: 1, label: 'Ingestão' },
        { id: 2, label: 'Validação' },
        { id: 3, label: 'Tratamento' },
        { id: 4, label: 'Publicação' },
      ],
      situacoes: [
        { id: 1, label: 'Concluída' },
        { id: 2, label: 'Em andamento' },
        { id: 3, label: 'Falhou' },
      ],
    })
    vi.spyOn(dashboardService, 'getDashboardProcesses').mockResolvedValue({
      content: mockBases,
      totalElements: mockBases.length,
      totalPages: 1,
      size: 12,
      number: 0,
      source: 'API',
    })
  })

  it('inicia por padrão com a aba "Concluídas / Vigentes" selecionada e exibe apenas as cargas concluídas', async () => {
    const wrapper = mount(CargaListView)
    await flushPromises()

    // Aba padrão selecionada é Concluídas
    const tabConcluidas = wrapper.find('[data-testid="tab-concluidas"]')
    expect(tabConcluidas.classes()).toContain('active')

    // Deve exibir 2 cards concluídos (101 e 102), filtrando fora a 103 que está "Em andamento"
    const cards = wrapper.findAll('[data-testid="catalog-card"]')
    expect(cards.length).toBe(2)
    expect(cards[0]!.find('[data-testid="badge-vigente"]').exists()).toBe(true)
    expect(cards[1]!.find('[data-testid="badge-vigente"]').exists()).toBe(true)
  })

  it('permite alternar para a aba "Todas as Cargas" e exibir o catálogo completo com todos os metadados', async () => {
    const wrapper = mount(CargaListView)
    await flushPromises()

    // Clica na aba Todas as Cargas
    const tabTodas = wrapper.find('[data-testid="tab-todas"]')
    await tabTodas.trigger('click')

    const cards = wrapper.findAll('[data-testid="catalog-card"]')
    expect(cards.length).toBe(3)

    // Verifica metadados do primeiro card (Imóveis Rurais)
    expect(cards[0]!.text()).toContain('Imóveis Rurais (CAR)')
    expect(cards[0]!.find('[data-testid="meta-source"]').text()).toBe('INCRA')
    expect(cards[0]!.find('[data-testid="meta-year"]').text()).toBe('2026')
    expect(cards[0]!.find('[data-testid="meta-date"]').text()).toContain('19/10/2026 20:05')

    // O terceiro card está "Em andamento", sem tag vigente
    expect(cards[2]!.find('[data-testid="badge-vigente"]').exists()).toBe(false)
  })

  it('filtra corretamente no lado do cliente por texto, Ano da Safra e Órgão de Origem', async () => {
    const wrapper = mount(CargaListView)
    await flushPromises()

    // Alterna para todas as cargas
    await wrapper.find('[data-testid="tab-todas"]').trigger('click')

    // Filtro por Ano da Safra (2025 -> apenas Reserva Legal)
    const yearSelect = wrapper.find('[data-testid="filter-year"]')
    await yearSelect.setValue('2025')
    expect(wrapper.findAll('[data-testid="catalog-card"]').length).toBe(1)
    expect(wrapper.text()).toContain('Reserva Legal')

    // Limpa ano e filtra por Órgão de Origem (IBAMA)
    await yearSelect.setValue('')
    const sourceSelect = wrapper.find('[data-testid="filter-source"]')
    await sourceSelect.setValue('IBAMA')
    expect(wrapper.findAll('[data-testid="catalog-card"]').length).toBe(1)
    expect(wrapper.text()).toContain('Área de Preservação Permanente (APP)')

    // Limpa órgão e filtra por busca textual ("INCRA")
    await sourceSelect.setValue('')
    const searchInput = wrapper.find('[data-testid="search-input"]')
    await searchInput.setValue('INCRA')
    expect(wrapper.findAll('[data-testid="catalog-card"]').length).toBe(1)
    expect(wrapper.text()).toContain('Imóveis Rurais (CAR)')
  })

  it('aciona filtros tradicionais e busca no servidor ao clicar em FILTRAR', async () => {
    const getProcessesSpy = vi.spyOn(dashboardService, 'getDashboardProcesses')
    const wrapper = mount(CargaListView)
    await flushPromises()

    // Configura resposta filtrada do servidor para o conjunto id=2 (Reserva Legal)
    getProcessesSpy.mockResolvedValueOnce({
      content: [mockBases[1]!],
      totalElements: 1,
      totalPages: 1,
      size: 12,
      number: 0,
      source: 'API',
    })

    const conjuntoSelect = wrapper.find('[data-testid="filter-conjunto"]')
    await conjuntoSelect.setValue('2')

    const btnFilter = wrapper.find('[data-testid="apply-filters"]')
    await btnFilter.trigger('click')
    await flushPromises()

    expect(getProcessesSpy).toHaveBeenCalledWith(
      expect.objectContaining({ conjunto: '2', page: 0 }),
    )
    const cards = wrapper.findAll('[data-testid="catalog-card"]')
    expect(cards.length).toBe(1)
    expect(cards[0]!.text()).toContain('Reserva Legal')
  })

  it('exibe Empty State amigável quando nenhuma base corresponde aos filtros', async () => {
    const wrapper = mount(CargaListView)
    await flushPromises()

    const searchInput = wrapper.find('[data-testid="search-input"]')
    await searchInput.setValue('base-inexistente-xyz-999')

    const emptyState = wrapper.find('[data-testid="empty-state"]')
    expect(emptyState.exists()).toBe(true)
    expect(emptyState.text()).toContain('Nenhuma base de dados encontrada com estes filtros')
  })

  it('exibe Toast de erro caso o carregamento do catálogo sofra timeout', async () => {
    vi.spyOn(dashboardService, 'getDashboardProcesses').mockRejectedValueOnce({
      code: 'ECONNABORTED',
      message: 'timeout of 10000ms exceeded',
    })

    const wrapper = mount(CargaListView)
    await flushPromises()

    const toast = wrapper.find('[data-testid="catalog-error-toast"]')
    expect(toast.exists()).toBe(true)
    expect(toast.text().toLowerCase()).toContain('timeout')
  })

  it('navega para o mapa ao clicar em "Visualizar no Mapa"', async () => {
    const wrapper = mount(CargaListView)
    await flushPromises()

    const mapBtn = wrapper.find('[data-testid="btn-view-map"]')
    await mapBtn.trigger('click')

    expect(pushMock).toHaveBeenCalledWith({
      name: 'mapa',
      query: {
        processoId: '101',
        base: 'Imóveis Rurais (CAR)',
        id: '101',
        source: 'INCRA',
        year: '2026',
      },
    })
  })
})
