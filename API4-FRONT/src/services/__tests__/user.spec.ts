import { describe, it, expect, beforeEach } from 'vitest'
import { userService } from '../user'

const storageMock = (() => {
  let store: Record<string, string> = {}
  return {
    getItem: (key: string) => store[key] || null,
    setItem: (key: string, value: string) => {
      store[key] = value.toString()
    },
    removeItem: (key: string) => {
      delete store[key]
    },
    clear: () => {
      store = {}
    },
  }
})()

Object.defineProperty(globalThis, 'localStorage', {
  value: storageMock,
  writable: true,
})

describe('User Service', () => {
  beforeEach(() => {
    localStorage.clear()
    userService.clearCurrentUser()
  })

  it('deve retornar o usuário padrão inicialmente', () => {
    const user = userService.getCurrentUser()
    expect(user).toBeDefined()
    expect(user.role).toBe('Operador')
    expect(user.name).toContain('João Silva')
  })

  it('deve alterar o papel do usuário e persistir no localStorage', () => {
    userService.setCurrentUserRole('Auditor')
    const user = userService.getCurrentUser()
    expect(user.role).toBe('Auditor')
    expect(user.name).toContain('Daniel Natan')
    expect(localStorage.getItem('api4-demo-role')).toBe('Auditor')
  })

  it('deve validar corretamente se o usuário é Administrador', () => {
    userService.setCurrentUserRole('Operador')
    expect(userService.isAdmin()).toBe(false)

    userService.setCurrentUserRole('Administrador')
    expect(userService.isAdmin()).toBe(true)
  })

  it('deve verificar permissão de acesso por perfil (canAccess)', () => {
    userService.setCurrentUserRole('Operador')
    expect(userService.canAccess(['Operador'])).toBe(true)
    expect(userService.canAccess(['Auditor'])).toBe(false)
    expect(userService.canAccess(['Operador', 'Auditor'])).toBe(true)

    // Administrador tem acesso a tudo
    userService.setCurrentUserRole('Administrador')
    expect(userService.canAccess(['Auditor'])).toBe(true)
    expect(userService.canAccess(['Analista'])).toBe(true)
  })

  it('deve retornar o catálogo de usuários de demonstração', () => {
    const demoUsers = userService.getDemoUsers()
    expect(demoUsers.Operador).toBeDefined()
    expect(demoUsers.Auditor).toBeDefined()
    expect(demoUsers.Analista).toBeDefined()
    expect(demoUsers.Administrador).toBeDefined()
  })
})

