/**
 * Serviço de Camadas e Delimitações Territoriais do Mapa.
 *
 * REGRA DE ARQUITETURA:
 * Nenhum mock é hardcoded neste serviço de produção.
 * Todas as requisições utilizam exclusivamente a API real do backend (/mapa/*, /publicacao/*).
 * Mocks pertencem exclusivamente ao diretório /mocks utilizado nos testes automatizados.
 */
import api from './api'

export interface MapFilters {
  conjuntoId?: number | string
  anoSafra?: string
  bbox?: string
  page?: number
  size?: number
  busca?: string
  processoId?: number | string
}

export interface PaginatedGeoJson {
  type: 'FeatureCollection'
  features: any[]
  page?: number
  size?: number
  totalElements?: number
  totalPages?: number
}

export const mapService = {
  /**
   * Obtém as localizações das bases publicadas via API.
   * Não utiliza mocks em produção; caso não haja dados ou ocorra erro, retorna array vazio ou propaga.
   */
  async getPublishedLocations() {
    try {
      const response = await api.get('/mapa/camadas-legado')
      if (Array.isArray(response.data)) {
        return response.data
      }
      return []
    } catch (error: any) {
      console.warn('Falha ao buscar locais do mapa via API:', error)
      return []
    }
  },

  /**
   * Obtém as feições vetoriais (GeoJSON) das camadas publicadas ou filtradas.
   * Nenhum dado de mock é injetado em produção.
   */
  async getTerritories(filters?: MapFilters): Promise<PaginatedGeoJson> {
    try {
      const response = await api.get('/mapa/camadas', { params: filters })
      if (response.data && response.data.type === 'FeatureCollection') {
        if (response.data.features && response.data.features.length > 0) {
          return response.data
        }
      }

      // Se filtrou por processoId específico e ainda não constou em /mapa/camadas, tenta a revisão da carga
      if (filters?.processoId) {
        try {
          const rev = await api.get(`/publicacao/${filters.processoId}/revisao`)
          if (rev.data?.feicoesGeoJson?.features && rev.data.feicoesGeoJson.features.length > 0) {
            return rev.data.feicoesGeoJson
          }
        } catch {
          // Continua
        }
      }

      if (response.data && response.data.type === 'FeatureCollection') {
        return response.data
      }

      return { type: 'FeatureCollection', features: [] }
    } catch (error: any) {
      if (filters?.processoId) {
        try {
          const rev = await api.get(`/publicacao/${filters.processoId}/revisao`)
          if (rev.data?.feicoesGeoJson?.features && rev.data.feicoesGeoJson.features.length > 0) {
            return rev.data.feicoesGeoJson
          }
        } catch {
          // Continua
        }
      }
      throw error
    }
  },

  async getFeatureDetails(idPoligono: number | string) {
    const response = await api.get(`/mapa/feicoes/${idPoligono}`)
    return response.data
  },

  async getRevisaoPublicacao(idCarga: number | string) {
    try {
      const response = await api.get(`/publicacao/${idCarga}/revisao`)
      return response.data
    } catch {
      const fallback = await api.get(`/mapa/publicacao/${idCarga}/revisao`)
      return fallback.data
    }
  },

  async aprovarTratamento(
    idCarga: number | string,
    gestorId: number = 1,
    parecer: string = '',
  ) {
    const response = await api.post(`/publicacao/${idCarga}/aprovar-tratamento`, {
      gestorId,
      parecer,
    })
    return response.data
  },
}
