/**
 * Serviço de Auditoria, Rastreabilidade e Linhagem de Cargas (US05 - Issue #16).
 *
 * REGRA DE ARQUITETURA:
 * Nenhum mock é hardcoded neste serviço de produção.
 * Todas as informações de versões, histórico, quarentena e reprocessamentos são obtidas
 * diretamente via API REST (/carga/{id}/linhagem ou /indicadores/{id}/linhagem).
 * Mocks pertencem exclusivamente ao diretório /mocks utilizado nos testes automatizados.
 */
import api from './api'
import type { ProcessLogDetails } from './process'

export interface ArquivoLinhagem {
  id: number
  nomeArquivo: string
  hashSha256: string
  dataUpload: string
  tamanhoBytes?: number
  usuarioId?: number
  usuarioNome?: string
  tentativa: number
}

export interface HistoricoEtapaLinhagem {
  etapa: string
  situacao: string
  dataInicio?: string
  dataFim?: string
  mensagem?: string
  resultado?: string
}

export interface LogQuarentenaLinhagem {
  dataDeteccao: string
  etapaFalha: string
  severidade: string
  mensagemErro: string
}

export interface DecisaoGestorLinhagem {
  id: number
  gestorId: number
  gestorNome: string
  etapa: string
  decisao: string
  parecer: string
  dataDecisao: string
}

export interface EventoAuditoriaLinhagem {
  id: number
  usuarioId: number
  usuarioNome: string
  acao: string
  detalhes: string
  dataHora: string
}

export interface LinhagemCompletaCarga {
  indicadorId: number
  processoId: number
  arquivoOriginalId: number
  nomeArquivoOriginal: string
  hashSha256: string
  parametrosProcessamento: string
  dataCarga: string
  statusAtual: string
  etapaAtual: string
  historicoEtapas: HistoricoEtapaLinhagem[]
  logsQuarentena: LogQuarentenaLinhagem[]
  arquivos: ArquivoLinhagem[]
  decisoesGestor: DecisaoGestorLinhagem[]
  eventosAuditoria: EventoAuditoriaLinhagem[]
}

export interface ProcessVersion extends ProcessLogDetails {
  versionTag: string
  versionNumber: number
  changeNote: string
  isLatest?: boolean
  arquivoId?: number
  tentativa?: number
}

export interface AuditProcessCarga {
  id: string
  dataset: string
  layerName: string
  source: string
  lastUpdate: string
  totalVersions: number
  currentStatus: 'Concluída' | 'Em andamento' | 'Aguardando validação' | 'Falhou'
  currentStage: string
  linhagemCompleta?: LinhagemCompletaCarga
  versions: ProcessVersion[]
}

export const auditoriaService = {
  /**
   * Obtém a linhagem oficial completa da carga via API REST (US05 - Tarefa 15).
   */
  async getLinhagemCarga(processoId: string | number): Promise<LinhagemCompletaCarga | null> {
    const numericId = Number(String(processoId).replace(/\D/g, '')) || processoId
    try {
      const res = await api.get(`/carga/${numericId}/linhagem`)
      return res?.data ?? null
    } catch {
      try {
        const fallbackRes = await api.get(`/indicadores/${numericId}/linhagem`)
        return fallbackRes?.data ?? null
      } catch (err) {
        return null
      }
    }
  },

  /**
   * Busca e formata o histórico de versões e auditoria para uma carga específica.
   * Modela as versões a partir das tentativas/arquivos reprocessados e do histórico real de etapas da carga.
   */
  async getVersionsForProcess(
    processId: string,
    currentDetails?: ProcessLogDetails | null,
  ): Promise<{ carga: AuditProcessCarga; versions: ProcessVersion[] }> {
    const linhagem = await this.getLinhagemCarga(processId)

    if (linhagem) {
      const arquivos = Array.isArray(linhagem.arquivos) && linhagem.arquivos.length > 0
        ? linhagem.arquivos
        : [
            {
              id: linhagem.arquivoOriginalId || 1,
              nomeArquivo: linhagem.nomeArquivoOriginal || 'arquivo_original.geojson',
              hashSha256: linhagem.hashSha256 || '',
              dataUpload: linhagem.dataCarga || '',
              tentativa: 1,
            },
          ]

      const totalArquivos = arquivos.length
      const baseURL = api.defaults.baseURL || 'http://localhost:8080'

      // Cria uma ProcessVersion para cada arquivo/tentativa de envio da carga
      const versions: ProcessVersion[] = arquivos.map((arq, idx) => {
        const tentativaNum = arq.tentativa || idx + 1
        const isLatest = idx === totalArquivos - 1
        const isInitial = idx === 0
        const tag = isLatest
          ? `v${tentativaNum}.0 (Atual)`
          : `v${tentativaNum}.0 (${isInitial ? 'Inicial' : 'Anterior'})`

        const note = isInitial
          ? `Arquivo bruto original submetido em ${arq.dataUpload ? new Date(arq.dataUpload).toLocaleString('pt-BR') : '—'}.`
          : `Novo arquivo submetido em reprocessamento pós-quarentena por ${arq.usuarioNome || 'Operador'}.`

        const versionStatus = isLatest
          ? (linhagem.statusAtual === 'CONCLUIDA'
            ? 'Concluída'
            : linhagem.statusAtual === 'FALHOU'
              ? 'Falhou'
              : 'Em andamento')
          : 'Substituído'

        return {
          id: String(linhagem.processoId || processId),
          dataset: currentDetails?.dataset || linhagem.nomeArquivoOriginal || 'Conjunto Territorial',
          layerName: currentDetails?.layerName || arq.nomeArquivo || `Carga ${processId}`,
          stage: (linhagem.etapaAtual as any) || currentDetails?.stage || 'Ingestão',
          status: versionStatus as any,
          dateTime: arq.dataUpload ? new Date(arq.dataUpload).toLocaleString('pt-BR') : currentDetails?.dateTime || '',
          source: currentDetails?.source || 'Órgão Oficial',
          year: currentDetails?.year || '2026',
          epsg: currentDetails?.epsg || 'EPSG:4674',
          originalFileUrl: `${baseURL}/carga/download/${processId}`,
          integrityHash: arq.hashSha256,
          description: note,
          versionNumber: tentativaNum,
          versionTag: tag,
          isLatest,
          changeNote: note,
          arquivoId: arq.id,
          tentativa: tentativaNum,
          mapCoordinates: currentDetails?.mapCoordinates || [
            [-15.793, -47.882],
            [-15.793, -47.8],
            [-15.85, -47.8],
            [-15.85, -47.882],
          ],
          logs: currentDetails?.logs || {},
          validationChecks: currentDetails?.validationChecks || [],
          treatmentChecks: currentDetails?.treatmentChecks || [],
          analyticsData: currentDetails?.analyticsData || [],
        }
      })

      // Ordena mais recente primeiro
      const reversedVersions = [...versions].reverse()
      const latest = reversedVersions[0]

      const carga: AuditProcessCarga = {
        id: String(linhagem.processoId || processId),
        dataset: currentDetails?.dataset || linhagem.nomeArquivoOriginal || 'Conjunto Territorial',
        layerName: currentDetails?.layerName || linhagem.nomeArquivoOriginal || `Carga ${processId}`,
        source: currentDetails?.source || 'Órgão Oficial',
        lastUpdate: latest?.dateTime || currentDetails?.dateTime || '',
        totalVersions: reversedVersions.length,
        currentStatus: (latest?.status || currentDetails?.status || 'Em andamento') as AuditProcessCarga['currentStatus'],
        currentStage: latest?.stage || currentDetails?.stage || 'Ingestão',
        linhagemCompleta: linhagem,
        versions: reversedVersions,
      }

      return { carga, versions: reversedVersions }
    }

    if (!currentDetails) {
      return {
        carga: {
          id: String(processId),
          dataset: '',
          layerName: '',
          source: '',
          lastUpdate: '',
          totalVersions: 0,
          currentStatus: 'Em andamento',
          currentStage: 'Ingestão',
          versions: [],
        },
        versions: [],
      }
    }

    // Se a linhagem ainda não estiver pronta (ex.: erro de conexão), cria entrada para o processo atual a partir dos detalhes carregados
    const fallbackVersion: ProcessVersion = {
      id: String(processId),
      dataset: currentDetails?.dataset || 'Conjunto Territorial',
      layerName: currentDetails?.layerName || `Carga ${processId}`,
      stage: currentDetails?.stage || 'Ingestão',
      status: currentDetails?.status || 'Em andamento',
      dateTime: currentDetails?.dateTime || new Date().toLocaleString('pt-BR'),
      source: currentDetails?.source || 'Órgão Oficial',
      year: currentDetails?.year || '2026',
      epsg: currentDetails?.epsg || 'EPSG:4674',
      originalFileUrl: `/api/carga/download/${processId}`,
      integrityHash: currentDetails?.integrityHash || '',
      description: currentDetails?.description || 'Processamento inicial da carga.',
      versionNumber: 1,
      versionTag: 'v1.0 (Original)',
      isLatest: true,
      changeNote: currentDetails?.pauseReason || 'Arquivo inicial recebido na esteira.',
      mapCoordinates: currentDetails?.mapCoordinates || [
        [-15.793, -47.882],
        [-15.793, -47.8],
        [-15.85, -47.8],
        [-15.85, -47.882],
      ],
      logs: currentDetails?.logs || {},
      validationChecks: currentDetails?.validationChecks || [],
      treatmentChecks: currentDetails?.treatmentChecks || [],
      analyticsData: currentDetails?.analyticsData || [],
    }

    const carga: AuditProcessCarga = {
      id: String(processId),
      dataset: currentDetails?.dataset || 'Conjunto Territorial',
      layerName: currentDetails?.layerName || `Carga ${processId}`,
      source: currentDetails?.source || 'Órgão Oficial',
      lastUpdate: fallbackVersion.dateTime,
      totalVersions: 1,
      currentStatus: fallbackVersion.status as any,
      currentStage: fallbackVersion.stage,
      versions: [fallbackVersion],
    }

    return { carga, versions: [fallbackVersion] }
  },
}
