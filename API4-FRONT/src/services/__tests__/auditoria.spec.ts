import { describe, it, expect, vi, beforeEach } from 'vitest'
import { auditoriaService } from '../auditoria'
import api from '../api'

vi.mock('../api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
    defaults: { baseURL: 'http://localhost:8080' },
  },
  mockactive: false,
}))

describe('Auditoria Service (US05 - Rastreabilidade e Reprocessamento)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('deve obter linhagem completa da carga via GET /carga/{id}/linhagem', async () => {
    const mockLinhagem = {
      indicadorId: 46,
      processoId: 46,
      arquivoOriginalId: 10,
      nomeArquivoOriginal: 'terras_indigenas.geojson',
      hashSha256: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
      etapaAtual: 'Publicação',
      statusAtual: 'CONCLUIDA',
      arquivos: [
        {
          id: 10,
          nomeArquivo: 'terras_indigenas.geojson',
          hashSha256: 'hash_v1_original',
          dataUpload: '2026-10-25T00:00:00',
          tentativa: 1,
        },
        {
          id: 11,
          nomeArquivo: 'terras_indigenas_v2.geojson',
          hashSha256: 'hash_v2_reprocessado',
          dataUpload: '2026-10-25T01:00:00',
          tentativa: 2,
        },
      ],
      historicoEtapas: [
        { etapa: 'Ingestão', situacao: 'CONCLUIDA', dataInicio: '2026-10-25T00:00:00' },
      ],
      logsQuarentena: [],
      decisoesGestor: [],
    }
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockLinhagem } as any)

    const linhagem = await auditoriaService.getLinhagemCarga(46)
    expect(api.get).toHaveBeenCalledWith('/carga/46/linhagem')
    expect(linhagem).toEqual(mockLinhagem)
  })

  it('deve mapear histórico de arquivos reprocessados como versões com hash individual', async () => {
    const mockLinhagem = {
      processoId: 46,
      nomeArquivoOriginal: 'terras_indigenas.geojson',
      etapaAtual: 'Publicação',
      statusAtual: 'CONCLUIDA',
      arquivos: [
        {
          id: 1,
          nomeArquivo: 'terras_original.geojson',
          hashSha256: 'hash_original_v1',
          dataUpload: '2026-10-25T00:00:00',
          tentativa: 1,
        },
        {
          id: 2,
          nomeArquivo: 'terras_reprocessado.geojson',
          hashSha256: 'hash_reprocessado_v2',
          dataUpload: '2026-10-25T01:00:00',
          tentativa: 2,
        },
      ],
    }
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockLinhagem } as any)

    const resultado = await auditoriaService.getVersionsForProcess('46')
    expect(resultado.carga.id).toBe('46')
    expect(resultado.versions.length).toBe(2)
    // Mais recente no topo:
    expect(resultado.versions[0].versionNumber).toBe(2)
    expect(resultado.versions[0].integrityHash).toBe('hash_reprocessado_v2')
    expect(resultado.versions[0].isLatest).toBe(true)
    // Arquivo original preservado:
    expect(resultado.versions[1].versionNumber).toBe(1)
    expect(resultado.versions[1].integrityHash).toBe('hash_original_v1')
  })

  it('deve retornar lista vazia de versões quando o banco não tiver histórico e não houver currentDetails', async () => {
    vi.mocked(api.get).mockRejectedValue(new Error('Processo não encontrado'))

    const resultado = await auditoriaService.getVersionsForProcess('999')
    expect(resultado.versions).toEqual([])
    expect(resultado.carga.totalVersions).toBe(0)
  })

  it('deve usar currentDetails como fallback quando a API de linhagem falhar', async () => {
    vi.mocked(api.get).mockRejectedValue(new Error('API offline'))

    const mockDetails: any = {
      id: '46',
      dataset: 'Terras indígenas',
      layerName: 'Terras indígenas',
      stage: 'Publicação',
      status: 'Concluída',
      integrityHash: 'fallback_hash_123',
    }

    const resultado = await auditoriaService.getVersionsForProcess('46', mockDetails)
    expect(resultado.versions.length).toBe(1)
    expect(resultado.versions[0].id).toBe('46')
    expect(resultado.versions[0].integrityHash).toBe('fallback_hash_123')
  })
})
