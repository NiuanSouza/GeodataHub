import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mapService } from '../map'
import api from '../api'

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>()
  return {
    ...actual,
    default: {
      get: vi.fn(),
      post: vi.fn(),
    },
    mockactive: false,
  }
})

describe('Map Service', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('deve buscar locais publicados do mapa via /mapa/camadas-legado', async () => {
    const mockLocationsData = [
      { id: 1, name: 'Ponto A', lat: -15.793, lng: -47.882 },
    ]

    vi.mocked(api.get).mockResolvedValueOnce({
      data: mockLocationsData,
    } as any)

    const result = await mapService.getPublishedLocations()
    expect(api.get).toHaveBeenCalledWith('/mapa/camadas-legado')
    expect(result).toEqual(mockLocationsData)
  })

  it('deve retornar lista vazia quando /mapa/camadas-legado estiver vazio e mockactive for false', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({
      data: [],
    } as any)

    const result = await mapService.getPublishedLocations()
    expect(result).toEqual([])
  })

  it('deve buscar camadas territoriais em formato GeoJSON FeatureCollection', async () => {
    const geojsonData = {
      type: 'FeatureCollection',
      features: [
        {
          type: 'Feature',
          geometry: { type: 'Point', coordinates: [-47.882, -15.793] },
          properties: { id: 1, nome: 'Área 1' },
        },
      ],
      totalElements: 1,
    }

    vi.mocked(api.get).mockResolvedValueOnce({
      data: geojsonData,
    } as any)

    const result = await mapService.getTerritories({ conjuntoId: 1, anoSafra: '2026' })
    expect(api.get).toHaveBeenCalledWith('/mapa/camadas', {
      params: { conjuntoId: 1, anoSafra: '2026' },
    })
    expect(result.type).toBe('FeatureCollection')
    expect(result.features.length).toBe(1)
  })

  it('deve propagar erro de falha ao buscar camadas territoriais quando mockactive for false', async () => {
    vi.mocked(api.get).mockRejectedValueOnce(new Error('Erro de conexão com mapa'))

    await expect(mapService.getTerritories()).rejects.toThrow('Erro de conexão com mapa')
  })

  it('deve buscar detalhes da feição por id do polígono', async () => {
    const feicaoData = {
      idPoligono: 10,
      areaHectares: 154.5,
      codigoImovel: 'MS-54321',
    }

    vi.mocked(api.get).mockResolvedValueOnce({
      data: feicaoData,
    } as any)

    const result = await mapService.getFeatureDetails(10)
    expect(api.get).toHaveBeenCalledWith('/mapa/feicoes/10')
    expect(result).toEqual(feicaoData)
  })

  it('deve aprovar tratamento da carga chamando a API de publicação', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({
      data: { sucesso: true, mensagem: 'Carga aprovada para publicação' },
    } as any)

    const result = await mapService.aprovarTratamento(7, 1, 'Parecer favorável')
    expect(api.post).toHaveBeenCalledWith('/publicacao/7/aprovar-tratamento', {
      gestorId: 1,
      parecer: 'Parecer favorável',
    })
    expect(result.sucesso).toBe(true)
  })
})

