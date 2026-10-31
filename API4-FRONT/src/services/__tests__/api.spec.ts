import { describe, it, expect } from 'vitest'
import api, { mockactive } from '../api'

describe('API Service', () => {
  it('deve exportar uma instância do axios', () => {
    expect(api).toBeDefined()
    expect(typeof api.get).toBe('function')
    expect(typeof api.post).toBe('function')
  })

  it('deve possuir a variável mockactive', () => {
    expect(typeof mockactive).toBe('boolean')
  })
})
