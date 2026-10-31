import { describe, it, expect, vi, beforeEach } from 'vitest'
import { spatialAnalysisService } from '../spatialAnalysis'
import { catalogoService } from '../catalogo'
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

vi.mock('../catalogo', () => ({
  catalogoService: {
    listarBases: vi.fn(),
    buscarDetalhesBase: vi.fn(),
  },
}))

describe('Spatial Analysis Service', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    spatialAnalysisService.localDocuments.clear()
  })

  it('deve listar documentos territoriais com categorias mapeadas do catálogo', async () => {
    vi.mocked(catalogoService.listarBases).mockResolvedValueOnce([
      {
        id: 1,
        ano_safra: '2026',
        versao_oficial: 'v1.0',
        data_publicacao: '2026-10-20',
        total_feicoes: 50,
        conjunto: 'Imóveis Rurais CAR',
        orgao: 'INCRA',
      },
      {
        id: 2,
        ano_safra: '2026',
        versao_oficial: 'v1.0',
        data_publicacao: '2026-10-20',
        total_feicoes: 12,
        conjunto: 'Áreas de Preservação Permanente (APP)',
        orgao: 'INPE',
      },
    ])

    const docs = await spatialAnalysisService.listDocuments()
    expect(docs.length).toBe(5)
    expect(docs[0].id).toBe('1')
    expect(docs[0].category).toBe('CAR')
    expect(docs[1].id).toBe('2')
    expect(docs[1].category).toBe('APP')
    expect(docs.some((d) => d.id === 'ref-app')).toBe(true)
    expect(docs.some((d) => d.id === 'ref-reserva-legal')).toBe(true)
    expect(docs.some((d) => d.id === 'ref-alerta')).toBe(true)
  })

  it('deve retornar as camadas de referência quando o catálogo for vazio e mockactive for false', async () => {
    vi.mocked(catalogoService.listarBases).mockResolvedValueOnce([])

    const docs = await spatialAnalysisService.listDocuments()
    expect(docs.length).toBe(3)
    expect(docs.map((d) => d.id)).toEqual(['ref-app', 'ref-reserva-legal', 'ref-alerta'])
  })

  it('deve registrar e recuperar documento local pré-carregado na sessão', async () => {
    const geojson: GeoJSON.FeatureCollection = {
      type: 'FeatureCollection',
      features: [
        {
          type: 'Feature',
          geometry: {
            type: 'Polygon',
            coordinates: [
              [
                [-47.88, -15.79],
                [-47.80, -15.79],
                [-47.80, -15.85],
                [-47.88, -15.85],
                [-47.88, -15.79],
              ],
            ],
          },
          properties: { id: 'local-1' },
        },
      ],
    }

    const doc = spatialAnalysisService.registerLocalDocument(
      {
        id: 'local-teste-1',
        name: 'Camada Local Teste',
        category: 'CAR',
        source: 'Upload Local',
        year: '2026',
        zone: 'SESSAO',
        status: 'CARREGADO',
        featureCount: 1,
      },
      geojson,
    )

    expect(doc.localOnly).toBe(true)
    const retrievedGeoJson = await spatialAnalysisService.getDocumentGeoJSON('local-teste-1')
    expect(retrievedGeoJson).toEqual(geojson)
  })

  it('deve calcular cruzamento territorial chamando POST /analise/cruzamento', async () => {
    const apiResponse = {
      propriedades: [
        {
          processoId: 10,
          codigoImovel: 'CAR-MS-001',
          anoSafra: '2026',
          areaImovelHectares: 200,
          hectaresAfetados: 50,
          sobreposicoes: [
            {
              categoria: 'APP',
              fonte: 'INCAR_APP',
              alerta: 'APP Hídrica',
              hectares: 50,
            },
          ],
        },
      ],
    }

    vi.mocked(api.post).mockResolvedValueOnce({
      data: apiResponse,
    } as any)

    const result = await spatialAnalysisService.calculate({
      carDocumentId: '10',
      restrictionDocumentIds: ['20'],
      safra: '2026',
    })

    expect(api.post).toHaveBeenCalledWith('/analise/cruzamento', {
      processoIds: [10, 20],
      anoSafra: '2026',
      categorias: ['ALERTA', 'APP', 'RESERVA_LEGAL'],
    })
    expect(result.status).toBe('CONCLUIDO')
    expect(result.results.length).toBe(1)
    expect(result.results[0].codigoCar).toBe('CAR-MS-001')
    expect(result.results[0].percentualSobreposicao).toBe(25) // (50/200)*100
  })

  it('deve bloquear cálculo de sobreposição com arquivos locais não persistidos', async () => {
    await expect(
      spatialAnalysisService.calculate({
        carDocumentId: 'local-1',
        restrictionDocumentIds: ['2'],
        safra: '2026',
      }),
    ).rejects.toThrow('Arquivos importados nesta sessão servem para pré-visualização')
  })
})

