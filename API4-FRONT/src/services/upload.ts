/**
 * Serviço de Upload e Ingestão de Cargas Geoespaciais.
 *
 * REGRA DE ARQUITETURA:
 * Nenhum mock é hardcoded neste serviço de produção.
 * Todas as operações utilizam exclusivamente a API real do backend (/carga/*, /dominios/*).
 * Mocks pertencem exclusivamente ao diretório /mocks utilizado nos testes automatizados.
 */
import api from './api'
import { userService } from './user'

export const uploadService = {
  async cadastrarMetadados(payload: Record<string, any>): Promise<any> {
    const backendPayload = {
      operadorId: userService.getCurrentUser().id || 1,
      orgaoId: parseInt(payload.orgaoEmissor) || 1,
      conjuntoId: parseInt(payload.conjuntoDados) || 1,
      anoSafra: String(payload.anoReferencia || '2026').trim(),
      epsgOrigem: String(payload.epsg || '4674').replace(/\D/g, '') || '4674',
      competencia: payload.competencia,
      formato: payload.formato,
      classificacaoAcesso: payload.classificacaoAcesso,
      coberturaEspacial: payload.coberturaEspacial,
      coberturaTemporalInicio: payload.coberturaTemporalInicio || undefined,
      coberturaTemporalFim: payload.coberturaTemporalFim || undefined,
    }
    const res = await api.post('/carga/metadados', backendPayload)
    return res.data
  },

  async uploadArquivo(
    processoId: number,
    file: File,
    usuarioId: number = userService.getCurrentUser().id,
  ): Promise<any> {
    const formData = new FormData()
    formData.append('processoId', processoId.toString())
    formData.append('file', file)
    if (usuarioId) {
      formData.append('usuarioId', usuarioId.toString())
    }

    const res = await api.post('/carga/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    })
    return res.data
  },

  /**
   * Busca as opções dinâmicas para preenchimento do formulário (anos, epsg, conjuntos, órgãos)
   */
  async getUploadOptions() {
    try {
      const res = await api.get('/dominios/carga')
      if (res.data) {
        return res.data
      }
      return { anos: [], epsgs: [], conjuntos: [], orgaos: [] }
    } catch {
      return { anos: [], epsgs: [], conjuntos: [], orgaos: [] }
    }
  },

  async downloadArquivoOriginal(processoId: number): Promise<Blob> {
    try {
      const res = await api.get(`/carga/download/${processoId}`, {
        responseType: 'blob',
      })
      return res.data
    } catch (err: any) {
      throw err.response?.data || err
    }
  },

  async reprocessarCarga(
    processoId: number,
    file: File,
    usuarioId: number = userService.getCurrentUser().id,
  ): Promise<any> {
    try {
      const formData = new FormData()
      formData.append('file', file)
      if (usuarioId) {
        formData.append('usuarioId', usuarioId.toString())
      }
      const res = await api.post(`/carga/${processoId}/reprocessar`, formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      })
      return res.data
    } catch (err: any) {
      throw err.response?.data || err
    }
  },

  async cancelarCarga(
    processoId: number,
    motivo: string,
    usuarioId: number = userService.getCurrentUser().id,
  ): Promise<void> {
    await api.patch(
      `/carga/${processoId}/cancelar`,
      { motivo },
      {
        params: { usuarioId },
      },
    )
  },

  async notificarOperadorQuarentena(
    processoId: number,
    usuarioId: number = userService.getCurrentUser().id,
  ): Promise<void> {
    await api.post(`/carga/${processoId}/quarentena/notificar-operador`, null, {
      params: { usuarioId },
    })
  },

  async exportarRejeicoesCsv(processoId: number): Promise<Blob> {
    const res = await api.get(`/carga/${processoId}/rejeicoes/exportar`, {
      responseType: 'blob',
    })
    return res.data
  },

  async importarReferenciasGeoespaciais(
    fonte?: string,
    limitePorFonte: number = 5000,
  ): Promise<any> {
    const res = await api.post('/carga/referencias/importar', null, {
      params: { fonte, limitePorFonte },
    })
    return res.data
  },
}
