import api from './api'
import { userService } from './user'
import { processService } from './process'

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
  source?: 'API' | 'DEMONSTRACAO'
}

export interface ProcessoMetricasResponse {
  totalProcessos: number
  porSituacao: Record<string, number>
  porEtapa: Record<string, number>
  porConjunto: Record<string, number>
}

export interface DashboardSummary {
  title: string
  value: number
  type: 'success' | 'warning' | 'danger' | 'info' | 'primary' | 'orange'
  iconType: 'down' | 'check' | 'warning' | 'error'
}

export interface ProcessLog {
  id: string
  dateTime: string
  dataset: string
  stage: string
  status:
    | 'Concluída'
    | 'Em andamento'
    | 'Aguardando validação'
    | 'Em validação'
    | 'Falhou'
    | 'Em quarentena'
    | 'Fechado'
  source?: string
  year?: string
  epsg?: string
  integrityHash?: string
  pauseReason?: string
  logs?: string[]
  quarantined?: boolean
  auditorRequested?: boolean
  assignedEditor?: string
}

export interface DashboardData {
  summary: DashboardSummary[]
  recentProcesses: ProcessLog[]
}

export interface DashboardFilters {
  dateBegin?: string
  dateEnd?: string
  conjunto?: string
  etapa?: string
  situacao?: string
  page?: number
  size?: number
}

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

export function mapStage(backendStage: string): string {
  if (!backendStage) return 'Ingestão'
  switch (backendStage) {
    case 'INGESTAO':
      return 'Ingestão'
    case 'TRATAMENTO':
      return 'Tratamento'
    case 'VALIDACAO':
      return 'Validação'
    case 'CALCULO_ANALITICO':
    case 'PUBLICACAO':
      return 'Publicação'
    default:
      return backendStage
  }
}

export function mapStatus(backendStatus: string): string {
  if (!backendStatus) return 'Em andamento'
  switch (backendStatus) {
    case 'CONCLUIDA':
      return 'Concluída'
    case 'COM_RESSALVA':
      return 'Com ressalva'
    case 'EM_VALIDACAO':
    case 'EM_QUARENTENA':
      return 'Em quarentena'
    case 'EM_ANDAMENTO':
      return 'Em andamento'
    case 'FALHOU':
      return 'Falhou'
    case 'FECHADO':
      return 'Fechado'
    default:
      return backendStatus
  }
}

export function mapMetricasToSummary(metricas: ProcessoMetricasResponse): DashboardSummary[] {
  const getVal = (key: string) => metricas.porSituacao?.[key] || 0

  return [
    {
      title: 'TOTAL DE CARGAS',
      value: metricas.totalProcessos || 0,
      type: 'orange',
      iconType: 'down',
    },
    {
      title: 'CONCLUÍDAS',
      value: getVal('CONCLUIDA') + getVal('COM_RESSALVA') + getVal('Concluída'),
      type: 'success',
      iconType: 'check',
    },
    {
      title: 'EM ANDAMENTO',
      value: getVal('EM_ANDAMENTO') + getVal('Em andamento'),
      type: 'info',
      iconType: 'down',
    },
    {
      title: 'ERROS',
      value:
        getVal('FALHOU') +
        getVal('EM_VALIDACAO') +
        getVal('Falhou') +
        getVal('Aguardando validação'),
      type: 'danger',
      iconType: 'error',
    },
  ]
}

export const dashboardService = {
  async getDashboardSummary(): Promise<DashboardSummary[]> {
    const res = await api.get('/processos/metricas')
    return mapMetricasToSummary(res.data)
  },

  async getDashboardProcesses(filters?: DashboardFilters): Promise<Page<ProcessLog>> {
    const params: any = {}
    if (filters) {
      if (filters.dateBegin) params.dataInicio = filters.dateBegin
      if (filters.dateEnd) params.dataFim = filters.dateEnd
      if (filters.conjunto) params.conjuntoId = filters.conjunto
      if (filters.etapa) params.etapaId = filters.etapa
      if (filters.situacao) params.situacaoId = filters.situacao
      if (filters.page !== undefined) params.page = filters.page
      if (filters.size !== undefined) params.size = filters.size
    }

    const res = await api.get('/processos', { params })
    if (res.data && res.data.content) {
      res.data.content.forEach((item: any) => {
        item.stage = mapStage(item.stage)
        item.status = mapStatus(item.status)
      })
    }

    return { ...res.data, source: 'API' }
  },

  async requestAuditor(processId: string): Promise<void> {
    const numericId = Number(String(processId).replace(/\D/g, '')) || 1
    await api.post('/notificacoes/auditor', {
      processoId: numericId,
      mensagem: `Solicitação de auditoria para o processo ${processId}`,
    })
  },

  async getFilterOptions() {
    try {
      const res = await api.get('/dominios/filtros')
      return res.data
    } catch {
      return { conjuntos: [], etapas: [], situacoes: [] }
    }
  },

  async getAvailableEditors(): Promise<{ id: number; name: string }[]> {
    try {
      const res = await api.get('/processos/usuarios/auditores')
      if (res.data && Array.isArray(res.data) && res.data.length > 0) {
        return res.data.map((u: any) => ({
          id: u.id || u.usuarioId,
          name: u.nome || u.name || 'Auditor Sem Nome',
        }))
      }
      return []
    } catch {
      return []
    }
  },

  async assignEditor(
    processId: string,
    editorId: number,
    editorName?: string,
  ): Promise<ProcessLog | null> {
    const res = await api.put(`/processos/${processId}/editor`, { editorId })
    return res.data
  },

  /**
   * Aprova e fecha a versão oficial permanentemente na Publicação.
   */
  async aprovarEFecharVersao(
    item: any,
    responsavelNome = userService.getCurrentUser().name,
  ): Promise<{ success: boolean }> {
    const usuarioId = userService.getCurrentUser().id

    if (/^\d+$/.test(String(item.id))) {
      await api.patch(`/indicadores/${item.id}/fechar`, null, {
        headers: { 'X-Gestor-ID': String(usuarioId) },
      })
    }

    item.decided = true
    item.decision = 'aprovado'
    item.isClosed = true
    item.status = 'Fechado'
    item.versaoOficial = item.versaoOficial || 'v1.0 Oficial'
    item.aprovador = responsavelNome
    item.dataPublicacao = new Date().toLocaleString('pt-BR')
    item.pauseReason = 'Versão oficial bloqueada e publicada no catálogo.'

    return { success: true }
  },

  /**
   * Reprova a carga exigindo parecer obrigatório e selecionando destino
   * ('reprocessar' ou 'invalidar').
   */
  async reprovarCarga(
    item: any,
    parecer: string,
    destino: 'reprocessar' | 'invalidar',
  ): Promise<{ success: boolean }> {
    if (!parecer || !parecer.trim()) {
      throw new Error('O parecer técnico é obrigatório para reprovar a carga.')
    }

    if (/^\d+$/.test(String(item.id))) {
      await api.put(`/processos/${item.id}/editor`, {
        editorId: userService.getCurrentUser().id,
      })
    }

    item.decided = true
    item.decision = 'reprovado'
    item.rejectionReason = parecer.trim()
    item.rejectionDestino = destino

    if (destino === 'reprocessar') {
      item.status = 'Em andamento'
      item.pauseReason = `Reprovado (${item.stage || 'Publicação'}): ${parecer.trim()}. Retornado ao Operador.`
    } else {
      item.status = 'Falhou'
      item.pauseReason = `Carga invalidada permanentemente: ${parecer.trim()}.`
    }

    return { success: true }
  },

  /**
   * Exporta a versão consolidada em formato CSV ou GeoJSON.
   */
  async exportarVersao(itemOrId: any, formato: 'csv' | 'geojson'): Promise<Blob> {
    const id = typeof itemOrId === 'object' ? itemOrId.id : String(itemOrId)
    return await processService.exportIndicator(id, formato)
  },
}
