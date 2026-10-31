import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ProcessStageIngestao from '@/components/process-stages/ProcessStageIngestao.vue'
import ProcessStageQuarentena from '@/components/process-stages/ProcessStageQuarentena.vue'
import ProcessStagePublicacao from '@/components/process-stages/ProcessStagePublicacao.vue'
import Auditoria from '@/components/Auditoria.vue'
import { userService } from '@/services/user'
import type { ProcessLogDetails } from '@/services/process'
import type { ProcessVersion } from '@/services/auditoria'

vi.mock('vue-router', () => ({
  useRoute: () => ({
    path: '/detalhes/9001',
    params: { id: '9001' },
    query: {},
  }),
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    back: vi.fn(),
  }),
  RouterLink: {
    template: '<a><slot /></a>',
  },
}))

const sampleDetails: ProcessLogDetails = {
  id: '9001',
  dataSource: 'API',
  dataset: 'Cálculo de Área de Preservação Permanente (APP)',
  stage: 'Validação',
  status: 'Falhou',
  dateTime: '10/10/2026 15:40',
  layerName: 'Camada APP Hidrográfica',
  source: 'IBAMA',
  year: '2026',
  epsg: 'EPSG:4674',
  epsgDetected: 'EPSG:31983',
  description: 'Submissão com divergência de projeção.',
  integrityHash: '38b7ba9ee2c2d7c9a199015b830184ae',
  originalFileUrl: '/carga/download/9001',
  mapCoordinates: [],
  logs: {
    Ingestão: ['Arquivo recebido'],
  },
  pauseReason: 'Polígonos com auto-interseção detectados',
  quarantineRecords: [],
}

const sampleVersions: ProcessVersion[] = [
  {
    ...sampleDetails,
    id: '9001-v3',
    versionTag: 'v3 (Atual)',
    dateTime: '12/10/2026 18:00',
    changeNote: 'Terceira tentativa após revisão topológica',
  },
  {
    ...sampleDetails,
    id: '9001-v2',
    versionTag: 'v2',
    dateTime: '11/10/2026 14:30',
    changeNote: 'Reprocessamento com ajuste de EPSG',
  },
  {
    ...sampleDetails,
    id: '9001-v1',
    versionTag: 'v1 (Original)',
    dateTime: '10/10/2026 15:40',
    changeNote: 'Submissão inicial do arquivo bruto',
  },
]

const samplePublicacaoDetails: ProcessLogDetails = {
  id: 'CARG-2026-PUB1',
  dataSource: 'DEMONSTRACAO',
  dataset: 'Malha Municipal e Divisão Territorial',
  stage: 'Publicação',
  status: 'Em andamento',
  dateTime: '12/10/2026 10:30',
  layerName: 'Camada Municipal 2026',
  source: 'IBGE',
  year: '2026',
  epsg: 'EPSG:4674 (SIRGAS 2000)',
  description: 'Processo pronto para publicação final.',
  integrityHash: 'sha256:778899aabbcc',
  originalFileUrl: '/carga/download/CARG-2026-PUB1',
  mapCoordinates: [],
  logs: {
    Publicação: ['Cálculo concluído.'],
  },
  analyticsData: [
    {
      param: 'Área Total',
      result: '12.450 ha',
      reference: 'Lei 12.651/2012',
      status: 'success',
      statusLabel: 'Conforme',
    },
  ],
}

describe('Etapas do Processo de Carga (Pipeline, Quarentena, Publicação e Auditoria)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    userService.setCurrentUserRole('Operador')
  })

  describe('Etapa de Ingestão e Rastreabilidade (ProcessStageIngestao)', () => {
    it('renderiza árvore de linhagem reversa, hash de integridade SHA-256 e alerta de divergência de EPSG', () => {
      const wrapper = mount(ProcessStageIngestao, {
        props: {
          details: sampleDetails,
          readOnly: true,
          versions: sampleVersions,
        },
      })

      // Código de Integridade Hash
      expect(wrapper.find('[data-testid="integrity-hash"]').text()).toBe(
        '38b7ba9ee2c2d7c9a199015b830184ae',
      )

      // Alerta de Projeção Cartográfica (Divergência entre Declarado e Detectado)
      expect(wrapper.find('[data-testid="epsg-divergence-banner"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="epsg-declared"]').text()).toBe('EPSG:4674')
      expect(wrapper.find('[data-testid="epsg-detected"]').text()).toBe('EPSG:31983')

      // Árvore de Linhagem Reversa (6 nós da publicação até a origem bruta)
      const nodes = wrapper.findAll('[data-testid="lineage-node"]')
      expect(nodes.length).toBe(6)
      expect(nodes[0]!.text()).toContain('1. Status de Publicação')
      expect(nodes[3]!.text()).toContain('2 tentativa(s) anterior(es) registrada(s) no histórico')
      expect(nodes[5]!.text()).toContain('38b7ba9ee2c2d7c9a199015b830184ae')
    })

    it('bloqueia botões de edição de metadados em modo de somente leitura', () => {
      const wrapper = mount(ProcessStageIngestao, {
        props: {
          details: sampleDetails,
          readOnly: true,
        },
      })
      expect(wrapper.find('[data-testid="btn-edit-metadata"]').exists()).toBe(false)
    })
  })

  describe('Painel de Auditoria e Versões (Auditoria)', () => {
    it('renderiza todas as tentativas de submissão e reprocessamento na ordem cronológica correta', () => {
      const wrapper = mount(Auditoria, {
        props: {
          versions: sampleVersions,
          selectedVersionId: '9001-v3',
          cargaId: '9001',
        },
      })

      const items = wrapper.findAll('.location-item')
      expect(items.length).toBe(3)
      expect(items[0]!.text()).toContain('v3 (Atual)')
      expect(items[1]!.text()).toContain('v2')
      expect(items[2]!.text()).toContain('v1 (Original)')
    })
  })

  describe('Etapa de Quarentena e Resolução de Erros (ProcessStageQuarentena)', () => {
    it('notifica erros de validação em toasts para exportação vazia e submissão sem arquivo', async () => {
      const wrapper = mount(ProcessStageQuarentena, {
        props: {
          details: { ...sampleDetails, quarantineRecords: [] },
          readOnly: false,
        },
      })

      // Tentativa de exportar relatório vazio
      await wrapper.find('[data-testid="btn-export-csv"]').trigger('click')
      expect(wrapper.find('[data-testid="quarantine-error-toast"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="quarantine-error-toast"]').text()).toContain('vazio')

      // Tentativa de confirmar reprocessamento sem selecionar novo arquivo
      await wrapper.find('[data-testid="btn-open-reprocess-modal"]').trigger('click')
      expect(wrapper.find('[data-testid="reprocess-modal"]').exists()).toBe(true)
      await wrapper.find('[data-testid="btn-confirm-reprocess"]').trigger('click')
      expect(wrapper.find('[data-testid="quarantine-error-toast"]').text()).toContain(
        'Anexe um novo arquivo corrigido válido',
      )
    })

    it('modal de cancelamento e invalidação exige justificativa textual e alerta sobre a guarda do arquivo original', async () => {
      const wrapper = mount(ProcessStageQuarentena, {
        props: {
          details: { ...sampleDetails, quarantineRecords: [] },
          readOnly: false,
        },
      })

      await wrapper.find('[data-testid="btn-open-cancel-modal"]').trigger('click')
      expect(wrapper.find('[data-testid="cancel-modal"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="hash-retention-warning"]').text()).toContain(
        '38b7ba9ee2c2d7c9a199015b830184ae',
      )

      await wrapper.find('[data-testid="btn-confirm-cancel"]').trigger('click')
      expect(wrapper.find('[data-testid="quarantine-error-toast"]').text()).toContain(
        'justificativa textual obrigatória',
      )
    })

    it('confirma cancelamento/invalidação mantendo integridade do arquivo bruto para auditoria', async () => {
      const details = { ...sampleDetails, status: 'Falhou' as const }
      const wrapper = mount(ProcessStageQuarentena, {
        props: { details, readOnly: false },
      })

      // Cancelamento abortado pelo usuário
      await wrapper.find('[data-testid="btn-open-cancel-modal"]').trigger('click')
      expect(wrapper.find('[data-testid="hash-retention-warning"]').text()).toContain(
        'arquivo bruto original',
      )
      await wrapper.findAll('.modal-footer button')[0]!.trigger('click')
      expect(wrapper.find('[data-testid="cancel-modal"]').exists()).toBe(false)
      expect(details.status).toBe('Falhou')

      // Confirmação com parecer
      await wrapper.find('[data-testid="btn-open-cancel-modal"]').trigger('click')
      await wrapper.find('[data-testid="input-cancel-justification"]').setValue('Arquivo enviado por engano')
      await wrapper.find('[data-testid="btn-confirm-cancel"]').trigger('click')

      expect(details.status).toBe('Fechado')
      const toast = wrapper.find('[data-testid="quarantine-success-toast"]')
      expect(toast.exists()).toBe(true)
      expect(toast.text()).toContain('preservados para auditoria')
    })

    it('restringe ações de mutação na Quarentena para o perfil Auditor, permitindo sinalização ao Operador', () => {
      userService.setCurrentUserRole('Auditor')
      const quarentenaWrapper = mount(ProcessStageQuarentena, {
        props: {
          details: sampleDetails,
          readOnly: true,
        },
      })
      expect(quarentenaWrapper.find('[data-testid="btn-open-reprocess-modal"]').exists()).toBe(false)
      expect(quarentenaWrapper.find('[data-testid="btn-open-cancel-modal"]').exists()).toBe(false)
      expect(quarentenaWrapper.find('[data-testid="btn-signal-operator"]').exists()).toBe(true)
    })
  })

  describe('Etapa de Publicação e Homologação Final (ProcessStagePublicacao)', () => {
    it('integra homologação com modal de confirmação e alerta de versão permanente', async () => {
      const createObjectURLSpy = vi.fn().mockReturnValue('blob:mock-url')
      const revokeObjectURLSpy = vi.fn()
      window.URL.createObjectURL = createObjectURLSpy
      window.URL.revokeObjectURL = revokeObjectURLSpy

      userService.setCurrentUserRole('Administrador')
      const wrapper = mount(ProcessStagePublicacao, {
        props: {
          details: { ...samplePublicacaoDetails },
          readOnly: false,
        },
      })

      expect(wrapper.find('[data-testid="gestor-decision-panel"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="btn-aprovar-publicar"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="btn-reprovar-carga"]').exists()).toBe(true)

      await wrapper.find('[data-testid="btn-aprovar-publicar"]').trigger('click')
      expect(wrapper.find('[data-testid="publish-confirm-modal"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="publish-alert-text"]').text()).toContain(
        'ficará imutável para auditoria histórica',
      )

      await wrapper.find('[data-testid="btn-confirm-publish"]').trigger('click')
      await flushPromises()

      // Exibição do selo oficial e ferramentas de download
      expect(wrapper.find('[data-testid="badge-versao-bloqueada"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="btn-export-official-csv"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="btn-export-official-geojson"]').exists()).toBe(true)

      // Dispara eventos de exportação oficial
      await wrapper.find('[data-testid="btn-export-official-csv"]').trigger('click')
      expect(wrapper.emitted('export')).toBeTruthy()
      expect(wrapper.emitted('export')![0]).toEqual(['csv'])

      await wrapper.find('[data-testid="btn-export-official-geojson"]').trigger('click')
      expect(wrapper.emitted('export')![1]).toEqual(['geojson'])
    })

    it('modal de reprovação exige justificativa técnica obrigatória e registra motivo na carga', async () => {
      userService.setCurrentUserRole('Administrador')
      const details = { ...samplePublicacaoDetails }
      const wrapper = mount(ProcessStagePublicacao, {
        props: {
          details,
          readOnly: false,
        },
      })

      await wrapper.find('[data-testid="btn-reprovar-carga"]').trigger('click')
      expect(wrapper.find('[data-testid="rejection-modal"]').exists()).toBe(true)

      // Confirmação bloqueada se justificativa estiver vazia
      const btnConfirm = wrapper.find('[data-testid="btn-confirm-rejection"]')
      expect(btnConfirm.attributes('disabled')).toBeDefined()

      // Preenche justificativa obrigatória e confirma
      await wrapper.find('[data-testid="input-rejection-reason"]').setValue('Divergência métrica nas áreas')
      expect(btnConfirm.attributes('disabled')).toBeUndefined()

      await btnConfirm.trigger('click')
      await flushPromises()

      expect(wrapper.find('[data-testid="rejection-modal"]').exists()).toBe(false)
      expect(details.stage).toBe('Tratamento')
      expect(details.pauseReason).toContain('Divergência métrica nas áreas')
    })

    it('oculta o painel de homologação para usuários sem privilégios de administração', () => {
      userService.setCurrentUserRole('Auditor')
      const audStage = mount(ProcessStagePublicacao, {
        props: {
          details: { ...samplePublicacaoDetails },
          readOnly: false,
        },
      })
      expect(audStage.find('[data-testid="gestor-decision-panel"]').exists()).toBe(false)

      userService.setCurrentUserRole('Operador')
      const opStage = mount(ProcessStagePublicacao, {
        props: {
          details: { ...samplePublicacaoDetails },
          readOnly: false,
        },
      })
      expect(opStage.find('[data-testid="gestor-decision-panel"]').exists()).toBe(false)
    })
  })
})
