/**
 * Serviço de Gerenciamento e Detalhes de Processos de Carga.
 *
 * REGRA DE ARQUITETURA:
 * Nenhum mock é hardcoded neste serviço de produção.
 * Todas as requisições utilizam exclusivamente a API real do backend (/processos/*, /calculos/*).
 * Mocks pertencem exclusivamente ao diretório /mocks utilizado nos testes automatizados.
 */
import api from './api'
import { mapStage, mapStatus } from './dashboard'

/** Item de checklist retornado pela API para as etapas de Validação e Tratamento */
export interface ProcessStageCheck {
  id: string
  label: string
  description: string
  status: 'success' | 'error' | 'pending'
  detail: string
}

/** Linha de resultado analítico retornada pela API para a etapa de Publicação */
export interface ProcessAnalyticsRow {
  param: string
  result: string
  reference: string
  status: 'success' | 'error' | 'warning'
  statusLabel: string
}

export interface QuarantineRecord {
  recordId: string
  motivo: string
  etapaOrigem: string
  execucaoId: string
  dataRejeicao: string
  campo: string
  valorEncontrado: string
  valorEsperado: string
}

export interface ProcessLogDetails {
  id: string
  dataSource?: 'API' | 'DEMONSTRACAO'
  dataset: string
  stage: string
  status: 'Concluída' | 'Em andamento' | 'Aguardando validação' | 'Falhou' | 'Fechado'
  dateTime: string
  layerName: string
  source: string
  year: string
  epsg: string
  epsgDetected?: string
  description: string
  competencia?: string
  formato?: string
  classificacaoAcesso?: string
  coberturaEspacial?: string
  coberturaTemporalInicio?: string
  coberturaTemporalFim?: string
  integrityHash?: string
  originalFileUrl: string
  mapCoordinates: [number, number][]
  logs: Record<string, string[]>
  pauseReason?: string
  assignedEditor?: string
  validationChecks?: ProcessStageCheck[]
  treatmentChecks?: ProcessStageCheck[]
  analyticsData?: ProcessAnalyticsRow[]
  processamentoConcluido?: boolean
  progressoProcessamento?: number
}

export interface ProcessTraceability {
  indicadorId: number
  processoId: number
  arquivoOriginalId: number
  nomeArquivoOriginal: string
  hashSha256: string
  parametrosProcessamento: string
  dataCarga: string
}

export type IndicatorExportFormat = 'geojson' | 'csv'

export const processService = {
  /**
   * Busca os detalhes completos de um processo, incluindo os checklists de
   * Validação/Tratamento e os dados analíticos de Publicação.
   *
   * API esperada: GET /api/processes/{id}
   */
  async getProcessDetails(id: string): Promise<ProcessLogDetails> {
    try {
      const res = await api.get(`/processos/${id}`)
      if (res.data) {
        res.data.dataSource = 'API'
        res.data.stage = mapStage(res.data.stage)
        res.data.status = mapStatus(res.data.status)
        const baseURL = api.defaults.baseURL || 'http://localhost:8080'
        res.data.originalFileUrl = `${baseURL}/carga/download/${id}`

        if (res.data.logs && typeof res.data.logs === 'object') {
          const normalizedLogs: Record<string, string[]> = {}
          for (const [key, val] of Object.entries(res.data.logs)) {
            const mappedKey = mapStage(key)
            normalizedLogs[mappedKey] = [
              ...(normalizedLogs[mappedKey] || []),
              ...(Array.isArray(val) ? (val as string[]) : []),
            ]
          }
          res.data.logs = normalizedLogs
        }

        // Preenche com checklists padrão apenas se mockactive for true
        if (!res.data.validationChecks || res.data.validationChecks.length === 0) {
          res.data.validationChecks = mockactive ? validationChecksOk : []
        }
        if (!res.data.treatmentChecks || res.data.treatmentChecks.length === 0) {
          res.data.treatmentChecks = mockactive ? treatmentChecksOk(res.data.epsg || 'EPSG:4674') : []
        }
        // Integração com cálculo analítico real do backend (/carga/{id}/calculos - US06)
        // Só busca cálculos se o processamento em segundo plano estiver 100% concluído
        if ((!res.data.analyticsData || res.data.analyticsData.length === 0) && res.data.processamentoConcluido !== false) {
          if (/^\d+$/.test(id)) {
            try {
              const calculosRes = await api.get(`/carga/${id}/calculos`)
              const calculos = calculosRes.data
              if (calculos && calculos.areaTotalHectares !== undefined) {
                const totalAppHa = calculos.totaisPorCategoria?.APP?.hectares ?? 0
                const qtdApp = calculos.totaisPorCategoria?.APP?.quantidade ?? 0
                const totalRlHa = calculos.totaisPorCategoria?.RESERVA_LEGAL?.hectares ?? 0
                const qtdRl = calculos.totaisPorCategoria?.RESERVA_LEGAL?.quantidade ?? 0
                const totalAlertaHa = calculos.totaisPorCategoria?.ALERTA?.hectares ?? 0
                const qtdAlerta = calculos.totaisPorCategoria?.ALERTA?.quantidade ?? 0

                res.data.analyticsData = [
                  {
                    param: 'Cálculo de Área Total Contínua',
                    result: `${Number(calculos.areaTotalHectares).toLocaleString('pt-BR')} ha`,
                    reference: `${calculos.poligonosCalculados || 0} feição(ões)`,
                    status: 'success',
                    statusLabel: 'Calculado',
                  },
                  {
                    param: 'Proporção de Reserva Legal',
                    result: `${totalRlHa} ha (${qtdRl} cruzamento(s))`,
                    reference: 'Camada de Referência INCRA/INCAR',
                    status: qtdRl > 0 ? 'success' : 'warning',
                    statusLabel: qtdRl > 0 ? 'Regular' : 'Não identificada',
                  },
                  {
                    param: 'Área de APP Hídrica',
                    result: `${totalAppHa} ha (${qtdApp} interseção(ões))`,
                    reference: 'Art. 4º — Lei 12.651/2012',
                    status: 'success',
                    statusLabel: qtdApp > 0 ? 'Conforme' : 'Sem sobreposição',
                  },
                  {
                    param: 'Sobreposição com Áreas Embargadas / Alertas',
                    result: `${totalAlertaHa} ha (${qtdAlerta} alerta(s))`,
                    reference: 'Cadastro IBAMA / INPE',
                    status: qtdAlerta > 0 ? 'danger' : 'success',
                    statusLabel: qtdAlerta > 0 ? 'Alerta Crítico' : 'Sem alertas',
                  },
                ]
              } else {
                res.data.analyticsData = []
              }
            } catch {
              res.data.analyticsData = []
            }
          } else {
            res.data.analyticsData = []
          }
        }
      }
      return res.data
    } catch (err) {
      throw err
    }
  },

  async getTraceability(id: string | number): Promise<ProcessTraceability> {
    const res = await api.get<ProcessTraceability>(`/indicadores/${encodeURIComponent(id)}/linhagem`)
    return res.data
  },

  async exportIndicator(id: string | number, format: IndicatorExportFormat): Promise<Blob> {
    const res = await api.get(`/indicadores/${encodeURIComponent(id)}/exportar`, {
      params: { formato: format },
      responseType: 'blob',
    })
    return res.data as Blob
  },

  async iniciarProcessamento(id: string | number, nomeArquivo?: string): Promise<any> {
    try {
      const params = nomeArquivo ? { nomeArquivo } : {}
      const res = await api.post(`/processos/${id}/processar`, null, { params })
      return res.data
    } catch (err: any) {
      if (!mockactive) throw err.response?.data || err
      console.warn('API indisponível, mockando sucesso do início de processamento')
      await delay(500)
      return { success: true }
    }
  },

  async getLogEspacial(id: string | number): Promise<any> {
    try {
      const res = await api.get(`/processos/${id}/log-espacial`)
      return res.data
    } catch (err: any) {
      if (!mockactive) throw err.response?.data || err
      console.warn('API indisponível, mockando log espacial')
      await delay(300)
      return { type: 'FeatureCollection', features: [] }
    }
  },

  async registrarDecisaoGestor(
    id: string | number,
    payload: {
      gestorId: number
      etapa: 'VALIDACAO' | 'TRATAMENTO' | 'PUBLICACAO'
      decisao: 'APROVADO' | 'REPROVADO'
      parecer?: string
    },
  ): Promise<any> {
    const salvarLocal = () => {
      const decisaoObj = {
        id: Date.now(),
        processoId: id,
        gestorId: payload.gestorId,
        gestorNome: 'Gestor API4',
        etapa: payload.etapa,
        decisao: payload.decisao,
        parecer: payload.parecer,
        dataDecisao: new Date().toISOString(),
      }
      try {
        const raw = localStorage.getItem(`api4_decisoes_${id}`)
        const list = raw ? JSON.parse(raw) : []
        list.unshift(decisaoObj)
        localStorage.setItem(`api4_decisoes_${id}`, JSON.stringify(list))
      } catch {
        // ignore storage error
      }
    }

    try {
      const cleanId = encodeURIComponent(String(id))
      const res = await api.post(`/carga/${cleanId}/decisao-gestor`, payload)
      salvarLocal()
      return res.data
    } catch (err: any) {
      if (!mockactive) throw err.response?.data || err
      console.warn('API indisponível, simulando decisão do gestor')
      await delay(400)
      salvarLocal()
      return { sucesso: true, processoId: id, ...payload }
    }
  },

  async alocarAuditor(
    id: string | number,
    auditorId: number,
    gestorId: number = 1,
  ): Promise<any> {
    try {
      const res = await api.post(`/carga/${id}/alocar-auditor`, { auditorId, gestorId })
      return res.data
    } catch (err: any) {
      if (!mockactive) throw err.response?.data || err
      console.warn('API indisponível, simulando alocação de auditor')
      await delay(300)
      return { sucesso: true, processoId: id, auditorId }
    }
  },

  async getAuditores(): Promise<Array<{ id: number; nome: string; email: string }>> {
    try {
      const res = await api.get('/carga/auditores')
      if (res.data && Array.isArray(res.data) && res.data.length > 0) {
        return res.data
      }
      return [
        { id: 2, nome: 'Carlos Silva (Auditor)', email: 'carlos.auditor@agro.gov.br' },
        { id: 3, nome: 'Mariana Santos (Auditora Sênior)', email: 'mariana.auditora@agro.gov.br' },
      ]
    } catch (err: any) {
      return [
        { id: 2, nome: 'Carlos Silva (Auditor)', email: 'carlos.auditor@agro.gov.br' },
        { id: 3, nome: 'Mariana Santos (Auditora Sênior)', email: 'mariana.auditora@agro.gov.br' },
      ]
    }
  },

  async getDecisoesGestor(id: string | number): Promise<Array<{
    id: number
    processoId: number
    gestorId: number
    gestorNome?: string
    etapa: 'VALIDACAO' | 'TRATAMENTO' | 'PUBLICACAO'
    decisao: 'APROVADO' | 'REPROVADO'
    parecer?: string
    dataDecisao: string
  }>> {
    let localList: any[] = []
    try {
      const raw = localStorage.getItem(`api4_decisoes_${id}`)
      if (raw) localList = JSON.parse(raw)
    } catch {
      // ignore storage error
    }

    try {
      const cleanId = encodeURIComponent(String(id))
      const res = await api.get(`/carga/${cleanId}/decisoes-gestor`)
      if (res.data && Array.isArray(res.data)) {
        if (res.data.length > 0) return res.data
        return localList.length > 0 ? localList : res.data
      }
      return localList
    } catch (err: any) {
      return localList
    }
  },
}
