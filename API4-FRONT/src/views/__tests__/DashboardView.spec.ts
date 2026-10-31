import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import DashboardView from '../DashboardView.vue'
import { userService } from '@/services/user'
import { dashboardService, type ProcessLog } from '@/services/dashboard'

const pushMock = vi.fn()

vi.mock('vue-router', () => ({
  useRoute: () => ({
    path: '/',
    params: {},
    query: {},
  }),
  useRouter: () => ({
    push: pushMock,
    replace: vi.fn(),
    back: vi.fn(),
  }),
  RouterLink: {
    template: '<a><slot /></a>',
  },
  'router-link': {
    template: '<a><slot /></a>',
  },
}))

const mockProcesses: ProcessLog[] = [
  {
    id: '9001',
    dateTime: '10/10/2026 15:40',
    dataset: 'APP Hidrográfica',
    stage: 'Validação',
    status: 'Falhou',
    source: 'IBAMA',
    year: '2026',
    epsg: 'EPSG:4674',
    quarantined: true,
  },
  {
    id: '9002',
    dateTime: '12/10/2026 18:20',
    dataset: 'Imóveis Rurais',
    stage: 'Tratamento',
    status: 'Em andamento',
    source: 'INCRA',
    year: '2026',
    epsg: 'EPSG:4674',
    assignedEditor: 'João Especialista',
  },
  {
    id: '9003',
    dateTime: '15/10/2026 09:10',
    dataset: 'Reserva Legal',
    stage: 'Publicação',
    status: 'Concluída',
    source: 'SICAR',
    year: '2026',
    epsg: 'EPSG:4674',
  },
]

describe('DashboardView - Painel Operacional Unificado', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    userService.setCurrentUserRole('Operador')

    vi.spyOn(dashboardService, 'getFilterOptions').mockResolvedValue({
      conjuntos: [
        { id: 1, label: 'APP Hidrográfica' },
        { id: 2, label: 'Imóveis Rurais' },
      ],
      etapas: [
        { id: 1, label: 'Validação' },
        { id: 2, label: 'Tratamento' },
      ],
      situacoes: [
        { id: 1, label: 'Concluída' },
        { id: 2, label: 'Falhou' },
      ],
    })
    vi.spyOn(dashboardService, 'getAvailableEditors').mockResolvedValue([
      { id: 10, name: 'Carlos Especialista' },
    ])
    vi.spyOn(dashboardService, 'getDashboardSummary').mockResolvedValue([])
    vi.spyOn(dashboardService, 'getDashboardProcesses').mockResolvedValue({
      content: mockProcesses,
      totalElements: mockProcesses.length,
      totalPages: 1,
      size: 10,
      number: 0,
    })
  })

  it('renderiza o cabeçalho operacional, cards de resumo e a tabela padrão com todas as cargas', async () => {
    const wrapper = mount(DashboardView)
    await flushPromises()

    expect(wrapper.find('h1').text()).toContain('Dashboard Operacional')
    expect(wrapper.find('.card-filters').exists()).toBe(true)

    // Verifica que exibe a tabela com as 3 linhas carregadas
    const rows = wrapper.findAll('.clickable-row')
    expect(rows.length).toBe(3)
    expect(wrapper.text()).toContain('APP Hidrográfica')
    expect(wrapper.text()).toContain('Imóveis Rurais')
  })

  it('filtra processos ao alternar entre as abas de categoria rápida', async () => {
    const wrapper = mount(DashboardView)
    await flushPromises()

    // Aba Quarentena -> exibe apenas carga retida (9001)
    const btnQuarentena = wrapper.findAll('.category-tab-btn')[2]!
    await btnQuarentena.trigger('click')
    const rowsQuarentena = wrapper.findAll('.clickable-row')
    expect(rowsQuarentena.length).toBe(1)
    expect(rowsQuarentena[0]!.text()).toContain('APP Hidrográfica')

    // Aba Com Especialista -> exibe apenas carga com assignedEditor (9002)
    const btnEspecialista = wrapper.findAll('.category-tab-btn')[5]!
    await btnEspecialista.trigger('click')
    const rowsEspecialista = wrapper.findAll('.clickable-row')
    expect(rowsEspecialista.length).toBe(1)
    expect(rowsEspecialista[0]!.text()).toContain('Imóveis Rurais')
  })

  it('aplica filtros de formulário e consulta o serviço do dashboard', async () => {
    const getProcessesSpy = vi.spyOn(dashboardService, 'getDashboardProcesses')
    const wrapper = mount(DashboardView)
    await flushPromises()

    const conjuntoSelect = wrapper.find('.filters-container select')
    await conjuntoSelect.setValue(1)

    await wrapper.find('.btn-filter').trigger('click')
    await flushPromises()

    expect(getProcessesSpy).toHaveBeenCalledWith(
      expect.objectContaining({ conjunto: 1 }),
    )
  })

  it('expande linha para exibir detalhes e permite navegar para a tela de auditoria da carga', async () => {
    userService.setCurrentUserRole('Auditor')
    const wrapper = mount(DashboardView)
    await flushPromises()

    // Clica na linha para expandir detalhes
    const row = wrapper.find('.clickable-row')
    expect(row.exists()).toBe(true)
    await row.trigger('click')
    await flushPromises()

    // Detalhes expandidos: exibe metadados e botão de auditoria
    const auditBtn = wrapper.find('.btn_action_audit')
    expect(auditBtn.exists()).toBe(true)
    expect(auditBtn.text()).toContain('AUDITAR CARGA / VER LINHAGEM')

    await auditBtn.trigger('click')
    expect(pushMock).toHaveBeenCalledWith({
      name: 'process-log',
      params: { id: '9001' },
    })
  })

  it('permite acesso de visualização das cargas operacionais para usuário com perfil Administrador', async () => {
    userService.setCurrentUserRole('Administrador')
    const wrapper = mount(DashboardView)
    await flushPromises()

    expect(wrapper.find('h1').text()).toContain('Dashboard Operacional')
    expect(wrapper.findAll('.category-tab-btn').length).toBe(6)
    expect(wrapper.text()).toContain('APP Hidrográfica')
  })

  it('exibe botão de upload para Operador e oculta botões de mutação para Auditor', async () => {
    // 1. Operador: visualiza botão de upload
    userService.setCurrentUserRole('Operador')
    const opWrapper = mount(DashboardView)
    await flushPromises()
    expect(opWrapper.find('.btn-upload').exists()).toBe(true)

    // 2. Auditor: oculta botão de upload e de alocação de especialista
    userService.setCurrentUserRole('Auditor')
    const audWrapper = mount(DashboardView)
    await flushPromises()
    expect(audWrapper.find('.btn-upload').exists()).toBe(false)
    expect(audWrapper.find('.btn_action_editor').exists()).toBe(false)
  })
})
