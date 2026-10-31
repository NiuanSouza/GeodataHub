import { describe, it, expect, vi, beforeEach } from 'vitest'
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

describe('Catalogo Service', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('deve listar bases publicadas da API com sucesso', async () => {
    const mockBases = [
      {
        id: 1,
        ano_safra: '2026',
        versao_oficial: 'v1.0',
        data_publicacao: '2026-10-20',
        total_feicoes: 150,
        conjunto: 'Imóveis Rurais',
      },
    ]

    vi.mocked(api.get).mockResolvedValueOnce({
      status: 200,
      data: mockBases,
    } as any)

    const resultado = await catalogoService.listarBases({ anoSafra: '2026' })
    expect(api.get).toHaveBeenCalledWith('/catalogo/bases', {
      params: { anoSafra: '2026' },
    })
    expect(resultado).toEqual(mockBases)
    expect(resultado.length).toBe(1)
  })

  it('deve retornar array vazio quando a API responder 204 No Content', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({
      status: 204,
      data: null,
    } as any)

    const resultado = await catalogoService.listarBases()
    expect(resultado).toEqual([])
  })

  it('deve propagar erro quando a API falhar e mockactive for false', async () => {
    vi.mocked(api.get).mockRejectedValueOnce(new Error('Erro interno no servidor 500'))

    await expect(catalogoService.listarBases()).rejects.toThrow('Erro interno no servidor 500')
  })

  it('deve buscar detalhes de uma base específica por ID', async () => {
    const baseDetalhe = {
      id: 42,
      ano_safra: '2026',
      versao_oficial: 'v2.0',
      data_publicacao: '2026-10-22',
      total_feicoes: 320,
    }

    vi.mocked(api.get).mockResolvedValueOnce({
      status: 200,
      data: baseDetalhe,
    } as any)

    const resultado = await catalogoService.buscarDetalhesBase(42)
    expect(api.get).toHaveBeenCalledWith('/catalogo/bases/42')
    expect(resultado).toEqual(baseDetalhe)
  })

  it('deve propagar erro ao buscar detalhes de base inexistente', async () => {
    vi.mocked(api.get).mockRejectedValueOnce(new Error('Base não encontrada 404'))

    await expect(catalogoService.buscarDetalhesBase(999)).rejects.toThrow('Base não encontrada 404')
  })
})

