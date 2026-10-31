import { describe, it, expect } from 'vitest'
import { processService } from '../process'

describe('Process Service', () => {
  it('deve possuir a implementação principal getProcessDetails', () => {
    expect(processService).toBeDefined()
    expect(typeof processService.getProcessDetails).toBe('function')
  })
})
