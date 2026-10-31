import { describe, it, expect, vi } from 'vitest'
import { mapStage, mapStatus, mapMetricasToSummary } from '../dashboard'

describe('Dashboard Service Helpers', () => {
  describe('mapStage', () => {
    it('deve mapear os enumeradores do backend para o formato de UI (Ingestão)', () => {
      expect(mapStage('INGESTAO')).toBe('Ingestão')
      expect(mapStage('TRATAMENTO')).toBe('Tratamento')
      expect(mapStage('VALIDACAO')).toBe('Validação')
      expect(mapStage('PUBLICACAO')).toBe('Publicação')
      expect(mapStage('CALCULO_ANALITICO')).toBe('Publicação')
    })

    it('deve retornar Ingestão quando null ou indefinido', () => {
      expect(mapStage('')).toBe('Ingestão')
    })
  })

  describe('mapStatus', () => {
    it('deve mapear os enumeradores do backend para o formato de UI', () => {
      expect(mapStatus('CONCLUIDA')).toBe('Concluída')
      expect(mapStatus('COM_RESSALVA')).toBe('Com ressalva')
      expect(mapStatus('EM_ANDAMENTO')).toBe('Em andamento')
      expect(mapStatus('EM_VALIDACAO')).toBe('Em quarentena')
      expect(mapStatus('EM_QUARENTENA')).toBe('Em quarentena')
      expect(mapStatus('FALHOU')).toBe('Falhou')
    })
  })

  describe('mapMetricasToSummary', () => {
    it('deve retornar os valores contados corretamente e ignorar a formatação exata', () => {
      const payload = {
        totalProcessos: 10,
        porSituacao: {
          CONCLUIDA: 5,
          EM_ANDAMENTO: 2,
          FALHOU: 3
        },
        porEtapa: {},
        porConjunto: {}
      }
      
      const summary = mapMetricasToSummary(payload)
      expect(summary.length).toBe(4)
      expect(summary[0].value).toBe(10) // Total
      expect(summary[1].value).toBe(5)  // Concluídas
      expect(summary[2].value).toBe(2)  // Em Andamento
      expect(summary[3].value).toBe(3)  // Erros
    })
  })
})
