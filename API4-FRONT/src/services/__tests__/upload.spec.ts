import { describe, it, expect } from 'vitest'
import { uploadService } from '../upload'

describe('Upload Service', () => {
  it('deve exportar a camada de serviço e conter os métodos principais', () => {
    expect(uploadService).toBeDefined()
    expect(typeof uploadService.cadastrarMetadados).toBe('function')
    expect(typeof uploadService.uploadArquivo).toBe('function')
    expect(typeof uploadService.getUploadOptions).toBe('function')
  })
})
